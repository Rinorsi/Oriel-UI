# Oriel UI（优易库）

面向 Minecraft Java 版的模组 UI 库：用类 HTML/CSS 的声明式文件写界面，引擎负责样式、排版、动画与输入，再用游戏自己的 GUI 渲染器画出来。第一套主题对齐基岩版 Ore UI 的像素斜角风格，也支持平滑圆角风格。

> **致谢与来源**：本项目基于 [Vellum](https://github.com/MaxLeiter/vellum)（MIT，作者 Max Leiter）的引擎思路与代码底座发展而来——引擎与游戏解耦、窄渲染接口、预览器加无头截图、一份源码多版本。感谢原作者。我们在此基础上独立演进，补充了 Ore 主题与组件库、中文排版与输入法、诊断体系、26.1 适配、MCP 与自动验收等。许可证与版权声明见 [LICENSE](LICENSE)、[NOTICE](NOTICE)。

> 0.x 阶段，不兼容的公共 API 变更提升次版本。推荐声明必需依赖并限制在同一次版本内；范围与 0.10 迁移说明见 [安装与稳定性](docs/api/setup.md)。

## 特点

- **写法接近网页**：HTML + CSS（块、行内、Flex、Grid、过渡、`@keyframes`、渐变、滤镜、遮罩、`clip-path`……），具体能写什么见 [CSS 支持参考](docs/zh/css-reference.md)。
- **不支持就报错**：未知属性、单位、元素、at 规则都会给出带文件和行号的诊断，不会静默忽略。
- **Minecraft 内容进页面**：`<item>`、`<slot>`（容器槽位）、`<entity>`、`<model>`、`<scene>`。
- **界面类型**：全屏/弹窗界面、HUD 叠加层、服务端驱动的界面、带物品槽的容器界面。
- **Ore 像素主题 + 平滑主题**，亮/暗两套，200 个像素图标（三种线宽）、粒子与命名动效库，另有首页、背包、锻造台、音乐、设置、药水 HUD、组件编辑器、物品数据编辑器等预设界面作示例。
- **中文友好**：CJK 断行、输入法预编辑与候选窗定位、中文字体子集。
- **为 AI 开发设计**：热重载、无头截图、页面检查、MCP 服务、游戏内自动验收。
- **版本**：Minecraft 26.1.2（主验收版本）/ 26.2 / 26.3，Fabric 与 NeoForge，Java 25。

## 文档

制作与接入从 [中文手册入口](docs/zh/index.md)开始，查 [能力与限制](docs/zh/capabilities.md)、[完整流程](docs/zh/ui-workflow.md)、[Java API](docs/zh/java-api.md)。能力修改须同步 [维护合同](docs/zh/maintenance.md)。

从 [docs/zh/index.md](docs/zh/index.md) 开始：

| | |
|---|---|
| [实现原理](docs/zh/architecture.md) | 模块、一帧如何画出来、与 Vellum 的关系 |
| [接入指南](docs/zh/integration.md) | 依赖、打开页面、HUD、服务端、容器 |
| [服务端篇](docs/zh/server.md) | 什么界面需要服务端、数据怎么流、谁负责校验 |
| [CSS 支持参考](docs/zh/css-reference.md) | 属性、选择器、函数的支持情况（由引擎生成） |
| [做一个新页面](docs/zh/making-a-page.md) | 脚手架、页面结构、控制器 |

## 构建

需要 JDK 25 与 git（构建时会下载 Rhino 源码并打补丁）。

```bash
./gradlew build
```

## 运行演示

```bash
./gradlew :fabric:26.1:runClient
```

进入世界后在聊天栏输入 `/oriel demo` 打开首页，`/oriel demo catalog` 看组件与动效目录。

## 开发

```bash
node tools/new-page.mjs <名字> "<标题>"      # 新页面：HTML + 控制器 + 测试 + 登记
./gradlew :kit:test :preview:checkUi          # 测试 + 全部页面无头检查
node tools/mcp/oriel-preview.mjs              # MCP 服务（检查、截图、交互）
```

## 许可证

MIT。包含 Vellum 的部分保留其版权声明；内置的 Mozilla Rhino（MPL-2.0）、字体（SIL OFL）等第三方内容见 [NOTICE](NOTICE)。
