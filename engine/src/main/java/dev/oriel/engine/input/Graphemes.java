package dev.oriel.engine.input;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cached extended grapheme boundaries; offsets remain UTF-16 indices. */
final class Graphemes {
    private static final Pattern CLUSTER = Pattern.compile("\\X");
    private int[] boundaries;
    private String text;

    private void use(String value) {
        if (text != value) {
            text = value;
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
        int found = Arrays.binarySearch(boundaries, at);
        return boundaries[found >= 0 ? found : -found - 2];
    }

    int previous(String value, int offset) {
        use(value);
        int found = Arrays.binarySearch(boundaries, Math.clamp(offset, 0, value.length()));
        return boundaries[Math.max(0, found >= 0 ? found - 1 : -found - 2)];
    }

    int next(String value, int offset) {
        use(value);
        int found = Arrays.binarySearch(boundaries, Math.clamp(offset, 0, value.length()));
        return boundaries[Math.min(boundaries.length - 1, found >= 0 ? found + 1 : -found - 1)];
    }
}
