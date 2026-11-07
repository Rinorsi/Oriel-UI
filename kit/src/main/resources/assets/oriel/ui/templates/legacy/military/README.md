# 现代军武

- 作者 / 制作者：云诺羲
- 版本：v1.0
- 分类：旧版 / HUD
- 介绍：战地式的冷色 HUD：平移的罗盘带、旋转扫描的雷达、开火扩散的准星、受击方向和依次滑入的击杀信息。
- 入口：oriel:ui/templates/legacy/military/index.html

## 模板自带语言

名称、介绍、分类、标签与状态只编辑本目录 template.json 的 locales。当前语言无对应内容时按英文 → 中文 → 其他已有语言回退；空字段同样回退。玩家示例名不属于作者字段。

### zh_cn

- Name / 名称：现代军武
- Description / 介绍：战地式的冷色 HUD：平移的罗盘带、旋转扫描的雷达、开火扩散的准星、受击方向和依次滑入的击杀信息。
- Category / 分类：旧版 / HUD
- Status / 状态：旧版风格模板

### en_us

- Name / 名称：Modern Military
- Description / 介绍：A cool-toned Battlefield-style HUD: a sliding compass strip, a sweeping radar, a crosshair that spreads when firing, hit direction and kill notices sliding in one by one.
- Category / 分类：Legacy / HUD
- Status / 状态：Legacy style template

修改后运行 node tools/templates/sync.mjs，同步只生成目录索引和文件清单，不会覆盖本文件夹的 template.json。
版权与素材来源见 SOURCES.md；目录归属不代表已完成游戏验收。
