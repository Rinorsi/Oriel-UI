package dev.oriel.engine.host;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/** Class-loader-local optional host extensions. No implementation dependency is required by core. */
public final class ExtensionRegistry {
    private static final CompatibilityProfile PROFILE = profile();

    private ExtensionRegistry() {}

    public static CompatibilityProfile compatibility() { return PROFILE; }

    public static <T> List<T> providers(Class<T> type) {
        return ServiceLoader.load(type, ExtensionRegistry.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get).toList();
    }

    private static CompatibilityProfile profile() {
        List<CompatibilityProfile> profiles = providers(CompatibilityProfile.class);
        if (profiles.size() > 1) throw new IllegalStateException("Multiple compatibility profiles are installed");
        return profiles.isEmpty() ? new NeutralProfile() : profiles.getFirst();
    }

    private static final class NeutralProfile implements CompatibilityProfile {
        public float defaultSize() { return 16; }
        public float normalLineHeight() { return 1.2f; }
        public String defaultFamily() { return "serif"; }
        public String monospaceFamily() { return "monospace"; }
        public String computedFamily(String family) { return family; }
        public String fontId(String family) { return family.strip().toLowerCase(Locale.ROOT); }
        public Map<String, Float> sizeKeywords() {
            return Map.of("xx-small", 9f, "x-small", 10f, "small", 13f, "medium", 16f,
                    "large", 18f, "x-large", 24f, "xx-large", 32f, "xxx-large", 48f);
        }
        public float sizeStep() { return 1.2f; }
        public float glyphHeightAtNativeSize() { return defaultSize() * normalLineHeight(); }
        public float ascentAtNativeSize() { return defaultSize() * 0.8f; }
        public String clickSound() { return ""; }
        public String rangeThumb(boolean highlighted) { return ""; }
        public String userAgentStyle() { return "html, body, div, p { display: block }"; }
        public String namedColors() { return ""; }
        public String nativeShadowKeyword() { return ""; }
    }
}
