import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {coreEntries, coreHash, readJar, jarMetadata} from '../check-release.mjs';

export const manifestPath = 'META-INF/oriel/manifest.json';
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
export function createManifest(entries, metadata, apiBytes) {
  const resources = [], packages = new Map();
  for (const [name, hash] of coreEntries(entries)) {
    if (!name.endsWith('.class')) resources.push({path:name, sha256:hash});
    else {
      const group = name.slice(0, name.lastIndexOf('/') + 1) + '*.class';
      packages.set(group, (packages.get(group) ?? '') + name + '\0' + hash + '\n');
    }
  }
  for (const [name, sequence] of packages) resources.push({path:name, sha256:sha(sequence)});
  resources.sort((a,b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
  const manifest = {schema:1, ...metadata, coreHash:coreHash(entries), apiHash:sha(apiBytes), entries:resources};
  if (Buffer.byteLength(JSON.stringify(manifest)) > 200 * 1024) throw Error('Oriel manifest exceeds 200 KiB');
  return manifest;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [jarFile, apiFile, output] = process.argv.slice(2);
  const {entries} = readJar(jarFile), meta = jarMetadata(entries);
  const jarManifest = entries.get('META-INF/MANIFEST.MF').toString();
  const mc = jarManifest.match(/^Built-On-Minecraft: (.+)\r?$/m)?.[1].trim();
  const variant = jarManifest.match(/^Oriel-Variant: (.+)\r?$/m)?.[1].trim();
  if (!mc || !['slim','full'].includes(variant)) throw Error('Missing target/variant JAR metadata');
  fs.mkdirSync(path.dirname(output), {recursive:true});
  fs.writeFileSync(output, JSON.stringify(createManifest(entries, {version:meta.version, variant, loader:meta.loader, mc}, fs.readFileSync(apiFile))));
}
