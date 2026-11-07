import fs from 'node:fs';
import path from 'node:path';
import { STATE, id, preview, requireThat } from './core.mjs';

/** Executes the existing headless preview route; never starts or modifies a game session. */
export async function inspectPaint(args, pagePath) {
  requireThat(typeof args.page === 'string' && args.page.length > 0, 'PAGE_REQUIRED', '必须提供 page。');
  const width = args.width ?? 640, height = args.height ?? 360, frames = args.frames ?? 2;
  requireThat(Number.isInteger(width) && width > 0 && width <= 1920
    && Number.isInteger(height) && height > 0 && height <= 1080
    && Number.isInteger(frames) && frames > 0 && frames <= 600,
  'INVALID_SIZE', '尺寸或帧数超出范围。');
  requireThat(args.element == null || typeof args.element === 'string', 'INVALID_ELEMENT', 'element 必须是标签子串。');
  const directory = path.join(STATE, 'jobs', id('paint'));
  fs.mkdirSync(directory, { recursive: true });
  const report = path.join(directory, 'paint.json');
  const command = [pagePath(args.page), '--scale', '1', '--size', width + 'x' + height,
    '--frames', String(frames), '--snapshot', path.join(directory, 'page.png'), '--paint-trace', report];
  if (args.element != null) command.push('--paint-element', args.element);
  const result = await preview(command);
  if (result.code !== 0 || !fs.existsSync(report)) {
    return { passed: false, exitCode: result.code, output: result.output, report: null };
  }
  const value = JSON.parse(fs.readFileSync(report, 'utf8'));
  return { passed: true, ...value, commands: value.commands.slice(0, 500),
    commandCount: value.commands.length, truncated: value.commands.length > 500, report,
    note: '原生内容仅记录边界；预览命令和缓存统计不代表游戏 GPU、纹理上传或完整可见性分析。' };
}
