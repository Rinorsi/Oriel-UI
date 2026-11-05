import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const digest = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
export function auditAssets(root, manifest, tracked, requireCleared = false) {
  const errors = [], listed = new Set();
  const assets = tracked.filter(p => /\.(png|svg|jpe?g|webp|ttf|otf|woff2?)$/.test(p));
  for (const entry of manifest.entries) {
    if (listed.has(entry.path)) errors.push('Duplicate asset: ' + entry.path);
    listed.add(entry.path);
    if (!assets.includes(entry.path)) { errors.push('Untracked asset: ' + entry.path); continue; }
    if (entry.shipped !== entry.path.includes('/src/main/resources/')) errors.push('Incorrect shipping classification: ' + entry.path);
    if (!fs.existsSync(path.join(root, entry.path)) || digest(path.join(root, entry.path)) !== entry.sha256)
      errors.push('Changed or missing asset: ' + entry.path);
    if (!['license-documented', 'needs-user-confirmation'].includes(entry.status)) errors.push('Unknown status: ' + entry.path);
    if (entry.status === 'license-documented' && (!entry.source || !entry.license || !entry.licenseFiles?.length))
      errors.push('Missing license evidence: ' + entry.path);
    for (const license of entry.licenseFiles || []) if (!tracked.includes(license) || !fs.existsSync(path.join(root, license)))
      errors.push('Missing license file: ' + license);
    if (requireCleared && entry.path.includes('/src/main/resources/') && entry.status !== 'license-documented') errors.push('Needs user confirmation: ' + entry.path);
  }
  for (const asset of assets) if (!listed.has(asset)) errors.push('Unregistered asset: ' + asset);
  for (const entry of manifest.licenseFiles) {
    if (!tracked.includes(entry.path) || !fs.existsSync(path.join(root, entry.path)) || digest(path.join(root, entry.path)) !== entry.sha256)
      errors.push('Changed or missing license: ' + entry.path);
  }
  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.slice(2).some(arg => arg !== '--require-cleared')) throw Error('Usage: node tools/check-asset-provenance.mjs [--require-cleared]');
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'docs/asset-provenance.json'), 'utf8'));
  const tracked = execFileSync('git', ['ls-files'], {cwd: root, encoding: 'utf8'}).trim().split('\n');
  const errors = auditAssets(root, manifest, tracked, process.argv.includes('--require-cleared'));
  const pending = manifest.entries.filter(e => e.shipped && e.status === 'needs-user-confirmation').length;
  for (const error of errors) console.error(error);
  console.log('Asset inventory: ' + manifest.entries.length + '; shipped assets needing user confirmation: ' + pending + '. Inventory checks do not grant distribution rights.');
  process.exitCode = errors.length ? 1 : 0;
}
