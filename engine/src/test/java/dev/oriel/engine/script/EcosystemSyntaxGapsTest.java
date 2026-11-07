package dev.oriel.engine.script;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Minimal current parser gaps, retained separately from DOM/library success scenarios. */
class EcosystemSyntaxGapsTest {
    @Test
    void reportsUnsupportedSyntaxRatherThanSuggestingMissingDomApis() {
        Page page = new TestHost().load("");
        page.run("function parseProbe(source){try{Function(source);return 'parsed'}catch(e){return e.name}}");
        assertEquals("SyntaxError", page.eval("parseProbe('return (...args) => args.length')"));
        assertEquals("SyntaxError", page.eval("parseProbe('function* g(){var x; (x=1, yield x)}')"));
        assertEquals("SyntaxError", page.eval("parseProbe('var key=1; var {[key]: value}={1:2}')"));
        assertEquals("SyntaxError", page.eval("parseProbe('class A { #value = 1; }')"));
        assertEquals("SyntaxError", page.eval("parseProbe('var {a,...rest} = {a:1,b:2}')"));
    }
}
