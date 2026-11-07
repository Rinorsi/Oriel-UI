import fs from 'node:fs';
import path from 'node:path';
import { check, write, hash } from './core.mjs';

// Deterministic parser/matcher/style invalidation stress pages; not representative game scenes.
export function synthetic(elements, rules, kind) {
  check([1000, 5000, 20000].includes(elements) && [200, 1000, 5000].includes(rules) && ['class', 'hover', 'attribute'].includes(kind), 'Unsupported synthetic dimensions');
  const css = Array.from({ length: rules }, (_, i) => {
    const selector = kind === 'attribute' ? `[data-bench="v${i}"]` : '.c' + i + (kind === 'hover' ? ':hover' : '');
    return selector + '{opacity:' + (i % 2 ? '.9' : '1') + '}';
  }).join('\n');
  const dom = Array.from({ length: elements }, (_, i) => `<div class="cell c${i % rules}" data-bench="v${i % rules}"></div>`).join('');
  // One shared animation invalidates inherited style without creating one loop per node.
  return '<!doctype html><html><head><style>.cell{width:1px;height:1px;display:inline-block}\n' + css + '</style></head><body>' + dom +
    '<script>var b=document.body;var n=0;function step(){n++;b.style.opacity=n%2?".99":"1";requestAnimationFrame(step);}requestAnimationFrame(step);</script></body></html>\n';
}
export function generate(directory) {
  check(!fs.existsSync(directory), 'Synthetic output already exists');
  fs.mkdirSync(directory, { recursive: true });
  const suite = [];
  for (const elements of [1000, 5000, 20000]) for (const rules of [200, 1000, 5000]) for (const kind of ['class', 'hover', 'attribute']) {
    const name = `${elements}-${rules}-${kind}.html`, html = synthetic(elements, rules, kind);
    fs.writeFileSync(path.join(directory, name), html);
    suite.push({ name, elements, rules, kind, sha256: hash(html) });
  }
  write(path.join(directory, 'suite.json'), { schemaVersion: 1, suite, seed: 0,
    notCovered: ['hover transitions require an input-aware adapter; FrameBenchmark measures non-hover matching only', 'phase-specific style/layout timing unavailable in FrameBenchmark'] });
  console.log('Generated 27 deterministic stress pages: ' + directory);
}
