package dev.oriel.engine.layout;

/**
 * Line break opportunities for Chinese, Japanese and Korean text, the part of UAX #14 that matters for CJK user
 * interfaces: lines may break between two characters when either is ideographic (Han, kana, CJK symbols and
 * full-width forms), except where the basic kinsoku rules forbid it. Closing punctuation, small kana and the
 * prolonged sound mark never start a line; opening punctuation never ends one. Text without CJK characters gets
 * no new opportunities: its words still break only at spaces, as before.
 */
public final class CjkBreaks {
    private CjkBreaks() {}

    /** Characters that may not start a line (行頭禁則 / 避头): closing brackets, stops, small kana, ー, ... */
    private static final String NO_START = "、。，．,.：；:;！？!?)）]］}｝〕〉》」』】〙〗〟’”｠»‐゠–〜～…‥・ー々〻ゝゞヽヾ"
            + "ぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶㇰㇱㇲㇳㇴㇵㇶㇷㇸㇹㇺㇻㇼㇽㇾㇿ%％‰℃°";
    /** Characters that may not end a line (行末禁則 / 避尾): opening brackets and quotes. */
    private static final String NO_END = "(（[［{｛〔〈《「『【〘〖〝‘“｟«";

    /** Whether {@code cp} is an ideographic character (UAX #14 class ID, roughly) or CJK punctuation. */
    public static boolean isCjk(int cp) {
        return cp >= 0x2E80 && cp <= 0x2FDF       // CJK radicals, Kangxi radicals
                || cp >= 0x3000 && cp <= 0x30FF   // CJK symbols and punctuation, hiragana, katakana
                || cp >= 0x3100 && cp <= 0x31FF   // bopomofo, kanbun, CJK strokes, katakana extensions
                || cp >= 0x3200 && cp <= 0x4DBF   // enclosed CJK, compatibility, extension A
                || cp >= 0x4E00 && cp <= 0x9FFF   // CJK unified ideographs
                || cp >= 0xA960 && cp <= 0xA97F   // Hangul jamo extended A
                || cp >= 0xAC00 && cp <= 0xD7AF   // Hangul syllables
                || cp >= 0xF900 && cp <= 0xFAFF   // CJK compatibility ideographs
                || cp >= 0xFE30 && cp <= 0xFE4F   // CJK compatibility forms
                || cp >= 0xFF00 && cp <= 0xFFEF   // half-width and full-width forms
                || cp >= 0x20000 && cp <= 0x3FFFF; // CJK extensions B and later
    }

    /** Whether a line may break between {@code before} and {@code after} because of CJK rules. */
    public static boolean breakBetween(int before, int after) {
        if (!isCjk(before) && !isCjk(after)) return false;
        if (after == ' ' || before == ' ') return false;
        return NO_START.indexOf(after) < 0 && NO_END.indexOf(before) < 0 && !(isCombining(after));
    }

    private static boolean isCombining(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK || cp == 0x200D || cp >= 0xFE00 && cp <= 0xFE0F;
    }
}
