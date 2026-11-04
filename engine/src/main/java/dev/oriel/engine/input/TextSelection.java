package dev.oriel.engine.input;

import dev.oriel.engine.dom.Element;

/**
 * The caret and selection of a text field ({@code <input>} of a text type, {@code <textarea>}), for page code: what
 * {@code selectionStart}, {@code selectionEnd}, {@code setSelectionRange} and {@code setRangeText} are in a browser.
 * Offsets are UTF-16 units into the value. On anything that is not a text field, reads give 0 and writes do nothing.
 */
public final class TextSelection {
    private TextSelection() {}

    /** Where the selection starts (the caret when nothing is selected). */
    public static int start(Element field) {
        TextField f = TextField.ifText(field);
        return f == null ? 0 : f.editor.selectionStart();
    }

    /** Where the selection ends (the caret when nothing is selected). */
    public static int end(Element field) {
        TextField f = TextField.ifText(field);
        return f == null ? 0 : f.editor.selectionEnd();
    }

    /** Selects {@code [start, end)}; equal offsets put the caret there. */
    public static void select(Element field, int start, int end) {
        TextField f = TextField.ifText(field);
        if (f != null) f.select(start, end);
    }

    /**
     * Replaces {@code [start, end)} with {@code text} as the user typing it would: one undo step, {@code beforeinput}
     * and {@code input} events, the caret after the new text.
     */
    public static void replace(Element field, int start, int end, String text) {
        TextField f = TextField.ifText(field);
        if (f != null) f.replace(start, end, text);
    }
}
