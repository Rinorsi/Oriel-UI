import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { ROOT } from './lib/core.mjs';
import { topics, rules, prompt } from './lib/rules.mjs';

test('every published rules topic resolves after handbook consolidation', () => {
  for (const topic of Object.keys(topics)) {
    const result = rules({ topic });
    assert.equal(result.found, true, topic);
    assert.ok(result.text.startsWith('# '), topic);
    assert.equal(result.text, fs.readFileSync(path.join(ROOT, result.source), 'utf8'), topic);
    assert.match(result.rulesHash, /^[a-f0-9]{64}$/);
  }
  assert.equal(rules({ topic: 'quickref' }).source, 'docs/zh/design.md');
  assert.equal(rules({ topic: 'showcase' }).source, 'docs/zh/templates.md');
  assert.equal(rules({ topic: 'upgrading' }).source, 'docs/zh/distribution.md');
});

test('merged prompt sections remain isolated and retain the approval contract', () => {
  for (const name of ['workflow', 'revise', 'integrate']) {
    const text = prompt(name);
    assert.ok(text.length > 100, name);
    assert.ok(!text.startsWith('# MCP'), name);
    assert.ok(!/^## /m.test(text), name);
    assert.ok(!text.includes('协作与证据'), name);
    assert.match(text, /批准|同意/, name);
  }
  assert.match(prompt('workflow'), /Agent 不得代点确认/);
  assert.match(prompt('revise'), /不得使用旧版批准安装新版页面/);
  assert.match(prompt('integrate'), /未批准、被拒绝、检查失败或内容陈旧/);
});
