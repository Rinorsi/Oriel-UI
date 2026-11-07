package dev.oriel.engine.script;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import dev.oriel.shadow.rhino.ScriptableObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Allocation shape checks, deliberately independent of wall-clock performance samples. */
class EcosystemLazyBindingsTest extends EcosystemModeTestSupport {
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static boolean materialized(ScriptableObject object, String name) {
        return Arrays.asList(object.getAllIds()).contains(name);
    }

    private static ScriptableObject prototype(DomBindings dom, String name) throws ReflectiveOperationException {
        return ((HostClass<?>) field(dom, name)).prototype;
    }

    @Test
    void unusedFeaturesDoNotAllocateSlotsOrAnInputClass() throws ReflectiveOperationException {
        Page page = new TestHost().load("<input id=i><div id=d></div>");
        RhinoScriptRuntime rt = (RhinoScriptRuntime) page.doc.scripts();
        for (String name : new String[] {"HTMLInputElement", "HTMLIFrameElement", "SVGElement", "NodeList", "HTMLCollection"}) {
            assertFalse(materialized(rt.global, name), name);
        }
        assertFalse(materialized(prototype(rt.dom, "element"), "namespaceURI"));
        assertFalse(materialized(prototype(rt.dom, "document"), "createElementNS"));
        assertEquals("i", page.eval("document.getElementById('i').id"));
        assertNull(field(rt.dom, "input"), "ordinary input access must not create the new input type");
    }

    @Test
    void eachTypeIsInstalledOnlyWhenThatNameIsRead() {
        Page page = new TestHost().load("");
        RhinoScriptRuntime rt = (RhinoScriptRuntime) page.doc.scripts();
        assertEquals("function", page.eval("typeof HTMLIFrameElement"));
        assertTrue(materialized(rt.global, "HTMLIFrameElement"));
        assertFalse(materialized(rt.global, "HTMLInputElement"));
        assertFalse(materialized(rt.global, "SVGElement"));
    }

    @Test
    void namespaceAndFactoryAreInstalledIndependently() throws ReflectiveOperationException {
        Page page = new TestHost().load("");
        RhinoScriptRuntime rt = (RhinoScriptRuntime) page.doc.scripts();
        assertEquals("http://www.w3.org/1999/xhtml", page.eval("document.body.namespaceURI"));
        assertTrue(materialized(prototype(rt.dom, "element"), "namespaceURI"));
        assertFalse(materialized(prototype(rt.dom, "document"), "createElementNS"));
        assertEquals("true", page.eval("document.createElementNS===document.createElementNS"));
        assertTrue(materialized(prototype(rt.dom, "document"), "createElementNS"));
    }

    @Test
    void existingAndDetachedInputsAcquireTheTypeAfterItsFirstProbe() throws ReflectiveOperationException {
        Page page = new TestHost().load("<input id=i>");
        RhinoScriptRuntime rt = (RhinoScriptRuntime) page.doc.scripts();
        page.run("var saved=document.getElementById('i'),cloned=saved.cloneNode();");
        assertNull(field(rt.dom, "input"));
        assertEquals("true true true", page.eval("[saved instanceof HTMLInputElement,cloned instanceof HTMLInputElement,saved.constructor===HTMLInputElement].join(' ')"));
    }

    @Test
    void existenceChecksStayLazyAndExplicitDescriptorsMaterializeOnlyTheirType() {
        Page page = new TestHost().load("");
        RhinoScriptRuntime rt = (RhinoScriptRuntime) page.doc.scripts();
        assertEquals("true", page.eval("'SVGElement' in window"));
        assertFalse(materialized(rt.global, "SVGElement"));
        assertEquals("true", page.eval("Object.getOwnPropertyDescriptor(window,'SVGElement').value===SVGElement"));
        assertTrue(materialized(rt.global, "SVGElement"));
        assertFalse(materialized(rt.global, "HTMLInputElement"));
    }

    @Test
    void namespaceIsReadOnlyEvenWhenAssignmentIsItsFirstUse() {
        Page page = new TestHost().load("");
        page.run("document.body.namespaceURI='hostile'");
        assertEquals("http://www.w3.org/1999/xhtml", page.eval("document.body.namespaceURI"));
        assertEquals("false", page.eval("Object.prototype.hasOwnProperty.call(document.body,'namespaceURI')"));
    }

    @Test
    void explicitInputPrototypeChangesArePreservedAcrossTypeProbes() {
        Page page = new TestHost().load("<input id=i>");
        page.run("var saved=document.getElementById('i'),before=document.createElement('input');Object.setPrototypeOf(before,null);var ctor=HTMLInputElement;Object.setPrototypeOf(saved,Element.prototype)");
        assertEquals("false false", page.eval("[saved instanceof HTMLInputElement,before instanceof HTMLInputElement].join(' ')"));
        page.run("var detached=document.createElement('input');Object.setPrototypeOf(detached,null)");
        assertEquals("false", page.eval("detached instanceof HTMLInputElement"));
    }

    @Test
    void deletedAndOverriddenNamesDoNotReappear() {
        Page page = new TestHost().load("");
        assertEquals("undefined 7 undefined", page.eval("(function(){delete window.SVGElement;window.HTMLIFrameElement=7;var n=window.HTMLIFrameElement;delete window.HTMLIFrameElement;return [typeof SVGElement,n,typeof HTMLIFrameElement].join(' ')})()"));
    }
}
