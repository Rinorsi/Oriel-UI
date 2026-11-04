package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.host.Host;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A host for kit tests: pages and stylesheets from the classpath ({@code ns:path} is {@code assets/ns/path}), a
 * monospace font (6 px a character, CJK 9 px), fixed texture sizes, and records of what pages send and log.
 */
final class KitHost implements Host {
    final List<String> sent = new ArrayList<>();
    final List<String> logs = new ArrayList<>();
    final List<String> navigations = new ArrayList<>();
    final Map<String, float[]> imageSizes = new HashMap<>();
    boolean closed;
    /** What {@link #send} answers: false is a page with no server (the previewer), true one that has one. */
    boolean acceptSends;

    @Override
    public FontMetrics fonts() {
        return new FontMetrics() {
            @Override
            public float width(String text, FontSpec font) {
                float w = 0;
                for (int i = 0; i < text.length(); ) {
                    int cp = text.codePointAt(i);
                    w += (cp >= 0x2E80 ? 9 : 6) * font.scale();
                    i += Character.charCount(cp);
                }
                return w;
            }
        };
    }

    @Override
    public String loadText(String url) {
        int colon = url.indexOf(':');
        String path = "assets/" + url.substring(0, colon) + "/" + url.substring(colon + 1);
        try (InputStream in = KitHost.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public float[] imageSize(String url) {
        return imageSizes.getOrDefault(url, new float[] {8, 8});
    }

    @Override
    public void log(LogLevel level, String message) {
        logs.add(level + " " + message);
        if (level == LogLevel.ERROR) throw new AssertionError(message);
    }

    @Override
    public void reportError(String message, Throwable error) {
        throw new AssertionError(message, error);
    }

    @Override
    public boolean send(String channel, String json) {
        sent.add(channel + " " + json);
        return acceptSends;
    }

    @Override
    public void navigate(String url) {
        navigations.add(url);
    }

    @Override
    public void close() {
        closed = true;
    }

    /**
     * Loads a kit page in a 427×240 viewport (the GUI size of an 854×480 window at scale 2) with {@code controller}
     * as its controller, and runs its first frame.
     */
    Document open(String url, PageControllers.Controller controller) {
        return open(url, controller, 427, 240);
    }

    /** {@link #open(String, PageControllers.Controller)} in a viewport of the given GUI size. */
    Document open(String url, PageControllers.Controller controller, int width, int height) {
        OrielKit.register();
        PageControllers.register(url, () -> controller);
        Document doc = Document.parse(this, url, loadText(url), null, new Viewport(width, height, 2));
        doc.frame(0);
        if (doc.error() != null) throw new AssertionError("page stopped", doc.error());
        return doc;
    }

    /** Presses and releases {@code button} at the centre of {@code selector}'s first match, with frames between. */
    static void click(Document doc, String selector, int button, Modifiers mods, double[] clock) {
        float[] c = centre(doc, selector);
        doc.input().mouseMove(c[0], c[1], mods);
        doc.input().mouseDown(c[0], c[1], button, mods);
        frame(doc, clock, 16);
        doc.input().mouseUp(c[0], c[1], button, mods);
        frame(doc, clock, 16);
    }

    /** Drags with the primary button from one selector's centre to another's. */
    static void drag(Document doc, String from, String to, int button, double[] clock) {
        float[] a = centre(doc, from), b = centre(doc, to);
        doc.input().mouseMove(a[0], a[1], Modifiers.NONE);
        doc.input().mouseDown(a[0], a[1], button, Modifiers.NONE);
        frame(doc, clock, 16);
        for (int i = 1; i <= 4; i++) {
            doc.input().mouseMove(a[0] + (b[0] - a[0]) * i / 4, a[1] + (b[1] - a[1]) * i / 4, Modifiers.NONE);
            frame(doc, clock, 16);
        }
        doc.input().mouseUp(b[0], b[1], button, Modifiers.NONE);
        frame(doc, clock, 16);
    }

    static float[] centre(Document doc, String selector) {
        float[] r = doc.querySelector(selector).getBoundingClientRect();
        return new float[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    /** Advances the page's clock by {@code ms} and runs a frame. */
    static void frame(Document doc, double[] clock, double ms) {
        clock[0] += ms;
        doc.frame(clock[0]);
        if (doc.error() != null) throw new AssertionError("page stopped", doc.error());
    }
}
