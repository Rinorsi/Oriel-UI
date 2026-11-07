import fs from 'node:fs';
import path from 'node:path';
import { ROOT, preview, requireThat } from './core.mjs';

export function whyTree(explanation, budget = 200) {
  let remaining = budget, omitted = 0;
  function visit(value, key = 'explanation') {
    if (remaining-- <= 0) { omitted++; return undefined; }
    if (value === null || typeof value !== 'object') return { key, value };
    const children = [];
    for (const [name, item] of Object.entries(value)) {
      if (remaining <= 0) { omitted++; continue; }
      const child = visit(item, name); if (child) children.push(child);
    }
    return { key, children };
  }
  const tree = visit(explanation);
  return { tree, maxNodes: budget, truncated: omitted > 0, omittedBranches: omitted, source: 'engine ' + explanation.kind + '; no inferred reasons' };
}
export function parseWhy(output, maxNodes = 200) {
  const explanations = [];
  let bytes = 0, truncated = false;
  for (const line of output.split(/\r?\n/)) {
    if (!line.startsWith('{')) continue;
    let value; try { value = JSON.parse(line); } catch { requireThat(false, 'INVALID_WHY_JSONL', '选择器解释JSONL损坏。'); }
    if (!['selector-explanation', 'cascade-explanation'].includes(value.kind)) continue;
    if (explanations.length >= 50 || bytes + line.length > 512000) { truncated = true; continue; }
    bytes += line.length; explanations.push(value);
  }
  requireThat(explanations.length, 'WHY_UNAVAILABLE', '当前预览器未返回selector-explanation，需构建并集成R03 --why。');
  const whyTrees = explanations.map(value => whyTree(value, maxNodes));
  return { explanations, whyTrees, rawTruncated: truncated, truncated: truncated || whyTrees.some(tree => tree.truncated),
    coverage: 'selector predicate/candidate/declaration explanation emitted by current engine; not layout constraint tracing' };
}
export async function inspect(args, resolvePage, { runner = preview, source = path.join(ROOT, 'preview/src/main/java/dev/oriel/preview/Preview.java') } = {}) {
  const frames = args.frames ?? 1, maxNodes = args.maxNodes ?? 200;
  requireThat(Number.isInteger(frames) && frames > 0 && frames <= 600 && Number.isInteger(maxNodes) && maxNodes > 0 && maxNodes <= 500, 'INVALID_BUDGET', 'frames为1–600，maxNodes为1–500。');
  requireThat(Boolean(args.selector) !== Boolean(args.property), 'INVALID_INSPECT', 'selector 与 property 必须且只能指定一个。');
  const flag = args.property ? '--why-property' : '--why';
  for (const key of ['page', args.property ? 'property' : 'selector', 'element']) requireThat(typeof args[key] === 'string' && args[key].length > 0 && args[key].length <= 4000, 'INVALID_INSPECT', '缺少或过长参数：' + key);
  requireThat(fs.readFileSync(source, 'utf8').includes('case ' + JSON.stringify(flag)), 'WHY_UNAVAILABLE', '当前工作树未集成R03 --why；不会切换到其他工作树。');
  const result = await runner([resolvePage(args.page), flag, args.property ?? args.selector, args.element, '--frames', String(frames)]);
  const base = { schemaVersion: 1, exitCode: result.code, output: result.output, outputTruncated: result.outputTruncated || false };
  if (result.code !== 0) return { ...base, passed: false, explanations: [], whyTrees: [], code: 'INSPECT_FAILED', message: '预览器选择器解释失败。' };
  try { const parsed = parseWhy(result.output, maxNodes); return { ...base, passed: true, ...parsed, truncated: parsed.truncated || result.outputTruncated || false }; }
  catch (error) { return { ...base, passed: false, explanations: [], whyTrees: [], code: error.code || 'INVALID_WHY_JSONL', message: error.message }; }
}
