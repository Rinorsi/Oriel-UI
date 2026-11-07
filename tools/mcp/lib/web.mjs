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
export function reviewPage(item, offset = 0, limit = 12) {
  requireThat(item.mode === 'screenshots' && Array.isArray(item.shots), 'REVIEW_MODE', '仅截图验收支持分页。');
  requireThat(Number.isInteger(offset) && offset >= 0 && offset < item.shots.length && Number.isInteger(limit) && limit >= 1 && limit <= 12, 'INVALID_PAGE', 'offset须在截图集合内，limit为1–12。');
  const selected = item.shots.slice(offset, offset + limit);
  for (const shot of selected) requireThat(fileHash(shot.image) === shot.hash, 'SNAPSHOT_CHANGED', '验收截图已变化或缺失，请重新prepare。');
  const presented = new Set(item.presentedShotHashes || []);
  const indices = new Set(item.presentedShotIndices || []);
  for (const shot of selected) presented.add(shot.hash);
  for (let i = offset; i < offset + selected.length; i++) indices.add(i);
  item.presentedShotHashes = [...presented];
  item.presentedShotIndices = [...indices];
  saveRecord('reviews', item);
  return { ...item, imagePaths: selected.map(shot => shot.image), agentMayApprove: false, pagination: { total: item.shots.length, offset, returned: selected.length,
    remaining: item.shots.length - offset - selected.length, nextOffset: offset + selected.length < item.shots.length ? offset + selected.length : null,
    complete: item.shots.every((shot, index) => indices.has(index) && presented.has(shot.hash)), unpresentedCount: item.shots.filter((shot, index) => !indices.has(index) || !presented.has(shot.hash)).length },
    nextActions: ['逐页把截图原样交用户查看；未完整返回全部固定截图前不能记录批准。'] };
}
export async function review({ action = 'prepare', projectId, reviewId, mode = 'web', decision, quote, offset = 0, limit = 12 }) {
  if (action === 'page') {
    const item = loadRecord('reviews', reviewId);
    assertCurrent(loadRecord('revisions', item.revisionId));
    return reviewPage(item, offset, limit);
  }
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
    requireThat(decision !== 'approved' || item.shots.every((shot, index) => (item.presentedShotIndices || []).includes(index) && (item.presentedShotHashes || []).includes(shot.hash)), 'REVIEW_INCOMPLETE', '还有截图未返回，请先逐页调用oriel_review.page并交用户查看。');
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
    requireThat((revision.check.shots || []).every(shot => shot.passed && shot.image && fs.existsSync(shot.image)), 'NO_SCREENSHOTS', '检查截图存在失败或缺失，请重新检查。');
    const shots = (revision.check.shots || [])
      .map(shot => ({ scene: shot.scene, width: shot.width, height: shot.height, image: shot.image, hash: fileHash(shot.image) }));
    requireThat(shots.length > 0, 'NO_SCREENSHOTS', '当前版本的检查没有产生可用的截图。');
    item.shots = shots; item.shotHashes = shots.map(shot => shot.hash);
    saveRecord('reviews', item);
    return reviewPage(item, offset, limit);
  }
  saveRecord('reviews', item);
  const web = await startWeb(revision, item.id);
  item.url = web.url; item.sessionId = web.id; saveRecord('reviews', item);
  return { ...item, nextActions: ['请用户本人打开网页，检查场景后批准或填写修改意见。'], agentMayApprove: false };
}
