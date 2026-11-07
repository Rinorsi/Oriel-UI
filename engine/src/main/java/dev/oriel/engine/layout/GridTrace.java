package dev.oriel.engine.layout;

import com.google.gson.JsonObject;
import dev.oriel.engine.layout.GridSolverPlacement.Placed;
import dev.oriel.engine.layout.GridSolverTracks.Mode;
import dev.oriel.engine.layout.GridSolverTracks.Track;

/** Placement, track sizing and baseline diagnostics for the candidate grid solver. */
final class GridTrace {
    private final String query;
    private GridTrace(String query) { this.query = query; }

    static GridTrace configured() {
        String query = System.getProperty("oriel.layout.grid.trace", "off");
        return switch (query) {
            case "off" -> null;
            case "why-size", "why-position", "all" -> new GridTrace(query);
            default -> throw new IllegalArgumentException("oriel.layout.grid.trace must be off, why-size, why-position or all: " + query);
        };
    }

    void placement(LayoutBox container, Placed item) {
        if (query.equals("why-size")) return;
        var data = event(container, "placement", "why-position", "Resolved grid lines after explicit and automatic placement");
        item(data, item);
        data.addProperty("autoFlow", container.style.gridAutoFlow.toString());
        data.addProperty("columnStartSpecified", item.box.style.gridColumnStart.toString());
        data.addProperty("rowStartSpecified", item.box.style.gridRowStart.toString());
        LayoutTraceSink.emit(container, data);
    }

    void contribution(LayoutBox container, Placed item, Axis axis, String constraint, float size) {
        if (query.equals("why-position")) return;
        var data = event(container, "contribution", "why-size", "Cached item contribution under this container's fixed constraints");
        item(data, item);
        data.addProperty("axis", axis.toString());
        data.addProperty("constraint", constraint);
        LayoutTraceSink.number(data, "size", size);
        LayoutTraceSink.emit(container, data);
    }

    void tracks(LayoutBox container, Axis axis, Track[] tracks, Mode mode, float available, String stage) {
        if (query.equals("why-position")) return;
        for (int i = 0; i < tracks.length; i++) {
            Track track = tracks[i];
            var data = event(container, "track", "why-size", "Track state at " + stage + " sizing stage");
            data.addProperty("axis", axis.toString());
            data.addProperty("stage", stage);
            data.addProperty("track", i);
            data.addProperty("mode", mode.toString());
            data.addProperty("minKind", track.minKind.toString());
            data.addProperty("maxKind", track.maxKind.toString());
            data.addProperty("collapsed", track.collapsed);
            LayoutTraceSink.number(data, "available", available);
            LayoutTraceSink.number(data, "baseSize", track.base);
            LayoutTraceSink.number(data, "growthLimit", track.limit);
            LayoutTraceSink.emit(container, data);
        }
    }

    void position(LayoutBox container, Placed item, float width, float height, float baselineShift) {
        if (query.equals("why-size")) return;
        var data = event(container, "position", "why-position", "Grid-relative position after area alignment and baseline sharing; before relative positioning");
        item(data, item);
        LayoutTraceSink.number(data, "x", item.box.x);
        LayoutTraceSink.number(data, "y", item.box.y);
        LayoutTraceSink.number(data, "width", width);
        LayoutTraceSink.number(data, "height", height);
        LayoutTraceSink.number(data, "baselineShift", baselineShift);
        LayoutTraceSink.emit(container, data);
    }

    private static JsonObject event(LayoutBox box, String phase, String query, String reason) {
        var data = new JsonObject();
        data.addProperty("context", "grid");
        data.addProperty("phase", phase);
        data.addProperty("query", query);
        data.addProperty("container", box.element.toString());
        data.addProperty("reason", reason);
        return data;
    }

    private static void item(JsonObject data, Placed item) {
        data.addProperty("item", item.box.element.toString());
        data.addProperty("sourceLine", item.box.element.sourceLine());
        data.addProperty("columnStart", item.columnStart);
        data.addProperty("columnEnd", item.columnEnd);
        data.addProperty("rowStart", item.rowStart);
        data.addProperty("rowEnd", item.rowEnd);
    }
}
