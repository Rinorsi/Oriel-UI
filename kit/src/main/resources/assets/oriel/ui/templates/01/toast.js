// Native animation timelines; no per-frame JS layout reads or DOM mutation loop.
// Source: video/studio/src/gallery/fx/mcd5/toast.tsx (30 fps).
// Continuous interpolation is intentional for the in-game version.
var animations=[],timers=[],holdAnimations=[],holdTimers=[];
var holding=false,completed=false,quiet=false;
var OUT='cubic-bezier(0.16,1,0.3,1)',IN='cubic-bezier(0.7,0,0.84,0)',MS=1000/30;
function el(id){return document.getElementById(id);}
function run(node,frames,options){var a=node.animate(frames,options);animations.push(a);return a;}
function later(fn,ms){var id=setTimeout(fn,ms);timers.push(id);return id;}
function clearTimers(list){list.forEach(function(id){clearTimeout(id);});list.length=0;}
function cancel(list){list.forEach(function(a){a.cancel();});list.length=0;}
function flash(id,frames,delay){
 return run(el(id),[
  {offset:0,opacity:0,transform:'scale(0.08)'},
  {offset:.12,opacity:1,transform:'scale(0.7)'},
  {offset:.4,opacity:1,transform:'scale(1)'},
  {offset:1,opacity:0,transform:'scale(0.85)'}
 ],{duration:frames*MS,delay:delay||0,easing:'linear',fill:'both'});
}
function slide(id,at,hold,distance){
 var total=9+hold+9,leave=9+hold;
 return run(el(id),[
  {offset:0,opacity:0,transform:'translateX('+distance+'px)'},
  {offset:.001,opacity:1,transform:'translateX('+distance+'px)',easing:OUT},
  {offset:9/total,opacity:1,transform:'translateX(-4.6667px)',easing:'ease-out'},
  {offset:12/total,opacity:1,transform:'translateX(0px)'},
  {offset:leave/total,opacity:1,transform:'translateX(0px)',easing:IN},
  {offset:(leave+7)/total,opacity:1,transform:'translateX('+distance+'px)'},
  {offset:1,opacity:0,transform:'translateX('+distance+'px)'}
 ],{duration:total*MS,delay:at*MS,fill:'both'});
}
function shine(id,at,duration,width){
 run(el(id),[{opacity:1,transform:'translateX(0px)'},{opacity:1,transform:'translateX('+(width*1.4)+'px)'}],{duration:duration*MS,delay:at*MS,easing:'linear',fill:'none'});
}
function random(seed){return function(){seed=(seed+0x6d2b79f5)>>>0;var t=seed;t=Math.imul(t^(t>>>15),t|1);t^=t+Math.imul(t^(t>>>7),t|61);return ((t^(t>>>14))>>>0)/4294967296;};}
var rnd=random(5*131+7),particles=[];
for(var i=0;i<16;i++){
 var angle=rnd()*Math.PI*2,speed=(6+rnd()*14)*.8,life=24*(.6+rnd()*.4),size=Math.max(4,Math.round(10*(.5+rnd()*.8)/4)*4);
 var color=['#fcd448','#fff6c0','#d8a943'][Math.floor(rnd()*3)];
 var dot=document.createElement('i');dot.className='particle';dot.style.width=(size/3)+'px';dot.style.height=(size/3)+'px';dot.style.background=color;
 if(size>=8)dot.style.boxShadow='inset 1px 1px 0 #ffffff88';el('particles').appendChild(dot);
 var keys=[];
 for(var k=0;k<=12;k++){
  var t=life*k/12,q=1-Math.pow(1-t/life,2),x=Math.cos(angle)*speed*life*.55*q/3;
  var y=(Math.sin(angle)*speed*life*.55*q*.65-speed*life*.12*q*.35+.55*t*t*.5)/3;
  keys.push({offset:k/12,transform:'translate('+x+'px,'+y+'px)',opacity:k===12?0:(k>8&&k%2===0?0:1)});
 }
 particles.push({node:dot,keys:keys,life:life});
}
function releaseHold(){
 if(completed)return;
 holding=false;clearTimers(holdTimers);cancel(holdAnimations);
 el('hold-key-face').style.transform='translateY(0px)';el('hold-key').classList.remove('filled');
}
function finishHold(){
 if(!holding||completed)return;
 holding=false;completed=true;clearTimers(holdTimers);
 el('skip-panel').classList.add('done');
 if(!quiet)flash('done-flash',10,0);
 // Retain the white key, white rail and gold caption before sliding away.
 later(function(){
  run(el('skip'),[{opacity:1,transform:'translateY(0px)'},{opacity:0,transform:'translateY(13px)'}],{duration:quiet?1:8*MS,easing:IN,fill:'forwards'});
 },6*MS);
}
function beginHold(){
 if(holding||completed)return;
 holding=true;
 el('hold-key-face').style.transform='translateY(2px)';
 holdAnimations.push(run(el('hold-fill'),[{transform:'scaleX(0)'},{transform:'scaleX(1)'}],{duration:62*MS,easing:'linear',fill:'forwards'}));
 holdAnimations.push(run(el('key-fill'),[{transform:'scaleY(0)'},{transform:'scaleY(1)'}],{duration:62*MS,easing:'linear',fill:'forwards'}));
 holdTimers.push(later(function(){el('hold-key').classList.add('filled');},62*MS*.55));
 holdTimers.push(later(finishHold,62*MS));
}
function stop(){clearTimers(timers);clearTimers(holdTimers);cancel(animations);holdAnimations=[];holding=false;}
function replay(){
 stop();completed=false;quiet=getComputedStyle(document.documentElement).getPropertyValue('--reduce').trim()==='1';
 el('skip-panel').classList.remove('done');el('hold-key').classList.remove('filled');el('hold-key-face').style.transform='translateY(0px)';
 ['achievement','tip','skip'].forEach(function(id){el(id).style.opacity=quiet?'1':'0';});
 if(quiet)return;
 slide('achievement',8,64,300);slide('tip',40,70,800/3);
 run(el('skip'),[{opacity:0,transform:'translateY(20px)'},{opacity:1,transform:'translateY(0px)'}],{delay:30*MS,duration:8*MS,easing:OUT,fill:'both'});
 shine('shine-first',19,16,272);shine('shine-second',51,16,272);shine('shine-tip',51,14,244);
 flash('land-flash',12,17*MS);
 particles.forEach(function(p){run(p.node,p.keys,{duration:p.life*MS,delay:17*MS,fill:'none',easing:'linear'});});
 run(el('map-key-face'),[{offset:0,transform:'translateY(0px)'},{offset:.12,transform:'translateY(2px)'},{offset:.88,transform:'translateY(2px)'},{offset:1,transform:'translateY(0px)'}],{duration:6*MS,delay:62*MS,easing:'linear'});
}
window.addEventListener('keydown',function(e){
 if(e.repeat)return;
 if(e.key==='r'||e.key==='R'){e.preventDefault();replay();}
 if(e.key==='e'||e.key==='E'){e.preventDefault();beginHold();}
 if(e.key==='Escape'){e.preventDefault();location.href='oriel:ui/styles.html';}
});
window.addEventListener('keyup',function(e){if(e.key==='e'||e.key==='E')releaseHold();});
window.addEventListener('blur',releaseHold);window.addEventListener('pagehide',stop);window.addEventListener('unload',stop);
/* FX-LEGACY-BEGIN */
var F=typeof F==='number'?F:MS,anims=animations,comp=document.body,nodes={},meta={},FX_ART={};
var BACK=typeof BACK==='string'?BACK:'cubic-bezier(.34,1.56,.64,1)';
function clamp(t){return Math.max(0,Math.min(1,t));}
function bez(t,a,b,c,d){var lo=0,hi=1,u=0;for(var i=0;i<16;i++){u=(lo+hi)/2;var x=3*(1-u)*(1-u)*u*a+3*(1-u)*u*u*c+u*u*u;if(x<t)lo=u;else hi=u;}return 3*(1-u)*(1-u)*u*b+3*(1-u)*u*u*d+u*u*u;}
function out(t){return bez(clamp(t),.16,1,.3,1);}function back(t){return bez(clamp(t),.34,1.56,.64,1);}
function div(){throw Error('Legacy effects use adopt only');}function piece(){throw Error('Legacy effects use adopt only');}function track(){throw Error('Legacy effects use adopt only');}function anim(){throw Error('Legacy effects use adopt only');}function after(fn,f){return setTimeout(fn,f*F);}

/* Page-side Canvas backend. Captures only decorative helpers; layout, text and input stay native. */
var FX=(function(){
 var scope=null,layers=[],jobs=[],raf=0,clock=0,serial=0,cache={},drawX=160,drawY=90,identity=[1,0,0,1,0,0];
 var nativeDiv=div,nativePiece=piece,nativeTrack=track,nativeAnim=anim,nativeAfter=after,nativeStop=stop,nativeClear=clearTimeout;
 function node(parent,x,y,w,h){var n={fx:true,style:{left:(x||0)/3+'px',top:(y||0)/3+'px',width:(w||0)/3+'px',height:(h||0)/3+'px'},children:[],tracks:[],parentElement:parent,removed:false,appendChild:function(c){c.parentElement=this;this.children.push(c);},remove:function(){this.removed=true;},setAttribute:function(k,v){this[k]=v;}};n.style.setProperty=function(k,v){this[k]=v;};parent.children.push(n);return n;}
 function alive(el){for(var p=el;p;p=p.parentElement)if(p===document.body||p===document.documentElement)return true;return false;}
 function layer(parent){for(var i=0;i<layers.length;i++)if(layers[i].parent===parent&&layers[i].canvas.parentElement===parent)return layers[i];var full=parent===comp||parseFloat(parent.style.width)>=639,ox=full?0:160,oy=full?0:90,c=document.createElement('canvas');c.width=full?1280:1920;c.height=full?720:1080;c.style.position='absolute';c.style.left=-ox+'px';c.style.top=-oy+'px';c.style.width=c.width/2+'px';c.style.height=c.height/2+'px';c.style.pointerEvents='none';parent.appendChild(c);var l={ox:ox,oy:oy,parent:parent,canvas:c,ctx:c.getContext('2d'),children:[],direct:[]};layers.push(l);return l;}
 function run(s,fn){var old=scope;scope=s;try{return fn();}finally{scope=old;}}
 function wake(){if(!raf)raf=requestAnimationFrame(frame);}
 function schedule(s,fn,delay){var j={fxJob:true,id:++serial,at:performance.now()+(quiet?0:delay)*F,scope:s,fn:fn,cancelled:false};jobs.push(j);wake();return j;}
 function capture(fn,args){if(quiet)return;var owner=comp;for(var i=0;i<args.length;i++)if(args[i]&&typeof args[i].appendChild==='function'){owner=args[i];break;}var s={owner:owner};return run(s,function(){var result=fn.apply(null,args);wake();return result;});}
 div=function(id,parent,x,y,w,h,cls){if(!scope)return nativeDiv.apply(null,arguments);var p=parent&&parent.fx?parent:layer(parent||comp),n=node(p,x,y,w,h);n.id=id||'';n.className=cls||'layer';if(id)nodes[id]=n;return n;};
 piece=function(id,parent,x,y,instance){if(!scope)return nativePiece.apply(null,arguments);var m=meta[id];if(!m)throw Error('Missing effect art '+id);var e=div(instance||id,parent,x===undefined?m.x:x,y===undefined?m.y:y,m.innerW,m.innerH),im=node(e,-m.pad,-m.pad,m.width,m.height);im.image='art/'+id+'.png';im.src=im.image;e.art=im;e.meta=m;e.nativeTextNodes=[];return e;};
 after=function(fn,f){return scope?schedule(scope,fn,f||0):nativeAfter(fn,f);};
 clearTimeout=function(t){if(t&&t.fxJob)t.cancelled=true;else nativeClear(t);};
 function motion(e,d,fn,delay,fill){var t={at:performance.now()+(delay||0)*F,d:d*F,fn:fn,fill:fill||'both',cancelled:false,cancel:function(){this.cancelled=true;}};e.tracks.push(t);anims.push(t);wake();return t;}
 function cssTransform(ops){return ops.map(function(o){if(o[0]===0)return 'translate('+o[1]+(o[3]?'%':'px')+','+o[2]+(o[4]?'%':'px')+')';if(o[0]===1)return 'scale('+o[1]+','+o[2]+')';return (o[0]===2?'rotate(':'skewX(')+o[1]+'deg)';}).join(' ');}
 track=function(e,d,fn,at,fill){return e&&e.fx?motion(e,d,function(t){return fn(t*d);},at,fill):nativeTrack(e,d,function(f){var s=fn(f);if(Array.isArray(s.transform))s.transform=cssTransform(s.transform);return s;},at,fill);};
 function easing(t,name){if(!name||name==='linear')return t;if(name===OUT)return out(t);if(name===BACK)return back(t);if(name==='ease-in')return t*t;if(name==='ease-out')return 1-(1-t)*(1-t);if(name==='ease-in-out')return t*t*(3-2*t);var v=name.match(/[-.0-9]+/g);return v&&v.length===4?bez(t,+v[0],+v[1],+v[2],+v[3]):t;}
 function interpolate(a,b,t){if(typeof a==='number'&&typeof b==='number')return a+(b-a)*t;if(Array.isArray(b))return b.map(function(v,i){return interpolate(a[i],v,t);});var aa=String(a).match(/-?[0-9]*[.]?[0-9]+(?:e[-+]?[0-9]+)?/g)||[],i=0;return String(b).replace(/-?[0-9]*[.]?[0-9]+(?:e[-+]?[0-9]+)?/g,function(v){var x=+aa[i++];return String(isFinite(x)?x+(+v-x)*t:+v);});}
 anim=function(e,k,d,at,ease,fill){if(!e||!e.fx)return nativeAnim.apply(null,arguments);return motion(e,d,function(t){t=easing(t,ease);var i=1;while(i<k.length-1&&t>(k[i].offset===undefined?i/(k.length-1):k[i].offset))i++;var a=k[i-1],b=k[i],lo=a.offset===undefined?(i-1)/(k.length-1):a.offset,hi=b.offset===undefined?i/(k.length-1):b.offset,u=clamp((t-lo)/(hi-lo)),v={};Object.keys(b).forEach(function(p){if(p!=='offset'&&p!=='easing')v[p]=interpolate(a[p]===undefined?b[p]:a[p],b[p],u);});return v;},at,fill);};
 function mul(a,b){return [a[0]*b[0]+a[2]*b[1],a[1]*b[0]+a[3]*b[1],a[0]*b[2]+a[2]*b[3],a[1]*b[2]+a[3]*b[3],a[0]*b[4]+a[2]*b[5]+a[4],a[1]*b[4]+a[3]*b[5]+a[5]];}
 function tr(s,w,h){var m=[1,0,0,1,0,0];if(!s||s==='none')return m;if(Array.isArray(s)){for(var i=0;i<s.length;i++){var o=s[i],a=o[1],b=o[2];if(o[0]===0){if(o[3])a=a*w/100;if(o[4])b=b*h/100;m[4]+=m[0]*a+m[2]*b;m[5]+=m[1]*a+m[3]*b;}else if(o[0]===1){m[0]*=a;m[1]*=a;m[2]*=b;m[3]*=b;}else if(o[0]===2){var r=a*Math.PI/180,c=Math.cos(r),q=Math.sin(r),x=m[0],y=m[1];m[0]=x*c+m[2]*q;m[1]=y*c+m[3]*q;m[2]=m[2]*c-x*q;m[3]=m[3]*c-y*q;}else {var k=Math.tan(a*Math.PI/180);m[2]+=m[0]*k;m[3]+=m[1]*k;}}return m;}var re=/([A-Za-z]+)[(]([^)]*)[)]/g,q;while((q=re.exec(s))){var v=q[2].split(','),a=parseFloat(v[0]),b=v.length>1?parseFloat(v[1]):a,n=[1,0,0,1,0,0];if(q[1].indexOf('translate')===0){if(v[0].indexOf('%')>=0)a=a*w/100;if(v.length>1&&v[1].indexOf('%')>=0)b=b*h/100;n[4]=q[1]==='translateY'?0:a;n[5]=q[1]==='translateX'?0:q[1]==='translateY'?a:v.length>1?b:0;}else if(q[1].indexOf('scale')===0){n[0]=q[1]==='scaleY'?1:a;n[3]=q[1]==='scaleX'?1:b;}else if(q[1]==='rotate'){var r=a*Math.PI/180;n=[Math.cos(r),Math.sin(r),-Math.sin(r),Math.cos(r),0,0];}else if(q[1]==='matrix')n=v.map(Number);else if(q[1]==='skewX')n[2]=Math.tan(a*Math.PI/180);else throw Error('Unsupported particle transform '+q[1]);m=mul(m,n);}return m;}
 function num(v,size){return typeof v==='string'&&v.indexOf('%')>=0?parseFloat(v)*size/100:parseFloat(v)||0;}
 function quad(ctx,m,x,y,w,h,c,alpha){if(!w||!h||alpha<=0)return;ctx.fillStyle=c;ctx.globalAlpha=alpha;if(Math.abs(m[1])+Math.abs(m[2])<.00001){ctx.fillRect((m[0]*x+m[4]+drawX)*2,(m[3]*y+m[5]+drawY)*2,m[0]*w*2,m[3]*h*2);return;}if(typeof ctx.fillQuad==='function'){ctx.fillQuad((m[0]*x+m[2]*y+m[4]+drawX)*2,(m[1]*x+m[3]*y+m[5]+drawY)*2,(m[0]*(x+w)+m[2]*y+m[4]+drawX)*2,(m[1]*(x+w)+m[3]*y+m[5]+drawY)*2,(m[0]*(x+w)+m[2]*(y+h)+m[4]+drawX)*2,(m[1]*(x+w)+m[3]*(y+h)+m[5]+drawY)*2,(m[0]*x+m[2]*(y+h)+m[4]+drawX)*2,(m[1]*x+m[3]*(y+h)+m[5]+drawY)*2);return;}var p=[[x,y],[x+w,y],[x+w,y+h],[x,y+h]].map(function(p){return [(m[0]*p[0]+m[2]*p[1]+m[4]+drawX)*2,(m[1]*p[0]+m[3]*p[1]+m[5]+drawY)*2];});if(Math.abs(m[1])+Math.abs(m[2])<.00001){ctx.fillRect(p[0][0],p[0][1],p[2][0]-p[0][0],p[2][1]-p[0][1]);return;}var lo=Math.max(0,Math.floor(Math.min(p[0][1],p[1][1],p[2][1],p[3][1]))),hi=Math.min(1080,Math.ceil(Math.max(p[0][1],p[1][1],p[2][1],p[3][1])));for(var yy=lo;yy<hi;yy++){var xs=[];for(var i=0;i<4;i++){var a=p[i],b=p[(i+1)%4];if((a[1]<=yy+.5&&b[1]>yy+.5)||(b[1]<=yy+.5&&a[1]>yy+.5))xs.push(a[0]+(yy+.5-a[1])*(b[0]-a[0])/(b[1]-a[1]));}if(xs.length>=2){var left=Math.min.apply(null,xs),right=Math.max.apply(null,xs);ctx.fillRect(left,yy,right-left,1);}}}
 function art(id){if(cache[id])return cache[id];var d=FX_ART[id];if(!d)throw Error('No particle sprite data '+id);var c=document.createElement('canvas');c.width=d.side;c.height=d.side;var ctx=c.getContext('2d'),im=ctx.createImageData(d.side,d.side),u=new Uint32Array(im.data.buffer),p=d.palette.slice();u[0]=0x01020304;var little=im.data[0]===4;u[0]=0;if(!little)p=p.map(function(v){return (((v&255)<<24)|((v>>>8&255)<<16)|((v>>>16&255)<<8)|(v>>>24))>>>0;});for(var i=0;i<d.runs.length;i+=3)u.fill(p[d.runs[i+2]],d.runs[i],d.runs[i]+d.runs[i+1]);ctx.putImageData(im,0,0);return cache[id]={canvas:c,data:d};}
 function image(ctx,n,m,w,h,alpha){var a=art(n.src||n.image),d=a.data,cx=m[4]+m[0]*w/2+m[2]*h/2,cy=m[5]+m[1]*w/2+m[3]*h/2,sx=Math.hypot(m[0],m[1]),sy=Math.hypot(m[2],m[3]),angle=Math.atan2(m[1],m[0]),step=((Math.round(angle/(Math.PI*2)*d.steps)%d.steps)+d.steps)%d.steps,W=w*sx*d.tile/d.ink,H=h*sy*d.tile/d.ink;ctx.globalAlpha=alpha;ctx.drawImage(a.canvas,step%d.cols*d.tile,Math.floor(step/d.cols)*d.tile,d.tile,d.tile,(cx-W/2+drawX)*2,(cy-H/2+drawY)*2,W*2,H*2);}
 function paint(ctx,n,parentMatrix,parentAlpha,parentW,parentH){if(n.removed)return;var s=n.tracks.length?Object.assign({},n.style):n.style,future=false;for(var i=0;i<n.tracks.length;i++){var t=n.tracks[i];if(t.cancelled)continue;var u=(clock-t.at)/t.d;if(t.iterations===Infinity&&u>=0)u=u%1;if(u<0){future=true;if(t.fill!=='both'&&t.fill!=='backwards')continue;}if(u>1&&t.fill!=='both'&&t.fill!=='forwards')continue;Object.assign(s,t.fn(clamp(u)));}
 var w=num(s.width,parentW),h=num(s.height,parentH),x=num(s.left,parentW),y=num(s.top,parentH),origin=(s.transformOrigin||'50% 50%').split(' '),ox=num(origin[0],w),oy=num(origin[1]||origin[0],h),m=tr(s.transform,w,h);m[4]+=x+ox-m[0]*ox-m[2]*oy;m[5]+=y+oy-m[1]*ox-m[3]*oy;if(parentMatrix!==identity)m=mul(parentMatrix,m);var alpha=parentAlpha*(s.opacity===undefined?1:Number(s.opacity));if(alpha<=0)return;if(n.image){image(ctx,n,m,w,h,alpha);return;}if(s.background){if(s.background.indexOf('gradient')>=0)throw Error('Gradient requires native effect layer');quad(ctx,m,0,0,w,h,s.background,alpha);}if(s.border){var b=s.border.split(' '),bw=parseFloat(b[0]),bc=b[b.length-1];quad(ctx,m,0,0,w,bw,bc,alpha);quad(ctx,m,0,h-bw,w,bw,bc,alpha);quad(ctx,m,0,0,bw,h,bc,alpha);quad(ctx,m,w-bw,0,bw,h,bc,alpha);}if(s.boxShadow&&s.boxShadow.indexOf('inset')===0){var v=s.boxShadow.split(' '),dx=parseFloat(v[1]),dy=parseFloat(v[2]),c=v[v.length-1];quad(ctx,m,dx<0?w+dx:0,0,Math.abs(dx),h,c,alpha);quad(ctx,m,0,dy<0?h+dy:0,w,Math.abs(dy),c,alpha);}for(var j=0;j<n.children.length;j++)paint(ctx,n.children[j],m,alpha,w,h);}
 function deadline(n){var end=0;for(var i=0;i<n.tracks.length;i++)if(!n.tracks[i].cancelled)end=Math.max(end,n.tracks[i].iterations===Infinity?Infinity:n.tracks[i].at+n.tracks[i].d);for(var j=0;j<n.children.length;j++)end=Math.max(end,deadline(n.children[j]));return end;}
 function retire(n){n.removed=true;n.tracks.forEach(function(t){t.cancelled=true;var i=anims.indexOf(t);if(i>=0)anims.splice(i,1);});n.children.forEach(retire);}
 function frame(now){raf=0;clock=now;var pending=jobs;jobs=[];for(var i=0;i<pending.length;i++){var j=pending[i];if(j.cancelled||(j.scope.owner&&!j.scope.owner.fx&&!alive(j.scope.owner)))continue;if(j.at<=now)run(j.scope,j.fn);else jobs.push(j);}var active=jobs.length>0;layers=layers.filter(function(l){if(!alive(l.parent)||l.canvas.parentElement!==l.parent){l.children.forEach(retire);l.canvas.remove();return false;}drawX=l.ox;drawY=l.oy;l.ctx.clearRect(0,0,l.canvas.width,l.canvas.height);l.children=l.children.filter(function(n){var end=deadline(n);if(n.removed||end===0||clock>end+F){retire(n);return false;}return true;});for(var k=0;k<l.children.length;k++)paint(l.ctx,l.children[k],identity,1,640,360);l.direct=l.direct.filter(function(b){b.items=b.items.filter(function(p){return b.draw(l.ctx,p,now,l.ox,l.oy);});return b.items.length>0;});if(l.children.length||l.direct.length)active=true;else {l.canvas.remove();return false;}return true;});if(active)wake();}
 function cancelOwner(owner){jobs=jobs.filter(function(j){return j.scope.owner!==owner;});layers=layers.filter(function(l){if(l.parent!==owner)return true;l.children.forEach(retire);l.canvas.remove();return false;});if(!jobs.length&&!layers.length&&raf){cancelAnimationFrame(raf);raf=0;}}
 function reset(){if(raf)cancelAnimationFrame(raf);raf=0;jobs=[];layers.forEach(function(l){l.children.forEach(retire);l.canvas.remove();});layers=[];scope=null;}
 if(typeof window.addEventListener==='function'){window.addEventListener('pagehide',reset);window.addEventListener('unload',reset);}
 stop=function(){reset();nativeStop();};
 function wrap(name){var original=window[name];if(typeof original!=='function')return;window[name]=function(){return capture(original,arguments);};}
 function clone(el,parent){if(!el.fxSavedStyle){var c=getComputedStyle(el),s={};['left','top','width','height','opacity'].forEach(function(p){s[p]=el.style[p]||c[p];});s.background=el.style.background||c.backgroundColor;s.boxShadow=el.style.boxShadow;s.border=el.style.border;el.fxSavedStyle=s;}var n=node(parent,0,0,0,0);Object.assign(n.style,el.fxSavedStyle);if(el.tagName.toLowerCase()==='img')n.src=n.image=el.getAttribute('src');else for(var i=0;i<el.children.length;i++)clone(el.children[i],n);return n;}
 function adopt(el,k,o){var old=scope;scope={owner:el.parentElement};try{var n=clone(el,layer(el.parentElement));el.style.display='none';var a=anim(n,k,o.duration/F,(o.delay||0)/F,o.easing,o.fill||'none');a.iterations=o.iterations;anims.splice(anims.indexOf(a),1);return a;}finally{scope=old;}}
 return {wrap:wrap,decorate:function(fn){return function(){return capture(fn,arguments);};},choose:function(kind,fn){return kind.indexOf('item:')===0?fn:function(){return capture(fn,arguments);};},capture:capture,adopt:adopt,sprite:art,quad:quad,direct:function(parent,items,draw){layer(parent).direct.push({items:items,draw:draw});wake();},reset:reset,cancelOwner:cancelOwner,stats:function(){return {layers:layers.length,jobs:jobs.length,raf:raf};}};
})();

var originalParticleAnimation=run;
run=function(e,k,o){if(/(^| )(particle|ember)( |$)/.test(e.className)){anims=animations;var a=FX.adopt(e,k,o);animations.push(a);return a;}return originalParticleAnimation(e,k,o);};
window.addEventListener('pagehide',FX.reset);window.addEventListener('unload',FX.reset);
replay();