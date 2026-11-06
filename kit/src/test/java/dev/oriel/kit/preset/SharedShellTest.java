package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A page that is only HTML opts into the library's shared behaviour with data-oriel-shell, and gets it without any Java. */
class SharedShellTest {
    @Test void aPlainPageThatOptsInGetsTheShellAndOneThatDoesNotDoesNot() {
        OrielKit.register();
        KitHost host = new KitHost();
        Document plain = Document.parse(host, "test:plain.html", "<html><body><div id=x>x</div></body></html>");
        assertNull(plain.documentElement().getAttribute("data-theme"));
        Document opted = Document.parse(host, "test:opted.html", "<html data-oriel-shell><body><div id=x class=m-ripple>x</div></body></html>");
        assertEquals("dark", opted.documentElement().getAttribute("data-theme"));
        plain.close();
        opted.close();
    }
}
