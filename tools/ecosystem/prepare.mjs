import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const args = process.argv.slice(2);
const option = name => args[args.indexOf(name) + 1];
if (!args.includes('--out')) throw Error('Usage: node tools/ecosystem/prepare.mjs --out <external-folder> [--cache <verified-libs-folder>]');
const output = path.resolve(option('--out'));
await fs.mkdir(output, {recursive:true});
const canonical = await fs.realpath(output);
const canonicalRepo = await fs.realpath(repo);
const relative = path.relative(canonicalRepo, canonical);
if (!relative || relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative)) {
  throw Error('External output is required: third-party libraries and run assets must stay outside the repository');
}
const manifest = JSON.parse(await fs.readFile(new URL('./libraries.json', import.meta.url), 'utf8'));
const libraryDir = path.join(output, 'assets/ecosystem/ui/vendor');
await fs.mkdir(libraryDir, {recursive:true});
for (const library of manifest) {
  for (const asset of library.assets) {
    const bytes = args.includes('--cache')
      ? await fs.readFile(path.join(path.resolve(option('--cache')), asset.local))
      : Buffer.from(await (async () => {
          const response = await fetch(asset.url, {signal:AbortSignal.timeout(60_000)});
          if (!response.ok) throw Error(`${response.status}: ${asset.url}`);
          return response.arrayBuffer();
        })());
    const hash = createHash('sha256').update(bytes).digest('hex');
    if (bytes.length !== asset.bytes || hash !== asset.sha256) throw Error(`Fixture hash mismatch: ${asset.local}`);
    await fs.writeFile(path.join(libraryDir, asset.local), bytes);
  }
}
await fs.cp(path.join(repo, 'tools/mcp/examples/ecosystem'), path.join(output, 'assets/ecosystem/ui'), {recursive:true});
await fs.writeFile(path.join(output, 'libraries.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify({output, libraries:libraryDir, pageRoot:path.join(output, 'assets/ecosystem/ui'),
  runtimeOptions:['-Doriel.ecosystem=new'],
  note:'GSAP 3.12.5 uses its custom license; all downloaded builds stay external. Review upstream licenses before redistribution.'}));
