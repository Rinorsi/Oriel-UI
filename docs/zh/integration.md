# 接入指南：怎么用、怎么接

[文档总目录](index.md)

**当前版本与打包规则见[分发指南](distribution.md)。** 本页负责页面与业务接线；依赖方式、同 minor 范围和最终成品审计统一在分发指南维护。

给**下游模组作者**（和帮他们写代码的 AI）。读完你应该知道：我的界面属于哪一类、依赖怎么加、页面放哪、怎么打开、怎么和模组代码或服务端对话。

> 本页只写"怎么选、怎么串起来"。每个 API 的完整行为与限制见 [页面接口](pages.md)、[HUD 叠加层](hud.md)、[容器页面](containers.md)，方法签名速查见 [Java API](java-api.md)。

## 1. 先判断：你的界面属于哪一类

当前普通客户端页面可用 `screen.driver().uiScale(0..4)` 选择独立原生比例，
默认 0 跟随游戏；保持原 Document，宿主自行保存偏好。不要为此套工作区 CSS transform
或修改全局 GUI 选项。坐标转换及原版菜单槽限制见[页面接口](pages.md#独立页面比例)。

判断标准只有一条：**这个操作的结果会不会改变游戏世界里的真实数据？**

| 类别 | 例子 | 服务端要不要参与 | 用什么 |
|---|---|---|---|
| **A. 纯客户端界面** | 设置、音乐播放器、图鉴、主题切换 | 本地能力不依赖服务端 Oriel | `OrielScreens.open/openInline(...)` |
| **B. 常驻 HUD** | 药水栏、任务追踪、提示条 | 本地数据不依赖服务端 Oriel | `OrielHud.register/show/push` |
| **C. 服务端驱动的界面** | 物品/组件编辑器、商店、排行榜 | 需要：服务端提供数据并校验操作 | `OrielServer.open(player, 页面, 数据)` |
| **D. 带物品槽的界面** | 锻造台、箱子、自定义背包 | 需要：物品在服务端，走原版菜单同步 | 注册菜单类型 + `OrielScreens.registerContainer` |

C 和 D 的原理与完整例子见[服务端篇](server.md)。A/B 的本地消息监听与热重载不使用服务端会话；最终下游的注册项、端侧入口和连接兼容性须按目标验证。两加载器的环境与依赖声明见[安装方向与元数据](distribution.md#服务端到底要不要装-oriel)。

## 2. 获取与依赖

只有 jar、没有仓库时，直接看 [只有 jar 时的最小 Fabric 工程](quickstart.md)（本地 jar 依赖写法）。

在本仓库里不加 `-Poriel_slim` 构建完整版，加 `-Poriel_slim` 构建瘦包（同时排除开发工具）；给玩家的完整版另加 `-Poriel_release`。每个目标的 jar 在 `<加载器>/versions/<项目版本>/build/libs/oriel-<加载器>-<MC 版本>-<版本>.jar`（MC 26.1.2 的项目目录是 `26.1`）。把它放进测试环境的 `mods/`，开发时让你的工程依赖这个 jar。仓库的 `./gradlew templateCompile -Poriel_slim` 会把瘦包发布到 `build/template-maven` 并编译两个模板，不使用上游 Vellum 的 Maven。

模组 id 固定为 `oriel`。在你的 `neoforge.mods.toml` / `fabric.mod.json` 里声明为**必需依赖**；想把库打进自己的 jar，用 Jar-in-Jar（NeoForge 的 `jarJar`、Fabric 的 `include`）。多个模组都带了本库时，加载器须先满足全部消费者的版本约束，再选择一份可用版本；约束无交集应拒绝加载，不能保证总是选最新版。

支持的组合：Minecraft 26.1.2 / 26.2 / 26.3，Fabric 与 NeoForge，Java 25。

### 依赖写法

下游可外部依赖或嵌入瘦包，仓库 template/ 默认外部依赖。完整 Fabric include / NeoForge jarJar 与元数据片段见[分发指南](distribution.md#4-gradle-与加载器元数据)，不要仅更新元数据而遗漏实际工件。

从 Maven 消费 0.15.0 瘦包，目标坐标为 dev.oriel:oriel-<loader>-<mc>:0.15.0。loader 工件自包含，transitive=false；common 只需类型时可 compileOnly 使用同游戏版本的 loader 工件。只有 jar 时见[最小 Fabric 工程](quickstart.md)。Maven 不发布完整版。

页面与客户端 API 在渲染线程使用，服务端 API 在服务器线程使用。服务端打开页面、推送会话或提供菜单时两端需要匹配 Oriel；纯客户端本地页面/HUD 不使用服务端会话，但下游注册与连接仍须按目标验收。详见[安装方向](distribution.md#服务端到底要不要装-oriel)。

### 稳定性与兼容性

公共 API 是：`dev.oriel.mod.server.OrielServer`、`OrielSession`、设置文件的键，以及 `dev.oriel.mod.client.OrielScreens`、`OrielScreen`、`OrielContainerScreen`、`OrielHud`、`OrielEntities`、`OrielAutomation` 和 `DocumentDriver` 的公共方法。`ForgeMenu` 现在是参考内容，需要请复制到自己模组；不再属于公共 API。带 `@ApiStatus.Internal` 注解的成员是跨包的实现桥接，不是下游 API。所有实现类都在 `dev.oriel.mod.internal` 之下；即使 Java 的可见性是 public，也不要导入它们。

核心有任何变化都提升 minor；同 minor 完整版补丁只更新参考内容。公共 API、基础资源和自己的页面可以作为运行依赖；模板、showcase/demo、内部类与演示注册不能作为核心合同。需要示例时[复制到自己的模组](templates.md#怎么复用到你的模组)，再用瘦包检查类、资源、通信及业务。跨 minor 后重新编译验证，不仅放宽元数据范围。

`./gradlew check -Poriel_slim` 包含用本仓库编译的两个加载器模板，以及每个 Minecraft 版本的 API 快照 `api/*.txt`。`:common:<版本>:checkPublicApi` 拒绝受支持签名里出现内部类型和签名漂移；经过审查后用 `-PupdateApi` 更新。`checkBackendBoundaries` 锁定引擎、kit 和预览不得导入游戏或加载器。这些在 fork 的内部实现演进时保护它。

## 3. 页面放哪、URL 怎么写

页面是**资源**，放进你的模组或资源包：

```
src/main/resources/assets/mymod/oriel/shop.html      →  URL "mymod:oriel/shop.html"
src/main/resources/assets/mymod/oriel/shop.css       →  页面里 <link rel="stylesheet" href="shop.css">
src/main/resources/assets/mymod/oriel/icon.png       →  <img src="icon.png">
```

- URL 是 Minecraft 风格的标识符：`命名空间:路径`，**命名空间必须是你的模组 id**；路径没有强制目录，本手册示例统一用 `oriel/`。库自己的内置页面放在 `assets/oriel/ui/…` 与 `assets/oriel/oriel/…`，那只是库内部的习惯。相对引用按页面所在目录解析。
- 资源包可以覆盖你的页面，玩家因此能换皮肤。
- Ore 像素主题的结构和类名见 [制作流程](ui-workflow.md) 与 [设计规范](design.md)。不用主题，自己写 CSS 也完全可以。
- 开发环境下，保存页面文件会热重载已打开的页面，不用重启游戏。下游模组：运行目录在工程里时自动生效；运行目录在别处加 `-Doriel.sourceRoot=<工程目录>`；只把 jar 丢进 `mods/` 开发就在 `config/oriel/dev-sources.properties` 写一行 `命名空间=源码资源目录`（三种写法见 [快速入门](hot-reload.md)）。

## 4. 客户端打开页面（A 类）

```java
OrielScreen screen = OrielScreens.open("mymod:oriel/settings.html");
screen.driver().onMessage("save", value -> MyConfig.save(value.getAsJsonObject()));

OrielScreens.open("mymod:oriel/journal.html", journalJson);        // 带初始数据（页面里的 oriel.data）
OrielScreens.openInline("<h1>Hello</h1><p>{{ name }}</p>", data);   // 运行时生成的 HTML
```

页面与模组代码有两种对话方式（同一页可混用）：

| 方式 | 页面一侧 | Java 一侧 |
|---|---|---|
| 消息 | `oriel.send('save', {…})` | `driver().onMessage("save", value -> …)` |
| 数据 | `oriel.data`、`oriel.on('data', fn)`、模板 `{{ x }}` | `driver().push(json)` 整体替换；`patch(key, json)` 只改一个字段；`merge(obj)` |

常用的还有：`driver().onClose(...)`、`driver().onKey(...)`（接管页面不用的按键）、`OrielScreens.onPageLoad(url, driver -> …)`（页面每次加载时给它数据）、`screen.pauses(true)`（单人时暂停世界）。完整列表见 [页面接口](pages.md)。

### 用 Java 控制器写行为（kit 的做法）

页面只写 HTML/CSS，行为放进一个 Java 类：

```java
// 客户端初始化时注册：页面 URL → 控制器工厂
PageControllers.register("mymod:oriel/shop.html", ShopController::new);

final class ShopController implements PageControllers.Controller {
    @Override public void attach(Document doc) {
        Element buy = doc.getElementById("buy");
        buy.addEventListener("click", e -> doc.host().send("buy", "\"apple\""));   // 第二个参数是 JSON 文本；发给模组或服务端
    }
    @Override public Object saveState() { return state; }          // 热重载时保留
    @Override public void restoreState(Object saved) { state = (State) saved; }
}
```

控制器在页面解析完成、脚本运行之后、`DOMContentLoaded` 之前挂上。kit 里的 `Dom`、`Shell`、`Flip`、`Slider` 等是控制器常用的小工具（改 DOM、翻页过渡、FLIP 动画、滑块），看 `kit/src/main/java/dev/oriel/kit/` 的源码和 [做一个新页面](ui-workflow.md) 的第 3 节。

> 稳定性提示：[接入指南第 2 节](integration.md#2-获取与依赖) 把 `OrielServer`、`OrielSession`、`OrielScreens`、`OrielScreen`、`OrielContainerScreen`、`OrielHud`、`OrielAutomation`、`DocumentDriver` 的公共方法列为 API；`PageControllers`、`kit` 里的工具类目前**不在稳定承诺内**，1.0 前可能变。

## 5. HUD 叠加层（B 类）

```java
// 客户端初始化：
OrielHud.register(id("tracker"), "mymod:oriel/tracker.html");
OrielHud.show(id("tracker"));
OrielHud.push(id("tracker"), questJson);                         // 页面的 oriel.data
OrielHud.patch(id("tracker"), "progress", new JsonPrimitive(3)); // 只改一个字段
OrielHud.hide(id("tracker"));
```

- HUD 默认不接收输入；用 `OrielHud.Input.WHEN_CHAT_OPEN`、`WHEN_CURSOR_FREE` 或自己的判断函数，指定在哪些界面上可以用鼠标点它。
- 页面可以自己 `oriel.close()`（动画结束时），Toast 队列就是这样做的。
- 每帧都会画，页面要写得便宜：少用会触发重排的属性动画（见 [CSS 支持参考](css-reference.md) 里的"变化时"列）。
- 我们自带的药水 HUD 是现成例子：`kit/.../preset/PotionHudPreset.java`、`potions.html`。库里它只是预览（示例效果）；要显示真实效果，在你自己的模组里用 `GameEffects.provide(...)` 提供数据。要把它放到原版界面旁或替换原版效果图标，需要你的模组自己写 Mixin，Oriel 不注入。

详见 [HUD 叠加层](hud.md)。

## 6. 服务端驱动的界面（C 类）

```java
// 服务端线程：
OrielSession session = OrielServer.open(player, "mymod:oriel/shop.html", stockJson)
        .onMessage("buy", (p, value) -> Shop.buy(p, value.getAsString()))   // 在这里校验，再改真实数据
        .onClose(() -> Shop.forget(player));
session.push(updatedStockJson);               // 页面数据更新
session.patch("gold", new JsonPrimitive(12)); // 只更新一个字段
session.close();
```

页面必须在客户端存在（客户端装了你的模组，或服务器资源包提供了它）；纯服务端模组可以用 `OrielServer.openInline(player, html, data)` 把整页 HTML 发过去。**把页面发来的每条消息当成恶意输入**：校验玩家、校验范围、校验物品是否还在手上。本库已经替你做了限频、大小和嵌套深度限制、会话数上限，但"这个操作合不合理"只有你的代码知道。参考实现：`common/.../server/ItemEditor.java`。

**本节的服务端功能需要两端都有库**：装有 Oriel 的服务器会在玩家加入时通过 `requireOnClient` 拒绝未装或协议不兼容的客户端，给出双语提示，这是当前源码行为。客户端单装时只能使用本地能力，不能调用本节的 `OrielServer` 会话、服务端推送或 `ForgeMenu`。安装方向与目标验证要求见[分发指南](distribution.md#服务端到底要不要装-oriel)。`driver.serverSession()` 只是页面来源标记，不是服务器装库探测；没有统一服务端能力探测 API，等待服务端确认的业务必须自己设计回执和超时。

## 7. 带物品槽的界面（D 类）

```java
// 客户端初始化（两个加载器都要，在游戏加载完成之前）：
OrielScreens.registerContainer(MyMenus.FORGE, "mymod:oriel/forge.html");
```

```html
<section class="grid">
  <slot v-for="i in range(0, 27)" :index="i"></slot>
</section>
```

`<slot index="n">` 把菜单的第 n 个格子放到元素画出来的位置，格子跟着滚动、变换、动画走；点击、拖拽、Shift 点击、物品提示由原版处理，所以服务端同步不用你操心。服务端那一半（`MenuType`、`AbstractContainerMenu`、方块或命令里 `player.openMenu(...)`）是你的模组自己的，和不用本库时完全一样。示例：`/oriel showcase chest` 对应 `OrielDemos.java` 与 [容器页面](containers.md)。

## 8. 命令与配置

下表的 showcase / 演示项只适用于完整版；瘦包移除参考入口或明确说明不含演示内容。两变体都提供 `/oriel settings` 和 `/oriel about`。瘦包 Home 动作打开模组设置，`Shell.back()` 直接关闭；关于页模板头像回退到核心 emblem。我的页面仍可用 `/oriel open oriel_mine:<目录>/index.html` 打开并保留热重载。`ForgeMenu` 现在是参考内容，需要请复制到自己模组；只有完整版包含实现、默认 screen、`oriel:forge` 注册和 `/oriel-forge`，瘦包均不包含。公共容器绑定 API 保留。具体目标的入口、交互与业务仍须分别验证，不能用构建通过替代。

| 命令 | 作用 |
|---|---|
| `/oriel settings` | 打开模组设置（客户端，两变体） |
| `/oriel about` | 打开关于页（客户端，两变体） |
| `/oriel showcase` | 打开首页（客户端） |
| `/oriel showcase catalog` | 组件与动效的活目录 |
| `/oriel open <url>` | 打开任意页面，如 `/oriel open mymod:oriel/shop.html` |
| `/oriel reload` | 重新加载所有已打开的页面（F3+T 也会） |
| `/oriel showcase chest` | 真实箱子菜单配页面槽位（服务端，需要管理员权限） |
| `/oriel showcase live` | 服务端会话示例（每秒推送玩家状态） |

配置文件 `config/oriel.properties`，所有键、默认值与范围见 [安全与设置](security.md#设置)。常用的：`client.nestedNotice`（`on` / `off`，默认 `on`，设置页开关，仅抑制嵌套风险弹窗）、`client.serverPages`（`allow` / `ask` / `block`，是否显示服务器发来的页面）、`client.reducedMotion`、`client.shapeShader`、`client.inspector`（仅含开发工具的构建启用 F12 元素检查器，`-Poriel_release` 正式包忽略此开关）。

## 9. 开发你自己的页面：最短路径

通过 Agent 制作的新页面优先使用 [MCP 主工作流](ui-workflow.md)：网页预览、引擎检查、用户批准固定版本后再接入。首版自动接入适配器 oriel-dev 写入本库共同资源目录并提供游戏打开/验证入口；外部工程可用 mod-project 资源接入目标（见下文），不承诺自动生成服务器业务、容器菜单或权限处理。手动接入仍可按本页 API 完成。

1. 在本仓库的 kit 里加页面，用脚手架：`node tools/new-page.mjs bestiary "怪物图鉴"`；你自己的模组把页面放进自己的 `assets/<命名空间>/oriel/`（见第 3 节）。
2. 对着 [CSS 支持参考](css-reference.md) 写样式，不确定的先查表；写了不支持的东西会得到诊断，不会悄悄失效。
3. 无头检查：`./gradlew :preview:checkUi`，或让 AI 通过 MCP（`node tools/mcp/oriel-preview.mjs`）调用 `oriel_check`、`oriel_snapshot`。
4. 进游戏看：`./gradlew :fabric:26.1:runClient`，`/oriel open mymod:oriel/xxx.html`，F12 看元素盒子。
5. 要做自动验收：`OrielAutomation`（点击、输入、拖拽、等待页面稳定）见 [开发参考](development.md)。

### 用 MCP 写入下游模组资源

用户批准固定版本后，调用 oriel_integrate，参数 action=plan、reviewId、target=mod-project、projectRoot=C:/work/my-mod、modId=mymod、pageName=settings，再以回执 id 作为 planId 调用 apply。写入 src/main/resources/assets/mymod/ui/settings/，多模块优先 common；目录不明确返回候选供选择。完整参数、目录映射和限制见 [MCP 手册](mcp.md#下游模组目标mod-project)。

默认入口包的回执给出可复制的最小代码：
~~~java
import dev.oriel.mod.client.OrielScreens;

OrielScreens.open("mymod:ui/settings/index.html");
~~~

Fabric 在客户端初始化中注册快捷键或客户端命令，NeoForge 在客户端事件中注册；在客户端线程调用上述打开代码（见第 4 节）。工具只返回片段，不修改 Java、Gradle、元数据，不安装 jar。

热更新指引按资源目录标明工程类型，并提供 [热更新](hot-reload.md) 第 1 节三种模组开发情形：工程内 runClient 自动读取；外部运行目录设置 `-Doriel.sourceRoot=<含 src/main/resources 的子工程>`；仅 jar 开发设置 dev-sources.properties。实际运行方式不能单靠资源目录判断，按启动方式选择。复制成功不等于游戏验证，须在下游游戏打开验证，MCP 不为此目标启动库自己的客户端。

### 一步接入：检测并生成最小骨架

调用 MCP 工具 `oriel_scaffold`，只传绝对路径 `projectRoot` 即可只读检测 Fabric / NeoForge、Minecraft 版本、Stonecutter、mod id、资源目录、Java 包和已有 Oriel 依赖。返回 Gradle / 元数据依赖、客户端打开代码、客户端命令注册片段；默认不写工程，也不生成骨架包。仅解析已知文件的字面量，不执行 Gradle；动态值、多个版本或目录歧义会返回 `missing` / `candidates`，用同名参数补充，不能把推测当成检测结果。

热更新不能从源码推断实际启动方式：补 `launchMode=gradle-inside|gradle-outside|jar`，分别对应[热更新](hot-reload.md)第 1 节第 2/3/4 种情形。Stonecutter 多版本需明确选 `minecraft`，依赖片段合并到现有加载器构建脚本并按目标版本选工件。清单是可复制片段，不会安装库、改构建或覆盖整个 depends。

要写入示例：同一工具传 `prepare=true`，只在工作区生成固定包，调用与 `oriel_check` 相同的真实引擎严格检查及双尺寸截图。拿返回的 `projectId` 调用 `oriel_review`（`mode=screenshots`），展示截图和清单，用户在聊天里明确批准后逐字引用原话记录决定，再用原参数、`apply=true`、`reviewId` 写入。参数、目标、资源或规则改变须重新 prepare/check/review。

只新建两个不存在的文件：`assets/<modid>/ui/hello/index.html` 与所选源码目录/包下的 `OrielHello.java`；已有文件跳过并列在 `skipped`，重复执行幂等。不会改 Gradle、元数据或已有 Java；命令片段须自行合入客户端初始化，`OrielHello` 不能从公共/服务端入口引用。回执保留批准版本、写入摘要、跳过项及 `gameVerified=false`；下游编译、客户端效果和热更新仍需实际工程另行验证，不会启动 Oriel 自己的游戏。

## 接入已有模组

先确定页面显示的数据和动作，在自己的命名空间编写 HTML/CSS/JS；客户端用 OrielScreens 打开，服务端会话用 OrielServer 打开。把原来的按钮操作接到消息监听，状态变化通过 push 更新 DOM，避免反复重建文档而丢失焦点、滚动或草稿。真实槽位仍通过容器 API 接入，不以普通物品图标替代服务端物品栏逻辑。

资源目录、打开代码和消息回执见本页各节与[页面接口](pages.md)；制作与验证只按[制作流程](ui-workflow.md)进行，无需另一套迁移步骤。
