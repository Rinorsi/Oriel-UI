package dev.oriel.kit.preset;

import dev.oriel.kit.Dom;
import dev.oriel.kit.Flip;
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
 * reorder; "整理" sorts and merges with the items gliding to their new slots.
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
    private String query = "";

    @Override
    public void attach(Document document) {
        doc = document;
        fixture(slots);
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
        Dom.onClick(Dom.byId(doc, "back"), e -> doc.navigate("gallery.html"));
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
        else hideTip();
    }

    private void release(MouseEvent e) {
        if (held == null || carrying || e.button != pressButton) return;
        int i = slotAt(e);
        boolean still = Math.abs(e.clientX - pressX) < 3 && Math.abs(e.clientY - pressY) < 3;
        if (still && i == heldFrom) {
            // A click: keep carrying the stack until the next press.
            carrying = true;
            status("拿起了 " + held.name() + " ×" + held.count() + "，点击格子放下");
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
        status(s.name() + " 移到了" + (toMain ? "物品栏" : "快捷栏"));
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
        status("已整理：" + stacks.size() + " 组物品");
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
        for (int i : matches) order(i, query.isEmpty() ? i : n++, false);
        for (int i : rest) order(i, query.isEmpty() ? i : n++, true);
        for (int i = FIRST_HOTBAR; i < SLOTS; i++) slotElements[i].toggleClass("dim", !query.isEmpty() && !matches(slots[i]));
        flip.play(0.28f);
        if (!query.isEmpty()) status("找到 " + matches.size() + " 组匹配「" + text.trim() + "」的物品");
    }

    private boolean matches(Stack s) {
        if (query.isEmpty()) return true;
        return s != null && (s.name().toLowerCase(Locale.ROOT).contains(query) || s.id().contains(query));
    }

    private void order(int i, int position, boolean dim) {
        style(slotElements[i]).set("order", Integer.toString(position)).apply();
        slotElements[i].toggleClass("dim", dim && !query.isEmpty());
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
        if (i < MAIN && !query.isEmpty()) slotElements[i].toggleClass("dim", !matches(s));
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
        status("这里放不下 " + (held != null ? held.name() : slots[i].name()));
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
        tip.appendChild(Dom.el(doc, "div", "tip-name rarity-" + s.rarity(), s.name()));
        for (String line : s.lore()) tip.appendChild(Dom.el(doc, "div", lineClass("tip-", line), strip(line)));
        if (s.maxDurability() > 0) tip.appendChild(Dom.el(doc, "div", "tip-line", "耐久 " + s.durability() + " / " + s.maxDurability()));
        float left = x + 10, top = y - 14;
        if (left + 140 > doc.viewportWidth()) left = x - 150;
        style(tip).set("display", "block").px("left", left).px("top", Math.max(2, top)).apply();
        showDetails(s);
    }

    private void hideTip() {
        style(tip).set("display", "none").apply();
    }

    private void showDetails(Stack s) {
        details.removeAllChildren();
        Element big = Dom.el(doc, "item", "big", null);
        big.setAttribute("id", s.id());
        details.appendChild(big);
        details.appendChild(Dom.el(doc, "div", "name rarity-" + s.rarity(), s.name() + (s.count() > 1 ? " ×" + s.count() : "")));
        for (String line : s.lore()) details.appendChild(Dom.el(doc, "div", lineClass("", line), strip(line)));
        if (s.maxDurability() > 0) {
            Element bar = Dom.el(doc, "div", "ore-progress" + (s.durability() * 4 < s.maxDurability() ? " red" : ""), null);
            Element fill = Dom.el(doc, "div", "fill", null);
            fill.setAttribute("style", "width: " + Math.round(100f * s.durability() / s.maxDurability()) + "%");
            bar.appendChild(fill);
            details.appendChild(bar);
            details.appendChild(Dom.el(doc, "div", "lore", "耐久 " + s.durability() + " / " + s.maxDurability()));
        }
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

    static void fixture(Stack[] slots) {
        slots[0] = tool("minecraft:diamond_sword", "锋利 V 钻石剑", "epic", 1450, 1561, List.of("*锋利 V", "*抢夺 III", "+8 攻击伤害", "+1.6 攻击速度"));
        slots[1] = tool("minecraft:bow", "力量 IV 弓", "rare", 300, 384, List.of("*力量 IV", "*无限"));
        slots[2] = item("minecraft:golden_apple", "金苹果", 3, 64, "rare", List.of("吃下后获得吸收与生命恢复"));
        slots[3] = item("minecraft:oak_log", "橡木原木", 64, 64, "common", List.of());
        slots[4] = item("minecraft:torch", "火把", 32, 64, "common", List.of());
        slots[5] = item("minecraft:ender_pearl", "末影珍珠", 16, 16, "common", List.of("投掷后传送到落点"));
        slots[7] = item("minecraft:cooked_beef", "牛排", 24, 64, "common", List.of("恢复 8 点饥饿值"));
        slots[9] = tool("minecraft:iron_pickaxe", "效率 III 铁镐", "uncommon", 120, 250, List.of("*效率 III", "+4 攻击伤害"));
        slots[10] = item("minecraft:bread", "面包", 12, 64, "common", List.of());
        slots[11] = item("minecraft:arrow", "箭", 48, 64, "common", List.of());
        slots[12] = item("minecraft:cobblestone", "圆石", 64, 64, "common", List.of());
        slots[13] = item("minecraft:cobblestone", "圆石", 23, 64, "common", List.of());
        slots[15] = item("minecraft:redstone", "红石粉", 30, 64, "common", List.of());
        slots[16] = item("minecraft:emerald", "绿宝石", 7, 64, "uncommon", List.of("村民最爱的货币"));
        slots[18] = item("minecraft:book", "书", 5, 64, "common", List.of());
        slots[19] = item("minecraft:apple", "苹果", 8, 64, "common", List.of());
        slots[20] = item("minecraft:compass", "指南针", 1, 1, "common", List.of("总是指向世界出生点"));
        slots[22] = item("minecraft:diamond", "钻石", 9, 64, "rare", List.of());
        slots[24] = item("minecraft:bread", "面包", 7, 64, "common", List.of());
        slots[25] = item("minecraft:elytra", "鞘翅", 1, 1, "epic", List.of("+滑翔"));
        slots[26] = item("minecraft:experience_bottle", "附魔之瓶", 16, 64, "uncommon", List.of());
        slots[FIRST_HOTBAR] = tool("minecraft:netherite_sword", "下界合金剑", "epic", 2031, 2031, List.of("*锋利 III", "+8 攻击伤害"));
        slots[FIRST_HOTBAR + 1] = tool("minecraft:diamond_pickaxe", "时运 III 钻石镐", "rare", 1200, 1561, List.of("*时运 III", "*耐久 III"));
        slots[FIRST_HOTBAR + 2] = item("minecraft:water_bucket", "水桶", 1, 1, "common", List.of());
        slots[FIRST_HOTBAR + 3] = item("minecraft:cooked_porkchop", "熟猪排", 40, 64, "common", List.of());
        slots[FIRST_HOTBAR + 4] = item("minecraft:shield", "盾牌", 1, 1, "common", List.of("+格挡"));
        slots[FIRST_HOTBAR + 8] = item("minecraft:torch", "火把", 40, 64, "common", List.of());
        slots[FIRST_EQUIP] = armor("minecraft:diamond_helmet", "钻石头盔", 0, 300, 363);
        slots[FIRST_EQUIP + 1] = armor("minecraft:diamond_chestplate", "保护 IV 钻石胸甲", 1, 480, 528);
        slots[FIRST_EQUIP + 3] = armor("minecraft:iron_boots", "摔落保护 IV 铁靴", 3, 50, 195);
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

    private static Stack armor(String id, String name, int slot, int durability, int max) {
        return new Stack(id, name, 1, 1, slot == 1 ? "rare" : "uncommon", List.of("+护甲"), durability, max, slot);
    }
}
