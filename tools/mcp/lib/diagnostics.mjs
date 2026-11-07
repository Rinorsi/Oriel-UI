import fs from 'node:fs';
import path from 'node:path';
import { STATE, safePath, identifier, id, now, writeJson, readJson, requireThat } from './core.mjs';

export const severities = ['info', 'warning', 'error'];
export function validateEvent(event) {
  requireThat(event?.schemaVersion === 1 && typeof event.sessionId === 'string' && Number.isSafeInteger(event.seq) && event.seq > 0 &&
    typeof event.time === 'string' && !Number.isNaN(Date.parse(event.time)) && severities.includes(event.severity) && typeof event.code === 'string' &&
    typeof event.phase === 'string' && typeof event.message === 'string' && typeof event.environment === 'string' &&
    (event.requestId === undefined || event.requestId === null || typeof event.requestId === 'string') &&
    (event.durationMs === undefined || (Number.isFinite(event.durationMs) && event.durationMs >= 0)) &&
    (event.source === undefined || event.source === null || typeof event.source === 'object' && !Array.isArray(event.source)) &&
    (event.data === undefined || event.data && typeof event.data === 'object' && !Array.isArray(event.data)), 'INVALID_DIAGNOSTIC', 'JSONL诊断信封不符合v1合同。');
  return event;
}
export function diagnosticSink(directory = path.join(STATE, 'diagnostics'), { maxEvents = 10000, maxBytes = 8 * 1024 * 1024 } = {}) {
  fs.mkdirSync(directory, { recursive: true });
  const sessionId = id('diag'), file = path.join(directory, sessionId + '.jsonl'), meta = path.join(directory, sessionId + '.json');
  let seq = 0, bytes = 0, dropped = 0;
  return { sessionId, emit(fields) {
    const event = validateEvent({ ...fields, schemaVersion: 1, sessionId, seq: seq + 1, time: now(), environment: 'mcp' });
    const line = JSON.stringify(event) + '\n', size = Buffer.byteLength(line);
    if (seq >= maxEvents || bytes + size > maxBytes) dropped++;
    else { fs.appendFileSync(file, line); seq++; bytes += size; }
    writeJson(meta, { schemaVersion: 1, sessionId, stored: seq, bytes, dropped, updatedAt: now(), scope: 'MCP tool events; arguments and outputs are not recorded' });
  } };
}
export function logs({ sessionId, after = 0, limit = 100, levels = severities } = {}, directory = path.join(STATE, 'diagnostics')) {
  fs.mkdirSync(directory, { recursive: true });
  if (!sessionId) {
    const sessions = fs.readdirSync(directory).filter(f => /^diag-[a-z0-9-]+\.json$/.test(f)).map(f => readJson(safePath(directory, f))).sort((a,b) => b.updatedAt.localeCompare(a.updatedAt));
    return { schemaVersion: 1, sessions: sessions.slice(0,100), totalSessions: sessions.length, sessionsTruncated: sessions.length > 100, scope: 'MCP tool events only' };
  }
  identifier(sessionId);
  requireThat(Number.isSafeInteger(after) && after >= 0 && Number.isInteger(limit) && limit > 0 && limit <= 200 && Array.isArray(levels) && levels.every(v => severities.includes(v)), 'INVALID_CURSOR', 'after为非负整数，limit为1–200，levels为info/warning/error。');
  const metaFile = safePath(directory, sessionId + '.json');
  requireThat(fs.existsSync(metaFile), 'NOT_FOUND', '诊断会话不存在。');
  const meta = readJson(metaFile), file = safePath(directory, sessionId + '.jsonl');
  requireThat(!fs.existsSync(file) || fs.statSync(file).size <= 8 * 1024 * 1024, 'DIAGNOSTIC_TOO_LARGE', '诊断文件超过8MiB。');
  const all = fs.existsSync(file) ? fs.readFileSync(file, 'utf8').split('\n').filter(Boolean).map(line => { try { return validateEvent(JSON.parse(line)); } catch { requireThat(false, 'INVALID_DIAGNOSTIC', 'JSONL存在损坏或未知合同事件，不能当作完整证据。'); } }) : [];
  requireThat(all.every((event, index) => event.sessionId === sessionId && event.seq === index + 1), 'INVALID_DIAGNOSTIC', '会话或递增序号不一致。');
  requireThat(meta.stored === all.length && Number.isSafeInteger(meta.dropped) && meta.dropped >= 0, 'INVALID_DIAGNOSTIC', '诊断元数据与事件计数不一致。');
  const matching = all.filter(event => event.seq > after && levels.includes(event.severity)), events = matching.slice(0, limit);
  return { schemaVersion: 1, sessionId, events, nextCursor: events.at(-1)?.seq ?? after, remaining: Math.max(0, matching.length - events.length), dropped: meta.dropped, truncated: matching.length > events.length || meta.dropped > 0, scope: meta.scope };
}
