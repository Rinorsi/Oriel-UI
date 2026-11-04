package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.input.TextSelection;

/**
 * A text field for Minecraft formatted text ({@code .o-mcfield} around an {@code input} or {@code textarea}): a bar of
 * the sixteen colours, a custom colour, bold, italic, underline, strikethrough, obfuscated and reset, and a live
 * preview under the field. A button wraps the selection in its code (and {@code §r} after it), or puts the code at
 * the caret; the value keeps the codes ({@link McFormat}), so it is what the game, a sign or a plugin reads.
 *
 * <p>The field's {@code data-json} attribute follows the value as a chat component; {@code data-json-out="#id"} also
 * writes it into that element. {@code data-prefix="&"} writes {@code &} codes instead of {@code §}. The bar and the
 * preview are made when missing ({@code .o-mcbar}, {@code .o-mcpreview}), so markup only needs the field.
 */
public final class McField {
    private final Document doc;
    private final Element box, field, preview;
    private final String prefix;

    private McField(Document doc, Element box, Element field) {
        this.doc = doc;
        this.box = box;
        this.field = field;
        this.prefix = "&".equals(box.getAttribute("data-prefix")) ? "&" : "§";
        Element bar = box.querySelector(".o-mcbar");
        if (bar == null) {
            bar = Dom.html(doc, barMarkup());
            box.insertBefore(bar, box.firstChild());
        }
        Element hex = box.querySelector(".o-mchex");
        if (hex == null) {
            hex = Dom.html(doc, "<div class=\"o-mchex o-hidden\"><span class=\"o-faint\">#</span><input class=\"ore-field\" maxlength=\"6\" "
                    + "placeholder=\"55ffaa\"><i class=\"sw\"></i><div class=\"ore-btn\" tabindex=\"0\">应用</div></div>");
            box.insertBefore(hex, field);
        }
        Element p = box.querySelector(".o-mcpreview");
        if (p == null) {
            p = Dom.el(doc, "div", "o-mcpreview", null);
            box.appendChild(p);
        }
        preview = p;
        // Pressing the bar must not take focus (and the selection) from the field.
        Dom.onMouse(bar, "mousedown", e -> {
            if (Dom.closest(e, "input") == null) e.preventDefault();
        });
        for (Element b : bar.querySelectorAll("[data-mc]")) {
            String code = b.getAttribute("data-mc");
            Shell.activate(b, () -> apply(prefix + code));
        }
        Element hexBox = hex, hexInput = hex.querySelector("input"), hexSwatch = hex.querySelector(".sw");
        Element custom = bar.querySelector(".custom");
        if (custom != null) Shell.activate(custom, () -> {
            hexBox.toggleClass("o-hidden", !hexBox.hasClass("o-hidden"));
            custom.toggleClass("on", !hexBox.hasClass("o-hidden"));
        });
        Runnable applyHex = () -> {
            String v = hexInput.value().strip().replace("#", "");
            if (!v.matches("[0-9a-fA-F]{6}")) {
                hexInput.addClass("bad");
                return;
            }
            hexInput.removeClass("bad");
            apply(prefix + "#" + v.toLowerCase(java.util.Locale.ROOT));
        };
        Dom.on(hexInput, "input", e -> {
            String v = hexInput.value().strip().replace("#", "");
            hexInput.toggleClass("bad", !v.isEmpty() && !v.matches("[0-9a-fA-F]{0,6}"));
            if (v.matches("[0-9a-fA-F]{6}")) hexSwatch.setAttribute("style", "background-color: #" + v);
        });
        Dom.onKey(hexInput, e -> {
            if (e.key.equals("Enter")) applyHex.run();
        });
        Shell.activate(hex.querySelector(".ore-btn"), applyHex);
        Dom.on(field, "input", e -> update());
        update();
    }

    /** Wires every {@code .o-mcfield} on the page. */
    public static void attach(Document doc) {
        for (Element box : doc.querySelectorAll(".o-mcfield")) {
            Element field = box.querySelector("textarea, input");
            if (field != null && box.querySelector(".o-mcbar .sw") == null) new McField(doc, box, field);
        }
    }

    /** Wraps the selection in {@code token} (then a reset), or puts it at the caret; the field keeps focus. */
    private void apply(String token) {
        String value = field.value();
        int start = TextSelection.start(field), end = TextSelection.end(field);
        field.focus();
        boolean reset = token.equals(prefix + "r");
        if (start < end && reset) {
            // Clearing a selection: its codes go, and it starts unformatted.
            String plain = McFormat.strip(value.substring(start, end));
            TextSelection.replace(field, start, end, token + plain);
            TextSelection.select(field, start + token.length(), start + token.length() + plain.length());
        } else if (start < end) {
            String inner = value.substring(start, end);
            TextSelection.replace(field, start, end, token + inner + prefix + "r");
            TextSelection.select(field, start + token.length(), start + token.length() + inner.length());
        } else {
            TextSelection.replace(field, start, end, token);
        }
        update();
    }

    private void update() {
        String value = field.value();
        McFormat.render(doc, preview, value.isEmpty() ? "" : value);
        preview.toggleClass("empty", McFormat.strip(value).isBlank());
        String json = McFormat.json(value);
        box.setAttribute("data-json", json);
        String out = box.getAttribute("data-json-out");
        Element target = out == null ? null : doc.querySelector(out);
        if (target != null) Dom.text(target, json);
    }

    private static String barMarkup() {
        StringBuilder s = new StringBuilder("<div class=\"o-mcbar\"><div class=\"o-mcswatches\">");
        for (int i = 0; i < 16; i++) {
            char code = "0123456789abcdef".charAt(i);
            s.append("<i class=\"sw\" data-mc=\"").append(code).append("\" title=\"").append(McFormat.COLOR_LABELS[i]).append(" §").append(code)
                    .append("\" style=\"background-color: ").append(McFormat.hex(McFormat.COLORS[i])).append("\"></i>");
        }
        s.append("<i class=\"sw custom\" title=\"自定义颜色 §#rrggbb\"><img class=\"o-ico\" src=\"oriel:textures/icons/palette.png\"></i></div>");
        s.append("<div class=\"o-mcfmts\">")
                .append("<span data-mc=\"l\" title=\"粗体 §l\" style=\"font-weight: bold\">B</span>")
                .append("<span data-mc=\"o\" title=\"斜体 §o\" style=\"font-style: italic\">I</span>")
                .append("<span data-mc=\"n\" title=\"下划线 §n\" style=\"text-decoration: underline\">U</span>")
                .append("<span data-mc=\"m\" title=\"删除线 §m\" style=\"text-decoration: line-through\">S</span>")
                .append("<span data-mc=\"k\" title=\"乱码 §k\" style=\"-mc-obfuscated: on\">K</span>")
                .append("<span class=\"reset\" data-mc=\"r\" title=\"清除格式 §r\">清除</span></div></div>");
        return s.toString();
    }
}
