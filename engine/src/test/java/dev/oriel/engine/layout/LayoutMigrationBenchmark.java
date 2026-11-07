package dev.oriel.engine.layout;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.paint.Coordinates;
import dev.oriel.engine.testing.TestHost;

import java.util.Arrays;
import java.util.Locale;

/** Standalone alternating benchmark. Run only in the workspace benchmark window, never as a JUnit test. */
public final class LayoutMigrationBenchmark {
    private LayoutMigrationBenchmark() {}

    public static void main(String[] args) {
        int rounds = args.length == 0 ? 5 : Integer.parseInt(args[0]);
        for (int count : new int[] {1000, 5000, 20000}) {
            for (int round = 0; round < rounds; round++) {
                for (String mode : round % 2 == 0 ? new String[] {"old", "new"} : new String[] {"new", "old"}) {
                    run(count, mode, round);
                }
            }
        }
    }

    private static void run(int count, String mode, int round) {
        System.setProperty("oriel.layout.block", mode);
        StringBuilder html = new StringBuilder("<style>.row{height:16px;width:75%;margin:2px 0}.view{height:240px;overflow:auto}</style><div id=view class=view>");
        for (int i = 0; i < count; i++) html.append("<div class=row>row content</div>");
        html.append("</div>");
        TestHost host = new TestHost();
        host.scripting = null;
        Document document = host.load(html.toString()).doc;
        Element view = document.getElementById("view");
        Element target = view.children().getLast();
        target.setAttribute("style", "transform:translateX(0px)");
        document.flushLayout();
        for (String scenario : new String[] {"layout", "scroll", "transform"}) {
            long[] samples = new long[80];
            for (int i = -40; i < samples.length; i++) {
                long begin = System.nanoTime();
                if (scenario.equals("layout")) {
                    document.invalidateLayout();
                    document.flushLayout();
                } else {
                    if (scenario.equals("scroll")) view.scrollTo(0, (i + 40) * 3);
                    else target.setAttribute("style", "transform:translateX(" + (i & 15) + "px)");
                    Box before = document.layoutEngine().root();
                    document.flushLayout();
                    Coordinates.boundingRect(target.box);
                    if (before != document.layoutEngine().root()) throw new AssertionError("Fast path triggered layout");
                }
                long elapsed = System.nanoTime() - begin;
                if (i >= 0) samples[i] = elapsed;
            }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT, "{\"nodes\":%d,\"round\":%d,\"mode\":\"%s\",\"scenario\":\"%s\",\"p50Ms\":%.6f,\"p95Ms\":%.6f}%n",
                    count, round, mode, scenario, (samples[39] / 2.0 + samples[40] / 2.0) / 1e6, samples[75] / 1e6);
        }
        document.close();
    }
}
