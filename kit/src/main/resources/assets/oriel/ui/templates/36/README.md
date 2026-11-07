# 36 · 付款与投入反馈

- 作者 / 制作者：云诺羲
- 版本：0.1.0-preview
- 分类：事件动效与轨迹
- 介绍：商店付款、材料捐献、任务交付
- 入口：oriel:ui/templates/36/index.html

## 模板自带语言

名称、介绍、分类、标签与状态只编辑本目录 template.json 的 locales。当前语言无对应内容时按英文 → 中文 → 其他已有语言回退；空字段同样回退。玩家示例名不属于作者字段。

### zh_cn

- Name / 名称：付款与投入反馈
- Description / 介绍：商店付款、材料捐献、任务交付
- Category / 分类：事件动效与轨迹
- Status / 状态：代表原型，待游戏验收

### en_us

- Name / 名称：Payment feedback
- Description / 介绍：Show payment, spending and resource input feedback.
- Category / 分类：Events and motion
- Status / 状态：Prototype; in-game acceptance pending

修改后运行 node tools/templates/sync.mjs，同步只生成目录索引和文件清单，不会覆盖本文件夹的 template.json。
版权与素材来源见 SOURCES.md；目录归属不代表已完成游戏验收。
