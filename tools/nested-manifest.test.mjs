import test from 'node:test';
import assert from 'node:assert/strict';
import {createManifest} from './release/manifest.mjs';
import {coreHash} from './check-release.mjs';

test('nested manifest uses release boundaries, package hashes and API snapshot bytes', () => {
  const entries = new Map([
    ['dev/oriel/engine/A.class', Buffer.from('a')], ['dev/oriel/engine/B.class', Buffer.from('b')],
    ['assets/oriel/ui/ore.css', Buffer.from('core')], ['assets/oriel/ui/templates/a.html', Buffer.from('reference')],
    ['META-INF/oriel/manifest.json', Buffer.from('old manifest')]
  ]);
  const meta = {version:'0.12.0', variant:'slim', loader:'fabric', mc:'26.3'};
  const m = createManifest(entries, meta, Buffer.from('api'));
  assert.equal(m.coreHash, coreHash(entries));
  assert.equal(m.entries.length, 2);
  assert.ok(m.entries.some(e => e.path === 'dev/oriel/engine/*.class'));
  assert.ok(m.entries.some(e => e.path === 'assets/oriel/ui/ore.css'));
  assert.notEqual(m.apiHash, createManifest(entries, meta, Buffer.from('changed API')).apiHash);
  entries.set('assets/oriel/ui/templates/a.html', Buffer.from('changed reference'));
  assert.deepEqual(m, createManifest(entries, meta, Buffer.from('api')));
  entries.set('dev/oriel/engine/A.class', Buffer.from('changed core'));
  assert.notEqual(m.coreHash, createManifest(entries, meta, Buffer.from('api')).coreHash);
});
