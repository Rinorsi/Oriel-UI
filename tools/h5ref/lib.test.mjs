import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {comparePixels, diagnosticsFrom, aggregateDiagnostics, isInside, markdownReport} from './lib.mjs';

test('MAE has exact endpoints and threshold does not hide small errors', () => {
  const black = Uint8Array.from([0, 0, 0, 255]);
  assert.equal(comparePixels(black, black).score, 0);
  assert.equal(comparePixels(black, Uint8Array.from([255, 255, 255, 255])).score, 100);
  const small = comparePixels(black, Uint8Array.from([8, 0, 0, 255]));
  assert.equal(small.changedPixels, 0);
  assert.ok(small.score > 0);
  assert.equal(small.heat[0], 8);
  assert.throws(() => comparePixels(black, []));
});

test('diagnostics preserve Windows paths, source lines, unknown formats and page frequencies', () => {
  const line = '[diagnostic] file:///D:/ui/a.html:12: warning [unsupported-property] Unsupported property "backdrop-filter"';
  const parsed = diagnosticsFrom('other\r\n' + line + '\r\n' + line);
  assert.equal(parsed.length, 2);
  assert.equal(parsed[0].feature, 'backdrop-filter');
  assert.equal(parsed[0].source, 'file:///D:/ui/a.html:12');
  const groups = aggregateDiagnostics([{page: 'a', diagnostics: parsed}, {page: 'b', diagnostics: parsed.slice(0, 1)}]);
  assert.equal(groups[0].pageCount, 2);
  assert.equal(groups[0].occurrences, 3);
  assert.equal(diagnosticsFrom('[diagnostic] future format')[0].feature, 'unparsed-diagnostic');
});

test('path guard distinguishes a sibling with the same prefix', () => {
  const root = path.resolve('workspace');
  assert.equal(isInside(root, path.join(root, 'out')), true);
  assert.equal(isInside(root, root + '-elsewhere'), false);
});

test('real engine declarations group by property, not value or nested declaration', () => {
  const messages = [
    "Invalid declaration 'filter: brightness(1.3)' (not a supported property)",
    "Invalid declaration 'filter: drop-shadow(0 0 4px red)' (not a supported property)",
    "Skipped '& span { flex: 1; }': not a declaration (CSS nesting is not supported)",
  ];
  const parsed = messages.map(message => diagnosticsFrom('[diagnostic] D:/page.html:2: warning [unknown-property] ' + message)[0]);
  assert.deepEqual(parsed.map(d => d.feature), ['filter', 'filter', 'CSS nesting']);
  const groups = aggregateDiagnostics([{page: 'a', diagnostics: parsed}]);
  assert.equal(groups[0].feature, 'filter');
  assert.equal(groups[0].occurrences, 2);
});

test('non-declaration diagnostics are not grouped by incidental colon text', () => {
  const stylesheet = diagnosticsFrom('[diagnostic] D:/page.html: error [missing-stylesheet] Stylesheet not found: https://fonts.googleapis.com/css2?family=Orbitron')[0];
  const selector = diagnosticsFrom("[diagnostic] D:/page.html:2: warning [invalid-selector] Invalid selector '.lore::first-letter': unsupported pseudo-element")[0];
  assert.equal(stylesheet.feature, 'missing-stylesheet');
  assert.equal(selector.feature, 'invalid-selector');
});

test('report keeps execution failures separate from a zero score', () => {
  const md = markdownReport({background: '#ffffff', browserVersion: 'test', diagnosticFrequency: [], pages: [
    {page: 'failed.html', error: 'failed', diagnostics: [], browserWarnings: [], files: {}},
  ]});
  assert.match(md, /执行失败 1 页/);
  assert.match(md, /failed.html \| — \| —/);
  assert.ok(md.includes('\n## failed.html\n'));
});

test('CLI rejects unknown options and translucent backgrounds before launching tools', () => {
  const cli = fileURLToPath(new URL('./compare.mjs', import.meta.url));
  for (const args of [['--unknown'], ['--background', 'transparent'], ['--timeout', '0']]) {
    const result = spawnSync(process.execPath, [cli, ...args], {encoding: 'utf8', windowsHide: true});
    assert.equal(result.status, 1);
    assert.match(result.stderr, /H5 对照失败/);
  }
});

test('CLI documents --corpus and rejects ambiguous corpus sources', () => {
  const cli = fileURLToPath(new URL('./compare.mjs', import.meta.url));
  const help = spawnSync(process.execPath, [cli, '--help'], {encoding: 'utf8', windowsHide: true});
  assert.equal(help.status, 0);
  assert.match(help.stdout, /--corpus/);
  assert.match(help.stdout, /--preview-lib/);
  const duplicate = spawnSync(process.execPath, [cli, 'a.html', '--corpus', 'b.html'], {encoding: 'utf8', windowsHide: true});
  assert.equal(duplicate.status, 1);
  assert.match(duplicate.stderr, /--corpus 与位置参数只能选一个/);
});
