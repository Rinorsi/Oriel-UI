# 暗黑地牢

- 作者 / 制作者：云诺羲
- 版本：v1.1
- 分类：旧版 / HUD
- 介绍：哥特风的回合战：火把光照、四人小队与骷髅对峙、压力值、崩溃时的「偏执」和旁白。
- 入口：oriel:ui/templates/legacy/dungeon/index.html

## 模板自带语言

名称、介绍、分类、标签与状态只编辑本目录 template.json 的 locales。当前语言无对应内容时按英文 → 中文 → 其他已有语言回退；空字段同样回退。玩家示例名不属于作者字段。

### zh_cn

- Name / 名称：暗黑地牢
- Description / 介绍：哥特风的回合战：火把光照、四人小队与骷髅对峙、压力值、崩溃时的「偏执」和旁白。
- Category / 分类：旧版 / HUD
- Status / 状态：旧版风格模板

### en_us

- Name / 名称：Darkest Dungeon
- Description / 介绍：Gothic turn-based combat: torchlight, a party of four facing skeletons, stress, a "Paranoid" break and a narrator.
- Category / 分类：Legacy / HUD
- Status / 状态：Legacy style template

修改后运行 node tools/templates/sync.mjs，同步只生成目录索引和文件清单，不会覆盖本文件夹的 template.json。
版权与素材来源见 SOURCES.md；目录归属不代表已完成游戏验收。
