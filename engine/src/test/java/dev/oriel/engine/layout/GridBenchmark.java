package dev.oriel.engine.layout;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.testing.TestHost;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Opt-in layout-only probe. Run in an exclusive benchmark window, never as part of unit tests. */
public final class GridBenchmark {
    private GridBenchmark() {}

    public static void main(String[] args) {
        String[] modes = args.length == 0 ? new String[] {"old", "new"} : args;
        for (String scenario : new String[] {"fixed", "dense", "spanning", "percent"}) {
            for (int round = 0; round < 5; round++) {
                for (int i = 0; i < modes.length; i++) {
                    String mode = modes[round % 2 == 0 ? i : modes.length - 1 - i];
                    System.setProperty("oriel.layout.grid", mode);
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
        StringBuilder html = new StringBuilder("<div>");
        for (int group = 0; group < 16; group++) {
            String style = switch (scenario) {
                case "fixed" -> "grid-template-columns:repeat(12,35px);grid-auto-rows:12px";
                case "dense" -> "grid-template-columns:repeat(12,1fr);grid-auto-flow:dense";
                case "spanning" -> "grid-template-columns:repeat(12,minmax(10px,auto))";
                default -> "grid-template-columns:repeat(12,1fr);grid-template-rows:50% 50%";
            };
            html.append("<div style='display:grid;width:600px;gap:2px;").append(style).append("'>");
            for (int item = 0; item < 24; item++) {
                int span = scenario.equals("dense") || scenario.equals("spanning") ? item % 3 + 1 : 1;
                html.append("<div style='grid-column:span ").append(span).append(";min-width:0;height:12px'>Text</div>");
            }
            html.append("</div>");
        }
        return html.append("</div>").toString();
    }
}
