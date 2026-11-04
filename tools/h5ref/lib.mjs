import path from 'node:path';

// RGB MAE measures all pixels; the threshold only affects the changed-pixel count.
export function comparePixels(a, b, threshold = 8) {
  if (a.length === 0 || a.length !== b.length || a.length % 4 !== 0) {
    throw new Error('Images must have equal, nonempty RGBA buffers');
  }
  const heat = new Uint8Array(a.length);
  let total = 0, changed = 0, peak = 0;
  for (let i = 0; i < a.length; i += 4) {
    let max = 0;
    for (let c = 0; c < 3; c++) {
      const d = Math.abs(a[i + c] - b[i + c]);
      total += d;
      max = Math.max(max, d);
    }
    if (max > threshold) changed++;
    peak = Math.max(peak, max);
    heat[i] = max;
    heat[i + 1] = Math.round(max * max / 255);
    heat[i + 3] = 255;
  }
  return {score: total / (a.length / 4 * 3 * 255) * 100,
    changedPixels: changed, changedPercent: changed / (a.length / 4) * 100, peak, heat};
}

export function diagnosticsFrom(stderr) {
  return stderr.split(/\r?\n/).filter(line => line.startsWith('[diagnostic] ')).map(raw => {
    const match = /^\[diagnostic\] (.*?): (info|warning|error) \[([^\]]+)\] (.*)$/.exec(raw);
    if (!match) return {raw, feature: 'unparsed-diagnostic', code: 'unparsed-diagnostic'};
    const [, source, severity, code, message] = match;
    // Preserve unknown diagnostic formats under their code instead of dropping them.
    const declaration = message.match(/(?:property|declaration)\s+['"]([^'"]+)['"]/i)?.[1];
    const feature = message.includes('CSS nesting') ? 'CSS nesting' : declaration?.split(':')[0].trim()
      ?? message.match(/^['"]([a-z-]+)['"]\s*:/i)?.[1]
      ?? message.match(/^([a-z-]+)\s*:/)?.[1]
      ?? message.match(/(@[a-z-]+|<[a-z-]+>)/)?.[1]
      ?? code;
    return {raw, source, severity, code, message, feature};
  });
}

export function aggregateDiagnostics(pages) {
  const groups = new Map();
  for (const page of pages) for (const d of page.diagnostics) {
    if (!groups.has(d.feature)) groups.set(d.feature, {feature: d.feature, occurrences: 0, pages: new Set(), codes: new Set()});
    const group = groups.get(d.feature);
    group.occurrences++;
    group.pages.add(page.page);
    group.codes.add(d.code);
  }
  return [...groups.values()].map(g => ({...g, pageCount: g.pages.size, pages: [...g.pages], codes: [...g.codes]}))
    .sort((a, b) => b.pageCount - a.pageCount || b.occurrences - a.occurrences || a.feature.localeCompare(b.feature));
}

export function isInside(parent, child) {
  const rel = path.relative(parent, child);
  return rel === '' || (!rel.startsWith('..' + path.sep) && rel !== '..' && !path.isAbsolute(rel));
}

const cell = text => String(text).replaceAll('|', '\|').replaceAll('\n', ' ');
export function markdownReport(report) {
  const lines = ['# H5 对照报告', '',
    '视口：427×240 CSS px；缩放：2；背景：' + report.background + '。',
    'Chromium：' + report.browserVersion + '。共 ' + report.pages.length + ' 页，执行失败 ' + report.pages.filter(p => p.error).length + ' 页。', '',
    '分数 = RGB 平均绝对误差 / 255 × 100（0 完全一致，100 最大差异）。变化像素：任一 RGB 通道差 > 8。',
    '字体、字宽、换行和抗锯齿不同属于已知差异；不会自动把这些差异判定为“仅文字”。零诊断也不代表浏览器等价。',
    '静态页面在第一帧比较；Chrome 的 CSS 动画暂停在 0ms。缺失资源、默认 UA 样式也可能产生差异。', '',
    '| 页面 | MAE 分数 | 变化像素 | 诊断 | 结果 |', '|---|---:|---:|---:|---|'];
  for (const p of report.pages) lines.push('| ' + cell(p.page) + ' | ' + (p.metrics?.score.toFixed(4) ?? '—') + ' | '
    + (p.metrics ? p.metrics.changedPercent.toFixed(2) + '%' : '—') + ' | ' + p.diagnostics.length + ' | '
    + (p.error ? cell(p.error) : '[并排图](' + p.files.comparison + ')') + ' |');
  lines.push('', '## 诊断频率（按出现页数降序）', '', '| 属性 / 特性 | 页数 | 次数 | 诊断代码 |', '|---|---:|---:|---|');
  for (const g of report.diagnosticFrequency) lines.push('| ' + cell(g.feature) + ' | ' + g.pageCount + ' | ' + g.occurrences + ' | ' + cell(g.codes.join(', ')) + ' |');
  if (!report.diagnosticFrequency.length) lines.push('| 无诊断 | 0 | 0 | — |');
  for (const p of report.pages) {
    lines.push('', '## ' + p.page, '');
    if (p.files.comparison) lines.push('![Chrome / 引擎 / 差异热力图](' + p.files.comparison + ')', '');
    lines.push('引擎退出码：' + (p.engineExitCode ?? '未运行') + '；文字内容：' + (p.hasText ? '有（字体已知不同）' : '无或未检测') + '。', '');
    for (const d of p.diagnostics) lines.push('- ' + cell(d.raw));
    for (const w of p.browserWarnings) lines.push('- Chrome：' + cell(w));
    if (p.error) lines.push('- 执行失败：' + cell(p.error));
  }
  return lines.join('\n') + '\n';
}
