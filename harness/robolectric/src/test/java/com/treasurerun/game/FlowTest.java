package com.treasurerun.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLog;

/** The real app on the Android framework: menus -> game view -> real touch events -> level complete -> next level. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class FlowTest {
    MainActivity act; View root; Bitmap frame; Canvas canvas;

    void frames(int n) {
        for (int i = 0; i < n; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS);
            root.draw(canvas);
        }
    }
    void shot(String name) throws Exception {
        root.draw(canvas);
        File out = new File(System.getProperty("treasurerun.out", "build/harness")); out.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(out, name))) { frame.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }
    void touch(View v, int action, float x, float y, long down) {
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        v.dispatchTouchEvent(e);
        e.recycle();
    }
    void tap(View v, float x, float y) {
        long d = SystemClock.uptimeMillis();
        touch(v, MotionEvent.ACTION_DOWN, x, y, d);
        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS);
        touch(v, MotionEvent.ACTION_UP, x, y, d);
        shadowOf(Looper.getMainLooper()).idle();
    }
    /** Screen position of a menu hotspot, from ScreenView's layout log. */
    float[] hot(int screen, String id) {
        float[] r = null;
        Pattern p = Pattern.compile("HOT screen=" + screen + " id=" + id + " x=(\\d+) y=(\\d+)");
        for (ShadowLog.LogItem li : ShadowLog.getLogsForTag("TreasureRun")) {
            Matcher m = p.matcher(li.msg);
            if (m.find()) r = new float[]{Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2))};
        }
        return r;
    }
    static Object field(Object o, String n) throws Exception { Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o); }

    @Test
    public void playSwipeCompleteNextLevel() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        act = c.get();
        root = act.getWindow().getDecorView();
        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS);
        frame = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(frame);
        root.draw(canvas);
        ScreenView screens = (ScreenView) field(act, "screens");
        GameView gv = (GameView) field(act, "game");
        // splash: tap PLAY for real
        float[] play = hot(0, "play");
        assertNotNull("play button laid out", play);
        tap(screens, play[0], play[1]);
        assertEquals("game view shown", View.VISIBLE, gv.getVisibility());
        Game g = gv.game;
        assertEquals(1, g.level.number);
        frames(200);                                  // intro walk-in (~3 s)
        assertEquals("playing", Game.PLAY, g.phase);
        shot("flow_game.png");
        // swipe up with real MotionEvents on the game view and hold
        float y0 = g.boy.y;
        long d = SystemClock.uptimeMillis();
        touch(gv, MotionEvent.ACTION_DOWN, 540, 1900, d);
        for (int i = 1; i <= 8; i++) { shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS); touch(gv, MotionEvent.ACTION_MOVE, 540, 1900 - 30 * i, d); root.draw(canvas); }
        frames(40);
        shot("flow_swipe.png");
        touch(gv, MotionEvent.ACTION_UP, 540, 1660, d);
        assertTrue("real swipe moved the boy up: " + y0 + " -> " + g.boy.y, g.boy.y < y0 - 100);
        // pretend the level was escaped: the complete screen comes up
        act.levelComplete(1, 3, 1);
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        root.draw(canvas);
        assertEquals("complete screen", 2, screens.getScreen());
        assertEquals("progress saved", 2, act.getSharedPreferences("progress", 0).getInt("unlocked", 0));
        shot("flow_complete.png");
        float[] next = hot(2, "next");
        assertNotNull("next button laid out", next);
        tap(screens, next[0], next[1]);
        assertEquals("NEXT LEVEL starts level 2", 2, gv.game.level.number);
        frames(60);
        shot("flow_level2.png");
        // finish level 2 as well; the level-select screen shows door 3 unlocked
        act.levelComplete(2, 0, 4);
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        act.onHotspot(2, "home");
        act.onHotspot(0, "levels");
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        assertEquals(3, screens.getScreen());
        shot("flow_levels.png");
        float[] door3 = hot(3, "door3");
        assertNotNull(door3);
        tap(screens, door3[0], door3[1]);
        assertEquals("door 3 opens level 3", 3, gv.game.level.number);
        // play continues from the furthest level
        act.onHotspot(0, "play");
        assertEquals(3, gv.game.level.number);
    }
}
