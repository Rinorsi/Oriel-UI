package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minecraft's formatting codes, as chat, signs, books, item names and server plugins write them: {@code §} (or
 * {@code &}) followed by a colour ({@code 0}–{@code 9}, {@code a}–{@code f}), a style ({@code k} obfuscated,
 * {@code l} bold, {@code m} strikethrough, {@code n} underline, {@code o} italic) or {@code r} (reset). As in the
 * game, a colour code also ends the styles before it. {@code §#rrggbb} is a colour outside the sixteen (what JSON text
 * allows; plugins write it as {@code &#rrggbb}).
 *
 * <p>{@link #parse} splits text into styled {@link Run runs}; {@link #render} fills an element with them as spans,
 * {@link #json} writes the chat component the game reads ({@code <mc-text json>}, {@code /tellraw}, item names) and
 * {@link #strip} keeps only the text. Pages can also write the classes from {@code ore.css}: {@code mc-0}…{@code mc-f}
 * for the colours, {@code mc-k mc-l mc-m mc-n mc-o} for the styles.
 */
public final class McFormat {
    /** The sixteen colours by code, as RGB, with the names chat JSON uses. */
    public static final int[] COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    public static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
    /** Message keys for the colours' display names; COLOR_NAMES remains the language-independent JSON protocol. */
    public static final String[] COLOR_LABELS = {
            "oriel.ui.mcformat.black", "oriel.ui.mcformat.dark_blue", "oriel.ui.mcformat.dark_green", "oriel.ui.mcformat.dark_aqua", "oriel.ui.mcformat.dark_red", "oriel.ui.mcformat.purple", "oriel.ui.mcformat.gold", "oriel.ui.mcformat.gray",
            "oriel.ui.mcformat.dark_gray", "oriel.ui.mcformat.blue", "oriel.ui.mcformat.green", "oriel.ui.mcformat.aqua", "oriel.ui.mcformat.red", "oriel.ui.mcformat.light_purple", "oriel.ui.mcformat.yellow", "oriel.ui.mcformat.white"};
    public static final String CODES = "0123456789abcdefklmnor";

    private McFormat() {}

    /**
     * A piece of text in one style. {@code color} is RGB or -1 (the element's own colour); {@code code} is the
     * colour's code character, or {@code '#'} for a hex colour, or 0.
     */
    public record Run(String text, int color, char code, boolean bold, boolean italic, boolean underlined,
                      boolean strikethrough, boolean obfuscated) {
        /** The run's CSS (for an inline {@code style}). */
        public String css() {
            StringBuilder s = new StringBuilder();
            if (color >= 0) s.append("color: ").append(hex(color)).append("; ");
            if (bold) s.append("font-weight: bold; ");
            if (italic) s.append("font-style: italic; ");
            if (underlined || strikethrough) {
                s.append("text-decoration: ").append(underlined ? "underline" : "").append(underlined && strikethrough ? " " : "")
                        .append(strikethrough ? "line-through" : "").append("; ");
            }
            if (obfuscated) s.append("-mc-obfuscated: on; ");
            return s.toString().strip();
        }
    }

    /** Whether {@code c} (either case) is a formatting code. */
    public static boolean isCode(char c) {
        return CODES.indexOf(Character.toLowerCase(c)) >= 0;
    }

    /** {@code text} cut into runs at its codes ({@code §} and {@code &} both start one). */
    public static List<Run> parse(String text) {
        List<Run> runs = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int color = -1;
        char code = 0;
        boolean bold = false, italic = false, under = false, strike = false, obf = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int hex = (c == '§' || c == '&') ? hexAt(text, i + 1) : -1;
            boolean isCode = (c == '§' || c == '&') && i + 1 < text.length() && isCode(text.charAt(i + 1));
            if (!isCode && hex < 0) {
                part.append(c);
                continue;
            }
            if (!part.isEmpty()) {
                runs.add(new Run(part.toString(), color, code, bold, italic, under, strike, obf));
                part.setLength(0);
            }
            if (hex >= 0) {
                color = hex;
                code = '#';
                bold = italic = under = strike = obf = false;
                i += 7;
                continue;
            }
            char k = Character.toLowerCase(text.charAt(++i));
            int index = "0123456789abcdef".indexOf(k);
            if (index >= 0) {
                color = COLORS[index];
                code = k;
                bold = italic = under = strike = obf = false;
            } else {
                switch (k) {
                    case 'k' -> obf = true;
                    case 'l' -> bold = true;
                    case 'm' -> strike = true;
                    case 'n' -> under = true;
                    case 'o' -> italic = true;
                    default -> { // r
                        color = -1;
                        code = 0;
                        bold = italic = under = strike = obf = false;
                    }
                }
            }
        }
        if (!part.isEmpty()) runs.add(new Run(part.toString(), color, code, bold, italic, under, strike, obf));
        return runs;
    }

    /** The text without its codes. */
    public static String strip(String text) {
        StringBuilder out = new StringBuilder();
        for (Run r : parse(text)) out.append(r.text());
        return out.toString();
    }

    /** Replaces {@code element}'s children with the runs of {@code text}, each a span with its style. */
    public static void render(Document doc, Element element, String text) {
        element.removeAllChildren();
        for (Run r : parse(text)) {
            Element span = Dom.el(doc, "span", null, r.text());
            String css = r.css();
            if (!css.isEmpty()) span.setAttribute("style", css);
            element.appendChild(span);
        }
    }

    /**
     * The chat component for {@code text}: {@code {"text":"","extra":[...]}} with each run's colour (a name for the
     * sixteen) and styles, as {@code <mc-text json>}, {@code /tellraw} and item names read it.
     */
    public static String json(String text) {
        StringBuilder out = new StringBuilder("{\"text\":\"\",\"extra\":[");
        boolean first = true;
        for (Run r : parse(text)) {
            if (!first) out.append(',');
            first = false;
            out.append("{\"text\":").append(quote(r.text()));
            if (r.color() >= 0) {
                int index = r.code() == '#' ? -1 : "0123456789abcdef".indexOf(r.code());
                out.append(",\"color\":\"").append(index >= 0 ? COLOR_NAMES[index] : hex(r.color())).append('"');
            }
            if (r.bold()) out.append(",\"bold\":true");
            if (r.italic()) out.append(",\"italic\":true");
            if (r.underlined()) out.append(",\"underlined\":true");
            if (r.strikethrough()) out.append(",\"strikethrough\":true");
            if (r.obfuscated()) out.append(",\"obfuscated\":true");
            out.append('}');
        }
        return out.append("]}").toString();
    }

    /** {@code #rrggbb}. */
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    /** The colour of {@code #rrggbb} at {@code i} (a hex code's body), or -1. */
    private static int hexAt(String text, int i) {
        if (i + 7 > text.length() || text.charAt(i) != '#') return -1;
        try {
            return Integer.parseInt(text.substring(i + 1, i + 7), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String quote(String s) {
        StringBuilder q = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> q.append("\\\"");
                case '\\' -> q.append("\\\\");
                case '\n' -> q.append("\\n");
                case '\t' -> q.append("\\t");
                default -> {
                    if (c < 0x20) q.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else q.append(c);
                }
            }
        }
        return q.append('"').toString();
    }
}
