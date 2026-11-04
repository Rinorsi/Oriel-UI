package dev.oriel.engine.css;

import dev.oriel.engine.host.ExtensionRegistry;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.paint.Canvas;
import dev.oriel.engine.style.Image;

import java.util.List;

/** Parsing and rendering semantics for a compatibility image kind. Public image values remain stable. */
public interface ImageProvider {
    Image fromUrl(String url);
    Image function(String name, List<ComponentValue> arguments);
    boolean handles(Image image);
    float[] size(Document document, Image image);
    void paint(Canvas canvas, Image image, float x, float y, float width, float height, int tint);
}
