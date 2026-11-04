package dev.oriel.engine.parse;

/** One-based positions in the original UTF-16 source, with CRLF counted once. */
public record SourceLocation(int line, int column) {
    public static final class Index {
        private final int[] starts;
        private final int length;
        public Index(String source) {
            length = source.length();
            java.util.ArrayList<Integer> lines = new java.util.ArrayList<>(); lines.add(0);
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (c == '\r') { if (i + 1 < source.length() && source.charAt(i + 1) == '\n') i++; lines.add(i + 1); }
                else if (c == '\n' || c == '\f') lines.add(i + 1);
            }
            starts = lines.stream().mapToInt(Integer::intValue).toArray();
        }
        public SourceLocation at(int offset) {
            offset = Math.max(0, Math.min(length, offset));
            int line = java.util.Arrays.binarySearch(starts, offset);
            if (line < 0) line = -line - 2;
            return new SourceLocation(line + 1, offset - starts[line] + 1);
        }
    }
    public static SourceLocation at(String source, int offset) {
        int line = 1, column = 1;
        for (int i = 0; i < Math.min(offset, source.length()); i++) {
            char c = source.charAt(i);
            if (c == '\r') {
                if (i + 1 < offset && source.charAt(i + 1) == '\n') i++;
                line++; column = 1;
            } else if (c == '\n' || c == '\f') { line++; column = 1; }
            else column++;
        }
        return new SourceLocation(line, column);
    }
}
