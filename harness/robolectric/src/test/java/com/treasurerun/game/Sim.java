package com.treasurerun.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;

/** Drives a {@link Game} deterministically at 60 fps with scripted touches; records host callbacks; saves frames. */
final class Sim implements Game.Host {
    static final int W = 1080, H = 2400;
    static final File OUT = new File(System.getProperty("treasurerun.out", "build/harness"));
    static final Map<String, Bitmap> CACHE = new HashMap<>();
    final Game game;
    final List<String> events = new ArrayList<>();
    int completed = -1; boolean paused, home;
    float t;                       // simulated clock (s)
    final Bitmap frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(frame);

    Sim() {
        Context ctx = RuntimeEnvironment.getApplication();
        Typeface f = Typeface.createFromAsset(ctx.getAssets(), "fonts/LilitaOne-Regular.ttf");
        game = new Game(this, f);
        game.layout(W, H);
        List<Rect> cut = new ArrayList<>(); cut.add(new Rect(490, 0, 590, 90));
        game.setInsets(new Rect(0, 90, 0, 60), cut, new int[]{80, 80, 80, 80});
    }

    @Override public Bitmap bitmap(String p) {
        synchronized (CACHE) {
            Bitmap b = CACHE.get(p);
            if (b == null) {
                try (InputStream in = RuntimeEnvironment.getApplication().getAssets().open(p)) { b = BitmapFactory.decodeStream(in); }
                catch (Exception e) { throw new RuntimeException("missing asset " + p, e); }
                CACHE.put(p, b);
            }
            return b;
        }
    }
    @Override public void forget(String prefix) { }
    @Override public void sound(int id, float v) { events.add("sound:" + Game.SOUNDS[id]); }
    @Override public void haptic(int kind) { events.add("haptic:" + kind); }
    @Override public void levelComplete(int level, int coins, int gems) { completed = level; events.add("complete:" + level + ":" + coins + ":" + gems); }
    @Override public void pause() { paused = true; }
    @Override public void home() { home = true; }

    Sim start(int level) { game.start(level); return this; }

    void step(float seconds) {
        int n = Math.max(1, Math.round(seconds * 60));
        for (int i = 0; i < n; i++) { t += 1 / 60f; game.update(1 / 60f); }
    }
    /** Steps until the condition holds or the timeout passes; returns whether it held. */
    boolean until(java.util.function.BooleanSupplier c, float timeout) {
        float end = t + timeout;
        while (t < end) { if (c.getAsBoolean()) return true; step(1 / 60f); }
        return c.getAsBoolean();
    }
    void playIntro() { until(() -> game.phase == Game.PLAY, 5f); }

    // ---- touch helpers (screen px) ----
    void down(int id, float x, float y) { game.touchDown(id, x, y, t); }
    void move(int id, float x, float y) { game.touchMove(id, x, y, t); }
    void up(int id, float x, float y) { game.touchUp(id, x, y, t); }
    /** Press, drag to (x1,y1) over dragTime, hold for holdTime, release. */
    void drag(float x0, float y0, float x1, float y1, float dragTime, float holdTime) {
        down(0, x0, y0);
        int n = Math.max(2, Math.round(dragTime * 60));
        for (int i = 1; i <= n; i++) { step(1 / 60f); move(0, x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n); }
        step(holdTime);
        up(0, x1, y1);
    }
    /** Starts a drag and leaves the finger down (steering). */
    void holdDrag(float x0, float y0, float x1, float y1) {
        down(0, x0, y0);
        for (int i = 1; i <= 6; i++) { step(1 / 60f); move(0, x0 + (x1 - x0) * i / 6, y0 + (y1 - y0) * i / 6); }
    }
    void release(float x, float y) { up(0, x, y); }
    void tap(float x, float y) { down(0, x, y); step(0.05f); up(0, x, y); }
    void tapWorld(float wx, float wy) { tap(game.screenX(wx), game.screenY(wy)); }

    /** Steers the boy toward a world point with a held drag until within dist floor units (or timeout). */
    boolean steerTo(float wx, float wy, float dist, float timeout) {
        float x0 = W * 0.3f, y0 = H * 0.75f;
        down(0, x0, y0);
        float end = t + timeout;
        boolean ok = false;
        while (t < end) {
            Boy b = game.boy;
            if (game.nav.floorDist(b.x, b.y, wx, wy) < dist) { ok = true; break; }
            float sx = game.screenX(wx) - game.screenX(b.x), sy = game.screenY(wy) - game.screenY(b.y);
            float l = (float) Math.hypot(sx, sy);
            move(0, x0 + sx / l * W * 0.13f, y0 + sy / l * W * 0.13f);
            step(1 / 60f);
            if (game.phase != Game.PLAY) break;
        }
        up(0, x0, y0);
        return ok;
    }

    File shot(String name) {
        game.render(canvas);
        OUT.mkdirs();
        File f = new File(OUT, name);
        try (FileOutputStream o = new FileOutputStream(f)) { frame.compress(Bitmap.CompressFormat.PNG, 100, o); }
        catch (Exception e) { throw new RuntimeException(e); }
        return f;
    }
    /** Downscaled contact sheet of frames rendered every `every` seconds. */
    void sheet(String name, int cols, int rows, float every, Runnable perFrame) {
        int tw = W / 4, th = H / 4;
        Bitmap s = Bitmap.createBitmap(tw * cols, th * rows, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(s);
        for (int i = 0; i < cols * rows; i++) {
            if (perFrame != null) perFrame.run();
            game.render(canvas);
            c.drawBitmap(frame, new Rect(0, 0, W, H), new Rect((i % cols) * tw, (i / cols) * th, (i % cols + 1) * tw, (i / cols + 1) * th), null);
            step(every);
        }
        OUT.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(OUT, name))) { s.compress(Bitmap.CompressFormat.PNG, 100, o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    boolean heard(String snd) { for (String e : events) if (e.equals("sound:sfx/" + snd + ".wav")) return true; return false; }
}
