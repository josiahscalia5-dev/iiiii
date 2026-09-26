package com.treasurerun.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Renders walk-cycle contact sheets of every rig view for visual review (build/harness/rig_*.png). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RigPreviewTest {
    static Game.Host host() { return new Sim(); }

    static void sheet(String name, Rig.Style st, Rig.View[] views, float[] facings, boolean[] mirrors, float amt, float run) throws Exception {
        int cols = 8, rows = views.length; float H = 300;
        Bitmap b = Bitmap.createBitmap(cols * 200, rows * 360, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.rgb(150, 110, 100));
        Paint grid = new Paint(); grid.setColor(Color.argb(60, 255, 255, 255));
        Rig rig = new Rig(); Game.Host h = host();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Rig.Pose p = new Rig.Pose();
                p.x = col * 200 + 100; p.y = row * 360 + 330; p.scale = H; p.fore = 0.55f;
                p.view = views[row]; p.mirror = mirrors[row]; p.facing = facings[row];
                p.phase = col / (float) cols; p.amt = amt; p.run = run;
                c.drawLine(col * 200, p.y, col * 200 + 200, p.y, grid);
                rig.draw(c, st, p, h);
            }
        }
        File out = new File(System.getProperty("treasurerun.out", "build/harness")); out.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(out, name))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test
    public void boySheets() throws Exception {
        Rig.View[] v = {Rig.BOY_BACK, Rig.BOY_TQ, Rig.BOY_TQ, Rig.BOY_FRONT, Rig.BOY_FRONT};
        float[] f = {-90, -40, -140, 18, 162};
        boolean[] m = {false, false, true, false, true};
        sheet("rig_boy_walk.png", Rig.BOY, v, f, m, 1f, 0f);
        sheet("rig_boy_idle.png", Rig.BOY, v, f, m, 0f, 0f);
    }

    @Test
    public void guardSheets() throws Exception {
        Rig.View[] v = {Rig.GUARD_TQB, Rig.GUARD_TQF, Rig.GUARD_FRONT, Rig.GUARD_TQF};
        float[] f = {-40, 145, 90, 35};
        boolean[] m = {false, false, false, true};
        sheet("rig_guard_walk.png", Rig.GUARD, v, f, m, 1f, 0f);
    }
}
