package dev.oriel.engine.parse;

/** One-based positions in the original UTF-16 source, with CRLF counted once. */
public record SourceLocation(int line, int column) {
    // The index holds no reference to its key. A stylesheet can be reclaimed after its document closes.
    private static final java.util.Map<String, Index> INDEXES = new java.util.WeakHashMap<>();
    public static final class Index {
        private final int[] starts;
        private final int[] crlf;
        private final int length;
        public Index(String source) {
            length = source.length();
            java.util.ArrayList<Integer> lines = new java.util.ArrayList<>(); lines.add(0);
            java.util.ArrayList<Integer> pairs = new java.util.ArrayList<>();
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (c == '\r') { if (i + 1 < source.length() && source.charAt(i + 1) == '\n') { i++; pairs.add(i); } lines.add(i + 1); }
                else if (c == '\n' || c == '\f') lines.add(i + 1);
            }
            starts = lines.stream().mapToInt(Integer::intValue).toArray();
            crlf = pairs.stream().mapToInt(Integer::intValue).toArray();
        }
        public SourceLocation at(int offset) {
            offset = Math.max(0, Math.min(length, offset));
            // An offset between CR and LF already belongs to the new line, at column one.
            if (java.util.Arrays.binarySearch(crlf, offset) >= 0) offset++;
            int line = java.util.Arrays.binarySearch(starts, offset);
            if (line < 0) line = -line - 2;
            return new SourceLocation(line + 1, offset - starts[line] + 1);
        }
    }
    public static SourceLocation at(String source, int offset) {
        Index index;
        synchronized (INDEXES) {
            index = INDEXES.get(source);
            if (index == null) {
                index = new Index(source);
                INDEXES.put(source, index);
            }
        }
        return index.at(offset);
    }
}
