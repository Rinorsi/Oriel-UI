#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { inflateSync, crc32 } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { read, write, check, hash } from './core.mjs';

export const effectIds = ['09', '10', '13', '18', '22', '25', '27', '31', '33', '37', '38', '42', '43', '48', '50'];
// ImageIO RGB/RGBA PNGs only. Fail explicitly on indexed, 16-bit or interlaced input.
export function pixels(buffer) {
  check(buffer.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10])), 'Not PNG');
  let width, height, channels, ended = false;
  const data = [];
  for (let at = 8; at + 12 <= buffer.length;) {
    const length = buffer.readUInt32BE(at), name = buffer.toString('ascii', at + 4, at + 8);
    check(at + 12 + length <= buffer.length, 'Truncated PNG');
    const chunk = buffer.subarray(at + 8, at + 8 + length);
    check(crc32(buffer.subarray(at + 4, at + 8 + length)) === buffer.readUInt32BE(at + 8 + length), 'PNG CRC mismatch');
    if (name === 'IHDR') {
      check(length === 13 && !width, 'Invalid PNG header');
      width = chunk.readUInt32BE(0); height = chunk.readUInt32BE(4); channels = chunk[9] === 6 ? 4 : chunk[9] === 2 ? 3 : 0;
      check(width > 0 && height > 0 && width <= 4096 && height <= 4096 && chunk[8] === 8 && channels && chunk[10] === 0 && chunk[11] === 0 && chunk[12] === 0, 'Unsupported PNG (need non-interlaced RGB/RGBA8 <=4096)');
    } else if (name === 'tRNS') throw new Error('Unsupported PNG transparency chunk; convert to RGBA8');
    else if (name === 'IDAT') data.push(chunk);
    else if (name === 'IEND') { ended = true; break; }
    at += length + 12;
  }
  check(width && ended && data.length, 'Incomplete PNG');
  const stride = width * channels, raw = inflateSync(Buffer.concat(data), { maxOutputLength: (stride + 1) * height });
  check(raw.length === (stride + 1) * height, 'Invalid scanline length');
  const decoded = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)]; check(filter <= 4, 'Unknown PNG filter');
    for (let x = 0; x < stride; x++) {
      const index = y * stride + x, left = x >= channels ? decoded[index - channels] : 0;
      const up = y ? decoded[index - stride] : 0, corner = y && x >= channels ? decoded[index - stride - channels] : 0;
      const p = left + up - corner, a = Math.abs(p - left), b = Math.abs(p - up), c = Math.abs(p - corner);
      const predictor = filter === 0 ? 0 : filter === 1 ? left : filter === 2 ? up : filter === 3 ? Math.floor((left + up) / 2) : a <= b && a <= c ? left : b <= c ? up : corner;
      decoded[index] = (raw[y * (stride + 1) + 1 + x] + predictor) & 255;
    }
  }
  const rgba = channels === 4 ? decoded : Buffer.alloc(width * height * 4);
  if (channels === 3) for (let i = 0, j = 0; i < decoded.length; i += 3, j += 4) { decoded.copy(rgba, j, i, i + 3); rgba[j + 3] = 255; }
  return { width, height, rgba };
}
export function compareEffects(old, candidate, oldRoot, newRoot) {
  const contract = value => {
    check(value.schemaVersion === 1 && value.context && Array.isArray(value.frames), 'Invalid effects manifest');
    for (const key of ['clock', 'seed', 'resourceHash', 'scenarioHash']) check(value.context[key] !== undefined, 'Missing effects context: ' + key);
    const counts = new Map();
    for (const f of value.frames) {
      check(effectIds.includes(f.id) && [427, 640].includes(f.width) && f.height === (f.width === 427 ? 240 : 360), 'Invalid effect/viewport');
      check(['start', 'entrance', 'peak', 'exit'].includes(f.state) && Number.isFinite(f.timeMs) && f.timeMs >= 0 && /^[a-f0-9]{64}$/.test(f.actionsHash ?? '') && /^[a-f0-9]{64}$/.test(f.pageHash ?? ''), 'Frame state/time/action/page hash required');
      const key = `${f.id}/${f.width}/${f.state}`; check(!counts.has(key), 'Duplicate effect frame: ' + key); counts.set(key, f);
    }
    for (const id of effectIds) for (const width of [427, 640]) for (const state of ['start', 'entrance', 'peak', 'exit']) check(counts.has(`${id}/${width}/${state}`), 'Missing effect frame: ' + `${id}/${width}/${state}`);
    return counts;
  };
  const a = contract(old), b = contract(candidate);
  check(JSON.stringify(old.context) === JSON.stringify(candidate.context), 'Effects clock/seed/resource/scenario context differs');
  const frames = [];
  for (const [key, before] of a) {
    const after = b.get(key);
    for (const field of ['timeMs', 'actionsHash', 'pageHash']) check(before[field] === after[field], 'Effects identity differs: ' + key + '/' + field);
    const left = pixels(fs.readFileSync(path.resolve(oldRoot, before.png))), right = pixels(fs.readFileSync(path.resolve(newRoot, after.png)));
    check(left.width === before.width && left.height === before.height && right.width === after.width && right.height === after.height, 'PNG/manifest size differs');
    let changedPixels = 0, maxChannelDelta = 0;
    for (let i = 0; i < left.rgba.length; i += 4) {
      let changed = false;
      for (let c = 0; c < 4; c++) { const delta = Math.abs(left.rgba[i + c] - right.rgba[i + c]); changed ||= delta !== 0; maxChannelDelta = Math.max(maxChannelDelta, delta); }
      if (changed) changedPixels++;
    }
    frames.push({ key, changedPixels, maxChannelDelta, oldRgbaHash: hash(left.rgba), newRgbaHash: hash(right.rgba) });
  }
  return { schemaVersion: 1, verdict: frames.every(f => f.changedPixels === 0) ? 'PASS' : 'FAIL', frames,
    notCovered: ['capture execution and state assertions (producer responsibility)', 'original video equality', 'native item/entity game rendering', 'game GPU/IME/audio'] };
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [oldFile, newFile, output] = process.argv.slice(2);
    check(oldFile && newFile && output && process.argv.length === 5, 'Usage: node tools/bench/effects.mjs old.json new.json external-output.json');
    let ancestor = path.dirname(path.resolve(output));
    while (!fs.existsSync(ancestor)) ancestor = path.dirname(ancestor);
    for (let at = fs.realpathSync(ancestor); ; at = path.dirname(at)) { check(!fs.existsSync(path.join(at, '.git')), 'Output inside repository'); if (at === path.dirname(at)) break; }
    const report = compareEffects(read(oldFile), read(newFile), path.dirname(path.resolve(oldFile)), path.dirname(path.resolve(newFile)));
    write(output, report); console.log(report.verdict + ': ' + output); process.exitCode = report.verdict === 'PASS' ? 0 : 1;
  } catch (error) { console.error(error.message); process.exitCode = 2; }
}
