package dev.oriel.kit.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Lang;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;

/** The local host supplies copy labels; all text is inserted as text, never as markup. */
public final class NestedNoticePreset implements PageControllers.Controller {
    @Override public void attach(Document doc) {
        Shell.setup(doc);
        JsonObject data = doc.initialData() == null ? new JsonObject() : JsonParser.parseString(doc.initialData()).getAsJsonObject();
        Dom.text(Dom.byId(doc, "parents"), data.has("parents") ? data.get("parents").getAsString() : "");
        Dom.text(Dom.byId(doc, "active"), data.has("active") ? Lang.t(doc, "oriel.ui.nested.active", data.get("active").getAsString()) : "");
        Dom.byId(doc, "never").addEventListener("change", event -> {
            JsonObject request = new JsonObject();
            request.addProperty("op", "nested-setting");
            request.addProperty("disable", Dom.byId(doc, "never").checked());
            doc.host().send(OrielKit.CHANNEL, request.toString());
        });
        Shell.activate(Dom.byId(doc, "dismiss"), () -> {
            JsonObject request = new JsonObject();
            request.addProperty("op", "nested-dismiss");
            request.addProperty("disable", Dom.byId(doc, "never").checked());
            if (!doc.host().send(OrielKit.CHANNEL, request.toString())) doc.host().close();
        });
    }
}
