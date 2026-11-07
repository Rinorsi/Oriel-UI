package dev.oriel.engine.layout;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.testing.TestHost;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Opt-in layout-only probe. Run in an exclusive benchmark window, never as part of unit tests. */
public final class FlexBenchmark {
    private FlexBenchmark() {}

    public static void main(String[] args) {
        String[] modes = args.length == 0 ? new String[] {"old", "new"} : args;
        for (String scenario : new String[] {"fixed", "wrapped", "columns", "nested"}) {
            for (int round = 0; round < 5; round++) {
                for (int i = 0; i < modes.length; i++) {
                    String mode = modes[round % 2 == 0 ? i : modes.length - 1 - i];
                    System.setProperty("oriel.layout.flex", mode);
                    TestHost host = new TestHost();
                    host.scripting = null;
                    Document doc = host.load(page(scenario), 640, 360).doc;
                    try {
                        for (int n = 0; n < 300; n++) relayout(doc);
                        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                        long thread = Thread.currentThread().threadId();
                        long startBytes = bean.getThreadAllocatedBytes(thread);
                        long[] times = new long[600];
                        for (int n = 0; n < times.length; n++) {
                            long start = System.nanoTime();
                            relayout(doc);
                            times[n] = System.nanoTime() - start;
                        }
                        long bytes = bean.getThreadAllocatedBytes(thread) - startBytes;
                        Arrays.sort(times);
                        System.out.printf(Locale.ROOT,
                                "{\"scenario\":\"%s\",\"mode\":\"%s\",\"round\":%d,\"p50_us\":%.3f,\"p95_us\":%.3f,\"bytes\":%d}%n",
                                scenario, mode, round, times[299] / 1000.0, times[569] / 1000.0, bytes / times.length);
                    } finally { doc.close(); }
                }
            }
        }
    }

    private static void relayout(Document document) {
        document.invalidateLayout();
        document.flushLayout();
    }

    private static String page(String scenario) {
        StringBuilder html = new StringBuilder("<div style='display:flex;flex-direction:column'>");
        for (int row = 0; row < 24; row++) {
            String style = switch (scenario) {
                case "fixed" -> "display:flex;height:20px;gap:2px";
                case "wrapped" -> "display:flex;flex-wrap:wrap;gap:3px;width:260px;align-content:center";
                case "columns" -> "display:flex;flex-direction:column;max-height:180px;flex-wrap:wrap;gap:2px";
                default -> "display:flex;gap:2px;align-items:baseline";
            };
            html.append("<div style='").append(style).append("'>");
            for (int item = 0; item < 12; item++) {
                if (scenario.equals("nested")) html.append("<div style='display:flex;flex-direction:column;flex:1;min-width:0'><span>Text 中文</span><span>second line</span></div>");
                else html.append("<div style='width:35px;height:12px;flex-shrink:0'></div>");
            }
            html.append("</div>");
        }
        return html.append("</div>").toString();
    }
}
