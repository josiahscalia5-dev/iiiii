package com.treasurerun.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Room geometry helpers on the walk mask: walkability, floor-space distances (perspective-corrected), line of sight
 * and A* paths. World coords are room-image pixels; "floor units" are pixels at depth 1 (y = room.yRef).
 */
final class Nav {
    final Room room;
    final byte[] walk;       // 1 = walkable, at 1/walkDiv resolution
    final int ww, wh;
    // coarse grid for A*: one node per CD x CD walk cells, walkable when its centre can hold a small actor
    static final int CD = 3;
    final int cw, ch;
    final boolean[] node;

    private final float[] sxT, syT;          // per-row perspective scales (avoids Math.pow in the ray loops)

    Nav(Room room, byte[] walk, int ww, int wh) {
        this.room = room; this.walk = walk; this.ww = ww; this.wh = wh;
        sxT = new float[room.h + 2]; syT = new float[room.h + 2];
        for (int y = 0; y < sxT.length; y++) {
            float d = Math.max(0.05f, room.depth(y));
            sxT[y] = d; syT[y] = room.vFactor * (float) Math.pow(d, room.vPow);
        }
        cw = ww / CD; ch = wh / CD;
        node = new boolean[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            float wx = (x * CD + CD * 0.5f) * room.walkDiv, wy = (y * CD + CD * 0.5f) * room.walkDiv;
            node[x + y * cw] = canStand(wx, wy, room.feetR);
        }
    }

    boolean walkable(float x, float y) {
        int i = (int) (x / room.walkDiv), j = (int) (y / room.walkDiv);
        return i >= 0 && j >= 0 && i < ww && j < wh && walk[i + j * ww] != 0;
    }

    /** Footprint check, radius r (floor units) scaled by depth like the v0.4 collision. */
    boolean canStand(float x, float y, float r) {
        float m = r * Math.max(0.15f, room.depth(y));
        return walkable(x, y) && walkable(x - m, y) && walkable(x + m, y) && walkable(x, y - m * 0.6f) && walkable(x, y + m * 0.6f);
    }

    // ---- perspective ----
    /** World px per floor unit horizontally / vertically at y. */
    float sx(float y) { int i = (int) y; return i >= 0 && i < sxT.length ? sxT[i] : Math.max(0.05f, room.depth(y)); }
    float sy(float y) { int i = (int) y; return i >= 0 && i < syT.length ? syT[i] : room.vFactor * (float) Math.pow(Math.max(0.05f, room.depth(y)), room.vPow); }
    /** Floor-space vertical distance between two world ys (exact for the room's perspective model). */
    float floorDZ(float y0, float y1) {
        float h = room.horizon, R = room.yRef - h;
        float a = Math.max(1f, y0 - h), b = Math.max(1f, y1 - h);
        if (room.vPow > 1.5f) return R * R / room.vFactor * (1f / a - 1f / b);           // + when y1 > y0
        return R / room.vFactor * (float) Math.log(b / a);
    }
    float floorDist(float x0, float y0, float x1, float y1) {
        float dx = (x1 - x0) / sx((y0 + y1) * 0.5f);
        return (float) Math.hypot(dx, floorDZ(y0, y1));
    }
    /** Floor-space direction angle (deg, 0 = right, 90 = toward camera) from a to b. */
    float floorAngle(float x0, float y0, float x1, float y1) {
        return (float) Math.toDegrees(Math.atan2(floorDZ(y0, y1), (x1 - x0) / sx((y0 + y1) * 0.5f)));
    }
    /** Converts a screen/world direction into the floor direction that moves along it on screen. */
    float screenToFloorAngle(float dx, float dy, float y) {
        return (float) Math.toDegrees(Math.atan2(dy / sy(y), dx / sx(y)));
    }

    // ---- sight ----
    /** True when nothing unwalkable (walls, cases, plants...) lies between the two points. */
    boolean los(float x0, float y0, float x1, float y1) {
        float d = (float) Math.hypot(x1 - x0, y1 - y0);
        float step = room.walkDiv * 0.9f;
        int n = Math.max(1, (int) (d / step));
        for (int k = 1; k < n; k++) {
            float t = k / (float) n;
            if (!walkable(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)) return false;
        }
        return true;
    }
    /** Walks a floor-space ray from (x,y) at angle ang for up to range floor units; returns the world end point. */
    void ray(float x, float y, float ang, float range, float[] out) {
        double a = Math.toRadians(ang);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float px = x, py = y, t = 0;
        while (t < range) {
            // one walk-mask cell per step, whatever the depth
            float stepF = room.walkDiv / Math.max(sx(py) * Math.abs(ca) + sy(py) * Math.abs(sa), 0.05f);
            float s = Math.min(stepF, range - t);
            float nx = px + ca * s * sx(py), ny = py + sa * s * sy(py);
            if (!walkable(nx, ny)) {
                // refine the hit a little
                for (int k = 0; k < 3; k++) { s *= 0.5f; float mx = px + ca * s * sx(py), my = py + sa * s * sy(py); if (walkable(mx, my)) { px = mx; py = my; } }
                break;
            }
            px = nx; py = ny; t += s;
        }
        out[0] = px; out[1] = py;
    }

    // ---- paths ----
    private int[] gScore, came; private float[] fScore; private int[] heap; private int heapN; private boolean[] closed;

    private int nearestNode(float x, float y) {
        int cx = (int) (x / room.walkDiv / CD), cy = (int) (y / room.walkDiv / CD);
        for (int r = 0; r < 12; r++) {
            int best = -1; float bd = Float.MAX_VALUE;
            for (int j = cy - r; j <= cy + r; j++) for (int i = cx - r; i <= cx + r; i++) {
                if (i < 0 || j < 0 || i >= cw || j >= ch || !node[i + j * cw]) continue;
                if (Math.max(Math.abs(i - cx), Math.abs(j - cy)) != r) continue;
                float dd = (i - cx) * (i - cx) + (j - cy) * (j - cy);
                if (dd < bd) { bd = dd; best = i + j * cw; }
            }
            if (best >= 0) return best;
        }
        return -1;
    }
    float nodeX(int n) { return ((n % cw) * CD + CD * 0.5f) * room.walkDiv; }
    float nodeY(int n) { return ((n / cw) * CD + CD * 0.5f) * room.walkDiv; }

    /** A* from a to b; returns world waypoints (excluding the start), smoothed with line-of-sight, or null. */
    List<float[]> path(float ax, float ay, float bx, float by) {
        int s = nearestNode(ax, ay), g = nearestNode(bx, by);
        if (s < 0 || g < 0) return null;
        int n = cw * ch;
        if (gScore == null) { gScore = new int[n]; came = new int[n]; fScore = new float[n]; heap = new int[n * 2]; closed = new boolean[n]; }
        Arrays.fill(gScore, Integer.MAX_VALUE); Arrays.fill(closed, false);
        heapN = 0;
        gScore[s] = 0; came[s] = -1; fScore[s] = h(s, g); push(s);
        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1}, dy = {0, 0, 1, -1, 1, -1, 1, -1};
        int[] cost = {10, 10, 10, 10, 14, 14, 14, 14};
        boolean found = false; int iter = 0;
        while (heapN > 0 && iter++ < 60000) {
            int cur = pop();
            if (cur == g) { found = true; break; }
            if (closed[cur]) continue;
            closed[cur] = true;
            int cx = cur % cw, cy = cur / cw;
            for (int k = 0; k < 8; k++) {
                int nx = cx + dx[k], ny = cy + dy[k];
                if (nx < 0 || ny < 0 || nx >= cw || ny >= ch) continue;
                int nb = nx + ny * cw;
                if (!node[nb] || closed[nb]) continue;
                if (k >= 4 && (!node[nx + cy * cw] || !node[cx + ny * cw])) continue;   // no corner cutting
                int t = gScore[cur] + cost[k];
                if (t < gScore[nb]) { gScore[nb] = t; came[nb] = cur; fScore[nb] = t + h(nb, g); push(nb); }
            }
        }
        if (!found) return null;
        ArrayList<float[]> raw = new ArrayList<>();
        for (int c = g; c != -1; c = came[c]) raw.add(0, new float[]{nodeX(c), nodeY(c)});
        raw.set(raw.size() - 1, new float[]{bx, by});
        // string pulling
        ArrayList<float[]> out = new ArrayList<>();
        float px = ax, py = ay; int i = 0;
        while (i < raw.size()) {
            int j = raw.size() - 1;
            while (j > i && !clear(px, py, raw.get(j)[0], raw.get(j)[1])) j--;
            out.add(raw.get(j)); px = raw.get(j)[0]; py = raw.get(j)[1]; i = j + 1;
        }
        return out;
    }
    /** Line clear for a walking actor (samples the footprint along the segment). */
    boolean clear(float x0, float y0, float x1, float y1) {
        float d = (float) Math.hypot(x1 - x0, y1 - y0);
        int n = Math.max(1, (int) (d / (room.walkDiv * 1.5f)));
        for (int k = 1; k <= n; k++) {
            float t = k / (float) n;
            if (!canStand(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, room.feetR)) return false;
        }
        return true;
    }
    private float h(int a, int b) {
        int ax = a % cw, ay = a / cw, bx = b % cw, by = b / cw;
        int dx = Math.abs(ax - bx), dy = Math.abs(ay - by);
        return 10 * Math.max(dx, dy) + 4 * Math.min(dx, dy);
    }
    private void push(int v) {
        if (heapN == heap.length) heap = Arrays.copyOf(heap, heap.length * 2);
        int i = heapN++; heap[i] = v;
        while (i > 0) { int p = (i - 1) / 2; if (fScore[heap[p]] <= fScore[heap[i]]) break; int t = heap[p]; heap[p] = heap[i]; heap[i] = t; i = p; }
    }
    private int pop() {
        int top = heap[0]; heap[0] = heap[--heapN];
        int i = 0;
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            if (l < heapN && fScore[heap[l]] < fScore[heap[m]]) m = l;
            if (r < heapN && fScore[heap[r]] < fScore[heap[m]]) m = r;
            if (m == i) break;
            int t = heap[m]; heap[m] = heap[i]; heap[i] = t; i = m;
        }
        return top;
    }
}
