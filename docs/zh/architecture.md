# Oriel 的运行原理

[入口](index.md) · [制作流程](ui-workflow.md)

Oriel 是游戏内的 HTML/CSS 子集引擎。加载资源文本，解析 DOM/样式，层叠、布局、动画、输入和绘制由引擎处理，Minecraft 宿主将绘制调用转换为游戏 GUI 渲染。它不是嵌入 Chromium，不是网页截图播放器，也不自动把网页生成 Java Screen 源码。

| 模块 | 责任 |
|---|---|
| engine | 游戏无关的解析、样式、布局、动画、DOM、输入、Canvas 接口；不直接 import MC/加载器 |
| mc-extension | MC 字体/颜色/样式/资源/场景等兼容策略；由宿主安装 |
| script-rhino | Rhino 脚本与 DOM 绑定的可选实现 |
| rhino | 打补丁与重定位的 Rhino 运行库，保留 MPL 声明 |
| kit | Ore CSS、资源、组件、Java 页面控制器；依赖引擎，不依赖 common |
| common | MC Screen/HUD/容器/原生元素、网络、服务端业务、资源与输入适配 |
| fabric / neoforge | 各加载器注册与事件胶水 |
| preview | 相同引擎，Java2D 渲染；本机资源、无头检查/动作/截图 |

依赖不能写反：kit → engine，common 使用 engine/kit 及运行扩展，loader → common；preview 使用 engine/kit 与扩展。核心仍保留兼容公共类型壳，不能宣称所有 MC 概念已完全清空。内部拆分不应改变已公布 HTML/CSS 名称和行为。

一帧：资源 → DOM/规则 → 层叠与 ComputedStyle → Block/Inline/Flex/Grid 布局 → Canvas 调用 → 游戏后端或 Java2D 后端。滤镜、遮罩、混合与裁剪可能需要离屏纹理再合成，代价高于普通背景。原生 item/entity/model/scene 由 MC 适配层绘制，预览不能替代其验证。

输入由宿主转换成引擎事件，交互可由 Java 控制器或沙箱 JS 执行。服务器只推页面/数据、验消息和管理真实状态，不创建客户端 Screen。协议与渲染是不同边界。

Minecraft 版本差异通过 Stonecutter 与加载器分层处理；新版本仍需编译与游戏验证，不能由“引擎独立”推导全部兼容。字体、GUI 缩放、输入后端、原生模型与 GPU 合成依然依赖游戏。

本项目基于 Vellum（MIT，Max Leiter）fork，保留 LICENSE/NOTICE；AUI 只作仓库外思想参考。设计提案、替换路线和调研不属于页面能力合同；现状以 [能力表](capabilities.md)和参数参考为准。
