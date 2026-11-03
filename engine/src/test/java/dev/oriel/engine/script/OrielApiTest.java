package dev.oriel.engine.script;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrielApiTest {
    @Test
    void sendSerialisesJson() {
        Page page = new TestHost().load("<script>oriel.send('buy', {item: 'minecraft:apple', count: 2})</script>");
        assertArrayEquals(new String[] {"buy", "{\"item\":\"minecraft:apple\",\"count\":2}"}, page.host.sent.getFirst());
    }

    @Test
    void sendReturnsWhetherTheHostTookTheMessage() {
        TestHost host = new TestHost();
        host.sendLimit = 2;
        Page page = host.load("<script>var results = []; for (let i = 0; i < 3; i++) results.push(oriel.send('spam', i));</script>");
        assertEquals("true,true,false", page.eval("results.join()"));
        assertEquals(2, page.host.sent.size());
    }

    @Test
    void receivedDataReplacesOrielDataAndNotifiesListeners() {
        Page page = new TestHost().load("""
                <script>
                const log = [];
                oriel.on('data', d => log.push('data ' + d.hp));
                const stop = oriel.on('toast', msg => log.push('toast ' + msg.text));
                </script>""");
        assertEquals("{}", page.eval("JSON.stringify(oriel.data)"));
        page.doc.receive("data", "{\"hp\": 20}");
        page.doc.receive("toast", "{\"text\": \"hi\"}");
        page.run("stop()");
        page.doc.receive("toast", "{\"text\": \"ignored\"}");
        assertEquals("data 20,toast hi 20", page.eval("log.join() + ' ' + oriel.data.hp"));
    }

    @Test
    void aPatchSetsOneFieldInPlaceThenTellsPatchAndDataListeners() {
        Page page = new TestHost().recordErrors().load("""
                <script>
                const log = [];
                oriel.on('patch', p => log.push('patch ' + p.key + '=' + JSON.stringify(p.value)));
                oriel.on('data', (d, key) => log.push('data ' + key + ' ' + JSON.stringify(d)));
                </script>""");
        page.doc.receive("data", "{\"hp\": 20, \"name\": \"Steve\", \"stats\": {\"kills\": 3}}");
        page.run("var before = oriel.data, stats = oriel.data.stats; log.length = 0");
        page.doc.patch("hp", "7");
        page.doc.patch("name", null);
        page.doc.patch("pos", "[1, 2]");
        assertEquals(List.of(
                "patch hp=7", "data hp {\"hp\":7,\"name\":\"Steve\",\"stats\":{\"kills\":3}}",
                "patch name=undefined", "data name {\"hp\":7,\"stats\":{\"kills\":3}}",
                "patch pos=[1,2]", "data pos {\"hp\":7,\"stats\":{\"kills\":3},\"pos\":[1,2]}"),
                List.of(page.eval("log.join('|')").split("\\|")));
        assertEquals("true true false", page.eval("[oriel.data === before, oriel.data.stats === stats, 'name' in oriel.data].join(' ')"),
                "the data object and the fields not patched are the same objects");
        assertEquals("", page.errors());
    }

    @Test
    void aPatchOnDataThatIsNotAnObjectStartsANewOne() {
        Page page = new TestHost().load("");
        page.doc.receive("data", "[1, 2]");
        page.doc.patch("a", "1");
        assertEquals("{\"a\":1}", page.eval("JSON.stringify(oriel.data)"));
        page.doc.receive("data", "42");
        page.doc.patch("gone", null);
        assertEquals("{}", page.eval("JSON.stringify(oriel.data)"));
    }

    @Test
    void aMalformedPatchIsReportedAndChangesNothing() {
        Page page = new TestHost().recordErrors().load("<script>let calls = 0; oriel.on('patch', () => calls++)</script>");
        page.doc.receive("data", "{\"hp\": 20}");
        page.doc.patch("hp", "{nope");
        assertTrue(page.errors().contains("Error in patch 'hp': SyntaxError"), page.errors());
        assertEquals("20 0", page.eval("oriel.data.hp + ' ' + calls"));
    }

    @Test
    void badJsonIsReported() {
        Page page = new TestHost().recordErrors().load("");
        page.doc.receive("data", "{nope");
        assertTrue(page.errors().contains("Error in message 'data': SyntaxError"), page.errors());
    }

    @Test
    void hostCalls() {
        Page page = new TestHost().load("""
                <script>
                oriel.playSound('minecraft:ui.button.click', 0.5, 2);
                var label = oriel.t('gui.done', 'x');
                oriel.close();
                </script>""");
        assertEquals(List.of("minecraft:ui.button.click"), page.host.sounds);
        assertEquals("gui.done", page.eval("label"));
        assertTrue(page.host.closed);
    }

    @Test
    void closingFiresPagehideThenUnloadWhileScriptsStillRun() {
        Page page = new TestHost().load("""
                <script>
                addEventListener('pagehide', () => oriel.send('bye', 'pagehide'));
                window.addEventListener('unload', () => {
                  oriel.send('bye', 'unload');
                  setTimeout(() => oriel.send('bye', 'too late'), 0);
                });
                </script>""");
        page.doc.close();
        assertEquals(List.of("pagehide", "unload"), page.host.sent.stream().map(m -> m[1].replace("\"", "")).toList());
        page.doc.frame(100);
        assertEquals(2, page.host.sent.size(), "timers die with the document");
    }
}
