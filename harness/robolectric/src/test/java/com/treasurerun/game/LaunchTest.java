package com.treasurerun.game;

import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LaunchTest {
    static final File OUT = new File(System.getProperty("treasurerun.out", "build/harness"));

    static void shot(View v, String name) throws Exception {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        OUT.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(OUT, name))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test
    public void launchesToMenu() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
        View root = c.get().getWindow().getDecorView();
        assertNotNull(root);
        System.out.println("decor " + root.getWidth() + "x" + root.getHeight());
        shot(root, "launch_menu.png");
    }
}
