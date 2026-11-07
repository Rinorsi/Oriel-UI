import test from 'node:test';
import assert from 'node:assert/strict';
import { layoutProbeOptions, layoutProbeRecords } from './lib/layout-probe.mjs';

test('layout probe keeps page/selector as arguments and defaults to old without geometry dump', () => {
  const probe = layoutProbeOptions({ selector: '#panel > .item' }, 'C:/a page.html', 'C:/out.png', '-Xmx1g');
  assert.equal(probe.command[0], 'C:/a page.html');
  assert.equal(probe.command.at(-1), '#panel > .item');
  assert.match(probe.env.JAVA_TOOL_OPTIONS, /-Xmx1g -Doriel.layout.block=old/);
  assert.match(probe.env.JAVA_TOOL_OPTIONS, /-Doriel.layoutGeometry=false$/);
});

test('layout probe rejects invalid backend and budgets', () => {
  for (const args of [{ backend: 'typo' }, { width: 0 }, { frames: 601 }, { height: 1.5 }, { selector: '' }, { geometry: 'true' }]) {
    assert.throws(() => layoutProbeOptions(args, 'page', 'image'));
  }
  assert.match(layoutProbeOptions({ backend: 'both', geometry: true }, 'page', 'image').env.JAVA_TOOL_OPTIONS, /-Doriel.layout.block=both/);
});

test('layout records reject missing or truncated geometry', () => {
  const why = JSON.stringify({ schemaVersion: 1, kind: 'why-size', data: {} });
  const complete = count => JSON.stringify({ schemaVersion: 1, kind: 'layout-complete', data: { whySizeRecords: count, geometryRecords: 0 } });
  assert.equal(layoutProbeRecords(`human text\n${why}\n${complete(1)}\n`).length, 1);
  assert.throws(() => layoutProbeRecords(''));
  assert.throws(() => layoutProbeRecords(`${why}\n{truncated`, true));
  assert.throws(() => layoutProbeRecords(why), 'a missing completion record cannot certify a partial capture');
  assert.throws(() => layoutProbeRecords(`${why}\n${complete(2)}`), 'dropping an earlier record must not pass');
});
