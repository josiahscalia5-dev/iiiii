package com.treasurerun.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * A live museum guard (painted art, re-used): patrols a route, gets suspicious ("?") when the boy is in his
 * flashlight cone, raises the alarm ("!", shocked front face), chases along A* paths, loses the boy behind
 * obstacles, searches the last-seen spot and walks back to his route. Vision lives in floor space and is
 * blocked by anything the boy can't walk on.
 */
final class Guard {
    static final int PATROL = 0, SUSPICIOUS = 1, ALERT = 2, CHASE = 3, SEARCH = 4, RETURN = 5;

    // sprite metrics (px): feet anchor, flashlight lens, height used for scaling
    static final String SIDE = "rooms/guard_side.png", FRONT = "rooms/guard_front.png";
    static final float SIDE_AX = 100, SIDE_AY = 304, SIDE_H = 300, SIDE_LX = 186, SIDE_LY = 163;
    static final float FRONT_AX = 92, FRONT_AY = 330, FRONT_H = 325, FRONT_LX = 14, FRONT_LY = 158;

    final Room.GuardDef def;
    final Room room;
    float x, y;
    /** floor-space facing (radians, 0 = up/away, +pi/2 = right) */
    float heading;
    int state = PATROL;
    float stateT;
    /** suspicion 0..1 */
    float sus;
    int wp;          // current route waypoint
    int dir = 1;     // ping-pong direction
    float waitT;     // pause at a waypoint
    float lookBase;  // heading when a look-around started
    float lastX, lastY, lostT;
    float stepPhase, moveAmt;
    float bubbleT;   // for the ?/! pop-in
    boolean seesBoy;
    // path following
    final float[] path = new float[256];
    int pathN, pathI;
    float replanT;
    // vision fan (world points) for drawing
    final float[] fan = new float[2 * 34];
    int fanN;

    // tuning (floor px are depth-1 world px of this room)
    final float walkSpeed, chaseSpeed, range, halfAngle, catchR, hearR, noticeR, feetR;

    Guard(Room room, Room.GuardDef def) {
        this.room = room;
        this.def = def;
        walkSpeed = room.speed * 0.42f;
        chaseSpeed = room.speed * 1.08f;
        range = room.boyH * room.visionRange;
        halfAngle = (float) Math.toRadians(30);
        catchR = room.boyH * 0.3f;
        hearR = room.boyH * 0.75f;
        noticeR = room.boyH * 0.55f;
        feetR = room.feetR * 1.1f;
        reset();
    }

    void reset() {
        x = def.route[0][0];
        y = def.route[0][1];
        wp = def.route.length > 1 ? 1 : 0;
        dir = 1;
        heading = headingTo(def.route[wp][0], def.route[wp][1]);
        if (def.route.length == 1) heading = def.pause.length > 1 ? def.pause[1] : 0;
        state = PATROL;
        stateT = 0;
        sus = 0;
        waitT = 0;
        pathN = pathI = 0;
        seesBoy = false;
    }

    float headingTo(float tx, float ty) {
        float d = Math.max(0.1f, room.depth(y));
        return (float) Math.atan2((tx - x) / d, -(ty - y) / (room.vFactor * (float) Math.pow(d, room.vPow)));
    }

    /** @return true when the boy is caught this frame */
    boolean update(float dt, Nav nav, Boy boy, boolean boySprinting, boolean boyActive) {
        stateT += dt;
        bubbleT += dt;
        float dist = nav.floorDist(x, y, boy.x, boy.y);
        // --- perception ---
        seesBoy = false;
        if (boyActive && dist < range * (state == CHASE ? 1.6f : 1f)) {
            float to = headingTo(boy.x, boy.y);
            float off = Math.abs(Boy.wrap(to - heading));
            boolean inCone = off < (state == CHASE || state == SEARCH ? halfAngle * 1.8f : halfAngle) || dist < catchR * 1.3f;
            if (inCone && nav.lineOfSight(x, y - 4, boy.x, boy.y - 4)) seesBoy = true;
        }
        // right next to him he senses the boy even outside the cone; a sprinting boy is heard further away
        boolean hears = boyActive && (dist < noticeR || (boySprinting && dist < hearR)) && nav.lineOfSight(x, y, boy.x, boy.y);
        if (seesBoy) {
            float rate = 1.15f / (0.3f + dist / range) * (boySprinting ? 1.5f : 1f);
            sus = Math.min(1, sus + rate * dt);
            lastX = boy.x;
            lastY = boy.y;
            lostT = 0;
        } else if (hears) {
            sus = Math.min(1, sus + (dist < noticeR ? 1.6f : 0.9f) * dt);
            lastX = boy.x;
            lastY = boy.y;
        } else if (state == PATROL || state == SUSPICIOUS) {
            sus = Math.max(0, sus - 0.3f * dt);
        }

        switch (state) {
            case PATROL:
                patrol(dt, nav);
                if (sus > 0.12f) setState(SUSPICIOUS);
                break;
            case SUSPICIOUS:
                moveAmt = approach(moveAmt, 0, dt * 5);
                turnTo(headingTo(lastX, lastY), dt, 4);
                if (sus >= 1) setState(ALERT);
                else if (sus <= 0 && stateT > 0.8f) { setState(PATROL); }
                break;
            case ALERT:
                moveAmt = approach(moveAmt, 0, dt * 8);
                turnTo(headingTo(lastX, lastY), dt, 8);
                if (stateT > 0.55f) { setState(CHASE); replanT = 0; }
                break;
            case CHASE:
                if (seesBoy) lostT = 0; else lostT += dt;
                replanT -= dt;
                if (replanT <= 0) { plan(nav, lastX, lastY); replanT = 0.35f; }
                followPath(dt, nav, chaseSpeed);
                if (lostT > 1.6f) setState(SEARCH);
                break;
            case SEARCH:
                if (seesBoy && sus > 0.5f) { setState(CHASE); replanT = 0; break; }
                if (stateT < dt * 1.5f) plan(nav, lastX, lastY);
                if (pathI < pathN) followPath(dt, nav, walkSpeed * 1.3f);
                else {
                    moveAmt = approach(moveAmt, 0, dt * 5);
                    if (waitT == 0) lookBase = heading;
                    waitT += dt;
                    turnTo(lookBase + 1.1f * (float) Math.sin(waitT * 2.2f), dt, 5);
                    if (waitT > 2.8f) { waitT = 0; sus = 0; setState(RETURN); }
                }
                break;
            case RETURN:
                if (stateT < dt * 1.5f) {
                    // back to the nearest waypoint of the route
                    int best = 0;
                    float bd = Float.MAX_VALUE;
                    for (int i = 0; i < def.route.length; i++) {
                        float d = nav.floorDist(x, y, def.route[i][0], def.route[i][1]);
                        if (d < bd) { bd = d; best = i; }
                    }
                    wp = best;
                    plan(nav, def.route[wp][0], def.route[wp][1]);
                }
                followPath(dt, nav, walkSpeed);
                if (pathI >= pathN) { setState(PATROL); waitT = 0; }
                if (sus > 0.25f) setState(SUSPICIOUS);
                break;
        }
        // caught: the chase reached him (or he walked straight into an alert guard)
        return boyActive && state >= ALERT && state <= SEARCH && dist < catchR;
    }

    private void setState(int s) {
        if (s == state) return;
        if (s == ALERT || s == SUSPICIOUS) bubbleT = 0;
        state = s;
        stateT = 0;
        if (s != SEARCH) waitT = 0;
    }

    private void patrol(float dt, Nav nav) {
        float[] t = def.route[wp];
        if (waitT > 0) {
            // look-around pause: sweep the flashlight
            waitT -= dt;
            moveAmt = approach(moveAmt, 0, dt * 6);
            float look = def.pause.length > wp * 2 + 1 ? def.pause[wp * 2 + 1] : heading;
            turnTo(look + 0.5f * (float) Math.sin((waitT) * 2.0f), dt, 3);
            if (waitT <= 0) {
                waitT = 0;
                nextWaypoint();
            }
            return;
        }
        float d = nav.floorDist(x, y, t[0], t[1]);
        if (d < room.boyH * 0.06f) {
            float p = def.pause.length > wp * 2 ? def.pause[wp * 2] : 0;
            if (p > 0) waitT = p;
            else nextWaypoint();
            return;
        }
        steer(dt, nav, t[0], t[1], walkSpeed);
    }

    private void nextWaypoint() {
        int n = def.route.length;
        if (n <= 1) return;
        if (def.pingPong) {
            if (wp + dir >= n || wp + dir < 0) dir = -dir;
            wp += dir;
        } else wp = (wp + 1) % n;
    }

    private void plan(Nav nav, float tx, float ty) {
        pathN = nav.path(x, y, tx, ty, feetR, path, path.length / 2);
        pathI = 0;
    }

    private void followPath(float dt, Nav nav, float speed) {
        if (pathI >= pathN) { moveAmt = approach(moveAmt, 0, dt * 6); return; }
        float tx = path[pathI * 2], ty = path[pathI * 2 + 1];
        if (nav.floorDist(x, y, tx, ty) < room.boyH * 0.07f) { pathI++; return; }
        steer(dt, nav, tx, ty, speed);
    }

    private void steer(float dt, Nav nav, float tx, float ty, float speed) {
        float d = Math.max(0.1f, room.depth(y));
        float vf = room.vFactor * (float) Math.pow(d, room.vPow);
        float fx = (tx - x) / d, fy = (ty - y) / vf;
        float len = (float) Math.hypot(fx, fy);
        if (len < 1e-3f) return;
        float want = (float) Math.atan2(fx, -fy);
        turnTo(want, dt, 7);
        // walk along the current heading once roughly facing the target (so turns read as turns)
        float align = (float) Math.cos(Boy.wrap(want - heading));
        float v = speed * Math.max(0, align) * Math.min(1, len / (room.boyH * 0.05f) + 0.3f);
        moveAmt = approach(moveAmt, 1, dt * 5);
        float stepF = v * dt;
        float nx = x + fx / len * stepF * d, ny = y + fy / len * stepF * vf;
        if (nav.canStand(nx, ny, feetR)) { x = nx; y = ny; }
        else if (nav.canStand(nx, y, feetR)) x = nx;
        else if (nav.canStand(x, ny, feetR)) y = ny;
        stepPhase += stepF / (room.boyH * 0.55f);
    }

    private void turnTo(float target, float dt, float rate) {
        float dd = Boy.wrap(target - heading);
        float mx = rate * dt;
        heading = Boy.wrap(heading + Math.max(-mx, Math.min(mx, dd)));
    }

    private static float approach(float v, float t, float k) {
        return v + (t - v) * Math.min(1, k);
    }

    // =====================================================================================
    // drawing
    // =====================================================================================

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path fanPath = new Path();
    private final RectF rect = new RectF();

    /** flashlight beam on the floor: a fan of rays clipped by obstacles */
    void drawCone(Canvas c, Nav nav, float time) {
        int n = fan.length / 2 - 2;
        float d = Math.max(0.1f, room.depth(y));
        float r = range * (state == CHASE ? 1.15f : 1f);
        // start a little in front of the feet so the beam leaves the guard, not his heels
        float ox = x + (float) Math.sin(heading) * d * room.boyH * 0.08f;
        float oy = y - (float) Math.cos(heading) * room.vFactor * (float) Math.pow(d, room.vPow) * room.boyH * 0.08f;
        fanN = 0;
        fan[fanN++] = ox;
        fan[fanN++] = oy;
        for (int i = 0; i <= n; i++) {
            float a = heading - halfAngle + 2 * halfAngle * i / n;
            float fx = (float) Math.sin(a), fy = -(float) Math.cos(a);
            float t = nav.ray(ox, oy, fx, fy, r);
            // walk the ray again to find its end point in world space
            float px = ox, py = oy, travelled = 0, want = t * r, step = nav.div * 0.8f;
            while (travelled < want) {
                float dd = Math.max(0.1f, room.depth(py));
                float sx = fx * dd, sy = fy * room.vFactor * (float) Math.pow(dd, room.vPow);
                float len = (float) Math.hypot(sx, sy);
                float k = Math.min(step / len, want - travelled);
                px += sx * k;
                py += sy * k;
                travelled += k;
                if (k <= 0) break;
            }
            fan[fanN++] = px;
            fan[fanN++] = py;
        }
        fanPath.reset();
        fanPath.moveTo(fan[0], fan[1]);
        for (int i = 2; i < fanN; i += 2) fanPath.lineTo(fan[i], fan[i + 1]);
        fanPath.close();
        int col = beamColor();
        float reach = r * d * 1.05f;
        float flick = 0.92f + 0.08f * (float) Math.sin(time * 13 + x);
        int a0 = Math.round((state >= ALERT ? 150 : 115) * flick);
        paint.setShader(new RadialGradient(ox, oy, Math.max(4, reach), new int[]{
                Color.argb(a0, Color.red(col), Color.green(col), Color.blue(col)),
                Color.argb(a0 * 2 / 3, Color.red(col), Color.green(col), Color.blue(col)),
                Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);   // paint alpha modulates the shader
        c.drawPath(fanPath, paint);
        paint.setShader(null);
    }

    int beamColor() {
        int base = room.beamColor;
        if (state >= ALERT && state <= CHASE) return Color.rgb(255, 60, 50);
        if (state == SUSPICIOUS || state == SEARCH) {
            float k = state == SEARCH ? 0.5f : Math.min(1, sus * 1.2f);
            return mix(base, Color.rgb(255, 150, 40), k);
        }
        return base;
    }

    static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** head-top in world coords after draw() (for the bubble) */
    float headX, headY, drawH;

    void draw(Canvas c, Host host, float time) {
        float d = room.depth(y);
        float H = room.guardH * d;
        boolean front = state == ALERT || (state == CHASE && Math.abs(Boy.wrap(heading)) > Math.PI * 0.72);
        Bitmap b = host.bitmap(front ? FRONT : SIDE);
        // shadow
        paint.setShader(null);
        paint.setColor(Color.argb(90, 20, 5, 20));
        float sw = H * 0.42f, sh = sw * 0.28f;
        rect.set(x - sw / 2, y - sh / 2, x + sw / 2, y + sh / 2);
        c.drawOval(rect, paint);
        if (b == null) return;
        boolean faceLeft = Math.sin(heading) < 0;
        float spH = front ? FRONT_H : SIDE_H, ax = front ? FRONT_AX : SIDE_AX, ay = front ? FRONT_AY : SIDE_AY;
        float s = H / spH;
        // waddle: bob twice per stride, tilt with the stepping leg, a little squash
        float ph = stepPhase * (float) Math.PI * 2;
        float bob = moveAmt * Math.abs((float) Math.sin(ph)) * H * 0.035f;
        float tilt = moveAmt * 3.2f * (float) Math.sin(ph);
        float squash = 1 + moveAmt * 0.03f * (float) Math.cos(ph * 2);
        float alertJump = state == ALERT ? Math.max(0, (float) Math.sin(Math.min(1, stateT / 0.3f) * Math.PI)) * H * 0.08f : 0;
        c.save();
        c.translate(x, y - bob - alertJump);
        c.rotate(tilt);
        c.scale((faceLeft && !front ? -1 : 1) * s / squash, s * squash);
        c.drawBitmap(b, -ax, -ay, bmp);
        // lens glow
        float lx = (front ? FRONT_LX : SIDE_LX) - ax, ly = (front ? FRONT_LY : SIDE_LY) - ay;
        int col = beamColor();
        float gr = 26 * (0.9f + 0.1f * (float) Math.sin(time * 11));
        paint.setShader(new RadialGradient(lx, ly, gr, new int[]{Color.argb(230, 255, 255, 235),
                Color.argb(120, Color.red(col), Color.green(col), Color.blue(col)), Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))},
                new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(lx, ly, gr, paint);
        paint.setShader(null);
        c.restore();
        headX = x;
        headY = y - H * 1.02f - bob - alertJump;
        drawH = H;
    }

    /** "?" / "!" bubble above the head, drawn after the world so occluders never hide it */
    void drawBubble(Canvas c, Typeface font, float time) {
        String t = null;
        int col = 0;
        if (state == SUSPICIOUS || state == SEARCH) { t = "?"; col = Color.rgb(255, 205, 40); }
        else if (state == ALERT || state == CHASE) { t = "!"; col = Color.rgb(255, 60, 50); }
        if (t == null) return;
        float pop = Math.min(1, bubbleT / 0.18f);
        float sc = pop < 1 ? 0.4f + 0.8f * pop : 1 + 0.06f * (float) Math.sin(time * 9);
        float r = drawH * 0.13f * sc;
        float cx = headX, cy = headY - r * 1.2f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(235, 255, 255, 255));
        c.drawCircle(cx, cy, r, paint);
        Path tail = fanPath;
        tail.reset();
        tail.moveTo(cx - r * 0.35f, cy + r * 0.7f);
        tail.lineTo(cx + r * 0.1f, cy + r * 1.45f);
        tail.lineTo(cx + r * 0.35f, cy + r * 0.6f);
        tail.close();
        c.drawPath(tail, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * 0.12f);
        paint.setColor(Color.argb(220, 40, 20, 40));
        c.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(font);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(r * 1.5f);
        paint.setColor(col);
        c.drawText(t, cx, cy + r * 0.52f, paint);
        // suspicion meter ring while "?"
        if (state == SUSPICIOUS) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.2f);
            paint.setColor(Color.argb(230, 255, 150, 40));
            rect.set(cx - r * 1.18f, cy - r * 1.18f, cx + r * 1.18f, cy + r * 1.18f);
            c.drawArc(rect, -90, 360 * sus, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
