package dev.oriel.preview;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The two source checks: a function declared twice, and a class hidden for good that a script writes into the page. */
class SourceLintTest {
    @TempDir Path dir;

    private List<Diagnostic> check(String html, String code) throws Exception {
        try (MinecraftAssets assets = MinecraftAssets.open(List.of(dir), Optional.empty())) {
            PreviewHost host = new PreviewHost(assets, new MinecraftFont(assets));
            host.quiet(true);
            Document doc = Document.parse(host, dir.resolve("page.html").toString(), html);
            try {
                doc.frame(0);
                return PageLint.run(doc, host).stream().filter(d -> d.code().equals(code)).toList();
            } finally { doc.close(); }
        }
    }

    @Test void aFunctionDeclaredTwiceAcrossScriptsIsReportedOnce() throws Exception {
        Files.writeString(dir.resolve("a.js"), "function toast(m) { return 1; }\n");
        String html = "<script src='a.js'></script><script>\nfunction toast(m) { return 2; }\nfunction other() {}\n</script>";
        var found = check(html, "duplicate-global-function");
        assertEquals(1, found.size());
        assertTrue(found.getFirst().message().contains("toast"));
        assertTrue(found.getFirst().suggestion().contains("toast"));
    }

    @Test void distinctNamesAndNestedFunctionsAreFine() throws Exception {
        String html = "<script>\nfunction a() {}\nfunction b() {\n  function a() {}\n}\nconst c = function () {};\n</script>";
        assertTrue(check(html, "duplicate-global-function").isEmpty());
    }

    @Test void aHiddenClassTheScriptCreatesIsReported() throws Exception {
        String html = "<style>.k-note { display:none; }</style><div id=box></div>"
                + "<script>document.getElementById('box').innerHTML = '<div class=\"k-note\">careful</div>';</script>";
        var found = check(html, "hidden-class-created-by-script");
        assertEquals(1, found.size());
        assertTrue(found.getFirst().message().contains(".k-note"));
    }

    @Test void classNameAssignmentCountsToo() throws Exception {
        String html = "<style>.note{display:none}</style><script>var e = document.createElement('div'); e.className = 'row note';</script>";
        assertEquals(1, check(html, "hidden-class-created-by-script").size());
    }

    @Test void aClassThatSomeRuleShowsOrThatIsNamedToHideIsNotReported() throws Exception {
        String shown = "<style>.k-note{display:none}.k-note.on{display:block}</style><script>x.innerHTML='<i class=\"k-note\">';</script>";
        assertTrue(check(shown, "hidden-class-created-by-script").isEmpty());
        String named = "<style>.hidden{display:none}</style><script>x.innerHTML='<i class=\"hidden\">';</script>";
        assertTrue(check(named, "hidden-class-created-by-script").isEmpty());
        String toggled = "<style>.note{display:none}</style><script>el.classList.add('note');</script>";
        assertTrue(check(toggled, "hidden-class-created-by-script").isEmpty());
    }
}
