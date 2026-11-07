package dev.oriel.engine.dom;

import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttributeIndexWorkTest {
    @Test
    void absentAttributeBucketsDoNotReadEveryIndexedAttribute() {
        String saved = System.getProperty("oriel.selector");
        String cascade = System.getProperty("oriel.cascade");
        try {
            System.setProperty("oriel.selector", "new");
            System.setProperty("oriel.cascade", "new"); // Count production reads, not the both-mode full oracle.
            StringBuilder html = new StringBuilder("<style>");
            for (int i = 0; i < 500; i++) html.append("[data-unused-").append(i).append("]{color:red}");
            Document document = Document.parse(new TestHost(), "test:attribute-index.html",
                    html.append("</style><main></main>").toString());
            CountingElement element = new CountingElement(document);
            document.querySelector("main").appendChild(element);
            document.flushStyle();
            assertEquals(0, element.attributeReads, "absent buckets must not scale with stylesheet size");

            element.setAttribute("data-unused-37", "");
            element.attributeReads = 0;
            document.flushStyle();
            assertEquals(0xFFFF0000, element.baseStyle.color);
            assertTrue(element.attributeReads <= 2, "only the present attribute needs matching");

            element.removeAttribute("data-unused-37");
            element.attributeReads = 0;
            document.flushStyle();
            assertEquals(0, element.attributeReads);
            assertNotEquals(0xFFFF0000, element.baseStyle.color);
            document.close();
        } finally {
            if (saved == null) System.clearProperty("oriel.selector");
            else System.setProperty("oriel.selector", saved);
            if (cascade == null) System.clearProperty("oriel.cascade");
            else System.setProperty("oriel.cascade", cascade);
        }
    }

    private static final class CountingElement extends Element {
        int attributeReads;

        CountingElement(Document document) { super(document, "p"); }

        @Override public String getAttribute(String name) {
            if (name.startsWith("data-unused-")) attributeReads++;
            return super.getAttribute(name);
        }

        @Override public boolean hasAttribute(String name) {
            if (name.startsWith("data-unused-")) attributeReads++;
            return super.hasAttribute(name);
        }
    }
}
