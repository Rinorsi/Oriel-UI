import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {auditAssets} from './check-asset-provenance.mjs';

test('asset inventory rejects stale bytes, missing registrations and uncleared shipping assets', () => {
  assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to an external directory');
  const root = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'assets-'));
  const asset = 'kit/src/main/resources/asset.png';
  fs.mkdirSync(path.dirname(path.join(root, asset)), {recursive: true});
  fs.writeFileSync(path.join(root, asset), 'fixture');
  const entry = {path: asset, sha256: crypto.createHash('sha256').update('fixture').digest('hex'), shipped: true, status: 'needs-user-confirmation'};
  const manifest = {entries: [entry], licenseFiles: []};
  assert.deepEqual(auditAssets(root, manifest, [asset]), []);
  assert.match(auditAssets(root, manifest, [asset], true).join(), /Needs user confirmation/);
  entry.shipped = false;
  assert.match(auditAssets(root, manifest, [asset], true).join(), /Incorrect shipping classification/);
  entry.shipped = true;
  assert.match(auditAssets(root, manifest, [asset, 'new.svg']).join(), /Unregistered asset/);
  fs.writeFileSync(path.join(root, asset), 'changed');
  assert.match(auditAssets(root, manifest, [asset]).join(), /Changed or missing asset/);
});

test('documented licenses require evidence and retain license bytes', () => {
  assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to an external directory');
  const root = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'licenses-'));
  const asset = 'kit/src/main/resources/font.ttf';
  fs.mkdirSync(path.dirname(path.join(root, asset)), {recursive: true});
  fs.writeFileSync(path.join(root, asset), 'fixture');
  fs.writeFileSync(path.join(root, 'OFL.txt'), 'license');
  const hash = text => crypto.createHash('sha256').update(text).digest('hex');
  const entry = {path: asset, sha256: hash('fixture'), shipped: true, status: 'license-documented'};
  const manifest = {entries: [entry], licenseFiles: [{path: 'OFL.txt', sha256: hash('license')}]};
  const tracked = [asset, 'OFL.txt'];
  assert.match(auditAssets(root, manifest, tracked).join(), /Missing license evidence/);
  Object.assign(entry, {source: 'https://example.org/font', license: 'OFL-1.1', licenseFiles: ['OFL.txt']});
  assert.deepEqual(auditAssets(root, manifest, tracked, true), []);
  fs.writeFileSync(path.join(root, 'OFL.txt'), 'changed');
  assert.match(auditAssets(root, manifest, tracked).join(), /Changed or missing license/);
});
