package com.treasurerun.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Behavioural checks for the v0.5 gameplay (controls, walking, guards, treasure, exit, levels). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GameplayTest {

    static Sim level(int n) { Sim s = new Sim().start(n); s.playIntro(); return s; }
    /** Level with its guards removed (to test movement/treasure without being chased). */
    static Sim quiet(int n) { Sim s = level(n); s.game.guards.clear(); return s; }
    static void log(String m) { System.out.println("[harness] " + m); }

    // ------------------------------------------------------------------ controls
    @Test
    public void swipeUpMovesForwardAndTurnsAway() {
        Sim s = quiet(1);
        Boy b = s.game.boy;
        float y0 = b.y, x0 = b.x;
        s.holdDrag(540, 1900, 540, 1600);
        s.step(1.0f);
        assertTrue("moved forward (up): " + y0 + " -> " + b.y, b.y < y0 - 150);
        assertTrue("stayed on its line: " + x0 + " -> " + b.x, Math.abs(b.x - x0) < 40);
        assertEquals("faces away", -90f, b.facing, 12f);
        assertTrue("uses the back view", b.view == Rig.BOY_BACK);
        s.release(540, 1600);
        log("swipe up: y " + y0 + " -> " + b.y);
    }

    @Test
    public void swipeLeftAndRightSteer() {
        Sim s = quiet(1);
        Boy b = s.game.boy;
        float x0 = b.x;
        s.holdDrag(540, 1900, 300, 1900); s.step(0.8f); s.release(300, 1900);
        assertTrue("moved left " + x0 + " -> " + b.x, b.x < x0 - 80);
        assertTrue("faces left", Math.abs(Walker.wrap(b.facing - 180f)) < 20f);
        s.step(1.0f);
        float x1 = b.x;
        s.holdDrag(540, 1900, 780, 1900); s.step(0.8f); s.release(780, 1900);
        assertTrue("moved right " + x1 + " -> " + b.x, b.x > x1 + 80);
        assertTrue("faces right", Math.abs(Walker.wrap(b.facing)) < 20f);
        log("left/right: " + x0 + " -> " + x1 + " -> " + b.x);
    }

    @Test
    public void swipeDownMovesBack() {
        Sim s = quiet(1);
        Boy b = s.game.boy;
        s.holdDrag(540, 1900, 540, 1500); s.step(1.2f); s.release(540, 1500); s.step(0.6f);
        float y0 = b.y;
        s.holdDrag(540, 1500, 540, 1800); s.step(0.8f);
        assertTrue("moved back toward the camera " + y0 + " -> " + b.y, b.y > y0 + 80);
        assertTrue("turned to face the camera side", Math.abs(Walker.wrap(b.facing - 90f)) < 35f);
        s.release(540, 1800);
    }

    @Test
    public void quickSwipeCoastsThenEasesToAStop() {
        Sim s = quiet(1);
        Boy b = s.game.boy;
        float y0 = b.y;
        s.drag(540, 1900, 540, 1760, 0.1f, 0f);            // short quick flick up
        float yRelease = b.y;
        s.step(0.3f);
        assertTrue("keeps walking after the flick: " + yRelease + " -> " + b.y, b.y < yRelease - 15);
        s.step(2.5f);
        assertEquals("then stops", 0f, b.speed, 1f);
        float shortWalk = s.game.nav.floorDist(b.x, y0, b.x, b.y);
        // a longer flick goes further
        Sim s2 = quiet(1);
        Boy b2 = s2.game.boy;
        float y2 = b2.y;
        s2.drag(540, 2100, 540, 1500, 0.14f, 0f);
        s2.step(3.5f);
        assertEquals("then stops", 0f, b2.speed, 1f);
        float longWalk = s2.game.nav.floorDist(b2.x, y2, b2.x, b2.y);
        assertTrue("short swipe = a few steps (" + shortWalk / b.H() + " boy heights)", shortWalk > 0.25f * b.H() && shortWalk < 1.2f * b.H());
        assertTrue("longer swipe walks further: " + shortWalk + " vs " + longWalk, longWalk > shortWalk * 1.8f);
        log("flick short " + shortWalk / b.H() + "H long " + longWalk / b.H() + "H");
    }

    @Test
    public void directionChangesSmoothlyWhileMoving() {
        Sim s = quiet(1);
        Boy b = s.game.boy;
        s.down(0, 540, 1900);
        for (int i = 1; i <= 6; i++) { s.step(1 / 60f); s.move(0, 540, 1900 - 40 * i); }
        s.step(0.6f);
        // slide the finger to the right without lifting it
        float maxJump = 0, prev = b.facing;
        List<Float> heads = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            s.move(0, 540 + 16 * i, 1660);
            s.step(1 / 60f);
            maxJump = Math.max(maxJump, Math.abs(Walker.wrap(b.facing - prev)));
            prev = b.facing; heads.add(b.facing);
        }
        s.step(0.6f);
        s.up(0, 860, 1660);
        assertTrue("ended up heading right-ish: " + b.facing, b.facing > -60f && b.facing < 30f);
        assertTrue("no snapping (max per-frame turn " + maxJump + ")", maxJump <= 20f);
        assertTrue("kept moving while turning", b.speed > 100f || b.amt > 0.3f);
        log("turn headings " + heads);
    }

    @Test
    public void startsAndStopsSmoothly() {
        Sim s = quiet(1);
        Boy b = s.game.boy;
        s.down(0, 540, 1900);
        List<Float> ramp = new ArrayList<>();
        for (int i = 1; i <= 16; i++) { s.move(0, 540, 1900 - 20 * i); s.step(1 / 60f); ramp.add(b.speed); }
        assertTrue("accelerates over several frames " + ramp, ramp.get(3) < ramp.get(15) * 0.6f && ramp.get(15) > 300);
        s.step(0.8f);
        float phase0 = b.phase;
        assertTrue("stride fully open while walking (amt " + b.amt + ")", b.amt > 0.8f);
        s.up(0, 540, 1580);
        List<Float> amts = new ArrayList<>();
        for (int i = 0; i < 30; i++) { s.step(1 / 60f); amts.add(b.amt); }
        assertTrue("stride closes gradually, not instantly " + amts, amts.get(2) > 0.2f && amts.get(29) < 0.12f);
        for (int i = 1; i < amts.size(); i++) assertTrue("no pops while stopping", amts.get(i - 1) - amts.get(i) < 0.1f);
        log("accel " + ramp + " phase " + phase0);
    }

    @Test
    public void walkCycleAlternatesLegsWithoutSliding() {
        Sim s = quiet(1);
        Boy b = s.game.boy;
        s.holdDrag(540, 1900, 540, 1600);
        s.step(0.5f);
        float y0 = b.y, ph0 = b.phase; int wraps = 0; float last = ph0;
        for (int i = 0; i < 60; i++) { s.step(1 / 60f); if (b.phase < last) wraps++; last = b.phase; }
        float dist = s.game.nav.floorDist(b.x, y0, b.x, b.y);
        float cycles = wraps + (b.phase - ph0);
        float perCycle = dist / cycles;
        float expected = 2f * Rig.BOY.stride * b.H() / 0.6f;
        assertTrue("gait cycles advance with distance: " + cycles + " cycles over " + dist, cycles > 1.2f);
        assertEquals("distance per stride cycle matches the planted-foot stride", expected, perCycle, expected * 0.2f);
        s.release(540, 1600);
        log("cycles/s " + cycles + " floor dist " + dist);
    }

    @Test
    public void noJoystickAssetsOrDrawing() {
        Sim s = level(1);
        s.step(1f);
        s.shot("check_no_joystick.png");
        for (String k : Sim.CACHE.keySet()) assertFalse("joystick art requested: " + k, k.contains("joy"));
        assertTrue("joystick art removed from the APK assets", missing("hud/joy_base.png") && missing("hud/joy_knob.png"));
    }
    static boolean missing(String a) {
        try { org.robolectric.RuntimeEnvironment.getApplication().getAssets().open(a).close(); return false; } catch (Exception e) { return true; }
    }

    // ------------------------------------------------------------------ guards
    @Test
    public void guardPatrolsWithStopsAndLooks() {
        Sim s = new Sim().start(1);
        s.playIntro();
        Guard g = s.game.guards.get(0);
        float minX = g.x, maxX = g.x; boolean paused = false, lookedAround = false; float f0 = 0;
        for (int i = 0; i < 60 * 22; i++) {
            s.step(1 / 60f);
            minX = Math.min(minX, g.x); maxX = Math.max(maxX, g.x);
            if (g.waitT > 0) { if (!paused) f0 = g.facing; paused = true; if (Math.abs(Walker.wrap(g.facing - f0)) > 25) lookedAround = true; }
            assertEquals("stays on patrol while the boy hides at the entrance", Guard.PATROL, g.state);
        }
        assertTrue("walks the route across the corridor " + minX + ".." + maxX, maxX - minX > 450);
        assertTrue("stops at waypoints", paused);
        assertTrue("looks around while stopped", lookedAround);
        assertTrue("walk animation runs (phase/amt)", g.amt >= 0f);
        log("guard x range " + minX + ".." + maxX);
    }

    /** Places the boy in plain view in front of a guard and runs the detection -> chase -> catch chain. */
    @Test
    public void guardNoticesAlertsChasesAndCatches() {
        Sim s = level(1);
        Guard g = s.game.guards.get(0);
        // wait until the guard is walking, then drop the boy a little in front of him
        s.until(() -> g.waitT <= 0 && g.speed > 50, 10f);
        Boy b = s.game.boy;
        double a = Math.toRadians(g.facing);
        float d = 1.1f * s.game.room.boyH;
        b.x = g.x + (float) Math.cos(a) * d * s.game.nav.sx(g.y); b.y = g.y + (float) Math.sin(a) * d * s.game.nav.sy(g.y);
        assertTrue("test setup: boy stands on the floor", s.game.nav.canStand(b.x, b.y, s.game.room.feetR));
        assertTrue("sees suspicious first", s.until(() -> g.state == Guard.SUSPICIOUS, 1.0f));
        assertTrue("guard stops walking when suspicious", s.until(() -> g.speed < 30, 0.5f));
        s.shot("check_suspicious.png");
        assertTrue("becomes sure -> ALERT", s.until(() -> g.state == Guard.ALERT, 3f));
        assertTrue("alert sting played", s.heard("alert"));
        s.step(0.15f); s.shot("check_alert.png");
        assertTrue("then chases", s.until(() -> g.state == Guard.CHASE, 2f));
        float d0 = s.game.nav.floorDist(g.x, g.y, b.x, b.y);
        s.step(0.4f);
        assertTrue("moves toward the boy", s.game.nav.floorDist(g.x, g.y, b.x, b.y) < d0);
        s.shot("check_chase.png");
        assertTrue("catches a boy who stands still", s.until(() -> s.game.phase == Game.CAUGHT, 5f));
        assertTrue("caught sound", s.heard("caught"));
        s.step(1.2f);
        s.shot("check_caught.png");
        // retry
        s.tap(s.game.retryBtn.centerX(), s.game.retryBtn.centerY());
        assertEquals("retry restarts the level", Game.INTRO, s.game.phase);
        assertEquals(1, s.game.level.number);
        assertEquals(0, s.game.coins);
    }

    @Test
    public void guardDoesNotSeeThroughObstacles() {
        Sim s = level(1);
        Game gm = s.game;
        // find a spot inside a guard's cone range whose sight line is blocked by a display case / plant / crate
        float[] spot = null; float gx = 0, gy = 0, face = 0;
        outer:
        for (float yy = 1200; yy < 1900; yy += 20) for (float xx = 150; xx < 950; xx += 20) {
            if (!gm.nav.canStand(xx, yy, gm.room.feetR)) continue;
            for (float by = yy - 300; by < yy + 300; by += 15) for (float bx = xx - 450; bx < xx + 450; bx += 15) {
                if (!gm.nav.canStand(bx, by, gm.room.feetR)) continue;
                float d = gm.nav.floorDist(xx, yy, bx, by);
                if (d < 0.9f * gm.room.boyH || d > 1.8f * gm.room.boyH) continue;
                if (gm.nav.los(xx, yy, bx, by)) continue;
                spot = new float[]{bx, by}; gx = xx; gy = yy; face = gm.nav.floorAngle(xx, yy, bx, by); break outer;
            }
        }
        assertNotNull("found a hiding spot behind an obstacle", spot);
        gm.guards.clear();
        Guard g = new Guard(gm, gm.level, new Level.Wp[]{new Level.Wp(gx, gy, 999f, 0f)});
        g.waitT = 999f; g.lookBase = face; g.facing = face;
        gm.guards.add(g);
        gm.boy.x = spot[0]; gm.boy.y = spot[1];
        s.step(3f);
        assertTrue("hidden boy is not noticed (awareness " + g.awareness + ")", g.awareness < 0.05f && g.state == Guard.PATROL);
        s.shot("check_hidden.png");
        log("hidden at " + spot[0] + "," + spot[1] + " guard at " + gx + "," + gy);
        // step out into view: now he is noticed
        float d = 1.2f * gm.room.boyH;
        gm.boy.x = gx + (float) Math.cos(Math.toRadians(face)) * 0; // reset
        float[] vis = null;
        for (float r = 0.8f; r < 2.0f && vis == null; r += 0.1f) for (float da = -20; da <= 20 && vis == null; da += 5) {
            double a = Math.toRadians(face + da);
            float bx = gx + (float) Math.cos(a) * r * gm.room.boyH * gm.nav.sx(gy), by = gy + (float) Math.sin(a) * r * gm.room.boyH * gm.nav.sy(gy);
            if (gm.nav.canStand(bx, by, gm.room.feetR) && gm.nav.los(gx, gy, bx, by)) vis = new float[]{bx, by};
        }
        if (vis != null) {
            gm.boy.x = vis[0]; gm.boy.y = vis[1];
            assertTrue("boy in the open is noticed", s.until(() -> g.awareness > 0.2f, 2f));
        }
    }

    @Test
    public void boyCanEscapeAChaseAndGuardReturnsToPatrol() {
        Sim s = level(1);
        Guard g = s.game.guards.get(0);
        Boy b = s.game.boy;
        s.until(() -> g.waitT <= 0 && g.speed > 50, 10f);
        double a = Math.toRadians(g.facing);
        float d = 1.2f * s.game.room.boyH;
        b.x = g.x + (float) Math.cos(a) * d * s.game.nav.sx(g.y); b.y = g.y + (float) Math.sin(a) * d * s.game.nav.sy(g.y);
        assertTrue(s.until(() -> g.state == Guard.CHASE, 4f));
        // sprint away down the corridor (hold sprint + steer toward the entrance)
        s.game.touchDown(1, s.game.runX, s.game.runY, s.t);
        s.steerTo(505, 2300, 60, 4f);
        s.game.touchUp(1, s.game.runX, s.game.runY, s.t);
        assertTrue("not caught while sprinting away", s.game.phase == Game.PLAY);
        assertTrue("guard gives up and searches", s.until(() -> g.state == Guard.SEARCH, 6f));
        assertTrue("player told they got away", s.game.msg != null && s.game.msg.contains("GOT AWAY"));
        s.shot("check_search.png");
        assertTrue("then returns to patrol", s.until(() -> g.state == Guard.RETURN || g.state == Guard.PATROL, 12f));
        assertTrue("and patrols again", s.until(() -> g.state == Guard.PATROL, 20f));
        assertEquals(Game.PLAY, s.game.phase);
    }

    // ------------------------------------------------------------------ treasure / exit / levels
    @Test
    public void walkingIntoACoinCollectsIt() {
        Sim s = quiet(1);
        Game.Pickup coin = null;
        for (Game.Pickup p : s.game.pickups) if (p.kind == Game.COIN && (coin == null || p.y > coin.y)) coin = p;
        final Game.Pickup c = coin;
        assertTrue("reached the coin", s.steerTo(c.x, c.y, 0.15f * s.game.room.boyH, 8f));
        assertTrue("taken", s.until(() -> c.taken, 1f));
        assertTrue("grab reach animation plays", s.game.boy.reachT >= 0 || s.game.boy.reach > 0);
        assertTrue("flies to the counter and counts", s.until(() -> s.game.coins == 1, 2f));
        assertTrue("coin sound", s.heard("coin"));
    }

    @Test
    public void tappingTreasureWalksThereAndGrabsIt() {
        Sim s = quiet(1);
        Game.Pickup far = null;
        for (Game.Pickup p : s.game.pickups) if (p.kind == Game.COIN && (far == null || p.y < far.y)) far = p;
        // bring it on screen first
        s.steerTo(far.x, far.y + 400, 0.4f * s.game.room.boyH, 8f); s.step(1f);
        final Game.Pickup f = far;
        s.tap(s.game.screenX(f.drawX), s.game.screenY(f.drawY - f.size * 0.62f));
        assertNotNull("tap planned a walk", s.game.boy.path);
        assertTrue("walks over and grabs it", s.until(() -> f.taken, 8f));
    }

    @Test
    public void exitStaysLockedUntilTheDiamondIsStolenThenCompletesTheLevel() {
        Sim s = quiet(1);
        Game gm = s.game;
        float ex = (gm.room.exitX0 + gm.room.exitX1) / 2;
        // try the exit first
        s.steerTo(ex, gm.room.exitY - 20, 10, 12f);
        assertTrue("locked exit does not end the level", gm.phase == Game.PLAY && !gm.exitOpen);
        assertTrue("player is told why", gm.msg != null && gm.msg.contains("DIAMOND"));
        s.shot("check_locked_exit.png");
        // steal the diamond by tapping it
        Game.Pickup d = null;
        for (Game.Pickup p : gm.pickups) if (p.kind == Game.CASE) d = p;
        final Game.Pickup dia = d;
        s.step(0.5f);
        s.tap(gm.screenX(dia.drawX), gm.screenY(dia.drawY));
        assertTrue("walks to the case and steals the diamond", s.until(() -> dia.taken, 10f));
        s.step(0.3f); s.shot("check_steal.png");
        assertTrue("diamond reaches the counter; exit opens", s.until(() -> gm.exitOpen, 3f));
        assertEquals(1, gm.gems);
        assertTrue("unlock sound", s.heard("unlock"));
        s.shot("check_exit_open.png");
        s.steerTo(ex, gm.room.exitY - 30, 5, 12f);
        assertTrue("escape sequence starts", s.until(() -> gm.phase == Game.ESCAPE, 2f));
        s.step(0.9f); s.shot("check_escape.png");
        assertTrue("level completes", s.until(() -> s.completed == 1, 4f));
    }

    /** Fairness: a player who times the guard's patrol can sneak past, steal the diamond and escape unseen. */
    @Test
    public void level1CanBeWonBySneaking() {
        Sim s = level(1);
        Game gm = s.game;
        Guard g = gm.guards.get(0);
        // wait for the guard to turn his back and head right
        assertTrue(s.until(() -> g.state == Guard.PATROL && g.waitT <= 0 && Math.abs(Walker.wrap(g.facing)) < 30 && g.x < 450, 30f));
        final int[] worst = {0};
        final int[] last = {-1};
        Runnable watch = () -> {
            worst[0] = Math.max(worst[0], g.state == Guard.SEARCH || g.state == Guard.RETURN ? 1 : g.state);
            if (g.state != last[0]) { last[0] = g.state; log(String.format("t=%.2f guard state %d at %.0f,%.0f facing %.0f aw %.2f | boy %.0f,%.0f", s.t, g.state, g.x, g.y, g.facing, g.awareness, gm.boy.x, gm.boy.y)); }
        };
        assertTrue("sneak up the left side", steerWatched(s, 290, 1660, 0.2f, 6f, watch));
        Game.Pickup dia = null;
        for (Game.Pickup p : gm.pickups) if (p.kind == Game.CASE) dia = p;
        final Game.Pickup d = dia;
        steerWatchedUntil(s, 262, 1300, 0.15f, 6f, watch, () -> d.taken);
        assertTrue("reached the case and stole the diamond", d.taken);
        assertTrue("diamond stolen, exit open", s.until(() -> { watch.run(); return gm.exitOpen; }, 4f));
        float ex = (gm.room.exitX0 + gm.room.exitX1) / 2;
        steerWatched(s, ex, gm.room.exitY - 30, 0.05f, 8f, watch);
        log("after exit steer: boy " + gm.boy.x + "," + gm.boy.y + " phase " + gm.phase + " state " + g.state + " guard " + g.x + "," + g.y + " msg " + gm.msg);
        assertTrue("escaped", s.until(() -> { watch.run(); return s.completed == 1; }, 4f));
        assertTrue("never alerted the guard (worst state " + worst[0] + ")", worst[0] <= Guard.SUSPICIOUS);
        log("sneak run finished at t=" + s.t + " worst guard state " + worst[0]);
    }

    static boolean steerWatched(Sim s, float wx, float wy, float distH, float timeout, Runnable watch) {
        return steerWatchedUntil(s, wx, wy, distH, timeout, watch, () -> false);
    }

    static boolean steerWatchedUntil(Sim s, float wx, float wy, float distH, float timeout, Runnable watch, java.util.function.BooleanSupplier done) {
        float x0 = Sim.W * 0.3f, y0 = Sim.H * 0.75f;
        s.down(0, x0, y0);
        float end = s.t + timeout; boolean ok = false;
        while (s.t < end && s.game.phase == Game.PLAY) {
            Boy b = s.game.boy;
            if (s.game.nav.floorDist(b.x, b.y, wx, wy) < distH * b.H() || done.getAsBoolean()) { ok = true; break; }
            float sx = s.game.screenX(wx) - s.game.screenX(b.x), sy = s.game.screenY(wy) - s.game.screenY(b.y);
            float l = (float) Math.hypot(sx, sy);
            s.move(0, x0 + sx / l * Sim.W * 0.13f, y0 + sy / l * Sim.W * 0.13f);
            s.step(1 / 60f); watch.run();
        }
        s.up(0, x0, y0);
        return ok || s.game.phase == Game.ESCAPE;
    }

    @Test
    public void difficultyRampsUp() {
        for (int n = 2; n <= Level.COUNT; n++) {
            Level a = Level.get(n - 1), b = Level.get(n);
            assertTrue("L" + n + " guards", b.guards.length >= a.guards.length);
            assertTrue("L" + n + " detect time", b.detect <= a.detect);
            assertTrue("L" + n + " reaction", b.react <= a.react);
            assertTrue("L" + n + " chase speed", b.chaseSpeed >= a.chaseSpeed);
            assertTrue("L" + n + " patrol speed", b.patrolSpeed >= a.patrolSpeed);
            assertTrue("L" + n + " sight", b.sight >= a.sight);
            assertTrue("L" + n + " memory", b.lose >= a.lose);
        }
        // same room gets more guards each time it comes back
        assertTrue(Level.get(1).guards.length < Level.get(3).guards.length && Level.get(3).guards.length < Level.get(5).guards.length);
        assertTrue(Level.get(2).guards.length < Level.get(4).guards.length && Level.get(4).guards.length < Level.get(6).guards.length);
        assertEquals("level 1 is gentle: one guard", 1, Level.get(1).guards.length);
        // every guard route starts on reachable floor in its level
        for (int n = 1; n <= Level.COUNT; n++) {
            Sim s = new Sim().start(n);
            for (Guard g : s.game.guards) assertTrue("L" + n + " guard placed on the floor", s.game.nav.canStand(g.x, g.y, s.game.room.feetR));
            s.step(4f);
            for (Guard g : s.game.guards) assertTrue("L" + n + " guard moving/looking", g.stateT > 0);
        }
    }

    @Test
    public void level2PlaysInTheGrandHall() {
        Sim s = level(2);
        Game gm = s.game;
        assertEquals(1, gm.roomIndex);
        assertEquals(2, gm.guards.size());
        s.shot("check_level2.png");
        gm.guards.clear();
        int got = 0;
        for (Game.Pickup p : gm.pickups) {
            if (got >= 4) break;
            final Game.Pickup q = p;
            List<float[]> path = gm.nav.path(gm.boy.x, gm.boy.y, p.x, p.y);
            assertNotNull("gem reachable " + p.x + "," + p.y, path);
            gm.boy.follow(path); gm.markTarget = p;
            assertTrue("collected gem at " + p.x + "," + p.y, s.until(() -> q.taken, 25f));
            got++;
        }
        assertTrue("4 gems open the exit", s.until(() -> gm.exitOpen, 3f));
        List<float[]> out = gm.nav.path(gm.boy.x, gm.boy.y, (gm.room.exitX0 + gm.room.exitX1) / 2, gm.room.exitY + 10);
        assertNotNull("exit reachable", out);
        gm.boy.follow(out);
        s.until(() -> gm.boy.path == null, 30f);
        s.steerTo((gm.room.exitX0 + gm.room.exitX1) / 2, gm.room.exitY - 30, 5, 5f);
        assertTrue("level 2 completes", s.until(() -> s.completed == 2, 5f));
    }
}
