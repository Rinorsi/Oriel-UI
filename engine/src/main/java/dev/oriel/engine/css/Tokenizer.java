package dev.oriel.engine.css;

import dev.oriel.engine.parse.ParserAudit;
import java.util.List;

final class Tokenizer {
    private Tokenizer() {}
    static List<Token> tokenize(String source) {
        if (ParserAudit.mode() == ParserAudit.Mode.OLD) return LegacyTokenizer.tokenize(source);
        List<Token> tokens = CssScanner.scan(source);
        if (ParserAudit.mode() == ParserAudit.Mode.BOTH) ParserAudit.compare("css-tokens", "<tokens>", CssAudit.values(LegacyTokenizer.tokenize(source)), CssAudit.values(tokens));
        return tokens;
    }
    static boolean isWhitespace(int c) { return CssScanner.space(c); }
}
