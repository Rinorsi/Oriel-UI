package dev.oriel.engine.input;

import dev.oriel.engine.event.Event;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.layout.TextMeasure;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Standalone synthetic measurements. Run under the workspace benchmark lock, never as a unit test. */
public final class InputBenchmark {
    private static volatile long blackhole;
    private InputBenchmark() {}

    public static void main(String[] args) {
        int rounds = args.length == 0 ? 5 : Integer.parseInt(args[0]);
        for (int round = 0; round < rounds; round++) {
            List<String> modes = round % 2 == 0 ? List.of("old", "new") : List.of("new", "old");
            for (String mode : modes) {
                System.setProperty("oriel.inputBackend", mode);
                Page grid = new TestHost().load("<style>div{width:120px;height:2px}</style>" + "<div></div>".repeat(5000));
                measure(round, mode, "hit-5000", 200, () -> {
                    for (int i = 0; i < 200; i++) { var hit = grid.input.hit(40, i * 47 % 10000); if (hit != null) blackhole += hit.element().nodeName().length(); }
                });
                Page page = new TestHost().load("<div id=p><input id=t></div>");
                var target = page.byId("t");
                page.doc.addEventListener("probe", e -> blackhole++);
                measure(round, mode, "dispatch", 10000, () -> {
                    for (int i = 0; i < 10000; i++) target.dispatchEvent(new Event("probe"));
                });
                FontMetrics fonts = (text, font) -> {
                    double total = 0;
                    for (int i = 0; i < text.length(); i++) for (int j = 0; j < 30; j++) total += Math.sqrt(text.charAt(i) + j);
                    return (float) total;
                };
                TextMeasure text = new TextMeasure(fonts);
                ComputedStyle style = new ComputedStyle();
                FontSpec font = FontSpec.of(style);
                String content = "a long textarea 中文 é 👩‍👩‍👧‍👦 ".repeat(150);
                measure(round, mode, "long-text-layout", 20, () -> {
                    for (int i = 0; i < 20; i++) blackhole += new TextLayout(content, false, 300, text, font, style).lineCount();
                });
                TextLayout layout = new TextLayout("a".repeat(12000), false, Float.POSITIVE_INFINITY, text, font, style);
                measure(round, mode, "caret-hit", 1000, () -> {
                    for (int i = 0; i < 1000; i++) blackhole += layout.offsetAt(0, i * 7919 % 1000000);
                });
                measure(round, mode, "textarea-edit", 200, () -> {
                    TextEditor editor = new TextEditor(true);
                    editor.reset("hello world ".repeat(400));
                    for (int i = 0; i < 200; i++) {
                        editor.moveHorizontally(false, false, false);
                        editor.apply(editor.planInsert("x", "insertText"));
                    }
                    blackhole += editor.text().length();
                });
            }
        }
    }

    private static void measure(int round, String mode, String name, int operations, Runnable task) {
        for (int i = 0; i < 15; i++) task.run();
        long[] samples = new long[31];
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime();
            task.run();
            samples[i] = System.nanoTime() - start;
        }
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT, "{\"round\":%d,\"mode\":\"%s\",\"case\":\"%s\",\"p50Ns\":%.3f,\"p95Ns\":%.3f,\"bytesPerOp\":%.3f}%n",
                round, mode, name, (double) samples[15] / operations, (double) samples[29] / operations,
                (double) allocated / samples.length / operations);
    }
}
