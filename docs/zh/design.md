# 优易库设计规范（Ore 像素主题 v2）

相关：动效 `motion.md`、布局套路 `recipes.md`、做页面流程 `ui-workflow.md`；游戏里 `/oriel showcase catalog` 是所有组件与动效的活目录。

适用于 `kit/src/main/resources/assets/oriel/ui/` 下的所有页面。样式全部在 `ore.css`，页面只写布局。

## 1. 尺寸与网格

交互音效沿用 kit 的[UI 声音 token](capabilities.md#ui-音效)，默认悬停无声；音量与开关由客户端配置控制，不在页面另建一套资源映射。

- **单位是 GUI 像素**：1 CSS px = 1 GUI px。设计基准 **640×360**（1080p、GUI 缩放 3，或 720p、缩放 2）；最小要撑住 **427×240**，用 `@media (max-width: 560px)` / `(max-height: 300px)` 收起次要栏。
- **4px 网格**：间距只用 2 / 4 / 6 / 8 / 12。
- **MC 像素字体优先两档**：8px 正文、16px 大标题；其他字体按 [字体表](components.md)推荐字号（如 11/12px）。这不是引擎仅支持两个字号的限制。
- **图标 16×16**，放大只用 2 倍（`.o-ico.x2` = 32px）。物品同理：16 或 32。

## 2. 层级：只有三层

| 层 | 用什么 | 说明 |
|---|---|---|
| 底 | `.o-app` | 世界画面压暗 + 暗角（亮色主题略浅） |
| 面 | `.o-window` | Ore 斜角面板，里面是 `.o-titlebar` + `.o-body` |
| 浮 | `.o-tile` | 扁平块：1px 边 + 顶部 1px 高光，可交互的加 `.hot` |

- **块里不再放块**。块内部的分组用间距、`.o-caps` 小标题、`.o-divider` 分隔线。
- 凹陷区（`.o-well`、`.ore-slot`、`.ore-field`）用于"装东西的地方"：物品格、输入框、进度槽。
- 侧栏 `.o-side` / 导航栏 `.o-rail` 是窗体的一部分，用 `--o-well` 底色和 1px 分隔线，不用块。

## 3. 斜角与扁平

- **斜角（border-image 纹理）**：只给窗体、按钮、物品格、输入框、开关、滑块、标签页。这些是 Ore 的标志。
- **扁平**：块、芯片 `.o-chip`、行 `.o-row`、分段控件 `.o-seg`。
- 按钮里的图标要单独着色（`.ore-btn .o-ico` 已处理）；**不要给带纹理的元素设 `-mc-tint`**，它会把纹理一起染色。

## 4. 颜色与主题

- 所有颜色走变量：`--o-text / --o-text-dim / --o-text-faint`、`--o-accent`、`--o-tile`、`--o-well`、`--o-line`……
- **亮色主题** `[data-theme=light]`：经典 Java 背包灰（面板 #c6c6c6、物品格 #8b8b8b、白/深灰斜角）。纹理换成 `*_l.png`。新组件只要用变量就自动支持亮色。
- 主题由 `Shell.theme(doc, "light")` 切换并记住，之后打开的页面沿用。

## 5. 稀有度与光效

稀有度类设置 `--tier`，文字、边框、光晕都读它：

| 类 | 暗色 | 亮色 | 用途 |
|---|---|---|---|
| `.r-common` | #c9cacd | #6f6f6f | 普通 |
| `.r-uncommon` | #6cc349 | #2f7d1d | 优秀 |
| `.r-rare` | #55c8ff | #1478b8 | 稀有 |
| `.r-epic` | #c46aff | #8a3ad0 | 史诗：呼吸光 `.fx-breathe` |
| `.r-legendary` | #ffb52b | #b86e00 | 传说：渐变框 `.o-frame` + 扫光 `.fx-sweep` |
| `.r-mythic` | #ff5a6a | #c42e3e | 神话 |

- 光效分级使用：普通无光；优秀/稀有只有边框色；史诗呼吸；传说渐变框 + 扫光 + 星光粒子。**不要所有东西都发光**，否则没有重点。
- 附魔物品用 `.fx-glint`（紫色斜纹流动，模仿附魔书的光泽）。
- 光晕底：`.fx-pool`（物品后面一团稀有度色的光）。

## 6. 动效

| 用途 | 时长 | 曲线 |
|---|---|---|
| 悬停、按下、颜色 | 120–160ms | `--o-ease-out` |
| 面板展开、行插入 | 220–360ms | `--o-ease-out` |
| 弹出、强调 | 260–320ms | `--o-ease-back`（轻微回弹） |
| 呼吸、扫光、漂浮 | 1.6–3.2s 循环 | ease-in-out / linear |

- 进场：块 `fx-rise` 逐个延迟 40ms（窗体本身没有专用进场类）。
- 反馈：成功 `fx-pop`，失败 `fx-shake`。

## 7. 粒子（`dev.oriel.kit.fx.Particles`）

```html
<canvas class="fx" data-fx="embers" data-rate="12" data-area="bottom" data-seed="3"></canvas>
```

- 预设：`embers` 余烬、`sparks` 火花（爆发）、`glint` 附魔闪光、`stars` 星光、`notes` 音符、`bubbles` 气泡、`dust` 浮尘、`hearts` 爱心。
- 一个画布像素 = 1 GUI px，粒子是真像素；60Hz 定步长 + 种子，截图可复现。
- 控制器里：`Particles fx = Shell.setup(doc); fx.emitter("id").rate(20); fx.emitter("id").burst("sparks", x, y, 16);`
- 每屏最多 2–3 个常驻发射器；爆发用于反馈（击中、获得物品）。

## 8. 图标

- 16×16 白色像素图标提供 regular/thin/bold 三档；名称以 MCP oriel_pages 为准。加载转圈用 `spinner` / `spinner_dots`（无投影，圆心在 8,8，可直接旋转）。用法：`<img class="o-ico" src="../textures/icons/gear.png">`，颜色由 `-mc-tint` / `--ico` 决定。
- 新生成位图默认用 Mivo，先复用现有图标；像素图保持整数倍率与透明边缘，许可证随资源保留。

## 9. 两种风格

- **像素（默认，`data-style="ore"`）**：Ore 斜角纹理，按下下沉。适合原版风格的模组。
- **平滑（`data-style="smooth"`）**：同一套组件换成圆角、渐变和柔和阴影（GPU 有向距离场着色器绘制，26.1–26.3 都支持）。适合现代风格的工具和编辑器。
- 页面不用改：`Shell.style(doc, "smooth")` 或设置页「外观 → 界面风格」切换，之后打开的页面沿用。两种风格都支持亮/暗主题。

## 10. 检查清单（每个页面）

1. `./gradlew :preview:checkUi`：无错误、无缺图、无 CSS 警告。
2. 640×360 与 427×240 各截一张图，看层级是否一眼可辨：标题 > 主内容 > 辅助信息。
3. 信息和操作先清楚；需要强调时只保留一个视觉中心，不为填空白强塞特效。
4. 键盘可达：Tab 有焦点框，Enter/空格可激活，Esc 关闭。
5. 亮色主题下再看一遍。

## 字体、尺寸与最小骨架

### 1. 字体与字号：像素字只在"原生字号"和"2 倍"上清晰

开始做动画前，还必须读 [粒子与动画性能规范](performance.md)：密集粒子共享画布、旋转四边形用原生 fillQuad，禁止脚本逐扫描线；先测最重阶段，不能用静止帧率代替动画验收。

| 字体 | family | 清晰的字号 | 用在哪 |
|---|---|---|---|
| MC 默认 / `oriel:pixel` | `minecraft:default` / `oriel:pixel` | **8、16** | 英文、数字、图标旁的短标签 |
| `oriel:pixel_12` | `oriel:pixel_12` | **12、24** | **中文正文和标题的首选** |
| `oriel:cubic` | `oriel:cubic` | 11、22 | 中文，笔画更圆润 |
| 轮廓字体（sans、kai 等） | `oriel:sans` 等 | 任意 | 需要任意字号时 |

- **中文不要用 8px。** 汉字只有 8×8 个点，笔画挤成一团（实测又粗又糊）。中文用 `oriel:pixel_12` + 12px，标题 24px。
- 字号不在上表的阶梯上，`oriel_check` / `preview --check --strict` 会报"字号 Npx 不在 …阶梯上（像素字会糊）"。看到这条就改字号，不要忽略。
- **Ore 标题栏的 `h1` 默认是 8px**（`ore.css`），用中文时在页面里加 `.o-titlebar h1 { font-size: 12px; }`。
- 自定义字体放 `assets/<ns>/font/<name>.json`，CSS 写 `font-family: <ns>:<name>`，不用 `@font-face`。

### 2. 尺寸：先按 640×360 做，再保证 427×240 不坏

- 1 CSS px = 1 GUI px。设计基准 **640×360**，最小要撑住 **427×240**（1280×720 窗口、GUI 缩放 3）。小屏用 `@media (max-width: 560px)`、`(max-height: 300px)` 收起次要内容。
- 间距只用 2 / 4 / 6 / 8 / 12。图标 16×16，放大只用 2 倍。
- **任何文字都不能出框**：长文本用 `overflow: hidden; text-overflow: ellipsis; white-space: nowrap`，或让它换行；统计行、标签行要么换行要么在小屏隐藏。

- **限高滚动框直接用 `max-height: 40px; overflow-y: auto`。** 内容少时随内容收缩，超出时内部滚动；父级自动高度按限高后的盒子计算。`height: 40px` 则始终保留固定高度，不需要额外包固定高度容器。

检查器会报告实际省略却没有 title / title-json 的文字、nowrap 被自身或祖先裁掉的文字，以及 flex 行收缩切掉的内容；图标和角标可设 flex:none / min-width，文字区域单独省略并提供完整提示。详见[诊断列表](validation.md#布局后诊断与-png-检查)。引用 PNG 宽高超过 2048px 或文件超过 1 MiB 也会报告，应先缩图、压缩。

### 3. 最小骨架：直接复制这个

放在 `assets/<你的模组id>/ui/index.html`（或 `config/oriel/my-pages/` 里）。复制后按目标资源运行 `preview --check --strict`，并检查实际字体与尺寸。

```html
<!doctype html>
<html data-oriel-shell>
<head>
  <meta charset="utf-8">
  <title>我的页面</title>
  <meta name="oriel-subtitle" content="一句话说明这个页面">
  <link rel="stylesheet" href="oriel:ui/ore.css">
  <style>
    html { font-family: oriel:pixel_12; font-size: 12px; }   /* 中文正文：pixel_12 + 12px，大标题 24px */
    .o-titlebar h1 { font-size: 12px; }                          /* Ore 标题栏默认 8px，中文要改成 12px */
    .page { flex: 1; display: flex; flex-direction: column; gap: 8px; padding: 12px; }
    .page h2 { margin: 0; font-size: 24px; font-weight: normal; }
  </style>
</head>
<body>
<div class="o-app">
  <div class="o-window">
    <div class="o-titlebar">
      <h1>我的页面</h1>
      <div class="o-grow"></div>
      <div class="ore-btn icon" data-action="close" tabindex="0"><img class="o-ico" src="oriel:textures/icons/close.png"></div>
    </div>
    <div class="o-body">
      <div class="page">
        <h2>你好，Oriel</h2>
        <p>中文正文用 pixel_12 的 12px，标题 24px。</p>
        <div class="ore-btn m-ripple" tabindex="0" style="align-self: flex-start">按我，有涟漪</div>
      </div>
    </div>
  </div>
</div>
</body>
</html>
```

- `oriel:ui/ore.css` 是库的主题；`oriel:textures/icons/…` 是库的图标。**页面里引用库的资源一律写带 `oriel:` 的完整地址**，写 `../textures/…` 只在库自己的 `ui/` 目录里有效。
- 关闭按钮用 `data-action="close"`，不用写脚本。

### 4. 不写 Java，也要库的动效：`data-oriel-shell`

UI 交互音效用库的[声音 token](capabilities.md#ui-音效)，默认悬停静音；`data-oriel-sound="tab"` 覆盖点击声，`off` 关闭当前元素的声音，不要给每个悬停都加声。

涟漪（`m-ripple`）、指针聚光（`m-spot`）、数字计数（`data-count`）、主题/图标切换、`data-action` 的关闭与返回、粒子画布，这些原来要 Java 控制器调用 `Shell.setup`。**纯 HTML 页面在 `<html>` 上加 `data-oriel-shell` 就等于调用了它**，不需要控制器。动效只用 `ore.css` 里已有的 `m-*` 类和 `--m-*` 曲线，不要自己写时长和缓动。有自己控制器的页面不用加。
