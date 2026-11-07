package dev.oriel.engine.script;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Run with the hash-verified external fixtures prepared by tools/ecosystem/prepare.mjs. */
@EnabledIfEnvironmentVariable(named = "ORIEL_ECOSYSTEM_LIBS", matches = ".+")
class EcosystemLibrariesTest extends EcosystemModeTestSupport {
    private static Page load(String... libraries) throws IOException {
        TestHost host = new TestHost();
        StringBuilder html = new StringBuilder("<div id=app></div><div id=box></div><canvas id=c width=120 height=80></canvas>");
        for (String library : libraries) {
            host.resource("test:" + library + ".js", Files.readString(Path.of(System.getenv("ORIEL_ECOSYSTEM_LIBS"), library + ".js")));
            html.append("<script src='").append(library).append(".js'></script>");
        }
        Page page = host.load(html.toString());
        page.run("var app=document.getElementById('app'),box=document.getElementById('box');");
        return page;
    }

    @Test
    void reactHooksClickAndKeyedList() throws IOException {
        Page page = load("react", "react-extra");
        page.run("""
                function App(){var s=React.useState(0);return React.createElement('div',null,
                  React.createElement('button',{onClick:function(){s[1](s[0]+1)}},'count '+s[0]),
                  React.createElement('ul',null,[1,2,3].map(function(x){return React.createElement('li',{key:x},'item '+x)})))}
                var root=ReactDOM.createRoot(app);
                ReactDOM.flushSync(function(){root.render(React.createElement(App))});
                app.querySelector('button').click();
                """);
        page.frame(32).frame(160);
        assertEquals("count 1item 1item 2item 3", page.eval("app.textContent"));
        page.run("root.unmount()");
        assertEquals("0", page.eval("app.childNodes.length"));
    }

    @Test
    void preactHtmControlledInputAndList() throws IOException {
        Page page = load("preact", "preact-extra");
        page.run("""
                var html=htm.bind(preact.h),name='Alex';
                function render(){preact.render(html`<div><input value=${name} onInput=${function(e){name=e.target.value;render()}}/><p>${name}</p><ul>${[1,2,3].map(function(i){return html`<li key=${i}>item ${i}</li>`})}</ul></div>`,app)}
                render();var field=app.querySelector('input');field.value='Steve';field.dispatchEvent(new Event('input',{bubbles:true}));
                """);
        page.frame();
        assertEquals("Steveitem 1item 2item 3", page.eval("app.textContent"));
        assertEquals("3", page.eval("app.querySelectorAll('li').length"));
    }

    @Test
    void gsapTimelineStaggerEaseAndCleanup() throws IOException {
        Page page = load("gsap");
        page.run("""
                var a={x:0},b={x:0},done=0;
                var timeline=gsap.timeline({paused:true,onComplete:function(){done++}});
                timeline.to([a,b],{x:100,duration:1,stagger:0.25,ease:'power2.inOut'}).to(a,{x:200,duration:0.5});
                timeline.totalTime(0.5);var first=a.x;timeline.totalTime(timeline.totalDuration());
                """);
        assertEquals("50 200 100 1", page.eval("[first,a.x,b.x,done].join(' ')"));
        page.run("var domTimeline=gsap.timeline({paused:true}).to(box,{x:80,opacity:0.5,duration:1,ease:'none'});domTimeline.seek(1);");
        assertTrue(page.eval("box.style.transform").contains("80px"));
        assertEquals("0.5", page.eval("box.style.opacity"));
        page.run("timeline.kill();domTimeline.kill();gsap.ticker.sleep();");
        page.doc.close();
    }

    @Test
    void animeSequenceAndDomTransforms() throws IOException {
        Page page = load("anime");
        page.run("""
                var sequence=anime.timeline({autoplay:false,easing:'linear'});
                sequence.add({targets:box,translateX:40,opacity:0.5,duration:100})
                  .add({targets:box,translateX:80,duration:100});
                sequence.seek(200);
                """);
        assertTrue(page.eval("box.style.transform").contains("80px"));
        assertEquals("0.5", page.eval("box.style.opacity"));
        page.run("sequence.pause();anime.remove(box)");
    }

    @Test
    void confettiUsesActualNativeQuadPixelsAndCleansUp() throws IOException {
        Page page = load("confetti");
        page.run(Files.readString(Path.of("../kit/src/main/resources/assets/oriel/ui/oriel-shims.js")));
        page.run("var fire=OrielShims.squareConfetti(document.getElementById('c'),confetti);fire({particleCount:24,ticks:30,startVelocity:3,origin:{x:0.5,y:0.5}});");
        for (int frame = 1; frame <= 12; frame++) page.frame(frame * 17);
        assertEquals("true", page.eval("Array.prototype.some.call(document.getElementById('c').getContext('2d').getImageData(0,0,120,80).data,function(v,i){return i%4===3&&v>0})"));
        page.run("fire.dispose();");
        page.doc.close();
    }

    @Test
    void pureLogicAndMithrilRemainUsable() throws IOException {
        Page page = load("tween", "dayjs", "mithril");
        page.run("var model={x:0};new TWEEN.Tween(model).to({x:100},100).start(0);TWEEN.update(120);var n=0;function render(){m.render(app,m('button',{onclick:function(){n++;render()}},'count '+n))}render();app.firstChild.click();");
        assertEquals("100 2026-11-08 count 1", page.eval("[model.x,dayjs('2026-11-07').add(1,'day').format('YYYY-MM-DD'),app.textContent].join(' ')"));
    }

    @Test
    void markedWithExplicitSyntaxLoweringParsesTrustedMarkdown() throws IOException {
        Page page = load("marked-compat");
        page.run("app.innerHTML=marked.parse('# Hello'+String.fromCharCode(10,10)+'**world**')");
        assertEquals("Hello", page.eval("app.querySelector('h1').textContent"));
        assertEquals("world", page.eval("app.querySelector('strong').textContent"));
    }

    @Test
    void bundledNanostoresAndD3Computations() throws IOException {
        Page page = load("nano-compute", "d3-compute");
        page.run("var atom=nanoAtom(0);atom.set(1);var scale=d3ScaleLinear().domain([0,10]).range([0,100]);");
        assertEquals("1 50 M0,0L10,20", page.eval("[atom.get(),scale(5),d3Line()([[0,0],[10,20]])].join(' ')"));
    }
}
