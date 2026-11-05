# 接入指南：怎么用、怎么接

[文档总目录](index.md)

给**下游模组作者**（和帮他们写代码的 AI）。读完你应该知道：我的界面属于哪一类、依赖怎么加、页面放哪、怎么打开、怎么和模组代码或服务端对话。

> 本页只写"怎么选、怎么串起来"。每个 API 的完整签名与限制在 [docs/api/](../api/)（英文，来自上游，经过核对仍然有效的部分）。

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

在本仓库里 `./gradlew build`，每个目标的 jar 在 `<加载器>/versions/<MC 版本>/build/libs/oriel-<加载器>-<MC 版本>-<版本>.jar`。把它放进测试环境的 `mods/`，开发时让你的工程依赖这个 jar。

模组 id 固定为 `oriel`。在你的 `neoforge.mods.toml` / `fabric.mod.json` 里声明为**必需依赖**；想把库打进自己的 jar，用 Jar-in-Jar（NeoForge 的 `jarJar`、Fabric 的 `include`）。多个模组都带了本库时，加载器只加载最新的一份。

支持的组合：Minecraft 26.1.2 / 26.2 / 26.3，Fabric 与 NeoForge，Java 25。

0.10 系列推荐依赖范围：Fabric `>=0.10.0 <0.11.0`，NeoForge `[0.10.0,0.11.0)`。1.0 之前不兼容的公共 API 变更提升 minor 版本。必需外部依赖是推荐路径，Jar-in-Jar 仅为可选便利。仓库 `./gradlew templateCompile` 会将当前产物发布到 build/template-maven 并编译两个模板，不使用上游 Vellum Maven。

可选依赖（你的模组没有本库也能运行时）：先判断本库是否加载（`ModList.get().isLoaded("oriel")` / `FabricLoader.getInstance().isModLoaded("oriel")`），再使用 `dev.oriel` 的类；把用到本库的代码放进单独的类，没装时它不会被加载。

## 3. 页面放哪、URL 怎么写

页面是**资源**，放进你的模组或资源包：

```
src/main/resources/assets/mymod/oriel/shop.html      →  URL "mymod:oriel/shop.html"
src/main/resources/assets/mymod/oriel/shop.css       →  页面里 <link rel="stylesheet" href="shop.css">
src/main/resources/assets/mymod/oriel/icon.png       →  <img src="icon.png">
```

- URL 是 Minecraft 风格的标识符：`命名空间:路径`。相对引用按页面所在目录解析。
- 资源包可以覆盖你的页面，玩家因此能换皮肤。
- Ore 像素主题的结构和类名见 [做一个新页面](making-a-page.md) 与 [设计规范](design.md)。不用主题，自己写 CSS 也完全可以。
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

常用的还有：`driver().onClose(...)`、`driver().onKey(...)`（接管页面不用的按键）、`OrielScreens.onPageLoad(url, driver -> …)`（页面每次加载时给它数据）、`screen.pauses(true)`（单人时暂停世界）。完整列表见 [docs/api/pages.md](../api/pages.md)。

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

> 稳定性提示：`docs/api/setup.md` 把 `OrielServer`、`OrielSession`、`OrielScreens`、`OrielScreen`、`OrielContainerScreen`、`OrielHud`、`OrielAutomation`、`DocumentDriver` 的公共方法列为 API；`PageControllers`、`kit` 里的工具类目前**不在稳定承诺内**，1.0 前可能变。

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

详见 [docs/api/hud.md](../api/hud.md)。

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

`<slot index="n">` 把菜单的第 n 个格子放到元素画出来的位置，格子跟着滚动、变换、动画走；点击、拖拽、Shift 点击、物品提示由原版处理，所以服务端同步不用你操心。服务端那一半（`MenuType`、`AbstractContainerMenu`、方块或命令里 `player.openMenu(...)`）是你的模组自己的，和不用本库时完全一样。示例：`/oriel demo chest` 对应 `OrielDemos.java` 与 `docs/api/containers.md`。

## 8. 命令与配置

| 命令 | 作用 |
|---|---|
| `/oriel demo` | 打开首页（客户端） |
| `/oriel demo catalog` | 组件与动效的活目录 |
| `/oriel open <url>` | 打开任意页面，如 `/oriel open mymod:oriel/shop.html` |
| `/oriel reload` | 重新加载所有已打开的页面（F3+T 也会） |
| `/oriel edit` | 物品数据编辑器（服务端校验后写回，需要管理员权限） |
| `/oriel demo chest` | 真实箱子菜单配页面槽位（服务端，需要管理员权限） |
| `/oriel demo live` | 服务端会话示例（每秒推送玩家状态） |

配置文件 `config/oriel.properties`，所有键、默认值与范围见 [docs/api/security.md](../api/security.md#settings)。常用的：`client.serverPages`（`allow` / `ask` / `block`，是否显示服务器发来的页面）、`client.reducedMotion`、`client.shapeShader`、`client.inspector`（仅含开发工具的构建启用 F12 元素检查器，`-Poriel_release` 正式包忽略此开关）。

## 9. 开发你自己的页面：最短路径

通过 Agent 制作的新页面优先使用 [MCP 主工作流](ui-workflow.md)：网页预览、引擎检查、用户批准固定版本后再接入。首版自动接入适配器 oriel-dev 写入本库共同资源目录并提供游戏打开/验证入口；外部工程需明确适配器，不承诺自动生成服务器业务、容器菜单或权限处理。手动接入仍可按本页 API 完成。

1. 在本仓库的 kit 里加页面，用脚手架：`node tools/new-page.mjs bestiary "怪物图鉴"`；你自己的模组把页面放进自己的 `assets/<命名空间>/oriel/`（见第 3 节）。
2. 对着 [CSS 支持参考](css-reference.md) 写样式，不确定的先查表；写了不支持的东西会得到诊断，不会悄悄失效。
3. 无头检查：`./gradlew :preview:checkUi`，或让 AI 通过 MCP（`node tools/mcp/oriel-preview.mjs`）调用 `oriel_check`、`oriel_snapshot`。
4. 进游戏看：`./gradlew :fabric:26.1:runClient`，`/oriel open mymod:oriel/xxx.html`，F12 看元素盒子。
5. 要做自动验收：`OrielAutomation`（点击、输入、拖拽、等待页面稳定）见 [docs/api/development.md](../api/development.md)。
