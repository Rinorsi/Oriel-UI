package dev.oriel.preview.render;

import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.paint.Canvas;
import dev.oriel.engine.paint.SmoothShape;
import dev.oriel.engine.style.Colors;
import dev.oriel.engine.style.Shadow;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import dev.oriel.engine.style.MaskComposite;
import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.BlendMode;
import java.util.Deque;

/**
 * {@link Canvas} over a Java2D {@link BufferedImage}, drawing like Minecraft's GUI renderer: one GUI px is
 * {@code scale} device px, textures and glyphs are sampled nearest-neighbour, sprites are cut with vanilla's
 * scaling, text uses {@link MinecraftFont}, clips are device-pixel rectangles like the GUI scissor, and rounded boxes,
 * borders and box-shadows have the shape shader's smooth edges and blur ({@link ShapeRasterizer}). Used by the
 * previewer and the snapshot tests. A canvas can be {@link #reset} and reused for the next frame of the same image.
 */
public final class ImageCanvas implements Canvas {
    private record State(AffineTransform transform, Rectangle clip, float alpha) {}

    /** Where drawing goes: the image, or a group's layer of the same size. */
    private record Target(BufferedImage image, int[] pixels, Graphics2D g, TriangleRasterizer rasterizer, ShapeRasterizer shapes) {}

    /**
     * An open group (or backdrop): its layer, the target it draws onto when it ends, its mask layers and their
     * operators, and the alpha it is drawn with (the canvas's alpha when it began; inside, drawing is at full alpha).
     */
    private record Group(Target content, Target under, List<Target> masks, List<MaskComposite> ops, float alpha) {}

    private static final AffineTransform IDENTITY = new AffineTransform();

    private final BufferedImage image;
    private final float scale;
    private final MinecraftAssets assets;
    private final MinecraftFont font;
    /** The current target's tools (see {@link #bind}). */
    private Graphics2D g;
    private TriangleRasterizer rasterizer;
    private ShapeRasterizer shapes;
    private final Target root;
    private Target current;
    private final Deque<Group> groups = new ArrayDeque<>();
    /** The box being painted asked for hard pixel edges ({@code shape-rendering: crispEdges}). */
    private boolean crisp;
    private final Rectangle bounds;
    private final Deque<State> saved = new ArrayDeque<>();
    /** GUI → device. */
    private AffineTransform transform;
    /** In device px; replaced, never mutated. */
    private Rectangle clip;
    private float alpha = 1;
    /** What the Graphics2D currently has; a null colour after a texture paint replaced it. */
    private Rectangle appliedClip;
    private int appliedAlpha = 255;
    private boolean appliedSmooth;
    private Color appliedColor;

    // Scratch objects, reused by every primitive.
    private final Rectangle2D.Float rect = new Rectangle2D.Float(), source = new Rectangle2D.Float();
    private final Path2D.Float quad = new Path2D.Float();
    private final double[] device = new double[8];
    private final AffineTransform imageToGui = new AffineTransform(), imageToDevice = new AffineTransform();
    private final TextSink textSink = new TextSink();

    /** Draws onto {@code image}, which must be {@code TYPE_INT_ARGB} and not a sub-image. */
    public ImageCanvas(BufferedImage image, float scale, MinecraftAssets assets, MinecraftFont font) {
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) throw new IllegalArgumentException("Needs a TYPE_INT_ARGB image");
        this.image = image;
        this.scale = scale;
        this.assets = assets;
        this.font = font;
        this.root = target(image);
        this.bounds = new Rectangle(image.getWidth(), image.getHeight());
        bind(root);
        reset();
    }

    public BufferedImage image() { return image; }

    public float scale() { return scale; }

    /** Back to the initial state (GUI scale transform, no clip, full alpha) for drawing the next frame. */
    public void reset() {
        while (!groups.isEmpty()) dispose(groups.pop());
        bind(root);
        saved.clear();
        transform = AffineTransform.getScaleInstance(scale, scale);
        clip = bounds;
        alpha = 1;
    }

    /** Releases the Graphics2D; the canvas must not be used afterwards. */
    public void dispose() {
        reset();
        root.g().dispose();
    }

    // ---- Targets ----

    private static Target target(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Pure strokes keep shape edges where they are, so pixels are covered by their centres, as on a GPU.
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        return new Target(image, pixels, g, new TriangleRasterizer(pixels, image.getWidth()), new ShapeRasterizer(pixels, image.getWidth()));
    }

    /** A new transparent layer the size of the image. */
    private Target layer() {
        return target(new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB));
    }

    /** Draws onto {@code t} from now on; the Graphics2D state cache starts over. */
    private void bind(Target t) {
        current = t;
        g = t.g();
        rasterizer = t.rasterizer();
        shapes = t.shapes();
        appliedClip = null;
        appliedColor = null;
        appliedAlpha = -1;
        appliedSmooth = false;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private static void dispose(Group group) {
        group.content().g().dispose();
        for (Target m : group.masks()) m.g().dispose();
    }

    // ---- Groups ----

    @Override
    public boolean beginGroup() {
        groups.push(new Group(layer(), current, new ArrayList<>(), new ArrayList<>(), alpha));
        alpha = 1;
        bind(groups.peek().content());
        return true;
    }

    @Override
    public void beginMask(MaskComposite composite) {
        Group group = groups.peek();
        if (group == null) return;
        Target mask = layer();
        group.masks().add(mask);
        group.ops().add(composite);
        bind(mask);
    }

    @Override
    public void endGroup(float opacity, List<Filter> filters, BlendMode blend) {
        Group group = groups.pop();
        bind(group.under());
        Rectangle area = clip.intersection(bounds);
        if (!area.isEmpty()) {
            int w = image.getWidth();
            int[] px = group.content().pixels();
            Layers2D.filter(px, w, area, filters, 1 / devicePixel());
            float[] mask = null;
            for (int i = 0; i < group.masks().size(); i++) {
                if (mask == null) mask = new float[area.width * area.height];
                Layers2D.maskComposite(mask, group.masks().get(i).pixels(), w, area, group.ops().get(i), i == 0);
            }
            float a = opacity * group.alpha();
            if (mask != null || a < 1) Layers2D.multiplyAlpha(px, w, area, mask, a);
            Layers2D.composite(px, group.under().pixels(), w, area, blend);
        }
        dispose(group);
    }

    @Override
    public boolean beginBackdrop() {
        groups.push(new Group(layer(), current, List.of(), List.of(), alpha));
        alpha = 1;
        bind(groups.peek().content());
        return true;
    }

    @Override
    public void endBackdrop(List<Filter> filters) {
        Group group = groups.pop();
        bind(group.under());
        Rectangle area = clip.intersection(bounds);
        if (!area.isEmpty()) {
            int w = image.getWidth();
            int[] under = group.under().pixels(), copy = under.clone(), shape = group.content().pixels();
            Layers2D.filter(copy, w, area, filters, 1 / devicePixel());
            float[] mask = new float[area.width * area.height];
            Layers2D.maskComposite(mask, shape, w, area, MaskComposite.ADD, true);
            Layers2D.multiplyAlpha(copy, w, area, mask, group.alpha());
            Layers2D.composite(copy, under, w, area, BlendMode.NORMAL);
        }
        dispose(group);
    }

    // ---- State ----

    @Override
    public void save() {
        saved.push(new State(new AffineTransform(transform), clip, alpha));
    }

    @Override
    public int saveCount() {
        return saved.size();
    }

    @Override
    public void restore() {
        State state = saved.poll();
        if (state == null) throw new IllegalStateException("restore() without save()");
        transform = state.transform();
        clip = state.clip();
        alpha = state.alpha();
    }

    @Override
    public void translate(float dx, float dy) {
        transform.translate(dx, dy);
    }

    @Override
    public void transform(float a, float b, float c, float d, float e, float f) {
        transform.concatenate(new AffineTransform(a, b, c, d, e, f));
    }

    @Override
    public void multiplyAlpha(float alpha) {
        this.alpha *= Math.clamp(alpha, 0f, 1f);
    }

    /** Like Minecraft's scissor: the transformed rectangle's bounding box, on whole device pixels. */
    @Override
    public void clipRect(float x, float y, float width, float height) {
        rect.setRect(x, y, width, height);
        Rectangle2D box = transform.createTransformedShape(rect).getBounds2D();
        int x0 = (int) Math.round(box.getMinX()), y0 = (int) Math.round(box.getMinY());
        int x1 = (int) Math.round(box.getMaxX()), y1 = (int) Math.round(box.getMaxY());
        Rectangle next = clip.intersection(new Rectangle(x0, y0, x1 - x0, y1 - y0));
        clip = next.isEmpty() ? new Rectangle() : next;
    }

    @Override
    public float devicePixel() {
        double det = Math.abs(transform.getDeterminant());
        return det == 0 ? 1 / scale : (float) (1 / Math.sqrt(det));
    }

    // ---- Primitives ----

    @Override
    public void fillRect(float x, float y, float width, float height, int argb) {
        rect.setRect(x, y, width, height);
        fill(rect, modulate(argb));
    }

    /** Uniform quads are Java2D polygons; quads with differing vertex colours go through the software rasterizer. */
    @Override
    public void fillQuads(float[] xy, int[] colors, int quadCount) {
        for (int q = 0; q < quadCount; q++) {
            int p = q * 8, c = q * 4;
            int c0 = modulate(colors[c]), c1 = modulate(colors[c + 1]), c2 = modulate(colors[c + 2]), c3 = modulate(colors[c + 3]);
            if (c0 == c1 && c0 == c2 && c0 == c3) {
                quad.reset();
                quad.moveTo(xy[p], xy[p + 1]);
                for (int v = 1; v < 4; v++) quad.lineTo(xy[p + v * 2], xy[p + v * 2 + 1]);
                quad.closePath();
                fill(quad, c0);
            } else {
                // A GPU draws a quad as the triangles (0, 1, 2) and (0, 2, 3).
                transform.transform(xy, p, device, 0, 4);
                rasterizer.fill(clip, device[0], device[1], c0, device[2], device[3], c1, device[4], device[5], c2);
                rasterizer.fill(clip, device[0], device[1], c0, device[4], device[5], c2, device[6], device[7], c3);
            }
        }
    }

    // ---- Shapes, as Minecraft's shape shader draws them (see McCanvas) ----

    @Override
    public void crispShapes(boolean crisp) {
        this.crisp = crisp;
    }

    @Override
    public void fillRoundedRect(float x, float y, float width, float height, float[] radii, int argb) {
        if (!smooth(SmoothShape.box(x, y, width, height, radii, 0, argb))) Canvas.super.fillRoundedRect(x, y, width, height, radii, argb);
    }

    @Override
    public void fillBorder(float[] outer, float[] radii, float[] widths, int[] colors) {
        if (!smooth(SmoothShape.border(outer, radii, widths, colors))) Canvas.super.fillBorder(outer, radii, widths, colors);
    }

    @Override
    public void fillBoxShadow(float[] box, float[] radii, Shadow shadow) {
        if (!smooth(SmoothShape.shadow(box, radii, shadow))) Canvas.super.fillBoxShadow(box, radii, shadow);
    }

    /** Draws a smooth shape; false (null, or crisp edges asked for) to tessellate it instead. */
    private boolean smooth(SmoothShape s) {
        if (s == null || crisp) return false;
        int c = modulate(s.argb());
        if (s != SmoothShape.NOTHING && (c >>> 24) != 0) shapes.fill(s, transform, clip, devicePixel(), c);
        return true;
    }

    @Override
    public void drawText(String text, float x, float y, FontSpec spec, int argb, int decorations, boolean shadow) {
        textSink.x = x;
        textSink.y = y;
        font.draw((decorations & Canvas.OBFUSCATED) != 0 ? scramble(text) : text, spec, modulate(argb), decorations, shadow, textSink);
    }

    /**
     * Obfuscated text for a still image: each letter or digit becomes another (the same for the same text, so
     * previews compare), CJK becomes a CJK character; spaces stay.
     */
    static String scramble(String text) {
        String latin = "ABCDEFGHJKLMNOPRSTUVWXYZabdeghknopqrsuvwxyz0123456789";
        StringBuilder out = new StringBuilder(text.length());
        int seed = text.hashCode();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            seed = seed * 1103515245 + 12345;
            int pick = (seed >>> 8) & 0xFFFF;
            if (Character.isWhitespace(cp)) out.appendCodePoint(cp);
            else if (cp >= 0x4E00 && cp <= 0x9FFF) out.appendCodePoint(0x4E00 + pick % 0x5000);
            else out.append(latin.charAt(pick % latin.length()));
        }
        return out.toString();
    }

    /** Unknown textures draw the missing texture. */
    @Override
    public void drawImage(String url, float x, float y, float width, float height,
                          float u0, float v0, float u1, float v1, int tint, boolean smooth) {
        Texture texture = assets.texture(url).orElse(MinecraftAssets.MISSING);
        blit(texture.image(), u0, v0, u1, v1, x, y, width, height, modulate(tint), smooth || texture.blur());
    }

    @Override
    public void drawSprite(String spriteId, float x, float y, float width, float height, int tint) {
        Texture sprite = assets.sprite(spriteId).orElse(MinecraftAssets.MISSING);
        int color = modulate(tint);
        // blitSprite works in whole GUI px; only the origin may be fractional (McCanvas translates the pose).
        SpriteSlicer.slice(sprite.scaling(), Math.round(width), Math.round(height), (bx, by, bw, bh, u0, v0, u1, v1) ->
                blit(sprite.image(), u0, v0, u1, v1, x + bx, y + by, bw, bh, color, false));
    }

    // ---- Drawing ----
    // Graphics2D state is only touched when it changes: Java2D revalidates its pipeline on every change, which would
    // otherwise dominate the cost of small blits such as glyphs.

    private int modulate(int argb) {
        return Colors.withAlphaFactor(argb, alpha);
    }

    /** Fills a shape given in GUI px with a final colour. */
    private void fill(Shape shape, int argb) {
        if ((argb >>> 24) == 0) return;
        prepare(transform, 255, false);
        if (appliedColor == null || appliedColor.getRGB() != argb) {
            appliedColor = new Color(argb, true);
            g.setColor(appliedColor);
        }
        g.fill(shape);
    }

    /**
     * Draws the part of {@code img} between normalised UVs into a GUI rectangle. UVs past 0..1 repeat the image (as
     * Minecraft samples textures with REPEAT) and reversed UVs flip it.
     */
    private void blit(BufferedImage img, float u0, float v0, float u1, float v1,
                      float x, float y, float width, float height, int argb, boolean smooth) {
        float sx0 = u0 * img.getWidth(), sx1 = u1 * img.getWidth(), sy0 = v0 * img.getHeight(), sy1 = v1 * img.getHeight();
        if (sx0 == sx1 || sy0 == sy1 || width == 0 || height == 0) return;
        float scaleX = width / (sx1 - sx0), scaleY = height / (sy1 - sy0);
        imageToGui.setTransform(scaleX, 0, 0, scaleY, x - sx0 * scaleX, y - sy0 * scaleY);
        source.setRect(Math.min(sx0, sx1), Math.min(sy0, sy1), Math.abs(sx1 - sx0), Math.abs(sy1 - sy0));
        blit(img, source, imageToGui, argb, smooth);
    }

    /** Draws the {@code source} region of {@code img} (image px), placed by {@code imageToGui}, multiplied by {@code argb}. */
    private void blit(BufferedImage img, Rectangle2D source, AffineTransform imageToGui, int argb, boolean smooth) {
        int a = argb >>> 24;
        if (a == 0) return;
        imageToDevice.setTransform(transform);
        imageToDevice.concatenate(imageToGui);
        prepare(imageToDevice, a, smooth);
        BufferedImage tinted = Tints.tint(img, argb);
        Rectangle whole = source.getBounds();
        int w = img.getWidth(), h = img.getHeight();
        if (source.getMinX() < 0 || source.getMinY() < 0 || source.getMaxX() > w || source.getMaxY() > h) {
            g.setPaint(new TexturePaint(tinted, new Rectangle(w, h)));
            appliedColor = null;
            g.fill(source);
        } else if (whole.x == 0 && whole.y == 0 && whole.width == w && whole.height == h || !smooth && source.equals(whole)) {
            // Whole texels: draw only them. (Filtered sampling must still see the neighbouring texels, as on a GPU.)
            boolean all = whole.x == 0 && whole.y == 0 && whole.width == w && whole.height == h;
            g.drawImage(all ? tinted : tinted.getSubimage(whole.x, whole.y, whole.width, whole.height), whole.x, whole.y, null);
        } else {
            g.clip(source);
            g.drawImage(tinted, 0, 0, null);
            appliedClip = null;
        }
    }

    /** Points the Graphics2D at the current clip, the given user → device transform, alpha and filtering. */
    private void prepare(AffineTransform userToDevice, int alpha, boolean smooth) {
        if (appliedClip != clip) {
            g.setTransform(IDENTITY);
            g.setClip(clip.equals(bounds) ? null : clip); // Java2D is faster without a clip than with a full one
            appliedClip = clip;
        }
        g.setTransform(userToDevice);
        if (alpha != appliedAlpha) {
            g.setComposite(alpha == 255 ? AlphaComposite.SrcOver : AlphaComposite.SrcOver.derive(alpha / 255f));
            appliedAlpha = alpha;
        }
        if (smooth != appliedSmooth) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth
                    ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            appliedSmooth = smooth;
        }
    }

    /** Where {@link MinecraftFont#draw} puts a line of text: glyph blits and decoration bars at the text origin. */
    private final class TextSink implements MinecraftFont.GlyphSink {
        float x, y;
        private final AffineTransform glyphToGui = new AffineTransform();
        private final Rectangle2D.Float glyphBounds = new Rectangle2D.Float(), bar = new Rectangle2D.Float();

        @Override
        public void glyph(BufferedImage glyph, AffineTransform glyphToText, int color) {
            glyphToGui.setToTranslation(x, y);
            glyphToGui.concatenate(glyphToText);
            glyphBounds.setRect(0, 0, glyph.getWidth(), glyph.getHeight());
            blit(glyph, glyphBounds, glyphToGui, color, false);
        }

        @Override
        public void rect(float x0, float y0, float x1, float y1, int color) {
            bar.setRect(x + x0, y + y0, x1 - x0, y1 - y0);
            fill(bar, color);
        }
    }
}
