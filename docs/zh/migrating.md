# 把 mod 的界面迁到 Oriel

[入口](index.md) · [接入指南](integration.md) · [制作流程](ui-workflow.md) · [脚本](scripting.md)

怎么把一个手绘的界面（自定义的 `Screen` 子类，带布局计算、手动文字换行、命中测试和滚动状态）迁成 Oriel 页面。请先读 [Java 接入 API](java-api.md)，再读[脚本](scripting.md)（JavaScript 方言和模板）。

## 1. 先设置依赖

依赖写法见[接入指南](integration.md#2-获取与依赖)，不要使用上游 Maven 获取本 fork。H5 制作和移植的完整步骤见[制作流程](ui-workflow.md)，下面的表保留原生 Screen 向 Oriel 迁移的对应关系。

如果你的大部分界面都迁到了 Oriel，就把它设成必需依赖（`neoforge.mods.toml` 里 `type="required"`，`fabric.mod.json` 里 `"depends"`），不要为每个界面保留一个原版的后备界面。一个功能两套界面，正是你要通过迁移消除的重复。在你的 Modrinth 和 CurseForge 页面上也把它列为必需依赖，或者把它打进你的 jar。

## 2. Oriel 界面的形状

大多数手绘的界面已经分成了三部分：服务器（或客户端的模型）构建视图模型，界面把它画出来，点击变成动作。保留这个结构，只替换绘制部分：

| 迁移前 | 迁移后 |
|---|---|
| `ViewModel` record，由 `MyScreen.render(...)` 绘制 | 同样的数据作为 JSON 放进 `oriel.data`；由模板渲染 |
| 点击命中测试调用 `sendAction("x", arg)` | `@click="oriel.send('x', arg)"`，由 `onMessage("x", …)`（客户端）或 `session.onMessage("x", …)`（服务端）接收 |
| "每 N 个 tick 刷新"、重建控件、恢复焦点和滚动 | `driver.push(json)` / `session.push(json)`；DOM 原地更新，焦点、滚动和草稿都保留 |

页面放在你 jar 里的 `assets/<modid>/oriel/<name>.html`（旁边放 `.css`/`.js`），用 id `<modid>:oriel/<name>.html` 打开。把共用样式（用 CSS 自定义属性表示的调色板、面板、字体排印）放进一个样式表，由每个页面链接。

## 3. 什么替代什么

| 现在手写的 | 在 Oriel 里 |
|---|---|
| `width - 8`、`Mth.clamp(pw * 27 / 100, 108, 138)`、两栏与一栏的分支 | flexbox / grid、`min()`/`max()`/`clamp()`、`@media (max-width: …)` |
| `font.split`、省略号、限制 N 行、两端对齐 | 正常的文字流、`text-overflow: ellipsis`、`line-clamp`、`text-align: justify` |
| 滚动偏移、滚动条、"跟随底部"、拖动滑块 | `overflow: auto`（叠加滚动条、滚轮、拖动、平滑）、`el.scrollTo` / `scrollIntoView` |
| 每帧的悬停矩形、链接命中测试 | 带 `:hover`、`@click`、`<a href>` 的元素 |
| 淡入、滑动、打字机的定时器 | `transition`、`@keyframes`、`el.animate()`，或页面脚本里的一个小定时器 |
| 调色板常量 | CSS 自定义属性（`--panel: #1b1b22`） |
| `EditBox`、`Button`、滑块、循环按钮 | `<input>`、`<button>`、`<input type=range>`、`<select>`、复选框（缺省是原版外观，可自由改样式） |
| `g.entity(...)` 肖像 | `<entity type="…" follow-mouse>`、`<entity id="<网络 id>">`、`<entity player>`；`rotatable`，以及 CSS `-mc-yaw`/`-mc-pitch`/`-mc-model-scale`；用 `-mc-gaze-reach` 和 `-mc-gaze-limit` 让注视比背包界面更柔和或有上限；用 `-mc-entity-focus: eyes` 和 `object-position` 裁头肩特写；用 `OrielEntities.registerPortraitState` 提供自己的渲染状态（不带气泡或标签）；方块和物品用 `<model block="…">` 或 `<model item="…">`（详见[元素](elements.md)） |
| `g.item(...)` 图标、物品提示 | `<item id="…" count="…" tooltip>` |
| `NativeImage` + `DynamicTexture` 的地图和程序化图像 | 把纹理注册到某个 `Identifier` 下，用 `<img src="mymod:dynamic/map">` 或 `background: url(mymod:dynamic/map)`；或者用 `<canvas>` 的 `getContext('2d')` 绘制 |
| `blitSprite(...)` 九宫格 | `background: sprite(mymod:widget/panel)`（遵守该精灵 `.mcmeta` 的缩放设置） |
| 手写淡入淡出的 HUD 层 | `OrielHud.register(id, url)`、`show`/`hide`/`push`；淡入淡出用 CSS 动画 |
| 箱子式菜单 | `OrielScreens.registerContainer(menuType, url)`，并把 `<slot index="n">` 放在槽位该在的地方（见[容器页面](containers.md)） |
| 英文字面量 | `<mc-text key="…">` 或 `oriel.t(key, …args)`（Minecraft 语言文件） |

## 4. 怎么迁一个界面

1. 列出界面显示什么、发出哪些动作。把视图模型的 JSON 形状写下来（往往已经以载荷或 record 的形式存在）。
2. 先在预览器里把页面做出来（命令见[验收工具](validation.md)），用真实的样本数据，保存即热重载，F12 打开检查器。
3. 用 `oriel.send(channel, value)` 接动作，在原来处理界面动作的地方处理它们。
4. 在原来打开旧界面的地方打开它（客户端 `OrielScreens.open(url, data)`，服务端 `OrielServer.open(player, url, data)`），然后推送更新，而不是重建。
5. 在游戏里用热重载检查它（开发客户端运行时编辑 `src/main/resources` 下的页面），并用你的开发自动巡检在 GUI 缩放 2 和 3 下截图。
6. 页面覆盖了功能之后，删除旧界面和它的布局辅助代码。

## 5. 注意事项

- 脚本是 Rhino 的方言，但已经支持 `class`、`async`/`await`、调用里的展开和 `for (const x of …)`；不能用的是私有类成员、async 生成器、`import`/`export`、对象 rest 解构和 `Intl`。完整清单、与浏览器的差异见[脚本](scripting.md#语言)。
- 模板在下一帧更新。改变状态后立刻读 DOM 的代码要用 `oriel.nextTick(fn)`。
- `oriel.data` 的键会遮住模板表达式里的 `oriel.state` 和全局变量。把页面的选项嵌套在一个键下（`{start: {...}}`），不要平铺在顶层。
- 像素尺寸：`px` 是一个 GUI 像素；缺省字体是 8px，在 8 的倍数上保持锐利（12 和 16 也可以）。1280×720 的窗口下，GUI 缩放 2 是 640×360，缩放 3 约 427×240；按这个范围设计，两个缩放都要检查。
- 活的实体耗帧时间。一个界面几个没问题；长列表里用刷怪蛋或 `<item>` 图标。
- 不支持的 CSS 写法会在页面检查和日志里得到带文件位置的诊断，不会悄悄失效；当你看到某处样子不对，先看检查结果和预览器的检查器。目前不支持的布局：float、表格（用 Grid/Flex）、多栏、CSS 3D 透视；sticky 会按 relative 近似并给出提示。完整边界见[能力边界](capabilities.md)。

## 6. 问题出在 Oriel 上时

如果迁移需要 Oriel 做不到的东西（缺少某个 CSS 特性、元素、API 钩子），或者碰到了 bug，请带着能复现的最小页面向 Oriel 维护者报告。请不要在你的 mod 里绕过去，也不要去改 Oriel 的副本。修复这样会落进 Oriel，惠及每一个 mod。
