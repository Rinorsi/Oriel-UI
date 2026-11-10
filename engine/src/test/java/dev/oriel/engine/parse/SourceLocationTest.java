package dev.oriel.engine.parse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceLocationTest {
    @Test void cachedAndIndexedPositionsMatchOriginalScannerAtEveryBoundary() {
        for (String source : new String[] {"", "abc", "a\r\nb\rc\nd\fe", "\r\n\r\n", "汉字😀\r\n尾\n"}) {
            var index = new SourceLocation.Index(source);
            for (int offset = -2; offset <= source.length() + 2; offset++) {
                var expected = scan(source, offset);
                assertEquals(expected, index.at(offset), source + " @" + offset);
                assertEquals(expected, SourceLocation.at(source, offset));
                assertEquals(expected, SourceLocation.at(new String(source), offset));
            }
        }
    }

    private static SourceLocation scan(String source, int offset) {
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
