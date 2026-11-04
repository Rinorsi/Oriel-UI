package dev.oriel.engine.paint;

/**
 * Fills simple polygons (a clip-path's outline, say) as triangles, by ear clipping. Self-intersecting polygons come
 * out as their ear decomposition, which is the non-zero fill for most shapes people draw by hand.
 */
final class Polygons {
    private Polygons() {}

    /** Fills the polygon {@code xy} (x, y alternating) with one colour. */
    static void fill(Canvas canvas, float[] xy, int argb) {
        int n = xy.length / 2;
        if (n < 3) return;
        // Clip ears from a list wound like the canvas's quads (negative area in GUI space), where convex is cross < 0.
        java.util.List<Integer> v = new java.util.ArrayList<>(n);
        boolean flip = area(xy) > 0;
        for (int i = 0; i < n; i++) v.add(flip ? n - 1 - i : i);
        float[] quads = new float[(n - 2) * 8];
        int[] colors = new int[(n - 2) * 4];
        java.util.Arrays.fill(colors, argb);
        int count = 0, i = 0, guard = 2 * n * n;
        while (v.size() > 3 && guard-- > 0) {
            int size = v.size(), at = i % size;
            int a = v.get((at + size - 1) % size), b = v.get(at), c = v.get((at + 1) % size);
            if (isEar(xy, v, a, b, c)) {
                put(quads, count++, xy, a, b, c);
                v.remove(at);
            } else {
                i++;
            }
        }
        if (v.size() == 3) put(quads, count++, xy, v.get(0), v.get(1), v.get(2));
        canvas.fillQuads(quads, colors, count);
    }

    /** Signed area (shoelace); negative for the winding the canvas expects. */
    static float area(float[] xy) {
        float s = 0;
        int n = xy.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            s += xy[2 * i] * xy[2 * j + 1] - xy[2 * j] * xy[2 * i + 1];
        }
        return s / 2;
    }

    private static boolean isEar(float[] xy, java.util.List<Integer> idx, int a, int b, int c) {
        float ax = xy[2 * a], ay = xy[2 * a + 1], bx = xy[2 * b], by = xy[2 * b + 1], cx = xy[2 * c], cy = xy[2 * c + 1];
        float cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (cross >= 0) return false; // reflex (or flat) for the negative winding
        for (int p : idx) {
            if (p == a || p == b || p == c) continue;
            if (inside(xy[2 * p], xy[2 * p + 1], ax, ay, bx, by, cx, cy)) return false;
        }
        return true;
    }

    private static boolean inside(float px, float py, float ax, float ay, float bx, float by, float cx, float cy) {
        float d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        float d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        float d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    /** A triangle as a quad with its last vertex repeated. */
    private static void put(float[] quads, int q, float[] xy, int a, int b, int c) {
        int o = q * 8;
        quads[o] = xy[2 * a];
        quads[o + 1] = xy[2 * a + 1];
        quads[o + 2] = xy[2 * b];
        quads[o + 3] = xy[2 * b + 1];
        quads[o + 4] = xy[2 * c];
        quads[o + 5] = xy[2 * c + 1];
        quads[o + 6] = xy[2 * c];
        quads[o + 7] = xy[2 * c + 1];
    }
}
