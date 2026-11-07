# Oriel UI 制作与接入手册

用 HTML/CSS 制作 Minecraft 界面，用 Java 控制器或受限 JavaScript 接交互，用 Minecraft 宿主显示物品、实体和容器。**没有把任意 H5 自动转译成游戏 UI 的工具，也没有浏览器与游戏全效果等价的保证。**

本套文档对应本仓库 Oriel UI，不是 AUI 或 Vellum 上游能力清单。**所有手册都在这个目录（`docs/zh/`）里，且只有中文这一套**；基准、适用范围及更新规则见[维护约定](maintenance.md)。普通页面开发按下面的入口查表即可；发现文档漏项时由修改能力的维护者补齐，不要求每个页面作者重新研究引擎。

## 第一次制作页面，按这个顺序读

**只有库 jar、没有本仓库？** 先看[只有 jar 时的最小 Fabric 工程](quickstart.md)：完整的构建文件、入口类和页面，已实际编译并启动验证。

使用 Agent 时，先打开 [MCP 接入手册](mcp.md)和[调用提示词](agent-prompts.md)，按[网页验收 → 游戏接入流程](ui-workflow.md)制作。MCP 提供规则查询、可交互网页预览、严格检查、固定版本人工验收与受控游戏接入。

0. **[产品定位与 agent 工作准则](principles.md)**：库是什么、不是什么，十条准则和动手前自问清单。**所有 agent 先读这一页。**
0. **[更新与升级](upgrading.md)**：从上一版迁移要改什么、行为变化、新增、已知限制。
0. **[复刻流程（开发者用）](replication.md)**：要把已有的视频动画、网页或原版界面复刻成 Oriel 页面时才用；参数对照表、必须一致的项目、阶段检查。普通做页面不用读。
0. **[做页面前先读：十条硬规则](page-rules.md)**：字体字号、尺寸、可直接复制的骨架、不写 Java 也能用库的动效、放哪里怎么预览。AI 做页面必读。
0. **[热更新：怎么配、怎么确认、什么会热](hot-reload.md)**：保存页面后游戏里 1 秒刷新；五种情形怎么选、日志里怎么确认、排错表。做页面前先配好它。
0. **[Showcase 源码导览](showcase.md)**：8 个整页示例 + 4 个基础演示的源码在哪、每个效果在第几行怎么做、怎么复制进你的模组。想做类似效果先读它。
1. [能做什么、不能做什么](capabilities.md)：选择 Screen、HUD、服务端页面或真实容器；先排除不支持的路线。
2. [从 H5 到游戏的完整流程](ui-workflow.md)：文件、状态、交互、资源映射、接入、每层验收和完成条件。
3. [设计规范](design.md)、[组件与参数](components.md)、[动效规范](motion.md)：Ore 外壳、主题、控件、反馈、字体与粒子。
4. [CSS 参数参考](css-guide.md)、[HTML 与 Minecraft 元素](elements.md)：准确的名称、取值、默认、单位、限制和例子。
5. [脚本与交互合同](scripting.md)：语言、DOM、事件、模板、限额。
6. [接入指南](integration.md)、[页面接口](pages.md)、[Java API](java-api.md)、[服务端与容器](server.md)：依赖、资源 URL、注册、同步与权限。
7. [检查与验收工具](validation.md)：可复制的 CLI、交互脚本、MCP 参数、错误排查。

## 按任务查

动画、粒子或卡顿问题，开工先读 [粒子与动画性能规范](performance.md)。

| 我要做 | 读这里 |
|---|---|
| 只有 jar，做一个自己的 mod 界面 | [只有 jar 时的最小 Fabric 工程](quickstart.md) |
| 把已有的手绘 Screen 迁成页面 | [迁移](migrating.md) |
| 选依赖、看稳定性承诺和版本范围 | [接入指南第 2 节](integration.md#2-获取与依赖) |
| 从客户端或服务器打开页面、交换数据 | [页面接口](pages.md)；签名速查见 [Java API](java-api.md) |
| 画在 HUD 上的叠加层 | [HUD 叠加层](hud.md) |
| 带物品槽的界面 | [容器页面](containers.md)（页面一侧）→ [服务端与容器](server.md)（服务端一侧） |
| 玩家可以自己改的页面（改文件夹） | [页面接口：玩家可编辑的页面](pages.md#玩家可编辑的页面) |
| 浏览器里先把视觉做好，再移植 | [制作流程](ui-workflow.md)，特别是浏览器桥接和四层验收 |
| 查某个 CSS 属性、选择器或单位 | [CSS 参数参考](css-guide.md)；先看顶部的语义例外 |
| 查 `<item>` / `<entity>` / `<scene>` 的属性、提示框、原版外观类 | [元素参数](elements.md) |
| 查按钮、开关、下拉、抽屉和主题怎么写 | [组件与参数](components.md) |
| 查可用动画和粒子 | [动效](motion.md)与[组件](components.md) |
| 查 JS 能否用 fetch、模块、Canvas、Vue；查沙箱限额 | [脚本与交互合同](scripting.md) |
| 用 Java ViewModel 驱动 o-text/o-for 等指令 | [Java 声明式绑定](bindings.md)：路径、稳定 key、诊断与可运行示例 |
| 朗读器（Narrator）读什么 | [朗读](narration.md) |
| 服务器页面的安全模型、`config/oriel.properties` 全部设置 | [安全与设置](security.md) |
| 命令、F12 检查器、游戏内自动化、无界面运行 | [开发参考](development.md) |
| 换一个 agent 继续做 UI | 把本页和[制作流程](ui-workflow.md)交给它，再给业务字段合同 |
| 新增、删除、改名一个能力或字段 | [维护约定](maintenance.md)：同一次变更同步哪些文档 |

## 全部页面一览

**制作**：[能力边界](capabilities.md) · [制作流程](ui-workflow.md) · [设计](design.md) · [组件](components.md) · [动效](motion.md) · [布局配方](recipes.md) · [国际化](i18n.md)

**参数**：[CSS 参数参考](css-guide.md) · [CSS 支持参考](css-reference.md) · [元素](elements.md) · [脚本](scripting.md) · [Java 声明式绑定](bindings.md)

**接入**：[最小 Fabric 工程](quickstart.md) · [接入指南](integration.md) · [页面接口](pages.md) · [HUD](hud.md) · [容器页面](containers.md) · [Java API](java-api.md) · [服务端与容器](server.md) · [迁移](migrating.md) · [朗读](narration.md) · [安全与设置](security.md)

**验收与开发**：[验收工具](validation.md) · [MCP](mcp.md) · [Agent 提示词](agent-prompts.md) · [开发参考](development.md)

**版本与维护**：[版本适配](versions.md) · [维护约定](maintenance.md) · [架构](architecture.md) · [自研解析器](parsers.md) · [后端构建与维护](backend-maintenance.md)

## 维护者资料（做页面和接入的人不需要读）

面向渲染后端、构建和移植新 Minecraft 版本的人：

- [版本适配](versions.md)：版本表、26.2 与 26.3 的差异、六目标冒烟、新增版本流程。
- [26.3 GUI API 笔记](mc-26-3-gui-api.md)：针对 26.3 核对过的客户端 GUI 渲染 API、画中画、输入和容器界面。
- [SDF 着色器绘制盒子](sdf-boxes.md)：圆角、边框、阴影的着色器实现和现状。
- [后端构建与维护](backend-maintenance.md)、[自研解析器](parsers.md)、[架构](architecture.md)。
- 仓库里的另几份：[Rhino 补丁](../../rhino/README.md)、[预览器](../../preview/README.md)、[H5 对照工具](../../tools/h5ref/README.md)、[素材来源与许可](../asset-provenance.md)（逐文件清单 `asset-provenance.json`）。

## 范围说明

AUI/Vellum 的第三方源码、历史研究、未确认提议和验收日志都不属于下游使用手册；它们在仓库外工作区。许可证来源仍保留在 LICENSE/NOTICE。设计提案不代表已实现，已实现的部分在各页面里单独写明。


选择器未生效、候选规则与声明赢家解释见 [选择器诊断](selector-debug.md)。
