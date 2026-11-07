#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { read, write, check, hash, tree, freeze, verify, compare, markdown, execute, environment } from './core.mjs';
import { generate } from './synthetic.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
function external(target) {
  const full = path.resolve(target);
  // Also resolve an existing ancestor, so a junction cannot redirect output into source.
  let ancestor = full;
  while (!fs.existsSync(ancestor)) ancestor = path.dirname(ancestor);
  const resolved = path.resolve(fs.realpathSync(ancestor), path.relative(ancestor, full));
  const rel = path.relative(root, resolved);
  check(rel === '..' || rel.startsWith('..' + path.sep) || path.isAbsolute(rel), 'Outputs must be outside this worktree');
  for (let at = fs.realpathSync(ancestor); ; at = path.dirname(at)) {
    check(!fs.existsSync(path.join(at, '.git')), 'Outputs must not be inside any repository');
    if (at === path.dirname(at)) break;
  }
  return full;
}
function parameters(args) {
  const allowed = new Set(['--config', '--manifest', '--out', '--freeze', '--synthetic']);
  const result = {};
  for (let i = 0; i < args.length; i++) {
    const key = args[i]; check(allowed.has(key) && !Object.hasOwn(result, key), 'Unknown/duplicate option: ' + key);
    if (key === '--freeze') result[key] = true;
    else { check(args[i + 1] && !args[i + 1].startsWith('--'), 'Missing value: ' + key); result[key] = args[++i]; }
  }
  return result;
}
function adapter(variant, config, directory) {
  const output = path.join(directory, 'frames.json'), baseline = path.join(directory, 'temporary-baseline.json');
  const substitutions = { out: output, baseline, pages: config.pages, root: variant.root };
  const command = variant.command ? variant.command.map(arg => arg.replace(/\{(out|baseline|pages|root)\}/g, (_, k) => substitutions[k])) : [
    config.java || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin/java') : 'java'),
    '-Xms1g', '-Xmx1g', '-Djava.awt.headless=true', '-Doriel.benchUpdate=true',
    '-Doriel.benchWarmup=' + (config.warmup ?? 60), '-Doriel.benchFrames=' + (config.frames ?? 120), '-Doriel.benchRounds=3',
    ...(variant.jvmArgs ?? []), '-cp', path.join(variant.root, 'preview/build/install/preview/lib/*'),
    'dev.oriel.preview.FrameBenchmark', config.pages, baseline, output
  ];
  return { command, output };
}
async function measure(config, frozen, directory) {
  const rounds = config.rounds ?? 3;
  check(Number.isInteger(rounds) && rounds >= 3 && rounds % 2 === 1 && rounds <= 9, 'rounds must be odd, 3..9');
  check(Number.isFinite(config.timeoutMs ?? 120000) && (config.timeoutMs ?? 120000) > 0, 'timeoutMs must be positive');
  for (const name of ['old', 'new']) {
    const variant = config[name];
    check(variant?.root && path.isAbsolute(variant.root), 'Missing/relative variant root: ' + name);
    if (!variant.command) {
      const jar = config.env?.ORIEL_MC_JAR;
      check(jar && path.isAbsolute(jar) && config.resources.some(resource => path.resolve(resource) === path.resolve(jar)), 'Built-in adapter requires explicit ORIEL_MC_JAR in env and resources');
      check(!variant.env?.ORIEL_MC_JAR || path.resolve(variant.env.ORIEL_MC_JAR) === path.resolve(jar), 'Variant Minecraft assets differ');
      for (const arg of variant.jvmArgs ?? []) check(!arg.startsWith('-Doriel.mcJar='), 'Pin Minecraft resources with shared env, not variant JVM arguments');
    }
  }
  const binaries = Object.fromEntries(['old', 'new'].map(name => {
    const variant = config[name];
    const roots = variant.artifacts ?? [path.join(variant.root, 'preview/build/install/preview/lib')];
    check(roots.length, 'Artifact identity required');
    return [name, roots.map(tree)];
  }));
  const fingerprint = () => Object.fromEntries(['old', 'new'].map(name => [name, (config[name].artifacts ?? [path.join(config[name].root, 'preview/build/install/preview/lib')]).map(tree)]));
  const plan = [];
  for (const labels of [['aa0', 'aa1'], ['old', 'new']]) for (let round = 0; round < rounds; round++) {
    for (const label of round % 2 ? [...labels].reverse() : labels) plan.push({ label, round });
  }
  const samples = [], started = Date.now();
  write(path.join(directory, 'run-identity.json'), { schemaVersion: 1, config, frozen, binaries, environment: environment(), plan });
  try {
    for (let index = 0; index < plan.length; index++) {
      check(Date.now() - started < 20 * 60_000, 'Measurement window exceeded 20 minutes; split the page set explicitly');
      verify(config, frozen);
      check(JSON.stringify(fingerprint()) === JSON.stringify(binaries), 'Engine artifacts changed during measurement');
      const item = plan[index], variant = config[item.label === 'new' ? 'new' : 'old'];
      const part = path.join(directory, String(index).padStart(2, '0') + '-' + item.label);
      fs.mkdirSync(part);
      const { command, output } = adapter(variant, config, part);
      console.log(`${index + 1}/${plan.length} ${item.label} round ${item.round + 1}`);
      await execute(command, { cwd: variant.root, env: { ...config.env, ...variant.env }, log: path.join(part, 'command.log'), timeout: Math.min(config.timeoutMs ?? 120000, 20 * 60_000 - (Date.now() - started)) });
      const report = read(output);
      const sample = { ...item, command, report, reportHash: hash(fs.readFileSync(output)) };
      samples.push(sample);
      write(path.join(part, 'sample.json'), sample);
    }
    verify(config, frozen);
    check(JSON.stringify(fingerprint()) === JSON.stringify(binaries), 'Engine artifacts changed during measurement');
    const report = { ...compare(samples, frozen.identity.pages, config.maxNoise ?? .05), manifestHash: frozen.identityHash, binaries, samples };
    write(path.join(directory, 'comparison.json'), report);
    fs.writeFileSync(path.join(directory, 'comparison.md'), markdown(report), { flag: 'wx' });
    console.log(report.verdict + ': ' + path.join(directory, 'comparison.md'));
    process.exitCode = report.verdict === 'PASS' ? 0 : report.verdict === 'FAIL' ? 1 : 2;
  } catch (error) {
    write(path.join(directory, 'failure.json'), { result: 'ERROR', message: error.message, completedSamples: samples.length });
    throw error;
  }
}
async function main() {
  const options = parameters(process.argv.slice(2));
  if (options['--synthetic']) return generate(external(options['--synthetic']));
  check(options['--config'] && options['--manifest'], 'Usage: node tools/bench/run.mjs --config <absolute.json> --manifest <absolute.json> [--freeze | --out <new external dir>]');
  const configFile = path.resolve(options['--config']), config = read(configFile);
  check(path.isAbsolute(config.pages) && config.resources?.every(path.isAbsolute), 'Use absolute paths in config');
  if (options['--freeze']) {
    const historical = config.historicalBaseline ? read(config.historicalBaseline) : null;
    write(external(options['--manifest']), freeze(config, historical));
    console.log('Frozen resource manifest; historical timing baseline unchanged');
    return;
  }
  check(options['--out'], '--out is required');
  const frozen = read(options['--manifest']); verify(config, frozen);
  const directory = external(options['--out']); check(!fs.existsSync(directory), 'Output directory already exists');
  // Re-enter once under the shared wrapper; a per-invocation marker prevents recursive locking.
  if (process.argv[2] !== undefined && process.env.ORIEL_BENCH_CHILD_TOKEN !== hash(configFile + directory)) {
    const workspace = config.workspace || process.env.ORIEL_WORKSPACE;
    check(workspace && path.isAbsolute(workspace), 'workspace absolute path required for bench-window.sh');
    const lockScript = path.join(workspace, 'tools/bench-window.sh');
    check(fs.existsSync(lockScript), 'Missing shared measurement lock script');
    const resultLog = directory + '.window.log';
    fs.mkdirSync(path.dirname(directory), { recursive: true });
    process.exitCode = await execute(['bash', lockScript, process.execPath, fileURLToPath(import.meta.url), ...process.argv.slice(2)], {
      cwd: root, log: resultLog, timeout: 80 * 60_000, acceptedCodes: [0, 1, 2], env: { ORIEL_BENCH_CHILD_TOKEN: hash(configFile + directory) }
    });
    console.log('Measurement receipt: ' + directory + '; window log: ' + resultLog);
    return;
  }
  fs.mkdirSync(directory);
  await measure(config, frozen, directory);
}
main().catch(error => { console.error(error.message); process.exitCode = 2; });
