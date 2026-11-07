package dev.oriel.engine.css;

import com.google.gson.Gson;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.testing.TestHost;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Explicit synthetic benchmark, not a timing-sensitive unit test. Emits one JSON object per workload. */
public final class SelectorPerformanceProbe {
    private SelectorPerformanceProbe() {}

    public static void main(String[] args) {
        int count = Integer.parseInt(args[0]), rules = Integer.parseInt(args[1]);
        StringBuilder markup = new StringBuilder("<style>");
        for (int i = 0; i < rules; i++) {
            String selector = switch (i % 3) {
                case 0 -> ".c" + i;
                case 1 -> ".c" + i + ":hover";
                default -> "[data-k" + i + "]";
            };
            markup.append(selector).append("{opacity:").append(i % 2 == 0 ? ".4" : ".8").append("}");
        }
        markup.append("</style><main>");
        for (int i = 0; i < count; i++) markup.append("<p class=c").append(i % rules).append("></p>");
        Document document = Document.parse(new TestHost(), "test:selector-benchmark.html", markup.append("</main>").toString());
        if (document.error() != null) throw new IllegalStateException("Benchmark document failed", document.error());
        document.flushStyle();
        List<Element> elements = document.querySelectorAll("p");
        for (String operation : List.of("class", "hover", "attributes")) {
            long[] samples = new long[15];
            for (int i = 0; i < 25; i++) {
                long start = System.nanoTime();
                mutate(document, elements, operation, i);
                document.flushStyle();
                if (i >= 10) samples[i - 10] = System.nanoTime() - start;
            }
            Arrays.sort(samples);
            System.out.println(new Gson().toJson(Map.of("mode", SelectorAudit.mode().name(),
                    "sourceSha", System.getProperty("oriel.benchSource", "unspecified"), "elements", count,
                    "rules", rules, "operation", operation, "medianMs", samples[7] / 1e6,
                    "p95Ms", samples[14] / 1e6, "rematched", document.styleEngine().rematched)));
        }
        document.close();
    }

    private static void mutate(Document document, List<Element> elements, String operation, int step) {
        switch (operation) {
            case "class" -> elements.getFirst().setAttribute("class", step % 2 == 0 ? "c0" : "c3");
            case "hover" -> document.setHovered(elements.get(1), step % 2 == 0);
            case "attributes" -> {
                for (int i = 0; i < Math.min(elements.size(), 64); i++) {
                    if (step % 2 == 0) elements.get(i).setAttribute("data-k2", "");
                    else elements.get(i).removeAttribute("data-k2");
                }
            }
            default -> throw new IllegalArgumentException(operation);
        }
    }
}
