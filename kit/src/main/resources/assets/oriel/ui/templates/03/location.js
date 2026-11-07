// Original: mcd2/location.tsx, Label, 30 fps. Continuous native animation.
var F=1000/30,OUT="cubic-bezier(.16,1,.3,1)",BACK="cubic-bezier(.34,1.56,.64,1)";
var animations=[],timers=[],blocks=["stone","cobblestone","magma","redstone_block","netherrack"],names=["石头","圆石","岩浆块","红石块","下界岩"];
var currentBlock=0,quiet=false;
function id(name){return document.getElementById(name);}
function animate(el,keys,options){var a=el.animate(keys,options);animations.push(a);return a;}
function schedule(fn,frame){timers.push(setTimeout(fn,frame*F));}
function stop(){timers.forEach(function(t){clearTimeout(t);});timers=[];animations.forEach(function(a){a.cancel();});animations=[];}
function setBlock(index){currentBlock=index;id("block").setAttribute("src","art/"+blocks[index]+".png");id("block").setAttribute("alt",names[index]);id("flash").setAttribute("src","art/"+blocks[index]+"-bright.png");}
function pop(flash){
 animate(id("slot"),[{transform:"scale(.88)"},{transform:"scale(1)"}],{duration:4*F,easing:"linear",fill:"forwards"});
 if(flash)animate(id("flash"),[{opacity:1},{opacity:0}],{duration:3*F,easing:"linear",fill:"none"});
}
function replay(){
 stop();setBlock(0);quiet=getComputedStyle(document.documentElement).getPropertyValue("--reduce").trim()==="1";
 id("label").style.opacity=quiet?"1":"0";id("badge-fade").style.opacity=quiet?"1":"0";
 id("slot").style.transform=quiet?"scale(1)":"scale(.88)";id("badge").style.transform=quiet?"scale(1)":"scale(0)";
 if(quiet)return;
 var fade=[{offset:0,opacity:0},{offset:2/146,opacity:0},{offset:10/146,opacity:1},{offset:136/146,opacity:1},{offset:1,opacity:0}];
 animate(id("label"),fade,{duration:146*F,fill:"forwards",easing:"linear"});
 animate(id("badge-fade"),fade,{duration:146*F,fill:"forwards",easing:"linear"});
 animate(id("label"),[{offset:0,transform:"translateY(8px)"},{offset:2/146,transform:"translateY(8px)",easing:OUT},{offset:10/146,transform:"translateY(0px)"},{offset:136/146,transform:"translateY(0px)",easing:"linear"},{offset:1,transform:"translateY(4px)"}],{duration:146*F,fill:"forwards"});
 animate(id("badge"),[{transform:"scale(0)"},{transform:"scale(1)"}],{duration:8*F,delay:8*F,easing:BACK,fill:"forwards"});
 schedule(function(){pop(false);},6);
 for(var at=24;at<146;at+=18)(function(frame){schedule(function(){setBlock(Math.floor((frame-6)/18)%blocks.length);pop(true);},frame);})(at);
 schedule(function(){if(typeof oriel!=="undefined"&&oriel.playSound&&!(oriel.data&&oriel.data.audio===false))oriel.playSound("minecraft:ui.toast.in",.3,1);},4);
 // Finish once and release callbacks; no idle animation frame loop.
 schedule(function(){timers=[];},150);
}
window.addEventListener("keydown",function(e){if(e.repeat)return;if(e.key==="r"||e.key==="R"){e.preventDefault();replay();}if(e.key==="Escape"){e.preventDefault();stop();location.href="oriel:ui/styles.html";}});
window.addEventListener("pagehide",stop);window.addEventListener("unload",stop);
replay();
