package dev.oriel.engine.script;

import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanvasLineBindingsTest {
    @Test
    void narrowLineAndCompositeAreBoundAndInvalidModesThrowTypeError() {
        var page = new TestHost().load("""
                <canvas id=c width=8 height=8></canvas><script>
                const c=document.getElementById('c').getContext('2d');
                c.strokeStyle='#ff000080';c.lineWidth=2;c.globalCompositeOperation='lighter';
                c.strokeLine(2,3,5,3,'round');c.strokeLine(2,3,5,3,'round');
                const p=c.getImageData(2,2,1,1).data;
                document.documentElement.setAttribute('result',p[0]+','+p[3]);
                try{c.globalCompositeOperation='xor';}catch(e){document.documentElement.setAttribute('error',e.name);}
                </script>
                """);
        assertEquals("255,255", page.doc.documentElement().getAttribute("result"));
        assertEquals("TypeError", page.doc.documentElement().getAttribute("error"));
    }
}
