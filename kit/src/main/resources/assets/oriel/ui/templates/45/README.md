# 合成配方界面

- 作者 / 制作者：云诺羲
- 版本：0.1.0-preview
- 分类：游戏宿主 UI 原型
- 介绍：配方预览、合成台皮肤
- 入口：oriel:ui/templates/45/index.html

## 模板自带语言

名称、介绍、分类、标签与状态只编辑本目录 template.json 的 locales。当前语言无对应内容时按英文 → 中文 → 其他已有语言回退；空字段同样回退。玩家示例名不属于作者字段。

### zh_cn

- Name / 名称：合成配方界面
- Description / 介绍：配方预览、合成台皮肤
- Category / 分类：游戏宿主 UI 原型
- Status / 状态：代表原型，待游戏验收

### en_us

- Name / 名称：Crafting recipes
- Description / 介绍：A crafting recipe preview; real recipes are supplied by the host mod.
- Category / 分类：Game UI prototypes
- Status / 状态：Prototype; in-game acceptance pending

修改后运行 node tools/templates/sync.mjs，同步只生成目录索引和文件清单，不会覆盖本文件夹的 template.json。
版权与素材来源见 SOURCES.md；目录归属不代表已完成游戏验收。
