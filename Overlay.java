package arcmod;
import android.widget.ScrollView;
import android.view.ViewGroup;
import java.util.Set;
import java.util.List;
import android.widget.CheckBox;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Looper;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class Overlay {
    static Activity act;
    static final StringBuilder rec = new StringBuilder();
    static boolean recording = false;
    static long recStart = 0;
    static View recTarget = null;
    static volatile boolean autoRunning = false;
    static volatile int replayGen = 0;
    static volatile String curRecName = "?";
    static final java.util.Map<Integer, float[]> downPtrs = java.util.Collections.synchronizedMap(new java.util.HashMap<Integer, float[]>());
    static File recDir() { return new File(root(), "rec"); }
    static long[] T1S = new long[0], T2S = new long[0];
    static float[] X1S = new float[0], X2S = new float[0];
    static final int REQ_ART = 1001;
    static String pendingBase = null;
    static int dpi = 3;

    static File root() { return new File(act.getExternalFilesDir(null), "arcmod"); }
    static File assetFile(String rel) { return new File(new File(root(), "assets"), rel); }

    static void installCrashHandler() {
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    jlog("CRASH in " + t.getName() + ": " + e);
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    File f = new File(root(), "log.txt");
                    FileWriter w = new FileWriter(f, true);
                    w.write(sw.toString());
                    w.close();
                } catch (Throwable ignored) {}
                if (def != null) def.uncaughtException(t, e);
            }
        });
    }

    public static void attach(Activity a) {
        act = a;
        dpi = (int) a.getResources().getDisplayMetrics().density;
        a.runOnUiThread(new Runnable() {
            public void run() {
                try { buildEntry(); } catch (Throwable t) {}
                try {
                    File ag = new File(root(), "agreed.txt");
                    if (!ag.exists()) {
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            public void run() { try { showDisclaimer(); } catch (Throwable t) {} }
                        }, 2500);
                    }
                } catch (Throwable t) {}
            }
        });
    }

    static void buildEntry() {
        final FrameLayout layer = new FrameLayout(act);
        final TextView b = new TextView(act);
        modBtn = b;
        b.setText("MOD");
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setTextSize(6);
        b.setAllCaps(false);
        b.setMinWidth(0); b.setMinHeight(0);
        b.setPadding(2 * dpi, 0, 2 * dpi, 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC7A5CA8);
        g.setCornerRadius(7 * dpi);
        b.setBackground(g);
        b.setTextColor(0xFFFFFFFF);

        final TextView dot = new TextView(act);
        dot.setText("·");
        dot.setTextSize(8);
        dot.setAllCaps(false);
        dot.setMinWidth(0); dot.setMinHeight(0);
        dot.setPadding(4 * dpi, 0, 4 * dpi, 0);
        GradientDrawable g2 = new GradientDrawable();
        g2.setColor(0x66FFFFFF);
        g2.setCornerRadius(7 * dpi);
        dot.setBackground(g2);
        dot.setTextColor(0xAAFFFFFF);
        dot.setVisibility(View.GONE);

        final int bw = (int) (40 * dpi), bh = (int) (22 * dpi);
        final int[] pos = { act.getResources().getDisplayMetrics().widthPixels - 40 * dpi,
                            (int) (act.getResources().getDisplayMetrics().heightPixels * 0.30f) };

        b.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; boolean moved;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX() - pos[0]; dy = e.getRawY() - pos[1]; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float nx = e.getRawX() - dx, ny = e.getRawY() - dy;
                        if (Math.abs(nx - pos[0]) > 4 || Math.abs(ny - pos[1]) > 4) moved = true;
                        pos[0] = (int) nx; pos[1] = (int) ny;
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.getLayoutParams();
                        lp.leftMargin = pos[0]; lp.topMargin = pos[1];
                        b.setLayoutParams(lp);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) v.performClick();
                        return true;
                }
                return false;
            }
        });
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { modMenu(); } });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                b.setVisibility(View.GONE);
                dot.setVisibility(View.VISIBLE);
                FrameLayout.LayoutParams dlp = (FrameLayout.LayoutParams) dot.getLayoutParams();
                dlp.leftMargin = pos[0]; dlp.topMargin = pos[1];
                dot.setLayoutParams(dlp);
                toast("已收起，点小点恢复");
                return true;
            }
        });
        dot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dot.setVisibility(View.GONE); b.setVisibility(View.VISIBLE); }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bw, bh);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = pos[0]; lp.topMargin = pos[1];
        layer.addView(b, lp);
        View kv = new View(act);
        kv.setFocusable(true);
        kv.setFocusableInTouchMode(true);
        kv.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, android.view.KeyEvent e) {
                if (hideMode && e.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
                            || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
                    modMenu();
                    return true;
                }
                return false;
            }
        });
        keyGrab = kv;
        layer.addView(kv, new FrameLayout.LayoutParams(1, 1));
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(bw, bh);
        dp.gravity = Gravity.TOP | Gravity.LEFT;
        dp.leftMargin = pos[0]; dp.topMargin = pos[1];
        layer.addView(dot, dp);
        ((ViewGroup) act.getWindow().getDecorView()).addView(layer,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        installCrashHandler();
        readWin();
        try {
            File lf = new File(root(), "log_on.txt");
            if (lf.exists()) {
                java.io.BufferedReader lr = new java.io.BufferedReader(new FileReader(lf));
                logOn = "1".equals(lr.readLine().trim());
                lr.close();
            }
        } catch (Throwable t) {}
        applyWin();
        applyHide();
    }

    static String[] artBases() {
        try {
            String[] all = act.getAssets().list("char/1080");
            List<String> out = new ArrayList<String>();
            if (all != null) for (String s : all) if (s.endsWith(".png")) out.add(s.substring(0, s.length() - 4));
            Collections.sort(out, new Comparator<String>() {
                public int compare(String a, String b) {
                    int x = num(a), y = num(b);
                    if (x != y) return x - y;
                    return a.compareTo(b);
                }
            });
            return out.toArray(new String[0]);
        } catch (Throwable t) { return new String[0]; }
    }

    static int num(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (Character.isDigit(c)) sb.append(c); else break; }
        try { return Integer.parseInt(sb.toString()); } catch (Throwable t) { return 9999; }
    }

    static String readText(String path) throws Exception {
        InputStream in = act.getAssets().open(path);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    static List<String> objects() {
        try { return splitObjects(readText("char/characters.json")); } catch (Throwable t) { return new ArrayList<String>(); }
    }

    static List<String> splitObjects(String s) {
        List<String> out = new ArrayList<String>();
        int depth = 0, start = -1; boolean inStr = false, esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) { if (esc) esc = false; else if (c == '\\') esc = true; else if (c == '"') inStr = false; continue; }
            if (c == '"') { inStr = true; continue; }
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0 && start >= 0) { out.add(s.substring(start, i + 1)); start = -1; } }
        }
        return out;
    }

    static List<String> stringsOf(String obj) {
        List<String> out = new ArrayList<String>();
        Matcher m = Pattern.compile("\"search_strings\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(obj);
        if (m.find()) {
            Matcher q = Pattern.compile("\"([^\"]*)\"").matcher(m.group(1));
            while (q.find()) out.add(q.group(1));
        }
        return out;
    }

    static int zhIndex(int n) {
        if (n >= 4) return 1;   // ja, zh-Hans, zh-Hant, ko
        if (n == 3) return 1;
        return 0;
    }

    static int zhScore(String s) {
        int sc = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) sc += 3;        // 汉字
            else if (c >= 0x3040 && c <= 0x30FF) sc -= 2;   // 假名
            else if (c >= 0xAC00 && c <= 0xD7AF) sc -= 2;   // 谚文
        }
        return sc;
    }

    static String bestName(List<String> ss) {
        if (ss.isEmpty()) return "";
        int best = 0, bs = Integer.MIN_VALUE;
        for (int i = 0; i < ss.size(); i++) {
            int sc = zhScore(ss.get(i));
            if (sc > bs) { bs = sc; best = i; }
        }
        return ss.get(best);
    }

    static String baseKey(String base) {
        if (base.length() > 1 && (base.endsWith("u") || base.endsWith("o"))) {
            String b2 = base.substring(0, base.length() - 1);
            if (findObject(b2) != null) return b2;
        }
        return base;
    }

    static String findObject(String base) {
        for (String o : objects()) {
            Matcher m = Pattern.compile("\"character_id\"\\s*:\\s*\"?" + Pattern.quote(base) + "\"?(\\s*[,}])").matcher(o);
            if (m.find()) return o;
        }
        return null;
    }

    static String displayName(String base) {
        int i = 0;
        while (i < base.length() && Character.isDigit(base.charAt(i))) i++;
        String num = base.substring(0, i);
        String suffix = base.substring(i);
        String key = (num.length() > 0) ? num : baseKey(base);
        String n = moNameOf(key);
        if (n == null) {
            String o = findObject(key);
            if (o != null) n = bestName(stringsOf(o));
        }
        if (n == null || zhScore(n) < 0) {
            String o = findObject(key);
            String en = null;
            if (o != null) {
                Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"").matcher(o);
                if (m.find()) en = m.group(1);
            }
            if (en != null && en.length() > 0) n = en.substring(0, 1).toUpperCase() + en.substring(1);
        }
        if (n == null) n = key;
        String mark = "";
        String o2 = findObject(key);
        if (o2 != null) {
            Matcher m2 = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"").matcher(o2);
            if (m2.find()) {
                String en = m2.group(1);
                int us2 = en.indexOf('_');
                if (us2 > 0 && us2 + 1 < en.length())
                    mark += "（" + en.substring(us2 + 1).replace('_', ' ') + "）";
            }
        }
        if (suffix.equals("u") || suffix.equals("o")) mark += " ·觉醒";
        else if (suffix.length() > 0) {
            String t = suffix.startsWith("_") ? suffix.substring(1) : suffix;
            if (t.length() > 0) mark = " ·" + t;
        }
        if (moNameOf(key) == null) mark += "（无法改名）";
        return n + mark;
    }

    static int[] pngSize(InputStream in) {
        try {
            byte[] h = new byte[24];
            int r = in.read(h);
            in.close();
            if (r >= 24) {
                int w = ((h[16] & 0xff) << 24) | ((h[17] & 0xff) << 16) | ((h[18] & 0xff) << 8) | (h[19] & 0xff);
                int hh = ((h[20] & 0xff) << 24) | ((h[21] & 0xff) << 16) | ((h[22] & 0xff) << 8) | (h[23] & 0xff);
                return new int[]{w, hh};
            }
        } catch (Throwable t) {}
        return null;
    }

    static int[] assetPngSize(String rel, int[] fallback) {
        try { return pngSize(act.getAssets().open(rel)); } catch (Throwable t) { return fallback; }
    }

    static AlertDialog[] dlgRef = new AlertDialog[1];
    static DialogInterface.OnClickListener listLongPress = null;
    static ViewGroup curFloat = null;
    static TextView modBtn = null;
    static View keyGrab = null;
    static boolean hideMode = false;
    static long lastVolMs = 0;
    static int uiSize = 40, uiAlpha = 100;
    static boolean audioWatch = false;
    static TextView audioView = null;

    static final StringBuilder logBuf = new StringBuilder();
    static volatile long lastLogMs = 0;
    static boolean logOn = false;
    static TextView logView = null;
    static ViewGroup logLayer = null;

    static void addResult(String name, int events, long secs) {
        try {
            File f = new File(root(), "results.txt");
            FileWriter w = new FileWriter(f, true);
            w.write(String.format("%tF %tT", new java.util.Date(), new java.util.Date())
                    + " | " + name + " | " + events + " 事件 | " + secs + " 秒\n");
            w.close();
        } catch (Throwable t) {}
    }

    static void showResults() {
        try {
            File f = new File(root(), "results.txt");
            if (!f.exists()) { toast("还没有记录"); return; }
            java.io.BufferedReader r = new java.io.BufferedReader(new FileReader(f));
            java.util.List<String> lines = new java.util.ArrayList<String>();
            String ln;
            while ((ln = r.readLine()) != null) lines.add(ln);
            r.close();
            int st = Math.max(0, lines.size() - 25);
            StringBuilder b = new StringBuilder();
            for (int i = st; i < lines.size(); i++) b.append(lines.get(i)).append('\n');
            TextView tv = label(b.toString());
            tv.setTextSize(10);
            showDarkCustom("回放记录（最近 25 条）", tv, new String[]{"清空", "关闭"},
                    new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                new File(root(), "results.txt").delete();
                                closeDlg();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { closeDlg(); }
                        }
                    });
        } catch (Throwable t) { toast("读取失败"); }
    }

    static void jlog(String msg) {
        lastLogMs = System.currentTimeMillis();
        String line = String.format("%tT ", new java.util.Date()) + msg;
        logBuf.append(line).append('\n');
        if (logBuf.length() > 30000) logBuf.delete(0, 6000);
        if (logOn) {
            try {
                File f = new File(root(), "log.txt");
                FileWriter w = new FileWriter(f, true);
                w.write(line + "\n");
                w.close();
            } catch (Throwable t) {}
        }
        if (logView != null) {
            final String tail = tailLines(12);
            act.runOnUiThread(new Runnable() {
                public void run() { if (logView != null) logView.setText(tail); }
            });
        }
    }

    static String tailLines(int n) {
        String[] all = logBuf.toString().split("\n");
        int st = Math.max(0, all.length - n);
        StringBuilder b = new StringBuilder();
        for (int i = st; i < all.length; i++) b.append(all[i]).append('\n');
        return b.toString();
    }

    static void toggleLogFile() {
        logOn = !logOn;
        try {
            File f = new File(root(), "log_on.txt");
            FileWriter w = new FileWriter(f);
            w.write(logOn ? "1" : "0"); w.close();
        } catch (Throwable t) {}
        jlog("日志记录 " + (logOn ? "开启" : "关闭"));
        toast(logOn ? "已开启日志记录\narcmod/log.txt" : "已关闭日志记录");
    }

    static void toggleLogWindow() {
        if (logView != null) {
            try { ((ViewGroup) logLayer.getParent()).removeView(logLayer); } catch (Throwable t) {}
            logView = null; logLayer = null;
            toast("已关闭实时日志");
            return;
        }
        View root = act.getWindow().getDecorView();
        logLayer = new FrameLayout(act);
        final TextView tv = new TextView(act);
        tv.setTextSize(9);
        tv.setTextColor(0xFFCFE8FF);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setPadding(8 * dpi, 6 * dpi, 8 * dpi, 6 * dpi);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC0A0A12);
        g.setCornerRadius(8 * dpi);
        g.setStroke((int) (1 * dpi), 0x66A98BD6);
        tv.setBackground(g);
        tv.setText(logBuf.length() == 0 ? "等待日志…\n（进行操作后会出现记录）" : tailLines(12));
        final int w = (int) (act.getResources().getDisplayMetrics().widthPixels * 0.52f);
        final int h = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.26f);
        final int[] pos = { (int) (10 * dpi), (int) (act.getResources().getDisplayMetrics().heightPixels * 0.30f) };
        tv.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX() - pos[0]; dy = e.getRawY() - pos[1]; return true;
                    case MotionEvent.ACTION_MOVE: {
                        pos[0] = (int) (e.getRawX() - dx); pos[1] = (int) (e.getRawY() - dy);
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tv.getLayoutParams();
                        lp.leftMargin = pos[0]; lp.topMargin = pos[1];
                        tv.setLayoutParams(lp);
                        return true;
                    }
                }
                return false;
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = pos[0]; lp.topMargin = pos[1];
        logLayer.addView(tv, lp);
        ((ViewGroup) root).addView(logLayer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        logView = tv;

    }

    static long[] songState() {
        try {
            File f = new File(root(), "song_state.txt");
            if (!f.exists()) return null;
            java.io.BufferedReader r = new java.io.BufferedReader(new FileReader(f));
            String[] p = r.readLine().trim().split(" ");
            r.close();
            long play = p.length > 3 ? Long.parseLong(p[3]) : 0;
            long dl = p.length > 4 ? Long.parseLong(p[4]) : 0;
            long chart = p.length > 5 ? Long.parseLong(p[5]) : 0;
            return new long[]{Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]), play, dl, chart};
        } catch (Throwable t) { return null; }
    }

    static long playTs() {
        long[] st = songState();
        if (st == null) return 0;
        return st.length > 3 ? st[3] : 0;
    }

    static boolean playSceneFresh(long base) {
        long ts = playTs();
        return ts != 0 && ts != base;
    }

    static boolean songPlaying() {
        long[] st = songState();
        if (st == null) return false;
        return st[2] > 0 && (st[1] - st[0]) < 400 && st[0] > 0;
    }

    static boolean readHide() {
        try {
            File f = assetFile("hide_mod.txt");
            if (!f.exists()) return false;
            java.io.BufferedReader r = new java.io.BufferedReader(new FileReader(f));
            String ln = r.readLine(); r.close();
            return "1".equals(ln.trim());
        } catch (Throwable t) { return false; }
    }

    static void writeHide(boolean v) {
        try {
            File f = assetFile("hide_mod.txt");
            f.getParentFile().mkdirs();
            FileWriter w = new FileWriter(f);
            w.write(v ? "1" : "0"); w.close();
        } catch (Throwable t) {}
    }

    static int uiColor = 0x7A5CA8;
    static int uiAccent = 0xB79CEB;
    static int uiBg = 0x1A1726;

    static void readWin() {
        try {
            File f = assetFile("win.txt");
            if (!f.exists()) return;
            java.io.BufferedReader r = new java.io.BufferedReader(new FileReader(f));
            String[] p = r.readLine().trim().split(",");
            r.close();
            uiSize = Integer.parseInt(p[0].trim());
            uiAlpha = Integer.parseInt(p[1].trim());
            if (p.length > 2) uiColor = (int) Long.parseLong(p[2].trim());
            if (p.length > 3) uiAccent = (int) Long.parseLong(p[3].trim());
            if (p.length > 4) uiBg = (int) Long.parseLong(p[4].trim());
        } catch (Throwable t) {}
    }

    static void writeWin(int size, int alpha, int color) {
        try {
            File f = assetFile("win.txt");
            f.getParentFile().mkdirs();
            FileWriter w = new FileWriter(f);
            w.write(size + "," + alpha + "," + color + "," + uiAccent + "," + uiBg); w.close();
        } catch (Throwable t) {}
    }

    static void applyWin() {
        try {
            if (modBtn != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) modBtn.getLayoutParams();
                lp.width = (int) (uiSize * dpi);
                lp.height = (int) (uiSize * 0.55f * dpi);
                modBtn.setLayoutParams(lp);
                modBtn.setAlpha(uiAlpha / 100f);
                GradientDrawable g = new GradientDrawable();
                int a = Math.round(255 * (uiAlpha / 100f) * 0.85f);
                g.setColor((a << 24) | (uiColor & 0x00FFFFFF));
                g.setCornerRadius(7 * dpi);
                modBtn.setBackground(g);
            }
        } catch (Throwable t) {}
    }

    static void uiSettings() {
        closeDlg();
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20 * dpi, 10 * dpi, 20 * dpi, 0);
        box.addView(label("按钮大小：" + uiSize));
        final SeekBar sb = new SeekBar(act);
        sb.setMax(60); sb.setProgress(uiSize - 20);
        box.addView(sb);
        box.addView(label("不透明度：" + uiAlpha + "%"));
        final SeekBar sa = new SeekBar(act);
        sa.setMax(90); sa.setProgress(uiAlpha - 10);
        box.addView(sa);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) { uiSize = v + 20; applyWin(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        sa.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) { uiAlpha = v + 10; applyWin(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(label("颜色"));
        final int[] palette = {0x7A5CA8, 0x3A6EA5, 0x2E8B57, 0xB85C5C, 0x8B5CB8, 0x4A4A55, 0xC8A24A, 0x2B2B33};
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < palette.length; i++) {
            final int col = palette[i];
            View sw = new View(act);
            GradientDrawable sg = new GradientDrawable();
            sg.setColor(0xFF000000 | col);
            sg.setCornerRadius(6 * dpi);
            sw.setBackground(sg);
            LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams((int) (34 * dpi), (int) (34 * dpi));
            sp2.setMargins((int) (3 * dpi), (int) (4 * dpi), (int) (3 * dpi), (int) (4 * dpi));
            row.addView(sw, sp2);
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { uiColor = col; applyWin(); }
            });
        }
        box.addView(row);
        box.addView(label("主题色（标题/按钮/描边）"));
        final int[] accents = {0xB79CEB, 0x7FC8F8, 0x7FD8B0, 0xF0A0A0, 0xE8C878, 0xC0C0D0};
        LinearLayout row2 = new LinearLayout(act);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < accents.length; i++) {
            final int col = accents[i];
            View sw = new View(act);
            GradientDrawable sg = new GradientDrawable();
            sg.setColor(0xFF000000 | col);
            sg.setCornerRadius(6 * dpi);
            sw.setBackground(sg);
            LinearLayout.LayoutParams sp3 = new LinearLayout.LayoutParams((int) (32 * dpi), (int) (32 * dpi));
            sp3.setMargins((int) (3 * dpi), (int) (4 * dpi), (int) (3 * dpi), (int) (4 * dpi));
            row2.addView(sw, sp3);
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { uiAccent = col; toast("已选主题色，保存后生效"); }
            });
        }
        box.addView(row2);
        box.addView(label("背景色"));
        final int[] bgs = {0x1A1726, 0x121722, 0x1B2419, 0x241818, 0x101014};
        LinearLayout row3 = new LinearLayout(act);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < bgs.length; i++) {
            final int col = bgs[i];
            View sw = new View(act);
            GradientDrawable sg = new GradientDrawable();
            sg.setColor(0xFF000000 | col);
            sg.setCornerRadius(6 * dpi);
            sw.setBackground(sg);
            LinearLayout.LayoutParams sp4 = new LinearLayout.LayoutParams((int) (32 * dpi), (int) (32 * dpi));
            sp4.setMargins((int) (3 * dpi), (int) (4 * dpi), (int) (3 * dpi), (int) (4 * dpi));
            row3.addView(sw, sp4);
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { uiBg = col; toast("已选背景色，保存后生效"); }
            });
        }
        box.addView(row3);
        showDarkCustom("MOD 悬浮窗外观", box, new String[]{"恢复默认", "返回", "保存"}, new DialogInterface.OnClickListener[]{
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    uiSize = 40; uiAlpha = 100; uiColor = 0x7A5CA8; uiAccent = 0xB79CEB; uiBg = 0x1A1726;
                    writeWin(uiSize, uiAlpha, uiColor); applyWin();
                    toast("已恢复默认");
                }
            },
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { closeDlg(); }
            },
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    writeWin(uiSize, uiAlpha, uiColor); applyWin(); closeDlg();
                }
            }
        });
    }

    static void toggleAudioWatch() {
        audioWatch = !audioWatch;
        if (audioWatch) {
            if (audioView == null) {
                audioView = new TextView(act);
                audioView.setTextSize(12);
                audioView.setTextColor(0xFFFFFFFF);
                audioView.setPadding(10 * dpi, 4 * dpi, 10 * dpi, 4 * dpi);
                GradientDrawable g = new GradientDrawable();
                g.setColor(0xCC000000);
                g.setCornerRadius(6 * dpi);
                audioView.setBackground(g);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.leftMargin = 10 * dpi; lp.topMargin = 10 * dpi;
                ((ViewGroup) act.getWindow().getDecorView()).addView(audioView, lp);
            }
            audioView.setVisibility(View.VISIBLE);
            final android.media.AudioManager am = (android.media.AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            new Thread(new Runnable() {
                public void run() {
                    while (audioWatch) {
                        final boolean on = am.isMusicActive();
                        act.runOnUiThread(new Runnable() {
                            public void run() {
                                if (audioView != null) audioView.setText("音乐：" + (on ? "播放中" : "静音"));
                            }
                        });
                        try { Thread.sleep(200); } catch (Exception e) {}
                    }
                }
            }).start();
            toast("音频监测已开启");
        } else {
            if (audioView != null) audioView.setVisibility(View.GONE);
            toast("音频监测已关闭");
        }
    }

    static void applyHide() {
        hideMode = readHide();
        if (modBtn != null) modBtn.setVisibility(hideMode ? View.GONE : View.VISIBLE);
        if (keyGrab != null) {
            if (hideMode) keyGrab.requestFocus();
            else keyGrab.clearFocus();
        }
    }

    static void closeDlg() {
        try { if (dlgRef[0] != null) { dlgRef[0].dismiss(); dlgRef[0] = null; } } catch (Throwable t) {}
    }

    static void clearFloat() {
        try {
            if (curFloat != null && curFloat.getParent() instanceof ViewGroup) {
                ((ViewGroup) curFloat.getParent()).removeView(curFloat);
            }
        } catch (Throwable t) {}
        curFloat = null;
    }

    static AlertDialog showDarkCustom(String title, View content, String[] labels, DialogInterface.OnClickListener[] cbs) {
        closeDlg();
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20 * dpi, 16 * dpi, 20 * dpi, 12 * dpi);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((0xE6 << 24) | (uiBg & 0x00FFFFFF));
        bg.setCornerRadius(18 * dpi);
        bg.setStroke((int) (1 * dpi), (0x66 << 24) | (uiAccent & 0x00FFFFFF));
        box.setBackground(bg);
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextColor(0xFFF2E9FF);
        t.setTextSize(16);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(4 * dpi, 0, 0, 8 * dpi);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView x = new TextView(act);
        x.setText("✕");
        x.setTextColor(0xFFB9A6E0);
        x.setTextSize(15);
        x.setPadding(10 * dpi, 0, 4 * dpi, 8 * dpi);
        x.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeDlg(); }
        });
        head.addView(x);
        box.addView(head);
        if (content != null) {
            ScrollView sv = new ScrollView(act);
            int maxH = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.62f);
            sv.addView(content);
            box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH));
        }
        if (labels != null) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END);
            row.setPadding(0, 8 * dpi, 0, 0);
            for (int i = 0; i < labels.length; i++) {
                final DialogInterface.OnClickListener cb = cbs[i];
                TextView btn = new TextView(act);
                btn.setText(labels[i]);
                btn.setTextColor(0xFF000000 | (uiAccent & 0x00FFFFFF));
                btn.setTextSize(14);
                btn.setPadding(14 * dpi, 8 * dpi, 6 * dpi, 8 * dpi);
                btn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { if (cb != null) cb.onClick(null, 0); }
                });
                row.addView(btn);
            }
            box.addView(row);
        }
        AlertDialog d = new AlertDialog.Builder(act).setView(box).create();
        d.show();
        dlgRef[0] = d;
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            int wpx = (int) (act.getResources().getDisplayMetrics().widthPixels * 0.86f);
            d.getWindow().setLayout(wpx, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }

    static void showDark(String title, final String[] items, final DialogInterface.OnClickListener pick, final boolean withBack) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20 * dpi, 16 * dpi, 20 * dpi, 10 * dpi);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((0xE6 << 24) | (uiBg & 0x00FFFFFF));
        bg.setCornerRadius(18 * dpi);
        bg.setStroke((int) (1 * dpi), (0x66 << 24) | (uiAccent & 0x00FFFFFF));
        box.setBackground(bg);
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextColor(0xFFF2E9FF);
        t.setTextSize(17);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(4 * dpi, 0, 0, 10 * dpi);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(t, tp);
        {
            TextView x = new TextView(act);
            x.setText("✕");
            x.setTextColor(0xFFB9A6E0);
            x.setTextSize(16);
            x.setPadding(12 * dpi, 0, 4 * dpi, 10 * dpi);
            x.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    closeDlg();
                    if (withBack) modMenu();
                }
            });
            head.addView(x);
        }
        box.addView(head);
        ScrollView sv = new ScrollView(act);
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView row = new TextView(act);
            row.setText(items[i]);
            row.setTextColor(0xFFE8DFF7);
            row.setTextSize(15);
            row.setPadding(10 * dpi, 12 * dpi, 10 * dpi, 12 * dpi);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { pick.onClick(null, idx); }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    if (listLongPress != null) { listLongPress.onClick(null, idx); return true; }
                    return false;
                }
            });
            list.addView(row);
            View div = new View(act);
            div.setBackgroundColor(0x22FFFFFF);
            list.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        sv.addView(list);
        int maxH2 = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.62f);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH2));
        closeDlg();
        final AlertDialog d = new AlertDialog.Builder(act).setView(box).create();
        d.show();
        dlgRef[0] = d;
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            int wpx = (int) (act.getResources().getDisplayMetrics().widthPixels * 0.86f);
            d.getWindow().setLayout(wpx, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    static void modMenu() {
        jlog("打开 ArcMOD 菜单");
        final String[] items = {
                "1  搭档修改",
                "2  自动打歌（beta）",
                "3  停止回放",
                "4  MOD 悬浮窗外观 / 窗口颜色",
                "5  " + (hideMode ? "显示 MOD 按钮" : "隐藏 MOD 按钮（音量加/减唤起）"),
                "6  回放记录",
                "7  导出录制备份",
                "8  导入录制备份",
                "9  帮助页",
                "10  " + (logOn ? "关闭日志记录" : "开启收集日志"),
                "11  " + (logView != null ? "关闭实时日志" : "开启实时日志"),
                "12  日志管理"
        };
        showDark("ArcMOD（Beta）", items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 0) pickPartner();
                else if (w == 1) autoMenu();
                else if (w == 2) {
                    autoRunning = false; replayGen++; cancelAllPointers();
                    toast("已停止回放");
                }
                else if (w == 3) uiSettings();
                else if (w == 4) {
                    writeHide(!hideMode);
                    applyHide();
                    toast(hideMode ? "已隐藏，按音量键唤出" : "已恢复按钮");
                }
                else if (w == 5) showResults();
                else if (w == 6) exportRecordings();
                else if (w == 7) importRecordings();
                else if (w == 8) showHelp();
                else if (w == 9) toggleLogFile();
                else if (w == 10) toggleLogWindow();
                else showLogs();
            }
        }, false);
    }

    static void autoMenu() {
        final String[] items = {"1  录制自动打歌", "2  回放谱面录制"};
        showDark("自动打歌（beta）", items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                closeDlg();
                if (w == 0) toggleRecord();
                else replayMenu();
            }
        }, true);
    }

    static void showHelp() {
        String txt =
            "搭档修改\n替换立绘 / icon、改名、立绘预设 A/B\n" +
            "注意：部分搭档无法改名（翻译表里没有对应条目）\n\n" +
            "自动打歌（beta）\n① 录制自动打歌：点开后出现 ● 浮窗，直接进谱面就会自动开始录制（不用点 ●）\n" +
            "打完点 ■ 结束并命名保存\n" +
            "② 回放谱面录制：选好录制后出现 ▶ 浮窗，直接进谱面就会自动回放（不用点 ▶）\n" +
            "● / ▶ 都只是备用，卡住时可手动点一下强制开始\n\n" +
            "⚠ 自动谱面回放不稳定\n录制完成后请先重启游戏，再播放回放。\n" +
            "若回放不正常、有延迟或错位，请重新录制。\n\n" +
            "停止回放\n随时停止（也可用 MOD 菜单）\n\n" +
            "外观\n大小、透明度、按钮颜色、弹窗主题色/背景色\n\n" +
            "隐藏 MOD 按钮\n隐藏后按音量加/减唤出菜单\n\n" +
            "回放记录 / 备份\n每次回放自动记一条；录制可导出导入 zip\n\n" +
            "日志\n收集日志写入 arcmod/log.txt；实时日志为屏幕浮窗";
        TextView tv = label(txt);
        tv.setTextSize(11);
        showDarkCustom("帮助", tv, new String[]{"关闭"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { closeDlg(); }
                        }
                });
    }

    static int logSort = 0;

    static String fmtSize(long n) {
        if (n > 1024 * 1024) return String.format("%.1f MB", n / 1048576.0);
        if (n > 1024) return (n / 1024) + " KB";
        return n + " B";
    }

    static void showLogs() {
        final File dir = root();
        File[] fs = dir.listFiles();
        final java.util.List<File> logs = new java.util.ArrayList<File>();
        if (fs != null) for (File f : fs) {
            String n = f.getName();
            if (f.isFile() && (n.endsWith(".log") || n.equals("log.txt") || n.equals("ui.log"))) logs.add(f);
        }
        if (logSort == 0) {
            java.util.Collections.sort(logs, new java.util.Comparator<File>() {
                public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
            });
        } else {
            java.util.Collections.sort(logs, new java.util.Comparator<File>() {
                public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
            });
        }
        if (logs.isEmpty()) { toast("没有日志文件"); return; }
        final boolean[] sel = new boolean[logs.size()];
        final java.util.List<CheckBox> cbList = new java.util.ArrayList<CheckBox>();
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView title = label("排序：" + (logSort == 0 ? "按名称" : "按时间") + "（点击切换）");
        title.setTextColor(0xFFB79CEB);
        box.addView(title);
        for (int i = 0; i < logs.size(); i++) {
            final File f = logs.get(i);
            final int idx = i;
            final CheckBox cb = new CheckBox(act);
            cb.setText(f.getName() + "    " + fmtSize(f.length()));
            cb.setTextColor(0xFFE8DFF7);
            cb.setTextSize(12);
            cb.setChecked(false);
            cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(android.widget.CompoundButton b, boolean v) { sel[idx] = v; }
            });
            cb.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    closeDlg();
                    showDarkCustom(f.getName() + "  " + fmtSize(f.length()), label("选择操作"), new String[]{"改名", "删除", "返回"},
                            new DialogInterface.OnClickListener[]{
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface d, int w) { renameLog(f); }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface d, int w) { f.delete(); closeDlg(); showLogs(); }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface d, int w) { closeDlg(); showLogs(); }
                                    }
                            });
                    return true;
                }
            });
            cbList.add(cb);
            box.addView(cb);
        }
        title.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logSort = 1 - logSort;
                closeDlg();
                showLogs();
            }
        });
        showDarkCustom("日志管理（多选后可清理，长按改名/删除）", box,
                new String[]{"返回", "全选", "清理选中"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { closeDlg(); }
                        },
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                boolean all = true;
                                for (boolean b : sel) if (!b) { all = false; break; }
                                for (int i = 0; i < cbList.size(); i++) cbList.get(i).setChecked(!all);
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                int cnt = 0;
                                for (int i = 0; i < sel.length; i++) if (sel[i]) { logs.get(i).delete(); cnt++; }
                                closeDlg();
                                toast(cnt == 0 ? "没有选中文件" : ("已清理 " + cnt + " 个日志"));
                                if (cnt > 0) showLogs();
                            }
                        }
                });
    }



    static void renameLog(final File f) {
        closeDlg();
        final EditText e = edit();
        e.setText(f.getName());
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(4 * dpi, 0, 4 * dpi, 0);
        box.addView(label("新文件名"));
        box.addView(e);
        showDarkCustom("重命名日志", box, new String[]{"取消", "确定"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { closeDlg(); showLogs(); }
                        },
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                String nm = e.getText().toString().trim();
                                if (nm.length() > 0) f.renameTo(new File(root(), nm));
                                closeDlg(); showLogs();
                            }
                        }
                });
    }

    static void showDisclaimer() {
        TextView tv = label(
                "1. 版权归属：本程序仅供逆向工程学习与技术探讨，所涉及的原游戏版权均归属原游戏制作与发行公司所有。\n\n" +
                "2. 免责条款：本程序完全免费共享，严禁用于任何商业倒卖、代练牟利或非法用途。因传播、违法使用等一切行为所引发的一切后果均由使用者全部承担，开发者概不负责。\n\n" +
                "3. 清理义务：分流与注入资源仅供临时本地测试运行，请您在下载试用后的 24 小时内彻底删除。如果您喜欢该游戏内容，请支持正版并遵守官方协议。\n\n" +
                "4. 维权处理：若本程序相关内容侵犯了您的合法权益，请权利人携带相关证明与开发者联系，核实后我们将立刻停止维护并彻底销毁资源。\n\n" +
                "联系邮箱：ykm956km@gmail.com");
        tv.setTextSize(10);
        showDarkCustom("ArcMOD（Beta）声明", tv, new String[]{"不再提示", "我已阅读并完全同意以上声明"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    FileWriter w2 = new FileWriter(new File(root(), "agreed.txt"));
                                    w2.write("1"); w2.close();
                                } catch (Throwable t) {}
                                closeDlg();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { closeDlg(); }
                        }
                });
    }

    // ==== restored: toggleRecord ====
    static void toggleRecord() {
        if (recording) {
            Overlay.stopRecording();
            return;
        }
        Overlay.showRecButton();
    }

    // ==== restored: showRecButton ====
    static void showRecButton() {
        Overlay.closeDlg();
        Overlay.clearFloat();
        View view = act.getWindow().getDecorView();
        final FrameLayout frameLayout = new FrameLayout((Context)act);
        final TextView textView = new TextView((Context)act);
        textView.setText((CharSequence)"\u25cf");
        textView.setTextSize(20.0f);
        textView.setGravity(17);
        textView.setIncludeFontPadding(false);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-858634945);
        gradientDrawable.setCornerRadius((float)(26 * dpi));
        textView.setBackground((Drawable)gradientDrawable);
        textView.setTextColor(-1);
        int n = 52 * dpi;
        final int[] nArray = new int[]{Overlay.act.getResources().getDisplayMetrics().widthPixels - n - 14 * dpi, (int)((float)Overlay.act.getResources().getDisplayMetrics().heightPixels * 0.12f)};
        final boolean[] blArray = new boolean[]{false};
        textView.setOnTouchListener(new View.OnTouchListener(){
            float dx;
            float dy;
            boolean moved;

            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getActionMasked()) {
                    case 0: {
                        this.dx = motionEvent.getRawX() - (float)nArray[0];
                        this.dy = motionEvent.getRawY() - (float)nArray[1];
                        this.moved = false;
                        return true;
                    }
                    case 2: {
                        float f = motionEvent.getRawX() - this.dx;
                        float f2 = motionEvent.getRawY() - this.dy;
                        if (Math.abs(f - (float)nArray[0]) > 4.0f || Math.abs(f2 - (float)nArray[1]) > 4.0f) {
                            this.moved = true;
                        }
                        nArray[0] = (int)f;
                        nArray[1] = (int)f2;
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)textView.getLayoutParams();
                        layoutParams.leftMargin = nArray[0];
                        layoutParams.topMargin = nArray[1];
                        textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
                        return true;
                    }
                    case 1: {
                        if (!this.moved) {
                            if (!blArray[0]) {
                                Overlay.startRecordingNow();
                                blArray[0] = true;
                                textView.setText((CharSequence)"\u25a0");
                                Overlay.toast("\u5f55\u5236\u4e2d\u2026\u6253\u5b8c\u70b9 \u25a0 \u7ed3\u675f");
                            } else {
                                Overlay.clearFloat();
                                Overlay.stopRecording();
                            }
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(n, n);
        layoutParams.leftMargin = nArray[0];
        layoutParams.topMargin = nArray[1];
        frameLayout.addView((View)textView, (ViewGroup.LayoutParams)layoutParams);
        ((ViewGroup)view).addView((View)frameLayout, new ViewGroup.LayoutParams(-1, -1));
        curFloat = frameLayout;
        final long l = Overlay.playTs();
        Overlay.jlog("REC armed base=" + l);
        new Thread(new Runnable(){

            @Override
            public void run() {
                while (curFloat == frameLayout && !recording) {
                    if (Overlay.playSceneFresh(l)) {
                        Overlay.jlog("REC detect scene");
                        act.runOnUiThread(new Runnable(){

                            @Override
                            public void run() {
                                if (recording) {
                                    return;
                                }
                                Overlay.startRecordingNow();
                                blArray[0] = true;
                                textView.setText((CharSequence)"\u25a0");
                                Overlay.toast("\u68c0\u6d4b\u5230\u8c31\u9762\u5f00\u59cb\uff0c\u81ea\u52a8\u5f55\u5236\u4e2d\u2026");
                            }
                        });
                        return;
                    }
                    try {
                        Thread.sleep(100L);
                    }
                    catch (Exception exception) {}
                }
            }
        }).start();
        Overlay.toast("\u8fdb\u5165\u8c31\u9762\u81ea\u52a8\u5f00\u59cb\u5f55\u5236\uff08\u4e5f\u53ef\u624b\u52a8\u70b9 \u25cf\uff09");
    }

    // ==== restored: startRecordingNow ====
    static void startRecordingNow() {
        Overlay.jlog("REC start");
        View view = act.getWindow().getDecorView();
        View view2 = Overlay.findSurface(view);
        if (view2 == null) {
            Overlay.toast("\u627e\u4e0d\u5230\u6e38\u620f\u753b\u9762");
            return;
        }
        recTarget = view2;
        rec.setLength(0);
        recStart = SystemClock.uptimeMillis();
        recording = true;
        view2.setOnTouchListener(new View.OnTouchListener(){

            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (!recording) {
                    return false;
                }
                long l = SystemClock.uptimeMillis() - recStart;
                rec.append(l).append(';').append(motionEvent.getAction());
                int n = motionEvent.getPointerCount();
                for (int i = 0; i < n; ++i) {
                    rec.append(';').append(motionEvent.getPointerId(i)).append(',').append(Math.round(motionEvent.getX(i))).append(',').append(Math.round(motionEvent.getY(i)));
                }
                rec.append('\n');
                return false;
            }
        });
    }

    // ==== restored: stopRecording ====
    static void stopRecording() {
        recording = false;
        long maxGap = 0; long prevT = -1;
        for (String ln : rec.toString().split("\n")) {
            int sc = ln.indexOf(';');
            if (sc <= 0) continue;
            try {
                long t = Long.parseLong(ln.substring(0, sc));
                if (prevT >= 0 && t - prevT > maxGap) maxGap = t - prevT;
                prevT = t;
            } catch (Throwable t) {}
        }
        if (maxGap > 500) toast("⚠ 检测到 " + maxGap + "ms 中断，本次录制可能异常，建议重启游戏后重录");
        Overlay.jlog("REC stop events=" + (rec.length() > 0 ? rec.toString().split("\n").length : 0));
        final String string = rec.toString();
        rec.setLength(0);
        final EditText editText = Overlay.edit();
        editText.setText((CharSequence)("rec_" + System.currentTimeMillis() % 100000L));
        LinearLayout linearLayout = new LinearLayout((Context)act);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(4 * dpi, 0, 4 * dpi, 0);
        linearLayout.addView((View)Overlay.label("\u4e8b\u4ef6\u6570\uff1a" + (string.length() > 0 ? string.split("\n").length : 0)));
        linearLayout.addView((View)editText);
        Overlay.showDarkCustom("\u4fdd\u5b58\u5f55\u5236", (View)linearLayout, new String[]{"\u4e22\u5f03", "\u4fdd\u5b58"}, new DialogInterface.OnClickListener[]{new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialogInterface, int n) {
                Overlay.closeDlg();
            }
        }, new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialogInterface, int n) {
                try {
                    File file = Overlay.recDir();
                    file.mkdirs();
                    String string2 = editText.getText().toString().trim();
                    if (string2.length() == 0) {
                        string2 = "rec";
                    }
                    File file2 = new File(file, string2 + ".txt");
                    FileWriter fileWriter = new FileWriter(file2);
                    fileWriter.write(string);
                    fileWriter.close();
                    Overlay.toast("\u5df2\u4fdd\u5b58\uff1a" + file2.getName());
                }
                catch (Throwable throwable) {
                    Overlay.toast("\u4fdd\u5b58\u5931\u8d25: " + throwable);
                }
            }
        }});
    }

    // ==== restored: findSurface ====
    static View findSurface(View view) {
        if (view instanceof SurfaceView) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup)view;
            for (int i = 0; i < viewGroup.getChildCount(); ++i) {
                View view2 = Overlay.findSurface(viewGroup.getChildAt(i));
                if (view2 == null) continue;
                return view2;
            }
        }
        return null;
    }

    // ==== restored: exportRecordings ====
    static void exportRecordings() {
        try {
            File file = Overlay.recDir();
            File[] fileArray = file.listFiles();
            if (fileArray == null || fileArray.length == 0) {
                Overlay.toast("\u6ca1\u6709\u53ef\u5bfc\u51fa\u7684\u5f55\u5236");
                return;
            }
            File file2 = new File(Overlay.root(), "exports");
            file2.mkdirs();
            File file3 = new File(file2, "rec_" + System.currentTimeMillis() / 1000L + ".zip");
            ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(file3));
            for (File file4 : fileArray) {
                int n;
                if (!file4.getName().endsWith(".txt")) continue;
                zipOutputStream.putNextEntry(new ZipEntry(file4.getName()));
                FileInputStream fileInputStream = new FileInputStream(file4);
                byte[] byArray = new byte[8192];
                while ((n = fileInputStream.read(byArray)) > 0) {
                    zipOutputStream.write(byArray, 0, n);
                }
                fileInputStream.close();
                zipOutputStream.closeEntry();
            }
            zipOutputStream.close();
            Overlay.toast("\u5df2\u5bfc\u51fa\uff1a" + file3.getName() + "\n\u8def\u5f84 arcmod/exports/");
        }
        catch (Throwable throwable) {
            Overlay.toast("\u5bfc\u51fa\u5931\u8d25: " + throwable);
        }
    }

    // ==== restored: importRecordings ====
    static void importRecordings() {
        final File file = new File(Overlay.root(), "exports");
        File[] fileArray = file.listFiles();
        ArrayList<String> arrayList = new ArrayList<String>();
        if (fileArray != null) {
            for (File file2 : fileArray) {
                if (!file2.getName().endsWith(".zip")) continue;
                arrayList.add(file2.getName());
            }
        }
        Collections.sort(arrayList);
        if (arrayList.isEmpty()) {
            Overlay.toast("exports \u76ee\u5f55\u4e0b\u6ca1\u6709\u5907\u4efd");
            return;
        }
        final String[] items = (String[]) arrayList.toArray(new String[0]);
        showDark("选择要导入的备份", items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int n) {
                try {
                    ZipEntry zipEntry;
                    File file3 = new File(file, items[n]);
                    ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(file3));
                    Overlay.recDir().mkdirs();
                    int n2 = 0;
                    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                        int n3;
                        if (zipEntry.isDirectory()) continue;
                        File file2 = new File(Overlay.recDir(), zipEntry.getName());
                        FileOutputStream fileOutputStream = new FileOutputStream(file2);
                        byte[] byArray = new byte[8192];
                        while ((n3 = zipInputStream.read(byArray)) > 0) {
                            fileOutputStream.write(byArray, 0, n3);
                        }
                        fileOutputStream.close();
                        ++n2;
                    }
                    zipInputStream.close();
                    Overlay.toast("\u5df2\u5bfc\u5165 " + n2 + " \u4e2a\u5f55\u5236");
                }
                catch (Throwable throwable) {
                    Overlay.toast("\u5bfc\u5165\u5931\u8d25: " + throwable);
                }
            }
        }, true);
    }

    // ==== restored: replayMenu ====


    // ==== restored: fileOptions ====
    static void replayMenu() {
        File dir = recDir();
        File[] fs = dir.listFiles();
        java.util.List<String> raw = new java.util.ArrayList<String>();
        java.util.List<String> disp = new java.util.ArrayList<String>();
        if (fs != null) {
            java.util.List<File> list = new java.util.ArrayList<File>();
            for (File f : fs) if (f.getName().endsWith(".txt")) list.add(f);
            if (recordSort == 1) {
                java.util.Collections.sort(list, new java.util.Comparator<File>() {
                    public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
                });
            } else {
                java.util.Collections.sort(list, new java.util.Comparator<File>() {
                    public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
                });
            }
            for (File f : list) {
                String nm = f.getName().replace(".txt", "");
                int lines = 0; long dur = 0;
                try {
                    java.io.BufferedReader r = new java.io.BufferedReader(new FileReader(f));
                    String ln;
                    while ((ln = r.readLine()) != null) {
                        lines++;
                        int sc = ln.indexOf(';');
                        if (sc > 0) { try { dur = Long.parseLong(ln.substring(0, sc)); } catch (Throwable t) {} }
                    }
                    r.close();
                } catch (Throwable t) {}
                raw.add(nm);
                disp.add(nm + "    " + lines + " 条 / " + (dur / 1000) + " 秒");
            }
        }
        if (raw.isEmpty()) {
            File legacy = assetFile("record.txt");
            if (legacy.exists()) { raw.add("record"); disp.add("record"); }
        }
        if (raw.isEmpty()) { toast("还没有录制文件"); return; }
        java.util.List<String> show = new java.util.ArrayList<String>();
        show.add("排序：" + (recordSort == 0 ? "按名称" : "按时间") + "（点此切换）");
        show.addAll(disp);
        final String[] names = raw.toArray(new String[0]);
        final String[] items = show.toArray(new String[0]);
        listLongPress = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 0) { recordSort = 1 - recordSort; closeDlg(); listLongPress = null; replayMenu(); return; }
                closeDlg(); listLongPress = null;
                fileOptions(names[w - 1]);
            }
        };
        showDark("选择回放录制（长按可改名/删除）", items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 0) { recordSort = 1 - recordSort; closeDlg(); listLongPress = null; replayMenu(); return; }
                closeDlg(); listLongPress = null;
                showPlayButton(names[w - 1]);
            }
        }, true);
    }

    static int recordSort = 0;

    static void fileOptions(final String string) {
        String[] stringArray = new String[]{"\u91cd\u547d\u540d", "\u5220\u9664", "\u53d6\u6d88"};
        Overlay.showDark(string, stringArray, new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialogInterface, int n) {
                if (n == 0) {
                    Overlay.renameRec(string);
                } else if (n == 1) {
                    Overlay.deleteRec(string);
                } else {
                    Overlay.replayMenu();
                }
            }
        }, true);
    }

    // ==== restored: renameRec ====
    static void renameRec(final String string) {
        Overlay.closeDlg();
        final EditText editText = Overlay.edit();
        editText.setText((CharSequence)string);
        LinearLayout linearLayout = new LinearLayout((Context)act);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(20 * dpi, 8 * dpi, 20 * dpi, 0);
        linearLayout.addView((View)Overlay.label("\u65b0\u7684\u540d\u5b57"));
        linearLayout.addView((View)editText);
        Overlay.showDarkCustom("\u91cd\u547d\u540d", (View)linearLayout, new String[]{"\u53d6\u6d88", "\u786e\u5b9a"}, new DialogInterface.OnClickListener[]{new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialogInterface, int n) {
                Overlay.closeDlg();
                Overlay.replayMenu();
            }
        }, new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialogInterface, int n) {
                try {
                    String string2 = editText.getText().toString().trim();
                    if (string2.length() > 0) {
                        File file = new File(Overlay.recDir(), string + ".txt");
                        File file2 = new File(Overlay.recDir(), string2 + ".txt");
                        if (file.exists()) {
                            file.renameTo(file2);
                        }
                        Overlay.toast("\u5df2\u91cd\u547d\u540d\u4e3a " + string2);
                    }
                    Overlay.closeDlg();
                    Overlay.replayMenu();
                }
                catch (Throwable throwable) {
                    Overlay.toast("\u5931\u8d25: " + throwable);
                }
            }
        }});
    }

    // ==== restored: deleteRec ====
    static void deleteRec(final String string) {
        Overlay.closeDlg();
        TextView textView = Overlay.label("\u786e\u5b9a\u5220\u9664\u300c" + string + "\u300d\uff1f");
        Overlay.showDarkCustom("\u5220\u9664\u5f55\u5236", (View)textView, new String[]{"\u53d6\u6d88", "\u5220\u9664"}, new DialogInterface.OnClickListener[]{new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialogInterface, int n) {
                Overlay.closeDlg();
                Overlay.replayMenu();
            }
        }, new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialogInterface, int n) {
                try {
                    File file = new File(Overlay.recDir(), string + ".txt");
                    if (file.exists()) {
                        file.delete();
                    }
                    Overlay.toast("\u5df2\u5220\u9664");
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                Overlay.closeDlg();
                Overlay.replayMenu();
            }
        }});
    }

    // ==== restored: showPlayButton ====
    static void showPlayButton(String string) {
        List<Object[]> list;
        curRecName = string;
        try {
            list = Overlay.loadEvents(string);
        }
        catch (Throwable throwable) {
            Overlay.toast("\u8bfb\u53d6\u5931\u8d25: " + throwable);
            return;
        }
        if (list.isEmpty()) {
            Overlay.toast("\u5f55\u5236\u4e3a\u7a7a");
            return;
        }
        final List<Object[]> list2 = list;
        Overlay.closeDlg();
        Overlay.clearFloat();
        final View view = act.getWindow().getDecorView();
        final FrameLayout frameLayout = new FrameLayout((Context)act);
        final TextView textView = new TextView((Context)act);
        textView.setText((CharSequence)"\u25b6");
        textView.setTextSize(18.0f);
        textView.setGravity(17);
        textView.setIncludeFontPadding(false);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-865251416);
        gradientDrawable.setCornerRadius((float)(26 * dpi));
        textView.setBackground((Drawable)gradientDrawable);
        textView.setTextColor(-1);
        int n = 52 * dpi;
        final int[] nArray = new int[]{Overlay.act.getResources().getDisplayMetrics().widthPixels - n - 14 * dpi, (int)((float)Overlay.act.getResources().getDisplayMetrics().heightPixels * 0.12f)};
        textView.setOnTouchListener(new View.OnTouchListener(){
            float dx;
            float dy;
            float sx;
            float sy;
            long td;

            public boolean onTouch(View view2, MotionEvent motionEvent) {
                switch (motionEvent.getActionMasked()) {
                    case 0: {
                        this.dx = motionEvent.getRawX() - (float)nArray[0];
                        this.dy = motionEvent.getRawY() - (float)nArray[1];
                        this.sx = motionEvent.getRawX();
                        this.sy = motionEvent.getRawY();
                        this.td = System.currentTimeMillis();
                        return true;
                    }
                    case 2: {
                        if (Math.abs(motionEvent.getRawX() - this.sx) > (float)(18 * dpi) || Math.abs(motionEvent.getRawY() - this.sy) > (float)(18 * dpi)) {
                            float f = motionEvent.getRawX() - this.dx;
                            float f2 = motionEvent.getRawY() - this.dy;
                            nArray[0] = (int)f;
                            nArray[1] = (int)f2;
                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)textView.getLayoutParams();
                            layoutParams.leftMargin = nArray[0];
                            layoutParams.topMargin = nArray[1];
                            textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
                        }
                        return true;
                    }
                    case 1: {
                        if (Math.abs(motionEvent.getRawX() - this.sx) < (float)(18 * dpi) && Math.abs(motionEvent.getRawY() - this.sy) < (float)(18 * dpi) && System.currentTimeMillis() - this.td < 600L) {
                            if (autoRunning) {
                                Overlay.toast("\u6b63\u5728\u56de\u653e\u4e2d");
                                return true;
                            }
                            Overlay.clearFloat();
                            autoRunning = true;
                            int n = ++replayGen;
                            Overlay.jlog("PLAY manual click");
                            Overlay.toast("\u5f00\u59cb\u56de\u653e");
                            Overlay.scheduleReplay(list2, new HashSet<Integer>(), SystemClock.uptimeMillis(), n, view);
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(n, n);
        layoutParams.leftMargin = nArray[0];
        layoutParams.topMargin = nArray[1];
        frameLayout.addView((View)textView, (ViewGroup.LayoutParams)layoutParams);
        ((ViewGroup)view).addView((View)frameLayout, new ViewGroup.LayoutParams(-1, -1));
        curFloat = frameLayout;
        autoRunning = false;
        final long l = Overlay.playTs();
        Overlay.jlog("PLAY armed base=" + l);
        new Thread(new Runnable(){

            @Override
            public void run() {
                while (curFloat == frameLayout) {
                    if (Overlay.playSceneFresh(l)) {
                        Overlay.jlog("PLAY detect scene");
                        act.runOnUiThread(new Runnable(){

                            @Override
                            public void run() {
                                Overlay.jlog("PLAY trigger autoRunning=" + autoRunning);
                                if (curFloat != frameLayout || autoRunning) {
                                    return;
                                }
                                Overlay.clearFloat();
                                autoRunning = true;
                                int n = ++replayGen;
                                Overlay.toast("\u68c0\u6d4b\u5230\u8c31\u9762\u5f00\u59cb\uff0c\u81ea\u52a8\u56de\u653e\u4e2d\u2026");
                                Overlay.scheduleReplay(list2, new HashSet<Integer>(), SystemClock.uptimeMillis(), n, view);
                            }
                        });
                        return;
                    }
                    try {
                        Thread.sleep(100L);
                    }
                    catch (Exception exception) {}
                }
            }
        }).start();
    }

    // ==== restored: loadEvents ====
    static List<Object[]> loadEvents(String string) throws Exception {
        String string2;
        File file = new File(Overlay.recDir(), string + ".txt");
        if (!file.exists()) {
            file = Overlay.assetFile("record.txt");
        }
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        ArrayList<Object[]> arrayList = new ArrayList<Object[]>();
        while ((string2 = bufferedReader.readLine()) != null) {
            String[] stringArray = string2.split(";");
            if (stringArray.length < 2) continue;
            long l = Long.parseLong(stringArray[0]);
            int n = Integer.parseInt(stringArray[1]);
            int n2 = stringArray.length - 2;
            int[] nArray = new int[n2];
            float[] fArray = new float[n2];
            float[] fArray2 = new float[n2];
            for (int i = 0; i < n2; ++i) {
                String[] stringArray2 = stringArray[i + 2].split(",");
                nArray[i] = Integer.parseInt(stringArray2[0]);
                fArray[i] = Float.parseFloat(stringArray2[1]);
                fArray2[i] = Float.parseFloat(stringArray2[2]);
            }
            arrayList.add(new Object[]{l, n, nArray, fArray, fArray2});
        }
        bufferedReader.close();
        return arrayList;
    }

    // ==== restored: scheduleReplay ====
    static void scheduleReplay(final List<Object[]> list, final Set<Integer> set, final long l, final int n, final View view) {
        Overlay.jlog("SCHEDULE events=" + list.size() + " gen=" + n + " running=" + autoRunning);
        new Thread(new Runnable(){

            @Override
            public void run() {
                int n2 = 0;
                while (autoRunning && n == replayGen) {
                    try {
                        Thread.sleep(1000L);
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                    Overlay.jlog("REPLAY t=" + ++n2 + "s ptr=" + downPtrs.size());
                }
                Overlay.jlog("REPLAY end t=" + n2 + "s");
                Overlay.addResult(curRecName, list.size(), n2);
            }
        }).start();
        if (!autoRunning) {
            return;
        }
        final int n2 = n;
        new Thread(new Runnable(){

            @Override
            public void run() {
                while (autoRunning && n2 == replayGen) {
                    try {
                        Thread.sleep(200L);
                    }
                    catch (Exception exception) {}
                }
            }
        }).start();
        final Handler handler = new Handler(Looper.getMainLooper());
        final long[] lArray = new long[]{0L};
        new Thread(new Runnable(){

            @Override
            public void run() {
                for (int i = 0; i < list.size(); ++i) {
                    int n7;
                    if (!autoRunning || n != replayGen) {
                        return;
                    }
                    if (set.contains(i)) continue;
                    long l4 = (Long)((Object[])list.get(i))[0];
                    long l2 = l + l4;
                    long l3 = SystemClock.uptimeMillis();
                    while (l3 < l2) {
                        if (!autoRunning || n != replayGen) {
                            return;
                        }
                        try {
                            Thread.sleep(Math.min(5L, l2 - l3));
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                        l3 = SystemClock.uptimeMillis();
                    }
                    int n2 = (Integer)((Object[])list.get(i))[1];
                    final int[] nArray = (int[])((Object[])list.get(i))[2];
                    final float[] fArray = (float[])((Object[])list.get(i))[3];
                    final float[] fArray2 = (float[])((Object[])list.get(i))[4];
                    if (nArray.length == 0) continue;
                    int n3 = n2;
                    int n4 = n3 & 0xFF;
                    if ((n4 == 5 || n4 == 6) && (n7 = n3 >> 8 & 0xFF) >= nArray.length) {
                        n3 = n4 == 6 ? 1 : 0;
                    }
                    n7 = n3;
                    final int actFinal = n7;
                    try {
                        int n5;
                        int n6 = n7 & 0xFF;
                        if (n6 == 0) {
                            downPtrs.clear();
                            for (n5 = 0; n5 < nArray.length; ++n5) {
                                downPtrs.put(nArray[n5], new float[]{fArray[n5], fArray2[n5]});
                            }
                        } else if (n6 == 5 || n6 == 2) {
                            for (n5 = 0; n5 < nArray.length; ++n5) {
                                downPtrs.put(nArray[n5], new float[]{fArray[n5], fArray2[n5]});
                            }
                        } else if (n6 == 1 || n6 == 3) {
                            downPtrs.clear();
                        } else if (n6 == 6 && (n5 = n7 >> 8 & 0xFF) < nArray.length) {
                            downPtrs.remove(nArray[n5]);
                        }
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    handler.post(new Runnable(){

                        @Override
                        public void run() {
                            if (!autoRunning || n != replayGen) {
                                return;
                            }
                            MotionEvent.PointerProperties[] pointerPropertiesArray = new MotionEvent.PointerProperties[nArray.length];
                            MotionEvent.PointerCoords[] pointerCoordsArray = new MotionEvent.PointerCoords[nArray.length];
                            for (int i = 0; i < nArray.length; ++i) {
                                pointerPropertiesArray[i] = new MotionEvent.PointerProperties();
                                pointerPropertiesArray[i].id = nArray[i];
                                pointerPropertiesArray[i].toolType = 1;
                                pointerCoordsArray[i] = new MotionEvent.PointerCoords();
                                pointerCoordsArray[i].x = fArray[i];
                                pointerCoordsArray[i].y = fArray2[i];
                                pointerCoordsArray[i].pressure = 1.0f;
                                pointerCoordsArray[i].size = 1.0f;
                            }
                            long l = SystemClock.uptimeMillis();
                            if ((actFinal & 0xFF) == 0) {
                                lArray[0] = l;
                            }
                            long l2 = lArray[0] == 0L ? l : lArray[0];
                            MotionEvent motionEvent = MotionEvent.obtain((long)l2, (long)l, (int)actFinal, (int)nArray.length, (MotionEvent.PointerProperties[])pointerPropertiesArray, (MotionEvent.PointerCoords[])pointerCoordsArray, (int)0, (int)0, (float)1.0f, (float)1.0f, (int)0, (int)0, (int)4098, (int)0);
                            view.dispatchTouchEvent(motionEvent);
                            motionEvent.recycle();
                        }
                    });
                }
            }
        }).start();
    }

    // ==== restored: replayParams ====


    // ==== restored: replayRecord ====


    static void autoPlay() {
        try {
            String[] all = act.getAssets().list("songs");
            final java.util.List<String> songs = new java.util.ArrayList<String>();
            for (String id : all) {
                try {
                    String[] f = act.getAssets().list("songs/" + id);
                    if (f == null) continue;
                    for (String x : f) if (x.endsWith(".aff")) { songs.add(id); break; }
                } catch (Throwable t) {}
            }
            if (songs.isEmpty()) { toast("未找到谱面"); return; }
            java.util.Collections.sort(songs);
            final String[] items = songs.toArray(new String[0]);
            new AlertDialog.Builder(act).setTitle("自动打歌 · 选择歌曲")
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { chooseDiff(items[w]); }
                }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { modMenu(); }
                }).show();
        } catch (Throwable t) { toast("读取曲目失败: " + t); }
    }

    static void chooseDiff(final String song) {
        final String[] names = {"Past", "Present", "Future", "Beyond"};
        new AlertDialog.Builder(act).setTitle(song + " · 难度")
            .setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { prepareRun(song, w); }
            }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { autoPlay(); }
            }).show();
    }

    static int[] readStartPoint() {
        try {
            java.io.File f = assetFile("auto_start.txt");
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String ln = r.readLine(); r.close();
            String[] p = ln.split(",");
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (Throwable t) { return new int[]{50, 88}; }
    }

    static void writeStartPoint(int x, int y) {
        try {
            java.io.File f = assetFile("auto_start.txt");
            f.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(x + "," + y); w.close();
        } catch (Throwable t) {}
    }

    static int readOffset() {
        try {
            java.io.File f = assetFile("auto_offset.txt");
            if (!f.exists()) return 0;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String ln = r.readLine(); r.close();
            return Integer.parseInt(ln.trim());
        } catch (Throwable t) { return 0; }
    }

    static void writeOffset(int v) {
        try {
            java.io.File f = assetFile("auto_offset.txt");
            f.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(String.valueOf(v)); w.close();
        } catch (Throwable t) {}
    }

    static void prepareRun(final String song, final int diff) {
        final int[] sp = readStartPoint();
        final EditText e = edit();
        e.setText(String.valueOf(readOffset()));
        final EditText ex = edit();
        ex.setText(String.valueOf(sp[0]));
        final EditText ey = edit();
        ey.setText(String.valueOf(sp[1]));
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20 * dpi, 8 * dpi, 20 * dpi, 0);
        box.addView(label(song + "   难度 " + diff));
        box.addView(label("开演按钮位置 X%（横向）"));
        box.addView(ex);
        box.addView(label("开演按钮位置 Y%（纵向，越大越靠下）"));
        box.addView(ey);
        box.addView(label("加载延迟（毫秒）：点完开演到音乐响起"));
        box.addView(e);
        box.addView(label("流程：3 秒后自动点开演 → 等延迟 → 开始注入"));
        new AlertDialog.Builder(act).setTitle("自动打歌")
            .setView(box)
            .setPositiveButton("开始", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    int off, x, y;
                    try { off = Integer.parseInt(e.getText().toString().trim()); } catch (Throwable t) { off = 0; }
                    try { x = Integer.parseInt(ex.getText().toString().trim()); } catch (Throwable t) { x = 50; }
                    try { y = Integer.parseInt(ey.getText().toString().trim()); } catch (Throwable t) { y = 88; }
                    writeOffset(off); writeStartPoint(x, y);
                    runInject(song, diff, off, x, y);
                }
            }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { chooseDiff(song); }
            }).show();
    }

    static long[] parseChart(String path, int diff) {
        try {
            InputStream in = act.getAssets().open("songs/" + path + "/" + diff + ".aff");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String[] lines = new String(bo.toByteArray(), "UTF-8").split("\n");
            java.util.List<long[]> out = new java.util.ArrayList<long[]>();
            for (String ln : lines) {
                ln = ln.trim();
                if (ln.length() < 5) continue;
                if (ln.startsWith("(")) {
                    int rp = ln.indexOf(')');
                    if (rp < 0) continue;
                    String[] p = ln.substring(1, rp).split(",");
                    if (p.length < 2) continue;
                    try {
                        long t = (long) Math.round(Double.parseDouble(p[0].trim()));
                        int lane = (int) Double.parseDouble(p[1].trim());
                        out.add(new long[]{t, lane, 0});
                    } catch (Throwable t) { }
                } else if (ln.startsWith("hold(")) {
                    int rp = ln.indexOf(')');
                    if (rp < 0) continue;
                    String[] p = ln.substring(5, rp).split(",");
                    if (p.length < 2) continue;
                    try {
                        long t = (long) Math.round(Double.parseDouble(p[0].trim()));
                        int lane = (int) Double.parseDouble(p[1].trim());
                        long e = p.length > 2 ? (long) Math.round(Double.parseDouble(p[2].trim())) : t + 300;
                        out.add(new long[]{t, lane, e});
                    } catch (Throwable t) { }
                }
            }
            java.util.Collections.sort(out, new java.util.Comparator<long[]>() {
                public int compare(long[] a, long[] b) { return Long.compare(a[0], b[0]); }
            });
            long[] res = new long[out.size() * 3];
            for (int i = 0; i < out.size(); i++) { res[i * 3] = out.get(i)[0]; res[i * 3 + 1] = out.get(i)[1]; res[i * 3 + 2] = out.get(i)[2]; }
            return res;
        } catch (Throwable t) { return new long[0]; }
    }

    static float[][] parseArcs(String song, int diff) {
        try {
            InputStream in = act.getAssets().open("songs/" + song + "/" + diff + ".aff");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            java.util.List<float[]> out = new java.util.ArrayList<float[]>();
            for (String ln : new String(bo.toByteArray(), "UTF-8").split("\n")) {
                ln = ln.trim();
                if (!ln.startsWith("arc(")) continue;
                int rp = ln.indexOf(')');
                if (rp < 0) continue;
                String[] p = ln.substring(4, rp).split(",");
                if (p.length < 4) continue;
                try {
                    float t1 = Float.parseFloat(p[0].trim());
                    float t2 = Float.parseFloat(p[1].trim());
                    float x1 = Float.parseFloat(p[2].trim());
                    float x2 = Float.parseFloat(p[3].trim());
                    if (t2 <= t1) t2 = t1 + 100;
                    out.add(new float[]{t1, t2, x1, x2});
                } catch (Throwable t) {}
            }
            return out.toArray(new float[0][]);
        } catch (Throwable t) { return new float[0][]; }
    }

    static android.view.MotionEvent.PointerProperties[] mkPP(int[] ids) {
        android.view.MotionEvent.PointerProperties[] pp = new android.view.MotionEvent.PointerProperties[ids.length];
        for (int i = 0; i < ids.length; i++) {
            pp[i] = new android.view.MotionEvent.PointerProperties();
            pp[i].id = ids[i]; pp[i].toolType = android.view.MotionEvent.TOOL_TYPE_FINGER;
        }
        return pp;
    }

    static void runInject(final String song, final int diff, final int offsetMs, final int sx, final int sy) {
        final long[] notes = parseChart(song, diff);
        final float[][] arcs = parseArcs(song, diff);
        int total = notes.length / 3 + arcs.length;
        if (total == 0) { toast("谱面为空或解析失败"); return; }
        final int N = total;
        final long[] T1 = new long[N], T2 = new long[N];
        final float[] X1 = new float[N], X2 = new float[N];
        final boolean[] ISARC = new boolean[N];
        int k = 0;
        for (int i = 0; i < notes.length; i += 3) {
            long t = notes[i], lane = notes[i + 1], e = notes[i + 2];
            T1[k] = t; T2[k] = (e > 0 ? e : t + 40);
            X1[k] = (lane + 0.5f) / 4f; X2[k] = X1[k];
            ISARC[k] = false; k++;
        }
        for (float[] a : arcs) {
            T1[k] = (long) a[0]; T2[k] = (long) a[1];
            X1[k] = a[2]; X2[k] = a[3];
            ISARC[k] = true; k++;
        }
        T1S = T1; T2S = T2; X1S = X1; X2S = X2;
        long lastEnd = 0;
        for (int i = 0; i < N; i++) if (T2[i] > lastEnd) lastEnd = T2[i];
        final long endAt = lastEnd;
        final View root = act.getWindow().getDecorView();
        final int W = root.getWidth(), H = root.getHeight();
        final float JUDGE_Y = H * 0.86f, SKY_Y = H * 0.40f;
        final float PLAY_LEFT = W * 0.05f, PLAY_W = W * 0.90f;
        toast("共 " + N + " 段（含蛇）\n3 秒后点开演，再等 " + offsetMs + " ms");
        autoRunning = true;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                if (!autoRunning) return;
                tap(W * sx / 100f, H * sy / 100f, 60);
                final long t0 = android.os.SystemClock.uptimeMillis() + offsetMs;
                new Thread(new Runnable() {
                    public void run() {
                        int[] slot = new int[N];
                        for (int i = 0; i < N; i++) slot[i] = -1;
                        int nextId = 1;
                        long downTime = 0;
                        while (autoRunning) {
                            long t = android.os.SystemClock.uptimeMillis() - t0;
                            if (t < 0) { sleep(5); continue; }
                            if (t > endAt + 400) break;
                            // 计算活动段
                            java.util.List<Integer> actv = new java.util.ArrayList<Integer>();
                            for (int i = 0; i < N; i++) if (t >= T1[i] && t <= T2[i]) actv.add(i);
                            // 先处理一个结束的段
                            int ending = -1;
                            for (int i = 0; i < N; i++) if (slot[i] >= 0 && !actv.contains(i)) { ending = i; break; }
                            if (ending >= 0) {
                                java.util.List<Integer> ids = new java.util.ArrayList<Integer>();
                                java.util.List<float[]> pos = new java.util.ArrayList<float[]>();
                                for (int i = 0; i < N; i++) if (slot[i] >= 0) {
                                    ids.add(slot[i]);
                                    pos.add(new float[]{ xAt(i, t, W, PLAY_LEFT, PLAY_W), ISARC[i] ? SKY_Y : JUDGE_Y });
                                }
                                int ai = ids.indexOf(slot[ending]);
                                if (ai < 0) ai = 0;
                                dispatch(root, downTime, android.view.MotionEvent.ACTION_POINTER_UP | (ai << 8), ids, pos);
                                slot[ending] = -1;
                                sleep(4);
                                continue;
                            }
                            // 再处理一个新增的段
                            int starting = -1;
                            for (int i : actv) if (slot[i] < 0) { starting = i; break; }
                            if (starting >= 0) {
                                slot[starting] = nextId;
                                nextId = (nextId % 300) + 1;
                                java.util.List<Integer> ids = new java.util.ArrayList<Integer>();
                                java.util.List<float[]> pos = new java.util.ArrayList<float[]>();
                                for (int i = 0; i < N; i++) if (slot[i] >= 0) {
                                    ids.add(slot[i]);
                                    pos.add(new float[]{ xAt(i, t, W, PLAY_LEFT, PLAY_W), ISARC[i] ? SKY_Y : JUDGE_Y });
                                }
                                int ai = ids.indexOf(slot[starting]);
                                if (ai < 0) ai = 0;
                                int action = (downTime == 0)
                                        ? android.view.MotionEvent.ACTION_DOWN
                                        : (android.view.MotionEvent.ACTION_POINTER_DOWN | (ai << 8));
                                if (downTime == 0) downTime = android.os.SystemClock.uptimeMillis();
                                dispatch(root, downTime, action, ids, pos);
                                sleep(4);
                                continue;
                            }
                            if (actv.isEmpty()) { sleep(4); continue; }
                            java.util.List<Integer> ids = new java.util.ArrayList<Integer>();
                            java.util.List<float[]> pos = new java.util.ArrayList<float[]>();
                            for (int i : actv) if (slot[i] >= 0) {
                                ids.add(slot[i]);
                                pos.add(new float[]{ xAt(i, t, W, PLAY_LEFT, PLAY_W), ISARC[i] ? SKY_Y : JUDGE_Y });
                            }
                            if (!ids.isEmpty()) dispatch(root, downTime, android.view.MotionEvent.ACTION_MOVE, ids, pos);
                            sleep(8);
                        }
                        java.util.List<Integer> ids = new java.util.ArrayList<Integer>();
                        java.util.List<float[]> pos = new java.util.ArrayList<float[]>();
                        for (int i = 0; i < N; i++) if (slot[i] >= 0) {
                            ids.add(slot[i]);
                            pos.add(new float[]{ W * 0.5f, JUDGE_Y });
                        }
                        if (!ids.isEmpty()) dispatch(root, downTime, android.view.MotionEvent.ACTION_CANCEL, ids, pos);
                        autoRunning = false;
                    }
                }).start();
            }
        }, 3000);
    }

    static void cancelAllPointers() {
        try {
            if (downPtrs.isEmpty()) return;
            java.util.List<Integer> ids = new java.util.ArrayList<Integer>(downPtrs.keySet());
            java.util.List<float[]> ps = new java.util.ArrayList<float[]>();
            for (Integer id : ids) ps.add(downPtrs.get(id));
            downPtrs.clear();
            dispatch(act.getWindow().getDecorView(), 0, android.view.MotionEvent.ACTION_CANCEL, ids, ps);
        } catch (Throwable t) {}
    }

    static void sleep(int ms) { try { Thread.sleep(ms); } catch (Exception e) {} }

    static float xAt(int seg, long t, int W, float left, float playW) {
        return left + playW * xOf(seg, t);
    }

    static float xOf(int seg, long t) {
        long a = T1S[seg], b = T2S[seg];
        float x1 = X1S[seg], x2 = X2S[seg];
        float r = (b > a) ? (t - a) / (float) (b - a) : 0f;
        if (r < 0) r = 0; if (r > 1) r = 1;
        return x1 + (x2 - x1) * r;
    }

    static void dispatch(View root, long downTime, int action, java.util.List<Integer> ids, java.util.List<float[]> pos) {
        try {
            int cnt = ids.size();
            if (cnt == 0) return;
            android.view.MotionEvent.PointerProperties[] pp;
            pp = new android.view.MotionEvent.PointerProperties[cnt];
            android.view.MotionEvent.PointerCoords[] pc = new android.view.MotionEvent.PointerCoords[cnt];
            for (int i = 0; i < cnt; i++) {
                pp[i] = new android.view.MotionEvent.PointerProperties();
                pp[i].id = ids.get(i); pp[i].toolType = android.view.MotionEvent.TOOL_TYPE_FINGER;
                pc[i] = new android.view.MotionEvent.PointerCoords();
                pc[i].x = pos.get(i)[0]; pc[i].y = pos.get(i)[1];
                pc[i].pressure = 1f; pc[i].size = 1f;
            }
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent ev = android.view.MotionEvent.obtain(downTime, now, action, cnt, pp, pc,
                    0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
            root.dispatchTouchEvent(ev);
            ev.recycle();
        } catch (Throwable t) {}
    }

    static void tap(float x, float y, int ms) {
        try {
            View root = act.getWindow().getDecorView();
            long t = android.os.SystemClock.uptimeMillis();
            MotionEvent d = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
            root.dispatchTouchEvent(d); d.recycle();
            MotionEvent u = MotionEvent.obtain(t, t + ms, MotionEvent.ACTION_UP, x, y, 0);
            root.dispatchTouchEvent(u); u.recycle();
        } catch (Throwable t) {}
    }

    static void pickPartner() {
        final String[] bases = artBases();
        if (bases.length == 0) { toast("读取资源失败"); return; }
        final String[] items = new String[bases.length + 1];
        for (int i = 0; i < bases.length; i++) items[i] = bases[i] + "   " + displayName(bases[i]);
        items[bases.length] = "〔测试〕向屏幕中心注入一次点击";
        showDark("选择搭档 / 形态", items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { actions(bases[w]); }
        }, true);
    }

    static File presetFile(String base, String slot) {
        return new File(new File(root(), "presets"), base + "_" + slot + ".png");
    }

    static void presetMenu(final String base) {
        String[] items = {"保存当前为 A", "应用 A", "保存当前为 B", "应用 B"};
        showDark("立绘预设 " + displayName(base), items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                try {
                    closeDlg();
                    File target = assetFile("char/1080/" + base + ".png");
                    String slot = (w == 0 || w == 1) ? "A" : "B";
                    File pf = presetFile(base, slot);
                    if (w == 0 || w == 2) {
                        Bitmap cur = loadCurrent(base);
                        if (cur == null) { toast("读取当前立绘失败"); return; }
                        pf.getParentFile().mkdirs();
                        java.io.FileOutputStream fo = new java.io.FileOutputStream(pf);
                        cur.compress(Bitmap.CompressFormat.PNG, 100, fo);
                        fo.close();
                        toast("已保存预设 " + slot);
                    } else {
                        if (!pf.exists()) { toast("预设 " + slot + " 还不存在"); return; }
                        target.getParentFile().mkdirs();
                        java.io.FileInputStream in = new java.io.FileInputStream(pf);
                        java.io.FileOutputStream fo = new java.io.FileOutputStream(target);
                        byte[] buf = new byte[8192]; int n;
                        while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                        in.close(); fo.close();
                        toast("已应用预设 " + slot + "\n重进游戏生效");
                    }
                } catch (Throwable t) { toast("失败: " + t); }
            }
        }, true);
    }

    static void actions(final String base) {
        final String[] items = {"编辑立绘", "编辑icon", "调整立绘", "编辑信息", "立绘预设", "恢复默认"};
        showDark(base + "   " + displayName(base), items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 0) { pendingBase = base; pickImage(); }
                else if (w == 1) editIcon(base);
                else if (w == 2) adjust(base);
                else if (w == 3) editInfo(base);
                else if (w == 4) presetMenu(base);
                else reset(base);
            }
        }, true);
    }

    static void pickImage() {
        Intent i = new Intent("android.intent.action.OPEN_DOCUMENT");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { act.startActivityForResult(i, REQ_ART); } catch (Throwable t) { toast("无法打开选图"); }
    }

    public static void onActivityResult(int req, int res, Intent data) {
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_ICON) { handleIcon(data.getData()); return; }
        if (req != REQ_ART) return;
        Uri uri = data.getData();
        try {
            int[] size = assetPngSize("char/1080/" + pendingBase + ".png", new int[]{1800, 1800});
            showCrop(uri, size[0], size[1], "char/1080/" + pendingBase + ".png", "裁剪立绘", false);
        } catch (Throwable t) { toast("失败: " + t); }
    }

    static class CropView extends View {
        Bitmap bm;
        boolean diamond = false;
        Matrix mat = new Matrix();
        float baseScale = 1f, scale = 1f, tx = 0, ty = 0;
        float lastX, lastY, lastDist = 0;
        int vpSize = 0;

        CropView(android.content.Context c) { super(c); }

        void setBitmap(Bitmap b) {
            bm = b;
            post(new Runnable() { public void run() { fitNow(); } });
        }

        void fitNow() {
            if (bm == null || getWidth() == 0) return;
            vpSize = (int) (Math.min(getWidth(), getHeight()) * 0.86f);
            baseScale = Math.max((float) vpSize / bm.getWidth(), (float) vpSize / bm.getHeight());
            scale = baseScale;
            tx = (getWidth() - bm.getWidth() * scale) / 2f;
            ty = (getHeight() - bm.getHeight() * scale) / 2f;
            update();
        }

        void update() {
            mat.reset();
            mat.postScale(scale, scale);
            mat.postTranslate(tx, ty);
            invalidate();
        }

        float[] vpRect() {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float h = vpSize / 2f;
            return new float[]{cx - h, cy - h, cx + h, cy + h};
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            c.drawColor(0xFF0A0810);
            if (bm == null) return;
            c.drawBitmap(bm, mat, null);
            float[] r = vpRect();
            android.graphics.Paint dim = new android.graphics.Paint();
            dim.setColor(0x99000000);
            c.drawRect(0, 0, getWidth(), r[1], dim);
            c.drawRect(0, r[3], getWidth(), getHeight(), dim);
            c.drawRect(0, r[1], r[0], r[3], dim);
            c.drawRect(r[2], r[1], getWidth(), r[3], dim);
            android.graphics.Paint line = new android.graphics.Paint();
            line.setStyle(android.graphics.Paint.Style.STROKE);
            line.setStrokeWidth(3);
            line.setColor(0xFFB79CEB);
            if (diamond) {
                android.graphics.Path dp = new android.graphics.Path();
                float cx = (r[0] + r[2]) / 2f, cy = (r[1] + r[3]) / 2f;
                float hw = (r[2] - r[0]) / 2f, hh = (r[3] - r[1]) / 2f;
                dp.moveTo(cx, cy - hh); dp.lineTo(cx + hw, cy); dp.lineTo(cx, cy + hh); dp.lineTo(cx - hw, cy); dp.close();
                android.graphics.Paint dim2 = new android.graphics.Paint();
                dim2.setColor(0x99000000);
                android.graphics.Region reg = new android.graphics.Region();
                c.save();
                c.clipPath(dp, android.graphics.Region.Op.DIFFERENCE);
                c.drawRect(0, 0, getWidth(), getHeight(), dim2);
                c.restore();
                c.drawPath(dp, line);
            } else {
                c.drawRect(r[0], r[1], r[2], r[3], line);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX(); lastY = e.getY(); break;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() == 1) {
                        tx += e.getX() - lastX; ty += e.getY() - lastY;
                        lastX = e.getX(); lastY = e.getY();
                    } else {
                        float d = dist(e);
                        if (lastDist > 0) {
                            float f = d / lastDist;
                            float ns = Math.max(baseScale * 0.5f, Math.min(scale * f, baseScale * 8f));
                            float cx = e.getX(), cy = e.getY();
                            tx = cx - (cx - tx) * (ns / scale);
                            ty = cy - (cy - ty) * (ns / scale);
                            scale = ns;
                        }
                        lastDist = d;
                    }
                    update(); break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    lastDist = 0; break;
            }
            return true;
        }

        float dist(MotionEvent e) {
            float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
            return (float) Math.hypot(dx, dy);
        }

        Bitmap crop() {
            float[] r = vpRect();
            float[] pts = {r[0], r[1], r[2], r[3]};
            Matrix inv = new Matrix();
            if (!mat.invert(inv)) return null;
            inv.mapPoints(pts);
            int x = Math.max(0, (int) pts[0]);
            int y = Math.max(0, (int) pts[1]);
            int x2 = Math.min(bm.getWidth(), (int) pts[2]);
            int y2 = Math.min(bm.getHeight(), (int) pts[3]);
            int w = Math.max(1, x2 - x), h = Math.max(1, y2 - y);
            return Bitmap.createBitmap(bm, x, y, w, h);
        }
    }

    static void showCrop(Uri uri, final int outW, final int outH, final String outRel, final String label, final boolean diamond) {
        try {
            Bitmap bm = decode(uri, 2048);
            if (bm == null) { toast("图片解码失败"); return; }
            FrameLayout box = new FrameLayout(act);
            final CropView cv = new CropView(act);
            cv.diamond = diamond;
            cv.setBitmap(bm);
            box.addView(cv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (act.getResources().getDisplayMetrics().heightPixels * 0.55f)));
            final String lbl = label;
            showDarkCustom(label, box, new String[]{"重置", "取消", "裁剪保存"}, new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { cv.fitNow(); }
                },
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { closeDlg(); }
                },
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            Bitmap c = cv.crop();
                            if (c == null) { toast("裁剪失败"); return; }
                            Bitmap sc = Bitmap.createScaledBitmap(c, outW, outH, true);
                            Bitmap out = sc;
                            if (diamond) {
                                out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                                android.graphics.Canvas cc = new android.graphics.Canvas(out);
                                android.graphics.Path dp = new android.graphics.Path();
                                dp.moveTo(outW / 2f, 0); dp.lineTo(outW, outH / 2f);
                                dp.lineTo(outW / 2f, outH); dp.lineTo(0, outH / 2f); dp.close();
                                cc.clipPath(dp);
                                cc.drawBitmap(sc, 0, 0, null);
                            }
                            save(out, assetFile(outRel));
                            closeDlg();
                            toast("已保存\n重进游戏生效");
                        } catch (Throwable t) { toast("失败: " + t); }
                    }
                }
            });
        } catch (Throwable t) { toast("失败: " + t); }
    }

    static Bitmap decode(Uri uri, int max) throws Exception {
        InputStream in = act.getContentResolver().openInputStream(uri);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true; BitmapFactory.decodeStream(in, null, o); in.close();
        int sample = 1;
        while (o.outWidth / sample > max || o.outHeight / sample > max) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        in = act.getContentResolver().openInputStream(uri);
        Bitmap b = BitmapFactory.decodeStream(in, null, o2); in.close();
        return b;
    }

    static Bitmap fit(Bitmap src, int W, int H, float scale, float dx, float dy) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        float s = scale * Math.min((float) W / src.getWidth(), (float) H / src.getHeight());
        Matrix m = new Matrix();
        m.postScale(s, s);
        m.postTranslate((W - src.getWidth() * s) / 2f + dx * W, (H - src.getHeight() * s) / 2f + dy * H);
        c.drawBitmap(src, m, p);
        return out;
    }

    static void editIcon(final String base) {
        pendingIconBase = base;
        Intent i = new Intent("android.intent.action.OPEN_DOCUMENT");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { act.startActivityForResult(i, REQ_ICON); } catch (Throwable t) { toast("无法打开选图"); }
    }

    static final int REQ_ICON = 1002;
    static String pendingIconBase = null;

    static void handleIcon(Uri uri) {
        try {
            int[] size = assetPngSize("char/" + pendingIconBase + "_icon.png", new int[]{170, 170});
            showCrop(uri, size[0], size[1], "char/" + pendingIconBase + "_icon.png", "裁剪 icon", true);
        } catch (Throwable t) { toast("失败: " + t); }
    }

    static class Preview extends View {
        Bitmap bm; float[] st; int W0, H0; float lastX, lastY;
        Runnable onChange;
        Preview(Context c, Bitmap b, float[] s, int[] size) { super(c); bm = b; st = s; W0 = size[0]; H0 = size[1]; }

        protected void onDraw(Canvas c) {
            c.drawColor(0xFF1E1B2A);
            if (bm == null) return;
            float s = st[0] * Math.min((float) getWidth() / bm.getWidth(), (float) getHeight() / bm.getHeight());
            float dx = st[1] * getWidth(), dy = st[2] * getHeight();
            Matrix m = new Matrix();
            m.postScale(s, s);
            m.postTranslate((getWidth() - bm.getWidth() * s) / 2f + dx, (getHeight() - bm.getHeight() * s) / 2f + dy);
            c.drawBitmap(bm, m, new Paint(Paint.FILTER_BITMAP_FLAG));
            Paint p = new Paint(); p.setColor(0x66FFFFFF); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            c.drawRect(2, 2, getWidth() - 2, getHeight() - 2, p);
        }

        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { lastX = e.getX(); lastY = e.getY(); return true; }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                st[1] += (e.getX() - lastX) / getWidth();
                st[2] += (e.getY() - lastY) / getHeight();
                if (st[1] > 0.5f) st[1] = 0.5f; if (st[1] < -0.5f) st[1] = -0.5f;
                if (st[2] > 0.5f) st[2] = 0.5f; if (st[2] < -0.5f) st[2] = -0.5f;
                lastX = e.getX(); lastY = e.getY();
                invalidate();
                if (onChange != null) onChange.run();
                return true;
            }
            return true;
        }
    }

    static void adjust(final String base) {
        final Bitmap src = loadCurrent(base);
        if (src == null) { toast("找不到当前立绘"); return; }
        final int[] size = assetPngSize("char/1080/" + base + ".png", new int[]{1800, 1800});
        final float[] st = {1f, 0f, 0f};
        final TextView tvS = label("缩放 100%");
        final TextView tvX = label("水平 0%");
        final TextView tvY = label("垂直 0%");
        final Preview pv = new Preview(act, src, st, size);
        pv.onChange = new Runnable() {
            public void run() {
                tvX.setText("水平 " + Math.round(st[1] * 100) + "%");
                tvY.setText("垂直 " + Math.round(st[2] * 100) + "%");
            }
        };
        SeekBar sb = new SeekBar(act);
        sb.setMax(150);
        sb.setProgress(50);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int v, boolean u) {
                st[0] = 0.5f + v / 100f;
                tvS.setText("缩放 " + Math.round(st[0] * 100) + "%");
                pv.invalidate();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(16 * dpi, 8 * dpi, 16 * dpi, 8 * dpi);
        box.addView(pv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        box.addView(tvS); box.addView(sb);
        LinearLayout row = new LinearLayout(act);
        row.addView(tvX, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvY, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row);
        box.addView(label("在预览区拖动可调整位置"));
        showDarkCustom("调整立绘 " + base, box, new String[]{"取消", "保存"}, new DialogInterface.OnClickListener[]{
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d0, int w0) { closeDlg(); }
            },
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        Bitmap outBm = render(src, size[0], size[1], st[0], st[1], st[2]);
                        save(outBm, assetFile("char/1080/" + base + ".png"));
                        toast("已应用\n重进游戏生效");
                    } catch (Throwable t) { toast("失败: " + t); }
                }
            }
        });
    }

    static Bitmap render(Bitmap src, int W, int H, float sc, float dx, float dy) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float s = sc * Math.min((float) W / src.getWidth(), (float) H / src.getHeight());
        Matrix m = new Matrix();
        m.postScale(s, s);
        m.postTranslate((W - src.getWidth() * s) / 2f + dx * W, (H - src.getHeight() * s) / 2f + dy * H);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    static EditText edit() {
        EditText e = new EditText(act);
        e.setTextColor(0xFFF3EDFF);
        e.setHintTextColor(0xFF9A8FB5);
        return e;
    }

    static TextView label(String s) {
        TextView v = new TextView(act);
        v.setText(s);
        v.setTextSize(12);
        v.setTextColor(0xFFDCD2F0);
        v.setPadding(0, (int) (6 * dpi), 0, (int) (2 * dpi));
        return v;
    }

    static Bitmap loadCurrent(String base) {
        try {
            File f = assetFile("char/1080/" + base + ".png");
            if (f.exists()) return BitmapFactory.decodeFile(f.getAbsolutePath());
            InputStream in = act.getAssets().open("char/1080/" + base + ".png");
            Bitmap b = BitmapFactory.decodeStream(in); in.close(); return b;
        } catch (Throwable t) { return null; }
    }

    static void editInfo(final String base) {
        String obj = findObject(baseKey(base));
        if (obj == null) { toast("该形态没有独立条目，可编辑本体的名字"); return; }
        String mo = moNameOf(base);
        final String oldName = mo != null ? mo : bestName(stringsOf(obj));
        final EditText eName = edit(); eName.setText(oldName);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20 * dpi, 10 * dpi, 20 * dpi, 0);
        box.addView(label("显示名（写入翻译表 assets/tl/*.mo）"));
        box.addView(eName);
        box.addView(label("当前：" + oldName));
        showDarkCustom("编辑信息 " + base, box, new String[]{"取消", "保存"}, new DialogInterface.OnClickListener[]{
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d0, int w0) { closeDlg(); }
            },
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        String nm = eName.getText().toString().trim().replace("\t", " ");
                        if (nm.length() == 0) { toast("名字不能为空"); return; }
                        String done = patchAllLocales(oldName, nm);
                        if (done.length() == 0) { toast("改名失败：名字比原名长，或该角色不在翻译表\n请用不超过原名的长度"); return; }
                        toast("已写入 " + done + "\n" + oldName + " → " + nm + "\n重进游戏生效");
                    } catch (Throwable t) { toast("保存失败: " + t); }
                }
            }
        });
    }

    static void writeOverride(String oldName, String newName) throws Exception {
        File f = assetFile("tl/overrides.txt");
        f.getParentFile().mkdirs();
        List<String> lines = new ArrayList<String>();
        if (f.exists()) {
            InputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            for (String ln : new String(bo.toByteArray(), "UTF-8").split("\n"))
                if (ln.trim().length() > 0) lines.add(ln);
        }
        boolean done = false;
        for (int i = 0; i < lines.size(); i++) {
            int t = lines.get(i).indexOf('\t');
            if (t > 0 && lines.get(i).substring(0, t).equals(oldName)) { lines.set(i, oldName + "\t" + newName); done = true; }
        }
        if (!done) lines.add(oldName + "\t" + newName);
        StringBuilder sb = new StringBuilder();
        for (String ln : lines) sb.append(ln).append('\n');
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(sb.toString().getBytes("UTF-8")); fo.close();
    }

    static String moNameOf(String base) {
        try {
            String key = baseKey(base);
            String obj = findObject(key);
            if (obj == null) return null;
            Matcher mn = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"").matcher(obj);
            if (!mn.find()) return null;
            String en = mn.group(1);
            String msgid = en.substring(0, 1).toUpperCase() + en.substring(1);
            byte[] d;
            InputStream in = act.getAssets().open("tl/zh-Hans.mo");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            d = bo.toByteArray();
            if (d.length < 28) return null;
            int cnt = le32(d, 8), oo = le32(d, 12), to = le32(d, 16);
            byte[] id = msgid.getBytes();
            for (int i = 0; i < cnt; i++) {
                int a = le32(d, oo + 4 * i), b = le32(d, to + 4 * i);
                if (a < 0 || a >= d.length || b < 0 || b >= d.length) continue;
                int la = 0; while (a + la < d.length && d[a + la] != 0) la++;
                if (la != id.length) continue;
                boolean eq = true;
                for (int k = 0; k < la; k++) if (d[a + k] != id[k]) { eq = false; break; }
                if (!eq) continue;
                int lb = 0; while (b + lb < d.length && d[b + lb] != 0) lb++;
                return new String(d, b, lb, "UTF-8");
            }
        } catch (Throwable t) {}
        return null;
    }

    static boolean patchMo(byte[] d, String oldName, String newName) {
        if (d.length < 28 || (d[0] & 0xff) != 0xde || (d[1] & 0xff) != 0x12 || (d[2] & 0xff) != 0x04 || (d[3] & 0xff) != 0x95) return false;
        int cnt = le32(d, 8), oo = le32(d, 12), to = le32(d, 16);
        if (cnt <= 0 || cnt > 50000 || oo + 4 * cnt > d.length || to + 4 * cnt > d.length) return false;
        byte[] oldB = oldName.getBytes();
        byte[] newB = newName.getBytes();
        boolean any = false;
        for (int i = 0; i < cnt; i++) {
            int b = le32(d, to + 4 * i);
            if (b < 0 || b >= d.length) continue;
            int len = 0;
            while (b + len < d.length && d[b + len] != 0) len++;
            if (len < oldB.length) continue;
            boolean eq = true;
            for (int k = 0; k < oldB.length; k++) if (d[b + k] != oldB[k]) { eq = false; break; }
            if (!eq) continue;
            if (newB.length + 1 > len + 1) continue;
            System.arraycopy(newB, 0, d, b, newB.length);
            for (int k = newB.length; k <= len; k++) d[b + k] = 0;
            any = true;
        }
        return any;
    }

    static byte[] patchMoGrow(byte[] d, String oldName, String newName) {
        if (d.length < 28) return null;
        int cnt = le32(d, 8), oo = le32(d, 12), to = le32(d, 16);
        if (cnt <= 0 || cnt > 50000 || oo + 4 * cnt > d.length || to + 4 * cnt > d.length) return null;
        byte[] oldB = oldName.getBytes(), newB = newName.getBytes();
        java.util.List<Integer> hits = new java.util.ArrayList<Integer>();
        for (int i = 0; i < cnt; i++) {
            int b = le32(d, to + 4 * i);
            if (b < 0 || b >= d.length) continue;
            int len = 0;
            while (b + len < d.length && d[b + len] != 0) len++;
            if (len < oldB.length) continue;
            boolean eq = true;
            for (int k = 0; k < oldB.length; k++) if (d[b + k] != oldB[k]) { eq = false; break; }
            if (eq) hits.add(i);
        }
        if (hits.isEmpty()) return null;
        byte[] out = new byte[d.length + hits.size() * (newB.length + 1)];
        System.arraycopy(d, 0, out, 0, d.length);
        int pos = d.length;
        for (int idx : hits) {
            System.arraycopy(newB, 0, out, pos, newB.length);
            out[pos + newB.length] = 0;
            int off = to + 4 * idx;
            out[off] = (byte) (pos & 0xff);
            out[off + 1] = (byte) ((pos >> 8) & 0xff);
            out[off + 2] = (byte) ((pos >> 16) & 0xff);
            out[off + 3] = (byte) ((pos >> 24) & 0xff);
            pos += newB.length + 1;
        }
        return out;
    }

    static int le32(byte[] d, int off) {
        return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8) | ((d[off + 2] & 0xff) << 16) | ((d[off + 3] & 0xff) << 24);
    }

    static String patchAllLocales(String oldName, String newName) throws Exception {
        String[] locales = {"zh-Hans", "zh-Hant", "ja", "en", "ko"};
        StringBuilder done = new StringBuilder();
        for (String lc : locales) {
            String path = "tl/" + lc + ".mo";
            byte[] data;
            try {
                InputStream in = act.getAssets().open(path);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                data = bo.toByteArray();
            } catch (Throwable t) { continue; }
            if (patchMo(data, oldName, newName)) {
                File f = assetFile(path);
                f.getParentFile().mkdirs();
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(data); fo.close();
                done.append(lc).append(' ');
            }
        }
        return done.toString();
    }

    static void reset(String base) {
        assetFile("char/1080/" + base + ".png").delete();
        assetFile("char/" + base + "_icon.png").delete();
        toast("已恢复默认\n重进游戏生效");
    }

    static void save(Bitmap bm, File f) throws Exception {
        f.getParentFile().mkdirs();
        FileOutputStream fo = new FileOutputStream(f);
        bm.compress(Bitmap.CompressFormat.PNG, 100, fo);
        fo.close();
    }

    static void testTap() {
        try {
            final View root = act.getWindow().getDecorView();
            final float x = root.getWidth() / 2f, y = root.getHeight() / 2f;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() {
                    long t = android.os.SystemClock.uptimeMillis();
                    MotionEvent d = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
                    root.dispatchTouchEvent(d); d.recycle();
                    MotionEvent u = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0);
                    root.dispatchTouchEvent(u); u.recycle();
                    toast("已注入点击 (" + (int) x + "," + (int) y + ")");
                }
            }, 800);
        } catch (Throwable t) { toast("注入失败: " + t); }
    }

    static void toast(final String s) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() { Toast.makeText(act, s, Toast.LENGTH_LONG).show(); } });
    }
}
