package dev.oriel.engine.paint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/** Byte-budget retention policy, used only after a host knows every target from a frame is idle. */
public final class LayerRetention {
    private LayerRetention() {}

    /** Returns evictions, newest targets preferred. Active targets must never be passed to this method. */
    public static <T> List<T> evict(List<T> idle, long budget, ToLongFunction<T> bytes, ToIntFunction<T> lastUsed) {
        if (budget < 0) throw new IllegalArgumentException("Negative layer retention budget");
        List<T> newest = new ArrayList<>(idle);
        newest.sort(Comparator.comparingInt(lastUsed).reversed());
        List<T> evictions = new ArrayList<>();
        long remaining = budget;
        for (T target : newest) {
            long cost = bytes.applyAsLong(target);
            if (cost < 0) throw new IllegalArgumentException("Negative layer byte size");
            if (cost > remaining) evictions.add(target);
            else remaining -= cost;
        }
        return evictions;
    }
}
