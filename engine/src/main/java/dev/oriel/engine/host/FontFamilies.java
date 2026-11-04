package dev.oriel.engine.host;

import java.util.Locale;
import java.util.Map;

/**
 * Font family names and the Minecraft fonts they mean: the one mapping the style engine and every host share, so a
 * family resolves to the same font in game, in the previewer and in tests.
 *
 * <p>CSS generic families stand for Minecraft's fonts ({@code monospace} is {@code minecraft:uniform}, the others
 * {@code minecraft:default}); other names are font ids ({@code assets/<ns>/font/<id>.json}), read as Minecraft reads
 * ids: without a namespace they are {@code minecraft:}.
 */
public final class FontFamilies {
    public static final String DEFAULT = "minecraft:default";
    public static final String UNIFORM = "minecraft:uniform";
    private FontFamilies() {}
    public static String computed(String family) { return ExtensionRegistry.compatibility().computedFamily(family); }
    public static String fontId(String family) { return ExtensionRegistry.compatibility().fontId(family); }
}
