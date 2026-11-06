import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { ROOT, STATE, now, id, token, requireThat, loadRecord, saveRecord, writeJson, readJson, safePath, fileHash } from './core.mjs';
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
export async function review({ action = 'prepare', projectId, reviewId, mode = 'web', decision, quote }) {
  if (action === 'status') {
    const item = loadRecord('reviews', reviewId);
    let current = true, blocked;
    try { assertCurrent(loadRecord('revisions', item.revisionId)); } catch (e) { current = false; blocked = { code: e.code, message: e.message }; }
    return { ...item, current, blocked };
  }
  if (action === 'decide') {
    // Screenshots reviews only: the user looked at the images the agent was handed and said so in chat. The tool cannot see the chat,
    // so the receipt keeps the user's own words and the hashes of what was shown, and marks the approval as attested by the agent.
    const item = loadRecord('reviews', reviewId);
    requireThat(item.mode === 'screenshots', 'REVIEW_MODE', '只有 mode=screenshots 的验收可以在聊天里记录决定；网页验收必须由用户在网页上点击。');
    requireThat(item.decision === 'pending', 'REVIEW_CLOSED', '该验收已处理。');
    requireThat(['approved', 'rejected'].includes(decision), 'INVALID_DECISION', 'decision 为 approved 或 rejected。');
    requireThat(typeof quote === 'string' && quote.trim().length > 0 && quote.length <= 2000, 'QUOTE_REQUIRED', '必须逐字引用用户在聊天里表达这个决定的那句话（不能为空，最长 2000 字）。');
    const revision = loadRecord('revisions', item.revisionId);
    assertCurrent(revision);
    for (const shot of item.shots) requireThat(fileHash(shot.image) === shot.hash, 'SNAPSHOT_CHANGED', '验收截图已变化，请重新 prepare：' + shot.scene);
    const decided = { ...item, decision, quote: quote.trim(), decidedAt: now(), source: 'user-chat', recordedBy: 'agent', approvalStrength: 'chat-attested-by-agent' };
    saveRecord('reviews', decided);
    return { ...decided, nextActions: decision === 'approved' ? ['oriel_integrate.plan'] : ['按用户意见修改后重新 oriel_check 和 oriel_review.prepare'] };
  }
  requireThat(action === 'prepare', 'INVALID_ACTION', '验收为 prepare/status/decide；网页验收时 Agent 不能设置批准。');
  requireThat(['web', 'screenshots'].includes(mode), 'INVALID_MODE', 'mode 为 web 或 screenshots。');
  const revision = await freeze(projectId);
  requireThat(revision.check?.passed, 'CHECK_REQUIRED', '必须先通过当前版本的 oriel_check。');
  assertCurrent(revision);
  const item = { id: id('review'), projectId, revisionId: revision.id, hash: revision.hash, decision: 'pending', kind: 'human', mode, createdAt: now() };
  if (mode === 'screenshots') {
    const shots = (revision.check.shots || []).filter(shot => shot.passed && shot.image && fs.existsSync(shot.image))
      .map(shot => ({ scene: shot.scene, width: shot.width, height: shot.height, image: shot.image, hash: fileHash(shot.image) }));
    requireThat(shots.length > 0, 'NO_SCREENSHOTS', '当前版本的检查没有产生可用的截图。');
    item.shots = shots; item.shotHashes = shots.map(shot => shot.hash);
    saveRecord('reviews', item);
    return { ...item, imagePaths: shots.map(shot => shot.image), agentMayApprove: false,
      nextActions: ['把这些截图原样发给用户，等用户在聊天里明确同意或提出修改；同意后用 oriel_review.decide 逐字引用用户的话。'] };
  }
  saveRecord('reviews', item);
  const web = await startWeb(revision, item.id);
  item.url = web.url; item.sessionId = web.id; saveRecord('reviews', item);
  return { ...item, nextActions: ['请用户本人打开网页，检查场景后批准或填写修改意见。'], agentMayApprove: false };
}
