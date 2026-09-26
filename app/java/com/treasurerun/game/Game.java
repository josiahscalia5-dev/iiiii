package com.treasurerun.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Gameplay: one level at a time. Uses only android.graphics so the Robolectric harness can drive it frame by frame.
 * GameView feeds it time, size/insets and touches.
 */
final class Game {
    interface Host {
        Bitmap bitmap(String path);
        void forget(String prefix);
        void sound(int id, float volume);
        void haptic(int kind);          // 0 light, 1 medium, 2 heavy
        void levelComplete(int level, int coins, int gems);
        void pause();
        void home();
    }

    static final int SND_COIN = 0, SND_GEM = 1, SND_DIAMOND = 2, SND_HMM = 3, SND_ALERT = 4, SND_CAUGHT = 5,
            SND_UNLOCK = 6, SND_ESCAPE = 7, SND_STEP = 8, SND_TAP = 9;
    static final String[] SOUNDS = {"sfx/coin.wav", "sfx/gem.wav", "sfx/diamond.wav", "sfx/hmm.wav", "sfx/alert.wav",
            "sfx/caught.wav", "sfx/unlock.wav", "sfx/escape.wav", "sfx/step.wav", "sfx/tap.wav"};

    static final int INTRO = 0, PLAY = 1, CAUGHT = 2, ESCAPE = 3, DONE = 4;
    static final int COIN = 0, GEM = 1, CASE = 2;

    static final class Pickup {
        final int kind; final float x, y, size;
        float drawX, drawY;               // where it is drawn (differs from x,y for the case diamond)
        boolean taken, flying, arrived;
        float t, fx, fy;                  // anim time after taking, fly start (screen)
        Pickup(int kind, float x, float y, float size) { this.kind = kind; this.x = x; this.y = y; this.size = size; drawX = x; drawY = y; }
    }

    static final class Spark { float x, y, vx, vy, life, max, size; int color; boolean screen; }

    final Host host;
    final Typeface font;
    // level
    Level level; Room room; int roomIndex = -1; Nav nav;
    Boy boy;
    final List<Guard> guards = new ArrayList<>();
    final List<Pickup> pickups = new ArrayList<>();
    int coins, gems, stolen;         // this level
    int phase = DONE; float phaseT, time;
    boolean exitOpen; float exitOpenT = -1f;
    Guard catcher;
    // screen
    int sw, sh; float hs;
    final Rect safe = new Rect(); final List<Rect> cutouts = new ArrayList<>(); final int[] cr = new int[4];
    float pauseX, pauseY, pauseR, runX, runY, runR, handX, handY, handR;
    final RectF coinPill = new RectF(), gemPill = new RectF(), panel = new RectF(), retryBtn = new RectF(), homeBtn = new RectF();
    Shader pillShader;
    // camera
    float camX, camY, camScale; boolean camReady; float shake;
    // input
    int steerId = -1, runId = -1;
    float steerOX, steerOY, steerX, steerY, steerT0, steerMoved; boolean steerActive;
    float lastMoveT; float swipeVX, swipeVY;
    float markX, markY, markT = -1f; Pickup markTarget;
    float handFlash, pauseFlash, coinBump, gemBump;
    String msg; float msgT;
    boolean hintShown;
    // fx
    final List<Spark> sparks = new ArrayList<>();
    final Random rnd = new Random(7);
    // drawing
    final Rig rig = new Rig();
    final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF tmpR = new RectF(); final Path path = new Path(); final Matrix mtx = new Matrix();
    final PorterDuffColorFilter goldTint = new PorterDuffColorFilter(Color.rgb(255, 214, 90), PorterDuff.Mode.SRC_IN),
            coinTint = new PorterDuffColorFilter(Color.rgb(255, 200, 60), PorterDuff.Mode.SRC_IN),
            gemTint = new PorterDuffColorFilter(Color.rgb(90, 200, 255), PorterDuff.Mode.SRC_IN),
            redTint = new PorterDuffColorFilter(Color.rgb(255, 60, 40), PorterDuff.Mode.SRC_IN),
            warmTint = new PorterDuffColorFilter(Color.rgb(255, 196, 110), PorterDuff.Mode.SRC_IN);
    final Bitmap glow;
    final List<Object> items = new ArrayList<>(); final List<float[]> itemDepth = new ArrayList<>();

    Game(Host host, Typeface font) {
        this.host = host; this.font = font;
        text.setTypeface(font); stroke.setTypeface(font);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeJoin(Paint.Join.ROUND);
        glow = makeGlow(128);
    }

    static Bitmap makeGlow(int n) {
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(n / 2f, n / 2f, n / 2f, new int[]{Color.argb(255, 255, 255, 255), Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255)}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(n / 2f, n / 2f, n / 2f, p);
        return b;
    }

    void sfx(int id) { host.sound(id, id == SND_STEP ? 0.35f : 1f); }

    // ================================================================== level setup
    void start(int n) {
        level = Level.get(n);
        loadRoom(level.room);
        coins = gems = stolen = 0;
        pickups.clear(); sparks.clear(); guards.clear();
        for (float[] c : level.coins) pickups.add(new Pickup(COIN, c[0], c[1], c[2]));
        for (float[] c : level.gems) pickups.add(new Pickup(GEM, c[0], c[1], c[2]));
        if (room.caseDiamond != null) {
            float[] d = room.caseDiamond;
            Pickup p = new Pickup(CASE, d[3], d[4], d[2]); p.drawX = d[0]; p.drawY = d[1];
            pickups.add(p);
        }
        boy = new Boy(this);
        boy.x = room.enterX; boy.y = room.enterY; boy.facing = -90f;
        boy.path = new ArrayList<>(); boy.path.add(new float[]{room.spawnX, room.spawnY}); boy.pathI = 0; boy.ghost = true;
        for (Level.Wp[] r : level.guards) guards.add(new Guard(this, level, r));
        exitOpen = level.need <= 0; exitOpenT = -1f;
        catcher = null;
        phase = INTRO; phaseT = 0;
        steerId = runId = -1; steerActive = false; markT = -1f; markTarget = null;
        msg = null; camReady = false; shake = 0;
        hintShown = false;
    }

    private void loadRoom(int i) {
        if (roomIndex >= 0 && roomIndex != i) host.forget("rooms/room" + (roomIndex + 1) + "_");
        roomIndex = i;
        room = RoomData.ROOMS[i];
        for (String s : room.bg) host.bitmap(s);
        for (Occ o : room.occ) host.bitmap(o.sprite);
        Bitmap w = host.bitmap(room.walk);
        int ww = w.getWidth(), wh = w.getHeight();
        int[] px = new int[ww * wh];
        w.getPixels(px, 0, ww, 0, 0, ww, wh);
        byte[] walk = new byte[px.length];
        for (int k = 0; k < px.length; k++) walk[k] = (byte) (((px[k] >> 16) & 255) > 127 ? 1 : 0);
        nav = new Nav(room, walk, ww, wh);
    }

    // ================================================================== layout
    void setInsets(Rect s, List<Rect> cut, int[] corners) {
        safe.set(s.left, s.top, s.right, s.bottom);
        cutouts.clear(); cutouts.addAll(cut);
        System.arraycopy(corners, 0, cr, 0, 4);
        layout(sw, sh);
    }

    void layout(int w, int h) {
        sw = w; sh = h;
        if (w == 0 || h == 0) return;
        hs = Math.min(sw / 1056f, sh / 2240f) * 0.86f;
        float g = 0.03f * sw;
        int cutBottom = 0;
        for (Rect r : cutouts) if (r.top < sh / 4) cutBottom = Math.max(cutBottom, r.bottom);
        float top = Math.max(Math.max(safe.top, cutBottom) + 0.012f * sw, g);
        float left = Math.max(Math.max(safe.left, 0) + g, cornerInset() * 0.55f);
        float bottom = sh - Math.max(safe.bottom, 0) - 0.02f * sw;
        float right = Math.min(sw - Math.max(safe.right, 0) - g, sw - cornerInset() * 0.55f);
        pauseR = 61f * hs; pauseX = pauseR + left; pauseY = top + pauseR;
        float ph = 92f * hs, gap = 0.022f * sw, pw = 250f * hs;
        pillShader = new LinearGradient(0, pauseY - ph / 2, 0, pauseY + ph / 2, Color.argb(225, 22, 26, 58), Color.argb(225, 10, 10, 30), Shader.TileMode.CLAMP);
        coinPill.set(pauseX + pauseR + gap, pauseY - ph / 2, pauseX + pauseR + gap + pw, pauseY + ph / 2);
        gemPill.set(coinPill.right + gap, coinPill.top, coinPill.right + gap + pw, coinPill.bottom);
        if (gemPill.right > right) {
            float k = (right - coinPill.left - gap) / (2 * pw);
            coinPill.right = coinPill.left + pw * k;
            gemPill.set(coinPill.right + gap, coinPill.top, coinPill.right + gap + pw * k, coinPill.bottom);
        }
        float pwid = Math.min(931f * hs, right - left);
        panel.set((sw - pwid) / 2, pauseY + pauseR + 0.018f * sw, (sw + pwid) / 2, pauseY + pauseR + 0.018f * sw + 162f * pwid / 931f);
        runR = 132f * hs; handR = 118f * hs;
        runX = right - runR; runY = bottom - 199f * hs + 20f * hs;
        handX = right - handR - 6f * hs; handY = runY - runR - handR - 36f * hs;
        float bw = Math.min(sw * 0.62f, 620f * hs * 1.3f), bh = bw * 236f / 499f;
        retryBtn.set((sw - bw) / 2, sh * 0.56f, (sw + bw) / 2, sh * 0.56f + bh);
        float hw = bw * 0.78f, hh = hw * 236f / 510f;
        homeBtn.set((sw - hw) / 2, retryBtn.bottom + 0.02f * sh, (sw + hw) / 2, retryBtn.bottom + 0.02f * sh + hh);
        camReady = false;
    }

    private float cornerInset() { int m = 0; for (int v : cr) m = Math.max(m, v); return m * 0.3f; }

    // ================================================================== input
    boolean inCircle(float x, float y, float cx, float cy, float r) { return Math.hypot(x - cx, y - cy) < r; }

    void touchDown(int id, float x, float y, float t) {
        if (room == null) return;
        if (phase == CAUGHT) {
            if (phaseT > 0.9f && retryBtn.contains(x, y)) { host.haptic(0); start(level.number); }
            else if (phaseT > 0.9f && homeBtn.contains(x, y)) { host.haptic(0); host.home(); }
            return;
        }
        if (inCircle(x, y, pauseX, pauseY, pauseR * 1.35f)) { pauseFlash = 1; host.haptic(0); host.pause(); return; }
        if (phase != PLAY) return;
        if (inCircle(x, y, runX, runY, runR * 1.25f)) { runId = id; boy.sprint = true; host.haptic(0); return; }
        if (inCircle(x, y, handX, handY, handR * 1.25f)) { handFlash = 1; host.haptic(0); onHand(); return; }
        if (steerId < 0) {
            steerId = id; steerOX = steerX = x; steerOY = steerY = y; steerT0 = t; steerActive = false; steerMoved = 0;
            lastMoveT = t; swipeVX = swipeVY = 0;
            boy.flickLeft = 0;                       // finger down: stop coasting
        }
    }

    void touchMove(int id, float x, float y, float t) {
        if (id != steerId || phase != PLAY) return;
        if (x == steerX && y == steerY) return;
        float dt = Math.max(0.004f, t - lastMoveT);
        float ivx = (x - steerX) / dt, ivy = (y - steerY) / dt;
        swipeVX += (ivx - swipeVX) * 0.5f; swipeVY += (ivy - swipeVY) * 0.5f;
        steerMoved += (float) Math.hypot(x - steerX, y - steerY);
        steerX = x; steerY = y; lastMoveT = t;
        float dx = x - steerOX, dy = y - steerOY, d = (float) Math.hypot(dx, dy);
        float dead = 0.028f * sw, maxR = 0.15f * sw;
        if (!steerActive && d > dead) { steerActive = true; boy.path = null; markT = -1f; markTarget = null; }
        if (steerActive) {
            if (d > maxR) { steerOX = x - dx / d * maxR; steerOY = y - dy / d * maxR; d = maxR; dx = x - steerOX; dy = y - steerOY; }
            if (d > dead * 0.5f) {
                boy.steering = true;
                boy.steerAng = nav.screenToFloorAngle(dx, dy, boy.y);
                boy.steerMag = Math.max(0.3f, Math.min(1f, (d - dead * 0.5f) / (0.09f * sw)));
            } else boy.steerMag = 0;
        }
    }

    void touchUp(int id, float x, float y, float t) {
        if (id == runId) { runId = -1; if (boy != null) boy.sprint = false; }
        if (id != steerId) return;
        steerId = -1;
        if (phase != PLAY) { boy.steering = false; return; }
        float held = t - steerT0;
        if (!steerActive) {
            if (held < 0.4f) tap(x, y);
        } else {
            boy.steering = false;
            float v = (float) Math.hypot(swipeVX, swipeVY);
            // quick swipe: keep walking that way for a distance that grows with the swipe length
            if (held < 0.45f && v > 0.5f * sw && t - lastMoveT < 0.1f) {
                float len = (float) Math.hypot(x - steerOX, y - steerOY) + steerMoved * 0.3f;
                boy.flickAng = nav.screenToFloorAngle(swipeVX, swipeVY, boy.y);
                boy.flickLeft = boy.H() * Math.max(0.25f, Math.min(2.4f, 5f * (float) Math.pow(len / sw, 1.2)));
            }
        }
        steerActive = false;
    }

    void touchCancel() {
        steerId = runId = -1; steerActive = false;
        if (boy != null) { boy.steering = false; boy.sprint = false; }
    }

    float worldX(float sx) { return camX + (sx - sw / 2f) / camScale; }
    float worldY(float sy) { return camY + (sy - sh / 2f) / camScale; }
    float screenX(float wx) { return (wx - camX) * camScale + sw / 2f; }
    float screenY(float wy) { return (wy - camY) * camScale + sh / 2f; }

    private void tap(float sx, float sy) {
        float wx = worldX(sx), wy = worldY(sy);
        // treasure under the finger?
        Pickup best = null; float bd = Float.MAX_VALUE;
        for (Pickup p : pickups) {
            if (p.taken) continue;
            float px = screenX(p.drawX), py = screenY(p.kind == CASE ? p.drawY : p.drawY - p.size * 0.62f);
            float d = (float) Math.hypot(px - sx, py - sy);
            float r = Math.max(0.07f * sw, p.size * 0.7f * camScale);
            if (d < r && d < bd) { bd = d; best = p; }
        }
        float tx = wx, ty = wy;
        if (best != null) { tx = best.x; ty = best.y; }
        else if (!nav.canStand(wx, wy, room.feetR)) {
            // snap to the nearest standable point below the tap (the floor in front of what was tapped)
            boolean ok = false;
            for (int k = 1; k < 40 && !ok; k++) { float yy = wy + k * 8f; if (nav.canStand(wx, yy, room.feetR)) { ty = yy; ok = true; } }
            if (!ok) return;
        }
        List<float[]> p = nav.path(boy.x, boy.y, tx, ty);
        if (p == null || p.isEmpty()) return;
        boy.follow(p);
        markX = tx; markY = ty; markT = 0; markTarget = best;
        sfx(SND_TAP);
    }

    void onArrive() {
        if (markTarget != null && !markTarget.taken && near(markTarget, 0.75f)) take(markTarget);
        markTarget = null;
    }

    private void onHand() {
        if (phase != PLAY) return;
        Pickup best = null; float bd = Float.MAX_VALUE;
        for (Pickup p : pickups) {
            if (p.taken) continue;
            float d = nav.floorDist(boy.x, boy.y, p.x, p.y);
            if (d < bd) { bd = d; best = p; }
        }
        if (best != null && near(best, 0.8f)) take(best);
        else boy.grab(boy.x, boy.y - 50);
    }

    // ================================================================== update
    void update(float dt) {
        if (room == null || phase == DONE) return;
        time += dt; phaseT += dt;
        handFlash = Math.max(0, handFlash - 3 * dt); pauseFlash = Math.max(0, pauseFlash - 3 * dt);
        coinBump = Math.max(0, coinBump - 4 * dt); gemBump = Math.max(0, gemBump - 4 * dt);
        shake = Math.max(0, shake - dt * 2.5f);
        if (msgT > 0) msgT -= dt;
        if (markT >= 0) markT += dt;
        switch (phase) {
            case INTRO:
                boy.update(dt);
                if (boy.path == null || phaseT > 3.2f) {
                    boy.path = null; boy.ghost = false; phase = PLAY; phaseT = 0;
                    if (level.number == 1 && !hintShown) { say("SWIPE TO SNEAK  •  TAP TREASURE TO GRAB", 4.5f); hintShown = true; }
                }
                break;
            case PLAY:
                boy.update(dt);
                checkPickups();
                checkExit();
                break;
            case CAUGHT:
                boy.speed = 0; boy.amt = Math.max(0, boy.amt - dt * 4); boy.moved = 0;
                boy.animate(dt, room.speed);
                break;
            case ESCAPE: {
                float tx = (room.exitX0 + room.exitX1) / 2f, ty = room.exitY - 170f * room.depth(room.exitY);
                float ang = nav.floorAngle(boy.x, boy.y, tx, ty);
                boy.turnToward(ang, 500f, dt);
                boy.speed = room.speed * 0.8f;
                double a = Math.toRadians(boy.facing);
                float step = boy.speed * dt;
                boy.x += (float) Math.cos(a) * step * nav.sx(boy.y); boy.y += (float) Math.sin(a) * step * nav.sy(boy.y);
                boy.moved = step;
                boy.animate(dt, room.speed);
                boy.alpha = Math.max(0, 1f - Math.max(0, phaseT - 0.55f) / 0.5f);
                if (phaseT > 1.6f) { phase = DONE; host.levelComplete(level.number, coins, gems); }
                break;
            }
        }
        boolean active = phase == PLAY;
        for (Guard gd : guards) gd.update(dt, boy, active);
        updateSparks(dt);
        ambient(dt);
        updateCamera(dt);
    }

    void onAlert(Guard gd) {
        shake = Math.max(shake, 0.5f);
        host.haptic(2);
        say("YOU'VE BEEN SPOTTED!  RUN!", 1.8f);
    }

    void onLost(Guard gd) {
        for (Guard o : guards) if (o != gd && o.chasing()) return;
        say("YOU GOT AWAY!", 1.8f);
    }

    void caught(Guard gd) {
        if (phase != PLAY) return;
        phase = CAUGHT; phaseT = 0; catcher = gd;
        boy.stop(); boy.lockT = 99f; boy.hop = 0.12f;
        shake = 1f;
        sfx(SND_CAUGHT); host.haptic(2);
        for (Guard o : guards) if (o != gd) o.speed = 0;
    }

    void say(String s, float t) { msg = s; msgT = t; }

    private boolean near(Pickup p, float mul) {
        float r = p.kind == CASE ? 0.42f * room.boyH : (0.2f * room.boyH + p.size * 0.35f / Math.max(0.2f, room.depth(p.y)));
        return nav.floorDist(boy.x, boy.y, p.x, p.y) < r * mul;
    }

    private void checkPickups() {
        for (Pickup p : pickups) {
            if (!p.taken && near(p, 1f) && (p.kind != CASE || boy.lockT <= 0)) take(p);
            if (p.taken) p.t += 0; // animated in updateSparks
        }
    }

    private void take(Pickup p) {
        p.taken = true; p.t = 0; p.flying = false; p.arrived = false;
        boy.grab(p.drawX, p.drawY);
        if (p.kind == CASE) {
            boy.lockT = 0.5f; boy.reachDur = 0.6f; boy.stop();
            boy.turnToward(nav.floorAngle(boy.x, boy.y, p.drawX, p.y - 40), 9999f, 1f);
            sfx(SND_DIAMOND); host.haptic(1);
            burst(p.drawX, p.drawY, 26, Color.rgb(140, 220, 255), false);
        } else {
            sfx(p.kind == COIN ? SND_COIN : SND_GEM); host.haptic(0);
            burst(p.drawX, p.drawY - p.size * 0.62f, 10, p.kind == COIN ? Color.rgb(255, 214, 90) : Color.rgb(140, 220, 255), false);
        }
    }

    /** Called when a flying treasure reaches its HUD counter. */
    private void arrived(Pickup p) {
        if (p.kind == COIN) { coins++; coinBump = 1; }
        else { gems++; gemBump = 1; }
        if (p.kind != COIN) {
            stolen++;
            if (!exitOpen && stolen >= level.need) {
                exitOpen = true; exitOpenT = 0;
                sfx(SND_UNLOCK); host.haptic(1);
                say("THE EXIT IS OPEN!  GET OUT!", 2.6f);
            }
        }
        RectF pill = p.kind == COIN ? coinPill : gemPill;
        burst(pill.left + pill.height() * 0.55f, pill.centerY(), 8, p.kind == COIN ? Color.rgb(255, 214, 90) : Color.rgb(140, 220, 255), true);
    }

    private void checkExit() {
        float cx = (room.exitX0 + room.exitX1) / 2f;
        boolean inX = boy.x > room.exitX0 && boy.x < room.exitX1;
        if (!exitOpen) {
            if (inX && boy.y < room.exitY + 60f * room.depth(room.exitY)) {
                boy.y = Math.max(boy.y, room.exitY + 30f * room.depth(room.exitY));
                if (msgT <= 0) say(level.room == 0 ? "LOCKED!  STEAL THE DIAMOND FIRST" : "LOCKED!  STEAL " + (level.need - stolen) + " MORE GEM" + (level.need - stolen == 1 ? "" : "S"), 2f);
            }
            return;
        }
        if (exitOpenT >= 0) exitOpenT += 0.016f;
        if (inX && boy.y <= room.exitY) {
            phase = ESCAPE; phaseT = 0; boy.stop(); sfx(SND_ESCAPE); host.haptic(1);
            for (Guard g : guards) g.speed = 0;
        }
    }

    // ================================================================== fx
    void burst(float x, float y, int n, int color, boolean screen) {
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            double a = rnd.nextDouble() * Math.PI * 2; float v = (screen ? 0.25f * sw : 260f) * (0.4f + rnd.nextFloat());
            s.x = x; s.y = y; s.vx = (float) Math.cos(a) * v; s.vy = (float) Math.sin(a) * v - (screen ? 0 : 120f);
            s.max = s.life = 0.45f + 0.35f * rnd.nextFloat(); s.size = (screen ? 0.018f * sw : 18f) * (0.6f + rnd.nextFloat()); s.color = color; s.screen = screen;
            sparks.add(s);
        }
    }

    private void updateSparks(float dt) {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.life -= dt; if (s.life <= 0) { sparks.remove(i); continue; }
            s.x += s.vx * dt; s.y += s.vy * dt; s.vx *= (1 - 2.5f * dt); s.vy *= (1 - 2.5f * dt);
            if (!s.screen) s.vy += 140f * dt;
        }
        for (Pickup p : pickups) {
            if (!p.taken || p.arrived) continue;
            p.t += dt;
            float lift = p.kind == CASE ? 0.55f : 0.28f;
            if (!p.flying && p.t >= lift) {
                p.flying = true;
                float wy = p.kind == CASE ? p.drawY - 60f : p.drawY - p.size * 0.62f - p.size * 0.9f;
                p.fx = screenX(p.drawX); p.fy = screenY(wy);
            }
            if (p.flying && p.t >= lift + 0.6f) { p.arrived = true; arrived(p); }
            // trail
            if (p.flying && rnd.nextFloat() < 0.6f) {
                float[] q = flyPos(p);
                Spark s = new Spark(); s.x = q[0]; s.y = q[1]; s.vx = (rnd.nextFloat() - 0.5f) * 40; s.vy = (rnd.nextFloat() - 0.5f) * 40;
                s.max = s.life = 0.35f; s.size = 0.012f * sw; s.color = p.kind == COIN ? Color.rgb(255, 230, 140) : Color.rgb(170, 230, 255); s.screen = true;
                sparks.add(s);
            }
        }
    }

    private final float[] fly = new float[3];
    /** Screen position (+scale 0..1) of a flying treasure. */
    private float[] flyPos(Pickup p) {
        float lift = p.kind == CASE ? 0.55f : 0.28f;
        float t = Math.min(1f, (p.t - lift) / 0.6f);
        float e = t * t * (3 - 2 * t);
        RectF pill = p.kind == COIN ? coinPill : gemPill;
        float tx = pill.left + pill.height() * 0.59f, ty = pill.centerY();
        float cx = (p.fx + tx) / 2 + 0.15f * sw, cy = Math.min(p.fy, ty) - 0.05f * sh;
        float u = 1 - e;
        fly[0] = u * u * p.fx + 2 * u * e * cx + e * e * tx;
        fly[1] = u * u * p.fy + 2 * u * e * cy + e * e * ty;
        fly[2] = 1 - 0.55f * e;
        return fly;
    }

    // ambient life: dust drifting through lamp light
    private float dustT;
    private void ambient(float dt) {
        dustT -= dt;
        if (dustT <= 0 && room.lamps != null && room.lamps.length > 0) {
            dustT = 0.35f;
            float[] l = room.lamps[rnd.nextInt(room.lamps.length)];
            Spark s = new Spark();
            float d = Math.max(0.25f, room.depth(l[1] + 200));
            s.x = l[0] + (rnd.nextFloat() - 0.5f) * 180 * d; s.y = l[1] + rnd.nextFloat() * 160 * d;
            s.vx = (rnd.nextFloat() - 0.5f) * 14; s.vy = -8 - rnd.nextFloat() * 10;
            s.max = s.life = 3f + rnd.nextFloat() * 2f; s.size = -(4f + rnd.nextFloat() * 4f) * d; // negative size = dust
            s.color = Color.rgb(255, 230, 180);
            sparks.add(s);
        }
    }

    // ================================================================== camera
    void updateCamera(float dt) {
        float base = Math.max(sw / (float) room.w, sh / (float) room.h);
        float view = room.viewW > 0 ? Math.max(1f, (sw / room.viewW) / base) : 1f;
        float k = clamp((1f - room.depth(boy.y)) / (1f - room.sFar), 0, 1);
        float target = view * ((3 - 2 * k) * k * k * (room.zoomFar - 1) + 1) * base;
        camScale = camReady ? camScale + (target - camScale) * Math.min(1, 2.5f * dt) : target;
        camScale = Math.max(camScale, base);
        float vw = sw / camScale, vh = sh / camScale;
        float tx = boy.x, ty = boy.y - (room.feetAt - 0.5f) * vh;
        if (phase == CAUGHT && catcher != null) { tx = (boy.x + catcher.x) / 2; }
        tx = clamp(tx, vw / 2, room.w - vw / 2); ty = clamp(ty, vh / 2, room.h - vh / 2);
        if (!camReady) { camX = tx; camY = ty; camReady = true; }
        else { float m = Math.min(1, 4.5f * dt); camX += (tx - camX) * m; camY += (ty - camY) * m; }
        camX = clamp(camX, vw / 2, room.w - vw / 2); camY = clamp(camY, vh / 2, room.h - vh / 2);
    }

    static float clamp(float v, float a, float b) { return v < a ? a : v > b ? b : v; }

    // ================================================================== render
    void render(Canvas c) {
        if (room == null || sw == 0) return;
        if (!camReady) updateCamera(0);
        c.drawColor(Color.BLACK);
        c.save();
        float sk = shake * shake * 0.012f * sw;
        c.translate(sw / 2f + (sk > 0 ? (float) Math.sin(time * 61) * sk : 0), sh / 2f + (sk > 0 ? (float) Math.cos(time * 53) * sk : 0));
        c.scale(camScale, camScale);
        c.translate(-camX, -camY);
        bmp.setAlpha(255);
        for (int i = 0; i < room.bg.length; i++) {
            Bitmap b = host.bitmap(room.bg[i]);
            if (b != null) c.drawBitmap(b, 0, room.bgY[i], bmp);
        }
        drawLamps(c);
        drawExitGlow(c);
        for (Guard gd : guards) drawCone(c, gd);
        drawMarker(c);
        drawSteerHint(c);
        drawWorld(c);
        for (Guard gd : guards) drawBubble(c, gd);
        drawExitSign(c);
        drawSparks(c, false);
        c.restore();
        drawVignette(c);
        drawHud(c);
        drawFlying(c);
        drawSparks(c, true);
        drawMessage(c);
        if (phase == INTRO || (phase == PLAY && phaseT < 1.5f)) drawBanner(c);
        if (phase == INTRO && phaseT < 0.5f) { fill.setShader(null); fill.setColor(Color.argb(Math.round(255 * (1 - phaseT / 0.5f)), 0, 0, 0)); c.drawRect(0, 0, sw, sh, fill); }
        if (phase == CAUGHT) drawCaught(c);
        if (phase == ESCAPE || phase == DONE) drawIris(c);
    }

    private void drawWorld(Canvas c) {
        items.clear(); itemDepth.clear();
        float vw = sw / camScale / 2, vh = sh / camScale / 2;
        float minY = boy.y;
        for (Guard g : guards) minY = Math.min(minY, g.y);
        for (Pickup p : pickups) if (!p.taken || (p.kind != CASE && !p.flying)) minY = Math.min(minY, p.y);
        add(boy, boy.y);
        for (Guard g : guards) add(g, g.y);
        for (Pickup p : pickups) {
            if (p.taken && p.flying) continue;
            add(p, p.kind == CASE ? caseDepth() : p.y);
        }
        for (Occ o : room.occ) {
            if (o.base <= minY) continue;
            if (o.x > camX + vw || o.x + o.w < camX - vw || o.y > camY + vh || o.y + o.h < camY - vh) continue;
            add(o, o.base);
        }
        // insertion sort by depth (stable, small n)
        int n = items.size();
        Object[] it = items.toArray(); float[] dp = new float[n];
        for (int i = 0; i < n; i++) dp[i] = itemDepth.get(i)[0];
        for (int i = 1; i < n; i++) { Object o = it[i]; float d = dp[i]; int j = i - 1; while (j >= 0 && dp[j] > d) { it[j + 1] = it[j]; dp[j + 1] = dp[j]; j--; } it[j + 1] = o; dp[j + 1] = d; }
        for (Object o : it) {
            if (o instanceof Boy) drawBoy(c);
            else if (o instanceof Guard) drawGuard(c, (Guard) o);
            else if (o instanceof Pickup) drawPickup(c, (Pickup) o);
            else { Occ oc = (Occ) o; Bitmap b = host.bitmap(oc.sprite); if (b != null) { bmp.setAlpha(255); c.drawBitmap(b, oc.x, oc.y, bmp); } }
        }
    }
    private float caseDepth() { for (Occ o : room.occ) if (o.sprite.endsWith("diamond_case.png")) return o.base + 0.5f; return 99999f; }
    private void add(Object o, float d) { items.add(o); itemDepth.add(new float[]{d}); }

    private void drawBoy(Canvas c) {
        if (boy.alpha <= 0) return;
        Rig.Pose p = boy.pose();
        p.alpha = Math.round(255 * boy.alpha);
        if (phase == CAUGHT) p.lean = (float) Math.sin(phaseT * 30) * 4f * Math.max(0, 1 - phaseT);
        rig.draw(c, Rig.BOY, p, host);
    }

    private void drawGuard(Canvas c, Guard g) {
        Rig.Pose p = g.pose();
        p.alpha = 255;
        rig.draw(c, Rig.GUARD, p, host);
        // flashlight lens glow
        if (!Float.isNaN(p.lampSX)) {
            int col = g.state == Guard.CHASE || g.state == Guard.ALERT ? Color.rgb(255, 120, 90) : Color.rgb(255, 236, 170);
            float r = p.scale * 0.09f;
            bmp.setColorFilter(new PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN));
            bmp.setAlpha(200);
            tmpR.set(p.lampSX - r, p.lampSY - r, p.lampSX + r, p.lampSY + r);
            c.drawBitmap(glow, null, tmpR, bmp);
            bmp.setColorFilter(null); bmp.setAlpha(255);
        }
    }

    private void drawCone(Canvas c, Guard g) {
        if (g.coneN < 3) return;
        int col;
        if (g.state == Guard.CHASE || g.state == Guard.ALERT || g.state == Guard.HOLD) col = Color.rgb(255, 70, 50);
        else if (g.state == Guard.SUSPICIOUS || g.state == Guard.SEARCH || g.awareness > 0.1f) col = Color.rgb(255, 176, 50);
        else col = Color.rgb(255, 238, 160);
        path.reset();
        path.moveTo(g.cone[0], g.cone[1]);
        for (int i = 1; i < g.coneN; i++) path.lineTo(g.cone[2 * i], g.cone[2 * i + 1]);
        path.close();
        float range = g.sight() * nav.sx(g.y) * 1.1f;
        float a0 = phase == PLAY || phase == INTRO ? 1f : 0.6f;
        fill.setShader(new RadialGradient(g.x, g.y, Math.max(10f, range), new int[]{
                Color.argb(Math.round(185 * a0), Color.red(col), Color.green(col), Color.blue(col)),
                Color.argb(Math.round(125 * a0), Color.red(col), Color.green(col), Color.blue(col)),
                Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        // soft edge line
        stroke.setShader(null);
        stroke.setStrokeWidth(Math.max(1.5f, 3f * nav.sx(g.y)));
        stroke.setColor(Color.argb(Math.round(110 * a0), Color.red(col), Color.green(col), Color.blue(col)));
        c.drawPath(path, stroke);
    }

    private void drawBubble(Canvas c, Guard g) {
        if (g.state == Guard.HOLD || phase == CAUGHT || phase == ESCAPE) return;
        boolean alert = g.state == Guard.ALERT || g.state == Guard.CHASE;
        boolean sus = !alert && (g.state == Guard.SUSPICIOUS || g.state == Guard.SEARCH || g.awareness > 0.12f);
        if (!alert && !sus) return;
        if (g.state == Guard.CHASE && g.stateT > 1.2f && g.seesBoy) return;   // keep the screen readable mid-chase
        Rig.Pose p = g.pose;
        float s = p.scale * 0.2f;
        float pop = Math.min(1f, g.bubbleT / 0.18f);
        float sc = pop < 1 ? 0.4f + 0.8f * pop : 1f + 0.06f * (float) Math.sin(time * 8);
        float bx = p.headSX + p.scale * 0.12f * (g.mirror ? -1 : 1), by = p.headSY - s * 0.9f;
        c.save();
        c.translate(bx, by);
        c.scale(sc, sc);
        // bubble
        fill.setShader(null);
        fill.setColor(Color.argb(235, 255, 255, 255));
        tmpR.set(-s * 0.62f, -s * 0.7f, s * 0.62f, s * 0.7f);
        c.drawRoundRect(tmpR, s * 0.5f, s * 0.5f, fill);
        path.reset(); path.moveTo(-s * 0.18f, s * 0.6f); path.lineTo(-s * 0.36f, s * 1.0f); path.lineTo(s * 0.12f, s * 0.62f); path.close();
        c.drawPath(path, fill);
        stroke.setShader(null); stroke.setStrokeWidth(s * 0.08f); stroke.setColor(Color.argb(230, 40, 20, 10));
        c.drawRoundRect(tmpR, s * 0.5f, s * 0.5f, stroke);
        if (sus) {
            // awareness fill behind the "?"
            fill.setColor(Color.argb(200, 255, 176, 40));
            c.save();
            c.clipRect(-s * 0.62f, s * 0.7f - s * 1.4f * g.awareness, s * 0.62f, s * 0.7f);
            c.drawRoundRect(tmpR, s * 0.5f, s * 0.5f, fill);
            c.restore();
        }
        text.setTextAlign(Paint.Align.CENTER); text.setTextSize(s * 1.15f); text.setShader(null);
        text.setColor(alert ? Color.rgb(230, 30, 20) : Color.rgb(120, 60, 0));
        c.drawText(alert ? "!" : "?", 0, s * 0.42f, text);
        c.restore();
    }

    private void drawPickup(Canvas c, Pickup p) {
        if (p.kind == CASE) { drawCaseDiamond(c, p); return; }
        Bitmap b = host.bitmap(p.kind == COIN ? "rooms/coin.png" : "rooms/diamond.png");
        if (b == null) return;
        float s = p.size, idx = p.x * 0.01f;
        float bob = 0.62f * s + (float) Math.sin(time * 2.6f + idx * 1.3f) * s * 0.08f;
        float pop = 1f, alpha = 1f, rise = 0;
        if (p.taken) { float t = Math.min(1f, p.t / 0.28f); pop = 1 + 0.5f * t; rise = s * 0.9f * t; }
        boolean close = !p.taken && near(p, 2.2f);
        float cy = p.y - bob - rise;
        fill.setShader(null);
        if (!p.taken) { fill.setColor(Color.argb(70, 20, 5, 20)); tmpR.set(p.x - 0.36f * s, p.y - 0.09f * s, p.x + 0.36f * s, p.y + 0.09f * s); c.drawOval(tmpR, fill); }
        bmp.setColorFilter(p.kind == COIN ? coinTint : gemTint);
        bmp.setAlpha(Math.round(150 * alpha * (0.85f + 0.15f * (float) Math.sin(time * 3 + idx)) * (close ? 1.35f : 1f) > 255 ? 255 : 150 * alpha * (0.85f + 0.15f * (float) Math.sin(time * 3 + idx)) * (close ? 1.35f : 1f)));
        float gr = 1.25f * s * pop * (close ? 1.2f : 1f);
        tmpR.set(p.x - gr, cy - gr, p.x + gr, cy + gr);
        c.drawBitmap(glow, null, tmpR, bmp);
        bmp.setColorFilter(null); bmp.setAlpha(Math.round(255 * alpha));
        float hw = s / 2 * pop, hh = b.getHeight() * hw / b.getWidth();
        float spin = p.kind == COIN ? 0.75f + 0.25f * (float) Math.cos(time * 2.2f + idx) : 1f;
        tmpR.set(p.x - hw * spin, cy - hh, p.x + hw * spin, cy + hh);
        c.drawBitmap(b, null, tmpR, bmp);
        bmp.setAlpha(255);
        if (!p.taken && rnd.nextFloat() < (close ? 0.25f : 0.05f)) twinkle(p.x + (rnd.nextFloat() - 0.5f) * s, cy + (rnd.nextFloat() - 0.5f) * s * 0.8f, s * 0.12f);
    }

    private void drawCaseDiamond(Canvas c, Pickup p) {
        Bitmap b = host.bitmap("rooms/diamond.png");
        if (b == null || p.flying) return;
        float s = p.size;
        float lift = p.taken ? Math.min(1f, p.t / 0.55f) : 0f;
        float cy = p.drawY + (float) Math.sin(time * 2f) * s * 0.03f - lift * s * 0.9f;
        boolean close = !p.taken && near(p, 2.4f);
        float pulse = 0.8f + 0.2f * (float) Math.sin(time * 4f);
        bmp.setColorFilter(gemTint);
        bmp.setAlpha(Math.round((close ? 230 : 150) * pulse));
        float gr = s * (close ? 1.5f : 1.1f) * (1 + lift * 0.4f);
        tmpR.set(p.drawX - gr, cy - gr, p.drawX + gr, cy + gr);
        c.drawBitmap(glow, null, tmpR, bmp);
        bmp.setColorFilter(null);
        bmp.setAlpha(p.taken ? 255 : 240);
        float hw = s / 2 * (1 + lift * 0.3f), hh = b.getHeight() * hw / b.getWidth();
        tmpR.set(p.drawX - hw, cy - hh, p.drawX + hw, cy + hh);
        c.drawBitmap(b, null, tmpR, bmp);
        bmp.setAlpha(255);
        if (!p.taken) {
            // glass sheen so it sits inside the case
            fill.setShader(null); fill.setColor(Color.argb(28, 190, 230, 255));
            tmpR.set(p.drawX - s * 0.8f, p.drawY - s * 0.9f, p.drawX + s * 0.8f, p.drawY + s * 0.7f);
            c.drawRect(tmpR, fill);
            if (rnd.nextFloat() < (close ? 0.45f : 0.12f)) twinkle(p.drawX + (rnd.nextFloat() - 0.5f) * s, cy + (rnd.nextFloat() - 0.5f) * s * 0.8f, s * 0.14f);
            if (close && phase == PLAY) {
                // "grab" hint above the case
                float a = 0.6f + 0.4f * (float) Math.sin(time * 6);
                text.setTextAlign(Paint.Align.CENTER); text.setTextSize(s * 0.36f);
                stroke.setTextAlign(Paint.Align.CENTER); stroke.setTextSize(s * 0.36f); stroke.setStrokeWidth(s * 0.06f);
                stroke.setColor(Color.argb(Math.round(220 * a), 20, 10, 40));
                c.drawText("TAP TO STEAL", p.drawX, p.drawY - s * 1.25f, stroke);
                text.setColor(Color.argb(Math.round(255 * a), 170, 235, 255));
                c.drawText("TAP TO STEAL", p.drawX, p.drawY - s * 1.25f, text);
            }
        }
    }

    private void twinkle(float x, float y, float size) {
        Spark s = new Spark(); s.x = x; s.y = y; s.vx = 0; s.vy = -10; s.max = s.life = 0.5f; s.size = size; s.color = Color.WHITE; s.screen = false;
        sparks.add(s);
    }

    private void drawSparks(Canvas c, boolean screen) {
        fill.setShader(null);
        for (Spark s : sparks) {
            if (s.screen != screen) continue;
            float k = s.life / s.max;
            if (s.size < 0) { // dust mote
                float a = (float) Math.sin(Math.PI * (1 - k)) * 0.5f;
                fill.setColor(Color.argb(Math.round(110 * a), Color.red(s.color), Color.green(s.color), Color.blue(s.color)));
                c.drawCircle(s.x, s.y, -s.size * 0.5f, fill);
                continue;
            }
            float r = s.size * (0.4f + 0.6f * k);
            fill.setColor(Color.argb(Math.round(255 * Math.min(1, k * 1.6f)), Color.red(s.color), Color.green(s.color), Color.blue(s.color)));
            // four-point star
            path.reset();
            path.moveTo(s.x, s.y - r); path.lineTo(s.x + r * 0.22f, s.y - r * 0.22f); path.lineTo(s.x + r, s.y); path.lineTo(s.x + r * 0.22f, s.y + r * 0.22f);
            path.lineTo(s.x, s.y + r); path.lineTo(s.x - r * 0.22f, s.y + r * 0.22f); path.lineTo(s.x - r, s.y); path.lineTo(s.x - r * 0.22f, s.y - r * 0.22f); path.close();
            c.drawPath(path, fill);
        }
    }

    private void drawLamps(Canvas c) {
        if (room.lamps != null) {
            bmp.setColorFilter(warmTint);
            for (int i = 0; i < room.lamps.length; i++) {
                float[] l = room.lamps[i];
                float f = 0.82f + 0.1f * (float) Math.sin(time * 3.1f + i * 1.7f) + 0.08f * (float) Math.sin(time * 11.3f + i * 2.9f);
                bmp.setAlpha(Math.round(95 * f));
                float r = l[2] * (0.95f + 0.05f * f);
                tmpR.set(l[0] - r, l[1] - r, l[0] + r, l[1] + r);
                c.drawBitmap(glow, null, tmpR, bmp);
            }
            bmp.setColorFilter(null); bmp.setAlpha(255);
        }
        if (room.led != null) {
            float on = (time % 1.4f) < 0.7f ? 1f : 0.25f;
            bmp.setColorFilter(redTint); bmp.setAlpha(Math.round(220 * on));
            float r = room.led[2] * (on > 0.5f ? 1f : 0.7f);
            tmpR.set(room.led[0] - r, room.led[1] - r, room.led[0] + r, room.led[1] + r);
            c.drawBitmap(glow, null, tmpR, bmp);
            bmp.setColorFilter(null); bmp.setAlpha(255);
        }
    }

    private void drawExitGlow(Canvas c) {
        float d = room.depth(room.exitY);
        float cx = (room.exitX0 + room.exitX1) / 2f, w = (room.exitX1 - room.exitX0) * 0.62f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * (exitOpen ? 3.2f : 2f));
        bmp.setColorFilter(exitOpen ? goldTint : redTint);
        bmp.setAlpha(Math.round(pulse * (exitOpen ? 140 : 80)));
        tmpR.set(cx - w, room.exitY - w * 0.3f * d * 2, cx + w, room.exitY + w * 0.3f * d * 2);
        c.drawBitmap(glow, null, tmpR, bmp);
        if (exitOpenT >= 0 && exitOpenT < 1.2f) {
            float t = exitOpenT / 1.2f;
            bmp.setColorFilter(goldTint); bmp.setAlpha(Math.round(220 * (1 - t)));
            float r = w * (1 + 2.5f * t);
            tmpR.set(cx - r, room.doorY - r, cx + r, room.doorY + r);
            c.drawBitmap(glow, null, tmpR, bmp);
        }
        bmp.setColorFilter(null); bmp.setAlpha(255);
    }

    private void drawExitSign(Canvas c) {
        float m = Math.max(60f, room.depth(room.doorY) * 150f) * (room.w > 1500 ? 1.4f : 1f);
        float x = room.doorX, y = room.doorY + (float) Math.sin(time * 4) * m * 0.12f;
        float near = clamp((boy.y - room.exitY) / (420f * room.depth(room.exitY)), 0.35f, 1f);
        int a = Math.round(255 * near);
        if (!exitOpen) {
            // padlock
            float s = m * 0.55f;
            stroke.setShader(null); stroke.setStrokeWidth(s * 0.16f); stroke.setColor(Color.argb(a, 70, 50, 30));
            tmpR.set(x - s * 0.32f, y - s * 0.95f, x + s * 0.32f, y - s * 0.25f);
            c.drawArc(tmpR, 180, 180, false, stroke);
            stroke.setColor(Color.argb(a, 230, 200, 120)); stroke.setStrokeWidth(s * 0.09f);
            c.drawArc(tmpR, 180, 180, false, stroke);
            fill.setShader(null); fill.setColor(Color.argb(a, 60, 35, 10));
            tmpR.set(x - s * 0.5f, y - s * 0.62f, x + s * 0.5f, y + s * 0.18f);
            c.drawRoundRect(tmpR, s * 0.12f, s * 0.12f, fill);
            fill.setColor(Color.argb(a, 255, 196, 60));
            tmpR.inset(s * 0.07f, s * 0.07f);
            c.drawRoundRect(tmpR, s * 0.09f, s * 0.09f, fill);
            fill.setColor(Color.argb(a, 90, 50, 10));
            c.drawCircle(x, y - s * 0.28f, s * 0.09f, fill);
            return;
        }
        path.reset();
        path.moveTo(x, y + 0.55f * m); path.lineTo(x - 0.5f * m, y); path.lineTo(x - 0.2f * m, y); path.lineTo(x - 0.2f * m, y - 0.5f * m);
        path.lineTo(x + 0.2f * m, y - 0.5f * m); path.lineTo(x + 0.2f * m, y); path.lineTo(x + 0.5f * m, y); path.close();
        stroke.setShader(null); stroke.setColor(Color.argb(Math.round(a * 0.86f), 60, 25, 0)); stroke.setStrokeWidth(0.12f * m);
        c.drawPath(path, stroke);
        fill.setShader(new LinearGradient(0, y - m * 0.7f, 0, y + m * 0.7f, Color.rgb(255, 160, 20), Color.rgb(255, 236, 120), Shader.TileMode.CLAMP));
        fill.setAlpha(a);
        c.drawPath(path, fill);
        fill.setShader(null); fill.setAlpha(255);
        text.setTextAlign(Paint.Align.CENTER); text.setTextSize(m * 0.5f);
        stroke.setTextAlign(Paint.Align.CENTER); stroke.setTextSize(m * 0.5f); stroke.setStrokeWidth(0.1f * m); stroke.setColor(Color.argb(a, 40, 15, 0));
        c.drawText("EXIT", x, y - 0.75f * m, stroke);
        text.setColor(Color.argb(a, 255, 238, 150));
        c.drawText("EXIT", x, y - 0.75f * m, text);
    }

    private void drawMarker(Canvas c) {
        if (markT < 0 || boy.path == null) return;
        float d = room.depth(markY), r = 60f * d * (1 + 0.25f * (float) Math.sin(time * 6));
        float t = Math.min(1f, markT / 0.4f);
        stroke.setShader(null); stroke.setStrokeWidth(6f * d); stroke.setColor(Color.argb(Math.round(200 * t), 255, 240, 170));
        tmpR.set(markX - r, markY - r * 0.4f, markX + r, markY + r * 0.4f);
        c.drawOval(tmpR, stroke);
        if (markT < 0.5f) {
            float rr = r * (1 + 3 * markT);
            stroke.setColor(Color.argb(Math.round(200 * (1 - markT / 0.5f)), 255, 255, 255));
            tmpR.set(markX - rr, markY - rr * 0.4f, markX + rr, markY + rr * 0.4f);
            c.drawOval(tmpR, stroke);
        }
    }

    /** Small chevron on the floor showing where the swipe is steering. */
    private void drawSteerHint(Canvas c) {
        if (!boy.steering || boy.steerMag <= 0 || phase != PLAY) return;
        double a = Math.toRadians(boy.steerAng);
        float d = nav.sx(boy.y), h = boy.H();
        float fx = (float) Math.cos(a), fy = (float) Math.sin(a);
        float cx = boy.x + fx * h * 0.42f * d, cy = boy.y + fy * h * 0.42f * nav.sy(boy.y);
        float s = h * 0.09f * d;
        float px = -fy, py = fx;
        path.reset();
        path.moveTo(cx + fx * s, cy + fy * s * nav.sy(boy.y) / d);
        path.lineTo(cx - fx * s * 0.6f + px * s, cy + (-fy * s * 0.6f + py * s) * nav.sy(boy.y) / d);
        path.lineTo(cx - fx * s * 0.2f, cy - fy * s * 0.2f * nav.sy(boy.y) / d);
        path.lineTo(cx - fx * s * 0.6f - px * s, cy + (-fy * s * 0.6f - py * s) * nav.sy(boy.y) / d);
        path.close();
        fill.setShader(null); fill.setColor(Color.argb(Math.round(110 * boy.steerMag + 40), 255, 255, 255));
        c.drawPath(path, fill);
    }

    private void drawVignette(Canvas c) {
        boolean chase = false;
        for (Guard g : guards) if (g.chasing()) chase = true;
        if (!chase && phase != CAUGHT) return;
        float a = phase == CAUGHT ? 0.55f : 0.28f + 0.12f * (float) Math.sin(time * 7);
        fill.setShader(new RadialGradient(sw / 2f, sh / 2f, Math.max(sw, sh) * 0.75f, new int[]{Color.argb(0, 200, 0, 0), Color.argb(0, 200, 0, 0), Color.argb(Math.round(255 * a), 170, 0, 0)}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, sw, sh, fill);
        fill.setShader(null);
    }

    // ================================================================== HUD
    private void drawHud(Canvas c) {
        circleSprite(c, "hud/btn_pause.png", pauseX, pauseY, pauseR * (1 - 0.08f * pauseFlash), 255);
        pill(c, coinPill, "hud/icon_coin.png", String.valueOf(coins), coinBump);
        pill(c, gemPill, "rooms/diamond.png", String.valueOf(gems), gemBump);
        Bitmap p = host.bitmap("hud/panel.png");
        if (p != null) {
            bmp.setAlpha(235); c.drawBitmap(p, null, panel, bmp); bmp.setAlpha(255);
            String obj = exitOpen ? "ESCAPE THROUGH THE EXIT!" : level.objective;
            float th = panel.height() * 0.36f, tx = panel.left + panel.width() * 0.2f, maxW = panel.width() * 0.56f;
            text.setTextAlign(Paint.Align.LEFT); text.setTextSize(th);
            float mw = text.measureText(obj); if (mw > maxW) th *= maxW / mw;
            float ty = panel.centerY() + 0.36f * th;
            outlined(c, obj, tx, ty, th, exitOpen ? Color.rgb(255, 225, 90) : Color.WHITE, Paint.Align.LEFT);
            int need = Math.max(1, level.need);
            outlined(c, Math.min(stolen, need) + "/" + need, panel.right - panel.width() * 0.05f, ty, panel.height() * 0.36f, stolen >= need ? Color.rgb(255, 225, 90) : Color.WHITE, Paint.Align.RIGHT);
        }
        if (phase == PLAY || phase == INTRO) {
            circleSprite(c, "hud/btn_run.png", runX, runY, runR * (runId >= 0 ? 0.92f : 1f), 255);
            boolean canGrab = false;
            for (Pickup q : pickups) if (!q.taken && near(q, 1.6f)) canGrab = true;
            if (canGrab) {
                float s = 0.5f + 0.5f * (float) Math.sin(time * 6);
                bmp.setColorFilter(goldTint); bmp.setAlpha(Math.round(s * 160));
                float r = handR * 1.45f; tmpR.set(handX - r, handY - r, handX + r, handY + r);
                c.drawBitmap(glow, null, tmpR, bmp);
                bmp.setColorFilter(null); bmp.setAlpha(255);
            }
            circleSprite(c, "hud/btn_hand.png", handX, handY, handR * (1 - 0.1f * handFlash), 255);
        }
        // swipe guide: a faint ring where the finger went down and a dot under it
        if (steerActive && phase == PLAY) {
            float r = 0.055f * sw;
            stroke.setShader(null); stroke.setStrokeWidth(0.006f * sw); stroke.setColor(Color.argb(90, 255, 255, 255));
            c.drawCircle(steerOX, steerOY, r, stroke);
            fill.setShader(null); fill.setColor(Color.argb(110, 255, 255, 255));
            c.drawCircle(steerX, steerY, r * 0.42f, fill);
            stroke.setColor(Color.argb(60, 255, 255, 255));
            c.drawLine(steerOX, steerOY, steerX, steerY, stroke);
        }
    }

    private void drawFlying(Canvas c) {
        for (Pickup p : pickups) {
            if (!p.flying || p.arrived) continue;
            float[] q = flyPos(p);
            Bitmap b = host.bitmap(p.kind == COIN ? "hud/icon_coin.png" : "rooms/diamond.png");
            if (b == null) continue;
            float r = coinPill.height() * 0.62f * q[2] * (p.kind == CASE ? 1.6f : 1.1f);
            bmp.setColorFilter(p.kind == COIN ? coinTint : gemTint); bmp.setAlpha(170);
            tmpR.set(q[0] - r * 1.8f, q[1] - r * 1.8f, q[0] + r * 1.8f, q[1] + r * 1.8f);
            c.drawBitmap(glow, null, tmpR, bmp);
            bmp.setColorFilter(null); bmp.setAlpha(255);
            float hh = r * b.getHeight() / b.getWidth();
            tmpR.set(q[0] - r, q[1] - hh, q[0] + r, q[1] + hh);
            c.drawBitmap(b, null, tmpR, bmp);
        }
    }

    private void drawMessage(Canvas c) {
        if (msg == null || msgT <= 0) return;
        float a = Math.min(1f, msgT / 0.3f);
        float ts = 0.048f * sw;
        text.setTextSize(ts);
        float w = text.measureText(msg);
        if (w > sw * 0.86f) { ts *= sw * 0.86f / w; text.setTextSize(ts); w = text.measureText(msg); }
        float y = panel.bottom + 0.06f * sh;
        fill.setShader(null); fill.setColor(Color.argb(Math.round(170 * a), 10, 8, 30));
        tmpR.set(sw / 2f - w / 2 - ts * 0.6f, y - ts * 1.05f, sw / 2f + w / 2 + ts * 0.6f, y + ts * 0.45f);
        c.drawRoundRect(tmpR, ts * 0.6f, ts * 0.6f, fill);
        outlined(c, msg, sw / 2f, y, ts, Color.argb(Math.round(255 * a), 255, 238, 170), Paint.Align.CENTER);
    }

    private void drawBanner(Canvas c) {
        float t = phase == INTRO ? phaseT : 3.2f + phaseT;
        float in = Math.min(1f, t / 0.35f), out = clamp((4.3f - t) / 0.4f, 0, 1);
        float a = Math.min(in, out);
        if (a <= 0) return;
        float y = sh * 0.3f - (1 - in) * 0.05f * sh;
        float h = 0.11f * sw;
        fill.setShader(new LinearGradient(0, y - h, 0, y + h * 0.5f, Color.argb(Math.round(200 * a), 20, 16, 50), Color.argb(Math.round(200 * a), 8, 6, 24), Shader.TileMode.CLAMP));
        c.drawRect(0, y - h, sw, y + h * 0.55f, fill);
        fill.setShader(null);
        text.setTextAlign(Paint.Align.CENTER); text.setTextSize(h * 0.36f); text.setColor(Color.argb(Math.round(255 * a), 150, 200, 255));
        c.drawText("LEVEL " + level.number, sw / 2f, y - h * 0.5f, text);
        fitted(c, room.title, sw / 2f, y + h * 0.28f, h * 0.62f, sw * 0.9f, Math.round(255 * a));
    }

    private void drawCaught(Canvas c) {
        float t = phaseT;
        float dim = Math.min(1f, t / 0.5f);
        fill.setShader(null); fill.setColor(Color.argb(Math.round(150 * dim), 0, 0, 0));
        c.drawRect(0, 0, sw, sh, fill);
        float s = t < 0.25f ? 2.2f - 1.2f * t / 0.25f : 1f + 0.03f * (float) Math.sin(t * 5);
        c.save();
        c.translate(sw / 2f, sh * 0.36f);
        c.scale(s, s);
        fitted(c, "CAUGHT!", 0, 0, sw * 0.2f, sw * 0.86f, Math.round(255 * Math.min(1f, t / 0.15f)));
        c.restore();
        if (t > 0.6f) {
            float a = Math.min(1f, (t - 0.6f) / 0.3f);
            outlined(c, "The guard got you. Try again!", sw / 2f, sh * 0.45f, sw * 0.05f, Color.argb(Math.round(255 * a), 255, 238, 170), Paint.Align.CENTER);
            float bs = 1f + 0.04f * (float) Math.sin(t * 4);
            Bitmap r = host.bitmap("layers/complete_replay.png"), h = host.bitmap("layers/complete_home.png");
            bmp.setAlpha(Math.round(255 * a));
            if (r != null) { tmpR.set(retryBtn); tmpR.inset(-retryBtn.width() * (bs - 1) / 2, -retryBtn.height() * (bs - 1) / 2); c.drawBitmap(r, null, tmpR, bmp); }
            if (h != null) c.drawBitmap(h, null, homeBtn, bmp);
            bmp.setAlpha(255);
        }
    }

    private void drawIris(Canvas c) {
        float t = clamp((phaseT - 0.6f) / 0.9f, 0, 1);
        if (phase == DONE) t = 1;
        if (t <= 0) return;
        float cx = screenX((room.exitX0 + room.exitX1) / 2f), cy = screenY(room.exitY - 100 * room.depth(room.exitY));
        float r = (1 - t) * (float) Math.hypot(sw, sh);
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addRect(0, 0, sw, sh, Path.Direction.CW);
        if (r > 1) path.addCircle(cx, cy, r, Path.Direction.CW);
        fill.setShader(null); fill.setColor(Color.BLACK);
        c.drawPath(path, fill);
        path.setFillType(Path.FillType.WINDING);
        if (t > 0.5f) {
            float a = (t - 0.5f) / 0.5f;
            fitted(c, "ESCAPED!", sw / 2f, sh * 0.48f, sw * 0.16f, sw * 0.86f, Math.round(255 * a));
        }
    }

    private void circleSprite(Canvas c, String s, float x, float y, float r, int a) {
        Bitmap b = host.bitmap(s);
        if (b == null) return;
        float w = b.getWidth() / (float) (b.getWidth() - 8) * r;
        tmpR.set(x - w, y - w, x + w, y + w);
        bmp.setAlpha(a); c.drawBitmap(b, null, tmpR, bmp); bmp.setAlpha(255);
    }

    private void pill(Canvas c, RectF r, String icon, String val, float bump) {
        float h = r.height() / 2;
        fill.setShader(pillShader); c.drawRoundRect(r, h, h, fill); fill.setShader(null);
        stroke.setShader(null); stroke.setStrokeWidth(r.height() * 0.05f); stroke.setColor(Color.argb(200, 90, 150, 255));
        c.drawRoundRect(r, h, h, stroke);
        Bitmap b = host.bitmap(icon);
        float iw = r.height() * 1.02f * (1 + 0.25f * bump), il = r.left + r.height() * 0.08f - (iw - r.height() * 1.02f) / 2;
        if (b != null) { float ih = b.getHeight() * iw / b.getWidth(); tmpR.set(il, r.centerY() - ih / 2, il + iw, r.centerY() + ih / 2); c.drawBitmap(b, null, tmpR, bmp); }
        float ts = r.height() * 0.56f * (1 + 0.25f * bump);
        outlined(c, val, (r.left + r.height() * 1.1f + r.right - h * 0.6f) / 2f, r.centerY() + 0.36f * ts, ts, Color.WHITE, Paint.Align.CENTER);
    }

    private void outlined(Canvas c, String s, float x, float y, float size, int color, Paint.Align al) {
        stroke.setShader(null); stroke.setTextAlign(al); stroke.setTextSize(size); stroke.setStrokeWidth(0.16f * size);
        stroke.setColor(Color.argb(Math.round(230 * Color.alpha(color) / 255f), 10, 8, 30));
        c.drawText(s, x, y, stroke);
        text.setShader(null); text.setTextAlign(al); text.setTextSize(size); text.setColor(color);
        c.drawText(s, x, y, text);
    }

    private void fitted(Canvas c, String s, float x, float y, float size, float maxW, int a) {
        text.setTextAlign(Paint.Align.CENTER); stroke.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(size);
        float w = text.measureText(s);
        if (w > maxW) size *= maxW / w;
        text.setTextSize(size); stroke.setTextSize(size); stroke.setStrokeWidth(0.14f * size);
        stroke.setShader(null); stroke.setColor(Color.argb(a, 60, 20, 0));
        c.drawText(s, x, y, stroke);
        text.setShader(new LinearGradient(0, y - size, 0, y, Color.argb(a, 255, 240, 140), Color.argb(a, 255, 165, 20), Shader.TileMode.CLAMP));
        text.setColor(Color.argb(a, 255, 255, 255));
        c.drawText(s, x, y, text);
        text.setShader(null);
    }
}
