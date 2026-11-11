#!/usr/bin/env node
// Looks effects up in the showcase source: which page, which line, how, and the code itself.
//   node tools/showcase/query.mjs 视差 无缝          # every term must match (effect, how, tags, page)
//   node tools/showcase/query.mjs box-shadow --list   # headings only
//   node tools/showcase/query.mjs --page showcase/title.html --list
//   node tools/showcase/query.mjs 进度条 --lines 30 --json
//   node tools/showcase/query.mjs --write-doc          # refresh the table in docs/zh/templates.md
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const ROOT = path.resolve(HERE, '../..');
const index = JSON.parse(fs.readFileSync(path.join(HERE, 'index.json'), 'utf8'));
const SOURCE = path.join(ROOT, index.root);
const COMMENT = /(\/\*|\/\/|<!--)/;

/** The 1-based line of the entry's anchor: the first comment line containing it, else the first line that does. */
export function locate(entry) {
  const lines = fs.readFileSync(path.join(SOURCE, entry.page), 'utf8').split(/\r?\n/);
  let any = -1;
  for (let i = 0; i < lines.length; i++) {
    if (!lines[i].includes(entry.anchor)) continue;
    if (COMMENT.test(lines[i])) return i + 1;
    if (any < 0) any = i + 1;
  }
  return any;
}

export function entries() {
  return index.entries.map(e => ({ ...e, line: locate(e), file: index.root + e.page }));
}

function excerpt(entry, count) {
  const lines = fs.readFileSync(path.join(SOURCE, entry.page), 'utf8').split(/\r?\n/);
  return lines.slice(entry.line - 1, entry.line - 1 + count).map((text, i) => String(entry.line + i).padStart(5) + '  ' + text).join('\n');
}

export function table() {
  const byPage = new Map();
  for (const e of entries()) (byPage.get(e.page) ?? byPage.set(e.page, []).get(e.page)).push(e);
  let out = '';
  for (const [page, list] of byPage) {
    out += '\n### `' + page + '`' + (index.pages?.[page] ? '：' + index.pages[page] : '') + '\n\n| 效果 | 位置 | 怎么做 |\n|---|---|---|\n';
    for (const e of list) out += '| ' + e.effect + ' | `' + path.basename(e.page) + ':' + e.line + '` | ' + e.how + ' |\n';
  }
  return out;
}

function main(argv) {
  const terms = [], opts = { lines: 14 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--list') opts.list = true;
    else if (a === '--json') opts.json = true;
    else if (a === '--write-doc') opts.write = true;
    else if (a === '--lines') opts.lines = Number(argv[++i]) || 14;
    else if (a === '--page') opts.page = argv[++i];
    else terms.push(a.toLowerCase());
  }
  if (opts.write) {
    const doc = path.join(ROOT, 'docs/zh/templates.md');
    const text = fs.readFileSync(doc, 'utf8');
    const start = '<!-- showcase-index:start -->', end = '<!-- showcase-index:end -->';
    const a = text.indexOf(start), b = text.indexOf(end);
    if (a < 0 || b < a) throw new Error('docs/zh/templates.md has no showcase-index markers');
    fs.writeFileSync(doc, text.slice(0, a + start.length) + '\n' + table() + text.slice(b));
    console.log('updated docs/zh/templates.md');
    return;
  }
  const found = entries().filter(e => (!opts.page || e.page === opts.page)
    && terms.every(t => (e.effect + ' ' + e.how + ' ' + e.tags.join(' ') + ' ' + e.page).toLowerCase().includes(t)));
  if (opts.json) return console.log(JSON.stringify(found, null, 2));
  if (!found.length) return console.log('没有匹配。换个词，或用 --list --page <页面> 看某页的全部效果。页面：' + [...new Set(index.entries.map(e => e.page))].join('、'));
  const shown = opts.list ? found : found.slice(0, 5);
  for (const e of shown) {
    console.log(`${e.page}:${e.line}  ${e.effect}\n  ${e.how}   [${e.tags.join(' ')}]`);
    if (!opts.list) console.log(excerpt(e, opts.lines) + '\n');
  }
  if (!opts.list && found.length > shown.length) console.log(`还有 ${found.length - shown.length} 条匹配，加 --list 看标题，或更具体的词。`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main(process.argv.slice(2));
