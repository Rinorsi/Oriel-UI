package dev.oriel.engine.script;

import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcosystemShimTest extends EcosystemModeTestSupport {
    private static Page page() throws IOException {
        Page page = new TestHost().load("<canvas id=c width=8 height=8></canvas>");
        page.run(Files.readString(Path.of("../kit/src/main/resources/assets/oriel/ui/oriel-shims.js")));
        page.run("var ctx = OrielShims.quadContext(document.getElementById('c')); ctx.fillStyle='rgba(255,0,0,0.5)';");
        return page;
    }

    @Test
    void bridgeIsLocalAndDiagnosticsAreExplicit() throws IOException {
        Page page = page();
        assertEquals("undefined", page.eval("typeof document.createElement('canvas').getContext('2d').beginPath"));
        assertEquals("true true four-point-fill-only", page.eval("(function(){var c=OrielShims.capabilities();return [c.htmlNamespace,c.reactIframeProbe,c.canvas].join(' ')})()"));
        assertTrue(page.host.logs.isEmpty(), "import and capability inspection must not log by default");
    }

    @Test
    void roundedConcaveAndCrossedPathsFillWithoutChangingNativeContract() throws IOException {
        Page page = page();
        page.run("ctx.beginPath(); ctx.moveTo(0,0); ctx.lineTo(6,0); ctx.lineTo(2,2); ctx.lineTo(0,6); ctx.fill();");
        assertEquals("128", page.eval("ctx.getImageData(1,1,1,1).data[3]"));
        assertEquals("0", page.eval("ctx.getImageData(5,5,1,1).data[3]"));
        page.run("ctx.clearRect(0,0,8,8); ctx.beginPath(); ctx.moveTo(0,0); ctx.lineTo(6,6); ctx.lineTo(0,6); ctx.lineTo(6,0); ctx.fill();");
        assertEquals("128 128 0", page.eval("[ctx.getImageData(2,0,1,1).data[3],ctx.getImageData(2,5,1,1).data[3],ctx.getImageData(0,3,1,1).data[3]].join(' ')"));
        assertEquals("TypeError", page.eval("(function(){try{ctx.fillQuad(0,0,6,0,2,2,0,6)}catch(e){return e.name}})()"));
    }

    @Test
    void triangleJoinDoesNotApplyAlphaTwice() throws IOException {
        Page page = page();
        page.run("ctx.beginPath();ctx.moveTo(0,0);ctx.lineTo(6,0);ctx.lineTo(2,2);ctx.lineTo(0,6);ctx.fill();");
        assertEquals("true", page.eval("Array.prototype.every.call(ctx.getImageData(0,0,8,8).data,function(v,i){return i%4!==3||v===0||v===128})"));
    }

    @Test
    void roundedRetracedEdgesHaveNoArea() throws IOException {
        Page page = page();
        page.run("ctx.beginPath();ctx.moveTo(0,0);ctx.lineTo(6,0);ctx.lineTo(0,0);ctx.lineTo(0,6);ctx.fill();");
        assertEquals("true", page.eval("Array.prototype.every.call(ctx.getImageData(0,0,8,8).data,function(v){return v===0})"));
    }

    @Test
    void bridgeRejectsGeneralPathsAndUnsupportedConfettiOptions() throws IOException {
        Page page = page();
        assertEquals("Error", page.eval("(function(){try{ctx.beginPath();ctx.lineTo(Infinity,0)}catch(e){return e.name}})()"));
        assertEquals("Error", page.eval("(function(){try{ctx.fill()}catch(e){return e.name}})()"));
        page.run("var resets=0,options;var fire=OrielShims.squareConfetti(document.getElementById('c'),{create:function(){var fn=function(o){options=o};fn.reset=function(){resets++};return fn}});fire({particleCount:8});");
        assertEquals("true square 8", page.eval("[options.flat,options.shapes[0],options.particleCount].join(' ')"));
        assertEquals("Error", page.eval("(function(){try{fire({shapes:['circle']})}catch(e){return e.name}})()"));
        page.run("fire.dispose();fire.dispose();");
        assertEquals("1", page.eval("resets"));
        assertEquals("Error", page.eval("(function(){try{fire()}catch(e){return e.name}})()"));
    }
}
