# 优易库文档

优易库（Oriel UI）是面向 Minecraft Java 版的模组 UI 库：用 HTML 和 CSS 写界面，引擎负责样式、排版、动画和输入，再用游戏自己的渲染器画出来。

## 从哪里开始

| 我想… | 读 |
|---|---|
| 搞清楚它是什么、怎么实现的 | [实现原理](architecture.md) |
| 在自己的模组里用它（依赖、打开页面、HUD、服务端、容器） | [接入指南](integration.md) |
| 判断我的界面要不要服务端 | [服务端篇](server.md) |
| 查某个 CSS 属性、选择器、函数能不能写、怎么写 | [CSS 支持参考](css-reference.md)（由引擎生成） |
| 做一个新页面 | [做一个新页面](making-a-page.md) → [设计规范](design.md) → [布局套路](recipes.md) → [动效](motion.md) |

游戏里 `/oriel demo catalog` 是所有组件与动效的活目录；`/oriel demo` 打开首页。

## 给 AI（agent）的入口

写或改页面前，按这个顺序读，不要凭浏览器经验猜：

1. [CSS 支持参考](css-reference.md)：能写什么一目了然。不支持的东西引擎会报诊断，**不会静默当作别的值**；写之前查表比写完再改快。
2. [做一个新页面](making-a-page.md)：脚手架命令、页面结构、控制器怎么写。
3. [设计规范](design.md)：尺寸、层级、稀有度光效、两种风格；[布局套路](recipes.md)先挑一个再改。
4. 判断是否涉及服务端：[服务端篇](server.md) 的“三类界面”。涉及的话，**服务端必须校验**，页面发来的消息一律当恶意输入。

改完之后：

```
./gradlew :kit:test :preview:checkUi          单元测试 + 全部页面无头检查
node tools/mcp/oriel-preview.mjs              MCP 服务：oriel_pages / oriel_check / oriel_snapshot
./gradlew :fabric:26.1:runClient -Pautopilot=oriel     进游戏逐页截图（1280×720）
```

截图、日志、运行目录只放工作区的 `daily/<日期>/`，不要放进仓库。

## 全部文档

**中文（本项目撰写）**

- [后端构建与性能门禁](backend-maintenance.md)——Gradle/JDK25、依赖哈希、Jar-in-Jar共存与帧耗时回归

- [实现原理](architecture.md)、[接入指南](integration.md)、[服务端篇](server.md)
- [CSS 支持参考](css-reference.md)——生成文件，不要手改；改了引擎的 CSS 支持后用 `ORIEL_UPDATE_DOCS=1 ./gradlew :engine:test --tests '*CssReferenceTest'` 重新生成，否则测试失败
- [做一个新页面](making-a-page.md)、[设计规范](design.md)、[布局套路](recipes.md)、[动效](motion.md)

**英文**

- Java API：[安装与稳定性](../api/setup.md)、[打开页面](../api/pages.md)、[容器](../api/containers.md)、[HUD](../api/hud.md)、[Minecraft 元素](../api/elements.md)、[朗读](../api/narration.md)、[安全与配置](../api/security.md)、[开发与自动化](../api/development.md)
- [页面脚本与模板](../SCRIPTING.md)、[迁移手绘界面](../MIGRATING.md)、[Minecraft 版本](../versions.md)
- 引擎开发者：[26.3 GUI 笔记](../MC_26_3_GUI_API.md)、[SDF 方框提案](../design/sdf-boxes.md)、[预览器](../../preview/README.md)
