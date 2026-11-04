package dev.oriel.engine.paint;

import dev.oriel.engine.style.BlendMode;
import dev.oriel.engine.style.MaskComposite;
import dev.oriel.engine.style.Image;
import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.ReplacedContent;
import dev.oriel.engine.input.Controls;
import dev.oriel.engine.layout.Box;
import dev.oriel.engine.layout.Fragment;
import dev.oriel.engine.style.BackgroundLayer;
import dev.oriel.engine.style.BorderStyle;
import dev.oriel.engine.style.Colors;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.ShapeRendering;
import dev.oriel.engine.style.Shadow;
import dev.oriel.engine.style.Visibility;

import java.util.Arrays;
import java.util.List;

/**
 * The painting side of {@link StackingOrder}: draws each step of the walk onto a {@link Canvas}. Per box, in order:
 * outer box-shadows, background, inset box-shadows, border, form control ({@link Controls#paint}), replaced content
 * ({@link ReplacedContent#paint}); after its content: scrollbars and outline. Rectangle edges are snapped to device
 * pixels.
 */
final class BoxPainter implements StackingOrder.Visitor {
    private final Painter painter;
    private final QuadBatch batch = new QuadBatch();
    private final Geometry geometry = new Geometry();
    private final Backgrounds backgrounds;
    private final Borders borders = new Borders();
    private final BorderImages borderImages;

    private Canvas canvas;
    /** GUI px per device pixel in the current transform, saved per transformed context. */
    private float dp;
    private float[] dpStack = new float[8];
    private int depth;
    /** Per open context: the box and its style when it is drawn as a group (effects), else null. */
    private Group[] groups = new Group[8];

    /** A context drawn through a group: what {@link #exitContext} needs to draw its masks and composite it. */
    private record Group(Box box, ComputedStyle style, float x, float y) {}

    private final float[] rect = new float[4], radii = new float[8], widths = new float[4];
    /** The border or padding box a box-shadow is drawn from, for {@link Canvas#fillBoxShadow}. */
    private final float[] shadowBox = new float[4], shadowRadii = new float[8];
    private final BorderStyle[] styles = new BorderStyle[4];
    private final int[] colors = new int[4];

    BoxPainter(Painter painter) {
        this.painter = painter;
        this.backgrounds = new Backgrounds(painter.document());
        this.borderImages = new BorderImages(painter.document());
    }

    void begin(Canvas canvas) {
        this.canvas = canvas;
        dp = Geometry.devicePixel(canvas);
        depth = 0;
        rootGroup = pageGroup && canvas.backdropNeedsGroup() && canvas.beginPageGroup();
    }

    void end() {
        if (rootGroup) canvas.endGroup(1, List.of(), BlendMode.NORMAL);
        rootGroup = false;
        canvas = null;
    }

    /**
     * The page is painted as a group, so a backend that can only read back groups ({@link Canvas#backdropNeedsGroup})
     * can draw its backdrops: set once a backdrop was met outside any group, from the next frame on.
     */
    private boolean pageGroup, rootGroup;

    private float snap(float v) {
        return Geometry.snap(v, dp);
    }

    // ---- State ----

    @Override
    public boolean enterContext(Box box, ComputedStyle style, float x, float y, Affine transform) {
        // Overshooting easings can push animated opacity outside 0..1.
        float opacity = Math.min(1, style.opacity);
        if (!(opacity > 0)) return false;
        canvas.save();
        if (depth == dpStack.length) {
            dpStack = Arrays.copyOf(dpStack, depth * 2);
            groups = Arrays.copyOf(groups, depth * 2);
        }
        float bx = x, by = y;
        dpStack[depth] = dp;
        if (transform != null) {
            canvas.translate(x, y);
            canvas.transform(transform.a, transform.b, transform.c, transform.d, transform.e, transform.f);
            dp = Geometry.devicePixel(canvas);
            bx = by = 0;
        }
        // The backdrop reads what is under the element, so it comes before the element's own group.
        if (!style.backdropFilter.isEmpty()) backdrop(box, style, bx, by);
        boolean effects = style.hasLayerEffects();
        float[] extent = effects ? Extents.of(box, style, bx, by) : null;
        boolean group = effects && (extent == null ? canvas.beginGroup()
                : canvas.beginGroup(extent[0], extent[1], extent[2] - extent[0], extent[3] - extent[1]));
        if (effects && !group) unsupported("filter, backdrop-filter, clip-path, mask and mix-blend-mode are not drawn: this canvas has no offscreen layers");
        String limits = group ? canvas.groupLimits(style.filter, style.mixBlendMode) : null;
        if (limits != null) unsupported(limits);
        groups[depth++] = group ? new Group(box, style, bx, by) : null;
        if (!group && opacity < 1) canvas.multiplyAlpha(opacity);
        return true;
    }

    @Override
    public void exitContext() {
        Group group = groups[--depth];
        groups[depth] = null;
        if (group != null) {
            masks(group);
            ComputedStyle s = group.style;
            canvas.endGroup(Math.min(1, s.opacity), s.filter, s.mixBlendMode);
        }
        dp = dpStack[depth];
        canvas.restore();
    }

    /**
     * {@code backdrop-filter}: what is under the border box, through the filters, inside its rounded shape, or inside
     * its clip-path, which cuts the backdrop with the rest of the element (Filter Effects 2 §3; the clip-path alone
     * when the box is also rounded).
     */
    private void backdrop(Box box, ComputedStyle s, float x, float y) {
        if (!canvas.beginBackdrop()) {
            if (canvas.backdropNeedsGroup() && !rootGroup && !pageGroup) {
                pageGroup = true; // drawn from the next frame on
                return;
            }
            unsupported("backdrop-filter is not drawn: this canvas cannot read back what it has drawn");
            return;
        }
        float[] outline = s.clipPath.outline(x, y, box.width, box.height);
        Geometry g = geometry.box(box, s, x, y, dp);
        if (outline != null) Polygons.fill(canvas, outline, Colors.WHITE);
        else if (Shapes.isRounded(g.radii)) canvas.fillRoundedRect(g.x, g.y, g.width, g.height, g.radii, Colors.WHITE);
        else canvas.fillRect(g.x, g.y, g.width, g.height, Colors.WHITE);
        canvas.endBackdrop(s.backdropFilter);
    }

    /** The group's masks: its mask images bottom layer first, then its clip-path, which every mask is cut by. */
    private void masks(Group group) {
        ComputedStyle s = group.style;
        boolean any = false;
        List<Image> images = s.maskImage;
        if (!images.isEmpty()) {
            Geometry g = geometry.box(group.box, s, group.x, group.y, dp);
            for (int i = images.size() - 1; i >= 0; i--) {
                canvas.beginMask(s.maskComposite.get(i % s.maskComposite.size()));
                backgrounds.mask(canvas, batch, g, s, images.get(i), s.maskClip.get(i % s.maskClip.size()), dp);
                any = true;
            }
        }
        float[] outline = s.clipPath.outline(group.x, group.y, group.box.width, group.box.height);
        if (outline != null) {
            canvas.beginMask(any ? MaskComposite.INTERSECT : MaskComposite.ADD);
            Polygons.fill(canvas, outline, Colors.WHITE);
        }
    }

    /** Reports, once per page and message, an effect that is drawn without some of it. */
    private void unsupported(String why) {
        painter.document().diagnostics().report(new Diagnostic(Diagnostic.Severity.WARNING, "effects-unsupported",
                painter.document().url(), 0, why, ""));
    }

    /** Per open clip: whether it is a group cut to rounded corners when it pops. */
    private boolean[] roundedClips = new boolean[8];
    private final float[] clipBox = new float[4], clipRadii = new float[8];
    private Box[] clipBoxes = new Box[8];
    private float[] clipOrigins = new float[16];
    private int clips;

    /**
     * Overflow clips: the padding box as a rectangle, and when the clipping box has rounded corners and the canvas
     * has groups, the content as a group masked to the rounded padding box (CSS Backgrounds 3 §5.3).
     */
    @Override
    public boolean pushClip(Box clipper, float x, float y, float width, float height) {
        if (!pushClip(x, y, width, height)) return false;
        if (clips == roundedClips.length) {
            roundedClips = Arrays.copyOf(roundedClips, clips * 2);
            clipBoxes = Arrays.copyOf(clipBoxes, clips * 2);
            clipOrigins = Arrays.copyOf(clipOrigins, clips * 4);
        }
        ComputedStyle s = StackingOrder.styleOf(clipper);
        boolean rounded = s.hasRadius() && canvas.beginGroup();
        roundedClips[clips] = rounded;
        clipBoxes[clips] = rounded ? clipper : null;
        clipOrigins[clips * 2] = x - clipper.borderLeft;
        clipOrigins[clips * 2 + 1] = y - clipper.borderTop;
        clips++;
        return true;
    }

    @Override
    public void popClip(Box clipper) {
        clips--;
        if (roundedClips[clips]) {
            Box box = clipBoxes[clips];
            ComputedStyle s = StackingOrder.styleOf(box);
            geometry.box(box, s, clipOrigins[clips * 2], clipOrigins[clips * 2 + 1], dp).area(BackgroundLayer.Box.PADDING_BOX, clipBox, clipRadii);
            canvas.beginMask(MaskComposite.ADD);
            canvas.fillRoundedRect(clipBox[0], clipBox[1], clipBox[2], clipBox[3], clipRadii, Colors.WHITE);
            canvas.endGroup(1, List.of(), BlendMode.NORMAL);
            clipBoxes[clips] = null;
        }
        popClip();
    }

    /** A plain rectangle clip (the overflow of replaced content, and every overflow clip's rectangle). */
    private boolean pushClip(float x, float y, float width, float height) {
        if (width <= 0 || height <= 0) return false;
        canvas.save();
        float x0 = snap(x), y0 = snap(y);
        canvas.clipRect(x0, y0, snap(x + width) - x0, snap(y + height) - y0);
        return true;
    }

    private void popClip() {
        canvas.restore();
    }

    // ---- Boxes ----

    @Override
    public void box(Box box, ComputedStyle s, float x, float y) {
        if (s.visibility != Visibility.VISIBLE || box.kind == Box.Kind.ANONYMOUS) return;
        Geometry g = geometry.box(box, s, x, y, dp);
        decorations(s, g);
        if (Backgrounds.clipsToText(s)) textBackground(g, s, sink -> StackingOrder.textRuns(box, x, y, sink));
        Element element = box.element;
        if (element == null || box.kind == Box.Kind.PSEUDO) return;
        if (Controls.isControl(element.tagName())) {
            canvas.save();
            canvas.translate(x, y);
            Controls.paint(canvas, box, s);
            canvas.restore();
        }
        if (box.kind == Box.Kind.REPLACED && element.replaced != null) replaced(box, s, element.replaced, x, y);
    }

    @Override
    public void inlineBox(Box block, Fragment.InlineBox fragment, ComputedStyle s, float x, float y) {
        if (s.visibility != Visibility.VISIBLE) return;
        Geometry g = geometry.fragment(fragment, s, x, y, dp);
        decorations(s, g);
        if (Backgrounds.clipsToText(s)) textBackground(g, s, sink -> StackingOrder.textRuns(block, fragment, x, y, sink));
    }

    /**
     * {@code background-clip: text}: the layers clipped to the text, as a group masked by the glyphs of the text
     * {@code runs} gives (the text itself is painted later, as usual, usually transparent). Without groups the
     * layers are left out, so the text stays readable in its own colour.
     */
    private void textBackground(Geometry g, ComputedStyle s, java.util.function.Consumer<StackingOrder.RunSink> runs) {
        if (!canvas.beginGroup()) {
            unsupported("background-clip: text is left out: this canvas has no offscreen layers");
            return;
        }
        backgrounds.paint(canvas, batch, g, s, dp, true);
        batch.flush(canvas);
        canvas.beginMask(MaskComposite.ADD);
        runs.accept((run, rx, ry) -> {
            ComputedStyle rs = StackingOrder.styleOf(run);
            if (rs.visibility != Visibility.VISIBLE || run.text().isEmpty()) return;
            TextPainter.glyphs(canvas, run.text(), run.spaced(), rx + run.x(), ry + run.y(), rs.font(), rs, Colors.WHITE, dp);
        });
        canvas.endGroup(1, List.of(), BlendMode.NORMAL);
    }

    /** Outer shadows, background, inset shadows and border (CSS paints the first shadow on top). */
    private void decorations(ComputedStyle s, Geometry g) {
        canvas.crispShapes(s.shapeRendering == ShapeRendering.CRISP_EDGES);
        paintShadows(g, s.boxShadow, false, BackgroundLayer.Box.BORDER_BOX);
        backgrounds.paint(canvas, batch, g, s, dp, false);
        paintShadows(g, s.boxShadow, true, BackgroundLayer.Box.PADDING_BOX);
        // A border image replaces the border's own painting (CSS Backgrounds 3 §6), when its source can be drawn.
        if (borderImages.paint(canvas, g, s, dp)) return;
        if (!s.hasBorder()) return;
        rect[0] = g.x;
        rect[1] = g.y;
        rect[2] = g.width;
        rect[3] = g.height;
        styles[0] = s.borderTopStyle;
        styles[1] = s.borderRightStyle;
        styles[2] = s.borderBottomStyle;
        styles[3] = s.borderLeftStyle;
        colors[0] = s.borderTopColor;
        colors[1] = s.borderRightColor;
        colors[2] = s.borderBottomColor;
        colors[3] = s.borderLeftColor;
        borders.paint(canvas, batch, dp, rect, g.radii, g.border, styles, colors);
    }

    /** The outer or inset shadows, last first, drawn from {@code area}. */
    private void paintShadows(Geometry g, List<Shadow> shadows, boolean inset, BackgroundLayer.Box area) {
        if (shadows.isEmpty()) return;
        g.area(area, shadowBox, shadowRadii);
        for (int i = shadows.size() - 1; i >= 0; i--) {
            if (shadows.get(i).inset() == inset) canvas.fillBoxShadow(shadowBox, shadowRadii, shadows.get(i));
        }
    }

    /** Replaced content in the content box, sized by object-fit and placed by object-position; clipped when it overflows. */
    private void replaced(Box box, ComputedStyle s, ReplacedContent content, float x, float y) {
        float cx = x + box.contentX(), cy = y + box.contentY(), cw = box.contentWidth(), ch = box.contentHeight();
        float[] fitted = Coordinates.objectBox(box, s, content);
        float w = fitted[2], h = fitted[3];
        boolean overflows = w > cw + 0.01f || h > ch + 0.01f;
        float ox = x + fitted[0], oy = y + fitted[1];
        float x0 = snap(ox), y0 = snap(oy);
        float width = snap(ox + w) - x0, height = snap(oy + h) - y0;
        if (width <= 0 || height <= 0 || overflows && !pushClip(cx, cy, cw, ch)) return;
        content.paint(canvas, x0, y0, width, height);
        if (overflows) popClip();
    }

    // ---- Text ----

    @Override
    public void textRun(Box block, Fragment.TextRun run, float x, float y) {
        ComputedStyle s = StackingOrder.styleOf(run);
        if (s.visibility != Visibility.VISIBLE || run.text().isEmpty()) return;
        TextPainter.draw(canvas, run.text(), run.spaced(), x + run.x(), y + run.y(), s.font(), s, s.color, dp);
    }

    // ---- After the content ----

    @Override
    public void after(Box box, ComputedStyle s, float x, float y) {
        if (s.visibility != Visibility.VISIBLE) return;
        if (box.isScrollContainer()) {
            scrollbar(box, s, x, y, true);
            scrollbar(box, s, x, y, false);
        }
        if (StackingOrder.hasOutline(s)) outline(s, x, y, box.width, box.height);
    }

    @Override
    public void inlineOutline(Fragment.InlineBox fragment, ComputedStyle s, float x, float y) {
        if (s.visibility == Visibility.VISIBLE) outline(s, x + fragment.x(), y + fragment.y(), fragment.width(), fragment.height());
    }

    private void scrollbar(Box box, ComputedStyle s, float x, float y, boolean vertical) {
        boolean hovered = painter.scrollbarHovered(box, vertical);
        if (!Scrollbars.track(box, vertical, hovered, rect)) return;
        fill(x + rect[0], y + rect[1], rect[2], rect[3], s.scrollbarTrackColor);
        Scrollbars.thumb(box, vertical, hovered, rect);
        fill(x + rect[0], y + rect[1], rect[2], rect[3], s.scrollbarThumbColor);
    }

    /** The outline around a border box at (x, y), outline-offset away, following the border radius. */
    private void outline(ComputedStyle s, float x, float y, float width, float height) {
        canvas.crispShapes(s.shapeRendering == ShapeRendering.CRISP_EDGES);
        float out = s.outlineOffset + s.outlineWidth;
        float x0 = snap(x - out), y0 = snap(y - out);
        rect[0] = x0;
        rect[1] = y0;
        rect[2] = snap(x + width + out) - x0;
        rect[3] = snap(y + height + out) - y0;
        if (rect[2] <= 0 || rect[3] <= 0) return;
        Shapes.radii(s, width, height, radii);
        Shapes.insetRadii(radii, -out, -out, -out, -out, radii);
        Arrays.fill(widths, Math.max(dp, snap(s.outlineWidth)));
        Arrays.fill(styles, s.outlineStyle);
        Arrays.fill(colors, s.outlineColor);
        borders.paint(canvas, batch, dp, rect, radii, widths, styles, colors);
    }

    private void fill(float x, float y, float w, float h, int argb) {
        if (Colors.isTransparent(argb)) return;
        float x0 = snap(x), y0 = snap(y);
        canvas.fillRect(x0, y0, snap(x + w) - x0, snap(y + h) - y0, argb);
    }
}
