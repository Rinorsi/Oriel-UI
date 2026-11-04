package dev.oriel.kit;

import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.i18n.Messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Kit messages use the host's resource packs first and the same bundled language files in tests and previews. */
public final class Lang {
    private static final Map<String, String> ZH = load("zh_cn"), EN = load("en_us");
    static { register(); }
    private Lang() {}

    static void register() {
        Messages.registerBundle("oriel.", (language, key) ->
                language.equals("zh_cn") ? ZH.get(key) : language.equals("en_us") ? EN.get(key) : null);
    }

    public static String t(Document doc, String key, Object... args) {
        return doc.messages().text(key, args);
    }

    public static String plural(Document doc, String key, long count, Object... args) {
        return doc.messages().plural(key, count, args);
    }

    /** Static fixture metadata may contain a key; game-provided or user-authored names remain literal. */
    public static String value(Document doc, String value) {
        return value != null && value.startsWith("oriel.ui.") ? t(doc, value) : value;
    }

    /** Text inserted into an existing markup fragment, never markup supplied by a translator. */
    public static String html(Document doc, String key, Object... args) {
        return t(doc, key, args).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Resolves only library-owned fixture data; server-supplied item names and components are never rewritten. */
    public static com.google.gson.JsonElement fixture(Document doc, com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            var object = new com.google.gson.JsonObject();
            value.getAsJsonObject().entrySet().forEach(e -> object.add(e.getKey(), fixture(doc, e.getValue())));
            return object;
        }
        if (value.isJsonArray()) {
            var array = new com.google.gson.JsonArray();
            value.getAsJsonArray().forEach(e -> array.add(fixture(doc, e)));
            return array;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return new com.google.gson.JsonPrimitive(value(doc, value.getAsString()));
        }
        return value.deepCopy();
    }

    private static Map<String, String> load(String language) {
        String path = "/assets/oriel/lang/" + language + ".json";
        try (InputStream stream = Lang.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing language resource: " + path);
            Map<String, String> messages = new HashMap<>();
            JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(entry -> messages.put(entry.getKey(), entry.getValue().getAsString()));
            return Map.copyOf(messages);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }
}
