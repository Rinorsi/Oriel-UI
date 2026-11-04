package dev.oriel.kit.preset;

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
    static final String[] OPERATION_NAMES = {"+", "×基", "×总"};
    static final String[] SLOTS = {"any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body"};
    static final Map<String, String> SLOT_NAMES = Map.of("any", "任意", "mainhand", "主手", "offhand", "副手", "hand", "手持",
            "feet", "脚", "legs", "腿", "chest", "胸", "head", "头", "armor", "盔甲", "body", "身体");

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

    @Override
    public void attach(Document document) {
        doc = document;
        Particles fx = Shell.setup(doc);
        glintFx = fx.emitter("stage-fx");
        String data = doc.initialData();
        JsonElement parsed = data == null ? null : JsonParser.parseString(data);
        baseline = parsed != null && parsed.isJsonObject() && parsed.getAsJsonObject().has("id") ? parsed.getAsJsonObject() : fixture();
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
            sw.setAttribute("title", c == null ? "跟随稀有度" : c);
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
        status("已还原到打开时的样子", null);
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
        if (name != null && name.length() > MAX_NAME) errors.add("名称最多 " + MAX_NAME + " 个字");
        JsonArray lore = work.getAsJsonArray("lore");
        if (lore.size() > MAX_LORE_LINES) errors.add("说明最多 " + MAX_LORE_LINES + " 行");
        for (JsonElement l : lore) if (l.getAsString().length() > MAX_LORE_LENGTH) errors.add("每行说明最多 " + MAX_LORE_LENGTH + " 个字");
        for (JsonElement e : work.getAsJsonArray("enchantments")) {
            int level = e.getAsJsonObject().get("level").getAsInt();
            if (level < 1 || level > MAX_LEVEL) errors.add("附魔等级要在 1–" + MAX_LEVEL + " 之间");
        }
        for (JsonElement e : work.getAsJsonArray("attributes")) {
            JsonElement amount = e.getAsJsonObject().get("amount");
            if (amount == null || amount.isJsonNull() || !Double.isFinite(amount.getAsDouble()) || Math.abs(amount.getAsDouble()) > MAX_AMOUNT) {
                errors.add("属性数值要在 ±" + (int) MAX_AMOUNT + " 之内");
            }
        }
        JsonElement max = work.get("maxDamage");
        if (max == null || max.isJsonNull()) errors.add("最大耐久要是数字");
        else if (baseline.get("maxDamage").getAsInt() > 0 && (max.getAsInt() < 1 || max.getAsInt() > MAX_DURABILITY)) {
            errors.add("最大耐久要在 1–" + MAX_DURABILITY + " 之间");
        } else if (max.getAsInt() > 0 && work.get("damage").getAsInt() >= max.getAsInt()) errors.add("损耗要小于最大耐久");
    }

    void save() {
        validate();
        if (!errors.isEmpty()) {
            Motion.play(saveButton, "m-error", 300);
            status(errors.getFirst(), "bad");
            return;
        }
        if (changes().isEmpty()) {
            status("没有修改", null);
            return;
        }
        JsonObject edit = new JsonObject();
        for (String f : changes()) edit.add(f, work.get(f));
        if (doc.host().send("save", edit.toString())) {
            waiting = true;
            status("正在保存…", null);
        } else {
            status("预览模式：没有连着服务器，修改不会写回物品", "bad");
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
            status("已保存到手里的物品", "good");
            toast("已保存");
        } else {
            Motion.play(saveButton, "m-error", 300);
            status("服务器拒绝了：" + string(r, "reason"), "bad");
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
        else if (lastStatus.startsWith("名称") || lastStatus.contains("之间") || lastStatus.contains("最多") || lastStatus.contains("要")) status("修改的是副本；保存前原物品不会变", null);
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
            tooltip.appendChild(Dom.el(doc, "div", "tg", "装备在" + SLOT_NAMES.getOrDefault(slot, slot) + "时："));
            for (JsonObject o : list) tooltip.appendChild(Dom.el(doc, "div", "ta", amountText(o) + " " + attributeName(string(o, "attribute"))));
        });
        int max = intOr(work.get("maxDamage"), 0), damage = intOr(work.get("damage"), 0);
        if (work.get("unbreakable").getAsBoolean()) tooltip.appendChild(Dom.el(doc, "div", "ta", "无法破坏"));
        else if (max > 0 && damage > 0) tooltip.appendChild(Dom.el(doc, "div", "td", "耐久度：" + (max - damage) + " / " + max));
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
            Element remove = iconButton("trash", "删除这行");
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
        if (lore.isEmpty()) box.appendChild(Dom.el(doc, "div", "empty", "没有说明。"));
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
        if (shown == 0) results.appendChild(Dom.el(doc, "span", "o-faint", "没有匹配的附魔"));
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
            Element minus = iconButton("minus", "降一级"), plus = iconButton("plus", "升一级");
            Element v = Dom.el(doc, "span", "v" + (level < 1 || level > MAX_LEVEL ? " bad" : ""), roman(level));
            Shell.activate(minus, () -> setEnchantLevel(index, level - 1));
            Shell.activate(plus, () -> setEnchantLevel(index, level + 1));
            stepper.appendChild(minus);
            stepper.appendChild(v);
            stepper.appendChild(plus);
            row.appendChild(stepper);
            Integer max = enchantMax.get(id);
            row.appendChild(Dom.el(doc, "span", "max", max == null ? "" : "原版最高 " + roman(max)));
            Element remove = iconButton("trash", "去掉这个附魔");
            Shell.activate(remove, () -> {
                JsonArray next = work.getAsJsonArray("enchantments").deepCopy();
                next.remove(index);
                edit("enchantments", next);
            });
            row.appendChild(remove);
            rows.appendChild(row);
        }
        if (list.isEmpty()) rows.appendChild(Dom.el(doc, "div", "empty", "没有附魔。在上面搜索并点一下就能加上。"));
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
        if (shown == 0) results.appendChild(Dom.el(doc, "span", "o-faint", "没有匹配的属性"));
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
                Element b = Dom.el(doc, "span", op.equals(string(o, "operation")) ? "on" : null, OPERATION_NAMES[k]);
                Shell.activate(b, () -> setAttribute(index, "operation", op));
                ops.appendChild(b);
            }
            row.appendChild(ops);
            Element slot = Dom.el(doc, "div", "ore-btn small", SLOT_NAMES.getOrDefault(string(o, "slot"), string(o, "slot")));
            slot.setAttribute("title", "点击切换装备部位");
            Shell.activate(slot, () -> {
                int at = List.of(SLOTS).indexOf(string(o, "slot"));
                setAttribute(index, "slot", SLOTS[(at + 1) % SLOTS.length]);
            });
            row.appendChild(slot);
            Element remove = iconButton("trash", "删除这条");
            Shell.activate(remove, () -> {
                JsonArray next = work.getAsJsonArray("attributes").deepCopy();
                next.remove(index);
                edit("attributes", next);
            });
            row.appendChild(remove);
            rows.appendChild(row);
        }
        if (list.isEmpty()) rows.appendChild(Dom.el(doc, "div", "empty", "没有属性修饰符（物品用原版默认值）。"));
    }

    void setAttribute(int index, String key, String value) {
        JsonArray next = work.getAsJsonArray("attributes").deepCopy();
        next.get(index).getAsJsonObject().addProperty(key, value);
        edit("attributes", next);
    }

    private void renderDurability() {
        int max = intOr(work.get("maxDamage"), 0), damage = intOr(work.get("damage"), 0);
        boolean damageable = baseline.get("maxDamage").getAsInt() > 0;
        Dom.text(Dom.byId(doc, "dura-note"), damageable ? "" : "这个物品没有耐久");
        if (!damageSlider.dragging()) damageSlider.set(max > 1 ? damage / (float) (max - 1) : 0);
        Dom.text(Dom.byId(doc, "damage-v"), damage + " / " + max);
        Dom.byId(doc, "unbreakable").toggleClass("on", work.get("unbreakable").getAsBoolean());
        JsonElement g = work.get("glint");
        String gv = g == null || g.isJsonNull() ? "default" : g.getAsBoolean() ? "on" : "off";
        for (Element e : doc.querySelectorAll("#glint [data-g]")) e.toggleClass("on", e.getAttribute("data-g").equals(gv));
        Dom.byId(doc, "max-damage").toggleClass("bad", errors.stream().anyMatch(x -> x.contains("耐久") || x.contains("损耗")));
    }

    /** Every component as an expandable tree (read only). */
    private void renderTree() {
        Element tree = Dom.byId(doc, "tree");
        tree.removeAllChildren();
        JsonObject all = work.has("components") && work.get("components").isJsonObject() ? work.getAsJsonObject("components") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : all.entrySet()) tree.appendChild(node(e.getKey(), e.getValue()));
        if (all.isEmpty()) tree.appendChild(Dom.el(doc, "div", "empty", "（没有组件数据）"));
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
        Element t = Dom.el(doc, "div", "o-toast", text);
        Dom.byId(doc, "app").appendChild(t);
        doc.scheduler().setTimeout(t::remove, 2700);
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
    static JsonObject fixture() {
        String json = """
                {"id": "minecraft:diamond_sword", "count": 1, "defaultName": "钻石剑", "name": "龙牙", "nameColor": "#ffaa00",
                 "rarity": "rare", "lore": ["传说它斩落过末影龙的一颗牙"],
                 "enchantments": [{"id": "minecraft:sharpness", "level": 5}, {"id": "minecraft:looting", "level": 3}],
                 "damage": 120, "maxDamage": 1561, "unbreakable": false, "glint": null,
                 "attributes": [{"attribute": "minecraft:attack_damage", "modifier": "minecraft:base_attack_damage", "amount": 7,
                                 "operation": "add_value", "slot": "mainhand"},
                                {"attribute": "minecraft:attack_speed", "modifier": "minecraft:base_attack_speed", "amount": -2.4,
                                 "operation": "add_value", "slot": "mainhand"}],
                 "enchantmentChoices": [{"id": "minecraft:sharpness", "name": "锋利", "max": 5}, {"id": "minecraft:smite", "name": "亡灵杀手", "max": 5},
                   {"id": "minecraft:looting", "name": "抢夺", "max": 3}, {"id": "minecraft:fire_aspect", "name": "火焰附加", "max": 2},
                   {"id": "minecraft:knockback", "name": "击退", "max": 2}, {"id": "minecraft:sweeping_edge", "name": "横扫之刃", "max": 3},
                   {"id": "minecraft:unbreaking", "name": "耐久", "max": 3}, {"id": "minecraft:mending", "name": "经验修补", "max": 1}],
                 "attributeChoices": [{"id": "minecraft:attack_damage", "name": "攻击伤害"}, {"id": "minecraft:attack_speed", "name": "攻击速度"},
                   {"id": "minecraft:movement_speed", "name": "速度"}, {"id": "minecraft:max_health", "name": "最大生命值"},
                   {"id": "minecraft:armor", "name": "盔甲"}, {"id": "minecraft:luck", "name": "幸运"}],
                 "components": {"minecraft:custom_name": {"text": "龙牙", "color": "gold", "italic": false}, "minecraft:rarity": "rare",
                   "minecraft:enchantments": {"minecraft:sharpness": 5, "minecraft:looting": 3}, "minecraft:damage": 120,
                   "minecraft:max_damage": 1561, "minecraft:lore": ["传说它斩落过末影龙的一颗牙"], "minecraft:repair_cost": 0,
                   "minecraft:tool": {"rules": [{"blocks": "minecraft:cobweb", "speed": 15.0, "correct_for_drops": true}], "damage_per_block": 2}}}
                """;
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
