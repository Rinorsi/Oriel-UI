# 动效规范

Ore kit 的动画优先用 `ore.css` 里的命名动效和 `dev.oriel.kit.Motion`，不要在页面里自己写时长和曲线。游戏里打开 `/oriel showcase catalog` →「动效」可以逐个点着看。

## 1. 时长与曲线（`--m-*`）

| 变量 | 值 | 用在 |
|---|---|---|
| `--m-fast` | 120ms | 悬停、按下、颜色变化 |
| `--m-base` | 220ms | 展开收起、标签切换、提示出现 |
| `--m-slow` | 360ms | 进场、转场、弹层 |
| `--m-out` | cubic-bezier(0.2, 0.8, 0.2, 1) | 进入画面的东西 |
| `--m-in` | cubic-bezier(0.5, 0, 0.75, 0) | 离开画面的东西 |
| `--m-in-out` | cubic-bezier(0.65, 0, 0.35, 1) | 在画面里移动的东西（转场的面板） |
| `--m-spring` | cubic-bezier(0.34, 1.56, 0.64, 1) | 弹出、按钮松开、指示条 |

规则：进来用 out，出去用 in，原地变化用 in-out；回弹只给"有分量的小东西"（按钮、徽章、弹窗），大面板不要弹。

## 2. 动效清单

| 场景 | 写法 | 效果 |
|---|---|---|
| 按下 | `class="m-press"` | 按下缩到 95% 下沉 1px，松开弹回 |
| 像素涟漪 | `class="m-ripple"`（Java 控制器页面调用 Shell.setup；纯 HTML 页面在 `<html>` 上写 `data-oriel-shell` 即可，见下） | 从按下的点长出一个方形光环 |
| 悬停抬起 | `.o-tile.hot` 自带；其他元素 `m-lift` | 上浮 2px + 稀有度色光晕 |
| 指针聚光 | `class="m-spot"` | 一团柔光跟着鼠标走（代替引擎画不了的 3D 倾斜） |
| 进场 | `m-rise` `m-pop` `m-drop` `m-fade-in` `m-slide-left/right` | 一次性进场 |
| 依次进场 | 容器 `class="m-stagger"` | 子元素间隔 40ms 依次浮上来 |
| 滚动浮现 | 元素 `m-reveal`，容器 `Motion.reveal(scroller)` | 滚到可见时浮现 |
| 展开收起 | `Motion.toggle(body, open)`；箭头 `m-caret` | 高度平滑过渡，箭头转 180° |
| 指示条 | `<i class="m-indicator">` + `Motion.indicator(bar, tab)` | 一根光条滑到选中的标签下 |
| 数字 | `data-count="2235"`，或 `Motion.count(el, 0, n, ms, 后缀)` | 数字滚动到目标 |
| 转场（进入） | 链接写 `data-go="forge" data-zoom` | 卡片放大成新页面，旧内容变暗，新内容浮入 |
| 切页（页签） | 标题栏 `.o-apptabs` 里的 `data-go` | 标题栏不动；旧内容向一侧滑出，新内容从新页签那侧滑入；指示条从旧页签滑到新页签 |
| 返回 | `data-action="back"` 或 `Shell.back(doc)` | 旧内容缩小淡出，首页内容从略大缩回 |
| 自定义切页 | `Shell.go(doc, url, "next" / "prev" / "back")` | 同上三种，任意按钮都能用 |
| 窗口化 / 最大化 | Shell 自动给每个标题栏加切换按钮；`Shell.window(doc, "mini")` | 窗口平滑缩成居中小窗（640×360）或铺满；所有页面记住同一个模式 |
| 弹层 | `.m-modal` 加 `on` | 背景淡入，面板弹起 |
| 提示 | `Notice.toast` / `data-toast` | 角落滑入叠放，到时自己离开（见 [组件](components.md)的通知与提示） |
| 加载 | `.m-skeleton` | 灰色占位块上扫过一道光 |
| 成功 / 失败 / 提醒 | `Motion.play(el, "m-success"/"m-error", ms)`；`m-attention` | 放大一下发绿光 / 左右抖 + 红框 / 持续金色脉冲 |
| 一次性动画重播 | `Motion.play(el, 类名, 毫秒)` | 去掉再加上类名，保证每次都从头播 |

### 切页动画是怎么做的

每个页面都是同一个外壳：全屏背景 `.o-app`，里面是窗口 `.o-window`，窗口里有标题栏 `.o-titlebar` 和内容 `.o-body`。切页时只有 `.o-body` 在动，背景和窗口框在两个页面上完全一样，所以不会闪黑。

1. `Shell.go(doc, url, kind)` 先给当前 `.o-body` 加 `m-page-leave-<kind>`（130ms 滑出或缩小淡出），再打开新页面。
2. 新页面的 `Shell.setup` 给它的 `.o-body` 加 `m-page-<kind>`（240ms 从对应方向滑入）。
3. 页签之间切换时，Shell 记住旧页签的序号；新页面第一帧把指示条放在旧页签下（`m-snap` 关掉过渡），下一帧再滑到当前页签。

想改动画：改 `ore.css` 里的 `@keyframes m-page-*`。想加新的种类：写一对 `.o-body.m-page-xxx` / `.o-body.m-page-leave-xxx`，然后 `Shell.go(doc, url, "xxx")`。

### 控件默认就能点

写在 HTML 里的控件不用写 Java 也能交互（`Widgets`）：`.ore-tabs > .ore-tab` 和 `.o-seg > *` 点哪个选哪个（里面放 `<i class="m-indicator">` 会跟着滑），`.ore-toggle` 和 `.o-chip.hot` 点了切换 `on`，`.ore-slider` 能拖。带 `id` 的开关、芯片、滑块留给页面控制器自己接。

## 3. 什么时候用什么

- **每次操作都要有反馈**：按钮至少 `m-press` 或 Ore 下沉；保存这类重要操作加 `m-success` / `m-error`。
- **一屏一个主动效**：主视觉可以一直动（转动的 3D、粒子），其余元素只在交互时动。
- **常驻循环动画 ≤ 3 处**（呼吸、扫光、漂浮、粒子都算）。设计检查器超过 8 个发光元素会警告。
- **进场只播一次**，不要在每次数据刷新时重播（控制器里只改内容，别重建整块）。
- **尊重减少动效**：`<html data-motion="reduced">` 时走主题的减少动效样式；自写 JS/Java/粒子循环也要停用或减量，验收内容与操作仍可达。

## 4. 引擎限制（写动效前先看）

- 没有 3D 变换（`perspective`、`rotateY`）：3D 用 `<model>` / `<entity>` / `<scene>` 的 `-mc-yaw`、`-mc-pitch`。
- `filter` / `backdrop-filter` / 混合模式已有实现，函数见 [CSS 参考](css-guide.md)。注册插值为离散；平滑呼吸优先用阴影、颜色、opacity。游戏毛玻璃不能读下层原版 Screen。
- `height: auto` 不能直接过渡：用 `Motion.toggle`。
- 支持 `repeating-radial-gradient`，也支持重复线性/锥形渐变。
