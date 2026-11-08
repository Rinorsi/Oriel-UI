import fs from 'node:fs';
import path from 'node:path';
import { ROOT, sha, requireThat, fileHash, walk } from './core.mjs';

export const topics = {
  orchestration: 'agent-orchestration.md', 'agent-tools': 'd36-agent-tools.md', benchmark: 'd36-mcp-bench.md',
  input: 'd36-input.md',
  layout: 'd36-r06.md',
  distribution: 'distribution.md',
  performance: 'performance.md',
  principles: 'principles.md', replication: 'replication.md', upgrading: 'upgrading.md', quickref: 'page-rules.md', hotreload: 'hot-reload.md', showcase: 'showcase.md', overview: 'capabilities.md', html: 'elements.md', css: 'css-guide.md', javascript: 'scripting.md',
  components: 'components.md', design: 'design.md', workflow: 'ui-workflow.md', integration: 'integration.md', validation: 'validation.md',
};
export const profileVersions = ['26.1.2', '26.2', '26.3'];
export function validateProfile(profile = {}) {
  const result = { minecraft: profile.minecraft || '26.1.2', loader: profile.loader || 'fabric', oriel: '0.11.0' };
  requireThat(profileVersions.includes(result.minecraft) && ['fabric', 'neoforge'].includes(result.loader), 'UNSUPPORTED_PROFILE', '请选择明确支持的 MC/加载器组合。', { versions: profileVersions });
  requireThat(!profile.oriel || profile.oriel === '0.11.0', 'UNSUPPORTED_PROFILE', '当前规则针对 Oriel 0.11.0。');
  return result;
}
export function instructions() {
  return '先调用oriel_capabilities了解合同与限制，再读oriel_rules（quickref为页面必读规则，showcase为现成示例索引，orchestration为协作合同）。页面默认iterate：确认用户现有游戏的热更新来源，严格检查后保存并提供双尺寸截图，不自行启动或重启游戏。正式deliver仍须project/check、固定版本review与用户本人批准，禁止Agent代点或用CLI绕过；只有有效回执才能plan/apply，批准前不得为工具自测启动游戏。资源或目标变化使旧批准失效。黄金图须独立baseline审批，不能挪用页面批准。调用oriel_status获取正式交付下一步。';
}
export function prompt(name = 'workflow') {
  requireThat(['workflow', 'revise', 'integrate'].includes(name), 'UNKNOWN_PROMPT', '提示词不存在。');
  const source = fs.readFileSync(path.join(ROOT, 'docs/zh/agent-prompts.md'), 'utf8').replaceAll('\r\n', '\n');
  return source.split('## ' + name + '\n')[1]?.split('\n## ')[0]?.trim() || source;
}
export function rulesIdentity() {
  const files = [...Object.values(topics), 'agent-prompts.md', 'mcp.md'];
  const implementation = ['oriel-preview.mjs', 'web-worker.mjs', 'game-worker.mjs', ...walk(path.join(ROOT, 'tools/mcp/lib')).map(file => 'lib/' + file), ...walk(path.join(ROOT, 'tools/mcp/schemas')).map(file => 'schemas/' + file)];
  return sha(JSON.stringify({ docs: files.map(file => [file, fs.existsSync(path.join(ROOT, 'docs/zh', file)) ? fs.readFileSync(path.join(ROOT, 'docs/zh', file), 'utf8') : '']), implementation: implementation.map(file => [file, fileHash(path.join(ROOT, 'tools/mcp', file))]) }));
}
export function rules({ topic = 'overview', feature, profile } = {}) {
  requireThat(topic in topics, 'UNKNOWN_TOPIC', '未知主题。', { topics: Object.keys(topics) });
  const file = 'docs/zh/' + topics[topic];
  const full = fs.readFileSync(path.join(ROOT, file), 'utf8');
  let text = full;
  if (feature) {
    requireThat(typeof feature === 'string' && feature.length <= 150, 'INVALID_QUERY', '查询词过长。');
    const lines = full.split('\n'), selected = new Set();
    lines.forEach((line, i) => { if (line.toLowerCase().includes(feature.toLowerCase())) for (let j = Math.max(0, i - 2); j <= Math.min(lines.length - 1, i + 3); j++) selected.add(j); });
    text = [...selected].sort((a, b) => a - b).map(i => (i + 1) + ': ' + lines[i]).join('\n');
  }
  return { profile: validateProfile(profile), rulesHash: rulesIdentity(), topic, source: file, found: !!text,
    interpretation: '结果是权威手册的匹配段落。未查到不代表支持；supported/unsupported/approximate/unverified 按段落中的条件判读。源码有实现不等于游戏已验证。',
    text: text || '没有查到该能力；请读取完整主题或使用已列支持的替代方式。', instructions: instructions(), topics: Object.keys(topics) };
}
