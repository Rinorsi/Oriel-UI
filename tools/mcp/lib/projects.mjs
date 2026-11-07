import fs from 'node:fs';
import path from 'node:path';
import { ROOT, STATE, KIT, inside, identifier, safePath, walk, copyTree, sha, treeHash, fileHash, id, now, requireThat, loadRecord, saveRecord, writeJson, ensurePreview, engineIdentity } from './core.mjs';
import { rulesIdentity, validateProfile } from './rules.mjs';

export function registerProject(args) {
  const projectId = identifier(args.projectId);
  const sourceRoot = path.resolve(args.sourceRoot || '');
  requireThat(args.sourceRoot && fs.existsSync(sourceRoot) && fs.statSync(sourceRoot).isDirectory(), 'SOURCE_REQUIRED', 'sourceRoot 必须指向已有的 UI 包目录（包含 assets）。');
  requireThat(!inside(sourceRoot, STATE) && sourceRoot !== ROOT, 'SOURCE_TOO_BROAD', '请指定单独的 UI 包目录，不能登记整个仓库或工作区。');
  requireThat(typeof args.namespace === 'string' && /^[a-z][a-z0-9_]{1,40}$/.test(args.namespace) && !['oriel', 'minecraft'].includes(args.namespace), 'INVALID_NAMESPACE', '页面使用独立命名空间，不能覆盖 oriel 或 minecraft。');
  const entry = args.entry || 'ui/index.html';
  safePath(sourceRoot, 'assets/' + args.namespace + '/' + entry);
  const fixture = args.fixture || {};
  requireThat(JSON.stringify(fixture).length <= 65536, 'FIXTURE_TOO_LARGE', '预览数据超过 64 KiB。');
  const scenes = args.scenes || [{ name: 'initial', actions: [], description: '初始页面' }];
  requireThat(Array.isArray(scenes) && scenes.length > 0 && scenes.length <= 16, 'INVALID_SCENES', '需要 1–16 个验收场景。');
  const sceneNames = new Set();
  for (const scene of scenes) {
    identifier(scene.name);
    requireThat(!sceneNames.has(scene.name), 'DUPLICATE_SCENE', '场景名称不能重复。'); sceneNames.add(scene.name);
    validateActions(scene.actions || []);
  }
  const previous = fs.existsSync(path.join(STATE, 'projects', projectId + '.json')) ? loadRecord('projects', projectId) : {};
  const project = { ...previous, id: projectId, sourceRoot, namespace: args.namespace, entry,
    title: String(args.title || projectId).slice(0, 120), profile: validateProfile(args.profile), fixture,
    scenes, behavior: args.behavior || 'javascript', businessContract: String(args.businessContract || '前端演示，不修改游戏数据。').slice(0, 12000),
    knownDifferences: args.knownDifferences || ['浏览器字体与游戏字体可能不同。', '原生 MC 内容在网页端为占位展示。'],
    updatedAt: now(), createdAt: previous.createdAt || now() };
  requireThat(['javascript', 'java'].includes(project.behavior), 'INVALID_BEHAVIOR', 'behavior 为 javascript 或 java。');
  sourceFiles(project);
  saveRecord('projects', project);
  return { ...project, nextActions: ['oriel_web_preview', 'oriel_check'], note: '修改登记信息或源文件后需要重新检查和验收。' };
}
export function validateActions(actions) {
  requireThat(Array.isArray(actions) && actions.length <= 100, 'INVALID_ACTIONS', '动作应为不超过 100 条的数组。');
  for (const action of actions) {
    requireThat(typeof action === 'string' && action.length < 2000 && !/[\r\n]/.test(action) && (/^(wait|move|click|wheel|key|type|why-no-click|focus-info)\s+\S/.test(action) || /^preedit(?:\s+.*)?$/.test(action) || /^trace-input (on|off)$/.test(action)), 'INVALID_ACTION', '只支持 wait/move/click/wheel/key/type/preedit/why-no-click/focus-info 和 trace-input on/off，每条动作占一行。');
    if (action.startsWith('wait ')) requireThat(/^wait [0-9]+$/.test(action) && Number(action.slice(5)) <= 600, 'INVALID_WAIT', '等待不超过 600 帧。');
  }
}
export function sourceFiles(project) {
  const files = walk(project.sourceRoot);
  requireThat(files.length <= 1200, 'PACKAGE_TOO_LARGE', '单个页面包最多 1200 个文件。');
  let size = 0;
  for (const rel of files) {
    requireThat(rel.startsWith('assets/' + project.namespace + '/') || rel.startsWith('browser/') || ['README.md', 'ui-package.json'].includes(rel), 'UNDECLARED_FILE', '包内文件必须在自身 assets 命名空间或 browser 目录：' + rel);
    requireThat(!rel.startsWith('assets/') || /\.(html|css|js|json|png|jpg|jpeg|webp|ttf|otf|ogg|txt)$/i.test(rel), 'UNSUPPORTED_ASSET', '不支持的游戏资源：' + rel);
    const file = safePath(project.sourceRoot, rel);
    size += fs.statSync(file).size;
  }
  requireThat(size <= 32 * 1024 * 1024, 'PACKAGE_TOO_LARGE', '页面源包超过 32 MiB。');
  requireThat(files.includes('assets/' + project.namespace + '/' + project.entry), 'ENTRY_MISSING', '页面入口不存在。');
  return files;
}
export function contentIdentity(project) {
  const version = project.profile.minecraft === '26.1.2' ? '26.1' : project.profile.minecraft;
  const assetJar = path.resolve(process.env.ORIEL_MC_JAR || path.join(ROOT, 'common/versions', version, 'build/moddev/artifacts/vanilla-' + project.profile.minecraft + '-1-merged.jar'));
  requireThat(fs.existsSync(assetJar), 'MC_ASSETS_REQUIRED', '目标版本客户端资源不存在，请设置 ORIEL_MC_JAR 或构建该版本。', { assetJar });
  const assetsHash = fileHash(assetJar);
  const data = { title: project.title, namespace: project.namespace, entry: project.entry, profile: project.profile,
    fixture: project.fixture, scenes: project.scenes, behavior: project.behavior, businessContract: project.businessContract, knownDifferences: project.knownDifferences };
  sourceFiles(project);
  return { hash: sha(JSON.stringify({ data, source: treeHash(project.sourceRoot), theme: treeHash(path.join(KIT, 'assets')), rules: rulesIdentity(), engine: engineIdentity(), assetsHash })), rulesHash: rulesIdentity(), engineHash: engineIdentity(), assetJar, assetsHash };
}
export async function freeze(projectId) {
  await ensurePreview();
  const project = loadRecord('projects', projectId), identity = contentIdentity(project);
  const revisionId = 'rev-' + identity.hash.slice(0, 32);
  const existing = path.join(STATE, 'revisions', revisionId + '.json');
  if (fs.existsSync(existing)) { const rev = loadRecord('revisions', revisionId); verifyRevision(rev); return rev; }
  const directory = path.join(STATE, 'revisions', revisionId);
  fs.mkdirSync(directory, { recursive: true });
  const pack = path.join(directory, 'pack'); fs.mkdirSync(pack, { recursive: true });
  copyTree(project.sourceRoot, pack);
  // Copy the complete theme resource root: CSS can reference fonts/icons dynamically.
  copyTree(path.join(KIT, 'assets/oriel'), path.join(pack, 'assets/oriel'));
  const files = walk(pack).map(file => ({ file, hash: fileHash(path.join(pack, file)) }));
  const revision = { id: revisionId, projectId, hash: identity.hash, rulesHash: identity.rulesHash, engineHash: identity.engineHash,
    project, directory, pack, files, packHash: sha(JSON.stringify(files)), assetJar: identity.assetJar, assetsHash: identity.assetsHash, entry: 'assets/' + project.namespace + '/' + project.entry, createdAt: now() };
  // A source edit during copying must never produce a receipt for a different page.
  requireThat(contentIdentity(project).hash === identity.hash, 'SOURCE_CHANGED', '冻结过程中源文件发生变化，请重试。');
  writeJson(path.join(directory, 'fixture.json'), project.fixture);
  saveRecord('revisions', revision);
  return revision;
}
export function verifyRevision(revision) {
  requireThat(fileHash(revision.assetJar) === revision.assetsHash, 'MC_ASSETS_CHANGED', 'Minecraft 预览资源发生变化，请重新检查。');
  const files = walk(revision.pack).map(file => ({ file, hash: fileHash(safePath(revision.pack, file)) }));
  requireThat(sha(JSON.stringify(files)) === revision.packHash, 'SNAPSHOT_CHANGED', '固定版本的资源被修改，请重新检查。');
  requireThat(fileHash(path.join(revision.directory, 'fixture.json')) === sha(JSON.stringify(revision.project.fixture, null, 2) + '\n'), 'SNAPSHOT_CHANGED', '预览数据已变化。');
}
export function assertCurrent(revision) {
  verifyRevision(revision);
  const project = loadRecord('projects', revision.projectId);
  requireThat(contentIdentity(project).hash === revision.hash, 'STALE_REVISION', '页面、资源、规则、引擎或目标已变化；请重新检查和验收。');
  return project;
}
export function projectStatus(projectId) {
  const project = loadRecord('projects', projectId);
  let currentHash;
  try { currentHash = contentIdentity(project).hash; } catch (e) { return { projectId, stage: 'blocked', code: e.code, message: e.message, nextActions: ['oriel_check'] }; }
  const reviews = fs.readdirSync(path.join(STATE, 'reviews')).filter(f => f.endsWith('.json')).map(f => JSON.parse(fs.readFileSync(path.join(STATE, 'reviews', f), 'utf8')))
    .filter(r => r.projectId === projectId).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const review = reviews.find(r => r.hash === currentHash);
  const revisionFile = path.join(STATE, 'revisions', 'rev-' + currentHash.slice(0, 32) + '.json');
  const revision = fs.existsSync(revisionFile) ? JSON.parse(fs.readFileSync(revisionFile, 'utf8')) : null;
  let stage = revision?.check?.passed ? 'checked' : 'draft';
  if (review) stage = review.decision === 'approved' ? 'approved' : review.decision === 'rejected' ? 'rejected' : 'awaiting_review';
  if (project.integration?.hash === currentHash) stage = project.game?.hash === currentHash && project.game.passed ? 'game_verified' : 'integrated';
  return { projectId, title: project.title, profile: project.profile, currentHash, stage, revisionId: revision?.id,
    check: revision?.check, review: review ? { id: review.id, decision: review.decision, decidedAt: review.decidedAt, feedback: review.feedback } : null,
    previousReviewStale: reviews.length > 0 && !review, integration: project.integration, game: project.game,
    nextActions: stage === 'draft' ? ['oriel_check'] : stage === 'checked' || stage === 'rejected' ? ['oriel_review.prepare'] : stage === 'awaiting_review' ? ['等待用户本人网页验收'] : stage === 'approved' ? ['oriel_integrate.plan'] : ['oriel_game_preview'] };
}
