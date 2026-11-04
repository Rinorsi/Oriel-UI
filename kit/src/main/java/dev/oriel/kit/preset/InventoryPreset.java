package dev.oriel.kit.preset;

import dev.oriel.kit.Lang;

import dev.oriel.kit.Dom;
import dev.oriel.kit.Flip;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import dev.oriel.kit.fx.Particles;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.MouseEvent;
import dev.oriel.engine.host.PageControllers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The inventory preset: 27 main slots, a 9-slot hotbar and five equipment slots over fixture items. Drag a stack to
 * move, merge or swap it; right-press takes half; a click without moving picks the stack up to carry, and while
 * carrying a right-press drops one. Shift-click moves a stack between the hotbar and the main grid. Hovering shows
 * a tooltip and the details pane; the search dims what does not match and brings matches to the front with a FLIP
 * reorder; "整理" sorts and merges with the items gliding to their new slots. The category tabs filter the same
 * way. Slots are lit by rarity and enchanted items carry the glint; the details pane turns the hovered item in 3D
 * and otherwise shows an overview of the bag.
 */
public final class InventoryPreset implements PageControllers.Controller {
    static final int MAIN = 27, HOTBAR = 9, EQUIP = 5;
    static final int FIRST_HOTBAR = MAIN, FIRST_EQUIP = MAIN + HOTBAR, SLOTS = MAIN + HOTBAR + EQUIP;
    private static final String[] EQUIP_HINTS = {"minecraft:iron_helmet", "minecraft:iron_chestplate",
            "minecraft:iron_leggings", "minecraft:iron_boots", "minecraft:shield"};

    /** An item stack of the fixture. {@code equip} is the equipment slot it fits (0-4), or -1. */
    record Stack(String id, String name, int count, int max, String rarity, List<String> lore, int durability,
                 int maxDurability, int equip) {
        Stack withCount(int n) {
            return new Stack(id, name, n, max, rarity, lore, durability, maxDurability, equip);
        }

        boolean stacksWith(Stack o) {
            return o != null && id.equals(o.id) && max > 1 && durability == o.durability;
        }
    }

    final Stack[] slots = new Stack[SLOTS];
    private final Element[] slotElements = new Element[SLOTS];
    /** The element showing each slot's stack, kept with the stack when it moves, so moves can be animated. */
    private final Element[] itemElements = new Element[SLOTS];
    private final Map<Element, Dom.Style> styles = new HashMap<>();
    private Document doc;
    private Element ghost, ghostItem, tip, details, status;

    // The stack on the pointer: where it came from, whether it is carried (click) or dragged (held button).
    private Stack held;
    private int heldFrom = -1;
    private boolean carrying;
    private int pressButton = -1;
    private float pressX, pressY;
    private int hoverTarget = -1;
    private String query = "", category = "all";
    private boolean ready;
    private Particles particles;
    /** The stack the details pane shows (null: the overview), so moving over one item does not rebuild it. */
    private Stack detailed;
    private boolean overviewShown;

    @Override
    public void attach(Document document) {
        doc = document;
        particles = Shell.setup(doc);
        fixture(doc, slots);
        Element main = Dom.byId(doc, "main"), hotbar = Dom.byId(doc, "hotbar"), equip = Dom.byId(doc, "equip");
        for (int i = 0; i < SLOTS; i++) {
            Element slot = Dom.el(doc, "div", "ore-slot", null);
            slot.setAttribute("data-i", Integer.toString(i));
            if (i >= FIRST_EQUIP) {
                Element hint = Dom.el(doc, "item", "hint", null);
                hint.setAttribute("id", EQUIP_HINTS[i - FIRST_EQUIP]);
                slot.appendChild(hint);
            }
            (i < MAIN ? main : i < FIRST_EQUIP ? hotbar : equip).appendChild(slot);
            slotElements[i] = slot;
            render(i);
        }
        ghost = Dom.byId(doc, "ghost");
        ghostItem = Dom.el(doc, "item", null, null);
        ghost.appendChild(ghostItem);
        tip = Dom.byId(doc, "tip");
        details = Dom.byId(doc, "details");
        status = Dom.byId(doc, "status");

        Element screen = Dom.byId(doc, "screen");
        Dom.onMouse(screen, "mousedown", this::press);
        Dom.onMouse(screen, "mousemove", this::move);
        Dom.onMouse(screen, "mouseup", this::release);
        screen.addEventListener("contextmenu", e -> e.preventDefault());
        Element search = Dom.byId(doc, "search");
        search.addEventListener("input", e -> filter(search.value()));
        Dom.onClick(Dom.byId(doc, "sort"), e -> sort());
        Dom.onClick(Dom.byId(doc, "close"), e -> doc.host().close());
        Dom.onClick(Dom.byId(doc, "back"), e -> Shell.back(doc));
        for (Element tab : doc.querySelectorAll("[data-cat]")) Shell.activate(tab, () -> category(tab));
        Element effects = doc.getElementById("effects");
        if (effects != null) new PotionHudPreset().mountPanel(doc, effects, false);
        // The info column's tabs: the item under the pointer, or the status effects with the column's full height.
        for (Element tab : doc.querySelectorAll("#info-tabs [data-pane]")) {
            Shell.activate(tab, () -> {
                for (Element pane : doc.querySelectorAll(".info .pane")) {
                    pane.toggleClass("off", !pane.getAttribute("id").equals(tab.getAttribute("data-pane")));
                }
            });
        }
        ready = true;
        summary();
        showOverview();
    }

    private record State(Stack[] slots, String query, String category) {}

    @Override
    public Object saveState() {
        if (held != null) putBack();
        return new State(slots.clone(), query, category);
    }

    @Override
    public void restoreState(Object state) {
        if (!(state instanceof State s)) return;
        System.arraycopy(s.slots(), 0, slots, 0, SLOTS);
        for (int i = 0; i < SLOTS; i++) render(i);
        for (Element t : doc.querySelectorAll("[data-cat]")) if (t.getAttribute("data-cat").equals(s.category())) category(t);
        if (!s.query().isEmpty()) {
            Dom.byId(doc, "search").setValue(s.query());
            filter(s.query());
        }
        overviewShown = false;
        showOverview();
    }

    private void category(Element tab) {
        for (Element t : doc.querySelectorAll("[data-cat]")) t.toggleClass("on", t == tab);
        category = tab.getAttribute("data-cat");
        filter(query);
        if (category.equals("all") && query.isEmpty()) status(Lang.t(doc, "oriel.ui.inventory.show_all_items"));
    }

    /** The tab an item belongs to. */
    static String categoryOf(String id) {
        String n = id.substring(id.indexOf(':') + 1);
        if (n.endsWith("sword") || n.equals("bow") || n.equals("arrow") || n.equals("shield") || n.equals("crossbow") || n.equals("trident")) return "weapon";
        if (n.endsWith("pickaxe") || n.endsWith("_axe") || n.endsWith("shovel") || n.endsWith("hoe") || n.endsWith("bucket")
                || n.equals("compass") || n.equals("elytra") || n.equals("torch") || n.equals("ender_pearl") || n.endsWith("helmet")
                || n.endsWith("chestplate") || n.endsWith("leggings") || n.endsWith("boots")) return "tool";
        if (n.contains("apple") || n.contains("beef") || n.contains("bread") || n.contains("porkchop") || n.contains("cooked")) return "food";
        if (n.endsWith("log") || n.contains("cobblestone") || n.endsWith("planks") || n.endsWith("stone")) return "block";
        return "material";
    }

    // ---- Pointer ----

    private int slotAt(MouseEvent e) {
        Element slot = Dom.closest(e, ".ore-slot");
        return slot == null ? -1 : Integer.parseInt(slot.getAttribute("data-i"));
    }

    private void press(MouseEvent e) {
        if (e.button != 0 && e.button != 2) return;
        int i = slotAt(e);
        if (held != null) {
            if (i < 0) return;
            if (e.button == 2) dropOne(i);
            else drop(i);
            return;
        }
        if (i < 0 || slots[i] == null) return;
        if (e.button == 0 && e.modifiers.shift()) {
            quickMove(i);
            return;
        }
        Stack s = slots[i];
        int take = e.button == 2 ? (s.count + 1) / 2 : s.count;
        held = s.withCount(take);
        slots[i] = take == s.count ? null : s.withCount(s.count - take);
        heldFrom = i;
        carrying = false;
        pressButton = e.button;
        pressX = e.clientX;
        pressY = e.clientY;
        render(i);
        showGhost(e);
        hideTip();
    }

    private void move(MouseEvent e) {
        int i = slotAt(e);
        if (held != null) {
            placeGhost(e.clientX, e.clientY);
            setTarget(i);
            return;
        }
        if (i >= 0 && slots[i] != null) showTip(slots[i], e.clientX, e.clientY);
        else {
            hideTip();
            showOverview();
        }
    }

    private void release(MouseEvent e) {
        if (held == null || carrying || e.button != pressButton) return;
        int i = slotAt(e);
        boolean still = Math.abs(e.clientX - pressX) < 3 && Math.abs(e.clientY - pressY) < 3;
        if (still && i == heldFrom) {
            // A click: keep carrying the stack until the next press.
            carrying = true;
            status(Lang.t(doc, "oriel.ui.inventory.picked_up") + Lang.value(doc, held.name()) + " ×" + held.count() + Lang.t(doc, "oriel.ui.inventory.click_a_slot_to_place_it_down"));
            return;
        }
        if (i >= 0) drop(i);
        // Dropped outside the slots, or where it does not fit: back where it came from.
        if (held != null) putBack();
    }

    // ---- Moves ----

    private void drop(int i) {
        Stack target = slots[i];
        if (!fits(held, i)) {
            reject(i);
            return;
        }
        if (target == null) {
            slots[i] = held;
            held = null;
        } else if (target.stacksWith(held)) {
            int room = target.max() - target.count();
            int moved = Math.min(room, held.count());
            slots[i] = target.withCount(target.count() + moved);
            held = held.count() == moved ? null : held.withCount(held.count() - moved);
            if (held != null && slots[heldFrom] == null) {
                slots[heldFrom] = held;
                held = null;
                render(heldFrom);
            }
        } else if (slots[heldFrom] == null && fits(target, heldFrom)) {
            slots[i] = held;
            slots[heldFrom] = target;
            held = null;
            render(heldFrom);
        } else {
            reject(i);
            return;
        }
        render(i);
        land(i);
        if (held == null) endHold();
        else showGhost(null);
    }

    private void dropOne(int i) {
        Stack target = slots[i];
        if (!fits(held, i) || target != null && (!target.stacksWith(held) || target.count() >= target.max())) {
            reject(i);
            return;
        }
        slots[i] = target == null ? held.withCount(1) : target.withCount(target.count() + 1);
        held = held.count() == 1 ? null : held.withCount(held.count() - 1);
        render(i);
        land(i);
        if (held == null) endHold();
        else updateGhostItem();
    }

    private void putBack() {
        Stack back = slots[heldFrom];
        slots[heldFrom] = back == null ? held : back.withCount(back.count() + held.count());
        render(heldFrom);
        land(heldFrom);
        endHold();
    }

    private void quickMove(int from) {
        boolean toMain = from >= FIRST_HOTBAR;
        int start = toMain ? 0 : FIRST_HOTBAR, end = toMain ? MAIN : FIRST_EQUIP;
        Stack s = slots[from];
        int target = -1;
        for (int j = start; j < end && target < 0; j++) if (s.stacksWith(slots[j]) && slots[j].count() + s.count() <= s.max()) target = j;
        for (int j = start; j < end && target < 0; j++) if (slots[j] == null) target = j;
        if (target < 0) {
            reject(from);
            return;
        }
        Flip flip = new Flip(doc, List.of(itemElements[from]), this::style);
        if (slots[target] == null) {
            slots[target] = s;
            itemElements[target] = itemElements[from];
            itemElements[from] = null;
            slots[from] = null;
            slotElements[target].appendChild(itemElements[target]);
        } else {
            slots[target] = slots[target].withCount(slots[target].count() + s.count());
            slots[from] = null;
            render(from);
            render(target);
        }
        flip.play(0.22f);
        status(Lang.value(doc, s.name()) + Lang.t(doc, "oriel.ui.inventory.moved_to") + (toMain ? Lang.t(doc, "oriel.ui.inventory.inventory") : Lang.t(doc, "oriel.ui.inventory.hotbar")));
    }

    /** Sorts the main grid: merges stacks of the same item, then orders by rarity and name, gliding items home. */
    private void sort() {
        List<Stack> stacks = new ArrayList<>();
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < MAIN; i++) {
            if (slots[i] == null) continue;
            Stack s = slots[i];
            boolean merged = false;
            for (int k = 0; k < stacks.size() && !merged; k++) {
                Stack t = stacks.get(k);
                if (t.stacksWith(s) && t.count() + s.count() <= t.max()) {
                    stacks.set(k, t.withCount(t.count() + s.count()));
                    merged = true;
                }
            }
            if (merged) {
                itemElements[i].remove();
            } else {
                stacks.add(s);
                items.add(itemElements[i]);
            }
            itemElements[i] = null;
        }
        Flip flip = new Flip(doc, items, this::style);
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < stacks.size(); k++) order.add(k);
        order.sort(Comparator.comparingInt((Integer k) -> rank(stacks.get(k).rarity())).thenComparing(k -> stacks.get(k).id()));
        for (int i = 0; i < MAIN; i++) slots[i] = null;
        for (int n = 0; n < order.size(); n++) {
            int k = order.get(n);
            slots[n] = stacks.get(k);
            itemElements[n] = items.get(k);
            slotElements[n].appendChild(items.get(k));
            updateItem(items.get(k), stacks.get(k));
        }
        flip.play(0.3f);
        filter(query);
        status(Lang.t(doc, "oriel.ui.inventory.sorted") + stacks.size() + Lang.t(doc, "oriel.ui.inventory.item_groups"));
    }

    private static int rank(String rarity) {
        return switch (rarity) {
            case "epic" -> 0;
            case "rare" -> 1;
            case "uncommon" -> 2;
            default -> 3;
        };
    }

    private boolean fits(Stack s, int slot) {
        return slot < FIRST_EQUIP || slot == SLOTS - 1 || s.equip() == slot - FIRST_EQUIP;
    }

    // ---- Search ----

    void filter(String text) {
        query = text.trim().toLowerCase(Locale.ROOT);
        List<Element> grid = new ArrayList<>();
        for (int i = 0; i < MAIN; i++) grid.add(slotElements[i]);
        Flip flip = new Flip(doc, grid, this::style);
        List<Integer> matches = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = 0; i < MAIN; i++) (matches(slots[i]) ? matches : rest).add(i);
        int n = 0;
        for (int i : matches) order(i, filtering() ? n++ : i, false);
        for (int i : rest) order(i, filtering() ? n++ : i, true);
        for (int i = FIRST_HOTBAR; i < SLOTS; i++) slotElements[i].toggleClass("dim", filtering() && !matches(slots[i]));
        flip.play(0.28f);
        if (!query.isEmpty()) status(Lang.t(doc, "oriel.ui.inventory.found") + matches.size() + Lang.t(doc, "oriel.ui.inventory.item_groups_matching") + text.trim() + Lang.t(doc, "oriel.ui.inventory.label"));
        else if (!category.equals("all")) status(matches.size() + Lang.t(doc, "oriel.ui.inventory.groups") + Lang.value(doc, CATEGORY_NAMES.getOrDefault(category, "")) + Lang.t(doc, "oriel.ui.inventory.item"));
    }

    private boolean matches(Stack s) {
        if (query.isEmpty() && category.equals("all")) return true;
        if (s == null) return false;
        if (!category.equals("all") && !categoryOf(s.id()).equals(category)) return false;
        return query.isEmpty() || Lang.value(doc, s.name()).toLowerCase(Locale.ROOT).contains(query) || s.id().contains(query);
    }

    private boolean filtering() {
        return !query.isEmpty() || !category.equals("all");
    }

    private void order(int i, int position, boolean dim) {
        style(slotElements[i]).set("order", Integer.toString(position)).apply();
        slotElements[i].toggleClass("dim", dim && filtering());
        slotElements[i].toggleClass("match", !dim && !query.isEmpty());
    }

    // ---- View ----

    private Dom.Style style(Element e) {
        return styles.computeIfAbsent(e, Dom.Style::new);
    }

    void render(int i) {
        Stack s = slots[i];
        Element item = itemElements[i];
        if (s == null) {
            if (item != null) item.remove();
            itemElements[i] = null;
        } else {
            if (item == null) {
                item = Dom.el(doc, "item", null, null);
                slotElements[i].appendChild(item);
                itemElements[i] = item;
            }
            updateItem(item, s);
        }
        if (i < MAIN && filtering()) slotElements[i].toggleClass("dim", !matches(s));
        Element slot = slotElements[i];
        for (String tier : TIERS) slot.toggleClass("r-" + tier, s != null && s.rarity().equals(tier) && !tier.equals("common"));
        slot.toggleClass("fx-glint", s != null && enchanted(s));
        if (ready) summary();
    }

    private static final String[] TIERS = {"common", "uncommon", "rare", "epic"};
    private static final Map<String, String> TIER_NAMES = Map.of("common", "oriel.ui.inventory.common", "uncommon", "oriel.ui.inventory.uncommon", "rare", "oriel.ui.inventory.rare", "epic", "oriel.ui.inventory.epic");
    private static final Map<String, String> CATEGORY_NAMES = Map.of("weapon", "oriel.ui.inventory.weapon", "tool", "oriel.ui.inventory.tools", "food", "oriel.ui.inventory.food",
            "block", "oriel.ui.inventory.block", "material", "oriel.ui.inventory.materials");

    private boolean enchanted(Stack s) {
        for (String line : displayLore(s)) if (line.startsWith("*")) return true;
        return false;
    }

    /** The capacity bar and the rarity counts under the grid. */
    private void summary() {
        int used = 0;
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < FIRST_EQUIP; i++) {
            if (slots[i] == null) continue;
            used++;
            counts.merge(slots[i].rarity(), 1, Integer::sum);
        }
        Dom.text(Dom.byId(doc, "used"), used + " / " + FIRST_EQUIP);
        style(Dom.byId(doc, "fill")).set("--value", String.valueOf(used / (float) FIRST_EQUIP)).apply();
        Element tiers = Dom.byId(doc, "tiers");
        tiers.removeAllChildren();
        for (String tier : new String[] {"epic", "rare", "uncommon"}) {
            Element t = Dom.el(doc, "span", "r-" + tier, null);
            t.appendChild(Dom.el(doc, "i", null, null));
            t.appendChild(doc.createTextNode(String.valueOf(counts.getOrDefault(tier, 0))));
            tiers.appendChild(t);
        }
        if (overviewShown) {
            overviewShown = false;
            showOverview();
        }
    }

    private static void updateItem(Element item, Stack s) {
        Dom.attr(item, "id", s.id());
        if (s.count() > 1) Dom.attr(item, "count", Integer.toString(s.count()));
        else if (item.hasAttribute("count")) item.removeAttribute("count");
    }

    private void land(int i) {
        Element slot = slotElements[i];
        slot.removeClass("landed");
        doc.scheduler().requestAnimationFrame(t -> slot.addClass("landed"));
        doc.scheduler().setTimeout(() -> slot.removeClass("landed"), 260);
    }

    private void reject(int i) {
        Element slot = slotElements[i];
        slot.addClass("reject");
        doc.scheduler().setTimeout(() -> slot.removeClass("reject"), 260);
        status(Lang.t(doc, "oriel.ui.inventory.there_is_no_room_here_for") + (held != null ? Lang.value(doc, held.name()) : Lang.value(doc, slots[i].name())));
    }

    private void endHold() {
        held = null;
        heldFrom = -1;
        carrying = false;
        pressButton = -1;
        ghost.removeClass("on");
        setTarget(-1);
    }

    private void showGhost(MouseEvent e) {
        updateGhostItem();
        ghost.addClass("on");
        if (e != null) placeGhost(e.clientX, e.clientY);
    }

    private void updateGhostItem() {
        updateItem(ghostItem, held);
    }

    private void placeGhost(float x, float y) {
        style(ghost).px("left", x - 10).px("top", y - 10).apply();
    }

    private void setTarget(int i) {
        if (hoverTarget == i) return;
        if (hoverTarget >= 0) slotElements[hoverTarget].removeClass("target");
        hoverTarget = held != null ? i : -1;
        if (hoverTarget >= 0) slotElements[hoverTarget].addClass("target");
    }

    private void showTip(Stack s, float x, float y) {
        tip.removeAllChildren();
        tip.toggleClass("rare", s.rarity().equals("epic"));
        for (String tier : TIERS) tip.toggleClass("r-" + tier, s.rarity().equals(tier));
        tip.appendChild(Dom.el(doc, "div", "tip-name", Lang.value(doc, s.name())));
        for (String line : displayLore(s)) tip.appendChild(Dom.el(doc, "div", lineClass("tip-", line), strip(line)));
        if (s.maxDurability() > 0) tip.appendChild(Dom.el(doc, "div", "tip-line", Lang.t(doc, "oriel.ui.inventory.durability") + s.durability() + " / " + s.maxDurability()));
        float left = x + 10, top = y - 14;
        if (left + 140 > doc.viewportWidth()) left = x - 150;
        style(tip).set("display", "block").px("left", left).px("top", Math.max(2, top)).apply();
        showDetails(s);
    }

    private void hideTip() {
        style(tip).set("display", "none").apply();
    }

    private void showDetails(Stack s) {
        if (s == detailed) return;
        detailed = s;
        overviewShown = false;
        details.removeAllChildren();
        tier(s.rarity());
        Element stage = Dom.el(doc, "div", "stage", null);
        Element model = Dom.el(doc, "model", null, null);
        model.setAttribute("item", s.id());
        stage.appendChild(model);
        if (s.rarity().equals("epic")) {
            Element sparkle = Dom.el(doc, "canvas", "fx", null);
            sparkle.setAttribute("data-fx", enchanted(s) ? "glint" : "stars");
            sparkle.setAttribute("data-rate", "6");
            stage.appendChild(sparkle);
            particles.emitter(sparkle);
        }
        details.appendChild(stage);
        details.appendChild(Dom.el(doc, "div", "name", Lang.value(doc, s.name())));
        Element chips = Dom.el(doc, "div", "chips", null);
        chips.appendChild(Dom.el(doc, "span", "o-chip tier", Lang.value(doc, TIER_NAMES.getOrDefault(s.rarity(), Lang.t(doc, "oriel.ui.inventory.common")))));
        chips.appendChild(Dom.el(doc, "span", "o-chip", Lang.value(doc, CATEGORY_NAMES.getOrDefault(categoryOf(s.id()), Lang.t(doc, "oriel.ui.inventory.item")))));
        if (s.count() > 1) chips.appendChild(Dom.el(doc, "span", "o-chip", "×" + s.count()));
        details.appendChild(chips);
        details.appendChild(Dom.el(doc, "div", "o-divider", null));
        for (String line : displayLore(s)) {
            String kind = lineClass("", line);
            Element row = Dom.el(doc, "div", "line " + kind, null);
            String icon = kind.equals("enchant") ? "sparkle" : kind.equals("attr") ? "plus" : null;
            if (icon != null) row.appendChild(icon(icon));
            row.appendChild(doc.createTextNode(strip(line).replaceFirst("^\\+", "")));
            details.appendChild(row);
        }
        if (displayLore(s).isEmpty()) details.appendChild(Dom.el(doc, "div", "line lore", Lang.t(doc, "oriel.ui.inventory.common_items")));
        if (s.maxDurability() > 0) {
            float left = s.durability() / (float) s.maxDurability();
            Element dura = Dom.el(doc, "div", "dura", null);
            Element row = Dom.el(doc, "div", "row", null);
            row.appendChild(Dom.el(doc, "span", null, Lang.t(doc, "oriel.ui.inventory.durability_2")));
            row.appendChild(Dom.el(doc, "span", null, s.durability() + " / " + s.maxDurability()));
            dura.appendChild(row);
            Element bar = Dom.el(doc, "div", "o-meter" + (left < 0.25f ? " low" : left < 0.6f ? " mid" : ""), null);
            bar.setAttribute("style", "--value: " + left);
            bar.appendChild(Dom.el(doc, "i", null, null));
            dura.appendChild(bar);
            details.appendChild(dura);
        }
    }

    /** With nothing hovered: what is in the bag, by rarity. */
    private void showOverview() {
        if (overviewShown) return;
        overviewShown = true;
        detailed = null;
        details.removeAllChildren();
        tier("common");
        Element box = Dom.el(doc, "div", "overview", null);
        box.appendChild(Dom.el(doc, "div", "o-caps", Lang.t(doc, "oriel.ui.inventory.overview")));
        Map<String, Integer> counts = new HashMap<>();
        int enchantedCount = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] == null) continue;
            counts.merge(slots[i].rarity(), 1, Integer::sum);
            if (enchanted(slots[i])) enchantedCount++;
        }
        for (String tier : new String[] {"epic", "rare", "uncommon", "common"}) {
            Element row = Dom.el(doc, "div", "o-row r-" + tier, null);
            row.appendChild(Dom.el(doc, "i", null, null));
            row.appendChild(Dom.el(doc, "span", "o-tier", Lang.value(doc, TIER_NAMES.get(tier))));
            row.appendChild(Dom.el(doc, "span", "n", String.valueOf(counts.getOrDefault(tier, 0))));
            box.appendChild(row);
        }
        Element ench = Dom.el(doc, "div", "line enchant", null);
        ench.appendChild(icon("sparkle"));
        ench.appendChild(doc.createTextNode(enchantedCount + Lang.t(doc, "oriel.ui.inventory.enchanted_items")));
        box.appendChild(ench);
        box.appendChild(Dom.el(doc, "div", "o-caps", Lang.t(doc, "oriel.ui.inventory.valuable_items")));
        List<Stack> best = new ArrayList<>();
        for (Stack st : slots) if (st != null && !st.rarity().equals("common")) best.add(st);
        best.sort(Comparator.comparingInt((Stack st) -> rank(st.rarity())));
        for (Stack st : best.subList(0, Math.min(4, best.size()))) {
            Element row = Dom.el(doc, "div", "best r-" + st.rarity(), null);
            Element it = Dom.el(doc, "item", null, null);
            it.setAttribute("id", st.id());
            row.appendChild(it);
            row.appendChild(doc.createTextNode(Lang.value(doc, st.name())));
            box.appendChild(row);
        }
        box.appendChild(Dom.el(doc, "div", "o-divider", null));
        box.appendChild(Dom.el(doc, "div", "line lore", Lang.t(doc, "oriel.ui.inventory.hover_over_an_item_and_rotate_it")));
        details.appendChild(box);
    }

    private void tier(String rarity) {
        for (String t : TIERS) details.toggleClass("r-" + t, t.equals(rarity));
    }

    private Element icon(String name) {
        Element img = Dom.el(doc, "img", "o-ico", null);
        img.setAttribute("src", "../textures/icons/" + name + ".png");
        return img;
    }

    /** Lore lines starting with '+' are attributes, with '*' enchantments; the rest plain. */
    private static String lineClass(String prefix, String line) {
        if (line.startsWith("+")) return prefix + "attr";
        if (line.startsWith("*")) return prefix + "enchant";
        return prefix.isEmpty() ? "lore" : prefix + "line";
    }

    private static String strip(String line) {
        return line.startsWith("*") ? line.substring(1) : line;
    }

    private void status(String text) {
        Dom.text(status, text);
    }

    // ---- Fixture ----

    private List<String> displayLore(Stack stack) {
        return stack.lore().stream().map(line -> Lang.value(doc, line)).toList();
    }

    static void fixture(Document doc, Stack[] slots) {
        slots[0] = tool("minecraft:diamond_sword", "oriel.ui.inventory.sharpness_v_diamond_sword", "epic", 1450, 1561, List.of("oriel.ui.inventory.sharpness_v", "oriel.ui.inventory.looting_iii", "oriel.ui.inventory.8_attack_damage", "oriel.ui.inventory.1_6_attack_speed"));
        slots[1] = tool("minecraft:bow", "oriel.ui.inventory.power_iv_bow", "rare", 300, 384, List.of("oriel.ui.inventory.power_iv", "oriel.ui.inventory.infinity"));
        slots[2] = item("minecraft:golden_apple", "oriel.ui.inventory.golden_apple", 3, 64, "rare", List.of("oriel.ui.inventory.grants_absorption_and_regeneration_when_consumed"));
        slots[3] = item("minecraft:oak_log", "oriel.ui.inventory.oak_log", 64, 64, "common", List.of());
        slots[4] = item("minecraft:torch", "oriel.ui.inventory.torch", 32, 64, "common", List.of());
        slots[5] = item("minecraft:ender_pearl", "oriel.ui.inventory.ender_pearl", 16, 16, "common", List.of("oriel.ui.inventory.teleports_you_to_the_landing_point_when"));
        slots[7] = item("minecraft:cooked_beef", "oriel.ui.inventory.steak", 24, 64, "common", List.of("oriel.ui.inventory.restores_8_hunger_points"));
        slots[9] = tool("minecraft:iron_pickaxe", "oriel.ui.inventory.efficiency_iii_iron_pickaxe", "uncommon", 120, 250, List.of("oriel.ui.inventory.efficiency_iii", "oriel.ui.inventory.4_attack_damage"));
        slots[10] = item("minecraft:bread", "oriel.ui.inventory.bread", 12, 64, "common", List.of());
        slots[11] = item("minecraft:arrow", "oriel.ui.inventory.arrow", 48, 64, "common", List.of());
        slots[12] = item("minecraft:cobblestone", "oriel.ui.inventory.cobblestone", 64, 64, "common", List.of());
        slots[13] = item("minecraft:cobblestone", "oriel.ui.inventory.cobblestone", 23, 64, "common", List.of());
        slots[15] = item("minecraft:redstone", "oriel.ui.inventory.redstone_dust", 30, 64, "common", List.of());
        slots[16] = item("minecraft:emerald", "oriel.ui.inventory.emerald", 7, 64, "uncommon", List.of("oriel.ui.inventory.the_villager_s_favorite_currency"));
        slots[18] = item("minecraft:book", "oriel.ui.inventory.book", 5, 64, "common", List.of());
        slots[19] = item("minecraft:apple", "oriel.ui.inventory.apple", 8, 64, "common", List.of());
        slots[20] = item("minecraft:compass", "oriel.ui.inventory.compass", 1, 1, "common", List.of("oriel.ui.inventory.always_points_to_the_world_spawn"));
        slots[22] = item("minecraft:diamond", "oriel.ui.inventory.diamond", 9, 64, "rare", List.of());
        slots[24] = item("minecraft:bread", "oriel.ui.inventory.bread", 7, 64, "common", List.of());
        slots[25] = item("minecraft:elytra", "oriel.ui.inventory.elytra", 1, 1, "epic", List.of("oriel.ui.inventory.gliding"));
        slots[26] = item("minecraft:experience_bottle", "oriel.ui.inventory.bottle_o_enchanting", 16, 64, "uncommon", List.of());
        slots[FIRST_HOTBAR] = tool("minecraft:netherite_sword", "oriel.ui.inventory.netherite_sword", "epic", 2031, 2031, List.of("oriel.ui.inventory.sharpness_iii", "oriel.ui.inventory.8_attack_damage"));
        slots[FIRST_HOTBAR + 1] = tool("minecraft:diamond_pickaxe", "oriel.ui.inventory.fortune_iii_diamond_pickaxe", "rare", 1200, 1561, List.of("oriel.ui.inventory.fortune_iii", "oriel.ui.inventory.unbreaking_iii"));
        slots[FIRST_HOTBAR + 2] = item("minecraft:water_bucket", "oriel.ui.inventory.water_bucket", 1, 1, "common", List.of());
        slots[FIRST_HOTBAR + 3] = item("minecraft:cooked_porkchop", "oriel.ui.inventory.cooked_porkchop", 40, 64, "common", List.of());
        slots[FIRST_HOTBAR + 4] = item("minecraft:shield", "oriel.ui.inventory.shield", 1, 1, "common", List.of("oriel.ui.inventory.blocking"));
        slots[FIRST_HOTBAR + 8] = item("minecraft:torch", "oriel.ui.inventory.torch", 40, 64, "common", List.of());
        slots[FIRST_EQUIP] = armor(doc, "minecraft:diamond_helmet", "oriel.ui.inventory.diamond_helmet", 0, 300, 363);
        slots[FIRST_EQUIP + 1] = armor(doc, "minecraft:diamond_chestplate", "oriel.ui.inventory.protection_iv_diamond_chestplate", 1, 480, 528);
        slots[FIRST_EQUIP + 3] = armor(doc, "minecraft:iron_boots", "oriel.ui.inventory.feather_falling_iv_iron_boots", 3, 50, 195);
        for (int i = FIRST_HOTBAR; i < SLOTS; i++) {
            if (slots[i] != null && slots[i].id().equals("minecraft:shield") && i != SLOTS - 1) {
                Stack s = slots[i];
                slots[i] = new Stack(s.id(), s.name(), 1, 1, s.rarity(), s.lore(), 336, 336, 4);
            }
        }
    }

    private static Stack item(String id, String name, int count, int max, String rarity, List<String> lore) {
        return new Stack(id, name, count, max, rarity, lore, 0, 0, -1);
    }

    private static Stack tool(String id, String name, String rarity, int durability, int max, List<String> lore) {
        return new Stack(id, name, 1, 1, rarity, lore, durability, max, -1);
    }

    private static Stack armor(Document doc, String id, String name, int slot, int durability, int max) {
        return new Stack(id, name, 1, 1, slot == 1 ? "rare" : "uncommon", List.of("oriel.ui.inventory.armor_2"), durability, max, slot);
    }
}
