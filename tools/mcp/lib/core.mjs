import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const ROOT = path.resolve(process.env.ORIEL_ROOT || path.join(path.dirname(fileURLToPath(import.meta.url)), '../../..'));
export const WORKSPACE = path.resolve(process.env.ORIEL_WORKSPACE || path.join(ROOT, '../Oriel-UI-Workspace'));
const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
export const STATE = path.resolve(process.env.ORIEL_MCP_STATE || path.join(WORKSPACE, 'daily', date, 'scratch/mcp-workflow'));
export const LIB = path.join(ROOT, 'preview/build/install/preview/lib');
export const KIT = path.join(ROOT, 'kit/src/main/resources');
export const JAVA = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
export const now = () => new Date().toISOString();
export const sha = value => crypto.createHash('sha256').update(value).digest('hex');
export const token = () => crypto.randomBytes(24).toString('hex');
export const id = prefix => prefix + '-' + crypto.randomUUID();
export class WorkflowError extends Error {
  constructor(code, message, details = {}) { super(message); this.code = code; this.details = details; }
}
export function requireThat(condition, code, message, details) {
  if (!condition) throw new WorkflowError(code, message, details);
}
export function inside(root, file) {
  const rel = path.relative(path.resolve(root), path.resolve(file));
  return rel === '' || (!rel.startsWith('..' + path.sep) && rel !== '..' && !path.isAbsolute(rel));
}
export function identifier(value) {
  requireThat(typeof value === 'string' && /^[a-z0-9][a-z0-9_-]{0,79}$/.test(value), 'INVALID_ID', 'ID 只允许小写字母、数字、下划线和连字符。');
  return value;
}
export function safePath(root, relative) {
  requireThat(typeof relative === 'string' && relative.length > 0 && !relative.includes('\0') && !path.isAbsolute(relative) && !relative.includes('\\') && !relative.includes(':'), 'INVALID_PATH', '必须使用资源根下的相对路径。');
  const target = path.resolve(root, relative);
  requireThat(inside(root, target), 'PATH_ESCAPE', '路径超出了声明目录。');
  // Reject symlinks/junctions, including a non-existent target with a linked parent.
  let part = path.resolve(root);
  requireThat(fs.existsSync(part) && !fs.lstatSync(part).isSymbolicLink(), 'INVALID_ROOT', '资源根必须是实际目录。');
  for (const segment of path.relative(root, target).split(path.sep).filter(Boolean)) {
    part = path.join(part, segment);
    if (fs.existsSync(part)) requireThat(!fs.lstatSync(part).isSymbolicLink(), 'SYMLINK', '资源不允许符号链接或目录联接。');
  }
  if (fs.existsSync(target)) requireThat(inside(fs.realpathSync(root), fs.realpathSync(target)), 'PATH_ESCAPE', '真实路径超出了声明目录。');
  return target;
}
export function walk(root, prefix = '') {
  if (!fs.existsSync(root)) return [];
  const out = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    requireThat(!entry.isSymbolicLink(), 'SYMLINK', '不支持链接资源：' + entry.name);
    if (['.git', 'node_modules', 'build', '.gradle'].includes(entry.name)) continue;
    const rel = prefix + entry.name;
    if (entry.isDirectory()) out.push(...walk(path.join(root, entry.name), rel + '/'));
    else if (entry.isFile()) out.push(rel);
  }
  return out;
}
export function fileHash(file) { return fs.existsSync(file) ? sha(fs.readFileSync(file)) : null; }
export function treeHash(root) { return sha(JSON.stringify(walk(root).map(rel => [rel, fileHash(path.join(root, rel))]))); }
export function readJson(file) { return JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '')); }
export function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temp = file + '.' + crypto.randomUUID() + '.tmp';
  fs.writeFileSync(temp, JSON.stringify(value, null, 2) + '\n');
  fs.renameSync(temp, file);
}
export function initState() {
  requireThat(inside(WORKSPACE, STATE) && !inside(ROOT, STATE), 'INVALID_STATE_ROOT', 'MCP 运行产物必须位于仓库外工作区。');
  fs.mkdirSync(STATE, { recursive: true });
  for (const name of ['projects', 'revisions', 'reviews', 'plans', 'jobs']) fs.mkdirSync(path.join(STATE, name), { recursive: true });
}
export function recordFile(kind, name) { return safePath(STATE, kind + '/' + identifier(name) + '.json'); }
export function loadRecord(kind, name) {
  const file = recordFile(kind, name);
  requireThat(fs.existsSync(file), 'NOT_FOUND', '记录不存在：' + name);
  return readJson(file);
}
export function saveRecord(kind, record) { writeJson(recordFile(kind, record.id), record); return record; }
export function copyTree(from, to) {
  for (const rel of walk(from)) {
    const target = path.join(to, rel);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(safePath(from, rel), target);
  }
}
export function run(command, args, { cwd = ROOT, timeout = 120000, env = {}, signal, logFile, onSpawn } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, env: { ...process.env, ...env }, windowsHide: true, shell: false, signal });
    onSpawn?.(child);
    let output = '', timedOut = false, outputTruncated = false;
    const log = logFile ? fs.createWriteStream(logFile) : null;
    const capture = chunk => { log?.write(chunk); const joined = output + chunk.toString(); outputTruncated ||= joined.length > 2_000_000; output = joined.slice(-2_000_000); };
    child.stdout.on('data', capture); child.stderr.on('data', capture);
    const timer = setTimeout(() => { timedOut = true; stopProcess(child); }, timeout);
    child.once('error', error => { clearTimeout(timer); log?.end(); reject(error); });
    child.once('close', (code, stoppedBy) => { clearTimeout(timer); log?.end(); resolve({ code, stoppedBy, timedOut, output, outputTruncated }); });
  });
}
export function stopProcess(child) {
  if (!child?.pid || child.exitCode !== null) return;
  if (process.platform === 'win32') spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' });
  else child.kill('SIGTERM');
}
export const schedulerArguments = args => args.filter(arg => arg !== '--console=plain');
export function gradle(args, options = {}) {
  // The command is fixed; user-supplied shell fragments never reach cmd.exe.
  requireThat(args.every(arg => typeof arg === 'string' && !/[\r\n"&|<>^%!]/.test(arg)), 'INVALID_BUILD_ARGUMENT', '构建参数含不允许的 shell 字符。');
  const scheduler = path.join(WORKSPACE, 'tools/gws.sh');
  if (fs.existsSync(scheduler)) return run('bash', [scheduler, ROOT, ...schedulerArguments(args)], { ...options, timeout: options.timeout ?? 4200000 });
  return process.platform === 'win32'
    ? run('cmd.exe', ['/d', '/c', path.join(ROOT, 'gradlew.bat'), ...args], options)
    : run(path.join(ROOT, 'gradlew'), args, options);
}
let building;
export async function ensurePreview() {
  const inputs = ['engine/src/main', 'script-rhino/src/main', 'mc-extension/src/main', 'kit/src/main', 'preview/src/main', 'build-logic/src/main'];
  const hash = sha(JSON.stringify(inputs.map(rel => [rel, treeHash(path.join(ROOT, rel))])));
  const stamp = path.join(STATE, 'preview-build.json');
  if (fs.existsSync(LIB) && fs.existsSync(stamp) && readJson(stamp).hash === hash && readJson(stamp).engineHash === engineIdentity()) return;
  if (!building) {
    const logFile = path.join(STATE, 'jobs', id('preview-build') + '.log');
    fs.mkdirSync(path.dirname(logFile), { recursive: true });
    building = gradle([':preview:installDist', '--console=plain'], { timeout: 4200000, logFile }).then(r => {
      requireThat(r.code === 0, 'PREVIEW_BUILD_FAILED', '预览器构建失败。', { output: r.output, logFile });
      writeJson(stamp, { hash, engineHash: engineIdentity(), at: now(), logFile });
    }).finally(() => { building = null; });
  }
  await building;
}
export async function preview(args, env = {}) {
  await ensurePreview();
  return run(JAVA, ['-Djava.awt.headless=true', '-cp', path.join(LIB, '*'), 'dev.oriel.preview.Preview', ...args], { env });
}
export function engineIdentity() {
  requireThat(fs.existsSync(LIB), 'PREVIEW_NOT_BUILT', '先构建 :preview:installDist。');
  return treeHash(LIB);
}
export const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
