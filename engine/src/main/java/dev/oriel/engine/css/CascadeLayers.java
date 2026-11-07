package dev.oriel.engine.css;

import java.util.LinkedHashMap;
import java.util.Map;

/** A layer tree preserves first appearance and keeps nested layers inside their parent's interval. */
final class CascadeLayers {
    static final class Layer {
        final String name;
        final Map<String, Layer> children = new LinkedHashMap<>();
        int rank;

        Layer(String name) { this.name = name; }
    }

    final Layer root = new Layer("");
    private int anonymous;

    Layer register(Layer parent, String name) {
        if (name.isEmpty()) {
            Layer layer = new Layer(parent.name + ".<anonymous-" + ++anonymous + ">");
            parent.children.put(layer.name, layer);
            return layer;
        }
        Layer current = parent;
        for (String part : name.split("[.]")) {
            Layer at = current;
            current = current.children.computeIfAbsent(part, key -> new Layer(at.name.isEmpty() ? key : at.name + "." + key));
        }
        return current;
    }

    void finish() { number(root, 0); }

    private static int number(Layer layer, int rank) {
        for (Layer child : layer.children.values()) rank = number(child, rank);
        layer.rank = rank++; // Direct declarations follow this layer's sublayers.
        return rank;
    }
}
