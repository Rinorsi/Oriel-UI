import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { ROOT, STATE, now, id, sha, fileHash, safePath, inside, requireThat, loadRecord, saveRecord, writeJson, readJson } from './core.mjs';
import { assertCurrent, verifyRevision } from './projects.mjs';
import { installFiles } from './transaction.mjs';

export function approved(reviewId) {
  const review = loadRecord('reviews', reviewId);
  requireThat(review.kind === 'human' && review.decision === 'approved' && review.source === 'local-review-form', 'APPROVAL_REQUIRED', '必须由用户本人在固定版本的网页验收页批准。');
  const revision = loadRecord('revisions', review.revisionId);
  requireThat(revision.hash === review.hash && revision.check?.passed, 'CHECK_REQUIRED', '验收版本没有有效检查结果。');
  assertCurrent(revision);
  return { review, revision };
}
export function integrate({ action = 'plan', reviewId, planId, target = 'oriel-dev' }) {
  requireThat(target === 'oriel-dev', 'TARGET_ADAPTER_REQUIRED', '首版接入适配器为本仓库 oriel-dev；其他工程需提供专属适配器，不能猜测目录。');
  if (action === 'plan') {
    const { review, revision } = approved(reviewId);
    const root = path.join(ROOT, 'common/src/main/resources');
    const files = revision.files.filter(f => f.file.startsWith('assets/' + revision.project.namespace + '/')).map(f => {
      const destination = safePath(root, f.file);
      return { file: f.file, hash: f.hash, destination, previous: fileHash(destination), operation: fs.existsSync(destination) ? 'update' : 'create' };
    });
    const plan = { id: id('plan'), projectId: revision.projectId, reviewId: review.id, revisionId: revision.id, hash: revision.hash, target, root, files,
      pageUrl: revision.project.namespace + ':' + revision.project.entry, status: 'planned', createdAt: now(),
      notes: ['接入共同资源目录；两个加载器均会打包。', '使用 /oriel open 打开，游戏预览工具会自动打开。', '不生成真实业务处理器，不安装 browser/mock.js。'] };
    plan.planHash = sha(JSON.stringify({ hash: plan.hash, target, files }));
    saveRecord('plans', plan); return plan;
  }
  requireThat(action === 'apply', 'INVALID_ACTION', 'action 为 plan/apply。');
  const plan = loadRecord('plans', planId);
  const { revision } = approved(plan.reviewId);
  requireThat(plan.status === 'planned' && revision.hash === plan.hash, 'STALE_PLAN', '接入计划已过期或已执行。');
  requireThat(plan.root === path.join(ROOT, 'common/src/main/resources') && plan.planHash === sha(JSON.stringify({ hash: plan.hash, target: plan.target, files: plan.files })), 'PLAN_CHANGED', '接入计划被修改。');
  try {
    installFiles(plan.files, revision.pack, plan.root, path.join(STATE, 'plans', plan.id));
  } catch (error) {
    plan.status = 'failed'; plan.error = error.message; plan.rollbackConflicts = error.rollbackConflicts || []; saveRecord('plans', plan); throw error;
  }
  plan.status = 'applied'; plan.appliedAt = now(); saveRecord('plans', plan);
  const project = loadRecord('projects', plan.projectId);
  project.integration = { planId: plan.id, reviewId: plan.reviewId, hash: plan.hash, pageUrl: plan.pageUrl, at: now(), target: plan.target };
  saveRecord('projects', project);
  return { ...project.integration, status: 'integrated', gameVerified: false, nextActions: ['oriel_game_preview.start'] };
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
  for (const file of plan.files) requireThat(fileHash(safePath(plan.root, file.file)) === file.hash, 'TARGET_CONFLICT', '游戏资源被改动：' + file.file);
  const selected = revision.project.scenes.find(s => s.name === scene);
  requireThat(selected, 'UNKNOWN_SCENE', '该场景未纳入网页验收。');
  const running = fs.readdirSync(path.join(STATE, 'jobs')).filter(f => f.endsWith('.json')).map(f => readJson(path.join(STATE, 'jobs', f))).find(j => j.kind === 'game' && ['queued', 'running'].includes(j.status));
  requireThat(!running, 'GAME_BUSY', '已有游戏预览任务，请先等待或取消。', { jobId: running?.id });
  const job = { id: id('game'), kind: 'game', projectId, revisionId: revision.id, hash: revision.hash, reviewId: project.integration.reviewId,
    pageUrl: project.integration.pageUrl, profile: project.profile, scene, actions: selected.actions, fixture: project.fixture, status: 'queued', createdAt: now() };
  job.directory = path.join(STATE, 'jobs', job.id); fs.mkdirSync(job.directory, { recursive: true }); saveRecord('jobs', job);
  const log = fs.openSync(path.join(job.directory, 'worker.log'), 'a');
  const child = spawn(process.execPath, [path.join(ROOT, 'tools/mcp/game-worker.mjs'), job.id], { cwd: ROOT, detached: true, windowsHide: true, stdio: ['ignore', log, log], env: { ...process.env, ORIEL_MCP_STATE: STATE } });
  child.once('error', error => saveRecord('jobs', { ...job, status: 'failed', error: error.message }));
  child.unref(); fs.closeSync(log);
  return { ...job, nextActions: ['oriel_game_preview.status'], note: '将启动独立开发客户端；不操作用户现有游戏会话。' };
}
