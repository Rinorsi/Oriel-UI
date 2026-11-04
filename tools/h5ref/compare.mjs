#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {parseArgs} from 'node:util';
import {comparePixels, diagnosticsFrom, aggregateDiagnostics, isInside, markdownReport} from './lib.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const viewport = {width: 427, height: 240, scale: 2};
const help = '用法：node tools/h5ref/compare.mjs [corpus目录或单页.html] [--corpus 目录或单页.html] [--out 目录] [--chromium 可执行文件]\n'
  + '  [--workspace 工作区] [--chrome-home 工作区内依赖目录] [--preview-lib 预览器lib目录] [--background #rrggbb] [--java java路径]\n'
  + '  [--timeout 30000]；先运行 ./gradlew --offline :preview:installDist。';

async function findWorkspace() {
  for (let dir = repo; ; dir = path.dirname(dir)) {
    if (path.basename(dir) === 'Oriel-UI-Workspace') return dir;
    const sibling = path.join(path.dirname(dir), 'Oriel-UI-Workspace');
    if (await exists(path.join(sibling, 'planning'))) return sibling;
    if (path.dirname(dir) === dir) throw new Error('找不到工作区，请指定 --workspace');
  }
}
async function exists(file) { return fs.access(file).then(() => true, () => false); }
async function inRepository(directory) {
  for (let dir = directory; ; dir = path.dirname(dir)) {
    if (await exists(path.join(dir, '.git'))) return true;
    if (path.dirname(dir) === dir) return false;
  }
}
async function htmlFiles(root) {
  const stat = await fs.stat(root);
  if (stat.isFile()) {
    if (!root.toLowerCase().endsWith('.html')) throw new Error('输入文件必须是 .html');
    return [root];
  }
  const found = [];
  for (const entry of await fs.readdir(root, {withFileTypes: true})) {
    const full = path.join(root, entry.name);
    if (entry.isDirectory()) found.push(...await htmlFiles(full));
    else if (entry.isFile() && entry.name.toLowerCase().endsWith('.html')) found.push(full);
  }
  return found.sort();
}
function runEngine(java, args, timeout) {
  return new Promise(resolve => {
    const child = spawn(java, args, {cwd: repo, windowsHide: true, timeout});
    let stdout = '', stderr = '', error;
    child.stdout.on('data', data => { stdout += data; });
    child.stderr.on('data', data => { stderr += data; });
    child.on('error', e => { error = e.message; });
    child.on('close', (code, signal) => resolve({stdout, stderr, code, error: error ?? (signal ? '引擎超时或被终止：' + signal : null)}));
  });
}

// Chrome is also the PNG contact-sheet compositor; no graphics binaries are installed.
async function contactSheet(browser, images, title, score) {
  const tab = await browser.newPage();
  try {
    const data = await tab.evaluate(async ({images, title, score}) => {
      const loaded = await Promise.all(images.map(src => new Promise((resolve, reject) => {
        const img = new Image(); img.onload = () => resolve(img); img.onerror = reject; img.src = src;
      })));
      const width = loaded[0].width, height = loaded[0].height, header = 76, footer = 40;
      const canvas = document.createElement('canvas');
      canvas.width = width * 3; canvas.height = height + header + footer;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#e5e7eb'; ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = '#111827'; ctx.font = '20px sans-serif';
      ctx.fillText(title + ' | RGB MAE ' + score.toFixed(4) + ' / 100', 12, 27);
      ['Chrome', 'Oriel engine', 'Difference heatmap'].forEach((label, i) => {
        ctx.fillText(label, i * width + 12, 59);
        ctx.drawImage(loaded[i], i * width, header);
      });
      ctx.font = '17px sans-serif';
      ctx.fillText('Heat: black = equal, red -> yellow = max RGB channel difference (0 -> 255).', 12, canvas.height - 13);
      return canvas.toDataURL('image/png').split(',')[1];
    }, {images, title, score});
    return Buffer.from(data, 'base64');
  } finally { await tab.close(); }
}

async function main() {
  const {values, positionals} = parseArgs({allowPositionals: true, options: {
    corpus: {type: 'string'}, out: {type: 'string'}, chromium: {type: 'string'}, workspace: {type: 'string'},
    'chrome-home': {type: 'string'}, background: {type: 'string', default: '#ffffff'},
    'preview-lib': {type: 'string'},
    java: {type: 'string'}, timeout: {type: 'string', default: '30000'}, help: {type: 'boolean'},
  }});
  if (values.help) { console.log(help); return; }
  if (positionals.length > 1) throw new Error(help);
  if (values.corpus && positionals.length) throw new Error('--corpus 与位置参数只能选一个');
  const timeout = Number(values.timeout);
  if (!Number.isSafeInteger(timeout) || timeout <= 0) throw new Error('--timeout 必须为正整数毫秒');
  if (!/^#[0-9a-f]{6}$/i.test(values.background)) throw new Error('--background 必须为 #rrggbb，不接受透明颜色');
  const workspace = await fs.realpath(path.resolve(values.workspace ?? await findWorkspace()));
  const chromeHome = path.resolve(values['chrome-home'] ?? path.join(workspace, 'tools/chrome'));
  const out = path.resolve(values.out ?? path.join(workspace, 'daily/2026-11-04/workers/h5ref/out'));
  if (isInside(repo, out) || !isInside(workspace, out)) throw new Error('输出目录必须在工作区内、源码仓库外');
  if (await inRepository(out)) throw new Error('输出目录不能位于其他源码仓库或 worktree 内');
  if (!isInside(workspace, chromeHome) || isInside(repo, chromeHome)) throw new Error('Chromium 依赖必须放在工作区内、源码仓库外');
  await fs.mkdir(out, {recursive: true});
  const realOut = await fs.realpath(out);
  if (isInside(await fs.realpath(repo), realOut) || !isInside(workspace, realOut)) throw new Error('输出目录链接指向工作区外或源码仓库');
  if (await inRepository(realOut)) throw new Error('输出目录链接指向源码仓库');
  const realChrome = await fs.realpath(chromeHome);
  if (!isInside(workspace, realChrome) || isInside(await fs.realpath(repo), realChrome)) throw new Error('Chromium 目录链接指向工作区外或源码仓库');
  const corpus = await fs.realpath(path.resolve(values.corpus ?? positionals[0] ?? path.join(repo, 'tools/h5ref/corpus')));
  const files = await htmlFiles(corpus);
  if (!files.length) throw new Error('语料中没有 .html 页面：' + corpus);
  const base = (await fs.stat(corpus)).isFile() ? path.dirname(corpus) : corpus;
  const previewLib = path.resolve(values['preview-lib'] ?? path.join(repo, 'preview/build/install/preview/lib'));
  if (!await exists(previewLib)) throw new Error('预览器 lib 目录不存在：' + previewLib + '；请先 installDist 或指定 --preview-lib');
  if (!(await fs.stat(previewLib)).isDirectory() || !(await fs.readdir(previewLib)).some(name => name.endsWith('.jar'))) {
    throw new Error('--preview-lib 必须指向包含 JAR 的目录：' + previewLib);
  }
  process.env.PUPPETEER_CACHE_DIR = path.join(chromeHome, 'cache');
  const require = createRequire(path.join(chromeHome, 'package.json'));
  const {default: puppeteer} = await import(pathToFileURL(require.resolve('puppeteer')));
  const {PNG} = require('pngjs');
  const executablePath = values.chromium ? path.resolve(values.chromium) : await puppeteer.executablePath();
  if (!isInside(workspace, await fs.realpath(executablePath))) throw new Error('Chromium 可执行文件必须位于工作区内');
  const java = values.java ?? (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, process.platform === 'win32' ? 'bin/java.exe' : 'bin/java') : 'java');
  const browser = await puppeteer.launch({executablePath, headless: true, timeout,
    userDataDir: await fs.mkdtemp(path.join(out, 'chrome-profile-'))});
  const report = {schemaVersion: 1, createdAt: new Date().toISOString(), corpus, viewport,
    background: values.background, browserVersion: await browser.version(), executablePath, java, previewLib,
    engineAssets: process.env.ORIEL_MC_JAR ?? 'auto-detected (see engine.stdout.log)',
    metrics: {name: 'RGB MAE / 255 * 100', changedThreshold: 8}, pages: []};
  try {
    for (const file of files) {
      const name = path.relative(base, file).split(path.sep).join('/');
      const id = name.replace(/[^a-zA-Z0-9_-]/g, '_') + '-' + createHash('sha256').update(name).digest('hex').slice(0, 8);
      const pageOut = path.join(out, id);
      await fs.mkdir(pageOut, {recursive: true});
      if (!isInside(realOut, await fs.realpath(pageOut))) throw new Error('页面输出目录链接指向报告目录外：' + pageOut);
      const result = {page: name, source: file, diagnostics: [], browserWarnings: [], files: {}, engineExitCode: null};
      report.pages.push(result);
      const engineFile = path.join(pageOut, 'engine.png');
      await fs.rm(engineFile, {force: true});
      let tab;
      try {
        // Run the engine even when Chrome cannot load a page, so its diagnostics are retained.
        const args = ['-cp', path.join(previewLib, '*'), 'dev.oriel.preview.Preview', file,
          '--size', '427x240', '--scale', '2', '--background', values.background, '--snapshot', engineFile];
        const engine = await runEngine(java, args, timeout);
        result.engineCommand = {executable: java, args};
        result.engineExitCode = engine.code;
        result.diagnostics = diagnosticsFrom(engine.stderr);
        await fs.writeFile(path.join(pageOut, 'engine.stdout.log'), engine.stdout);
        await fs.writeFile(path.join(pageOut, 'engine.stderr.log'), engine.stderr);
        result.files.stdout = id + '/engine.stdout.log'; result.files.stderr = id + '/engine.stderr.log';
        if (engine.error) throw new Error(engine.error);
        tab = await browser.newPage();
        tab.setDefaultTimeout(timeout);
        await tab.setJavaScriptEnabled(false);
        await tab.setViewport({width: viewport.width, height: viewport.height, deviceScaleFactor: viewport.scale});
        tab.on('requestfailed', req => result.browserWarnings.push(req.url() + ': ' + req.failure()?.errorText));
        tab.on('pageerror', error => result.browserWarnings.push(error.message));
        const cdp = await tab.createCDPSession();
        const rgb = values.background.slice(1).match(/../g).map(v => parseInt(v, 16));
        await cdp.send('Emulation.setDefaultBackgroundColorOverride', {color: {r: rgb[0], g: rgb[1], b: rgb[2], a: 1}});
        await tab.goto(pathToFileURL(file).href, {waitUntil: 'load', timeout});
        result.hasText = await tab.evaluate(async () => {
          await document.fonts.ready;
          for (const animation of document.getAnimations()) { animation.pause(); animation.currentTime = 0; }
          return !!document.body?.innerText.trim();
        });
        const chrome = Buffer.from(await tab.screenshot({path: path.join(pageOut, 'chrome.png')}));
        result.files.chrome = id + '/chrome.png';
        if (engine.code !== 0) throw new Error('引擎退出码 ' + engine.code + '，详见 engine.stderr.log');
        const enginePng = await fs.readFile(engineFile);
        result.files.engine = id + '/engine.png';
        const a = PNG.sync.read(chrome), b = PNG.sync.read(enginePng);
        if (a.width !== 854 || a.height !== 480 || b.width !== a.width || b.height !== a.height) throw new Error('截图尺寸不匹配，预期 854×480');
        const {heat, ...metrics} = comparePixels(a.data, b.data);
        result.metrics = metrics;
        const heatPng = PNG.sync.write({width: a.width, height: a.height, data: Buffer.from(heat)});
        await fs.writeFile(path.join(pageOut, 'heatmap.png'), heatPng);
        result.files.heatmap = id + '/heatmap.png';
        const images = [chrome, enginePng, heatPng].map(buf => 'data:image/png;base64,' + buf.toString('base64'));
        await fs.writeFile(path.join(pageOut, 'comparison.png'), await contactSheet(browser, images, name, metrics.score));
        result.files.comparison = id + '/comparison.png';
        console.log(name + ': MAE=' + metrics.score.toFixed(4) + ', diagnostics=' + result.diagnostics.length);
      } catch (error) {
        result.error = error.message;
        console.error(name + ': ' + error.message);
      } finally { if (tab) await tab.close(); }
    }
  } finally { await browser.close(); }
  report.diagnosticFrequency = aggregateDiagnostics(report.pages);
  await fs.writeFile(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n');
  await fs.writeFile(path.join(out, 'report.md'), markdownReport(report));
  console.log('报告：' + path.join(out, 'report.md'));
  if (report.pages.some(p => p.error)) process.exitCode = 1;
}

main().catch(error => { console.error('H5 对照失败：' + error.message); process.exitCode = 1; });
