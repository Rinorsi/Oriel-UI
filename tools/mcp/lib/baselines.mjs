import fs from 'node:fs';
import path from 'node:path';
import { ROOT, STATE, WORKSPACE, id, now, safePath, fileHash, treeHash, sha, requireThat, writeJson, readJson } from './core.mjs';
import { installFiles } from './transaction.mjs';
import { pixels } from '../../bench/effects.mjs';

const roots = ['preview/src/test/snapshots/', 'engine/src/test/resources/', 'kit/src/test/resources/'];
function difference(leftFile, rightFile) {
  const left = pixels(fs.readFileSync(leftFile)), right = pixels(fs.readFileSync(rightFile));
  requireThat(left.width === right.width && left.height === right.height, 'BASELINE_SIZE_CHANGED', '当前流程仅支持同尺寸PNG基线；尺寸变化须另行明确审批。');
  let changedPixels = 0, maxChannelDelta = 0;
  for (let i = 0; i < left.rgba.length; i += 4) {
    let changed = false;
    for (let c = 0; c < 4; c++) { const delta = Math.abs(left.rgba[i+c] - right.rgba[i+c]); changed ||= delta > 0; maxChannelDelta = Math.max(maxChannelDelta, delta); }
    if (changed) changedPixels++;
  }
  return { width: left.width, height: left.height, changedPixels, maxChannelDelta, tolerance: 0 };
}
export function baselineManager({ root = ROOT, state = STATE, lockRoot } = {}) {
  const directory = path.join(state, 'baselines');
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  lockRoot ??= path.join(WORKSPACE, 'daily', day, 'scratch/mcp-baseline-locks');
  const recordFile = name => safePath(directory, name + '.json');
  const sourceFingerprint = () => sha(JSON.stringify(['engine/src/main','preview/src/main','kit/src/main','mc-extension/src/main','script-rhino/src/main','docs/zh','tools/mcp/lib','preview/build/install/preview/lib'].map(relative => [relative,treeHash(path.join(root,relative))])));
  const validate = item => {
    requireThat(item.kind === 'human-baseline', 'BASELINE_REVIEW_REQUIRED', '普通页面批准不能更新黄金图。');
    requireThat(item.sourceFingerprint === sourceFingerprint(), 'STALE_BASELINE_SOURCE', '代码、资源、规则或预览产物已变化，须重新提出并审批基线候选。');
    requireThat(fileHash(safePath(root, item.baseline)) === item.oldHash, 'STALE_BASELINE', '黄金图已发生变化，请重新提出候选。');
    for (const shot of item.shots) requireThat(fileHash(safePath(state, path.relative(state, shot.image).split(path.sep).join('/'))) === shot.hash, 'SNAPSHOT_CHANGED', '固定基线验收图已变化。');
    if (item.oracleEvidence) requireThat(fileHash(safePath(state, path.relative(state, item.oracleEvidence.image).split(path.sep).join('/'))) === item.oracleEvidence.hash, 'SNAPSHOT_CHANGED', '固定浏览器参考已变化。');
  };
  const present = item => {
    validate(item); item.presentedShotHashes = item.shots.map(s => s.hash); writeJson(recordFile(item.id), item);
    return { schemaVersion: 1, ...item, imagePaths: item.shots.map(s => s.image), agentMayApprove: false,
      nextActions: ['向用户展示旧图/新图及差异数，解释与固定浏览器oracle的一致性；用户明确同意该基线更新后才逐字代记决定。'] };
  };
  return function baseline({ action = 'propose', proposalId, baseline, candidate, oracle, reason, decision, quote } = {}) {
    fs.mkdirSync(directory, { recursive: true });
    if (action === 'propose') {
      requireThat(typeof baseline === 'string' && path.posix.normalize(baseline) === baseline && roots.some(prefix => baseline.startsWith(prefix)) && baseline.endsWith('.png'), 'INVALID_BASELINE', '仅支持已存在测试资源目录下的PNG黄金图。');
      requireThat(typeof reason === 'string' && reason.trim().length > 0 && reason.length <= 4000, 'REASON_REQUIRED', '须解释此次基线变化及浏览器oracle依据。');
      const target = safePath(root, baseline), input = safePath(state, candidate);
      for (const file of [target, input]) requireThat(fs.existsSync(file) && fs.statSync(file).isFile() && fs.statSync(file).size <= 8 * 1024 * 1024, 'INVALID_IMAGE', '基线/候选须为已有且不超过8MiB的PNG。');
      requireThat(fs.statSync(input).size <= 300 * 1024, 'BASELINE_TOO_LARGE', '仓库黄金图候选不得超过300KiB。');
      const fingerprint = sourceFingerprint();
      const oldHash = fileHash(target), candidateHash = fileHash(input), diff = difference(target, input), name = id('baseline');
      const snapshot = path.join(directory, name); fs.mkdirSync(snapshot);
      fs.copyFileSync(target, path.join(snapshot, 'old.png')); fs.copyFileSync(input, path.join(snapshot, 'candidate.png'));
      let oracleEvidence = null;
      if (oracle) {
        const reference = safePath(state, oracle);
        requireThat(fs.statSync(reference).size <= 8 * 1024 * 1024, 'INVALID_IMAGE', '浏览器参考图超过8MiB。');
        const comparison = difference(input, reference), oracleHash = fileHash(reference);
        fs.copyFileSync(reference, path.join(snapshot, 'browser.png'));
        requireThat(fileHash(path.join(snapshot, 'browser.png')) === oracleHash, 'SNAPSHOT_CHANGED', '冻结浏览器参考时源文件变化。');
        oracleEvidence = { hash: oracleHash, image: path.join(snapshot, 'browser.png'), comparison, runtimeVerified: false, provenance: 'caller-provided fixed browser PNG; producer identity not verified' };
      }
      const item = { id: name, kind: 'human-baseline', mode: 'screenshots', decision: 'pending', createdAt: now(), baseline, reason: reason.trim(), oldHash, candidateHash, diff, sourceFingerprint: fingerprint,
        oracleEvidence, shots: [{ image: path.join(snapshot, 'old.png'), hash: oldHash }, { image: path.join(snapshot, 'candidate.png'), hash: candidateHash }] };
      return present(item);
    }
    requireThat(['status','decide','promote'].includes(action), 'INVALID_ACTION', '基线流程为propose/status/decide/promote。');
    requireThat(typeof proposalId === 'string' && /^baseline-[a-z0-9-]+$/.test(proposalId) && fs.existsSync(recordFile(proposalId)), 'BASELINE_NOT_FOUND', '基线候选不存在。');
    const item = readJson(recordFile(proposalId));
    if (action === 'status' && item.promotedAt) return { schemaVersion: 1, ...item, currentCandidateInstalled: fileHash(safePath(root, item.baseline)) === item.candidateHash, currentSourceMatches: item.sourceFingerprint === sourceFingerprint() };
    if (action === 'status') return present(item);
    validate(item);
    if (action === 'decide') {
      requireThat(item.decision === 'pending' && !item.promotedAt, 'REVIEW_CLOSED', '基线验收已处理。');
      requireThat(['approved','rejected'].includes(decision) && typeof quote === 'string' && quote.trim() && quote.length <= 2000, 'QUOTE_REQUIRED', '须逐字引用用户对该固定基线的明确决定。');
      requireThat(item.shots.every(shot => (item.presentedShotHashes || []).includes(shot.hash)), 'REVIEW_INCOMPLETE', '尚未返回固定基线图片。');
      const decided = { ...item, decision, quote: quote.trim(), source: 'user-chat', recordedBy: 'agent', approvalStrength: 'chat-attested-by-agent', decidedAt: now() };
      writeJson(recordFile(item.id), decided); return { schemaVersion: 1, ...decided };
    }
    requireThat(item.decision === 'approved' && item.source === 'user-chat' && item.quote && !item.promotedAt, 'BASELINE_APPROVAL_REQUIRED', '必须单独获得该固定黄金候选的用户批准，不能挪用页面批准。');
    requireThat(item.oracleEvidence && item.oracleEvidence.comparison.changedPixels === 0 && fileHash(item.oracleEvidence.image) === item.oracleEvidence.hash, 'BROWSER_ORACLE_REQUIRED', '安装前必须提供固定浏览器参考并证明候选逐像素一致；没有oracle不能更新黄金图。');
    fs.mkdirSync(lockRoot, { recursive: true });
    const lock = path.join(lockRoot, sha(fs.realpathSync(root) + '/' + item.baseline));
    try { fs.mkdirSync(lock); } catch (error) { if (error.code === 'EEXIST') requireThat(false, 'BASELINE_BUSY', '另一进程正在更新该黄金图；不自动清除未知锁。'); throw error; }
    try {
      validate(item);
      const snapshot = path.dirname(item.shots[1].image);
      installFiles([{ file: 'candidate.png', target: item.baseline, destination: safePath(root, item.baseline), previous: item.oldHash, hash: item.candidateHash }], snapshot, root, path.join(snapshot, 'backup'));
      const result = { ...item, promotedAt: now(), installedHash: fileHash(safePath(root, item.baseline)), rollbackCopy: path.join(snapshot, 'backup/0'), candidateMatchesProvidedBrowserPixels: true, browserRuntimeVerified: false };
      writeJson(recordFile(item.id), result); return { schemaVersion: 1, ...result, note: '仅替换获准PNG并匹配提供的浏览器参考像素；浏览器采集来源仍须报告独立证据。' };
    } finally { fs.rmdirSync(lock); }
  };
}
export const baseline = baselineManager();
