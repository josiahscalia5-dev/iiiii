package com.treasurerun.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Zoomed walk-cycle strips of the boy (8 directions) and a guard, for animation review. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AnimSheetTest {
    static final int CW = 300, CH = 420, FR = 8;

    static void crop(Sim s, Canvas dst, int col, int row, float wx, float wy, float hWorld) {
        s.game.render(s.canvas);
        float cx = s.game.screenX(wx), cy = s.game.screenY(wy - hWorld * 0.45f);
        float half = s.game.camScale * hWorld * 0.62f;
        Rect src = new Rect(Math.round(cx - half * CW / CH), Math.round(cy - half), Math.round(cx + half * CW / CH), Math.round(cy + half));
        dst.drawBitmap(s.frame, src, new Rect(col * CW, row * CH, (col + 1) * CW, (row + 1) * CH), null);
    }

    static void save(Bitmap b, String n) throws Exception {
        Sim.OUT.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(Sim.OUT, n))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test
    public void boyEightDirections() throws Exception {
        float[][] dirs = {{0, -1}, {0.7f, -0.7f}, {1, 0}, {0.7f, 0.7f}, {0, 1}, {-0.7f, 0.7f}, {-1, 0}, {-0.7f, -0.7f}};
        Bitmap sheet = Bitmap.createBitmap(CW * FR, CH * dirs.length, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sheet); c.drawColor(Color.BLACK);
        for (int d = 0; d < dirs.length; d++) {
            Sim s = new Sim().start(1); s.playIntro(); s.game.guards.clear();
            Boy b = s.game.boy;
            b.x = 520; b.y = 1950; b.facing = -90; s.game.camReady = false;
            s.holdDrag(540, 1800, 540 + dirs[d][0] * 220, 1800 + dirs[d][1] * 220);
            s.step(0.45f);
            for (int f = 0; f < FR; f++) { crop(s, c, f, d, b.x, b.y, b.H() * s.game.nav.sx(b.y)); s.step(1 / 15f); }
            s.release(540, 1800);
        }
        save(sheet, "anim_boy_8dir.png");
    }

    @Test
    public void boyStartTurnStop() throws Exception {
        Bitmap sheet = Bitmap.createBitmap(CW * FR, CH * 3, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sheet); c.drawColor(Color.BLACK);
        Sim s = new Sim().start(1); s.playIntro(); s.game.guards.clear();
        Boy b = s.game.boy; b.x = 520; b.y = 1950; s.game.camReady = false; s.step(0.5f);
        // row 0: standing -> start walking right
        s.down(0, 540, 1800);
        for (int f = 0; f < FR; f++) { s.move(0, 540 + 30 * (f + 1), 1800); crop(s, c, f, 0, b.x, b.y, b.H() * s.game.nav.sx(b.y)); s.step(1 / 15f); }
        // row 1: reverse to the left while moving (turn)
        for (int f = 0; f < FR; f++) { s.move(0, 540 - 30 * (f + 1), 1800); crop(s, c, f, 1, b.x, b.y, b.H() * s.game.nav.sx(b.y)); s.step(1 / 15f); }
        s.step(0.4f);
        // row 2: release -> ease to a stop
        s.up(0, 300, 1800);
        for (int f = 0; f < FR; f++) { crop(s, c, f, 2, b.x, b.y, b.H() * s.game.nav.sx(b.y)); s.step(1 / 12f); }
        save(sheet, "anim_boy_start_turn_stop.png");
    }

    @Test
    public void guardWalk() throws Exception {
        Bitmap sheet = Bitmap.createBitmap(CW * FR, CH * 3, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sheet); c.drawColor(Color.BLACK);
        Sim s = new Sim().start(1); s.playIntro();
        Guard g = s.game.guards.get(0);
        s.game.boy.x = 520; s.game.boy.y = 1700; s.game.boy.escaped = true;  // park the camera near him, invisible to guards
        for (int row = 0; row < 3; row++) {
            s.until(() -> g.waitT <= 0 && g.speed > 100, 12f);
            for (int f = 0; f < FR; f++) { crop(s, c, f, row, g.x, g.y, g.H() * s.game.nav.sx(g.y)); s.step(1 / 15f); }
            s.step(row == 0 ? 2.5f : 1.5f);
        }
        save(sheet, "anim_guard_walk.png");
    }
}
