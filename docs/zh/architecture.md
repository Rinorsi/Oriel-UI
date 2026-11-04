# 优易库是怎么实现的

[文档总目录](index.md)

这篇讲**它由哪几块组成、一页 HTML 怎么变成屏幕上的像素、为什么这样分层**。想直接用，看 [接入指南](integration.md)；想查 CSS 能写什么，看 [CSS 支持参考](css-reference.md)。

## 一句话

优易库是一个运行在 Minecraft 里的"小型网页引擎"：你写 HTML 和 CSS，引擎自己解析、算样式、排版、画出来，再交给游戏自己的 GUI 渲染器显示。它**不依赖浏览器**，也**不是把网页截图贴进游戏**。

## 与 Vellum 的关系

引擎的思路与底座来自 [Vellum](https://github.com/MaxLeiter/vellum)（MIT，作者 Max Leiter）：把引擎做成与游戏无关的独立模块、用窄接口对接游戏渲染、用 Swing 预览器和无头截图做开发闭环、用 Stonecutter 一份源码支持多个 Minecraft 版本。我们从它的某个提交 fork 出来，之后独立发展，不再合并上游。

在此之上我们做的事：

| 方向 | 内容 |
|---|---|
| 主题与组件库 `kit` | Ore 像素主题（`ore.css`）、200 个像素图标（三种线宽）、命名动效库、粒子、预设界面（首页、背包、锻造台、音乐、设置、药水 HUD、组件编辑器、物品数据编辑器） |
| 中文 | CJK 断行、输入法预编辑与候选窗定位、中文字体子集 |
| 诊断 | 不支持的属性、单位、元素、at 规则一律报告（含文件、行号、建议），不静默忽略 |
| 渲染 | 26.1 适配、离屏层（`clip-path`、遮罩、混合模式、滤镜）、圆角裁剪、`backdrop-filter`、`border-image` 九宫格 |
| 开发闭环 | MCP 服务（检查、截图、交互）、六目标冒烟、Java 控制器热重载并保留状态 |

许可证上，保留了 Vellum 的版权声明与 MIT 全文（见仓库根目录 `LICENSE`、`NOTICE`）。

## 模块一览

```
Oriel-UI/
├─ engine/    纯 Java 的网页引擎：HTML/CSS 解析、样式、排版、动画、事件、绘制指令。不引用 Minecraft、不引用任何加载器
├─ rhino/     打了补丁的 Mozilla Rhino（MPL-2.0），页面脚本用；脚本默认不是 kit 页面的写法
├─ common/    连接引擎与 Minecraft：屏幕、HUD、容器界面、网络包、服务端接口、配置。按 MC 版本编译
├─ fabric/ neoforge/   加载器胶水，每个只有几百行
├─ kit/       Ore 主题 + 预设界面 + 共用件（Shell、Dom、Flip、Slider、Particles …），页面在 assets/oriel/ui/
├─ preview/   Swing 预览器 + 无头截图 + 页面检查（:preview:checkUi）
├─ template/  下游模组起步模板（NeoForge、Fabric）
├─ tools/     new-page.mjs（页面脚手架）、mcp/（MCP 服务）、烟雾测试脚本
└─ docs/
```

**依赖方向只有一条**：`kit → common → engine`，`fabric/neoforge → common`。`engine` 不知道 Minecraft 存在，所以它能在预览器和单元测试里原样运行；换 Minecraft 版本时，引擎和 kit 一行不用改，只动 `common` 里随版本变化的那一小撮代码。

## 一帧是怎么画出来的

```
HTML 文本 ──解析──► DOM 树 ─┐
CSS 文本 ──解析──► 规则表 ──┴─► 层叠 + 继承 ──► 每个元素的 ComputedStyle
                                                     │
                                         排版（块 / 行内 / Flex / Grid）
                                                     │
                                          盒树 + 位置 + 层叠顺序
                                                     │
                         绘制：背景、边框、阴影、文字、图片 …… 变成 Canvas 调用
                                                     │
                 ┌───────────────────────────────────┴───────────────────────────┐
         McCanvas（游戏内）                                              预览器画布（Java2D）
      用游戏的 GUI 渲染器画                                         把同样的调用画进内存 / 窗口
```

要点：

1. **样式是强类型的**。CSS 先被切成标记再解析成值（长度、颜色、枚举…），不是字符串拼接。无效的声明整条丢弃，并产生一条诊断。
2. **排版是自己写的**：块级、行内、Flex、Grid、滚动容器、绝对定位、文字断行（含 CJK 断行规则）。用从 Chrome 导出的几何夹具做对拍测试（`engine/src/test/resources/taffy`）。
3. **绘制只通过一个窄接口 `Canvas`**：保存/恢复状态、平移、变换、裁剪、填充矩形、四边形批量、画文字、画精灵，外加圆角框、边框、阴影（默认实现把它们拆成四边形，后端可以覆盖成更好的）。游戏里的实现是 `McCanvas`，它把每次调用变成原版 GUI 的一条绘制状态。
4. **圆角、边框、阴影用自带着色器画**（`assets/oriel/shaders/core/sdf_box.*`），得到平滑抗锯齿的边和真正的高斯模糊；着色器编译失败会自动退回四边形，也可以用配置 `client.shapeShader=false` 关掉。`shape-rendering: crispEdges` 让某个元素保持像素锐利，Ore 像素风格靠它。（三个 MC 版本都带这套着色器，各版本的顶点格式差异见 [docs/versions.md](../versions.md)。）
5. **离屏层**：`filter`、`backdrop-filter`、`clip-path`、遮罩、`mix-blend-mode`、圆角内裁剪需要先画到临时纹理再合成，引擎侧由 `paint/Layers` 描述，游戏侧由 `client/render/Layers` 和 `layer*.fsh` 着色器执行。

## 输入

引擎自己有一套事件系统（鼠标、键盘、滚轮、焦点、拖拽、文本选择、表单控件、Tab 导航），`common` 里的屏幕类把游戏的输入转成引擎事件。文本输入框支持输入法：预编辑文本显示在输入位置，候选窗被定位到光标旁（Windows 实测，其他平台尽力而为）。页面里 `:hover`、`:active`、`:focus-visible` 等伪类由这套状态驱动。

## 页面怎么有行为

有两种方式，**kit 里的页面只用第一种**：

| 方式 | 写法 | 适合 |
|---|---|---|
| **Java 控制器** | 页面只写 HTML/CSS；一个实现 `PageControllers.Controller` 的类在页面解析完成后拿到 `Document`，监听事件、改 DOM、管状态。按页面 URL 注册 | kit 预设界面；逻辑复杂、要和模组代码直接互通 |
| **页面脚本** | 页面里写 JavaScript（Rhino 沙箱），用 `oriel.data` / `oriel.send(channel, value)` / `oriel.on('data', …)` 和模组或服务端通信；还有 Vue 风格模板（`{{ x }}`、`v-for`、`:index`） | 来自服务端的页面、第三方模组的轻量页面 |

脚本能力保留，但**交互约定统一为"事件 + 命名命令 + 可观察状态"**，将来再接 KubeJS 之类只是调用这三样。服务端发来的页面一律在沙箱里跑：没有 Java 访问、没有网络、没有文件，有 CPU 和内存预算。

## Minecraft 内容怎么进页面

引擎里有"替换元素"的概念：元素的内容不由 HTML 决定，而由宿主画。`common/client/replaced/` 提供了：

| 元素 | 内容 |
|---|---|
| `<item id="minecraft:diamond">` | 物品图标、数量、附魔光效、物品提示 |
| `<slot index="n">` | 容器菜单的第 n 个格子（见 [服务端篇](server.md)） |
| `<entity type="…">` / `<entity player>` | 3D 实体或玩家模型，可跟随鼠标 |
| `<model block="…">` / `<model item="…">` | 3D 方块或物品模型，用 `-mc-yaw`、`-mc-pitch` 转 |
| `<scene>` | 一小块 3D 世界（结构文件 + 方块分组 + 实体 + 锚点标注） |

元素的详细属性见 [docs/api/elements.md](../api/elements.md)（英文，来自上游）。

## 版本与加载器

- 目标：Minecraft **26.1.2**（主验收版本）、**26.2**、**26.3**；Fabric 与 NeoForge；Java 25。
- 用 [Stonecutter](https://stonecutter.kikugie.dev) 从一份源码生成每个版本：差异写成 `//? if >=26.3 { … }` 条件和 `stonecutter.gradle` 里的重命名规则。新增一个 MC 版本的步骤见 [docs/versions.md](../versions.md)。
- 引擎、Rhino、预览器与版本无关，只编译一次。

## 诊断：不支持就大声说

所有"没按你写的执行"的情况都变成一条 `Diagnostic`（严重程度、代码、来源、行号、说明、建议）：

- 未知或非法的属性与取值、未知单位、非法选择器、被忽略的 at 规则；
- 不支持的 HTML 元素（`<svg>`、`<table>`、`<video>` 等，会建议替代写法）；
- 近似实现的特性（如 `@layer` 被压平、`position: sticky` 按 `relative`）。

游戏里在页面的错误面板看，开发时用 `./gradlew :preview:checkUi` 或 MCP 的 `oriel_check` 一次列出全部页面的问题。这条规则同时写在项目约定里：禁止静默忽略、静默降级。

## 开发闭环（给人和 AI）

1. `node tools/new-page.mjs <名字> "<标题>"` 生成页面、控制器、测试并登记。
2. 改 HTML/CSS/控制器。
3. `./gradlew :kit:test :preview:checkUi`：单元测试 + 全部页面无头加载检查。
4. 预览器无头截图：`--snapshot out.png --frames 60`，可加 `--boxes "<选择器>"` 打印元素盒子排查布局；MCP 的 `oriel_snapshot` 同理，还能传 `click`、`type`、`key` 等动作。
5. 进游戏验收：`./gradlew :fabric:26.1:runClient -Pautopilot=oriel`，自动打开每个界面并截图。
6. 游戏里保存 HTML/CSS/资源会热重载当前页面；Java 控制器改动也会重载，并通过 `saveState()` / `restoreState()` 保留状态。

## 测试

| 位置 | 测什么 |
|---|---|
| `engine/src/test` | 解析、层叠、排版（对拍 Chrome 夹具）、动画、事件、脚本沙箱、安全限制、绘制指令；`CssReferenceTest` 守护 [CSS 支持参考](css-reference.md) 不与代码脱节 |
| `kit/src/test` | 每个预设页面：加载、交互、断言 DOM |
| `preview/src/test` | 预览器与截图后端 |
| `common/.../gametest` | 服务端 GameTest：网络包编解码、限流、会话、畸形输入 |
| `tools/smoke-all.sh` | 六目标（两个加载器 × 三个 MC 版本）客户端冒烟：进世界、开页面、截图、查错 |
