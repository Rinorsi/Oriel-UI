package dev.oriel.engine.replaced;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.style.Image;

/**
 * Where image pixels come from, for {@code <img>} and backgrounds alike: textures and sprites from the host, canvases
 * from the document ({@code canvas:id} is the {@code <canvas>} with that id).
 */
public final class ImageSources {
    private ImageSources() {}

    /** The live canvas a {@code canvas:id} image names, or null when it is missing or the image is not a canvas. */
    public static CanvasContent canvas(Document document, Image image) {
        return image instanceof Image.Canvas c ? CanvasContent.find(document, c.id()) : null;
    }

    /**
     * The natural size {width, height} in px of a texture, sprite or canvas, or null when unknown (and for
     * gradients, which have none). The array may be shared: do not modify it.
     */
    public static float[] size(Document document, Image image) {
        return size(document, image, canvas(document, image));
    }

    /** {@link #size(Document, Image)} with the image's {@link #canvas} already looked up. */
    public static float[] size(Document document, Image image, CanvasContent canvas) {
        return switch (image) {
            case Image.Url url -> textureSize(document, url.url());
            case Image.Canvas c -> canvas == null ? null : canvas.size();
            default -> dev.oriel.engine.css.ImageProviders.size(document, image);
        };
    }

    private static float[] textureSize(Document document, String url) {
        if (url.regionMatches(true, 0, "data:", 0, 5)) {
            document.diagnostics().reportOnce("unsupported-url:" + url, () -> new Diagnostic(
                    Diagnostic.Severity.WARNING, "unsupported-url", document.url(), 0,
                    "Data image URLs are not supported: " + url,
                    "save the image as a PNG file and reference it with <img src=\"image.png\"> or url(image.png)"));
            return null;
        }
        float[] size = document.host().imageSize(url);
        if (size == null && document.host().imageFailed(url)) {
            document.diagnostics().reportOnce("missing-image:" + url, () -> new Diagnostic(
                    Diagnostic.Severity.WARNING, "missing-image", document.url(), 0,
                    "Cannot load image '" + url + "'",
                    "check the image path relative to the page or stylesheet and supply a readable PNG file"));
        }
        return size;
    }

    /**
     * The URL {@link dev.oriel.engine.paint.Canvas#drawImage} draws a texture or canvas with; null for sprites and
     * gradients, and for a canvas that is missing or that the host cannot draw.
     */
    public static String textureUrl(Document document, Image image) {
        return textureUrl(image, canvas(document, image));
    }

    /** {@link #textureUrl(Document, Image)} with the image's {@link #canvas} already looked up. */
    public static String textureUrl(Image image, CanvasContent canvas) {
        return switch (image) {
            case Image.Url url -> url.url();
            case Image.Canvas c -> canvas == null ? null : canvas.surface().url();
            default -> null;
        };
    }
}
