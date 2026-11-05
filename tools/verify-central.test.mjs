import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {verifyCentral} from './verify-central.mjs';

const bytes = Buffer.from('official artifact fixture');
const hash = createHash('sha256').update(bytes).digest('hex');
const xml = value => `<component group='org.example' name='library' version='1.0'><artifact name='library-1.0.jar'><sha256 value='${value}'/></artifact></component>`;

test('official bytes must match the committed SHA-256 without changing trust', () => {
  const metadata = xml(hash);
  const rows = verifyCentral(metadata, ['org.example:library:1.0'], url => {
    assert.equal(url, 'https://repo.maven.apache.org/maven2/org/example/library/1.0/library-1.0.jar');
    return bytes;
  });
  assert.equal(rows[0].sha256, hash);
  assert.equal(metadata, xml(hash));
  assert.throws(() => verifyCentral(xml('0'.repeat(64)), ['org.example:library:1.0'], () => bytes), /SHA-256 mismatch.*expected=.*actual=/);
});

test('unknown coordinates, path injection and unavailable sources fail closed', () => {
  const fetch = () => { throw new Error('HTTP 404'); };
  assert.throws(() => verifyCentral(xml(hash), ['org.other:library:1.0'], fetch), /not pinned/);
  for (const coordinate of ['org.example:library:../1', 'org.example:library:1.0?x', 'org.example:library:1.0:extra'])
    assert.throws(() => verifyCentral(xml(hash), [coordinate], fetch), /Invalid coordinate/);
  assert.throws(() => verifyCentral(xml(hash), ['org.example:library:1.0'], fetch), /HTTP 404/);
  assert.throws(() => verifyCentral(xml(hash), [], fetch), /Specify/);
});

test('every pinned classifier is checked and unpinned bytes cannot pass', () => {
  const metadata = xml(hash).replace('</component>', `<artifact name='library-1.0-sources.jar'><sha256 value='${hash}'/></artifact></component>`);
  let calls = 0;
  assert.equal(verifyCentral(metadata, ['org.example:library:1.0'], () => { calls++; return bytes; }).length, 2);
  assert.equal(calls, 2);
  assert.throws(() => verifyCentral(xml(hash).replace(` value='${hash}'`, ''), ['org.example:library:1.0'], () => bytes), /No SHA-256/);
  assert.throws(() => verifyCentral(xml(hash).replace('library-1.0.jar', '../escape.jar'), ['org.example:library:1.0'], () => bytes), /Invalid artifact/);
});
