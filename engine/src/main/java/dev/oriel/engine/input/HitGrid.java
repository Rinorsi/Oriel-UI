package dev.oriel.engine.input;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.layout.Box;
import dev.oriel.engine.paint.HitResult;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Display;
import dev.oriel.engine.style.PointerEvents;
import dev.oriel.engine.style.Position;
import dev.oriel.engine.style.Visibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conservative broad phase for large plain block trees. Complex painting stays on the shared painter traversal.
 * Eligibility is proved for the whole tree; overflow is represented by each child's own rectangle, never a parent
 * containment assumption. Every frame and geometry invalidation discards the proof.
 */
final class HitGrid {
    private static final int CELL = 64, MAX_REFS = 32768, MIN_BOXES = 128;
    private record Entry(Box box, float x, float y) {}
    private final Document document;
    private final Map<Long, List<Entry>> cells = new HashMap<>();
    private long epoch = Long.MIN_VALUE;
    private Box root;
    private boolean usable;

    HitGrid(Document document) { this.document = document; }
    boolean indexed() { return usable; }

    HitResult hit(float x, float y) {
        Box current = document.layoutEngine().root();
        if (root != current || epoch != document.inputGeometryVersion()) rebuild(current);
        if (!usable || !Float.isFinite(x) || !Float.isFinite(y)) return document.painter().hitTest(x, y);
        List<Entry> candidates = cells.get(key(cell(x), cell(y)));
        if (candidates != null) for (int i = candidates.size() - 1; i >= 0; i--) {
            Entry e = candidates.get(i);
            if (x >= e.x && y >= e.y && x < e.x + e.box.width && y < e.y + e.box.height)
                return new HitResult(e.box.element, e.box, x - e.x, y - e.y);
        }
        return null;
    }

    private void rebuild(Box current) {
        root = current;
        epoch = document.inputGeometryVersion();
        cells.clear();
        usable = false;
        if (root == null) return;
        List<Entry> entries = new ArrayList<>();
        if (!collect(root, 0, 0, entries) || entries.size() < MIN_BOXES) return;
        int references = 0;
        for (Entry e : entries) {
            if (e.box.width <= 0 || e.box.height <= 0) continue;
            int left = cell(e.x), top = cell(e.y);
            int right = cell(e.x + e.box.width), bottom = cell(e.y + e.box.height);
            long count = ((long) right - left + 1) * ((long) bottom - top + 1);
            if (count <= 0 || count > MAX_REFS - references) { cells.clear(); return; }
            references += (int) count;
            for (int row = top; row <= bottom; row++) for (int col = left; col <= right; col++)
                cells.computeIfAbsent(key(col, row), k -> new ArrayList<>()).add(e);
        }
        usable = true;
    }

    private static boolean collect(Box box, float parentX, float parentY, List<Entry> entries) {
        ComputedStyle style = box.element == null ? box.style : box.element.style;
        if (box.kind != Box.Kind.BLOCK || style == null || box.atomicInline || !box.lines.isEmpty()
                || style.display != Display.BLOCK || style.position != Position.STATIC || style.opacity != 1
                || style.hasTransform() || style.hasGroupEffects() || style.hasRadius() || box.isScrollContainer()
                || style.overflowX.clips() || style.overflowY.clips()
                || box.hostOffsetX != 0 || box.hostOffsetY != 0 || !style.zIndexAuto) return false;
        float x = parentX + box.x, y = parentY + box.y;
        if (!Float.isFinite(x + box.width) || !Float.isFinite(y + box.height)
                || Math.abs(x) + box.width > 1_000_000 || Math.abs(y) + box.height > 1_000_000) return false;
        if (box.element != null && style.pointerEvents != PointerEvents.NONE && style.visibility == Visibility.VISIBLE)
            entries.add(new Entry(box, x, y));
        for (Box child : box.children) if (!collect(child, x, y, entries)) return false;
        return true;
    }

    private static int cell(float value) { return (int) Math.floor(value / CELL); }
    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
}
