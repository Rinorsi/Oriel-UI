#!/usr/bin/env node
// Browser regression for the bundled workbench. It refuses human-review sessions.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { WORKSPACE, ROOT, STATE, readJson, writeJson, inside, requireThat, loadRecord } from './lib/core.mjs';

const receipt = readJson(process.argv[2]);
const web = receipt.structuredContent || receipt;
const job = loadRecord('jobs', web.id);
requireThat(job.kind === 'web' && !job.reviewId, 'TEST_SESSION_ONLY', '浏览器测试只操作开发预览，禁止自动操作用户验收页。');
const out = path.resolve(process.argv[3] || path.join(STATE, 'browser-test'));
requireThat(inside(WORKSPACE, out) && !inside(ROOT, out), 'OUTPUT_ROOT', '浏览器产物放工作区。');
fs.mkdirSync(out, { recursive: true });
const chromeHome = path.join(WORKSPACE, 'tools/chrome');
const require = createRequire(path.join(chromeHome, 'package.json'));
const puppeteer = require('puppeteer');
const versions = fs.readdirSync(path.join(chromeHome, 'cache/chrome')).sort();
const executablePath = path.join(chromeHome, 'cache/chrome', versions.at(-1), 'chrome-win64/chrome.exe');
const browser = await puppeteer.launch({ executablePath, headless: true, userDataDir: path.join(out, 'browser-profile'), args: ['--disable-background-networking', '--no-first-run'] });
const checks = [], errors = [], missing = [];
try {
  const page = await browser.newPage(); await page.setViewport({ width: 1440, height: 1050, deviceScaleFactor: 1 });
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.status() >= 400 && !response.url().endsWith('/favicon.ico')) missing.push({ url: response.url(), status: response.status() }); });
  await page.goto(web.url, { waitUntil: 'networkidle0' });
  const iframe = await page.$('#page'), frame = await iframe.contentFrame();
  async function click(selector) {
    const rect = await frame.$eval(selector, el => { el.scrollIntoView({ block: 'nearest' }); const r = el.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height, viewport: innerWidth }; });
    assert.ok(rect.width && rect.height, 'Visible click target: ' + selector);
    const bounds = await iframe.boundingBox(), scale = bounds.width / rect.viewport;
    await page.mouse.click(bounds.x + (rect.x + rect.width / 2) * scale, bounds.y + (rect.y + rect.height / 2) * scale);
  }
  await frame.waitForSelector('#item-name');
  assert.equal(await frame.$eval('#item-name', el => el.value), '霜锋'); checks.push('initial');
  await page.screenshot({ path: path.join(out, 'browser-review-shell.png'), fullPage: true });
  await iframe.screenshot({ path: path.join(out, 'browser-initial.png') });
  await frame.$eval('#item-name', el => { el.value = '雪原之刃'; el.dispatchEvent(new Event('input', { bubbles: true })); });
  assert.equal(await frame.$eval('#preview-name', el => el.textContent), '雪原之刃'); checks.push('name updates preview');
  await click('#format-name'); await frame.waitForSelector('#format-modal:not(.hidden)'); await click('#color-gold'); await click('#apply-format');
  assert.equal(await frame.$eval('#preview-name', el => el.style.color), 'rgb(240, 204, 126)'); checks.push('format draft');
  await click('#nav-enchant'); await click('#add-enchant');
  assert.equal(await frame.$eval('#mending-row', el => el.classList.contains('hidden')), false); checks.push('enchantment added');
  await click('#add-component'); await frame.waitForSelector('#component-modal:not(.hidden)'); await frame.type('#component-search', '不可破坏'); await click('[data-component="unbreakable"]');
  assert.equal(await frame.$eval('#component-count', el => el.textContent), '4'); checks.push('component selection');
  await click('#module-block');
  assert.equal(await frame.$eval('#object-title', el => el.textContent), '箱子');
  await frame.waitForFunction(() => { const image = document.getElementById('preview-icon'); return image.complete && image.naturalWidth > 0; });
  assert.ok(await frame.$eval('#preview-icon', el => el.complete && el.naturalWidth > 0)); checks.push('dynamic asset URI mapping');
  await iframe.screenshot({ path: path.join(out, 'browser-block.png') });
  await click('#module-item'); await click('#nav-basic'); await click('#save-draft');
  assert.match(await frame.$eval('#feedback', el => el.textContent), /未保存到游戏或磁盘/); checks.push('demo save disclosure');
  await click('#close-output');
  await page.evaluate(() => resizeFrame(427, 240));
  assert.ok(await frame.$eval('#save-draft', el => { const r = el.getBoundingClientRect(); return r.bottom <= innerHeight && r.left >= 0 && r.right <= innerWidth; }));
  checks.push('compact actions visible'); await iframe.screenshot({ path: path.join(out, 'browser-compact.png') });
  assert.deepEqual(errors, []); assert.deepEqual(missing, []);
  writeJson(path.join(out, 'result.json'), { passed: true, checks, errors, missing, url: web.url, humanApprovalAttempted: false, browser: await browser.version() });
  console.log(JSON.stringify({ passed: true, checks, out }));
} catch (error) {
  writeJson(path.join(out, 'result.json'), { passed: false, checks, errors, missing, error: error.message, humanApprovalAttempted: false });
  throw error;
} finally { await browser.close(); }
