package dev.oriel.engine.layout;

import com.google.gson.JsonObject;
import dev.oriel.engine.host.Host;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Shared diagnostic envelope, allocated only when a solver trace is enabled. */
final class LayoutTraceSink {
    private static final String SESSION = UUID.randomUUID().toString();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private LayoutTraceSink() {}

    static void emit(LayoutBox container, JsonObject data) {
        JsonObject event = new JsonObject();
        event.addProperty("schemaVersion", 1);
        event.addProperty("sessionId", SESSION);
        event.addProperty("seq", SEQUENCE.incrementAndGet());
        event.addProperty("time", Instant.now().toString());
        event.addProperty("environment", System.getProperty("oriel.layout.traceEnvironment", "engine"));
        event.addProperty("phase", "layout");
        event.addProperty("severity", "info");
        event.addProperty("code", "layout." + data.get("context").getAsString() + "." + data.get("phase").getAsString());
        event.addProperty("message", data.get("reason").getAsString());
        JsonObject source = new JsonObject();
        source.addProperty("url", container.element.ownerDocument().url());
        source.addProperty("line", container.element.sourceLine());
        event.add("source", source);
        event.add("data", data);
        String json = event.toString();
        if (Boolean.getBoolean("oriel.layout.traceStdout")) {
            System.out.println(json);
            return;
        }
        String file = System.getProperty("oriel.layout.traceFile");
        if (file == null) container.element.ownerDocument().host().log(Host.LogLevel.DEBUG, json);
        else try {
            Files.writeString(Path.of(file), json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot append layout trace to " + file, e);
        }
    }

    static void number(JsonObject data, String key, float value) {
        data.addProperty(key, Float.isFinite(value) ? value : null);
    }
}
