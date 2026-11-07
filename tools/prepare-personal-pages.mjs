import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';

function within(root, target) {
  const rel = path.relative(root, target);
  return rel && !rel.startsWith('..') && !path.isAbsolute(rel);
}
function assertPlainTree(dir) {
  if (fs.lstatSync(dir).isSymbolicLink()) throw Error('Refusing linked directory: ' + dir);
  for (const e of fs.readdirSync(dir, {withFileTypes:true})) {
    const f = path.join(dir, e.name);
    if (e.isSymbolicLink()) throw Error('Refusing linked file: ' + f);
    if (e.isDirectory()) assertPlainTree(f);
  }
}
function digestTree(dir, prefix = '') {
  return fs.readdirSync(dir, {withFileTypes:true}).flatMap(e => {
    const f = path.join(dir, e.name), name = prefix + e.name;
    return e.isDirectory() ? digestTree(f, name + '/') : [{file:name,sha256:crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex')}];
  }).sort((a,b) => a.file.localeCompare(b.file));
}
function keep(dir) {
  const file = path.join(dir, 'KEEP');
  if (!fs.existsSync(file)) fs.writeFileSync(file, 'Contains personal UI pages. Preserve during daily cleanup; archive/ui-templates contains independent snapshots.\n', {flag:'wx'});
}

function pageFolders(dir, prefix = '', depth = 0) {
  const pages = [];
  for (const e of fs.readdirSync(dir, {withFileTypes:true})) {
    if (!e.isDirectory() || !/^[a-z0-9_-]+$/.test(e.name)) continue;
    const child = path.join(dir, e.name), name = prefix + e.name;
    if (fs.existsSync(path.join(child, 'index.html'))) pages.push({name});
    else if (depth < 3) pages.push(...pageFolders(child, name + '/', depth + 1));
  }
  return pages;
}

// Seed a new dated client before launch. Existing page folders are never overwritten.
// No watcher, no game restart, and no changes to the library or official demo pages.
export function preparePersonalPages({workspace, runRoot, target}) {
  workspace = fs.realpathSync(workspace);
  runRoot = path.resolve(runRoot);
  if (!/^(fabric|neoforge)-26\.[123]$/.test(target)) throw Error('Invalid client target');
  const daily = path.join(workspace, 'daily'), rel = path.relative(daily, runRoot).split(path.sep);
  if (!within(daily, runRoot) || !/^\d{4}-\d{2}-\d{2}$/.test(rel[0])) throw Error('Run directory must be inside a dated workspace daily folder');
  const destDay = path.join(daily, rel[0]);
  fs.mkdirSync(runRoot, {recursive:true});
  if (!within(fs.realpathSync(daily), fs.realpathSync(runRoot))) throw Error('Run directory resolves outside daily');
  const destination = path.join(runRoot, target, 'client/config/oriel/my-pages');
  fs.mkdirSync(destination, {recursive:true});
  if (!within(fs.realpathSync(daily), fs.realpathSync(destination))) throw Error('Page directory resolves outside daily');
  assertPlainTree(destination);
  const knownFile = new URL('./templates/previous-personal-pages.json', import.meta.url);
  const known = fs.existsSync(knownFile) ? JSON.parse(fs.readFileSync(knownFile, 'utf8')) : {};
  const sources = [];
  for (const day of fs.readdirSync(daily).filter(d=>/^\d{4}-\d{2}-\d{2}$/.test(d)).sort().reverse()) {
    const dir = path.join(daily, day, 'run', target, 'client/config/oriel/my-pages');
    if (path.resolve(dir) !== path.resolve(destination) && fs.existsSync(dir)) sources.push({dir, day:path.join(daily,day)});
  }
  const archive = path.join(workspace,'archive/ui-templates'), latest = path.join(archive,'LATEST.json');
  if (fs.existsSync(latest)) {
    const snapshot = path.resolve(JSON.parse(fs.readFileSync(latest,'utf8')).snapshot);
    if (!within(archive,snapshot) || !within(fs.realpathSync(archive),fs.realpathSync(snapshot))) throw Error('Snapshot must stay inside archive/ui-templates');
    sources.push({dir:path.join(snapshot,'my-pages')});
  }
  const restored = [], preserved = new Set(), seen = new Set();
  keep(destDay);
  for (const source of sources) {
    if (!fs.existsSync(source.dir)) continue;
    assertPlainTree(source.dir);
    for (const e of pageFolders(source.dir)) {
      const from=path.join(source.dir,e.name), to=path.join(destination,e.name);
      if (!fs.existsSync(path.join(from,'index.html'))) continue;
      if (seen.has(e.name)) continue;
      seen.add(e.name);
      if (fs.existsSync(to)) {preserved.add(e.name);continue;}
      const before=digestTree(from);
      // Packaged official originals no longer need restoring; preserve modified personal copies.
      if (known[e.name] === crypto.createHash('sha256').update(JSON.stringify(before)).digest('hex')) continue;
      fs.mkdirSync(path.dirname(to), {recursive:true});
      const temp=path.join(destination,'.incoming-'+crypto.randomUUID());
      fs.cpSync(from,temp,{recursive:true,errorOnExist:true,force:false});
      const after=digestTree(temp);
      if (JSON.stringify(before)!==JSON.stringify(after)) throw Error('Copy changed during verification: '+from+'; temporary copy retained at '+temp);
      // A concurrent creator must not be replaced, even with an empty directory.
      if (fs.existsSync(to)) throw Error('Destination appeared during copy; preserving both: '+to);
      fs.renameSync(temp,to);
      if(source.day)keep(source.day);
      restored.push({slug:e.name,source:from,destination:to,files:after});
    }
  }
  return {destination,restoredPages:restored.length,preservedPages:[...preserved],restored};
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const opts={};for(let i=2;i<process.argv.length;i+=2)opts[process.argv[i].replace(/^--/,'')]=process.argv[i+1];
  const receipt=preparePersonalPages({workspace:opts.workspace,runRoot:opts['run-root'],target:opts.target});
  if(opts.report){fs.mkdirSync(path.dirname(opts.report),{recursive:true});fs.writeFileSync(opts.report,JSON.stringify(receipt,null,2));}
  console.log(JSON.stringify({destination:receipt.destination,restoredPages:receipt.restoredPages,preservedPages:receipt.preservedPages.length}));
}
