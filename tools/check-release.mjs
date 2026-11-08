import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {inflateRawSync} from 'node:zlib';
import {fileURLToPath} from 'node:url';
import {auditMaven} from './check-maven.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const boundaries = JSON.parse(fs.readFileSync(path.join(root, 'tools/release/boundaries.json'), 'utf8'));
const patterns = Object.fromEntries(['development', 'reference', 'metadata'].map(kind => [kind, boundaries[kind].map(p => new RegExp(p))]));
export function classifyEntry(name) {
  for (const kind of ['development', 'reference', 'metadata']) if (patterns[kind].some(p => p.test(name))) return kind;
  return 'core';
}
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
export function coreEntries(entries) {
  return [...entries].filter(([name]) => !name.endsWith('/') && classifyEntry(name) === 'core')
    .sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).map(([name, bytes]) => [name, sha(bytes)]);
}
export function coreHash(entries) {
  return sha(coreEntries(entries).map(([name, hash]) => name + '\0' + hash + '\n').join(''));
}
export function properties(text) {
  return Object.fromEntries(text.split(/\r?\n/).filter(l => l.trim() && !l.trim().startsWith('#')).map(l => {
    const i = l.indexOf('=');
    if (i < 1) throw Error('Invalid property: ' + l);
    return [l.slice(0, i).trim(), l.slice(i + 1).trim()];
  }));
}
export function variantVersions(core, patch) {
  if (!/^\d+\.\d+\.0$/.test(core) || !/^[1-9]\d*$/.test(String(patch))) throw Error('Expected core X.Y.0 and oriel_full_patch >= 1');
  return {slim:core, full:core.replace(/0$/, String(patch))};
}
function compare(a, b) {
  if (![a,b].every(v => /^\d+\.\d+\.\d+$/.test(v))) throw Error('Expected release version X.Y.Z');
  const aa = a.split('.').map(Number), bb = b.split('.').map(Number);
  for (let i = 0; i < 3; i++) if (aa[i] !== bb[i]) return aa[i] < bb[i] ? -1 : 1;
  return 0;
}
export function inRange(version, range) {
  const match = range.match(/^>=([\d.]+) <([\d.]+)$/) || range.match(/^\[([\d.]+),\s*([\d.]+)\)$/);
  if (!match) throw Error('Unsupported release range: ' + range);
  return compare(version, match[1]) >= 0 && compare(version, match[2]) < 0;
}
export function checkCoreBaseline(minor, target, hash, pins, required = false) {
  const key = minor + '.' + target;
  if (pins[key] && pins[key] !== hash) throw Error('核心有变，需要升 minor: ' + key + ' expected=' + pins[key] + ' actual=' + hash);
  if (required && !pins[key]) throw Error('Missing core hash baseline: ' + key + '; validate a slim/full pair with --record-core before release');
}

// Read the central directory: compressed byte counts must come from the JAR, not source sizes.
export function readJar(file) {
  const bytes = fs.readFileSync(file);
  let end = bytes.length - 22;
  while (end >= Math.max(0, bytes.length - 65557) && bytes.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < Math.max(0, bytes.length - 65557)) throw Error('Invalid ZIP: ' + file);
  const count = bytes.readUInt16LE(end + 10);
  let offset = bytes.readUInt32LE(end + 16);
  if (count === 0xffff || offset === 0xffffffff) throw Error('ZIP64 not supported: ' + file);
  const entries = new Map(), sizes = new Map();
  for (let i = 0; i < count; i++) {
    if (bytes.readUInt32LE(offset) !== 0x02014b50) throw Error('Invalid ZIP central directory');
    const method = bytes.readUInt16LE(offset + 10), size = bytes.readUInt32LE(offset + 20);
    const length = bytes.readUInt16LE(offset + 28), extra = bytes.readUInt16LE(offset + 30), comment = bytes.readUInt16LE(offset + 32);
    const name = bytes.toString('utf8', offset + 46, offset + 46 + length), local = bytes.readUInt32LE(offset + 42);
    const start = local + 30 + bytes.readUInt16LE(local + 26) + bytes.readUInt16LE(local + 28);
    if (entries.has(name)) throw Error('Duplicate ZIP entry: ' + name);
    if (!name.endsWith('/')) {
      const data = bytes.subarray(start, start + size);
      if (method !== 0 && method !== 8) throw Error('Unsupported ZIP method: ' + method);
      entries.set(name, method === 0 ? data : inflateRawSync(data));
      sizes.set(name, size);
    }
    offset += 46 + length + extra + comment;
  }
  return {entries, sizes, bytes:bytes.length};
}

// Every retained source resource is required, including each font, icon, Ore texture and service registration.
function requiredCoreResources() {
  const names = new Set(boundaries.requiredCore);
  for (const module of ['kit', 'common', 'mc-extension', 'script-rhino']) {
    const dir = path.join(root, module, 'src/main/resources');
    if (!fs.existsSync(dir)) continue;
    for (const entry of fs.readdirSync(dir, {recursive:true, withFileTypes:true})) if (entry.isFile()) {
      const name = path.relative(dir, path.join(entry.parentPath, entry.name)).replaceAll('\\', '/');
      if (classifyEntry(name) === 'core') names.add(name);
    }
  }
  return [...names];
}
const requiredCore = requiredCoreResources();

// This checks packaging, not a full game launch or arbitrary bytecode linkage.
export function auditEntries(entries, {variant = 'release'} = {}) {
  const files = new Set(entries), failures = [];
  for (const name of ['LICENSE', 'NOTICE']) if (![...files].some(p => p === name || p.startsWith(name + '_'))) failures.push('Missing project ' + name);
  for (const name of ['LICENSE.txt', 'NOTICE.txt', 'SOURCE.txt']) if (!files.has('META-INF/licenses/rhino/' + name)) failures.push('Missing Rhino ' + name);
  if (![...files].some(p => p.startsWith('META-INF/licenses/fonts/') && p.endsWith('.txt'))) failures.push('Missing font licenses');
  for (const p of files) {
    const kind = classifyEntry(p);
    if (kind === 'development' && variant !== 'full') failures.push('Development class in release: ' + p);
    if (kind === 'reference' && variant === 'slim') failures.push('Reference content in slim: ' + p);
    if (p.startsWith('taffy/') || p.includes('/test/resources/')) failures.push('Test fixture in release: ' + p);
  }
  if (variant === 'slim') for (const p of requiredCore) if (!files.has(p)) failures.push('Missing core entry: ' + p);
  return failures;
}
export function jarMetadata(entries) {
  const fabric = entries.get('fabric.mod.json'), neo = entries.get('META-INF/neoforge.mods.toml');
  if (Boolean(fabric) === Boolean(neo)) throw Error('Expected exactly one loader metadata file');
  if (fabric) {
    const data = JSON.parse(fabric.toString());
    return {loader:'fabric', id:data.id, version:data.version};
  }
  const text = neo.toString(), mod = text.split('[[mods]]')[1]?.split(/\[\[/)[0];
  return {loader:'neoforge', id:mod?.match(/^\s*modId\s*=\s*"([^"]+)"/m)?.[1], version:mod?.match(/^\s*version\s*=\s*"([^"]+)"/m)?.[1]};
}
export function auditJar(file, {variant, allowUnrecorded = false} = {}) {
  const jar = readJar(file), meta = jarMetadata(jar.entries);
  variant ??= jar.entries.has('assets/oriel/ui/templates/catalog.json') ? 'full' : 'slim';
  const failures = auditEntries(jar.entries.keys(), {variant});
  if (variant === 'full') for (const p of requiredCore) if (!jar.entries.has(p)) failures.push('Missing core entry: ' + p);
  if (meta.id !== 'oriel') failures.push('Expected mod id oriel, got ' + meta.id);
  const props = properties(fs.readFileSync(path.join(root, 'gradle.properties'), 'utf8'));
  const versions = variantVersions(props.version, props.oriel_full_patch);
  if (meta.version !== versions[variant]) failures.push('Expected ' + variant + ' version ' + versions[variant] + ', got ' + meta.version);
  for (const [loader, resource] of [['fabric','fabric.mod.json'], ['neoforge','META-INF/neoforge.mods.toml']]) {
    const text = fs.readFileSync(path.join(root, 'template', loader, 'src/main/resources', resource), 'utf8');
    const range = loader === 'fabric' ? JSON.parse(text).depends.oriel : text.match(/versionRange\s*=\s*"([^"\n]+)"[\s\S]*?/)?.[1];
    // Locate the oriel dependency specifically, not the Minecraft or loader range.
    const actual = loader === 'neoforge' ? text.split(/modId\s*=\s*"oriel"/)[1]?.match(/versionRange\s*=\s*"([^"]+)"/)?.[1] : range;
    for (const version of Object.values(versions)) if (!actual || !inRange(version, actual)) failures.push(loader + ' template range excludes ' + version);
  }
  const manifest = jar.entries.get('META-INF/MANIFEST.MF')?.toString() ?? '';
  if (/^Oriel-Development-Tools: excluded\r?$/m.test(manifest) && variant === 'full')
    failures.push(...auditEntries(jar.entries.keys()));
  const declaredVariant = manifest.match(/^Oriel-Variant: (.+)\r?$/m)?.[1]?.trim();
  if (declaredVariant && declaredVariant !== variant) failures.push('Variant metadata disagrees with content: ' + declaredVariant);
  const mc = manifest.match(/^Built-On-Minecraft: (.+)\r?$/m)?.[1]?.trim();
  if (!mc) failures.push('Missing Built-On-Minecraft metadata');
  const target = meta.loader + '-' + mc, minor = props.version.split('.').slice(0,2).join('.');
  const hash = coreHash(jar.entries), pinFile = path.join(root, 'tools/release/core-hash.properties');
  const nestedBytes = jar.entries.get('META-INF/oriel/manifest.json');
  if (!nestedBytes) failures.push('Missing Oriel nested manifest');
  else {
    try {
      const nested = JSON.parse(nestedBytes.toString());
      const expectedApi = sha(fs.readFileSync(path.join(root, 'api', (mc === '26.1.2' ? '26.1' : mc) + '.txt')));
      if (nestedBytes.length > 200 * 1024) failures.push('Nested manifest exceeds 200 KiB');
      if (nested.schema !== 1 || nested.version !== meta.version || nested.variant !== variant || nested.loader !== meta.loader || nested.mc !== mc)
        failures.push('Nested manifest distribution metadata mismatch');
      if (nested.coreHash !== hash || nested.apiHash !== expectedApi) failures.push('Nested manifest hash mismatch');
      const groups = new Map(), expected = new Map();
      for (const [name, digest] of coreEntries(jar.entries)) {
        if (!name.endsWith('.class')) expected.set(name, digest);
        else {
          const group = name.slice(0, name.lastIndexOf('/') + 1) + '*.class';
          groups.set(group, (groups.get(group) ?? '') + name + '\0' + digest + '\n');
        }
      }
      for (const [name, sequence] of groups) expected.set(name, sha(sequence));
      if (!Array.isArray(nested.entries) || nested.entries.length !== expected.size
          || new Set(nested.entries.map(e => e.path)).size !== expected.size
          || nested.entries.some(e => expected.get(e.path) !== e.sha256)) failures.push('Nested manifest inventory mismatch');
    } catch (e) { failures.push('Invalid nested manifest: ' + e.message); }
  }
  const pins = properties(fs.readFileSync(pinFile, 'utf8'));
  try { checkCoreBaseline(minor, target, hash, pins, !allowUnrecorded); } catch (e) { failures.push(e.message); }
  return {jar:path.resolve(file), variant, ...meta, target, minor, coreHash:hash, entries:jar.entries.size, bytes:jar.bytes, failures};
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2), record = args.includes('--record-core'), allowUnrecorded = args.includes('--allow-unrecorded');
  const mavenArg = args.find(s => s.startsWith('--maven-dir='));
  const files = args.filter(s => !s.startsWith('--'));
  if ((!files.length && !mavenArg) || (record && !files.length) || args.some(s => s.startsWith('--') && !s.startsWith('--maven-dir=') && !['--record-core','--allow-unrecorded'].includes(s)))
    throw Error('Usage: node tools/check-release.mjs [slim.jar full.jar ...] [--record-core|--allow-unrecorded] [--maven-dir=<local Maven directory>]');
  const maven = mavenArg ? auditMaven(mavenArg.slice('--maven-dir='.length)) : null;
  const results = files.map(file => auditJar(file, {allowUnrecorded: record || allowUnrecorded}));
  for (const slim of results.filter(r => r.variant === 'slim')) for (const full of results.filter(r => r.variant === 'full' && r.target === slim.target)) {
    if (full.coreHash !== slim.coreHash) full.failures.push('Slim/full core hashes differ: ' + slim.target);
    if (compare(full.version, slim.version) <= 0) full.failures.push('Full version must exceed slim');
  }
  if (record) {
    for (const result of results) if (!results.some(other => other.target === result.target && other.variant !== result.variant))
      result.failures.push('--record-core requires both slim and full for ' + result.target);
    if (results.every(r => !r.failures.length) && !maven?.failures.length) {
      const file = path.join(root, 'tools/release/core-hash.properties');
      const pins = properties(fs.readFileSync(file, 'utf8'));
      for (const r of results) pins[r.minor + '.' + r.target] = r.coreHash;
      fs.writeFileSync(file, '# Release core hashes. Never replace an existing minor; bump minor for core changes.\n'
        + Object.entries(pins).sort(([a],[b]) => a.localeCompare(b)).map(([k,v]) => k + '=' + v).join('\n') + '\n');
    }
  }
  console.log(JSON.stringify(results, null, 2));
  if (maven) console.log(JSON.stringify(maven, null, 2));
  process.exitCode = results.some(r => r.failures.length) || maven?.failures.length ? 1 : 0;
}
