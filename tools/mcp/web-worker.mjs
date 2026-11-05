import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { readJson, writeJson, loadRecord, saveRecord, requireThat, safePath, escapeHtml as h, now } from './lib/core.mjs';
import { verifyRevision, assertCurrent } from './lib/projects.mjs';

const configPath = process.argv[2];
const config = readJson(configPath);
const revision = loadRecord('revisions', config.revisionId);
verifyRevision(revision);
let origin, contentOrigin;
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8', '.png': 'image/png', '.webp': 'image/webp', '.jpg': 'image/jpeg', '.ttf': 'font/ttf', '.otf': 'font/otf' };
function send(res, code, data, type = 'text/html; charset=utf-8') {
  res.writeHead(code, { 'Content-Type': type, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' }); res.end(data);
}
function address(value) {
  return value.replace(/\b([a-z][a-z0-9_.-]*):([a-z0-9_./-]+\.(?:css|js|png|jpg|webp|ttf|otf|json))/gi, (_, ns, rel) => '/' + config.contentToken + '/assets/' + ns + '/' + rel);
}
function bridge() {
  const dict = {};
  for (const ns of ['oriel', revision.project.namespace]) {
    const file = path.join(revision.pack, 'assets', ns, 'lang/zh_cn.json'); if (fs.existsSync(file)) Object.assign(dict, readJson(file));
  }
  return '(()=>{const listeners={},data=' + JSON.stringify(revision.project.fixture).replace(/</g, '\\u003c') + ',words=' + JSON.stringify(dict).replace(/</g, '\\u003c') + ';' +
    'window.oriel={data,on:(n,f)=>{(listeners[n]||(listeners[n]=[])).push(f);return()=>oriel.off(n,f)},off:(n,f)=>{listeners[n]=(listeners[n]||[]).filter(x=>x!==f)},' +
    'send:(name,value)=>{console.info("[Oriel 浏览器模拟消息]",name,value);return false},state:o=>o,close:()=>console.info("[模拟关闭]"),open:()=>{throw new Error("浏览器桥接未实现页面导航")},playSound:()=>{},t:(key,...args)=>{let s=words[key]||key;args.forEach(v=>s=s.replace(/%s/,String(v)));return s},nextTick:f=>Promise.resolve().then(f)};' +
    'const setAttribute=Element.prototype.setAttribute;Element.prototype.setAttribute=function(name,value){if((name==="src"||name==="href")&&/^[a-z0-9_.-]+:/.test(String(value)))value="/' + config.contentToken + '/assets/"+String(value).replace(":","/");return setAttribute.call(this,name,value)};' +
    'document.addEventListener("DOMContentLoaded",()=>{document.querySelectorAll("item,slot,entity,model,scene").forEach(el=>{el.setAttribute("title","原生 Minecraft 内容占位，须在游戏验证");if(!el.textContent.trim())el.textContent="◇";el.style.display="inline-block";el.style.minWidth="16px";el.style.minHeight="16px";});});})();';
}
const contentServer = http.createServer((req, res) => {
  try {
    requireThat(req.method === 'GET', 'METHOD', '只允许读取页面资源。');
    const pathname = decodeURIComponent(new URL(req.url, contentOrigin).pathname);
    requireThat(pathname.startsWith('/' + config.contentToken + '/'), 'TOKEN', '无效预览地址。');
    const rel = pathname.slice(config.contentToken.length + 2);
    if (rel === '_bridge.js') return send(res, 200, bridge(), types['.js']);
    verifyRevision(revision);
    const file = safePath(revision.pack, rel);
    requireThat(fs.existsSync(file) && fs.statSync(file).isFile(), 'NOT_FOUND', '资源不存在。');
    const extension = path.extname(file).toLowerCase();
    let bytes = fs.readFileSync(file);
    res.setHeader('Access-Control-Allow-Origin', '*'); // Read-only content origin, never the review origin.
    res.setHeader('Content-Security-Policy', "default-src 'self' data:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; img-src 'self' data:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'");
    if (extension === '.html') {
      let text = address(bytes.toString('utf8'));
      const prefix = '/' + config.contentToken + '/';
      const head = '<script src="' + prefix + '_bridge.js"></script><style>@font-face{font-family:"oriel:sans";src:url("' + prefix + 'assets/oriel/font/noto_sans_sc.ttf")}@font-face{font-family:"oriel:pixel";src:local("monospace")}html,body{margin:0}*{box-sizing:border-box}</style>';
      text = /<head\b[^>]*>/i.test(text) ? text.replace(/<head\b[^>]*>/i, m => m + head) : head + text;
      // Optional browser-only adapter is injected before shared scripts, never bundled into game assets.
      if (fs.existsSync(path.join(revision.pack, 'browser/mock.js'))) text = text.replace(head, head + '<script src="' + prefix + 'browser/mock.js"></script>');
      bytes = Buffer.from(text);
    } else if (extension === '.css') bytes = Buffer.from(address(bytes.toString('utf8')));
    send(res, 200, bytes, types[extension] || 'application/octet-stream');
  } catch (error) { send(res, 400, h(error.message)); }
});

function shell() {
  const item = config.reviewId ? loadRecord('reviews', config.reviewId) : null;
  const status = item?.decision === 'approved' ? '已批准' : item?.decision === 'rejected' ? '待修改' : item ? '等待你的验收' : '开发预览 · 固定快照';
  const shots = revision.check?.shots || [];
  const shotLinks = shots.map((s, i) => '<a target="_blank" href="/artifact/' + config.shellToken + '/' + i + '">' + h(s.scene) + ' · ' + s.width + '×' + s.height + '</a>').join('');
  const decision = item && item.decision === 'pending' ? '<form action="/decision/' + config.shellToken + '" method="post"><input type="hidden" name="csrf" value="' + config.csrf + '"><textarea name="feedback" placeholder="需要调整的地方（可选）"></textarea><div class="actions"><button name="decision" value="rejected">需要修改</button><button class="primary" name="decision" value="approved">网页验收通过</button></div><p class="small">确认的是本次固定版本；此操作不会自行安装或启动游戏。</p></form>' : '<p>' + h(item?.feedback || '查看预览和引擎截图。修改源文件后重新打开预览。') + '</p>';
  const scenes = revision.project.scenes.map(s => '<li><strong>' + h(s.name) + '</strong> ' + h(s.description || s.actions.join(' → ') || '初始状态') + '</li>').join('');
  return '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>' + h(revision.project.title) + ' · Oriel 验收</title><style>' +
    '*{box-sizing:border-box}body{margin:0;background:#17191b;color:#ecefed;font:14px/1.6 system-ui,"Microsoft YaHei",sans-serif}header{display:flex;justify-content:space-between;gap:20px;padding:18px 24px;border-bottom:1px solid #343a3a}h1{font-size:18px;margin:0}p{margin:6px 0;color:#adb6b1}.tag{color:#a9d988;font-size:12px}.layout{display:grid;grid-template-columns:minmax(0,1fr) 290px;min-height:calc(100vh - 87px)}main{padding:20px;min-width:0}aside{padding:22px;border-left:1px solid #343a3a;background:#1e2223}h2{font-size:14px;margin:24px 0 8px}button{border:1px solid #4a5350;color:#e5e9e6;background:#303737;padding:9px 14px;cursor:pointer;font:inherit}button:hover{background:#404b45}.primary{background:#609940;color:#fff;border-color:#83b65e}.toolbar{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px}.stage{position:relative;min-height:260px;overflow:hidden;background:#101213;border:1px solid #303938}iframe{position:absolute;border:0;transform-origin:top left;background:#252829}.small{font-size:12px;color:#9daaa2}textarea{width:100%;min-height:82px;background:#151a19;border:1px solid #4a5350;color:#fff;padding:10px;font:inherit;resize:vertical}.actions{display:flex;gap:8px;margin-top:10px}.shots a{display:block;color:#b8d69f;padding:3px 0}ul{padding-left:18px;font-size:12px;color:#c0c9c3}.hash{font-family:monospace;word-break:break-all;font-size:11px}.info{margin-top:16px;padding:14px;border-left:2px solid #76965d;background:#202722}@media(max-width:900px){.layout{grid-template-columns:1fr}aside{border-left:0;border-top:1px solid #343a3a}header{padding:14px}main{padding:12px}}</style></head><body>' +
    '<header><div><div class="tag">ORIEL / UI REVIEW</div><h1>' + h(revision.project.title) + '</h1></div><div>' + h(status) + '<br><span class="small">' + h(revision.project.profile.minecraft + ' · ' + revision.project.profile.loader) + '</span></div></header><div class="layout"><main><div class="toolbar"><button onclick="resizeFrame(640,360)">标准 640 × 360</button><button onclick="resizeFrame(427,240)">紧凑 427 × 240</button><button onclick="document.getElementById(\'page\').src=frameUrl">重置演示</button></div><div class="stage" id="stage"><iframe id="page" title="页面预览" sandbox="allow-scripts" src="' + contentOrigin + '/' + config.contentToken + '/' + revision.entry + '"></iframe></div><div class="info"><strong>可直接点击、输入和切换页面</strong><p>' + h(revision.project.businessContract) + '</p><p class="small">模拟消息不写入游戏数据。浏览器与 Oriel 引擎的结果分别展示。</p></div></main><aside><strong>' + h(status) + '</strong><p class="hash">版本 ' + revision.hash.slice(0, 16) + '</p>' + decision + '<h2>请检查这些场景</h2><ul>' + scenes + '</ul><h2>Oriel 引擎截图</h2><div class="shots">' + (shotLinks || '<p>尚未执行引擎检查。</p>') + '</div><h2>已知差异</h2><ul>' + revision.project.knownDifferences.map(x => '<li>' + h(x) + '</li>').join('') + '</ul></aside></div>' +
    '<script>const frameUrl=document.getElementById("page").src;let w=640,h=360;function resizeFrame(x,y){w=x;h=y;const stage=document.getElementById("stage"),frame=document.getElementById("page"),scale=Math.min(stage.clientWidth/w,2);frame.style.width=w+"px";frame.style.height=h+"px";frame.style.transform="scale("+scale+")";stage.style.height=(h*scale)+"px";}window.addEventListener("resize",()=>resizeFrame(w,h));resizeFrame(w,h);</script></body></html>';
}
const shellServer = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, origin);
    res.setHeader('Content-Security-Policy', "default-src 'self'; frame-src " + contentOrigin + "; script-src 'unsafe-inline'; style-src 'unsafe-inline'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    res.setHeader('X-Frame-Options', 'DENY');
    if (url.pathname === '/health') return send(res, 200, 'ok', 'text/plain');
    if (url.pathname === '/stop/' + config.shellToken && req.method === 'POST') {
      send(res, 200, 'stopped', 'text/plain'); setTimeout(() => { shellServer.close(); contentServer.close(); process.exit(0); }, 100); return;
    }
    if (url.pathname === '/review/' + config.shellToken && req.method === 'GET') { verifyRevision(revision); return send(res, 200, shell()); }
    if (url.pathname.startsWith('/artifact/' + config.shellToken + '/') && req.method === 'GET') {
      const index = Number(url.pathname.split('/').at(-1)), shot = revision.check?.shots?.[index];
      requireThat(shot?.image && shot.image.startsWith(revision.directory + path.sep), 'NOT_FOUND', '截图不存在。');
      return send(res, 200, fs.readFileSync(shot.image), 'image/png');
    }
    if (url.pathname === '/decision/' + config.shellToken && req.method === 'POST') {
      requireThat(config.reviewId && req.headers.origin === origin && req.headers['sec-fetch-site'] === 'same-origin', 'REVIEW_ORIGIN', '必须从独立验收页本人提交。');
      let body = ''; for await (const chunk of req) { body += chunk; requireThat(body.length <= 16000, 'BODY_TOO_LARGE', '意见过长。'); }
      const fields = new URLSearchParams(body);
      requireThat(fields.get('csrf') === config.csrf, 'REVIEW_TOKEN', '验收页面已失效。');
      const decision = fields.get('decision'); requireThat(['approved', 'rejected'].includes(decision), 'INVALID_DECISION', '无效验收决定。');
      assertCurrent(revision);
      const item = loadRecord('reviews', config.reviewId);
      requireThat(item.decision === 'pending' && item.kind === 'human' && revision.check?.passed, 'REVIEW_CLOSED', '该验收已处理或检查未通过。');
      saveRecord('reviews', { ...item, decision, feedback: fields.get('feedback') || '', decidedAt: now(), source: 'local-review-form' });
      res.writeHead(303, { Location: '/review/' + config.shellToken }); res.end(); return;
    }
    send(res, 404, 'Not found', 'text/plain');
  } catch (error) { send(res, 409, '<meta charset="utf-8"><p>' + h(error.message) + '</p><p>请返回 Agent 重新检查或生成验收页。</p>'); }
});
await new Promise(resolve => contentServer.listen(0, '127.0.0.1', resolve));
contentOrigin = 'http://127.0.0.1:' + contentServer.address().port;
await new Promise(resolve => shellServer.listen(0, '127.0.0.1', resolve));
origin = 'http://127.0.0.1:' + shellServer.address().port;
writeJson(path.join(path.dirname(configPath), 'ready.json'), { id: config.id, pid: process.pid, origin, url: origin + '/review/' + config.shellToken, contentOrigin, running: true });
// No persistent startup registration; a deliberately opened review remains available for 24 hours.
setTimeout(() => process.exit(0), 24 * 60 * 60 * 1000);
