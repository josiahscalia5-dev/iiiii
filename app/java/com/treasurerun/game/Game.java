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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * All gameplay: rooms, the boy, guards, pickups, the diamond heist, touch controls, camera, HUD and the
 * room-to-room transition. Uses only android.graphics so the desktop harness can run it unchanged;
 * GameView (Android) just feeds it frames, touches and insets.
 */
final class Game {
    // phases
    static final int INTRO = 0, PLAY = 1, EXIT = 2, CAUGHT = 3, DONE = 4;

    final Host host;
    final Typeface font;

    // screen
    int sw, sh;
    final Rect safe = new Rect();
    final List<Rect> cutouts = new ArrayList<Rect>();
    final int[] corners = new int[4];
    float hs;
    float dp = 2.6f;   // px per dp (set by the view)

    // room
    Room room;
    int roomIndex = -1;
    Nav nav;
    volatile boolean nextLoaded;
    int nextRoom;

    // actors
    final Boy boy = new Boy();
    final List<Guard> guards = new ArrayList<Guard>();

    // progress
    int coins, gems;
    int coinsAtEntry, gemsAtEntry;
    boolean[] coinTaken, gemTaken;
    float[] coinPop, gemPop;
    int roomPicked, roomTotal;
    boolean heistDone;
    float heistT = -1;       // steal animation clock (-1 idle)
    boolean stealOnArrive;
    float flyT = -1;         // diamond flying to the HUD
    float flySX, flySY;      // its screen start
    float lockedHintT;

    // phase
    int phase = DONE;
    float phaseT;
    float time;
    boolean running;

    // camera
    float camX, camY, camScale;
    boolean camReady;
    float camPush;          // extra zoom during the exit push

    // controls
    int steerId = -1;
    float steerX, steerY, steerDownX, steerDownY, steerDownT, steerTravel;
    int sprintId = -1;
    boolean sprint;
    final float[] walkPath = new float[512];
    final float[] steerPath = new float[512];
    int steerN, steerI;
    float steerReplan, steerGoalX, steerGoalY;
    int walkN, walkI;
    boolean walking;         // following walkPath (tap-to-walk)
    float tapMarkX, tapMarkY, tapMarkT = -1;

    // HUD layout
    float pauseX, pauseY, pauseR, runX, runY, runR, handX, handY, handR;
    final RectF coinPill = new RectF(), gemPill = new RectF(), panel = new RectF();
    Shader pillShader;
    float pauseFlash, handFlash, coinBump, gemBump;
    boolean interactNear;
    int pausePointer = -1, handPointer = -1;

    // first-run controls hint
    boolean hintDone;
    float hintT = -1;

    // banner / caught
    String bannerTop = "", bannerMain = "";
    float bannerT = -1;
    Guard catcher;

    // drawing scratch
    final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF tmpR = new RectF();
    final Path path = new Path();
    final Matrix mtx = new Matrix();
    final Bitmap glow;
    final PorterDuffColorFilter goldTint = new PorterDuffColorFilter(Color.rgb(255, 214, 90), PorterDuff.Mode.SRC_IN);
    final PorterDuffColorFilter coinTint = new PorterDuffColorFilter(Color.rgb(255, 200, 60), PorterDuff.Mode.SRC_IN);
    final PorterDuffColorFilter gemTint = new PorterDuffColorFilter(Color.rgb(90, 200, 255), PorterDuff.Mode.SRC_IN);
    final PorterDuffColorFilter whiteTint = new PorterDuffColorFilter(Color.rgb(255, 255, 255), PorterDuff.Mode.SRC_IN);
    Shader arrowShader;
    float arrowShaderY = Float.NaN;

    Game(Host host, Typeface font) {
        this.host = host;
        this.font = font;
        text.setTypeface(font);
        stroke.setTypeface(font);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        glow = makeGlow(128);
    }

    private static Bitmap makeGlow(int n) {
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(n / 2f, n / 2f, n / 2f, new int[]{Color.argb(255, 255, 255, 255), Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(n / 2f, n / 2f, n / 2f, p);
        return b;
    }

    // =====================================================================================
    // lifecycle
    // =====================================================================================

    void startRun() {
        coins = 0;
        gems = 0;
        hintT = -1;
        loadRoomSync(0);
        enterRoom();
        running = true;
    }

    private void loadAssets(Room r) {
        for (String s : r.bg) host.bitmap(s);
        for (Occ o : r.occ) host.bitmap(o.sprite);
        if (r.heist != null) {
            host.bitmap(r.heist.emptyPatch);
            host.bitmap(r.heist.occEmpty);
        }
        host.bitmap(r.walk);
    }

    private void loadRoomSync(int i) {
        loadAssets(RoomData.ROOMS[i]);
        applyRoom(i);
    }

    private void applyRoom(int i) {
        if (roomIndex >= 0 && roomIndex != i) host.forget("rooms/room" + (roomIndex + 1) + "_");
        roomIndex = i;
        nextRoom = -2;   // nothing preloaded for this room yet (a replay may have dropped it)
        room = RoomData.ROOMS[i];
        Bitmap walk = host.bitmap(room.walk);
        nav = new Nav(room, walk);
        host.forget(room.walk);
        coinsAtEntry = coins;
        gemsAtEntry = gems;
        resetRoomState();
        host.log("ROOM " + i + " " + room.title);
    }

    /** everything that a restart (after being caught) puts back */
    private void resetRoomState() {
        coins = coinsAtEntry;
        gems = gemsAtEntry;
        coinTaken = new boolean[room.coins.length];
        gemTaken = new boolean[room.gems.length];
        coinPop = new float[room.coins.length];
        gemPop = new float[room.gems.length];
        roomTotal = room.coins.length + room.gems.length + (room.heist != null ? 1 : 0);
        roomPicked = 0;
        heistDone = room.heist == null;
        heistT = -1;
        flyT = -1;
        stealOnArrive = false;
        if (room.heist != null && room.heist.gate != null) {
            int[] g = room.heist.gate;
            nav.block(g[0], g[1], g[2], g[3], false);
            nav.computeClearance();
        }
        guards.clear();
        for (Room.GuardDef d : room.guards) guards.add(new Guard(room, d));
        catcher = null;
        clearControls();
    }

    private void clearControls() {
        steerId = -1;
        sprintId = -1;
        sprint = false;
        walking = false;
        walkN = walkI = 0;
        pausePointer = handPointer = -1;
    }

    /** start the walk-in from the room's entrance with the name banner */
    private void enterRoom() {
        boy.place(room.enterX, room.enterY, 0);
        phase = INTRO;
        phaseT = 0;
        camReady = false;
        camPush = 0;
        bannerTop = room.label;
        bannerMain = room.title;
        bannerT = 0;
        preloadNext();
    }

    void resume() {
        running = true;
    }

    void pause() {
        running = false;
        clearControls();
    }

    // =====================================================================================
    // layout
    // =====================================================================================

    void setSize(int w, int h) {
        if (w == sw && h == sh) return;
        sw = w;
        sh = h;
        layoutHud();
        camReady = false;
    }

    void layoutHud() {
        if (sw == 0 || sh == 0) return;
        hs = Math.min(sw / 1056f, sh / 2240f) * 0.86f;
        float edge = 0.03f * sw;
        int cutBottom = 0;
        for (Rect r : cutouts) if (r.top < sh / 4) cutBottom = Math.max(cutBottom, r.bottom);
        float top = Math.max(Math.max(safe.top, cutBottom) + 0.012f * sw, edge);
        float left = Math.max(safe.left, 0) + edge;
        float bottom = sh - Math.max(safe.bottom, 0) - 0.02f * sw;
        float l2 = Math.max(left, cornerInset() * 0.55f);
        float right = Math.min(sw - Math.max(safe.right, 0) - edge, sw - cornerInset() * 0.55f);
        pauseR = 61 * hs;
        pauseX = pauseR + l2;
        pauseY = top + pauseR;
        float pillH = 92 * hs, gap = 0.022f * sw, pillW = 250 * hs;
        pillShader = new LinearGradient(0, pauseY - pillH / 2, 0, pauseY + pillH / 2, Color.argb(225, 22, 26, 58), Color.argb(225, 10, 10, 30), Shader.TileMode.CLAMP);
        coinPill.set(pauseX + pauseR + gap, pauseY - pillH / 2, pauseX + pauseR + gap + pillW, pauseY + pillH / 2);
        gemPill.set(coinPill.right + gap, coinPill.top, coinPill.right + gap + pillW, coinPill.bottom);
        if (gemPill.right > right) {
            float k = (right - coinPill.left - gap) / (2 * pillW);
            coinPill.right = coinPill.left + pillW * k;
            gemPill.set(coinPill.right + gap, coinPill.top, coinPill.right + gap + pillW * k, coinPill.bottom);
        }
        float pw = Math.min(931 * hs, right - l2);
        panel.set((sw - pw) / 2, pauseY + pauseR + 0.018f * sw, (sw + pw) / 2, pauseY + pauseR + 0.018f * sw + 162 * pw / 931);
        runR = 132 * hs;
        handR = 118 * hs;
        runX = right - runR;
        runY = bottom - 199 * hs + 20 * hs;
        handX = right - handR - 6 * hs;
        handY = runY - runR - handR - 36 * hs;
    }

    private float cornerInset() {
        int m = 0;
        for (int c : corners) m = Math.max(m, c);
        return m * 0.3f;
    }

    // =====================================================================================
    // input (screen px)
    // =====================================================================================

    private boolean inCircle(float x, float y, float cx, float cy, float r) {
        return Math.hypot(x - cx, y - cy) < r;
    }

    void touchDown(int id, float x, float y) {
        if (!running || phase == DONE) return;
        if (inCircle(x, y, pauseX, pauseY, pauseR * 1.35f)) {
            pausePointer = id;
            pauseFlash = 1;
            return;
        }
        if (inCircle(x, y, runX, runY, runR * 1.12f)) {
            sprintId = id;
            sprint = true;
            host.haptic(0);
            return;
        }
        if (inCircle(x, y, handX, handY, handR * 1.15f)) {
            handPointer = id;
            handFlash = 1;
            onHand();
            return;
        }
        if (steerId == -1 && (phase == PLAY || phase == INTRO)) {
            steerId = id;
            steerX = steerDownX = x;
            steerY = steerDownY = y;
            steerDownT = time;
            steerTravel = 0;
            walking = false;   // holding overrides a tap-walk in progress
            stealOnArrive = false;
        }
    }

    void touchMove(int id, float x, float y) {
        if (id == steerId) {
            steerTravel = Math.max(steerTravel, (float) Math.hypot(x - steerDownX, y - steerDownY));
            steerX = x;
            steerY = y;
        }
    }

    void touchUp(int id, float x, float y) {
        if (id == pausePointer) {
            pausePointer = -1;
            if (inCircle(x, y, pauseX, pauseY, pauseR * 1.5f)) host.onGamePause();
            return;
        }
        if (id == sprintId) {
            sprintId = -1;
            sprint = false;
            return;
        }
        if (id == handPointer) {
            handPointer = -1;
            return;
        }
        if (id == steerId) {
            steerId = -1;
            boolean tap = time - steerDownT < 0.3f && steerTravel < 14 * dp;
            if (tap && phase == PLAY) onTap(x, y);
            if (phase == PLAY) hintDone = true;
        }
    }

    void touchCancel() {
        clearControls();
    }

    float worldX(float sx) { return (sx - sw / 2f) / camScale + camX; }

    float worldY(float sy) { return (sy - sh / 2f) / camScale + camY; }

    float screenX(float wx) { return (wx - camX) * camScale + sw / 2f; }

    float screenY(float wy) { return (wy - camY) * camScale + sh / 2f; }

    /** quick tap: steal if it's on the diamond, otherwise walk there (around obstacles) */
    private void onTap(float sx, float sy) {
        float wx = worldX(sx), wy = worldY(sy);
        Room.Heist h = room.heist;
        if (h != null && !heistDone && heistT < 0 && Math.hypot(wx - h.gemX, wy - h.gemY) < h.tapR) {
            if (nearHeist()) startSteal();
            else {
                stealOnArrive = walkTo(h.standX, h.standY);
            }
            return;
        }
        float[] p = new float[2];
        if (!nav.canStand(wx, wy, room.feetR)) {
            if (!nav.nearestStandable(wx, wy, room.feetR, p)) return;
            wx = p[0];
            wy = p[1];
        }
        walkTo(wx, wy);
        tapMarkX = wx;
        tapMarkY = wy;
        tapMarkT = 0;
    }

    private boolean walkTo(float x, float y) {
        walkN = nav.path(boy.x, boy.y, x, y, room.feetR, walkPath, walkPath.length / 2);
        walkI = 0;
        walking = walkN > 0;
        return walking;
    }

    private boolean nearHeist() {
        Room.Heist h = room.heist;
        return h != null && nav.floorDist(boy.x, boy.y, h.standX, h.standY) < room.boyH * 0.42f;
    }

    private void onHand() {
        host.haptic(1);
        if (phase != PLAY) return;
        if (!heistDone && heistT < 0 && nearHeist()) {
            startSteal();
            return;
        }
        if (interactNear) {
            beginExit();
            return;
        }
        float r = 5 * room.depth(boy.y) * room.feetR;
        for (int i = 0; i < room.coins.length; i++) if (!coinTaken[i] && near(room.coins[i], r)) take(true, i);
        for (int i = 0; i < room.gems.length; i++) if (!gemTaken[i] && near(room.gems[i], r)) take(false, i);
    }

    // =====================================================================================
    // update
    // =====================================================================================

    void frame(Canvas c, float dt) {
        if (room == null || sw == 0) return;
        if (running) update(dt);
        render(c);
    }

    void update(float dt) {
        time += dt;
        pauseFlash = Math.max(0, pauseFlash - 3 * dt);
        handFlash = Math.max(0, handFlash - 3 * dt);
        coinBump = Math.max(0, coinBump - 4 * dt);
        gemBump = Math.max(0, gemBump - 4 * dt);
        lockedHintT = Math.max(-1, lockedHintT - dt);
        if (tapMarkT >= 0) { tapMarkT += dt; if (tapMarkT > 0.6f) tapMarkT = -1; }
        if (bannerT >= 0) { bannerT += dt; if (bannerT > 3.2f) bannerT = -1; }
        // controls hint: once, at the start of the first run, until the player has walked (or 7s)
        if (!hintDone && roomIndex == 0 && phase == PLAY && hintT == -1) hintT = 0;
        if (hintT >= 0) {
            hintT += dt;
            if (hintDone && hintT < 6.6f) hintT = 6.6f;   // fade out as soon as he walks
            if (hintT > 7) hintT = -2;                    // done for this run
        }
        for (int i = 0; i < coinPop.length; i++) if (coinTaken[i] && coinPop[i] < 1) coinPop[i] = Math.min(1, coinPop[i] + dt / 0.4f);
        for (int i = 0; i < gemPop.length; i++) if (gemTaken[i] && gemPop[i] < 1) gemPop[i] = Math.min(1, gemPop[i] + dt / 0.45f);
        phaseT += dt;

        float moveFx = 0, moveFy = 0;   // desired floor-space direction * speed factor (0..1)
        boolean auto = false;           // scripted walking ignores collisions
        switch (phase) {
            case INTRO: {
                float dx = room.spawnX - boy.x, dy = room.spawnY - boy.y;
                float d = (float) Math.hypot(dx, dy);
                if (d > 6 * room.depth(boy.y)) {
                    float[] f = floorDir(boy.x, boy.y, room.spawnX, room.spawnY);
                    float ease = Math.min(1, d / (room.boyH * room.depth(boy.y) * 0.25f));
                    moveFx = f[0] * Math.max(0.35f, ease);
                    moveFy = f[1] * Math.max(0.35f, ease);
                    auto = !nav.canStand(boy.x, boy.y, room.feetR);
                } else if (phaseT > 0.5f) {
                    phase = PLAY;
                    phaseT = 0;
                }
                if (phaseT > 4) { phase = PLAY; phaseT = 0; }
                break;
            }
            case PLAY: {
                if (heistT >= 0) break;   // stealing: stand still
                if (steerId >= 0) {
                    float wx = worldX(steerX), wy = worldY(steerY);
                    float[] f = floorDir(boy.x, boy.y, wx, wy);
                    float dist = f[2];
                    float k = Math.min(1, dist / (room.boyH * 0.3f));
                    if (dist > room.boyH * 0.06f) {
                        float gx = wx, gy = wy;
                        // straight line blocked: follow an A* route towards the finger instead
                        if (!nav.clearLine(boy.x, boy.y, wx, wy, room.feetR * 0.9f)) {
                            steerReplan -= dt;
                            if (steerReplan <= 0 || Math.hypot(wx - steerGoalX, wy - steerGoalY) > 60) {
                                steerReplan = 0.3f;
                                steerGoalX = wx;
                                steerGoalY = wy;
                                float[] p = new float[2];
                                float tx = wx, ty = wy;
                                if (!nav.canStand(tx, ty, room.feetR) && nav.nearestStandable(tx, ty, room.feetR, p)) { tx = p[0]; ty = p[1]; }
                                steerN = nav.path(boy.x, boy.y, tx, ty, room.feetR, steerPath, steerPath.length / 2);
                                steerI = 0;
                            }
                            while (steerI < steerN - 1 && floorDir(boy.x, boy.y, steerPath[steerI * 2], steerPath[steerI * 2 + 1])[2] < room.boyH * 0.1f) steerI++;
                            if (steerI < steerN) { gx = steerPath[steerI * 2]; gy = steerPath[steerI * 2 + 1]; }
                        } else steerN = 0;
                        f = floorDir(boy.x, boy.y, gx, gy);
                        moveFx = f[0] * k;
                        moveFy = f[1] * k;
                    }
                } else if (walking) {
                    float tx = walkPath[walkI * 2], ty = walkPath[walkI * 2 + 1];
                    float[] f = floorDir(boy.x, boy.y, tx, ty);
                    boolean last = walkI == walkN - 1;
                    if (f[2] < room.boyH * (last ? 0.05f : 0.12f)) {
                        walkI++;
                        if (walkI >= walkN) {
                            walking = false;
                            if (stealOnArrive) { stealOnArrive = false; if (nearHeist()) startSteal(); }
                        }
                    } else {
                        float k = last ? Math.min(1, f[2] / (room.boyH * 0.2f) + 0.25f) : 1;
                        moveFx = f[0] * k;
                        moveFy = f[1] * k;
                    }
                }
                break;
            }
            case EXIT: {
                float tx = room.doorX, ty = room.exitY - 240 * room.depth(room.exitY);
                float[] f = floorDir(boy.x, boy.y, tx, ty);
                if (f[2] > 4) { moveFx = f[0]; moveFy = f[1]; }
                auto = true;
                camPush = Math.min(1, phaseT / 0.9f);
                if (phaseT >= 1.05f && nextLoaded) {
                    if (nextRoom < 0) {
                        phase = DONE;
                        running = false;
                        host.onGameFinished(coins, gems);
                        return;
                    }
                    applyRoom(nextRoom);
                    enterRoom();
                    return;
                }
                break;
            }
            case CAUGHT: {
                boy.hop = phaseT < 0.35f ? (float) Math.sin(phaseT / 0.35f * Math.PI) : 0;
                if (phaseT > 1.7f) {
                    boy.hop = 0;
                    resetRoomState();
                    enterRoom();
                    return;
                }
                break;
            }
            default:
                return;
        }

        moveBoy(dt, moveFx, moveFy, auto);

        // steal animation
        if (heistT >= 0) updateSteal(dt);
        if (flyT >= 0) {
            flyT += dt / 0.8f;
            if (flyT >= 1) {
                flyT = -1;
                gems++;
                gemBump = 1;
                host.haptic(1);
            }
        }

        // pickups (walk over)
        float d = Math.max(0.12f, room.depth(boy.y));
        float r = 2.2f * room.feetR * d;
        if (phase == PLAY || phase == INTRO) {
            for (int i = 0; i < room.coins.length; i++) if (!coinTaken[i] && near(room.coins[i], r)) take(true, i);
            for (int i = 0; i < room.gems.length; i++) if (!gemTaken[i] && near(room.gems[i], r)) take(false, i);
        }

        // guards
        boolean active = phase == PLAY;
        if (phase != CAUGHT) for (Guard g : guards) {
            boolean caught = g.update(dt, nav, boy, sprint && boy.moveAmt > 0.5f, active);
            if (caught && phase == PLAY) {
                phase = CAUGHT;
                phaseT = 0;
                catcher = g;
                clearControls();
                host.haptic(2);
            }
        }

        // exit
        interactNear = false;
        if (phase == PLAY && heistDone) {
            if (boy.x > room.exitX0 && boy.x < room.exitX1 && boy.y < room.exitY + 260 * room.depth(room.exitY)) interactNear = true;
            if (boy.x > room.exitX0 && boy.x < room.exitX1 && boy.y <= room.exitY) beginExit();
        }
        if (phase == PLAY && !heistDone && room.heist != null && room.heist.gate != null) {
            int[] g = room.heist.gate;
            if (boy.x > g[0] - 40 && boy.x < g[2] + 40 && boy.y < g[3] + 90 * room.depth(g[3]) && lockedHintT < 0) lockedHintT = 2.2f;
        }
        updateCamera(dt);
    }

    /** floor-space direction (unit) and distance from a to b; out = {fx, fy, dist} */
    private final float[] dirOut = new float[3];

    private float[] floorDir(float ax, float ay, float bx, float by) {
        float d = Math.max(0.1f, room.depth(ay));
        float fx = (bx - ax) / d, fy = (by - ay) / (room.vFactor * (float) Math.pow(d, room.vPow));
        float len = (float) Math.hypot(fx, fy);
        dirOut[2] = len;
        if (len < 1e-4f) { dirOut[0] = dirOut[1] = 0; } else { dirOut[0] = fx / len; dirOut[1] = fy / len; }
        return dirOut;
    }

    private void moveBoy(float dt, float fx, float fy, boolean auto) {
        float d = Math.max(0.12f, room.depth(boy.y));
        float vf = room.vFactor * (float) Math.pow(d, room.vPow);
        // scripted walks: the far end of a steep room is slow in world px, so hurry the doorway walk along
        float spd = room.speed * (sprint && phase == PLAY ? 1.65f : phase == EXIT ? 2.4f : 1f);
        float tvx = fx * spd * d, tvy = fy * spd * vf;
        float k = Math.min(1, (Math.hypot(fx, fy) > 0.01 ? 12 : 9) * dt);
        boy.vx += (tvx - boy.vx) * k;
        boy.vy += (tvy - boy.vy) * k;
        float ox = boy.x, oy = boy.y;
        float nx = boy.x + boy.vx * dt, ny = boy.y + boy.vy * dt;
        if (auto || nav.canStand(nx, ny, room.feetR)) { boy.x = nx; boy.y = ny; }
        else if (!flowAround(dt, fx, fy, spd, d, vf)) {
            if (nav.canStand(nx, boy.y, room.feetR)) { boy.x = nx; boy.vy *= 0.5f; }
            else if (nav.canStand(boy.x, ny, room.feetR)) { boy.y = ny; boy.vx *= 0.5f; }
            else { boy.vx *= 0.3f; boy.vy *= 0.3f; if (walking) walking = false; }
        }
        float mdx = (boy.x - ox) / d, mdy = (boy.y - oy) / vf;
        float moved = (float) Math.hypot(mdx, mdy);
        boolean driven = Math.hypot(fx, fy) > 0.01;
        boy.animate(dt, moved, driven ? fx : mdx, driven ? fy : mdy, room.boyH, driven, sprint && phase == PLAY);
    }

    /** blocked head-on: slide around the obstacle by trying the wanted direction turned +-35 / +-70 degrees */
    private boolean flowAround(float dt, float fx, float fy, float spd, float d, float vf) {
        float len = (float) Math.hypot(fx, fy);
        if (len < 0.05f) return false;
        float step = spd * len * dt;
        for (int k = 1; k <= 4; k++) {
            float a = (float) Math.toRadians(k <= 2 ? 35 : 70) * (k % 2 == 1 ? 1 : -1);
            // prefer the side we were already drifting towards
            if (k % 2 == 1 && (boy.vx * fy - boy.vy * fx) > 0) a = -a;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float rx = (fx * c - fy * s) / len, ry = (fx * s + fy * c) / len;
            float nx = boy.x + rx * step * d * (k <= 2 ? 0.8f : 0.55f), ny = boy.y + ry * step * vf * (k <= 2 ? 0.8f : 0.55f);
            if (nav.canStand(nx, ny, room.feetR)) {
                boy.vx = (nx - boy.x) / dt;
                boy.vy = (ny - boy.y) / dt;
                boy.x = nx;
                boy.y = ny;
                return true;
            }
        }
        return false;
    }

    private boolean near(float[] p, float r) {
        return Math.hypot(boy.x - p[0], (boy.y - p[1]) * 1.5f) < p[2] * 0.35f + r;
    }

    private void take(boolean coin, int i) {
        if (coin) { coinTaken[i] = true; coins++; coinBump = 1; }
        else { gemTaken[i] = true; gems++; gemBump = 1; }
        roomPicked++;
        host.haptic(0);
    }

    // ---- heist ----
    private void startSteal() {
        Room.Heist h = room.heist;
        heistT = 0;
        walking = false;
        steerId = -1;
        boy.faceLock = Boy.wrap((float) Math.atan2((h.gemX - boy.x) * 0.2f, -(h.gemY - boy.y)));
        host.haptic(1);
    }

    private void updateSteal(float dt) {
        Room.Heist h = room.heist;
        // turn, reach (0.35s), grab at 0.45s, lower the arm
        heistT += dt;
        float t = heistT;
        boy.reach = t < 0.15f ? 0 : t < 0.45f ? (t - 0.15f) / 0.3f : t < 0.8f ? 1 - (t - 0.45f) / 0.35f : 0;
        boy.reach = boy.reach * boy.reach * (3 - 2 * boy.reach);
        if (!heistDone && t >= 0.45f) {
            heistDone = true;
            roomPicked++;
            flyT = 0;
            flySX = screenX(h.gemX);
            flySY = screenY(h.gemY);
            if (h.gate != null) {
                nav.block(h.gate[0], h.gate[1], h.gate[2], h.gate[3], true);
                nav.computeClearance();
            }
            host.haptic(1);
        }
        if (t >= 0.85f) {
            heistT = -1;
            boy.reach = 0;
            boy.faceLock = -999;
        }
    }

    private void beginExit() {
        phase = EXIT;
        phaseT = 0;
        clearControls();
        if (roomIndex + 1 < RoomData.ROOMS.length) preloadNext();
        else {
            nextRoom = -1;
            nextLoaded = true;
        }
        host.haptic(1);
    }

    /** decode the next room in the background (started as soon as a room begins, so the doorway never waits) */
    private void preloadNext() {
        int n = roomIndex + 1;
        if (n >= RoomData.ROOMS.length || nextRoom == n) return;
        nextRoom = n;
        nextLoaded = false;
        final Room r = RoomData.ROOMS[n];
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loadAssets(r);
                nextLoaded = true;
            }
        }, "room-loader");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // ---- camera ----
    void updateCamera(float dt) {
        float base = Math.max(sw / (float) room.w, sh / (float) room.h);
        float view = room.viewW > 0 ? Math.max(1, sw / room.viewW / base) : 1;
        float far = clamp((1 - room.depth(boy.y)) / (1 - room.sFar), 0, 1);
        float want = view * ((3 - 2 * far) * far * far * (room.zoomFar - 1) + 1) * base;
        want *= 1 + 0.35f * camPush * camPush;
        if (camReady) camScale += (want - camScale) * Math.min(1, 2.5f * dt * (camPush > 0 ? 2 : 1));
        else camScale = want;
        camScale = Math.max(camScale, base);
        float vw = sw / camScale, vh = sh / camScale;
        float tx = boy.x, ty = boy.y - (room.feetAt - 0.5f) * vh;
        if (camPush > 0) { tx += (room.doorX - tx) * camPush * 0.6f; ty += (boy.y - boy.height(room) * 0.5f - ty) * camPush * 0.5f; }
        tx = clamp(tx, vw / 2, room.w - vw / 2);
        ty = clamp(ty, vh / 2, room.h - vh / 2);
        if (!camReady) {
            camX = tx;
            camY = ty;
            camReady = true;
        } else {
            float k = Math.min(1, 4.5f * dt);
            camX += (tx - camX) * k;
            camY += (ty - camY) * k;
        }
        camX = clamp(camX, vw / 2, room.w - vw / 2);
        camY = clamp(camY, vh / 2, room.h - vh / 2);
    }

    static float clamp(float v, float a, float b) {
        return v < a ? a : v > b ? b : v;
    }

    // =====================================================================================
    // render
    // =====================================================================================

    private static final class Item {
        int kind, index;
        float depth;
    }

    private final Item[] pool = new Item[256];
    private final float[] itemBoxes = new float[4 * 64], itemDepth = new float[64];
    private final List<Item> items = new ArrayList<Item>();
    private final Comparator<Item> byDepth = new Comparator<Item>() {
        @Override
        public int compare(Item a, Item b) {
            return Float.compare(a.depth, b.depth);
        }
    };

    {
        for (int i = 0; i < pool.length; i++) pool[i] = new Item();
    }

    static final int K_BOY = 0, K_COIN = 1, K_GEM = 2, K_OCC = 3, K_GUARD = 4;

    void render(Canvas c) {
        if (!camReady) updateCamera(0);
        c.save();
        c.translate(sw / 2f, sh / 2f);
        c.scale(camScale, camScale);
        c.translate(-camX, -camY);
        bmp.setAlpha(255);
        for (int i = 0; i < room.bg.length; i++) {
            Bitmap b = host.bitmap(room.bg[i]);
            if (b != null) c.drawBitmap(b, 0, room.bgY[i], bmp);
        }
        Room.Heist h = room.heist;
        if (h != null && heistDone) {
            Bitmap p = host.bitmap(h.emptyPatch);
            if (p != null) c.drawBitmap(p, h.patchX, h.patchY, bmp);
        }
        if (heistDone) drawExitGlow(c);
        for (Guard g : guards) g.drawCone(c, nav, time);
        if (tapMarkT >= 0) drawTapMark(c);
        drawWorldItems(c);
        if (h != null && !heistDone) drawSparkles(c);
        for (Guard g : guards) g.drawBubble(c, font, time);
        if (heistDone) drawExitArrow(c);
        if (lockedHintT > 0 && !heistDone) drawHint(c, "Steal the diamond first!");
        c.restore();
        drawHud(c);
        if (flyT >= 0) drawFlyingGem(c);
        drawOverlays(c);
    }

    private void drawWorldItems(Canvas c) {
        items.clear();
        int n = 0;
        // dynamic things and their world boxes (occluders are only needed where something is behind them)
        float[] boxes = itemBoxes, bdepth = itemDepth;
        int nb = 0;
        Item it = pool[n++];
        it.kind = K_BOY;
        it.index = 0;
        it.depth = boy.y;
        items.add(it);
        float bh = boy.height(room);
        nb = box(boxes, bdepth, nb, boy.x - 0.55f * bh, boy.y - 1.15f * bh, boy.x + 0.55f * bh, boy.y + 0.1f * bh, boy.y);
        for (int i = 0; i < guards.size() && n < pool.length; i++) {
            Guard g = guards.get(i);
            it = pool[n++];
            it.kind = K_GUARD;
            it.index = i;
            it.depth = g.y;
            items.add(it);
            float gh = room.guardH * room.depth(g.y);
            nb = box(boxes, bdepth, nb, g.x - 0.5f * gh, g.y - 1.3f * gh, g.x + 0.5f * gh, g.y + 0.1f * gh, g.y);
        }
        for (int i = 0; i < room.coins.length && n < pool.length; i++) {
            if (coinTaken[i] && coinPop[i] >= 1) continue;
            float[] p = room.coins[i];
            it = pool[n++];
            it.kind = K_COIN;
            it.index = i;
            it.depth = p[1];
            items.add(it);
            nb = box(boxes, bdepth, nb, p[0] - p[2], p[1] - 2.5f * p[2], p[0] + p[2], p[1] + 0.2f * p[2], p[1]);
        }
        for (int i = 0; i < room.gems.length && n < pool.length; i++) {
            if (gemTaken[i] && gemPop[i] >= 1) continue;
            float[] p = room.gems[i];
            it = pool[n++];
            it.kind = K_GEM;
            it.index = i;
            it.depth = p[1];
            items.add(it);
            nb = box(boxes, bdepth, nb, p[0] - p[2], p[1] - 2.5f * p[2], p[0] + p[2], p[1] + 0.2f * p[2], p[1]);
        }
        float hw = sw / camScale / 2, hh = sh / camScale / 2;
        for (int i = 0; i < room.occ.length && n < pool.length; i++) {
            Occ o = room.occ[i];
            if (o.x > camX + hw || o.x + o.w < camX - hw || o.y > camY + hh || o.y + o.h < camY - hh) continue;
            boolean need = false;
            for (int b = 0; b < nb && !need; b++) {
                if (o.base > bdepth[b] && o.x <= boxes[b * 4 + 2] && o.x + o.w >= boxes[b * 4] && o.y <= boxes[b * 4 + 3] && o.y + o.h >= boxes[b * 4 + 1]) need = true;
            }
            if (!need) continue;
            it = pool[n++];
            it.kind = K_OCC;
            it.index = i;
            it.depth = o.base;
            items.add(it);
        }
        Collections.sort(items, byDepth);
        for (Item t : items) {
            switch (t.kind) {
                case K_BOY:
                    boy.draw(c, room, host);
                    break;
                case K_GUARD:
                    guards.get(t.index).draw(c, host, time);
                    break;
                case K_COIN:
                    drawPickup(c, room.coins[t.index], "rooms/coin.png", coinPop[t.index], coinTaken[t.index], coinTint, t.index);
                    break;
                case K_GEM:
                    drawPickup(c, room.gems[t.index], "rooms/diamond.png", gemPop[t.index], gemTaken[t.index], gemTint, t.index + 7);
                    break;
                default: {
                    Occ o = room.occ[t.index];
                    String s = o.sprite;
                    if (room.heist != null && heistDone && s.equals(room.heist.occ)) s = room.heist.occEmpty;
                    Bitmap b = host.bitmap(s);
                    if (b != null) {
                        bmp.setAlpha(255);
                        c.drawBitmap(b, o.x, o.y, bmp);
                    }
                }
            }
        }
    }

    private static int box(float[] boxes, float[] depth, int n, float l, float t, float r, float b, float d) {
        if (n >= depth.length) return n;
        boxes[n * 4] = l;
        boxes[n * 4 + 1] = t;
        boxes[n * 4 + 2] = r;
        boxes[n * 4 + 3] = b;
        depth[n] = d;
        return n + 1;
    }

    private void drawPickup(Canvas c, float[] p, String sprite, float pop, boolean taken, ColorFilter tint, int seed) {
        Bitmap b = host.bitmap(sprite);
        if (b == null) return;
        float size = p[2];
        float hover = 0.62f * size + (float) Math.sin(time * 2.6f + seed * 1.3f) * size * 0.08f;
        float grow = 1, alpha = 1, rise = 0;
        if (taken) {
            grow = 1 + 0.6f * pop;
            alpha = 1 - pop;
            rise = 0.9f * size * pop;
        }
        fill.setShader(null);
        if (!taken) {
            fill.setColor(Color.argb(70, 20, 5, 20));
            tmpR.set(p[0] - 0.36f * size, p[1] - 0.09f * size, p[0] + 0.36f * size, p[1] + 0.09f * size);
            c.drawOval(tmpR, fill);
        }
        float cy = p[1] - hover - rise;
        bmp.setColorFilter(tint);
        bmp.setAlpha(Math.round(150 * alpha * (0.85f + 0.15f * (float) Math.sin(time * 3 + seed))));
        float gr = 1.25f * size * grow;
        tmpR.set(p[0] - gr, cy - gr, p[0] + gr, cy + gr);
        c.drawBitmap(glow, null, tmpR, bmp);
        bmp.setColorFilter(null);
        bmp.setAlpha(Math.round(alpha * 255));
        float hwid = size / 2 * grow;
        float hh = b.getHeight() * hwid / b.getWidth();
        float spin = sprite.contains("coin") ? 0.75f + 0.25f * (float) Math.cos(time * 2.2f + seed) : 1;
        tmpR.set(p[0] - hwid * spin, cy - hh, p[0] + hwid * spin, cy + hh);
        c.drawBitmap(b, null, tmpR, bmp);
        bmp.setAlpha(255);
    }

    private void drawSparkles(Canvas c) {
        Room.Heist h = room.heist;
        float near = nav.floorDist(boy.x, boy.y, h.standX, h.standY) / room.boyH;
        float k = clamp(1.6f - near, 0, 1);
        float base = 0.35f + 0.65f * k;
        // glow behind the gem
        bmp.setColorFilter(gemTint);
        bmp.setAlpha(Math.round(90 * base * (0.8f + 0.2f * (float) Math.sin(time * 3))));
        float gr = h.gemSize * (1.1f + 0.3f * k);
        tmpR.set(h.gemX - gr, h.gemY - gr, h.gemX + gr, h.gemY + gr);
        c.drawBitmap(glow, null, tmpR, bmp);
        bmp.setColorFilter(null);
        bmp.setAlpha(255);
        // twinkles
        int count = 3 + Math.round(5 * k);
        for (int i = 0; i < count; i++) {
            float ph = time * (0.9f + 0.13f * i) + i * 1.7f;
            float life = ph - (float) Math.floor(ph);
            float a = (float) Math.sin(life * Math.PI);
            float ang = i * 2.4f + (float) Math.floor(ph) * 1.3f;
            float rr = h.gemSize * (0.25f + 0.5f * ((i * 37 % 10) / 10f));
            float sx = h.gemX + (float) Math.cos(ang) * rr, sy = h.gemY + (float) Math.sin(ang) * rr * 0.9f;
            star(c, sx, sy, h.gemSize * (0.08f + 0.1f * k) * a, Color.argb(Math.round(255 * a * base), 255, 255, 255));
        }
        // "tap me" ring when he's close enough to grab it
        if (k > 0.55f && heistT < 0) {
            float pulse = (time * 1.3f) % 1f;
            stroke.setStrokeWidth(h.gemSize * 0.05f);
            stroke.setColor(Color.argb(Math.round(200 * (1 - pulse)), 160, 230, 255));
            c.drawCircle(h.gemX, h.gemY, h.gemSize * (0.5f + 0.4f * pulse), stroke);
        }
    }

    private void star(Canvas c, float x, float y, float r, int color) {
        if (r <= 0.3f) return;
        path.reset();
        for (int i = 0; i < 8; i++) {
            float a = (float) (i * Math.PI / 4);
            float rr = i % 2 == 0 ? r : r * 0.28f;
            float px = x + (float) Math.cos(a) * rr, py = y + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        fill.setShader(null);
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    private void drawTapMark(Canvas c) {
        float t = tapMarkT / 0.6f;
        float d = room.depth(tapMarkY);
        float r = room.boyH * d * 0.12f * (0.6f + 0.6f * t);
        stroke.setStrokeWidth(room.boyH * d * 0.015f);
        stroke.setColor(Color.argb(Math.round(220 * (1 - t)), 255, 236, 150));
        tmpR.set(tapMarkX - r, tapMarkY - r * 0.4f, tapMarkX + r, tapMarkY + r * 0.4f);
        c.drawOval(tmpR, stroke);
    }

    private void drawHint(Canvas c, String s) {
        float a = clamp(lockedHintT / 0.4f, 0, 1) * clamp((2.2f - lockedHintT) / 0.2f, 0, 1);
        float size = boy.height(room) * 0.11f;
        float x = boy.x, y = boy.y - boy.height(room) * 1.12f;
        text.setTextSize(size);
        float w = text.measureText(s) + size;
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(200 * a), 20, 16, 40));
        tmpR.set(x - w / 2, y - size * 1.1f, x + w / 2, y + size * 0.45f);
        c.drawRoundRect(tmpR, size * 0.7f, size * 0.7f, fill);
        outlined(c, s, x, y, size, Color.argb(Math.round(255 * a), 255, 225, 110), Paint.Align.CENTER, a);
    }

    private void drawExitGlow(Canvas c) {
        float d = room.depth(room.exitY);
        float cx = (room.exitX0 + room.exitX1) / 2;
        float r = (room.exitX1 - room.exitX0) * 0.62f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 3.2f);
        bmp.setColorFilter(goldTint);
        bmp.setAlpha(Math.round(pulse * 120));
        tmpR.set(cx - r, room.exitY - r * 0.3f * d * 2, cx + r, room.exitY + r * 0.3f * d * 2);
        c.drawBitmap(glow, null, tmpR, bmp);
        bmp.setColorFilter(null);
        bmp.setAlpha(255);
    }

    private void drawExitArrow(Canvas c) {
        float vis = clamp((boy.y - room.exitY) / (420 * room.depth(room.exitY)), 0, 1);
        if (phase == EXIT) vis *= clamp(1 - phaseT / 0.3f, 0, 1);
        if (vis <= 0.02f) return;
        int a = Math.round(255 * vis);
        float size = Math.max(60, room.depth(room.doorY) * 150) * (room.w > 1500 ? 1.4f : 1);
        float bob = (float) Math.sin(time * 4) * size * 0.18f;
        float x = room.doorX, y = room.doorY + bob;
        path.reset();
        path.moveTo(x, y + 0.55f * size);
        path.lineTo(x - size * 0.5f, y);
        path.lineTo(x - size * 0.2f, y);
        path.lineTo(x - size * 0.2f, y - size * 0.5f);
        path.lineTo(x + size * 0.2f, y - size * 0.5f);
        path.lineTo(x + size * 0.2f, y);
        path.lineTo(x + size * 0.5f, y);
        path.close();
        c.save();
        c.rotate(180, x, y);
        stroke.setColor(Color.argb(Math.round(vis * 220), 60, 25, 0));
        stroke.setStrokeWidth(0.12f * size);
        c.drawPath(path, stroke);
        if (arrowShader == null || arrowShaderY != room.doorY) {
            arrowShader = new LinearGradient(0, -size * 0.7f, 0, size * 0.7f, Color.rgb(255, 160, 20), Color.rgb(255, 236, 120), Shader.TileMode.CLAMP);
            arrowShaderY = room.doorY;
        }
        mtx.setTranslate(0, y);
        arrowShader.setLocalMatrix(mtx);
        fill.setShader(arrowShader);
        fill.setAlpha(a);
        c.drawPath(path, fill);
        fill.setShader(null);
        fill.setAlpha(255);
        c.restore();
        outlined(c, "EXIT", x, y - 0.75f * size, size * 0.5f, Color.argb(a, 255, 238, 150), Paint.Align.CENTER, vis);
    }

    private void drawFlyingGem(Canvas c) {
        Bitmap b = host.bitmap("rooms/diamond.png");
        if (b == null) return;
        float t = flyT;
        float e = t * t * (3 - 2 * t);
        float tx = gemPill.left + gemPill.height() * 0.6f, ty = gemPill.centerY();
        // arc: up first, then into the counter
        float mx = (flySX + tx) / 2 + sw * 0.1f, my = Math.min(flySY, ty) - sh * 0.08f;
        float x = (1 - e) * (1 - e) * flySX + 2 * (1 - e) * e * mx + e * e * tx;
        float y = (1 - e) * (1 - e) * flySY + 2 * (1 - e) * e * my + e * e * ty;
        float size = sw * 0.12f * (1 - 0.55f * e) * (1 + 0.25f * (float) Math.sin(t * Math.PI));
        bmp.setColorFilter(gemTint);
        bmp.setAlpha(170);
        tmpR.set(x - size * 1.1f, y - size * 1.1f, x + size * 1.1f, y + size * 1.1f);
        c.drawBitmap(glow, null, tmpR, bmp);
        bmp.setColorFilter(null);
        bmp.setAlpha(255);
        float hh = b.getHeight() * size / 2 / b.getWidth();
        c.save();
        c.rotate(360 * e, x, y);
        tmpR.set(x - size / 2, y - hh, x + size / 2, y + hh);
        c.drawBitmap(b, null, tmpR, bmp);
        c.restore();
        for (int i = 0; i < 4; i++) {
            float lt = clamp(t - i * 0.06f, 0, 1);
            float le = lt * lt * (3 - 2 * lt);
            float sx = (1 - le) * (1 - le) * flySX + 2 * (1 - le) * le * mx + le * le * tx;
            float sy = (1 - le) * (1 - le) * flySY + 2 * (1 - le) * le * my + le * le * ty;
            star(c, sx, sy, size * 0.18f * (1 - i * 0.2f), Color.argb(200 - i * 40, 220, 245, 255));
        }
    }

    // ---- HUD ----
    private void drawHud(Canvas c) {
        drawCircleSprite(c, "hud/btn_pause.png", pauseX, pauseY, pauseR * (1 - 0.08f * pauseFlash), 255);
        drawPill(c, coinPill, "hud/icon_coin.png", String.valueOf(coins), coinBump);
        drawPill(c, gemPill, "rooms/diamond.png", String.valueOf(gems), gemBump);
        Bitmap p = host.bitmap("hud/panel.png");
        if (p != null) {
            bmp.setAlpha(235);
            c.drawBitmap(p, null, panel, bmp);
            bmp.setAlpha(255);
            String obj = !heistDone && room.objectiveHeist != null ? room.objectiveHeist : room.objective;
            float size = panel.height() * 0.36f;
            float x = panel.left + panel.width() * 0.2f, maxW = panel.width() * 0.56f;
            text.setTextSize(size);
            float w = text.measureText(obj);
            if (w > maxW) size *= maxW / w;
            float y = panel.centerY() + 0.36f * size;
            outlined(c, obj, x, y, size, Color.WHITE, Paint.Align.LEFT, 1);
            outlined(c, roomPicked + "/" + roomTotal, panel.right - panel.width() * 0.05f, panel.centerY() + 0.36f * panel.height() * 0.36f,
                    panel.height() * 0.36f, roomPicked == roomTotal && roomTotal > 0 ? Color.rgb(255, 225, 90) : Color.WHITE, Paint.Align.RIGHT, 1);
        }
        drawCircleSprite(c, "hud/btn_run.png", runX, runY, runR * (sprint ? 0.92f : 1f), 255);
        boolean glowHand = (interactNear || (!heistDone && heistT < 0 && nearHeist())) && phase == PLAY;
        if (glowHand) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 6);
            bmp.setColorFilter(goldTint);
            bmp.setAlpha(Math.round(pulse * 160));
            float r = handR * 1.45f;
            tmpR.set(handX - r, handY - r, handX + r, handY + r);
            c.drawBitmap(glow, null, tmpR, bmp);
            bmp.setColorFilter(null);
            bmp.setAlpha(255);
        }
        drawCircleSprite(c, "hud/btn_hand.png", handX, handY, handR * (1 - 0.1f * handFlash), 255);
    }

    private void drawCircleSprite(Canvas c, String s, float x, float y, float r, int a) {
        Bitmap b = host.bitmap(s);
        if (b == null) return;
        float rr = b.getWidth() / (float) (b.getWidth() - 8) * r;
        tmpR.set(x - rr, y - rr, x + rr, y + rr);
        bmp.setAlpha(a);
        c.drawBitmap(b, null, tmpR, bmp);
        bmp.setAlpha(255);
    }

    private void drawPill(Canvas c, RectF r, String icon, String value, float bump) {
        float rad = r.height() / 2;
        fill.setShader(pillShader);
        fill.setColor(0xFFFFFFFF);
        c.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(r.height() * 0.05f);
        stroke.setColor(Color.argb(200, 90, 150, 255));
        c.drawRoundRect(r, rad, rad, stroke);
        Bitmap b = host.bitmap(icon);
        float iw = r.height() * 1.02f, ix = r.left + r.height() * 0.08f;
        if (b != null) {
            float ih = b.getHeight() * iw / b.getWidth();
            tmpR.set(ix, r.centerY() - ih / 2, ix + iw, r.centerY() + ih / 2);
            c.drawBitmap(b, null, tmpR, bmp);
        }
        float size = r.height() * 0.56f * (1 + 0.25f * bump);
        outlined(c, value, (ix + iw + r.right - rad * 0.6f) / 2, r.centerY() + 0.36f * size, size, Color.WHITE, Paint.Align.CENTER, 1);
    }

    private void outlined(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, float alpha) {
        stroke.setTextAlign(align);
        stroke.setTextSize(size);
        stroke.setStrokeWidth(0.16f * size);
        stroke.setColor(Color.argb(Math.round(230 * alpha), 10, 8, 30));
        c.drawText(s, x, y, stroke);
        text.setTextAlign(align);
        text.setTextSize(size);
        text.setColor(color);
        c.drawText(s, x, y, text);
    }

    // ---- overlays: iris, banner, caught ----
    private void drawOverlays(Canvas c) {
        float iris = 1;   // 1 = fully open
        float cx = screenX(boy.x), cy = screenY(boy.y - boy.height(room) * 0.5f);
        if (phase == INTRO) iris = clamp(phaseT / 0.75f, 0, 1);
        else if (phase == EXIT) iris = 1 - clamp((phaseT - 0.3f) / 0.7f, 0, 1);
        else if (phase == CAUGHT) iris = 1 - clamp((phaseT - 1.0f) / 0.55f, 0, 1);
        else if (phase == DONE) iris = 0;
        if (iris < 1) {
            float maxR = (float) Math.hypot(Math.max(cx, sw - cx), Math.max(cy, sh - cy)) * 1.05f;
            float e = iris * iris * (3 - 2 * iris);
            float r = maxR * e;
            path.reset();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.addRect(0, 0, sw, sh, Path.Direction.CW);
            if (r > 0.5f) path.addCircle(cx, cy, r, Path.Direction.CW);
            fill.setShader(null);
            fill.setColor(Color.BLACK);
            c.drawPath(path, fill);
            path.setFillType(Path.FillType.WINDING);
            if (r > 2) {
                stroke.setStrokeWidth(sw * 0.012f);
                stroke.setColor(Color.argb(160, 255, 210, 90));
                c.drawCircle(cx, cy, r, stroke);
            }
        }
        if (bannerT >= 0) drawBanner(c);
        if (hintT >= 0) drawControlsHint(c);
        if (phase == CAUGHT) {
            float t = clamp(phaseT / 0.25f, 0, 1);
            float sc = t < 1 ? 0.5f + 0.7f * t : 1.2f - 0.2f * clamp((phaseT - 0.25f) / 0.2f, 0, 1);
            float size = sw * 0.16f * sc;
            float a = clamp((1.5f - phaseT) / 0.3f, 0, 1);
            fitted(c, "CAUGHT!", sw / 2f, sh * 0.42f, size, sw * 0.9f, Math.round(255 * a), Color.rgb(255, 120, 90), Color.rgb(220, 30, 30));
        }
    }

    private void drawControlsHint(Canvas c) {
        float a = clamp(hintT / 0.4f, 0, 1) * clamp((7 - hintT) / 0.4f, 0, 1);
        if (a <= 0) return;
        float cx = (runX - runR * 1.3f) / 2f;
        float y = runY + runR * 0.2f;
        float size = sw * 0.042f;
        String l1 = "HOLD ANYWHERE TO WALK", l2 = "TAP A SPOT TO GO THERE";
        text.setTextSize(size);
        float w = Math.max(text.measureText(l1), text.measureText(l2)) + size * 1.6f;
        float h = size * 3.1f;
        tmpR.set(cx - w / 2, y - h / 2, cx + w / 2, y + h / 2);
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(200 * a), 16, 12, 40));
        c.drawRoundRect(tmpR, h * 0.3f, h * 0.3f, fill);
        stroke.setStrokeWidth(size * 0.12f);
        stroke.setColor(Color.argb(Math.round(200 * a), 255, 200, 80));
        c.drawRoundRect(tmpR, h * 0.3f, h * 0.3f, stroke);
        outlined(c, l1, cx, y - size * 0.25f, size, Color.argb(Math.round(255 * a), 255, 255, 255), Paint.Align.CENTER, a);
        outlined(c, l2, cx, y + size * 1.05f, size * 0.85f, Color.argb(Math.round(255 * a), 255, 225, 110), Paint.Align.CENTER, a);
    }

    private void drawBanner(Canvas c) {
        float t = bannerT;
        float in = clamp((t - 0.35f) / 0.35f, 0, 1), out = clamp((3.2f - t) / 0.5f, 0, 1);
        float a = Math.min(in, out);
        if (a <= 0) return;
        float e = in * in * (3 - 2 * in);
        float w = Math.min(sw * 0.78f, 700 * hs * 1.3f), h = w * 0.22f;
        float x = sw / 2f, y = panel.bottom + h * 0.2f + (e - 1) * h * 0.6f;
        tmpR.set(x - w / 2, y, x + w / 2, y + h);
        fill.setShader(new LinearGradient(0, tmpR.top, 0, tmpR.bottom, Color.argb(Math.round(230 * a), 40, 30, 90), Color.argb(Math.round(230 * a), 16, 12, 40), Shader.TileMode.CLAMP));
        fill.setColor(0xFFFFFFFF);
        c.drawRoundRect(tmpR, h / 2, h / 2, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(h * 0.05f);
        stroke.setColor(Color.argb(Math.round(220 * a), 255, 200, 80));
        c.drawRoundRect(tmpR, h / 2, h / 2, stroke);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(h * 0.26f);
        text.setColor(Color.argb(Math.round(255 * a), 150, 200, 255));
        c.drawText(bannerTop, x, y + h * 0.36f, text);
        fitted(c, bannerMain, x, y + h * 0.82f, h * 0.44f, w * 0.86f, Math.round(255 * a), Color.rgb(255, 240, 140), Color.rgb(255, 165, 20));
    }

    private void fitted(Canvas c, String s, float x, float y, float size, float maxW, int a, int top, int bottom) {
        text.setTextSize(size);
        float w = text.measureText(s);
        if (w > maxW) size *= maxW / w;
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setTextAlign(Paint.Align.CENTER);
        stroke.setTextSize(size);
        stroke.setStrokeWidth(0.14f * size);
        stroke.setColor(Color.argb(a, 60, 20, 0));
        c.drawText(s, x, y, stroke);
        text.setShader(new LinearGradient(0, y - size, 0, y, Color.argb(a, Color.red(top), Color.green(top), Color.blue(top)),
                Color.argb(a, Color.red(bottom), Color.green(bottom), Color.blue(bottom)), Shader.TileMode.CLAMP));
        text.setColor(Color.argb(a, 255, 255, 255));
        c.drawText(s, x, y, text);
        text.setShader(null);
    }

    String debugState() {
        return String.format(Locale.US, "room=%d phase=%d boy=%.0f,%.0f cam=%.0f,%.0f scale=%.3f", roomIndex, phase, boy.x, boy.y, camX, camY, camScale);
    }
}
