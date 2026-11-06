package dev.oriel.engine.replaced;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.ReplacedContent;
import dev.oriel.engine.paint.Canvas;
import dev.oriel.engine.style.Colors;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Image;
import dev.oriel.engine.style.ImageRendering;

/**
 * {@code <img src>} and {@code <sprite src>}. An img shows a texture ({@code ns:textures/....png}, or a path
 * relative to the page), a GUI sprite ({@code sprite:ns:path}) or a canvas ({@code canvas:id}); a sprite element's
 * src is a sprite id. The natural size comes from the host (textures, sprites) or the canvas, and is re-read every
 * frame: canvases resize. Tinted by {@code -mc-tint}.
 */
final class ImageContent implements ReplacedContent {
    private final Element element;
    private final boolean spriteElement;
    /** What src names, or null without one. */
    private Image source;
    private float width = Float.NaN, height = Float.NaN;

    ImageContent(Element element) { this(element, false); }

    ImageContent(Element element, boolean spriteElement) {
        this.element = element;
        this.spriteElement = spriteElement;
        load();
    }

    @Override
    public float intrinsicWidth() {
        return width;
    }

    @Override
    public float intrinsicHeight() {
        return height;
    }

    @Override
    public void attributeChanged(String name) {
        if (name.equals("src") || name.equals("loading")) load();
        if (name.equals("loading")) document().invalidateLayout();
    }

    @Override
    public boolean update() {
        return refreshSize();
    }

    @Override
    public void paint(Canvas canvas, float x, float y, float w, float h) {
        if (source == null || !allowed()) return;
        ComputedStyle s = element.style;
        int tint = s == null ? Colors.WHITE : s.tint;
        if (dev.oriel.engine.css.ImageProviders.handles(source)) {
            dev.oriel.engine.css.ImageProviders.paint(canvas, source, x, y, w, h, tint);
            return;
        }
        String url = ImageSources.textureUrl(document(), source);
        if (url != null) canvas.drawImage(url, x, y, w, h, 0, 0, 1, 1, tint, s != null && s.imageRendering == ImageRendering.SMOOTH);
    }

    private Document document() {
        return element.ownerDocument();
    }

    Image source() { return source; }

    private boolean allowed() {
        return !LazyImages.lazyImage(element) || element.isConnected() && document().lazyImages().allows(element, source);
    }

    private void load() {
        String src = element.getAttribute("src");
        src = src == null ? "" : src.strip();
        if (src.isEmpty()) source = null;
        else if (spriteElement) source = dev.oriel.engine.css.ImageProviders.fromUrl(Image.SPRITE_SCHEME + src);
        else source = Image.ofUrl(src, document()::resolveUrl);
        refreshSize();
    }

    /** Reads the natural size again; true when it changed (a canvas was resized, a texture appeared). */
    private boolean refreshSize() {
        float[] size = source == null || !allowed() ? null : ImageSources.size(document(), source);
        float w = size == null ? Float.NaN : size[0], h = size == null ? Float.NaN : size[1];
        boolean changed = Float.compare(w, width) != 0 || Float.compare(h, height) != 0;
        width = w;
        height = h;
        return changed;
    }
}
