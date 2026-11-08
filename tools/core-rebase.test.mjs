import test from 'node:test';
import assert from 'node:assert/strict';
import {validateRebase, rebasePins} from './release/rebase.mjs';
const source = 'a'.repeat(40);
const pairs = ['fabric','neoforge'].flatMap(loader => ['26.1.2','26.2','26.3'].flatMap(mc =>
  ['slim','full'].map(variant => ({target:loader+'-'+mc, minor:'0.12', variant, coreHash:'b'.repeat(64), failures:[]}))));
test('unreleased rebase requires exact source commit and explicit unreleased version', () => {
  assert.doesNotThrow(() => validateRebase(source, source, '0.12.0', '## 0.12.0 slim / 0.12.1 full (unreleased)'));
  for (const bad of ['', 'HEAD', 'a'.repeat(7), 'c'.repeat(40)])
    assert.throws(() => validateRebase(bad, source, '0.12.0', '## 0.12.0 slim / 0.12.1 full (unreleased)'));
  assert.throws(() => validateRebase(source, source, '0.12.0', '## 0.12.0 (released)'));
});
test('rebase requires all six matching pairs and records old pins and source', () => {
  const old = '# previous audit\n0.11.fabric-26.3='+'c'.repeat(64)+'\n0.12.fabric-26.3='+'d'.repeat(64)+'\n';
  const next = rebasePins(old, pairs, source);
  assert.ok(next.includes('source='+source));
  assert.ok(next.includes('previous 0.12.fabric-26.3='+'d'.repeat(64)));
  assert.ok(next.includes('0.11.fabric-26.3='+'c'.repeat(64)));
  assert.throws(() => rebasePins(old, pairs.slice(1), source));
  assert.throws(() => rebasePins(old, pairs.map((r,i) => i ? r : {...r,coreHash:'e'.repeat(64)}), source));
  assert.throws(() => rebasePins(old, pairs.map((r,i) => i ? r : {...r,failures:['broken']}), source));
  assert.throws(() => rebasePins(old, [...pairs,pairs[0]], source));
});
