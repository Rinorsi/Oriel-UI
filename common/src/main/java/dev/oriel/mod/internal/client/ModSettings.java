package dev.oriel.mod.internal.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.oriel.kit.OrielKit;
import dev.oriel.mod.client.OrielScreens;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.OrielConfig;
import dev.oriel.mod.internal.OrielConfig.Setting;
import dev.oriel.mod.internal.platform.Services;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local host for the player's settings page. No safety limits or developer switches are writable here. */
public final class ModSettings {
    private static final int MAX_REQUEST_CHARS = 1024;
    private static final List<Setting<?>> EDITABLE = List.of(
            OrielConfig.CLIENT_UI_SOUNDS, OrielConfig.CLIENT_UI_SOUND_VOLUME,
            OrielConfig.CLIENT_REDUCED_MOTION, OrielConfig.CLIENT_TYPING_NOTICE,
            OrielConfig.CLIENT_NESTED_NOTICE,
            OrielConfig.CLIENT_SERVER_PAGES, OrielConfig.CLIENT_WEB_LINKS, OrielConfig.CLIENT_SHAPE_SHADER);

    private ModSettings() {}

    public static void init() {
        Path file = Services.PLATFORM.configDir().resolve(OrielConfig.FILE_NAME);
        OrielScreens.onPageLoad(OrielKit.page("modsettings"), driver -> {
            // A server may open this URL too; it must never gain access to local preferences.
            if (driver.serverSession()) return;
            driver.patch("settings", state());
            // DocumentDriver.send enforces client.messageBurst/messagesPerSecond across links and reloads.
            driver.onMessage(OrielKit.CHANNEL, request -> {
                try {
                    String error = handle(file, request);
                    if (error != null) Constants.LOG.warn("Oriel: settings request refused: {}", error);
                } catch (IOException e) {
                    Constants.LOG.warn("Oriel: cannot save settings to {}: {}", file, e.toString());
                } finally {
                    driver.patch("settings", state());
                }
            });
        });
    }

    /** Returns a diagnostic on refusal; unknown operations are ignored. The driver's send budget limits writes. */
    static @Nullable String handle(Path file, JsonElement request) throws IOException {
        if (!bounded(request)) return "expected a flat settings request of at most 1024 characters";
        JsonObject object = request.getAsJsonObject();
        String op = string(object.get("op"));
        Map<String, String> changes = new LinkedHashMap<>();
        if ("reset".equals(op)) {
            for (Setting<?> setting : EDITABLE) changes.put(setting.key(), setting.defaultValue().toString());
        } else if ("set".equals(op)) {
            String key = string(object.get("key"));
            Setting<?> setting = EDITABLE.stream().filter(s -> s.key().equals(key)).findFirst().orElse(null);
            if (setting == null) return "key is not player-editable";
            JsonElement value = object.get("value");
            if (value == null || !value.isJsonPrimitive()) return "expected a scalar value for " + key;
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            Object def = setting.defaultValue();
            // The kit sends text for all controls; also accept native JSON booleans/numbers of the right type.
            if (!primitive.isString() && (def instanceof Boolean && !primitive.isBoolean()
                    || def instanceof Number && !primitive.isNumber()
                    || def instanceof Enum<?>)) return "wrong value type for " + key;
            changes.put(key, primitive.getAsString());
        } else return null;
        return OrielConfig.update(file, changes);
    }

    /** Bound the small flat protocol before serializing; deeply nested input cannot recurse here. */
    private static boolean bounded(JsonElement request) {
        if (request == null || !request.isJsonObject()) return false;
        JsonObject object = request.getAsJsonObject();
        if (object.size() > 3) return false;
        for (var field : object.entrySet()) {
            if (field.getKey().length() > MAX_REQUEST_CHARS) return false;
            JsonElement value = field.getValue();
            if (!value.isJsonNull() && (!value.isJsonPrimitive()
                    || value.getAsString().length() > MAX_REQUEST_CHARS)) return false;
        }
        return object.toString().length() <= MAX_REQUEST_CHARS;
    }

    private static String string(@Nullable JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    static JsonObject state() {
        synchronized (OrielConfig.class) {
            JsonArray items = new JsonArray();
            for (Setting<?> setting : EDITABLE) {
                JsonObject item = new JsonObject();
                Object def = setting.defaultValue();
                item.addProperty("key", setting.key());
                item.addProperty("type", def instanceof Boolean ? "bool" : def instanceof Number ? "number" : "choice");
                item.add("value", json(setting.get()));
                item.add("default", json(def));
                item.add("min", def instanceof Number ? new JsonPrimitive(0) : JsonNull.INSTANCE);
                item.add("max", def instanceof Number ? new JsonPrimitive(1) : JsonNull.INSTANCE);
                item.add("step", def instanceof Number ? new JsonPrimitive(0.05) : JsonNull.INSTANCE);
                JsonArray options = new JsonArray();
                if (def instanceof Enum<?> choice) {
                    for (Enum<?> option : choice.getDeclaringClass().getEnumConstants()) options.add(option.name());
                }
                item.add("options", options);
                // Every consumer reads dynamically; McCanvas captures shapeShader anew for each frame.
                item.addProperty("live", true);
                items.add(item);
            }
            JsonObject state = new JsonObject();
            state.add("items", items);
            return state;
        }
    }

    private static JsonPrimitive json(Object value) {
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        return new JsonPrimitive(((Enum<?>) value).name());
    }
}
