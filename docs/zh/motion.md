# 动效规范

所有动画只用 `ore.css` 里的命名动效和 `dev.oriel.kit.Motion`，不要在页面里自己写时长和曲线。游戏里打开 `/oriel demo catalog` →「动效」可以逐个点着看。

## 1. 时长与曲线（`--m-*`）

| 变量 | 值 | 用在 |
|---|---|---|
| `--m-fast` | 120ms | 悬停、按下、颜色变化 |
| `--m-base` | 220ms | 展开收起、标签切换、提示出现 |
| `--m-slow` | 360ms | 进场、转场、弹层 |
| `--m-out` | 快出慢停 | 进入画面的东西 |
| `--m-in` | 慢起快走 | 离开画面的东西 |
| `--m-in-out` | 两头慢 | 在画面里移动的东西（转场的面板） |
| `--m-spring` | 轻微回弹 | 弹出、按钮松开、指示条 |

规则：进来用 out，出去用 in，原地变化用 in-out；回弹只给"有分量的小东西"（按钮、徽章、弹窗），大面板不要弹。

## 2. 动效清单

| 场景 | 写法 | 效果 |
|---|---|---|
| 按下 | `class="m-press"` | 按下缩到 95% 下沉 1px，松开弹回 |
| 像素涟漪 | `class="m-ripple"`（Shell 自动接好） | 从按下的点长出一个方形光环 |
| 悬停抬起 | `.o-tile.hot` 自带；其他元素 `m-lift` | 上浮 2px + 稀有度色光晕 |
| 指针聚光 | `class="m-spot"` | 一团柔光跟着鼠标走（代替引擎画不了的 3D 倾斜） |
| 进场 | `m-rise` `m-pop` `m-drop` `m-fade-in` `m-slide-left/right` | 一次性进场 |
| 依次进场 | 容器 `class="m-stagger"` | 子元素间隔 40ms 依次浮上来 |
| 滚动浮现 | 元素 `m-reveal`，容器 `Motion.reveal(scroller)` | 滚到可见时浮现 |
| 展开收起 | `Motion.toggle(body, open)`；箭头 `m-caret` | 高度平滑过渡，箭头转 180° |
| 指示条 | `<i class="m-indicator">` + `Motion.indicator(bar, tab)` | 一根光条滑到选中的标签下 |
| 数字 | `data-count="2235"`，或 `Motion.count(el, 0, n, ms, 后缀)` | 数字滚动到目标 |
| 转场 | 链接写 `data-go="forge" data-zoom` | 卡片放大成新页面，旧页淡出，新窗口放大进来 |
| 弹层 | `.m-modal` 加 `on` | 背景淡入，面板弹起 |
| 提示 | `.o-toast` | 右上角滑入，2.6 秒后淡出 |
| 加载 | `.m-skeleton` | 灰色占位块上扫过一道光 |
| 成功 / 失败 / 提醒 | `Motion.play(el, "m-success"/"m-error", ms)`；`m-attention` | 放大一下发绿光 / 左右抖 + 红框 / 持续金色脉冲 |
| 一次性动画重播 | `Motion.play(el, 类名, 毫秒)` | 去掉再加上类名，保证每次都从头播 |

## 3. 什么时候用什么

- **每次操作都要有反馈**：按钮至少 `m-press` 或 Ore 下沉；保存这类重要操作加 `m-success` / `m-error`。
- **一屏一个主动效**：主视觉可以一直动（转动的 3D、粒子），其余元素只在交互时动。
- **常驻循环动画 ≤ 3 处**（呼吸、扫光、漂浮、粒子都算）。设计检查器超过 8 个发光元素会警告。
- **进场只播一次**，不要在每次数据刷新时重播（控制器里只改内容，别重建整块）。
- **尊重减少动效**：`<html data-motion="reduced">` 时全部停下但内容照常显示。

## 4. 引擎限制（写动效前先看）

- 没有 3D 变换（`perspective`、`rotateY`）：3D 用 `<model>` / `<entity>` / `<scene>` 的 `-mc-yaw`、`-mc-pitch`。
- 没有 `filter` / `backdrop-filter` / 混合模式：发光用 `box-shadow` 和半透明渐变叠加。
- `height: auto` 不能直接过渡：用 `Motion.toggle`。
- 没有 `repeating-radial-gradient`：用多段 `radial-gradient` 代替。
