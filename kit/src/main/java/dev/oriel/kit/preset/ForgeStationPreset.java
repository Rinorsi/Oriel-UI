package dev.oriel.kit.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Shell;

/**
 * The repair forge as a real container: the page of the server's forge menu (ForgeMenu in the mod, {@code oriel:forge}).
 * The menu's slots are the page's {@code <slot>}s; this shows what the menu reports in its data - {@code progress} and
 * {@code duration} (ticks), {@code state} (0 idle, 1 working, 2 output blocked) and {@code selectedRecipe} - as the
 * arrow's fill, the forge's glow and embers, and the status line, and lights the output when there is one to take.
 */
public final class ForgeStationPreset implements PageControllers.Controller {
    static final int IDLE = 0, WORKING = 1, BLOCKED = 2;

    private Document doc;
    private Element work, fill, status, state, recipe, embers;

    @Override
    public void attach(Document document) {
        doc = document;
        Shell.setup(doc);
        work = Dom.byId(doc, "work");
        fill = Dom.byId(doc, "fill");
        status = Dom.byId(doc, "status");
        state = Dom.byId(doc, "state");
        recipe = Dom.byId(doc, "recipe");
        embers = Dom.byId(doc, "embers");
        if (doc.initialData() != null) update(JsonParser.parseString(doc.initialData()));
        doc.addDataListener((key, json) -> {
            if (key == null && json != null) update(JsonParser.parseString(json));
        });
    }

    private void update(JsonElement value) {
        if (!value.isJsonObject()) return;
        JsonObject o = value.getAsJsonObject();
        if (o.has("title")) Dom.text(Dom.byId(doc, "title"), o.get("title").getAsString());
        if (o.has("inventory")) Dom.text(Dom.byId(doc, "inv-label"), o.get("inventory").getAsString());
        int progress = o.has("progress") ? o.get("progress").getAsInt() : 0;
        int duration = o.has("duration") ? o.get("duration").getAsInt() : 0;
        int s = o.has("state") ? o.get("state").getAsInt() : IDLE;
        String selected = o.has("selectedRecipe") ? o.get("selectedRecipe").getAsString() : "";
        boolean output = hasItem(o, 2), input = hasItem(o, 0);
        new Dom.Style(fill).set("width", duration > 0 ? Math.min(100, progress * 100 / duration) + "%" : "0%").apply();
        boolean working = s == WORKING;
        work.toggleClass("working", working);
        work.toggleClass("done", output && !working);
        embers.toggleClass("off", !working);
        status.toggleClass("working", working);
        status.toggleClass("blocked", s == BLOCKED);
        status.toggleClass("ready", output && s != BLOCKED);
        Dom.text(state, switch (s) {
            case WORKING -> "正在修复…";
            case BLOCKED -> "先取走成品，才能继续";
            default -> output ? "修好了，取走成品" : input ? "放入对应的材料" : "放入一把受损的铁剑或钻石剑";
        });
        Dom.text(recipe, recipeName(selected));
    }

    /** Whether slot {@code index} of the menu holds an item. */
    private static boolean hasItem(JsonObject o, int index) {
        if (!o.has("slots")) return false;
        JsonArray slots = o.getAsJsonArray("slots");
        return index < slots.size() && !slots.get(index).getAsJsonObject().get("id").getAsString().isEmpty();
    }

    private static String recipeName(String id) {
        return switch (id) {
            case "oriel:repair_iron_sword" -> "铁剑 + 铁锭";
            case "oriel:repair_diamond_sword" -> "钻石剑 + 钻石";
            default -> "";
        };
    }
}
