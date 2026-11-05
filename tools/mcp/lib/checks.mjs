import fs from 'node:fs';
import path from 'node:path';
import { STATE, ROOT, KIT, safePath, requireThat, saveRecord, writeJson, readJson, preview, id, now, walk } from './core.mjs';
import { freeze, verifyRevision, validateActions } from './projects.mjs';

function issue(code, message, file, source, offset = -1, suggestion = '') {
  const before = offset < 0 ? null : source.slice(0, offset);
  return { ruleId: code, severity: 'error', category: 'structure', file, line: before === null ? null : before.split('\n').length,
    column: before === null ? null : before.length - before.lastIndexOf('\n'), message, suggestion, environment: 'shared' };
}
export function inspectPackage(revision) {
  const problems = [];
  const own = revision.files.filter(f => f.file.startsWith('assets/' + revision.project.namespace + '/') && /\.(html|css|js)$/.test(f.file));
  for (const { file } of own) {
    const source = fs.readFileSync(safePath(revision.pack, file), 'utf8');
    const clean = source.replace(/<!--[^]*?-->|\/\*[^]*?\*\//g, match => match.replace(/[^\n]/g, ' '));
    if (file.endsWith('.html')) {
      const ids = new Set();
      for (const match of clean.matchAll(/\bid\s*=\s*["']([^"']+)["']/g)) {
        if (ids.has(match[1])) problems.push(issue('duplicate-id', '重复 id：' + match[1], file, source, match.index, '为交互目标使用唯一 id。'));
        ids.add(match[1]);
      }
      for (const match of clean.matchAll(/<(svg|iframe|video|audio|object|embed)\b/gi)) problems.push(issue('browser-element', '游戏不提供浏览器元素 ' + match[1], file, source, match.index, '使用 PNG、原生模型或宿主声音 API。'));
      if (/\bv-(if|for|model|text|html)\s*=/.test(clean) || /\{\{[^]*?\}\}/.test(clean)) problems.push(issue('browser-template-adapter', '该页面包含 Oriel 模板，首版浏览器桥接不执行这些模板。', file, source, -1, '改用共享普通 DOM，或先实现并验证专门的浏览器适配。'));
      if (/<script\b[^>]*type\s*=\s*["']module["']/i.test(clean)) problems.push(issue('es-modules', '游戏脚本不提供 ES modules。', file, source));
    }
    if (file.endsWith('.js') || file.endsWith('.html')) {
      const code = file.endsWith('.html') ? [...clean.matchAll(/<script\b[^>]*>([^]*?)<\/script>/gi)].map(m => m[1]).join('\n') : clean;
      for (const match of code.matchAll(/\b(fetch|XMLHttpRequest|WebSocket|Worker|WebGLRenderingContext|eval)\s*\(|\bimport\s+(?:\(|["'{*])|\bnew\s+Function\s*\(/g)) problems.push(issue('browser-api', '检测到不允许的动态/浏览器能力：' + match[0], file, source, -1, '通过已声明的 oriel.send/on 与宿主交互。'));
    }
    const references = [];
    if (file.endsWith('.html')) for (const m of clean.matchAll(/\b(?:src|href)\s*=\s*["']([^"']+)["']/gi)) references.push([m[1], m.index]);
    if (/\.(html|css)$/.test(file)) {
      for (const m of clean.matchAll(/url\(\s*["']?([^\s)'"\r\n]+)["']?\s*\)/gi)) references.push([m[1], m.index]);
      for (const m of clean.matchAll(/@import\s+["']([^"']+)["']/gi)) references.push([m[1], m.index]);
    }
    for (const [url, offset] of references) {
      if (!url || url.startsWith('#')) continue;
      if (/^(https?:|data:|file:|javascript:|\/\/)/i.test(url)) { problems.push(issue('external-resource', '未声明的外部/内嵌资源：' + url, file, source, offset, '将资源放进 assets 并使用资源 ID。')); continue; }
      if (url.startsWith('minecraft:')) continue; // Resolved and checked by the actual engine asset host.
      let relative;
      if (/^[a-z0-9_.-]+:/.test(url)) { const [namespace, ...rest] = url.split(':'); relative = 'assets/' + namespace + '/' + rest.join(':'); }
      else relative = path.posix.normalize(path.posix.join(path.posix.dirname(file), url));
      try {
        requireThat(relative.startsWith('assets/'), 'MOCK_IN_GAME', '游戏页面不能引用浏览器 mock。');
        requireThat(fs.existsSync(safePath(revision.pack, relative)), 'MISSING_RESOURCE', '资源不存在：' + url);
      } catch (e) { problems.push(issue(e.code || 'resource', e.message, file, source, offset)); }
    }
  }
  if (revision.project.behavior === 'java') problems.push(issue('java-controller-adapter', '通用网页工作流尚不执行外部 Java 控制器。', revision.entry, '', -1, '使用共享 JS，或先提供可验证的外部预览启动器与浏览器适配。'));
  return problems;
}
export async function snapshotRevision(revision, { scene, actions, width = 640, height = 360, frames = 60, boxes, strict = true } = {}) {
  verifyRevision(revision);
  requireThat([width, height, frames].every(n => Number.isInteger(n) && n > 0) && width <= 1920 && height <= 1080 && frames <= 600, 'INVALID_SIZE', '尺寸或帧数超出限制。');
  const selected = scene ? revision.project.scenes.find(s => s.name === scene) : null;
  requireThat(!scene || selected, 'UNKNOWN_SCENE', '场景不存在。');
  actions = actions || selected?.actions || []; validateActions(actions);
  const directory = path.join(revision.directory, id('shot')); fs.mkdirSync(directory);
  const shot = path.join(directory, 'page.png');
  const args = [path.join(revision.pack, revision.entry), '--scale', '2', '--size', width + 'x' + height, '--snapshot', shot, '--data', path.join(revision.directory, 'fixture.json')];
  if (strict) args.push('--strict');
  if (actions.length) { const file = path.join(directory, 'actions.txt'); fs.writeFileSync(file, actions.join('\n') + '\n'); args.push('--actions', file, '--out', directory); }
  else args.push('--frames', String(frames));
  if (boxes) args.push('--boxes', String(boxes));
  const result = await preview(args, { ORIEL_MC_JAR: revision.assetJar });
  fs.writeFileSync(path.join(directory, 'preview.log'), result.output);
  return { id: path.basename(directory), scene: scene || 'custom', width, height, actions, passed: result.code === 0 && fs.existsSync(shot), exitCode: result.code, timedOut: result.timedOut, image: fs.existsSync(shot) ? shot : null, output: result.output, at: now() };
}
export async function checkProject({ projectId }) {
  const revision = await freeze(projectId);
  const diagnostics = inspectPackage(revision);
  const reportPath = path.join(revision.directory, 'engine-check.json');
  // Whole owned namespace, not the unrelated built-in sample pages.
  const result = await preview(['--check', path.join(revision.pack, 'assets', revision.project.namespace), '--strict', '--lang', 'zh_cn', '--report', reportPath], { ORIEL_MC_JAR: revision.assetJar });
  fs.writeFileSync(path.join(revision.directory, 'engine-check.log'), result.output);
  const reports = fs.existsSync(reportPath) ? readJson(reportPath) : [];
  for (const report of reports) {
    const location = { file: report.file, viewport: [report.width, report.height], environment: 'oriel', line: null, column: null };
    if (report.minecraftVersion !== revision.project.profile.minecraft) diagnostics.push({ ...location, ruleId: 'minecraft-assets-version', severity: 'error', message: '预览资源版本 ' + report.minecraftVersion + ' 与目标 ' + revision.project.profile.minecraft + ' 不一致。' });
    if (report.error) diagnostics.push({ ...location, ruleId: 'page-error', severity: 'error', message: report.error });
    for (const message of report.runtimeErrors || []) diagnostics.push({ ...location, ruleId: 'runtime-error', severity: 'error', message });
    for (const url of report.missingImages || []) diagnostics.push({ ...location, ruleId: 'missing-image', severity: 'error', message: url });
    for (const d of report.diagnostics || []) diagnostics.push({ ...location, ruleId: d.code, severity: d.severity.toLowerCase(), file: d.source, line: d.line || null, column: d.column || null, message: d.message, suggestion: d.suggestion });
    for (const message of report.design || []) diagnostics.push({ ...location, ruleId: 'design', severity: 'warning', message });
    if (report.droppedDiagnostics) diagnostics.push({ ...location, ruleId: 'diagnostics-truncated', severity: 'error', message: '诊断被截断，不能批准不完整的检查。' });
  }
  if (result.code !== 0 && !diagnostics.length || !reports.length) diagnostics.push({ ruleId: 'engine-check-failed', severity: 'error', file: null, line: null, column: null, message: result.output.slice(-4000), environment: 'oriel' });
  const shots = [];
  if (result.code === 0 && !diagnostics.some(d => d.severity === 'error' || d.severity === 'warning')) {
    for (const scene of revision.project.scenes) for (const [width, height] of [[640, 360], [427, 240]]) {
      const shot = await snapshotRevision(revision, { scene: scene.name, width, height }); shots.push(shot);
      if (!shot.passed) diagnostics.push({ ruleId: 'scene-failed', severity: 'error', scene: scene.name, viewport: [width, height], file: revision.entry, line: null, column: null, message: shot.output.slice(-3000), environment: 'oriel' });
    }
  }
  revision.check = { passed: reports.length > 0 && result.code === 0 && !diagnostics.some(d => ['error', 'warning'].includes(d.severity)), strict: true, at: now(), hash: revision.hash, diagnostics, shots,
    coverage: ['静态资源引用与已知禁用能力', '真实引擎解析与诊断', '640×360 / 427×240', '声明场景动作执行'],
    notCovered: ['任意动态 JS 的完整静态证明', '用户审美判断', '原生 MC 渲染、IME、容器和网络', '未声明状态与业务结果断言'] };
  saveRecord('revisions', revision);
  return { projectId, revisionId: revision.id, ...revision.check, nextActions: revision.check.passed ? ['oriel_web_preview', 'oriel_review.prepare'] : ['修复 diagnostics 后重新 oriel_check'] };
}
