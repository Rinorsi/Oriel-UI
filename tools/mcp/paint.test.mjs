import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectPaint } from './lib/paint.mjs';

test('paint inspection rejects malformed requests before running a preview', async () => {
  const never = () => { throw new Error('must not resolve a page'); };
  for (const args of [{}, { page: 'x', width: 1.5 }, { page: 'x', frames: 0 },
    { page: 'x', height: 1081 }, { page: 'x', element: {} }]) {
    await assert.rejects(inspectPaint(args, never), e => ['PAGE_REQUIRED', 'INVALID_SIZE', 'INVALID_ELEMENT'].includes(e.code));
  }
});
