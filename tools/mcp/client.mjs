#!/usr/bin/env node
// Small transport client for integration tests and agent environments without dynamic MCP registration.
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
import { ROOT } from './lib/core.mjs';

export class McpClient {
  constructor(env = {}) {
    this.sequence = 0; this.pending = new Map(); this.stderr = '';
    this.child = spawn(process.execPath, [path.join(ROOT, 'tools/mcp/oriel-preview.mjs')], { cwd: ROOT, windowsHide: true, env: { ...process.env, ...env }, stdio: ['pipe', 'pipe', 'pipe'] });
    this.child.stderr.on('data', chunk => { this.stderr = (this.stderr + chunk).slice(-12000); });
    createInterface({ input: this.child.stdout }).on('line', line => {
      try {
        const message = JSON.parse(line), item = this.pending.get(message.id);
        if (!item) return; clearTimeout(item.timer); this.pending.delete(message.id);
        if (message.error) item.reject(new Error(JSON.stringify(message.error))); else item.resolve(message.result);
      } catch (error) { for (const item of this.pending.values()) item.reject(error); }
    });
    this.child.on('exit', code => { for (const item of this.pending.values()) { clearTimeout(item.timer); item.reject(new Error('MCP exited ' + code + ': ' + this.stderr)); } this.pending.clear(); });
  }
  request(method, params = {}, timeout = 600000) {
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('MCP request timed out: ' + method)); }, timeout);
      this.pending.set(id, { resolve, reject, timer });
      this.child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
    });
  }
  async initialize() {
    const result = await this.request('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'oriel-workflow-client', version: '1.0.0' } });
    this.child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n'); return result;
  }
  call(name, args = {}) { return this.request('tools/call', { name, arguments: args }); }
  close() { this.child.stdin.end(); }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [name = 'oriel_rules', argsFile, output] = process.argv.slice(2);
  const client = new McpClient();
  try {
    await client.initialize();
    const response = name.startsWith('@') ? await client.request(name.slice(1), argsFile ? JSON.parse(fs.readFileSync(argsFile, 'utf8')) : {}) : await client.call(name, argsFile ? JSON.parse(fs.readFileSync(argsFile, 'utf8')) : {});
    if (output) { fs.mkdirSync(path.dirname(path.resolve(output)), { recursive: true }); fs.writeFileSync(output, JSON.stringify(response, null, 2)); }
    const view = response.structuredContent || response;
    console.log(JSON.stringify(view, null, 2));
    if (response.isError) process.exitCode = 1;
  } finally { client.close(); }
}
