package com.treasurerun.game;

import java.util.List;

/**
 * Museum guard: patrols a route, looks around at stops, sees with a flashlight cone (line of sight on the walk mask),
 * grows suspicious ("?"), double-takes ("!"), chases with A*, searches the last spot he saw the boy, then returns.
 */
final class Guard extends Walker {
    static final int PATROL = 0, SUSPICIOUS = 1, ALERT = 2, CHASE = 3, SEARCH = 4, RETURN = 5, HOLD = 6;

    final Level lv;
    final Level.Wp[] route;
    int state = PATROL, wp;          // next waypoint
    float stateT, waitT, lookBase;
    float awareness;                 // 0..1 how sure he is he saw someone
    float lastX, lastY;              // last known boy position
    float loseT, pathT, bubbleT;
    boolean seesBoy;
    List<float[]> path; int pathI;
    float stepT;                     // footstep sound timer
    // flashlight cone (world points, rebuilt every frame)
    final float[] cone = new float[2 * 27];
    int coneN;

    Guard(Game g, Level lv, Level.Wp[] route) {
        super(g, Rig.GUARD, 1.22f);
        this.lv = lv; this.route = route;
        x = route[0].x; y = route[0].y;
        wp = route.length > 1 ? 1 : 0;
        facing = route.length > 1 ? g.nav.floorAngle(x, y, route[1].x, route[1].y) : 90f;
        if (route[0].pause > 0) { waitT = route[0].pause; lookBase = facing; }
    }

    float boyH() { return g.room.boyH; }
    float sight() { return lv.sight * boyH() * (state == SEARCH ? 1.1f : 1f); }

    float routeDist() { return routeDist(x, y); }

    /** Floor distance from a point to the nearest point of this guard's patrol route. */
    float routeDist(float x, float y) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < route.length; i++) {
            Level.Wp a = route[i], b = route[(i + 1) % route.length];
            float dx = b.x - a.x, dy = b.y - a.y, l2 = dx * dx + dy * dy;
            float t = l2 < 1e-3f ? 0 : Math.max(0, Math.min(1, ((x - a.x) * dx + (y - a.y) * dy) / l2));
            best = Math.min(best, g.nav.floorDist(x, y, a.x + dx * t, a.y + dy * t));
        }
        return best;
    }
    float fov() { return lv.fov * (state == SUSPICIOUS || state == SEARCH ? 1.2f : 1f); }

    @Override
    Rig.View pickView(float heading, boolean[] m) {
        float a = wrap(heading + 90f);                  // 0 = away from camera, +-180 = toward it
        float aa = Math.abs(a);
        if (aa <= 97f) { m[0] = a < 0; return Rig.GUARD_TQB; }
        if (aa <= 152f) { m[0] = a > 0; return Rig.GUARD_TQF; }
        m[0] = false; return Rig.GUARD_FRONT;
    }

    void update(float dt, Boy boy, boolean active) {
        stateT += dt; bubbleT += dt;
        moved = 0; run = 0;
        float walk = lv.patrolSpeed * boyH();
        // --- senses ---
        seesBoy = false;
        if (active && boy.visible()) {
            float d = g.nav.floorDist(x, y, boy.x, boy.y);
            float ang = g.nav.floorAngle(x, y, boy.x, boy.y);
            boolean inCone = d < sight() && Math.abs(wrap(ang - facing)) < fov();
            boolean close = d < 0.3f * boyH();                 // bumping into him: he feels it even from behind
            if ((inCone || close) && g.nav.los(x, y, boy.x, boy.y)) {
                seesBoy = true; lastX = boy.x; lastY = boy.y;
                float near = 1f - Math.min(1f, d / sight());
                float rate = (0.55f + 1.6f * near * near) / lv.detect;
                if (boy.running()) rate *= 1.5f;
                if (boy.speed < 1f) rate *= 0.75f;
                if (close) rate = Math.max(rate, 4f);
                awareness = Math.min(1f, awareness + rate * dt);
            } else if (boy.running() && d < 1.35f * boyH() && state <= SUSPICIOUS) {
                // heard footsteps: turn toward the noise
                awareness = Math.max(awareness, 0.35f);
                lastX = boy.x; lastY = boy.y;
            }
        }
        if (!seesBoy && state <= SUSPICIOUS) awareness = Math.max(0f, awareness - dt * (state == SUSPICIOUS ? 0.22f : 0.4f));
        // --- decisions ---
        switch (state) {
            case PATROL:
                if (awareness > 0.12f) { set(SUSPICIOUS); g.sfx(Game.SND_HMM); break; }
                patrol(dt, walk);
                break;
            case SUSPICIOUS: {
                turnToward(g.nav.floorAngle(x, y, lastX, lastY), 240f, dt);
                speed = Math.max(0f, speed - dt * walk * 4f);
                if (awareness >= 1f) { alert(); break; }
                if (awareness <= 0f && stateT > 1.2f) { set(SEARCH); path = null; }
                break;
            }
            case ALERT:
                speed = 0;
                if (stateT < 0.12f) hop = 0.07f;
                if (stateT >= lv.react) { forceFront = false; set(CHASE); pathT = 0; }
                break;
            case CHASE: {
                run = 1;
                if (seesBoy) loseT = 0; else loseT += dt;
                if (loseT > lv.lose) { set(SEARCH); path = null; g.onLost(this); break; }
                if (routeDist() > lv.leash * boyH()) {       // too far from his post: give up here
                    lastX = x; lastY = y; set(SEARCH); path = null; g.onLost(this); break;
                }
                float tx = seesBoy ? boy.x : lastX, ty = seesBoy ? boy.y : lastY;
                go(tx, ty, lv.chaseSpeed * boyH(), dt, 720f, true);
                if (g.nav.floorDist(x, y, boy.x, boy.y) < 0.36f * boyH() && boy.visible()) { set(HOLD); g.caught(this); }
                break;
            }
            case SEARCH: {
                boolean inZone = g.boy != null && routeDist(boy.x, boy.y) < lv.leash * boyH();
                if (seesBoy && awareness > 0.6f && inZone) { alert(); break; }
                if (seesBoy) awareness = Math.min(1f, awareness + dt * 2f);
                float d = g.nav.floorDist(x, y, lastX, lastY);
                if (d > 0.25f * boyH() && stateT < 6f && waitT <= 0) {
                    go(lastX, lastY, walk * 1.35f, dt, 420f, false);
                } else {
                    if (waitT <= 0) { waitT = lv.search; lookBase = facing; }
                    speed = Math.max(0f, speed - dt * walk * 4f);
                    waitT -= dt;
                    facing = wrap(lookBase + 70f * (float) Math.sin((lv.search - waitT) * 2.1f));
                    if (waitT <= 0) { set(RETURN); path = null; awareness = 0; }
                }
                break;
            }
            case RETURN: {
                if (awareness > 0.12f && routeDist(boy.x, boy.y) < lv.leash * boyH()) { set(SUSPICIOUS); break; }
                Level.Wp t = route[wp];
                if (g.nav.floorDist(x, y, t.x, t.y) < 0.2f * boyH()) { set(PATROL); break; }
                go(t.x, t.y, walk, dt, 360f, false);
                break;
            }
            case HOLD:
                speed = 0;
                turnToward(g.nav.floorAngle(x, y, boy.x, boy.y), 400f, dt);
                break;
        }
        animate(dt, walk);
        // footsteps (quiet, only while running/chasing on screen)
        if (run > 0 && moved > 0) { stepT -= dt; if (stepT <= 0) { stepT = 0.28f; g.sfx(Game.SND_STEP); } }
        buildCone();
    }

    private void alert() {
        set(ALERT); forceFront = true; awareness = 1f; bubbleT = 0; g.sfx(Game.SND_ALERT); g.onAlert(this);
    }

    private void set(int s) { state = s; stateT = 0; waitT = 0; loseT = 0; if (s != SEARCH) bubbleT = 0; }

    private void patrol(float dt, float walk) {
        if (waitT > 0) {                                  // stopped at a waypoint: look around
            waitT -= dt;
            speed = Math.max(0f, speed - dt * walk * 4f);
            Level.Wp cur = route[(wp - 1 + route.length) % route.length];
            float t = cur.pause - waitT;
            facing = wrap(lookBase + cur.look * (float) Math.sin(t * 2.2f / Math.max(0.6f, cur.pause) * Math.PI * 0.5f));
            if (waitT <= 0 && route.length > 1) path = null;
            return;
        }
        if (route.length < 2) { speed = 0; return; }
        Level.Wp t = route[wp];
        if (g.nav.floorDist(x, y, t.x, t.y) < 0.12f * boyH()) {
            if (t.pause > 0) { waitT = t.pause; lookBase = facing; }
            wp = (wp + 1) % route.length;
            path = null;
            return;
        }
        go(t.x, t.y, walk, dt, 300f, false);
    }

    /** Walks toward a target, directly when clear, otherwise along an A* path. */
    private void go(float tx, float ty, float spd, float dt, float turnRate, boolean hurry) {
        Nav nav = g.nav;
        float gx = tx, gy = ty;
        if (nav.clear(x, y, tx, ty)) { path = null; }
        else {
            pathT -= dt;
            if (path == null || pathT <= 0) { path = nav.path(x, y, tx, ty); pathI = 0; pathT = hurry ? 0.35f : 2f; }
            if (path != null) {
                while (pathI < path.size() - 1 && nav.floorDist(x, y, path.get(pathI)[0], path.get(pathI)[1]) < 0.15f * boyH()) pathI++;
                if (pathI < path.size()) { gx = path.get(pathI)[0]; gy = path.get(pathI)[1]; }
            }
        }
        float ang = nav.floorAngle(x, y, gx, gy);
        turnToward(ang, turnRate, dt);
        float diff = Math.abs(wrap(ang - facing));
        float target = spd * Math.max(0f, (float) Math.cos(Math.toRadians(Math.min(89f, diff))));
        speed += (target - speed) * Math.min(1f, dt * 5f);
        float dist = Math.min(speed * dt, nav.floorDist(x, y, gx, gy) + 0.01f);
        float m = moveFloor(facing, dist, g.room.feetR * 0.9f);
        if (m < dist * 0.2f && dist > 0) { m = moveFloor(ang, dist, g.room.feetR * 0.9f); if (m == 0) pathT = 0; }
        moved = m;
    }

    /** Flashlight cone clipped by obstacles. */
    private void buildCone() {
        int n = 10;
        float f = fov(), range = sight();
        cone[0] = x; cone[1] = y;
        float[] out = RAY;
        for (int i = 0; i <= 2 * n; i++) {
            float a = facing - f + f * i / n;
            g.nav.ray(x, y, a, range, out);
            cone[2 + 2 * i] = out[0]; cone[3 + 2 * i] = out[1];
        }
        coneN = 2 * n + 2;
    }
    private static final float[] RAY = new float[2];

    boolean chasing() { return state == ALERT || state == CHASE; }
}
