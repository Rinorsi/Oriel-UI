import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const policy = JSON.parse(fs.readFileSync(path.join(root, 'gradle/errorprone.json'), 'utf8'));

// Opt in with the exact resolved compiler classpath, never download or guess cache contents.
test('JDK 25 Error Prone rejects defects, accepts correct and generated code',
  {skip: !process.env.ORIEL_ERRORPRONE_CLASSPATH}, () => {
    assert.ok(process.env.JAVA_HOME, 'Set JAVA_HOME to JDK 25');
    assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP outside the repository');
    const base = fs.realpathSync(process.env.ORIEL_TEST_TMP);
    const relative = path.relative(root, base);
    assert.ok(path.isAbsolute(relative) || relative.startsWith('..' + path.sep), 'Temporary directory must be external');
    const dir = fs.mkdtempSync(path.join(base, 'errorprone-'));
    const javac = path.join(process.env.JAVA_HOME, 'bin', os.platform() === 'win32' ? 'javac.exe' : 'javac');
    const version = spawnSync(javac, ['-version'], {encoding: 'utf8', timeout: 10000});
    assert.ifError(version.error);
    assert.match(version.stdout + version.stderr, /javac 25(?:[.\s]|$)/);
    const jvm = [
      ...policy.exports.map(p => '-J--add-exports=jdk.compiler/com.sun.tools.javac.' + p + '=ALL-UNNAMED'),
      ...policy.opens.map(p => '-J--add-opens=jdk.compiler/com.sun.tools.javac.' + p + '=ALL-UNNAMED')
    ];
    const plugin = ['ErrorProne', '-XepDisableAllChecks',
      ...policy.errors.map(rule => '-Xep:' + rule + ':ERROR'),
      ...Object.keys(policy.disabled).map(rule => '-Xep:' + rule + ':OFF'),
      '-XepExcludedPaths:' + policy.excludedPaths].join(' ');
    const run = (subdir, body) => {
      const location = path.join(dir, subdir);
      fs.mkdirSync(location, {recursive: true});
      const source = path.join(location, 'Probe.java');
      fs.writeFileSync(source, 'class Probe { ' + body + ' }');
      const result = spawnSync(javac, [...jvm, '-encoding', 'UTF-8', '-d', location,
        '-processorpath', process.env.ORIEL_ERRORPRONE_CLASSPATH, '-XDcompilePolicy=simple',
        '--should-stop=ifError=FLOW', '-Xplugin:' + plugin, source],
      {encoding: 'utf8', timeout: 60000});
      assert.ifError(result.error);
      return {status: result.status, output: result.stdout + result.stderr};
    };
    try {
      const bad = run('main', 'boolean same(int[] a,int[] b){return a.equals(b);} '
        + 'void assign(int n){n=n;} void regex(){java.util.regex.Pattern.compile("[");}');
      assert.notEqual(bad.status, 0, bad.output);
      for (const rule of ['ArrayEquals', 'SelfAssignment', 'InvalidPatternSyntax'])
        assert.ok(bad.output.includes('[' + rule + ']'), bad.output);
      const good = run('correct', 'boolean same(int[] a,int[] b){return java.util.Arrays.equals(a,b);}');
      assert.equal(good.status, 0, good.output);
      const generated = run('generated/sources/annotationProcessor/java/main',
        'boolean same(int[] a,int[] b){return a.equals(b);}');
      assert.equal(generated.status, 0, generated.output);
      const stonecutter = run('generated/stonecutter/main/java',
        'boolean same(int[] a,int[] b){return a.equals(b);}');
      assert.notEqual(stonecutter.status, 0, stonecutter.output);
      assert.ok(stonecutter.output.includes('[ArrayEquals]'), stonecutter.output);
    } finally {
      fs.rmSync(dir, {recursive: true, force: true});
    }
  });
