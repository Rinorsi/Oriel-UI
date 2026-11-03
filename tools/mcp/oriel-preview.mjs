#!/usr/bin/env node
// Oriel preview MCP server (stdio, no dependencies): lets an AI agent check, render and poke Oriel pages headless
// through the previewer, without a game. Register it with an MCP client, e.g.
//   { "command": "node", "args": ["tools/mcp/oriel-preview.mjs"], "cwd": "<repository root>" }
// Tools:
//   oriel_pages     list the kit pages and the icon names
//   oriel_check     the page checker (errors, missing images, CSS diagnostics) on a page or a directory
//   oriel_snapshot  render a page (optionally after actions: click / key / type / wait / move) and return the PNG,
//                   plus element boxes for a selector
// It runs preview/build/install/preview (built on first use with ./gradlew :preview:installDist).
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { createInterface } from 'node:readline';

const ROOT = resolve(process.env.ORIEL_ROOT || process.cwd());
const UI = join(ROOT, 'kit/src/main/resources/assets/oriel/ui');
const ICONS = join(ROOT, 'kit/src/main/resources/assets/oriel/textures/icons');
const LIB = join(ROOT, 'preview/build/install/preview/lib');
const JAVA = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';

function ensureBuilt() {
  if (existsSync(LIB)) return null;
  const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
  const r = spawnSync(gradlew, [':preview:installDist', '-q'], { cwd: ROOT, encoding: 'utf8', shell: process.platform === 'win32' });
  return r.status === 0 ? null : `building the previewer failed:\n${r.stdout}\n${r.stderr}`;
}

function preview(args) {
  const failed = ensureBuilt();
  if (failed) return { status: 1, out: failed };
  const r = spawnSync(JAVA, ['-Djava.awt.headless=true', '-cp', join(LIB, '*'), 'dev.oriel.preview.Preview', ...args],
    { cwd: ROOT, encoding: 'utf8', timeout: 120000 });
  return { status: r.status, out: `${r.stdout || ''}${r.stderr || ''}`.replace(/^Minecraft assets:.*\n/m, '') };
}

const page = (p) => (p.includes('/') || p.includes('\\') ? p : join(UI, p.endsWith('.html') ? p : `${p}.html`));

const TOOLS = [
  {
    name: 'oriel_pages',
    description: 'Lists the Oriel kit UI pages (kit/src/main/resources/assets/oriel/ui) and the 16x16 icon names usable as ../textures/icons/<name>.png.',
    inputSchema: { type: 'object', properties: {} },
    run: () => text(`pages: ${readdirSync(UI).filter((f) => f.endsWith('.html')).join(', ')}\nicons: ${readdirSync(ICONS).map((f) => f.replace('.png', '')).join(', ')}`),
  },
  {
    name: 'oriel_check',
    description: 'Checks a page (name like "forge", or a path) or a directory: loads each page with its Java controller at 640x360 and 427x240 and reports engine/controller errors, missing images and CSS the engine did not take. Run after every edit.',
    inputSchema: { type: 'object', properties: { target: { type: 'string', description: 'page name, page path or directory; default: all kit pages' } } },
    run: ({ target }) => {
      const r = preview(['--check', target ? page(target) : UI]);
      return text(r.out.trim() + (r.status === 0 ? '' : `\n(exit ${r.status})`));
    },
  },
  {
    name: 'oriel_snapshot',
    description: 'Renders a page headless and returns a PNG. Optional actions run first, one per line: "wait 30" (frames), "click <selector|x y>", "move <selector>", "key Enter", "type text", "wheel <selector> 40". Optional boxes prints the GUI-px box of every element matching a selector, to debug layout. 3D models and entities show as placeholders; the game is the final judge.',
    inputSchema: {
      type: 'object',
      required: ['page'],
      properties: {
        page: { type: 'string', description: 'page name like "inventory" or a path' },
        width: { type: 'number', description: 'GUI width, default 640' },
        height: { type: 'number', description: 'GUI height, default 360' },
        frames: { type: 'number', description: 'frames to run before the shot (no actions), default 60' },
        actions: { type: 'array', items: { type: 'string' } },
        boxes: { type: 'string', description: 'CSS selector whose element boxes to print' },
      },
    },
    run: ({ page: p, width = 640, height = 360, frames = 60, actions, boxes }) => {
      const dir = mkdtempSync(join(tmpdir(), 'oriel-mcp-'));
      const shot = join(dir, 'shot.png');
      const args = [page(p), '--scale', '2', '--size', `${width}x${height}`, '--snapshot', shot];
      if (actions?.length) {
        const file = join(dir, 'actions.txt');
        writeFileSync(file, actions.join('\n') + '\n');
        args.push('--actions', file, '--out', dir);
      } else {
        args.push('--frames', String(frames));
      }
      if (boxes) args.push('--boxes', boxes);
      const r = preview(args);
      const content = [{ type: 'text', text: r.out.trim() || 'rendered' }];
      if (existsSync(shot)) content.push({ type: 'image', mimeType: 'image/png', data: readFileSync(shot).toString('base64') });
      return { content, isError: r.status !== 0 };
    },
  },
];

const text = (t) => ({ content: [{ type: 'text', text: t }] });
const send = (msg) => process.stdout.write(JSON.stringify(msg) + '\n');

createInterface({ input: process.stdin }).on('line', (line) => {
  if (!line.trim()) return;
  let msg;
  try {
    msg = JSON.parse(line);
  } catch {
    return send({ jsonrpc: '2.0', id: null, error: { code: -32700, message: 'parse error' } });
  }
  const { id, method, params } = msg;
  if (id === undefined) return; // notifications (initialized, cancelled)
  try {
    if (method === 'initialize') {
      return send({ jsonrpc: '2.0', id, result: {
        protocolVersion: params?.protocolVersion || '2025-06-18',
        capabilities: { tools: {} },
        serverInfo: { name: 'oriel-preview', version: '1.0.0' },
      } });
    }
    if (method === 'ping') return send({ jsonrpc: '2.0', id, result: {} });
    if (method === 'tools/list') return send({ jsonrpc: '2.0', id, result: { tools: TOOLS.map(({ run, ...t }) => t) } });
    if (method === 'tools/call') {
      const tool = TOOLS.find((t) => t.name === params?.name);
      if (!tool) return send({ jsonrpc: '2.0', id, error: { code: -32602, message: `unknown tool ${params?.name}` } });
      return send({ jsonrpc: '2.0', id, result: tool.run(params.arguments || {}) });
    }
    send({ jsonrpc: '2.0', id, error: { code: -32601, message: `unknown method ${method}` } });
  } catch (e) {
    send({ jsonrpc: '2.0', id, result: { content: [{ type: 'text', text: String(e?.stack || e) }], isError: true } });
  }
});
