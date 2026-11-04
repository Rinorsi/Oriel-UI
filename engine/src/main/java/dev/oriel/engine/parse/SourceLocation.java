package dev.oriel.engine.parse;

/** One-based positions in the original UTF-16 source, with CRLF counted once. */
public record SourceLocation(int line, int column) {
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
