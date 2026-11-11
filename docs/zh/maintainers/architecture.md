# 架构与解析器

## Oriel 的运行原理

[入口](../index.md) · [制作流程](../ui-workflow.md)

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

本项目基于 Vellum（MIT，Max Leiter）fork，保留 LICENSE/NOTICE；AUI 只作仓库外思想参考。设计提案、替换路线和调研不属于页面能力合同；现状以 [能力表](../capabilities.md)和参数参考为准。

## HTML 与 CSS 解析

解析语法层由 Oriel 自己实现，不依赖 jsoup 或 CSS 库。属性值计算、选择器匹配和布局不属于语法解析层。

HTML 使用拉取式分词和独立建树：标签、属性、文字、注释、字符引用、void、raw-text/RCDATA、文档骨架和作用域内的隐式结束标签。片段创建的 script 保持惰性。支持 UI 已使用的自闭合标签约定，不声称实现完整 adoption agency 或全部 HTML 浏览器行为；不支持的元素仍由能力诊断明确报告。

CSS 使用 CSS Syntax 3 词法规则、迭代组件值栈以及规则／声明解析。保留原 Token/ComponentValue 数据接口，使 Properties、Numeric、Images 等值解析器无需换算法。嵌套深度受引擎预算限制；@media、@keyframes、@font-face 等先作为语法节点解析，再交给原能力层决定如何使用或报告。

语法问题保留原文件名和一基 line、column。无效输入仍会恢复，但报告 css-syntax 或 html-syntax；BAD_STRING/BAD_URL 等 token 继续交给声明恢复处理。新增 Diagnostic.column()，旧六参数构造器仍可用。CSS 无效声明与能力诊断也记录属性名列号，HTML 不支持元素记录标签位置；资源诊断和 HTML 内嵌 CSS 保留各自输入范围，不伪造未提供的宿主源码偏移。

## 解析模式与回归检查

默认模式与 `oriel.parser=new` 都使用当前解析器；`old` / `both` 已不再支持，非法模式明确报错。语法回归比较 DOM、CSS 规则、声明和诊断字段；有视觉影响的改动还须在固定资源、尺寸与状态下对照画面。

## 解析与恢复约定

- param 按 void 元素处理，不会错误包含后续节点。
- 原始文本结束名须有标签分隔符：script 不会被字符串中的“</scripted>”提前截断。
- CSS 声明列表中的 at-rule 在自身块结束时恢复，后面的合法声明不会被一起吞掉。
- CSS 输入中的 NUL 和孤立代理码位按 Unicode replacement 处理；EOF 后反斜杠转义成为 U+FFFD，而非静默分裂为另一 token。
- 原来静默接受／跳过的重复属性、无效引用、错配结束标签、未闭合字符串／注释／块等，产生带位置的语法诊断；对应可恢复的 DOM 或有效声明尽量保持不变。

这些行为由解析器回归测试覆盖。合法组件值的原始 source/start/end 保留，以支持变量和 CSS 序列化。
