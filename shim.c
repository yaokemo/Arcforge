#define _GNU_SOURCE
#include <android/asset_manager.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <errno.h>
#include <stdarg.h>

static const char* LOGPATH="/storage/emulated/0/Android/data/moe.low.arc/files/arcmod/shim.log";
static void plog(const char* fmt, ...){
  FILE* f=fopen(LOGPATH,"a"); if(!f) return;
  va_list ap; va_start(ap,fmt); vfprintf(f,fmt,ap); va_end(ap);
  fputc(10,f); fclose(f);
}

static const char* DIRS[] = {
  "/storage/emulated/0/Android/data/moe.low.arc/files/arcmod/",
  "/sdcard/Android/data/moe.low.arc/files/arcmod/",
  0
};

#define MAXE 64
#define MAXH 256
struct ent { AAsset* a; unsigned char* buf; long len, pos; };
static struct { AAsset* a; int song; } HS[MAXH];
static struct { int fd; int song; } FS[256];
static struct { FILE* f; int song; } FP[256];
static int is_song_path(const char* p){
  if(!p) return 0;
  if(!strstr(p, "/files/dl/")) return 0;
  size_t n = strlen(p);
  if(n > 4 && strcmp(p+n-4, ".tmp")==0) return 0;
  if(n > 4 && strcmp(p+n-4, ".pre")==0) return 0;
  return 1;
}
static void fp_add(FILE* f, int song){ for(int i=0;i<256;i++) if(!FP[i].f){ FP[i].f=f; FP[i].song=song; return; } }
static int fp_song(FILE* f){ for(int i=0;i<256;i++) if(FP[i].f==f) return FP[i].song; return 0; }
static void fp_del(FILE* f){ for(int i=0;i<256;i++) if(FP[i].f==f){ FP[i].f=0; FP[i].song=0; return; } }
static void fs_add(int fd, int song){ for(int i=0;i<256;i++) if(!FS[i].fd){ FS[i].fd=fd; FS[i].song=song; return; } }
static int fs_song(int fd){ for(int i=0;i<256;i++) if(FS[i].fd==fd) return FS[i].song; return 0; }
static void fs_del(int fd){ for(int i=0;i<256;i++) if(FS[i].fd==fd){ FS[i].fd=0; FS[i].song=0; return; } }
static volatile long g_song_last = 0;
static volatile long g_song_reads = 0;
static volatile int  g_song_open = 0;
static volatile long g_play_ts = 0;
static volatile long g_play_hits = 0;
static volatile long g_dl_ts = 0;
static volatile long g_chart_ts = 0;

static long mono_ms(void){
  struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
  return ts.tv_sec*1000L + ts.tv_nsec/1000000L;
}

static void hs_add(AAsset* a, int song){
  for(int i=0;i<MAXH;i++) if(!HS[i].a){ HS[i].a=a; HS[i].song=song; return; }
}
static int hs_song(AAsset* a){
  for(int i=0;i<MAXH;i++) if(HS[i].a==a) return HS[i].song;
  return 0;
}
static void hs_del(AAsset* a){
  for(int i=0;i<MAXH;i++) if(HS[i].a==a){ HS[i].a=0; HS[i].song=0; return; }
}

static void* status_thread(void* arg){
  char path[512];
  snprintf(path,sizeof(path),"%ssong_state.txt", DIRS[0]);
  for(;;){
    long now=mono_ms();
    FILE* f=fopen(path,"w");
    if(f){ fprintf(f,"%ld %ld %ld %ld %ld %ld\n", g_song_last, now, g_song_reads, g_play_ts, g_dl_ts, g_chart_ts); fclose(f); }
    struct timespec ts={0,100*1000000L}; nanosleep(&ts,0);
  }
  return 0;
}
static struct ent g[MAXE];
static pthread_mutex_t mtx = PTHREAD_MUTEX_INITIALIZER;

static FILE* (*real_fopen)(const char*, const char*);
static size_t (*real_fread)(void*, size_t, size_t, FILE*);
static int (*real_fclose)(FILE*);
static int (*real_sys_open)(const char*, int, ...);
static ssize_t (*real_sys_read)(int, void*, size_t);
static int (*real_sys_close)(int);
static void* (*real_open)(AAssetManager*, const char*, int);
static void  (*real_close)(AAsset*);
static int   (*real_read)(AAsset*, void*, size_t);
static off_t (*real_len)(AAsset*);

static void initreal(void){
  if(real_open) return;
  void* h = dlopen("libandroid.so", RTLD_NOW|RTLD_NOLOAD);
  if(!h) h = dlopen("libandroid.so", RTLD_NOW);
  real_open  = dlsym(h, "AAssetManager_open");
  real_close = dlsym(h, "AAsset_close");
  real_read  = dlsym(h, "AAsset_read");
  real_len   = dlsym(h, "AAsset_getLength");
  real_fopen = dlsym(h, "fopen");
  real_fread = dlsym(h, "fread");
  real_fclose= dlsym(h, "fclose");
  real_sys_open = dlsym(h, "open");
  real_sys_read = dlsym(h, "read");
  real_sys_close= dlsym(h, "close");
}

AAsset* AAssetManager_open(AAssetManager* mgr, const char* filename, int mode);
off_t AAsset_getLength(AAsset* a);
int AAsset_read(AAsset* a, void* buf, size_t count);
void AAsset_close(AAsset* a);

static unsigned char* loadfile(const char* path, long* out){
  struct stat st;
  if(stat(path,&st)!=0 || st.st_size<=0) return 0;
  FILE* f=fopen(path,"rb"); if(!f) return 0;
  unsigned char* b=malloc(st.st_size);
  if(b){ *out=fread(b,1,st.st_size,f); } else *out=0;
  fclose(f); return b;
}

/*
  Minimal GNU .mo patcher:
  reads overrides file (lines: old<TAB>new) and rewrites msgstr values.
*/
/* 原地替换 msgstr：结构、偏移、hash 表都不动，只覆盖那一段字节（新串需 <= 原长，余下补 0） */
static unsigned char* mo_patch(const unsigned char* d, long n, long* outLen){
  if(n < 28 || memcmp(d, "\xde\x12\x04\x95", 4)) return 0;
  unsigned int cnt, oo, to;
  memcpy(&cnt, d+8, 4); memcpy(&oo, d+12, 4); memcpy(&to, d+16, 4);
  if(cnt == 0 || cnt > 50000) return 0;
  if((long)oo + 4*(long)cnt > n || (long)to + 4*(long)cnt > n) return 0;

  char pairOld[32][128], pairNew[32][128]; int np = 0;
  for(int di=0; DIRS[di]; di++){
    char lp[512]; snprintf(lp,sizeof(lp),"%sassets/tl/overrides.txt",DIRS[di]);
    FILE* f=fopen(lp,"r"); if(!f) continue;
    char line[320];
    while(np<32 && fgets(line,sizeof(line),f)){
      char* tab=strchr(line,'\t'); if(!tab) continue; *tab=0;
      char* nl=strchr(tab+1,'\n'); if(nl) *nl=0;
      if(!line[0] || !tab[1]) continue;
      strncpy(pairOld[np], line, 127); pairOld[np][127]=0;
      strncpy(pairNew[np], tab+1, 127); pairNew[np][127]=0;
      np++;
    }
    fclose(f); break;
  }
  if(!np) return 0;

  unsigned char* buf = malloc(n);
  if(!buf) return 0;
  memcpy(buf, d, n);
  int changed = 0, skipped = 0;
  for(unsigned i=0;i<cnt;i++){
    unsigned int b; memcpy(&b, d+to+4*i, 4);
    if(b >= (unsigned)n) continue;
    const char* v=(const char*)d+b;
    long slot=(long)strlen(v)+1;
    for(int k=0;k<np;k++){
      if(strcmp(pairOld[k], v)) continue;
      long nl2=(long)strlen(pairNew[k]);
      if(nl2 + 1 > slot){ skipped++; break; }
      memcpy(buf+b, pairNew[k], nl2);
      memset(buf+b+nl2, 0, slot-nl2);
      changed++;
      break;
    }
  }
  if(skipped) plog("MO skip %d (name too long)", skipped);
  if(!changed){ free(buf); return 0; }
  *outLen = n;
  return buf;
}

static struct ent* slot(AAsset* a){ for(int i=0;i<MAXE;i++) if(g[i].a==a) return &g[i]; return 0; }
static struct ent* freeslot(void){ for(int i=0;i<MAXE;i++) if(!g[i].a) return &g[i]; return 0; }

static int g_logcnt = 0;
static void logpath(const char* tag, const char* p){
  if(!p || g_logcnt > 200) return;
  if(!strstr(p, ".ogg") && !strstr(p, ".mp3") && !strstr(p, "/dl/")) return;
  g_logcnt++;
  plog("%s %s", tag, p);
}

FILE* fopen(const char* path, const char* mode){
  initreal();
  logpath("FOPEN", path);
  if(is_song_path(path)) g_dl_ts = mono_ms();
  FILE* f = real_fopen(path, mode);
  if(f) fp_add(f, is_song_path(path));
  return f;
}
size_t fread(void* p, size_t sz, size_t n, FILE* f){
  initreal();
  if(fp_song(f)){ g_song_last = mono_ms(); g_song_reads++; }
  return real_fread(p, sz, n, f);
}
int fclose(FILE* f){
  initreal(); fp_del(f);
  return real_fclose(f);
}
int open(const char* path, int flags, ...){
  initreal();
  mode_t m = 0;
  va_list ap; va_start(ap, flags); m = (mode_t)va_arg(ap, int); va_end(ap);
  logpath("OPEN", path);
  if(is_song_path(path)) g_dl_ts = mono_ms();
  int fd = real_sys_open(path, flags, m);
  if(fd >= 0) fs_add(fd, is_song_path(path));
  return fd;
}
ssize_t read(int fd, void* buf, size_t n){
  initreal();
  if(fs_song(fd)){ g_song_last = mono_ms(); g_song_reads++; }
  return real_sys_read(fd, buf, n);
}
int close(int fd){
  initreal(); fs_del(fd);
  return real_sys_close(fd);
}

AAsset* AAssetManager_open(AAssetManager* mgr, const char* filename, int mode){
  initreal();
  if(!filename) return real_open(mgr, filename, mode);
  if(strstr(filename,"songs/") && strstr(filename,".ogg")) g_song_open = 1;

  if(strstr(filename,".aff")){ g_chart_ts = mono_ms(); }
  if((strstr(filename,"layouts/ingame/") || strstr(filename,"img/gamescene/"))
     && !strstr(filename,"layouts/ingame/end/")){
    g_play_ts = mono_ms(); g_play_hits++;
  }
  { char path[512];
    for(int d=0; DIRS[d]; d++){
      snprintf(path,sizeof(path),"%sassets/%s",DIRS[d],filename);
      long n=0; unsigned char* b=loadfile(path,&n);
      if(b){
        AAsset* a=real_open(mgr,filename,mode);
        if(a){
          pthread_mutex_lock(&mtx); struct ent* e=freeslot();
          if(e){ e->a=a; e->buf=b; e->len=n; e->pos=0; }
          pthread_mutex_unlock(&mtx);
          if(e){ plog("HIT %s", filename); return a; }
          free(b);
        } else free(b);
        break;
      }
    }
  }
  AAsset* ra2 = real_open(mgr, filename, mode);
  if(ra2) hs_add(ra2, 0);
  return ra2;
}

off_t AAsset_getLength(AAsset* a){
  initreal();
  pthread_mutex_lock(&mtx); struct ent* e=slot(a); pthread_mutex_unlock(&mtx);
  return e? e->len : real_len(a);
}

int AAsset_read(AAsset* a, void* buf, size_t count){
  initreal();
  if(hs_song(a)){ g_song_last = mono_ms(); g_song_reads++; }
  pthread_mutex_lock(&mtx);
  struct ent* e=slot(a);
  if(e){
    long n=(long)count; if(n>e->len-e->pos) n=e->len-e->pos;
    if(n>0) memcpy(buf, e->buf+e->pos, n);
    e->pos+=n; pthread_mutex_unlock(&mtx); return (int)n;
  }
  pthread_mutex_unlock(&mtx);
  return real_read(a,buf,count);
}

void AAsset_close(AAsset* a){
  initreal();
  hs_del(a);
  pthread_mutex_lock(&mtx);
  struct ent* e=slot(a);
  if(e){ free(e->buf); e->a=0; e->buf=0; e->len=0; e->pos=0; }
  pthread_mutex_unlock(&mtx);
  real_close(a);
}

/* ---- GOT patching ---- */
static struct { const char* name; void* fn; } TARGETS[] = {
  {"fopen",              (void*)fopen},
  {"fread",              (void*)fread},
  {"fclose",             (void*)fclose},
  {"open",               (void*)open},
  {"read",               (void*)read},
  {"close",              (void*)close},
  {"AAssetManager_open", (void*)AAssetManager_open},
  {"AAsset_read",        (void*)AAsset_read},
  {"AAsset_getLength",   (void*)AAsset_getLength},
  {"AAsset_close",       (void*)AAsset_close},
};

static int patch_one(uintptr_t base, Elf64_Rela* rel, int n, Elf64_Sym* sym, const char* str, const char* want, void* fn){
  for(int i=0;i<n;i++){
    int type = ELF64_R_TYPE(rel[i].r_info);
    if(type!=R_AARCH64_JUMP_SLOT && type!=R_AARCH64_GLOB_DAT) continue;
    Elf64_Sym* s = &sym[ELF64_R_SYM(rel[i].r_info)];
    const char* nm = str + s->st_name;
    if(strcmp(nm, want)) continue;
    uintptr_t* slotp = (uintptr_t*)(base + rel[i].r_offset);
    uintptr_t page = (uintptr_t)slotp & ~0xfffUL;
    size_t span = ((uintptr_t)slotp - page) + 16;
    if(mprotect((void*)page, span, PROT_READ|PROT_WRITE) != 0){
      plog("mprotect fail addr=%p errno=%d", (void*)slotp, errno);
      return 0;
    }
    *slotp = (uintptr_t)fn;
    plog("write GOT %s at %p -> %p", want, (void*)slotp, fn);
    return 1;
  }
  return 0;
}

static int patch_lib(uintptr_t base){
  int hit=0;
  Elf64_Ehdr* eh = (Elf64_Ehdr*)base;
  if(memcmp(eh->e_ident, ELFMAG, 4)) return 0;
  Elf64_Phdr* ph = (Elf64_Phdr*)(base + eh->e_phoff);
  Elf64_Dyn* dyn = 0;
  for(int i=0;i<eh->e_phnum;i++) if(ph[i].p_type==PT_DYNAMIC) dyn=(Elf64_Dyn*)(base+ph[i].p_vaddr);
  if(!dyn) return 0;
  uintptr_t symtab=0, strtab=0, jmprel=0, rela=0;
  size_t pltrelsz=0, relasz=0;
  for(Elf64_Dyn* d=dyn; d->d_tag!=DT_NULL; d++){
    switch(d->d_tag){
      case DT_SYMTAB: symtab=base+d->d_un.d_ptr; break;
      case DT_STRTAB: strtab=base+d->d_un.d_ptr; break;
      case DT_JMPREL: jmprel=base+d->d_un.d_ptr; break;
      case DT_PLTRELSZ: pltrelsz=d->d_un.d_val; break;
      case DT_RELA: rela=base+d->d_un.d_ptr; break;
      case DT_RELASZ: relasz=d->d_un.d_val; break;
    }
  }
  if(!symtab || !strtab) return 0;
  Elf64_Sym* sym=(Elf64_Sym*)symtab; const char* str=(const char*)strtab;
  for(unsigned t=0;t<sizeof(TARGETS)/sizeof(TARGETS[0]);t++){
    if(jmprel && pltrelsz && patch_one(base,(Elf64_Rela*)jmprel,pltrelsz/sizeof(Elf64_Rela),sym,str,TARGETS[t].name,TARGETS[t].fn)){ plog("  patched %s (plt)",TARGETS[t].name); hit++; }
    if(rela && relasz && patch_one(base,(Elf64_Rela*)rela,relasz/sizeof(Elf64_Rela),sym,str,TARGETS[t].name,TARGETS[t].fn)){ plog("  patched %s (rela)",TARGETS[t].name); hit++; }
  }
  return hit;
}

static void* waiter(void* arg){
  (void)arg;
  char cmd[128]=""; FILE* cf=fopen("/proc/self/cmdline","r");
  if(cf){ size_t r=fread(cmd,1,sizeof(cmd)-1,cf); cmd[r>0?r:0]=0; fclose(cf); }
  plog("waiter start pid=%d cmd=%s", (int)getpid(), cmd);
  for(int i=0;i<1200;i++){
    void* h=dlopen("libcocos2dcpp.so", RTLD_NOW|RTLD_NOLOAD);
    if(h){
      void* f=dlsym(h, "Java_org_cocos2dx_lib_Cocos2dxRenderer_nativeRender");
      Dl_info di;
      if(f && dladdr(f,&di) && di.dli_fbase){
        uintptr_t base=(uintptr_t)di.dli_fbase;
        plog("FOUND base=%p render=%p (iter %d)", (void*)base, f, i);
        int hits=patch_lib(base);
        plog("patch done hits=%d", hits);
        dlclose(h);
        break;
      }
      dlclose(h);
    }
    if(i==0) plog("scan0: cocos not loaded yet");
    if(i%100==0 && i) plog("wait %d", i);
    usleep(100000);
  }
  return 0;
}

__attribute__((constructor)) static void start(void){
  plog("shim loaded");
  pthread_t t; pthread_create(&t,0,waiter,0);
  pthread_t t2; pthread_create(&t2,0,status_thread,0);
}
