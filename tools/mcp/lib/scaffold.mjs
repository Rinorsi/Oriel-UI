import fs from 'node:fs';
import path from 'node:path';
import { STATE, safePath, requireThat, walk, sha, id, now, saveRecord, fileHash } from './core.mjs';
import { assertProjectRoot } from './mod-project.mjs';
import { registerProject } from './projects.mjs';
import { checkProject } from './checks.mjs';
import { approved } from './integration.mjs';
import { installFiles } from './transaction.mjs';
import { profileVersions } from './rules.mjs';

export const helloHtml = [
  '<!doctype html>', '<html data-oriel-shell><head><meta charset="utf-8"><title>Hello Oriel</title>',
  '<link rel="stylesheet" href="oriel:ui/ore.css">',
  '<style>html { font-family: oriel:pixel_12; font-size: 12px; }',
  '.o-titlebar h1 { font-size: 12px; } .page { flex: 1; padding: 12px; }</style></head>',
  '<body><div class="o-app"><div class="o-window">',
  '<div class="o-titlebar"><h1>Hello Oriel</h1></div>',
  '<div class="o-body"><div class="page"><p>你好，Oriel</p>',
  '<button class="ore-btn" data-action="close">关闭</button>',
  '</div></div></div></div></body></html>', ''
].join('\n');
const JAVA_WORDS = new Set('abstract assert boolean break byte case catch char class const continue default do double else enum exports extends final finally float for goto if implements import instanceof int interface long module native new package private protected public record requires return short static strictfp super switch synchronized this throw throws transient try var void volatile while true false null yield _'.split(' '));

// Inspect sources and configuration as text, never execute downstream Gradle code.
export function detectScaffold(args) {
  requireThat(typeof args.projectRoot === 'string' && path.isAbsolute(args.projectRoot) && !args.projectRoot.replaceAll(String.fromCharCode(92), '/').split('/').includes('..'), 'PROJECT_ROOT_REQUIRED', 'projectRoot 必须是已有工程绝对路径，不能含 ..。');
  const root = path.resolve(args.projectRoot);
  requireThat(fs.existsSync(root) && fs.statSync(root).isDirectory(), 'PROJECT_ROOT_REQUIRED', '工程目录不存在。');
  assertProjectRoot(root);
  const read = rel => { const f = safePath(root, rel); return fs.existsSync(f) && fs.statSync(f).isFile() ? fs.readFileSync(f, 'utf8') : ''; };
  const configs = ['settings.gradle', 'settings.gradle.kts', 'build.gradle', 'build.gradle.kts', 'gradle.properties', 'stonecutter.gradle', 'stonecutter.gradle.kts', 'stonecutter.properties.toml'];
  for (const module of ['fabric', 'neoforge', 'common']) for (const name of ['build.gradle', 'build.gradle.kts', 'gradle.properties']) configs.push(module + '/' + name);
  const config = configs.map(file => ({ file, text: read(file) })).filter(f => f.text);
  const combined = config.map(f => f.text).join('\n');
  const resourceCandidates = ['src/main/resources', 'common/src/main/resources', 'fabric/src/main/resources', 'neoforge/src/main/resources'].filter(rel => fs.existsSync(safePath(root, rel)));
  for (const rel of resourceCandidates) requireThat(fs.statSync(safePath(root, rel)).isDirectory(), 'INVALID_RESOURCE_DIRECTORY', '资源路径必须是目录：' + rel);
  const resourcesDir = args.resourcesDir ?? (resourceCandidates.includes('common/src/main/resources') ? 'common/src/main/resources' : resourceCandidates.length === 1 ? resourceCandidates[0] : undefined);
  if (resourcesDir) { requireThat(!resourcesDir.replaceAll(String.fromCharCode(92), '/').split('/').includes('..'), 'PATH_ESCAPE', 'resourcesDir 不能含 ..。'); safePath(root, resourcesDir); }
  if (resourcesDir && fs.existsSync(safePath(root, resourcesDir))) requireThat(fs.statSync(safePath(root, resourcesDir)).isDirectory(), 'INVALID_RESOURCE_DIRECTORY', 'resourcesDir 必须是目录。');
  const ids = new Set(), loaders = new Set(), versions = new Set(), packages = new Set();
  for (const entry of config.filter(f => f.file.endsWith('gradle.properties'))) {
    for (const match of entry.text.matchAll(/^\s*(?:mod_id|modId)\s*[=:]\s*([a-z][a-z0-9_]{1,40})\s*$/gm)) ids.add(match[1]);
  }
  const metadata = [];
  for (const rel of [...new Set([...resourceCandidates, ...(resourcesDir ? [resourcesDir] : [])])]) {
    const fabric = read(rel + '/fabric.mod.json');
    if (fabric) {
      let value; try { value = JSON.parse(fabric.replace(/^\uFEFF/, '')); } catch { requireThat(false, 'INVALID_METADATA', '无法解析 ' + rel + '/fabric.mod.json'); }
      loaders.add('fabric'); if (/^[a-z][a-z0-9_]{1,40}$/.test(value.id)) ids.add(value.id);
      if (typeof value.depends?.minecraft === 'string' && /^\d+\.\d+(?:\.\d+)?$/.test(value.depends.minecraft)) versions.add(value.depends.minecraft);
      metadata.push({ file: rel + '/fabric.mod.json', oriel: value.depends?.oriel ?? value.suggests?.oriel ?? null });
    }
    const neo = read(rel + '/META-INF/neoforge.mods.toml').replace(/^\s*#.*$/gm, '');
    if (neo) {
      loaders.add('neoforge');
      for (const section of neo.matchAll(/^\s*\[\[mods\]\]\s*$([\s\S]*?)(?=^\s*\[|(?![\s\S]))/gm)) { const match = section[1].match(/^\s*modId\s*=\s*["']([a-z][a-z0-9_]{1,40})["']/m); if (match) ids.add(match[1]); }
      const deps = [...neo.matchAll(/^\s*\[\[dependencies\.[^\]]+\]\]([\s\S]*?)(?=^\s*\[|(?![\s\S]))/gm)];
      const dep = deps.find(section => /^\s*modId\s*=\s*["']oriel["']/m.test(section[1]));
      metadata.push({ file: rel + '/META-INF/neoforge.mods.toml', oriel: dep?.[0] ?? null });
    }
  }
  if (/fabric-loom|fabric_loom|net\.fabricmc/.test(combined)) loaders.add('fabric');
  if (/net\.neoforged|neoForge\s*\{/.test(combined)) loaders.add('neoforge');
  for (const m of combined.matchAll(/(?:minecraft_version\s*[=:]\s*["']?|minecraft(?:Version)?\s*=\s*["']|version\s*\(\s*["']|vcsVersion\s*=\s*["']|com\.mojang:minecraft:)(\d+\.\d+(?:\.\d+)?)/g)) versions.add(m[1]);
  if (/stonecutter/i.test(combined)) for (const list of combined.matchAll(/\bversions\s*(?:\(([^)]*)\)|([^\n]+))/g)) {
    for (const version of (list[1] ?? list[2]).matchAll(/["'](\d+\.\d+(?:\.\d+)?)["']/g)) versions.add(version[1]);
  }
  const sourceRoots = ['src/client/java', 'src/main/java', 'fabric/src/client/java', 'fabric/src/main/java', 'neoforge/src/main/java', 'common/src/main/java'];
  for (const rel of sourceRoots) {
    const dir = safePath(root, rel); if (!fs.existsSync(dir)) continue;
    const files = walk(dir); requireThat(files.length <= 2000, 'PROJECT_TOO_LARGE', 'Java 源码目录过大，请使用较小工程。');
    for (const file of files.filter(f => f.endsWith('.java'))) { const m = read(rel + '/' + file).match(/^\s*package\s+([a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)*)\s*;/m); if (m) packages.add(m[1]); }
  }
  const only = set => set.size === 1 ? [...set][0] : undefined;
  const modId = args.modId ?? only(ids), loader = args.loader ?? only(loaders), minecraft = args.minecraft ?? only(versions);
  let javaPackage = args.javaPackage ?? only(packages);
  if (!javaPackage && packages.size) { const parts = [...packages][0].split('.'); while (parts.length && ![...packages].every(p => p === parts.join('.') || p.startsWith(parts.join('.') + '.'))) parts.pop(); if (parts.length >= 2) javaPackage = parts.join('.'); }
  if (modId) requireThat(/^[a-z][a-z0-9_]{1,40}$/.test(modId) && !['minecraft', 'oriel'].includes(modId), 'INVALID_NAMESPACE', 'modId 必须是下游模组命名空间。');
  if (javaPackage) requireThat(/^[a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)+$/.test(javaPackage) && !javaPackage.split('.').some(p => JAVA_WORDS.has(p)), 'INVALID_PACKAGE', 'javaPackage 必须是合法的 Java 包名。');
  if (loader) requireThat(['fabric', 'neoforge'].includes(loader), 'UNSUPPORTED_PROFILE', 'loader 为 fabric/neoforge。');
  if (minecraft) requireThat(profileVersions.includes(minecraft), 'UNSUPPORTED_PROFILE', '当前 Oriel 不支持此 Minecraft 版本。', { minecraft, supported: profileVersions });
  const javaCandidates = sourceRoots.filter(rel => fs.existsSync(safePath(root, rel))
    && (!loader || !rel.startsWith(loader === 'fabric' ? 'neoforge/' : 'fabric/'))
    && (!resourcesDir || rel.startsWith(resourcesDir.split('src/')[0])));
  const javaDir = args.javaDir ?? (javaCandidates.find(rel => rel.endsWith('src/client/java')) ?? (javaCandidates.length === 1 ? javaCandidates[0] : undefined));
  if (javaDir) { requireThat(/^(?:(?:fabric|neoforge|common)\/)?src\/(?:main|client)\/java$/.test(javaDir), 'INVALID_JAVA_DIRECTORY', 'javaDir 必须是标准的工程内 Java 源码目录。'); safePath(root, javaDir); }
  if (javaDir && fs.existsSync(safePath(root, javaDir))) requireThat(fs.statSync(safePath(root, javaDir)).isDirectory(), 'INVALID_JAVA_DIRECTORY', 'javaDir 必须是目录。');
  const missing = Object.entries({ resourcesDir, modId, loader, minecraft, javaPackage, javaDir }).filter(([, v]) => !v).map(([k]) => k);
  const hotReload = args.launchMode ? { mode: args.launchMode, case: { 'gradle-inside': 2, 'gradle-outside': 3, jar: 4 }[args.launchMode] } : { mode: null, needs: 'launchMode', choices: ['gradle-inside', 'gradle-outside', 'jar'] };
  requireThat(!args.launchMode || hotReload.case, 'INVALID_LAUNCH_MODE', 'launchMode 为 gradle-inside/gradle-outside/jar。');
  if (!args.launchMode) missing.push('launchMode');
  const result = { projectRoot: root, loader, minecraft, stonecutter: /stonecutter/i.test(combined), modId, resourcesDir, javaPackage, javaDir, hotReload, missing, candidates: { resourcesDir: resourceCandidates, loader: [...loaders], minecraft: [...versions], modId: [...ids], javaPackage: [...packages], javaDir: javaCandidates }, existingOriel: { gradle: config.flatMap(f => f.text.split('\n').filter(line => /(?:dev\.oriel|oriel[-_:])/.test(line)).map(line => ({ file: f.file, line: line.trim() }))), metadata } };
  if (missing.some(field => field !== 'launchMode')) return { ...result, status: 'needs-information' };
  const pageUrl = modId + ':ui/hello/index.html';
  const java = ['package ' + javaPackage + ';', '', 'import dev.oriel.mod.client.OrielScreens;', '', '// Client-only: never call from a common initializer.', 'public final class OrielHello {', '    private OrielHello() {}', '', '    public static void open() {', '        OrielScreens.open("' + pageUrl + '");', '    }', '}', ''].join('\n');
  const registration = loader === 'fabric'
    ? ['// In ClientModInitializer.onInitializeClient (requires Fabric API):', 'net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->', '    dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("' + modId + '-hello")', '        .executes(ctx -> { net.minecraft.client.Minecraft.getInstance().schedule(' + javaPackage + '.OrielHello::open); return 1; })));'].join('\n')
    : ['// Register in client-only initialization:', 'net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(', '    (net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) -> event.getDispatcher().register(', '        net.minecraft.commands.Commands.literal("' + modId + '-hello").executes(ctx -> {', '            net.minecraft.client.Minecraft.getInstance().schedule(' + javaPackage + '.OrielHello::open); return 1;', '        })));'].join('\n');
  const module = resourcesDir.split('src/')[0];
  result.hotReload.instruction = !hotReload.case ? '请提供 launchMode；不能仅凭资源目录猜测运行方式。' : hotReload.case === 2 ? '工程内 Gradle runClient：自动热更新。' : hotReload.case === 3 ? '-Doriel.sourceRoot=' + path.resolve(root, module || '.') : '在游戏 config/oriel/dev-sources.properties 写入 ' + modId + '=' + path.join(root, resourcesDir, 'assets', modId);
  return { ...result, status: missing.length ? 'needs-information' : 'ready', pageUrl, snippets: { gradle: 'repositories { mavenLocal() }\ndependencies { implementation("dev.oriel:oriel-' + loader + '-' + minecraft + ':0.11.0") }', metadata: loader === 'fabric' ? '"depends": { "oriel": ">=0.11.0 <0.12.0" }' : '[[dependencies.' + modId + ']]\nmodId="oriel"\ntype="required"\nversionRange="[0.11.0,0.12.0)"\nordering="NONE"\nside="CLIENT"', open: javaPackage + '.OrielHello.open();', registration }, files: [{ file: 'assets/' + modId + '/ui/hello/index.html', target: resourcesDir + '/assets/' + modId + '/ui/hello/index.html', content: helloHtml }, { file: 'browser/OrielHello.java', target: javaDir + '/' + javaPackage.replaceAll('.', '/') + '/OrielHello.java', content: java }], notes: ['依赖片段需先 publishToMavenLocal；按现有 Gradle DSL 合并，不替换已有依赖。', 'Stonecutter 依赖放入加载器构建脚本，逐目标使用匹配的 Minecraft 工件。', '工具不安装库、不改构建与元数据、不注册命令；Java 只能由客户端入口引用。', '复制成功不等于下游编译或游戏验证。'] };
}
const binding = plan => sha(JSON.stringify(Object.fromEntries(['projectRoot', 'loader', 'minecraft', 'stonecutter', 'modId', 'resourcesDir', 'javaPackage', 'javaDir', 'hotReload', 'files', 'snippets'].map(key => [key, plan[key]]))));

export async function scaffold(args) {
  const plan = detectScaffold(args);
  if (plan.missing.length || (!args.prepare && !args.apply)) return plan;
  requireThat(!(args.prepare && args.apply), 'INVALID_ARGS', 'prepare 和 apply 不能同时为 true。');
  if (args.prepare) {
    const projectId = id('scaffold'), sourceRoot = path.join(STATE, 'plans', projectId, 'source');
    fs.mkdirSync(sourceRoot, { recursive: true });
    for (const file of plan.files) { const dest = safePath(sourceRoot, file.file); fs.mkdirSync(path.dirname(dest), { recursive: true }); fs.writeFileSync(dest, file.content); }
    fs.writeFileSync(path.join(sourceRoot, 'ui-package.json'), JSON.stringify(plan, null, 2) + '\n');
    registerProject({ projectId, sourceRoot, namespace: plan.modId, entry: 'ui/hello/index.html', profile: { loader: plan.loader, minecraft: plan.minecraft } });
    const check = await checkProject({ projectId });
    return { ...plan, projectId, check, nextActions: check.passed ? ['oriel_review.prepare（mode=screenshots），用户看图并批准后带 reviewId 调用 oriel_scaffold apply=true'] : ['修复检查错误后重新 prepare'], gameVerified: false };
  }
  requireThat(args.reviewId, 'APPROVAL_REQUIRED', 'apply 需要用户看图批准后的 reviewId；先 prepare，再 oriel_review。');
  const { revision, review } = approved(args.reviewId);
  requireThat(review.source === 'user-chat', 'APPROVAL_REQUIRED', '骨架写入需要截图验收后用户在聊天里明确同意。');
  const manifest = safePath(revision.pack, 'ui-package.json');
  requireThat(fs.existsSync(manifest) && binding(JSON.parse(fs.readFileSync(manifest, 'utf8'))) === binding(plan), 'SCAFFOLD_CHANGED', '目标工程、参数或生成内容已变化；重新 prepare/check/review。');
  const files = [], skipped = [];
  for (const file of plan.files) {
    requireThat(fileHash(safePath(revision.pack, file.file)) === sha(file.content), 'SCAFFOLD_CHANGED', '批准版本不是当前骨架。');
    const destination = safePath(plan.projectRoot, file.target);
    if (fs.existsSync(destination)) { skipped.push(file.target); continue; }
    files.push({ file: file.file, target: file.target, destination, hash: sha(file.content), previous: null, operation: 'create' });
  }
  assertProjectRoot(plan.projectRoot);
  const receipt = { id: id('scaffold-install'), kind: 'scaffold', reviewId: review.id, revisionId: revision.id, hash: revision.hash, projectRoot: plan.projectRoot, files, skipped, status: 'planned', createdAt: now(), gameVerified: false };
  saveRecord('plans', receipt);
  try { installFiles(files, revision.pack, plan.projectRoot, path.join(STATE, 'plans', receipt.id), fs.copyFileSync, { createOnly: true }); receipt.status = 'applied'; }
  catch (error) { receipt.status = 'failed'; receipt.error = error.message; receipt.rollbackConflicts = error.rollbackConflicts || []; saveRecord('plans', receipt); throw error; }
  receipt.appliedAt = now(); saveRecord('plans', receipt);
  return { ...receipt, pageUrl: plan.pageUrl, snippets: plan.snippets, hotReload: plan.hotReload, notes: plan.notes };
}
