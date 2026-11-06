# 优易库设计规范（Ore 像素主题 v2）

相关：动效 `motion.md`、布局套路 `recipes.md`、做页面流程 `ui-workflow.md`；游戏里 `/oriel showcase catalog` 是所有组件与动效的活目录。

适用于 `kit/src/main/resources/assets/oriel/ui/` 下的所有页面。样式全部在 `ore.css`，页面只写布局。

## 1. 尺寸与网格

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
