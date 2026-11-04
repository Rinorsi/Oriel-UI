package dev.oriel.engine.paint;

import dev.oriel.engine.layout.Box;
import dev.oriel.engine.layout.Fragment;
import dev.oriel.engine.layout.LineBox;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.Shadow;

/**
 * The most a box can paint: its border box and every descendant's (boxes, lines, fragments), grown by the shadows,
 * outlines and filters that reach past them. Conservative by design (a group is drawn into a layer of this size, so
 * too small would cut content off): a transformed descendant makes it unknown.
 */
final class Extents {
    private Extents() {}

    /** {x0, y0, x1, y1} of what {@code box} at (x, y) can paint, with {@code s}'s own filters; null when unknown. */
    static float[] of(Box box, ComputedStyle s, float x, float y) {
        float[] e = {x, y, x + box.width, y + box.height};
        float[] reach = {0};
        if (!walk(box, x, y, e, reach, true)) return null;
        float grow = reach[0] + 1; // and a pixel for antialiased edges
        e[0] -= grow;
        e[1] -= grow;
        e[2] += grow;
        e[3] += grow;
        return e;
    }

    private static boolean walk(Box box, float x, float y, float[] e, float[] reach, boolean root) {
        ComputedStyle s = StackingOrder.styleOf(box);
        // A transformed descendant can paint anywhere. (Every box, positioned or not, is placed relative to its parent.)
        if (!root && !s.transform.isEmpty()) return false;
        include(e, x, y, x + box.width, y + box.height);
        reach[0] = Math.max(reach[0], reach(s));
        float cx = x - box.scrollLeft(), cy = y - box.scrollTop();
        for (LineBox line : box.lines) {
            include(e, cx + line.x, cy + line.y, cx + line.x + line.width, cy + line.y + line.height);
            for (Fragment f : line.fragments) {
                include(e, cx + f.x(), cy + f.y(), cx + f.x() + f.width(), cy + f.y() + f.height());
                if (f instanceof Fragment.TextRun run) reach[0] = Math.max(reach[0], shadows(StackingOrder.styleOf(run).textShadow));
                else if (f instanceof Fragment.InlineBox ib) reach[0] = Math.max(reach[0], reach(StackingOrder.styleOf(ib.box())));
            }
        }
        for (Box child : box.children) {
            if (!walk(child, cx + child.x, cy + child.y, e, reach, false)) return false;
        }
        return true;
    }

    private static void include(float[] e, float x0, float y0, float x1, float y1) {
        e[0] = Math.min(e[0], x0);
        e[1] = Math.min(e[1], y0);
        e[2] = Math.max(e[2], x1);
        e[3] = Math.max(e[3], y1);
    }

    /** How far past its border box a box's own shadows, outline and filters paint. */
    private static float reach(ComputedStyle s) {
        float r = shadows(s.boxShadow);
        if (StackingOrder.hasOutline(s)) r = Math.max(r, s.outlineWidth + Math.max(0, s.outlineOffset));
        return Math.max(r, filterReach(s));
    }

    private static float shadows(java.util.List<Shadow> shadows) {
        float r = 0;
        for (Shadow sh : shadows) {
            if (sh.inset() || sh.isNative()) continue;
            r = Math.max(r, Math.max(Math.abs(sh.offsetX()), Math.abs(sh.offsetY())) + sh.blur() + Math.max(0, sh.spread()));
        }
        return r;
    }

    /** How far {@code s}'s filters spread what they are given: blurs 3 sigma, drop shadows their offset and blur. */
    private static float filterReach(ComputedStyle s) {
        float r = 0;
        for (Filter f : s.filter) {
            if (f.kind() == Filter.Kind.BLUR) r += 3 * f.amount();
            else if (f.kind() == Filter.Kind.DROP_SHADOW) r += Math.max(Math.abs(f.x()), Math.abs(f.y())) + 1.5f * f.amount();
        }
        return r;
    }
}
