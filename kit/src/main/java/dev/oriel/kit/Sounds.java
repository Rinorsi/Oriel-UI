package dev.oriel.kit;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.MouseEvent;

import java.util.HashMap;
import java.util.Map;

/** Shared UI sound tokens. Hosts resolve tokens here; core never depends on Minecraft sound names. */
public final class Sounds {
    public record Sound(String id, float volume, float pitch) {}

    private static final Map<String, Sound> TOKENS = Map.of(
            "click", new Sound("minecraft:ui.button.click", 0.6f, 1f),
            "hover", new Sound("minecraft:ui.button.click", 0.12f, 1.6f),
            "open", new Sound("minecraft:block.note_block.harp", 0.35f, 1.2f),
            "close", new Sound("minecraft:block.note_block.harp", 0.3f, 0.8f),
            "tab", new Sound("minecraft:ui.button.click", 0.4f, 1.3f),
            "toggle", new Sound("minecraft:ui.button.click", 0.45f, 1.1f),
            "success", new Sound("minecraft:entity.experience_orb.pickup", 0.4f, 1.2f),
            "error", new Sound("minecraft:block.note_block.bass", 0.4f, 0.8f));
    private static final String TARGETS = "[data-oriel-sound], .ore-btn, .ore-tab, .o-apptab, .o-seg > *, .o-apptabs > *, .ore-toggle, .o-chip.hot";

    private Sounds() {}

    public static Map<String, Sound> tokens() { return TOKENS; }

    /** A token or an existing namespaced event id. Unknown bare names are programming errors. */
    public static Sound resolve(String name, float volume, float pitch) {
        Sound token = TOKENS.get(name);
        if (token != null) return new Sound(token.id(), token.volume() * volume, token.pitch() * pitch);
        if (name.contains(":")) return new Sound(name, volume, pitch);
        throw new IllegalArgumentException("Unknown UI sound token: " + name + "; expected " + TOKENS.keySet());
    }

    /** Hosts apply user settings and rate limits, including for explicit script calls. */
    public static void play(Document doc, String token) {
        if (!"off".equals(token)) doc.host().playSound(token, 1, 1);
    }

    static boolean nativeControl(Element e) {
        return switch (e.tagName()) {
            case "button", "summary", "select", "label" -> true;
            case "input" -> switch (e.inputType()) {
                case "checkbox", "radio", "button", "submit", "reset", "image" -> true;
                default -> false;
            };
            default -> false;
        };
    }

    private static boolean insideNativeControl(Element e) {
        for (Element parent = e; parent != null; parent = parent.parentElement()) {
            if (nativeControl(parent)) return true;
        }
        return false;
    }

    private static String token(Element e) {
        String explicit = e.getAttribute("data-oriel-sound");
        if (explicit != null) {
            if (!explicit.equals("off") && !TOKENS.containsKey(explicit)) {
                e.ownerDocument().diagnostics().report(new Diagnostic(Diagnostic.Severity.ERROR, "ui-sound-token",
                        e.ownerDocument().url(), e.sourceLine(), "Unknown UI sound token: " + explicit,
                        "Use click, hover, open, close, tab, toggle, success, error or off."));
                return "off";
            }
            return explicit;
        }
        if (e.hasClass("ore-toggle") || e.hasClass("hot") && e.hasClass("o-chip")
                || e.inputType().equals("checkbox") || e.inputType().equals("radio")) return "toggle";
        if (e.hasClass("ore-tab") || e.closest(".o-seg, .o-apptabs") != null) return "tab";
        return "click";
    }

    private static void activate(Element e) {
        if (e == null || e.isDisabled() || "true".equals(e.getAttribute("aria-disabled"))) return;
        String name = token(e);
        if (!name.equals("hover")) play(e.ownerDocument(), name);
    }

    /** Installs delegated interaction sounds and returns a DOM-change-only modal watcher for Shell's loop. */
    static Runnable attach(Document doc) {
        validate(doc);
        doc.addEventListener("activationsound", event -> {
            event.preventDefault();
            if (event.target() instanceof Element e) activate(e);
        });
        doc.addEventListener("click", event -> {
            Element e = Dom.closest(event, TARGETS);
            Element target = Dom.closest(event, "*");
            if (e != null && target != null && !insideNativeControl(target) && !event.defaultPrevented()) activate(e);
        });
        doc.addEventListener("mouseover", event -> {
            if (!(event instanceof MouseEvent mouse)) return;
            Element e = Dom.closest(event, "[data-oriel-sound]");
            if (e != null && "hover".equals(token(e)) && !e.isDisabled()
                    && (mouse.relatedTarget == null || !e.contains(mouse.relatedTarget))) play(doc, "hover");
        });
        Map<Element, Boolean> modals = new HashMap<>();
        for (Element e : doc.querySelectorAll(".m-modal")) modals.put(e, e.hasClass("on"));
        return () -> {
            validate(doc);
            for (Element e : doc.querySelectorAll(".m-modal")) {
                boolean on = e.hasClass("on");
                boolean before = modals.getOrDefault(e, false);
                if (on != before && !"off".equals(e.getAttribute("data-oriel-sound"))) play(doc, on ? "open" : "close");
                modals.put(e, on);
            }
            modals.keySet().removeIf(e -> !e.isConnected());
        };
    }

    private static void validate(Document doc) {
        for (Element e : doc.querySelectorAll("[data-oriel-sound]")) token(e);
    }
}
