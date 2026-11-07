# 方块地下城

- 作者 / 制作者：云诺羲
- 版本：v1.0
- 分类：旧版 / HUD
- 介绍：方块地下城式的俯视冒险：等距地砖与熔岩、圆角石板面板、生命条与治疗药水、1 2 3 神器冷却、灵魂和绿宝石。
- 入口：oriel:ui/templates/legacy/mcdungeons/index.html

## 模板自带语言

名称、介绍、分类、标签与状态只编辑本目录 template.json 的 locales。当前语言无对应内容时按英文 → 中文 → 其他已有语言回退；空字段同样回退。玩家示例名不属于作者字段。

### zh_cn

- Name / 名称：方块地下城
- Description / 介绍：方块地下城式的俯视冒险：等距地砖与熔岩、圆角石板面板、生命条与治疗药水、1 2 3 神器冷却、灵魂和绿宝石。
- Category / 分类：旧版 / HUD
- Status / 状态：旧版风格模板

### en_us

- Name / 名称：Block Dungeons
- Description / 介绍：A Minecraft Dungeons-style top-down adventure: isometric tiles and lava, a rounded slate bar, health and a healing potion, artifact cooldowns on 1 2 3, souls and emeralds.
- Category / 分类：Legacy / HUD
- Status / 状态：Legacy style template

修改后运行 node tools/templates/sync.mjs，同步只生成目录索引和文件清单，不会覆盖本文件夹的 template.json。
版权与素材来源见 SOURCES.md；目录归属不代表已完成游戏验收。
