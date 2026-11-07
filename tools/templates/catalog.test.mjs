import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const assets=root+'/kit/src/main/resources/assets/oriel',base=assets+'/ui/templates';
const catalog=JSON.parse(fs.readFileSync(base+'/catalog.json','utf8'));
const files=(dir,rel='')=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name),rel+e.name+'/'):[rel+e.name]).sort();

test('56 official directories (44 left out) and 11 legacy entries have complete metadata and inventories',()=>{
 assert.deepEqual(catalog.filter(t=>t.source==='official').map(t=>t.id),Array.from({length:57},(_,i)=>String(i+1).padStart(2,'0')).filter(id=>id!=='44'));
 assert.equal(catalog.filter(t=>t.source==='legacy').length,11);
 assert.equal(catalog.filter(t=>t.kind==='reference').length,0);
 assert.equal(new Set(catalog.map(t=>t.id)).size,67);
 for(const t of catalog){
  const dir=base+'/'+path.dirname(t.entry);
  const meta=JSON.parse(fs.readFileSync(dir+'/template.json','utf8'));
  assert.equal(meta.author,'云诺羲');
  for(const key of ['version','author','entry','source','group'])assert.ok(meta[key],t.id+' '+key);
  for(const language of ['zh_cn','en_us'])for(const key of ['name','description','category'])assert.ok(meta.locales[language][key]?.trim(),t.id+' '+language+'.'+key);
  for(const key of ['id','entry','source','kind','cover'])assert.equal(meta[key],t[key]);
  assert.equal(t.locales,undefined,'index is not a second editable translation source');
  assert.equal(t.name,undefined);
  assert.deepEqual(JSON.parse(fs.readFileSync(dir+'/files.json','utf8')),files(dir));
  if(t.page)assert.ok(fs.statSync(assets+'/'+t.page.slice('oriel:'.length)).size>100,t.page);else assert.ok(fs.statSync(base+'/'+t.entry).size>100);
  assert.ok(fs.existsSync(assets+'/'+t.cover.slice('oriel:'.length)),t.cover);
 }
});

test('catalog display data has no dependency on global template translation keys',()=>{
 const html=fs.readFileSync(assets+'/ui/styles.html','utf8');
 assert.ok(!html.includes('oriel.ui.numbered.'));
 for(const tag of html.matchAll(/<div class="tpl"[^>]*>/g))assert.ok(!tag[0].includes('oriel.ui.'),tag[0]);
 for(const lang of ['zh_cn','en_us']){
  const messages=JSON.parse(fs.readFileSync(assets+'/lang/'+lang+'.json','utf8'));
  assert.equal(Object.keys(messages).filter(k=>k.startsWith('oriel.ui.numbered.')).length,0);
 }
});

test('relative HTML and CSS assets survive relocation; menu uses packaged settings',()=>{
 for(const t of catalog){
  const dir=base+'/'+path.dirname(t.entry);
  for(const f of files(dir).filter(f=>['.html','.css'].includes(path.extname(f)))){
   const s=fs.readFileSync(dir+'/'+f,'utf8');
   const refs=[...s.matchAll(/(?:src|href)="([^"]+)"/g),...s.matchAll(/url\(['"]?([^)'"\s]+)['"]?\)/g)].map(m=>m[1]);
   for(const r of refs){
    if(r.startsWith('#')||r.includes(':')||r.includes('var('))continue;
    assert.ok(fs.existsSync(path.resolve(dir,path.dirname(f),r)),t.id+'/'+f+' -> '+r);
   }
  }
 }
 assert.match(fs.readFileSync(base+'/08/page.js','utf8'),/oriel:ui\/templates\/07\/index.html/);
});

test('official catalog cards match source order; no legacy card claims current official status',()=>{
 const html=fs.readFileSync(assets+'/ui/styles.html','utf8');
 const cards=[...html.matchAll(/<div class="tpl" data-id="([^"]+)"[^>]*data-source="([^"]+)"/g)];
 assert.deepEqual(cards.map(m=>m[1]),catalog.map(t=>t.id));
 assert.deepEqual(cards.map(m=>m[2]),catalog.map(t=>t.source));
 assert.ok(html.includes('data-view="legacy"'));
 for(const t of catalog.filter(t=>t.source==='legacy')){
  const f=assets+'/ui/styles/'+t.id+'.html';if(!fs.existsSync(f))continue;const old=fs.readFileSync(f,'utf8');
  assert.ok(old.includes('oriel:ui/templates/'+t.entry));
  assert.ok(old.length<400,'compatibility file must not contain another copy of the UI');
 }
});
