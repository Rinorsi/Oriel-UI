package dev.oriel.engine.dom;

/** Adapts the unchanged text expansion implementation to the core lifecycle hook. */
public final class McTextExpansion implements ElementExpansion {
    public String tag() { return MinecraftText.TAG; }
    public boolean expandsOn(Element element, String attribute) { return MinecraftText.expandsOn(element, attribute); }
    public boolean expand(Document document, Element element) { return MinecraftText.expand(document, element); }
}
