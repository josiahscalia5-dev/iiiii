package com.treasurerun.game;

import android.graphics.Bitmap;

/** Walk mask of a room (1/walkDiv resolution): collision, line of sight, vision rays and A* paths. */
final class Nav {
    final Room room;
    final int w, h, div;
    final byte[] walk;
    /** chamfer distance (in cells) from each walkable cell to the nearest blocked one */
    final short[] clear;
    // A* scratch
    private final int[] g, from, heap, stamp;
    private int heapN, curStamp;

    Nav(Room room, Bitmap mask) {
        this.room = room;
        div = room.walkDiv;
        w = mask.getWidth();
        h = mask.getHeight();
        int n = w * h;
        int[] px = new int[n];
        mask.getPixels(px, 0, w, 0, 0, w, h);
        walk = new byte[n];
        for (int i = 0; i < n; i++) walk[i] = (byte) (((px[i] >> 16) & 255) > 127 ? 1 : 0);
        for (int[] b : room.blocks) block(b[0], b[1], b[2], b[3], false);
        clear = new short[n];
        g = new int[n];
        from = new int[n];
        heap = new int[n * 2];
        key = new int[n * 2];
        stamp = new int[n];
        computeClearance();
    }

    /** world-space rectangle, made unwalkable (or walkable again) */
    void block(int x0, int y0, int x1, int y1, boolean open) {
        for (int y = Math.max(0, y0 / div); y < Math.min(h, y1 / div); y++)
            for (int x = Math.max(0, x0 / div); x < Math.min(w, x1 / div); x++) walk[x + y * w] = (byte) (open ? 1 : 0);
    }

    void computeClearance() {
        final int INF = 30000;
        for (int i = 0; i < w * h; i++) clear[i] = (short) (walk[i] != 0 ? INF : 0);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = x + y * w;
                if (clear[i] == 0) continue;
                int v = clear[i];
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) v = 1;
                if (x > 0) v = Math.min(v, clear[i - 1] + 2);
                if (y > 0) v = Math.min(v, clear[i - w] + 2);
                if (x > 0 && y > 0) v = Math.min(v, clear[i - w - 1] + 3);
                if (x < w - 1 && y > 0) v = Math.min(v, clear[i - w + 1] + 3);
                clear[i] = (short) v;
            }
        for (int y = h - 1; y >= 0; y--)
            for (int x = w - 1; x >= 0; x--) {
                int i = x + y * w;
                if (clear[i] == 0) continue;
                int v = clear[i];
                if (x < w - 1) v = Math.min(v, clear[i + 1] + 2);
                if (y < h - 1) v = Math.min(v, clear[i + w] + 2);
                if (x < w - 1 && y < h - 1) v = Math.min(v, clear[i + w + 1] + 3);
                if (x > 0 && y < h - 1) v = Math.min(v, clear[i + w - 1] + 3);
                clear[i] = (short) v;
            }
    }

    boolean walkable(float x, float y) {
        int cx = (int) (x / div), cy = (int) (y / div);
        return cx >= 0 && cy >= 0 && cx < w && cy < h && walk[cx + cy * w] != 0;
    }

    /** feet footprint (an ellipse in floor space) fits on walkable cells */
    boolean canStand(float x, float y, float feetR) {
        float r = feetR * Math.max(0.15f, room.depth(y));
        return walkable(x, y) && walkable(x - r, y) && walkable(x + r, y) && walkable(x, y - r * 0.6f) && walkable(x, y + r * 0.6f);
    }

    /** clearance at a world point in world px (half the chamfer units, times the cell size) */
    float clearance(float x, float y) {
        int cx = (int) (x / div), cy = (int) (y / div);
        if (cx < 0 || cy < 0 || cx >= w || cy >= h) return 0;
        return clear[cx + cy * w] * 0.5f * div;
    }

    /** nearest walkable point with some clearance, searching outward (for taps on obstacles) */
    boolean nearestStandable(float x, float y, float feetR, float[] out) {
        for (int r = 0; r <= 60; r++) {
            float best = Float.MAX_VALUE;
            for (int k = 0; k < Math.max(1, r * 8); k++) {
                double a = k * Math.PI * 2 / Math.max(1, r * 8);
                float px = x + (float) Math.cos(a) * r * div * 1.5f, py = y + (float) Math.sin(a) * r * div * 1.5f;
                if (canStand(px, py, feetR * 1.1f)) {
                    float d = (px - x) * (px - x) + (py - y) * (py - y);
                    if (d < best) { best = d; out[0] = px; out[1] = py; }
                }
            }
            if (best < Float.MAX_VALUE) return true;
        }
        return false;
    }

    /** true when the straight segment crosses only walkable cells (vision is blocked by anything you can't walk on) */
    boolean lineOfSight(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = (int) (Math.max(Math.abs(dx), Math.abs(dy)) / div) + 1;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            if (!walkable(x0 + dx * t, y0 + dy * t)) return false;
        }
        return true;
    }

    /** segment keeps at least `need` world px of clearance (for walking straight between path points) */
    boolean clearLine(float x0, float y0, float x1, float y1, float need) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = (int) (Math.max(Math.abs(dx), Math.abs(dy)) / div) + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x0 + dx * t, y = y0 + dy * t;
            if (clearance(x, y) < need * Math.max(0.15f, room.depth(y))) return false;
        }
        return true;
    }

    /**
     * Cast a ray along a floor-space direction (fx, fy) for `range` floor px; returns the travelled fraction (0..1).
     * Floor space: 1 floor px = depth world px horizontally, vFactor*depth^vPow vertically.
     */
    float ray(float x, float y, float fx, float fy, float range) {
        float step = div * 0.8f, travelled = 0;
        while (travelled < range) {
            float d = Math.max(0.1f, room.depth(y));
            float sx = fx * d, sy = fy * room.vFactor * (float) Math.pow(d, room.vPow);
            float len = (float) Math.hypot(sx, sy);
            if (len < 1e-4f) return 1;
            float k = step / len;
            x += sx * k;
            y += sy * k;
            travelled += step / len;
            if (!walkable(x, y)) return Math.min(1, travelled / range);
        }
        return 1;
    }

    /** floor-space distance between two world points (perspective-corrected, in depth-1 world px) */
    float floorDist(float x0, float y0, float x1, float y1) {
        float d = Math.max(0.1f, room.depth((y0 + y1) * 0.5f));
        float fx = (x1 - x0) / d, fy = (y1 - y0) / (room.vFactor * (float) Math.pow(d, room.vPow));
        return (float) Math.hypot(fx, fy);
    }

    /**
     * A* over walk cells from world (x0,y0) to (x1,y1), keeping `feetR`-scaled clearance, 8-connected.
     * Writes simplified waypoints (world coords, excluding the start) into out[2*i..]; returns the count (0 = no path).
     */
    int path(float x0, float y0, float x1, float y1, float feetR, float[] out, int maxPts) {
        int sx = clampX((int) (x0 / div)), sy = clampY((int) (y0 / div));
        int tx = clampX((int) (x1 / div)), ty = clampY((int) (y1 / div));
        int start = sx + sy * w, goal = tx + ty * w;
        if (walk[goal] == 0) return 0;
        curStamp++;
        if (curStamp == Integer.MAX_VALUE) { java.util.Arrays.fill(stamp, 0); curStamp = 1; }
        heapN = 0;
        g[start] = 0;
        from[start] = -1;
        stamp[start] = curStamp;
        push(start, heur(sx, sy, tx, ty));
        int found = -1, expanded = 0;
        while (heapN > 0 && expanded < 400000) {
            int f = key[0];
            int cur = pop();
            if (cur == goal) { found = cur; break; }
            int cx = cur % w, cy = cur / w;
            if (f > g[cur] + heur(cx, cy, tx, ty)) continue;   // stale duplicate entry
            expanded++;
            int gc = g[cur];
            for (int k = 0; k < 8; k++) {
                int nx = cx + DX[k], ny = cy + DY[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int ni = nx + ny * w;
                if (walk[ni] == 0) continue;
                // clearance (chamfer units: 2 per cell) for the agent's feet radius at that depth, relaxed near start/goal
                float need = 2 * 0.9f * feetR * Math.max(0.15f, room.depth(ny * div)) / div;
                boolean nearEnds = Math.abs(nx - tx) + Math.abs(ny - ty) < 6 || Math.abs(nx - sx) + Math.abs(ny - sy) < 6;
                if (!nearEnds && clear[ni] < need) continue;
                if (k >= 4 && (walk[cx + DX[k] + cy * w] == 0 || walk[cx + (cy + DY[k]) * w] == 0)) continue;
                int ng = gc + (k < 4 ? 10 : 14) + (clear[ni] < need * 2 ? 6 : 0);
                if (stamp[ni] != curStamp || ng < g[ni]) {
                    stamp[ni] = curStamp;
                    g[ni] = ng;
                    from[ni] = cur;
                    push(ni, ng + heur(nx, ny, tx, ty));
                }
            }
        }
        if (found < 0) return 0;
        // walk back
        int len = 0;
        for (int c = found; c != -1; c = from[c]) len++;
        int[] cells = new int[len];
        int i = len;
        for (int c = found; c != -1; c = from[c]) cells[--i] = c;
        // string-pull: from each anchor, jump to the farthest cell still reachable in a clear straight line
        int n = 0;
        int a = 0;
        float ax = x0, ay = y0;
        float need = feetR * 0.8f;
        while (a < len - 1 && n < maxPts) {
            int best = a + 1;
            for (int j = len - 1; j > a + 1; j -= Math.max(1, (j - a) / 12)) {
                float jx = (cells[j] % w + 0.5f) * div, jy = (cells[j] / w + 0.5f) * div;
                if (clearLine(ax, ay, jx, jy, need)) { best = j; break; }
            }
            // refine forward one cell at a time from the coarse pick
            while (best + 1 < len) {
                float jx = (cells[best + 1] % w + 0.5f) * div, jy = (cells[best + 1] / w + 0.5f) * div;
                if (!clearLine(ax, ay, jx, jy, need)) break;
                best++;
            }
            a = best;
            ax = (cells[a] % w + 0.5f) * div;
            ay = (cells[a] / w + 0.5f) * div;
            out[n * 2] = ax;
            out[n * 2 + 1] = ay;
            n++;
        }
        if (n > 0) { out[(n - 1) * 2] = x1; out[(n - 1) * 2 + 1] = y1; }
        return n;
    }

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};

    private int clampX(int x) { return x < 0 ? 0 : x >= w ? w - 1 : x; }
    private int clampY(int y) { return y < 0 ? 0 : y >= h ? h - 1 : y; }

    private static int heur(int x, int y, int tx, int ty) {
        int dx = Math.abs(x - tx), dy = Math.abs(y - ty);
        return 10 * Math.max(dx, dy) + 4 * Math.min(dx, dy);
    }

    // binary min-heap of cell ids keyed by f = g + h (lazy: improved cells are pushed again)
    private final int[] key;

    private void push(int cell, int f) {
        if (heapN >= heap.length) return;
        int i = heapN++;
        heap[i] = cell;
        key[i] = f;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (key[p] <= key[i]) break;
            swap(i, p);
            i = p;
        }
    }

    private int pop() {
        int top = heap[0];
        heapN--;
        heap[0] = heap[heapN];
        key[0] = key[heapN];
        int i = 0;
        while (true) {
            int l = i * 2 + 1, r = l + 1, m = i;
            if (l < heapN && key[l] < key[m]) m = l;
            if (r < heapN && key[r] < key[m]) m = r;
            if (m == i) break;
            swap(i, m);
            i = m;
        }
        return top;
    }

    private void swap(int a, int b) {
        int t = heap[a]; heap[a] = heap[b]; heap[b] = t;
        t = key[a]; key[a] = key[b]; key[b] = t;
    }
}
