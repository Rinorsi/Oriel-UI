package dev.oriel.engine.layout;

import com.google.gson.JsonObject;
import dev.oriel.engine.host.Host;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Explicit developer diagnostics. No events or buffers are allocated when tracing is disabled. */
final class FlexTrace {
    private final String query;
    private final Path output;

    private FlexTrace(String query) {
        this.query = query;
        String file = System.getProperty("oriel.layout.traceFile");
        output = file == null ? null : Path.of(file);
    }

    static FlexTrace configured() {
        String query = System.getProperty("oriel.layout.flex.trace", "off");
        return switch (query) {
            case "off" -> null;
            case "why-size", "why-position", "all" -> new FlexTrace(query);
            default -> throw new IllegalArgumentException("oriel.layout.flex.trace must be off, why-size, why-position or all: " + query);
        };
    }

    void item(LayoutBox container, LayoutBox item, String phase, int line, int iteration,
              float basis, float hypothetical, float target, float cross, float free, float violation,
              boolean frozen, float mainOffset, float crossOffset) {
        boolean position = phase.equals("position");
        if ((query.equals("why-position") && !position) || (query.equals("why-size") && position)) return;
        JsonObject event = new JsonObject();
        event.addProperty("schema", 1);
        event.addProperty("context", "flex");
        event.addProperty("query", position ? "why-position" : "why-size");
        event.addProperty("phase", phase);
        event.addProperty("source", container.element.ownerDocument().url());
        event.addProperty("container", container.element.toString());
        event.addProperty("item", item.element.toString());
        event.addProperty("kind", item.kind.toString());
        event.addProperty("sourceLine", item.element.sourceLine());
        event.addProperty("line", line);
        event.addProperty("iteration", iteration);
        number(event, "baseSize", basis);
        number(event, "hypotheticalSize", hypothetical);
        number(event, "targetSize", target);
        number(event, "crossSize", cross);
        number(event, "freeSpace", free);
        number(event, "violation", violation);
        event.addProperty("frozen", frozen);
        number(event, "mainOffset", mainOffset);
        number(event, "crossOffset", crossOffset);
        number(event, "grow", item.style.flexGrow);
        number(event, "shrink", item.style.flexShrink);
        event.addProperty("direction", container.style.flexDirection.toString());
        event.addProperty("reason", position ? "Final flex-relative offsets after gap, auto margins and alignment"
                : frozen ? "Frozen target: inflexible item, balanced iteration or selected min/max violation"
                : "Distributing remaining free space");
        String json = event.toString();
        if (output == null) container.element.ownerDocument().host().log(Host.LogLevel.DEBUG, json);
        else {
            try {
                Files.writeString(output, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot append flex trace to " + output, e);
            }
        }
    }

    private static void number(JsonObject event, String key, float value) {
        event.addProperty(key, Float.isFinite(value) ? value : null);
    }
}
