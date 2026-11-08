import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { auditMaven } from './check-maven.mjs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP outside the repository');
const root = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'maven-'));
const publish = (artifact, deps = []) => {
  const dir = path.join(root, 'dev/oriel', artifact, '0.12.0');
  fs.mkdirSync(dir, { recursive: true });
  const base = path.join(dir, artifact + '-0.12.0');
  fs.writeFileSync(base + '.jar', 'fixture');
  fs.writeFileSync(base + '.pom', `<project><groupId>dev.oriel</groupId><artifactId>${artifact}</artifactId><version>0.12.0</version><dependencies>` + deps.map(module => `<dependency><groupId>dev.oriel</groupId><artifactId>${module}</artifactId><version>0.12.0</version><scope>runtime</scope></dependency>`).join('') + '</dependencies></project>');
  fs.writeFileSync(base + '.module', JSON.stringify({ component: { group: 'dev.oriel', module: artifact, version: '0.12.0' }, variants: [{ name: 'runtimeElements', dependencies: deps.map(module => ({ group: 'dev.oriel', module, version: { requires: '0.12.0' } })) }] }));
};
test('Maven audit catches the missing kit publication in both metadata formats and checks transitive modules', () => {
  publish('oriel-common-26.3', ['kit']);
  let result = auditMaven(root);
  assert.equal(result.failures.length, 4);
  assert.ok(result.failures.some(s => s.includes('.pom: missing dev.oriel:kit:0.12.0')));
  assert.ok(result.failures.some(s => s.includes('.module: missing dev.oriel:kit:0.12.0')));
  publish('oriel-common-26.3', ['oriel-kit']);
  publish('oriel-kit', ['oriel-engine']);
  assert.equal(auditMaven(root).failures.length, 4);
  publish('oriel-engine');
  fs.unlinkSync(path.join(root, 'dev/oriel/oriel-engine/0.12.0/oriel-engine-0.12.0.module'));
  assert.deepEqual(auditMaven(root).failures, [], 'POM-only publications resolve correctly');
  fs.unlinkSync(path.join(root, 'dev/oriel/oriel-kit/0.12.0/oriel-kit-0.12.0.jar'));
  assert.equal(auditMaven(root).failures.length, 2);
});
test('Maven audit rejects full publications even without internal dependencies', () => {
  const dir = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'full-maven-'));
  fs.writeFileSync(path.join(dir, 'full.pom'), '<project><groupId>dev.oriel</groupId><artifactId>oriel-kit</artifactId><version>0.12.1</version></project>');
  fs.writeFileSync(path.join(dir, 'full.module'), JSON.stringify({component:{group:'dev.oriel',module:'oriel-kit',version:'0.12.1'},variants:[]}));
  const result = auditMaven(dir);
  assert.equal(result.failures.length, 2);
  assert.ok(result.failures.every(f => f.includes('Maven accepts only slim X.Y.0')));
});

test('Maven publication rejects full builds before every repository route', { skip: process.env.ORIEL_MAVEN_PUBLICATION !== '1', timeout: 1200000 }, () => {
  assert.ok(process.env.ORIEL_GWS);
  const checkout = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const rejected = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'maven-rejected-'));
  for (const task of [':kit:publishAllPublicationsToOrielMavenRepository', 'publish', 'publishToMavenLocal', 'templateCompile']) {
    const destination = path.join(rejected, task.replaceAll(':', '_'));
    const result = spawnSync('bash', [process.env.ORIEL_GWS, checkout, task, '-Poriel_maven_dir=' + destination, '-Dmaven.repo.local=' + path.join(destination, 'local')], {
      cwd: checkout, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, env: { ...process.env, GW_TRIES: '1' }
    });
    console.log(task, result.stdout, result.stderr);
    assert.notEqual(result.status, 0, task + ' must reject full publication');
    assert.match(result.stdout + result.stderr, /Maven publishing requires -Poriel_slim/);
    assert.ok(!fs.existsSync(destination), 'reject before writing Maven artifacts');
  }
});

test('Maven audit rejects an empty repository', () => {
  assert.deepEqual(auditMaven(fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'empty-maven-'))).failures, ['No POM/module metadata found']);
});
