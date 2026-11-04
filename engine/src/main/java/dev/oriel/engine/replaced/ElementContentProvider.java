package dev.oriel.engine.replaced;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.ReplacedContent;

import java.util.Map;
import java.util.function.Function;

/** Host-neutral registration boundary for optional replaced elements. */
public interface ElementContentProvider {
    Map<String, Function<Element, ReplacedContent>> elements();
}
