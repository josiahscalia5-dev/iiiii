package com.treasurerun.game;

/**
 * Shared locomotion for the boy and the guards: floor-space movement with sliding collision, smooth turning,
 * speed easing, gait phase driven by distance (so feet don't slide) and body-view selection with a squash turn.
 */
abstract class Walker {
    final Game g;
    final Rig.Style style;
    final float heightMul;           // character height relative to the boy
    float x, y;                      // feet, world px
    float facing = -90f;             // floor heading, deg (0 = right, 90 = toward camera)
    float speed;                     // current floor speed (floor units / s)
    float amt, phase, run, breathe;  // animation state
    float lean, hop, reach, squashT = 1f;
    Rig.View view, prevView; boolean mirror, prevMirror;
    boolean forceFront;              // guards: face the camera for the "!" double-take
    boolean ghost;                   // scripted walks (entering the room) ignore collision
    final Rig.Pose pose = new Rig.Pose();
    float moved;                     // floor distance moved this frame

    Walker(Game g, Rig.Style style, float heightMul) { this.g = g; this.style = style; this.heightMul = heightMul; }

    float H() { return g.room.boyH * heightMul; }   // floor units

    static float wrap(float a) { a %= 360f; if (a > 180f) a -= 360f; if (a <= -180f) a += 360f; return a; }

    /** Rotates facing toward target at rate deg/s. When exactly opposite, turns via the camera side (reads better). */
    void turnToward(float target, float rate, float dt) {
        float d = wrap(target - facing);
        if (Math.abs(d) > 170f) d = (wrap(facing - 90f) < 0 ? 1 : -1) * Math.abs(d);   // turn through "toward camera"
        float step = rate * dt;
        facing = wrap(Math.abs(d) <= step ? target : facing + Math.signum(d) * step);
    }

    /** Moves along a floor heading by dist floor units with wall sliding. Returns the floor distance actually moved. */
    float moveFloor(float angDeg, float dist, float feetR) {
        if (dist <= 0) return 0;
        Nav nav = g.nav;
        double a = Math.toRadians(angDeg);
        float dx = (float) Math.cos(a) * dist * nav.sx(y), dy = (float) Math.sin(a) * dist * nav.sy(y);
        float nx = x + dx, ny = y + dy;
        float ox = x, oy = y;
        if (ghost) { x = nx; y = ny; return nav.floorDist(ox, oy, x, y); }
        if (nav.canStand(nx, ny, feetR)) { x = nx; y = ny; }
        else if (Math.abs(dx) > 1e-3f && nav.canStand(nx, y, feetR)) { x = nx; }
        else if (Math.abs(dy) > 1e-3f && nav.canStand(x, ny, feetR)) { y = ny; }
        else return 0;
        return nav.floorDist(ox, oy, x, y);
    }

    /** Picks the body view (+ mirror) for a heading. */
    abstract Rig.View pickView(float heading, boolean[] mirrorOut);

    /** Advances gait/turn animation. walkRef = floor speed that counts as a full stride. */
    void animate(float dt, float walkRef) {
        float target = walkRef > 0 ? Math.min(1f, speed / (walkRef * 0.55f)) : 0f;
        amt += (target - amt) * Math.min(1f, dt * (target > amt ? 7f : 9f));
        if (amt < 0.02f && target == 0) amt = 0;
        float H = H();
        float cyc = 2f * style.stride * (1f + 0.35f * run) * Math.max(0.4f, amt) * H / 0.6f;
        phase += moved / Math.max(1f, cyc);
        if (amt == 0) {                               // settle feet together when standing
            float p = phase - (float) Math.floor(phase);
            float goal = p < 0.25f ? 0f : p < 0.75f ? 0.5f : 1f;
            phase += (goal - p) * Math.min(1f, dt * 6f);
        }
        phase -= (float) Math.floor(phase);
        breathe += dt * 2.4f;
        boolean[] mo = MIRROR;
        Rig.View v = forceFront && this instanceof Guard ? Rig.GUARD_FRONT : pickView(facing, mo);
        boolean m = forceFront ? false : mo[0];
        if (view == null) { view = v; mirror = m; }
        else if (v != view || m != mirror) {
            if (squashT >= 0.5f || prevView == null) { prevView = view; prevMirror = mirror; }
            view = v; mirror = m; squashT = 0f;
        }
        squashT = Math.min(1f, squashT + dt / 0.2f);
        hop = Math.max(0f, hop - dt * 0.9f);
    }
    private static final boolean[] MIRROR = new boolean[1];

    static float canon(Rig.View v, boolean mirror) { return mirror ? wrap(180f - v.canon) : v.canon; }

    /** Fills the rig pose for rendering. */
    Rig.Pose pose() {
        Nav nav = g.nav;
        Rig.Pose p = pose;
        if (squashT < 1f && prevView == view) {
            // same painting, just mirrored: a quick flip reads as turning around
            boolean first = squashT < 0.5f;
            p.view = view; p.mirror = first ? prevMirror : mirror; p.prev = null; p.mix = 1f;
            p.squash = Math.max(0.12f, Math.abs((float) Math.cos(squashT * Math.PI)));
        } else {
            // different painting (back / three-quarter / front): cross-fade with a slight squash, like a body rotating
            p.view = view; p.mirror = mirror;
            p.prev = squashT < 1f ? prevView : null; p.prevMirror = prevMirror; p.mix = squashT;
            p.squash = 1f - 0.3f * (float) Math.sin(squashT * Math.PI);
        }
        float c = canon(p.view, p.mirror);
        float lim = p.view == Rig.BOY_FRONT ? 38f : 34f;
        p.facing = wrap(c + Math.max(-lim, Math.min(lim, wrap(facing - c))));
        p.x = x; p.y = y;
        p.scale = H() * nav.sx(y);
        // the painted characters are seen from a fixed ~3/4 angle; clamp the room's floor foreshortening to match it
        p.fore = Math.max(0.3f, Math.min(0.55f, nav.sy(y) / nav.sx(y)));
        p.phase = phase; p.amt = amt; p.run = run; p.lean = lean; p.breathe = breathe;
        p.reach = reach; p.hop = hop;
        return p;
    }
}
