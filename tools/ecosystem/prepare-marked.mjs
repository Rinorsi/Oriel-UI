import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const args = process.argv.slice(2);
const option = name => args[args.indexOf(name) + 1];
if (!args.includes('--libs') || !args.includes('--esbuild')) {
  throw Error('Usage: prepare-marked.mjs --libs <prepared-external-vendor-folder> --esbuild <esbuild/lib/main.js>');
}
const repo = await fs.realpath(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..'));
const output = await fs.realpath(path.resolve(option('--libs')));
const relative = path.relative(repo, output);
if (!relative || relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative)) {
  throw Error('Libraries and output must be external');
}
const manifest = JSON.parse(await fs.readFile(new URL('./libraries.json', import.meta.url), 'utf8'));
const asset = manifest.find(library => library.name === 'marked').assets[0];
const source = await fs.readFile(path.join(output, asset.local));
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
if (source.length !== asset.bytes || sha256(source) !== asset.sha256) throw Error('Pinned marked fixture hash mismatch');
const esbuild = await import(pathToFileURL(path.resolve(option('--esbuild'))).href);
// Lower private fields and rest arrow functions; preserve the original UMD global.
const result = await esbuild.transform(source.toString('utf8'), {target:'es2015', supported:{arrow:false}});
const bytes = Buffer.from(result.code);
await fs.writeFile(path.join(output, 'marked-compat.js'), bytes);
const receipt = {library:'marked 12.0.2', license:'MIT', input:asset.sha256, esbuild:esbuild.version,
  target:'es2015', supported:{arrow:false}, bytes:bytes.length, sha256:sha256(bytes)};
await fs.writeFile(path.join(output, 'marked-receipt.json'), JSON.stringify(receipt, null, 2));
console.log(JSON.stringify(receipt));
