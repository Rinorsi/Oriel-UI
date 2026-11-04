package dev.oriel.engine.html;

import java.util.HashMap;
import java.util.Map;

/** Standard named-reference spellings supported by the existing UI subset. Data retained from the MIT baseline. */
final class EntityNames {
    static final Map<String, String> ALL = new HashMap<>();
    static {
        String[] pairs = {
                "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ",
                "copy", "©", "reg", "®", "trade", "™", "hellip", "…", "mdash", "—",
                "ndash", "–", "lsquo", "‘", "rsquo", "’", "ldquo", "“", "rdquo", "”",
                "laquo", "«", "raquo", "»", "bull", "•", "middot", "·", "times", "×",
                "divide", "÷", "deg", "°", "plusmn", "±", "minus", "−", "para", "¶",
                "sect", "§", "larr", "←", "uarr", "↑", "rarr", "→", "darr", "↓",
                "harr", "↔", "hearts", "♥", "diams", "♦", "clubs", "♣", "spades", "♠",
                "star", "☆", "check", "✓", "cross", "✗", "infin", "∞", "ensp", " ",
                "emsp", " ", "thinsp", " ", "zwnj", "‌", "zwj", "‍", "shy", "­",
                "frac12", "½", "frac14", "¼", "frac34", "¾", "sup2", "²", "sup3", "³",
                "euro", "€", "pound", "£", "yen", "¥", "cent", "¢", "iexcl", "¡",
                "iquest", "¿", "dagger", "†", "Dagger", "‡", "loz", "◊", "le", "≤",
                "ge", "≥", "ne", "≠", "asymp", "≈", "prime", "′", "Prime", "″",
        };
        for (int i = 0; i < pairs.length; i += 2) ALL.put(pairs[i], pairs[i + 1]);
    }


    private EntityNames() {}
}
