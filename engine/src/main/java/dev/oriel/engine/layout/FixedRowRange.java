package dev.oriel.engine.layout;

/** Pure geometry for a fixed-height list. This does not create, recycle or hide DOM nodes. */
public record FixedRowRange(int from, int to, double leading, double trailing) {
    /** Computes an exclusive range and spacer heights, with symmetric overscan measured in rows. */
    public static FixedRowRange calculate(int count, double rowHeight, double scrollTop, double viewportHeight, int overscan) {
        if (count < 0 || !Double.isFinite(rowHeight) || rowHeight <= 0 || !Double.isFinite(scrollTop)
                || !Double.isFinite(viewportHeight) || viewportHeight < 0 || overscan < 0
                || !Double.isFinite(count * rowHeight)) {
            throw new IllegalArgumentException("Expected nonnegative count/viewport/overscan and finite positive rowHeight and finite scrollTop");
        }
        double total = count * rowHeight;
        if (viewportHeight == 0 || count == 0) return new FixedRowRange(0, 0, 0, total);
        double top = Math.clamp(scrollTop, 0, Math.max(0, total - viewportHeight));
        int from = (int) Math.max(0, Math.floor(top / rowHeight) - overscan);
        int to = (int) Math.min(count, Math.ceil(Math.min(total, top + viewportHeight) / rowHeight) + overscan);
        return new FixedRowRange(from, to, from * rowHeight, (count - to) * rowHeight);
    }
}
