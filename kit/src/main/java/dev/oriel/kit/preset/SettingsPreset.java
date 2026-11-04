package dev.oriel.kit.preset;

import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;
import dev.oriel.kit.Slider;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.host.PageControllers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings preset: four tabs of toggles, sliders, dropdowns and text fields over a fixture schema. Everything
 * works from the keyboard: Tab moves focus (with the Ore focus ring), the arrow keys switch tabs and move sliders,
 * Space and Enter flip toggles and open dropdowns, Escape closes an open dropdown. Changed settings get a dot and
 * a count; "恢复默认" resets the shown tab. The side pane previews the render distance and brightness and lists the
 * changes; the 外观 tab's theme applies at once (and to the pages opened after).
 */
public final class SettingsPreset implements PageControllers.Controller {
    enum Kind { TOGGLE, SLIDER, SELECT, TEXT }

    /** One setting of the schema: for sliders min, max and step; for selects the options. */
    record Setting(String key, String label, Kind kind, Object initial, float min, float max, float step, String unit,
                   List<String> options, String section) {
        static Setting toggle(String key, String label, boolean on, String section) {
            return new Setting(key, label, Kind.TOGGLE, on, 0, 1, 1, "", List.of(), section);
        }

        static Setting slider(String key, String label, float value, float min, float max, float step, String unit, String section) {
            return new Setting(key, label, Kind.SLIDER, value, min, max, step, unit, List.of(), section);
        }

        static Setting select(String key, String label, String value, List<String> options, String section) {
            return new Setting(key, label, Kind.SELECT, value, 0, 0, 0, "", options, section);
        }

        static Setting text(String key, String label, String value, String section) {
            return new Setting(key, label, Kind.TEXT, value, 0, 0, 0, "", List.of(), section);
        }
    }

    static final Map<String, List<Setting>> TABS = new LinkedHashMap<>();

    static {
        TABS.put("视频", List.of(
                Setting.slider("render", "渲染距离", 12, 2, 32, 1, " 区块", "画面"),
                Setting.slider("fps", "最大帧率", 120, 30, 260, 10, " FPS", "画面"),
                Setting.select("graphics", "图像品质", "高品质", List.of("流畅", "高品质", "极佳"), "画面"),
                Setting.toggle("vsync", "垂直同步", true, "画面"),
                Setting.toggle("fullscreen", "全屏", false, "窗口"),
                Setting.select("scale", "界面尺寸", "自动", List.of("自动", "1", "2", "3", "4"), "窗口"),
                Setting.slider("gamma", "亮度", 50, 0, 100, 5, "%", "窗口")));
        TABS.put("声音", List.of(
                Setting.slider("master", "主音量", 100, 0, 100, 5, "%", "音量"),
                Setting.slider("music", "音乐", 60, 0, 100, 5, "%", "音量"),
                Setting.slider("blocks", "方块", 80, 0, 100, 5, "%", "音量"),
                Setting.slider("ambient", "环境", 70, 0, 100, 5, "%", "音量"),
                Setting.toggle("subtitles", "显示字幕", false, "其他"),
                Setting.select("device", "输出设备", "系统默认", List.of("系统默认", "扬声器", "耳机"), "其他")));
        TABS.put("控制", List.of(
                Setting.slider("sensitivity", "鼠标灵敏度", 100, 1, 200, 1, "%", "鼠标"),
                Setting.toggle("invert", "反转鼠标", false, "鼠标"),
                Setting.toggle("autojump", "自动跳跃", false, "移动"),
                Setting.toggle("sneak", "潜行切换", true, "移动"),
                Setting.text("name", "玩家名称", "Steve", "身份"),
                Setting.text("prefix", "聊天前缀", "[优易]", "身份")));
        TABS.put("辅助功能", List.of(
                Setting.select("narrator", "旁白", "关闭", List.of("关闭", "全部", "仅聊天", "仅系统"), "朗读"),
                Setting.toggle("contrast", "高对比度", false, "显示"),
                Setting.slider("textbg", "文本背景不透明度", 50, 0, 100, 10, "%", "显示"),
                Setting.toggle("motion", "减少动效", false, "显示"),
                Setting.slider("tooltip", "提示延迟", 500, 0, 2000, 100, " ms", "显示")));
        TABS.put("外观", List.of(
                Setting.select("theme", "界面主题", "暗色", List.of("暗色", "亮色"), "主题"),
                Setting.select("style", "界面风格", "像素", List.of("像素", "平滑"), "主题"),
                Setting.toggle("particles", "粒子与光效", true, "效果")));
    }

    static final Map<String, String> ICONS = Map.of("视频", "monitor", "声音", "volume", "控制", "mouse", "辅助功能",
            "accessibility", "外观", "palette");
    static final Map<String, String> HELP = Map.ofEntries(Map.entry("render", "能看见多远的区块"),
            Map.entry("fps", "越高越流畅"), Map.entry("graphics", "影响树叶与云"), Map.entry("gamma", "暗处的可见度"),
            Map.entry("master", "所有声音"), Map.entry("sensitivity", "视角转动速度"), Map.entry("name", "最多 24 个字"),
            Map.entry("narrator", "朗读界面文字"), Map.entry("theme", "立即生效"), Map.entry("style", "像素斜角或圆角阴影"), Map.entry("particles", "本页的粒子"),
            Map.entry("textbg", "聊天与字幕背后"));

    final Map<String, Object> values = new LinkedHashMap<>();
    String tab = "视频";
    private Document doc;
    private Element tabs, rows, status, crumb, chunks, dark, changesBox;
    private final List<Element> chunkCells = new ArrayList<>();
    private final List<Element> tabElements = new ArrayList<>();
    private Element openSelect;

    @Override
    public void attach(Document document) {
        doc = document;
        Shell.setup(doc);
        for (List<Setting> list : TABS.values()) for (Setting s : list) values.put(s.key(), s.initial());
        values.put("theme", Shell.theme().equals("light") ? "亮色" : "暗色");
        values.put("style", Shell.style().equals("smooth") ? "平滑" : "像素");
        crumb = Dom.byId(doc, "crumb");
        chunks = Dom.byId(doc, "chunks");
        dark = Dom.byId(doc, "dark");
        changesBox = Dom.byId(doc, "changes");
        for (int i = 0; i < 121; i++) {
            Element cell = Dom.el(doc, "i", null, null);
            chunks.appendChild(cell);
            chunkCells.add(cell);
        }
        tabs = Dom.byId(doc, "tabs");
        rows = Dom.byId(doc, "rows");
        status = Dom.byId(doc, "status");
        for (String name : TABS.keySet()) {
            Element t = Dom.el(doc, "div", "ore-tab", null);
            t.setAttribute("tabindex", "0");
            t.setAttribute("data-tab", name);
            Element icon = Dom.el(doc, "img", "o-ico", null);
            icon.setAttribute("src", "../textures/icons/" + ICONS.getOrDefault(name, "gear") + ".png");
            t.appendChild(icon);
            t.appendChild(Dom.el(doc, "span", null, name));
            t.appendChild(Dom.el(doc, "span", "o-badge", "0"));
            Dom.onClick(t, e -> show(name, false));
            Dom.onKey(t, e -> tabKey(e, name));
            tabs.appendChild(t);
            tabElements.add(t);
        }
        Dom.onClick(Dom.byId(doc, "reset"), e -> resetTab());
        Dom.onKey(Dom.byId(doc, "reset"), e -> activate(e, this::resetTab));
        Dom.onClick(Dom.byId(doc, "done"), e -> doc.host().close());
        Dom.onClick(Dom.byId(doc, "cancel"), e -> doc.host().close());
        Dom.onClick(Dom.byId(doc, "close"), e -> doc.host().close());
        Dom.onClick(Dom.byId(doc, "back"), e -> doc.navigate(OrielKit.GALLERY));
        // A press outside an open dropdown closes it.
        Dom.onMouse(doc, "mousedown", e -> {
            if (openSelect != null && Dom.closest(e, ".ore-select") != openSelect) closeSelect();
        });
        show(tab, false);
    }

    private record State(Map<String, Object> values, String tab) {}

    @Override
    public Object saveState() {
        return new State(new LinkedHashMap<>(values), tab);
    }

    @Override
    public void restoreState(Object state) {
        if (!(state instanceof State s)) return;
        values.putAll(s.values());
        show(TABS.containsKey(s.tab()) ? s.tab() : tab, false);
    }

    private static void activate(KeyboardEvent e, Runnable action) {
        if (e.key.equals("Enter") || e.key.equals(" ")) {
            e.preventDefault();
            action.run();
        }
    }

    private void tabKey(KeyboardEvent e, String name) {
        List<String> names = new ArrayList<>(TABS.keySet());
        int i = names.indexOf(name);
        if (e.key.equals("ArrowRight") || e.key.equals("ArrowLeft")) {
            e.preventDefault();
            int next = Math.floorMod(i + (e.key.equals("ArrowRight") ? 1 : -1), names.size());
            show(names.get(next), true);
        } else {
            activate(e, () -> show(name, false));
        }
    }

    void show(String name, boolean focusTab) {
        tab = name;
        closeSelect();
        List<String> names = new ArrayList<>(TABS.keySet());
        for (int i = 0; i < tabElements.size(); i++) tabElements.get(i).toggleClass("selected", names.get(i).equals(name));
        if (focusTab) tabElements.get(names.indexOf(name)).focus();
        Dom.text(crumb, "/ " + name);
        rows.removeAllChildren();
        String section = null;
        for (Setting s : TABS.get(name)) {
            if (!s.section().equals(section)) {
                section = s.section();
                rows.appendChild(Dom.el(doc, "div", "section o-caps", section));
            }
            rows.appendChild(row(s));
        }
        rows.removeClass("swap");
        doc.scheduler().requestAnimationFrame(t -> rows.addClass("swap"));
        updateStatus();
    }

    private Element row(Setting s) {
        Element row = Dom.el(doc, "div", "ore-row", null);
        row.setAttribute("data-key", s.key());
        Element label = Dom.el(doc, "div", "label", null);
        Element title = Dom.el(doc, "div", "t", s.label());
        title.appendChild(Dom.el(doc, "span", "dot", null));
        label.appendChild(title);
        if (HELP.containsKey(s.key())) label.appendChild(Dom.el(doc, "div", "h", HELP.get(s.key())));
        row.appendChild(label);
        Element control = Dom.el(doc, "div", "control", null);
        row.appendChild(control);
        switch (s.kind()) {
            case TOGGLE -> toggle(s, control);
            case SLIDER -> slider(s, control);
            case SELECT -> select(s, control);
            case TEXT -> text(s, control);
        }
        row.toggleClass("changed", !values.get(s.key()).equals(s.initial()));
        return row;
    }

    private void toggle(Setting s, Element control) {
        Element t = Dom.el(doc, "div", "ore-toggle", null);
        t.setAttribute("tabindex", "0");
        t.toggleClass("on", (Boolean) values.get(s.key()));
        Element state = Dom.el(doc, "span", "help", (Boolean) values.get(s.key()) ? "开" : "关");
        Runnable flip = () -> {
            boolean on = !(Boolean) values.get(s.key());
            set(s, on);
            t.toggleClass("on", on);
            Dom.text(state, on ? "开" : "关");
        };
        Dom.onClick(t, e -> flip.run());
        Dom.onKey(t, e -> activate(e, flip));
        control.appendChild(t);
        control.appendChild(state);
    }

    private void slider(Setting s, Element control) {
        Element el = Dom.html(doc, "<div class=\"ore-slider\" tabindex=\"0\"><div class=\"track\"></div><div class=\"fill\"></div><div class=\"thumb\"></div></div>");
        control.appendChild(el);
        Element val = Dom.el(doc, "span", "val", null);
        control.appendChild(val);
        int steps = Math.round((s.max() - s.min()) / s.step());
        Slider slider = new Slider(doc, el, steps);
        Runnable label = () -> Dom.text(val, format((Float) values.get(s.key())) + s.unit());
        slider.set(((Float) values.get(s.key()) - s.min()) / (s.max() - s.min()));
        slider.onInput(v -> {
            set(s, s.min() + Math.round(v * steps) * s.step());
            label.run();
        });
        label.run();
    }

    private void select(Setting s, Element control) {
        Element sel = Dom.el(doc, "div", "ore-select", null);
        sel.setAttribute("tabindex", "0");
        Element value = Dom.el(doc, "div", "value", (String) values.get(s.key()));
        Element menu = Dom.el(doc, "div", "menu", null);
        sel.appendChild(value);
        sel.appendChild(menu);
        for (String option : s.options()) {
            Element o = Dom.el(doc, "div", "option", option);
            o.toggleClass("chosen", option.equals(values.get(s.key())));
            Dom.onMouse(o, "mousedown", e -> {
                e.stopPropagation();
                choose(s, sel, option);
            });
            menu.appendChild(o);
        }
        Dom.onMouse(value, "mousedown", e -> {
            if (sel == openSelect) closeSelect();
            else openSelect(sel, s);
        });
        Dom.onKey(sel, e -> selectKey(e, s, sel));
        control.appendChild(sel);
    }

    private void openSelect(Element sel, Setting s) {
        closeSelect();
        openSelect = sel;
        sel.addClass("open");
        sel.focus();
        highlight(sel, s.options().indexOf((String) values.get(s.key())));
    }

    private void closeSelect() {
        if (openSelect != null) openSelect.removeClass("open");
        openSelect = null;
    }

    private void highlight(Element sel, int index) {
        List<Element> options = Dom.query(sel, ".menu").children();
        for (int i = 0; i < options.size(); i++) options.get(i).toggleClass("active", i == index);
    }

    private int highlighted(Element sel) {
        List<Element> options = Dom.query(sel, ".menu").children();
        for (int i = 0; i < options.size(); i++) if (options.get(i).hasClass("active")) return i;
        return 0;
    }

    private void selectKey(KeyboardEvent e, Setting s, Element sel) {
        boolean open = sel == openSelect;
        switch (e.key) {
            case "Enter", " " -> {
                e.preventDefault();
                if (open) choose(s, sel, s.options().get(highlighted(sel)));
                else openSelect(sel, s);
            }
            case "ArrowDown", "ArrowUp" -> {
                e.preventDefault();
                int delta = e.key.equals("ArrowDown") ? 1 : -1;
                if (!open) {
                    int i = Math.floorMod(s.options().indexOf((String) values.get(s.key())) + delta, s.options().size());
                    choose(s, sel, s.options().get(i));
                } else {
                    highlight(sel, Math.floorMod(highlighted(sel) + delta, s.options().size()));
                }
            }
            case "Escape" -> {
                if (open) {
                    e.preventDefault();
                    closeSelect();
                }
            }
            default -> { }
        }
    }

    private void choose(Setting s, Element sel, String option) {
        set(s, option);
        Dom.text(Dom.query(sel, ".value"), option);
        for (Element o : Dom.query(sel, ".menu").children()) o.toggleClass("chosen", o.textContent().equals(option));
        closeSelect();
    }

    private void text(Setting s, Element control) {
        Element input = Dom.el(doc, "input", "ore-field", null);
        input.setValue((String) values.get(s.key()));
        input.setAttribute("maxlength", "24");
        input.addEventListener("input", e -> set(s, input.value()));
        control.appendChild(input);
    }

    void set(Setting s, Object value) {
        values.put(s.key(), value);
        if (s.key().equals("theme")) Shell.theme(doc, value.equals("亮色") ? "light" : "dark");
        if (s.key().equals("style")) Shell.style(doc, value.equals("平滑") ? "smooth" : "ore");
        if (s.key().equals("particles")) {
            for (Element c : doc.querySelectorAll("canvas.fx")) c.toggleClass("off", !(Boolean) value);
        }
        for (Element row : rows.querySelectorAll(".ore-row")) {
            if (row.getAttribute("data-key").equals(s.key())) row.toggleClass("changed", !value.equals(s.initial()));
        }
        updateStatus();
    }

    int changes() {
        int n = 0;
        for (List<Setting> list : TABS.values()) for (Setting s : list) if (!values.get(s.key()).equals(s.initial())) n++;
        return n;
    }

    private void updateStatus() {
        int n = changes();
        Dom.text(status, n == 0 ? "没有修改" : "已修改 " + n + " 项");
        List<String> names = new ArrayList<>(TABS.keySet());
        for (int i = 0; i < tabElements.size(); i++) {
            int dirty = 0;
            for (Setting s : TABS.get(names.get(i))) if (!values.get(s.key()).equals(s.initial())) dirty++;
            tabElements.get(i).toggleClass("dirty", dirty > 0);
            Dom.text(Dom.query(tabElements.get(i), ".o-badge"), Integer.toString(dirty));
        }
        preview();
    }

    /** The side pane: chunks within the render distance, the brightness, and what has changed. */
    private void preview() {
        if (chunks == null) return;
        float render = (Float) values.get("render");
        float radius = 5 * render / 32f + 0.5f;
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < 11; x++) {
                Element cell = chunkCells.get(y * 11 + x);
                double d = Math.hypot(x - 5, y - 5);
                cell.setAttribute("class", x == 5 && y == 5 ? "me" : d <= radius - 1 ? "on" : d <= radius ? "edge" : "");
            }
        }
        float gamma = (Float) values.get("gamma");
        new Dom.Style(dark).set("opacity", Float.toString(Math.round((100 - gamma) * 0.7f) / 100f)).apply();
        changesBox.removeAllChildren();
        int shown = 0;
        for (List<Setting> list : TABS.values()) {
            for (Setting s : list) {
                Object v = values.get(s.key());
                if (v.equals(s.initial()) || shown >= 6) continue;
                shown++;
                Element c = Dom.el(doc, "div", "c", s.label() + " ");
                c.appendChild(Dom.el(doc, "b", null, show(v) + (s.kind() == Kind.SLIDER ? s.unit().trim() : "")));
                changesBox.appendChild(c);
            }
        }
        if (shown == 0) changesBox.appendChild(Dom.el(doc, "div", "c", "还没有修改"));
    }

    private static String show(Object v) {
        if (v instanceof Boolean b) return b ? "开" : "关";
        if (v instanceof Float f) return format(f);
        return String.valueOf(v);
    }

    void resetTab() {
        List<String> changed = new ArrayList<>();
        for (Setting s : TABS.get(tab)) {
            if (!values.get(s.key()).equals(s.initial())) changed.add(s.key());
            values.put(s.key(), s.initial());
        }
        if (changed.contains("theme")) Shell.theme(doc, "dark");
        if (changed.contains("style")) Shell.style(doc, "ore");
        show(tab, false);
        for (Element row : rows.querySelectorAll(".ore-row")) if (changed.contains(row.getAttribute("data-key"))) row.addClass("flash");
    }

    private static String format(float v) {
        return v == Math.round(v) ? Integer.toString(Math.round(v)) : Float.toString(v);
    }
}
