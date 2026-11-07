import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { deflateSync, crc32 } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { compare, freeze, verify, read, write, hash, tree, execute, markdown } from './core.mjs';
import { synthetic, generate } from './synthetic.mjs';
import { pixels, compareEffects } from './effects.mjs';

assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to a workspace scratch directory');
const temp = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'bench-'));
const here = path.dirname(fileURLToPath(import.meta.url));
const report = (p50 = 10, p95 = 20) => ({ warmup: 10, frames: 20, rounds: 3, aggregate: 'median-of-independent-rounds', viewport: '427x240@1', backend: 'ImageCanvas', clearBeforeFrame: true, java: '25', os: 'test', processors: 1, pages: { 'a.html': { p50_ms: p50, p95_ms: p95 } } });
function samples() { return ['aa0', 'aa1', 'old', 'new'].flatMap(label => Array.from({ length: 3 }, (_, round) => ({ label, round, report: report() }))); }
test('zero-noise identical A/A and A/B pass; p95 alone rejects regression', () => {
  const data = samples();
  assert.equal(compare(data, ['a.html']).verdict, 'PASS');
  data.filter(s => s.label === 'new').forEach(s => s.report.pages['a.html'].p95_ms = 21);
  const result = compare(data, ['a.html']); assert.equal(result.verdict, 'FAIL');
  assert.equal(result.rows[0].verdict, 'PASS'); assert.match(markdown(result), /p95_ms.*FAIL/);
});
test('noise permits only bounded changes and noisy measurements are inconclusive', () => {
  const data = samples();
  data.filter(s => s.label === 'aa1').forEach(s => s.report.pages['a.html'].p95_ms = 20.4);
  data.filter(s => s.label === 'new').forEach(s => s.report.pages['a.html'].p95_ms = 20.2);
  assert.equal(compare(data, ['a.html']).verdict, 'PASS');
  data.find(s => s.label === 'aa1').report.pages['a.html'].p50_ms = 12;
  assert.equal(compare(data, ['a.html']).verdict, 'INCONCLUSIVE');
});
test('round medians resist one outlier; malformed metrics/environment/page sets fail closed', () => {
  const data = samples(); data.find(s => s.label === 'new').report.pages['a.html'].p95_ms = 900;
  assert.equal(compare(data, ['a.html']).verdict, 'PASS');
  const bad = structuredClone(data); bad[0].report.java = '26';
  assert.throws(() => compare(bad, ['a.html']), /environment differs/);
  assert.throws(() => compare(data, ['missing.html']), /page set differs/);
  bad[0].report.pages['a.html'].p50_ms = NaN; assert.throws(() => compare(bad, ['a.html']), /Invalid metric/);
  assert.throws(() => compare(data.slice(1), ['a.html']), /rounds/);
});
test('resource freeze records page-set repair without rewriting historical timings', () => {
  const pages = path.join(temp, 'pages'); fs.mkdirSync(pages);
  fs.writeFileSync(path.join(pages, 'forge_station.html'), '<html></html>');
  const config = { pages, resources: [pages] };
  const frozen = freeze(config, { pages: { 'old.html': {} } });
  assert.deepEqual(frozen.historicalPageDelta, { added: ['forge_station.html'], removed: ['old.html'] });
  assert.deepEqual(Object.keys(tree(pages)), ['forge_station.html']);
  verify(config, frozen);
  const tampered = structuredClone(frozen); tampered.identity.pages.push('fake.html');
  assert.throws(() => verify(config, tampered), /identity changed/);
  fs.writeFileSync(path.join(pages, 'forge_station.html'), 'changed');
  assert.throws(() => verify(config, frozen), /identity changed/);
});
test('synthetic dimensions are deterministic and all 27 combinations exist externally', () => {
  assert.equal(synthetic(1000, 200, 'class'), synthetic(1000, 200, 'class'));
  assert.equal((synthetic(1000, 200, 'hover').match(/:hover/g) ?? []).length, 200);
  assert.equal((synthetic(1000, 200, 'attribute').match(/<div/g) ?? []).length, 1000);
  const directory = path.join(temp, 'synthetic'); generate(directory);
  assert.equal(read(path.join(directory, 'suite.json')).suite.length, 27);
  assert.throws(() => generate(directory), /already exists/);
});
test('CLI alternates independent processes, uses shared lock wrapper, retains raw reports', async () => {
  const directory = path.join(temp, 'cli'); fs.mkdirSync(directory);
  const pages = path.join(directory, 'pages'); fs.mkdirSync(pages); fs.writeFileSync(path.join(pages, 'a.html'), '<html></html>');
  const workspace = path.join(directory, 'workspace'); fs.mkdirSync(path.join(workspace, 'tools'), { recursive: true });
  const marker = path.join(directory, 'lock-marker');
  fs.writeFileSync(path.join(workspace, 'tools/bench-window.sh'), 'set -e\nprintf locked > "$ORIEL_BENCH_TEST_MARKER"\n"$@"\n');
  const adapter = path.join(directory, 'adapter.mjs');
  fs.writeFileSync(adapter, 'import fs from "node:fs";fs.writeFileSync(process.argv[2], ' + JSON.stringify(JSON.stringify(report())) + ');');
  const variant = { root: directory, artifacts: [adapter], command: [process.execPath, adapter, '{out}'] };
  const config = path.join(directory, 'config.json'), manifest = path.join(directory, 'frozen.json'), output = path.join(directory, 'result');
  write(config, { pages, resources: [pages], workspace, old: variant, new: variant });
  await execute([process.execPath, path.join(here, 'run.mjs'), '--config', config, '--manifest', manifest, '--freeze'], { log: path.join(directory, 'freeze.log') });
  await execute([process.execPath, path.join(here, 'run.mjs'), '--config', config, '--manifest', manifest, '--out', output], { log: path.join(directory, 'run.log'), env: { ORIEL_BENCH_TEST_MARKER: marker } });
  assert.equal(fs.readFileSync(marker, 'utf8'), 'locked');
  const result = read(path.join(output, 'comparison.json'));
  assert.equal(result.verdict, 'PASS'); assert.equal(result.samples.length, 12);
  assert.deepEqual(result.samples.map(s => s.label), ['aa0','aa1','aa1','aa0','aa0','aa1','old','new','new','old','old','new']);
  assert.ok(fs.existsSync(path.join(output, '00-aa0/command.log')));
});
test('spawn failures/timeout return errors without a successful report', async () => {
  await assert.rejects(execute(['oriel-no-such-executable'], { log: path.join(temp, 'spawn.log') }));
  await assert.rejects(execute([process.execPath, '-e', 'setTimeout(()=>{}, 10000)'], { log: path.join(temp, 'timeout.log'), timeout: 50 }), /timeout/);
});
function png(width, height, color) {
  const chunk = (name, bytes) => { const data = Buffer.alloc(12 + bytes.length); data.writeUInt32BE(bytes.length); data.write(name, 4); bytes.copy(data, 8); data.writeUInt32BE(crc32(data.subarray(4, 8 + bytes.length)), 8 + bytes.length); return data; };
  const header = Buffer.alloc(13); header.writeUInt32BE(width); header.writeUInt32BE(height, 4); header[8] = 8; header[9] = 6;
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) Buffer.from(color).copy(raw, y * (width * 4 + 1) + 1 + x * 4);
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
test('PNG comparator compares decoded RGBA and rejects unsupported/truncated images', () => {
  assert.deepEqual([...pixels(png(1, 1, [1,2,3,4])).rgba], [1,2,3,4]);
  assert.throws(() => pixels(Buffer.from('bad')), /Not PNG/);
  assert.throws(() => pixels(png(1,1,[1,2,3,4]).subarray(0, 35)), /Incomplete PNG/);
});
test('effects entry rejects missing states instead of giving partial evidence PASS', () => {
  const value = { schemaVersion: 1, context: { clock: '16ms-preview', seed: 0, resourceHash: hash('r'), scenarioHash: hash('s') }, frames: [] };
  assert.throws(() => compareEffects(value, value, temp, temp), /Missing effect frame/);
});
test('full 15-effect set compares all 120 decoded frames and detects changed pixels', () => {
  const frames = [], context = { clock: '16ms-preview', seed: 0, resourceHash: hash('r'), scenarioHash: hash('s') };
  for (const width of [427, 640]) fs.writeFileSync(path.join(temp, width + '.png'), png(width, width === 427 ? 240 : 360, [3,4,5,255]));
  for (const id of ['09','10','13','18','22','25','27','31','33','37','38','42','43','48','50']) for (const width of [427, 640]) for (const state of ['start','entrance','peak','exit']) {
    frames.push({ id, width, height: width === 427 ? 240 : 360, state, timeMs: 0, actionsHash: hash('a'), pageHash: hash(id), png: width + '.png' });
  }
  const value = { schemaVersion: 1, context, frames };
  assert.equal(compareEffects(value, value, temp, temp).verdict, 'PASS');
  fs.writeFileSync(path.join(temp, 'changed.png'), png(427, 240, [3,4,6,255]));
  const changed = structuredClone(value); changed.frames[0].png = 'changed.png';
  const result = compareEffects(value, changed, temp, temp);
  assert.equal(result.verdict, 'FAIL'); assert.equal(result.frames[0].changedPixels, 427 * 240);
});
test('committed historical frame baseline is short exactly forge_station, repaired manifest has 21 pages', () => {
  const root = path.resolve(here, '../..'), pages = path.join(root, 'kit/src/main/resources/assets/oriel/ui');
  const result = freeze({ pages, resources: [pages] }, read(path.join(root, 'preview/benchmarks/frames.json')));
  assert.equal(result.identity.pages.length, 21);
  assert.deepEqual(result.historicalPageDelta, { added: ['forge_station.html'], removed: [] });
});
