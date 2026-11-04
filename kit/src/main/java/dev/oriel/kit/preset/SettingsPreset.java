package dev.oriel.kit.preset;

import dev.oriel.kit.Lang;

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

    static final Map<String, List<Setting>> TAB_DEFINITIONS = new LinkedHashMap<>();

    static {
        TAB_DEFINITIONS.put("oriel.ui.settings.video_2", List.of(
                Setting.slider("render", "oriel.ui.settings.render_distance", 12, 2, 32, 1, "oriel.ui.settings.chunks", "oriel.ui.settings.graphics"),
                Setting.slider("fps", "oriel.ui.settings.max_frame_rate", 120, 30, 260, 10, " FPS", "oriel.ui.settings.graphics"),
                Setting.select("graphics", "oriel.ui.settings.graphics_quality", "oriel.ui.settings.high_quality", List.of("oriel.ui.settings.smooth", "oriel.ui.settings.high_quality", "oriel.ui.settings.excellent"), "oriel.ui.settings.graphics"),
                Setting.toggle("vsync", "oriel.ui.settings.vertical_sync", true, "oriel.ui.settings.graphics"),
                Setting.toggle("fullscreen", "oriel.ui.settings.fullscreen", false, "oriel.ui.settings.window_settings"),
                Setting.select("scale", "oriel.ui.settings.interface_scale", "oriel.ui.settings.auto", List.of("oriel.ui.settings.auto", "1", "2", "3", "4"), "oriel.ui.settings.window_settings"),
                Setting.slider("gamma", "oriel.ui.settings.brightness", 50, 0, 100, 5, "%", "oriel.ui.settings.window_settings")));
        TAB_DEFINITIONS.put("oriel.ui.settings.audio", List.of(
                Setting.slider("master", "oriel.ui.settings.master_volume", 100, 0, 100, 5, "%", "oriel.ui.settings.volume"),
                Setting.slider("music", "oriel.ui.settings.music", 60, 0, 100, 5, "%", "oriel.ui.settings.volume"),
                Setting.slider("blocks", "oriel.ui.settings.block", 80, 0, 100, 5, "%", "oriel.ui.settings.volume"),
                Setting.slider("ambient", "oriel.ui.settings.ambient", 70, 0, 100, 5, "%", "oriel.ui.settings.volume"),
                Setting.toggle("subtitles", "oriel.ui.settings.show_subtitles", false, "oriel.ui.settings.other"),
                Setting.select("device", "oriel.ui.settings.output_device", "oriel.ui.settings.system_default", List.of("oriel.ui.settings.system_default", "oriel.ui.settings.speakers", "oriel.ui.settings.headphones"), "oriel.ui.settings.other")));
        TAB_DEFINITIONS.put("oriel.ui.settings.controls", List.of(
                Setting.slider("sensitivity", "oriel.ui.settings.mouse_sensitivity", 100, 1, 200, 1, "%", "oriel.ui.settings.mouse"),
                Setting.toggle("invert", "oriel.ui.settings.invert_mouse", false, "oriel.ui.settings.mouse"),
                Setting.toggle("autojump", "oriel.ui.settings.auto_jump", false, "oriel.ui.settings.movement"),
                Setting.toggle("sneak", "oriel.ui.settings.toggle_sneak", true, "oriel.ui.settings.movement"),
                Setting.text("name", "oriel.ui.settings.player_name", "Steve", "oriel.ui.settings.identity"),
                Setting.text("prefix", "oriel.ui.settings.chat_prefix", "oriel.ui.settings.yooee", "oriel.ui.settings.identity")));
        TAB_DEFINITIONS.put("oriel.ui.settings.accessibility", List.of(
                Setting.select("narrator", "oriel.ui.settings.narration", "oriel.ui.settings.close", List.of("oriel.ui.settings.close", "oriel.ui.settings.all", "oriel.ui.settings.chat_only", "oriel.ui.settings.system_only"), "oriel.ui.settings.read_aloud"),
                Setting.toggle("contrast", "oriel.ui.settings.high_contrast", false, "oriel.ui.settings.display"),
                Setting.slider("textbg", "oriel.ui.settings.text_background_opacity", 50, 0, 100, 10, "%", "oriel.ui.settings.display"),
                Setting.toggle("motion", "oriel.ui.settings.reduce_motion", false, "oriel.ui.settings.display"),
                Setting.slider("tooltip", "oriel.ui.settings.tooltip_delay", 500, 0, 2000, 100, " ms", "oriel.ui.settings.display")));
        TAB_DEFINITIONS.put("oriel.ui.settings.appearance", List.of(
                Setting.select("theme", "oriel.ui.settings.interface_theme", "oriel.ui.settings.dark", List.of("oriel.ui.settings.dark", "oriel.ui.settings.light"), "oriel.ui.settings.theme"),
                Setting.select("style", "oriel.ui.settings.interface_style", "oriel.ui.settings.pixel", List.of("oriel.ui.settings.pixel", "oriel.ui.settings.smooth_2"), "oriel.ui.settings.theme"),
                Setting.select("icons", "oriel.ui.settings.icon_weight", "oriel.ui.settings.regular", List.of("oriel.ui.settings.outline", "oriel.ui.settings.regular", "oriel.ui.settings.bold"), "oriel.ui.settings.theme"),
                Setting.toggle("particles", "oriel.ui.settings.particles_lighting_effects", true, "oriel.ui.settings.effects")));
    }

    static final Map<String, String> ICON_DEFINITIONS = Map.of("oriel.ui.settings.video_2", "monitor", "oriel.ui.settings.audio", "volume", "oriel.ui.settings.controls", "mouse", "oriel.ui.settings.accessibility",
            "accessibility", "oriel.ui.settings.appearance", "palette");
    static final Map<String, String> HELP_DEFINITIONS = Map.ofEntries(Map.entry("render", "oriel.ui.settings.chunk_render_distance"),
            Map.entry("fps", "oriel.ui.settings.higher_is_smoother"), Map.entry("graphics", "oriel.ui.settings.affects_leaves_and_clouds"), Map.entry("gamma", "oriel.ui.settings.visibility_in_dark_areas"),
            Map.entry("master", "oriel.ui.settings.all_sounds"), Map.entry("sensitivity", "oriel.ui.settings.look_sensitivity"), Map.entry("name", "oriel.ui.settings.up_to_24_characters"),
            Map.entry("narrator", "oriel.ui.settings.read_interface_text_aloud"), Map.entry("theme", "oriel.ui.settings.takes_effect_immediately"), Map.entry("style", "oriel.ui.settings.pixel_bevels_or_rounded_corner_shadows"), Map.entry("icons", "oriel.ui.settings.wireframe_normal_or_bold"), Map.entry("particles", "oriel.ui.settings.particles_on_this_page"),
            Map.entry("textbg", "oriel.ui.settings.behind_chat_and_subtitles"));

    private final Map<String, List<Setting>> TABS = new LinkedHashMap<>();
    private final Map<String, String> ICONS = new LinkedHashMap<>(), HELP = new LinkedHashMap<>();

    final Map<String, Object> values = new LinkedHashMap<>();
    String tab;
    private Document doc;
    private Element tabs, rows, status, crumb, chunks, dark, changesBox;
    private final List<Element> chunkCells = new ArrayList<>();
    private final List<Element> tabElements = new ArrayList<>();
    private Element openSelect;

    @Override
    public void attach(Document document) {
        doc = document;
        tab = Lang.t(doc, "oriel.ui.settings.video_2");
        TAB_DEFINITIONS.forEach((name, settings) -> TABS.put(Lang.value(doc, name), settings.stream().map(s ->
                new Setting(s.key(), Lang.value(doc, s.label()), s.kind(), s.initial() instanceof String text ? Lang.value(doc, text) : s.initial(),
                        s.min(), s.max(), s.step(), Lang.value(doc, s.unit()), s.options().stream().map(v -> Lang.value(doc, v)).toList(),
                        Lang.value(doc, s.section()))).toList()));
        ICON_DEFINITIONS.forEach((name, icon) -> ICONS.put(Lang.value(doc, name), icon));
        HELP_DEFINITIONS.forEach((name, help) -> HELP.put(name, Lang.value(doc, help)));
        Shell.setup(doc);
        for (List<Setting> list : TABS.values()) for (Setting s : list) values.put(s.key(), s.initial());
        values.put("theme", Shell.theme().equals("light") ? Lang.t(doc, "oriel.ui.settings.light") : Lang.t(doc, "oriel.ui.settings.dark"));
        values.put("style", Shell.style().equals("smooth") ? Lang.t(doc, "oriel.ui.settings.smooth_2") : Lang.t(doc, "oriel.ui.settings.pixel"));
        values.put("icons", Shell.icons().equals("thin") ? Lang.t(doc, "oriel.ui.settings.outline") : Shell.icons().equals("bold") ? Lang.t(doc, "oriel.ui.settings.bold") : Lang.t(doc, "oriel.ui.settings.regular"));
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
        Dom.onClick(Dom.byId(doc, "back"), e -> Shell.back(doc));
        // A press outside an open dropdown closes it.
        Dom.onMouse(doc, "mousedown", e -> {
            if (openSelect != null && Dom.closest(e, ".ore-select") != openSelect) closeSelect();
        });
        show(tab, false);
    }

    private record State(Map<String, Object> values, String tab) {}

    @Override
    public Object saveState() {
        Map<String, Setting> definitions = new LinkedHashMap<>();
        TAB_DEFINITIONS.values().forEach(list -> list.forEach(s -> definitions.put(s.key(), s)));
        Map<String, Object> changed = new LinkedHashMap<>();
        for (List<Setting> list : TABS.values()) {
            for (Setting setting : list) {
                Object value = values.get(setting.key());
                if (java.util.Objects.equals(value, setting.initial())) continue;
                if (setting.kind() == Kind.SELECT) {
                    int index = setting.options().indexOf(value);
                    if (index >= 0) value = definitions.get(setting.key()).options().get(index);
                }
                changed.put(setting.key(), value);
            }
        }
        int index = new ArrayList<>(TABS.keySet()).indexOf(tab);
        String key = new ArrayList<>(TAB_DEFINITIONS.keySet()).get(Math.max(0, index));
        return new State(changed, key);
    }

    @Override
    public void restoreState(Object state) {
        if (!(state instanceof State s)) return;
        values.putAll(s.values());
        for (List<Setting> list : TAB_DEFINITIONS.values()) {
            for (Setting setting : list) {
                if (setting.kind() == Kind.SELECT && s.values().get(setting.key()) instanceof String value) {
                    values.put(setting.key(), Lang.value(doc, value));
                }
            }
        }
        String name = Lang.value(doc, s.tab());
        show(TABS.containsKey(name) ? name : tab, false);
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
        Element state = Dom.el(doc, "span", "help", (Boolean) values.get(s.key()) ? Lang.t(doc, "oriel.ui.settings.on") : Lang.t(doc, "oriel.ui.settings.off"));
        Runnable flip = () -> {
            boolean on = !(Boolean) values.get(s.key());
            set(s, on);
            t.toggleClass("on", on);
            Dom.text(state, on ? Lang.t(doc, "oriel.ui.settings.on") : Lang.t(doc, "oriel.ui.settings.off"));
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
        if (s.key().equals("theme")) Shell.theme(doc, value.equals(Lang.t(doc, "oriel.ui.settings.light")) ? "light" : "dark");
        if (s.key().equals("style")) Shell.style(doc, value.equals(Lang.t(doc, "oriel.ui.settings.smooth_2")) ? "smooth" : "ore");
        if (s.key().equals("icons")) Shell.icons(doc, value.equals(Lang.t(doc, "oriel.ui.settings.outline")) ? "thin" : value.equals(Lang.t(doc, "oriel.ui.settings.bold")) ? "bold" : "regular");
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
        Dom.text(status, n == 0 ? Lang.t(doc, "oriel.ui.settings.no_changes") : Lang.t(doc, "oriel.ui.settings.modified") + n + Lang.t(doc, "oriel.ui.settings.items"));
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
        if (shown == 0) changesBox.appendChild(Dom.el(doc, "div", "c", Lang.t(doc, "oriel.ui.settings.no_changes_made")));
    }

    private String show(Object v) {
        if (v instanceof Boolean b) return b ? Lang.t(doc, "oriel.ui.settings.on") : Lang.t(doc, "oriel.ui.settings.off");
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
        if (changed.contains("icons")) Shell.icons(doc, "regular");
        show(tab, false);
        for (Element row : rows.querySelectorAll(".ore-row")) if (changed.contains(row.getAttribute("data-key"))) row.addClass("flash");
    }

    private static String format(float v) {
        return v == Math.round(v) ? Integer.toString(Math.round(v)) : Float.toString(v);
    }
}
