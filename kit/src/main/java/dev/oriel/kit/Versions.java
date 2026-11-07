package dev.oriel.kit;

import java.util.ArrayList;
import java.util.List;

/**
 * Version ranges for templates ({@code "requires": {"oriel": ">=0.11.0", "minecraft": ">=26.1"}}): comparators
 * ({@code >=  >  <=  <  =}) separated by spaces or commas, all of which must hold. A version or range that is missing
 * or cannot be read counts as satisfied, so a preview or a test host with no versions never locks anything.
 */
public final class Versions {
    private Versions() {}

    public static boolean satisfies(String version, String range) {
        if (version == null || version.isBlank() || range == null || range.isBlank()) return true;
        int[] have = parse(version);
        if (have == null) return true;
        for (String part : range.trim().split("[\\s,]+")) {
            if (part.isEmpty()) continue;
            String op = part.startsWith(">=") ? ">=" : part.startsWith("<=") ? "<=" : part.startsWith(">") ? ">"
                    : part.startsWith("<") ? "<" : part.startsWith("=") ? "=" : "=";
            int[] want = parse(part.substring(part.startsWith(op) ? op.length() : 0));
            if (want == null) continue;
            int c = compare(have, want);
            boolean ok = switch (op) {
                case ">=" -> c >= 0;
                case ">" -> c > 0;
                case "<=" -> c <= 0;
                case "<" -> c < 0;
                default -> c == 0;
            };
            if (!ok) return false;
        }
        return true;
    }

    /**
     * A range as a player reads it: {@code >=0.11.0} becomes {@code 0.11+} and {@code >=26.1.2} becomes {@code 26.1.2+}
     * (trailing zero parts dropped, two parts kept); any other range is returned as written.
     */
    public static String describe(String range) {
        if (range == null) return "";
        String r = range.trim();
        if (!r.startsWith(">=") || r.substring(2).matches(".*[\\s,<>=].*")) return r;
        int[] v = parse(r.substring(2));
        if (v == null) return r;
        int n = v.length;
        while (n > 2 && v[n - 1] == 0) n--;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) out.append(i == 0 ? "" : ".").append(v[i]);
        return out.append('+').toString();
    }

    /** The numeric parts of a version such as {@code 0.11.0}, {@code v26.1.2} or {@code 1.0-beta}; null when it has none. */
    static int[] parse(String text) {
        String s = text.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        List<Integer> parts = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int start = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i == start) break;
            try {
                parts.add(Integer.parseInt(s.substring(start, i)));
            } catch (NumberFormatException e) {
                return null;
            }
            if (i < s.length() && s.charAt(i) == '.') i++;
            else break;
        }
        if (parts.isEmpty()) return null;
        return parts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }
}
