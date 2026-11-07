import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';import {test} from 'node:test';
import {preparePersonalPages} from './prepare-personal-pages.mjs';
test('nested numbered and legacy copies retain metadata across dates without overwriting edits',()=>{
  const base=process.env.ORIEL_TEST_TMP;fs.mkdirSync(base,{recursive:true});
  const workspace=fs.mkdtempSync(path.join(base,'nested-pages-'));
  const old=workspace+'/daily/2026-11-06/run/fabric-26.1/client/config/oriel/my-pages';
  for(const rel of ['templates/09','templates/legacy/xianxia']){
    fs.mkdirSync(old+'/'+rel,{recursive:true});
    fs.writeFileSync(old+'/'+rel+'/index.html','page '+rel);
    fs.writeFileSync(old+'/'+rel+'/template.json',JSON.stringify({locales:{en_us:{name:'User template'}}}));
  }
  const args={workspace,runRoot:workspace+'/daily/2026-11-07/run',target:'fabric-26.1'};
  const first=preparePersonalPages(args);
  assert.equal(first.restoredPages,2);
  assert.ok(fs.existsSync(first.destination+'/templates/legacy/xianxia/template.json'));
  fs.writeFileSync(first.destination+'/templates/09/template.json','user edited metadata');
  assert.equal(preparePersonalPages(args).restoredPages,0);
  const next=preparePersonalPages({...args,runRoot:workspace+'/daily/2026-11-08/run'});
  assert.equal(next.restoredPages,2);
  assert.equal(fs.readFileSync(next.destination+'/templates/09/template.json','utf8'),'user edited metadata');
});
test('dated launch restores missing personal pages and preserves local edits',()=>{
  const base=process.env.ORIEL_TEST_TMP;if(!base)throw Error('Set ORIEL_TEST_TMP to workspace daily scratch');
  fs.mkdirSync(base,{recursive:true});const workspace=fs.mkdtempSync(path.join(base,'pages-'));
  const old=path.join(workspace,'daily/2026-11-06/run/fabric-26.1/client/config/oriel/my-pages');
  const newer=path.join(workspace,'daily/2026-11-07/run/fabric-26.1/client/config/oriel/my-pages');
  for(const dir of [old+'/first',old+'/second',newer+'/first'])fs.mkdirSync(dir,{recursive:true});
  fs.writeFileSync(old+'/first/index.html','old');fs.writeFileSync(old+'/second/index.html','second');fs.writeFileSync(newer+'/first/index.html','user edit');
  const runRoot=path.join(workspace,'daily/2026-11-07/run'),args={workspace,runRoot,target:'fabric-26.1'};
  const r=preparePersonalPages(args);assert.equal(r.restoredPages,1);assert.equal(fs.readFileSync(newer+'/first/index.html','utf8'),'user edit');assert.equal(fs.readFileSync(newer+'/second/index.html','utf8'),'second');assert.ok(fs.existsSync(workspace+'/daily/2026-11-07/KEEP'));assert.ok(fs.existsSync(workspace+'/daily/2026-11-06/KEEP'));assert.equal(preparePersonalPages(args).restoredPages,0);
  const next=preparePersonalPages({...args,runRoot:path.join(workspace,'daily/2026-11-08/run')});assert.equal(next.restoredPages,2);assert.equal(fs.readFileSync(next.destination+'/first/index.html','utf8'),'user edit');
  assert.throws(()=>preparePersonalPages({...args,runRoot:path.join(workspace,'outside')}),/dated/);
  assert.throws(()=>preparePersonalPages({...args,target:'../other'}),/Invalid/);
});
test('independent archive can seed another loader without a previous daily client',()=>{
  const workspace=fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP,'archive-')),snapshot=workspace+'/archive/ui-templates/snapshot';fs.mkdirSync(snapshot+'/my-pages/first',{recursive:true});fs.writeFileSync(snapshot+'/my-pages/first/index.html','backup');fs.writeFileSync(workspace+'/archive/ui-templates/LATEST.json',JSON.stringify({snapshot}));fs.mkdirSync(workspace+'/daily',{recursive:true});
  const r=preparePersonalPages({workspace,runRoot:workspace+'/daily/2026-11-08/run',target:'neoforge-26.3'});assert.equal(r.restoredPages,1);assert.equal(fs.readFileSync(r.destination+'/first/index.html','utf8'),'backup');
});
