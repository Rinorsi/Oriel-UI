package dev.oriel.kit.preset;

import dev.oriel.kit.Lang;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.event.MouseEvent;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.GameRegistry;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The component editor: lay out a screen from the kit's parts. Parts come from the library (click to add, or drag
 * onto the artboard); on the artboard a press selects (Ctrl or Shift adds to the selection), dragging moves the
 * selection with 4px snapping and alignment guides against the other parts and the artboard's centre, and the corner
 * handle resizes. The layer tree mirrors the parts (search filters it; the eye hides, the lock pins), the inspector
 * edits the selection (position, size, text, variant, rarity; alignment for several), and every change is one step
 * of undo and redo. Keys: Ctrl+Z / Ctrl+Y (or Ctrl+Shift+Z), Delete, Ctrl+D, Ctrl+A, the arrows (Shift: 4px), Esc,
 * P to preview. "导出" shows the screen as HTML on the Ore theme.
 */
public final class EditorPreset implements PageControllers.Controller {
    static final int GRID = 4, SNAP_RANGE = 3;
    /** New canvas sizes: a name and width x height in GUI pixels. */
    static final String[][] SIZES = {{"oriel.ui.editor.default", "264", "172"}, {"oriel.ui.editor.chest", "176", "166"}, {"oriel.ui.editor.widescreen", "320", "200"},
            {"oriel.ui.editor.full_screen_small", "427", "240"}, {"oriel.ui.editor.full_screen_medium", "640", "360"}, {"oriel.ui.editor.pop_up", "200", "120"}};

    /** One part on the artboard. */
    static final class Part {
        int id;
        String kind, name, text, variant, tier;
        int x, y, w, h;
        boolean visible = true, locked;

        Part copy() {
            Part p = new Part();
            p.id = id;
            p.kind = kind;
            p.name = name;
            p.text = text;
            p.variant = variant;
            p.tier = tier;
            p.x = x;
            p.y = y;
            p.w = w;
            p.h = h;
            p.visible = visible;
            p.locked = locked;
            return p;
        }
    }

    static final Map<String, String> KIND_NAMES = Map.of("button", "oriel.ui.editor.button", "panel", "oriel.ui.editor.panel", "title", "oriel.ui.editor.title", "text", "oriel.ui.editor.text",
            "slot", "oriel.ui.editor.item_slot", "toggle", "oriel.ui.editor.toggle", "meter", "oriel.ui.editor.progress_bar", "block", "oriel.ui.editor.3d_cube", "entity", "oriel.ui.editor.3d_solid");
    static final Map<String, String> KIND_ICONS = Map.of("button", "mouse", "panel", "layers", "title", "file", "text", "list",
            "slot", "gem", "toggle", "sliders", "meter", "bolt", "block", "cube", "entity", "paw");
    static final List<String> VARIANTS = List.of("hero", "primary", "secondary", "destructive", "neutral");
    static final List<String> TIERS = List.of("common", "uncommon", "rare", "epic", "legendary");

    final List<Part> parts = new ArrayList<>();
    final Set<Integer> selection = new LinkedHashSet<>();
    final Deque<List<Part>> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private int nextId = 1;
    /** The artboard's size and the document's name (a new canvas sets both). */
    int boardW = 264, boardH = 172;
    String docName;
    /** Ctrl+C / Ctrl+X copies, pasted by Ctrl+V (kept across documents, like a clipboard). */
    private static final List<Part> clipboard = new ArrayList<>();
    private int pastes;
    /** The part whose item the picker is choosing, and the picker's filter. */
    private Part picking;
    private String pickKind = "all";
    boolean snap = true, preview;
    private String filter = "";

    private Document doc;
    private Element board, tree, inspector, ghost, guideV, guideH;
    // Pointer gestures on the artboard.
    private enum Gesture { NONE, MOVE, RESIZE, PLACE }
    private Gesture gesture = Gesture.NONE;
    private float startX, startY;
    private List<Part> before;
    private final Map<Integer, int[]> origin = new java.util.HashMap<>();
    private String placing;
    private boolean moved;

    @Override
    public void attach(Document document) {
        doc = document;
        docName = Lang.t(doc, "oriel.ui.editor.shop_interface");
        Shell.setup(doc);
        board = Dom.byId(doc, "board");
        tree = Dom.byId(doc, "tree");
        inspector = Dom.byId(doc, "inspector");
        ghost = Dom.byId(doc, "ghost");
        guideV = Dom.byId(doc, "guide-v");
        guideH = Dom.byId(doc, "guide-h");
        sample();

        for (Element part : doc.querySelectorAll(".palette .part")) {
            Dom.onMouse(part, "mousedown", e -> {
                if (e.button != 0) return;
                gesture = Gesture.PLACE;
                placing = part.getAttribute("data-kind");
                startX = e.clientX;
                startY = e.clientY;
                moved = false;
                e.preventDefault();
            });
        }
        Dom.onMouse(board, "mousedown", this::boardPress);
        Dom.onMouse(doc, "mousemove", this::pointerMove);
        Dom.onMouse(doc, "mouseup", this::pointerUp);
        Dom.onKey(doc, this::key);
        Element search = Dom.byId(doc, "filter");
        search.addEventListener("input", e -> {
            filter = search.value().trim().toLowerCase(Locale.ROOT);
            renderTree();
        });
        Shell.activate(Dom.byId(doc, "undo"), this::undo);
        Shell.activate(Dom.byId(doc, "redo"), this::redo);
        Shell.activate(Dom.byId(doc, "dup"), this::duplicate);
        Shell.activate(Dom.byId(doc, "del"), this::delete);
        Shell.activate(Dom.byId(doc, "snap"), this::toggleSnap);
        // Menu items and options-bar tools name their command.
        for (Element e : doc.querySelectorAll("[data-cmd]")) {
            String cmd = e.getAttribute("data-cmd");
            Shell.activate(e, () -> command(cmd));
        }
        Dom.query(doc, "[data-cmd=snap]").toggleClass("on", snap);
        Shell.activate(Dom.byId(doc, "preview"), this::togglePreview);
        Shell.activate(Dom.byId(doc, "export"), this::export);
        Shell.activate(Dom.byId(doc, "export-close"), () -> Dom.byId(doc, "export-box").removeClass("on"));
        Shell.activate(Dom.byId(doc, "close"), () -> doc.host().close());
        Shell.activate(Dom.byId(doc, "new"), this::openNew);
        Shell.activate(Dom.byId(doc, "keys"), () -> Dom.byId(doc, "keys-box").toggleClass("on"));
        Shell.activate(Dom.byId(doc, "keys-close"), () -> Dom.byId(doc, "keys-box").removeClass("on"));
        wireNew();
        wirePicker();
        render();
    }

    // ---- New canvas ----

    private void wireNew() {
        Element sizes = Dom.byId(doc, "new-sizes");
        for (String[] size : SIZES) {
            Element tile = Dom.el(doc, "div", "size o-tile hot", null);
            tile.setAttribute("tabindex", "0");
            tile.setAttribute("data-w", size[1]);
            tile.setAttribute("data-h", size[2]);
            // The shape at 1/16 scale, so the sizes compare at a glance.
            Element shape = Dom.el(doc, "i", "shape", null);
            new Dom.Style(shape).px("width", Integer.parseInt(size[1]) / 16f).px("height", Integer.parseInt(size[2]) / 16f).apply();
            tile.appendChild(shape);
            tile.appendChild(Dom.el(doc, "b", null, Lang.value(doc, size[0])));
            tile.appendChild(Dom.el(doc, "span", null, size[1] + " × " + size[2]));
            Shell.activate(tile, () -> {
                for (Element t : sizes.children()) t.toggleClass("sel", t == tile);
                Dom.byId(doc, "new-w").setValue(size[1]);
                Dom.byId(doc, "new-h").setValue(size[2]);
            });
            sizes.appendChild(tile);
        }
        Shell.activate(Dom.byId(doc, "new-cancel"), () -> Dom.byId(doc, "new-box").removeClass("on"));
        Shell.activate(Dom.byId(doc, "new-ok"), () -> {
            int w = parse(Dom.byId(doc, "new-w").value(), boardW), h = parse(Dom.byId(doc, "new-h").value(), boardH);
            String name = Dom.byId(doc, "new-name").value().trim();
            boolean withSample = Dom.byId(doc, "new-sample").hasClass("on");
            Dom.byId(doc, "new-box").removeClass("on");
            newCanvas(name.isEmpty() ? Lang.t(doc, "oriel.ui.editor.untitled_interface") : name, clamp(w, 64, 640), clamp(h, 48, 360), withSample);
        });
    }

    void openNew() {
        Dom.byId(doc, "new-name").setValue(Lang.t(doc, "oriel.ui.editor.new_interface"));
        Dom.byId(doc, "new-w").setValue(Integer.toString(boardW));
        Dom.byId(doc, "new-h").setValue(Integer.toString(boardH));
        for (Element t : Dom.byId(doc, "new-sizes").children()) {
            t.toggleClass("sel", t.getAttribute("data-w").equals(Integer.toString(boardW))
                    && t.getAttribute("data-h").equals(Integer.toString(boardH)));
        }
        Dom.byId(doc, "new-box").addClass("on");
        Dom.byId(doc, "new-name").focus();
    }

    /** Starts a new document: an empty artboard of this size (or the sample on it), with a fresh history. */
    void newCanvas(String name, int w, int h, boolean withSample) {
        parts.clear();
        selection.clear();
        undo.clear();
        redo.clear();
        nextId = 1;
        boardW = w;
        boardH = h;
        docName = name;
        if (withSample) sample();
        render();
        status(Lang.t(doc, "oriel.ui.editor.created") + name + "」 · " + w + " × " + h);
    }

    private static int parse(String v, int fallback) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- Clipboard ----

    void copy(boolean cut) {
        if (selection.isEmpty()) return;
        clipboard.clear();
        for (Part p : selected()) clipboard.add(p.copy());
        pastes = 0;
        if (cut) {
            checkpoint();
            parts.removeIf(p -> selection.contains(p.id));
            selection.clear();
            render();
        }
        status((cut ? Lang.t(doc, "oriel.ui.editor.cut_2") : Lang.t(doc, "oriel.ui.editor.copied")) + clipboard.size() + Lang.t(doc, "oriel.ui.editor.components"));
        for (Element t : doc.querySelectorAll("#ctx .clip")) t.toggleClass("disabled", false);
    }

    void paste() {
        if (clipboard.isEmpty()) return;
        checkpoint();
        int offset = 8 * ++pastes;
        selection.clear();
        for (Part c : clipboard) {
            Part p = c.copy();
            p.id = nextId++;
            p.w = Math.min(p.w, boardW);
            p.h = Math.min(p.h, boardH);
            p.x = clamp(c.x + offset, 0, boardW - p.w);
            p.y = clamp(c.y + offset, 0, boardH - p.h);
            parts.add(p);
            selection.add(p.id);
        }
        render();
        status(Lang.t(doc, "oriel.ui.editor.pasted") + clipboard.size() + Lang.t(doc, "oriel.ui.editor.components"));
    }

    // ---- Item picker ----

    static final int PICK_LIMIT = 120;

    private void wirePicker() {
        Dom.byId(doc, "pick-q").addEventListener("input", e -> renderPicker());
        for (Element k : doc.querySelectorAll("#pick-kind [data-k]")) {
            Shell.activate(k, () -> {
                pickKind = k.getAttribute("data-k");
                renderPicker();
            });
        }
        Shell.activate(Dom.byId(doc, "pick-close"), this::closePicker);
    }

    /** Opens the picker for {@code p}'s item (a slot) or block (a 3D block), listing what the game has registered. */
    void openPicker(Part p) {
        picking = p;
        GameRegistry.refresh();
        boolean entity = p.kind.equals("entity");
        if (entity) GameRegistry.refreshEntities();
        pickKind = entity ? "entity" : p.kind.equals("block") ? "block" : "all";
        for (Element k : doc.querySelectorAll("#pick-kind [data-k]")) k.toggleClass("on", k.getAttribute("data-k").equals(pickKind));
        Dom.byId(doc, "pick-kind").toggleClass("o-hidden", entity);
        Dom.text(Dom.byId(doc, "pick-title"), entity ? Lang.t(doc, "oriel.ui.editor.select_entity") : p.kind.equals("block") ? Lang.t(doc, "oriel.ui.editor.select_block") : Lang.t(doc, "oriel.ui.editor.select_item"));
        Dom.byId(doc, "pick-q").setValue("");
        Dom.text(Dom.byId(doc, "pick-hover"), "");
        Dom.byId(doc, "pick-box").addClass("on");
        Dom.byId(doc, "pick-q").focus();
        renderPicker();
    }

    private void closePicker() {
        picking = null;
        Dom.byId(doc, "pick-box").removeClass("on");
    }

    private void renderPicker() {
        Element grid = Dom.byId(doc, "pick-grid");
        grid.removeAllChildren();
        String query = Dom.byId(doc, "pick-q").value().trim().toLowerCase(Locale.ROOT);
        int total = 0;
        boolean entity = pickKind.equals("entity");
        for (GameRegistry.Entry e : entity ? GameRegistry.entities(doc) : GameRegistry.entries(doc)) {
            if (pickKind.equals("block") && !e.block() || pickKind.equals("item") && e.block()) continue;
            if (!e.matches(query)) continue;
            if (++total > PICK_LIMIT) continue;
            Element cell = Dom.el(doc, "div", "pick", null);
            cell.setAttribute("data-id", e.id());
            cell.toggleClass("on", picking != null && e.id().equals(picking.text));
            cell.appendChild(entity ? entityFace(e.id()) : itemFace(e.id()));
            cell.appendChild(Dom.el(doc, "span", "nm", e.name()));
            Dom.onMouse(cell, "mouseenter", ev -> Dom.text(Dom.byId(doc, "pick-hover"), e.name() + "  " + e.id()));
            Shell.activate(cell, () -> choose(e));
            grid.appendChild(cell);
        }
        Dom.text(Dom.byId(doc, "pick-count"), total == 0 ? Lang.t(doc, "oriel.ui.editor.not_found") : total > PICK_LIMIT
                ? Lang.t(doc, "oriel.ui.editor.total") + total + Lang.t(doc, "oriel.ui.editor.items_showing_the_first") + PICK_LIMIT + Lang.t(doc, "oriel.ui.editor.items_keep_typing_to_narrow_the_search") : Lang.t(doc, "oriel.ui.editor.total") + total + Lang.t(doc, "oriel.ui.editor.items"));
    }

    private void choose(GameRegistry.Entry e) {
        Part p = picking;
        closePicker();
        if (p == null) return;
        edit(() -> p.text = e.id());
        status(Lang.t(doc, "oriel.ui.editor.changed_to") + e.name());
    }


    private record State(List<Part> parts, Set<Integer> selection, Deque<List<Part>> undo, Deque<List<Part>> redo, int nextId,
                         int boardW, int boardH, String docName) {}

    @Override
    public Object saveState() {
        return new State(snapshot(), new LinkedHashSet<>(selection), new ArrayDeque<>(undo), new ArrayDeque<>(redo), nextId,
                boardW, boardH, docName);
    }

    @Override
    public void restoreState(Object state) {
        if (!(state instanceof State s)) return;
        parts.clear();
        parts.addAll(s.parts());
        selection.clear();
        selection.addAll(s.selection());
        undo.clear();
        undo.addAll(s.undo());
        redo.clear();
        redo.addAll(s.redo());
        nextId = s.nextId();
        boardW = s.boardW();
        boardH = s.boardH();
        docName = s.docName();
        render();
    }

    /** A starting screen: a shop card, so there is something to edit. */
    private void sample() {
        add("panel", 8, 8, 248, 156).name = Lang.t(doc, "oriel.ui.editor.background_panel");
        Part title = add("title", 18, 16, 140, 18);
        title.text = Lang.t(doc, "oriel.ui.editor.merchant_s_shelf");
        title.name = Lang.t(doc, "oriel.ui.editor.title");
        Part sub = add("text", 18, 38, 160, 10);
        sub.text = Lang.t(doc, "oriel.ui.editor.today_s_special_enchanted_diamond_sword");
        sub.name = Lang.t(doc, "oriel.ui.editor.subtitle");
        Part block = add("block", 176, 14, 64, 64);
        block.text = "minecraft:enchanting_table";
        block.name = Lang.t(doc, "oriel.ui.editor.display_block");
        Part slot = add("slot", 18, 60, 24, 24);
        slot.text = "minecraft:diamond_sword";
        slot.tier = "epic";
        slot.name = Lang.t(doc, "oriel.ui.editor.product_slot");
        Part meter = add("meter", 50, 72, 110, 7);
        meter.name = Lang.t(doc, "oriel.ui.editor.inventory");
        Part buy = add("button", 168, 132, 76, 22);
        buy.text = Lang.t(doc, "oriel.ui.editor.purchase");
        buy.variant = "hero";
        buy.name = Lang.t(doc, "oriel.ui.editor.purchase_button");
        Part cancel = add("button", 98, 132, 64, 22);
        cancel.text = Lang.t(doc, "oriel.ui.editor.leave");
        cancel.variant = "secondary";
        cancel.name = Lang.t(doc, "oriel.ui.editor.leave_button");
        undo.clear();
    }

    Part add(String kind, int x, int y, int w, int h) {
        Part p = new Part();
        p.id = nextId++;
        p.kind = kind;
        p.name = Lang.value(doc, KIND_NAMES.getOrDefault(kind, kind)) + " " + p.id;
        p.x = x;
        p.y = y;
        p.w = w;
        p.h = h;
        p.text = switch (kind) {
            case "button" -> Lang.t(doc, "oriel.ui.editor.button");
            case "title" -> Lang.t(doc, "oriel.ui.editor.title");
            case "text" -> Lang.t(doc, "oriel.ui.editor.some_text");
            case "slot" -> "minecraft:emerald";
            case "block" -> "minecraft:grass_block";
            case "entity" -> "minecraft:fox";
            default -> "";
        };
        p.variant = kind.equals("button") ? "primary" : "";
        p.tier = kind.equals("panel") || kind.equals("slot") ? "common" : "";
        parts.add(p);
        return p;
    }

    private static int[] defaultSize(String kind) {
        return switch (kind) {
            case "entity" -> new int[] {48, 64};
            case "button" -> new int[] {64, 20};
            case "panel" -> new int[] {96, 64};
            case "title" -> new int[] {96, 18};
            case "text" -> new int[] {96, 10};
            case "slot" -> new int[] {24, 24};
            case "toggle" -> new int[] {28, 14};
            case "meter" -> new int[] {80, 7};
            default -> new int[] {48, 48};
        };
    }

    /** Adds a part from the library at board coordinates, selected, as one undo step. */
    void place(String kind, int x, int y) {
        checkpoint();
        int[] size = defaultSize(kind);
        Part p = add(kind, clamp(snap(x - size[0] / 2), 0, boardW - size[0]), clamp(snap(y - size[1] / 2), 0, boardH - size[1]), size[0], size[1]);
        selection.clear();
        selection.add(p.id);
        render();
        status(Lang.value(doc, KIND_NAMES.get(kind)) + Lang.t(doc, "oriel.ui.editor.added"));
    }

    // ---- History ----

    private List<Part> snapshot() {
        List<Part> copy = new ArrayList<>();
        for (Part p : parts) copy.add(p.copy());
        return copy;
    }

    /** Records the current state as an undo step (before a change). */
    void checkpoint() {
        undo.push(snapshot());
        while (undo.size() > 100) undo.removeLast();
        redo.clear();
    }

    void undo() {
        if (undo.isEmpty()) return;
        redo.push(snapshot());
        restore(undo.pop());
        status(Lang.t(doc, "oriel.ui.editor.undone"));
    }

    void redo() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
        status(Lang.t(doc, "oriel.ui.editor.redone"));
    }

    private void restore(List<Part> state) {
        parts.clear();
        parts.addAll(state);
        selection.removeIf(id -> find(id) == null);
        render();
    }

    // ---- Edits ----

    void delete() {
        if (selection.isEmpty()) return;
        checkpoint();
        int n = selection.size();
        parts.removeIf(p -> selection.contains(p.id));
        selection.clear();
        render();
        status(Lang.t(doc, "oriel.ui.editor.deleted") + n + Lang.t(doc, "oriel.ui.editor.components"));
    }

    void duplicate() {
        if (selection.isEmpty()) return;
        checkpoint();
        Set<Integer> copies = new LinkedHashSet<>();
        for (Part p : selected()) {
            Part c = p.copy();
            c.id = nextId++;
            c.name = p.name + Lang.t(doc, "oriel.ui.editor.copies");
            c.x = clamp(p.x + 8, 0, boardW - p.w);
            c.y = clamp(p.y + 8, 0, boardH - p.h);
            parts.add(c);
            copies.add(c.id);
        }
        selection.clear();
        selection.addAll(copies);
        render();
        status(Lang.t(doc, "oriel.ui.editor.copied") + copies.size() + Lang.t(doc, "oriel.ui.editor.components"));
    }

    void nudge(int dx, int dy) {
        if (selection.isEmpty()) return;
        checkpoint();
        for (Part p : selected()) {
            if (p.locked) continue;
            p.x = clamp(p.x + dx, 0, boardW - p.w);
            p.y = clamp(p.y + dy, 0, boardH - p.h);
        }
        render();
    }

    /** Aligns the selection: left, centre (horizontal), top, or spreads it evenly across. */
    void align(String how) {
        List<Part> sel = selected();
        if (sel.size() < 2) return;
        checkpoint();
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE;
        for (Part p : sel) {
            left = Math.min(left, p.x);
            top = Math.min(top, p.y);
            right = Math.max(right, p.x + p.w);
        }
        final int minX = left, minY = top;
        switch (how) {
            case "left" -> sel.forEach(p -> p.x = minX);
            case "top" -> sel.forEach(p -> p.y = minY);
            case "centre" -> {
                int mid = (left + right) / 2;
                sel.forEach(p -> p.x = mid - p.w / 2);
            }
            case "spread" -> {
                sel.sort((a, b) -> Integer.compare(a.x, b.x));
                int total = 0;
                for (Part p : sel) total += p.w;
                float gap = (right - left - total) / (float) (sel.size() - 1), x = left;
                for (Part p : sel) {
                    p.x = Math.round(x);
                    x += p.w + gap;
                }
            }
            default -> { }
        }
        render();
    }

    /** A menu or options-bar command. */
    void command(String cmd) {
        switch (cmd) {
            case "export" -> export();
            case "cut" -> copy(true);
            case "copy" -> copy(false);
            case "paste" -> paste();
            case "all" -> selectAll();
            case "align-left" -> align("left");
            case "align-top" -> align("top");
            case "align-centre" -> align("centre");
            case "align-spread" -> align("spread");
            case "front" -> restack(true);
            case "back" -> restack(false);
            case "preview" -> togglePreview();
            case "dup" -> duplicate();
            case "delete" -> delete();
            case "new" -> openNew();
            case "snap" -> toggleSnap();
            default -> { }
        }
    }

    void selectAll() {
        selection.clear();
        for (Part p : parts) if (p.visible) selection.add(p.id);
        render();
    }

    void toggleSnap() {
        snap = !snap;
        Dom.byId(doc, "snap").toggleClass("off", !snap);
        Dom.query(doc, "[data-cmd=snap]").toggleClass("on", snap);
        status(snap ? Lang.t(doc, "oriel.ui.editor.snap_to_grid_on") : Lang.t(doc, "oriel.ui.editor.snap_to_grid_off"));
    }

    private void togglePreview() {
        preview = !preview;
        Dom.byId(doc, "viewport").toggleClass("preview-mode", preview);
        Dom.byId(doc, "preview").toggleClass("on", preview);
        Dom.query(doc, "[data-cmd=preview]").toggleClass("on", preview);
        status(preview ? Lang.t(doc, "oriel.ui.editor.preview_selection_box_hidden") : Lang.t(doc, "oriel.ui.editor.edit"));
    }

    // ---- Pointer ----

    private float[] boardPoint(MouseEvent e) {
        float[] r = board.getBoundingClientRect();
        return new float[] {e.clientX - r[0], e.clientY - r[1]};
    }

    private void boardPress(MouseEvent e) {
        if (e.button == 2 && !preview) {
            // A right press selects what it is on (keeping a selection it is part of), then the menu opens.
            Element hit = Dom.closest(e, ".node");
            Integer id = hit == null ? null : Integer.valueOf(hit.getAttribute("data-id"));
            if (id == null) selection.clear();
            else if (!selection.contains(id)) {
                selection.clear();
                selection.add(id);
            }
            // Redrawn next frame: the menu still looks up the pressed element during this event.
            doc.scheduler().requestAnimationFrame(t -> render());
            return;
        }
        if (e.button != 0 || preview) return;
        float[] at = boardPoint(e);
        Element grab = Dom.closest(e, ".grab");
        Element hit = Dom.closest(e, ".node");
        boolean add = e.modifiers.ctrl() || e.modifiers.shift();
        if (hit == null) {
            if (!add) selection.clear();
            render();
            return;
        }
        int id = Integer.parseInt(hit.getAttribute("data-id"));
        if (add) {
            if (!selection.remove(id)) selection.add(id);
        } else if (!selection.contains(id)) {
            selection.clear();
            selection.add(id);
        } else {
            // Pressing a selected part makes it the primary one (the inspector's).
            selection.remove(id);
            List<Integer> rest = new ArrayList<>(selection);
            selection.clear();
            selection.add(id);
            selection.addAll(rest);
        }
        startX = at[0];
        startY = at[1];
        moved = false;
        before = snapshot();
        origin.clear();
        for (Part p : selected()) origin.put(p.id, new int[] {p.x, p.y, p.w, p.h});
        gesture = grab != null ? Gesture.RESIZE : Gesture.MOVE;
        e.preventDefault();
        render();
    }

    private void pointerMove(MouseEvent e) {
        float[] at = boardPoint(e);
        Dom.text(Dom.byId(doc, "pointer"), Math.round(at[0]) + ", " + Math.round(at[1]));
        switch (gesture) {
            case PLACE -> {
                if (Math.abs(e.clientX - startX) + Math.abs(e.clientY - startY) > 3) moved = true;
                if (moved) {
                    Dom.text(ghost, "+ " + Lang.value(doc, KIND_NAMES.get(placing)));
                    new Dom.Style(ghost).px("left", e.clientX + 6).px("top", e.clientY + 6).apply();
                    ghost.addClass("on");
                }
            }
            case MOVE -> drag(at);
            case RESIZE -> resize(at);
            default -> { }
        }
    }

    private void drag(float[] at) {
        int dx = Math.round(at[0] - startX), dy = Math.round(at[1] - startY);
        if (!moved && Math.abs(dx) + Math.abs(dy) < 2) return;
        moved = true;
        Part lead = primary();
        int[] o = origin.get(lead.id);
        int nx = o[0] + dx, ny = o[1] + dy;
        if (snap) {
            nx = snap(nx);
            ny = snap(ny);
        }
        // Guides: the lead's left, centre or right against other parts' edges and centres and the board's centre.
        int[] gx = guide(nx, lead.w, true), gy = guide(ny, lead.h, false);
        nx = gx[0];
        ny = gy[0];
        showGuide(guideV, gx[1], true);
        showGuide(guideH, gy[1], false);
        int ddx = nx - o[0], ddy = ny - o[1];
        for (Part p : selected()) {
            if (p.locked) continue;
            int[] po = origin.get(p.id);
            p.x = clamp(po[0] + ddx, 0, boardW - p.w);
            p.y = clamp(po[1] + ddy, 0, boardH - p.h);
        }
        renderBoard();
        renderInspector();
    }

    /** Snaps a position to the nearest line within {@link #SNAP_RANGE}; returns {position, guide line or -1}. */
    private int[] guide(int pos, int size, boolean horizontal) {
        List<Integer> lines = new ArrayList<>();
        lines.add((horizontal ? boardW : boardH) / 2);
        for (Part p : parts) {
            if (selection.contains(p.id) || !p.visible) continue;
            int a = horizontal ? p.x : p.y, s = horizontal ? p.w : p.h;
            lines.add(a);
            lines.add(a + s / 2);
            lines.add(a + s);
        }
        int[] anchors = {0, size / 2, size};
        int best = SNAP_RANGE + 1, line = -1, at = pos;
        for (int l : lines) {
            for (int k : anchors) {
                int d = Math.abs(pos + k - l);
                if (d < best) {
                    best = d;
                    line = l;
                    at = l - k;
                }
            }
        }
        return best <= SNAP_RANGE ? new int[] {at, line} : new int[] {pos, -1};
    }

    private void showGuide(Element g, int line, boolean vertical) {
        g.toggleClass("on", line >= 0);
        if (line >= 0) new Dom.Style(g).px(vertical ? "left" : "top", line).apply();
    }

    private void resize(float[] at) {
        Part p = primary();
        if (p.locked) return;
        int[] o = origin.get(p.id);
        int w = o[2] + Math.round(at[0] - startX), h = o[3] + Math.round(at[1] - startY);
        if (snap) {
            w = snap(w);
            h = snap(h);
        }
        moved = true;
        p.w = clamp(w, 4, boardW - p.x);
        p.h = clamp(h, 4, boardH - p.y);
        renderBoard();
        renderInspector();
    }

    private void pointerUp(MouseEvent e) {
        if (gesture == Gesture.PLACE) {
            ghost.removeClass("on");
            float[] r = board.getBoundingClientRect(), at = boardPoint(e);
            boolean onBoard = at[0] >= 0 && at[1] >= 0 && at[0] < r[2] && at[1] < r[3];
            if (!moved) place(placing, boardW / 2, boardH / 2);
            else if (onBoard) place(placing, Math.round(at[0]), Math.round(at[1]));
        } else if ((gesture == Gesture.MOVE || gesture == Gesture.RESIZE) && moved && changedSince(before)) {
            undo.push(before);
            redo.clear();
            status(gesture == Gesture.MOVE ? Lang.t(doc, "oriel.ui.editor.moved") + selection.size() + Lang.t(doc, "oriel.ui.editor.components") : Lang.t(doc, "oriel.ui.editor.resized_by"));
            render();
        }
        gesture = Gesture.NONE;
        guideV.removeClass("on");
        guideH.removeClass("on");
    }

    /** Whether any part's box differs from {@code state} (a press that snapped back to where it was is no edit). */
    private boolean changedSince(List<Part> state) {
        if (state.size() != parts.size()) return true;
        for (Part old : state) {
            Part now = find(old.id);
            if (now == null || now.x != old.x || now.y != old.y || now.w != old.w || now.h != old.h) return true;
        }
        return false;
    }

    // ---- Keys ----

    private void key(KeyboardEvent e) {
        if (e.defaultPrevented()) return; // an open menu took it
        Element focus = doc.focusedElement();
        if (focus != null && focus.isTextControl()) {
            if (e.key.equals("Escape") && doc.querySelector(".m-modal.on") != null) {
                doc.querySelector(".m-modal.on").removeClass("on");
                picking = null;
                e.preventDefault();
            }
            return;
        }
        // With a dialog open, only Escape and the shortcut sheet reach the artboard.
        if (doc.querySelector(".m-modal.on") != null && !e.key.equals("Escape") && !e.key.equals("F1")) return;
        boolean ctrl = e.modifiers.ctrl() || e.modifiers.meta();
        int step = e.modifiers.shift() ? GRID : 1;
        boolean handled = true;
        switch (e.key) {
            case "z", "Z" -> {
                if (!ctrl) handled = false;
                else if (e.modifiers.shift()) redo();
                else undo();
            }
            case "y", "Y" -> {
                if (ctrl) redo();
                else handled = false;
            }
            case "d", "D" -> {
                if (ctrl) duplicate();
                else handled = false;
            }
            case "c", "C" -> {
                if (ctrl) copy(false);
                else handled = false;
            }
            case "x", "X" -> {
                if (ctrl) copy(true);
                else handled = false;
            }
            case "v", "V" -> {
                if (ctrl) paste();
                else handled = false;
            }
            case "n", "N" -> {
                if (ctrl) openNew();
                else handled = false;
            }
            case "s", "S" -> {
                if (ctrl) export();
                else handled = false;
            }
            case "F1", "?" -> Dom.byId(doc, "keys-box").toggleClass("on");
            case "a", "A" -> {
                if (ctrl) selectAll();
                else handled = false;
            }
            case "Delete", "Backspace" -> delete();
            case "Escape" -> {
                Element open = doc.querySelector(".m-modal.on");
                if (open != null) {
                    open.removeClass("on");
                    picking = null;
                } else {
                    selection.clear();
                    render();
                }
            }
            case "ArrowLeft" -> nudge(-step, 0);
            case "ArrowRight" -> nudge(step, 0);
            case "ArrowUp" -> nudge(0, -step);
            case "ArrowDown" -> nudge(0, step);
            case "p", "P" -> togglePreview();
            default -> handled = false;
        }
        if (handled) e.preventDefault();
    }

    // ---- View ----

    void render() {
        new Dom.Style(board).px("width", boardW).px("height", boardH).apply();
        Dom.text(Dom.byId(doc, "board-label"), docName + " · " + boardW + " × " + boardH);
        Dom.text(Dom.byId(doc, "doc-name"), "/ " + docName);
        Dom.text(Dom.byId(doc, "board-size"), boardW + " × " + boardH);
        for (Element t : doc.querySelectorAll(".optbar .multi")) t.toggleClass("disabled", selection.size() < 2);
        for (Element t : doc.querySelectorAll(".optbar .single, #ctx .sel")) t.toggleClass("disabled", selection.isEmpty());
        for (Element t : doc.querySelectorAll("#ctx .clip")) t.toggleClass("disabled", clipboard.isEmpty());
        renderBoard();
        renderTree();
        renderInspector();
        Dom.text(Dom.byId(doc, "history"), undo.size() + Lang.t(doc, "oriel.ui.editor.steps"));
        Dom.text(Dom.byId(doc, "sel-count"), selection.isEmpty() ? Lang.t(doc, "oriel.ui.editor.none_selected") : Lang.t(doc, "oriel.ui.editor.selected") + selection.size() + Lang.t(doc, "oriel.ui.editor.items"));
        Dom.byId(doc, "undo").toggleClass("disabled", undo.isEmpty());
        Dom.byId(doc, "redo").toggleClass("disabled", redo.isEmpty());
    }

    private void renderBoard() {
        for (Element n : List.copyOf(board.querySelectorAll(".node"))) n.remove();
        Integer lead = selection.isEmpty() ? null : selection.iterator().next();
        for (Part p : parts) {
            Element n = Dom.el(doc, "div", "node k-" + p.kind, null);
            n.setAttribute("data-id", Integer.toString(p.id));
            n.setAttribute("style", "left: " + p.x + "px; top: " + p.y + "px; width: " + p.w + "px; height: " + p.h + "px");
            n.toggleClass("sel", selection.contains(p.id));
            n.toggleClass("primary", lead != null && lead == p.id);
            n.toggleClass("hidden", !p.visible);
            n.toggleClass("locked", p.locked);
            Element body = Dom.html(doc, "<div class=\"body\">" + inner(p) + "</div>");
            n.appendChild(body);
            n.appendChild(Dom.el(doc, "div", "grab", null));
            board.appendChild(n);
        }
    }

    /** The markup a part shows (also what export writes). */
    static String inner(Part p) {
        String text = escape(p.text);
        return switch (p.kind) {
            case "button" -> "<div class=\"ore-btn" + (p.variant.equals("neutral") ? "" : " " + p.variant) + "\">" + text + "</div>";
            case "panel" -> "<div class=\"o-tile r-" + p.tier + (p.tier.equals("epic") || p.tier.equals("legendary") ? " fx-breathe" : "")
                    + "\" style=\"width: 100%; height: 100%\"></div>";
            case "title", "text" -> text;
            case "slot" -> "<div class=\"ore-slot\"><item id=\"" + text + "\"></item></div>";
            case "toggle" -> "<div class=\"ore-toggle on\"></div>";
            case "meter" -> "<div class=\"o-meter\" style=\"--value: 0.6\"><i></i></div>";
            case "entity" -> "<entity type=\"" + text + "\"></entity>";
            case "block" -> GameRegistry.isBlock(p.text) ? "<model block=\"" + text + "\"></model>" : "<model item=\"" + text + "\"></model>";
            default -> "";
        };
    }

    private void renderTree() {
        tree.removeAllChildren();
        Integer lead = selection.isEmpty() ? null : selection.iterator().next();
        // Top of the stack first, as in a layers panel.
        for (int i = parts.size() - 1; i >= 0; i--) {
            Part p = parts.get(i);
            if (!filter.isEmpty() && !p.name.toLowerCase(Locale.ROOT).contains(filter)) continue;
            Element row = Dom.el(doc, "div", "layer", null);
            row.toggleClass("sel", selection.contains(p.id));
            row.toggleClass("hidden", !p.visible);
            row.setAttribute("data-id", Integer.toString(p.id));
            row.appendChild(icon(KIND_ICONS.getOrDefault(p.kind, "layers"), "o-ico"));
            row.appendChild(Dom.el(doc, "span", "nm", p.name));
            if (p.kind.equals("slot") || p.kind.equals("block")) row.setAttribute("title", Lang.value(doc, GameRegistry.name(p.text)) + " · " + p.text);
            if (p.kind.equals("entity")) row.setAttribute("title", Lang.value(doc, GameRegistry.entityName(p.text)) + " · " + p.text);
            Element eye = icon(p.visible ? "eye" : "minus", "o-ico tg" + (p.visible ? "" : " on"));
            Element lock = icon(p.locked ? "lock" : "unlock", "o-ico tg" + (p.locked ? " on" : ""));
            row.appendChild(eye);
            row.appendChild(lock);
            Dom.onMouse(eye, "mousedown", e -> {
                e.stopPropagation();
                checkpoint();
                p.visible = !p.visible;
                if (!p.visible) selection.remove(p.id);
                render();
            });
            Dom.onMouse(lock, "mousedown", e -> {
                e.stopPropagation();
                checkpoint();
                p.locked = !p.locked;
                render();
            });
            Dom.onMouse(row, "mousedown", e -> {
                if (e.modifiers.ctrl() || e.modifiers.shift()) {
                    if (!selection.remove(p.id)) selection.add(p.id);
                } else {
                    selection.clear();
                    selection.add(p.id);
                }
                render();
            });
            tree.appendChild(row);
        }
        if (lead == null && tree.childCount() == 0) tree.appendChild(Dom.el(doc, "div", "empty-sel", Lang.t(doc, "oriel.ui.editor.no_matching_layers")));
    }

    private void renderInspector() {
        inspector.removeAllChildren();
        List<Part> sel = selected();
        if (sel.isEmpty()) {
            inspector.appendChild(Dom.el(doc, "div", "empty-sel", Lang.t(doc, "oriel.ui.editor.click_a_component_on_the_canvas_or")));
            inspector.appendChild(Dom.el(doc, "div", "o-caps", Lang.t(doc, "oriel.ui.editor.canvas")));
            inspector.appendChild(Dom.el(doc, "div", "empty-sel", boardW + " × " + boardH + " · " + parts.size() + Lang.t(doc, "oriel.ui.editor.components")));
            return;
        }
        Part p = sel.getFirst();
        Element kind = Dom.el(doc, "div", "kind", null);
        kind.appendChild(icon(KIND_ICONS.getOrDefault(p.kind, "layers"), "o-ico"));
        kind.appendChild(doc.createTextNode(Lang.value(doc, KIND_NAMES.getOrDefault(p.kind, p.kind)) + (sel.size() > 1 ? Lang.t(doc, "oriel.ui.editor.etc") + sel.size() + Lang.t(doc, "oriel.ui.editor.items") : "")));
        inspector.appendChild(kind);
        inspector.appendChild(field(Lang.t(doc, "oriel.ui.editor.name"), p.name, v -> p.name = v));
        Element pos = Dom.el(doc, "div", "pair", null);
        pos.appendChild(number("X", p.x, v -> p.x = clamp(v, 0, boardW - p.w)));
        pos.appendChild(number("Y", p.y, v -> p.y = clamp(v, 0, boardH - p.h)));
        pos.appendChild(number(Lang.t(doc, "oriel.ui.editor.width"), p.w, v -> p.w = clamp(v, 4, boardW - p.x)));
        pos.appendChild(number(Lang.t(doc, "oriel.ui.editor.high"), p.h, v -> p.h = clamp(v, 4, boardH - p.y)));
        inspector.appendChild(pos);
        if (List.of("button", "title", "text").contains(p.kind)) inspector.appendChild(field(Lang.t(doc, "oriel.ui.editor.text_2"), p.text, v -> p.text = v));
        if (List.of("slot", "block", "entity").contains(p.kind)) inspector.appendChild(picked(p));
        if (p.kind.equals("button")) {
            inspector.appendChild(Dom.el(doc, "div", "o-caps", Lang.t(doc, "oriel.ui.editor.style")));
            Element seg = Dom.el(doc, "div", "o-seg", null);
            for (String v : VARIANTS) {
                Element o = Dom.el(doc, "span", v.equals(p.variant) ? "on" : null, variantName(v));
                Dom.onClick(o, e -> edit(() -> p.variant = v));
                seg.appendChild(o);
            }
            inspector.appendChild(seg);
        }
        if (p.kind.equals("panel") || p.kind.equals("slot")) {
            inspector.appendChild(Dom.el(doc, "div", "o-caps", Lang.t(doc, "oriel.ui.editor.rarity")));
            Element tiers = Dom.el(doc, "div", "tiers", null);
            for (String t : TIERS) {
                Element o = Dom.el(doc, "span", "r-" + t + (t.equals(p.tier) ? " on" : ""), null);
                o.setAttribute("title", t);
                Dom.onClick(o, e -> edit(() -> p.tier = t));
                tiers.appendChild(o);
            }
            inspector.appendChild(tiers);
        }
    }

    void restack(boolean front) {
        List<Part> sel = selected();
        if (sel.isEmpty()) return;
        checkpoint();
        parts.removeAll(sel);
        if (front) parts.addAll(sel);
        else parts.addAll(0, sel);
        render();
    }

    /** The item a slot or block shows: its icon, the game's name for it and the id; a press opens the picker. */
    private Element picked(Part p) {
        Element card = Dom.el(doc, "div", "picked o-tile hot", null);
        card.setAttribute("tabindex", "0");
        card.setAttribute("id", "picked");
        boolean entity = p.kind.equals("entity");
        card.appendChild(entity ? entityFace(p.text) : itemFace(p.text));
        Element words = Dom.el(doc, "div", "words", null);
        words.appendChild(Dom.el(doc, "b", null, entity ? Lang.value(doc, GameRegistry.entityName(p.text)) : Lang.value(doc, GameRegistry.name(p.text))));
        words.appendChild(Dom.el(doc, "span", null, p.text));
        card.appendChild(words);
        card.appendChild(icon("search", "o-ico"));
        Shell.activate(card, () -> openPicker(p));
        return card;
    }

    private Element itemFace(String id) {
        Element item = Dom.el(doc, "item", null, null);
        item.setAttribute("id", id);
        return item;
    }

    /** An entity type's face in lists: its spawn egg when it has one, else a paw. */
    private Element entityFace(String id) {
        String egg = GameRegistry.spawnEgg(id);
        if (egg != null) return itemFace(egg);
        return icon("paw", "o-ico face");
    }

    private Element field(String label, String value, java.util.function.Consumer<String> apply) {
        Element f = Dom.el(doc, "div", "field", null);
        f.appendChild(Dom.el(doc, "label", null, label));
        Element input = Dom.el(doc, "input", "ore-field", null);
        input.setValue(value);
        input.addEventListener("change", e -> edit(() -> apply.accept(input.value())));
        f.appendChild(input);
        return f;
    }

    private Element number(String label, int value, java.util.function.IntConsumer apply) {
        return field(label, Integer.toString(value), v -> {
            try {
                apply.accept(Integer.parseInt(v.trim()));
            } catch (NumberFormatException ignored) {
                status("「" + v + Lang.t(doc, "oriel.ui.editor.is_not_a_number"));
            }
        });
    }

    /** One inspector edit: a checkpoint, the change, a redraw. */
    void edit(Runnable change) {
        checkpoint();
        change.run();
        render();
    }

    private String variantName(String v) {
        return switch (v) {
            case "hero" -> Lang.t(doc, "oriel.ui.editor.main");
            case "primary" -> Lang.t(doc, "oriel.ui.editor.confirm");
            case "secondary" -> Lang.t(doc, "oriel.ui.editor.secondary");
            case "destructive" -> Lang.t(doc, "oriel.ui.editor.critical");
            default -> Lang.t(doc, "oriel.ui.editor.normal");
        };
    }

    // ---- Export ----

    /** The screen as a page on the Ore theme. */
    String html() {
        StringBuilder b = new StringBuilder();
        b.append("<!doctype html>\n<html>\n<head>\n<link rel=\"stylesheet\" href=\"ore.css\">\n<style>\n")
                .append(".screen { position: relative; width: ").append(boardW).append("px; height: ").append(boardH).append("px; }\n")
                .append(".screen > * { position: absolute; display: flex; align-items: center; }\n</style>\n</head>\n<body>\n")
                .append("<div class=\"o-app\">\n  <div class=\"screen\">\n");
        for (Part p : parts) {
            if (!p.visible) continue;
            b.append("    <div data-name=\"").append(escape(p.name)).append("\" style=\"left: ").append(p.x).append("px; top: ").append(p.y)
                    .append("px; width: ").append(p.w).append("px; height: ").append(p.h).append("px\">").append(inner(p)).append("</div>\n");
        }
        return b.append("  </div>\n</div>\n</body>\n</html>\n").toString();
    }

    private void export() {
        Dom.text(Dom.byId(doc, "code"), html());
        Dom.byId(doc, "export-box").addClass("on");
        doc.host().send(OrielKit.CHANNEL, "\"export\"");
        status(Lang.t(doc, "oriel.ui.editor.exported") + parts.size() + Lang.t(doc, "oriel.ui.editor.components"));
    }

    // ---- Helpers ----

    List<Part> selected() {
        List<Part> out = new ArrayList<>();
        for (int id : selection) {
            Part p = find(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private Part primary() {
        return find(selection.iterator().next());
    }

    Part find(int id) {
        for (Part p : parts) if (p.id == id) return p;
        return null;
    }

    private Element icon(String name, String classes) {
        Element img = Dom.el(doc, "img", classes, null);
        img.setAttribute("src", "../textures/icons/" + name + ".png");
        return img;
    }

    private void status(String text) {
        Dom.text(Dom.byId(doc, "msg"), text);
    }

    private int snap(int v) {
        return snap ? Math.round(v / (float) GRID) * GRID : v;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
