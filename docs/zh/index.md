# Oriel UI 制作与接入手册

用 HTML/CSS 制作 Minecraft 界面，用 Java 控制器或受限 JavaScript 接交互，用 Minecraft 宿主显示物品、实体和容器。**没有把任意 H5 自动转译成游戏 UI 的工具，也没有浏览器与游戏全效果等价的保证。**

本套文档对应本仓库 Oriel UI，不是 AUI 或 Vellum 上游能力清单。基准、适用范围及更新规则见 [维护约定](maintenance.md)。普通页面开发按下面的入口查表即可；发现文档漏项时由修改能力的维护者补齐，不要求每个页面作者重新研究引擎。

## 第一次制作页面，按这个顺序读

**只有库 jar、没有本仓库？** 先看 [只有 jar 时的最小 Fabric 工程](quickstart.md)：完整的构建文件、入口类和页面，已实际编译并启动验证。

使用 Agent 时，先打开 [MCP 接入手册](mcp.md)和[调用提示词](agent-prompts.md)，按[网页验收 → 游戏接入流程](ui-workflow.md)制作。MCP 提供规则查询、可交互网页预览、严格检查、固定版本人工验收与受控游戏接入。

1. [能做什么、不能做什么](capabilities.md)：选择 Screen、HUD、服务端页面或真实容器；先排除不支持的路线。
2. [从 H5 到游戏的完整流程](ui-workflow.md)：文件、状态、交互、资源映射、接入、每层验收和完成条件。
3. [设计规范](design.md)、[组件与参数](components.md)、[动效规范](motion.md)：Ore 外壳、主题、控件、反馈、字体与粒子。
4. [CSS 参数参考](css-guide.md)、[HTML 与 Minecraft 元素](elements.md)：准确的名称、取值、默认、单位、限制和例子。
5. [脚本与交互合同](scripting.md)：Java/JS 两条路线、模板、DOM、事件、数据流、浏览器适配。
6. [接入模组](integration.md)、[Java API](java-api.md)、[服务端与容器](server.md)：依赖、资源 URL、注册、同步与权限。
7. [检查与验收工具](validation.md)：可复制的 CLI、交互脚本、MCP 参数、错误排查。

## 按任务查

| 我要做 | 读这里 |
|---|---|
| Ore 风格物品组件编辑器 | [组件](components.md) → [编辑器字段合同](item-editor.md) → [服务端](server.md) |
| 浏览器里先把视觉做好，再移植 | [制作流程](ui-workflow.md)，特别是浏览器桥接和四层验收 |
| 查某个 CSS 属性、选择器或单位 | [CSS 参数参考](css-guide.md)；先看顶部的语义例外 |
| 查 `<item>` / `<entity>` / `<scene>` 的属性 | [元素参数](elements.md) |
| 查按钮、开关、下拉、抽屉和主题怎么写 | [组件与参数](components.md) |
| 查可用动画和粒子 | [动效](motion.md)与[组件](components.md) |
| 查 JS 能否用 fetch、模块、Canvas、Vue | [脚本与交互合同](scripting.md) |
| 用 Java ViewModel 驱动 o-text/o-for 等指令 | [Java 声明式绑定](bindings.md)：路径、稳定 key、诊断与可运行示例 |
| 换一个 agent 继续做 UI | 把本页和 [制作流程](ui-workflow.md)交给它，再给业务字段合同 |
| 新增、删除、改名一个能力或字段 | [维护约定](maintenance.md)：同一次变更同步哪些文档 |
| 了解引擎工作原理 | [架构](architecture.md)；页面作者无需先读 |

## 文档分工

工程专题：[自研解析器](parsers.md)、[国际化](i18n.md)、[后端构建与维护](backend-maintenance.md)、[安装与公共 API 稳定性](../api/setup.md)、[素材来源与许可](../asset-provenance.md)。

`docs/zh/` 是中文制作与能力入口；`docs/api/` 和 `docs/SCRIPTING.md` 保留细节补充或兼容链接，索引明确指出其用途。`preview/README.md` 与 `tools/h5ref/README.md` 负责各工具本身。`docs/versions.md`、`docs/MC_26_3_GUI_API.md`、`docs/design/` 面向引擎维护，设计提案不代表已实现。

AUI/Vellum 的第三方源码、历史研究、未确认提议和本次验收日志都不属于下游使用手册；它们在仓库外工作区。许可证来源仍保留在 LICENSE/NOTICE。
