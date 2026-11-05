import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';

// Read only: never refresh verification metadata or read the local dependency cache.
export function verifyCentral(xml, coordinates, fetchArtifact) {
  if (!coordinates.length) throw new Error('Specify exact group:module:version coordinates');
  const attribute = (text, key) => text.match(new RegExp(`\\b${key}=['"]([^'"]+)['"]`))?.[1];
  const rows = [];
  for (const coordinate of coordinates) {
    const parts = coordinate.split(':');
    if (parts.length !== 3 || parts.some(p => !/^[A-Za-z0-9_][A-Za-z0-9_.-]*$/.test(p) || p.includes('..')))
      throw new Error(`Invalid coordinate: ${coordinate}`);
    const [group, name, version] = parts;
    const component = [...xml.matchAll(/<component\b([^>]*)>([\s\S]*?)<\/component>/g)]
      .find(m => attribute(m[1], 'group') === group && attribute(m[1], 'name') === name && attribute(m[1], 'version') === version);
    if (!component) throw new Error(`Coordinate not pinned: ${coordinate}`);
    const artifacts = [...component[2].matchAll(/<artifact\b([^>]*)>([\s\S]*?)<\/artifact>/g)];
    if (!artifacts.length) throw new Error(`No pinned artifacts: ${coordinate}`);
    for (const artifact of artifacts) {
      const file = attribute(artifact[1], 'name');
      if (!file || !/^[A-Za-z0-9_][A-Za-z0-9_.-]*$/.test(file) || file.includes('..'))
        throw new Error(`Invalid artifact: ${file}`);
      const expected = [...artifact[2].matchAll(/<sha256\b([^>]*)\/?>/g)].map(m => attribute(m[1], 'value'));
      if (!expected.length || expected.some(h => !/^[a-f0-9]{64}$/.test(h ?? '')))
        throw new Error(`No SHA-256 pin: ${coordinate}/${file}`);
      const url = `https://repo.maven.apache.org/maven2/${group.replaceAll('.', '/')}/${name}/${version}/${file}`;
      const sha256 = createHash('sha256').update(fetchArtifact(url)).digest('hex');
      if (!expected.includes(sha256))
        throw new Error(`SHA-256 mismatch: ${url} expected=${expected.join(',')} actual=${sha256}`);
      rows.push({coordinate, file, url, sha256});
    }
  }
  return rows;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const xml = fs.readFileSync(new URL('../gradle/verification-metadata.xml', import.meta.url), 'utf8');
    const rows = verifyCentral(xml, process.argv.slice(2), url => {
      const args = ['--fail', '--silent', '--show-error', '--proto', '=https', '--max-time', '60'];
      if (process.env.ORIEL_VERIFY_PROXY) args.push('--proxy', process.env.ORIEL_VERIFY_PROXY);
      args.push(url);
      return execFileSync('curl', args, {maxBuffer: 64 * 1024 * 1024, timeout: 65000});
    });
    console.log(JSON.stringify({source: 'Maven Central HTTPS', checkedAt: new Date().toISOString(), artifacts: rows}, null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
