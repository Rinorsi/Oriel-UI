import fs from 'node:fs';
import path from 'node:path';
import { safePath, requireThat, fileHash, identifier } from './core.mjs';

export function assertProjectRoot(root) {
  for (let part = path.resolve(root); ; part = path.dirname(part)) {
    requireThat(!fs.lstatSync(part).isSymbolicLink(), 'SYMLINK', '工程路径不允许符号链接或目录联接。');
    if (part === path.dirname(part)) break;
  }
  safePath(root, '.');
  return root;
}

// The transaction root is the project, so apply checks every destination ancestor again.
export function modProjectFiles(revision, { projectRoot, modId, resourcesDir, pageName }) {
  requireThat(typeof projectRoot === 'string' && path.isAbsolute(projectRoot) && !projectRoot.split(/[\\/]/).includes('..'), 'PROJECT_ROOT_REQUIRED', 'projectRoot 必须是已有工程的绝对路径，不能含 ..。');
  const root = path.resolve(projectRoot);
  requireThat(fs.existsSync(root) && fs.statSync(root).isDirectory(), 'PROJECT_ROOT_REQUIRED', 'projectRoot 必须是已有工程目录。');
  assertProjectRoot(root);
  const candidates = ['common/src/main/resources', 'src/main/resources', 'fabric/src/main/resources', 'neoforge/src/main/resources'];
  const existing = candidates.filter(rel => { const file = safePath(root, rel); return fs.existsSync(file) && fs.statSync(file).isDirectory(); });
  if (resourcesDir === undefined) resourcesDir = existing.includes(candidates[0]) ? candidates[0] : existing.length === 1 ? existing[0] : undefined;
  requireThat(resourcesDir, 'RESOURCE_DIRECTORY_REQUIRED', '不能唯一确定资源目录，请从候选中选择并传 resourcesDir（工程内相对路径）。', { candidates: existing.length ? existing : candidates });
  requireThat(!resourcesDir.split(/[\\/]/).includes('..'), 'PATH_ESCAPE', '资源目录不能含 ..。');
  const resourceRoot = safePath(root, resourcesDir);
  requireThat(!fs.existsSync(resourceRoot) || fs.statSync(resourceRoot).isDirectory(), 'RESOURCE_DIRECTORY_REQUIRED', '资源目录必须是目录。');
  const ids = new Set();
  for (const rel of modId === undefined ? [...new Set([resourcesDir, ...existing])] : []) {
    for (const name of ['fabric.mod.json', 'META-INF/neoforge.mods.toml']) {
      const file = safePath(root, rel + '/' + name);
      if (!fs.existsSync(file)) continue;
      const text = fs.readFileSync(file, 'utf8');
      if (name.endsWith('.json')) {
        let value;
        try { value = JSON.parse(text.replace(/^\uFEFF/, '')); }
        catch { requireThat(false, 'MOD_ID_REQUIRED', '无法解析模组元数据，请明确传 modId。', { file }); }
        if (typeof value?.id === 'string' && /^[a-z][a-z0-9_]{1,63}$/.test(value.id)) ids.add(value.id);
      } else {
        const sections = text.replace(/^\s*#.*$/gm, '').matchAll(/^\s*\[\[mods\]\]\s*$([\s\S]*?)(?=^\s*\[|(?![\s\S]))/gm);
        for (const section of sections) {
          const match = section[1].match(/^\s*modId\s*=\s*["']([a-z][a-z0-9_]{1,63})["']/m);
          if (match) ids.add(match[1]);
        }
      }
    }
  }
  modId ??= ids.size === 1 ? [...ids][0] : undefined;
  requireThat(typeof modId === 'string' && /^[a-z][a-z0-9_]{1,63}$/.test(modId) && !['oriel', 'minecraft'].includes(modId), 'MOD_ID_REQUIRED', '请传 modId；元数据没有唯一的字面量模组 id。', { candidates: [...ids] });
  pageName = identifier(pageName ?? revision.projectId);
  const prefix = 'assets/' + revision.project.namespace + '/';
  const ownFiles = revision.files.filter(f => f.file.startsWith(prefix));
  const base = ownFiles.every(f => f.file.startsWith(prefix + 'ui/')) ? 'ui/' : '';
  // Preserve approved bytes; namespace-qualified self references require another review after relocation.
  const files = ownFiles.map(f => {
    if (/\.(html|css|js|json)$/i.test(f.file)) requireThat(!fs.readFileSync(safePath(revision.pack, f.file), 'utf8').includes(revision.project.namespace + ':'), 'MOD_NAMESPACE_REFERENCE', '接入会移动页面目录；请将自身命名空间引用改为相对路径后重新检查和验收：' + f.file);
    const relative = f.file.slice(prefix.length + base.length);
    const target = resourcesDir + '/assets/' + modId + '/ui/' + pageName + '/' + relative;
    const destination = safePath(root, target);
    return { file: f.file, target, hash: f.hash, destination, previous: fileHash(destination), operation: fs.existsSync(destination) ? 'update' : 'create' };
  });
  const pageUrl = modId + ':ui/' + pageName + '/' + revision.project.entry.slice(base.length);
  const sourceModule = path.dirname(path.dirname(path.dirname(resourceRoot)));
  const guidance = {
    openCode: 'import dev.oriel.mod.client.OrielScreens;\n\nOrielScreens.open("' + pageUrl + '");',
    registration: '在 Fabric 客户端初始化或 NeoForge 客户端事件中注册快捷键或客户端命令，在客户端线程调用打开代码；见 docs/zh/integration.md 第 4 节。',
    hotReload: 'Gradle runClient 且运行目录在工程内：自动热更新；运行目录在工程外：-Doriel.sourceRoot=' + sourceModule + '；仅 jar 开发：config/oriel/dev-sources.properties 写入 ' + modId + '=' + path.join(resourceRoot, 'assets', modId) + '。见 docs/zh/hot-reload.md 第 1 节（第 2/3/4 种情形）。',
    projectType: resourcesDir.startsWith('common/') ? 'multi-module' : resourcesDir.startsWith('fabric/') ? 'fabric' : resourcesDir.startsWith('neoforge/') ? 'neoforge' : 'single-module'
  };
  return { root, files, pageUrl, projectRoot: root, resourcesDir, modId, pageName, guidance };
}
