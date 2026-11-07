# CSS 支持参考

> 本页由 `engine/src/test/java/dev/oriel/engine/css/CssReferenceTest.java` 生成，**不要手改**。
> 属性、继承、动画来自引擎的属性表；每个属性接受哪些关键字和值类型，是把候选值交给引擎真实的解析器试出来的；
> 选择器、at 规则、单位、函数同理。引擎改了 CSS 支持而没重新生成，测试会失败。
> 重新生成：`ORIEL_UPDATE_DOCS=1 ./gradlew :engine:test --tests '*CssReferenceTest'`

[文档总目录](index.md)

## 怎么读这份表

- **不支持就会报错**：未知属性、未知单位、非法取值、不支持的 at 规则都会产生诊断（含文件与行号，并尽量给出建议），不会静默当作 px 或透明。游戏里在页面错误面板看；开发时用 `./gradlew :preview:checkUi` 或 MCP 的 `oriel_check` 一次列出。
- **继承**：是，则子元素没写时沿用父元素的值（如 `color`、`font-*`）。
- **变化时**：`重排`＝改了会重新布局；`重绘`＝只重画，便宜得多。做动画优先选重绘类属性。
- **动画**：`连续`类（长度、数字、颜色、阴影、变换）可以平滑过渡；`离散`在动画中点直接切换；`否`不能动。
- **取值**：`关键字`列出引擎接受的写法；`类型`是除关键字外还接受的值类型；复合值（阴影、变换、渐变等）见下方“写法示例”。
- 所有属性都接受 `inherit`、`initial`、`unset`（`revert` 当作 `unset`）。

## 属性（145 个单项属性）

### 盒模型与定位

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `display` | 否 | 重排 | 离散 | 关键字：`block` `contents` `flex` `flow-root` `grid` `inline` `inline-block` `inline-flex` `inline-grid` `list-item` `none` |
| `position` | 否 | 重排 | 离散 | 关键字：`absolute` `fixed` `relative` `static` `sticky` |
| `box-sizing` | 否 | 重排 | 离散 | 关键字：`border-box` `content-box` |
| `width` | 否 | 重排 | 连续 | 关键字：`auto` `fit-content` `max-content` `min-content`；类型：长度、百分比 |
| `height` | 否 | 重排 | 连续 | 关键字：`auto` `fit-content` `max-content` `min-content`；类型：长度、百分比 |
| `min-width` | 否 | 重排 | 连续 | 关键字：`auto` `fit-content` `max-content` `min-content`；类型：长度、百分比 |
| `min-height` | 否 | 重排 | 连续 | 关键字：`auto` `fit-content` `max-content` `min-content`；类型：长度、百分比 |
| `max-width` | 否 | 重排 | 连续 | 关键字：`fit-content` `max-content` `min-content` `none`；类型：长度、百分比 |
| `max-height` | 否 | 重排 | 连续 | 关键字：`fit-content` `max-content` `min-content` `none`；类型：长度、百分比 |
| `top` | 否 | 重排 | 连续 | 关键字：`auto`；类型：长度、百分比 |
| `right` | 否 | 重排 | 连续 | 关键字：`auto`；类型：长度、百分比 |
| `bottom` | 否 | 重排 | 连续 | 关键字：`auto`；类型：长度、百分比 |
| `left` | 否 | 重排 | 连续 | 关键字：`auto`；类型：长度、百分比 |
| `margin-top` | 否 | 重排 | 连续 | 关键字：`auto`；类型：长度、百分比 |
| `margin-right` | 否 | 重排 | 连续 | 关键字：`auto`；类型：长度、百分比 |
| `margin-bottom` | 否 | 重排 | 连续 | 关键字：`auto`；类型：长度、百分比 |
| `margin-left` | 否 | 重排 | 连续 | 关键字：`auto`；类型：长度、百分比 |
| `padding-top` | 否 | 重排 | 连续 | 类型：长度、百分比 |
| `padding-right` | 否 | 重排 | 连续 | 类型：长度、百分比 |
| `padding-bottom` | 否 | 重排 | 连续 | 类型：长度、百分比 |
| `padding-left` | 否 | 重排 | 连续 | 类型：长度、百分比 |
| `z-index` | 否 | 重绘 | 连续 | 关键字：`auto`；类型：整数 |
| `visibility` | 是 | 重绘 | 离散 | 关键字：`collapse` `hidden` `visible` |
| `aspect-ratio` | 否 | 重排 | 连续 | 关键字：`auto`；类型：数字 |
| `writing-mode` | 是 | 重排 | 离散 | 关键字：`horizontal-tb` `vertical-lr` `vertical-rl` |
| `-webkit-text-stroke-width` | 是 | 重绘 | 连续 | 关键字：`medium` `thick` `thin`；类型：长度 |
| `-webkit-text-stroke-color` | 是 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |

### 边框与轮廓

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `border-top-width` | 否 | 重排 | 连续 | 关键字：`medium` `thick` `thin`；类型：长度 |
| `border-right-width` | 否 | 重排 | 连续 | 关键字：`medium` `thick` `thin`；类型：长度 |
| `border-bottom-width` | 否 | 重排 | 连续 | 关键字：`medium` `thick` `thin`；类型：长度 |
| `border-left-width` | 否 | 重排 | 连续 | 关键字：`medium` `thick` `thin`；类型：长度 |
| `border-top-style` | 否 | 重排 | 离散 | 关键字：`dashed` `dotted` `double` `groove` `hidden` `inset` `none` `outset` `ridge` `solid` |
| `border-right-style` | 否 | 重排 | 离散 | 关键字：`dashed` `dotted` `double` `groove` `hidden` `inset` `none` `outset` `ridge` `solid` |
| `border-bottom-style` | 否 | 重排 | 离散 | 关键字：`dashed` `dotted` `double` `groove` `hidden` `inset` `none` `outset` `ridge` `solid` |
| `border-left-style` | 否 | 重排 | 离散 | 关键字：`dashed` `dotted` `double` `groove` `hidden` `inset` `none` `outset` `ridge` `solid` |
| `border-top-color` | 否 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |
| `border-right-color` | 否 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |
| `border-bottom-color` | 否 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |
| `border-left-color` | 否 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |
| `border-top-left-radius` | 否 | 重绘 | 连续 | 类型：长度、百分比 |
| `border-top-right-radius` | 否 | 重绘 | 连续 | 类型：长度、百分比 |
| `border-bottom-right-radius` | 否 | 重绘 | 连续 | 类型：长度、百分比 |
| `border-bottom-left-radius` | 否 | 重绘 | 连续 | 类型：长度、百分比 |
| `outline-width` | 否 | 重绘 | 连续 | 关键字：`medium` `thick` `thin`；类型：长度 |
| `outline-style` | 否 | 重绘 | 离散 | 关键字：`auto` `dashed` `dotted` `double` `groove` `hidden` `inset` `none` `outset` `ridge` `solid` |
| `outline-color` | 否 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |
| `outline-offset` | 否 | 重绘 | 连续 | 类型：长度 |
| `border-image-source` | 否 | 重绘 | 离散 | 关键字：`none`；类型：图片 |
| `border-image-slice` | 否 | 重绘 | 离散 | 类型：百分比、数字 |
| `border-image-width` | 否 | 重绘 | 离散 | 关键字：`auto`；类型：长度、百分比、数字 |
| `border-image-outset` | 否 | 重绘 | 离散 | 类型：长度、数字 |
| `border-image-repeat` | 否 | 重绘 | 离散 | 关键字：`repeat` `round` `space` `stretch` |

### Flex 与对齐

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `flex-direction` | 否 | 重排 | 离散 | 关键字：`column` `column-reverse` `row` `row-reverse` |
| `flex-wrap` | 否 | 重排 | 离散 | 关键字：`nowrap` `wrap` `wrap-reverse` |
| `justify-content` | 否 | 重排 | 离散 | 关键字：`auto` `baseline` `center` `end` `first baseline` `flex-end` `flex-start` `last baseline` `left` `normal` `right` `self-end` `self-start` `space-around` `space-between` `space-evenly` `start` `stretch` |
| `align-items` | 否 | 重排 | 离散 | 关键字：`auto` `baseline` `center` `end` `first baseline` `flex-end` `flex-start` `last baseline` `left` `normal` `right` `self-end` `self-start` `space-around` `space-between` `space-evenly` `start` `stretch` |
| `align-content` | 否 | 重排 | 离散 | 关键字：`auto` `baseline` `center` `end` `first baseline` `flex-end` `flex-start` `last baseline` `left` `normal` `right` `self-end` `self-start` `space-around` `space-between` `space-evenly` `start` `stretch` |
| `align-self` | 否 | 重排 | 离散 | 关键字：`auto` `baseline` `center` `end` `first baseline` `flex-end` `flex-start` `last baseline` `left` `normal` `right` `self-end` `self-start` `space-around` `space-between` `space-evenly` `start` `stretch` |
| `justify-items` | 否 | 重排 | 离散 | 关键字：`auto` `baseline` `center` `end` `first baseline` `flex-end` `flex-start` `last baseline` `left` `normal` `right` `self-end` `self-start` `space-around` `space-between` `space-evenly` `start` `stretch` |
| `justify-self` | 否 | 重排 | 离散 | 关键字：`auto` `baseline` `center` `end` `first baseline` `flex-end` `flex-start` `last baseline` `left` `normal` `right` `self-end` `self-start` `space-around` `space-between` `space-evenly` `start` `stretch` |
| `flex-grow` | 否 | 重排 | 连续 | 类型：数字 |
| `flex-shrink` | 否 | 重排 | 连续 | 类型：数字 |
| `flex-basis` | 否 | 重排 | 连续 | 关键字：`auto` `fit-content` `max-content` `min-content`；类型：长度、百分比 |
| `order` | 否 | 重排 | 连续 | 类型：整数 |
| `row-gap` | 否 | 重排 | 连续 | 关键字：`normal`；类型：长度、百分比 |
| `column-gap` | 否 | 重排 | 连续 | 关键字：`normal`；类型：长度、百分比 |

### Grid

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `grid-template-columns` | 否 | 重排 | 离散 | 关键字：`auto` `max-content` `min-content` `none`；类型：长度、百分比（有示例） |
| `grid-template-rows` | 否 | 重排 | 离散 | 关键字：`auto` `max-content` `min-content` `none`；类型：长度、百分比 |
| `grid-template-areas` | 否 | 重排 | 离散 | 关键字：`none`；类型：字符串（有示例） |
| `grid-auto-columns` | 否 | 重排 | 离散 | 关键字：`auto` `max-content` `min-content`；类型：长度、百分比 |
| `grid-auto-rows` | 否 | 重排 | 离散 | 关键字：`auto` `max-content` `min-content`；类型：长度、百分比 |
| `grid-auto-flow` | 否 | 重排 | 离散 | 关键字：`column` `dense` `row` |
| `grid-column-start` | 否 | 重排 | 离散 | 任意名称（标识符）；类型：整数 |
| `grid-column-end` | 否 | 重排 | 离散 | 任意名称（标识符）；类型：整数 |
| `grid-row-start` | 否 | 重排 | 离散 | 任意名称（标识符）；类型：整数 |
| `grid-row-end` | 否 | 重排 | 离散 | 任意名称（标识符）；类型：整数 |

### 文本、字体与交互

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `vertical-align` | 否 | 重排 | 离散 | 关键字：`baseline` `bottom` `middle` `sub` `super` `text-bottom` `text-top` `top`；类型：长度、百分比 |
| `color` | 是 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |
| `font-family` | 是 | 重排 | 离散 | 任意名称（标识符）（有示例） |
| `font-size` | 是 | 重排 | 连续 | 关键字：`medium`；类型：长度、百分比 |
| `font-weight` | 是 | 重排 | 连续 | 关键字：`bold` `bolder` `lighter` `normal`；类型：数字 |
| `font-style` | 是 | 重排 | 离散 | 关键字：`italic` `normal` `oblique` |
| `line-height` | 是 | 重排 | 连续 | 关键字：`normal`；类型：长度、百分比、数字 |
| `letter-spacing` | 是 | 重排 | 连续 | 关键字：`normal`；类型：长度 |
| `word-spacing` | 是 | 重排 | 连续 | 关键字：`normal`；类型：长度 |
| `text-indent` | 是 | 重排 | 连续 | 类型：长度 |
| `text-align` | 是 | 重排 | 离散 | 关键字：`center` `end` `justify` `left` `right` `start` |
| `text-transform` | 是 | 重排 | 离散 | 关键字：`capitalize` `lowercase` `none` `uppercase` |
| `white-space` | 是 | 重排 | 离散 | 关键字：`break-spaces` `normal` `nowrap` `pre` `pre-line` `pre-wrap` |
| `list-style-type` | 是 | 重绘 | 离散 | 关键字：`circle` `disc` `none` `square` |
| `word-break` | 是 | 重排 | 离散 | 关键字：`anywhere` `break-all` `break-word` `keep-all` `normal` |
| `text-overflow` | 否 | 重绘 | 离散 | 关键字：`clip` `ellipsis` |
| `line-clamp` | 否 | 重排 | 离散 | 关键字：`none`；类型：整数 |
| `text-shadow` | 是 | 重绘 | 连续 | 关键字：`none`（有示例） |
| `cursor` | 是 | 重绘 | 离散 | 关键字：`alias` `all-scroll` `auto` `cell` `col-resize` `context-menu` `copy` `crosshair` `default` `e-resize` `ew-resize` `grab` `grabbing` `help` `move` `n-resize` `ne-resize` `nesw-resize` `no-drop` `none` `not-allowed` `ns-resize` `nw-resize` `nwse-resize` `pointer` `progress` `row-resize` `text` `vertical-text` `wait` `zoom-in` `zoom-out` |
| `pointer-events` | 是 | 重绘 | 离散 | 关键字：`all` `auto` `none` |
| `user-select` | 是 | 重绘 | 离散 | 关键字：`all` `auto` `contain` `none` `text` |
| `accent-color` | 是 | 重绘 | 连续 | 关键字：`auto` `currentcolor` `transparent`；类型：颜色 |

### 背景、遮罩与图像

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `object-fit` | 否 | 重绘 | 离散 | 关键字：`contain` `cover` `fill` `none` `scale-down` |
| `image-rendering` | 是 | 重绘 | 离散 | 关键字：`auto` `crisp-edges` `pixelated` `smooth` |
| `background-color` | 否 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |
| `mask-image` | 否 | 重绘 | 离散 | 关键字：`none`；类型：图片、渐变（有示例） |
| `mask-composite` | 否 | 重绘 | 离散 | 关键字：`add` `exclude` `intersect` `subtract` |
| `mask-clip` | 否 | 重绘 | 离散 | 关键字：`border-box` `content-box` `padding-box` |
| `content` | 否 | 重排 | 离散 | 关键字：`none` `normal`；类型：字符串 |
| `background-image` | 否 | 重绘 | 离散 | 关键字：`none`；类型：图片、渐变 |
| `background-position-x` | 否 | 重绘 | 离散 | 关键字：`center` `left` `right`；类型：长度、百分比 |
| `background-position-y` | 否 | 重绘 | 离散 | 关键字：`bottom` `center` `top`；类型：长度、百分比 |
| `background-size` | 否 | 重绘 | 离散 | 关键字：`auto` `contain` `cover`；类型：长度、百分比 |
| `background-repeat` | 否 | 重绘 | 离散 | 关键字：`no-repeat` `repeat` `repeat-x` `repeat-y` `round` `space` |
| `background-clip` | 否 | 重绘 | 离散 | 关键字：`border-box` `content-box` `padding-box` `text` |

### 特效与变换

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `opacity` | 否 | 重绘 | 连续 | 类型：百分比、数字 |
| `shape-rendering` | 是 | 重绘 | 离散 | 关键字：`auto` `crispEdges` `geometricPrecision` `optimizeSpeed` |
| `box-shadow` | 否 | 重绘 | 连续 | 关键字：`none`（有示例） |
| `transform` | 否 | 重绘 | 连续 | 关键字：`none`（有示例） |
| `filter` | 否 | 重绘 | 离散 | 关键字：`none`（有示例） |
| `backdrop-filter` | 否 | 重绘 | 离散 | 关键字：`none`（有示例） |
| `clip-path` | 否 | 重绘 | 离散 | 关键字：`none`（有示例） |
| `mix-blend-mode` | 否 | 重绘 | 离散 | 关键字：`color` `color-burn` `color-dodge` `darken` `difference` `exclusion` `hard-light` `hue` `lighten` `luminosity` `multiply` `normal` `overlay` `plus-lighter` `saturation` `screen` `soft-light` |
| `isolation` | 否 | 重绘 | 离散 | 关键字：`auto` `isolate` |

### 过渡与动画

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `transition-property` | 否 | 重绘 | 否 | 任意名称（标识符） |
| `transition-duration` | 否 | 重绘 | 否 | 类型：时间 |
| `transition-timing-function` | 否 | 重绘 | 否 | 关键字：`ease` `ease-in` `ease-in-out` `ease-out` `linear` `step-end` `step-start` |
| `transition-delay` | 否 | 重绘 | 否 | 类型：时间 |
| `animation-name` | 否 | 重绘 | 否 | 任意名称（标识符） |
| `animation-duration` | 否 | 重绘 | 否 | 关键字：`auto`；类型：时间 |
| `animation-timing-function` | 否 | 重绘 | 否 | 关键字：`ease` `ease-in` `ease-in-out` `ease-out` `linear` `step-end` `step-start` |
| `animation-delay` | 否 | 重绘 | 否 | 类型：时间 |
| `animation-iteration-count` | 否 | 重绘 | 否 | 关键字：`infinite`；类型：数字 |
| `animation-direction` | 否 | 重绘 | 否 | 关键字：`alternate` `alternate-reverse` `normal` `reverse` |
| `animation-fill-mode` | 否 | 重绘 | 否 | 关键字：`backwards` `both` `forwards` `none` |
| `animation-play-state` | 否 | 重绘 | 否 | 关键字：`paused` `running` |

### 滚动

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `overflow-x` | 否 | 重排 | 离散 | 关键字：`auto` `clip` `hidden` `overlay` `scroll` `visible` |
| `overflow-y` | 否 | 重排 | 离散 | 关键字：`auto` `clip` `hidden` `overlay` `scroll` `visible` |
| `scroll-behavior` | 否 | 重绘 | 离散 | 关键字：`auto` `smooth` |
| `scrollbar-width` | 否 | 重排 | 离散 | 关键字：`auto` `none` `thin` |

### Minecraft 专属（-mc-*）

| 属性 | 继承 | 变化时 | 动画 | 取值 |
|---|---|---|---|---|
| `-mc-tooltip-delay` | 是 | 重绘 | 离散 | 类型：时间 |
| `-mc-tint` | 是 | 重绘 | 连续 | 关键字：`currentcolor` `transparent`；类型：颜色 |
| `-mc-obfuscated` | 是 | 重绘 | 离散 | 关键字：`none` `off` `on` |
| `-mc-yaw` | 否 | 重绘 | 连续 | 类型：角度 |
| `-mc-pitch` | 否 | 重绘 | 连续 | 类型：角度 |
| `-mc-roll` | 否 | 重绘 | 连续 | 类型：角度 |
| `-mc-model-scale` | 否 | 重绘 | 连续 | 类型：数字 |
| `-mc-entity-focus` | 否 | 重绘 | 离散 | 关键字：`body` `center` `eyes` |
| `-mc-gaze-reach` | 否 | 重绘 | 连续 | 类型：长度 |

## 简写属性

简写只是把值分发给单项属性，没写的部分取初始值。

| 简写 | 展开为 |
|---|---|
| `-mc-gaze-limit` | （内部分量） |
| `-mc-scene-center` | （内部分量） |
| `-mc-translate` | （内部分量） |
| `-webkit-mask` | `mask-image` `mask-clip` `mask-composite` |
| `-webkit-text-stroke` | `-webkit-text-stroke-width` `-webkit-text-stroke-color` |
| `animation` | `animation-name` `animation-duration` `animation-timing-function` `animation-delay` `animation-iteration-count` `animation-direction` `animation-fill-mode` `animation-play-state` |
| `background` | `background-color` `background-image` `background-position-x` `background-position-y` `background-size` `background-repeat` `background-clip` |
| `background-position` | `background-position-x` `background-position-y` |
| `border` | `border-top-width` `border-right-width` `border-bottom-width` `border-left-width` `border-top-style` `border-right-style` `border-bottom-style` `border-left-style` `border-top-color` `border-right-color` `border-bottom-color` `border-left-color` |
| `border-bottom` | `border-bottom-width` `border-bottom-style` `border-bottom-color` |
| `border-color` | `border-top-color` `border-right-color` `border-bottom-color` `border-left-color` |
| `border-image` | `border-image-source` `border-image-slice` `border-image-width` `border-image-outset` `border-image-repeat` |
| `border-left` | `border-left-width` `border-left-style` `border-left-color` |
| `border-radius` | `border-top-left-radius` `border-top-right-radius` `border-bottom-right-radius` `border-bottom-left-radius` |
| `border-right` | `border-right-width` `border-right-style` `border-right-color` |
| `border-style` | `border-top-style` `border-right-style` `border-bottom-style` `border-left-style` |
| `border-top` | `border-top-width` `border-top-style` `border-top-color` |
| `border-width` | `border-top-width` `border-right-width` `border-bottom-width` `border-left-width` |
| `flex` | `flex-grow` `flex-shrink` `flex-basis` |
| `flex-flow` | `flex-direction` `flex-wrap` |
| `font` | `font-style` `font-weight` `font-size` `line-height` `font-family` |
| `gap` | `row-gap` `column-gap` |
| `grid-area` | `grid-row-start` `grid-column-start` `grid-row-end` `grid-column-end` |
| `grid-column` | `grid-column-start` `grid-column-end` |
| `grid-row` | `grid-row-start` `grid-row-end` |
| `grid-template` | `grid-template-rows` `grid-template-columns` `grid-template-areas` |
| `inset` | `top` `right` `bottom` `left` |
| `list-style` | `list-style-type` |
| `margin` | `margin-top` `margin-right` `margin-bottom` `margin-left` |
| `mask` | `mask-image` `mask-clip` `mask-composite` |
| `object-position` | （内部分量） |
| `outline` | `outline-width` `outline-style` `outline-color` |
| `overflow` | `overflow-x` `overflow-y` |
| `padding` | `padding-top` `padding-right` `padding-bottom` `padding-left` |
| `place-content` | `align-content` `justify-content` |
| `place-items` | `align-items` `justify-items` |
| `place-self` | `align-self` `justify-self` |
| `scrollbar-color` | （内部分量） |
| `text-decoration` | （内部分量） |
| `text-decoration-line` | （内部分量） |
| `transform-origin` | （内部分量） |
| `transition` | `transition-property` `transition-duration` `transition-timing-function` `transition-delay` |

别名（写哪个都行）：`overflow-wrap`、`word-wrap`→`word-break`；`-webkit-line-clamp`→`line-clamp`；`-webkit-backdrop-filter`、`-webkit-background-clip`、`-webkit-clip-path`、`-webkit-mask-image`、`-webkit-mask-composite`、`-webkit-mask-clip` 去掉前缀的同名属性。

## 写法示例（复合值）

以下每条都经过引擎解析验证。

| 属性 | 示例 |
|---|---|
| `transform` | `translate(10px, 4px) rotate(8deg) scale(1.1)` |
| `transition` | `transform .2s ease-out, opacity .2s` |
| `animation` | `pop .3s ease-out both` |
| `box-shadow` | `0 2px 6px rgba(0,0,0,.4), inset 0 0 0 1px #fff3` |
| `text-shadow` | `1px 1px 0 #000` |
| `filter` | `blur(2px) brightness(1.2)` |
| `backdrop-filter` | `blur(4px)` |
| `clip-path` | `polygon(0 0, 100% 0, 50% 100%)` |
| `background` | `linear-gradient(#444, #222) no-repeat center / cover, #111` |
| `border` | `2px solid #555` |
| `border-radius` | `4px 4px 0 0` |
| `border-image` | `url(frame.png) 6 / 6px stretch` |
| `grid-template-columns` | `repeat(3, 1fr) 80px` |
| `grid-template-areas` | `"a a b" "c c b"` |
| `flex` | `1 1 0` |
| `font` | `bold 12px/1.4 sans-serif` |
| `font-family` | `"Fusion Pixel", sans-serif` |
| `mask-image` | `linear-gradient(#000, transparent)` |
| `outline` | `1px solid #fff` |
| `inset` | `0 auto auto 0` |
| `-mc-translate` | `0 1 0` |
| `-mc-scene-center` | `4 0 4` |
| `-mc-gaze-limit` | `40deg 20deg 10deg` |
| `object-position` | `center top` |
| `transform-origin` | `50% 100%` |
| `text-decoration` | `underline` |

## 值：单位、函数、颜色、图片、变换

✅ 支持　❌ 不支持（会报诊断）　⚠ 近似（会提示）

### 长度、数学与变量

**单位**（`width`）：`1px` ✅　`1em` ✅　`1rem` ✅　`1%` ✅　`1vw` ✅　`1vh` ✅　`1vmin` ✅　`1vmax` ✅　`1dp` ✅　`1ch` ❌　`1ex` ❌　`1cm` ❌　`1mm` ❌　`1in` ❌　`1pt` ❌　`1pc` ❌　`1lh` ❌　`1svh` ❌　`1dvh` ❌　`1cqw` ❌

**数学函数**（`width`）：`calc(100% - 4px)` ✅　`calc(2 * 3px)` ✅　`min(10px, 5%)` ✅　`max(10px, 5%)` ✅　`clamp(10px, 5%, 40px)` ✅　`round(7.3px, 2px)` ❌　`abs(-4px)` ❌　`sign(-4px)` ❌　`mod(7px, 3px)` ❌　`pow(2, 3) * 1px` ❌　`sqrt(16) * 1px` ❌　`sin(30deg) * 10px` ❌

**变量与引用**（`width`）：`var(--w)` ✅　`var(--w, 10px)` ✅　`env(safe-area-inset-top)` ❌

**属性值引用**（`content`）：`attr(data-x)` ✅　`"文字"` ✅　`counter(a)` ✅

### 颜色

**颜色写法**（`color`）：`#f80` ✅　`#ff8800` ✅　`#f808` ✅　`#ff880080` ✅　`rgb(255 136 0)` ✅　`rgba(255, 136, 0, .5)` ✅　`hsl(30 100% 50%)` ✅　`hsla(30, 100%, 50%, .5)` ✅　`hwb(30 0% 0%)` ❌　`lab(60% 40 60)` ❌　`lch(60% 70 50)` ❌　`oklab(0.7 0.1 0.1)` ❌　`oklch(0.7 0.15 60)` ❌　`color(srgb 1 0.5 0)` ❌　`color-mix(in srgb, red 30%, blue)` ✅　`light-dark(white, black)` ❌　`currentColor` ✅　`transparent` ✅　`rebeccapurple` ✅　`mc-gold` ✅　`Canvas` ❌

### 图片与渐变

**图片值**（`background-image`）：`url(a.png)` ✅　`sprite(minecraft:icon/x)` ✅　`linear-gradient(red, blue)` ✅　`radial-gradient(red, blue)` ✅　`conic-gradient(red, blue)` ✅　`repeating-linear-gradient(red, blue 10px)` ✅　`repeating-radial-gradient(red, blue 10px)` ✅　`repeating-conic-gradient(red, blue 10deg)` ✅　`image-set(url(a.png) 1x)` ❌　`cross-fade(url(a.png), url(b.png))` ❌　`element(#a)` ❌　`url(a.png), linear-gradient(red, blue)` ✅

### 变换

**变换函数**（`transform`）：`translate(1px, 2px)` ✅　`translateX(1px)` ✅　`translateY(1px)` ✅　`translateZ(1px)` ❌　`translate3d(1px, 2px, 3px)` ❌　`scale(1.2)` ✅　`scaleX(1.2)` ✅　`scaleY(1.2)` ✅　`scaleZ(1.2)` ❌　`scale3d(1, 1, 1)` ❌　`rotate(10deg)` ✅　`rotateX(10deg)` ❌　`rotateY(10deg)` ❌　`rotateZ(10deg)` ✅　`rotate3d(0, 0, 1, 10deg)` ❌　`skew(10deg)` ✅　`skewX(10deg)` ✅　`skewY(10deg)` ✅　`matrix(1, 0, 0, 1, 0, 0)` ✅　`matrix3d(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)` ❌　`perspective(100px)` ❌

**独立变换属性**（`translate`）：`10px 4px` ❌

**独立变换属性**（`rotate`）：`10deg` ❌

**独立变换属性**（`scale`）：`1.2` ❌

### 滤镜与形状

**滤镜函数**（`filter`）：`blur(2px)` ✅　`brightness(1.2)` ✅　`contrast(1.2)` ✅　`grayscale(1)` ✅　`invert(1)` ✅　`opacity(.5)` ✅　`saturate(2)` ✅　`sepia(1)` ✅　`hue-rotate(30deg)` ✅　`drop-shadow(1px 1px 2px #000)` ✅　`url(#f)` ❌

**clip-path 形状**（`clip-path`）：`inset(4px round 2px)` ✅　`circle(50%)` ✅　`ellipse(40% 30%)` ✅　`polygon(0 0, 100% 0, 50% 100%)` ✅　`path('M0 0 L10 10')` ❌　`url(#c)` ❌

### 缓动

**缓动函数**（`transition-timing-function`）：`ease` ✅　`linear` ✅　`ease-in` ✅　`ease-out` ✅　`ease-in-out` ✅　`step-start` ✅　`step-end` ✅　`steps(4)` ✅　`steps(4, jump-end)` ✅　`cubic-bezier(.2, .8, .2, 1)` ✅　`linear(0, .5, 1)` ❌

## 选择器

### 基本形式

`*` ✅　`div` ✅　`.a` ✅　`#a` ✅　`[a]` ✅　`[a=b]` ✅　`[a~=b]` ✅　`[a|=b]` ✅　`[a^=b]` ✅　`[a$=b]` ✅　`[a*=b]` ✅　`[a=b i]` ✅　`a b` ✅　`a > b` ✅　`a + b` ✅　`a ~ b` ✅　`a, b` ✅　`a.b#c[d]:hover` ✅　`&.a` ❌　`ns|a` ❌

### 伪类

`:hover` ✅　`:active` ✅　`:focus` ✅　`:focus-visible` ✅　`:focus-within` ✅　`:checked` ✅　`:disabled` ✅　`:enabled` ✅　`:empty` ✅　`:root` ✅　`:first-child` ✅　`:last-child` ✅　`:only-child` ✅　`:first-of-type` ✅　`:last-of-type` ✅　`:only-of-type` ✅　`:placeholder-shown` ✅　`:open` ✅　`:scope` ✅　`:any-link` ✅　`:link` ✅　`:visited` ❌　`:target` ✅　`:read-only` ❌　`:read-write` ❌　`:required` ❌　`:optional` ❌　`:valid` ❌　`:invalid` ❌　`:indeterminate` ❌　`:default` ❌　`:in-range` ❌　`:out-of-range` ❌　`:host` ❌　`:lang(zh)` ❌　`:dir(ltr)` ❌　`:not(.a)` ✅　`:is(.a, .b)` ✅　`:where(.a)` ✅　`:has(> .a)` ✅　`:nth-child(2n+1)` ✅　`:nth-last-child(2)` ✅　`:nth-of-type(odd)` ✅　`:nth-last-of-type(even)` ✅　`:nth-child(2n of .a)` ✅

### 伪元素

`::before` ✅　`::after` ✅　`::placeholder` ✅　`::selection` ❌　`::first-line` ❌　`::first-letter` ✅　`::marker` ❌　`::backdrop` ❌　`::-webkit-scrollbar` ❌　`:before` ✅　`:after` ✅

## At 规则

| 规则 | 状态 |
|---|---|
| `@media` | ✅ |
| `@supports` | ✅ |
| `@import` | ✅ |
| `@keyframes` | ✅ |
| `@layer` | ⚠ 能用，但层级顺序不生效（压平） |
| `@font-face` | ❌ 忽略并提示 |
| `@container` | ❌ 忽略并提示 |
| `@property` | ❌ 忽略并提示 |
| `@page` | ❌ 忽略并提示 |
| `@namespace` | ❌ 忽略并提示 |
| `@charset` | ✅ |
| `@scope` | ❌ 忽略并提示 |
| `@starting-style` | ❌ 忽略并提示 |
| `@counter-style` | ❌ 忽略并提示 |
| `@view-transition` | ❌ 忽略并提示 |

## 媒体查询特性

`@media` 里可用：`width`、`height`（含 `min-`/`max-` 前缀和 `>=` 这类范围写法）、`orientation`、`prefers-reduced-motion`、`gui-scale`（Minecraft 的 GUI 缩放，Oriel 专有，可写 `min-gui-scale`）。其余特性不会匹配。

## 常见但不支持的属性

下面这些在浏览器里常见，Oriel 里**没有**。写了会得到诊断和“你是不是想写…”的建议。

`float`、`clear`、`columns`、`column-count`、`column-width`、`column-rule`、`table-layout`、`border-collapse`、`border-spacing`、`caption-side`、`empty-cells`、`direction`、`unicode-bidi`、`text-orientation`、`hyphens`、`tab-size`、`text-wrap`、`text-justify`、`text-decoration-color`、`text-decoration-style`、`text-decoration-thickness`、`text-underline-offset`、`text-emphasis`、`font-variant`、`font-feature-settings`、`font-stretch`、`font-kerning`、`quotes`、`counter-reset`、`counter-increment`、`list-style-position`、`list-style-image`、`resize`、`appearance`、`caret-color`、`will-change`、`contain`、`content-visibility`、`container-type`、`scroll-snap-type`、`overscroll-behavior`、`touch-action`、`perspective`、`perspective-origin`、`transform-style`、`backface-visibility`、`translate`、`rotate`、`scale`、`offset-path`、`clip`、`shape-outside`、`mask-position`、`mask-size`、`mask-repeat`、`mask-mode`、`background-attachment`、`background-blend-mode`、`background-origin`、`margin-inline`、`padding-inline`、`margin-block`、`inset-inline`、`border-start-start-radius`、`line-break`、`overflow-anchor`、`scrollbar-gutter`、`color-scheme`、`print-color-adjust`、`zoom`、`all`、`field-sizing`、`anchor-name`、`position-anchor`、`text-align-last`、`font-optical-sizing`、`font-synthesis`、`image-orientation`、`ruby-position`、`text-rendering`

（以下名字在候选里，但引擎其实支持：`writing-mode`、`scrollbar-color`）

其中 `float`、表格布局（`display: table`）、`position: sticky`（按 `relative` 处理并提示）、CSS 嵌套、`@layer` 的层级顺序目前都没有或只是近似；表格请用 `display: grid`，内联 SVG 请用 `<img>`、渐变或边框代替（引擎会按元素给出建议）。
