import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateActions } from './lib/projects.mjs';
import { rules } from './lib/rules.mjs';

test('input rules expose the same backend defaults and diagnostic contract as the handbook', () => {
  const response = rules({ topic: 'input' });
  assert.equal(response.source, 'docs/zh/d36-input.md');
  assert.ok(response.text.includes('缺省 old'));
  assert.ok(response.text.includes('why-no-click'));
});

test('input diagnostics and composition actions retain the bounded single-line contract', () => {
  assert.doesNotThrow(() => validateActions(['trace-input on', 'preedit zhongwen', 'preedit', 'type 中文', 'why-no-click #submit', 'focus-info #name', 'trace-input off']));
  for (const action of ['trace-input yes', 'trace-input on\nclick #secret', 'why-no-click', 'focus-info', 'shot ../../outside', 'preedit\nkey Enter']) {
    assert.throws(() => validateActions([action]), { code: 'INVALID_ACTION' });
  }
  assert.throws(() => validateActions(Array(101).fill('preedit')), { code: 'INVALID_ACTIONS' });
  assert.throws(() => validateActions(['type ' + 'x'.repeat(2000)]), { code: 'INVALID_ACTION' });
});
