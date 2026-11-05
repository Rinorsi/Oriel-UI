import {execFileSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// This checks packaging, not a full game launch or arbitrary bytecode linkage.
export function auditEntries(entries) {
  const files = new Set(entries);
  const failures = [];
  for (const name of ['LICENSE', 'NOTICE']) {
    if (![...files].some(p => p === name || p.startsWith(name + '_'))) failures.push('Missing project ' + name);
  }
  for (const name of ['LICENSE.txt', 'NOTICE.txt', 'SOURCE.txt']) {
    if (!files.has('META-INF/licenses/rhino/' + name)) failures.push('Missing Rhino ' + name);
  }
  if (![...files].some(p => p.startsWith('META-INF/licenses/fonts/') && p.endsWith('.txt'))) {
    failures.push('Missing font licenses');
  }
  for (const p of files) {
    if (/^dev[/]oriel[/]mod[/]internal[/]client[/](DevAutopilot|DevTour|DevPointer|DevReload|DevAgent|TourCursor|TourPointer|CursorArt)(\$.*)?\.class$/.test(p)) {
      failures.push('Development class in release: ' + p);
    }
    if (p.startsWith('taffy/') || p.includes('/test/resources/')) failures.push('Test fixture in release: ' + p);
  }
  return failures;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 3) throw new Error('Usage: node tools/check-release.mjs <release.jar>');
  const jarTool = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'jar') : 'jar';
  const entries = execFileSync(jarTool, ['tf', path.resolve(process.argv[2])], {encoding: 'utf8'}).split(/\r?\n/).filter(Boolean);
  const failures = auditEntries(entries);
  console.log(JSON.stringify({jar: path.resolve(process.argv[2]), entries: entries.length, failures}, null, 2));
  process.exitCode = failures.length ? 1 : 0;
}
