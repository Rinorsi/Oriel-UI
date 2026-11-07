import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const args = process.argv.slice(2);
const option = name => args[args.indexOf(name) + 1];
for (const flag of ['--modules','--esbuild','--out']) {
  if (!args.includes(flag)) throw Error('Usage: bundle-compute.mjs --modules <external-node_modules> --esbuild <esbuild/lib/main.js> --out <external-vendor-folder>');
}
const repo = await fs.realpath(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..'));
const output = await fs.realpath(path.resolve(option('--out')));
const relative = path.relative(repo, output);
if (!relative || relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative)) throw Error('Output must be external');
const modules = path.resolve(option('--modules'));
const expected = {nanostores:'0.11.3', 'd3-scale':'4.0.2', 'd3-shape':'3.2.0'};
for (const [name,version] of Object.entries(expected)) {
  const pkg = JSON.parse(await fs.readFile(path.join(modules,name,'package.json'),'utf8'));
  if (pkg.version !== version) throw Error('Unexpected package version: '+name+'@'+pkg.version);
}
const esbuild = await import(pathToFileURL(path.resolve(option('--esbuild'))).href);
const sources = {
  'nano-compute': 'import {atom} from "nanostores"; window.nanoAtom=atom;',
  'd3-compute': 'import {scaleLinear} from "d3-scale"; import {line} from "d3-shape"; window.d3ScaleLinear=scaleLinear; window.d3Line=line;'
};
const receipts = [];
for (const [name,contents] of Object.entries(sources)) {
  const result = await esbuild.build({stdin:{contents,resolveDir:path.dirname(modules)},
    nodePaths:[modules],bundle:true,write:false,format:'iife',target:'es2015'});
  const bytes = result.outputFiles[0].contents;
  await fs.writeFile(path.join(output,name+'.js'),bytes);
  receipts.push({name,dependencies:expected,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex'),
    licenses:name==='nano-compute'?'MIT':'ISC (d3-scale, d3-shape and dependencies)'});
}
await fs.writeFile(path.join(output,'compute-receipt.json'),JSON.stringify(receipts,null,2)+'\n');
console.log(JSON.stringify(receipts));
