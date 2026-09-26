package com.treasurerun.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Draws a walking character: a painted upper-body sprite (legs removed) over procedural 3D-lite legs.
 *
 * Legs live in a small 3D frame around the character (lateral, forward, up; units = character heights) and are
 * projected with the room's floor foreshortening, so steps read correctly from any heading. The gait is driven by
 * distance travelled (phase), with planted feet during stance, heel-toe roll, lifted swing, knee IK, hip bob/sway and
 * a counter-swinging arm.
 */
final class Rig {
    /** One painted view of a character. Image coords are pixels of the body sprite. */
    static final class View {
        final String body, arm;
        final float pxPerH, hipX, hipY, canon, hipH;   // canon = heading (deg, 0=right, 90=toward camera) the art faces
        final float armX, armY, pivX, pivY, armRest;   // arm sprite offset in body coords, shoulder pivot, rest angle
        final float lampX, lampY;                      // flashlight lens (guards), body coords
        View(String body, float pxPerH, float hipX, float hipY, float hipH, float canon) {
            this(body, pxPerH, hipX, hipY, hipH, canon, null, 0, 0, 0, 0, 0, Float.NaN, Float.NaN);
        }
        View(String body, float pxPerH, float hipX, float hipY, float hipH, float canon, String arm, float armX, float armY, float pivX, float pivY, float armRest, float lampX, float lampY) {
            this.body = body; this.pxPerH = pxPerH; this.hipX = hipX; this.hipY = hipY; this.hipH = hipH; this.canon = canon;
            this.arm = arm; this.armX = armX; this.armY = armY; this.pivX = pivX; this.pivY = pivY; this.armRest = armRest;
            this.lampX = lampX; this.lampY = lampY;
        }
    }

    /** Leg proportions (character heights) and colours. */
    static final class Style {
        final float thigh, shin, hipW, legW, footL, footW, footT, stride, lift, armAmp;
        final int pants, pantsHi, outline, cuff, shoe, shoeHi, sole, soleLo;
        Style(float thigh, float shin, float hipW, float legW, float footL, float footW, float footT, float stride, float lift, float armAmp,
              int pants, int pantsHi, int outline, int cuff, int shoe, int shoeHi, int sole, int soleLo) {
            this.thigh = thigh; this.shin = shin; this.hipW = hipW; this.legW = legW; this.footL = footL; this.footW = footW; this.footT = footT;
            this.stride = stride; this.lift = lift; this.armAmp = armAmp;
            this.pants = pants; this.pantsHi = pantsHi; this.outline = outline; this.cuff = cuff; this.shoe = shoe; this.shoeHi = shoeHi; this.sole = sole; this.soleLo = soleLo;
        }
    }

    static final Style BOY = new Style(0.143f, 0.137f, 0.07f, 0.106f, 0.19f, 0.11f, 0.075f, 0.13f, 0.07f, 26f,
            Color.rgb(24, 40, 108), Color.rgb(66, 100, 190), Color.rgb(6, 8, 30), Color.rgb(16, 26, 78),
            Color.rgb(178, 16, 18), Color.rgb(240, 84, 56), Color.rgb(246, 238, 230), Color.rgb(150, 128, 128));
    static final Style GUARD = new Style(0.181f, 0.172f, 0.072f, 0.112f, 0.16f, 0.085f, 0.06f, 0.13f, 0.065f, 0f,
            Color.rgb(22, 27, 66), Color.rgb(52, 64, 128), Color.rgb(4, 5, 18), Color.rgb(16, 20, 50),
            Color.rgb(20, 18, 24), Color.rgb(96, 92, 110), Color.rgb(10, 8, 10), Color.rgb(40, 36, 40));

    // Boy: back / three-quarter back / front-sneak (faces right). Values from the v0.4 poses + art/wip/parts/boy_meta.json.
    static final View BOY_BACK = new View("rig/boy_back_body.png", 758f, 170f, 522f, 0.311f, -90f,
            "rig/boy_back_arm.png", 248f, 282f, 272f, 318f, 0f, Float.NaN, Float.NaN);
    static final View BOY_TQ = new View("rig/boy_tq_body.png", 849f, 238f, 606f, 0.286f, -40f,
            "rig/boy_tq_arm.png", 352f, 360f, 385f, 405f, 0f, Float.NaN, Float.NaN);
    static final View BOY_FRONT = new View("rig/boy_front_body.png", 745f, 222f, 492f, 0.145f, 18f);
    // Guards: three-quarter back (faces up-right), three-quarter front (faces down-left), front (faces camera, shocked).
    static final View GUARD_TQB = new View("rig/guard_tqb.png", 340f, 99f, 205f, 0.365f, -40f, null, 0, 0, 0, 0, 0, 178f, 165f);
    static final View GUARD_TQF = new View("rig/guard_tqf.png", 300f, 105f, 180f, 0.365f, 145f, null, 0, 0, 0, 0, 0, 24f, 124f);
    static final View GUARD_FRONT = new View("rig/guard_front.png", 290f, 80f, 200f, 0.365f, 90f, null, 0, 0, 0, 0, 0, 9f, 158f);

    /** Per-frame pose. */
    static final class Pose {
        float x, y;          // ground point (world px)
        float scale;         // world px per character height
        float fore;          // floor foreshortening: world px of forward floor distance per px of lateral
        float facing;        // heading used for the legs (deg)
        View view; boolean mirror;
        View prev; boolean prevMirror; float mix = 1f;   // turning: cross-fade from prev to view (mix 0..1)
        float squash = 1f;   // horizontal squash while the body swaps views
        float phase;         // gait cycle 0..1
        float amt;           // 0 standing .. 1 full stride
        float run;           // 0 walk .. 1 sprint
        float lean;          // deg, body tilt
        float breathe;       // idle breathing phase (rad)
        float reach;         // 0..1 arm reach toward (reachX, reachY)
        float reachX, reachY;
        float hop;           // extra lift (character heights), e.g. a startled jump
        int alpha = 255;
        float lampSX, lampSY; // out: flashlight lens in world coords (NaN if the view has none)
        float headSX, headSY; // out: top of head in world coords
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final float[] hip = new float[2 * 3], knee = new float[2 * 3], ankle = new float[2 * 3], pitch = new float[2];
    private final float[] tmp3 = new float[3];
    private final float[][] leg = new float[2][6];   // projected hip, knee, ankle (x,y)

    Rig() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    // --- projection: local (lat, fwd, up) in character heights -> world offset from the ground point ---
    private float fx, fy, rx, ry, sc, fore;
    private float projX(float lat, float fwd) { return (lat * rx + fwd * fx) * sc; }
    private float projY(float lat, float fwd, float up) { return (lat * ry + fwd * fy) * sc * fore - up * sc; }

    void draw(Canvas c, Style st, Pose p, Game.Host host) {
        View v = p.view;
        double th = Math.toRadians(p.facing);
        fx = (float) Math.cos(th); fy = (float) Math.sin(th);
        rx = -fy; ry = fx;                                // character's right hand side
        sc = p.scale; fore = p.fore;
        float amt = p.amt;
        float stride = st.stride * (1f + 0.35f * p.run) * amt;
        float lift = st.lift * Math.min(1f, amt * 2.5f) * (1f + 0.4f * p.run);
        float ph = p.phase;
        // hips: bob lowest at double support, sway toward the stance leg
        float bob = (0.016f + 0.012f * p.run) * amt * (0.5f + 0.5f * (float) Math.cos(2 * Math.PI * (2 * ph - 0.1f)));
        float breathe = (1 - amt) * 0.004f * (float) Math.sin(p.breathe);
        float sway = 0.014f * amt * (float) Math.sin(2 * Math.PI * ph);
        float hipH = v.hipH - bob + breathe + p.hop;
        final float beta = 0.6f;                           // stance fraction
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1f : 1f;
            float q = ph + i * 0.5f; q -= (float) Math.floor(q);
            float fwd, h, pt;
            if (q < beta) {
                float t = q / beta;
                fwd = stride * (1 - 2 * t);
                h = 0;
                pt = t < 0.18f ? 16f * (1 - t / 0.18f) : t > 0.68f ? -34f * (t - 0.68f) / 0.32f : 0f;
            } else {
                float t = (q - beta) / (1 - beta);
                fwd = stride * (-1 + 2 * smooth(t));
                h = lift * (float) Math.sin(Math.PI * t);
                pt = -34f + 50f * smooth(t);
            }
            pt *= Math.min(1f, amt * 1.5f);
            h += p.hop * 0.5f;
            float lat = side * st.hipW;
            // hip joint
            hip[i * 3] = lat + sway * 0.5f; hip[i * 3 + 1] = 0; hip[i * 3 + 2] = hipH + side * 0.006f * amt * (float) Math.sin(2 * Math.PI * ph);
            // ankle: foot rolls about the heel (toe up) or the toe (heel up)
            double pr = Math.toRadians(pt);
            float heelBack = st.footL * 0.28f, toeFwd = st.footL * 0.72f, ankUp = st.footT * 0.9f;
            float af, au;
            if (pt >= 0) { // pivot at heel (ground) -> ankle above heel+
                af = fwd + heelBack * (float) Math.cos(pr) - heelBack - ankUp * (float) Math.sin(pr);
                au = h + heelBack * (float) Math.sin(pr) + ankUp * (float) Math.cos(pr);
            } else {       // pivot at toe
                float toeF = fwd + toeFwd;
                af = toeF - toeFwd * (float) Math.cos(-pr) - ankUp * (float) Math.sin(-pr) * -1f;
                au = h + toeFwd * (float) Math.sin(-pr) + ankUp * (float) Math.cos(-pr);
            }
            ankle[i * 3] = lat; ankle[i * 3 + 1] = af; ankle[i * 3 + 2] = au;
            pitch[i] = pt;
            ik(i, st);
        }
        // ground shadow
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(0.37f * p.alpha), 20, 5, 20));
        float shw = sc * 0.36f, shh = shw * Math.max(0.22f, Math.min(0.45f, fore * 0.42f));
        r.set(p.x - shw / 2, p.y - shh / 2, p.x + shw / 2, p.y + shh / 2);
        c.drawOval(r, fill);
        // legs, the one further from the camera (smaller floor y) first
        float near0 = ankle[0] * ry + ankle[1] * fy, near1 = ankle[3] * ry + ankle[4] * fy;
        int first = near0 <= near1 ? 0 : 1;
        for (int k = 0; k < 2; k++) drawLeg(c, st, p, k == 0 ? first : 1 - first);
        // body (cross-faded with the previous view while turning)
        float hx = p.x + projX(sway, 0), hy = p.y + projY(sway, 0, hipH);
        if (p.prev != null && p.mix < 1f) {
            drawBody(c, st, p, p.prev, p.prevMirror, Math.round(p.alpha * (1f - smooth(p.mix))), hx, hy, amt, ph, host);
            drawBody(c, st, p, v, p.mirror, Math.round(p.alpha * smooth(Math.min(1f, p.mix * 1.4f))), hx, hy, amt, ph, host);
        } else drawBody(c, st, p, v, p.mirror, p.alpha, hx, hy, amt, ph, host);
        float s = sc / v.pxPerH;
        // outputs (approximate, ignoring lean)
        float mir = p.mirror ? -1 : 1;
        if (!Float.isNaN(v.lampX)) {
            p.lampSX = hx + (v.lampX - v.hipX) * s * mir * p.squash;
            p.lampSY = hy + (v.lampY - v.hipY) * s;
        } else { p.lampSX = Float.NaN; p.lampSY = Float.NaN; }
        p.headSX = hx; p.headSY = hy - v.hipY * s;
    }

    private void drawBody(Canvas c, Style st, Pose p, View v, boolean mirror, int alpha, float hx, float hy, float amt, float ph, Game.Host host) {
        Bitmap b = host.bitmap(v.body);
        if (b == null || alpha <= 0) return;
        float s = sc / v.pxPerH;
        float lean = p.lean + (mirror ? 1 : -1) * p.reach * 6f;
        bmp.setAlpha(alpha);
        c.save();
        c.translate(hx, hy);
        c.rotate(lean);
        c.scale((mirror ? -1 : 1) * s * p.squash, s);
        c.drawBitmap(b, -v.hipX, -v.hipY, bmp);
        if (v.arm != null) {
            Bitmap a = host.bitmap(v.arm);
            if (a != null) {
                float swing = st.armAmp * amt * (float) Math.sin(2 * Math.PI * ph) * (1f + 0.3f * p.run);
                float reachAng = -70f * p.reach;
                c.save();
                c.translate(v.pivX - v.hipX, v.pivY - v.hipY);
                c.rotate(v.armRest + swing * (1 - p.reach) + reachAng);
                c.drawBitmap(a, v.armX - v.pivX, v.armY - v.pivY, bmp);
                c.restore();
            }
        }
        c.restore();
        bmp.setAlpha(255);
    }

    /** Two-bone IK, knee bends toward the facing direction. Fills knee[i]. */
    private void ik(int i, Style st) {
        float hx = hip[i * 3], hf = hip[i * 3 + 1], hu = hip[i * 3 + 2];
        float dx = ankle[i * 3] - hx, df = ankle[i * 3 + 1] - hf, du = ankle[i * 3 + 2] - hu;
        float L = (float) Math.sqrt(dx * dx + df * df + du * du);
        float L1 = st.thigh, L2 = st.shin, max = (L1 + L2) * 0.999f;
        if (L > max) { // leg can't reach: pull the ankle up toward the hip
            float k = max / L; dx *= k; df *= k; du *= k; L = max;
            ankle[i * 3] = hx + dx; ankle[i * 3 + 1] = hf + df; ankle[i * 3 + 2] = hu + du;
        }
        float a = (L1 * L1 - L2 * L2 + L * L) / (2 * L);
        float h = (float) Math.sqrt(Math.max(0, L1 * L1 - a * a));
        float ux = dx / L, uf = df / L, uu = du / L;
        // perpendicular toward "forward" (0,1,0) minus its component along the leg
        float pd = uf, px = -pd * ux, pf = 1 - pd * uf, pu = -pd * uu;
        float pl = (float) Math.sqrt(px * px + pf * pf + pu * pu);
        if (pl < 1e-5f) { px = 0; pf = 1; pu = 0; pl = 1; }
        knee[i * 3] = hx + ux * a + px / pl * h;
        knee[i * 3 + 1] = hf + uf * a + pf / pl * h;
        knee[i * 3 + 2] = hu + uu * a + pu / pl * h;
    }

    private void drawLeg(Canvas c, Style st, Pose p, int i) {
        float[] L = leg[i];
        L[0] = p.x + projX(hip[i * 3], hip[i * 3 + 1]);   L[1] = p.y + projY(hip[i * 3], hip[i * 3 + 1], hip[i * 3 + 2]);
        L[2] = p.x + projX(knee[i * 3], knee[i * 3 + 1]); L[3] = p.y + projY(knee[i * 3], knee[i * 3 + 1], knee[i * 3 + 2]);
        L[4] = p.x + projX(ankle[i * 3], ankle[i * 3 + 1]); L[5] = p.y + projY(ankle[i * 3], ankle[i * 3 + 1], ankle[i * 3 + 2]);
        float w = st.legW * sc, ol = Math.max(1.2f, w * 0.13f);
        int a = p.alpha;
        // shoe behind the shin when the foot points away from the camera
        boolean shoeFirst = fy < -0.2f;
        if (shoeFirst) drawShoe(c, st, p, i);
        line.setShader(null);
        line.setColor(withAlpha(st.outline, a));
        line.setStrokeWidth(w + 2 * ol);
        c.drawLine(L[0], L[1], L[2], L[3], line);
        line.setStrokeWidth(w * 0.9f + 2 * ol);
        c.drawLine(L[2], L[3], L[4], L[5], line);
        line.setColor(withAlpha(st.pants, a));
        line.setStrokeWidth(w);
        c.drawLine(L[0], L[1], L[2], L[3], line);
        line.setStrokeWidth(w * 0.9f);
        c.drawLine(L[2], L[3], L[4], L[5], line);
        // soft highlight on the lit side (light from upper left)
        line.setColor(withAlpha(st.pantsHi, Math.round(a * 0.55f)));
        line.setStrokeWidth(w * 0.3f);
        float ox = -w * 0.2f, oy = -w * 0.08f;
        c.drawLine(L[0] + ox, L[1] + oy, L[2] + ox, L[3] + oy, line);
        c.drawLine(L[2] + ox, L[3] + oy, L[4] + ox * 0.9f, L[5] + oy - w * 0.35f, line);
        // cuff
        line.setColor(withAlpha(st.cuff, a));
        line.setStrokeWidth(w * 1.02f);
        float kx = L[2] - L[4], ky = L[3] - L[5], kl = (float) Math.hypot(kx, ky);
        if (kl > 1e-3f) { kx /= kl; ky /= kl; }
        c.drawLine(L[4] + kx * w * 0.35f, L[5] + ky * w * 0.35f, L[4] + kx * w * 0.05f, L[5] + ky * w * 0.05f, line);
        if (!shoeFirst) drawShoe(c, st, p, i);
    }

    // --- shoe: two projected ellipsoids (sole + upper) oriented by heading and pitch ---
    private final float[] m = new float[3];
    private void drawShoe(Canvas c, Style st, Pose p, int i) {
        double pr = Math.toRadians(pitch[i]);
        float cp = (float) Math.cos(pr), sp = (float) Math.sin(pr);
        // foot frame in local (lat, fwd, up)
        float Fl = 0, Ff = cp, Fu = sp;          // along the foot
        float Ul = 0, Uf = -sp, Uu = cp;         // foot "up"
        float ax = ankle[i * 3], af = ankle[i * 3 + 1], au = ankle[i * 3 + 2];
        // centre slightly ahead of and below the ankle
        float cL = ax, cF = af + Ff * st.footL * 0.22f - Uf * st.footT * 0.55f, cU = au + Fu * st.footL * 0.22f - Uu * st.footT * 0.55f;
        int a = p.alpha;
        // sole: longer, flatter, a touch lower
        ellipsoid(c, p, cL, cF - Uf * st.footT * 0.25f, cU - Uu * st.footT * 0.25f, Fl, Ff, Fu, Ul, Uf, Uu,
                st.footL * 0.54f, st.footW * 0.54f, st.footT * 0.34f, withAlpha(st.sole, a), withAlpha(st.outline, a));
        ellipsoid(c, p, cL, cF + Uf * st.footT * 0.12f - Ff * st.footL * 0.03f, cU + Uu * st.footT * 0.12f - Fu * st.footL * 0.03f, Fl, Ff, Fu, Ul, Uf, Uu,
                st.footL * 0.47f, st.footW * 0.5f, st.footT * 0.5f, withAlpha(st.shoe, a), withAlpha(st.outline, a));
        // highlight
        float hx = p.x + projX(cL, cF + Uf * st.footT * 0.3f), hy = p.y + projY(cL, cF + Uf * st.footT * 0.3f, cU + Uu * st.footT * 0.4f);
        fill.setShader(null);
        fill.setColor(withAlpha(st.shoeHi, Math.round(a * 0.7f)));
        float hr = st.footW * sc * 0.16f;
        r.set(hx - hr * 1.4f, hy - hr, hx + hr * 1.4f, hy + hr);
        c.drawOval(r, fill);
    }

    /** Draws the silhouette of an ellipsoid (centre, axis F with semi-length a, lateral axis b, axis U with c). */
    private void ellipsoid(Canvas cv, Pose p, float cl, float cf, float cu, float Fl, float Ff, float Fu, float Ul, float Uf, float Uu,
                           float a, float b, float c, int col, int outline) {
        // projected axis vectors (world px)
        float ax = projX(Fl, Ff), ay = projY(Fl, Ff, Fu);
        float bx = projX(1, 0), by = projY(1, 0, 0);
        float ux = projX(Ul, Uf), uy = projY(Ul, Uf, Uu);
        // covariance of the projected ellipsoid: M = sum a_i^2 v_i v_i^T
        float mxx = a * a * ax * ax + b * b * bx * bx + c * c * ux * ux;
        float mxy = a * a * ax * ay + b * b * bx * by + c * c * ux * uy;
        float myy = a * a * ay * ay + b * b * by * by + c * c * uy * uy;
        float tr = mxx + myy, det = mxx * myy - mxy * mxy;
        float disc = (float) Math.sqrt(Math.max(0, tr * tr / 4 - det));
        float l1 = tr / 2 + disc, l2 = Math.max(0, tr / 2 - disc);
        float ang = (float) Math.toDegrees(Math.atan2(l1 - mxx, mxy));
        if (Math.abs(mxy) < 1e-6f) ang = mxx >= myy ? 0 : 90;
        float r1 = (float) Math.sqrt(l1), r2 = (float) Math.sqrt(l2);
        float x = p.x + projX(cl, cf), y = p.y + projY(cl, cf, cu);
        cv.save();
        cv.translate(x, y);
        cv.rotate(ang);
        float ol = Math.max(1f, sc * 0.009f);
        r.set(-r1 - ol, -r2 - ol, r1 + ol, r2 + ol);
        fill.setShader(null);
        fill.setColor(outline);
        cv.drawOval(r, fill);
        r.set(-r1, -r2, r1, r2);
        fill.setColor(col);
        cv.drawOval(r, fill);
        cv.restore();
    }

    static int withAlpha(int c, int a) { return (c & 0x00FFFFFF) | ((Color.alpha(c) * a / 255) << 24); }
}
