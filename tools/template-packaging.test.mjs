import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { readJar, properties } from './check-release.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
// Opt-in: publishes this checkout, builds both real templates, then switches modes in the SAME output directory.
test('template jars switch external/embedded/external and keep jijAcceptance isolated', { skip: process.env.ORIEL_TEMPLATE_PACKAGING !== '1', timeout: 1800000 }, () => {
  assert.ok(process.env.ORIEL_GWS && process.env.ORIEL_TEST_TMP);
  const gradle = (dir, args, rejection) => {
    const result = spawnSync('bash', [process.env.ORIEL_GWS, dir, ...args], { cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, env: { ...process.env, GW_TRIES: '1' } });
    console.log(result.stdout, result.stderr);
    if (rejection) {
      assert.notEqual(result.status, 0);
      assert.match(result.stdout + result.stderr, rejection);
    } else assert.equal(result.status, 0);
  };
  gradle(root, ['templateCompile', '-Poriel_slim']);
  const version = properties(fs.readFileSync(path.join(root, 'gradle.properties'), 'utf8')).version;
  for (const loader of ['fabric', 'neoforge']) {
    const dir = path.join(root, 'template', loader);
    const props = properties(fs.readFileSync(path.join(dir, 'gradle.properties'), 'utf8'));
    const original = path.join(root, 'build/template-maven/dev/oriel', `oriel-${loader}-${props.minecraft_version}`, version, `oriel-${loader}-${props.minecraft_version}-${version}.jar`);
    // A higher version left in a downstream mirror must not silently replace the preferred slim jar.
    const repository = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'template-maven-'));
    const artifact = `oriel-${loader}-${props.minecraft_version}`;
    const full = version.replace(/0$/, '1');
    for (const candidate of [version, full]) {
      const destination = path.join(repository, 'dev/oriel', artifact, candidate);
      fs.mkdirSync(destination, { recursive: true });
      for (const extension of ['pom', 'module', 'jar']) {
        const source = original.replace(/\.jar$/, '.' + extension);
        const bytes = fs.readFileSync(source);
        fs.writeFileSync(path.join(destination, `${artifact}-${candidate}.${extension}`), extension === 'jar' ? bytes : bytes.toString().replaceAll(version, candidate));
      }
    }
    fs.writeFileSync(path.join(repository, 'dev/oriel', artifact, 'maven-metadata.xml'), `<metadata><groupId>dev.oriel</groupId><artifactId>${artifact}</artifactId><versioning><latest>${full}</latest><release>${full}</release><versions><version>${version}</version><version>${full}</version></versions></versioning></metadata>`);
    for (const mode of ['default', 'true', 'false', 'acceptance']) {
      const args = ['jar', '-Poriel_version=' + version, '-PorielRepository=' + repository];
      if (mode !== 'default') args.push('-PorielEmbedded=' + (mode === 'acceptance' ? 'true' : mode));
      if (mode === 'acceptance') args.push('-PjijAcceptance', '-PjijRunDir=' + path.join(process.env.ORIEL_TEST_TMP, 'jij-template-unused'));
      gradle(dir, args);
      const jar = readJar(path.join(dir, 'build/libs', `${props.mod_id}-${props.mod_version}.jar`));
      let nested;
      if (loader === 'fabric') {
        const metadata = JSON.parse(jar.entries.get('fabric.mod.json'));
        assert.equal(metadata.depends.oriel, '>=0.12.0 <0.13.0');
        nested = (metadata.jars ?? []).map(j => j.file);
      } else {
        assert.match(jar.entries.get('META-INF/neoforge.mods.toml').toString(), /versionRange\s*=\s*"\[0\.12\.0,0\.13\.0\)"/);
        const metadata = jar.entries.get('META-INF/jarjar/metadata.json');
        const deps = metadata ? JSON.parse(metadata).jars : [];
        for (const dep of deps) {
          assert.equal(dep.version.range, '[0.12.0,0.13.0)');
          assert.equal(dep.version.artifactVersion, version);
        }
        nested = deps.map(j => j.path);
      }
      assert.equal(nested.length, mode === 'true' ? 1 : 0, `${loader} ${mode}`);
      assert.equal([...jar.entries.keys()].filter(n => n.endsWith('.jar')).length, nested.length);
      if (nested.length) {
        assert.ok(nested[0].includes(version + '.jar'));
        assert.deepEqual(jar.entries.get(nested[0]), fs.readFileSync(original), 'embed the original self-contained loader jar');
      }
      assert.ok(![...jar.entries.keys()].some(n => n.startsWith('dev/oriel/')), 'no flattened Oriel classes');
      console.log(`PASS ${loader} ${mode}`);
    }
    const force = path.join(repository, 'force-full.gradle');
    fs.writeFileSync(force, `allprojects { configurations.matching { it.name in ['include', 'includeInternal', 'jarJar'] }.configureEach { resolutionStrategy.force('dev.oriel:${artifact}:${full}') } }`);
    gradle(dir, ['jar', '-PorielEmbedded=true', '-PorielRepository=' + repository, '-I', force], /Refusing to embed non-slim Oriel/);
    console.log(`PASS ${loader} rejects a forced higher full version`);
  }
});
