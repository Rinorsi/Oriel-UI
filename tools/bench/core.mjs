import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { confidence95 } from './noise.mjs';

export const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
export const read = file => JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
export function write(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' });
}
export function check(value, message) { if (!value) throw new Error(message); }
export function files(root, prefix = '') {
  const stat = fs.lstatSync(root);
  check(!stat.isSymbolicLink(), 'Linked resource: ' + root);
  if (stat.isFile()) return [[prefix, hash(fs.readFileSync(root))]];
  return fs.readdirSync(root).sort().flatMap(name => files(path.join(root, name), prefix ? prefix + '/' + name : name));
}
// Directory entries use stable resource-relative keys, independent of worktree location.
export function tree(root) {
  if (fs.lstatSync(root).isFile()) return { sha256: hash(fs.readFileSync(root)) };
  return Object.fromEntries(files(root));
}
export function pageNames(root, prefix = '') {
  check(!fs.lstatSync(root).isSymbolicLink(), 'Linked page root');
  return fs.readdirSync(root, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name)).flatMap(e => {
    check(!e.isSymbolicLink(), 'Linked page: ' + e.name);
    return e.isDirectory() ? pageNames(path.join(root, e.name), prefix + e.name + '/') : e.name.endsWith('.html') ? [prefix + e.name] : [];
  });
}
export function sourceIdentity(config) {
  check(config.pages && config.resources?.length, 'pages and resources (including the Minecraft jar) are required');
  const pages = pageNames(config.pages);
  check(pages.length, 'No pages');
  return { pages, pageTree: tree(config.pages), resources: config.resources.map(tree), scenario: config.scenario ?? null };
}
export function freeze(config, historical = null) {
  const identity = sourceIdentity(config);
  const previous = historical ? Object.keys(historical.pages) : [];
  return { schemaVersion: 1, createdAt: new Date().toISOString(), identity, identityHash: hash(JSON.stringify(identity)),
    historicalPageDelta: { added: identity.pages.filter(p => !previous.includes(p)), removed: previous.filter(p => !identity.pages.includes(p)) },
    note: 'Resource manifest only; not a timing baseline or golden-image approval.' };
}
export function verify(config, frozen) {
  check(frozen.schemaVersion === 1 && hash(JSON.stringify(frozen.identity)) === frozen.identityHash && hash(JSON.stringify(sourceIdentity(config))) === frozen.identityHash, 'Frozen page/resource/scenario identity changed; create a separately reviewed manifest');
}
export const median = values => { const a = [...values].sort((a, b) => a - b); return a[Math.floor(a.length / 2)]; };
const metadata = ['warmup', 'frames', 'rounds', 'aggregate', 'viewport', 'backend', 'clearBeforeFrame', 'java', 'os', 'processors'];
export function validateReport(report, expectedPages) {
  check(report && Object.keys(report.pages ?? {}).sort().join('\n') === [...expectedPages].sort().join('\n'), 'Adapter page set differs');
  for (const key of metadata) check(report[key] !== undefined, 'Missing benchmark metadata: ' + key);
  for (const [page, cost] of Object.entries(report.pages)) {
    for (const q of ['p50_ms', 'p95_ms']) check(Number.isFinite(cost[q]) && cost[q] > 0, 'Invalid metric: ' + page + '/' + q);
    check(cost.p95_ms >= cost.p50_ms, 'p95 below p50: ' + page);
  }
}
export function compare(samples, expectedPages, maxNoise = .05) {
  check(Number.isFinite(maxNoise) && maxNoise >= 0 && maxNoise <= .25, 'maxNoise must be in [0, 0.25]');
  const groups = Object.fromEntries(['aa0', 'aa1', 'old', 'new'].map(k => [k, samples.filter(s => s.label === k)]));
  const count = groups.old.length;
  check(count >= 3 && count <= 9 && count % 2 === 1 && Object.values(groups).every(g => g.length === count), 'Require equal odd 3..9 A/A and A/B rounds');
  for (const group of Object.values(groups)) {
    group.sort((a, b) => a.round - b.round);
    check(group.every((sample, index) => sample.round === index), 'Missing/duplicate paired round identity');
  }
  let reference;
  for (const sample of samples) {
    validateReport(sample.report, expectedPages);
    const current = JSON.stringify(metadata.map(k => sample.report[k]));
    reference ??= current;
    check(current === reference, 'Benchmark configuration/environment differs');
  }
  const rows = [];
  for (const page of expectedPages) for (const metric of ['p50_ms', 'p95_ms']) {
    const values = key => groups[key].map(s => s.report.pages[page][metric]);
    const a0 = values('aa0'), a1 = values('aa1');
    const aaDifferences = a0.map((a, i) => (a1[i] - a) / a);
    const aaCI = confidence95(aaDifferences);
    const noise = Math.max(Math.abs(aaCI.low), Math.abs(aaCI.high));
    const before = median(values('old')), after = median(values('new'));
    const previous = values('old'), next = values('new');
    const pairedDifferences = previous.map((old, i) => (next[i] - old) / old);
    const ci95 = confidence95(pairedDifferences);
    rows.push({ page, metric, before, after, delta: ci95.mean, pairedDifferences, aaDifferences, ci95, aaCI, noise,
      verdict: noise > maxNoise ? 'INCONCLUSIVE' : ci95.high <= noise + 1e-12 ? 'PASS' : ci95.low > noise + 1e-12 ? 'FAIL' : 'INCONCLUSIVE' });
  }
  return { schemaVersion: 1, verdict: rows.some(r => r.verdict === 'FAIL') ? 'FAIL' : rows.some(r => r.verdict === 'INCONCLUSIVE') ? 'INCONCLUSIVE' : 'PASS',
    policy: { aggregation: 'paired per-round relative differences with two-sided Student-t 95% confidence interval; medians retained for display', noise: 'maximum absolute endpoint of paired A/A mean 95% confidence interval, per page/quantile', decision: 'PASS only if A/B CI upper <= noise; FAIL if lower > noise; otherwise INCONCLUSIVE', maxNoise }, rows,
    notCovered: ['style/layout phase costs (FrameBenchmark does not export them)', 'game GPU/FPS', 'IME', 'visual equivalence'] };
}
export function markdown(report) {
  const pct = v => (v * 100).toFixed(2) + '%';
  return '# CPU benchmark: ' + report.verdict + '\n\n| Page | Metric | Old median ms | New median ms | Paired mean | 95% CI | A/A noise | Result |\n|---|---|---:|---:|---:|---|---:|---|\n' +
    report.rows.map(r => `| ${r.page.replaceAll('|', '\\|')} | ${r.metric} | ${r.before.toFixed(4)} | ${r.after.toFixed(4)} | ${pct(r.delta)} | ${pct(r.ci95.low)} … ${pct(r.ci95.high)} | ${pct(r.noise)} | ${r.verdict} |`).join('\n') +
    '\n\n测量期间检测到外部 java/Minecraft 客户端进程：' + (report.interference?.detected ?? '探测不可用') + '；探测失败次数：' + (report.interference?.unavailableProbes ?? '未知') + '。Minecraft标记仅为进程提示，非实例确认。\n' +
    '\n\nNot covered: ' + report.notCovered.join('; ') + '.\n';
}
export function execute(argv, { cwd, env = {}, log, timeout = 120000, acceptedCodes = [0] } = {}) {
  check(Array.isArray(argv) && argv.length && argv.every(v => typeof v === 'string'), 'command must be an argv array');
  return new Promise((resolve, reject) => {
    const out = fs.openSync(log, 'wx');
    const child = spawn(argv[0], argv.slice(1), { cwd, env: { ...process.env, ...env }, shell: false, windowsHide: true, stdio: ['ignore', out, out] });
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; child.kill(); }, timeout);
    let closed = false;
    const closeLog = () => { if (!closed) { closed = true; fs.closeSync(out); } };
    child.once('error', e => { clearTimeout(timer); closeLog(); reject(e); });
    child.once('close', code => { clearTimeout(timer); closeLog(); acceptedCodes.includes(code) && !timedOut ? resolve(code) : reject(new Error(`Command failed (${timedOut ? 'timeout' : code}); log: ${log}`)); });
  });
}
export function environment() { return { platform: process.platform, arch: process.arch, cpus: os.cpus().map(c => c.model), node: process.version }; }
