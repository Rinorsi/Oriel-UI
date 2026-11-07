# 更新与升级

库的版本变化写在两处：仓库根的 `CHANGELOG.md`（英文，逐版本的完整历史，0.9.0 及更早是上游 Vellum 的历史），和这一页（中文，**怎么从上一版迁移**）。版本规则：1.0 之前，不兼容的受支持 API 变更提升 minor 版本（0.10 → 0.11），依赖范围随之变化，不会只升补丁号。做法准则见 [产品定位与 agent 工作准则](principles.md)。

## 0.10.0 → 0.11.0（不兼容）

### 要改的地方

| 之前 | 现在 |
|---|---|
| 依赖范围 `[0.10.0,0.11.0)`（NeoForge）、`>=0.10.0 <0.11.0`（Fabric） | `[0.11.0,0.12.0)`、`>=0.11.0 <0.12.0`；jar 文件名和 `oriel_version` 也改成 0.11.0 |
| `/oriel edit`、`OrielKit.ITEM_EDITOR`、`ItemEditorPreset`、`LocalizedMessage.componentValue`、语言键 `oriel.ui.item_editor.*` / `oriel.server.item_editor.*` | **没有了**：物品编辑器不属于库，请在你自己的模组里做（参考副本在工作区 `research/oriel-item-editor-reference/`） |
| `/oriel demo`、`/oriel demo <名称>`、`/oriel legacy`、`/oriel showcase <页面>` | 合并为 `/oriel showcase [页面]`；药水 HUD 开关是 `/oriel showcase potions`；打开模板中心是 `/oriel showcase templates`；原来 `legacy` 里的演示改叫 `layout`、`animation`、`livedata`、`forms`、`titlecard`；服务端演示 `chest`、`live` 不变 |
| 文档里的 `OrielPages.folder(...)`（类实际在 `dev.oriel.mod.internal` 里） | 公开入口 `dev.oriel.mod.client.OrielPages`（`folder`、`isFolder`），已列入 `api/*.txt` |

### 行为变化（不改代码，但你会感觉到）

- 限高滚动框修复：`max-height` 配合 `overflow: auto|scroll|hidden` 的 flex 子元素，以限高后的尺寸参与父级自动高度和固有尺寸计算，内部完整内容仍可滚动。原来为绕开父级被撑高而写的固定 `height` 或外层固定高度容器，可按需改回 `max-height`（内容少时自动收缩）。已通过引擎布局/滚动回归；游戏效果需更新 jar 并重启后核验。

- Canvas 的 drawImage 复用采样缓冲，并新增原生凸四边形 fillQuad；旧页面继续可用，使用新接口的页面应检测方法存在。更新 Java 库需要替换 jar/重启开发客户端，页面热重载不会升级引擎。参数与错误见 [脚本合同](scripting.md)，绘制路线与测量要求见 [性能规范](performance.md)。

- **库不再改动原版界面。** 背包旁的 Oriel 效果面板、隐藏原版效果图标的药水 HUD 都**移除了**：`/oriel showcase potions` 现在只是用示例效果的预览页，不碰原版，也不再给玩家加效果。配置项 `client.inventoryEffects`、`client.hideVanillaInventoryEffects` 随之删除（旧配置文件里留着也无妨，会被忽略）。这类界面要放进游戏，需要用 Mixin 修改 Minecraft 自己的逻辑；Oriel 支持设计这类界面，但**不会注入**，请在你自己的模组里接入，见 [组件](components.md) 的"状态效果面板"。
- 页面没处理 Escape 时，**回到上一个页面**，没有上一页才关闭；从模板中心的「演示」「我的页面」打开页面再按 Esc 回来，**回到原来的视图、排序、搜索、标签和滚动位置**，不再回到"全部"的顶部（10 分钟内有效）。
- 「我的页面」只列索引（标题、副标题、封面路径），封面图按需分批加载（每次 3 张，只加载正在显示和下一行的卡片），页面本身点开才加载；几十个页面不再一次全部加载。
- 按键设置里新增「优易库 Oriel UI → 打开优易库首页」：开发环境默认 F8，玩家环境默认不绑定。

### 新增

- UI 声音 token：按钮、页签、开关与模态框在 Shell 一处接入；`oriel.playSound('tab')`、`data-oriel-sound="off"`、`client.uiSounds` 和 `client.uiSoundVolume`，详见[音效合同](capabilities.md#ui-音效)。默认悬停仍静音；旧完整事件 id 继续可用。

- 模板中心：**演示**（12 个整页示例，标"旧版自带"）、**我的页面**（列出 `config/oriel/my-pages`，标题、副标题、封面写在页面 `<head>`）、标签筛选；首页「演示」按钮。
- 纯 HTML 页面在 `<html>` 加 `data-oriel-shell` 就能用库的动效，不用 Java 控制器。
- **热更新**：下游模组自己的页面保存后约 1 秒刷新，五种情形和排错见 [热更新](hot-reload.md)；只丢 jar 开发用 `config/oriel/dev-sources.properties`。
- MCP：`oriel-folder` 接入目标、截图验收（聊天里同意）、`quickref` / `showcase` / `hotreload` 规则主题。
- 工具与文档：[做页面前先读](page-rules.md)、[Showcase 导览](showcase.md)（附 `tools/showcase/query.mjs` 查询脚本）、[热更新](hot-reload.md)。
- 预览器自带库资源，工程外的页面写 `oriel:ui/ore.css` 也能检查。

### 迁移步骤

1. 改依赖范围、jar 名、`oriel_version`。
2. 全文搜索 `/oriel demo`、`/oriel legacy`、`ITEM_EDITOR`、`ItemEditorPreset`，按上表替换或删除。
3. 如果你依赖了背包效果面板或药水 HUD 替换原版图标：它们不再由库提供，请在你自己的模组里用 `PotionHudPreset` / `InventoryEffectsPreset` 加 Mixin 实现（`GameEffects.provide` 提供数据）。
4. 做页面前按 [热更新](hot-reload.md) 选一种配置，开始边改边看。
5. 用 `oriel_check`（或 `preview --check --strict`）检查页面，警告清零。

### 已知限制（0.11.0 仍有）

- 热更新里图片和语言文件不热；服务端打开的页面在源码路径下是否热更新没有验证。

## 0.9.0 → 0.10.0

不兼容边界：画布专属的槽位回调变成 `DocumentDriver.SlotSink`，`dev.oriel.mod.internal` 不再是可调用 API；依赖范围 `[0.10.0,0.11.0)`。详见 `CHANGELOG.md` 的 0.10.0 一节和 [接入指南](integration.md) 的版本部分。

## 维护这一页

- 每次不兼容变更或用户能感觉到的行为变化，**同一次变更**在这里加一段迁移说明（"之前 → 现在"）。
- 新版本开头先复制上一节的结构：要改的地方、行为变化、新增、迁移步骤、已知限制。
- `CHANGELOG.md` 写英文历史，这一页写怎么迁移，二者都要更新。


## 模板目录元数据迁移（2026-11-07）


官方模板的名称、介绍、分类、标签和状态从全局语言键迁入每个目录的 template.json.locales。原顶层 name/description/category/status 迁到 locales.zh_cn；英文放 locales.en_us。catalog.json 现在是生成的路由索引，不再承载可编辑文案。详见 [官方模板目录](templates.md)。


当前语言缺失或字段为空时按英文、中文、其他已有语言回退。同步脚本读取单项元数据，不覆盖用户写入的翻译，也不修改全局语言文件。旧版模板的新保存副本改为完整目录 templates/legacy/<id>/；原 id.html 副本保持有效且不覆盖。模板中心的通用按钮文字仍使用全局语言资源。
