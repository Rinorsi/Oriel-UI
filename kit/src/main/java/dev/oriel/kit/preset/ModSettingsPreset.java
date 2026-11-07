package dev.oriel.kit.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Lang;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import dev.oriel.kit.Slider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The mod's own settings. The game reports its client settings as the page's {@value #DATA} data
 * ({@code {"items": [{"key", "type": "bool|number|choice", "value", "default", "min", "max", "step", "options",
 * "live"}]}}) and takes {@code {"op": "set", "key": ..., "value": ...}} / {@code {"op": "reset"}} on
 * {@link OrielKit#CHANNEL}, writing each accepted change to its file at once. Without a game (the preview) the page
 * shows the defaults and changes stay on the page.
 */
public final class ModSettingsPreset implements PageControllers.Controller {
    public static final String DATA = "settings";

    /** What the page shows, grouped; a key the game reports that is not here is left out. */
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("sound", List.of("client.uiSounds", "client.uiSoundVolume"));
        GROUPS.put("display", List.of("client.reducedMotion", "client.shapeShader"));
        GROUPS.put("safety", List.of("client.serverPages", "client.webLinks", "client.typingNotice"));
    }
    private static final Map<String, String> GROUP_ICON = Map.of("sound", "music", "display", "sparkle", "safety", "shield");

    private static final String FIXTURE = "{\"items\":["
            + "{\"key\":\"client.uiSounds\",\"type\":\"bool\",\"value\":true,\"default\":true,\"live\":true},"
            + "{\"key\":\"client.uiSoundVolume\",\"type\":\"number\",\"value\":1.0,\"default\":1.0,\"min\":0,\"max\":1,\"step\":0.05,\"live\":true},"
            + "{\"key\":\"client.reducedMotion\",\"type\":\"bool\",\"value\":false,\"default\":false,\"live\":true},"
            + "{\"key\":\"client.shapeShader\",\"type\":\"bool\",\"value\":true,\"default\":true,\"live\":false},"
            + "{\"key\":\"client.serverPages\",\"type\":\"choice\",\"value\":\"ALLOW\",\"default\":\"ALLOW\",\"options\":[\"ALLOW\",\"ASK\",\"BLOCK\"],\"live\":true},"
            + "{\"key\":\"client.webLinks\",\"type\":\"choice\",\"value\":\"ASK\",\"default\":\"ASK\",\"options\":[\"ASK\",\"BLOCK\"],\"live\":true},"
            + "{\"key\":\"client.typingNotice\",\"type\":\"bool\",\"value\":true,\"default\":true,\"live\":true}]}";

    private record Item(String key, String type, String value, String def, double min, double max, double step, List<String> options, boolean live) {}

    /** One built control: how to show a value the game reports. */
    private interface Control {
        void show(String value);
    }

    private Document doc;
    private Element groups, savedMark;
    private final Map<String, Control> controls = new LinkedHashMap<>();
    private final Map<String, Item> items = new LinkedHashMap<>();
    private boolean built;

    @Override
    public void attach(Document document) {
        doc = document;
        Shell.setup(doc);
        groups = Dom.byId(doc, "groups");
        savedMark = Dom.byId(doc, "saved");
        Shell.activate(Dom.byId(doc, "reset"), this::reset);
        if (doc.initialData() != null) state(JsonParser.parseString(doc.initialData()));
        doc.addDataListener((key, json) -> {
            if (json == null) return;
            JsonElement value = JsonParser.parseString(json);
            if (DATA.equals(key)) state(value);
            else if (key == null && value.isJsonObject() && value.getAsJsonObject().has(DATA)) state(value.getAsJsonObject().get(DATA));
        });
        if (!built) state(JsonParser.parseString(FIXTURE));
    }

    // ---- State ----

    private void state(JsonElement value) {
        JsonElement root = value.isJsonObject() && value.getAsJsonObject().has(DATA) ? value.getAsJsonObject().get(DATA) : value;
        if (!root.isJsonObject() || !root.getAsJsonObject().has("items")) return;
        JsonArray array = root.getAsJsonObject().getAsJsonArray("items");
        Map<String, Item> fresh = new LinkedHashMap<>();
        for (JsonElement e : array) {
            if (!e.isJsonObject()) continue;
            Item item = parse(e.getAsJsonObject());
            if (item != null) fresh.put(item.key(), item);
        }
        if (fresh.isEmpty()) return;
        boolean first = !built;
        items.clear();
        items.putAll(fresh);
        if (first || !sameShape(fresh)) build();
        for (Item item : items.values()) {
            Control c = controls.get(item.key());
            if (c != null) c.show(item.value());
        }
    }

    private boolean sameShape(Map<String, Item> fresh) {
        return controls.keySet().equals(fresh.keySet());
    }

    private static Item parse(JsonObject o) {
        if (!o.has("key") || !o.has("type")) return null;
        String type = o.get("type").getAsString();
        List<String> options = new ArrayList<>();
        if (o.has("options")) for (JsonElement e : o.getAsJsonArray("options")) options.add(e.getAsString());
        return new Item(o.get("key").getAsString(), type, text(o, "value"), text(o, "default"),
                num(o, "min", 0), num(o, "max", 1), num(o, "step", 0.05), options, !o.has("live") || o.get("live").getAsBoolean());
    }

    private static String text(JsonObject o, String field) {
        return o.has(field) && !o.get(field).isJsonNull() ? o.get(field).getAsString() : "";
    }

    private static double num(JsonObject o, String field, double fallback) {
        return o.has(field) && o.get(field).isJsonPrimitive() ? o.get(field).getAsDouble() : fallback;
    }

    // ---- Building ----

    private void build() {
        built = true;
        controls.clear();
        groups.removeAllChildren();
        for (Map.Entry<String, List<String>> g : GROUPS.entrySet()) {
            List<Item> shown = new ArrayList<>();
            for (String key : g.getValue()) if (items.containsKey(key)) shown.add(items.get(key));
            if (shown.isEmpty()) continue;
            Element panel = Dom.html(doc, "<div class=\"group o-tile\"><h3><img class=\"o-ico\" src=\"../textures/icons/" + GROUP_ICON.get(g.getKey())
                    + ".png\"><span></span></h3></div>");
            Dom.text(Dom.query(panel, "h3 span"), Lang.t(doc, "oriel.ui.modsettings.group." + g.getKey()));
            for (Item item : shown) panel.appendChild(row(item));
            groups.appendChild(panel);
        }
    }

    private Element row(Item item) {
        Element row = Dom.html(doc, "<div class=\"item\"><div class=\"label\"><div class=\"n\"><span></span></div><div class=\"h\"></div></div><div class=\"control\"></div></div>");
        row.setAttribute("data-key", item.key());
        Dom.text(Dom.query(row, ".n span"), Lang.t(doc, "oriel.ui.modsettings." + item.key() + ".name"));
        Dom.text(Dom.query(row, ".h"), Lang.t(doc, "oriel.ui.modsettings." + item.key() + ".hint"));
        if (!item.live()) Dom.query(row, ".n").appendChild(Dom.el(doc, "em", null, Lang.t(doc, "oriel.ui.modsettings.restart")));
        Element control = Dom.query(row, ".control");
        switch (item.type()) {
            case "bool" -> toggle(item, control);
            case "number" -> slider(item, control);
            default -> choice(item, control);
        }
        return row;
    }

    private void toggle(Item item, Element control) {
        Element t = Dom.el(doc, "div", "ore-toggle", null);
        t.setAttribute("tabindex", "0");
        boolean[] on = {Boolean.parseBoolean(item.value())};
        Shell.activate(t, () -> {
            on[0] = !on[0];
            t.toggleClass("on", on[0]);
            change(item.key(), Boolean.toString(on[0]));
        });
        control.appendChild(t);
        controls.put(item.key(), v -> {
            on[0] = Boolean.parseBoolean(v);
            t.toggleClass("on", on[0]);
        });
        t.toggleClass("on", on[0]);
    }

    private void slider(Item item, Element control) {
        Element el = Dom.html(doc, "<div class=\"ore-slider\" tabindex=\"0\"><div class=\"track\"></div><div class=\"fill\"></div><div class=\"thumb\"></div></div>");
        Element val = Dom.el(doc, "span", "val", null);
        control.appendChild(el);
        control.appendChild(val);
        double span = Math.max(item.max() - item.min(), 1e-9);
        int steps = Math.max(1, (int) Math.round(span / Math.max(item.step(), 1e-9)));
        Slider slider = new Slider(doc, el, steps);
        Consumer<Double> label = v -> Dom.text(val, Math.round(v * 100) + "%");
        double start = parse(item.value(), item.min());
        slider.set((start - item.min()) / span);
        label.accept(start);
        slider.onInput(f -> label.accept(item.min() + f * span));
        slider.onCommit(f -> change(item.key(), format(item.min() + Math.round(f * steps) * (span / steps))));
        controls.put(item.key(), v -> {
            if (slider.dragging()) return;
            double x = parse(v, item.min());
            slider.set((x - item.min()) / span);
            label.accept(x);
        });
    }

    private void choice(Item item, Element control) {
        Element seg = Dom.el(doc, "div", "o-seg", null);
        List<Element> spans = new ArrayList<>();
        for (String option : item.options()) {
            Element s = Dom.el(doc, "span", null, Lang.t(doc, "oriel.ui.modsettings.opt." + option));
            s.setAttribute("tabindex", "0");
            s.toggleClass("on", option.equals(item.value()));
            Shell.activate(s, () -> {
                for (Element o : spans) o.toggleClass("on", o == s);
                change(item.key(), option);
            });
            spans.add(s);
            seg.appendChild(s);
        }
        control.appendChild(seg);
        controls.put(item.key(), v -> {
            for (int i = 0; i < spans.size(); i++) spans.get(i).toggleClass("on", item.options().get(i).equals(v));
        });
    }

    private static double parse(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String format(double v) {
        return Double.toString(Math.round(v * 1000) / 1000.0);
    }

    // ---- Changes ----

    private void change(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty("op", "set");
        o.addProperty("key", key);
        o.addProperty("value", value);
        if (doc.host().send(OrielKit.CHANNEL, o.toString())) flash();
    }

    private void reset() {
        JsonObject o = new JsonObject();
        o.addProperty("op", "reset");
        boolean sent = doc.host().send(OrielKit.CHANNEL, o.toString());
        // The game answers with the new state; without one (the preview) the page puts the defaults back itself.
        for (Item item : items.values()) {
            Control c = controls.get(item.key());
            if (c != null && !sent) c.show(item.def());
        }
        if (sent) flash();
    }

    private void flash() {
        savedMark.addClass("on");
        doc.scheduler().setTimeout(() -> savedMark.removeClass("on"), 1600);
    }
}
