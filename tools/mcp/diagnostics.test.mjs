import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { diagnosticSink, logs, validateEvent } from './lib/diagnostics.mjs';
import { parseWhy, whyTree, inspect } from './lib/inspect.mjs';
import { initState, STATE, id, fileHash, saveRecord } from './lib/core.mjs';
import { reviewPage, review } from './lib/web.mjs';
import { McpClient } from './client.mjs';

assert.ok(process.env.ORIEL_TEST_TMP); initState();
const temp = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'diagnostics-'));
test('JSONL reader exposes loss and cursor limits and refuses traversal or corrupt evidence', () => {
  const sink = diagnosticSink(path.join(temp, 'events'), { maxEvents: 3 });
  for (let i = 0; i < 4; i++) sink.emit({ severity: i === 1 ? 'warning' : 'info', code: 'tool.completed', phase: 'tool', message: 'done' });
  const first = logs({ sessionId: sink.sessionId, limit: 2 }, path.join(temp, 'events'));
  assert.equal(first.events.length, 2); assert.equal(first.dropped, 1); assert.equal(first.truncated, true);
  const next = logs({ sessionId: sink.sessionId, after: first.nextCursor }, path.join(temp, 'events'));
  assert.equal(next.events.length, 1); assert.equal(next.events[0].seq, 3);
  assert.equal(logs({ sessionId: sink.sessionId, levels: ['warning'] }, path.join(temp, 'events')).events.length, 1);
  assert.throws(() => logs({ sessionId: '../escape' }, path.join(temp, 'events')), /ID/);
  assert.throws(() => validateEvent({}), /v1/);
  fs.appendFileSync(path.join(temp, 'events', sink.sessionId + '.jsonl'), '{bad}\n');
  assert.throws(() => logs({ sessionId: sink.sessionId }, path.join(temp, 'events')), /损坏/);
});
test('why trees preserve engine evidence, budget truncation and JSONL errors are explicit', () => {
  const explanation = { kind: 'selector-explanation', queryResults: [{ selector: '.a:hover', matched: false, trace: ['hover=false'] }], declarationWinners: [] };
  const result = parseWhy(JSON.stringify(explanation));
  assert.deepEqual(result.explanations[0], explanation); assert.equal(result.whyTrees[0].truncated, false);
  assert.equal(whyTree(explanation, 2).truncated, true);
  assert.equal(parseWhy(JSON.stringify(explanation), 2).truncated, true);
  assert.throws(() => parseWhy('{broken'), /JSONL/); assert.throws(() => parseWhy('no-json'), /R03/);
});
test('inspect uses R03 CLI arguments and preserves success/failure receipt fields', async () => {
  const source=path.join(temp,'Preview.java'); fs.writeFileSync(source,'case "--why"');
  const args={page:'test',selector:'.a:hover',element:'#x',frames:3};
  const value=await inspect(args,()=>'/page',{source,runner:async argv=>{assert.deepEqual(argv,['/page','--why','.a:hover','#x','--frames','3']);return {code:0,output:JSON.stringify({kind:'selector-explanation',queryResults:[]}),outputTruncated:true};}});
  assert.equal(value.passed,true); assert.equal(value.truncated,true); assert.equal(value.explanations.length,1);
  const failure=await inspect(args,()=>'/page',{source,runner:async()=>({code:2,output:'bad selector'})});
  assert.equal(failure.passed,false); assert.equal(failure.exitCode,2); assert.equal(failure.output,'bad selector'); assert.deepEqual(failure.explanations,[]);
});

test('inspect preserves R04 property evidence and rejects ambiguous requests', async () => {
  const source = path.join(temp, 'PropertyPreview.java');
  fs.writeFileSync(source, 'case "--why-property"');
  const args = { page: 'test', property: 'color', element: '#x' };
  const explanation = { kind: 'cascade-explanation', property: 'color' };
  const value = await inspect(args, () => '/page', { source, runner: async argv => {
    assert.deepEqual(argv, ['/page', '--why-property', 'color', '#x', '--frames', '1']);
    return { code: 0, output: JSON.stringify(explanation) };
  } });
  assert.equal(value.passed, true);
  assert.deepEqual(value.explanations, [explanation]);
  assert.equal(value.whyTrees.length, 1);
  await assert.rejects(inspect({ ...args, selector: '#x' }, () => '/page', { source }), error => error.code === 'INVALID_INSPECT');
});
test('32 screenshot reviews expose pages and cannot approve missing pages even with duplicate image hashes', async () => {
  const image = path.join(temp, 'shot.png'); fs.writeFileSync(image, 'test image bytes');
  const item = { id: id('review'), mode: 'screenshots', decision: 'pending', revisionId: 'not-a-real-revision', shots: Array.from({ length: 32 }, (_, i) => ({ scene: 's' + i, image, hash: fileHash(image) })) };
  saveRecord('reviews', item);
  const first = reviewPage(item); assert.equal(first.imagePaths.length, 12); assert.equal(first.pagination.remaining, 20); assert.equal(first.pagination.complete, false);
  await assert.rejects(review({ action: 'decide', reviewId: item.id, decision: 'approved', quote: '同意' }), /还有截图未返回/);
  const second = reviewPage(item, 12); assert.equal(second.pagination.remaining, 8);
  const last = reviewPage(item, 24); assert.equal(last.imagePaths.length, 8); assert.equal(last.pagination.complete, true);
  assert.throws(() => reviewPage(item, 32), /offset/); assert.throws(() => reviewPage(item, 0, 13), /limit/);
  fs.writeFileSync(image, 'changed'); assert.throws(() => reviewPage(item), /变化或缺失/);
});
test('MCP logs record tool names without retaining argument payloads', async () => {
  const client = new McpClient({ ORIEL_MCP_DIAGNOSTICS: '1' });
  try {
    await client.initialize(); await client.call('oriel_capabilities');
    const list = await client.call('oriel_logs'); assert.equal(list.isError, undefined);
    const sessionId = list.structuredContent.currentSessionId;
    const result = await client.call('oriel_logs', { sessionId }); assert.equal(result.isError, undefined);
    assert.ok(result.structuredContent.events.some(e => e.data.tool === 'oriel_capabilities'));
    assert.ok(result.structuredContent.events.every(e => !Object.hasOwn(e.data, 'arguments')));
  } finally { client.close(); }
});
test('tool diagnostic recording stays disabled by default', async () => {
  const client = new McpClient({ ORIEL_MCP_DIAGNOSTICS: '0' });
  try { await client.initialize(); const result = await client.call('oriel_logs'); assert.equal(result.structuredContent.recordingEnabled, false); assert.equal(result.structuredContent.currentSessionId, null); }
  finally { client.close(); }
});
