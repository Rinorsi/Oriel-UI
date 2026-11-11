# Oriel UI 开发手册

本手册对应 **Oriel 0.15**：瘦包 `0.15.0`、完整版 `0.15.1`，Minecraft 26.1.2 / 26.2 / 26.3，Fabric / NeoForge，Java 25。依赖和打包规则只在[分发指南](distribution.md)维护；版本历史见[更新记录](../../CHANGELOG.md)。

Oriel 用 HTML/CSS 描述 Minecraft 界面，用受限 JavaScript 或 Java 接交互。它提供界面引擎和组件，业务逻辑由下游实现；不保证任意网页都能直接运行。

## 开始使用

| 你的目标 | 从这里开始 |
|---|---|
| 手头只有库 jar，先做出一个界面 | [最小 Fabric 工程](quickstart.md) |
| 在已有 Fabric / NeoForge 模组中接入 | [接入指南](integration.md) → [分发指南](distribution.md) |
| 先确认能做什么、有哪些限制 | [能力边界](capabilities.md) |
| 制作和调试一个页面 | [制作流程](ui-workflow.md) → [热更新](hot-reload.md) |
| 选现成样式和效果 | [模板与示例](templates.md) → [设计规范](design.md) |
| 让 AI 帮忙做页面 | [MCP 工具与调用示例](mcp.md) |

第一次制作页面：先选界面类型、配好热更新，再从模板复制页面；双尺寸预览后，在目标游戏中验证输入与业务。完整步骤只维护在[制作流程](ui-workflow.md)。

## 制作页面

| 文档 | 查什么 |
|---|---|
| [设计规范](design.md) | 字体字号、尺寸、主题、可复制骨架 |
| [组件](components.md) | 外壳、按钮、开关、下拉、抽屉、主题变量与 Shell |
| [动效](motion.md) · [布局配方](recipes.md) | 动画反馈与常见结构 |
| [模板与示例](templates.md) | 编号 UI、保存副本、资源复用与旧版演示索引 |
| [热更新](hot-reload.md) | 我的页面、源码目录、jar 开发与排错 |
| [性能](performance.md) | 粒子、Canvas、懒加载和测量边界 |
| [多语言与无障碍](i18n.md) | 翻译、语言回退、朗读与实时区域 |

## 参数与 API

| 文档 | 查什么 |
|---|---|
| [CSS 参数](css-guide.md) | 属性、选择器、单位、默认值与实际行为 |
| [元素](elements.md) | 表单、文本编辑、物品、实体、模型、场景与提示框 |
| [脚本](scripting.md) | JavaScript、DOM、事件、选区、Canvas、模板与沙箱 |
| [Java 绑定](bindings.md) | ViewModel 与声明式指令 |
| [页面接口](pages.md) · [Java API](java-api.md) | 打开、数据、消息、比例与生命周期 |
| [HUD](hud.md) | 叠加层注册、显示与更新 |
| [容器页面](containers.md) · [服务端](server.md) | 真实槽位、会话、通信与权限 |
| [安全与设置](security.md) | 安装方向、协议、沙箱与配置 |
| [前端库兼容](ecosystem.md) | 固定构建与受限兼容范围 |

## 调试与维护

页面作者查[检查与验收](validation.md)、[命令与检查器](development.md)、[MCP](mcp.md)。预览、游戏、联网与 OS 输入法是不同验证层，不能互相代替。

库维护者从[维护手册](maintainers/index.md)进入：架构、构建发布、版本适配、样式/布局/输入/绘制诊断和性能工具均集中在那里。自动生成的 [CSS 探测表](css-reference.md)供核对注册表使用，页面写法以 CSS 参数手册为准。
