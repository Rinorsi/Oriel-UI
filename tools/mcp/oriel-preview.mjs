#!/usr/bin/env node
// Oriel UI workflow MCP. stdout is exclusively newline-delimited JSON-RPC.
import fs from 'node:fs';
import path from 'node:path';
import { createInterface } from 'node:readline';
import { ROOT, STATE, KIT, initState, requireThat, WorkflowError, preview, id } from './lib/core.mjs';
import { instructions, topics, rules, prompt } from './lib/rules.mjs';
import { registerProject, projectStatus, freeze, validateActions } from './lib/projects.mjs';
import { checkProject, snapshotRevision } from './lib/checks.mjs';
import { webPreview, review } from './lib/web.mjs';
import { integrate, gamePreview } from './lib/integration.mjs';
import { scaffold } from './lib/scaffold.mjs';

initState();
const string = { type: 'string' }, number = { type: 'integer' }, object = { type: 'object' };
const schema = (properties = {}, required = []) => ({ type: 'object', properties, required, additionalProperties: false });
const tool = (name, description, properties, required, run, readOnly = false) => ({ name, description, inputSchema: schema(properties, required), annotations: { readOnlyHint: readOnly, destructiveHint: false, openWorldHint: false }, run });
function result(value, isError = false) { return { content: [{ type: 'text', text: JSON.stringify(value, null, 2) }], structuredContent: value, ...(isError ? { isError: true } : {}) }; }
const pagePath = value => !value ? path.join(KIT, 'assets/oriel/ui') : value.includes('/') || value.includes('\\') ? path.resolve(ROOT, value) : path.join(KIT, 'assets/oriel/ui', value.endsWith('.html') ? value : value + '.html');
async function imageResult(receipt) {
  const response = result(receipt, !receipt.passed);
  if (receipt.image && fs.existsSync(receipt.image)) response.content.push({ type: 'image', mimeType: 'image/png', data: fs.readFileSync(receipt.image).toString('base64') });
  return response;
}
// Screenshots review: the user must actually see these images, so they travel back as MCP image content (capped).
function reviewResult(receipt) {
  if (!receipt.imagePaths) return receipt;
  const { imagePaths, ...value } = receipt;
  const response = result(value);
  for (const file of imagePaths.slice(0, 12)) if (fs.existsSync(file)) response.content.push({ type: 'image', mimeType: 'image/png', data: fs.readFileSync(file).toString('base64') });
  return response;
}
const TOOLS = [
  tool('oriel_inspect', '加载指定页面：selector 解释选择器；property 解释属性的层叠输赢、继承、变量展开与当前动画值。二选一。只读诊断，不批准页面，不启动游戏。', { page: string, selector: string, property: string, element: string, frames: number }, ['page', 'element'], async args => {
    const frames = args.frames ?? 1;
    requireThat(frames > 0 && frames <= 600, 'INVALID_FRAMES', 'frames 必须在 1–600 之间。');
    requireThat(Boolean(args.selector) !== Boolean(args.property), 'INVALID_INSPECT', 'selector 与 property 必须且只能指定一个。');
    const r = await preview([pagePath(args.page), args.property ? '--why-property' : '--why', args.property ?? args.selector, args.element, '--frames', String(frames)]);
    const explanations = r.output.split(/\r?\n/).filter(line => line.startsWith('{')).flatMap(line => {
      try { const value = JSON.parse(line); return ['selector-explanation', 'cascade-explanation'].includes(value.kind) ? [value] : []; } catch { return []; }
    });
    return result({ passed: r.code === 0 && explanations.length > 0, exitCode: r.code, explanations, output: r.output }, r.code !== 0 || explanations.length === 0);
  }),
  tool('oriel_scaffold', '只读检测下游 Fabric/NeoForge/Stonecutter 工程并返回接入清单。prepare=true 在工作区生成并检查固定骨架；经 oriel_review 用户批准后 apply=true + reviewId 只新建 HTML/Java，不改已有文件或构建配置。', { projectRoot: string, loader: { ...string, enum: ['fabric', 'neoforge'] }, minecraft: string, modId: string, resourcesDir: string, javaDir: string, javaPackage: string, launchMode: { ...string, enum: ['gradle-inside', 'gradle-outside', 'jar'] }, prepare: { type: 'boolean' }, apply: { type: 'boolean' }, reviewId: string }, ['projectRoot'], args => scaffold(args)),
  tool('oriel_rules', '制作前必读。查询目标版本的支持能力、限制、替代方式与调用流程；未查询到不代表支持。', { topic: { ...string, enum: Object.keys(topics) }, feature: string, profile: object }, [], args => rules(args), true),
  tool('oriel_project', '登记一个包含 assets/<namespace> 的页面包。重新登记或改源文件将使旧检查与验收失效。', {
    projectId: string, title: string, sourceRoot: string, namespace: string, entry: string, profile: object,
    fixture: object, scenes: { type: 'array', items: object }, behavior: { ...string, enum: ['javascript', 'java'] }, businessContract: string, knownDifferences: { type: 'array', items: string },
  }, ['projectId', 'sourceRoot', 'namespace'], args => registerProject(args)),
  tool('oriel_status', '查看项目当前阶段、陈旧验收、检查/接入/游戏回执和下一步。', { projectId: string }, ['projectId'], args => projectStatus(args.projectId), true),
  tool('oriel_web_preview', '启动/查询/停止本机可交互网页预览。独立回环服务提供固定快照和模拟桥接；不会写入游戏。', { action: { ...string, enum: ['start', 'status', 'stop'] }, projectId: string, sessionId: string }, [], args => webPreview(args)),
  tool('oriel_check', '项目模式：结构/资源检查+真实引擎严格诊断+双尺寸场景，返回位置与修复建议。兼容旧 target 检查。', { projectId: string, target: string, strict: { type: 'boolean' } }, [], async args => {
    if (args.projectId) { const check = await checkProject(args); return result(check, !check.passed); }
    const r = await preview(['--check', pagePath(args.target), ...(args.strict ? ['--strict'] : [])]);
    return result({ passed: r.code === 0, exitCode: r.code, output: r.output, note: '旧 target 模式不产生可批准的项目验收。' }, r.code !== 0);
  }),
  tool('oriel_snapshot', '真实 Oriel 引擎截图。可执行已声明场景/动作并查看元素盒子。原生 3D 为占位，不能代替游戏验证。', {
    projectId: string, page: string, scene: string, width: number, height: number, frames: number, actions: { type: 'array', items: string }, boxes: string,
  }, [], async args => {
    if (args.projectId) return imageResult(await snapshotRevision(await freeze(args.projectId), args));
    requireThat(args.page, 'PAGE_REQUIRED', '必须提供 projectId 或 page。');
    validateActions(args.actions || []);
    const width = args.width || 640, height = args.height || 360, frames = args.frames || 60;
    requireThat(width > 0 && width <= 1920 && height > 0 && height <= 1080 && frames > 0 && frames <= 600, 'INVALID_SIZE', '尺寸或帧数超出范围。');
    const directory = path.join(STATE, 'jobs', id('snapshot')); fs.mkdirSync(directory);
    const image = path.join(directory, 'page.png');
    const command = [pagePath(args.page), '--scale', '2', '--size', width + 'x' + height, '--snapshot', image];
    if (args.actions?.length) { const actionsFile = path.join(directory, 'actions.txt'); fs.writeFileSync(actionsFile, args.actions.join('\n')); command.push('--actions', actionsFile, '--out', directory); }
    else command.push('--frames', String(frames));
    if (args.boxes) command.push('--boxes', args.boxes);
    const r = await preview(command); return imageResult({ passed: r.code === 0 && fs.existsSync(image), image: fs.existsSync(image) ? image : null, exitCode: r.code, output: r.output });
  }),
  tool('oriel_review', 'prepare 创建经过检查的固定版本验收：mode=web（缺省）开本机验收网页，必须用户本人点击批准，Agent 不能设置批准；mode=screenshots 把检查截图原样返回，让用户在聊天里看图决定，用户同意后用 decide 逐字引用用户的话（回执标注为聊天批准，由 Agent 代记）。status 查询决定。', { action: { ...string, enum: ['prepare', 'status', 'decide'] }, projectId: string, reviewId: string, mode: { ...string, enum: ['web', 'screenshots'] }, decision: { ...string, enum: ['approved', 'rejected'] }, quote: string }, [], async args => reviewResult(await review(args))),
  tool('oriel_integrate', '仅接入用户本人批准的当前版本。target=oriel-dev 写入本仓库共同资源；target=oriel-folder 复制进 folder 指定的文件夹（通常是游戏的 config/oriel/my-pages，游戏里以 oriel_mine: 打开），不改仓库也不改你的模组。target=mod-project 写入 projectRoot 下游工程的资源目录，支持 modId/resourcesDir/pageName，回执给出打开代码与热更新指引。plan 返回写入清单；apply 检查文件冲突并安装，失败恢复本次写入。', { action: { ...string, enum: ['plan', 'apply'] }, reviewId: string, planId: string, target: { ...string, enum: ['oriel-dev', 'oriel-folder', 'mod-project'] }, folder: string, projectRoot: string, modId: string, resourcesDir: string, pageName: string }, [], args => integrate(args)),
  tool('oriel_game_preview', '仅在验收并接入后启动独立游戏预览任务。status 查询截图、精确版本及诊断；cancel 仅取消本工具任务。', { action: { ...string, enum: ['start', 'status', 'cancel'] }, projectId: string, jobId: string, scene: string }, [], args => gamePreview(args)),
  tool('oriel_pages', '列出本库 kit 页面及已有像素图标，制作前优先复用。', {}, [], () => ({
    pages: fs.readdirSync(path.join(KIT, 'assets/oriel/ui')).filter(f => f.endsWith('.html')),
    icons: fs.readdirSync(path.join(KIT, 'assets/oriel/textures/icons')).filter(f => f.endsWith('.png')).map(f => f.slice(0, -4)),
  }), true),
];
function validate(args, spec) {
  requireThat(args && typeof args === 'object' && !Array.isArray(args), 'INVALID_ARGS', '参数必须是对象。');
  for (const key of spec.required || []) requireThat(key in args, 'INVALID_ARGS', '缺少参数：' + key);
  for (const [key, value] of Object.entries(args)) {
    const field = spec.properties[key]; requireThat(field, 'INVALID_ARGS', '未知参数：' + key);
    const matches = field.type === 'array' ? Array.isArray(value) : field.type === 'integer' ? Number.isInteger(value) : field.type === 'object' ? value && typeof value === 'object' && !Array.isArray(value) : typeof value === field.type;
    requireThat(matches && (!field.enum || field.enum.includes(value)), 'INVALID_ARGS', '参数类型或取值错误：' + key);
  }
}
let queue = Promise.resolve();
async function handle(message) {
  const { method, params = {} } = message;
  if (method === 'initialize') return { protocolVersion: ['2024-11-05', '2025-03-26', '2025-06-18'].includes(params.protocolVersion) ? params.protocolVersion : '2025-06-18',
    capabilities: { tools: {}, resources: {}, prompts: {} }, serverInfo: { name: 'oriel-ui-workflow', version: '2.0.0' }, instructions: instructions() };
  if (method === 'ping') return {};
  if (method === 'tools/list') return { tools: TOOLS.map(({ run, ...metadata }) => metadata) };
  if (method === 'resources/list') return { resources: Object.entries(topics).map(([name, file]) => ({ uri: 'oriel://rules/' + name, name, description: '当前能力合同：' + file, mimeType: 'text/markdown' })) };
  if (method === 'resources/read') {
    requireThat(typeof params.uri === 'string' && params.uri.startsWith('oriel://rules/'), 'UNKNOWN_RESOURCE', '未知资源 URI。');
    const rule = rules({ topic: params.uri.slice('oriel://rules/'.length) });
    return { contents: [{ uri: params.uri, mimeType: 'text/markdown', text: rule.text }] };
  }
  if (method === 'prompts/list') return { prompts: ['workflow', 'revise', 'integrate'].map(name => ({ name, description: { workflow: '从网页制作到用户验收及游戏接入', revise: '修改已制作页面并重新验收', integrate: '把已批准版本接入游戏' }[name] })) };
  if (method === 'prompts/get') return { description: 'Oriel 标准工作流', messages: [{ role: 'user', content: { type: 'text', text: prompt(params.name) } }] };
  if (method === 'tools/call') {
    const selected = TOOLS.find(t => t.name === params.name);
    if (!selected) throw new WorkflowError('UNKNOWN_TOOL', '未知工具：' + params.name);
    const invoke = async () => {
      try { validate(params.arguments || {}, selected.inputSchema); const value = await selected.run(params.arguments || {}); return value?.content ? value : result(value); }
      catch (error) { return result({ code: error.code || 'INTERNAL_ERROR', message: error.message, details: error.details || {} }, true); }
    };
    const pending = queue.then(invoke); queue = pending.catch(() => {}); return pending;
  }
  throw new WorkflowError('METHOD_NOT_FOUND', '不支持的 MCP 方法：' + method);
}
const send = value => process.stdout.write(JSON.stringify(value) + '\n');
const lines = createInterface({ input: process.stdin });
lines.on('line', async line => {
  if (!line.trim()) return;
  let message;
  try { requireThat(line.length <= 1_000_000, 'MESSAGE_TOO_LARGE', '消息过大。'); message = JSON.parse(line); }
  catch { send({ jsonrpc: '2.0', id: null, error: { code: -32700, message: '无效 JSON 消息。' } }); return; }
  if (message.id === undefined) return;
  try { send({ jsonrpc: '2.0', id: message.id, result: await handle(message) }); }
  catch (error) { send({ jsonrpc: '2.0', id: message.id, error: { code: error.code === 'METHOD_NOT_FOUND' ? -32601 : -32602, message: error.message } }); }
});
