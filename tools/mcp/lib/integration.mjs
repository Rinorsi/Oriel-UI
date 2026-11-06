import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { ROOT, STATE, now, id, sha, fileHash, safePath, inside, requireThat, loadRecord, saveRecord, writeJson, readJson } from './core.mjs';
import { assertCurrent, verifyRevision } from './projects.mjs';
import { installFiles } from './transaction.mjs';
import { modProjectFiles, assertProjectRoot } from './mod-project.mjs';

const DEV_ROOT = path.join(ROOT, 'common/src/main/resources');
// The game serves config/oriel/my-pages/ as this namespace, so a page copied there opens without any mod code.
const FOLDER_NAMESPACE = 'oriel_mine';
const TARGETS = ['oriel-dev', 'oriel-folder', 'mod-project'];

// An approval is either a click on the local review page, or, for a screenshots review only, the user's own words in chat
// quoted verbatim. Only web-worker.mjs writes source 'local-review-form'; only review({action:'decide'}) writes 'user-chat'.
export function approved(reviewId) {
  const review = loadRecord('reviews', reviewId);
  const web = review.kind === 'human' && review.decision === 'approved' && review.source === 'local-review-form';
  const chat = review.kind === 'human' && review.decision === 'approved' && review.mode === 'screenshots' && review.source === 'user-chat'
    && typeof review.quote === 'string' && review.quote.trim().length > 0 && Array.isArray(review.shotHashes) && review.shotHashes.length > 0;
  requireThat(web || chat, 'APPROVAL_REQUIRED', '必须由用户本人批准固定版本：在网页验收页点击，或看过截图后在聊天里明确同意（须逐字引用用户的话）。');
  const revision = loadRecord('revisions', review.revisionId);
  requireThat(revision.hash === review.hash && revision.check?.passed, 'CHECK_REQUIRED', '验收版本没有有效检查结果。');
  assertCurrent(revision);
  return { review, revision };
}
const rootOf = plan => plan.target === 'mod-project' ? assertProjectRoot(plan.projectRoot) : plan.target === 'oriel-folder' ? plan.folder : DEV_ROOT;
const planDigest = plan => sha(JSON.stringify({ hash: plan.hash, target: plan.target, root: plan.root, files: plan.files, pageUrl: plan.pageUrl, projectRoot: plan.projectRoot, guidance: plan.guidance }));
const targetOf = file => file.target ?? file.file;

function devFiles(revision) {
  return revision.files.filter(f => f.file.startsWith('assets/' + revision.project.namespace + '/')).map(f => {
    const destination = safePath(DEV_ROOT, f.file);
    return { file: f.file, hash: f.hash, destination, previous: fileHash(destination), operation: fs.existsSync(destination) ? 'update' : 'create' };
  });
}
// In a folder the page's namespace is oriel_mine and the namespace folder itself is the root, so files lose "assets/<ns>/".
function folderFiles(revision, folder) {
  const ns = revision.project.namespace, prefix = 'assets/' + ns + '/';
  return revision.files.filter(f => f.file.startsWith(prefix)).map(f => {
    if (/\.(html|css|js|json)$/i.test(f.file)) {
      const text = fs.readFileSync(safePath(revision.pack, f.file), 'utf8');
      requireThat(!text.includes(ns + ':'), 'FOLDER_NAMESPACE_REFERENCE',
        '放进文件夹后页面的命名空间是 ' + FOLDER_NAMESPACE + '，不是 ' + ns + '；请在页面里用相对路径引用自己的资源：' + f.file, { file: f.file });
    }
    const target = f.file.slice(prefix.length), destination = safePath(folder, target);
    return { file: f.file, target, hash: f.hash, destination, previous: fileHash(destination), operation: fs.existsSync(destination) ? 'update' : 'create' };
  });
}
export function integrate({ action = 'plan', reviewId, planId, target = 'oriel-dev', folder, projectRoot, modId, resourcesDir, pageName }) {
  requireThat(TARGETS.includes(target), 'TARGET_ADAPTER_REQUIRED', '接入目标为 oriel-dev、oriel-folder 或 mod-project。');
  if (action === 'plan') {
    const { review, revision } = approved(reviewId);
    let root, files, pageUrl, notes, extra = {};
    if (target === 'oriel-dev') {
      root = DEV_ROOT; files = devFiles(revision); pageUrl = revision.project.namespace + ':' + revision.project.entry;
      notes = ['接入共同资源目录；两个加载器均会打包。', '使用 /oriel open 打开，游戏预览工具会自动打开。', '不生成真实业务处理器，不安装 browser/mock.js。'];
    } else if (target === 'mod-project') {
      extra = modProjectFiles(revision, { projectRoot, modId, resourcesDir, pageName });
      ({ root, files, pageUrl } = extra);
      notes = ['只安装已批准的资源，不修改 Java、Gradle 或模组元数据。', '源包全部在 ui/ 下时去除共同 ui/ 前缀，否则保留命名空间内目录结构。', '下游游戏需自行打开验证；不启动 Oriel 的独立客户端。'];
    } else {
      requireThat(typeof folder === 'string' && path.isAbsolute(folder), 'FOLDER_REQUIRED', 'oriel-folder 目标需要 folder 参数：一个绝对路径，通常是游戏的 config/oriel/my-pages。');
      root = path.resolve(folder);
      requireThat(!inside(ROOT, root), 'FOLDER_IN_REPOSITORY', '目标文件夹不能在 Oriel 仓库内。');
      fs.mkdirSync(root, { recursive: true });   // an empty directory; the paths below are checked against a real root
      files = folderFiles(revision, root); pageUrl = FOLDER_NAMESPACE + ':' + revision.project.entry;
      notes = ['复制到指定文件夹，不改 Oriel 仓库，也不改你的模组。', '该文件夹是游戏的 config/oriel/my-pages 时，游戏里用 /oriel open ' + pageUrl + ' 打开，保存文件即热重载。',
        '游戏预览工具会把同样的文件放进它自己的独立运行目录再打开。', '不生成真实业务处理器，不安装 browser/mock.js。'];
    }
    requireThat(files.length > 0, 'NOTHING_TO_INSTALL', '页面包里没有属于命名空间 ' + revision.project.namespace + ' 的文件。');
    const plan = { id: id('plan'), projectId: revision.projectId, reviewId: review.id, revisionId: revision.id, hash: revision.hash, target, root, ...(target === 'oriel-folder' ? { folder: root } : {}),
      ...extra, files, pageUrl, status: 'planned', createdAt: now(), approval: review.source, notes };
    plan.planHash = planDigest(plan);
    saveRecord('plans', plan); return plan;
  }
  requireThat(action === 'apply', 'INVALID_ACTION', 'action 为 plan/apply。');
  const plan = loadRecord('plans', planId);
  const { revision } = approved(plan.reviewId);
  requireThat(plan.status === 'planned' && revision.hash === plan.hash, 'STALE_PLAN', '接入计划已过期或已执行。');
  requireThat(plan.root === rootOf(plan) && plan.planHash === planDigest(plan), 'PLAN_CHANGED', '接入计划被修改。');
  try {
    installFiles(plan.files, revision.pack, plan.root, path.join(STATE, 'plans', plan.id));
  } catch (error) {
    plan.status = 'failed'; plan.error = error.message; plan.rollbackConflicts = error.rollbackConflicts || []; saveRecord('plans', plan); throw error;
  }
  plan.status = 'applied'; plan.appliedAt = now(); saveRecord('plans', plan);
  const project = loadRecord('projects', plan.projectId);
  project.integration = { planId: plan.id, reviewId: plan.reviewId, hash: plan.hash, pageUrl: plan.pageUrl, at: now(), target: plan.target };
  saveRecord('projects', project);
  return { ...project.integration, status: 'integrated', gameVerified: false, ...(plan.guidance ? { guidance: plan.guidance } : {}), nextActions: plan.target === 'mod-project' ? ['在下游游戏中打开 ' + plan.pageUrl + ' 并验证'] : ['oriel_game_preview.start'] };
}
export async function gamePreview({ action = 'start', projectId, jobId, scene = 'initial' }) {
  if (action !== 'start') {
    const job = loadRecord('jobs', jobId);
    requireThat(job.kind === 'game', 'INVALID_JOB', '不是游戏预览任务。');
    if (action === 'cancel') { writeJson(path.join(job.directory, 'cancel.json'), { at: now() }); return { jobId, cancellationRequested: true }; }
    requireThat(action === 'status', 'INVALID_ACTION', 'action 为 start/status/cancel。');
    if (job.status === 'running' && job.pid) {
      try { process.kill(job.pid, 0); } catch { job.status = 'interrupted'; job.error = '游戏预览 worker 已退出，未取得完成回执。'; saveRecord('jobs', job); }
    }
    return job;
  }
  const project = loadRecord('projects', projectId);
  requireThat(project.integration, 'INTEGRATION_REQUIRED', '先通过 MCP 接入已验收页面。');
  const { revision } = approved(project.integration.reviewId);
  requireThat(revision.hash === project.integration.hash, 'STALE_INTEGRATION', '接入版本已经变化。');
  const plan = loadRecord('plans', project.integration.planId);
  requireThat(plan.target !== 'mod-project', 'DOWNSTREAM_GAME_REQUIRED', 'mod-project 页面须在下游模组的游戏中验证，不能用 Oriel 的独立客户端冒充。');
  for (const file of plan.files) requireThat(fileHash(safePath(plan.root, targetOf(file))) === file.hash, 'TARGET_CONFLICT', '游戏资源被改动：' + targetOf(file));
  const selected = revision.project.scenes.find(s => s.name === scene);
  requireThat(selected, 'UNKNOWN_SCENE', '该场景未纳入网页验收。');
  const running = fs.readdirSync(path.join(STATE, 'jobs')).filter(f => f.endsWith('.json')).map(f => readJson(path.join(STATE, 'jobs', f))).find(j => j.kind === 'game' && ['queued', 'running'].includes(j.status));
  requireThat(!running, 'GAME_BUSY', '已有游戏预览任务，请先等待或取消。', { jobId: running?.id });
  const job = { id: id('game'), kind: 'game', projectId, revisionId: revision.id, hash: revision.hash, reviewId: project.integration.reviewId,
    pageUrl: project.integration.pageUrl, profile: project.profile, scene, actions: selected.actions, fixture: project.fixture, status: 'queued', createdAt: now(),
    target: plan.target, ...(plan.target === 'oriel-folder' ? { pagesCopy: plan.files.map(f => ({ from: safePath(plan.root, f.target), to: f.target, hash: f.hash })) } : {}) };
  job.directory = path.join(STATE, 'jobs', job.id); fs.mkdirSync(job.directory, { recursive: true }); saveRecord('jobs', job);
  const log = fs.openSync(path.join(job.directory, 'worker.log'), 'a');
  const child = spawn(process.execPath, [path.join(ROOT, 'tools/mcp/game-worker.mjs'), job.id], { cwd: ROOT, detached: true, windowsHide: true, stdio: ['ignore', log, log], env: { ...process.env, ORIEL_MCP_STATE: STATE } });
  child.once('error', error => saveRecord('jobs', { ...job, status: 'failed', error: error.message }));
  child.unref(); fs.closeSync(log);
  return { ...job, nextActions: ['oriel_game_preview.status'], note: '将启动独立开发客户端；不操作用户现有游戏会话。' };
}
