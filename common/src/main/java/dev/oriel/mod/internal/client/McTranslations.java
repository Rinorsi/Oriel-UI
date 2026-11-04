package dev.oriel.mod.internal.client;

import com.google.gson.JsonParser;
import dev.oriel.mod.internal.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Exact-locale library messages, merged in Minecraft resource-pack order without its implicit English fallback. */
final class McTranslations {
    private static final Map<String, Map<String, String>> LOCALES = new HashMap<>();
    private static Language source;

    private McTranslations() {}

    static String lookup(String locale, String key) {
        if (source != Language.getInstance()) {
            source = Language.getInstance();
            LOCALES.clear();
        }
        return LOCALES.computeIfAbsent(locale, McTranslations::load).get(key);
    }

    private static Map<String, String> load(String locale) {
        Map<String, String> values = new HashMap<>();
        var id = Constants.id("lang/" + locale + ".json");
        for (var resource : Minecraft.getInstance().getResourceManager().getResourceStack(id)) {
            try (var input = resource.open(); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                        .forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
            } catch (java.io.IOException | RuntimeException e) {
                Constants.LOG.warn("Cannot read translation resource {}", id, e);
            }
        }
        return Map.copyOf(values);
    }
}
