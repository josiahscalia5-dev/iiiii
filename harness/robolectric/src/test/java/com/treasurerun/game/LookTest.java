package com.treasurerun.game;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Screenshots for visual review (build/harness/look_*.png). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LookTest {
    @Test
    public void level1() {
        Sim s = new Sim().start(1);
        s.step(0.2f); s.shot("look_l1_intro.png");
        s.playIntro(); s.step(0.5f); s.shot("look_l1_play.png");
        s.holdDrag(540, 1900, 540, 1650);
        s.step(0.6f); s.shot("look_l1_walk_up.png");
        s.sheet("look_l1_walk_sheet.png", 4, 2, 0.12f, null);
        s.release(540, 1650);
        System.out.println("boy " + s.game.boy.x + "," + s.game.boy.y + " guard " + s.game.guards.get(0).x + "," + s.game.guards.get(0).y + " state " + s.game.guards.get(0).state);
    }
}
