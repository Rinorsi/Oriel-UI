package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.kit.fx.Particles;

import java.util.List;

/**
 * What every kit page shares, wired from markup so a page needs no code for it:
 * <ul>
 *   <li>the theme: {@code data-theme} on {@code <html>} ({@link #theme()}, kept across pages);</li>
 *   <li>{@code data-go="forge"}: opens that kit page (Enter / Space too) with the forward transition; with
 *   {@code data-zoom} a lit outline also grows from the element to the window ({@link Motion#zoomRing});</li>
 *   <li>{@code data-count="2235"}: the number counts up when the page opens;</li>
 *   <li>{@code data-action="close" | "back" | "theme"}: closes the screen, returns to the gallery, flips the theme;</li>
 *   <li>{@code canvas[data-fx]}: particles ({@link Particles});</li>
 *   <li>{@code .o-apptabs}: the top-level pages' tabs in the title bar. Its {@code data-go} items switch pages as tabs:
 *   the title bar stays, the indicator glides from the old tab to the new one and the content slides in from the side
 *   of the new tab ({@link #go});</li>
 *   <li>the window mode: {@code data-window="max"} (the window fills the screen) or {@code "mini"} (a centred window
 *   over the game), kept across pages; every title bar gets the switch before its close button;</li>
 *   <li>the controls' default behaviour ({@link Widgets}), and loading ({@link Lazy}: {@code .o-lazy}, {@code .o-preload}).</li>
 * </ul>
 * <p>Page transitions: {@link #go} plays a leaving animation on {@code .o-body}, opens the page and gives the new
 * page's {@code .o-body} the matching entrance ({@code m-page-forward}, {@code -next}, {@code -prev}, {@code -back}; see
 * {@code ore.css}). Only the content moves; the backdrop and the window frame are the same on every page, so nothing
 * flashes.
 * Controllers call {@link #setup} first and keep the returned particles for bursts.
 */
public final class Shell {
    private static volatile String theme = "dark", style = "ore", window = "mini", icons = "regular", font = "mc";
    /** The entrance for the next page's content, and the app tab it was opened from (-1: none). */
    private static volatile String enter;
    private static volatile int fromTab = -1;

    private Shell() {}

    /** The current theme, {@code dark} or {@code light}. */
    public static String theme() {
        return theme;
    }

    /** The current style, {@code ore} (pixel bevels) or {@code smooth} (rounded, soft shadows). */
    public static String style() {
        return style;
    }

    /** Sets the style for the pages opened from now on. */
    public static void setStyle(String name) {
        style = name.equals("smooth") ? "smooth" : "ore";
    }

    /** Sets the style ({@code data-style} on {@code <html>}) for this page and the pages opened after it. */
    public static void style(Document doc, String name) {
        style = name.equals("smooth") ? "smooth" : "ore";
        Element root = doc.documentElement();
        if (root != null) root.setAttribute("data-style", style);
        for (Element e : doc.querySelectorAll("[data-style-pick]")) e.toggleClass("on", e.getAttribute("data-style-pick").equals(style));
    }

    /** Sets the theme for the pages opened from now on. */
    public static void setTheme(String name) {
        theme = name.equals("light") ? "light" : "dark";
    }

    /** Sets the theme for this page and the pages opened after it. */
    public static void theme(Document doc, String name) {
        theme = name.equals("light") ? "light" : "dark";
        Element root = doc.documentElement();
        if (root != null) root.setAttribute("data-theme", theme);
        // The one theme button on every page: a sun while dark (press for light), a moon while light.
        for (Element e : doc.querySelectorAll("[data-action=theme]")) {
            e.toggleClass("on", theme.equals("light"));
            e.setAttribute("title", theme.equals("light") ? "切换到暗色" : "切换到亮色");
            Element img = e.querySelector("img");
            if (img != null && img.getAttribute("src") != null) {
                String src = img.getAttribute("src").replaceAll("(sun|moon)[.]png$", theme.equals("light") ? "moon.png" : "sun.png");
                if (!src.equals(img.getAttribute("src"))) img.setAttribute("src", src);
            }
        }
    }

    /** The page font's id ({@link Fonts}); {@code mc} is the game's own. */
    public static String font() {
        return font;
    }

    /**
     * Sets the font for this page and the pages opened after it ({@code data-font} on {@code <html>}, which
     * {@code ore.css} turns into the font family); {@code [data-font-pick]} items show which is on.
     */
    public static void font(Document doc, String id) {
        font = Fonts.byId(id).id();
        Element root = doc.documentElement();
        if (root != null) {
            if (font.equals("mc")) root.removeAttribute("data-font");
            else root.setAttribute("data-font", font);
        }
        for (Element e : doc.querySelectorAll("[data-font-pick]")) e.toggleClass("on", e.getAttribute("data-font-pick").equals(font));
        for (Element e : doc.querySelectorAll(".ore-select[data-font-select] .value")) Dom.text(e, Fonts.byId(font).name());
    }

    /** The icon weight: {@code thin} (wireframe, 1px), {@code regular} (2px) or {@code bold} (3px). */
    public static String icons() {
        return icons;
    }

    /**
     * Sets the icon weight for this page and the pages opened after it: every {@code textures/icons/} image (and those
     * added later) uses {@code icons/thin/}, {@code icons/} or {@code icons/bold/}. {@code data-icons} on {@code <html>} says which.
     */
    public static void icons(Document doc, String weight) {
        icons = weight.equals("thin") || weight.equals("bold") ? weight : "regular";
        Element root = doc.documentElement();
        if (root != null) root.setAttribute("data-icons", icons);
        for (Element e : doc.querySelectorAll("[data-icons-pick]")) e.toggleClass("on", e.getAttribute("data-icons-pick").equals(icons));
        swapIcons(doc);
    }

    private static void swapIcons(Document doc) {
        for (Element img : doc.querySelectorAll("img")) {
            String src = img.getAttribute("src");
            if (src == null || !src.contains("textures/icons/")) continue;
            String regular = src.replace("textures/icons/thin/", "textures/icons/").replace("textures/icons/bold/", "textures/icons/");
            String want = icons.equals("regular") ? regular : regular.replace("textures/icons/", "textures/icons/" + icons + "/");
            if (!want.equals(src)) img.setAttribute("src", want);
        }
    }

    /** Keeps images added after the page opened at the chosen weight (checked when the DOM has changed). */
    private static void followIcons(Document doc) {
        int[] seen = {doc.domVersion()};
        doc.scheduler().requestAnimationFrame(new java.util.function.DoubleConsumer() {
            @Override
            public void accept(double t) {
                if (doc.domVersion() != seen[0]) {
                    if (!icons.equals("regular")) swapIcons(doc);
                    seen[0] = doc.domVersion();
                }
                doc.scheduler().requestAnimationFrame(this);
            }
        });
    }

    /** The window mode, {@code max} or {@code mini}. */
    public static String window() {
        return window;
    }

    /** Sets the window mode ({@code data-window} on {@code <html>}) for this page and the pages opened after it. */
    public static void window(Document doc, String mode) {
        window = mode.equals("mini") ? "mini" : "max";
        Element root = doc.documentElement();
        if (root != null) root.setAttribute("data-window", window);
    }

    /**
     * Opens {@code url} with a transition: {@code .o-body} plays {@code m-page-leave-<kind>}, then the page opens
     * and its {@code .o-body} plays {@code m-page-<kind>}. Kinds: {@code next} (to a tab on the right: the content
     * moves left), {@code prev} (the other way), {@code back} (returning to the launcher).
     */
    public static void go(Document doc, String url, String kind) {
        Element body = doc.querySelector(".o-body");
        enter = "m-page-" + kind;
        if (body == null) {
            doc.navigate(url);
            return;
        }
        body.addClass("m-page-leave-" + kind);
        doc.scheduler().setTimeout(() -> doc.navigate(url), Motion.FAST);
    }

    /** Back to the launcher, with the back transition. */
    public static void back(Document doc) {
        fromTab = -1;
        go(doc, OrielKit.GALLERY, "back");
    }

    /** Applies the theme, wires the shared attributes and starts the page's particles. */
    public static Particles setup(Document doc) {
        theme(doc, theme);
        style(doc, style);
        window(doc, window);
        icons(doc, icons);
        font(doc, font);
        followIcons(doc);
        for (Element e : doc.querySelectorAll("[data-icons-pick]")) activate(e, () -> icons(doc, e.getAttribute("data-icons-pick")));
        windowSwitch(doc);
        enter(doc);
        appTabs(doc);
        for (Element e : doc.querySelectorAll("[data-style-pick]")) activate(e, () -> style(doc, e.getAttribute("data-style-pick")));
        for (Element e : doc.querySelectorAll("[data-font-pick]")) activate(e, () -> font(doc, e.getAttribute("data-font-pick")));
        for (Element e : doc.querySelectorAll("[data-go]")) {
            if (e.closest(".o-apptabs") != null) continue;
            String page = e.getAttribute("data-go");
            boolean zoom = e.hasAttribute("data-zoom");
            activate(e, () -> {
                fromTab = -1;
                if (zoom) Motion.zoomRing(e);
                go(doc, OrielKit.page(page), "forward");
            });
        }
        for (Element e : doc.querySelectorAll("[data-action]")) {
            switch (e.getAttribute("data-action")) {
                case "close" -> activate(e, () -> doc.host().close());
                case "back" -> activate(e, () -> back(doc));
                case "theme" -> activate(e, () -> theme(doc, theme.equals("light") ? "dark" : "light"));
                default -> { }
            }
        }
        Widgets.attach(doc);
        Notice.attach(doc);
        Lazy.attach(doc);
        doc.addDataListener((key, json) -> {
            if (DEV_KEY.equals(key)) devBar(doc, json);
        });
        Motion.attach(doc);
        return Particles.attach(doc);
    }

    /** The data field the dev hot reload sets on every page (the game's DevReload). */
    static final String DEV_KEY = "oriel-dev";

    /**
     * The dev reload bar over the bottom of the window: what changed, and a button that asks the game to compile and
     * swap the classes in ({@code send("oriel-dev", "reload")}); none in release builds, where nothing sets the field.
     */
    private static void devBar(Document doc, String json) {
        Element bar = doc.getElementById("o-devbar");
        if (json == null || json.equals("null")) {
            if (bar != null) bar.remove();
            return;
        }
        com.google.gson.JsonObject o;
        try {
            o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        if (bar == null) {
            Element host = doc.querySelector(".o-window");
            if (host == null) host = doc.querySelector(".o-app");
            if (host == null) return;
            bar = Dom.html(doc, "<div class=\"o-devbar\" id=\"o-devbar\"><img class=\"o-ico\" src=\"oriel:textures/icons/refresh.png\">"
                    + "<span class=\"t\"></span><div class=\"o-grow\"></div><div class=\"ore-btn primary\" tabindex=\"0\"></div>"
                    + "<div class=\"x\" title=\"关闭\">×</div></div>");
            host.appendChild(bar);
            Element b = bar;
            activate(Dom.query(bar, ".ore-btn"), () -> doc.host().send(DEV_KEY, "\"reload\""));
            Dom.onClick(Dom.query(bar, ".x"), e -> b.remove());
        }
        String state = o.has("state") ? o.get("state").getAsString() : "changed";
        bar.setAttribute("class", "o-devbar " + state);
        Dom.text(Dom.query(bar, ".t"), o.has("text") ? o.get("text").getAsString() : "");
        Element button = Dom.query(bar, ".ore-btn");
        boolean action = o.has("action");
        button.toggleClass("o-hidden", !action);
        if (action) Dom.text(button, o.get("action").getAsString());
    }

    /** The entrance chosen by the page that opened this one (or the default, {@code o-content-in}). */
    private static void enter(Document doc) {
        String cls = enter;
        enter = null;
        Element body = doc.querySelector(".o-body");
        if (cls != null && body != null) body.addClass(cls);
    }

    /** The title bar tabs: switch with a direction, and the indicator starts under the tab we came from. */
    private static void appTabs(Document doc) {
        Element bar = doc.querySelector(".o-apptabs");
        int from = fromTab;
        fromTab = -1;
        if (bar == null) return;
        List<Element> tabs = bar.children().stream().filter(c -> !c.hasClass("m-indicator")).toList();
        int current = 0;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).hasClass("on")) current = i;
        int here = current;
        for (int i = 0; i < tabs.size(); i++) {
            Element tab = tabs.get(i);
            String page = tab.getAttribute("data-go");
            int index = i;
            if (page == null || index == here) continue;
            activate(tab, () -> {
                fromTab = here;
                go(doc, OrielKit.page(page), index > here ? "next" : "prev");
            });
        }
        Element indicator = bar.children().stream().filter(c -> c.hasClass("m-indicator")).findFirst().orElse(null);
        if (indicator == null) return;
        Element target = tabs.get(here), start = from >= 0 && from < tabs.size() ? tabs.get(from) : target;
        // First frame: under the old tab, without transition; the next frame glides it to this page's tab.
        indicator.addClass("m-snap");
        doc.scheduler().requestAnimationFrame(t -> {
            Motion.indicator(indicator, start);
            doc.scheduler().requestAnimationFrame(t2 -> {
                indicator.removeClass("m-snap");
                Motion.indicator(indicator, target);
            });
        });
    }

    /** Every title bar with a close button gets the window/maximize switch before it. */
    private static void windowSwitch(Document doc) {
        for (Element bar : doc.querySelectorAll(".o-titlebar")) {
            Element close = bar.querySelector("[data-action=close]");
            if (close == null) close = bar.querySelector("#close");
            if (close == null || bar.querySelector("[data-action=window]") != null) continue;
            Element button = Dom.html(doc, "<div class=\"ore-btn icon o-winbtn\" data-action=\"window\" tabindex=\"0\" title=\"窗口化 / 最大化\">"
                    + "<img class=\"o-ico i-restore\" src=\"../textures/icons/restore.png\">"
                    + "<img class=\"o-ico i-max\" src=\"../textures/icons/maximize.png\"></div>");
            close.parentElement().insertBefore(button, close);
            activate(button, () -> window(doc, window.equals("max") ? "mini" : "max"));
        }
    }

    /** Runs {@code action} on a click, and on Enter or Space while focused. */
    public static void activate(Element e, Runnable action) {
        Dom.onClick(e, ev -> action.run());
        Dom.onKey(e, ev -> {
            if (ev.key.equals("Enter") || ev.key.equals(" ")) {
                ev.preventDefault();
                action.run();
            }
        });
    }
}
