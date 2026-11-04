package dev.oriel.engine.css;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Prop;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generates {@code docs/zh/css-reference.md} from the engine itself, and fails when the committed file is out of date.
 *
 * <p>Nothing in the tables is typed by hand: which properties exist, what they inherit and whether they animate come
 * from {@link Prop}; which keywords and value types each property takes is found by feeding candidates to the real
 * parsers; selectors, at-rules, units and functions are probed the same way. So the page cannot drift from the code.
 * After changing CSS support, regenerate it with {@code ORIEL_UPDATE_DOCS=1 ./gradlew :engine:test --tests '*CssReferenceTest'}.
 */
class CssReferenceTest {
    private static final Path DOC = Path.of(System.getProperty("user.dir")).resolveSibling("docs").resolve("zh").resolve("css-reference.md");

    // ---- Candidates ----

    /** Keywords tried on every property. The CSS-wide keywords are left out: every property takes them. */
    private static final List<String> KEYWORDS = List.of(
            "auto", "none", "normal", "initial-letter", "inherit", "block", "inline", "inline-block", "flex", "inline-flex",
            "grid", "inline-grid", "contents", "table", "list-item", "flow-root", "static", "relative", "absolute", "fixed",
            "sticky", "visible", "hidden", "scroll", "clip", "collapse", "content-box", "border-box", "padding-box",
            "min-content", "max-content", "fit-content", "stretch", "start", "end", "center", "left", "right", "top", "bottom",
            "flex-start", "flex-end", "self-start", "self-end", "baseline", "first baseline", "last baseline", "space-between",
            "space-around", "space-evenly", "safe", "unsafe", "row", "row-reverse", "column", "column-reverse", "dense",
            "nowrap", "wrap", "wrap-reverse", "solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset",
            "thin", "medium", "thick", "transparent", "currentcolor", "bold", "bolder", "lighter", "italic", "oblique",
            "capitalize", "uppercase", "lowercase", "full-width", "justify", "justify-all", "match-parent", "pre", "pre-wrap",
            "pre-line", "break-spaces", "break-all", "keep-all", "break-word", "anywhere", "ellipsis", "underline", "overline",
            "line-through", "blink", "disc", "circle", "square", "decimal", "default", "pointer", "text", "wait", "help",
            "move", "not-allowed", "grab", "grabbing", "crosshair", "progress", "cell", "copy", "alias", "zoom-in", "zoom-out",
            "col-resize", "row-resize", "e-resize", "n-resize", "ne-resize", "nw-resize", "ns-resize", "ew-resize",
            "nesw-resize", "nwse-resize", "all-scroll", "context-menu", "no-drop", "vertical-text", "all", "fill", "contain",
            "cover", "scale-down", "repeat", "repeat-x", "repeat-y", "no-repeat", "space", "round", "smooth", "instant",
            "pixelated", "crisp-edges", "optimizeSpeed", "optimizeQuality", "geometricPrecision", "crispEdges", "isolate",
            "multiply", "screen", "overlay", "darken", "lighten", "color-dodge", "color-burn", "hard-light", "soft-light",
            "difference", "exclusion", "hue", "saturation", "color", "luminosity", "plus-lighter", "add", "subtract",
            "intersect", "exclude", "alpha", "luminance", "match-source", "text-top", "text-bottom", "super", "sub", "middle",
            "ease", "ease-in", "ease-out", "ease-in-out", "linear", "step-start", "step-end", "infinite", "alternate",
            "alternate-reverse", "reverse", "forwards", "backwards", "both", "running", "paused", "closest-side",
            "farthest-side", "closest-corner", "farthest-corner", "thin", "on", "off", "inside", "outside", "pan-x", "pan-y",
            "manipulation", "ltr", "rtl", "horizontal-tb", "vertical-rl", "vertical-lr", "dark", "light", "high", "low",
            "stable", "both-edges", "mandatory", "proximity", "x", "y", "block", "optional", "swap", "fallback", "from-font");

    /** Value kinds tried on every property: label and a sample the parser must take for the label to be listed. */
    private static final Map<String, String> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("长度", "10px");
        TYPES.put("百分比", "50%");
        TYPES.put("数字", "1.5");
        TYPES.put("整数", "2");
        TYPES.put("颜色", "#f80");
        TYPES.put("时间", "1s");
        TYPES.put("角度", "45deg");
        TYPES.put("字符串", "\"x\"");
        TYPES.put("图片", "url(a.png)");
        TYPES.put("渐变", "linear-gradient(red, blue)");
    }

    /**
     * Properties whose value is a compound of several parts, with one example each. The example must parse, or the
     * test fails, so the page never shows a form the engine rejects.
     */
    private static final Map<String, String> EXAMPLES = new LinkedHashMap<>();

    static {
        EXAMPLES.put("transform", "translate(10px, 4px) rotate(8deg) scale(1.1)");
        EXAMPLES.put("transition", "transform .2s ease-out, opacity .2s");
        EXAMPLES.put("animation", "pop .3s ease-out both");
        EXAMPLES.put("box-shadow", "0 2px 6px rgba(0,0,0,.4), inset 0 0 0 1px #fff3");
        EXAMPLES.put("text-shadow", "1px 1px 0 #000");
        EXAMPLES.put("filter", "blur(2px) brightness(1.2)");
        EXAMPLES.put("backdrop-filter", "blur(4px)");
        EXAMPLES.put("clip-path", "polygon(0 0, 100% 0, 50% 100%)");
        EXAMPLES.put("background", "linear-gradient(#444, #222) no-repeat center / cover, #111");
        EXAMPLES.put("border", "2px solid #555");
        EXAMPLES.put("border-radius", "4px 4px 0 0");
        EXAMPLES.put("border-image", "url(frame.png) 6 / 6px stretch");
        EXAMPLES.put("grid-template-columns", "repeat(3, 1fr) 80px");
        EXAMPLES.put("grid-template-areas", "\"a a b\" \"c c b\"");
        EXAMPLES.put("flex", "1 1 0");
        EXAMPLES.put("font", "bold 12px/1.4 sans-serif");
        EXAMPLES.put("font-family", "\"Fusion Pixel\", sans-serif");
        EXAMPLES.put("mask-image", "linear-gradient(#000, transparent)");
        EXAMPLES.put("outline", "1px solid #fff");
        EXAMPLES.put("inset", "0 auto auto 0");
        EXAMPLES.put("-mc-translate", "0 1 0");
        EXAMPLES.put("-mc-scene-center", "4 0 4");
        EXAMPLES.put("-mc-gaze-limit", "40deg 20deg 10deg");
        EXAMPLES.put("object-position", "center top");
        EXAMPLES.put("transform-origin", "50% 100%");
        EXAMPLES.put("text-decoration", "underline");
    }

    // ---- Feature probes (title, property, samples) ----

    private record Probe(String group, String property, List<String> samples) {}

    private static final List<Probe> LENGTH_PROBES = List.of(
            new Probe("单位", "width", List.of("1px", "1em", "1rem", "1%", "1vw", "1vh", "1vmin", "1vmax", "1dp", "1ch", "1ex",
                    "1cm", "1mm", "1in", "1pt", "1pc", "1lh", "1svh", "1dvh", "1cqw")),
            new Probe("数学函数", "width", List.of("calc(100% - 4px)", "calc(2 * 3px)", "min(10px, 5%)", "max(10px, 5%)",
                    "clamp(10px, 5%, 40px)", "round(7.3px, 2px)", "abs(-4px)", "sign(-4px)", "mod(7px, 3px)", "pow(2, 3) * 1px",
                    "sqrt(16) * 1px", "sin(30deg) * 10px")),
            new Probe("变量与引用", "width", List.of("var(--w)", "var(--w, 10px)", "env(safe-area-inset-top)")),
            new Probe("属性值引用", "content", List.of("attr(data-x)", "\"文字\"", "counter(a)")));

    private static final List<Probe> COLOR_PROBES = List.of(
            new Probe("颜色写法", "color", List.of("#f80", "#ff8800", "#f808", "#ff880080", "rgb(255 136 0)", "rgba(255, 136, 0, .5)",
                    "hsl(30 100% 50%)", "hsla(30, 100%, 50%, .5)", "hwb(30 0% 0%)", "lab(60% 40 60)", "lch(60% 70 50)",
                    "oklab(0.7 0.1 0.1)", "oklch(0.7 0.15 60)", "color(srgb 1 0.5 0)", "color-mix(in srgb, red 30%, blue)",
                    "light-dark(white, black)", "currentColor", "transparent", "rebeccapurple", "mc-gold", "Canvas")));

    private static final List<Probe> IMAGE_PROBES = List.of(
            new Probe("图片值", "background-image", List.of("url(a.png)", "sprite(minecraft:icon/x)", "linear-gradient(red, blue)",
                    "radial-gradient(red, blue)", "conic-gradient(red, blue)", "repeating-linear-gradient(red, blue 10px)",
                    "repeating-radial-gradient(red, blue 10px)", "repeating-conic-gradient(red, blue 10deg)",
                    "image-set(url(a.png) 1x)", "cross-fade(url(a.png), url(b.png))", "element(#a)",
                    "url(a.png), linear-gradient(red, blue)")));

    private static final List<Probe> TRANSFORM_PROBES = List.of(
            new Probe("变换函数", "transform", List.of("translate(1px, 2px)", "translateX(1px)", "translateY(1px)", "translateZ(1px)",
                    "translate3d(1px, 2px, 3px)", "scale(1.2)", "scaleX(1.2)", "scaleY(1.2)", "scaleZ(1.2)", "scale3d(1, 1, 1)",
                    "rotate(10deg)", "rotateX(10deg)", "rotateY(10deg)", "rotateZ(10deg)", "rotate3d(0, 0, 1, 10deg)",
                    "skew(10deg)", "skewX(10deg)", "skewY(10deg)", "matrix(1, 0, 0, 1, 0, 0)", "matrix3d(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)",
                    "perspective(100px)")),
            new Probe("独立变换属性", "translate", List.of("10px 4px")),
            new Probe("独立变换属性", "rotate", List.of("10deg")),
            new Probe("独立变换属性", "scale", List.of("1.2")));

    private static final List<Probe> FILTER_PROBES = List.of(
            new Probe("滤镜函数", "filter", List.of("blur(2px)", "brightness(1.2)", "contrast(1.2)", "grayscale(1)", "invert(1)",
                    "opacity(.5)", "saturate(2)", "sepia(1)", "hue-rotate(30deg)", "drop-shadow(1px 1px 2px #000)", "url(#f)")));

    private static final List<Probe> TIMING_PROBES = List.of(
            new Probe("缓动函数", "transition-timing-function", List.of("ease", "linear", "ease-in", "ease-out", "ease-in-out",
                    "step-start", "step-end", "steps(4)", "steps(4, jump-end)", "cubic-bezier(.2, .8, .2, 1)", "linear(0, .5, 1)")));

    private static final List<Probe> SHAPE_PROBES = List.of(
            new Probe("clip-path 形状", "clip-path", List.of("inset(4px round 2px)", "circle(50%)", "ellipse(40% 30%)",
                    "polygon(0 0, 100% 0, 50% 100%)", "path('M0 0 L10 10')", "url(#c)")));

    private static final List<String> PSEUDO_CLASSES = List.of(":hover", ":active", ":focus", ":focus-visible", ":focus-within",
            ":checked", ":disabled", ":enabled", ":empty", ":root", ":first-child", ":last-child", ":only-child", ":first-of-type",
            ":last-of-type", ":only-of-type", ":placeholder-shown", ":open", ":scope", ":any-link", ":link", ":visited", ":target",
            ":read-only", ":read-write", ":required", ":optional", ":valid", ":invalid", ":indeterminate", ":default",
            ":in-range", ":out-of-range", ":host", ":lang(zh)", ":dir(ltr)", ":not(.a)", ":is(.a, .b)", ":where(.a)", ":has(> .a)",
            ":nth-child(2n+1)", ":nth-last-child(2)", ":nth-of-type(odd)", ":nth-last-of-type(even)", ":nth-child(2n of .a)");

    private static final List<String> PSEUDO_ELEMENTS = List.of("::before", "::after", "::placeholder", "::selection",
            "::first-line", "::first-letter", "::marker", "::backdrop", "::-webkit-scrollbar", ":before", ":after");

    private static final List<String> SELECTOR_FORMS = List.of("*", "div", ".a", "#a", "[a]", "[a=b]", "[a~=b]", "[a|=b]", "[a^=b]",
            "[a$=b]", "[a*=b]", "[a=b i]", "a b", "a > b", "a + b", "a ~ b", "a, b", "a.b#c[d]:hover", "&.a", "ns|a");

    private static final List<String> AT_RULES = List.of("@media screen { a { color: red } }", "@supports (display: grid) { a { color: red } }",
            "@import url(x.css);", "@keyframes k { from { opacity: 0 } to { opacity: 1 } }", "@layer base { a { color: red } }",
            "@font-face { font-family: x; src: url(x.ttf) }", "@container (min-width: 10px) { a { color: red } }",
            "@property --x { syntax: '<length>'; inherits: false; initial-value: 0px }", "@page { margin: 0 }",
            "@namespace url(x);", "@charset \"utf-8\";", "@scope (.a) { b { color: red } }", "@starting-style { a { opacity: 0 } }",
            "@counter-style x { system: cyclic; symbols: a }", "@view-transition { navigation: auto }");

    /** Properties people reach for that the engine does not have. Whether each is known is checked, not assumed. */
    private static final List<String> WISHLIST = List.of("float", "clear", "columns", "column-count", "column-width", "column-rule",
            "table-layout", "border-collapse", "border-spacing", "caption-side", "empty-cells", "writing-mode", "direction",
            "unicode-bidi", "text-orientation", "hyphens", "tab-size", "text-wrap", "text-justify", "text-decoration-color",
            "text-decoration-style", "text-decoration-thickness", "text-underline-offset", "text-emphasis", "font-variant",
            "font-feature-settings", "font-stretch", "font-kerning", "quotes", "counter-reset", "counter-increment",
            "list-style-position", "list-style-image", "resize", "appearance", "caret-color", "will-change", "contain",
            "content-visibility", "container-type", "scroll-snap-type", "overscroll-behavior", "touch-action", "perspective",
            "perspective-origin", "transform-style", "backface-visibility", "translate", "rotate", "scale", "offset-path", "clip",
            "shape-outside", "mask-position", "mask-size", "mask-repeat", "mask-mode", "background-attachment",
            "background-blend-mode", "background-origin", "margin-inline", "padding-inline", "margin-block", "inset-inline",
            "border-start-start-radius", "line-break", "overflow-anchor", "scrollbar-color", "scrollbar-gutter", "color-scheme",
            "print-color-adjust", "zoom", "all", "field-sizing", "anchor-name", "position-anchor", "text-align-last",
            "font-optical-sizing", "font-synthesis", "image-orientation", "ruby-position", "text-rendering");

    // ---- Generation ----

    @Test
    void referenceIsCurrent() throws IOException {
        String text = generate();
        if (System.getenv("ORIEL_UPDATE_DOCS") != null) {
            Files.createDirectories(DOC.getParent());
            Files.writeString(DOC, text, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(DOC), DOC + " is missing; run with ORIEL_UPDATE_DOCS=1 to create it");
        String committed = Files.readString(DOC, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(text, committed, "docs/zh/css-reference.md is out of date; regenerate it with ORIEL_UPDATE_DOCS=1");
    }

    private static String generate() {
        StringBuilder out = new StringBuilder();
        out.append("# CSS 支持参考\n\n")
                .append("> 本页由 `engine/src/test/java/dev/oriel/engine/css/CssReferenceTest.java` 生成，**不要手改**。\n")
                .append("> 属性、继承、动画来自引擎的属性表；每个属性接受哪些关键字和值类型，是把候选值交给引擎真实的解析器试出来的；\n")
                .append("> 选择器、at 规则、单位、函数同理。引擎改了 CSS 支持而没重新生成，测试会失败。\n")
                .append("> 重新生成：`ORIEL_UPDATE_DOCS=1 ./gradlew :engine:test --tests '*CssReferenceTest'`\n\n")
                .append("[文档总目录](index.md)\n\n");
        out.append("## 怎么读这份表\n\n")
                .append("- **不支持就会报错**：未知属性、未知单位、非法取值、不支持的 at 规则都会产生诊断（含文件与行号，并尽量给出建议），")
                .append("不会静默当作 px 或透明。游戏里在页面错误面板看；开发时用 `./gradlew :preview:checkUi` 或 MCP 的 `oriel_check` 一次列出。\n")
                .append("- **继承**：是，则子元素没写时沿用父元素的值（如 `color`、`font-*`）。\n")
                .append("- **变化时**：`重排`＝改了会重新布局；`重绘`＝只重画，便宜得多。做动画优先选重绘类属性。\n")
                .append("- **动画**：`连续`类（长度、数字、颜色、阴影、变换）可以平滑过渡；`离散`在动画中点直接切换；`否`不能动。\n")
                .append("- **取值**：`关键字`列出引擎接受的写法；`类型`是除关键字外还接受的值类型；复合值（阴影、变换、渐变等）见下方“写法示例”。\n")
                .append("- 所有属性都接受 `inherit`、`initial`、`unset`（`revert` 当作 `unset`）。\n\n");

        Map<String, List<Longhand>> byCategory = new LinkedHashMap<>();
        for (String c : CATEGORIES) byCategory.put(c, new ArrayList<>());
        int count = 0;
        for (Longhand l : Properties.all()) {
            if (l.name.startsWith("-oriel-")) continue; // parts of -mc-* and other compound properties
            byCategory.get(category(l.name)).add(l);
            count++;
        }
        out.append("## 属性（").append(count).append(" 个单项属性）\n\n");
        Set<String> keywordPool = keywordPool();
        for (Map.Entry<String, List<Longhand>> e : byCategory.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            out.append("### ").append(e.getKey()).append("\n\n| 属性 | 继承 | 变化时 | 动画 | 取值 |\n|---|---|---|---|---|\n");
            for (Longhand l : e.getValue()) out.append(row(l, keywordPool));
            out.append('\n');
        }

        out.append("## 简写属性\n\n简写只是把值分发给单项属性，没写的部分取初始值。\n\n| 简写 | 展开为 |\n|---|---|\n");
        List<Shorthand> shorthands = new ArrayList<>(Shorthands.all());
        shorthands.sort((a, b) -> a.name().compareTo(b.name()));
        for (Shorthand s : shorthands) {
            List<String> parts = new ArrayList<>();
            for (Longhand l : s.longhands()) parts.add(l.name.startsWith("-oriel-") ? "（内部分量）" : "`" + l.name + "`");
            out.append("| `").append(s.name()).append("` | ").append(String.join(" ", new LinkedHashSet<>(parts))).append(" |\n");
        }
        out.append("\n别名（写哪个都行）：`overflow-wrap`、`word-wrap`→`word-break`；`-webkit-line-clamp`→`line-clamp`；")
                .append("`-webkit-backdrop-filter`、`-webkit-background-clip`、`-webkit-clip-path`、`-webkit-mask-image`、")
                .append("`-webkit-mask-composite`、`-webkit-mask-clip` 去掉前缀的同名属性。\n\n");
        for (String alias : List.of("overflow-wrap", "word-wrap", "-webkit-line-clamp", "-webkit-backdrop-filter",
                "-webkit-background-clip", "-webkit-clip-path", "-webkit-mask-image", "-webkit-mask-composite", "-webkit-mask-clip")) {
            assertTrue(Properties.longhand(alias) != null, "alias " + alias + " is documented but no longer resolves");
        }

        out.append("## 写法示例（复合值）\n\n以下每条都经过引擎解析验证。\n\n| 属性 | 示例 |\n|---|---|\n");
        for (Map.Entry<String, String> e : EXAMPLES.entrySet()) {
            assertTrue(accepts(e.getKey(), e.getValue()), "documented example is rejected: " + e.getKey() + ": " + e.getValue());
            out.append("| `").append(e.getKey()).append("` | `").append(e.getValue().replace("|", "\\|")).append("` |\n");
        }
        out.append('\n');

        out.append("## 值：单位、函数、颜色、图片、变换\n\n✅ 支持　❌ 不支持（会报诊断）　⚠ 近似（会提示）\n\n");
        probeSection(out, "长度、数学与变量", LENGTH_PROBES);
        probeSection(out, "颜色", COLOR_PROBES);
        probeSection(out, "图片与渐变", IMAGE_PROBES);
        probeSection(out, "变换", TRANSFORM_PROBES);
        probeSection(out, "滤镜与形状", concat(FILTER_PROBES, SHAPE_PROBES));
        probeSection(out, "缓动", TIMING_PROBES);

        out.append("## 选择器\n\n### 基本形式\n\n").append(selectorTable(SELECTOR_FORMS));
        out.append("\n### 伪类\n\n").append(selectorTable(PSEUDO_CLASSES));
        out.append("\n### 伪元素\n\n").append(selectorTable(PSEUDO_ELEMENTS));
        out.append("\n## At 规则\n\n| 规则 | 状态 |\n|---|---|\n");
        for (String rule : AT_RULES) {
            String name = rule.substring(0, rule.length() - rule.replaceFirst("^@[a-z-]+", "").length());
            out.append("| `").append(name).append("` | ").append(atRuleStatus(rule)).append(" |\n");
        }
        out.append("\n## 媒体查询特性\n\n`@media` 里可用：`width`、`height`（含 `min-`/`max-` 前缀和 `>=` 这类范围写法）、`orientation`、")
                .append("`prefers-reduced-motion`、`gui-scale`（Minecraft 的 GUI 缩放，Oriel 专有，可写 `min-gui-scale`）。")
                .append("其余特性不会匹配。\n\n");

        out.append("## 常见但不支持的属性\n\n下面这些在浏览器里常见，Oriel 里**没有**。写了会得到诊断和“你是不是想写…”的建议。\n\n");
        List<String> missing = new ArrayList<>(), present = new ArrayList<>();
        for (String name : WISHLIST) (Suggestions.knownProperty(name) ? present : missing).add(name);
        out.append(String.join("、", missing.stream().map(n -> "`" + n + "`").toList())).append("\n\n");
        if (!present.isEmpty()) {
            out.append("（以下名字在候选里，但引擎其实支持：")
                    .append(String.join("、", present.stream().map(n -> "`" + n + "`").toList())).append("）\n\n");
        }
        out.append("其中 `float`、表格布局（`display: table`）、`position: sticky`（按 `relative` 处理并提示）、CSS 嵌套、`@layer` 的层级顺序目前都没有或只是近似；")
                .append("表格请用 `display: grid`，内联 SVG 请用 `<img>`、渐变或边框代替（引擎会按元素给出建议）。\n");
        return out.toString();
    }

    private static final List<String> CATEGORIES = List.of("盒模型与定位", "边框与轮廓", "Flex 与对齐", "Grid", "文本、字体与交互",
            "背景、遮罩与图像", "特效与变换", "过渡与动画", "滚动", "Minecraft 专属（-mc-*）");

    private static String category(String name) {
        if (name.startsWith("-mc-")) return "Minecraft 专属（-mc-*）";
        if (name.startsWith("transition") || name.startsWith("animation")) return "过渡与动画";
        if (name.startsWith("border-image") || name.startsWith("border") || name.startsWith("outline")) return "边框与轮廓";
        if (name.startsWith("grid")) return "Grid";
        if (name.startsWith("flex") || name.startsWith("justify") || name.startsWith("align") || name.equals("order")
                || name.endsWith("gap")) return "Flex 与对齐";
        if (name.startsWith("background") || name.startsWith("mask") || name.startsWith("object")
                || name.equals("image-rendering") || name.equals("content")) return "背景、遮罩与图像";
        if (name.startsWith("filter") || name.startsWith("backdrop") || name.startsWith("transform") || name.equals("clip-path")
                || name.equals("box-shadow") || name.equals("opacity") || name.equals("mix-blend-mode")
                || name.equals("isolation") || name.equals("shape-rendering")) return "特效与变换";
        if (name.startsWith("scroll") || name.startsWith("overflow")) return "滚动";
        if (name.startsWith("font") || name.startsWith("text") || name.startsWith("line") || name.startsWith("letter")
                || name.startsWith("word") || name.startsWith("white") || name.startsWith("list")
                || name.equals("color") || name.equals("accent-color") || name.equals("cursor") || name.equals("pointer-events")
                || name.equals("user-select") || name.equals("vertical-align") || name.equals("--*")) return "文本、字体与交互";
        return "盒模型与定位";
    }

    private static String row(Longhand l, Set<String> pool) {
        Prop p = l.prop != null ? l.prop : l.group.prop;
        String inherited = p.inherited ? "是" : "否";
        String change = p.affectsLayout ? "重排" : "重绘";
        String animate = switch (p.interpolation) {
            case NONE -> "否";
            case DISCRETE -> "离散";
            default -> "连续";
        };
        List<String> keywords = new ArrayList<>();
        for (String k : pool) if (accepts(l, k)) keywords.add(k);
        List<String> types = new ArrayList<>();
        for (Map.Entry<String, String> t : TYPES.entrySet()) if (accepts(l, t.getValue())) types.add(t.getKey());
        // A property that takes any identifier (animation-name, transition-property) accepts every candidate.
        boolean anyIdentifier = keywords.size() > 60;
        if (anyIdentifier) keywords.clear();
        if (anyIdentifier && !l.name.startsWith("grid-")) types.clear();
        if (types.contains("数字")) types.remove("整数");
        StringBuilder v = new StringBuilder();
        if (anyIdentifier) v.append("任意名称（标识符）");
        if (!keywords.isEmpty()) v.append("关键字：").append(String.join(" ", keywords.stream().map(k -> "`" + k + "`").toList()));
        if (!types.isEmpty()) {
            if (v.length() > 0) v.append("；");
            v.append("类型：").append(String.join("、", types));
        }
        if (v.length() == 0) v.append("复合值，见示例");
        if (EXAMPLES.containsKey(l.name)) v.append("（有示例）");
        return "| `" + l.name + "` | " + inherited + " | " + change + " | " + animate + " | " + v + " |\n";
    }

    /** The candidates plus every constant of every keyword enum a property defaults to. */
    private static Set<String> keywordPool() {
        Set<String> pool = new TreeSet<>();
        for (String k : KEYWORDS) if (!k.equals("inherit")) pool.add(k);
        ComputedStyle initial = ComputedStyle.INITIAL;
        for (Prop p : Prop.values()) {
            Object v = p.get(initial);
            if (v instanceof Enum<?> e) {
                for (Object c : e.getDeclaringClass().getEnumConstants()) pool.add(Keywords.css((Enum<?>) c));
            }
        }
        return pool;
    }

    private static boolean accepts(Longhand l, String css) {
        return Decl.parse(l, CssParser.parseComponentValues(css), new ValueContext()) != null;
    }

    /** Whether the whole declaration (a shorthand too) is taken, through the same path a stylesheet uses. */
    private static boolean accepts(String property, String value) {
        return !status(property, value).equals("❌");
    }

    private static void probeSection(StringBuilder out, String title, List<Probe> probes) {
        out.append("### ").append(title).append("\n\n");
        for (Probe p : probes) {
            out.append("**").append(p.group()).append("**（`").append(p.property()).append("`）：");
            List<String> cells = new ArrayList<>();
            for (String s : p.samples()) cells.add("`" + s + "` " + status(p.property(), s));
            out.append(String.join("　", cells)).append("\n\n");
        }
    }

    private static String status(String property, String value) {
        List<Diagnostic> log = new ArrayList<>();
        List<Decl> decls = Stylesheet.declarations(property + ": " + value, "doc", new TestHost(), log::add);
        if (decls.isEmpty() || log.stream().anyMatch(d -> d.severity() != Diagnostic.Severity.INFO)) return "❌";
        return log.isEmpty() ? "✅" : "⚠";
    }

    private static String selectorTable(List<String> selectors) {
        List<String> cells = new ArrayList<>();
        for (String s : selectors) cells.add("`" + s + "` " + (parsesAsSelector(s) ? "✅" : "❌"));
        return String.join("　", cells) + "\n";
    }

    private static boolean parsesAsSelector(String selector) {
        try {
            SelectorParser.parse(selector);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String atRuleStatus(String rule) {
        List<Diagnostic> log = new ArrayList<>();
        Stylesheet.parse(rule, "doc", new TestHost(), log::add);
        for (Diagnostic d : log) {
            if (d.code().equals("layer-flattened")) return "⚠ 能用，但层级顺序不生效（压平）";
            if (d.code().equals("ignored-at-rule") || d.code().equals("unknown-at-rule") || d.code().equals("font-face")) return "❌ 忽略并提示";
        }
        return log.isEmpty() ? "✅" : "❌ " + log.get(0).code();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

}
