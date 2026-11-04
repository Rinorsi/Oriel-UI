package dev.oriel.engine.replaced;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.ReplacedContent;

import java.util.Map;
import java.util.function.Function;

/** The sprite element uses the original image sizing and paint path. */
public final class McElementContentProvider implements ElementContentProvider {
    public Map<String, Function<Element, ReplacedContent>> elements() {
        return Map.of("sprite", element -> new ImageContent(element, true));
    }
}
