package dev.oriel.engine.i18n;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.dom.Text;
import dev.oriel.engine.event.Event;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/** Per-document messages and text bindings. Hosts supply resource-pack translations; libraries may supply defaults. */
public final class Messages {
    private static final Map<String, BiFunction<String, String, String>> BUNDLES = new ConcurrentHashMap<>();
    private static final Set<String> ATTRIBUTES = Set.of("title", "placeholder", "alt", "aria-label", "value",
            "data-toast", "data-notice", "data-notice-action", "data-title");
    private final Document document;
    private final Map<Element, List<Binding>> bindings = new IdentityHashMap<>();
    private String language;

    public Messages(Document document) { this.document = document; }

    /** Registers immutable library defaults for keys beginning with prefix, without choosing a global language. */
    public static void registerBundle(String prefix, BiFunction<String, String, String> lookup) {
        BUNDLES.put(prefix, lookup);
    }

    public String language() { return document.host().language().toLowerCase(Locale.ROOT).replace('-', '_'); }

    /** Preserves legacy host formatting while routing missing keys through library defaults and diagnostics. */
    public String translate(String key, String... args) {
        if (BUNDLES.keySet().stream().anyMatch(key::startsWith) || pattern(language(), key) != null) {
            return text(key, (Object[]) args);
        }
        String value = document.host().translate(key, args);
        return value == null || value.equals(key) ? text(key, (Object[]) args) : value;
    }

    /** Formats a message. Missing keys and malformed arguments are visible document diagnostics. */
    public String text(String key, Object... args) {
        String pattern = pattern(language(), key);
        if (pattern == null) {
            pattern = language().equals("zh_cn") ? null : pattern("zh_cn", key);
            missing(key, pattern == null ? "the key itself" : "zh_cn");
            if (pattern == null) return key;
        }
        return format(key, pattern, args);
    }

    private String pattern(String locale, String key) {
        String pattern = document.host().translation(locale, key);
        if (pattern == null) {
            for (var entry : BUNDLES.entrySet()) {
                if (key.startsWith(entry.getKey())) {
                    pattern = entry.getValue().apply(locale, key);
                    if (pattern != null) break;
                }
            }
        }
        return pattern;
    }

    private void missing(String key, String fallback) {
        warn("missing-translation", key, "No translation for '" + key + "' in " + language() + "; using " + fallback,
                "add this key to the language resource files");
    }

    /** English has a singular form; Chinese uses the other form. Count is the first format argument. */
    public String plural(String key, long count, Object... args) {
        Object[] values = new Object[args.length + 1];
        values[0] = count;
        System.arraycopy(args, 0, values, 1, args.length);
        String form = language().startsWith("en") && Math.abs(count) == 1 ? "one" : "other";
        return text(key + "." + form, values);
    }

    private String format(String key, String pattern, Object[] args) {
        try {
            return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
        } catch (java.util.IllegalFormatException e) {
            warn("invalid-translation", key, "Cannot format translation '" + key + "': " + e.getMessage(),
                    "match the message's %s or positional placeholders to its arguments");
            return key;
        }
    }

    private void warn(String code, String key, String message, String suggestion) {
        document.diagnostics().reportOnce(code + ":" + language() + ":" + key, () -> new Diagnostic(
                Diagnostic.Severity.WARNING, code, document.url(), 0, message, suggestion));
    }

    /** Binds a parsed subtree. Direct text nodes are edited in place: icons, whitespace, and nested markup survive. */
    public void bind(Node node) {
        if (node instanceof Element element && !bindings.containsKey(element)) {
            List<Binding> found = new ArrayList<>();
            for (var attr : element.attributes().entrySet()) {
                String name = attr.getKey(), key = attr.getValue();
                if (name.equals("data-i18n")) {
                    Text target = null;
                    for (Node child : element.childNodes()) {
                        if (child instanceof Text t && !t.data().isBlank()) { target = t; break; }
                    }
                    if (target == null) target = element.appendChild(document.createTextNode(""));
                    found.add(new Binding(element, target, null, key));
                } else if (name.startsWith("data-i18n-text-")) {
                    try {
                        int index = Integer.parseInt(name.substring(15));
                        if (index >= 0 && index < element.childCount() && element.childAt(index) instanceof Text t) {
                            found.add(new Binding(element, t, null, key));
                        } else warn("invalid-translation-binding", key, "No text node for " + name, "bind an existing direct text node");
                    } catch (NumberFormatException e) {
                        warn("invalid-translation-binding", key, "Invalid text index in " + name, "use a zero-based child-node index");
                    }
                } else if (name.startsWith("data-i18n-") && ATTRIBUTES.contains(name.substring(10))) {
                    found.add(new Binding(element, null, name.substring(10), key));
                }
            }
            if (!found.isEmpty()) {
                bindings.put(element, found);
                found.forEach(Binding::apply);
            }
        }
        for (Node child : node.childNodes()) bind(child);
    }

    /** Language switches invalidate bindings even when no DOM or animation changed. */
    public boolean languageChanged() { return !language().equals(language); }

    public void refresh() {
        if (!languageChanged()) return;
        if (language == null) bind(document);
        boolean changed = language != null;
        language = language();
        bindings.entrySet().removeIf(entry -> !entry.getKey().isConnected());
        for (List<Binding> list : bindings.values()) list.forEach(Binding::apply);
        if (changed) {
            document.invalidateStyle();
            document.dispatchEvent(new Event("languagechange", false, false));
        }
    }

    private final class Binding {
        final Element element;
        final Text node;
        final String attribute, key, leading, trailing;
        String last;

        Binding(Element element, Text node, String attribute, String key) {
            this.element = element; this.node = node; this.attribute = attribute; this.key = key;
            String original = node == null ? "" : node.data();
            leading = original.substring(0, original.length() - original.stripLeading().length());
            trailing = original.substring(original.stripTrailing().length());
        }

        void apply() {
            String current = node == null ? element.getAttribute(attribute) : node.data();
            // Controller-owned or user-edited values must not be overwritten by an old markup binding.
            if (last != null && !last.equals(current)) return;
            String value = leading + text(key) + trailing;
            if (!value.equals(current)) {
                if (node != null) node.setData(value);
                else element.setAttribute(attribute, value);
            }
            last = value;
        }
    }
}
