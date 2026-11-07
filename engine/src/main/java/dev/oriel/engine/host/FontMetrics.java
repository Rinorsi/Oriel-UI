package dev.oriel.engine.host;

/**
 * Text measurement supplied by the host. In Minecraft this wraps {@code Font}; the test and preview backends use a
 * bitmap copy of the same glyph widths so layout matches the game.
 *
 * <p>The glyph box of a line of text is {@link #glyphHeight} tall; the engine centres it in the line box
 * (half-leading) and draws text with its top-left at the glyph box origin.
 */
public interface FontMetrics {
    /** Monotonic provider epoch. Hosts with mutable font resources increment it when advances change. */
    default long generation() { return 0; }

    /** Advance width of {@code text} in px, without letter spacing. */
    float width(String text, FontSpec font);

    /** Advance width of one code point. Used for breaking long words; defaults to {@link #width}. */
    default float charWidth(int codePoint, FontSpec font) {
        return width(new String(Character.toChars(codePoint)), font);
    }

    /** Height of the glyph box (Minecraft: 9 px at size 8). */
    default float glyphHeight(FontSpec font) {
        return ExtensionRegistry.compatibility().glyphHeightAtNativeSize() * font.scale();
    }

    /** Distance from the top of the glyph box to the alphabetic baseline (Minecraft: 7 px at size 8). */
    default float ascent(FontSpec font) {
        return ExtensionRegistry.compatibility().ascentAtNativeSize() * font.scale();
    }
}
