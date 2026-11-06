# 接入指南：怎么用、怎么接

[文档总目录](index.md)

给**下游模组作者**（和帮他们写代码的 AI）。读完你应该知道：我的界面属于哪一类、依赖怎么加、页面放哪、怎么打开、怎么和模组代码或服务端对话。

> 本页只写"怎么选、怎么串起来"。每个 API 的完整行为与限制见 [页面接口](pages.md)、[HUD 叠加层](hud.md)、[容器页面](containers.md)，方法签名速查见 [Java API](java-api.md)。

## 1. 先判断：你的界面属于哪一类

判断标准只有一条：**这个操作的结果会不会改变游戏世界里的真实数据？**

| 类别 | 例子 | 服务端要不要参与 | 用什么 |
|---|---|---|---|
| **A. 纯客户端界面** | 设置、音乐播放器、图鉴、主题切换 | 不需要，服务器不用装本库 | `OrielScreens.open(...)` |
| **B. 常驻 HUD** | 药水栏、任务追踪、提示条 | 数据客户端已有就不需要 | `OrielHud.register/show/push` |
| **C. 服务端驱动的界面** | 物品/组件编辑器、商店、排行榜 | 需要：服务端提供数据并校验操作 | `OrielServer.open(player, 页面, 数据)` |
| **D. 带物品槽的界面** | 锻造台、箱子、自定义背包 | 需要：物品在服务端，走原版菜单同步 | 注册菜单类型 + `OrielScreens.registerContainer` |

C 和 D 的原理与完整例子见 [服务端篇](server.md)。

## 2. 获取与依赖

只有 jar、没有仓库时，直接看 [只有 jar 时的最小 Fabric 工程](quickstart.md)（`implementation files(...)` 的写法，已验证）。

在本仓库里 `./gradlew build`，每个目标的 jar 在 `<加载器>/versions/<MC 版本>/build/libs/oriel-<加载器>-<MC 版本>-<版本>.jar`。把它放进测试环境的 `mods/`，开发时让你的工程依赖这个 jar。仓库的 `./gradlew templateCompile` 会把当前产物发布到 `build/template-maven` 并编译两个模板，不使用上游 Vellum 的 Maven。

模组 id 固定为 `oriel`。在你的 `neoforge.mods.toml` / `fabric.mod.json` 里声明为**必需依赖**；想把库打进自己的 jar，用 Jar-in-Jar（NeoForge 的 `jarJar`、Fabric 的 `include`）。多个模组都带了本库时，加载器须先满足全部消费者的版本约束，再选择一份可用版本；约束无交集应拒绝加载，不能保证总是选最新版。

支持的组合：Minecraft 26.1.2 / 26.2 / 26.3，Fabric 与 NeoForge，Java 25。

### 依赖写法

没有公开 Maven 仓库的承诺；不要使用上游 Vellum 仓库。在本仓库里 `./gradlew publishToMavenLocal` 把工件发布到本机，然后：

```groovy
repositories {
    mavenLocal()
}
// common/（针对原版编译）：API，加上它暴露的引擎类型
dependencies { compileOnly("dev.oriel:oriel-common-26.3:0.10.0") }
// neoforge/ 和 fabric/：加载器 jar，让开发运行时把 Oriel 当作 mod 加载（它自带引擎和 Rhino）
dependencies { implementation("dev.oriel:oriel-neoforge-26.3:0.10.0") }   // 或 oriel-fabric-26.3
```

没有仓库、只有 jar 时见[只有 jar 时的最小 Fabric 工程](quickstart.md)。每个 Minecraft 版本有自己的工件，版本号相同：

| Minecraft | Common | NeoForge | Fabric |
|---|---|---|---|
| 26.3 | `dev.oriel:oriel-common-26.3` | `dev.oriel:oriel-neoforge-26.3` | `dev.oriel:oriel-fabric-26.3` |
| 26.2 | `dev.oriel:oriel-common-26.2` | `dev.oriel:oriel-neoforge-26.2` | `dev.oriel:oriel-fabric-26.2` |
| 26.1.2 | `dev.oriel:oriel-common-26.1.2` | `dev.oriel:oriel-neoforge-26.1.2` | `dev.oriel:oriel-fabric-26.1.2` |

所有目标均使用 Java 25 和未混淆的 Minecraft API。Fabric 使用 `implementation`，无需旧版 remapping Loom。26.1/26.2 使用 GLFW 输入事件；26.3 使用 SDL。渲染包迁移等差异见[版本适配](versions.md)。

推荐的做法是必需依赖：`neoforge.mods.toml` 里 `[[dependencies.<modid>]] modId="oriel" type="required"`，`versionRange="[0.10.0,0.11.0)"`；`fabric.mod.json` 里 `"depends": {"oriel": ">=0.10.0 <0.11.0"}`；并在你的 Modrinth 和 CurseForge 页面上声明必需依赖，这样启动器会和你的 mod 一起安装它。玩家这样就能直接得到 Oriel 的修复，不用等你的下一个版本。

也可以把 Oriel 打进你的 jar，让玩家只装一个文件：

```groovy
// NeoForge (ModDevGradle)
dependencies { jarJar(implementation("dev.oriel:oriel-neoforge-26.3:0.10.0")) }
// Fabric (Loom)
dependencies { include(implementation("dev.oriel:oriel-fabric-26.3:0.10.0")) }
```

无论哪种方式，都要在 `neoforge.mods.toml` 或 `fabric.mod.json` 里保留必需依赖。多个 mod 都带了 Oriel、或玩家另外装了它时，加载器先满足全部消费者的版本约束再选一份可用版本，约束无交集会拒绝加载，不保证总是选最新版，所以实际加载的版本可能与你编译时用的不同。Oriel 仍然显示在 mod 列表里，并保留自己的配置文件。Rhino 的许可证和声明已在 Oriel 的 jar 里。

如果你的 mod 没有 Oriel 也能工作，就把依赖改成可选（`type="optional"`、`"suggests"`），并且：

- 在接触 `dev.oriel` 的类之前先确认 Oriel 已加载：
  - NeoForge：`ModList.get().isLoaded("oriel")`
  - Fabric：`FabricLoader.getInstance().isModLoaded("oriel")`
- 把面向 Oriel 的代码放进单独的类。这样它只在 Oriel 存在时才被类加载，你也可以在没有时退回原版界面。

两种方式都一样：

- 服务器调用在服务器线程运行；客户端调用在渲染线程运行。
- 页面 URL 是资源 id：`mymod:oriel/shop.html` 就是你 jar 里的 `assets/mymod/oriel/shop.html`（或资源包里的同名文件，所以资源包可以给你的界面换皮肤）。页面引用的样式表、脚本和图片相对它来解析，所以 `<link rel="stylesheet" href="shop.css">` 加载 `assets/mymod/oriel/shop.css`。

装了 Oriel 的服务器要求每个客户端都有匹配的 Oriel 协议。缺少或不兼容的客户端会被断开，并在两个加载器上都显示中英文说明。双方协商一个版本相关的能力；NeoForge 保持一个稳定的可选传输注册，使 Oriel 能送出同样的本地化说明，而不是加载器自己的消息。检查通过之前不会发送任何应用数据。断开提示文字来自内置的 en_us/zh_cn 语言文件，由服务器解析，因为缺少 Oriel 的客户端没有这些资源。装了 Oriel 的客户端仍然可以加入没装它的服务器，只使用纯本地页面。

### 稳定性与兼容性

公共 API 是：`dev.oriel.mod.server.OrielServer`、`OrielSession`、设置文件的键，以及 `dev.oriel.mod.client.OrielScreens`、`OrielScreen`、`OrielContainerScreen`、`OrielHud`、`OrielEntities`、`OrielAutomation` 和 `DocumentDriver` 的公共方法，再加上 `dev.oriel.mod.server.ForgeMenu` 及其槽位/数据合同。带 `@ApiStatus.Internal` 注解的成员是跨包的实现桥接，不是下游 API。所有实现类都在 `dev.oriel.mod.internal` 之下；即使 Java 的可见性是 public，也不要导入它们。

1.0 之前，不兼容的受支持 API 变更会提升 minor 版本（0.9 到 0.10），绝不会只提升补丁版本。这次边界清理引入了 0.10.0：旧的、专属画布的槽位回调变成 `DocumentDriver.SlotSink`，实现包不再是可调用 API。所以当前模板要求 `>=0.10.0 <0.11.0` / `[0.10.0,0.11.0)`。已有的 0.9 消费者在迁移之前必须留在 0.9 范围内：`>=0.9.0 <0.10.0` / `[0.9.0,0.10.0)`，它与 0.10 范围无交集。把两版嵌入不同的父 mod，不会自动修复公共 API 的二进制差异。跨 minor 版本必须迁移消费者并分别验证；只改版本元数据的夹具、共同 DOM 子集的无头通过，都不能证明游戏内或全部 API 兼容。实际发行 jar 的无头消费者探针用法见 [验证工具](../../tools/compat/README.md)。Jar-in-Jar 是可选的；推荐的做法是必需的外部依赖。更宽的范围需要测试，不能因为编译成功就推断它可行。

`./gradlew check` 包含用本仓库编译的两个加载器模板，以及每个 Minecraft 版本的 API 快照 `api/*.txt`。`:common:<版本>:checkPublicApi` 拒绝受支持签名里出现内部类型和签名漂移；经过审查后用 `-PupdateApi` 更新。`checkBackendBoundaries` 锁定引擎、kit 和预览不得导入游戏或加载器。这些在 fork 的内部实现演进时保护它。

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
- 开发环境下，保存页面文件会热重载已打开的页面，不用重启游戏。

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

控制器在页面解析完成、脚本运行之后、`DOMContentLoaded` 之前挂上。kit 里的 `Dom`、`Shell`、`Flip`、`Slider` 等是控制器常用的小工具（改 DOM、翻页过渡、FLIP 动画、滑块），看 `kit/src/main/java/dev/oriel/kit/` 的源码和 [做一个新页面](making-a-page.md) 的第 3 节。

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
- 我们自带的药水 HUD 是现成例子：`kit/.../preset/PotionHudPreset.java`、`potions.html`，数据来自玩家自己的药水效果。

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

**两端都必须安装**：服务器装了本库，加入的客户端就必须装（版本请与服务器保持一致），否则在进入服务器时被断开，并显示提示“此服务器需要安装优易库”。所以你的服务端逻辑里不需要为“客户端没装”准备后备界面。反过来，客户端装了本库、服务器没装是允许的，此时只能使用不需要服务端的界面（A、B 类里的纯客户端部分）。

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

| 命令 | 作用 |
|---|---|
| `/oriel showcase` | 打开首页（客户端） |
| `/oriel showcase catalog` | 组件与动效的活目录 |
| `/oriel open <url>` | 打开任意页面，如 `/oriel open mymod:oriel/shop.html` |
| `/oriel reload` | 重新加载所有已打开的页面（F3+T 也会） |
| `/oriel showcase chest` | 真实箱子菜单配页面槽位（服务端，需要管理员权限） |
| `/oriel showcase live` | 服务端会话示例（每秒推送玩家状态） |

配置文件 `config/oriel.properties`，所有键、默认值与范围见 [安全与设置](security.md#设置)。常用的：`client.serverPages`（`allow` / `ask` / `block`，是否显示服务器发来的页面）、`client.reducedMotion`、`client.shapeShader`、`client.inspector`（仅含开发工具的构建启用 F12 元素检查器，`-Poriel_release` 正式包忽略此开关）。

## 9. 开发你自己的页面：最短路径

通过 Agent 制作的新页面优先使用 [MCP 主工作流](ui-workflow.md)：网页预览、引擎检查、用户批准固定版本后再接入。首版自动接入适配器 oriel-dev 写入本库共同资源目录并提供游戏打开/验证入口；外部工程需明确适配器，不承诺自动生成服务器业务、容器菜单或权限处理。手动接入仍可按本页 API 完成。

1. 在本仓库的 kit 里加页面，用脚手架：`node tools/new-page.mjs bestiary "怪物图鉴"`；你自己的模组把页面放进自己的 `assets/<命名空间>/oriel/`（见第 3 节）。
2. 对着 [CSS 支持参考](css-reference.md) 写样式，不确定的先查表；写了不支持的东西会得到诊断，不会悄悄失效。
3. 无头检查：`./gradlew :preview:checkUi`，或让 AI 通过 MCP（`node tools/mcp/oriel-preview.mjs`）调用 `oriel_check`、`oriel_snapshot`。
4. 进游戏看：`./gradlew :fabric:26.1:runClient`，`/oriel open mymod:oriel/xxx.html`，F12 看元素盒子。
5. 要做自动验收：`OrielAutomation`（点击、输入、拖拽、等待页面稳定）见 [开发参考](development.md)。
