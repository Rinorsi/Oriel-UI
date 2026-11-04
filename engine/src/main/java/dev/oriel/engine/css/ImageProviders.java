package dev.oriel.engine.css;

import dev.oriel.engine.host.ExtensionRegistry;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.paint.Canvas;
import dev.oriel.engine.style.Image;

import java.util.List;

/** Dispatches optional image operations without knowing their host-specific resource scheme. */
public final class ImageProviders {
    private static final List<ImageProvider> PROVIDERS = ExtensionRegistry.providers(ImageProvider.class);
    private ImageProviders() {}

    public static Image fromUrl(String url) {
        for (ImageProvider provider : PROVIDERS) {
            Image image = provider.fromUrl(url);
            if (image != null) return image;
        }
        return null;
    }

    public static Image function(String name, List<ComponentValue> arguments) {
        for (ImageProvider provider : PROVIDERS) {
            Image image = provider.function(name, arguments);
            if (image != null) return image;
        }
        return null;
    }

    public static boolean handles(Image image) {
        return PROVIDERS.stream().anyMatch(provider -> provider.handles(image));
    }

    public static float[] size(Document document, Image image) {
        for (ImageProvider provider : PROVIDERS) if (provider.handles(image)) return provider.size(document, image);
        return null;
    }

    public static void paint(Canvas canvas, Image image, float x, float y, float width, float height, int tint) {
        for (ImageProvider provider : PROVIDERS) {
            if (provider.handles(image)) { provider.paint(canvas, image, x, y, width, height, tint); return; }
        }
        throw new IllegalStateException("No provider for image " + image);
    }
}
