(function () {
  'use strict';
  function el(id) { return document.getElementById(id); }
  function text(id, value) { el(id).textContent = value; }
  function show(id, visible) { el(id).classList.toggle('hidden', !visible); }
  function copy(value) { return JSON.parse(JSON.stringify(value)); }
  var initial = { name: '霜锋', lore: '一把来自雪原的剑。\n寒意藏在锋刃之间。', glint: 'inherit', sharpness: 5, unbreaking: 3, mending: false, color: '#9dd5e1', bold: false, components: [], facing: 'north', waterlogged: 'false' };
  var draft = copy(initial), history = [], activePanel = 'basic', module = 'item', color = draft.color;
  function changed() { return Object.keys(initial).filter(function (key) { return JSON.stringify(initial[key]) !== JSON.stringify(draft[key]); }); }
  function update(key, value) { if (JSON.stringify(draft[key]) === JSON.stringify(value)) return; history.push(copy(draft)); if (history.length > 30) history.shift(); draft[key] = value; render(); }
  function feedback(message) { text('feedback', message); }
  function render() {
    var count = changed().length;
    text('change-status', count ? count + ' 项未保存修改' : '尚未修改');
    text('preview-name', module === 'block' ? '箱子' : draft.name || '钻石剑');
    el('preview-name').style.color = draft.color;
    el('preview-name').style.fontWeight = draft.bold ? '700' : '400';
    text('preview-lore', module === 'block' ? '朝向：' + draft.facing + '\n含水：' + draft.waterlogged : draft.lore);
    text('enchant-summary', module === 'block' ? '状态与容器数据分别编辑' : '锋利 ' + draft.sharpness + '\n耐久 ' + draft.unbreaking + (draft.mending ? '\n经验修补 I' : ''));
    el('enchant-summary').style.whiteSpace = 'pre-wrap';
    text('component-count', String(3 + draft.components.length));
    show('mending-row', draft.mending);
    el('undo').disabled = history.length === 0;
    el('output-json').value = JSON.stringify({ mode: 'demo', changedFields: changed(), draft: draft }, null, 2);
  }
  function sync() {
    el('item-name').value = draft.name; el('item-lore').value = draft.lore; el('glint').value = draft.glint;
    el('sharpness').value = String(draft.sharpness); el('unbreaking').value = String(draft.unbreaking);
    el('facing').value = draft.facing; el('waterlogged').value = draft.waterlogged; render();
  }
  function panel(name) {
    if (module === 'block') modulePick('item');
    activePanel = name;
    ['basic', 'enchant', 'effects', 'advanced', 'block'].forEach(function (key) { show('panel-' + key, key === name); });
    ['basic', 'enchant', 'effects', 'advanced'].forEach(function (key) { el('nav-' + key).classList.toggle('selected', key === name); });
    el('form-area').scrollTop = 0;
    var help = { basic: ['显示与文本', '自定义名称、说明和光效分别保存。恢复原型与显式关闭并不相同。'], enchant: ['附魔与适用关系', '附魔的普通最高等级、编码范围及互斥条件，需要分别判断。'], effects: ['组件与消费行为', '能添加和能产生效果是两种结论。具体物品仍需游戏验证。'], advanced: ['保留类型与层级', '高级数据不自动平铺；未知内容保留，不能因表单没显示而删除。'] }[name];
    if (help) { text('help-title', help[0]); text('help-copy', help[1]); }
  }
  function modulePick(value) {
    module = value;
    el('module-item').classList.toggle('selected', value === 'item'); el('module-block').classList.toggle('selected', value === 'block');
    ['basic', 'enchant', 'effects', 'advanced', 'block'].forEach(function (key) { show('panel-' + key, value === 'block' ? key === 'block' : key === activePanel); });
    text('object-title', value === 'block' ? '箱子' : '钻石剑'); text('object-id', value === 'block' ? 'minecraft:chest' : 'minecraft:diamond_sword');
    el('object-icon').setAttribute('src', value === 'block' ? 'oriel:textures/icons/chest.png' : 'oriel:textures/icons/sword.png');
    el('preview-icon').setAttribute('src', value === 'block' ? 'oriel:textures/icons/chest.png' : 'oriel:textures/icons/sword.png');
    text('help-title', value === 'block' ? '方块状态 ≠ NBT' : '编辑工作副本');
    text('help-copy', value === 'block' ? '朝向与含水属于方块状态。容器物品属于方块实体数据。' : '修改保留在当前页面，不写入真实物品。');
    render();
  }
  ['basic', 'enchant', 'effects', 'advanced'].forEach(function (name) { el('nav-' + name).addEventListener('click', function () { panel(name); }); });
  el('module-item').addEventListener('click', function () { modulePick('item'); });
  el('module-block').addEventListener('click', function () { modulePick('block'); });
  el('item-name').addEventListener('input', function () { update('name', this.value); });
  el('item-lore').addEventListener('input', function () { update('lore', this.value); });
  el('glint').addEventListener('change', function () { update('glint', this.value); });
  ['sharpness', 'unbreaking'].forEach(function (key) { el(key).addEventListener('change', function () { var value = Number(this.value); if (!Number.isInteger(value) || value < 1 || value > 255) { feedback('示例等级应为 1–255 的整数'); this.value = String(draft[key]); return; } update(key, value); }); });
  ['facing', 'waterlogged'].forEach(function (key) { el(key).addEventListener('change', function () { update(key, this.value); }); });
  el('add-enchant').addEventListener('click', function () { update('mending', true); feedback('已添加经验修补 · 演示'); });
  el('remove-mending').addEventListener('click', function () { update('mending', false); });
  el('undo').addEventListener('click', function () { if (history.length) { draft = history.pop(); sync(); feedback('已撤销最近一次修改'); } });
  el('reset').addEventListener('click', function () { history.push(copy(draft)); draft = copy(initial); sync(); feedback('已恢复演示初始数据'); });
  function openComponents() { show('component-modal', true); el('component-search').focus(); }
  el('add-component').addEventListener('click', openComponents); el('effects-add').addEventListener('click', openComponents);
  el('close-components').addEventListener('click', function () { show('component-modal', false); });
  document.querySelectorAll('.component-option').forEach(function (button) { button.addEventListener('click', function () { var key = this.getAttribute('data-component'); if (draft.components.indexOf(key) < 0) update('components', draft.components.concat([key])); show('component-modal', false); feedback('已加入演示目录：' + key); }); });
  el('component-search').addEventListener('input', function () { var query = this.value.toLowerCase(); document.querySelectorAll('.component-option').forEach(function (button) { button.style.display = button.textContent.toLowerCase().indexOf(query) >= 0 || button.getAttribute('data-component').indexOf(query) >= 0 ? 'flex' : 'none'; }); });
  el('field-search').addEventListener('input', function () { var query = this.value; document.querySelectorAll('.nav-item').forEach(function (button) { button.style.display = button.textContent.indexOf(query) >= 0 ? 'flex' : 'none'; }); });
  function formatPreview() { text('format-preview', el('format-text').value || '名称预览'); el('format-preview').style.color = color; el('format-preview').style.fontWeight = el('format-bold').checked ? '700' : '400'; }
  el('format-name').addEventListener('click', function () { color = draft.color; el('format-text').value = draft.name; el('format-bold').checked = draft.bold; formatPreview(); show('format-modal', true); });
  el('close-format').addEventListener('click', function () { show('format-modal', false); });
  el('format-text').addEventListener('input', formatPreview); el('format-bold').addEventListener('change', formatPreview);
  [['frost', '#9dd5e1'], ['gold', '#f0cc7e'], ['white', '#edf0e8']].forEach(function (pair) { el('color-' + pair[0]).addEventListener('click', function () { color = pair[1]; formatPreview(); }); });
  el('apply-format').addEventListener('click', function () { history.push(copy(draft)); draft.name = el('format-text').value; draft.color = color; draft.bold = el('format-bold').checked; sync(); show('format-modal', false); feedback('名称格式已应用到工作副本'); });
  function output() { show('output-panel', true); render(); }
  el('view-diff').addEventListener('click', output); el('advanced-output').addEventListener('click', output); el('attribute-details').addEventListener('click', function () { output(); feedback('属性修饰器演示：add_value / mainhand / amount 4'); });
  el('close-output').addEventListener('click', function () { show('output-panel', false); });
  el('save-draft').addEventListener('click', function () { output(); feedback('已生成页面内草稿 · 未保存到游戏或磁盘'); });
  document.addEventListener('keydown', function (event) { if (event.key === 'Escape' && (!el('component-modal').classList.contains('hidden') || !el('format-modal').classList.contains('hidden'))) { show('component-modal', false); show('format-modal', false); event.preventDefault(); } });
  for (var slot = 0; slot < 27; slot++) { var cell = document.createElement('button'); cell.className = 'slot'; cell.textContent = slot === 0 ? '◆' : ''; cell.setAttribute('title', '演示槽位 ' + slot); cell.addEventListener('click', function () { feedback(this.getAttribute('title') + ' · 未绑定真实容器'); }); el('slot-grid').appendChild(cell); }
  render();
}());
