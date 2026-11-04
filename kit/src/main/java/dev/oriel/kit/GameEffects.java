package dev.oriel.kit;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Client effect snapshots supplied by the game; a missing provider keeps the animated preview fixtures. */
public final class GameEffects {
    public enum Kind { BENEFICIAL, HARMFUL, NEUTRAL }

    /**
     * Level is one-based; seconds is rounded up, or -1 for an infinite duration. Ambient is not proof of a beacon.
     * Visible controls particles; showIcon is the vanilla icon flag. Neither removes an active effect from this list.
     */
    public record Entry(String id, String name, int level, Kind kind, int seconds, String icon,
                        boolean ambient, boolean visible, boolean showIcon) {
        public Entry(String id, String name, int level, Kind kind, int seconds, String icon, boolean ambient, boolean visible) {
            this(id, name, level, kind, seconds, icon, ambient, visible, true);
        }
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(icon);
            if (level < 1 || seconds < -1) throw new IllegalArgumentException("Invalid effect level or duration: " + id);
        }
    }

    /** Providers reuse this immutable value until something changes; an empty list means no active effects. */
    public record Snapshot(List<Entry> effects) {
        public Snapshot { effects = List.copyOf(effects); }
    }

    private static Supplier<Snapshot> source = () -> null;

    private GameEffects() {}

    public static void provide(Supplier<Snapshot> provider) { source = Objects.requireNonNull(provider); }

    public static Snapshot current() { return source.get(); }

    public static void useFixtures() { source = () -> null; }
}
