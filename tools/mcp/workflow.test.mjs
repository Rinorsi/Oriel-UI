import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { McpClient } from './client.mjs';
import { ROOT, STATE, initState, gradle, safePath, id, loadRecord, saveRecord, fileHash } from './lib/core.mjs';
import { assertCurrent } from './lib/projects.mjs';
import { installFiles } from './lib/transaction.mjs';
import { prompt } from './lib/rules.mjs';

initState();
test('Windows Gradle wrapper does not rely on current-directory command search', { skip: process.platform !== 'win32' }, async () => {
  const result = await gradle(['--version']);
  assert.equal(result.code, 0, result.output);
  assert.match(result.output, /Gradle/);
});

test('real MCP transport exposes instructions, tools, rules resources and source-owned prompts', async () => {
  const client = new McpClient();
  try {
    const initialized = await client.initialize();
    assert.match(initialized.instructions, /用户本人/);
    const listing = await client.request('tools/list');
    assert.equal(listing.tools.length, 10);
    assert.ok(listing.tools.some(t => t.name === 'oriel_integrate'));
    assert.ok(!listing.tools.some(t => /approve/.test(t.name)));
    const resource = await client.request('resources/read', { uri: 'oriel://rules/javascript' });
    assert.match(resource.contents[0].text, /fetch/);
    const prompts = await client.request('prompts/get', { name: 'workflow' });
    assert.equal(prompts.messages[0].content.text, prompt('workflow'));
    const rejection = await client.call('oriel_review', { action: 'approve', reviewId: 'fake' });
    assert.equal(rejection.isError, true); assert.equal(rejection.structuredContent.code, 'INVALID_ARGS');
    const unknown = await client.call('oriel_rules', { topic: 'overview', profile: { minecraft: '26.4' } });
    assert.equal(unknown.structuredContent.code, 'UNSUPPORTED_PROFILE');
  } finally { client.close(); }
});

test('resource paths cannot escape or traverse junctions', () => {
  const root = path.join(STATE, id('path-test')); fs.mkdirSync(root);
  assert.throws(() => safePath(root, '../outside.txt'), /路径超出/);
  assert.throws(() => safePath(root, 'file:C:/outside'), /相对路径/);
  const target = path.join(STATE, id('outside')); fs.mkdirSync(target);
  fs.symlinkSync(target, path.join(root, 'linked'), process.platform === 'win32' ? 'junction' : 'dir');
  assert.throws(() => safePath(root, 'linked/target.txt'), /符号链接/);
  fs.unlinkSync(path.join(root, 'linked'));
});

test('resource installation is atomic per file, rejects conflicts and restores earlier files on failure', () => {
  const base = path.join(STATE, id('transaction'));
  const source = path.join(base, 'source'), target = path.join(base, 'target'), backup = path.join(base, 'backup');
  fs.mkdirSync(source, { recursive: true }); fs.mkdirSync(target);
  for (const name of ['one.html', 'two.css']) fs.writeFileSync(path.join(source, name), 'new ' + name);
  fs.writeFileSync(path.join(target, 'one.html'), 'old');
  const files = ['one.html', 'two.css'].map(file => ({ file, destination: path.join(target, file), hash: fileHash(path.join(source, file)), previous: fileHash(path.join(target, file)) }));
  let copies = 0;
  assert.throws(() => installFiles(files, source, target, backup, (from, to) => { if (++copies === 2) { fs.writeFileSync(to, 'partial'); throw new Error('simulated interrupted copy'); } fs.copyFileSync(from, to); }), /interrupted copy/);
  assert.equal(fs.readFileSync(path.join(target, 'one.html'), 'utf8'), 'old');
  assert.deepEqual(fs.readdirSync(target), ['one.html']);
  installFiles(files, source, target, backup);
  assert.equal(fs.readFileSync(path.join(target, 'two.css'), 'utf8'), 'new two.css');
  assert.throws(() => installFiles(files, source, target, backup), /目标文件/);
});

test('checked page can be reviewed, but neither pending nor test decisions can install; changes invalidate receipts', { timeout: 180000 }, async () => {
  const source = path.join(STATE, id('fixture'));
  fs.mkdirSync(path.join(source, 'assets/workflow_test/ui'), { recursive: true });
  const html = path.join(source, 'assets/workflow_test/ui/index.html');
  fs.writeFileSync(html, '<html><head><title>Test</title></head><body><p id="status">ready</p><button id="click">Change</button><script>document.getElementById("click").addEventListener("click",function(){document.getElementById("status").textContent="changed";});</script></body></html>');
  const projectId = id('test'), client = new McpClient();
  let sessionId;
  try {
    await client.initialize();
    const registered = await client.call('oriel_project', { projectId, sourceRoot: source, namespace: 'workflow_test', scenes: [{ name: 'initial', actions: [] }, { name: 'changed', actions: ['click #click'] }] });
    assert.equal(registered.isError, undefined);
    const premature = await client.call('oriel_review', { projectId });
    assert.equal(premature.structuredContent.code, 'CHECK_REQUIRED');
    const checked = await client.call('oriel_check', { projectId });
    assert.equal(checked.isError, undefined, JSON.stringify(checked.structuredContent));
    assert.equal(checked.structuredContent.shots.length, 4);
    const prepared = await client.call('oriel_review', { projectId });
    assert.equal(prepared.isError, undefined, JSON.stringify(prepared));
    const item = prepared.structuredContent; sessionId = item.sessionId;
    const page = await fetch(item.url); assert.equal(page.status, 200);
    const markup = await page.text(); assert.match(markup, /网页验收通过/); assert.match(markup, /sandbox="allow-scripts"/);
    const denied = await client.call('oriel_integrate', { reviewId: item.id });
    assert.equal(denied.structuredContent.code, 'APPROVAL_REQUIRED');
    const decisionPath = new URL(item.url).origin + markup.match(/action="([^" ]+decision[^" ]+)"/)[1];
    const crossOrigin = await fetch(decisionPath, { method: 'POST', headers: { Origin: 'http://untrusted.invalid', 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'decision=approved' });
    assert.equal(crossOrigin.status, 409);
    assert.equal(loadRecord('reviews', item.id).decision, 'pending');
    // Isolated negative fixture: a test decision must never unlock installation.
    const testRecord = { ...loadRecord('reviews', item.id), kind: 'test', decision: 'approved', source: 'local-review-form' };
    saveRecord('reviews', testRecord);
    const testDenied = await client.call('oriel_integrate', { reviewId: item.id });
    assert.equal(testDenied.structuredContent.code, 'APPROVAL_REQUIRED');
    fs.appendFileSync(html, '<p>modified after review</p>');
    const status = await client.call('oriel_review', { action: 'status', reviewId: item.id });
    assert.equal(status.structuredContent.current, false); assert.equal(status.structuredContent.blocked.code, 'STALE_REVISION');
    assert.throws(() => assertCurrent(loadRecord('revisions', item.revisionId)), /变化/);
  } finally {
    if (sessionId) await client.call('oriel_web_preview', { action: 'stop', sessionId });
    client.close();
  }
});

test('structure failures return blocking diagnostics instead of approvable success', { timeout: 120000 }, async () => {
  const source = path.join(STATE, id('bad-fixture')); fs.mkdirSync(path.join(source, 'assets/bad_ui/ui'), { recursive: true });
  fs.writeFileSync(path.join(source, 'assets/bad_ui/ui/index.html'), '<html><body><iframe></iframe><img src="absent.png"><p id="same"></p><p id="same"></p><script>fetch("https://example.invalid");</script></body></html>');
  const client = new McpClient(), projectId = id('bad');
  try {
    await client.initialize();
    await client.call('oriel_project', { projectId, sourceRoot: source, namespace: 'bad_ui' });
    const result = await client.call('oriel_check', { projectId });
    assert.equal(result.isError, true);
    const codes = new Set(result.structuredContent.diagnostics.map(d => d.ruleId));
    for (const code of ['duplicate-id', 'browser-element', 'browser-api', 'MISSING_RESOURCE']) assert.ok(codes.has(code), JSON.stringify([...codes]));
    const review = await client.call('oriel_review', { projectId }); assert.equal(review.structuredContent.code, 'CHECK_REQUIRED');
  } finally { client.close(); }
});
