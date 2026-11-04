package dev.oriel.engine.host;

/** Optional host policy. Resource names and default control presentation belong to its implementation. */
public interface CompatibilityProfile extends FontProvider {
    String clickSound();
    String rangeThumb(boolean highlighted);
    String userAgentStyle();
    String namedColors();
    String nativeShadowKeyword();
}
