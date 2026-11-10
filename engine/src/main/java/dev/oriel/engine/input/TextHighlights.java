package dev.oriel.engine.input;

import dev.oriel.engine.dom.Element;
import java.util.List;

/** Paint-only UTF-16 colour spans on the existing native text model. No value/selection/history changes. */
public final class TextHighlights {
    public record Range(int start, int end, int color) {}
    record State(String source, List<Range> ranges, boolean lineNumbers) {}
    private TextHighlights() {}

    /** Returns false for a stale source. Invalid spans throw before changing the current decoration. */
    public static boolean set(Element element, String source, List<Range> ranges, boolean lineNumbers) {
        if (!element.tagName().equals("textarea")) throw new IllegalArgumentException("Text highlights require textarea");
        if (ranges.size() > 100_000) throw new IllegalArgumentException("Too many text highlight ranges");
        int end = 0;
        for (Range r : ranges) {
            if (r.start() < end || r.start() >= r.end() || r.end() > source.length()
                    || split(source, r.start()) || split(source, r.end())) {
                throw new IllegalArgumentException("Invalid, overlapping or surrogate-splitting text highlight range");
            }
            end = r.end();
        }
        if (!element.value().equals(source)) return false;
        TextField field = TextField.of(element);
        boolean resized = field.highlights == null ? lineNumbers : field.highlights.lineNumbers() != lineNumbers;
        field.highlights = new State(source, List.copyOf(ranges), lineNumbers);
        if (resized) element.ownerDocument().invalidateLayout();
        else element.ownerDocument().invalidatePaint();
        return true;
    }

    private static boolean split(String s, int i) {
        return i > 0 && i < s.length() && Character.isHighSurrogate(s.charAt(i - 1))
                && Character.isLowSurrogate(s.charAt(i));
    }
}
