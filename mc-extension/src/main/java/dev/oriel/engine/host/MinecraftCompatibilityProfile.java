package dev.oriel.engine.host;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** The original Minecraft defaults, installed as a host compatibility policy. */
public final class MinecraftCompatibilityProfile implements CompatibilityProfile {
    public static final String DEFAULT = "minecraft:default";
    public static final String UNIFORM = "minecraft:uniform";

    private static final Map<String, String> GENERIC = Map.of("monospace", UNIFORM, "ui-monospace", UNIFORM,
            "serif", DEFAULT, "sans-serif", DEFAULT, "system-ui", DEFAULT, "ui-serif", DEFAULT, "ui-sans-serif", DEFAULT,
            "cursive", DEFAULT, "fantasy", DEFAULT);


    public float defaultSize() { return 8f; }
    public float normalLineHeight() { return 9f / 8f; }
    public String defaultFamily() { return DEFAULT; }
    public String monospaceFamily() { return UNIFORM; }
    public String computedFamily(String family) { return GENERIC.getOrDefault(family.toLowerCase(Locale.ROOT), family); }
    public String fontId(String family) {
        String id = computedFamily(family.strip()).toLowerCase(Locale.ROOT);
        return id.indexOf(':') > 0 ? id : "minecraft:" + id;
    }
    public Map<String, Float> sizeKeywords() {
        return Map.of("xx-small", 4f, "x-small", 5f, "small", 6f, "medium", 8f, "large", 12f, "x-large", 16f, "xx-large", 24f, "xxx-large", 32f);
    }
    public float sizeStep() { return 1.5f; }
    public float glyphHeightAtNativeSize() { return 9f; }
    public float ascentAtNativeSize() { return 7f; }
    public String nativeShadowKeyword() { return "minecraft"; }
    public String clickSound() { return "minecraft:ui.button.click"; }
    public String rangeThumb(boolean highlighted) { return highlighted ? "minecraft:widget/slider_handle_highlighted" : "minecraft:widget/slider_handle"; }
    public String namedColors() { return "mc-black 000000 mc-dark-blue 0000aa mc-dark-green 00aa00 mc-dark-aqua 00aaaa mc-dark-red aa0000 "
                + "mc-dark-purple aa00aa mc-gold ffaa00 mc-gray aaaaaa mc-dark-gray 555555 mc-blue 5555ff "
                + "mc-green 55ff55 mc-aqua 55ffff mc-red ff5555 mc-light-purple ff55ff mc-yellow ffff55 mc-white ffffff"; }
    public String userAgentStyle() {
        try (InputStream in = getClass().getResourceAsStream("/oriel/ua.css")) {
            if (in == null) throw new IllegalStateException("Missing /oriel/ua.css in the compatibility extension");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
