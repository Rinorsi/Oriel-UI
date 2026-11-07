# 组件编辑器

- 作者 / 制作者：云诺羲
- 版本：0.2.0
- 分类：菜单设置
- 介绍：拖动、对齐与属性面板的界面编辑器原型
- 入口：oriel:ui/templates/57/index.html

## 模板自带语言

名称、介绍、分类、标签与状态只编辑本目录 template.json 的 locales。当前语言无对应内容时按英文 → 中文 → 其他已有语言回退；空字段同样回退。玩家示例名不属于作者字段。

### zh_cn

- Name / 名称：组件编辑器
- Description / 介绍：拖动、对齐与属性面板的界面编辑器原型
- Category / 分类：菜单设置
- Status / 状态：

### en_us

- Name / 名称：Component editor
- Description / 介绍：A UI editor prototype: drag, align and a properties panel
- Category / 分类：Menus
- Status / 状态：

修改后运行 node tools/templates/sync.mjs，同步只生成目录索引和文件清单，不会覆盖本文件夹的 template.json。
版权与素材来源见 SOURCES.md；目录归属不代表已完成游戏验收。
