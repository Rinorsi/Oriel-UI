package dev.oriel.engine.css;

import dev.oriel.engine.css.Token.Type;

import java.util.ArrayList;
import java.util.List;

/** What diagnostics suggest instead of a declaration the engine could not take. */
final class Suggestions {
    /** The length units {@link Numeric} understands. */
    static final String UNITS = "px, em, rem, %, vw, vh, vmin, vmax, dp";

    private Suggestions() {}

    static boolean knownProperty(String name) {
        return name.startsWith("--") || Properties.longhand(name) != null || Shorthands.get(name) != null;
    }

    /** A hint for an invalid declaration: the nearest property name, a missing or unknown unit, or nothing. */
    static String forDeclaration(String name, List<ComponentValue> value) {
        if (!knownProperty(name)) {
            String near = nearest(name);
            return near == null ? "not a supported property" : "did you mean '" + near + "'?";
        }
        for (ComponentValue v : value) {
            if (v instanceof Token t && t.is(Type.NUMBER) && t.number != 0) {
                return "lengths need a unit, e.g. " + ComponentValue.text(List.of(v)) + "px";
            }
            if (v instanceof Token t && t.is(Type.DIMENSION) && !isKnownUnit(t.lower)) {
                return "unit '" + t.value + "' is not supported; use " + UNITS;
            }
        }
        return "the value is not valid for " + name;
    }

    private static boolean isKnownUnit(String unit) {
        return List.of("px", "em", "rem", "vw", "vh", "vmin", "vmax", "dp", "deg", "rad", "grad", "turn", "s", "ms", "fr")
                .contains(unit);
    }

    /** The supported property name closest to {@code name} (edit distance 2 at most), or null. */
    static String nearest(String name) {
        List<String> names = new ArrayList<>();
        for (Longhand l : Properties.all()) if (!l.name.startsWith("-oriel-")) names.add(l.name);
        for (Shorthand s : Shorthands.all()) names.add(s.name());
        String best = null;
        int bestDistance = 3;
        for (String n : names) {
            int d = distance(name, n);
            if (d < bestDistance || d == bestDistance && best != null && n.compareTo(best) < 0) {
                best = n;
                bestDistance = d;
            }
        }
        return bestDistance <= 2 ? best : null;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** {@code text} cut to 60 characters for a message. */
    static String clip(String text) {
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 60 ? t : t.substring(0, 57) + "...";
    }
}
