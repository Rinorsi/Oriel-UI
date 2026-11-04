package dev.oriel.kit.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Shell;

/**
 * The player's effects beside a vanilla inventory: the potion HUD's full panel ({@link PotionHudPreset#mountPanel}) in
 * a window of its own, where its host puts it. The host sends {@value #PLACE} (x, y, width, height in GUI px); below
 * {@link #NARROW} px wide the groups fold to strips of icons and the title to its icon.
 */
public final class InventoryEffectsPreset implements PageControllers.Controller {
    /** The data key of the panel's place. */
    public static final String PLACE = "place";
    static final int NARROW = 100;

    private Element side;
    private PotionHudPreset effects;

    @Override
    public void attach(Document doc) {
        Shell.setup(doc);
        side = Dom.byId(doc, "side");
        effects = new PotionHudPreset();
        effects.mountPanel(doc, Dom.byId(doc, "panel"), false);
        if (doc.initialData() != null) place(JsonParser.parseString(doc.initialData()).getAsJsonObject().getAsJsonObject(PLACE));
        doc.addDataListener((key, json) -> {
            if (json == null) return;
            JsonObject value = JsonParser.parseString(json).getAsJsonObject();
            if (PLACE.equals(key)) place(value);
            else if (key == null && value.has(PLACE)) place(value.getAsJsonObject(PLACE));
        });
    }

    private void place(JsonObject place) {
        if (place == null) return;
        int width = place.get("width").getAsInt();
        new Dom.Style(side).set("left", place.get("x").getAsInt() + "px").set("top", place.get("y").getAsInt() + "px")
                .set("width", width + "px").set("max-height", place.get("height").getAsInt() + "px").apply();
        boolean narrow = width < NARROW;
        side.toggleClass("narrow", narrow);
        for (Element group : side.querySelectorAll(".group")) group.toggleClass("folded", narrow);
    }
}
