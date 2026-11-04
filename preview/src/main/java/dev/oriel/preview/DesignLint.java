package dev.oriel.preview;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.dom.Text;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Overflow;
import dev.oriel.engine.style.TextOverflow;
import dev.oriel.engine.style.Visibility;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Design rules ({@code checkUi}'s {@code design:} lines), the mistakes that make a screen look cheap, from
 * docs/zh/design.md: a tile inside a tile, type off the 8/16px scale, text the window or its box cuts off, text
 * too faint to read on a solid background, and so many glowing things that nothing stands out. Warnings, not
 * failures: a page can have a reason. Runs on a laid-out document.
 */
final class DesignLint {
    static final Set<String> GLOW = Set.of("fx-breathe", "fx-sweep", "fx-glint", "fx-glow", "o-frame", "m-attention");
    static final int MAX_GLOWS = 8;
    static final double MIN_CONTRAST = 3.0;

    private DesignLint() {}

    static List<String> run(Document doc) {
        Set<String> out = new LinkedHashSet<>();
        float vw = doc.viewportWidth(), vh = doc.viewportHeight();
        int glows = 0;
        for (Element e : doc.querySelectorAll("*")) {
            ComputedStyle s = e.computedStyle();
            if (s == null || e.box == null || !shown(e)) continue;
            String where = describe(e);
            if (e.hasClass("o-tile") && insideTile(e)) out.add("块里套块（层级只有底/面/浮三层）: " + where);
            for (String g : GLOW) if (e.hasClass(g)) {
                glows++;
                break;
            }
            String text = ownText(e);
            if (text.isEmpty()) continue;
            if (s.fontSize != 8 && s.fontSize != 16) out.add("字号 " + fmt(s.fontSize) + "px 不在 8/16 阶梯上（像素字会糊）: " + where);
            float[] r = e.getBoundingClientRect();
            if (r[0] + r[2] > vw + 0.5f || r[1] + r[3] > vh + 0.5f || r[0] < -0.5f || r[1] < -0.5f) {
                if (!inScroller(e)) out.add("文字超出窗口: " + where + " 「" + clip(text) + "」");
            }
            if ((s.overflowX == Overflow.HIDDEN || s.overflowX == Overflow.CLIP) && s.textOverflow != TextOverflow.ELLIPSIS
                    && e.box.scrollWidth > e.box.paddingBoxWidth() + 1) {
                out.add("文字被截断（加 text-overflow: ellipsis 或放宽）: " + where + " 「" + clip(text) + "」");
            }
            Integer bg = background(e);
            if (bg != null) {
                double c = contrast(over(s.color, bg), bg);
                if (c < MIN_CONTRAST) out.add("对比度 " + fmt((float) c) + ":1 太低（至少 3:1）: " + where + " 「" + clip(text) + "」");
            }
        }
        if (glows > MAX_GLOWS) out.add("发光/动效元素 " + glows + " 个，超过 " + MAX_GLOWS + " 个就没有视觉重点了");
        return new ArrayList<>(out);
    }

    private static boolean shown(Element e) {
        for (Element p = e; p != null; p = p.parentElement()) {
            ComputedStyle s = p.computedStyle();
            if (s == null || s.visibility != Visibility.VISIBLE || s.opacity < 0.05f) return false;
        }
        return true;
    }

    private static boolean insideTile(Element e) {
        for (Element p = e.parentElement(); p != null; p = p.parentElement()) if (p.hasClass("o-tile")) return true;
        return false;
    }

    private static boolean inScroller(Element e) {
        for (Element p = e.parentElement(); p != null; p = p.parentElement()) {
            ComputedStyle s = p.computedStyle();
            if (s != null && (s.overflowY == Overflow.AUTO || s.overflowY == Overflow.SCROLL || s.overflowX == Overflow.AUTO
                    || s.overflowX == Overflow.SCROLL)) return true;
        }
        return false;
    }

    /** The element's own text (direct text children), trimmed. */
    private static String ownText(Element e) {
        StringBuilder b = new StringBuilder();
        for (Node n : e.childNodes()) if (n instanceof Text t) b.append(t.textContent());
        return b.toString().trim();
    }

    /**
     * The solid colour the text sits on: the nearest ancestor (or itself) with an opaque background colour, or null
     * when an image, gradient or texture is in the way (then contrast cannot be judged from styles).
     */
    private static Integer background(Element e) {
        for (Element p = e; p != null; p = p.parentElement()) {
            ComputedStyle s = p.computedStyle();
            if (s == null) return null;
            if (!s.backgroundLayers.isEmpty() || s.borderImageSource != dev.oriel.engine.style.BorderImage.Source.NONE) return null;
            if ((s.backgroundColor >>> 24) >= 230) return s.backgroundColor | 0xff000000;
            if ((s.backgroundColor >>> 24) > 20) return null;
        }
        return null;
    }

    private static int over(int fg, int bg) {
        float a = (fg >>> 24) / 255f;
        int r = Math.round(((fg >> 16) & 255) * a + ((bg >> 16) & 255) * (1 - a));
        int g = Math.round(((fg >> 8) & 255) * a + ((bg >> 8) & 255) * (1 - a));
        int b = Math.round((fg & 255) * a + (bg & 255) * (1 - a));
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int c) {
        return 0.2126 * channel((c >> 16) & 255) + 0.7152 * channel((c >> 8) & 255) + 0.0722 * channel(c & 255);
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static String describe(Element e) {
        StringBuilder b = new StringBuilder(e.tagName());
        String id = e.getAttribute("id"), cls = e.getAttribute("class");
        if (id != null) b.append('#').append(id);
        if (cls != null && !cls.isBlank()) b.append('.').append(String.join(".", cls.trim().split("\\s+")));
        return b.toString();
    }

    private static String clip(String t) {
        return t.length() > 16 ? t.substring(0, 16) + "…" : t;
    }

    private static String fmt(float v) {
        return v == Math.round(v) ? Integer.toString(Math.round(v)) : String.format("%.1f", v);
    }
}
