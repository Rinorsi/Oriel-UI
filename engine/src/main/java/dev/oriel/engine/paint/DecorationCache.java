package dev.oriel.engine.paint;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.layout.Box;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Image;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/** Bounded cache of resource-independent decoration programs; live content is always painted normally. */
final class DecorationCache {
    private static final long MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;
    private record Entry(ComputedStyle style, float[] geometry, DisplayList commands, long bytes) {}
    private final Map<Element, Entry> entries = new IdentityHashMap<>();
    private Box root;
    private long bytes, hits, misses, evictions;

    void begin(Box root) {
        if (this.root != root) {
            entries.clear();
            bytes = 0;
            this.root = root;
        }
        hits = misses = evictions = 0;
    }

    static boolean eligible(ComputedStyle style, Geometry geometry) {
        if (geometry.element == null || style.borderImageSource.image() != null) return false;
        // Animated used styles are replaced every frame; capturing them only adds transient programs.
        if (style != geometry.element.baseStyle) return false;
        boolean gradient = false;
        for (var layer : style.backgroundLayers) {
            Image image = layer.image();
            if (image == null) continue;
            if (!(image instanceof Image.LinearGradient || image instanceof Image.RadialGradient
                    || image instanceof Image.ConicGradient)) return false;
            gradient = true;
        }
        // Cheap flat decorations cost less to paint than to capture. Fonts and textures have no cache entries.
        return gradient;
    }

    DisplayList find(ComputedStyle style, Geometry geometry, float dp) {
        Entry entry = entries.get(geometry.element);
        if (entry != null && entry.style == style && matches(entry.geometry, geometry, dp)) {
            hits++;
            return entry.commands;
        }
        misses++;
        if (entry != null) { entries.remove(geometry.element); bytes -= entry.bytes; }
        return null;
    }

    void put(ComputedStyle style, Geometry geometry, float dp, DisplayList commands) {
        commands = commands.batched();
        long cost = commands.payloadBytes() + 256;
        if (cost > MAX_BYTES) return;
        if (entries.size() == MAX_ENTRIES || bytes + cost > MAX_BYTES) {
            evictions += entries.size();
            entries.clear();
            bytes = 0;
        }
        Entry previous = entries.put(geometry.element, new Entry(style, snapshot(geometry, dp), commands, cost));
        if (previous != null) bytes -= previous.bytes;
        bytes += cost;
    }

    private static float[] snapshot(Geometry g, float dp) {
        float[] values = {g.x, g.y, g.width, g.height, dp, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        System.arraycopy(g.border, 0, values, 5, 4);
        System.arraycopy(g.padding, 0, values, 9, 4);
        System.arraycopy(g.radii, 0, values, 13, 8);
        return values;
    }

    private static boolean matches(float[] a, Geometry g, float dp) {
        return a[0] == g.x && a[1] == g.y && a[2] == g.width && a[3] == g.height && a[4] == dp
                && Arrays.equals(a, 5, 9, g.border, 0, 4) && Arrays.equals(a, 9, 13, g.padding, 0, 4)
                && Arrays.equals(a, 13, 21, g.radii, 0, 8);
    }

    Map<String, Long> statistics() {
        return Map.of("hits", hits, "misses", misses, "evictions", evictions,
                "entries", (long) entries.size(), "payloadBytes", bytes);
    }
}
