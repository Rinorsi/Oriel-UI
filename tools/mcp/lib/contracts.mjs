import fs from 'node:fs';
import path from 'node:path';
import { ROOT, WORKSPACE, LIB, fileHash, run, requireThat } from './core.mjs';
import { validateProfile } from './rules.mjs';

export function capabilities(tools, { contractVersion = 1, profile } = {}) {
  requireThat(contractVersion === 1, 'UNSUPPORTED_CONTRACT', '仅支持 contractVersion=1。');
  const target = validateProfile(profile);
  const source = path.join(ROOT, 'preview/src/main/java/dev/oriel/preview/Preview.java');
  const whyDeclared = fs.existsSync(source) && fs.readFileSync(source, 'utf8').includes('case "--why"');
  return { schemaVersion: 1, contractVersion: 1, profile: target, environment: 'offline-preview',
    tools: tools.map(({ name, description, inputSchema, annotations }) => ({ name, description, inputSchema, annotations,
      output: { contractVersion: 1, encoding: 'MCP structuredContent plus JSON text', legacyCompatible: true, error: { fields: ['code', 'message', 'details'], isError: true } } })),
    features: { liveGameAttach: { status: 'unsupported' }, selectorWhy: { status: whyDeclared ? 'source-declared' : 'unavailable', runtimeVerified: false, requires: 'R03 preview --why compiled into current worktree; never switches trees' },
      diagnosticLogs: { status: tools.some(t => t.name === 'oriel_logs') ? 'supported' : 'unavailable', recordingEnabled: process.env.ORIEL_MCP_DIAGNOSTICS === '1', scope: 'MCP tool events only; not engine tracing' },
      baselinePromotion: { status: tools.some(t => t.name === 'oriel_baseline') ? 'supported' : 'unavailable', requires: 'separate fixed baseline human approval' } },
    limits: { reviewImagesPerResponse: 12, projectScenes: 16, checkViewports: 2 },
    identity: { root: ROOT, previewBuilt: fs.existsSync(LIB), previewSourceSha256: fileHash(source) },
    notCovered: ['compiled CLI capability verification', 'live game identity', 'game GPU/IME/audio'] };
}
export function leaseAt(tempRoot) {
  const marker = name => {
    const file = path.join(tempRoot, name);
    try { const stat = fs.lstatSync(file); return { name, present: true, directory: stat.isDirectory(), modifiedAt: stat.mtime.toISOString(), owner: null, liveness: 'unknown' }; }
    catch (error) { if (error.code === 'ENOENT') return { name, present: false }; throw error; }
  };
  const benchmark = marker('oriel-gradle.lock'), slots = [1,2,3].map(i => marker('oriel-gslot-' + i));
  return { schemaVersion: 1, observedAt: new Date().toISOString(), tempRoot, benchmark, slots,
    occupiedSlots: slots.filter(s => s.present).length, buildAllowedHint: !benchmark.present && slots.some(s => !s.present),
    consistency: 'non-atomic observation; gws admission must recheck', staleLockDeletionAllowed: false,
    protocol: 'directory markers only; no PID/owner/heartbeat lease metadata', scripts: { gws: path.join(WORKSPACE, 'tools/gws.sh'), benchmark: path.join(WORKSPACE, 'tools/bench-window.sh') } };
}
export async function buildStatus() {
  let tempRoot = '/tmp';
  if (process.platform === 'win32') {
    const result = await run('bash', ['-c', 'cygpath -m /tmp'], { timeout: 10000 });
    requireThat(result.code === 0 && result.output.trim(), 'LOCK_QUERY_FAILED', '无法读取Git Bash /tmp映射。');
    tempRoot = result.output.trim();
  }
  return leaseAt(tempRoot);
}
