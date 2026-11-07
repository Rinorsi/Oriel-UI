# 能力、限制与选型

下游骨架：`oriel_scaffold` 默认只读检测 Fabric/NeoForge/Stonecutter，返回依赖/客户端命令/热更新清单；可选固定包严格检查、截图聊天批准后，仅新建示例 HTML/Java。歧义需显式补参数，不改已有工程配置，游戏验证独立；见 [MCP 参数与边界](mcp.md#下游骨架oriel_scaffold)。

[文档入口](index.md) · [CSS](css-guide.md) · [元素](elements.md) · [验收](validation.md)

适用：Oriel UI 0.11.0 源码基准 `40cef5c`，MC 26.1.2 / 26.2 / 26.3 的 Fabric、NeoForge 目标。Gradle 中 26.1.2 的工程名是 `26.1`。本页是实现核对，不是这次任务重新运行了六目标验收。

## 选择界面类型

选择器默认用新匹配器（2026-11-08 起）；old/both 开关、复杂度与作用域合同见 [CSS 选择器](css-guide.md#选择器实现与迁移开关)。开关不扩大页面语法或游戏验收范围。

Agent 制作入口新增 [MCP 工作流](mcp.md)：规则查询、网页模拟预览、引擎严格检查、固定版本用户验收、oriel-dev / oriel-folder / mod-project 资源接入；前两者支持独立游戏预览，mod-project 需在下游游戏验证（参数见 MCP 手册）。网页桥接首版支持普通 DOM/JS，外部 Java 控制器及 Oriel 模板需专门适配，不能直接在浏览器执行。该工具能力不扩大下面列出的引擎 HTML/CSS/JS 范围。

| 目标 | 选用 | 状态由谁决定 |
|---|---|---|
| 设置、图鉴、模板中心、工具面板 | `OrielScreens.open` | 客户端；涉及配置落盘需自己接保存处理 |
| 药水提示、任务进度、公告 | `OrielHud` | 客户端展示；服务端权威数据仍需同步 |
| 物品组件表单、商店、对话 | `OrielServer` + `OrielSession` | 服务端校验并返回结果 |
| 箱子、仓库、真正的锻造槽位 | 原版菜单 + `OrielScreens.registerContainer` + `<slot>` | 服务端菜单；原版网络同步 |
| 实体展示、物品立绘、方块小场景 | `<entity>` / `<model>` / `<scene>` | 展示模型；不是可修改游戏世界的画布 |

**Ore 风格的物品组件编辑器可以做。** Oriel 不自带物品编辑器（它是界面库，物品编辑器属于上层模组，如独立的 Editor-Mod）；用 `<item>` 展示、`<slot>` 绑定真实槽位、服务端会话校验保存即可自己做，表单校验写法见 [服务端](server.md)。`<item>` 是展示，`<slot>` 才绑定真实菜单槽位。kit 的演示背包/forge 不是真实菜单；`/oriel-forge` 打开的独立 forge_station 已绑定服务端修理菜单，字段见 [服务端](server.md)。

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
| Java ViewModel 的 o-* 声明式绑定 | 需另做 mock，不执行 Java | 显式装入 Java 控制器并调用 Bindings.attach | kit 提供相同绑定实现；页面仍需游戏验收，见[绑定合同](bindings.md) |
| 页面 JavaScript | 浏览器 JS | Rhino 沙箱子集 | Rhino 沙箱子集 |
| MC 字体、中文换行 | 字宽/换行不等价 | 读取 MC 资源，仍有后备字体差异 | 游戏字体与资源包；必须检查 |
| 中文 IME 候选与预编辑 | 浏览器原生 | 不能替代 MC 输入链路验收 | 需按版本、输入后端实测 |
| `<item>` / `<slot>` / `<entity>` / `<model>` / `<scene>` | 尺寸相同的替身 | 原生内容不完整，3D 为占位 | Minecraft 真正绘制；槽位需容器绑定 |
| 真实物品修改、网络权限、联机隔离 | mock | fixture | 服务端/多人实测 |
| HUD 输入穿透、原版界面叠加 | mock | 不代表真实 Screen 层次 | 用 HUD Input 策略验证 |

## 可以用的效果

- 颜色：hex、RGB/RGBA、HSL/HSLA、命名色、`currentColor`、`color-mix(in srgb, ...)`；MC 扩展色 `mc-gold` 等。
- 背景：PNG、多层背景、线性/径向/锥形渐变及对应重复渐变、GUI 精灵、Canvas 图像。
- 图片加载：`img loading=lazy` 与 URL 背景 `data-oriel-lazy` 共享视口预取队列，默认每 50ms 最多 3 个源，页面可配置；默认 eager 不变，详见[性能规范](performance.md#列表图片懒加载)。
- 形状：实线/虚线等边框、圆角、`box-shadow`、`text-shadow`、`border-image`、文字描边、渐变字。
- 合成：blur/brightness/contrast/grayscale/invert/opacity/saturate/sepia/hue-rotate/drop-shadow；clip 的 inset/circle/ellipse/polygon；mask 的 add/subtract/intersect/exclude；混合模式见 CSS 表。
- 动态：2D 平移缩放旋转倾斜、transition、keyframes、命名动效、FLIP、Java Canvas 粒子；3D 内容通过 `-mc-yaw/-mc-pitch` 转动。
- 控件：输入、多行、复选、单选、range、select、进度、details；Ore 页签、菜单、抽屉、浮层、通知、懒显示需正确接控制器。

filter/clip/mask/mix-blend 的注册插值是**离散**；能静态画出并不代表能像浏览器一样平滑插值。要平滑呼吸光，优先使用 `opacity`、颜色、阴影、transform。

## UI 音效

声音 token 集中定义在 kit 的 `Sounds`，不附带音频文件。游戏宿主支持 `oriel.playSound('tab')`，原有 `oriel.playSound('minecraft:ui.button.click', 0.5, 1)` 继续可用；token 的音量和音高乘以调用参数。未知裸 token 报错，事件 id 仍受宿主资源检查。无头预览器只记录请求，不发声。

| token | 原版事件 id | 基础音量 / 音高 | 用途与选择理由（待用户耳听） |
|---|---|---|---|
| `click` | `minecraft:ui.button.click` | 0.6 / 1 | 短促的常规按钮反馈 |
| `hover` | `minecraft:ui.button.click` | 0.12 / 1.6 | 很轻的悬停提示，只显式开启 |
| `open` | `minecraft:block.note_block.harp` | 0.35 / 1.2 | 较高的短音提示模态框进入 |
| `close` | `minecraft:block.note_block.harp` | 0.3 / 0.8 | 同音色降低音高提示退出 |
| `tab` | `minecraft:ui.button.click` | 0.4 / 1.3 | 比按钮轻，区分页签选择 |
| `toggle` | `minecraft:ui.button.click` | 0.45 / 1.1 | 轻微提高音高表示状态切换 |
| `success` | `minecraft:entity.experience_orb.pickup` | 0.4 / 1.2 | 简短的正向反馈，页面显式调用 |
| `error` | `minecraft:block.note_block.bass` | 0.4 / 0.8 | 低音提示失败，避免尖锐高音，页面显式调用 |

页面须调用 `Shell.setup(doc)`，或在没有专属控制器的 `<html>` 上声明 `data-oriel-shell`，才会接入默认交互声：`.ore-btn` 点击 `click`；`.ore-tab`、`.o-seg`、`.o-apptabs` 选择 `tab`；`.ore-toggle`、热 chip 与原生复选/单选 `toggle`；`.m-modal` 的 `on` 类在相邻帧间改变时播放 `open` / `close`。初始已打开的模态框不发声，同一帧开关抵消不发声。此行为不会修改原版界面。

```html
<html data-oriel-shell>
  <body><button class="ore-btn" data-oriel-sound="success">完成</button>
  <button class="ore-btn" data-oriel-sound="off">静音按钮</button></body>
</html>
```

`data-oriel-sound` 覆盖当前元素的点击 token，值限上表或 `off`；`off` 只关闭声音，不阻止点击、表单与复选行为。`hover` 是特例：仅指针进入该元素时播放，点击不再播放；悬停声默认关闭。动态插入的按钮也生效；自定义控件的键盘激活通过 `Shell.activate` 接线。未知属性值诊断带页面路径和源码行号。无需 Shell 的页面仍可显式调用脚本声音；自定义宿主须在 `Host.playSound` 中用 `Sounds.resolve` 解析 token。

`config/oriel.properties` 中 `client.uiSounds=true`、`client.uiSoundVolume=1` 为默认值。前者关闭所有 Oriel UI 声音（含脚本事件 id 和旧默认点击声）；后者范围 0–1，乘在 `min(请求音量, client.maxSoundVolume)` 后，再由游戏声音设置缩放。仍共用 `client.soundsPerSecond` 限频，不播放音乐、不更改原版音量。配置在启动时读取，修改配置后重启客户端。完整设置见[安全](security.md)。验证等级：有单元测试与注册表 GameTest；实际游戏听感须逐目标耳听，不能由预览日志推导。

## 明确不能按浏览器原样使用

| 写法/预期 | 当前限制 | 制作时怎么处理 |
|---|---|---|
| 任意 React/Vue 应用原封不动加载 | 无完整浏览器平台、ES modules 或 npm 运行时 | 输出文档子集；交互用原生 JS 子集或 Java。Vue 风格模板不是 Vue 框架 |
| HTML 自动编译成模组代码 | 没有这样的转译器 | HTML/CSS 作为资源原样加载；手工接宿主与状态 |
| CSS 3D、perspective、rotateY、matrix3d | 不支持 | 用原生 3D 元素；普通卡片用 2D transform |
| sticky | 解析后按 relative，提示近似 | 固定标题放滚动区域外 |
| table、float、多栏布局 | 不支持对应布局 | Grid/Flex |
| @layer 真正层级、CSS 嵌套 | 默认 layer 压平；[实验层序](d36-r04.md)须显式开启；CSS 选择器嵌套不支持 | 显式选择器顺序和命名空间 |
| @font-face、网络字体 | 不支持 | MC font JSON + 本地字体资源 |
| SVG、video、audio、iframe、WebGL | 不作为浏览器平台提供 | PNG/原生模型；声音走 `oriel.playSound` |
| fetch、XHR、WebSocket、Java 直调、任意文件读写 | 脚本不提供 | 命名消息由宿主处理 |
| localStorage 跨重启保存 | 两种 storage 都是当前页内存 | Java/服务端持久化 |
| Canvas 路径、文字、transform、绘制任意图片 | Canvas 支持像素/矩形/凸四边形 fillQuad/另一 Canvas 的 drawImage；没有通用路径 | 用 DOM、图片元素或原生模型；fillQuad 参数见 scripting.md |
| 页面改 `oriel.data` 就同步服务端 | 只改变本地对象 | send 请求；服务端校验后 push/patch |
| 原生物品任意透明度/染色 | 物品不是普通 RGBA 图片；低透明度会隐藏 | 不依赖半透明物品作关键反馈，游戏验收 |
| 配方查看器适配已全部认证 | 有 GUI bounds 接缝，无 JEI/REI/EMI 全组合认证 | 标记 bounds，并在目标整合包测试 |

## 不能误当作已落地的能力

纯外部 CSS 文件保存支持原 DOM 增量刷新，HTML/脚本变更仍整页重建；状态保留、手动重载及观察范围见 [热重载边界](validation.md#游戏与热重载)。F12 检查器仅在包含开发工具的构建可用，正式运行包不含其实现与页面。

- 服务器装库时要求客户端安装并匹配协议；客户端装库仍可加入无库服务器。协议当前为 2。服务端拒绝回复携带语言键和参数，由客户端翻译；登录拒绝另有语言文件支持的双语文字，见 [服务端](server.md)。
- `data-i18n` 与 Java `Lang` 已提供，原生客户端提示使用 Minecraft I18n。API 检查、模板编译及依赖校验已有工程门禁；翻译参数见 [国际化](i18n.md)，工程限制见 [后端维护](backend-maintenance.md)。
- 注册表可提供模组物品和可召唤实体条目，不等于能编辑所有模组的组件，或所有实体渲染器都已兼容。未知组件应保留。

开发工具提供 `oriel_inspect` / `--why` 显式选择器解释，包含候选数、失败谓词、声明赢家和预算；不产生批准记录。参数与运行范围见 [选择器诊断](selector-debug.md)。

这些是使用边界。开发计划、分支合并状态和问题处置记录放工作区；能力真正合入后必须同时更新本页与字段参考，规则见 [维护约定](maintenance.md)。

输入后端开关、事件 JSONL、预览/MCP 动作、inert 与可选空间导航的参数及限制见[输入诊断与导航](d36-input.md)。缺省后端为 new（old 回退）；六目标真实 IME 与手柄另验。
绘制诊断与窄扩展：提供实验渐变装饰缓存、DisplayList 导出，以及 Canvas strokeLine 和 source-over/lighter/screen 子集；限制见 [绘制检查](d36-paint.md)。不代表通用 Canvas 路径、连续 clip-path 动画或原生物品 Sheen 已完成。
