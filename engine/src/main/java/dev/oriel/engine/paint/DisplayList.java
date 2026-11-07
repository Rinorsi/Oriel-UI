package dev.oriel.engine.paint;

import com.google.gson.Gson;
import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.host.ReplacedContent;
import dev.oriel.engine.style.BlendMode;
import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.MaskComposite;
import dev.oriel.engine.style.Shadow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, ordered Canvas calls. Native content is a live boundary, never a cached texture. */
public final class DisplayList {
    private record Entry(String element, String operation, Object[] arguments) {
        boolean same(Entry other) {
            return element.equals(other.element) && operation.equals(other.operation)
                    && Arrays.deepEquals(arguments, other.arguments);
        }
    }

    private final List<Entry> entries;
    private DisplayList(List<Entry> entries) { this.entries = List.copyOf(entries); }
    public int size() { return entries.size(); }

    public Map<String, Long> statistics() {
        long batches = 0, quads = 0, groups = 0, nativeContent = 0;
        for (Entry entry : entries) {
            switch (entry.operation) {
                case "fillQuads" -> { batches++; quads += (int) entry.arguments[2]; }
                case "beginGroup", "beginPageGroup", "beginBackdrop" -> { if ((boolean) entry.arguments[0]) groups++; }
                case "boundedGroup" -> { if ((boolean) entry.arguments[4]) groups++; }
                case "native" -> nativeContent++;
                default -> { }
            }
        }
        return Map.of("commands", (long) size(), "quadBatches", batches, "quads", quads,
                "groups", groups, "nativeBoundaries", nativeContent);
    }

    /** Only adjacent quads with the same element label are joined. Every state/effect/native call is a barrier. */
    public DisplayList batched() {
        List<Entry> result = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < entries.size();) {
            Entry first = entries.get(i);
            if (!first.operation.equals("fillQuads")) { result.add(first); i++; continue; }
            int end = i + 1, count = (int) first.arguments[2];
            while (end < entries.size()) {
                Entry next = entries.get(end);
                if (!next.operation.equals("fillQuads") || !next.element.equals(first.element)
                        || count + (int) next.arguments[2] > 4096) break;
                count += (int) next.arguments[2];
                end++;
            }
            if (end == i + 1) result.add(first);
            else {
                changed = true;
                float[] xy = new float[count * 8];
                int[] colors = new int[count * 4];
                int offset = 0;
                for (int n = i; n < end; n++) {
                    Entry part = entries.get(n);
                    int quads = (int) part.arguments[2];
                    System.arraycopy(part.arguments[0], 0, xy, offset * 8, quads * 8);
                    System.arraycopy(part.arguments[1], 0, colors, offset * 4, quads * 4);
                    offset += quads;
                }
                result.add(new Entry(first.element, "fillQuads", new Object[] {xy, colors, count}));
            }
            i = end;
        }
        return changed ? new DisplayList(result) : this;
    }

    /** Estimated payload bytes, excluding referenced host objects; not GPU texture memory. */
    public long payloadBytes() {
        long bytes = 0;
        for (Entry entry : entries) {
            bytes += 64 + entry.arguments.length * 8L;
            for (Object value : entry.arguments) {
                if (value instanceof float[] a) bytes += 16 + a.length * 4L;
                else if (value instanceof int[] a) bytes += 16 + a.length * 4L;
            }
        }
        return bytes;
    }

    /** Replays in order. The caller supplies the same initial state and resource generation. */
    public void replay(Canvas canvas) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            try { execute(canvas, entry); }
            catch (RuntimeException error) {
                throw new IllegalStateException("DisplayList command " + i + " " + entry.element
                        + " " + entry.operation + ": " + error.getMessage(), error);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void execute(Canvas canvas, Entry entry) {
        Object[] a = entry.arguments;
        switch (entry.operation) {
            case "save" -> canvas.save();
            case "restore" -> canvas.restore();
            case "translate" -> canvas.translate((float) a[0], (float) a[1]);
            case "transform" -> canvas.transform((float) a[0], (float) a[1], (float) a[2], (float) a[3], (float) a[4], (float) a[5]);
            case "multiplyAlpha" -> canvas.multiplyAlpha((float) a[0]);
            case "clipRect" -> canvas.clipRect((float) a[0], (float) a[1], (float) a[2], (float) a[3]);
            case "fillRect" -> canvas.fillRect((float) a[0], (float) a[1], (float) a[2], (float) a[3], (int) a[4]);
            case "fillQuads" -> canvas.fillQuads((float[]) a[0], (int[]) a[1], (int) a[2]);
            case "drawText" -> canvas.drawText((String) a[0], (float) a[1], (float) a[2], (FontSpec) a[3], (int) a[4], (int) a[5], (boolean) a[6]);
            case "drawImage" -> canvas.drawImage((String) a[0], (float) a[1], (float) a[2], (float) a[3], (float) a[4], (float) a[5], (float) a[6], (float) a[7], (float) a[8], (int) a[9], (boolean) a[10]);
            case "drawSprite" -> canvas.drawSprite((String) a[0], (float) a[1], (float) a[2], (float) a[3], (float) a[4], (int) a[5]);
            case "fillRoundedRect" -> canvas.fillRoundedRect((float) a[0], (float) a[1], (float) a[2], (float) a[3], (float[]) a[4], (int) a[5]);
            case "fillBorder" -> canvas.fillBorder((float[]) a[0], (float[]) a[1], (float[]) a[2], (int[]) a[3]);
            case "fillBoxShadow" -> canvas.fillBoxShadow((float[]) a[0], (float[]) a[1], (Shadow) a[2]);
            case "beginMask" -> canvas.beginMask((MaskComposite) a[0]);
            case "endGroup" -> canvas.endGroup((float) a[0], (List<Filter>) a[1], (BlendMode) a[2]);
            case "endBackdrop" -> canvas.endBackdrop((List<Filter>) a[0]);
            case "crispShapes" -> canvas.crispShapes((boolean) a[0]);
            case "beginGroup" -> accepted(canvas.beginGroup(), (boolean) a[0]);
            case "boundedGroup" -> accepted(canvas.beginGroup((float) a[0], (float) a[1], (float) a[2], (float) a[3]), (boolean) a[4]);
            case "beginPageGroup" -> accepted(canvas.beginPageGroup(), (boolean) a[0]);
            case "beginBackdrop" -> accepted(canvas.beginBackdrop(), (boolean) a[0]);
            case "native" -> nativePaint(canvas, (ReplacedContent) a[0], (float) a[1], (float) a[2], (float) a[3], (float) a[4]);
            case "skip" -> { }
            default -> throw new IllegalStateException("Unknown paint operation: " + entry.operation);
        }
    }

    private static void accepted(boolean actual, boolean expected) {
        if (actual != expected) throw new IllegalStateException("Backend layer capability changed: expected " + expected + ", got " + actual);
    }

    static void nativePaint(Canvas canvas, ReplacedContent content, float x, float y, float width, float height) {
        if (canvas instanceof Recorder recorder) recorder.nativeContent(content, x, y, width, height);
        else content.paint(canvas, x, y, width, height);
    }

    /** Exact argument and order comparison, including group acceptance and native content identity. */
    public void assertEquivalent(DisplayList other, String page) {
        DisplayList a = batched(), b = other.batched();
        a.assertExact(b, page);
    }

    private void assertExact(DisplayList other, String page) {
        int common = Math.min(size(), other.size());
        for (int i = 0; i < common; i++) {
            Entry a = entries.get(i), b = other.entries.get(i);
            if (!a.same(b)) throw new IllegalStateException("Paint mismatch " + page + " command " + i + " "
                    + a.element + " " + a.operation + " expected=" + Arrays.deepToString(a.arguments)
                    + " actual=" + b.operation + Arrays.deepToString(b.arguments));
        }
        if (size() != other.size()) throw new IllegalStateException("Paint mismatch " + page + " command count " + size() + " != " + other.size());
    }

    /** Element is an optional substring filter. Native objects are described without inspecting host state. */
    public String json(String element) { return new Gson().toJson(export(element)); }

    public String jsonLines(String element) {
        Gson gson = new Gson();
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> command : export(element)) out.append(gson.toJson(command)).append('\n');
        return out.toString();
    }

    public String text(String element) {
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> command : export(element)) out.append(command.get("command")).append(' ')
                .append(command.get("element")).append(' ').append(command.get("operation")).append(' ')
                .append(Arrays.deepToString((Object[]) command.get("arguments"))).append('\n');
        return out.toString();
    }

    private List<Map<String, Object>> export(String filter) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (filter != null && !entry.element.contains(filter)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("command", i);
            row.put("element", entry.element);
            row.put("operation", entry.operation);
            Object[] args = entry.arguments.clone();
            if (entry.operation.equals("native")) args[0] = args[0].getClass().getName();
            if (entry.operation.equals("drawText")) {
                FontSpec font = (FontSpec) args[3];
                args[3] = Map.of("families", font.families(), "size", font.size(),
                        "bold", font.bold(), "italic", font.italic());
            }
            row.put("arguments", args);
            result.add(row);
        }
        return result;
    }

    /** A tee: backend decisions and shape overrides remain authoritative; recording never draws twice. */
    public static final class Recorder implements Canvas {
        private static final int MAX_COMMANDS = 100_000;
        private final Canvas target;
        private final List<Entry> entries = new ArrayList<>();
        private String element = "<page>";

        public Recorder(Canvas target) { this.target = Objects.requireNonNull(target); }
        public void element(String element) { this.element = Objects.requireNonNull(element); }
        public void skipped(String reason) { add("skip", reason); }
        public DisplayList finish() { return new DisplayList(entries); }

        private void add(String operation, Object... arguments) {
            if (entries.size() == MAX_COMMANDS) throw new IllegalStateException("Paint trace exceeds " + MAX_COMMANDS + " commands at " + element);
            for (int i = 0; i < arguments.length; i++) {
                Object a = arguments[i];
                if (a instanceof float[] array) arguments[i] = array.clone();
                else if (a instanceof int[] array) arguments[i] = array.clone();
                else if (a instanceof List<?> list) arguments[i] = List.copyOf(list);
            }
            entries.add(new Entry(element, operation, arguments));
        }

        public void nativeContent(ReplacedContent content, float x, float y, float width, float height) {
            add("native", content, x, y, width, height);
            nativePaint(target, content, x, y, width, height);
        }

        @Override public int saveCount() { return target.saveCount(); }
        @Override public float devicePixel() { return target.devicePixel(); }
        @Override public boolean backdropNeedsGroup() { return target.backdropNeedsGroup(); }
        @Override public String groupLimits(List<Filter> filters, BlendMode blend) { return target.groupLimits(filters, blend); }

        @Override public void save() {
            add("save");
            target.save();
        }

        @Override public void restore() {
            add("restore");
            target.restore();
        }

        @Override public void translate(float dx, float dy) {
            add("translate", dx, dy);
            target.translate(dx, dy);
        }

        @Override public void transform(float a, float b, float c, float d, float e, float f) {
            add("transform", a, b, c, d, e, f);
            target.transform(a, b, c, d, e, f);
        }

        @Override public void multiplyAlpha(float alpha) {
            add("multiplyAlpha", alpha);
            target.multiplyAlpha(alpha);
        }

        @Override public void clipRect(float x, float y, float width, float height) {
            add("clipRect", x, y, width, height);
            target.clipRect(x, y, width, height);
        }

        @Override public void fillRect(float x, float y, float width, float height, int argb) {
            add("fillRect", x, y, width, height, argb);
            target.fillRect(x, y, width, height, argb);
        }

        @Override public void fillQuads(float[] xy, int[] colors, int quadCount) {
            add("fillQuads", Arrays.copyOf(xy, Math.multiplyExact(quadCount, 8)), Arrays.copyOf(colors, Math.multiplyExact(quadCount, 4)), quadCount);
            target.fillQuads(xy, colors, quadCount);
        }

        @Override public void drawText(String text, float x, float y, FontSpec font, int argb, int decorations, boolean shadow) {
            add("drawText", text, x, y, font, argb, decorations, shadow);
            target.drawText(text, x, y, font, argb, decorations, shadow);
        }

        @Override public void drawImage(String url, float x, float y, float width, float height, float u0, float v0, float u1, float v1, int tint, boolean smooth) {
            add("drawImage", url, x, y, width, height, u0, v0, u1, v1, tint, smooth);
            target.drawImage(url, x, y, width, height, u0, v0, u1, v1, tint, smooth);
        }

        @Override public void drawSprite(String spriteId, float x, float y, float width, float height, int tint) {
            add("drawSprite", spriteId, x, y, width, height, tint);
            target.drawSprite(spriteId, x, y, width, height, tint);
        }

        @Override public void fillRoundedRect(float x, float y, float width, float height, float[] radii, int argb) {
            add("fillRoundedRect", x, y, width, height, radii, argb);
            target.fillRoundedRect(x, y, width, height, radii, argb);
        }

        @Override public void fillBorder(float[] outer, float[] radii, float[] widths, int[] colors) {
            add("fillBorder", outer, radii, widths, colors);
            target.fillBorder(outer, radii, widths, colors);
        }

        @Override public void fillBoxShadow(float[] box, float[] radii, Shadow shadow) {
            add("fillBoxShadow", box, radii, shadow);
            target.fillBoxShadow(box, radii, shadow);
        }

        @Override public void beginMask(MaskComposite composite) {
            add("beginMask", composite);
            target.beginMask(composite);
        }

        @Override public void endGroup(float opacity, List<Filter> filters, BlendMode blend) {
            add("endGroup", opacity, filters, blend);
            target.endGroup(opacity, filters, blend);
        }

        @Override public void endBackdrop(List<Filter> filters) {
            add("endBackdrop", filters);
            target.endBackdrop(filters);
        }

        @Override public void crispShapes(boolean crisp) {
            add("crispShapes", crisp);
            target.crispShapes(crisp);
        }

        @Override public boolean beginGroup() {
            boolean result = target.beginGroup();
            add("beginGroup", result);
            return result;
        }

        @Override public boolean beginPageGroup() {
            boolean result = target.beginPageGroup();
            add("beginPageGroup", result);
            return result;
        }

        @Override public boolean beginBackdrop() {
            boolean result = target.beginBackdrop();
            add("beginBackdrop", result);
            return result;
        }

        @Override public boolean beginGroup(float x, float y, float width, float height) {
            boolean result = target.beginGroup(x, y, width, height);
            add("boundedGroup", x, y, width, height, result);
            return result;
        }
    }
}
