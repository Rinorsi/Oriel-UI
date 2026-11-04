package dev.oriel.engine.host;

import java.util.Map;

/** Font policy supplied by a compatibility profile, independently of glyph measurement. */
public interface FontProvider {
    float defaultSize();
    float normalLineHeight();
    String defaultFamily();
    String monospaceFamily();
    String computedFamily(String family);
    String fontId(String family);
    Map<String, Float> sizeKeywords();
    float sizeStep();
    float glyphHeightAtNativeSize();
    float ascentAtNativeSize();
}
