package com.treasurerun.game;

import android.graphics.Bitmap;
import java.io.File;
import java.util.*;

/** Host for the desktop harness: assets straight from app/assets via ImageIO. */
public class DesktopHost implements Host {
    final String root;
    final Map<String, Bitmap> cache = new HashMap<>();
    public final List<String> events = new ArrayList<>();
    public DesktopHost(String root) { this.root = root; }
    public synchronized Bitmap bitmap(String p) {
        if (cache.containsKey(p)) return cache.get(p);
        Bitmap b = null;
        try { java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(new File(root, p)); if (im != null) b = new Bitmap(im); } catch (Exception e) { }
        cache.put(p, b);
        return b;
    }
    public synchronized void forget(String prefix) { cache.keySet().removeIf(k -> k.startsWith(prefix)); }
    public void onGameFinished(int c, int g) { events.add("finished " + c + " " + g); }
    public void onGamePause() { events.add("pause"); }
    public void haptic(int k) { }
    public void log(String m) { System.out.println("[game] " + m); }
}
