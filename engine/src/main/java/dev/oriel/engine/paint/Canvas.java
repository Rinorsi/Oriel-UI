package dev.oriel.engine.paint;

import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.style.BlendMode;
import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.MaskComposite;
import dev.oriel.engine.style.Shadow;

import java.util.List;

/**
 * The drawing backend. The {@link Painter} turns the box tree into calls on this interface each frame; Minecraft
 * implements it over {@code GuiGraphicsExtractor}, tests and the previewer implement it over Java2D.
 *
 * <p>Backends implement a small set of primitives. Shapes such as rounded rectangles, rings and gradients have
 * default implementations that tessellate into {@link #fillQuads}; a backend may override them with something
 * better (an SDF shader, say).
 *
 * <p>Coordinates are GUI px in the current transform. Colours are ARGB and are multiplied by the current alpha.
 */
public interface Canvas {
    /** Text decoration flags for {@link #drawText}. */
    int UNDERLINE = 1, STRIKETHROUGH = 2, OBFUSCATED = 4;

    // ---- State ----

    /** Pushes transform, clip and alpha. */
    void save();

    /** Pops what the matching {@link #save} pushed. */
    void restore();

    /** The number of saves not yet restored. */
    int saveCount();

    /** Restores until only {@code count} saves remain (unwinding after an exception, say). */
    default void restoreToCount(int count) {
        while (saveCount() > count) restore();
    }

    void translate(float dx, float dy);

    /**
     * Post-multiplies the current transform by the affine matrix {@code [a c e; b d f; 0 0 1]} (the CSS
     * {@code matrix(a, b, c, d, e, f)} convention).
     */
    void transform(float a, float b, float c, float d, float e, float f);

    /** Multiplies the alpha applied to everything drawn until the matching {@link #restore}. */
    void multiplyAlpha(float alpha);

    /**
     * Intersects the clip with a rectangle in the current coordinate space. Backends that only support
     * axis-aligned scissoring clip to the transformed rectangle's bounding box.
     */
    void clipRect(float x, float y, float width, float height);

    /** Size of one device pixel in GUI px at the current transform (1 / GUI scale), for crisp hairlines. */
    default float devicePixel() { return 1f; }

    // ---- Primitives ----

    void fillRect(float x, float y, float width, float height, int argb);

    /**
     * Fills {@code quadCount} convex quads. {@code xy} holds 8 floats per quad (four vertices in order around the
     * quad); {@code colors} holds 4 ARGB values per quad, one per vertex, interpolated across it. A triangle is a
     * quad with its last vertex repeated. The painter emits every quad with the winding of vanilla's {@code fill}
     * ((x0,y0), (x0,y1), (x1,y1), (x1,y0): a negative signed area in GUI coordinates), so back-face culling keeps
     * them unless the transform mirrors. The arrays may be longer than needed and are reused by the caller after
     * the call returns: copy what you keep.
     */
    void fillQuads(float[] xy, int[] colors, int quadCount);

    /**
     * Draws a single line of text with the top-left of its glyph box at (x, y). {@code shadow} requests the
     * host's native drop shadow (Minecraft's 1px dark shadow); CSS text-shadows are drawn by the painter as extra
     * passes instead.
     */
    void drawText(String text, float x, float y, FontSpec font, int argb, int decorations, boolean shadow);

    /**
     * Draws (part of) an image. UVs are normalised 0..1. {@code tint} is multiplied in (white for none).
     * {@code url} is a texture URL (the engine handles {@code sprite:} and {@code canvas:} images itself; a canvas is
     * drawn through its surface's URL). Unknown images draw nothing (or a placeholder).
     */
    void drawImage(String url, float x, float y, float width, float height,
                   float u0, float v0, float u1, float v1, int tint, boolean smooth);

    /**
     * Draws a GUI sprite by id, honouring the sprite's own scaling (stretch, tile, nine-slice). Backends without
     * sprites may treat the id as an image URL.
     */
    void drawSprite(String spriteId, float x, float y, float width, float height, int tint);

    // ---- Shapes with default tessellation ----

    /**
     * Fills a rounded rectangle. {@code radii} holds 8 floats: horizontal and vertical radius for the top-left,
     * top-right, bottom-right and bottom-left corners, already clamped to fit.
     */
    default void fillRoundedRect(float x, float y, float width, float height, float[] radii, int argb) {
        Shapes.fillRoundedRect(this, x, y, width, height, radii, argb);
    }

    /**
     * Fills the region between an outer rounded rectangle and an inner one (a border). Side colours follow the
     * CSS rule: each side's trapezoid gets its own colour, with corners split diagonally.
     *
     * @param outer  {x, y, width, height} of the border box
     * @param radii  outer corner radii, as in {@link #fillRoundedRect}
     * @param widths top, right, bottom, left border widths
     * @param colors top, right, bottom, left ARGB colours
     */
    default void fillBorder(float[] outer, float[] radii, float[] widths, int[] colors) {
        Shapes.fillBorder(this, outer, radii, widths, colors);
    }

    /**
     * Draws one CSS box-shadow. For an outer shadow {@code box} is the border box, which the shadow is drawn under;
     * for an inset shadow ({@link Shadow#inset()}) it is the padding box, which the shadow stays inside. The shadow's
     * shape is {@code box} moved by the offset and grown (outer) or shrunk (inset) by the spread, blurred by a Gaussian
     * with σ = blur / 2. The default tessellates the blur into rings; it leaves the border box uncovered under an
     * outer shadow that is not offset, so translucent boxes do not darken.
     *
     * @param box   {x, y, width, height}
     * @param radii the box's corner radii, as in {@link #fillRoundedRect}
     */
    default void fillBoxShadow(float[] box, float[] radii, Shadow shadow) {
        Shadows.fill(this, box, radii, shadow);
    }

    // ---- Groups: offscreen layers for effects on a whole element ----

    /**
     * Starts a group: what is drawn until {@link #endGroup} goes into an offscreen layer instead of the target, then
     * is composited as one image (CSS filters, masks, clip-path, blend modes, group opacity). Returns false when the
     * backend has no layers: the painter then draws the element directly, without the effects, and says so.
     * Groups nest. Transform, clip and alpha are unchanged by the call.
     */
    default boolean beginGroup() { return false; }

    /**
     * {@link #beginGroup} for a group that paints nothing outside (x, y, width, height) in the current coordinates
     * (conservatively: its descendants, shadows and filters included), so a backend can size its layer to it.
     */
    default boolean beginGroup(float x, float y, float width, float height) { return beginGroup(); }

    /**
     * Inside a group, starts a mask layer: what is drawn next (until the next {@code beginMask} or {@link #endGroup})
     * is a mask whose alpha the group's content is multiplied by. Several mask layers combine in the order they are
     * begun, each onto the result of the ones before with {@code composite}; the first one's operator is ignored.
     */
    default void beginMask(MaskComposite composite) {}

    /**
     * Ends the innermost group and draws it onto what was the target: its pixels through {@code filters} (in order),
     * then its masks, then {@code opacity}, blended with {@code blend}.
     */
    default void endGroup(float opacity, List<Filter> filters, BlendMode blend) {}

    /**
     * What this canvas's groups leave out of {@code filters} and {@code blend}, said in a sentence, or null when they
     * draw it all. The painter reports it; the group is drawn without what is left out.
     */
    default String groupLimits(List<Filter> filters, BlendMode blend) { return null; }

    /**
     * Starts a backdrop ({@code backdrop-filter}): what is drawn until {@link #endBackdrop} is a shape mask. False
     * when the backend cannot read back what it has drawn, or (see {@link #backdropNeedsGroup}) not here.
     */
    default boolean beginBackdrop() { return false; }

    /**
     * Whether a backdrop can only be read inside a group: the backend can read back what a group has drawn, but not
     * the target itself. The painter then paints the page as a group (the page's backdrop root) once it has met a
     * backdrop outside any group, and the backend gives that group what is under the page as its backdrop.
     */
    default boolean backdropNeedsGroup() { return false; }

    /** {@link #beginGroup} for the page itself (see {@link #backdropNeedsGroup}): its backdrop is what is under the page. */
    default boolean beginPageGroup() { return beginGroup(); }

    /**
     * Ends the backdrop: what had been drawn under the shape is drawn again through {@code filters}, inside the shape
     * (the mask drawn since {@link #beginBackdrop}).
     */
    default void endBackdrop(List<Filter> filters) {}

    /**
     * Whether the shapes that follow ({@link #fillRoundedRect}, {@link #fillBorder}, {@link #fillBoxShadow}) keep hard
     * pixel edges, as their defaults draw them: true for a box with {@code shape-rendering: crispEdges}. The painter
     * sets it before each box's decorations and outline. A backend that draws shapes more smoothly should not do so
     * while it is true.
     */
    default void crispShapes(boolean crisp) {}
}
