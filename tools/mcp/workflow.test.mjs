import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { McpClient } from './client.mjs';
import { ROOT, STATE, initState, gradle, safePath, id, loadRecord, saveRecord, fileHash } from './lib/core.mjs';
import { assertCurrent } from './lib/projects.mjs';
import { installFiles } from './lib/transaction.mjs';
import { prompt } from './lib/rules.mjs';
import { modProjectFiles } from './lib/mod-project.mjs';

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
    assert.equal(listing.tools.length, 17);
    const inspect = listing.tools.find(tool => tool.name === 'oriel_inspect');
    assert.deepEqual(inspect.inputSchema.required, ['page', 'element']);
    assert.equal(inspect.inputSchema.properties.property.type, 'string');
    const invalidInspect = await client.call('oriel_inspect', { page: 'settings', element: 'body', selector: 'body', property: 'color' });
    assert.equal(invalidInspect.structuredContent.code, 'INVALID_INSPECT');
    assert.ok(listing.tools.some(t => t.name === 'oriel_layout'));
    assert.ok(listing.tools.some(t => t.name === 'oriel_scaffold'));
    assert.ok(listing.tools.some(t => t.name === 'oriel_integrate'));
    assert.ok(!listing.tools.some(t => /approve/.test(t.name)));
    const resource = await client.request('resources/read', { uri: 'oriel://rules/javascript' });
    assert.match(resource.contents[0].text, /fetch/);
    const layout = await client.request('resources/read', { uri: 'oriel://rules/layout' });
    assert.match(layout.contents[0].text, /oriel.layout.block/);
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

test('screenshots review: images come back, the user quote is required, chat approval installs into a folder and nowhere else', { timeout: 300000 }, async () => {
  const source = path.join(STATE, id('shots-fixture'));
  fs.mkdirSync(path.join(source, 'assets/shots_test/ui'), { recursive: true });
  const html = path.join(source, 'assets/shots_test/ui/index.html'), css = path.join(source, 'assets/shots_test/ui/style.css');
  fs.writeFileSync(html, '<html><head><title>Test</title><link rel="stylesheet" href="style.css"></head><body><p id="status">ready</p></body></html>');
  fs.writeFileSync(css, 'p { color: #6cc349; }');
  const folder = path.join(STATE, id('my-pages')), client = new McpClient(), projectId = id('shots');
  const scenes = [{ name: 'initial', actions: [] }];
  const checkAndPrepare = async () => {
    const checked = await client.call('oriel_check', { projectId });
    assert.equal(checked.isError, undefined, JSON.stringify(checked.structuredContent));
    const prepared = await client.call('oriel_review', { projectId, mode: 'screenshots' });
    assert.equal(prepared.isError, undefined, JSON.stringify(prepared.structuredContent));
    return prepared;
  };
  try {
    await client.initialize();
    assert.equal((await client.call('oriel_project', { projectId, sourceRoot: source, namespace: 'shots_test', entry: 'ui/index.html', scenes })).isError, undefined);
    const prepared = await checkAndPrepare();
    assert.ok(prepared.content.some(part => part.type === 'image'), 'the screenshots travel back as images');
    const item = prepared.structuredContent;
    assert.equal(item.mode, 'screenshots'); assert.equal(item.decision, 'pending'); assert.equal(item.agentMayApprove, false);
    // Pending: nothing installs.
    const downstream = path.join(process.env.ORIEL_TEST_TMP || STATE, id('downstream'));
    fs.mkdirSync(path.join(downstream, 'src/main/resources'), { recursive: true });
    fs.writeFileSync(path.join(downstream, 'src/main/resources/fabric.mod.json'), JSON.stringify({ id: 'downstream_test' }));
    const modArgs = { reviewId: item.id, target: 'mod-project', projectRoot: downstream, pageName: 'settings' };
    assert.equal((await client.call('oriel_integrate', modArgs)).structuredContent.code, 'APPROVAL_REQUIRED');
    assert.equal((await client.call('oriel_integrate', { reviewId: item.id, target: 'oriel-folder', folder })).structuredContent.code, 'APPROVAL_REQUIRED');
    // The user's words are required, and a web review cannot be decided from chat.
    assert.equal((await client.call('oriel_review', { action: 'decide', reviewId: item.id, decision: 'approved' })).structuredContent.code, 'QUOTE_REQUIRED');
    assert.equal((await client.call('oriel_review', { action: 'decide', reviewId: item.id, decision: 'approved', quote: '   ' })).structuredContent.code, 'QUOTE_REQUIRED');
    const webItem = { ...loadRecord('reviews', item.id), id: id('review'), mode: 'web' }; saveRecord('reviews', webItem);
    assert.equal((await client.call('oriel_review', { action: 'decide', reviewId: webItem.id, decision: 'approved', quote: 'looks good' })).structuredContent.code, 'REVIEW_MODE');
    const decided = await client.call('oriel_review', { action: 'decide', reviewId: item.id, decision: 'approved', quote: '可以，就这样接入' });
    assert.equal(decided.isError, undefined, JSON.stringify(decided.structuredContent));
    assert.equal(decided.structuredContent.source, 'user-chat'); assert.equal(decided.structuredContent.approvalStrength, 'chat-attested-by-agent');
    assert.equal((await client.call('oriel_review', { action: 'decide', reviewId: item.id, decision: 'rejected', quote: '又改主意了' })).structuredContent.code, 'REVIEW_CLOSED');
    // A chat approval record that was tampered with does not unlock anything.
    for (const tamper of [{ quote: '' }, { mode: 'web' }, { shotHashes: [] }, { source: 'agent' }]) {
      const bad = { ...loadRecord('reviews', item.id), id: id('review'), ...tamper }; saveRecord('reviews', bad);
      assert.equal((await client.call('oriel_integrate', { reviewId: bad.id, target: 'oriel-folder', folder })).structuredContent.code, 'APPROVAL_REQUIRED', JSON.stringify(tamper));
    }
    // The folder target needs an absolute folder outside the repository.
    assert.equal((await client.call('oriel_integrate', { reviewId: item.id, target: 'oriel-folder' })).structuredContent.code, 'FOLDER_REQUIRED');
    assert.equal((await client.call('oriel_integrate', { reviewId: item.id, target: 'oriel-folder', folder: 'relative/dir' })).structuredContent.code, 'FOLDER_REQUIRED');
    assert.equal((await client.call('oriel_integrate', { reviewId: item.id, target: 'oriel-folder', folder: path.join(ROOT, 'build/my-pages') })).structuredContent.code, 'FOLDER_IN_REPOSITORY');
    const plan = (await client.call('oriel_integrate', { reviewId: item.id, target: 'oriel-folder', folder })).structuredContent;
    assert.equal(plan.target, 'oriel-folder', JSON.stringify(plan)); assert.equal(plan.pageUrl, 'oriel_mine:ui/index.html');
    assert.deepEqual(plan.files.map(f => f.target).sort(), ['ui/index.html', 'ui/style.css']);
    const applied = await client.call('oriel_integrate', { action: 'apply', planId: plan.id });
    assert.equal(applied.isError, undefined, JSON.stringify(applied.structuredContent));
    assert.equal(fileHash(path.join(folder, 'ui/index.html')), fileHash(html)); assert.equal(fileHash(path.join(folder, 'ui/style.css')), fileHash(css));
    assert.equal(fileHash(path.join(ROOT, 'common/src/main/resources/assets/shots_test/ui/index.html')), null, 'nothing is written into the repository');
    const modPlan = (await client.call('oriel_integrate', modArgs)).structuredContent;
    assert.equal(modPlan.pageUrl, 'downstream_test:ui/settings/index.html', JSON.stringify(modPlan));
    assert.match(modPlan.guidance.openCode, /OrielScreens.open/);
    const modApplied = await client.call('oriel_integrate', { action: 'apply', planId: modPlan.id });
    assert.equal(modApplied.isError, undefined, JSON.stringify(modApplied));
    assert.equal(modApplied.structuredContent.gameVerified, false);
    assert.match(modApplied.structuredContent.guidance.hotReload, /hot-reload.md/);
    const installed = path.join(downstream, 'src/main/resources/assets/downstream_test/ui/settings/index.html');
    assert.equal(fileHash(installed), fileHash(html));
    // A new plan for the same approved revision is idempotent; the original plan remains single-use.
    const repeat = (await client.call('oriel_integrate', modArgs)).structuredContent;
    assert.equal((await client.call('oriel_integrate', { action: 'apply', planId: repeat.id })).isError, undefined);
    assert.equal((await client.call('oriel_integrate', { action: 'apply', planId: repeat.id })).structuredContent.code, 'STALE_PLAN');
    const conflict = (await client.call('oriel_integrate', modArgs)).structuredContent;
    fs.writeFileSync(installed, 'concurrent edit');
    assert.equal((await client.call('oriel_integrate', { action: 'apply', planId: conflict.id })).structuredContent.code, 'TARGET_CONFLICT');
    assert.equal(fs.readFileSync(installed, 'utf8'), 'concurrent edit');
    fs.writeFileSync(path.join(downstream, 'unrelated.txt'), 'preserved');
    const linkedPlan = (await client.call('oriel_integrate', modArgs)).structuredContent;
    const assets = path.join(downstream, 'src/main/resources/assets');
    const originalAssets = assets + '-original';
    const escape = path.join(process.env.ORIEL_TEST_TMP || STATE, id('apply-escape'));
    fs.mkdirSync(escape, { recursive: true });
    fs.renameSync(assets, originalAssets);
    fs.symlinkSync(escape, assets, process.platform === 'win32' ? 'junction' : 'dir');
    try {
      assert.equal((await client.call('oriel_integrate', { action: 'apply', planId: linkedPlan.id })).structuredContent.code, 'SYMLINK');
      assert.deepEqual(fs.readdirSync(escape), []);
    } finally { fs.unlinkSync(assets); fs.renameSync(originalAssets, assets); }
    assert.equal(fs.readFileSync(path.join(downstream, 'unrelated.txt'), 'utf8'), 'preserved');
    assert.equal((await client.call('oriel_integrate', { ...modArgs, resourcesDir: '../escape' })).structuredContent.code, 'PATH_ESCAPE');
    assert.equal((await client.call('oriel_game_preview', { projectId })).structuredContent.code, 'DOWNSTREAM_GAME_REQUIRED');
    // A page that names its own namespace cannot be copied into a folder, where the namespace is oriel_mine.
    fs.writeFileSync(css, 'p { color: #6cc349; } /* see shots_test:ui/other.png */');
    await client.call('oriel_project', { projectId, sourceRoot: source, namespace: 'shots_test', entry: 'ui/index.html', scenes });
    const second = (await checkAndPrepare()).structuredContent;
    assert.equal((await client.call('oriel_review', { action: 'decide', reviewId: second.id, decision: 'approved', quote: 'ok' })).isError, undefined);
    assert.equal((await client.call('oriel_integrate', { reviewId: second.id, target: 'oriel-folder', folder })).structuredContent.code, 'FOLDER_NAMESPACE_REFERENCE');
    assert.equal((await client.call('oriel_integrate', { ...modArgs, reviewId: second.id })).structuredContent.code, 'MOD_NAMESPACE_REFERENCE');
    // Changing the page after the decision makes the approval stale.
    fs.appendFileSync(html, '<p>changed after approval</p>');
    assert.equal((await client.call('oriel_integrate', { reviewId: second.id, target: 'oriel-folder', folder })).isError, true);
  } finally { client.close(); }
});

test('mod projects detect resources and metadata, return choices, and reject junctions', () => {
  const root = path.join(process.env.ORIEL_TEST_TMP || STATE, id('mod-detection'));
  fs.mkdirSync(root, { recursive: true });
  const revision = { projectId: 'settings', project: { namespace: 'source_test', entry: 'ui/index.html' }, files: [] };
  assert.throws(() => modProjectFiles(revision, { projectRoot: root }), error => error.code === 'RESOURCE_DIRECTORY_REQUIRED' && error.details.candidates.length === 4);
  fs.mkdirSync(path.join(root, 'fabric/src/main/resources'), { recursive: true });
  assert.throws(() => modProjectFiles(revision, { projectRoot: root }), error => error.code === 'MOD_ID_REQUIRED');
  fs.writeFileSync(path.join(root, 'fabric/src/main/resources/fabric.mod.json'), 'invalid json');
  assert.throws(() => modProjectFiles(revision, { projectRoot: root }), error => error.code === 'MOD_ID_REQUIRED');
  assert.equal(modProjectFiles(revision, { projectRoot: root, modId: 'explicit_mod' }).modId, 'explicit_mod');
  fs.unlinkSync(path.join(root, 'fabric/src/main/resources/fabric.mod.json'));
  fs.mkdirSync(path.join(root, 'neoforge/src/main/resources/META-INF'), { recursive: true });
  assert.throws(() => modProjectFiles(revision, { projectRoot: root }), error => error.code === 'RESOURCE_DIRECTORY_REQUIRED' && error.details.candidates.length === 2);
  fs.writeFileSync(path.join(root, 'neoforge/src/main/resources/META-INF/neoforge.mods.toml'), '[[mods]]\nmodId = "detected_mod"\n[[dependencies.detected_mod]]\nmodId = "minecraft"\n');
  const selected = modProjectFiles(revision, { projectRoot: root, resourcesDir: 'neoforge/src/main/resources' });
  assert.equal(selected.modId, 'detected_mod');
  assert.match(selected.guidance.hotReload, /neoforge/);
  fs.mkdirSync(path.join(root, 'common/src/main/resources'), { recursive: true });
  assert.equal(modProjectFiles(revision, { projectRoot: root }).resourcesDir, 'common/src/main/resources');
  fs.writeFileSync(path.join(root, 'fabric/src/main/resources/fabric.mod.json'), JSON.stringify({ id: 'another_mod' }));
  assert.throws(() => modProjectFiles(revision, { projectRoot: root }), error => error.code === 'MOD_ID_REQUIRED' && error.details.candidates.length === 2);
  assert.throws(() => modProjectFiles(revision, { projectRoot: root + '/../unused' }), error => error.code === 'PROJECT_ROOT_REQUIRED');
  assert.throws(() => modProjectFiles(revision, { projectRoot: root, resourcesDir: '../outside' }), error => error.code === 'PATH_ESCAPE');
  assert.throws(() => modProjectFiles(revision, { projectRoot: root, resourcesDir: 'unknown', modId: '../bad' }), error => error.code === 'MOD_ID_REQUIRED');
  const outside = path.join(process.env.ORIEL_TEST_TMP || STATE, id('outside-mod'));
  fs.mkdirSync(outside, { recursive: true });
  const link = path.join(root, 'linked');
  fs.symlinkSync(outside, link, process.platform === 'win32' ? 'junction' : 'dir');
  try {
    assert.throws(() => modProjectFiles(revision, { projectRoot: root, resourcesDir: 'linked/resources', modId: 'test_mod' }), error => error.code === 'SYMLINK');
    assert.throws(() => modProjectFiles(revision, { projectRoot: link, modId: 'test_mod' }), error => error.code === 'SYMLINK');
  } finally { fs.unlinkSync(link); }
});
