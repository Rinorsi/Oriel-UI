package dev.oriel.engine.css;

import dev.oriel.engine.diag.Diagnostic;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CssSyntaxMigrationTest {
    @Test
    void atRuleInDeclarationListDoesNotSwallowFollowingDeclaration() {
        var declarations = CssSyntax.declarations(CssSyntax.components("@unknown { x:y } color:red; width:2px"), (text, line) -> {});
        assertEquals(List.of("color", "width"), declarations.stream().map(CssParser.Declaration::name).toList());
    }

    @Test
    void syntaxFailuresCarryOriginalPositionsAndRecover() {
        List<Diagnostic> issues = new ArrayList<>();
        CssIssues.in("test:bad.css", issues::add, () -> CssSyntax.components("a {\r\n x: url(a b); y: 'broken\n z:2;"));
        assertTrue(issues.stream().anyMatch(d -> d.line() == 2 && d.column() == 5 && d.message().contains("url")));
        assertTrue(issues.stream().anyMatch(d -> d.message().contains("string")));
        assertTrue(issues.stream().anyMatch(d -> d.message().contains("Unclosed block")));
        assertTrue(issues.stream().allMatch(d -> d.format().startsWith("test:bad.css:")));
    }

    @Test
    void eofEscapeAndNullUseReplacementScalarInsteadOfAStandaloneBackslash() {
        var token = CssScanner.scan("abc\\").getFirst();
        assertEquals(Token.Type.IDENT, token.type);
        assertEquals("abc�", token.value);
        assertEquals("a�b", CssScanner.scan("a\0b").getFirst().value);
    }

    @Test
    void nestedBlocksRetainSpansAndImportantOnlyAtDeclarationEnd() {
        String text = "--Case: f([x],{a;b}) ! IMPORTANT; width:calc(2px + 3px); content:'!important'";
        var declarations = CssSyntax.declarations(CssSyntax.components(text), (value, line) -> fail(value));
        assertEquals(3, declarations.size());
        assertEquals("--Case", declarations.getFirst().name());
        assertTrue(declarations.getFirst().important());
        assertFalse(declarations.getLast().important());
        assertEquals("f([x],{a;b})", ComponentValue.text(declarations.getFirst().value()));
    }

    @Test
    void atRulesAreSyntaxNodesEvenWhenTheirSemanticsAreUnsupported() {
        var rules = CssSyntax.rules(CssSyntax.components("@media screen { a{x:1} } @keyframes k {to {opacity:1}} @font-face {font-family:x} @import 'a.css';"), true);
        assertEquals(List.of("media", "keyframes", "font-face", "import"), rules.stream().map(CssParser.Rule::atKeyword).toList());
        assertNull(rules.getLast().block());
        assertNotNull(rules.get(2).block());
    }
}
