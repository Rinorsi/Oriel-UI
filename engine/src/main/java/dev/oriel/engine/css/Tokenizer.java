package dev.oriel.engine.css;

import dev.oriel.engine.parse.ParserAudit;
import java.util.List;

/** Token contract adapter for the independent CSS scanner. */
final class Tokenizer {
    private Tokenizer() {}
    static List<Token> tokenize(String source) { ParserAudit.mode(); return CssScanner.scan(source); }
    static boolean isWhitespace(int c) { return CssScanner.space(c); }
}
