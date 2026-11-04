package dev.oriel.engine.paint;

import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.layout.SpacedText;
import dev.oriel.engine.style.Colors;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Shadow;

/**
 * Draws one line of styled text: its CSS text-shadows first (the first listed on top), then its
 * {@code -webkit-text-stroke}, then the text in its colour,
 * with the style's decorations; {@code text-shadow: minecraft} has the host draw its native shadow with the text.
 * Letter- or word-spaced text is drawn in its {@link SpacedText} parts. Positions snap to device pixels. The painter's
 * text runs and form controls both draw text through here.
 */
public final class TextPainter {
    private TextPainter() {}

    /**
     * Draws {@code text} with its glyph box's top-left at (x, y) in {@code s}'s decorations and shadows, in
     * {@code color} (usually {@code s.color}); {@code spaced} is null unless spacing applies.
     */
    public static void draw(Canvas canvas, String text, SpacedText spaced, float x, float y, FontSpec font,
                            ComputedStyle s, int color) {
        draw(canvas, text, spaced, x, y, font, s, color, Geometry.devicePixel(canvas));
    }

    /** Rounds a position to device pixels as {@link #draw} does, so carets and highlights line up with the text. */
    public static float snap(Canvas canvas, float v) {
        return Geometry.snap(v, Geometry.devicePixel(canvas));
    }

    /** {@link #draw} snapping to {@code devicePixel}, which the painter tracks per transform. */
    static void draw(Canvas canvas, String text, SpacedText spaced, float x, float y, FontSpec font,
                     ComputedStyle s, int color, float devicePixel) {
        int decorations = (s.underline ? Canvas.UNDERLINE : 0) | (s.lineThrough ? Canvas.STRIKETHROUGH : 0)
                | (s.obfuscated ? Canvas.OBFUSCATED : 0);
        boolean nativeShadow = false;
        for (int i = s.textShadow.size() - 1; i >= 0; i--) {
            Shadow shadow = s.textShadow.get(i);
            if (shadow.isNative()) {
                nativeShadow = true;
            } else if (!Colors.isTransparent(shadow.color())) {
                line(canvas, text, spaced, x + shadow.offsetX(), y + shadow.offsetY(), font, shadow.color(), decorations,
                        false, devicePixel);
            }
        }
        if (s.textStrokeWidth > 0 && !Colors.isTransparent(s.textStrokeColor)) {
            stroke(canvas, text, spaced, x, y, font, s.textStrokeColor, decorations, s.textStrokeWidth / 2, devicePixel);
        }
        if (!Colors.isTransparent(color)) line(canvas, text, spaced, x, y, font, color, decorations, nativeShadow, devicePixel);
    }

    /**
     * The stroke: the glyphs in the stroke colour, offset all round by up to {@code reach} (half the stroke width,
     * the part of a centred stroke that shows outside the glyphs), under the fill, which stays whole, as with
     * {@code paint-order: stroke}. Bitmap glyphs have no outline to stroke, so the copies trace it.
     */
    private static void stroke(Canvas canvas, String text, SpacedText spaced, float x, float y, FontSpec font, int argb,
                               int decorations, float reach, float dp) {
        reach = Math.max(reach, dp);
        int rings = Math.max(1, Math.round(reach / dp / 2));
        for (int ring = 1; ring <= rings; ring++) {
            float r = reach * ring / rings;
            int directions = r > 2 * dp ? 16 : 8;
            for (int i = 0; i < directions; i++) {
                double a = 2 * Math.PI * i / directions;
                line(canvas, text, spaced, x + r * (float) Math.cos(a), y + r * (float) Math.sin(a), font, argb,
                        decorations, false, dp);
            }
        }
    }

    /** The glyphs (and decoration lines) alone, without shadows: the shape {@code background-clip: text} paints. */
    static void glyphs(Canvas canvas, String text, SpacedText spaced, float x, float y, FontSpec font, ComputedStyle s,
                       int color, float devicePixel) {
        int decorations = (s.underline ? Canvas.UNDERLINE : 0) | (s.lineThrough ? Canvas.STRIKETHROUGH : 0)
                | (s.obfuscated ? Canvas.OBFUSCATED : 0);
        line(canvas, text, spaced, x, y, font, color, decorations, false, devicePixel);
    }

    private static void line(Canvas canvas, String text, SpacedText spaced, float x, float y, FontSpec font, int argb,
                             int decorations, boolean shadow, float dp) {
        float ty = Geometry.snap(y, dp);
        if (spaced == null) {
            canvas.drawText(text, Geometry.snap(x, dp), ty, font, argb, decorations, shadow);
            return;
        }
        String[] parts = spaced.parts();
        float[] xs = spaced.x();
        for (int i = 0; i < parts.length; i++) {
            canvas.drawText(parts[i], Geometry.snap(x + xs[i], dp), ty, font, argb, decorations, shadow);
        }
    }
}
