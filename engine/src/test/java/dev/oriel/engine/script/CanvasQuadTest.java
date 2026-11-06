package dev.oriel.engine.script;

import dev.oriel.engine.host.ArraySurface;
import dev.oriel.engine.replaced.CanvasContent;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanvasQuadTest {
    @Test
    void nativeQuadsMatchScriptScanlinesThroughRotationClippingAndAlpha() {
        Page page = new TestHost().load("""
                <canvas id=a width=64 height=48></canvas><canvas id=b width=64 height=48></canvas>
                <script>
                const a=document.getElementById('a').getContext('2d');
                const b=document.getElementById('b').getContext('2d');
                function scan(p) {
                    const lo=Math.max(0,Math.floor(Math.min(p[0][1],p[1][1],p[2][1],p[3][1])));
                    const hi=Math.min(48,Math.ceil(Math.max(p[0][1],p[1][1],p[2][1],p[3][1])));
                    for(let y=lo;y<hi;y++){
                        const xs=[];
                        for(let i=0;i<4;i++){const u=p[i],v=p[(i+1)%4];
                            if((u[1]<=y+.5&&v[1]>y+.5)||(v[1]<=y+.5&&u[1]>y+.5))
                                xs.push(u[0]+(y+.5-u[1])*(v[0]-u[0])/(v[1]-u[1]));
                        }
                        if(xs.length>=2){const l=Math.min.apply(null,xs),r=Math.max.apply(null,xs);b.fillRect(l,y,r-l,1);}
                    }
                }
                function draw(i){
                    a.clearRect(0,0,64,48);b.clearRect(0,0,64,48);
                    a.fillStyle=b.fillStyle=i%2?'#3698':'#0000';a.globalAlpha=b.globalAlpha=1;
                    a.fillRect(0,0,64,48);b.fillRect(0,0,64,48);
                    a.fillStyle=b.fillStyle='#fac080b0';a.globalAlpha=b.globalAlpha=(i%11)/10;
                    const angle=i*.231,c=Math.cos(angle),s=Math.sin(angle),x=i%80-10,y=i%60-10;
                    const p=[[-12,-7],[12,-7],[12,7],[-12,7]].map(v=>[x+v[0]*c-v[1]*s,y+v[0]*s+v[1]*c]);
                    if(i%2)p.reverse();
                    a.fillQuad(p[0][0],p[0][1],p[1][0],p[1][1],p[2][0],p[2][1],p[3][0],p[3][1]);
                    scan(p);
                }
                </script>
                """);
        for (int i = 0; i < 240; i++) {
            page.eval("draw(" + i + ")");
            assertArrayEquals(pixels(page, "b"), pixels(page, "a"), "rotation " + i);
        }
        assertFalse(page.eval("typeof a.fillQuad").equals("undefined"));
    }

    @Test
    void invalidQuadsFailAndDegenerateQuadsDoNotPaint() {
        Page page = new TestHost().load("<canvas id=a width=8 height=8></canvas>");
        page.run("var c=document.getElementById('a').getContext('2d');");
        assertEquals("rejected", page.eval("(()=>{try { c.fillQuad(0,0,8,8,0,8,8,0); return 'accepted'; } catch(e) { return 'rejected'; }})()"));
        assertEquals("rejected", page.eval("(()=>{try { c.fillQuad(0,0,8,0,8,Infinity,0,8); return 'accepted'; } catch(e) { return 'rejected'; }})()"));
        page.eval("c.fillQuad(1,1,2,2,3,3,4,4)");
        assertArrayEquals(new int[64], pixels(page, "a"));
    }

    private static int[] pixels(Page page, String id) {
        return ((ArraySurface) ((CanvasContent) page.doc.replacedContent(page.byId(id))).surface()).pixels();
    }
}
