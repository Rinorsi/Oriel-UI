import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { entries, ROOT } from './query.mjs';
const pending = JSON.parse(fs.readFileSync(path.join(ROOT, 'tools/showcase/index.json'), 'utf8')).pending ?? [];

test('every indexed effect still resolves to a line in its page', () => {
  const lost = entries().filter(e => e.line < 0).map(e => e.page + ': ' + e.anchor);
  assert.deepEqual(lost, [], '源码里找不到这些锚点，更新 tools/showcase/index.json');
});

test('every showcase and demo page has at least one entry, and no entry is duplicated', () => {
  const root = path.join(ROOT, 'common/src/main/resources/assets/oriel/oriel');
  const pages = ['showcase', 'demo'].flatMap(dir => fs.readdirSync(path.join(root, dir)).filter(f => f.endsWith('.html')).map(f => dir + '/' + f));
  const indexed = new Set(entries().map(e => e.page));
  assert.deepEqual(pages.filter(p => !indexed.has(p) && p !== 'demo/chest.html' && !pending.includes(p)), [], '这些页面还没有索引条目');
  const keys = entries().map(e => e.page + '|' + e.anchor);
  assert.equal(new Set(keys).size, keys.length);
});

test('the table in docs/zh/showcase.md is up to date', async () => {
  const { table } = await import('./query.mjs');
  const doc = fs.readFileSync(path.join(ROOT, 'docs/zh/showcase.md'), 'utf8').replace(/\r\n/g, '\n');
  const a = doc.indexOf('<!-- showcase-index:start -->') + '<!-- showcase-index:start -->'.length, b = doc.indexOf('<!-- showcase-index:end -->');
  assert.ok(a > 30 && b > a, '文档缺少标记');
  assert.equal(doc.slice(a, b).trim(), table().trim(), '运行 node tools/showcase/query.mjs --write-doc');
});
