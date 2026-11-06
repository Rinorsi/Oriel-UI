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

- **背包旁的 Oriel 效果面板默认关闭**（`client.inventoryEffects=false`）。以前默认开并替换原版列表；要用请在 `config/oriel.properties` 里设为 `true`。你的旧配置文件里如果已经写着 `true` 就仍然开着，自己改。
- 页面没处理 Escape 时，**回到上一个页面**，没有上一页才关闭；从模板中心的「演示」「我的页面」打开页面再按 Esc 回来，**回到原来的视图、排序、搜索、标签和滚动位置**，不再回到"全部"的顶部（10 分钟内有效）。
- 「我的页面」只列索引（标题、副标题、封面路径），封面图按需分批加载（每次 3 张，只加载正在显示和下一行的卡片），页面本身点开才加载；几十个页面不再一次全部加载。
- 按键设置里新增「优易库 Oriel UI → 打开优易库首页」：开发环境默认 F8，玩家环境默认不绑定。

### 新增

- 模板中心：**演示**（12 个整页示例，标"旧版自带"）、**我的页面**（列出 `config/oriel/my-pages`，标题、副标题、封面写在页面 `<head>`）、标签筛选；首页「演示」按钮。
- 纯 HTML 页面在 `<html>` 加 `data-oriel-shell` 就能用库的动效，不用 Java 控制器。
- **热更新**：下游模组自己的页面保存后约 1 秒刷新，五种情形和排错见 [热更新](hot-reload.md)；只丢 jar 开发用 `config/oriel/dev-sources.properties`。
- MCP：`oriel-folder` 接入目标、截图验收（聊天里同意）、`quickref` / `showcase` / `hotreload` 规则主题。
- 工具与文档：[做页面前先读](page-rules.md)、[Showcase 导览](showcase.md)（附 `tools/showcase/query.mjs` 查询脚本）、[热更新](hot-reload.md)。
- 预览器自带库资源，工程外的页面写 `oriel:ui/ore.css` 也能检查。

### 迁移步骤

1. 改依赖范围、jar 名、`oriel_version`。
2. 全文搜索 `/oriel demo`、`/oriel legacy`、`ITEM_EDITOR`、`ItemEditorPreset`，按上表替换或删除。
3. 如果你依赖了背包效果面板，在配置里打开 `client.inventoryEffects`。
4. 做页面前按 [热更新](hot-reload.md) 选一种配置，开始边改边看。
5. 用 `oriel_check`（或 `preview --check --strict`）检查页面，警告清零。

### 已知限制（0.11.0 仍有）

- 限高滚动框（`max-height` 加 `overflow-y:auto`）会让父级自动高度按完整内容算，见 [做页面前先读](page-rules.md) 的"已知引擎限制"。
- 热更新里图片和语言文件不热；服务端打开的页面在源码路径下是否热更新没有验证。
- UI 音效只有脚本的 `oriel.playSound` 和默认点击声，kit 与 showcase 页面还没有设计音效。

## 0.9.0 → 0.10.0

不兼容边界：画布专属的槽位回调变成 `DocumentDriver.SlotSink`，`dev.oriel.mod.internal` 不再是可调用 API；依赖范围 `[0.10.0,0.11.0)`。详见 `CHANGELOG.md` 的 0.10.0 一节和 [接入指南](integration.md) 的版本部分。

## 维护这一页

- 每次不兼容变更或用户能感觉到的行为变化，**同一次变更**在这里加一段迁移说明（"之前 → 现在"）。
- 新版本开头先复制上一节的结构：要改的地方、行为变化、新增、迁移步骤、已知限制。
- `CHANGELOG.md` 写英文历史，这一页写怎么迁移，二者都要更新。
