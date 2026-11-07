# 37 · 长期目标达成庆祝

- 作者 / 制作者：云诺羲
- 版本：0.1.0-preview
- 分类：事件动效与轨迹
- 介绍：全收集、累计挑战、长期成就
- 入口：oriel:ui/templates/37/index.html

## 模板自带语言

名称、介绍、分类、标签与状态只编辑本目录 template.json 的 locales。当前语言无对应内容时按英文 → 中文 → 其他已有语言回退；空字段同样回退。玩家示例名不属于作者字段。

### zh_cn

- Name / 名称：长期目标达成庆祝
- Description / 介绍：全收集、累计挑战、长期成就
- Category / 分类：事件动效与轨迹
- Status / 状态：性能获用户确认；其余待验收

### en_us

- Name / 名称：Milestone fireworks
- Description / 介绍：Celebrate long-term milestones with fireworks.
- Category / 分类：Events and motion
- Status / 状态：Performance confirmed; other checks pending

修改后运行 node tools/templates/sync.mjs，同步只生成目录索引和文件清单，不会覆盖本文件夹的 template.json。
版权与素材来源见 SOURCES.md；目录归属不代表已完成游戏验收。
