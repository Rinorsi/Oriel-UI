package dev.oriel.kit.bind;

import dev.oriel.engine.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Explicit, observable Java state. Maps and lists are snapshotted; no reflection or script evaluation is used. */
public final class ViewModel {
    static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*";
    private static final Pattern PATH = Pattern.compile(IDENTIFIER + "(?:\\." + IDENTIFIER + ")*");

    @FunctionalInterface
    public interface Command {
        void execute(Event event);
    }

    private Map<String, Object> values;
    private final List<Consumer<String>> listeners = new ArrayList<>();

    public ViewModel(Map<String, ?> initial) {
        values = copyMap(Objects.requireNonNull(initial));
    }

    public Object get(String path) {
        checkPath(path);
        return read(values, path);
    }

    /** Changes an existing path, synchronously notifying bindings on the document's owning thread. */
    public void set(String path, Object value) {
        Object previous = get(path);
        Object next = snapshot(value);
        if (Objects.equals(previous, next)) return;
        values = replace(values, path.split("\\."), 0, next);
        RuntimeException failure = null;
        for (Consumer<String> listener : List.copyOf(listeners)) {
            try { listener.accept(path); }
            catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }

    Runnable subscribe(Consumer<String> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    static void checkPath(String path) {
        if (path == null || !PATH.matcher(path).matches())
            throw new IllegalArgumentException("Expected a dotted ViewModel path, got: " + path);
    }

    static Object read(Object value, String path) {
        for (String segment : path.split("\\.")) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(segment))
                throw new IllegalArgumentException("Unknown ViewModel path: " + path);
            value = map.get(segment);
        }
        return value;
    }

    private static Map<String, Object> replace(Map<String, Object> map, String[] path, int index, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(map);
        if (index == path.length - 1) copy.put(path[index], value);
        else {
            Map<String, Object> child = copyMap((Map<?, ?>) map.get(path[index]));
            copy.put(path[index], replace(child, path, index + 1, value));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object snapshot(Object value) {
        if (value instanceof Map<?, ?> map) return copyMap(map);
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(snapshot(item));
            return Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Command
                || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof java.math.BigInteger
                || value instanceof java.math.BigDecimal) return value;
        throw new IllegalArgumentException("Unsupported ViewModel value: " + value.getClass().getName());
    }

    private static Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !key.matches(IDENTIFIER))
                throw new IllegalArgumentException("ViewModel keys must be identifiers: " + entry.getKey());
            copy.put(key, snapshot(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
