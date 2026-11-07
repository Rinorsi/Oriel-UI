package dev.oriel.engine.style;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;

/** Immutable track list with additive line-name metadata; list equality and CSS serialization stay unchanged. */
public final class GridTemplate extends AbstractList<GridTrack> implements RandomAccess {
    private final List<GridTrack> tracks;
    private final List<List<String>> names;

    public GridTemplate(List<GridTrack> tracks, List<List<String>> names) {
        this.tracks = List.copyOf(tracks);
        if (names.size() != tracks.size() + 1) throw new IllegalArgumentException("One name group is required per grid line");
        this.names = names.stream().map(List::copyOf).toList();
    }

    @Override public GridTrack get(int index) { return tracks.get(index); }
    @Override public int size() { return tracks.size(); }

    public static List<String> namesAt(List<GridTrack> tracks, int line) {
        return tracks instanceof GridTemplate template ? template.names.get(line) : List.of();
    }

    public static boolean sameNames(List<GridTrack> a, List<GridTrack> b) {
        if (!(a instanceof GridTemplate) && !(b instanceof GridTemplate)) return true;
        if (a.size() != b.size()) return false;
        for (int i = 0; i <= a.size(); i++) if (!namesAt(a, i).equals(namesAt(b, i))) return false;
        return true;
    }

    /** Append a segment, joining the two name groups that share its first line. */
    public static void appendNames(List<List<String>> output, List<GridTrack> segment) {
        if (!namesAt(segment, 0).isEmpty()) {
            var merged = new ArrayList<>(output.getLast());
            merged.addAll(namesAt(segment, 0));
            output.set(output.size() - 1, merged);
        }
        for (int i = 1; i <= segment.size(); i++) output.add(namesAt(segment, i));
    }
}
