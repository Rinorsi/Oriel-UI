package dev.oriel.kit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.oriel.engine.dom.Document;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Language-neutral library text in page data. Arguments are text, never markup; only the reader chooses a language. */
public record LocalizedMessage(String key, List<String> args, Long count) {
    public LocalizedMessage {
        Objects.requireNonNull(key);
        args = List.copyOf(args);
    }

    public static LocalizedMessage text(String key, Object... args) {
        return new LocalizedMessage(key, Arrays.stream(args).map(String::valueOf).toList(), null);
    }

    public static LocalizedMessage plural(String key, long count, Object... args) {
        return new LocalizedMessage(key, Arrays.stream(args).map(String::valueOf).toList(), count);
    }

    public JsonObject json() {
        JsonObject message = new JsonObject();
        message.addProperty("key", key);
        JsonArray values = new JsonArray();
        args.forEach(values::add);
        message.add("args", values);
        if (count != null) message.addProperty("count", count);
        return message;
    }

    public static LocalizedMessage fromJson(JsonObject message) {
        List<String> args = new java.util.ArrayList<>();
        message.getAsJsonArray("args").forEach(arg -> args.add(arg.getAsString()));
        return new LocalizedMessage(message.get("key").getAsString(), args,
                message.has("count") ? message.get("count").getAsLong() : null);
    }

    public String render(Document doc) {
        return count == null ? Lang.t(doc, key, args.toArray()) : Lang.plural(doc, key, count, args.toArray());
    }
}
