package com.treasurerun.game;

import android.graphics.*;
import java.io.File;
import java.util.*;

/**
 * Drives the real Game on a virtual phone screen: scripted touches, fixed 60 fps steps, PNG frames.
 * usage: scripts/harness.sh Sim <scenario> [outdir]
 */
public class Sim {
    final Game g;
    final DesktopHost host;
    final int w, h;
    final String out;
    int shots;
    final List<String> log = new ArrayList<>();

    Sim(int w, int h, String out) {
        this.w = w;
        this.h = h;
        this.out = out;
        new File(out).mkdirs();
        host = new DesktopHost("app/assets");
        g = new Game(host, Typeface.fromFile("app/assets/fonts/LilitaOne-Regular.ttf"));
        g.dp = 2.75f;
        g.safe.set(0, 110, 0, 60);     // status bar / gesture bar of a typical phone
        g.setSize(w, h);
    }

    void step() { g.update(1 / 60f); }

    void run(float sec) { for (int i = 0; i < Math.round(sec * 60); i++) step(); }

    /** run, capturing a frame every `every` seconds */
    void film(String name, float sec, float every) {
        int n = Math.round(sec * 60), k = Math.max(1, Math.round(every * 60));
        for (int i = 0; i < n; i++) {
            step();
            if (i % k == k - 1) shot(name + String.format("_%03d", i / k));
        }
    }

    void shot(String name) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        g.render(c);
        try {
            javax.imageio.ImageIO.write(b.img, "png", new File(out, name + ".png"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String st = name + ": " + g.debugState() + " guards=" + guardsState();
        log.add(st);
        System.out.println(st);
    }

    String guardsState() {
        StringBuilder sb = new StringBuilder();
        for (Guard q : g.guards) sb.append(String.format(Locale.US, "[%d %.0f,%.0f sus=%.2f]", q.state, q.x, q.y, q.sus));
        return sb.toString();
    }

    // touches in screen px
    void down(int id, float x, float y) { g.touchDown(id, x, y); }
    void move(int id, float x, float y) { g.touchMove(id, x, y); }
    void up(int id, float x, float y) { g.touchUp(id, x, y); }
    void tap(float x, float y) { down(0, x, y); step(); up(0, x, y); step(); }
    void tapWorld(float wx, float wy) { tap(g.screenX(wx), g.screenY(wy)); }

    /** hold a finger at a fixed screen point for `sec` */
    void hold(float x, float y, float sec) { down(0, x, y); run(sec); up(0, x, y); }

    boolean waitFor(java.util.function.BooleanSupplier c, float maxSec) {
        for (int i = 0; i < maxSec * 60; i++) { if (c.getAsBoolean()) return true; step(); }
        return c.getAsBoolean();
    }

    public static void main(String[] a) throws Exception {
        String sc = a.length > 0 ? a[0] : "intro";
        String out = a.length > 1 ? a[1] : "build/harness/" + sc;
        Sim s = new Sim(1080, 2340, out);
        Scenarios.run(sc, s);
        java.nio.file.Files.write(new File(out, "log.txt").toPath(), s.log);
    }
}
