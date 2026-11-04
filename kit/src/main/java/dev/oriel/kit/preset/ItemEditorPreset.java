package dev.oriel.kit.preset;

import dev.oriel.kit.Lang;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Notice;
import dev.oriel.kit.Motion;
import dev.oriel.kit.Shell;
import dev.oriel.kit.Slider;
import dev.oriel.kit.fx.Particles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The item data editor's page. It edits a working copy of the item the server sent ({@code oriel.data}: fields,
 * choices and every component as JSON; a fixture sword when opened without a server), shows the result live in the
 * 3D preview and the tooltip, checks every field as it changes (the server checks again), counts the changed fields,
 * and sends the copy on 保存 (Ctrl+S). The server answers through the {@code result} patch: saved (the copy becomes
 * the new baseline) or refused with a reason. 取消 closes without sending; 还原 goes back to the baseline.
 */
public final class ItemEditorPreset implements PageControllers.Controller {
    static final List<String> FIELDS = List.of("name", "nameColor", "rarity", "lore", "enchantments", "damage", "maxDamage",
            "unbreakable", "glint", "attributes");
    static final int MAX_NAME = 50, MAX_LORE_LINES = 16, MAX_LORE_LENGTH = 100, MAX_LEVEL = 255, MAX_DURABILITY = 100_000;
    static final double MAX_AMOUNT = 2048;
    static final String[] COLOURS = {null, "#ffffff", "#ffff55", "#55ffff", "#ff55ff", "#ffaa00", "#55ff55", "#ff5555", "#5555ff", "#aaaaaa"};
    static final Map<String, String> RARITY_COLOURS = Map.of("common", "#ffffff", "uncommon", "#ffff55", "rare", "#55ffff", "epic", "#ff55ff");
    static final Map<String, String> RARITY_TIERS = Map.of("common", "r-common", "uncommon", "r-uncommon", "rare", "r-rare", "epic", "r-epic");
    static final String[] OPERATIONS = {"add_value", "add_multiplied_base", "add_multiplied_total"};
    static final String[] OPERATION_NAMES = {"+", "oriel.ui.item_editor.base_message", "oriel.ui.item_editor.total"};
    static final String[] SLOTS = {"any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body"};
    static final Map<String, String> SLOT_NAMES = Map.of("any", "oriel.ui.item_editor.any", "mainhand", "oriel.ui.item_editor.main_hand", "offhand", "oriel.ui.item_editor.off_hand", "hand", "oriel.ui.item_editor.held",
            "feet", "oriel.ui.item_editor.feet", "legs", "oriel.ui.item_editor.legs", "chest", "oriel.ui.item_editor.chest", "head", "oriel.ui.item_editor.head", "armor", "oriel.ui.item_editor.armor", "body", "oriel.ui.item_editor.body");

    JsonObject baseline, work;
    final List<String> errors = new ArrayList<>();
    String lastStatus = "";
    private Document doc;
    private Element tooltip, model, left, status, saveButton;
    private Particles.Emitter glintFx;
    private Slider damageSlider;
    private final Map<String, String> enchantNames = new LinkedHashMap<>(), attributeNames = new LinkedHashMap<>();
    private final Map<String, Integer> enchantMax = new LinkedHashMap<>();
    private boolean waiting;
    private boolean fixtureData;

    @Override
    public void attach(Document document) {
        doc = document;
        Particles fx = Shell.setup(doc);
        glintFx = fx.emitter("stage-fx");
        String data = doc.initialData();
        JsonElement parsed = data == null ? null : JsonParser.parseString(data);
        fixtureData = parsed == null || !parsed.isJsonObject() || !parsed.getAsJsonObject().has("id");
        baseline = fixtureData ? fixture(doc) : parsed.getAsJsonObject();
        for (JsonElement c : array(baseline, "enchantmentChoices")) {
            JsonObject o = c.getAsJsonObject();
            enchantNames.put(o.get("id").getAsString(), o.get("name").getAsString());
            enchantMax.put(o.get("id").getAsString(), o.get("max").getAsInt());
        }
        for (JsonElement c : array(baseline, "attributeChoices")) {
            JsonObject o = c.getAsJsonObject();
            attributeNames.put(o.get("id").getAsString(), o.get("name").getAsString());
        }
        work = baseline.deepCopy();
        tooltip = Dom.byId(doc, "tooltip");
        model = Dom.byId(doc, "model");
        left = Dom.byId(doc, "left");
        status = Dom.byId(doc, "status");
        saveButton = Dom.byId(doc, "save");

        for (Element tab : doc.querySelectorAll("#tabs [data-tab]")) Shell.activate(tab, () -> tab(tab.getAttribute("data-tab")));
        Element name = Dom.byId(doc, "name");
        name.addEventListener("input", e -> edit("name", name.value().isEmpty() ? JsonNull.INSTANCE : new JsonPrimitive(name.value())));
        Element colours = Dom.byId(doc, "colors");
        for (String c : COLOURS) {
            Element sw = Dom.el(doc, "span", c == null ? "none" : null, null);
            if (c != null) new Dom.Style(sw).set("--c", c).apply();
            sw.setAttribute("title", c == null ? Lang.t(doc, "oriel.ui.item_editor.follow_rarity") : c);
            sw.setAttribute("data-c", c == null ? "" : c);
            Shell.activate(sw, () -> edit("nameColor", c == null ? JsonNull.INSTANCE : new JsonPrimitive(c)));
            colours.appendChild(sw);
        }
        for (Element r : doc.querySelectorAll("#rarity [data-r]")) Shell.activate(r, () -> edit("rarity", new JsonPrimitive(r.getAttribute("data-r"))));
        Shell.activate(Dom.byId(doc, "lore-add"), () -> {
            JsonArray lore = work.getAsJsonArray("lore").deepCopy();
            lore.add("");
            edit("lore", lore);
            renderLore(true);
        });
        Element es = Dom.byId(doc, "ench-search"), as = Dom.byId(doc, "attr-search");
        es.addEventListener("input", e -> renderEnchantPicker(es.value()));
        as.addEventListener("input", e -> renderAttributePicker(as.value()));
        Element maxDamage = Dom.byId(doc, "max-damage");
        maxDamage.addEventListener("input", e -> edit("maxDamage", number(maxDamage.value())));
        damageSlider = new Slider(doc, Dom.byId(doc, "damage"), 0).onInput(v -> {
            int max = intOr(work.get("maxDamage"), 0);
            edit("damage", new JsonPrimitive(Math.round(v * Math.max(0, max - 1))));
        });
        Element unbreakable = Dom.byId(doc, "unbreakable");
        Shell.activate(unbreakable, () -> edit("unbreakable", new JsonPrimitive(!work.get("unbreakable").getAsBoolean())));
        for (Element g : doc.querySelectorAll("#glint [data-g]")) {
            String v = g.getAttribute("data-g");
            Shell.activate(g, () -> edit("glint", v.equals("default") ? JsonNull.INSTANCE : new JsonPrimitive(v.equals("on"))));
        }
        Shell.activate(saveButton, this::save);
        Shell.activate(Dom.byId(doc, "revert"), this::revert);
        Shell.activate(Dom.byId(doc, "cancel"), () -> doc.host().close());
        Shell.activate(Dom.byId(doc, "close"), () -> doc.host().close());
        Dom.onKey(doc, this::key);
        doc.addDataListener((key, json) -> {
            if ("result".equals(key) && json != null) result(JsonParser.parseString(json).getAsJsonObject());
        });

        renderAll(true);
        renderEnchantPicker("");
        renderAttributePicker("");
        renderTree();
    }

    private record State(JsonObject baseline, JsonObject work, List<String> changed, boolean fixture, String tab, boolean waiting) {}

    @Override
    public Object saveState() {
        Element active = doc.querySelector("#tabs [data-tab].on");
        return new State(baseline.deepCopy(), work.deepCopy(), changes(), fixtureData,
                active == null ? null : active.getAttribute("data-tab"), waiting);
    }

    @Override
    public void restoreState(Object state) {
        if (!(state instanceof State s) || !baseline.get("id").equals(s.baseline().get("id"))) return;
        if (fixtureData && s.fixture()) {
            for (String field : s.changed()) work.add(field, s.work().get(field).deepCopy());
        } else {
            baseline = s.baseline().deepCopy();
            work = s.work().deepCopy();
        }
        waiting = s.waiting();
        renderAll(true);
        renderEnchantPicker("");
        renderAttributePicker("");
        renderTree();
        if (s.tab() != null) tab(s.tab());
    }

    private void key(KeyboardEvent e) {
        if ((e.modifiers.ctrl() || e.modifiers.meta()) && e.key.equalsIgnoreCase("s")) {
            e.preventDefault();
            save();
        }
    }

    void tab(String name) {
        for (Element t : doc.querySelectorAll("#tabs [data-tab]")) t.toggleClass("on", t.getAttribute("data-tab").equals(name));
        for (Element p : doc.querySelectorAll(".pane")) p.toggleClass("on", p.getAttribute("data-pane").equals(name));
    }

    // ---- Edits ----

    /** Sets a field of the working copy and redraws what depends on it (not the field being typed in). */
    void edit(String field, JsonElement value) {
        work.add(field, value);
        renderAll(false);
    }

    void revert() {
        work = baseline.deepCopy();
        renderAll(true);
        status(Lang.t(doc, "oriel.ui.item_editor.restored_to_how_it_looked_when_opened"), null);
    }

    /** The fields that differ from the baseline. */
    List<String> changes() {
        List<String> out = new ArrayList<>();
        for (String f : FIELDS) {
            JsonElement a = baseline.get(f), b = work.get(f);
            if (!(a == null ? JsonNull.INSTANCE : a).equals(b == null ? JsonNull.INSTANCE : b)) out.add(f);
        }
        return out;
    }

    /** Checks the working copy as the server will; fills {@link #errors}. */
    void validate() {
        errors.clear();
        String name = string(work, "name");
        if (name != null && name.length() > MAX_NAME) errors.add(Lang.t(doc, "oriel.ui.item_editor.name_can_be_at_most") + MAX_NAME + Lang.t(doc, "oriel.ui.item_editor.characters"));
        JsonArray lore = work.getAsJsonArray("lore");
        if (lore.size() > MAX_LORE_LINES) errors.add(Lang.t(doc, "oriel.ui.item_editor.description_can_be_at_most") + MAX_LORE_LINES + Lang.t(doc, "oriel.ui.item_editor.lines"));
        for (JsonElement l : lore) if (l.getAsString().length() > MAX_LORE_LENGTH) errors.add(Lang.t(doc, "oriel.ui.item_editor.description_can_be_at_most_2") + MAX_LORE_LENGTH + Lang.t(doc, "oriel.ui.item_editor.characters"));
        for (JsonElement e : work.getAsJsonArray("enchantments")) {
            int level = e.getAsJsonObject().get("level").getAsInt();
            if (level < 1 || level > MAX_LEVEL) errors.add(Lang.t(doc, "oriel.ui.item_editor.level_range", MAX_LEVEL));
        }
        for (JsonElement e : work.getAsJsonArray("attributes")) {
            JsonElement amount = e.getAsJsonObject().get("amount");
            if (amount == null || amount.isJsonNull() || !Double.isFinite(amount.getAsDouble()) || Math.abs(amount.getAsDouble()) > MAX_AMOUNT) {
                errors.add(Lang.t(doc, "oriel.ui.item_editor.amount_range", (int) MAX_AMOUNT));
            }
        }
        JsonElement max = work.get("maxDamage");
        if (max == null || max.isJsonNull()) errors.add(Lang.t(doc, "oriel.ui.item_editor.max_durability_must_be_a_number"));
        else if (baseline.get("maxDamage").getAsInt() > 0 && (max.getAsInt() < 1 || max.getAsInt() > MAX_DURABILITY)) {
            errors.add(Lang.t(doc, "oriel.ui.item_editor.durability_range", MAX_DURABILITY));
        } else if (max.getAsInt() > 0 && work.get("damage").getAsInt() >= max.getAsInt()) errors.add(Lang.t(doc, "oriel.ui.item_editor.damage_must_be_less_than_max_durability"));
    }

    void save() {
        validate();
        if (!errors.isEmpty()) {
            Motion.play(saveButton, "m-error", 300);
            status(errors.getFirst(), "bad");
            return;
        }
        if (changes().isEmpty()) {
            status(Lang.t(doc, "oriel.ui.item_editor.no_changes"), null);
            return;
        }
        JsonObject edit = new JsonObject();
        for (String f : changes()) edit.add(f, work.get(f));
        if (doc.host().send("save", edit.toString())) {
            waiting = true;
            status(Lang.t(doc, "oriel.ui.item_editor.saving"), null);
        } else {
            status(Lang.t(doc, "oriel.ui.item_editor.preview_mode_not_connected_to_a_server"), "bad");
        }
    }

    /** The server's answer. */
    void result(JsonObject r) {
        waiting = false;
        if ("saved".equals(string(r, "status"))) {
            JsonObject item = r.getAsJsonObject("item");
            if (item != null) baseline = item;
            work = baseline.deepCopy();
            renderAll(true);
            renderTree();
            Motion.play(saveButton, "m-success", 560);
            status(Lang.t(doc, "oriel.ui.item_editor.saved_to_the_item_in_hand"), "good");
            toast(Lang.t(doc, "oriel.ui.item_editor.saved"));
        } else {
            Motion.play(saveButton, "m-error", 300);
            status(Lang.t(doc, "oriel.server.item_editor.rejected_reason",
                    dev.oriel.kit.LocalizedMessage.fromJson(r.getAsJsonObject("reason")).render(doc)), "bad");
        }
    }

    // ---- View ----

    private void renderAll(boolean inputs) {
        validate();
        List<String> changed = changes();
        Dom.byId(doc, "dirty").toggleClass("on", !changed.isEmpty());
        Dom.text(Dom.byId(doc, "dirty-n"), Integer.toString(changed.size()));
        saveButton.toggleClass("disabled", !errors.isEmpty() || waiting);
        if (!errors.isEmpty()) status(errors.getFirst(), "bad");
        else if (lastStatus.startsWith(Lang.t(doc, "oriel.ui.item_editor.name")) || lastStatus.contains(Lang.t(doc, "oriel.ui.item_editor.between")) || lastStatus.contains(Lang.t(doc, "oriel.ui.item_editor.at_most")) || lastStatus.contains(Lang.t(doc, "oriel.ui.item_editor.must_be"))) status(Lang.t(doc, "oriel.ui.item_editor.edits_are_made_to_a_copy_the"), null);
        String rarity = string(work, "rarity");
        for (String t : RARITY_TIERS.values()) left.toggleClass(t, t.equals(RARITY_TIERS.get(rarity)));
        Dom.attr(model, "item", string(work, "id"));
        Dom.text(Dom.byId(doc, "crumb"), "/ " + displayName());
        renderTooltip();
        if (inputs) {
            Dom.byId(doc, "name").setValue(string(work, "name") == null ? "" : string(work, "name"));
            Dom.attr(Dom.byId(doc, "name"), "placeholder", string(work, "defaultName"));
            renderLore(false);
            JsonElement max = work.get("maxDamage");
            Dom.byId(doc, "max-damage").setValue(max == null || max.isJsonNull() ? "" : max.getAsString());
        }
        Dom.byId(doc, "name").toggleClass("bad", string(work, "name") != null && string(work, "name").length() > MAX_NAME);
        String colour = string(work, "nameColor");
        for (Element sw : doc.querySelectorAll("#colors span")) sw.toggleClass("on", sw.getAttribute("data-c").equals(colour == null ? "" : colour));
        for (Element r : doc.querySelectorAll("#rarity [data-r]")) r.toggleClass("on", r.getAttribute("data-r").equals(rarity));
        renderEnchantRows();
        renderAttributeRows();
        renderDurability();
    }

    private String displayName() {
        String name = string(work, "name");
        return name == null || name.isEmpty() ? string(work, "defaultName") : name;
    }

    private void renderTooltip() {
        tooltip.removeAllChildren();
        String colour = string(work, "nameColor");
        if (colour == null) colour = RARITY_COLOURS.getOrDefault(string(work, "rarity"), "#ffffff");
        Element name = Dom.el(doc, "div", "tn", displayName());
        new Dom.Style(name).set("--name", colour).apply();
        tooltip.appendChild(name);
        JsonArray enchants = work.getAsJsonArray("enchantments");
        JsonElement glint = work.get("glint");
        boolean glinted = glint != null && !glint.isJsonNull() ? glint.getAsBoolean() : !enchants.isEmpty();
        tooltip.toggleClass("glinted", glinted);
        glintFx.rate(glinted ? 6 : 0);
        for (JsonElement e : enchants) {
            JsonObject o = e.getAsJsonObject();
            tooltip.appendChild(Dom.el(doc, "div", "te", enchantName(o.get("id").getAsString()) + " " + roman(o.get("level").getAsInt())));
        }
        for (JsonElement l : work.getAsJsonArray("lore")) tooltip.appendChild(Dom.el(doc, "div", "tl", l.getAsString().isEmpty() ? " " : l.getAsString()));
        Map<String, List<JsonObject>> bySlot = new LinkedHashMap<>();
        for (JsonElement a : work.getAsJsonArray("attributes")) {
            JsonObject o = a.getAsJsonObject();
            bySlot.computeIfAbsent(string(o, "slot"), k -> new ArrayList<>()).add(o);
        }
        bySlot.forEach((slot, list) -> {
            tooltip.appendChild(Dom.el(doc, "div", "tg", Lang.t(doc, "oriel.ui.item_editor.equipped_slot", Lang.value(doc, SLOT_NAMES.getOrDefault(slot, slot)))));
            for (JsonObject o : list) tooltip.appendChild(Dom.el(doc, "div", "ta", amountText(o) + " " + attributeName(string(o, "attribute"))));
        });
        int max = intOr(work.get("maxDamage"), 0), damage = intOr(work.get("damage"), 0);
        if (work.get("unbreakable").getAsBoolean()) tooltip.appendChild(Dom.el(doc, "div", "ta", Lang.t(doc, "oriel.ui.item_editor.unbreakable")));
        else if (max > 0 && damage > 0) tooltip.appendChild(Dom.el(doc, "div", "td", Lang.t(doc, "oriel.ui.item_editor.durability_message") + (max - damage) + " / " + max));
        tooltip.appendChild(Dom.el(doc, "div", "tid", string(work, "id")));
    }

    private void renderLore(boolean focusLast) {
        Element box = Dom.byId(doc, "lore");
        box.removeAllChildren();
        JsonArray lore = work.getAsJsonArray("lore");
        Element last = null;
        for (int i = 0; i < lore.size(); i++) {
            int index = i;
            Element row = Dom.el(doc, "div", "line", null);
            Element input = Dom.el(doc, "input", "ore-field", null);
            input.setValue(lore.get(i).getAsString());
            input.setAttribute("maxlength", Integer.toString(MAX_LORE_LENGTH + 20));
            input.addEventListener("input", e -> {
                JsonArray next = work.getAsJsonArray("lore").deepCopy();
                next.set(index, new JsonPrimitive(input.value()));
                edit("lore", next);
                input.toggleClass("bad", input.value().length() > MAX_LORE_LENGTH);
            });
            Element remove = iconButton("trash", Lang.t(doc, "oriel.ui.item_editor.delete_this_line"));
            Shell.activate(remove, () -> {
                JsonArray next = work.getAsJsonArray("lore").deepCopy();
                next.remove(index);
                edit("lore", next);
                renderLore(false);
            });
            row.appendChild(input);
            row.appendChild(remove);
            box.appendChild(row);
            last = input;
        }
        if (lore.isEmpty()) box.appendChild(Dom.el(doc, "div", "empty", Lang.t(doc, "oriel.ui.item_editor.no_description")));
        if (focusLast && last != null) last.focus();
    }

    private void renderEnchantPicker(String query) {
        Element results = Dom.byId(doc, "ench-results");
        results.removeAllChildren();
        String q = query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Map.Entry<String, String> e : enchantNames.entrySet()) {
            if (!q.isEmpty() && !e.getValue().toLowerCase(Locale.ROOT).contains(q) && !e.getKey().contains(q)) continue;
            if (shown++ >= 24) break;
            Element chip = Dom.el(doc, "span", "o-chip hot m-press", e.getValue());
            String id = e.getKey();
            Shell.activate(chip, () -> addEnchant(id));
            results.appendChild(chip);
        }
        if (shown == 0) results.appendChild(Dom.el(doc, "span", "o-faint", Lang.t(doc, "oriel.ui.item_editor.no_matching_enchantments")));
    }

    void addEnchant(String id) {
        JsonArray list = work.getAsJsonArray("enchantments").deepCopy();
        for (JsonElement e : list) if (e.getAsJsonObject().get("id").getAsString().equals(id)) return;
        JsonObject en = new JsonObject();
        en.addProperty("id", id);
        en.addProperty("level", 1);
        list.add(en);
        edit("enchantments", list);
    }

    private void renderEnchantRows() {
        Element rows = Dom.byId(doc, "ench-rows");
        rows.removeAllChildren();
        JsonArray list = work.getAsJsonArray("enchantments");
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            JsonObject o = list.get(i).getAsJsonObject();
            String id = o.get("id").getAsString();
            int level = o.get("level").getAsInt();
            Element row = Dom.el(doc, "div", "erow o-well", null);
            row.appendChild(Dom.el(doc, "span", "nm ench", enchantName(id)));
            Element stepper = Dom.el(doc, "div", "stepper", null);
            Element minus = iconButton("minus", Lang.t(doc, "oriel.ui.item_editor.decrease_level")), plus = iconButton("plus", Lang.t(doc, "oriel.ui.item_editor.increase_level"));
            Element v = Dom.el(doc, "span", "v" + (level < 1 || level > MAX_LEVEL ? " bad" : ""), roman(level));
            Shell.activate(minus, () -> setEnchantLevel(index, level - 1));
            Shell.activate(plus, () -> setEnchantLevel(index, level + 1));
            stepper.appendChild(minus);
            stepper.appendChild(v);
            stepper.appendChild(plus);
            row.appendChild(stepper);
            Integer max = enchantMax.get(id);
            row.appendChild(Dom.el(doc, "span", "max", max == null ? "" : Lang.t(doc, "oriel.ui.item_editor.vanilla_maximum") + roman(max)));
            Element remove = iconButton("trash", Lang.t(doc, "oriel.ui.item_editor.remove_this_enchantment"));
            Shell.activate(remove, () -> {
                JsonArray next = work.getAsJsonArray("enchantments").deepCopy();
                next.remove(index);
                edit("enchantments", next);
            });
            row.appendChild(remove);
            rows.appendChild(row);
        }
        if (list.isEmpty()) rows.appendChild(Dom.el(doc, "div", "empty", Lang.t(doc, "oriel.ui.item_editor.no_enchantments_search_above_and_click_one")));
    }

    void setEnchantLevel(int index, int level) {
        JsonArray next = work.getAsJsonArray("enchantments").deepCopy();
        next.get(index).getAsJsonObject().addProperty("level", Math.max(1, Math.min(MAX_LEVEL, level)));
        edit("enchantments", next);
    }

    private void renderAttributePicker(String query) {
        Element results = Dom.byId(doc, "attr-results");
        results.removeAllChildren();
        String q = query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Map.Entry<String, String> e : attributeNames.entrySet()) {
            if (!q.isEmpty() && !e.getValue().toLowerCase(Locale.ROOT).contains(q) && !e.getKey().contains(q)) continue;
            if (shown++ >= 24) break;
            Element chip = Dom.el(doc, "span", "o-chip hot m-press", e.getValue());
            String id = e.getKey();
            Shell.activate(chip, () -> addAttribute(id));
            results.appendChild(chip);
        }
        if (shown == 0) results.appendChild(Dom.el(doc, "span", "o-faint", Lang.t(doc, "oriel.ui.item_editor.no_matching_attributes")));
    }

    void addAttribute(String id) {
        JsonArray list = work.getAsJsonArray("attributes").deepCopy();
        JsonObject a = new JsonObject();
        a.addProperty("attribute", id);
        a.addProperty("amount", 1);
        a.addProperty("operation", "add_value");
        a.addProperty("slot", "mainhand");
        list.add(a);
        edit("attributes", list);
    }

    private void renderAttributeRows() {
        Element rows = Dom.byId(doc, "attr-rows");
        Element focused = doc.focusedElement();
        // Typing an amount keeps its row (and focus); other changes rebuild the list.
        if (focused != null && focused.hasClass("amount") && rows.contains(focused)) return;
        rows.removeAllChildren();
        JsonArray list = work.getAsJsonArray("attributes");
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            JsonObject o = list.get(i).getAsJsonObject();
            Element row = Dom.el(doc, "div", "erow o-well", null);
            row.appendChild(Dom.el(doc, "span", "nm", attributeName(string(o, "attribute"))));
            Element amount = Dom.el(doc, "input", "ore-field amount", null);
            JsonElement av = o.get("amount");
            amount.setValue(av == null || av.isJsonNull() ? "" : format(av.getAsDouble()));
            amount.addEventListener("input", e -> {
                JsonArray next = work.getAsJsonArray("attributes").deepCopy();
                next.get(index).getAsJsonObject().add("amount", number(amount.value()));
                edit("attributes", next);
                JsonElement n = number(amount.value());
                amount.toggleClass("bad", n.isJsonNull() || Math.abs(n.getAsDouble()) > MAX_AMOUNT);
            });
            row.appendChild(amount);
            Element ops = Dom.el(doc, "div", "o-seg", null);
            for (int k = 0; k < OPERATIONS.length; k++) {
                String op = OPERATIONS[k];
                Element b = Dom.el(doc, "span", op.equals(string(o, "operation")) ? "on" : null, Lang.value(doc, OPERATION_NAMES[k]));
                Shell.activate(b, () -> setAttribute(index, "operation", op));
                ops.appendChild(b);
            }
            row.appendChild(ops);
            Element slot = Dom.el(doc, "div", "ore-btn small", Lang.value(doc, SLOT_NAMES.getOrDefault(string(o, "slot"), string(o, "slot"))));
            slot.setAttribute("title", Lang.t(doc, "oriel.ui.item_editor.click_to_toggle_equipment_slot"));
            Shell.activate(slot, () -> {
                int at = List.of(SLOTS).indexOf(string(o, "slot"));
                setAttribute(index, "slot", SLOTS[(at + 1) % SLOTS.length]);
            });
            row.appendChild(slot);
            Element remove = iconButton("trash", Lang.t(doc, "oriel.ui.item_editor.delete_this"));
            Shell.activate(remove, () -> {
                JsonArray next = work.getAsJsonArray("attributes").deepCopy();
                next.remove(index);
                edit("attributes", next);
            });
            row.appendChild(remove);
            rows.appendChild(row);
        }
        if (list.isEmpty()) rows.appendChild(Dom.el(doc, "div", "empty", Lang.t(doc, "oriel.ui.item_editor.no_attribute_modifiers_the_item_uses_vanilla")));
    }

    void setAttribute(int index, String key, String value) {
        JsonArray next = work.getAsJsonArray("attributes").deepCopy();
        next.get(index).getAsJsonObject().addProperty(key, value);
        edit("attributes", next);
    }

    private void renderDurability() {
        int max = intOr(work.get("maxDamage"), 0), damage = intOr(work.get("damage"), 0);
        boolean damageable = baseline.get("maxDamage").getAsInt() > 0;
        Dom.text(Dom.byId(doc, "dura-note"), damageable ? "" : Lang.t(doc, "oriel.ui.item_editor.this_item_has_no_durability"));
        if (!damageSlider.dragging()) damageSlider.set(max > 1 ? damage / (float) (max - 1) : 0);
        Dom.text(Dom.byId(doc, "damage-v"), damage + " / " + max);
        Dom.byId(doc, "unbreakable").toggleClass("on", work.get("unbreakable").getAsBoolean());
        JsonElement g = work.get("glint");
        String gv = g == null || g.isJsonNull() ? "default" : g.getAsBoolean() ? "on" : "off";
        for (Element e : doc.querySelectorAll("#glint [data-g]")) e.toggleClass("on", e.getAttribute("data-g").equals(gv));
        Dom.byId(doc, "max-damage").toggleClass("bad", errors.stream().anyMatch(x -> x.contains(Lang.t(doc, "oriel.ui.item_editor.durability_2")) || x.contains(Lang.t(doc, "oriel.ui.item_editor.damage"))));
    }

    /** Every component as an expandable tree (read only). */
    private void renderTree() {
        Element tree = Dom.byId(doc, "tree");
        tree.removeAllChildren();
        JsonObject all = work.has("components") && work.get("components").isJsonObject() ? work.getAsJsonObject("components") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : all.entrySet()) tree.appendChild(node(e.getKey(),
                dev.oriel.kit.LocalizedMessage.componentValue(doc, work, e.getKey(), e.getValue())));
        if (all.isEmpty()) tree.appendChild(Dom.el(doc, "div", "empty", Lang.t(doc, "oriel.ui.item_editor.no_component_data")));
    }

    private Element node(String key, JsonElement value) {
        Element n = Dom.el(doc, "div", "node", null);
        Element k = Dom.el(doc, "div", "k", null);
        boolean branch = value.isJsonObject() || value.isJsonArray();
        if (branch) {
            Element caret = Dom.el(doc, "img", "o-ico m-caret", null);
            caret.setAttribute("src", "../textures/icons/down.png");
            k.appendChild(caret);
        } else {
            k.appendChild(Dom.el(doc, "i", "sp", null));
        }
        k.appendChild(Dom.el(doc, "span", "key", key));
        k.appendChild(Dom.el(doc, "span", "val", branch ? (value.isJsonArray() ? "[" + value.getAsJsonArray().size() + "]" : "{" + value.getAsJsonObject().size() + "}")
                : value.toString()));
        n.appendChild(k);
        if (branch) {
            Element kids = Dom.el(doc, "div", "kids", null);
            n.appendChild(kids);
            boolean[] open = {false}, built = {false};
            Shell.activate(k, () -> {
                if (!built[0]) {
                    built[0] = true;
                    if (value.isJsonObject()) for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet()) kids.appendChild(node(e.getKey(), e.getValue()));
                    else for (int i = 0; i < value.getAsJsonArray().size(); i++) kids.appendChild(node(Integer.toString(i), value.getAsJsonArray().get(i)));
                }
                Motion.toggle(kids, open[0] = !open[0]);
            });
        }
        return n;
    }

    // ---- Helpers ----

    private void status(String text, String kind) {
        lastStatus = text;
        Dom.text(status, text);
        status.toggleClass("bad", "bad".equals(kind));
        status.toggleClass("good", "good".equals(kind));
    }

    private void toast(String text) {
        Notice.toast(doc, "success", text, null, 2700);
    }

    private Element iconButton(String icon, String title) {
        Element b = Dom.el(doc, "div", "ore-btn icon small", null);
        b.setAttribute("tabindex", "0");
        b.setAttribute("title", title);
        Element img = Dom.el(doc, "img", "o-ico", null);
        img.setAttribute("src", "../textures/icons/" + icon + ".png");
        b.appendChild(img);
        return b;
    }

    private String enchantName(String id) {
        return enchantNames.getOrDefault(id, id);
    }

    private String attributeName(String id) {
        return attributeNames.getOrDefault(id, id);
    }

    private static String amountText(JsonObject o) {
        JsonElement a = o.get("amount");
        if (a == null || a.isJsonNull()) return "?";
        double v = a.getAsDouble();
        boolean add = "add_value".equals(string(o, "operation"));
        String n = add ? format(Math.abs(v)) : format(Math.abs(v) * 100) + "%";
        return (v < 0 ? "-" : "+") + n;
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? r[n] : Integer.toString(n);
    }

    private static String format(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

    private static JsonElement number(String text) {
        try {
            String t = text.trim();
            if (t.isEmpty()) return JsonNull.INSTANCE;
            double v = Double.parseDouble(t);
            return v == Math.rint(v) && Math.abs(v) < 1e9 ? new JsonPrimitive((long) v) : new JsonPrimitive(v);
        } catch (NumberFormatException e) {
            return JsonNull.INSTANCE;
        }
    }

    private static int intOr(JsonElement e, int fallback) {
        return e == null || e.isJsonNull() ? fallback : e.getAsInt();
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    /** The item shown when the page opens without a server (previewer, tests): an enchanted diamond sword. */
    static JsonObject fixture(Document doc) {
        String json = """
                {"id": "minecraft:diamond_sword", "count": 1, "defaultName": "oriel.ui.item_editor.diamond_sword", "name": "oriel.ui.item_editor.dragon_tooth", "nameColor": "#ffaa00",
                 "rarity": "rare", "lore": ["oriel.ui.item_editor.legend_says_it_knocked_out_one_of"],
                 "enchantments": [{"id": "minecraft:sharpness", "level": 5}, {"id": "minecraft:looting", "level": 3}],
                 "damage": 120, "maxDamage": 1561, "unbreakable": false, "glint": null,
                 "attributes": [{"attribute": "minecraft:attack_damage", "modifier": "minecraft:base_attack_damage", "amount": 7,
                                 "operation": "add_value", "slot": "mainhand"},
                                {"attribute": "minecraft:attack_speed", "modifier": "minecraft:base_attack_speed", "amount": -2.4,
                                 "operation": "add_value", "slot": "mainhand"}],
                 "enchantmentChoices": [{"id": "minecraft:sharpness", "name": "oriel.ui.item_editor.sharpness", "max": 5}, {"id": "minecraft:smite", "name": "oriel.ui.item_editor.smite", "max": 5},
                   {"id": "minecraft:looting", "name": "oriel.ui.item_editor.looting", "max": 3}, {"id": "minecraft:fire_aspect", "name": "oriel.ui.item_editor.fire_aspect", "max": 2},
                   {"id": "minecraft:knockback", "name": "oriel.ui.item_editor.knockback", "max": 2}, {"id": "minecraft:sweeping_edge", "name": "oriel.ui.item_editor.sweeping_edge", "max": 3},
                   {"id": "minecraft:unbreaking", "name": "oriel.ui.item_editor.durability_2", "max": 3}, {"id": "minecraft:mending", "name": "oriel.ui.item_editor.mending", "max": 1}],
                 "attributeChoices": [{"id": "minecraft:attack_damage", "name": "oriel.ui.item_editor.attack_damage"}, {"id": "minecraft:attack_speed", "name": "oriel.ui.item_editor.attack_speed"},
                   {"id": "minecraft:movement_speed", "name": "oriel.ui.item_editor.speed"}, {"id": "minecraft:max_health", "name": "oriel.ui.item_editor.max_health"},
                   {"id": "minecraft:armor", "name": "oriel.ui.item_editor.armor"}, {"id": "minecraft:luck", "name": "oriel.ui.item_editor.luck"}],
                 "components": {"minecraft:custom_name": {"text": "oriel.ui.item_editor.dragon_tooth", "color": "gold", "italic": false}, "minecraft:rarity": "rare",
                   "minecraft:enchantments": {"minecraft:sharpness": 5, "minecraft:looting": 3}, "minecraft:damage": 120,
                   "minecraft:max_damage": 1561, "minecraft:lore": ["oriel.ui.item_editor.legend_says_it_knocked_out_one_of"], "minecraft:repair_cost": 0,
                   "minecraft:tool": {"rules": [{"blocks": "minecraft:cobweb", "speed": 15.0, "correct_for_drops": true}], "damage_per_block": 2}}}
                """;
        return Lang.fixture(doc, JsonParser.parseString(json)).getAsJsonObject();
    }
}
