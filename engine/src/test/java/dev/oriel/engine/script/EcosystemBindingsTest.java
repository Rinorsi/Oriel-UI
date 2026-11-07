package dev.oriel.engine.script;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Narrow browser probes used by React, Preact and anime; not a browser emulation contract. */
class EcosystemBindingsTest extends EcosystemModeTestSupport {
    @Test
    void defaultModeRetainsTheOriginalBindings() {
        System.clearProperty("oriel.ecosystem");
        Page page = new TestHost().load("<input id=i>");
        assertEquals("undefined undefined undefined true", page.eval("""
                [typeof HTMLInputElement, typeof document.createElementNS,
                  typeof document.body.namespaceURI,
                  document.getElementById('i') instanceof Element].join(' ')
                """));
    }

    @Test
    void htmlNamespaceCreatesOrdinaryTypedElements() {
        Page page = new TestHost().load("");
        assertEquals("true true true http://www.w3.org/1999/xhtml", page.eval("""
                (function () {
                  var input = document.createElementNS('http://www.w3.org/1999/xhtml', 'input');
                  return [input instanceof HTMLInputElement, input instanceof HTMLElement,
                    input instanceof Node, input.namespaceURI].join(' ');
                })()
                """));
        assertEquals("div", page.eval("document.createElementNS(null, 'div').localName"));
    }

    @Test
    void typeProbesDoNotMisidentifyOrdinaryElementsOrArrays() {
        Page page = new TestHost().load("<input id=i><div id=d></div>");
        assertEquals("false false false false false true true", page.eval("""
                [document.body instanceof HTMLIFrameElement, document.body instanceof SVGElement,
                  document.body instanceof HTMLInputElement, [] instanceof NodeList,
                  [] instanceof HTMLCollection, document.getElementById('i') instanceof HTMLInputElement,
                  Array.isArray(document.querySelectorAll('div'))].join(' ')
                """));
        assertEquals("true", page.eval("document.getElementById('i').cloneNode() instanceof HTMLInputElement"));
        assertEquals("true true true", page.eval("[HTMLInputElement===HTMLInputElement, SVGElement===SVGElement, NodeList===NodeList].join(' ')"));
    }

    @Test
    void unsupportedNamespacesAndConstructorsFailClosed() {
        Page page = new TestHost().load("");
        assertEquals("TypeError", page.eval("""
                (function () { try { document.createElementNS('http://www.w3.org/2000/svg', 'svg'); }
                  catch (e) { return e.name; } })()
                """));
        for (String name : new String[] {"HTMLIFrameElement", "HTMLInputElement", "SVGElement", "NodeList", "HTMLCollection"}) {
            assertEquals("TypeError: Illegal constructor", page.eval(
                    "(function () { try { new " + name + "(); } catch (e) { return e; } })()"));
            assertEquals("undefined", page.eval(name + ".getClass"));
        }
    }
}
