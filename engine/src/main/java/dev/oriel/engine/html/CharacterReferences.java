package dev.oriel.engine.html;

import java.util.Map;
import java.util.function.BiConsumer;

/** Character reference consumption, independent of token and tree construction. */
final class CharacterReferences {
    private CharacterReferences() {}

    static String decode(String input, boolean attribute, BiConsumer<Integer, String> issue) {
        StringBuilder result = new StringBuilder(input.length());
        int cursor = 0;
        while (cursor < input.length()) {
            int amp = input.indexOf('&', cursor);
            if (amp < 0) { result.append(input, cursor, input.length()); break; }
            result.append(input, cursor, amp);
            int end = amp + 1;
            if (end < input.length() && input.charAt(end) == '#') {
                end++; int radix = 10;
                if (end < input.length() && (input.charAt(end) == 'x' || input.charAt(end) == 'X')) { radix = 16; end++; }
                int digits = end; long number = 0;
                while (end < input.length() && Character.digit(input.charAt(end), radix) >= 0) {
                    number = Math.min(0x110000, number * radix + Character.digit(input.charAt(end++), radix));
                }
                if (end != digits) {
                    if (end < input.length() && input.charAt(end) == ';') end++;
                    else issue.accept(amp, "Numeric character reference has no semicolon");
                    int scalar = (int) number;
                    if (scalar == 0 || scalar > 0x10ffff || scalar >= 0xd800 && scalar <= 0xdfff) {
                        issue.accept(amp, "Invalid character reference replaced by U+FFFD"); scalar = 0xfffd;
                    }
                    result.appendCodePoint(scalar); cursor = end; continue;
                }
                issue.accept(amp, "Numeric character reference has no digits");
            } else {
                while (end < input.length() && Character.isLetterOrDigit(input.charAt(end))) end++;
                boolean terminated = end < input.length() && input.charAt(end) == ';';
                String name = input.substring(amp + 1, end), decoded = EntityNames.ALL.get(name);
                if (terminated && decoded != null) { result.append(decoded); cursor = end + 1; continue; }
                String legacy = null;
                for (String candidate : new String[] {"amp", "lt", "gt", "nbsp", "copy", "quot"}) {
                    if (input.startsWith(candidate, amp + 1) && (legacy == null || candidate.length() > legacy.length())) legacy = candidate;
                }
                if (legacy != null && !attribute) {
                    issue.accept(amp, "Named character reference has no semicolon");
                    result.append(EntityNames.ALL.get(legacy)); cursor = amp + 1 + legacy.length(); continue;
                }
                if (terminated) issue.accept(amp, "Unknown character reference '&" + name + ";' retained as text");
            }
            result.append('&'); cursor = amp + 1;
        }
        return result.toString();
    }
}
