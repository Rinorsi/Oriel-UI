package dev.oriel.engine.dom;

import dev.oriel.engine.host.ExtensionRegistry;

import java.util.List;

/** Optional element semantics, invoked at the same lifecycle points as ordinary DOM content. */
public interface ElementExpansion {
    String tag();
    boolean expandsOn(Element element, String attribute);
    boolean expand(Document document, Element element);

    List<ElementExpansion> INSTALLED = ExtensionRegistry.providers(ElementExpansion.class);

    static void load(Document document) {
        for (ElementExpansion expansion : INSTALLED) {
            for (Element element : document.getElementsByTagName(expansion.tag())) expansion.expand(document, element);
        }
    }

    static boolean connected(Document document, Element element) {
        for (ElementExpansion expansion : INSTALLED) {
            if (element.tagName().equals(expansion.tag()) && expansion.expand(document, element)) return true;
        }
        return false;
    }

    static void attributeChanged(Document document, Element element, String attribute) {
        for (ElementExpansion expansion : INSTALLED) {
            if (expansion.expandsOn(element, attribute)) expansion.expand(document, element);
        }
    }
}
