package dev.oriel.engine.input;

import dev.oriel.engine.dom.Element;

/** Input exclusion is separate from template contents (which are not styled or queried). */
public final class InputEligibility {
    private InputEligibility() {}
    public static boolean inert(Element element) {
        for (Element e = element; e != null; e = e.parentElement()) if (e.hasAttribute("inert")) return true;
        return false;
    }
}
