package dev.oriel.engine.diag;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Text;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranslationsTest {
    private static final class Host extends TestHost {
        String locale = "zh_cn";
        @Override public String language() { return locale; }
        @Override public String translation(String key) {
            return translation(locale, key);
        }
        @Override public String translation(String language, String key) {
            return switch (key) {
                case "test.label" -> language.equals("zh_cn") ? "你好" : "Hello";
                case "test.count" -> language.equals("zh_cn") ? "%s 个" : "%s items";
                case "test.zh_only" -> language.equals("zh_cn") ? "中文回退" : null;
                case "test.apples.one" -> language.equals("zh_cn") ? "%s 个苹果" : "%s apple";
                case "test.apples.other" -> language.equals("zh_cn") ? "%s 个苹果" : "%s apples";
                default -> null;
            };
        }
    }

    @Test void preservesNestedElementsAndWhitespaceWhileChangingLanguages() {
        Host host = new Host();
        var page = host.load("<button id=b data-i18n='test.label' data-i18n-title='test.label'><i></i> 你好 </button>");
        Element button = page.doc.getElementById("b"), icon = button.children().getFirst();
        Text text = (Text) button.childAt(1);
        assertEquals(" 你好 ", text.data());
        host.locale = "en_us";
        assertTrue(page.doc.needsFrame(16));
        page.frame();
        assertSame(icon, button.childAt(0));
        assertSame(text, button.childAt(1));
        assertEquals(" Hello ", text.data());
        assertEquals("Hello", button.getAttribute("title"));
        host.locale = "zh_cn"; page.frame();
        assertEquals(" 你好 ", text.data());
    }

    @Test void missingKeysAreVisibleDiagnosticsAndNeverSilentKeys() {
        var page = new Host().load("<span data-i18n='test.absent'>Text</span>");
        page.frame();
        assertEquals(1, page.doc.diagnostics().all().stream().filter(d -> d.code().equals("missing-translation")).count());
        assertEquals("test.absent", page.doc.querySelector("span").textContent());
        assertTrue(page.doc.diagnostics().all().getFirst().message().contains("test.absent"));
    }

    @Test void formattingAndControllerOwnedValuesRemainIndependent() {
        Host host = new Host();
        var page = host.load("<span id=s data-i18n='test.label'>你好</span>");
        assertEquals("3 个", page.doc.messages().text("test.count", 3));
        page.doc.getElementById("s").setTextContent("User text");
        host.locale = "en_us"; page.frame();
        assertEquals("User text", page.doc.getElementById("s").textContent());
        assertEquals("3 items", page.doc.messages().text("test.count", 3));
    }

    @Test void emptyBindingsAlsoWorkInProgrammaticallyCreatedDocuments() {
        Host host = new Host();
        Document doc = Document.create(host, "test:created.html");
        Element label = doc.createElement("span");
        label.setAttribute("data-i18n", "test.label");
        doc.body().appendChild(label);
        doc.frame(0);
        assertEquals("你好", label.textContent());
        host.locale = "en_us"; doc.frame(16);
        assertEquals("Hello", label.textContent());
    }

    @Test void scriptAndMcTextMissingKeysAlsoReachDocumentDiagnostics() {
        var page = new Host().load("<script>var translated = oriel.t('test.absent')</script>"
                + "<mc-text key='test.absent'></mc-text>");
        assertEquals(1, page.doc.diagnostics().all().stream().filter(d -> d.code().equals("missing-translation")).count());
        assertEquals("test.absent", page.doc.querySelector("mc-text").textContent(),
                "legacy calls keep their return value but now report the missing key");
    }

    @Test void mcTextAlsoFollowsLanguageChangesWithoutAReload() {
        Host host = new Host();
        var page = host.load("<mc-text key='test.label'></mc-text>");
        assertEquals("你好", page.doc.querySelector("mc-text").textContent());
        host.locale = "en_us"; page.frame();
        assertEquals("Hello", page.doc.querySelector("mc-text").textContent());
    }

    @Test void documentsDoNotShareAGlobalLanguage() {
        Host chinese = new Host(), english = new Host();
        english.locale = "en_us";
        var zh = chinese.load("<span data-i18n='test.label'>Text</span>");
        var en = english.load("<span data-i18n='test.label'>Text</span>");
        assertEquals("你好", zh.doc.querySelector("span").textContent());
        assertEquals("Hello", en.doc.querySelector("span").textContent());
        en.frame(); zh.frame();
        assertEquals("你好", zh.doc.messages().text("test.label"));
    }

    @Test void missingLocaleFallsBackToChineseAndLogsOnce() {
        Host host = new Host(); host.locale = "en_us";
        var page = host.load("<span data-i18n='test.zh_only'>test.zh_only</span>");
        assertEquals("中文回退", page.doc.querySelector("span").textContent());
        page.doc.messages().text("test.zh_only"); page.frame();
        assertEquals(1, page.doc.diagnostics().all().size());
        assertTrue(page.doc.diagnostics().all().getFirst().message().contains("using zh_cn"));
    }

    @Test void cssContentUsesDocumentLocaleAndAttributeArguments() {
        Host host = new Host();
        var page = host.load("<style>#a::before { content:i18n('test.count',attr(data-count)) }</style><div id=a data-count=3></div>");
        assertEquals("3 个", page.doc.getElementById("a").beforeStyle.content);
        host.locale = "en_us"; page.frame();
        assertEquals("3 items", page.doc.getElementById("a").beforeStyle.content);
        page.doc.getElementById("a").setAttribute("data-count", "4"); page.frame();
        assertEquals("4 items", page.doc.getElementById("a").beforeStyle.content);
    }

    @Test void pluralMessagesUseStableOneAndOtherKeys() {
        Host host = new Host();
        var page = host.load("<div></div>");
        assertEquals("1 个苹果", page.doc.messages().plural("test.apples", 1));
        host.locale = "en_us";
        assertEquals("1 apple", page.doc.messages().plural("test.apples", 1));
        assertEquals("2 apples", page.doc.messages().plural("test.apples", 2));
    }
}
