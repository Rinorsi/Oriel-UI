# 01 · 提示与成就弹条

- 作者 / 制作者：云诺羲
- 版本：0.1.0-preview
- 分类：基础组件与轻量提示
- 介绍：保存结果、物品获得、成就和错误提示
- 入口：oriel:ui/templates/01/index.html

## 模板自带语言

名称、介绍、分类、标签与状态只编辑本目录 template.json 的 locales。当前语言无对应内容时按英文 → 中文 → 其他已有语言回退；空字段同样回退。玩家示例名不属于作者字段。

### zh_cn

- Name / 名称：提示与成就弹条
- Description / 介绍：保存结果、物品获得、成就和错误提示
- Category / 分类：基础组件与轻量提示
- Status / 状态：用户已验收（代表版本）

### en_us

- Name / 名称：Achievements and notices
- Description / 介绍：Saved results, item rewards, achievements and error notices.
- Category / 分类：Controls and notices
- Status / 状态：Representative page accepted by the user

修改后运行 node tools/templates/sync.mjs，同步只生成目录索引和文件清单，不会覆盖本文件夹的 template.json。
版权与素材来源见 SOURCES.md；目录归属不代表已完成游戏验收。
