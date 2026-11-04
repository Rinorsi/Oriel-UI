package dev.oriel.engine.html;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlSyntaxMigrationTest {
    private static Document parse(String markup) {
        Document document = Document.create(new TestHost(), "test:malformed.html");
        document.removeAllChildren();
        HtmlTreeBuilder.document(document, markup, (offset, message) -> {
            var at = dev.oriel.engine.parse.SourceLocation.at(markup, offset);
            document.diagnostics().report(new dev.oriel.engine.diag.Diagnostic(
                    dev.oriel.engine.diag.Diagnostic.Severity.WARNING, "html-syntax", document.url(),
                    at.line(), at.column(), message, ""));
        });
        return document;
    }

    @Test
    void rawTextEndNameRequiresAnHtmlDelimiter() {
        Document doc = parse("<script>let x='</scripted>'; keep();</script><p>after</p>");
        assertEquals("let x='</scripted>'; keep();", doc.head().children().getFirst().textContent());
        assertEquals("after", doc.body().textContent());
    }

    @Test
    void duplicateAttributeUsesFirstValueAndReportsOriginalLineAndColumn() {
        Document doc = parse("<div\r\n id=a id=b>x</div>");
        assertEquals("a", doc.body().children().getFirst().id());
        var diagnostic = doc.diagnostics().all().getFirst();
        assertEquals(2, diagnostic.line()); assertEquals(7, diagnostic.column());
        assertTrue(diagnostic.format().startsWith("test:malformed.html:2:7:"));
    }

    @Test
    void commentsJoinTextAndMalformedInputAlwaysMakesProgress() {
        Document doc = parse("<div>a<!-- comment -->b</wrong>c</div><input value='unfinished");
        assertEquals("abc", doc.body().children().getFirst().textContent());
        assertTrue(doc.diagnostics().all().size() >= 2);
        assertNotNull(doc.body().children().getLast());
    }

    @Test
    void optionalEndsAreScopedToTheirOwnList() {
        Document doc = parse("<ul><li>a<ul><li>b<li>c</ul><li>d</ul>");
        assertEquals(2, doc.body().children().getFirst().children().size());
        assertEquals(2, doc.body().children().getFirst().children().getFirst().children().getFirst().children().size());
    }

    @Test
    void surrogateAndOutOfRangeReferencesRecoverWithDiagnostics() {
        Document doc = parse("<p>&#xD800; &#0; &#99999999999; &unsupported;</p>");
        assertEquals("� � � &unsupported;", doc.body().textContent());
        assertEquals(4, doc.diagnostics().all().size());
    }
}
