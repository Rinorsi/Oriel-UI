import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { capabilities, leaseAt } from './lib/contracts.mjs';
import { McpClient } from './client.mjs';
import { schedulerArguments, run } from './lib/core.mjs';

assert.ok(process.env.ORIEL_TEST_TMP);
const temp = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'contracts-'));
test('gws already supplies plain console; forwarding ensurePreview does not duplicate singleton option', () => {
  assert.deepEqual(schedulerArguments([':preview:installDist','--console=plain','--offline']), [':preview:installDist','--offline']);
  assert.deepEqual(schedulerArguments(['--version']), ['--version']);
});
test('bounded subprocess output explicitly signals truncation without changing legacy fields', async () => {
  const result = await run(process.execPath, ['-e','process.stdout.write("x".repeat(2000001))']);
  assert.equal(result.code, 0); assert.equal(result.output.length, 2000000); assert.equal(result.outputTruncated, true);
});
test('versioned capability discovery retains tool schemas and rejects unknown contracts', () => {
  const tools = [{ name: 'one', inputSchema: { type: 'object' }, run() {} }];
  const value = capabilities(tools);
  assert.equal(value.schemaVersion, 1); assert.equal(value.tools[0].name, 'one');
  assert.equal(value.tools[0].run, undefined); assert.equal(value.features.liveGameAttach.status, 'unsupported');
  assert.throws(() => capabilities(tools, { contractVersion: 2 }), /contractVersion/);
});
test('build marker state is observational, never infers owner or frees unknown directories', () => {
  fs.mkdirSync(path.join(temp, 'oriel-gradle.lock')); fs.mkdirSync(path.join(temp, 'oriel-gslot-2'));
  const value = leaseAt(temp); assert.equal(value.occupiedSlots, 1);
  assert.equal(value.buildAllowedHint, false); assert.equal(value.benchmark.owner, null);
  assert.equal(value.staleLockDeletionAllowed, false); assert.ok(fs.existsSync(path.join(temp, 'oriel-gradle.lock')));
});
test('real MCP discovery describes exactly the live tool registry without starting Java', async () => {
  const client = new McpClient();
  try {
    await client.initialize();
    const tools = await client.request('tools/list'); const result = await client.call('oriel_capabilities');
    assert.equal(result.isError, undefined);
    assert.deepEqual(result.structuredContent.tools.map(t => t.name), tools.tools.map(t => t.name));
    const status = await client.call('oriel_build_status');
    assert.equal(status.isError, undefined, JSON.stringify(status)); assert.equal(status.structuredContent.staleLockDeletionAllowed, false);
  } finally { client.close(); }
});
