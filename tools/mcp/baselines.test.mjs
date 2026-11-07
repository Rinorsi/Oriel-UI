import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { crc32, deflateSync } from 'node:zlib';
import { baselineManager } from './lib/baselines.mjs';
import { sha } from './lib/core.mjs';
import { pixels } from '../bench/effects.mjs';

assert.ok(process.env.ORIEL_TEST_TMP);
const temp = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'baselines-'));
test('baseline comparison decodes independently specified None/Sub/Up/Average/Paeth scanlines', () => {
  const first=[10,20,30,255,11,22,33,255], expected=[...first,20,40,60,255,23,46,69,255];
  const encoded=[[20,40,60,255,23,46,69,255],[20,40,60,255,3,6,9,0],[10,20,30,0,12,24,36,0],[15,30,45,128,8,15,23,0],[10,20,30,0,3,6,9,0]];
  const chunk=(name,bytes)=>{const b=Buffer.alloc(bytes.length+12);b.writeUInt32BE(bytes.length);b.write(name,4);bytes.copy(b,8);b.writeUInt32BE(crc32(b.subarray(4,8+bytes.length)),8+bytes.length);return b;};
  const header=Buffer.alloc(13);header.writeUInt32BE(2);header.writeUInt32BE(2,4);header[8]=8;header[9]=6;
  for(let filter=0;filter<5;filter++){
    const image=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(Buffer.from([0,...first,filter,...encoded[filter]]))),chunk('IEND',Buffer.alloc(0))]);
    assert.deepEqual([...pixels(image).rgba],expected,'filter '+filter);
  }
});
function png(color) {
  function chunk(name, bytes) { const b = Buffer.alloc(bytes.length+12); b.writeUInt32BE(bytes.length); b.write(name,4); bytes.copy(b,8); b.writeUInt32BE(crc32(b.subarray(4,8+bytes.length)),8+bytes.length); return b; }
  const header = Buffer.alloc(13); header.writeUInt32BE(1); header.writeUInt32BE(1,4); header[8]=8; header[9]=6;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',deflateSync(Buffer.from([0,...color]))),chunk('IEND',Buffer.alloc(0))]);
}
function fixture() {
  const dir=fs.mkdtempSync(path.join(temp,'case-')), root=path.join(dir,'root'), state=path.join(dir,'state');
  fs.mkdirSync(path.join(root,'preview/src/test/snapshots'),{recursive:true}); fs.mkdirSync(state);
  fs.writeFileSync(path.join(root,'preview/src/test/snapshots/a.png'),png([1,2,3,255])); fs.writeFileSync(path.join(state,'candidate.png'),png([4,5,6,255]));
  return { root, state, manage:baselineManager({root,state,lockRoot:path.join(dir,'locks')}) };
}
test('baseline approval is separate and promotion requires frozen matching browser oracle', () => {
  const f=fixture(), args={baseline:'preview/src/test/snapshots/a.png',candidate:'candidate.png',reason:'fixed browser comparison'};
  const proposed=f.manage(args); assert.equal(proposed.diff.changedPixels,1); assert.equal(proposed.imagePaths.length,2);
  assert.throws(()=>f.manage({action:'promote',proposalId:proposed.id}),/用户批准/);
  assert.throws(()=>f.manage({action:'decide',proposalId:proposed.id,decision:'approved',quote:''}),/逐字/);
  f.manage({action:'decide',proposalId:proposed.id,decision:'approved',quote:'批准这个固定黄金候选'});
  assert.throws(()=>f.manage({action:'promote',proposalId:proposed.id}),/oracle/);
  const withOracle=f.manage({...args,oracle:'candidate.png'}); f.manage({action:'decide',proposalId:withOracle.id,decision:'approved',quote:'批准该固定候选及浏览器参考'});
  const result=f.manage({action:'promote',proposalId:withOracle.id}); assert.equal(result.candidateMatchesProvidedBrowserPixels,true);
  assert.deepEqual(fs.readFileSync(path.join(f.root,args.baseline)),fs.readFileSync(path.join(f.state,'candidate.png'))); assert.ok(fs.existsSync(result.rollbackCopy));
  assert.throws(()=>f.manage({action:'promote',proposalId:withOracle.id}),/发生变化/);
});
test('candidate or target changes and path traversal never replace a golden', () => {
  const f=fixture(), args={baseline:'preview/src/test/snapshots/a.png',candidate:'candidate.png',reason:'test'};
  assert.throws(()=>f.manage({...args,baseline:'preview/src/test/snapshots/../../bad.png'}),/仅支持/);
  assert.throws(()=>f.manage({...args,candidate:'../escape.png'}),/超出/);
  const item=f.manage({...args,oracle:'candidate.png'});
  fs.writeFileSync(item.shots[1].image,'changed'); assert.throws(()=>f.manage({action:'decide',proposalId:item.id,decision:'approved',quote:'同意'}),/已变化/);
  const clean=f.manage({...args,oracle:'candidate.png'}); f.manage({action:'decide',proposalId:clean.id,decision:'approved',quote:'同意该候选'});
  fs.writeFileSync(path.join(f.root,args.baseline),png([9,9,9,255])); assert.throws(()=>f.manage({action:'promote',proposalId:clean.id}),/黄金图已发生变化/);
});
test('a mismatching browser oracle and an existing promotion lock both block writes', () => {
  const f=fixture(), args={baseline:'preview/src/test/snapshots/a.png',candidate:'candidate.png',reason:'test'};
  fs.copyFileSync(path.join(f.root,args.baseline),path.join(f.state,'browser.png'));
  const wrong=f.manage({...args,oracle:'browser.png'}); f.manage({action:'decide',proposalId:wrong.id,decision:'approved',quote:'同意候选'});
  assert.throws(()=>f.manage({action:'promote',proposalId:wrong.id}),/oracle/);
  const valid=f.manage({...args,oracle:'candidate.png'}); f.manage({action:'decide',proposalId:valid.id,decision:'approved',quote:'同意此固定候选'});
  const lock=path.join(path.dirname(f.root),'locks',sha(fs.realpathSync(f.root)+'/'+args.baseline)); fs.mkdirSync(lock,{recursive:true});
  assert.throws(()=>f.manage({action:'promote',proposalId:valid.id}),/另一进程/); assert.ok(fs.existsSync(lock));
  assert.deepEqual(fs.readFileSync(path.join(f.root,args.baseline)),fs.readFileSync(path.join(f.state,'browser.png')));
  assert.throws(()=>f.manage({action:'status',proposalId:'review-page-approval'}),/不存在/);
});
test('source changes invalidate a previously approved fixed baseline candidate', () => {
  const f=fixture(), args={baseline:'preview/src/test/snapshots/a.png',candidate:'candidate.png',oracle:'candidate.png',reason:'test'};
  const item=f.manage(args); f.manage({action:'decide',proposalId:item.id,decision:'approved',quote:'同意固定候选'});
  fs.mkdirSync(path.join(f.root,'engine/src/main'),{recursive:true}); fs.writeFileSync(path.join(f.root,'engine/src/main/changed.txt'),'changed');
  assert.throws(()=>f.manage({action:'promote',proposalId:item.id}),/代码、资源、规则/);
});

test('oversized candidates cannot install a golden beyond repository hygiene limits', () => {
  const f=fixture(); fs.writeFileSync(path.join(f.state,'candidate.png'),Buffer.alloc(300*1024+1));
  assert.throws(()=>f.manage({baseline:'preview/src/test/snapshots/a.png',candidate:'candidate.png',reason:'test'}),/300KiB/);
  assert.deepEqual(fs.readFileSync(path.join(f.root,'preview/src/test/snapshots/a.png')),png([1,2,3,255]));
});
