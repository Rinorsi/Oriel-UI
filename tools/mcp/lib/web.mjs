import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { ROOT, STATE, now, id, token, requireThat, loadRecord, saveRecord, writeJson, readJson, safePath } from './core.mjs';
import { freeze, assertCurrent, verifyRevision } from './projects.mjs';

export async function startWeb(revision, reviewId = null) {
  verifyRevision(revision);
  const sessionId = id('web'), directory = path.join(STATE, 'jobs', sessionId);
  fs.mkdirSync(directory, { recursive: true });
  const config = { id: sessionId, revisionId: revision.id, reviewId, shellToken: token(), contentToken: token(), csrf: token(), state: STATE, createdAt: now() };
  const configPath = path.join(directory, 'config.json'); writeJson(configPath, config);
  const log = fs.openSync(path.join(directory, 'web.log'), 'a');
  const child = spawn(process.execPath, [path.join(ROOT, 'tools/mcp/web-worker.mjs'), configPath], {
    cwd: ROOT, detached: true, windowsHide: true, stdio: ['ignore', log, log],
    env: { ...process.env, ORIEL_MCP_STATE: STATE },
  });
  child.unref(); fs.closeSync(log);
  let spawnError; child.on('error', e => { spawnError = e; });
  const ready = path.join(directory, 'ready.json');
  for (let n = 0; n < 100; n++) {
    if (spawnError) throw spawnError;
    if (fs.existsSync(ready)) {
      const receipt = readJson(ready);
      saveRecord('jobs', { ...receipt, kind: 'web', revisionId: revision.id, reviewId, configPath, createdAt: now() });
      return receipt;
    }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  requireThat(false, 'WEB_START_FAILED', '网页服务未启动，请检查日志。', { log: path.join(directory, 'web.log') });
}
export async function webPreview({ action = 'start', projectId, sessionId }) {
  if (action === 'start') return startWeb(await freeze(projectId));
  const job = loadRecord('jobs', sessionId);
  requireThat(job.kind === 'web', 'INVALID_SESSION', '不是网页服务。');
  if (action === 'status') {
    try { const r = await fetch(job.origin + '/health', { signal: AbortSignal.timeout(1500) }); return { ...job, running: r.ok }; }
    catch { return { ...job, running: false }; }
  }
  requireThat(action === 'stop', 'INVALID_ACTION', 'action 为 start/status/stop。');
  const config = readJson(job.configPath);
  const response = await fetch(job.origin + '/stop/' + config.shellToken, { method: 'POST', signal: AbortSignal.timeout(2000) });
  requireThat(response.ok, 'WEB_STOP_FAILED', '网页服务停止失败。');
  return { sessionId, stopped: true };
}
export async function review({ action = 'prepare', projectId, reviewId }) {
  if (action === 'status') {
    const item = loadRecord('reviews', reviewId);
    let current = true, blocked;
    try { assertCurrent(loadRecord('revisions', item.revisionId)); } catch (e) { current = false; blocked = { code: e.code, message: e.message }; }
    return { ...item, current, blocked };
  }
  requireThat(action === 'prepare', 'INVALID_ACTION', '验收只提供 prepare/status；Agent 不能设置批准。');
  const revision = await freeze(projectId);
  requireThat(revision.check?.passed, 'CHECK_REQUIRED', '必须先通过当前版本的 oriel_check。');
  assertCurrent(revision);
  const item = { id: id('review'), projectId, revisionId: revision.id, hash: revision.hash, decision: 'pending', kind: 'human', createdAt: now() };
  saveRecord('reviews', item);
  const web = await startWeb(revision, item.id);
  item.url = web.url; item.sessionId = web.id; saveRecord('reviews', item);
  return { ...item, nextActions: ['请用户本人打开网页，检查场景后批准或填写修改意见。'], agentMayApprove: false };
}
