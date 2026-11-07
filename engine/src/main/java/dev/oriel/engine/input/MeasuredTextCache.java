package dev.oriel.engine.input;

import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.FontSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Document-local LRU with both entry and UTF-16 retention budgets, invalidated on font-provider epochs. */
public final class MeasuredTextCache {
    private static final int MAX_ENTRIES = 8192, MAX_CHARS = 262144;
    private record Key(FontSpec font, String text) {}
    private final FontMetrics fonts;
    private final Map<Key, Float> widths = new LinkedHashMap<>(256, 0.75f, true);
    private final Map<FontSpec, Map<Integer, Float>> glyphs = new LinkedHashMap<>(16, 0.75f, true);
    private int characters, glyphCount;
    private long generation = Long.MIN_VALUE;

    public MeasuredTextCache(FontMetrics fonts) { this.fonts = fonts; }

    private void validate() {
        long epoch = fonts.generation();
        if (generation == epoch) return;
        generation = epoch;
        widths.clear();
        glyphs.clear();
        characters = glyphCount = 0;
    }

    public float width(String text, FontSpec font) {
        validate();
        if (text.length() > MAX_CHARS) return fonts.width(text, font);
        Key key = new Key(font, text);
        Float found = widths.get(key);
        if (found != null) return found;
        float value = fonts.width(text, font);
        while (!widths.isEmpty() && (widths.size() >= MAX_ENTRIES || characters + text.length() > MAX_CHARS)) {
            var entries = widths.entrySet().iterator();
            characters -= entries.next().getKey().text().length();
            entries.remove();
        }
        widths.put(key, value);
        characters += text.length();
        return value;
    }

    public float advance(int codePoint, FontSpec font) {
        validate();
        Map<Integer, Float> map = glyphs.get(font);
        Float found = map == null ? null : map.get(codePoint);
        if (found != null) return found;
        float value = fonts.charWidth(codePoint, font);
        if (glyphCount >= MAX_ENTRIES) { glyphs.clear(); glyphCount = 0; map = null; }
        if (map == null) { map = new java.util.HashMap<>(); glyphs.put(font, map); }
        map.put(codePoint, value);
        glyphCount++;
        return value;
    }
}
