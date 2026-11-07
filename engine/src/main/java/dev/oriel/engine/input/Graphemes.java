package dev.oriel.engine.input;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cached extended grapheme boundaries; offsets remain UTF-16 indices. */
final class Graphemes {
    private static final Pattern CLUSTER = Pattern.compile("\\X");
    private int[] boundaries;
    private String text;
    private final InputBackend backend = InputBackend.configured();
    private boolean simple;

    private void use(String value) {
        if (text != value) {
            text = value;
            simple = backend != InputBackend.OLD;
            for (int i = 0; simple && i < value.length(); i++) {
                char c = value.charAt(i);
                if (c > 127 || c == '\r') simple = false;
            }
            if (simple && backend == InputBackend.NEW) { boundaries = null; return; }
            int[] offsets = new int[value.length() + 1];
            int count = 1;
            Matcher matcher = CLUSTER.matcher(value);
            while (matcher.find()) offsets[count++] = matcher.end();
            boundaries = Arrays.copyOf(offsets, count);
        }
    }

    int floor(String value, int offset) {
        use(value);
        int at = Math.clamp(offset, 0, value.length());
        if (boundaries == null) return at;
        int found = Arrays.binarySearch(boundaries, at);
        int result = boundaries[found >= 0 ? found : -found - 2];
        if (simple && backend == InputBackend.BOTH && result != at) throw new IllegalStateException("grapheme floor differs");
        return result;
    }

    int previous(String value, int offset) {
        use(value);
        if (boundaries == null) return Math.max(0, Math.clamp(offset, 0, value.length()) - 1);
        int found = Arrays.binarySearch(boundaries, Math.clamp(offset, 0, value.length()));
        int result = boundaries[Math.max(0, found >= 0 ? found - 1 : -found - 2)];
        if (simple && backend == InputBackend.BOTH && result != Math.max(0, Math.clamp(offset, 0, value.length()) - 1))
            throw new IllegalStateException("grapheme previous differs at offset=" + offset);
        return result;
    }

    int next(String value, int offset) {
        use(value);
        if (boundaries == null) return Math.min(value.length(), Math.clamp(offset, 0, value.length()) + 1);
        int found = Arrays.binarySearch(boundaries, Math.clamp(offset, 0, value.length()));
        int result = boundaries[Math.min(boundaries.length - 1, found >= 0 ? found + 1 : -found - 1)];
        if (simple && backend == InputBackend.BOTH && result != Math.min(value.length(), Math.clamp(offset, 0, value.length()) + 1))
            throw new IllegalStateException("grapheme next differs at offset=" + offset);
        return result;
    }
}
