import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import * as release from './check-release.mjs';
import {auditEntries} from './check-release.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const licenses = ['LICENSE_Oriel UI', 'NOTICE_Oriel UI', ...['LICENSE.txt', 'NOTICE.txt', 'SOURCE.txt'].map(n => 'META-INF/licenses/rhino/' + n), 'META-INF/licenses/fonts/font.txt'];

test('slim rejects reference entries and requires its core, while full permits development content', () => {
  assert.equal(typeof release.classifyEntry, 'function');
  for (const entry of ['assets/oriel/ui/templates/catalog.json', 'assets/oriel/ui/gallery.html',
    'dev/oriel/kit/preset/StylesPreset.class', 'dev/oriel/mod/internal/client/showcase/Mobdex.class',
    'dev/oriel/mod/internal/gametest/GameTests.class', 'data/oriel/test_instance/forge_menu.json']) {
    assert.equal(release.classifyEntry(entry), 'reference', entry);
    assert.ok(auditEntries([...licenses, entry], {variant: 'slim'}).some(s => s.includes(entry)));
  }
  assert.ok(auditEntries(licenses, {variant:'slim'}).some(s => s.includes('ore.css')));
  assert.ok(auditEntries(licenses, {variant:'slim'}).some(s => s.includes('META-INF/oriel/boundaries.json')));
  assert.deepEqual(auditEntries([...licenses, 'dev/oriel/mod/internal/client/DevReload.class'], {variant:'full'}), []);
});

test('core hashes include paths and class bytes but ignore reference, dev and distribution metadata', () => {
  const core = new Map([['dev/oriel/engine/Test.class', Buffer.from('class')], ['assets/oriel/ui/ore.css', Buffer.from('css')]]);
  const hash = release.coreHash(core);
  assert.equal(hash, release.coreHash(new Map([...core].reverse())));
  assert.equal(hash, release.coreHash(new Map([...core, ['assets/oriel/ui/templates/01/page.js', Buffer.from('demo')],
    ['META-INF/MANIFEST.MF', Buffer.from('version')], ['fabric.mod.json', Buffer.from('version')]])));
  assert.notEqual(hash, release.coreHash(new Map([...core, ['dev/oriel/engine/Test.class', Buffer.from('changed')]])));
  assert.notEqual(hash, release.coreHash(new Map([['dev/oriel/engine/Renamed.class', Buffer.from('class')], [...core][1]])));
  assert.throws(() => release.checkCoreBaseline('0.12', 'fabric-26.3', hash, {'0.12.fabric-26.3':'0'.repeat(64)}), /核心有变，需要升 minor/);
  assert.throws(() => release.checkCoreBaseline('0.12', 'fabric-26.3', hash, {}, true), /Missing core hash baseline/);
  assert.doesNotThrow(() => release.checkCoreBaseline('0.12', 'fabric-26.3', hash, {'0.12.fabric-26.3':hash}, true));
});

test('variant versions and template ranges reject equal patches, wrong minors and excluded variants', () => {
  assert.deepEqual(release.variantVersions('0.12.0', '1'), {slim:'0.12.0', full:'0.12.1'});
  assert.throws(() => release.variantVersions('0.12.1', '1'));
  assert.throws(() => release.variantVersions('0.12.0', '0'));
  for (const range of ['>=0.12.0 <0.13.0', '[0.12.0,0.13.0)']) {
    assert.equal(release.inRange('0.12.0', range), true);
    assert.equal(release.inRange('0.12.1', range), true);
    assert.equal(release.inRange('0.13.0', range), false);
  }
  assert.equal(release.inRange('0.12.0', '>=0.11.0 <0.12.0'), false);
});

test('current nested fixtures retain real variant bytes and express conflicting ranges', () => {
  assert.ok(process.env.ORIEL_TEST_TMP);
  const dir = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'slim-jij-'));
  const bin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
  const slim = path.join(dir, 'slim.jar'), full = path.join(dir, 'full.jar');
  fs.writeFileSync(slim, 'real-slim-bytes'); fs.writeFileSync(full, 'real-full-bytes');
  for (const loader of ['fabric','neoforge']) for (const scenario of ['slim','top-full','two-parents','conflict']) {
    const out = path.join(dir, loader, scenario);
    execFileSync(bin, [path.join(root,'tools/compat/BundleFixtures.java'), loader, slim, out, full, scenario]);
    const entries = release.readJar(path.join(out,'oriel_bundle_1.jar')).entries;
    assert.deepEqual(entries.get('META-INF/jars/oriel.jar'), fs.readFileSync(slim));
    if (scenario === 'top-full') assert.deepEqual(fs.readFileSync(path.join(out,'oriel-full.jar')), fs.readFileSync(full));
    if (['two-parents','conflict'].includes(scenario)) {
      const second = release.readJar(path.join(out,'oriel_bundle_2.jar')).entries;
      assert.deepEqual(second.get('META-INF/jars/oriel.jar'), fs.readFileSync(full));
      const meta = second.get(loader === 'fabric' ? 'fabric.mod.json' : 'META-INF/neoforge.mods.toml').toString();
      assert.ok(meta.includes(scenario === 'conflict' ? '0.13.0' : '0.12.0'));
    }
  }
});

test('local Loom exclusions accept access-widened outputs and reject downloaded lookalikes', () => {
  assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to an external test directory');
  const dir = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'loom-origin-'));
  const bin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin') : '';
  const java = name => bin ? path.join(bin, name) : name;
  execFileSync(java('javac'), ['-Xlint:all', '-Werror', '-d', dir,
    path.join(root, 'build-logic/src/main/java/dev/oriel/buildlogic/GeneratedMinecraftArtifact.java'),
    path.join(root, 'tools/fixtures/GeneratedMinecraftProbe.java')]);
  assert.equal(execFileSync(java('java'), ['-cp', dir, 'GeneratedMinecraftProbe', dir], {encoding:'utf8'}).trim(), 'PASS');
});

test('canonical JAR checks ignore packaging metadata and reject changed entry bytes', () => {
  assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to an external test directory');
  const dir = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'canonical-jar-'));
  const bin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin') : '';
  const java = name => bin ? path.join(bin, name) : name;
  execFileSync(java('javac'), ['-Xlint:all', '-Werror', '-d', dir,
    path.join(root, 'build-logic/src/main/java/dev/oriel/buildlogic/CanonicalJarDigest.java'),
    path.join(root, 'tools/fixtures/CanonicalProbe.java')]);
  assert.equal(execFileSync(java('java'), ['-cp', dir, 'CanonicalProbe', dir], {encoding:'utf8'}).trim(), 'PASS');
});

test('release inventory rejects missing notices, dev classes and test fixtures', () => {
  assert.deepEqual(auditEntries(licenses), []);
  for (const entry of licenses) assert.ok(auditEntries(licenses.filter(p => p !== entry)).length, entry);
  for (const entry of ['dev/oriel/mod/internal/client/DevReload.class', 'dev/oriel/mod/internal/client/DevAutopilot$Step.class', 'taffy/case.html',
    'dev/oriel/mod/internal/client/DevInspector.class', 'dev/oriel/engine/inspect/Inspection.class',
    'dev/oriel/engine/inspect/BoxHighlight.class', 'dev/oriel/engine/script/InspectorBindings.class', 'assets/oriel/oriel/dev/inspector.html']) {
    assert.ok(auditEntries([...licenses, entry]).length, entry);
  }
});

test('optional development classes remain unreachable when removed, even with forced properties', () => {
  assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to an external test directory');
  const dir = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'release-flags-'));
  const bin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin') : '';
  const java = name => bin ? path.join(bin, name) : name;
  const pkg = 'dev.oriel.mod.internal.client';
  const classes = ['DevAutopilot', 'DevTour', 'DevReload', 'DevInspector'];
  const sources = classes.map(name => {
    const file = path.join(dir, name + '.java');
    fs.writeFileSync(file, `package ${pkg}; public class ${name} { static { System.setProperty("initialized.${name}", "true"); } public static void init() {} }`);
    return file;
  });
  const probe = path.join(dir, 'Probe.java');
  fs.writeFileSync(probe, `package ${pkg}; public class Probe { public static void main(String[] args) {
    boolean present = Boolean.parseBoolean(args[0]), requested = Boolean.parseBoolean(args[1]);
    if (DevFlags.AUTOPILOT != (present && requested) || DevFlags.TOUR != (present && requested) || DevFlags.RELOAD_AVAILABLE != present || DevFlags.INSPECTOR_AVAILABLE != present) throw new AssertionError("flags");
    for (String name : new String[]{"DevAutopilot", "DevTour", "DevReload", "DevInspector"}) if (System.getProperty("initialized." + name) != null) throw new AssertionError("eager initialization");
    if (DevFlags.RELOAD_AVAILABLE) DevReload.init();
    System.out.println("PASS");
  } }`);
  execFileSync(java('javac'), ['-Xlint:all', '-Werror', '-d', dir, path.join(root, 'common/src/main/java/dev/oriel/mod/internal/client/DevFlags.java'), ...sources, probe]);
  for (const present of [true, false]) {
    if (!present) for (const name of classes) fs.unlinkSync(path.join(dir, ...pkg.split('.'), name + '.class'));
    for (const requested of [false, true]) {
      const output = execFileSync(java('java'), ['-Doriel.autopilot=' + requested, '-Doriel.tour=' + requested, '-cp', dir, pkg + '.Probe', String(present), String(requested)], {encoding: 'utf8'});
      assert.equal(output.trim(), 'PASS');
    }
  }
});
