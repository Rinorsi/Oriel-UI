package dev.oriel.engine.security;

import dev.oriel.engine.Limits;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adversarial page inputs; dangerous requests must fail before reaching the host. */
class ScriptRedTeamTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "java.lang.Class.forName('java.lang.Runtime')",
            "Packages.java.lang.Runtime.getRuntime()",
            "Java.type('java.lang.ProcessBuilder')",
            "javax.script.ScriptEngineManager()",
            "document.getClass().getClassLoader()",
            "document.body.getClass().getDeclaredMethods()",
            "oriel.send.getClass().forName('java.lang.Runtime')",
            "Object.getPrototypeOf(document).getClass()",
            "new java.lang.Thread(function () {})",
            "new Worker('hostile.js')",
            "new SharedWorker('hostile.js')",
            "new ProcessBuilder('oriel-red-team-never-execute')",
            "Runtime.getRuntime()",
            "Function('return java.lang.Runtime.getRuntime()')()",
            "eval('Packages.java.lang.Class')",
            "document.constructor.constructor('return Packages')()"
    })
    void classReflectionProcessAndThreadAccessAreRejected(String attack) {
        assertRejected(new TestHost().recordErrors().load(""), attack);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "HTMLInputElement.constructor('return Packages')()",
            "HTMLIFrameElement.prototype.getClass().getClassLoader()",
            "SVGElement.constructor('return java.lang.Runtime')()",
            "document.createElementNS('https://hostile.invalid', 'iframe')",
            "document.createElementNS('http://www.w3.org/2000/svg', 'script')"
    })
    void optionalEcosystemEntriesRemainSandboxed(String attack) {
        String previous = System.getProperty("oriel.ecosystem");
        Page page;
        try {
            System.setProperty("oriel.ecosystem", "new");
            page = new TestHost().recordErrors().load("");
        } finally {
            if (previous == null) System.clearProperty("oriel.ecosystem");
            else System.setProperty("oriel.ecosystem", previous);
        }
        assertRejected(page, attack);
    }

    private static void assertRejected(Page page, String attack) {
        page.run(attack);
        assertFalse(page.errors().isEmpty(), attack);
        assertTrue(page.host.sent.isEmpty());
        assertTrue(page.host.navigations.isEmpty());
        page.run("oriel.send('alive', true)");
        assertEquals("alive", page.host.sent.getFirst()[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "while (true) {}",
            "try { while (true) {} } catch (e) { oriel.send('caught', true) } finally { oriel.send('finally', true) }",
            "eval('while (true) {}')",
            "Function('while (true) {}')()",
            "Promise.resolve().then(function () { while (true) {} })",
            "queueMicrotask(function again() { queueMicrotask(again) })",
            "(async function () { await 0; while (true) {} })()"
    })
    void entryBudgetCannotBeCaughtOrEvadedByGeneratedOrAsyncCode(String attack) {
        Page page = new TestHost().recordErrors()
                .limits(Limits.DEFAULTS.with("instructionBudget", 100_000)).load("");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> page.run(attack));
        assertTrue(page.errors().contains("Script stopped"), page.errors());
        assertTrue(page.host.sent.isEmpty(), "budget termination must skip catch and finally");
    }

    @Test
    void jsonInputIsConvertedOnceBeforeDepthValidation() {
        Page page = new TestHost().load("");
        assertEquals("true:1", page.eval("""
                (function () {
                  var conversions = 0;
                  var input = {toString: function () {
                    return ++conversions === 1 ? '0' : '['.repeat(513) + '0' + ']'.repeat(513);
                  }};
                  return (JSON.parse(input) === 0) + ':' + conversions;
                })()
                """));
    }

    @Test
    void overDeepJsonIsRejectedWithDiagnosticEvenWhenCaught() {
        Page page = new TestHost().load("");
        assertEquals("SyntaxError", page.eval("""
                (function () {
                  try { JSON.parse('['.repeat(513) + '0' + ']'.repeat(513)); }
                  catch (e) { return e.name; }
                  return 'accepted';
                })()
                """));
        assertTrue(page.doc.diagnostics().all().stream().anyMatch(d ->
                d.code().equals("script-json-depth") && d.source().equals("test:eval.js") && d.line() > 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "JSON.parse('42') === 42",
            "JSON.parse({toString: function () { return '42' }}) === 42",
            "JSON.parse('[1]', function (k, v) { return String(k) === '0' ? v + 1 : v })[0] === 2",
            "(function () { try { JSON.parse() } catch (e) { return e.name === 'SyntaxError' } })()"
    })
    void ordinaryJsonAndReviversAreUnchanged(String expression) {
        assertEquals("true", new TestHost().load("").eval(expression));
    }

    @Test
    void pushedDataCannotInjectJavaAccessOrPolluteAnotherPage() {
        TestHost host = new TestHost().recordErrors();
        Page page = host.load("<script>oriel.on('data', d => oriel.send('echo', d.text))</script>");
        page.doc.receive("data", "{\"__proto__\":{\"admin\":true},\"text\":\"java.lang.Runtime.getRuntime()\"}");
        assertEquals("\"java.lang.Runtime.getRuntime()\"", host.sent.getFirst()[1]);
        assertEquals("undefined undefined", page.eval("typeof ({}).admin + ' ' + typeof java"));
        page.run("Object.prototype.redTeam = true; window.pageSecret = 42");
        Page next = new TestHost().load("");
        assertEquals("undefined undefined", next.eval("typeof ({}).redTeam + ' ' + typeof pageSecret"));
    }
}
