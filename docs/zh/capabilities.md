# 能力、限制与选型

[文档入口](index.md) · [CSS](css-guide.md) · [元素](elements.md) · [验收](validation.md)

适用：Oriel UI 0.10.0 源码基准 `9928647`，MC 26.1.2 / 26.2 / 26.3 的 Fabric、NeoForge 目标。Gradle 中 26.1.2 的工程名是 `26.1`。本页是实现核对，不是这次任务重新运行了六目标验收。

## 选择界面类型

| 目标 | 选用 | 状态由谁决定 |
|---|---|---|
| 设置、图鉴、模板中心、工具面板 | `OrielScreens.open` | 客户端；涉及配置落盘需自己接保存处理 |
| 药水提示、任务进度、公告 | `OrielHud` | 客户端展示；服务端权威数据仍需同步 |
| 物品组件表单、商店、对话 | `OrielServer` + `OrielSession` | 服务端校验并返回结果 |
| 箱子、仓库、真正的锻造槽位 | 原版菜单 + `OrielScreens.registerContainer` + `<slot>` | 服务端菜单；原版网络同步 |
| 实体展示、物品立绘、方块小场景 | `<entity>` / `<model>` / `<scene>` | 展示模型；不是可修改游戏世界的画布 |

**Ore 风格的物品组件编辑器可以做。** 现有 `/oriel edit` 是表单编辑路线，支持名称、附魔、属性修饰等；完整字段见 [物品编辑器](item-editor.md)。`<item>` 是展示，`<slot>` 才绑定真实菜单槽位。kit 的演示背包/forge 不是真实菜单；`/oriel-forge` 打开的独立 forge_station 已绑定服务端修理菜单，字段见 [服务端](server.md)。

## 三种运行环境的边界

这里的“实现”表示当前代码有对应路径。它不意味着每种参数组合都已在全部版本游戏中验证。

| 能力 | 浏览器原型 | Oriel 预览器 | 游戏内 |
|---|---|---|---|
| Block / Inline / Flex / Grid、定位与滚动 | 浏览器规则 | Oriel 子集 | 同一引擎子集 |
| 圆角、边框、阴影、九宫格、渐变 | 浏览器绘制 | Java2D 实现 | GUI/着色器实现，边缘可能有差异 |
| `filter`、`clip-path`、mask、混合 | 支持范围由浏览器决定 | 支持表列出的子集 | 离屏层实现；需验 GPU 合成和裁剪 |
| 毛玻璃 `backdrop-filter` | 浏览器背景 | 预览器背景 | 可采样页面内容与世界；读不到下面的原版 Screen 内容 |
| CSS transition / keyframes、2D transform | 浏览器时钟 | 引擎时钟，可脚本逐帧 | 引擎时钟，帧率随游戏负载变化 |
| Ore 组件 CSS 外观 | 路径可解析时可看 | 可用 | 可用 |
| `Shell` / `Widgets` / Java 控制器行为 | 需要 mock/桥接 | 已注册控制器可运行 | 已注册控制器可运行 |
| 页面 JavaScript | 浏览器 JS | Rhino 沙箱子集 | Rhino 沙箱子集 |
| MC 字体、中文换行 | 字宽/换行不等价 | 读取 MC 资源，仍有后备字体差异 | 游戏字体与资源包；必须检查 |
| 中文 IME 候选与预编辑 | 浏览器原生 | 不能替代 MC 输入链路验收 | 需按版本、输入后端实测 |
| `<item>` / `<slot>` / `<entity>` / `<model>` / `<scene>` | 尺寸相同的替身 | 原生内容不完整，3D 为占位 | Minecraft 真正绘制；槽位需容器绑定 |
| 真实物品修改、网络权限、联机隔离 | mock | fixture | 服务端/多人实测 |
| HUD 输入穿透、原版界面叠加 | mock | 不代表真实 Screen 层次 | 用 HUD Input 策略验证 |

## 可以用的效果

- 颜色：hex、RGB/RGBA、HSL/HSLA、命名色、`currentColor`、`color-mix(in srgb, ...)`；MC 扩展色 `mc-gold` 等。
- 背景：PNG、多层背景、线性/径向/锥形渐变及对应重复渐变、GUI 精灵、Canvas 图像。
- 形状：实线/虚线等边框、圆角、`box-shadow`、`text-shadow`、`border-image`、文字描边、渐变字。
- 合成：blur/brightness/contrast/grayscale/invert/opacity/saturate/sepia/hue-rotate/drop-shadow；clip 的 inset/circle/ellipse/polygon；mask 的 add/subtract/intersect/exclude；混合模式见 CSS 表。
- 动态：2D 平移缩放旋转倾斜、transition、keyframes、命名动效、FLIP、Java Canvas 粒子；3D 内容通过 `-mc-yaw/-mc-pitch` 转动。
- 控件：输入、多行、复选、单选、range、select、进度、details；Ore 页签、菜单、抽屉、浮层、通知、懒显示需正确接控制器。

filter/clip/mask/mix-blend 的注册插值是**离散**；能静态画出并不代表能像浏览器一样平滑插值。要平滑呼吸光，优先使用 `opacity`、颜色、阴影、transform。

## 明确不能按浏览器原样使用

| 写法/预期 | 当前限制 | 制作时怎么处理 |
|---|---|---|
| 任意 React/Vue 应用原封不动加载 | 无完整浏览器平台、ES modules 或 npm 运行时 | 输出文档子集；交互用原生 JS 子集或 Java。Vue 风格模板不是 Vue 框架 |
| HTML 自动编译成模组代码 | 没有这样的转译器 | HTML/CSS 作为资源原样加载；手工接宿主与状态 |
| CSS 3D、perspective、rotateY、matrix3d | 不支持 | 用原生 3D 元素；普通卡片用 2D transform |
| sticky | 解析后按 relative，提示近似 | 固定标题放滚动区域外 |
| table、float、多栏布局 | 不支持对应布局 | Grid/Flex |
| @layer 真正层级、CSS 嵌套 | layer 压平；嵌套不支持 | 显式选择器顺序和命名空间 |
| @font-face、网络字体 | 不支持 | MC font JSON + 本地字体资源 |
| SVG、video、audio、iframe、WebGL | 不作为浏览器平台提供 | PNG/原生模型；声音走 `oriel.playSound` |
| fetch、XHR、WebSocket、Java 直调、任意文件读写 | 脚本不提供 | 命名消息由宿主处理 |
| localStorage 跨重启保存 | 两种 storage 都是当前页内存 | Java/服务端持久化 |
| Canvas 路径、文字、transform、绘制任意图片 | Canvas 只有像素/矩形/另一 Canvas 的 drawImage | 用 DOM、图片元素或原生模型 |
| 页面改 `oriel.data` 就同步服务端 | 只改变本地对象 | send 请求；服务端校验后 push/patch |
| 原生物品任意透明度/染色 | 物品不是普通 RGBA 图片；低透明度会隐藏 | 不依赖半透明物品作关键反馈，游戏验收 |
| 配方查看器适配已全部认证 | 有 GUI bounds 接缝，无 JEI/REI/EMI 全组合认证 | 标记 bounds，并在目标整合包测试 |

## 不能误当作已落地的能力

- 服务器装库时要求客户端安装并匹配协议；客户端装库仍可加入无库服务器。协议当前为 2。服务端物品编辑拒绝回复携带语言键和参数，由客户端翻译；登录拒绝另有语言文件支持的双语文字，见 [服务端](server.md)。
- `data-i18n` 与 Java `Lang` 已提供，原生客户端提示使用 Minecraft I18n。API 检查、模板编译及依赖校验已有工程门禁；翻译参数见 [国际化](i18n.md)，工程限制见 [后端维护](backend-maintenance.md)。
- 注册表可提供模组物品和可召唤实体条目，不等于能编辑所有模组的组件，或所有实体渲染器都已兼容。未知组件应保留。

这些是使用边界。开发计划、分支合并状态和问题处置记录放工作区；能力真正合入后必须同时更新本页与字段参考，规则见 [维护约定](maintenance.md)。
