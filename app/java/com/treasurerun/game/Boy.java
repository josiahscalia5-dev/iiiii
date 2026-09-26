package com.treasurerun.game;

import java.util.List;

/** The player: steered by swipes (Game sets the intent), walks/sneaks/sprints with the rig, reaches to grab treasure. */
final class Boy extends Walker {
    // intents (set by Game from touch input)
    boolean steering; float steerAng, steerMag;
    float flickAng, flickLeft;           // floor units still to walk after a quick swipe
    List<float[]> path; int pathI;       // tap-to-walk
    float stuckT, progBest = Float.MAX_VALUE;   // path-following watchdog
    boolean replanned;
    boolean sprint;
    float lockT;                         // can't move (grabbing / caught)
    float reachT = -1f, reachDur = 0.45f;
    float alpha = 1f;
    boolean escaped;

    Boy(Game g) { super(g, Rig.BOY, 1f); }

    @Override
    Rig.View pickView(float heading, boolean[] m) {
        float a = wrap(heading + 90f);
        float aa = Math.abs(a);
        // hysteresis against flicker at the thresholds
        if (view == Rig.BOY_BACK ? aa <= 34f : aa <= 26f) { m[0] = false; return Rig.BOY_BACK; }
        if (view == Rig.BOY_TQ ? aa <= 82f : aa <= 74f) { m[0] = a < 0; return Rig.BOY_TQ; }
        if (aa > 172f && view == Rig.BOY_FRONT) { m[0] = mirror; return Rig.BOY_FRONT; }  // straight down: keep side
        m[0] = a < 0;
        return Rig.BOY_FRONT;
    }

    boolean visible() { return !escaped && alpha > 0.3f; }
    boolean running() { return sprint && speed > g.room.speed * 1.2f; }
    boolean idle() { return !steering && flickLeft <= 0 && path == null; }

    void stop() { steering = false; flickLeft = 0; path = null; }

    void follow(List<float[]> p) { path = p; pathI = 0; stuckT = 0; progBest = Float.MAX_VALUE; replanned = false; flickLeft = 0; }

    /** Reach animation only; coins and gems are grabbed on the move (the case diamond locks separately). */
    void grab(float tx, float ty) { reachT = 0; reachDur = 0.45f; }

    void update(float dt) {
        Nav nav = g.nav;
        float walk = g.room.speed;
        float want = 0, ang = facing;
        if (lockT > 0) { lockT -= dt; }
        else if (steering && steerMag > 0) { want = steerMag; ang = steerAng; }
        else if (flickLeft > 0) { want = 1f; ang = flickAng; }
        else if (path != null) {
            // advance past reached waypoints, but only skip ahead when the next leg is clear from where he really is
            while (pathI < path.size()) {
                float[] p = path.get(pathI);
                float d = nav.floorDist(x, y, p[0], p[1]);
                boolean last = pathI == path.size() - 1;
                if (d < (last ? 0.06f : 0.09f) * H()) {
                    if (last) { pathI++; break; }
                    float[] n = path.get(pathI + 1);
                    if (d < 0.025f * H() || nav.clear(x, y, n[0], n[1])) { pathI++; progBest = Float.MAX_VALUE; continue; }
                }
                break;
            }
            if (pathI >= path.size()) { path = null; g.onArrive(); }
            else {
                float[] p = path.get(pathI);
                ang = nav.floorAngle(x, y, p[0], p[1]);
                boolean last = pathI == path.size() - 1;
                float d = nav.floorDist(x, y, p[0], p[1]);
                want = last || d < 0.2f * H() ? Math.max(0.3f, Math.min(1f, d / (0.5f * H()))) : 1f;
                // progress watchdog: replan once if we stop getting closer, then give up
                if (d < progBest - 0.02f * H()) { progBest = d; stuckT = 0; } else stuckT += dt;
                if (stuckT > 0.7f) {
                    float[] goal = path.get(path.size() - 1);
                    List<float[]> p2 = replanned ? null : nav.path(x, y, goal[0], goal[1]);
                    if (p2 == null || p2.isEmpty()) { path = null; want = 0; } else { path = p2; pathI = 0; replanned = true; }
                    stuckT = 0; progBest = Float.MAX_VALUE;
                }
            }
        }
        run = sprint && want > 0.5f ? Math.min(1f, run + dt * 4f) : Math.max(0f, run - dt * 3f);
        float target = want * walk * (1f + 0.65f * run);
        if (want > 0) turnToward(ang, 760f + 300f * run, dt);
        float diff = Math.abs(wrap(ang - facing));
        if (want > 0) target *= Math.max(0.12f, (float) Math.cos(Math.toRadians(Math.min(90f, diff))));
        speed += (target - speed) * Math.min(1f, dt * (target > speed ? 5.5f : 8f));
        if (speed < 2f && target == 0) speed = 0;
        float step = speed * dt;
        float m = moveFloor(want > 0 && diff < 60f ? facing : (want > 0 ? ang : facing), step, g.room.feetR);
        if (m < step * 0.35f && step > 0) {
            // blocked: try sliding along the wanted direction; otherwise drop to the speed actually achieved so the
            // legs don't keep walking on the spot
            float m2 = moveFloor(ang, step, g.room.feetR);
            m = Math.max(m, m2);
            if (m < step * 0.35f) speed = Math.min(speed, m / Math.max(dt, 1e-3f));
        }
        moved = m;
        if (flickLeft > 0) { flickLeft -= Math.max(m, step * 0.5f); if (flickLeft <= 0 || m == 0) flickLeft = 0; }
        // body language
        float spdN = speed / walk;
        float targetLean = spdN * (3.5f + 3f * run) * (float) Math.cos(Math.toRadians(facing));
        lean += (targetLean - lean) * Math.min(1f, dt * 6f);
        if (reachT >= 0) {
            reachT += dt;
            float t = reachT / reachDur;
            reach = t >= 1 ? 0 : (float) Math.sin(Math.PI * Math.min(1f, t));
            if (t >= 1) reachT = -1f;
        }
        animate(dt, walk);
    }
}
