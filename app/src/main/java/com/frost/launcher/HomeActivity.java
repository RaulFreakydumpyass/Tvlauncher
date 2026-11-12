package com.frost.launcher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The whole launcher: one activity, no XML layouts, no libraries. */
public class HomeActivity extends Activity implements PanelView.Host {
    private static final int S_COLS = 1, S_ICON = 2, S_NAMES = 3, S_NAMESZ = 4, S_UI = 5, S_DIM = 6, S_BLUR = 7,
            S_BAND = 8, S_PANEL = 9, S_TIME = 10, S_SPEED = 11, S_TILT = 12, S_SAVER = 13, S_FONT = 14,
            S_BG_PICK = 101, S_BG_REMOVE = 102, S_SYSTEM = 103, S_RESET = 104, S_ABOUT = 105,
            S_FONT_PICK = 106, S_FONT_REMOVE = 107;
    private static final int REQ_STORAGE = 7;
    private static final String VERSION = "1.2";

    final ExecutorService exec = Executors.newFixedThreadPool(2);
    private final Handler h = new Handler(Looper.getMainLooper());

    private Prefs pr, settingsOld;
    private FrameLayout content, ui, grid;
    private WallView wall;
    private BandView band;
    private PanelView panel;
    private SaverView saver;
    private View dimView;
    private TileView addTile, focusedView;
    private final ArrayList<TileView> tiles = new ArrayList<>();
    private final HashMap<String, Bitmap[]> artCache = new HashMap<>();
    private Bitmap wallBmp, glassBmp;
    private TileStore store;
    private Sheet sheet;
    private ObjectAnimator jiggle, bandFade;

    private int cols, tw, th, gap, left, pitch, padTop, labelH, namePx, gridTop, availH, rows;
    private int idx, zone;                      // zone 0 = app grid, 1 = top panel
    private float scrollY;
    private boolean editing, busy, loadingApps, saverOn;
    private long lastNav;
    private String curTime = "", curDate = "";
    private int pickMode;                       // 0 = tile icon, 1 = wallpaper, 2 = font
    private boolean wantFontPicker;             // waiting for the user to grant "All files access"
    private String pendingPkg, pendingLabel;
    private Bitmap pendingIcon;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshInfo();
            h.postDelayed(this, 20000);
        }
    };
    private final Runnable idleRun = () -> startSaver();

    // =================================================================== setup

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pr = Prefs.load(this);
        if (pr.font == Prefs.FONT_CUSTOM && !FontTools.has(this)) pr.font = 0;
        Spring.speed = Prefs.SPEEDS[pr.speed];
        Ui.init(this, pr.uiScale, pr.font);
        store = new TileStore(this);
        Bitmap[] d = WallTools.defaults();
        wallBmp = d[0];
        glassBmp = d[1];

        content = new FrameLayout(this);
        content.setBackgroundColor(Color.BLACK);
        ui = new FrameLayout(this);
        ui.setClipChildren(false);
        content.addView(ui, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        saver = new SaverView(this);
        saver.setVisibility(View.GONE);
        content.addView(saver, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(content);

        buildUi(store.load());
        layoutGrid(false);
        applyFocus(false);
        reloadWallpaper(false);
        hideSystemUi();
    }

    private void computeGrid() {
        final float sw = Ui.W;
        float m0 = sw * 0.0625f, g0 = sw * 0.022f;
        float cell = (sw - 2 * m0 - (pr.cols - 1) * g0) / pr.cols;
        tw = Math.max(40, Math.round(cell * pr.iconScale / 100f * Ui.scale));
        th = Math.round(tw * 9f / 16f);
        gap = Math.max(Math.round(g0 * Ui.scale), Math.round(tw * 0.10f));
        int fit = Math.max(1, (int) ((sw - 2 * m0 + gap) / (tw + gap) + 0.02f));
        cols = Math.min(pr.cols, fit);
        left = Math.round((sw - (cols * tw + (cols - 1) * gap)) / 2f);
        namePx = pr.names == 0 ? 0 : Ui.px(32 * pr.nameScale / 100f);
        float labelTop = Math.round(th * 0.09f) + Ui.px(10);
        labelH = pr.names == 0 ? 0 : Math.round(labelTop + namePx * 1.35f);
        pitch = th + labelH + Math.round(gap * 0.8f);
        padTop = Math.round(th * 0.12f) + Ui.px(8);
        gridTop = pr.panel ? Ui.px(34 + 88 + 56) : Ui.px(80);
        availH = Ui.H - gridTop + padTop - Ui.px(24);
    }

    private void buildUi(List<TileStore.Tile> order) {
        final int mp = ViewGroup.LayoutParams.MATCH_PARENT, wc = ViewGroup.LayoutParams.WRAP_CONTENT;
        computeGrid();

        wall = new WallView(this);
        wall.setDim(pr.dim);
        wall.setBitmap(wallBmp, false);
        ui.addView(wall, new FrameLayout.LayoutParams(mp, mp));

        grid = new FrameLayout(this);
        grid.setClipChildren(false);
        ui.addView(grid, new FrameLayout.LayoutParams(Ui.W, padTop * 2 + pitch));

        band = new BandView(this);
        band.setGlass(glassBmp);
        int bandPad = Ui.px(46);
        int bandW = cols * tw + (cols - 1) * gap + 2 * bandPad;
        int bandH = Math.round(th * 1.30f);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(bandW, bandH);
        bl.leftMargin = left - bandPad;
        grid.addView(band, bl);
        band.setVisibility(pr.band ? View.VISIBLE : View.GONE);

        tiles.clear();
        focusedView = null;
        addTile = new TileView(this, null, tw, th, namePx, pr.names, pr.tilt);
        grid.addView(addTile, new FrameLayout.LayoutParams(tw, th));
        wire(addTile);
        for (TileStore.Tile t : order) addTileView(t);

        dimView = new View(this);
        dimView.setBackgroundColor(Color.BLACK);
        dimView.setAlpha(0f);
        ui.addView(dimView, new FrameLayout.LayoutParams(mp, mp));

        panel = new PanelView(this, this);
        panel.setGlass(glassBmp);
        panel.setVisibility(pr.panel ? View.VISIBLE : View.GONE);
        ui.addView(panel, new FrameLayout.LayoutParams(mp, wc));
        refreshInfo();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);          // Home pressed again: close menus and jump back to the first app
        wakeSaver();
        dismissSheet();
        stopEdit();
        zone = 0;
        idx = 0;
        applyFocus(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        busy = false;
        h.removeCallbacks(tick);
        h.post(tick);
        scheduleSaver();
        if (wantFontPicker) {                                  // back from the "All files access" screen
            wantFontPicker = false;
            if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager() && sheet == null) {
                pickMode = 2;
                showFileSheet();
            }
        }
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(tick);
        h.removeCallbacks(idleRun);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        exec.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // A launcher never closes itself.
    }

    // =================================================================== tiles & grid

    private TileView addTileView(TileStore.Tile t) {
        TileView v = new TileView(this, t, tw, th, namePx, pr.names, pr.tilt);
        grid.addView(v, new FrameLayout.LayoutParams(tw, th));
        int i = tiles.size();
        v.setBase(tileX(i), tileY(i), false);
        tiles.add(v);
        wire(v);
        loadArt(v);
        return v;
    }

    private void wire(final TileView v) {
        v.setOnClickListener(x -> {
            if (sheet != null || editing) return;
            int i = v.tile == null ? tiles.size() : tiles.indexOf(v);
            if (i < 0) return;
            zone = 0;
            idx = i;
            applyFocus(true);
            if (v.tile == null) openAppPicker();
            else launch(v);
        });
        v.setOnLongClickListener(x -> {
            if (sheet != null || editing) return false;
            if (v.tile == null) {
                openAddMenu();
            } else {
                openOptions(tiles.indexOf(v));
            }
            return true;
        });
        v.setOnHoverListener((x, ev) -> {                     // air-mouse pointer moves focus
            if (ev.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER && sheet == null && !editing) {
                int i = v.tile == null ? tiles.size() : tiles.indexOf(v);
                if (i >= 0 && (zone != 0 || i != idx)) {
                    zone = 0;
                    idx = i;
                    applyFocus(true);
                }
            }
            return false;
        });
    }

    private void loadArt(final TileView v) {
        final TileStore.Tile t = v.tile;
        Bitmap[] cached = artCache.get(t.id);
        if (cached != null) {
            v.setArt(cached[0], cached[1]);
            return;
        }
        exec.execute(() -> {
            final Bitmap bg = BitmapFactory.decodeFile(store.bg(t).getPath());
            final Bitmap fg = BitmapFactory.decodeFile(store.fg(t).getPath());
            runOnUiThread(() -> {
                if (bg != null) artCache.put(t.id, new Bitmap[]{bg, fg});
                v.setArt(bg, fg);
            });
        });
    }

    private TileView viewAt(int i) {
        return i < tiles.size() ? tiles.get(i) : addTile;
    }

    private float tileX(int i) {
        return left + (i % cols) * (float) (tw + gap);
    }

    private float tileY(int i) {
        return padTop + (i / cols) * (float) pitch;
    }

    private void layoutGrid(boolean animate) {
        int n = tiles.size();
        rows = (n + cols) / cols;                                  // tiles + the "+" tile
        FrameLayout.LayoutParams gp = (FrameLayout.LayoutParams) grid.getLayoutParams();
        gp.height = padTop * 2 + rows * pitch;
        gp.topMargin = gridTop - padTop;
        grid.setLayoutParams(gp);
        for (int i = 0; i <= n; i++) viewAt(i).setBase(tileX(i), tileY(i), animate);
    }

    private void saveOrder() {
        ArrayList<TileStore.Tile> l = new ArrayList<>();
        for (TileView v : tiles) l.add(v.tile);
        store.save(l);
    }

    // ---- grid morph (smooth transition when the grid layout changes)

    /** Where every tile is on screen right now: {centre x, centre y, width}. */
    private HashMap<String, float[]> snapshot() {
        HashMap<String, float[]> m = new HashMap<>();
        float ty = gridTop - padTop - scrollY;
        for (int i = 0; i <= tiles.size(); i++) {
            TileView v = viewAt(i);
            String k = v.tile == null ? "+" : v.tile.id;
            m.put(k, new float[]{tileX(i) + tw / 2f, ty + tileY(i) + th / 2f, tw});
        }
        return m;
    }

    private void playMorph(HashMap<String, float[]> snap) {
        float ty = gridTop - padTop - scrollY;
        for (int i = 0; i <= tiles.size(); i++) {
            TileView v = viewAt(i);
            float[] s = snap.get(v.tile == null ? "+" : v.tile.id);
            if (s == null) continue;
            v.morphFrom(s[0] - tw / 2f, s[1] - ty - th / 2f, s[2] / tw);
        }
    }

    /** Rebuilds the whole UI with the current settings, morphing every tile from its old spot/size to the new one. */
    private void rebuildUi() {
        HashMap<String, float[]> snap = snapshot();
        ArrayList<TileStore.Tile> order = new ArrayList<>();
        for (TileView v : tiles) order.add(v.tile);
        stopEdit();
        ui.removeAllViews();
        Ui.init(this, pr.uiScale, pr.font);
        if (!pr.panel && zone == 1) zone = 0;
        buildUi(order);
        layoutGrid(false);
        applyFocus(false);
        playMorph(snap);
    }

    // =================================================================== focus / scrolling / band

    private void applyFocus(boolean animate) {
        TileView target = zone == 0 ? viewAt(idx) : null;
        if (focusedView != target) {
            if (focusedView != null) focusedView.setFocused(false, animate);
            if (target != null) target.setFocused(true, animate);
            focusedView = target;
        }
        panel.setExpanded(zone == 1, animate);
        float dimTo = zone == 1 ? 0.45f : 0f;
        if (animate) {
            dimView.animate().alpha(dimTo).setDuration(Spring.dur(260)).start();
        } else {
            dimView.animate().cancel();
            dimView.setAlpha(dimTo);
        }
        if (zone == 0) {
            scrollGridTo(idx / cols, animate);
        } else if (!animate) {
            grid.setTranslationY(-scrollY);
        }
        positionBand(animate);
    }

    private void scrollGridTo(int r, boolean animate) {
        float topEdge = r * (float) pitch;
        float botEdge = padTop + r * (float) pitch + th + labelH + padTop * 0.3f;
        float s = scrollY;
        if (topEdge < s) s = topEdge;
        if (botEdge > s + availH) s = botEdge - availH;
        float contentH = padTop * 2f + rows * (float) pitch;
        s = Math.min(s, Math.max(0f, contentH - availH));
        s = Math.max(0f, s);
        scrollY = s;
        if (animate) {
            grid.animate().translationY(-s).setDuration(Spring.EASE.ms()).setInterpolator(Spring.EASE)
                    .setUpdateListener(a -> band.invalidate()).start();
        } else {
            grid.animate().cancel();
            grid.setTranslationY(-s);
            band.invalidate();
        }
    }

    /** The frosted bar glides to the row you are on. */
    private void positionBand(boolean animate) {
        if (!pr.band) return;
        int bh = band.getLayoutParams().height;
        float ty = padTop + (idx / cols) * (float) pitch - (bh - th) / 2f;
        float alpha = zone == 0 ? 1f : 0f;
        if (bandFade != null) bandFade.cancel();
        if (animate) {
            band.animate().translationY(ty).setDuration(Spring.EASE.ms()).setInterpolator(Spring.EASE)
                    .setUpdateListener(a -> band.invalidate()).start();
            bandFade = ObjectAnimator.ofFloat(band, View.ALPHA, band.getAlpha(), alpha);
            bandFade.setDuration(Spring.dur(300));
            bandFade.start();
        } else {
            band.animate().cancel();
            band.setTranslationY(ty);
            band.setAlpha(alpha);
            band.invalidate();
        }
    }

    // =================================================================== keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (saverOn) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) wakeSaver();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) scheduleSaver();
        if (sheet != null && sheet.onKey(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (saverOn) wakeSaver();
        else scheduleSaver();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (saverOn) wakeSaver();
        else scheduleSaver();
        return super.dispatchGenericMotionEvent(ev);
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (sheet != null || busy) return super.onKeyDown(code, e);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                nav(-1, 0, e);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                nav(1, 0, e);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                nav(0, -1, e);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                nav(0, 1, e);
                return true;
            case KeyEvent.KEYCODE_SETTINGS:
                if (e.getRepeatCount() == 0) openLauncherSettings();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (e.getRepeatCount() == 0) optionsForFocus();
                return true;
            default:
                if (isOk(code)) {
                    if (e.getRepeatCount() == 0) e.startTracking();
                    return true;
                }
                return super.onKeyDown(code, e);
        }
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent e) {
        if (sheet == null && !busy && isOk(code)) {
            optionsForFocus();
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (sheet != null || busy) return super.onKeyUp(code, e);
        if (isOk(code)) {
            if (e.isTracking() && !e.isCanceled()) activateFocus();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (editing) {
                stopEdit();
            } else if (zone == 1) {
                zone = 0;
                applyFocus(true);
            }
            return true;
        }
        return super.onKeyUp(code, e);
    }

    private void nav(int dx, int dy, KeyEvent e) {
        long now = SystemClock.uptimeMillis();
        if (e.getRepeatCount() > 0 && now - lastNav < 140) return;   // calm key-repeat so the springs can breathe
        lastNav = now;
        if (editing) {
            moveEditing(dx, dy);
            return;
        }
        int n = tiles.size();
        if (zone == 0) {
            int j = idx;
            if (dx != 0) {
                int k = idx + dx;
                if (k >= 0 && k <= n && k / cols == idx / cols) j = k;
            } else if (dy > 0) {
                int k = idx + cols;
                if (k > n && idx / cols < n / cols) k = n;      // last row is shorter: land on the last tile
                if (k <= n) j = k;
            } else if (dy < 0) {
                int k = idx - cols;
                if (k >= 0) {
                    j = k;
                } else if (pr.panel) {
                    zone = 1;
                    applyFocus(true);
                    return;
                }
            }
            if (j != idx) {
                idx = j;
                viewAt(idx).tilt(dx, dy);
                applyFocus(true);
            }
        } else {
            if (dx != 0) panel.move(dx);
            else if (dy > 0) {
                zone = 0;
                applyFocus(true);
            }
        }
    }

    private void activateFocus() {
        if (editing) {
            stopEdit();
            return;
        }
        if (zone == 1) {
            panel.activate();
            return;
        }
        if (idx < tiles.size()) launch(tiles.get(idx));
        else openAppPicker();
    }

    private void optionsForFocus() {
        if (editing) {
            stopEdit();
            return;
        }
        if (zone == 1) openLauncherSettings();
        else if (idx < tiles.size()) openOptions(idx);
        else openAddMenu();
    }

    // =================================================================== top panel

    @Override
    public void onPanelAction(int id, String arg) {
        if (sheet != null) return;
        switch (id) {
            case PanelView.A_EXPAND:
                if (pr.panel) {
                    zone = 1;
                    applyFocus(true);
                }
                break;
            case PanelView.A_SETTINGS:
                openLauncherSettings();
                break;
            case PanelView.A_SYSTEM:
                openSettings(Settings.ACTION_SETTINGS);
                break;
            case PanelView.A_WIFI:
                openSettings(Settings.ACTION_WIFI_SETTINGS);
                break;
            case PanelView.A_MEM:
                freeMemory();
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private void refreshInfo() {
        Date now = new Date();
        boolean h24 = pr.timeFmt == 0 ? DateFormat.is24HourFormat(this) : pr.timeFmt == 2;
        curTime = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(now);
        curDate = new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now);
        String ram = "", st = "";
        int net = 0, usb = 0;
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            ram = "RAM " + gb(mi.availMem) + " free";
        } catch (Throwable ignored) {
            // leave blank
        }
        try {
            st = "Storage " + gb(new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes()) + " free";
        } catch (Throwable ignored) {
            // leave blank
        }
        try {
            NetworkInfo ni = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) net = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? 2 : 1;
        } catch (Throwable ignored) {
            // permission missing: show offline
        }
        try {
            File[] vols = new File("/storage").listFiles();
            if (vols != null) {
                for (File f : vols) {
                    String n = f.getName();
                    if (!n.equals("emulated") && !n.equals("self") && f.isDirectory()) usb++;
                }
            }
        } catch (Throwable ignored) {
            // no USB info
        }
        panel.setInfo(curTime, curDate);
        panel.setStats(ram, st, net, usb);
        saver.setInfo(curTime, curDate);
    }

    private static String gb(long bytes) {
        return String.format(Locale.US, "%.1f GB", bytes / 1073741824.0);
    }

    private long availMem() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.availMem;
    }

    /** Asks Android to stop background apps so low-RAM boxes feel snappy again. */
    private void freeMemory() {
        toast("Freeing memory…");
        final long before = availMem();
        exec.execute(() -> {
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                PackageManager pm = getPackageManager();
                Set<String> pk = new HashSet<>();
                for (ResolveInfo ri : pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                    pk.add(ri.activityInfo.packageName);
                }
                for (ResolveInfo ri : pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
                    pk.add(ri.activityInfo.packageName);
                }
                pk.remove(getPackageName());
                for (String s : pk) am.killBackgroundProcesses(s);
            } catch (Throwable ignored) {
                // best effort
            }
            System.gc();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                // fine
            }
            final long after = availMem();
            runOnUiThread(() -> {
                refreshInfo();
                long mb = (after - before) / 1048576L;
                toast(mb > 20 ? "Freed about " + mb + " MB" : "Memory is already clean");
            });
        });
    }

    // =================================================================== screensaver

    private void scheduleSaver() {
        h.removeCallbacks(idleRun);
        if (pr.saver > 0 && !saverOn) h.postDelayed(idleRun, Prefs.SAVER_MIN[pr.saver] * 60000L);
    }

    private void startSaver() {
        if (sheet != null || busy) {
            scheduleSaver();
            return;
        }
        saverOn = true;
        saver.start(wallBmp, curTime, curDate);
    }

    private void wakeSaver() {
        if (!saverOn) return;
        saverOn = false;
        saver.stop();
        scheduleSaver();
    }

    // =================================================================== actions

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void launch(final TileView v) {
        if (v.tile == null || sheet != null || busy) return;
        busy = true;
        v.press(() -> {
            busy = false;
            PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(v.tile.pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(v.tile.pkg);
            if (i == null) {
                toast(v.tile.label + " is not installed");
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                Bundle o = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle();
                startActivity(i, o);
            } catch (Exception e) {
                try {
                    startActivity(i);
                } catch (Exception e2) {
                    toast("Couldn't open " + v.tile.label);
                }
            }
        });
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                toast("Settings are not available on this device");
            }
        }
    }

    // ---- reorder mode (tiles jiggle like on tvOS)

    private void startEdit() {
        if (idx >= tiles.size() || editing) return;
        editing = true;
        TileView v = tiles.get(idx);
        jiggle = ObjectAnimator.ofFloat(v, View.ROTATION, -1.3f, 1.3f);
        jiggle.setDuration(130);
        jiggle.setRepeatCount(ValueAnimator.INFINITE);
        jiggle.setRepeatMode(ValueAnimator.REVERSE);
        jiggle.start();
        toast("Use the arrow keys to move it, OK when done");
    }

    private void stopEdit() {
        if (!editing) return;
        editing = false;
        if (jiggle != null) {
            jiggle.cancel();
            jiggle = null;
        }
        for (TileView v : tiles) {
            if (v.getRotation() != 0f) v.animate().rotation(0f).setDuration(Spring.dur(200)).start();
        }
    }

    private void moveEditing(int dx, int dy) {
        int n = tiles.size();
        int j = idx + dx + dy * cols;
        if (idx >= n || j < 0 || j >= n) return;
        if (dx != 0 && j / cols != idx / cols) return;           // don't wrap sideways across rows
        Collections.swap(tiles, idx, j);
        idx = j;
        layoutGrid(true);
        scrollGridTo(idx / cols, true);
        positionBand(true);
        saveOrder();
    }

    private void removeTile(int i) {
        if (i < 0 || i >= tiles.size()) return;
        final TileView v = tiles.remove(i);
        if (focusedView == v) focusedView = null;
        store.deleteFiles(v.tile);
        artCache.remove(v.tile.id);
        saveOrder();
        v.setFocused(false, false);
        v.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(Spring.dur(220)).withEndAction(new Runnable() {
            @Override
            public void run() {
                grid.removeView(v);
            }
        }).start();
        idx = Math.min(i, tiles.size());
        layoutGrid(true);
        applyFocus(true);
    }

    // =================================================================== sheets

    private Bitmap blurSnapshot() {
        try {
            int w = ui.getWidth(), hh = ui.getHeight();
            if (w <= 0 || hh <= 0) return null;
            int sw = 160, sh = Math.max(1, Math.round(160f * hh / w));
            Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(sw / (float) w, sh / (float) hh);
            ui.draw(c);
            int[] px = new int[sw * sh];
            b.getPixels(px, 0, sw, 0, 0, sw, sh);
            TileArt.boxBlur(px, sw, sh, 3, 2);
            b.setPixels(px, 0, sw, 0, 0, sw, sh);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private void showSheet(String title, String sub, int widthUnits, boolean preview, Sheet.Listener l,
                           ArrayList<Sheet.Item> items, int sel) {
        Sheet s = new Sheet(this, title, sub, widthUnits, preview, blurSnapshot(), l);
        sheet = s;
        s.setItems(items, sel);
        s.show(content);
    }

    private void dismissSheet() {
        if (sheet != null) {
            sheet.dismiss();
            sheet = null;
            scheduleSaver();
        }
    }

    // ---- "+" tile menu

    private void openAddMenu() {
        if (sheet != null) return;
        ArrayList<Sheet.Item> items = new ArrayList<>();
        items.add(new Sheet.Item("Add App", null, null, Sheet.K_PLAIN, 0));
        items.add(new Sheet.Item("Launcher Settings", null, null, Sheet.K_PLAIN, 1));
        items.add(new Sheet.Item("Android Settings", null, null, Sheet.K_PLAIN, 2));
        showSheet("Frost", null, 860, false, new Sheet.Listener() {
            @Override
            public void onPick(Sheet.Item it) {
                dismissSheet();
                int code = (Integer) it.tag;
                if (code == 0) openAppPicker();
                else if (code == 1) openLauncherSettings();
                else openSettings(Settings.ACTION_SETTINGS);
            }

            @Override
            public void onBack() {
                dismissSheet();
            }
        }, items, 0);
    }

    // ---- options menu for a tile

    private void openOptions(final int i) {
        if (sheet != null || i < 0 || i >= tiles.size()) return;
        final TileStore.Tile t = tiles.get(i).tile;
        ArrayList<Sheet.Item> items = new ArrayList<>();
        items.add(new Sheet.Item("Open", null, null, Sheet.K_PLAIN, 0));
        items.add(new Sheet.Item("Change Icon", null, null, Sheet.K_PLAIN, 1));
        items.add(new Sheet.Item("Move", null, null, Sheet.K_PLAIN, 2));
        items.add(new Sheet.Item("App Info", null, null, Sheet.K_PLAIN, 3));
        items.add(new Sheet.Item("Remove from Home", null, null, Sheet.K_PLAIN, 4));
        showSheet(t.label, null, 860, false, new Sheet.Listener() {
            @Override
            public void onPick(Sheet.Item it) {
                dismissSheet();
                int code = (Integer) it.tag;
                zone = 0;
                idx = i;
                if (code == 0) {
                    launch(tiles.get(i));
                } else if (code == 1) {
                    pendingPkg = t.pkg;
                    pendingLabel = t.label;
                    pendingIcon = TileArt.iconBitmap(HomeActivity.this, t.pkg);
                    if (pendingIcon != null) {
                        pendingIcon = Bitmap.createScaledBitmap(pendingIcon, 96, 96, true);
                    }
                    openImagePicker(0);
                } else if (code == 2) {
                    applyFocus(true);
                    startEdit();
                } else if (code == 3) {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + t.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        toast("App info is not available");
                    }
                } else if (code == 4) {
                    removeTile(i);
                }
            }

            @Override
            public void onBack() {
                dismissSheet();
            }
        }, items, 0);
    }

    // ---- step 1 of adding an app: choose the app

    private void openAppPicker() {
        if (sheet != null || loadingApps) return;
        loadingApps = true;
        toast("Loading apps…");
        final Set<String> seen = new HashSet<>();
        seen.add(getPackageName());
        for (TileView v : tiles) seen.add(v.tile.pkg);
        exec.execute(() -> {
            final ArrayList<Sheet.Item> items = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                List<ResolveInfo> all = new ArrayList<>();
                all.addAll(pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
                all.addAll(pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
                for (ResolveInfo ri : all) {
                    String pkg = ri.activityInfo.packageName;
                    if (!seen.add(pkg)) continue;
                    Bitmap ic = null;
                    try {
                        ic = TileArt.drawableToBitmap(ri.loadIcon(pm), 96);
                    } catch (Throwable ignored) {
                        // no icon, the row simply has none
                    }
                    items.add(new Sheet.Item(String.valueOf(ri.loadLabel(pm)), null, ic, Sheet.K_PLAIN, pkg));
                }
                Collections.sort(items, new Comparator<Sheet.Item>() {
                    @Override
                    public int compare(Sheet.Item a, Sheet.Item b) {
                        return a.title.compareToIgnoreCase(b.title);
                    }
                });
            } catch (Throwable ignored) {
                // show whatever we have
            }
            runOnUiThread(() -> {
                loadingApps = false;
                if (sheet != null) return;
                if (items.isEmpty()) {
                    toast("No more apps to add");
                    return;
                }
                showSheet("Add App", "Choose an app", 860, false, new Sheet.Listener() {
                    @Override
                    public void onPick(Sheet.Item it) {
                        pendingPkg = (String) it.tag;
                        pendingLabel = it.title;
                        pendingIcon = it.icon;
                        dismissSheet();
                        openImagePicker(0);
                    }

                    @Override
                    public void onBack() {
                        dismissSheet();
                    }
                }, items, 0);
            });
        });
    }

    // ---- image picker (tile icons AND wallpapers). Built in: many TV boxes have no system file picker.

    private String storagePerm() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES"
                : "android.permission.READ_EXTERNAL_STORAGE";
    }

    private boolean hasStoragePerm() {
        return checkSelfPermission(storagePerm()) == PackageManager.PERMISSION_GRANTED;
    }

    /** Fonts are not pictures: on Android 11+ the picture permission cannot read them, so ask for "All files access". */
    private void askAllFilesAccess() {
        wantFontPicker = true;
        toast("Allow \"All files access\" for Frost, then come back");
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                wantFontPicker = false;
                showFileSheet();                           // no such screen on this box: just try anyway
            }
        }
    }

    private void openImagePicker(int mode) {
        pickMode = mode;
        if (mode == 2 && Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) showFileSheet();
            else askAllFilesAccess();
            return;
        }
        if (!hasStoragePerm()) {
            requestPermissions(new String[]{storagePerm()}, REQ_STORAGE);
            return;
        }
        showFileSheet();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (rc == REQ_STORAGE && sheet == null) showFileSheet();
    }

    private File startDir() {
        String last = getSharedPreferences("ui", MODE_PRIVATE).getString(pickMode == 2 ? "lastFontDir" : "lastDir", null);
        if (last != null) {
            File f = new File(last);
            if (f.isDirectory()) return f;
        }
        File ext = Environment.getExternalStorageDirectory();
        File dl = new File(ext, "Download");
        return dl.isDirectory() ? dl : ext;
    }

    private Sheet.Item ownItem() {
        return new Sheet.Item("Use the app's own icon", "No custom image", pendingIcon, Sheet.K_OWN, null);
    }

    private void showFileSheet() {
        if (sheet != null) return;
        File dir = startDir();
        ArrayList<Sheet.Item> first = new ArrayList<>();
        if (pickMode == 0) first.add(ownItem());
        else first.add(new Sheet.Item("Loading…", null, null, Sheet.K_PLAIN, null));
        String title = pickMode == 0 ? "Icon for " + pendingLabel : (pickMode == 1 ? "Choose Background" : "Choose Font");
        showSheet(title, dir.getPath(), 1380, true, new Sheet.Listener() {
            @Override
            public void onPick(Sheet.Item it) {
                if (it.kind == Sheet.K_OWN) {
                    dismissSheet();
                    startBuild(pendingPkg, pendingLabel, null);
                } else if (it.kind == Sheet.K_FILE) {
                    File f = (File) it.tag;
                    File parent = f.getParentFile();
                    if (parent != null) {
                        getSharedPreferences("ui", MODE_PRIVATE).edit()
                                .putString(pickMode == 2 ? "lastFontDir" : "lastDir", parent.getPath()).apply();
                    }
                    dismissSheet();
                    if (pickMode == 0) startBuild(pendingPkg, pendingLabel, f.getPath());
                    else if (pickMode == 1) setWallpaperFrom(f.getPath());
                    else setFontFrom(f.getPath(), f.getName());
                } else if (it.tag instanceof File) {
                    browse((File) it.tag);
                }
            }

            @Override
            public void onBack() {
                dismissSheet();
            }
        }, first, 0);
        browse(dir);
    }

    private void browse(final File dir) {
        final Sheet s = sheet;
        if (s == null) return;
        exec.execute(() -> {
            final ArrayList<Sheet.Item> items = listDir(dir);
            runOnUiThread(() -> {
                if (sheet != s) return;
                s.setSubtitle(dir.getPath());
                s.setItems(items, 0);
            });
        });
    }

    private ArrayList<Sheet.Item> listDir(File dir) {
        ArrayList<Sheet.Item> out = new ArrayList<>();
        if (pickMode == 0) out.add(ownItem());
        File parent = dir.getParentFile();
        if (parent != null) {
            out.add(new Sheet.Item("Parent folder", parent.getPath(), null, Sheet.K_UP, parent));
        }
        if (!dir.getPath().equals("/storage")) {
            out.add(new Sheet.Item("USB & storage devices", "/storage", null, Sheet.K_DIR, new File("/storage")));
        }
        File[] fs = dir.listFiles();
        if (fs != null) {
            Arrays.sort(fs, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (File f : fs) {
                String n = f.getName();
                if (n.startsWith(".") || n.equals("self")) continue;
                if (f.isDirectory()) out.add(new Sheet.Item(n, null, null, Sheet.K_DIR, f));
            }
            for (File f : fs) {
                if (!f.isFile()) continue;
                String l = f.getName().toLowerCase(Locale.ROOT);
                boolean ok = pickMode == 2 ? FontTools.isFontName(l)
                        : (l.endsWith(".png") || l.endsWith(".webp") || l.endsWith(".jpg") || l.endsWith(".jpeg"));
                if (ok) out.add(new Sheet.Item(f.getName(), (f.length() / 1024) + " KB", null, Sheet.K_FILE, f));
            }
        }
        return out;
    }

    // ---- step 3 for icons: bake the tile and add it to the grid

    private void startBuild(final String pkg, final String lbl, final String png) {
        final TileStore.Tile t = new TileStore.Tile(TileStore.idFor(pkg), pkg, lbl);
        toast("Creating icon…");
        exec.execute(() -> {
            final String err = TileArt.build(HomeActivity.this, store, t, png);
            runOnUiThread(() -> {
                if (err != null) toast(err);
                else integrate(t);
            });
        });
    }

    private void integrate(TileStore.Tile t) {
        for (int i = 0; i < tiles.size(); i++) {           // "Change Icon" on an existing tile
            TileView old = tiles.get(i);
            if (old.tile.id.equals(t.id)) {
                artCache.remove(t.id);
                zone = 0;
                idx = i;
                loadArt(old);
                applyFocus(true);
                return;
            }
        }
        TileView v = addTileView(t);
        saveOrder();
        layoutGrid(true);
        v.spawn();
        zone = 0;
        idx = tiles.size() - 1;
        applyFocus(true);
    }

    // =================================================================== wallpaper

    private void setWallpaperFrom(final String path) {
        toast("Setting background…");
        exec.execute(() -> {
            final boolean ok = WallTools.importImage(HomeActivity.this, path);
            runOnUiThread(() -> {
                if (ok) reloadWallpaper(true);
                else toast("Couldn't use that picture");
            });
        });
    }

    // =================================================================== font

    private void setFontFrom(final String path, final String name) {
        toast("Applying font…");
        exec.execute(() -> {
            final boolean ok = FontTools.importFont(HomeActivity.this, path, name);
            runOnUiThread(() -> {
                if (!ok) {
                    toast("Couldn't use that font file");
                    return;
                }
                pr.font = Prefs.FONT_CUSTOM;
                pr.save(HomeActivity.this);
                rebuildUi();
                toast("Font applied");
            });
        });
    }

    private void reloadWallpaper(final boolean fade) {
        final int blur = pr.blur;
        exec.execute(() -> {
            final Bitmap[] b = WallTools.build(HomeActivity.this, blur);
            runOnUiThread(() -> {
                if (b == null || wall == null) return;
                wallBmp = b[0];
                glassBmp = b[1];
                wall.setBitmap(wallBmp, fade);
                wall.setDim(pr.dim);
                panel.setGlass(glassBmp);
                band.setGlass(glassBmp);
            });
        });
    }

    // =================================================================== launcher settings

    private ArrayList<Sheet.Item> buildSettingsItems() {
        ArrayList<Sheet.Item> l = new ArrayList<>();
        l.add(Sheet.Item.header("LAYOUT"));
        l.add(Sheet.Item.adj("Apps per row", valueOf(S_COLS), S_COLS));
        l.add(Sheet.Item.adj("Icon size", valueOf(S_ICON), S_ICON));
        l.add(Sheet.Item.adj("App names", valueOf(S_NAMES), S_NAMES));
        l.add(Sheet.Item.adj("Name size", valueOf(S_NAMESZ), S_NAMESZ));
        l.add(Sheet.Item.adj("Interface scale", valueOf(S_UI), S_UI));
        l.add(Sheet.Item.header("FONT"));
        l.add(Sheet.Item.adj("Font", valueOf(S_FONT), S_FONT));
        l.add(Sheet.Item.act("Choose font file (USB or local)", "", S_FONT_PICK));
        if (FontTools.has(this)) l.add(Sheet.Item.act("Remove custom font", FontTools.label(this), S_FONT_REMOVE));
        l.add(Sheet.Item.header("BACKGROUND"));
        l.add(Sheet.Item.act("Choose background (USB or local)", "", S_BG_PICK));
        l.add(Sheet.Item.act("Remove background", "", S_BG_REMOVE));
        l.add(Sheet.Item.adj("Background dim", valueOf(S_DIM), S_DIM));
        l.add(Sheet.Item.adj("Background blur", valueOf(S_BLUR), S_BLUR));
        l.add(Sheet.Item.header("BARS"));
        l.add(Sheet.Item.adj("Top panel", valueOf(S_PANEL), S_PANEL));
        l.add(Sheet.Item.adj("Selection bar", valueOf(S_BAND), S_BAND));
        l.add(Sheet.Item.adj("Time format", valueOf(S_TIME), S_TIME));
        l.add(Sheet.Item.header("EFFECTS"));
        l.add(Sheet.Item.adj("Animation speed", valueOf(S_SPEED), S_SPEED));
        l.add(Sheet.Item.adj("Tile tilt", valueOf(S_TILT), S_TILT));
        l.add(Sheet.Item.adj("Screensaver", valueOf(S_SAVER), S_SAVER));
        l.add(Sheet.Item.header("SYSTEM"));
        l.add(Sheet.Item.act("Android settings", "", S_SYSTEM));
        l.add(Sheet.Item.act("Reset to defaults", "", S_RESET));
        l.add(Sheet.Item.act("About", "Frost " + VERSION, S_ABOUT));
        return l;
    }

    private String valueOf(int id) {
        switch (id) {
            case S_COLS:
                return String.valueOf(pr.cols);
            case S_ICON:
                return pr.iconScale + "%";
            case S_NAMES:
                return new String[]{"Off", "On focus", "Always"}[pr.names];
            case S_NAMESZ:
                return pr.nameScale + "%";
            case S_UI:
                return pr.uiScale + "%";
            case S_DIM:
                return pr.dim + "%";
            case S_BLUR:
                return new String[]{"Off", "Soft", "Strong"}[pr.blur];
            case S_FONT:
                return pr.font == Prefs.FONT_CUSTOM ? FontTools.label(this)
                        : new String[]{"System", "Condensed", "Serif", "Monospace"}[pr.font];
            case S_BAND:
                return pr.band ? "On" : "Off";
            case S_PANEL:
                return pr.panel ? "On" : "Off";
            case S_TIME:
                return new String[]{"System", "12-hour", "24-hour"}[pr.timeFmt];
            case S_SPEED:
                return new String[]{"Off", "Fast", "Normal", "Relaxed"}[pr.speed];
            case S_TILT:
                return pr.tilt ? "On" : "Off";
            case S_SAVER:
                return pr.saver == 0 ? "Off" : Prefs.SAVER_MIN[pr.saver] + " min";
            default:
                return "";
        }
    }

    private static int step(int v, int dir, int min, int max, int st, boolean wrap) {
        int n = v + dir * st;
        if (n > max) return wrap ? min : max;
        if (n < min) return wrap ? max : min;
        return n;
    }

    private void adjust(int id, int dir, boolean wrap) {
        switch (id) {
            case S_COLS:
                pr.cols = step(pr.cols, dir, 3, 6, 1, wrap);
                break;
            case S_ICON:
                pr.iconScale = step(pr.iconScale, dir, 60, 150, 10, wrap);
                break;
            case S_NAMES:
                pr.names = step(pr.names, dir, 0, 2, 1, wrap);
                break;
            case S_NAMESZ:
                pr.nameScale = step(pr.nameScale, dir, 70, 150, 10, wrap);
                break;
            case S_UI:
                pr.uiScale = step(pr.uiScale, dir, 70, 130, 5, wrap);
                break;
            case S_DIM:
                pr.dim = step(pr.dim, dir, 0, 80, 5, wrap);
                break;
            case S_BLUR:
                pr.blur = step(pr.blur, dir, 0, 2, 1, wrap);
                break;
            case S_FONT:
                pr.font = step(pr.font, dir, 0, FontTools.has(this) ? Prefs.FONT_CUSTOM : 3, 1, wrap);
                break;
            case S_BAND:
                pr.band = !pr.band;
                break;
            case S_PANEL:
                pr.panel = !pr.panel;
                if (!pr.panel) toast("Panel off. Hold OK on the + tile to reach Settings.");
                break;
            case S_TIME:
                pr.timeFmt = step(pr.timeFmt, dir, 0, 2, 1, wrap);
                break;
            case S_SPEED:
                pr.speed = step(pr.speed, dir, 0, Prefs.SPEEDS.length - 1, 1, wrap);
                Spring.speed = Prefs.SPEEDS[pr.speed];
                break;
            case S_TILT:
                pr.tilt = !pr.tilt;
                break;
            case S_SAVER:
                pr.saver = step(pr.saver, dir, 0, Prefs.SAVER_MIN.length - 1, 1, wrap);
                break;
            default:
                break;
        }
    }

    private void openLauncherSettings() {
        if (sheet != null) return;
        settingsOld = pr.copy();
        showSheet("Launcher Settings", "Changes apply when you close this menu", 980, false, new Sheet.Listener() {
            @Override
            public void onPick(Sheet.Item it) {
                int id = (Integer) it.tag;
                if (id < 100) {
                    adjust(id, 1, true);
                    it.value = valueOf(id);
                    if (sheet != null) sheet.refresh();
                } else if (id == S_BG_PICK) {
                    closeSettings();
                    openImagePicker(1);
                } else if (id == S_FONT_PICK) {
                    closeSettings();
                    openImagePicker(2);
                } else if (id == S_FONT_REMOVE) {
                    FontTools.remove(HomeActivity.this);
                    if (pr.font == Prefs.FONT_CUSTOM) pr.font = 0;
                    if (sheet != null) {
                        refreshSettingValues();
                        sheet.refresh();
                    }
                    toast("Custom font removed");
                } else if (id == S_BG_REMOVE) {
                    //noinspection ResultOfMethodCallIgnored
                    WallTools.file(HomeActivity.this).delete();
                    reloadWallpaper(true);
                    toast("Background removed");
                } else if (id == S_SYSTEM) {
                    closeSettings();
                    openSettings(Settings.ACTION_SETTINGS);
                } else if (id == S_RESET) {
                    pr.reset();
                    Spring.speed = Prefs.SPEEDS[pr.speed];
                    if (sheet != null) {
                        refreshSettingValues();
                        sheet.refresh();
                    }
                    toast("Settings reset");
                } else if (id == S_ABOUT) {
                    toast("Frost Launcher " + VERSION);
                }
            }

            @Override
            public void onAdjust(Sheet.Item it, int dir) {
                int id = (Integer) it.tag;
                adjust(id, dir, false);
                it.value = valueOf(id);
            }

            @Override
            public void onBack() {
                closeSettings();
            }
        }, buildSettingsItems(), 1);
    }

    private void refreshSettingValues() {
        // the visible item list is owned by the sheet; rebuild values through a fresh list
        Sheet s = sheet;
        if (s == null) return;
        s.setItems(buildSettingsItems(), 1);
    }

    private void closeSettings() {
        Prefs old = settingsOld;
        settingsOld = null;
        dismissSheet();
        if (old != null) applySettings(old);
    }

    private void applySettings(Prefs old) {
        pr.save(this);
        Spring.speed = Prefs.SPEEDS[pr.speed];
        boolean layout = !pr.sameLayout(old);
        if (layout) rebuildUi();
        else refreshInfo();
        if (!pr.sameWall(old)) {
            wall.setDim(pr.dim);
            reloadWallpaper(true);
        }
        scheduleSaver();
    }
}
