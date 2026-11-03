package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.event.MouseEvent;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
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
    static final int BOARD_W = 264, BOARD_H = 172, GRID = 4, SNAP_RANGE = 3;

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

    static final Map<String, String> KIND_NAMES = Map.of("button", "按钮", "panel", "面板", "title", "标题", "text", "文本",
            "slot", "物品格", "toggle", "开关", "meter", "进度条", "block", "3D 方块");
    static final Map<String, String> KIND_ICONS = Map.of("button", "mouse", "panel", "layers", "title", "file", "text", "list",
            "slot", "gem", "toggle", "sliders", "meter", "bolt", "block", "cube");
    static final List<String> VARIANTS = List.of("hero", "primary", "secondary", "destructive", "neutral");
    static final List<String> TIERS = List.of("common", "uncommon", "rare", "epic", "legendary");

    final List<Part> parts = new ArrayList<>();
    final Set<Integer> selection = new LinkedHashSet<>();
    final Deque<List<Part>> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private int nextId = 1;
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
        Shell.activate(Dom.byId(doc, "snap"), () -> {
            snap = !snap;
            Dom.byId(doc, "snap").toggleClass("off", !snap);
        });
        Shell.activate(Dom.byId(doc, "preview"), this::togglePreview);
        Shell.activate(Dom.byId(doc, "export"), this::export);
        Shell.activate(Dom.byId(doc, "export-close"), () -> Dom.byId(doc, "export-box").removeClass("on"));
        Shell.activate(Dom.byId(doc, "close"), () -> doc.host().close());
        Shell.activate(Dom.byId(doc, "back"), () -> doc.navigate(OrielKit.GALLERY));
        render();
    }

    private record State(List<Part> parts, Set<Integer> selection, Deque<List<Part>> undo, Deque<List<Part>> redo, int nextId) {}

    @Override
    public Object saveState() {
        return new State(snapshot(), new LinkedHashSet<>(selection), new ArrayDeque<>(undo), new ArrayDeque<>(redo), nextId);
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
        render();
    }

    /** A starting screen: a shop card, so there is something to edit. */
    private void sample() {
        add("panel", 8, 8, 248, 156).name = "背景面板";
        Part title = add("title", 18, 16, 140, 18);
        title.text = "商人的货架";
        title.name = "标题";
        Part sub = add("text", 18, 38, 160, 10);
        sub.text = "今日特价：附魔钻石剑";
        sub.name = "副标题";
        Part block = add("block", 176, 14, 64, 64);
        block.text = "minecraft:enchanting_table";
        block.name = "展示方块";
        Part slot = add("slot", 18, 60, 24, 24);
        slot.text = "minecraft:diamond_sword";
        slot.tier = "epic";
        slot.name = "商品格";
        Part meter = add("meter", 50, 72, 110, 7);
        meter.name = "库存";
        Part buy = add("button", 168, 132, 76, 22);
        buy.text = "购买";
        buy.variant = "hero";
        buy.name = "购买按钮";
        Part cancel = add("button", 98, 132, 64, 22);
        cancel.text = "离开";
        cancel.variant = "secondary";
        cancel.name = "离开按钮";
        undo.clear();
    }

    Part add(String kind, int x, int y, int w, int h) {
        Part p = new Part();
        p.id = nextId++;
        p.kind = kind;
        p.name = KIND_NAMES.getOrDefault(kind, kind) + " " + p.id;
        p.x = x;
        p.y = y;
        p.w = w;
        p.h = h;
        p.text = switch (kind) {
            case "button" -> "按钮";
            case "title" -> "标题";
            case "text" -> "一段文字";
            case "slot" -> "minecraft:emerald";
            case "block" -> "minecraft:grass_block";
            default -> "";
        };
        p.variant = kind.equals("button") ? "primary" : "";
        p.tier = kind.equals("panel") || kind.equals("slot") ? "common" : "";
        parts.add(p);
        return p;
    }

    private static int[] defaultSize(String kind) {
        return switch (kind) {
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
        Part p = add(kind, clamp(snap(x - size[0] / 2), 0, BOARD_W - size[0]), clamp(snap(y - size[1] / 2), 0, BOARD_H - size[1]), size[0], size[1]);
        selection.clear();
        selection.add(p.id);
        render();
        status(KIND_NAMES.get(kind) + " 已添加");
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
        status("已撤销");
    }

    void redo() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
        status("已重做");
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
        status("删除了 " + n + " 个组件");
    }

    void duplicate() {
        if (selection.isEmpty()) return;
        checkpoint();
        Set<Integer> copies = new LinkedHashSet<>();
        for (Part p : selected()) {
            Part c = p.copy();
            c.id = nextId++;
            c.name = p.name + " 副本";
            c.x = clamp(p.x + 8, 0, BOARD_W - p.w);
            c.y = clamp(p.y + 8, 0, BOARD_H - p.h);
            parts.add(c);
            copies.add(c.id);
        }
        selection.clear();
        selection.addAll(copies);
        render();
        status("复制了 " + copies.size() + " 个组件");
    }

    void nudge(int dx, int dy) {
        if (selection.isEmpty()) return;
        checkpoint();
        for (Part p : selected()) {
            if (p.locked) continue;
            p.x = clamp(p.x + dx, 0, BOARD_W - p.w);
            p.y = clamp(p.y + dy, 0, BOARD_H - p.h);
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

    private void togglePreview() {
        preview = !preview;
        Dom.byId(doc, "viewport").toggleClass("preview-mode", preview);
        Dom.byId(doc, "preview").toggleClass("on", preview);
        status(preview ? "预览：选择框已隐藏" : "编辑");
    }

    // ---- Pointer ----

    private float[] boardPoint(MouseEvent e) {
        float[] r = board.getBoundingClientRect();
        return new float[] {e.clientX - r[0], e.clientY - r[1]};
    }

    private void boardPress(MouseEvent e) {
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
                    Dom.text(ghost, "+ " + KIND_NAMES.get(placing));
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
            p.x = clamp(po[0] + ddx, 0, BOARD_W - p.w);
            p.y = clamp(po[1] + ddy, 0, BOARD_H - p.h);
        }
        renderBoard();
        renderInspector();
    }

    /** Snaps a position to the nearest line within {@link #SNAP_RANGE}; returns {position, guide line or -1}. */
    private int[] guide(int pos, int size, boolean horizontal) {
        List<Integer> lines = new ArrayList<>();
        lines.add((horizontal ? BOARD_W : BOARD_H) / 2);
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
        p.w = clamp(w, 4, BOARD_W - p.x);
        p.h = clamp(h, 4, BOARD_H - p.y);
        renderBoard();
        renderInspector();
    }

    private void pointerUp(MouseEvent e) {
        if (gesture == Gesture.PLACE) {
            ghost.removeClass("on");
            float[] r = board.getBoundingClientRect(), at = boardPoint(e);
            boolean onBoard = at[0] >= 0 && at[1] >= 0 && at[0] < r[2] && at[1] < r[3];
            if (!moved) place(placing, BOARD_W / 2, BOARD_H / 2);
            else if (onBoard) place(placing, Math.round(at[0]), Math.round(at[1]));
        } else if ((gesture == Gesture.MOVE || gesture == Gesture.RESIZE) && moved) {
            undo.push(before);
            redo.clear();
            status(gesture == Gesture.MOVE ? "移动了 " + selection.size() + " 个组件" : "调整了大小");
            render();
        }
        gesture = Gesture.NONE;
        guideV.removeClass("on");
        guideH.removeClass("on");
    }

    // ---- Keys ----

    private void key(KeyboardEvent e) {
        Element focus = doc.focusedElement();
        if (focus != null && focus.isTextControl()) return;
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
            case "a", "A" -> {
                if (!ctrl) handled = false;
                else {
                    selection.clear();
                    for (Part p : parts) if (p.visible) selection.add(p.id);
                    render();
                }
            }
            case "Delete", "Backspace" -> delete();
            case "Escape" -> {
                selection.clear();
                render();
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
        renderBoard();
        renderTree();
        renderInspector();
        Dom.text(Dom.byId(doc, "history"), undo.size() + " 步");
        Dom.text(Dom.byId(doc, "sel-count"), selection.isEmpty() ? "未选择" : "已选 " + selection.size() + " 个");
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
            case "block" -> "<model block=\"" + text + "\"></model>";
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
        if (lead == null && tree.childCount() == 0) tree.appendChild(Dom.el(doc, "div", "empty-sel", "没有匹配的图层"));
    }

    private void renderInspector() {
        inspector.removeAllChildren();
        List<Part> sel = selected();
        inspector.appendChild(Dom.el(doc, "div", "o-caps", "属性"));
        if (sel.isEmpty()) {
            inspector.appendChild(Dom.el(doc, "div", "empty-sel", "在画板上点选组件，或从左侧拖入新组件。"));
            inspector.appendChild(Dom.el(doc, "div", "o-caps", "画板"));
            inspector.appendChild(Dom.el(doc, "div", "empty-sel", BOARD_W + " × " + BOARD_H + " · " + parts.size() + " 个组件"));
            return;
        }
        Part p = sel.getFirst();
        Element kind = Dom.el(doc, "div", "kind", null);
        kind.appendChild(icon(KIND_ICONS.getOrDefault(p.kind, "layers"), "o-ico"));
        kind.appendChild(doc.createTextNode(KIND_NAMES.getOrDefault(p.kind, p.kind) + (sel.size() > 1 ? " 等 " + sel.size() + " 个" : "")));
        inspector.appendChild(kind);
        inspector.appendChild(field("名称", p.name, v -> p.name = v));
        Element pos = Dom.el(doc, "div", "pair", null);
        pos.appendChild(number("X", p.x, v -> p.x = clamp(v, 0, BOARD_W - p.w)));
        pos.appendChild(number("Y", p.y, v -> p.y = clamp(v, 0, BOARD_H - p.h)));
        pos.appendChild(number("宽", p.w, v -> p.w = clamp(v, 4, BOARD_W - p.x)));
        pos.appendChild(number("高", p.h, v -> p.h = clamp(v, 4, BOARD_H - p.y)));
        inspector.appendChild(pos);
        if (List.of("button", "title", "text").contains(p.kind)) inspector.appendChild(field("文字", p.text, v -> p.text = v));
        if (List.of("slot", "block").contains(p.kind)) inspector.appendChild(field("ID", p.text, v -> p.text = v));
        if (p.kind.equals("button")) {
            inspector.appendChild(Dom.el(doc, "div", "o-caps", "样式"));
            Element seg = Dom.el(doc, "div", "o-seg", null);
            for (String v : VARIANTS) {
                Element o = Dom.el(doc, "span", v.equals(p.variant) ? "on" : null, variantName(v));
                Dom.onClick(o, e -> edit(() -> p.variant = v));
                seg.appendChild(o);
            }
            inspector.appendChild(seg);
        }
        if (p.kind.equals("panel") || p.kind.equals("slot")) {
            inspector.appendChild(Dom.el(doc, "div", "o-caps", "稀有度"));
            Element tiers = Dom.el(doc, "div", "tiers", null);
            for (String t : TIERS) {
                Element o = Dom.el(doc, "span", "r-" + t + (t.equals(p.tier) ? " on" : ""), null);
                o.setAttribute("title", t);
                Dom.onClick(o, e -> edit(() -> p.tier = t));
                tiers.appendChild(o);
            }
            inspector.appendChild(tiers);
        }
        if (sel.size() > 1) {
            inspector.appendChild(Dom.el(doc, "div", "o-caps", "对齐"));
            Element aligns = Dom.el(doc, "div", "aligns", null);
            String[][] actions = {{"left", "back", "左对齐"}, {"centre", "move", "居中"}, {"top", "up", "顶对齐"}, {"spread", "sliders", "均分"}};
            for (String[] a : actions) {
                Element b = Dom.el(doc, "div", "ore-btn icon", null);
                b.setAttribute("title", a[2]);
                b.appendChild(icon(a[1], "o-ico"));
                Shell.activate(b, () -> align(a[0]));
                aligns.appendChild(b);
            }
            inspector.appendChild(aligns);
        }
        inspector.appendChild(Dom.el(doc, "div", "o-caps", "层级"));
        Element order = Dom.el(doc, "div", "aligns", null);
        String[][] moves = {{"front", "up", "置顶"}, {"back", "down", "置底"}};
        for (String[] m : moves) {
            Element b = Dom.el(doc, "div", "ore-btn icon", null);
            b.setAttribute("title", m[2]);
            b.appendChild(icon(m[1], "o-ico"));
            Shell.activate(b, () -> restack(m[0].equals("front")));
            order.appendChild(b);
        }
        inspector.appendChild(order);
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
                status("「" + v + "」不是数字");
            }
        });
    }

    /** One inspector edit: a checkpoint, the change, a redraw. */
    void edit(Runnable change) {
        checkpoint();
        change.run();
        render();
    }

    private static String variantName(String v) {
        return switch (v) {
            case "hero" -> "主";
            case "primary" -> "确认";
            case "secondary" -> "次";
            case "destructive" -> "危";
            default -> "普";
        };
    }

    // ---- Export ----

    /** The screen as a page on the Ore theme. */
    String html() {
        StringBuilder b = new StringBuilder();
        b.append("<!doctype html>\n<html>\n<head>\n<link rel=\"stylesheet\" href=\"ore.css\">\n<style>\n")
                .append(".screen { position: relative; width: ").append(BOARD_W).append("px; height: ").append(BOARD_H).append("px; }\n")
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
        status("已导出 " + parts.size() + " 个组件");
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
