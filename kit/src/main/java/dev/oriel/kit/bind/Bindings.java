package dev.oriel.kit.bind;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.event.EventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Opt-in, path-only Java bindings. Own one instance per document and close it before discarding the page. */
public final class Bindings implements AutoCloseable {
    private static final Pattern FOR = Pattern.compile("(" + ViewModel.IDENTIFIER + ") +in +(.+)");
    private static final Pattern SUFFIX = Pattern.compile("[a-z_][a-z0-9_-]*");
    private final Document document;
    private final ViewModel model;
    private final Set<Group> groups = new HashSet<>();
    private Binding root;
    private Runnable unsubscribe = () -> {};
    private final EventListener unload = event -> close();
    private final Set<String> pending = new LinkedHashSet<>();
    private boolean updating;
    private boolean closed;

    private Bindings(Document document, ViewModel model) {
        this.document = Objects.requireNonNull(document);
        this.model = Objects.requireNonNull(model);
    }

    public static Bindings attach(Document document, ViewModel model) {
        Bindings bindings = new Bindings(document, model);
        try {
            bindings.validate(document.documentElement());
            bindings.root = bindings.compile(document.documentElement(), bindings.new Scope(null, null, null, null));
            bindings.unsubscribe = model.subscribe(bindings::update);
            document.addEventListener("unload", bindings.unload);
            bindings.update("");
            return bindings;
        } catch (RuntimeException ex) {
            bindings.close();
            throw ex;
        }
    }

    /** Stops observation and removes event listeners, leaving the last rendered DOM in place. Idempotent. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        unsubscribe.run();
        document.removeEventListener("unload", unload, false);
        pending.clear();
        for (Group group : List.copyOf(groups)) group.close();
    }

    private void update(String changed) {
        if (closed) return;
        pending.add(changed);
        if (updating) return;
        updating = true;
        try {
            while (!pending.isEmpty() && !closed) {
                String next = pending.iterator().next();
                pending.remove(next);
                root.update(next);
            }
        } finally {
            updating = false;
            pending.clear();
        }
    }

    private void validate(Element element) {
        boolean loop = element.hasAttribute("o-for");
        for (String name : element.attributes().keySet()) {
            if (!name.startsWith("o-")) continue;
            if (element.hasInertContent())
                throw error(element, "structure", "Java bindings cannot be placed on an inert template element");
            if (name.equals("o-for")) {
                Matcher matcher = FOR.matcher(element.getAttribute(name));
                if (!matcher.matches()) throw error(element, "expression", "Expected o-for='alias in collection.path'");
                try { ViewModel.checkPath(matcher.group(2)); }
                catch (IllegalArgumentException ex) { throw error(element, "expression", ex.getMessage()); }
                if (!path(element, "o-key").startsWith(matcher.group(1) + "."))
                    throw error(element, "key", "o-key must be a path below the row alias");
            } else if (name.equals("o-key")) {
                if (!loop) throw error(element, "directive", "o-key requires o-for on the same element");
            } else if (name.equals("o-text") || name.equals("o-if")) {
                path(element, name);
                if (name.equals("o-text") && !element.children().isEmpty())
                    throw error(element, "structure", "o-text requires an element without child elements");
            } else if (name.startsWith("o-class:") || name.startsWith("o-on:") || name.startsWith("o-attr:")) {
                String prefix = name.substring(0, name.indexOf(':') + 1);
                String target = suffix(element, name, prefix);
                if (prefix.equals("o-attr:") && (target.startsWith("o-") || target.startsWith("on")
                        || target.equals("style") || target.equals("class")))
                    throw error(element, "attribute", "o-attr cannot write binding, event, style or class attributes: " + target);
                path(element, name);
            } else throw error(element, "directive", "Unknown binding directive: " + name);
        }
        if (!element.hasInertContent()) for (Element child : element.children()) validate(child);
    }

    private interface Binding extends AutoCloseable {
        void update(String changed);
        @Override default void close() {}
    }

    private final class Group implements Binding {
        final List<Binding> children = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        boolean disposed;
        Group() { groups.add(this); }
        @Override public void update(String path) {
            for (Binding binding : children) binding.update(path);
        }
        @Override public void close() {
            if (disposed) return;
            disposed = true;
            for (Binding binding : children) binding.close();
            for (Runnable action : cleanup) action.run();
            groups.remove(this);
        }
    }

    private final class Scope {
        final Scope parent;
        final String alias, dependency;
        Object item;
        Scope(Scope parent, String alias, String dependency, Object item) {
            this.parent = parent; this.alias = alias; this.dependency = dependency; this.item = item;
        }
        Scope local(String path) {
            String first = path.split("\\.", 2)[0];
            for (Scope scope = this; scope != null; scope = scope.parent)
                if (first.equals(scope.alias)) return scope;
            return null;
        }
        String dependency(String path) {
            Scope local = local(path);
            return local == null ? path : local.dependency;
        }
        Object read(String path) {
            Scope local = local(path);
            if (local == null) return model.get(path);
            int dot = path.indexOf('.');
            return dot < 0 ? local.item : ViewModel.read(local.item, path.substring(dot + 1));
        }
    }

    private String path(Element element, String attribute) {
        String value = element.getAttribute(attribute);
        try { ViewModel.checkPath(value); }
        catch (IllegalArgumentException ex) { throw error(element, "expression", attribute + ": " + ex.getMessage()); }
        return value;
    }

    private Object read(Element element, Scope scope, String path) {
        try { return scope.read(path); }
        catch (IllegalArgumentException ex) { throw error(element, "path", ex.getMessage()); }
    }

    private static boolean affected(String dependency, String changed) {
        return changed.isEmpty() || dependency.equals(changed) || dependency.startsWith(changed + ".")
                || changed.startsWith(dependency + ".");
    }

    private Binding property(Element element, Scope scope, String path, Consumer<Object> apply) {
        return changed -> {
            if (affected(scope.dependency(path), changed)) apply.accept(read(element, scope, path));
        };
    }

    private Binding compile(Element element, Scope scope) {
        if (element.hasAttribute("o-for")) return new Loop(element, scope);
        Group group = new Group();
        for (Map.Entry<String, String> attribute : element.attributes().entrySet()) {
            String name = attribute.getKey();
            if (!name.startsWith("o-")) continue;
            if (name.equals("o-text")) {
                if (!element.children().isEmpty())
                    throw error(element, "structure", "o-text requires an element without child elements");
                String path = path(element, name);
                group.children.add(property(element, scope, path, value -> {
                    String text = scalar(element, value);
                    if (!element.textContent().equals(text)) element.setTextContent(text);
                }));
            } else if (name.equals("o-if")) {
                Node anchor = document.createTextNode("");
                element.parentNode().insertBefore(anchor, element);
                group.children.add(property(element, scope, path(element, name), value -> {
                    if (bool(element, value)) {
                        if (element.parentNode() == null) anchor.parentNode().insertBefore(element, anchor.nextSibling());
                    } else element.remove();
                }));
            } else if (name.startsWith("o-class:")) {
                String token = suffix(element, name, "o-class:");
                group.children.add(property(element, scope, path(element, name), value -> {
                    boolean on = bool(element, value);
                    if (element.hasClass(token) != on) element.toggleClass(token, on);
                }));
            } else if (name.startsWith("o-attr:")) {
                String target = suffix(element, name, "o-attr:");
                if (target.startsWith("o-") || target.startsWith("on") || target.equals("style") || target.equals("class"))
                    throw error(element, "attribute", "o-attr cannot write binding, event, style or class attributes: " + target);
                group.children.add(property(element, scope, path(element, name), value -> {
                    String text = value == null || Boolean.FALSE.equals(value) ? null
                            : Boolean.TRUE.equals(value) ? "" : scalar(element, value);
                    if (!Objects.equals(element.getAttribute(target), text)) {
                        if (text == null) element.removeAttribute(target);
                        else element.setAttribute(target, text);
                    }
                }));
            } else if (name.startsWith("o-on:")) {
                String event = suffix(element, name, "o-on:");
                String path = path(element, name);
                group.children.add(property(element, scope, path, value -> command(element, value)));
                EventListener listener = input -> {
                    if (closed) return;
                    ViewModel.Command action = command(element, read(element, scope, path));
                    try { action.execute(input); }
                    catch (RuntimeException ex) { throw error(element, "command", "Command '" + path + "' failed: " + ex.getMessage()); }
                };
                element.addEventListener(event, listener);
                group.cleanup.add(() -> element.removeEventListener(event, listener, false));
            } else throw error(element, "directive", "Unknown or misplaced binding directive: " + name);
        }
        if (!element.hasInertContent())
            for (Element child : element.children()) group.children.add(compile(child, scope));
        return group;
    }

    private String suffix(Element element, String attribute, String prefix) {
        String suffix = attribute.substring(prefix.length());
        if (!SUFFIX.matcher(suffix).matches()) throw error(element, "expression", "Invalid directive suffix: " + attribute);
        return suffix;
    }

    private String scalar(Element element, Object value) {
        if (value == null) return "";
        if (value instanceof String || value instanceof Boolean || value instanceof Number) return value.toString();
        throw error(element, "type", "Expected a scalar (string, number, boolean or null)");
    }

    private boolean bool(Element element, Object value) {
        if (value instanceof Boolean result) return result;
        throw error(element, "type", "o-if and o-class require a boolean");
    }

    private ViewModel.Command command(Element element, Object value) {
        if (value instanceof ViewModel.Command result) return result;
        throw error(element, "type", "o-on requires a ViewModel.Command");
    }

    private final class Loop implements Binding {
        final Element template;
        final Scope scope;
        final String alias, collection, key;
        final Node anchor;
        Map<Object, Row> rows = new LinkedHashMap<>();

        Loop(Element element, Scope scope) {
            this.template = element; this.scope = scope;
            if (element.hasInertContent()) throw error(element, "structure", "o-for must be placed on the repeated element, not template");
            Matcher matcher = FOR.matcher(element.getAttribute("o-for"));
            if (!matcher.matches()) throw error(element, "expression", "Expected o-for='alias in collection.path'");
            alias = matcher.group(1);
            collection = matcher.group(2);
            try { ViewModel.checkPath(collection); }
            catch (IllegalArgumentException ex) { throw error(element, "expression", ex.getMessage()); }
            key = path(element, "o-key");
            if (!key.startsWith(alias + ".")) throw error(element, "key", "o-key must be a path below the row alias");
            anchor = document.createTextNode("");
            element.parentNode().insertBefore(anchor, element);
            element.remove();
        }

        @Override public void update(String changed) {
            if (!affected(scope.dependency(collection), changed)) {
                for (Row row : rows.values()) row.binding.update(changed);
                return;
            }
            Object value = read(template, scope, collection);
            if (!(value instanceof List<?> items)) throw error(template, "type", "o-for requires a List");
            Map<Object, Object> keyed = new LinkedHashMap<>();
            for (Object item : items) {
                Scope local = new Scope(scope, alias, scope.dependency(collection), item);
                Object id = read(template, local, key);
                if (!(id instanceof String) && !(id instanceof Integer) && !(id instanceof Long)
                        && !(id instanceof Short) && !(id instanceof Byte))
                    throw error(template, "key", "o-key requires a non-null string or integral key");
                if (keyed.containsKey(id)) throw error(template, "key", "Duplicate o-key: " + id);
                keyed.put(id, item);
            }
            Map<Object, Row> next = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> entry : keyed.entrySet()) {
                Row row = rows.get(entry.getKey());
                if (row == null) row = new Row(entry.getValue());
                else row.local.item = entry.getValue();
                next.put(entry.getKey(), row);
            }
            for (Map.Entry<Object, Row> old : rows.entrySet())
                if (!next.containsKey(old.getKey())) old.getValue().remove();
            rows = next;
            Node cursor = anchor;
            List<Row> ordered = new ArrayList<>(rows.values());
            for (int i = ordered.size() - 1; i >= 0; i--) {
                Row row = ordered.get(i);
                if (row.end.nextSibling() != cursor)
                    for (Node node : row.nodes()) anchor.parentNode().insertBefore(node, cursor);
                cursor = row.start;
            }
            for (Row row : rows.values()) row.binding.update("");
        }

        @Override public void close() {
            for (Row row : rows.values()) row.binding.close();
        }

        private final class Row {
            final Scope local;
            final Node start, end;
            final Binding binding;
            Row(Object item) {
                local = new Scope(scope, alias, scope.dependency(collection), item);
                start = document.createTextNode(""); end = document.createTextNode("");
                Node parent = anchor.parentNode();
                parent.insertBefore(start, anchor);
                Element clone = (Element) template.cloneNode(true);
                clone.removeAttribute("o-for"); clone.removeAttribute("o-key");
                parent.insertBefore(clone, anchor);
                parent.insertBefore(end, anchor);
                binding = compile(clone, local);
            }
            List<Node> nodes() {
                List<Node> nodes = new ArrayList<>();
                for (Node node = start; node != null; node = node.nextSibling()) {
                    nodes.add(node);
                    if (node == end) break;
                }
                return nodes;
            }
            void remove() {
                binding.close();
                for (Node node : nodes()) node.remove();
            }
        }
    }

    private IllegalArgumentException error(Element element, String code, String message) {
        Diagnostic diagnostic = new Diagnostic(Diagnostic.Severity.ERROR, "binding-" + code, document.url(),
                element.sourceLine(), element.sourceColumn(), message, "Use the path-only Java binding contract in docs/zh/bindings.md");
        document.diagnostics().report(diagnostic);
        return new IllegalArgumentException(diagnostic.format());
    }
}
