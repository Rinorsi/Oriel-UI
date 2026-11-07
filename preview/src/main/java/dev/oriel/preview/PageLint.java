package dev.oriel.preview;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.layout.Box;
import dev.oriel.engine.layout.Fragment;
import dev.oriel.engine.paint.Affine;
import dev.oriel.engine.paint.Coordinates;
import dev.oriel.engine.style.BoxSizing;
import dev.oriel.engine.style.Display;
import dev.oriel.engine.style.Image;
import dev.oriel.engine.style.TextOverflow;
import dev.oriel.engine.style.Visibility;
import dev.oriel.engine.style.WhiteSpace;
import dev.oriel.preview.host.PreviewHost;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Post-layout authoring diagnostics, shared by check, snapshots and action previews. */
final class PageLint {
    static final int MAX_PER_RULE = 5;
    private static final float EPSILON = 0.5f;

    private PageLint() {}

    static List<Diagnostic> run(Document doc, PreviewHost host) {
        doc.flushLayout();
        Findings out = new Findings(doc, host.assets().locate(doc.url()).map(PageLint::bundledPage).orElse(false));
        Set<Box> visited = new HashSet<>();
        Set<String> images = new LinkedHashSet<>();
        int imageCount = 0;
        boolean hasLazyImages = false;
        Element firstImage = null;
        for (Element e : doc.querySelectorAll("*")) {
            if (e.tagName().equals("img")) {
                String src = e.getAttribute("src");
                if (src != null && !src.isBlank() && !src.startsWith("data:") && !src.startsWith("canvas:") && !src.startsWith("sprite:")) {
                    imageCount++;
                    if (firstImage == null) firstImage = e;
                    hasLazyImages |= dev.oriel.engine.replaced.LazyImages.lazyImage(e);
                    image(host.resolveUrl(doc.url(), src), e, host, images, out);
                }
            }
            if (e.computedStyle() != null) for (var layer : e.computedStyle().backgroundLayers) {
                if (layer.image() instanceof Image.Url u) {
                    imageCount++;
                    if (firstImage == null) firstImage = e;
                    hasLazyImages |= dev.oriel.engine.replaced.LazyImages.lazyBackground(e);
                    image(u.url(), e, host, images, out);
                }
            }
            if (e.computedStyle() != null) {
                if (e.computedStyle().borderImageSource.image() instanceof Image.Url u) image(u.url(), e, host, images, out);
                for (Image mask : e.computedStyle().maskImage) if (mask instanceof Image.Url u) image(u.url(), e, host, images, out);
            }
            if (e.box != null) visit(e.box, visited, out);
        }
        if (imageCount > 24 && !hasLazyImages) {
            out.add("images-without-lazy", firstImage, "同页图片/背景图共 " + imageCount + " 处，超过 24 处且没有懒加载",
                    "列表图片添加 loading=\"lazy\"，URL 背景图所在元素添加 data-oriel-lazy；显式设置 width/height 预留空间。");
        }
        sources(doc, host, out);
        return out.finish();
    }

    static void report(Document doc, PreviewHost host) {
        for (Diagnostic d : run(doc, host)) doc.diagnostics().report(d);
    }

    // ---- Page sources: scripts and style sheets, read as text ----

    private static final Pattern GLOBAL_FUNCTION = Pattern.compile("(?m)^(?:async\\s+)?function\\s*\\*?\\s*([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern BARE_CLASS_RULE = Pattern.compile("(?:^|[};])\\s*\\.([A-Za-z_][\\w-]*)\\s*\\{([^}]*)}");
    private static final Pattern DISPLAY_NONE = Pattern.compile("display\\s*:\\s*none");
    private static final Pattern DISPLAY_ANY = Pattern.compile("display\\s*:\\s*(?!none)[a-z-]+");
    /** Names that are meant to hide things when a script adds them. */
    private static final Pattern HIDING_NAME = Pattern.compile("(?i).*(hid|none|off|collaps|closed|inactive|invisible|empty|disabled).*");

    private record Source(Element owner, String text) {}

    /** Two checks over the page's own scripts and style sheets; the text is what the author wrote, not the DOM. */
    private static void sources(Document doc, PreviewHost host, Findings out) {
        List<Source> scripts = new ArrayList<>(), styles = new ArrayList<>();
        for (Element e : doc.querySelectorAll("script, style, link")) {
            switch (e.tagName()) {
                case "script" -> {
                    String type = e.getAttribute("type");
                    if (type != null && !type.isBlank() && !type.contains("javascript") && !type.equals("module")) continue;
                    String src = e.getAttribute("src");
                    String text = src != null && !src.isBlank() ? host.loadText(host.resolveUrl(doc.url(), src)) : e.textContent();
                    if (text != null) scripts.add(new Source(e, text));
                }
                case "style" -> styles.add(new Source(e, e.textContent()));
                default -> {
                    String rel = e.getAttribute("rel"), href = e.getAttribute("href");
                    if (rel != null && rel.contains("stylesheet") && href != null) {
                        String text = host.loadText(host.resolveUrl(doc.url(), href));
                        if (text != null) styles.add(new Source(e, text));
                    }
                }
            }
        }
        // The same top-level function declared twice: the later one silently replaces the earlier.
        Map<String, Integer> declared = new LinkedHashMap<>();
        for (Source script : scripts) {
            Matcher m = GLOBAL_FUNCTION.matcher(script.text());
            while (m.find()) {
                String name = m.group(1);
                if (declared.merge(name, 1, Integer::sum) == 2) {
                    out.add("duplicate-global-function", script.owner(), name,
                            "全局函数 " + name + "() 在页面脚本里被声明了不止一次，后面的会悄悄覆盖前面的",
                            "先在已加载的脚本里搜索现成的 " + name + "，只留一份定义；需要不同行为就换名字或传参数。");
                }
            }
        }
        // A bare class hidden for good by one rule, yet written into the page by a script: its elements never show.
        for (Source style : styles) {
            Matcher m = BARE_CLASS_RULE.matcher(style.text());
            while (m.find()) {
                String name = m.group(1);
                if (!DISPLAY_NONE.matcher(m.group(2)).find() || HIDING_NAME.matcher(name).matches()) continue;
                if (shownElsewhere(styles, name) || !createdByScript(scripts, name)) continue;
                out.add("hidden-class-created-by-script", style.owner(), "." + name,
                        "样式 ." + name + " 设了 display:none，页面脚本却会生成带这个类的元素，它们永远看不见",
                        "给它一条显示规则（例如 ." + name + ".on 或父级限定的 display），或改名/删掉隐藏规则。");
            }
        }
    }

    /** Some other rule mentioning the class sets a display other than none, so the class can be shown. */
    private static boolean shownElsewhere(List<Source> styles, String name) {
        Pattern rule = Pattern.compile("([^{}]*\\." + Pattern.quote(name) + "(?![\\w-])[^{}]*)\\{([^}]*)}");
        for (Source style : styles) {
            Matcher m = rule.matcher(style.text());
            while (m.find()) if (DISPLAY_ANY.matcher(m.group(2)).find()) return true;
        }
        return false;
    }

    /** The class written into markup by a script: class="..." inside a string, or an assignment to className. */
    private static boolean createdByScript(List<Source> scripts, String name) {
        String token = "(?<![\\w-])" + Pattern.quote(name) + "(?![\\w-])";
        Pattern created = Pattern.compile("(?:class\\s*=\\s*[\\\\\"']?[^\\\\\"'>]*|className\\s*\\+?=\\s*[\\\\\"'`][^\\\\\"'`]*)" + token);
        for (Source script : scripts) if (created.matcher(script.text()).find()) return true;
        return false;
    }

    /** Existing library visuals are reviewed separately; never exempt external pages by namespace alone. */
    static boolean bundledPage(Path page) {
        try {
            Path real = page.toRealPath();
            for (Path root = real.getParent(); root != null; root = root.getParent()) {
                if (!Files.isRegularFile(root.resolve("gradlew")) || !Files.isDirectory(root.resolve("preview/src/main/java/dev/oriel/preview"))) continue;
                Path relative = root.relativize(real);
                return relative.startsWith(Path.of("kit/src/main/resources/assets/oriel/ui"))
                        || relative.startsWith(Path.of("common/src/main/resources/assets/oriel/oriel/showcase"))
                        || relative.startsWith(Path.of("common/src/main/resources/assets/oriel/oriel/demo"));
            }
        } catch (IOException e) {
            // Unknown or inaccessible sources retain strict authoring diagnostics.
        }
        return false;
    }

    private static void visit(Box box, Set<Box> visited, Findings out) {
        if (!visited.add(box) || !shown(box.element)) return;
        for (var line : box.lines) for (var fragment : line.fragments) {
            if (!(fragment instanceof Fragment.TextRun run) || run.text().isBlank() || !shown(run.styleSource())) continue;
            Element owner = run.styleSource() != null ? run.styleSource() : box.element;
            if (owner == null || moving(box, out.doc)) continue;
            boolean ellipsis = generatedEllipsis(run);
            if (ellipsis && run.node() != null && box.style.textOverflow == TextOverflow.ELLIPSIS && visible(box, run, out.doc)
                    && !tooltip(owner)) {
                out.add("text-ellipsis-tooltip", owner, "被截断的文字应有悬停提示",
                        "给文字或祖先添加包含完整内容的 title / title-json，或放宽宽度、允许换行。");
            }
            if (ellipsis || run.style().whiteSpace != WhiteSpace.NOWRAP) continue;
            Box clip = clippingBox(box, run, out.doc);
            if (clip == null) continue;
            out.add("text-clipped", owner, "文字被裁掉却没有省略号",
                    "在实际承载文字的盒子上设置 overflow:hidden; text-overflow:ellipsis; white-space:nowrap 并加 title，或允许换行、放宽裁剪容器。");
            for (Box item = box; item != null; item = item.contentParent()) {
                if (squeezed(item) && (contains(item, clip) || contains(clip, item))) {
                    out.add("flex-content-clipped", item.element, "flex 行子项收缩后裁掉内容",
                            "给图标、角标或标签设置 flex:none / min-width；让文字区域单独收缩并使用省略号和 title。");
                    break;
                }
            }
        }
        // Replaced children (icons) can be cut without any overflowing text run.
        if (box.kind == Box.Kind.REPLACED && !moving(box, out.doc) && visible(box, null, out.doc)) {
            Box clip = clippingBox(box, null, out.doc);
            if (clip != null) for (Box item = box; item != null; item = item.contentParent()) {
                if (squeezed(item) && (contains(item, clip) || contains(clip, item))) {
                    out.add("flex-content-clipped", item.element, "flex 行子项收缩后裁掉图标或替换元素",
                            "给图标或标签设置 flex:none / min-width，文字区域单独收缩并使用省略号。");
                    break;
                }
            }
        }
        for (Box child : box.children) visit(child, visited, out);
    }

    private static boolean generatedEllipsis(Fragment.TextRun run) {
        return run.text().endsWith("…") && (run.node() == null
                || run.source() != null && run.source().length == run.text().length());
    }

    private static boolean tooltip(Element e) {
        for (Element p = e; p != null; p = p.parentElement()) {
            if (p.hasAttribute("title") || p.hasAttribute("title-json")) {
                return nonblank(p.getAttribute("title")) || nonblank(p.getAttribute("title-json"));
            }
        }
        return false;
    }

    private static boolean nonblank(String value) { return value != null && !value.isBlank(); }

    private static boolean shown(Element e) {
        for (Element p = e; p != null; p = p.parentElement()) {
            var s = p.computedStyle();
            if (s == null || s.display == Display.NONE || s.visibility != Visibility.VISIBLE || s.opacity < 0.05f) return false;
        }
        return true;
    }

    private static boolean moving(Box box, Document doc) {
        for (Box p = box; p != null; p = p.contentParent()) {
            if (!p.style.animations.isEmpty() && (doc.animations().isSettling()
                    || p.style.animations.stream().anyMatch(a -> !Float.isFinite(a.iterations())))) return true;
        }
        // Axis-aligned scale/translation preserve the bounds test, rotation and skew do not.
        Affine transform = Coordinates.toViewport(box, new Affine());
        return Math.abs(transform.b) > 0.001f || Math.abs(transform.c) > 0.001f;
    }

    private static float[] bounds(Box box, Fragment.TextRun run) {
        return run == null ? Coordinates.boundingRect(box) : Coordinates.toViewport(box, new Affine())
                .mapBounds(run.x() - box.scrollLeft(), run.y() - box.scrollTop(), run.width(), run.height(), new float[4]);
    }

    private static boolean visible(Box box, Fragment.TextRun run, Document doc) {
        float[] r = bounds(box, run);
        float x0 = Math.max(0, r[0]), y0 = Math.max(0, r[1]);
        float x1 = Math.min(doc.viewportWidth(), r[0] + r[2]), y1 = Math.min(doc.viewportHeight(), r[1] + r[3]);
        for (Box p = run == null ? box.contentParent() : box; p != null; p = p.contentParent()) {
            if (p.kind == Box.Kind.INLINE) continue;
            float[] c = Coordinates.toViewport(p, new Affine()).mapBounds(p.borderLeft, p.borderTop,
                    p.paddingBoxWidth(), p.paddingBoxHeight(), new float[4]);
            if (p.style.overflowX.clips()) { x0 = Math.max(x0, c[0]); x1 = Math.min(x1, c[0] + c[2]); }
            if (p.style.overflowY.clips()) { y0 = Math.max(y0, c[1]); y1 = Math.min(y1, c[1] + c[3]); }
        }
        return x1 > x0 + EPSILON && y1 > y0 + EPSILON;
    }

    private static Box clippingBox(Box box, Fragment.TextRun run, Document doc) {
        if (!visible(box, run, doc)) return null;
        float[] r = bounds(box, run);
        for (Box p = run == null ? box.contentParent() : box; p != null; p = p.contentParent()) {
            if (p.kind == Box.Kind.INLINE || !p.style.overflowX.clips()) continue;
            float[] c = Coordinates.toViewport(p, new Affine()).mapBounds(p.borderLeft, p.borderTop,
                    p.paddingBoxWidth(), p.paddingBoxHeight(), new float[4]);
            if (r[0] < c[0] - EPSILON || r[0] + r[2] > c[0] + c[2] + EPSILON) return p;
        }
        return null;
    }

    private static boolean contains(Box item, Box clip) {
        for (Box p = clip; p != null; p = p.contentParent()) if (p == item) return true;
        return false;
    }

    private static boolean squeezed(Box item) {
        Box parent = item.parent;
        if (item.element == null || item.kind == Box.Kind.ANONYMOUS || item.outOfFlow || parent == null || !parent.style.display.isFlex()
                || !parent.style.flexDirection.isRow() || item.style.flexShrink <= 0) return false;
        float edges = item.width - item.contentWidth();
        var basis = item.style.flexBasis.isAuto() ? item.style.width : item.style.flexBasis;
        float needed;
        float sizingEdges = item.style.boxSizing == BoxSizing.CONTENT_BOX ? edges : 0;
        if (basis.isFixed()) needed = basis.resolve(parent.contentWidth()) + sizingEdges;
        else {
            // Auto basis: only count intrinsic, unwrapped content which actually extends beyond the item.
            needed = item.scrollWidth + item.borderLeft + item.borderRight;
            for (var line : item.lines) needed = Math.max(needed, line.x + line.width + item.paddingRight + item.borderRight);
        }
        // A max-width cap is not shrinkage caused by flex's distribution of negative free space.
        if (item.style.maxWidth.isFixed()) needed = Math.min(needed, item.style.maxWidth.resolve(parent.contentWidth()) + sizingEdges);
        return item.width + EPSILON < needed;
    }

    private static void image(String url, Element owner, PreviewHost host, Set<String> seen, Findings out) {
        if (!seen.add(url)) return;
        host.assets().locate(url).ifPresent(path -> {
            String size = largePng(path);
            if (size != null) out.add("image-too-large", owner, url, "PNG 体积过大: " + url + " (" + size + ")",
                    "按实际显示尺寸缩图并压缩 PNG；宽高不超过 2048px，文件不超过 1 MiB。");
        });
    }

    /** Reads only the PNG signature/IHDR prefix and file metadata, never decodes pixels. */
    static String largePng(Path path) {
        try (var input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(24);
            if (header.length != 24) return null;
            ByteBuffer data = ByteBuffer.wrap(header);
            if (data.getLong() != 0x89504e470d0a1a0aL || data.getInt() != 13 || data.getInt() != 0x49484452) return null;
            int width = data.getInt(), height = data.getInt();
            long bytes = Files.size(path);
            return width > 2048 || height > 2048 || bytes > 1024 * 1024
                    ? width + "x" + height + ", " + bytes + " bytes" : null;
        } catch (IOException e) {
            // Loading reports unreadable/missing resources through the existing image diagnostics.
            return null;
        }
    }

    private static final class Findings {
        final Document doc;
        final Diagnostic.Severity severity;
        final Map<String, LinkedHashMap<Object, Diagnostic>> rules = new LinkedHashMap<>();

        Findings(Document doc, boolean bundled) {
            this.doc = doc;
            severity = bundled ? Diagnostic.Severity.INFO : Diagnostic.Severity.WARNING;
        }

        void add(String code, Element e, String message, String suggestion) {
            add(code, e, e, message, suggestion);
        }

        void add(String code, Element e, Object key, String message, String suggestion) {
            String excerpt = "";
            if (code.startsWith("text-") || code.startsWith("flex-")) {
                String text = e.textContent().strip().replace('\n', ' ');
                excerpt = " 「" + (text.length() > 48 ? text.substring(0, 48) + "…" : text) + "」";
            }
            rules.computeIfAbsent(code, ignored -> new LinkedHashMap<>()).putIfAbsent(key,
                    new Diagnostic(severity, code, doc.url(), e.sourceLine(), e.sourceColumn(),
                            message + ": " + e + excerpt, severity == Diagnostic.Severity.INFO ? "库内示例待修复（不阻断 strict）：" + suggestion : suggestion));
        }

        List<Diagnostic> finish() {
            List<Diagnostic> result = new ArrayList<>();
            rules.forEach((code, entries) -> {
                result.addAll(entries.values().stream().limit(MAX_PER_RULE).toList());
                if (entries.size() > MAX_PER_RULE) result.add(new Diagnostic(severity, code,
                        doc.url(), 0, "汇总: " + entries.size() + " 处，已显示前 " + MAX_PER_RULE + " 处",
                        "修复已列位置后重新检查其余元素。"));
            });
            return result;
        }
    }
}
