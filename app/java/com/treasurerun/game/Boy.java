package com.treasurerun.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/**
 * The player: movement state plus a "3D-lite" walk rig.
 * The painted body (head, beanie, backpack) is one sprite per view; the visible arm is a separate sprite swung
 * about the shoulder, and the legs are procedural: a small 3D model (hips, IK knees, heel-toe feet) projected
 * orthographically with the view's yaw and the camera pitch, drawn as shaded tapered capsules in the painted
 * jeans / red-sneaker colours. The cycle phase is driven by distance walked, so planted feet don't slide.
 */
final class Boy {
    static final int BACK = 0, TQ = 1, FRONT = 2;

    /** per-view rig description, all in that view's sprite pixels */
    static final class Rig {
        String body, arm;
        float spriteH, crouch;      // scale: depth*boyH*crouch world px == spriteH sprite px
        float hipX, hipY, groundY;  // hip centre and the ground line under it
        float hipHalf;              // 3D half distance between hip joints
        float yaw;                  // facing, degrees: 0 = away from camera, 90 = screen right
        float thigh, shin;          // 3D segment lengths
        float footLen;              // heel to toe
        float thighR, kneeR, ankleR;
        float stride, lift;         // walk step length and swing lift (3D)
        float armX, armY;           // arm sprite top-left in body sprite coords
        float pivotX, pivotY, handX, handY;
        float armAmp;               // arm swing amplitude (radians)
        float legScale = 1;         // overall leg size tweak for this view
    }

    static final Rig[] RIGS = new Rig[3];

    static {
        Rig r = new Rig();
        r.body = "rooms/boy_back_body.png"; r.arm = "rooms/boy_back_arm.png";
        r.spriteH = 758; r.crouch = 1;
        r.hipX = 170; r.hipY = 505; r.groundY = 758; r.hipHalf = 44;
        r.yaw = 0;
        r.thigh = 130; r.shin = 124; r.footLen = 128;
        r.thighR = 56; r.kneeR = 43; r.ankleR = 33;
        r.stride = 170; r.lift = 95;
        r.armX = 248; r.armY = 282; r.pivotX = 272; r.pivotY = 318; r.handX = 338; r.handY = 452;
        r.armAmp = 0.55f;
        RIGS[BACK] = r;

        r = new Rig();
        r.body = "rooms/boy_tq_body.png"; r.arm = "rooms/boy_tq_arm.png";
        r.spriteH = 849; r.crouch = 1;
        r.hipX = 250; r.hipY = 590; r.groundY = 865; r.hipHalf = 52;
        r.yaw = 42;
        r.thigh = 138; r.shin = 132; r.footLen = 140;
        r.thighR = 60; r.kneeR = 46; r.ankleR = 36;
        r.stride = 180; r.lift = 85;
        r.armX = 352; r.armY = 360; r.pivotX = 385; r.pivotY = 405; r.handX = 482; r.handY = 540;
        r.armAmp = 0.6f;
        RIGS[TQ] = r;

        r = new Rig();
        r.body = "rooms/boy_front_body.png"; r.arm = null;
        r.spriteH = 596; r.crouch = 0.8f;
        r.hipX = 245; r.hipY = 490; r.groundY = 600; r.hipHalf = 40;
        r.yaw = 112;
        r.thigh = 92; r.shin = 90; r.footLen = 104;
        r.thighR = 44; r.kneeR = 34; r.ankleR = 27;
        r.stride = 130; r.lift = 45;
        RIGS[FRONT] = r;
    }

    // camera pitch (how far the view looks down); matches the painted perspective
    static final float PITCH = (float) Math.toRadians(28);
    static final float CP = (float) Math.cos(PITCH), SP = (float) Math.sin(PITCH);

    // ---- movement state ----
    float x, y, vx, vy;
    /** smoothed floor-space heading, radians: 0 = up/away, +pi/2 = right */
    float heading;
    int view = BACK;
    boolean mirror;
    int prevView = BACK;
    boolean prevMirror;
    float turnT = 1;
    /** walk cycle phase, 0..1 (leg 0 heel-strike at 0) */
    float phase;
    /** 0 idle .. 1 walking, eased */
    float moveAmt;
    /** 0 walk .. 1 sprint style, eased */
    float runAmt;
    /** reaching up for a prize, 0..1 */
    float reach;
    float time;
    /** forced facing (e.g. while stealing): -999 = none */
    float faceLock = -999;

    // ---- drawing scratch ----
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final Matrix mtx = new Matrix();
    private final float[] p3 = new float[3];

    void place(float x, float y, float headingRad) {
        this.x = x;
        this.y = y;
        vx = vy = 0;
        heading = headingRad;
        pickView(true);
        prevView = view;
        prevMirror = mirror;
        turnT = 1;
        moveAmt = 0;
        runAmt = 0;
        reach = 0;
        faceLock = -999;
    }

    /**
     * Advance animation from the motion just applied.
     * @param floorMoved distance moved this frame in floor px (depth-1 world px)
     * @param fdx fdy    floor-space velocity direction (unnormalised; ignored when ~0)
     * @param boyH       room's boy height at depth 1 (world px)
     * @param moving     whether the boy is being driven (for the idle blend)
     * @param sprint     sprint style
     */
    void animate(float dt, float floorMoved, float fdx, float fdy, float boyH, boolean moving, boolean sprint) {
        time += dt;
        float sp = (float) Math.hypot(fdx, fdy);
        if (faceLock > -998) {
            turnHeading(faceLock, dt);
        } else if (sp > 1e-3f && moving) {
            turnHeading((float) Math.atan2(fdx, -fdy), dt);
        }
        pickView(false);
        turnT = Math.min(1, turnT + dt / 0.16f);
        float target = floorMoved / Math.max(dt, 1e-4f) > boyH * 0.08f ? 1 : 0;
        moveAmt += (target - moveAmt) * Math.min(1, dt * (target > moveAmt ? 9 : 7));
        runAmt += ((sprint && target > 0 ? 1 : 0) - runAmt) * Math.min(1, dt * 6);
        // phase from distance: one cycle = two steps; convert floor px to this view's sprite px
        Rig r = RIGS[view];
        float spritePerFloor = r.spriteH / (boyH * r.crouch);
        float stride = r.stride * (1 + 0.35f * runAmt);
        phase += floorMoved * spritePerFloor / (2 * stride);
        phase -= (float) Math.floor(phase);
    }

    private void turnHeading(float target, float dt) {
        float d = wrap(target - heading);
        float maxStep = (float) Math.toRadians(600) * dt;
        heading = wrap(heading + Math.max(-maxStep, Math.min(maxStep, d)));
    }

    private void pickView(boolean force) {
        float deg = (float) Math.toDegrees(heading);
        float a = Math.abs(deg);
        int v;
        float hyst = force ? 0 : 6;
        if (view == BACK) v = a <= 30 + hyst ? BACK : a <= 75 ? TQ : FRONT;
        else if (view == TQ) v = a <= 30 - hyst ? BACK : a <= 75 + hyst ? TQ : FRONT;
        else v = a <= 30 ? BACK : a <= 75 - hyst ? TQ : FRONT;
        boolean m = mirror;
        if (v != BACK) {
            if (a > 172 || a < 8) m = force ? false : mirror;
            else m = deg < 0;
        } else m = false;
        if (force) {
            view = v;
            mirror = m;
            return;
        }
        if (v != view || m != mirror) {
            prevView = view;
            prevMirror = mirror;
            view = v;
            mirror = m;
            turnT = 0;
        }
    }

    static float wrap(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a < -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }

    // =====================================================================================
    // drawing
    // =====================================================================================

    /** world height of the drawn boy (for shadows / effects) */
    float height(Room room) {
        return room.depth(y) * room.boyH;
    }

    void draw(Canvas c, Room room, Host host) {
        float depth = room.depth(y);
        // contact shadow
        float sw = room.boyH * depth * 0.36f;
        float sh = sw * (room.vPow > 1.5f ? 0.22f : 0.3f);
        fill.setShader(null);
        fill.setColor(Color.argb(95, 20, 5, 20));
        c.drawOval(new android.graphics.RectF(x - sw / 2, y - sh / 2, x + sw / 2, y + sh / 2), fill);

        boolean old = turnT < 0.5f;
        int v = old ? prevView : view;
        boolean m = old ? prevMirror : mirror;
        float squash = Math.max(0.08f, Math.abs((float) Math.cos(turnT * Math.PI)));
        Rig r = RIGS[v];
        Bitmap body = host.bitmap(r.body);
        if (body == null) return;
        float s = depth * room.boyH * r.crouch / r.spriteH;

        c.save();
        c.translate(x, y);
        c.scale((m ? -1 : 1) * squash * s, s);
        drawRig(c, r, body, r.arm == null ? null : host.bitmap(r.arm));
        c.restore();
    }

    // leg joint positions in screen space (sprite px, origin = ground under hip centre)
    private final float[] hip = new float[6], knee = new float[6], ankle = new float[6], toe = new float[6], heel = new float[6];
    private final float[] soleVis = new float[2], legZ = new float[2], footPitch = new float[2];

    private void drawRig(Canvas c, Rig r, Bitmap body, Bitmap arm) {
        float yaw = (float) Math.toRadians(r.yaw);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float walk = moveAmt;
        float run = runAmt;
        float stride = r.stride * (1 + 0.35f * run) * walk;
        float lift = r.lift * (1 + 0.6f * run) * walk;
        float duty = 0.62f - 0.2f * run;          // stance share of the cycle
        float legLen = r.thigh + r.shin;
        float hipH0 = (r.groundY - r.hipY) / CP;  // 3D hip height that projects onto the painted hip line

        // pelvis motion: bounce twice per cycle (highest mid-stance), sway towards the stance leg
        float bounce = (float) Math.cos((phase - duty * 0.5f) * Math.PI * 4);
        float hipDy = walk * (legLen * (0.035f + 0.04f * run)) * bounce - walk * legLen * (0.03f + 0.05f * run);
        float breathe = (1 - walk) * (float) Math.sin(time * 2.3f) * legLen * 0.008f;
        float sway = walk * r.hipHalf * 0.22f * (float) Math.sin(phase * Math.PI * 2);
        float hipH = hipH0 + hipDy + breathe;
        float lean = walk * (0.05f + 0.12f * run);   // forward lean (radians) of the upper body

        for (int leg = 0; leg < 2; leg++) {
            float side = leg == 0 ? -1 : 1;       // -1 = character's left
            float ph = phase + leg * 0.5f;
            ph -= (float) Math.floor(ph);
            float fz, fy, pitch;                  // foot (ankle) forward offset, height above ground, toe angle
            if (ph < duty) {
                float u = ph / duty;
                fz = stride * (0.5f - u);
                // heel strike -> flat -> heel rise (roll over the toe)
                pitch = walk * (u < 0.15f ? 0.35f * (1 - u / 0.15f) : u > 0.6f ? -0.75f * (u - 0.6f) / 0.4f : 0);
                fy = 0;
            } else {
                float u = (ph - duty) / (1 - duty);
                float e = u * u * (3 - 2 * u);
                fz = stride * (-0.5f + e);
                fy = lift * (float) Math.pow(Math.sin(Math.PI * u), 0.8) * (1 - 0.35f * u);
                pitch = walk * (-0.75f * (1 - u) * (1 - u) + 0.35f * u * u * u);
            }
            float ankleH = r.ankleR * 0.9f + fy + Math.max(0, -pitch) * r.footLen * 0.55f;
            // hip joint (character local: x right, y up, z forward)
            float hx = side * r.hipHalf + sway, hy = hipH, hz = 0;
            float ax = side * r.hipHalf * 0.85f + sway * 0.3f, ay = ankleH, az = fz;
            // two-bone IK in the leg's sagittal plane, knee bends forward
            float dy = ay - hy, dz = az - hz;
            float d = (float) Math.hypot(dy, dz);
            float t = r.thigh * r.legScale, sh = r.shin * r.legScale;
            if (d > t + sh - 0.5f) {
                float k = (t + sh - 0.5f) / d;
                dy *= k; dz *= k; d = t + sh - 0.5f;
                ay = hy + dy; az = hz + dz;
            }
            float cosA = (t * t + d * d - sh * sh) / (2 * t * d);
            float alpha = (float) Math.acos(Math.max(-1, Math.min(1, cosA)));
            // angles measured from +y towards +z; the leg points down so base ~ +-pi, and base - alpha bends the knee forward
            float base = (float) Math.atan2(dz, dy);
            float kAng = base - alpha;
            float ky = hy + t * (float) Math.cos(kAng), kz = hz + t * (float) Math.sin(kAng);
            float kx = (hx + ax) * 0.5f;
            // foot axes after pitch (toe up = +): forward (0, sin, cos), down (0, -cos, sin)
            float fcos = (float) Math.cos(pitch), fsin = (float) Math.sin(pitch);
            float back = r.footLen * 0.22f, front = r.footLen * 0.78f;
            float heelX = ax, heelY = ay - fsin * back - fcos * r.ankleR * 0.55f, heelZ = az - fcos * back + fsin * r.ankleR * 0.55f;
            float toeX = ax, toeY = ay + fsin * front - fcos * r.ankleR * 0.75f, toeZ = az + fcos * front + fsin * r.ankleR * 0.75f;

            project(hx, hy, hz, cy, sy, r, hip, leg);
            project(kx, ky, kz, cy, sy, r, knee, leg);
            project(ax, ay, az, cy, sy, r, ankle, leg);
            project(heelX, heelY, heelZ, cy, sy, r, heel, leg);
            project(toeX, toeY, toeZ, cy, sy, r, toe, leg);
            legZ[leg] = knee[leg * 3 + 2];
            // sole normal = foot down axis; it faces the camera when its dot with the view direction (0,-SP,CP) is negative
            float nDotView = fcos * SP + fsin * cy * CP;
            soleVis[leg] = Math.max(0, Math.min(1, -nDotView * 2.5f));
            footPitch[leg] = pitch;
        }

        // arm swing (visible right arm, opposite to the right leg), or a reach for the prize
        float armSwing = 0;
        {
            float ph = phase + 0.5f;                              // right leg = leg 1
            ph -= (float) Math.floor(ph);
            armSwing = -r.armAmp * (1 + 0.5f * run) * walk * (float) Math.cos(ph * Math.PI * 2);
        }

        // draw back-to-front: far leg, near leg, body, arm
        int first = legZ[0] > legZ[1] ? 0 : 1;
        drawLeg(c, r, first);
        // seat of the jeans: joins both hips under the jacket hem
        {
            float lx = hip[0], ly = hip[1], rx = hip[3], ry = hip[4];
            capsule(c, lx, ly - r.thighR * 0.2f, r.thighR * 1.05f, rx, ry - r.thighR * 0.2f, r.thighR * 1.05f, JEANS_DK, JEANS, JEANS_HI);
        }
        drawLeg(c, r, 1 - first);

        // body with bounce and lean (lean shows as a tilt for side-ish views)
        float hipLift = (hipH - hipH0) * CP;
        float tilt = (float) Math.toDegrees(lean * sy) + (float) Math.toDegrees(sway / r.hipHalf * 0.04f * cy);
        c.save();
        c.translate(sway * cy, -hipLift);
        c.rotate(tilt, 0, -(r.groundY - r.hipY));
        c.drawBitmap(body, -r.hipX, -r.groundY, bmp);
        if (arm != null) drawArm(c, r, arm, armSwing, cy, sy);
        c.restore();
    }

    /** character local (x right, y up, z forward) -> sprite screen px relative to the ground under the hips */
    private void project(float lx, float ly, float lz, float cy, float sy, Rig r, float[] out, int leg) {
        float X = lx * cy + lz * sy;
        float Z = -lx * sy + lz * cy;
        out[leg * 3] = X;
        out[leg * 3 + 1] = -(ly * CP + Z * SP);
        out[leg * 3 + 2] = Z;
    }

    // jeans / sneaker palette sampled from the painted boy
    private static final int JEANS_DK = Color.rgb(12, 20, 58), JEANS = Color.rgb(30, 52, 132), JEANS_HI = Color.rgb(78, 116, 212);
    private static final int CUFF = Color.rgb(52, 86, 176), CUFF_HI = Color.rgb(104, 140, 226);
    private static final int RED_DK = Color.rgb(120, 14, 18), RED = Color.rgb(214, 36, 30), RED_HI = Color.rgb(255, 112, 84);
    private static final int SOLE = Color.rgb(242, 236, 244), SOLE_DK = Color.rgb(170, 160, 186);
    private static final int TREAD = Color.rgb(44, 26, 48), TREAD_HI = Color.rgb(78, 56, 84);

    private void drawLeg(Canvas c, Rig r, int leg) {
        int i = leg * 3;
        float hx = hip[i], hy = hip[i + 1], kx = knee[i], ky = knee[i + 1], ax = ankle[i], ay = ankle[i + 1];
        float ls = r.legScale;
        // when the toe points away from the camera the leg hides the front of the shoe: shoe first
        boolean shoeBehind = toe[i + 2] > ankle[i + 2];
        if (shoeBehind) drawShoe(c, r, leg);
        // thigh and shin, then the rolled cuff
        capsule(c, hx, hy, r.thighR * ls, kx, ky, r.kneeR * ls, JEANS_DK, JEANS, JEANS_HI);
        capsule(c, kx, ky, r.kneeR * ls, ax, ay, r.ankleR * 1.08f * ls, JEANS_DK, JEANS, JEANS_HI);
        float cx = ax + (kx - ax) * 0.16f, cyy = ay + (ky - ay) * 0.16f;
        capsule(c, cx, cyy, r.ankleR * 1.28f * ls, ax + (kx - ax) * 0.02f, ay + (ky - ay) * 0.02f, r.ankleR * 1.22f * ls, JEANS_DK, CUFF, CUFF_HI);
        if (!shoeBehind) drawShoe(c, r, leg);
    }

    private void drawShoe(Canvas c, Rig r, int leg) {
        int i = leg * 3;
        float hx = heel[i], hy = heel[i + 1], tx = toe[i], ty = toe[i + 1];
        float rh = r.ankleR * 1.2f, rt = r.ankleR * 1.1f;
        // white midsole slightly below, red upper on top, then the tread if the sole faces us
        float dy = r.ankleR * 0.34f;
        capsule(c, hx, hy + dy, rh * 1.04f, tx, ty + dy, rt * 1.04f, SOLE_DK, SOLE, SOLE);
        capsule(c, hx, hy - dy * 0.45f, rh * 0.9f, tx, ty - dy * 0.45f, rt * 0.86f, RED_DK, RED, RED_HI);
        float sv = soleVis[leg];
        // laces: a white criss-cross strip from the ankle towards the toe, on the upper surface
        float up = 1 - sv;
        if (up > 0.3f) {
            float ddx = tx - hx, ddy = ty - hy;
            float len = (float) Math.hypot(ddx, ddy) + 1e-3f;
            float nx = -ddy / len, ny = ddx / len;
            float ox = 0, oy = -dy * 1.1f;
            fill.setShader(null);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeCap(Paint.Cap.ROUND);
            fill.setStrokeWidth(r.ankleR * 0.16f);
            fill.setColor(Color.argb(Math.round(230 * Math.min(1, (up - 0.3f) * 2)), 250, 246, 240));
            for (int k = 0; k < 3; k++) {
                float t = 0.42f + k * 0.14f;
                float px = hx + ddx * t + ox, py = hy + ddy * t + oy;
                float w = rh * (0.42f - k * 0.05f);
                c.drawLine(px - nx * w, py - ny * w, px + nx * w, py + ny * w, fill);
            }
            fill.setStyle(Paint.Style.FILL);
        }
        if (sv > 0.02f) {
            fill.setShader(null);
            int a = Math.round(255 * sv);
            float mx = (hx + tx) / 2, my = (hy + ty) / 2 + dy * 0.6f;
            // tread: dark rounded pad with a few ridges
            capsuleFlat(c, hx + (mx - hx) * 0.1f, hy + dy * 0.8f, rh * 0.8f, tx + (mx - tx) * 0.1f, ty + dy * 0.8f, rt * 0.75f, Color.argb(a, Color.red(TREAD), Color.green(TREAD), Color.blue(TREAD)));
            fill.setStrokeWidth(r.ankleR * 0.12f);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeCap(Paint.Cap.ROUND);
            fill.setColor(Color.argb(Math.round(a * 0.7f), Color.red(TREAD_HI), Color.green(TREAD_HI), Color.blue(TREAD_HI)));
            float ddx = tx - hx, ddy = ty - hy;
            float len = (float) Math.hypot(ddx, ddy) + 1e-3f;
            float nx = -ddy / len, ny = ddx / len;
            for (int k = 1; k <= 4; k++) {
                float t = k / 5f;
                float px = hx + ddx * t, py = hy + ddy * t + dy * 0.8f;
                float rr = (rh + (rt - rh) * t) * 0.55f;
                c.drawLine(px - nx * rr, py - ny * rr, px + nx * rr, py + ny * rr, fill);
            }
            fill.setStyle(Paint.Style.FILL);
        }
    }

    private void drawArm(Canvas c, Rig r, Bitmap arm, float swing, float cy, float sy) {
        // rest arm vector (sprite px, pivot->hand) lifted into 3D in the screen plane at the shoulder's depth
        float vx0 = r.handX - r.pivotX, vy0 = r.handY - r.pivotY;
        // local: screen x -> world X; screen y down -> Y up / CP
        float X = vx0, Y = -vy0 / CP, Z = 0;
        // rotate about the character's lateral axis (right vector (cy,0,-sy) in world) by the swing (forward = +)
        float ang = swing + reach * 1.9f;
        // express in the character frame: lateral l, up Y, forward f
        float l = X * cy - Z * sy, f = X * sy + Z * cy;
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float Y2 = Y * ca + f * sa, f2 = -Y * sa + f * ca;
        // back to world then project
        float X2 = l * cy + f2 * sy, Z2 = -l * sy + f2 * cy;
        float px = X2, py = -(Y2 * CP + Z2 * SP);
        float len0 = (float) Math.hypot(vx0, vy0), len1 = (float) Math.hypot(px, py);
        float rot = (float) Math.toDegrees(Math.atan2(py, px) - Math.atan2(vy0, vx0));
        float k = Math.max(0.45f, len1 / Math.max(1, len0));
        // arm sprite: rotate about the pivot, stretch along the arm axis only
        mtx.reset();
        mtx.setTranslate(r.armX - r.pivotX, r.armY - r.pivotY);
        float base = (float) Math.toDegrees(Math.atan2(vy0, vx0));
        mtx.postRotate(-base);
        mtx.postScale(k, 1);
        mtx.postRotate(base + rot);
        mtx.postTranslate(r.pivotX - r.hipX, r.pivotY - r.groundY);
        c.drawBitmap(arm, mtx, bmp);
    }

    // ---- shading primitives ----
    private final float[] hull = new float[80];

    /** tapered capsule between two circles, shaded across its width (dark rim / base / highlight towards upper-left) */
    private void capsule(Canvas c, float x1, float y1, float r1, float x2, float y2, float r2, int dark, int base, int hi) {
        int n = hullPath(x1, y1, r1, x2, y2, r2);
        if (n == 0) return;
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        float nx, ny;
        if (len < 1e-3f) { nx = -1; ny = 0; } else { nx = -dy / len; ny = dx / len; }
        // make the normal point towards the light (upper-left)
        if (nx * -0.7f + ny * -0.7f < 0) { nx = -nx; ny = -ny; }
        float rr = Math.max(r1, r2);
        float mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
        fill.setShader(new LinearGradient(mx + nx * rr, my + ny * rr, mx - nx * rr, my - ny * rr,
                new int[]{base, hi, base, dark, dark}, new float[]{0f, 0.28f, 0.55f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFFFFFFF);
        c.drawPath(path, fill);
        fill.setShader(null);
    }

    private void capsuleFlat(Canvas c, float x1, float y1, float r1, float x2, float y2, float r2, int color) {
        if (hullPath(x1, y1, r1, x2, y2, r2) == 0) return;
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    /** builds the convex hull of two circles into `path`; returns point count */
    private int hullPath(float x1, float y1, float r1, float x2, float y2, float r2) {
        path.reset();
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len <= Math.abs(r1 - r2) + 0.01f) {
            // one circle contains the other
            float r = Math.max(r1, r2);
            float cx = r1 >= r2 ? x1 : x2, cy = r1 >= r2 ? y1 : y2;
            path.addCircle(cx, cy, r, Path.Direction.CW);
            return 1;
        }
        float ux = dx / len, uy = dy / len;
        float sb = (r1 - r2) / len;
        float cb = (float) Math.sqrt(Math.max(0, 1 - sb * sb));
        float a0 = (float) Math.atan2(uy, ux);
        float beta = (float) Math.atan2(cb, sb);       // angle of the tangent point from the axis
        int seg = 10;
        boolean firstPt = true;
        // around circle 2 from +beta to -beta through the front (angle 0)
        for (int k = 0; k <= seg; k++) {
            float t = beta - 2 * beta * k / seg;
            float px = x2 + r2 * (float) Math.cos(a0 + t), py = y2 + r2 * (float) Math.sin(a0 + t);
            if (firstPt) { path.moveTo(px, py); firstPt = false; } else path.lineTo(px, py);
        }
        // around circle 1 from -beta to -(2pi - beta)... i.e. the back side
        float back = (float) (2 * Math.PI - 2 * beta);
        for (int k = 0; k <= seg; k++) {
            float t = -beta - back * k / seg;
            path.lineTo(x1 + r1 * (float) Math.cos(a0 + t), y1 + r1 * (float) Math.sin(a0 + t));
        }
        path.close();
        return 2 * seg + 2;
    }
}
