import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { STATE, initState, id, treeHash, fileHash, sha } from './lib/core.mjs';
import { detectScaffold } from './lib/scaffold.mjs';
import { installFiles } from './lib/transaction.mjs';
import { McpClient } from './client.mjs';

initState();
const base = process.env.ORIEL_TEST_TMP || STATE;
function write(root, rel, text) { const file = path.join(root, rel); fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, text); }
function fixture(loader = 'fabric', stonecutter = false) {
  const root = path.join(base, id('skeleton-' + loader)); fs.mkdirSync(root, { recursive: true });
  const prefix = stonecutter ? loader + '/' : '';
  write(root, 'settings.gradle', stonecutter ? "plugins { id 'dev.kikugie.stonecutter' version '0.9.8' }\nstonecutter { create(rootProject) { vcsVersion = '26.1.2'; branch('fabric') { version('26.1.2') } } }" : "rootProject.name = 'fixture'");
  write(root, prefix + 'build.gradle', loader === 'fabric' ? "plugins { id 'net.fabricmc.fabric-loom' }\ndependencies { implementation files('libs/oriel-fabric-26.1.2-0.11.0.jar') }" : "plugins { id 'net.neoforged.moddev' }");
  write(root, 'gradle.properties', 'minecraft_version=26.1.2\n');
  write(root, prefix + 'src/main/java/dev/example/Client.java', 'package dev.example;\npublic class Client {}\n');
  write(root, prefix + 'src/main/resources/' + (loader === 'fabric' ? 'fabric.mod.json' : 'META-INF/neoforge.mods.toml'), loader === 'fabric' ? JSON.stringify({ id: 'example_mod', depends: { minecraft: '26.1.2', oriel: '>=0.11.0 <0.12.0' } }) : '[[mods]]\nmodId="example_mod"\n[[dependencies.example_mod]]\nmodId="oriel"\ntype="required"\nversionRange="[0.11.0,0.12.0)"\n');
  return { projectRoot: root, launchMode: 'gradle-inside' };
}

for (const [loader, stonecutter] of [['fabric', false], ['neoforge', false], ['fabric', true]]) {
  test('scaffold detects ' + loader + (stonecutter ? ' Stonecutter' : '') + ' without writing', () => {
    const args = fixture(loader, stonecutter), before = treeHash(args.projectRoot);
    const plan = detectScaffold(args);
    assert.equal(plan.status, 'ready'); assert.equal(plan.loader, loader); assert.equal(plan.minecraft, '26.1.2');
    assert.equal(plan.modId, 'example_mod'); assert.equal(plan.stonecutter, stonecutter); assert.equal(plan.javaPackage, 'dev.example');
    assert.equal(plan.hotReload.case, 2); assert.match(plan.snippets.gradle, new RegExp('oriel-' + loader + '-26.1.2'));
    assert.match(plan.snippets.registration, /schedule/); assert.ok(plan.existingOriel.metadata[0].oriel);
    if (loader === 'fabric') assert.ok(plan.existingOriel.gradle.length);
    assert.equal(treeHash(args.projectRoot), before);
    assert.match(plan.files[0].content, /data-oriel-shell/);
    assert.equal(plan.resourcesDir, (stonecutter ? 'fabric/' : '') + 'src/main/resources');
  });
}

test('scaffold reports unknown/ambiguous inputs and launch modes without guessing', () => {
  const root = path.join(base, id('unknown')); fs.mkdirSync(root, { recursive: true });
  const before = treeHash(root), plan = detectScaffold({ projectRoot: root });
  assert.equal(plan.status, 'needs-information');
  for (const field of ['loader', 'minecraft', 'modId', 'resourcesDir', 'javaPackage', 'javaDir', 'launchMode']) assert.ok(plan.missing.includes(field));
  assert.equal(treeHash(root), before);
  const args = fixture('fabric', true);
  write(args.projectRoot, 'stonecutter.gradle', "versions('26.1.2', '26.2')");
  assert.ok(detectScaffold(args).missing.includes('minecraft'));
  const known = fixture(); delete known.launchMode;
  assert.ok(detectScaffold(known).missing.includes('launchMode'));
  assert.ok(detectScaffold(known).snippets.gradle);
  assert.equal(detectScaffold({ ...known, launchMode: 'gradle-outside' }).hotReload.case, 3);
  assert.match(detectScaffold({ ...known, launchMode: 'jar' }).hotReload.instruction, /dev-sources.properties/);
  write(known.projectRoot, 'src/main/resources/fabric.mod.json', JSON.stringify({ id: '$' + '{mod_id}', depends: { minecraft: '$' + '{minecraft_version}' } }));
  assert.ok(detectScaffold(known).missing.includes('modId'));
  assert.equal(detectScaffold({ ...known, modId: 'explicit_mod', launchMode: 'jar' }).modId, 'explicit_mod');
  write(known.projectRoot, 'gradle.properties', 'minecraft_version=26.1.2\nmod_id=from_properties\n');
  assert.equal(detectScaffold({ ...known, launchMode: 'jar' }).modId, 'from_properties');
});

test('scaffold rejects escaping paths, unsafe Java names, and junctions', () => {
  const args = fixture();
  for (const patch of [{ resourcesDir: '../outside' }, { javaDir: '../outside' }, { javaPackage: 'dev.class' }, { modId: '../outside' }, { projectRoot: args.projectRoot + '/../escape' }]) assert.throws(() => detectScaffold({ ...args, ...patch }));
  const outside = path.join(base, id('outside')); fs.mkdirSync(outside, { recursive: true });
  const link = path.join(args.projectRoot, 'linked');
  fs.symlinkSync(outside, link, process.platform === 'win32' ? 'junction' : 'dir');
  try {
    assert.throws(() => detectScaffold({ ...args, resourcesDir: 'linked/resources' }), e => e.code === 'SYMLINK');
    assert.throws(() => detectScaffold({ ...args, projectRoot: link }), e => e.code === 'SYMLINK');
  } finally { fs.unlinkSync(link); }
});

test('create-only transaction preserves concurrent creations and rolls back its earlier file', () => {
  const root = path.join(base, id('no-clobber')), source = path.join(root, 'source'), target = path.join(root, 'target');
  fs.mkdirSync(source, { recursive: true }); fs.mkdirSync(target);
  for (const file of ['one.txt', 'two.txt']) write(source, file, 'new');
  const files = ['one.txt', 'two.txt'].map(file => ({ file, destination: path.join(target, file), hash: sha('new'), previous: null }));
  const link = fs.linkSync;
  fs.linkSync = (from, to) => {
    if (to.endsWith('two.txt')) write(target, 'two.txt', 'concurrent');
    return link(from, to);
  };
  try { assert.throws(() => installFiles(files, source, target, path.join(root, 'backup'), fs.copyFileSync, { createOnly: true }), e => e.code === 'EEXIST'); }
  finally { fs.linkSync = link; }
  assert.equal(fs.existsSync(path.join(target, 'one.txt')), false);
  assert.equal(fs.readFileSync(path.join(target, 'two.txt'), 'utf8'), 'concurrent');
});

test('scaffold MCP prepare/check/review/apply requires approval, preserves files and is idempotent', { timeout: 240000 }, async () => {
  const args = fixture(), client = new McpClient();
  try {
    await client.initialize();
    const before = treeHash(args.projectRoot);
    const protectedFiles = ['settings.gradle', 'build.gradle', 'gradle.properties', 'src/main/resources/fabric.mod.json', 'src/main/java/dev/example/Client.java'].map(file => [file, fileHash(path.join(args.projectRoot, file))]);
    const read = await client.call('oriel_scaffold', args); assert.equal(read.isError, undefined);
    assert.equal(treeHash(args.projectRoot), before);
    const early = await client.call('oriel_scaffold', { ...args, apply: true }); assert.equal(early.structuredContent.code, 'APPROVAL_REQUIRED');
    assert.equal((await client.call('oriel_scaffold', { ...args, prepare: true, apply: true })).structuredContent.code, 'INVALID_ARGS');
    const prepared = await client.call('oriel_scaffold', { ...args, prepare: true });
    assert.equal(prepared.isError, undefined, JSON.stringify(prepared));
    assert.equal(prepared.structuredContent.check.passed, true, JSON.stringify(prepared.structuredContent.check));
    assert.equal(treeHash(args.projectRoot), before);
    const projectId = prepared.structuredContent.projectId;
    const review = await client.call('oriel_review', { projectId, mode: 'screenshots' });
    assert.equal(review.isError, undefined, JSON.stringify(review));
    const reviewId = review.structuredContent.id;
    assert.equal((await client.call('oriel_scaffold', { ...args, apply: true, reviewId })).structuredContent.code, 'APPROVAL_REQUIRED');
    // Exercise the public chat-decision mechanism with fixture-only approval text.
    const decision = await client.call('oriel_review', { action: 'decide', reviewId, decision: 'approved', quote: '测试夹具批准；仅限伪造工程。' });
    assert.equal(decision.isError, undefined, JSON.stringify(decision));
    const changed = await client.call('oriel_scaffold', { ...args, launchMode: 'jar', apply: true, reviewId });
    assert.equal(changed.structuredContent.code, 'SCAFFOLD_CHANGED');
    const installed = await client.call('oriel_scaffold', { ...args, apply: true, reviewId });
    assert.equal(installed.isError, undefined, JSON.stringify(installed));
    assert.equal(installed.structuredContent.files.length, 2); assert.equal(installed.structuredContent.gameVerified, false);
    for (const [file, hash] of protectedFiles) assert.equal(fileHash(path.join(args.projectRoot, file)), hash);
    for (const file of installed.structuredContent.files) assert.equal(fileHash(file.destination), file.hash);
    const after = treeHash(args.projectRoot);
    const repeat = await client.call('oriel_scaffold', { ...args, apply: true, reviewId });
    assert.equal(repeat.isError, undefined, JSON.stringify(repeat)); assert.equal(repeat.structuredContent.files.length, 0); assert.equal(repeat.structuredContent.skipped.length, 2);
    assert.equal(treeHash(args.projectRoot), after);
    const html = installed.structuredContent.files[0].destination; fs.writeFileSync(html, 'user changed this');
    assert.equal((await client.call('oriel_scaffold', { ...args, apply: true, reviewId })).isError, undefined);
    assert.equal(fs.readFileSync(html, 'utf8'), 'user changed this');
    const root2 = fixture();
    assert.equal((await client.call('oriel_scaffold', { ...root2, apply: true, reviewId })).structuredContent.code, 'SCAFFOLD_CHANGED');
  } finally { client.close(); }
});
