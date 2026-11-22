# Build Guide

The flow below was verified on an Android phone (proot Ubuntu). Other environments work the same way.

## Requirements

| Purpose | Tool |
|---|---|
| Java compile | `javac` (--release 8) + android.jar |
| dex | `d8` from Android build-tools |
| native build | clang + Android NDK sysroot |
| repack | Python `zipfile` |
| sign | MT Manager / apksigner (over 2 GB only MT works) |

## 1. Build the native library

```sh
NDK=<ndk>/toolchains/llvm/prebuilt/linux-x86_64/sysroot
clang --target=aarch64-linux-android24 --sysroot=$NDK \
      -shared -fPIC -O2 -o libarcshim.so shim.c \
      -fuse-ld=lld -nodefaultlibs -lc -ldl -llog -lm
```

## 2. Compile Java into a dex

```sh
javac --release 8 -classpath android.jar -d out java/arcmod/Overlay.java
java -cp d8.jar com.android.tools.r8.D8 --min-api 24 --output dexout out/arcmod/*.class
```

This produces `classes4.dex`.

## 3. Patch the game (once per game version)

1. Disassemble the official APK's `classes2.dex` with baksmali
2. Inside `MainActivity.onCreate`, insert:
   - `System.loadLibrary("arcshim");`
   - `invoke-static {p0}, Larcmod/Overlay;->attach(Landroid/app/Activity;)V`
3. Forward `onActivityResult` to `arcmod.Overlay`
4. Reassemble into `classes2.dex`

## 4. Repack

```python
import zipfile
z = zipfile.ZipFile(src_apk)
zo = zipfile.ZipFile(out_apk, "w", zipfile.ZIP_DEFLATED, compresslevel=6)
for i in z.infolist():
    n = i.filename
    if n.startswith("META-INF/"):
        continue                      # regenerated when signing
    data = z.read(n)
    if n == "classes4.dex":
        data = open("classes4.dex", "rb").read()
    elif n == "lib/arm64-v8a/libarcshim.so":
        data = open("libarcshim.so", "rb").read()
    zi = zipfile.ZipInfo(n, date_time=i.date_time)
    zi.compress_type = i.compress_type
    zi.external_attr = i.external_attr
    zo.writestr(zi, data, compresslevel=6 if i.compress_type else None)
zo.close()
```

Always verify with `zipfile.testzip()` and a non-zero tail check — a truncated APK will not install.

## 5. Sign

- Above 2 GB, `apksigner` overflows (`IndexOutOfBoundsException`); use MT Manager instead
- Install the signed APK; the disclaimer shows on first launch

## Notes

- Keep the package name unchanged (`moe.low.arc`) so saves and config survive
- `arcmod/` data lives in external storage and survives APK upgrades
- Every major game update requires step 3 again
