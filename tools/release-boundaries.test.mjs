import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {auditEntries} from './check-release.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const licenses = ['LICENSE_Oriel UI', 'NOTICE_Oriel UI', ...['LICENSE.txt', 'NOTICE.txt', 'SOURCE.txt'].map(n => 'META-INF/licenses/rhino/' + n), 'META-INF/licenses/fonts/font.txt'];

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
  for (const entry of ['dev/oriel/mod/internal/client/DevReload.class', 'dev/oriel/mod/internal/client/DevAutopilot$Step.class', 'taffy/case.html']) {
    assert.ok(auditEntries([...licenses, entry]).length, entry);
  }
});

test('optional development classes remain unreachable when removed, even with forced properties', () => {
  assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to an external test directory');
  const dir = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'release-flags-'));
  const bin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin') : '';
  const java = name => bin ? path.join(bin, name) : name;
  const pkg = 'dev.oriel.mod.internal.client';
  const classes = ['DevAutopilot', 'DevTour', 'DevReload'];
  const sources = classes.map(name => {
    const file = path.join(dir, name + '.java');
    fs.writeFileSync(file, `package ${pkg}; public class ${name} { static { System.setProperty("initialized.${name}", "true"); } public static void init() {} }`);
    return file;
  });
  const probe = path.join(dir, 'Probe.java');
  fs.writeFileSync(probe, `package ${pkg}; public class Probe { public static void main(String[] args) {
    boolean present = Boolean.parseBoolean(args[0]), requested = Boolean.parseBoolean(args[1]);
    if (DevFlags.AUTOPILOT != (present && requested) || DevFlags.TOUR != (present && requested) || DevFlags.RELOAD_AVAILABLE != present) throw new AssertionError("flags");
    for (String name : new String[]{"DevAutopilot", "DevTour", "DevReload"}) if (System.getProperty("initialized." + name) != null) throw new AssertionError("eager initialization");
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
