# Ore 组件与参数

[入口](index.md) · [设计](design.md) · [动效](motion.md) · [制作流程](ui-workflow.md)

主题资源：`oriel:ui/ore.css`。kit 与 PageControllers 是项目内复用层，尚非独立稳定第三方 SDK。**加载 CSS 只得到外观；Shell/Widgets 的 data-* 行为必须有控制器接线。**

## 外壳与布局

`Shell.setup` 统一接入[UI 声音 token](capabilities.md#ui-音效)：按钮、页签、开关和 `.m-modal.on` 状态；`data-oriel-sound` 可覆盖点击 token 或设 `off` 静音，`hover` 仅显式悬停播放。纯 HTML 用 `data-oriel-shell` 接线，只有 CSS 不接声音。

```html
<div class="o-app"><div class="o-window">
  <header class="o-titlebar"><h1>编辑器</h1>
    <button type="button" class="ore-btn" data-action="close">关闭</button>
  </header>
  <div class="o-body">
    <main class="o-main"><section class="o-tile">内容</section></main>
    <aside class="o-side">预览/详情</aside>
  </div>
</div></div>
```

| 类/变量 | 结构/默认 | 作用 |
|---|---|---|
| o-app / o-window / o-titlebar / o-body | 上述层级 | 背景、窗口、标题、内容；转场主要移动 o-body |
| o-rail / o-main / o-side | o-body 内兄弟；side 默认 150px | 导航、主区、详情；小尺寸收起次要栏 |
| o-tile / o-tile.hot / o-tile.sel | 内容块 / 可点 / 选中 | hot 有视觉反馈，不自动执行业务 |
| o-well / ore-slot / ore-field | 凹陷内容区 | 展示槽不等于真正 slot |
| o-cols / o-cols.three | 2/3 个 1fr 列 | --gap 默认 8px |
| o-split | --side:120px; --gap:8px | 左栏+主区 |
| o-auto | --min:64px; --gap:8px | auto-fill + minmax 自动列数 |
| o-center / o-stack / o-spread | --gap:8px | 居中竖排 / 竖堆 / 横向两端对齐 |
| o-ratio | --ratio:16 / 9 | 宽高比 |
| o-hidden | 隐藏状态 | data-show 页签联动 |
| o-h1 / o-h2 / o-label / o-dim / o-faint / o-accent / o-num / o-caps | 文本用途 | 正文色跟随主题 |

## 控件结构和接线

| 组件 | 必需结构/状态 | 默认交互与业务责任 |
|---|---|---|
| 按钮 | button.ore-btn；hero/primary/secondary/destructive/icon/big/disabled | hover/active 是 CSS；点击由控制器/脚本接。disabled 类是外观，原生按钮另设 disabled |
| 页签 | .ore-tabs > .ore-tab；选中 selected；可放 .m-indicator | Widgets 选中；data-show=#pane 切内容 |
| 分段 | .o-seg > *；选中 on | Widgets 切 on；带导航/外观动作的项由相应监听者处理 |
| 开关 | .ore-toggle；on=开 | 无 id 自动翻 on；有 id 必须自己接 |
| 芯片 | .o-chip.hot；on=选中 | 无 id 自动翻 on；不自动同步服务端 |
| 滑块 | .ore-slider 下 .track、.fill、.thumb | 无 id 自动接，初值读 fill 行内 width 百分比，缺省 0.5；有 id 手工 new Slider |
| 下拉 | .ore-select > .value 与 .menu > .option | open=展开、chosen=选定、active=键盘项；无 id 自动接，有 id 自己接；方向/Enter/Esc |
| 菜单栏 | .o-menubar > .o-menu，内 .title 和 .item | open 展开；选项业务自行接 |
| 抽屉/浮动面板 | .o-drawer / .o-sheet 内 .o-drawer-handle | shut 收起；drawer 参与布局，sheet 覆盖 |
| 浮层 | data-pop=目标id；目标 .o-pop | 值不加 #；Widgets 放置与开关 |
| 右键菜单 | data-context=目标id；目标 .o-context | Widgets 处理右键/关闭；菜单业务自行接 |
| 格式输入 | .o-mcfield 内 input.ore-field | Shell 接 McField 工具条和预览 |
| 懒显示 | .o-lazy | 进入可视区域逐步揭开；图片读取请另用 img loading=lazy / URL 背景 data-oriel-lazy，见[性能规范](performance.md#列表图片懒加载) |
| 预加载遮罩 | .o-preload；data-min 毫秒，默认 500 | Lazy.ready(doc) 完成；Lazy.progress 更新进度 |

```html
<div class="ore-slider" id="power" tabindex="0">
  <i class="track"></i><i class="fill"></i><i class="thumb"></i>
</div>
```

Java：`new Slider(doc, doc.getElementById("power"), 10).onInput(v -> ...).onCommit(v -> ...)`。值范围 0–1，steps=10 表示十个间隔，0 连续；set(v) 钳到范围并更新视觉，**不调用回调**。实际数值由业务换算。onInput 用于即时预览，onCommit 用于松手/键盘操作后的提交。

## 状态效果面板

药水 HUD 的完整面板（`PotionHudPreset`），也嵌在 kit 背包的"效果"页签里。**这是界面设计和预览，库不会把它放进游戏**：这类界面要放进游戏，需要用 Mixin 修改 Minecraft 自己的逻辑；Oriel 支持设计这类界面，但**不会注入**，请在你自己的模组里接入（见下面的"放进游戏的做法"）。效果按增益、减益、中性分组，每组可以显示成两种样子：

| 样子 | 内容 | 什么时候 |
|---|---|---|
| 行 | 图标、名称和等级、剩余时间；背景色条随剩余时间缩短，最后 10 秒闪烁 | 组内效果不超过阈值时 |
| 图标块（`.group.folded .strip .chip`） | 18×18 图标；变暗遮罩随时间增长；等级大于 1 时右下角显示数字（像物品堆叠数）；快到期时闪烁 | 组内效果超过阈值时，或位置太窄时（`InventoryEffectsPreset.NARROW`，100px） |

- 行和图标块悬停都会弹出同一张信息卡（`.o-efftip`）：名称、等级、类别、作用说明、剩余/总时长和效果 id。信息卡放在面板旁边，不挡在指针下面。
- 点组标题可以在行和图标块之间切换；玩家的选择会保留，不会被新增或到期的效果改回去。
- 嵌入：`new PotionHudPreset().mountPanel(doc, where, compactOver)`。`where` 是可以滚动的容器；`compactOver` 是阈值：原版背包旁用 `PotionHudPreset.COMPACT_OVER`（4），有足够高度的页签传 0（始终显示行，玩家仍可手动切换）。
- 滚动提示：面板会给 `where` 的父元素加 `more-above` / `more-below` 类，表示上方或下方还有内容被遮住。页面用它在对应边缘画渐隐（见 `inventory_effects.html`）。
- 预览用示例效果（会自己到期、新增）。要显示玩家真实的效果，在你自己的模组里调用 `GameEffects.provide(...)` 提供数据。
- **放进游戏的做法（你自己的模组做）**：药水栏要放在原版背包旁或原版效果图标的位置，必须改 Minecraft 自己的逻辑（隐藏原版列表、读背包位置），这需要 Mixin 或加载器的屏幕事件。Oriel 不做这一步，也不注册任何 Mixin：好处是装上库不会动原版界面，出问题的范围小。`InventoryEffectsPreset` 是专为这种场景准备的面板页，宿主发 `place`（x、y、宽、高）给它放位置，下游模组自己决定放哪、怎么隐藏原版列表。

## Shell 的 data-* 合同

控制器每次 attach 调用一次 Shell.setup(doc)，返回 Particles。**纯 HTML 页面（玩家放在 my-pages 里的文件、没有 Java 的资源页）不用写控制器：在 `<html>` 或 `<body>` 上加 `data-oriel-shell`，库就会替它调用同一个 Shell.setup**，于是涟漪、聚光、数字计数、主题与图标切换、`data-action` 的关闭/返回、粒子画布等声明式效果照常生效。有自己控制器的页面不受影响。它设置共享主题/字体/图标/窗口模式，接 Widgets/Notice/粒子等；重复 setup 可能重复监听，不要每次刷新再调用。

| 属性 | 值/默认 | 作用 |
|---|---|---|
| html data-theme | dark / light；初始 dark | Shell.theme 设置 |
| html data-style | ore / smooth；初始 ore | Shell.style 设置 |
| html data-window | mini / max；初始 mini | Shell.window；可能覆盖 HTML 初值 |
| html data-icons | regular / thin / bold；初始 regular | Shell.icons 改图标目录 |
| html data-font | 字体 id；初始 mc | Shell.font；跨 kit 页沿用，不保证重启持久化 |
| data-go | kit 页面名，如 forge | 导航 oriel:ui/forge.html；不是任意 URL 路由器 |
| data-zoom | 布尔 | 配合 data-go 放大轮廓转场 |
| data-action | close / back / theme | 关闭 / 回 gallery / 切主题；window 为自动按钮所用 |
| data-style-pick / data-icons-pick / data-font-pick | 对应枚举/id | 点击更换，设置 on 标记 |
| data-count | 数字文本 | 进入时计数动画；持续更新用 Motion.count |
| data-show | #目标id | 页签/分段显示目标 |
| data-toast | 标题或 标题\|副标题 | 默认 3200ms，最多 4 条 |
| data-notice | 提示正文 | 底部条，一次一条 |
| data-kind | info / success / warn / error；默认 info | 通知状态 |
| data-notice-action | 动作文字 | 仅显示按钮；真实撤销用 Notice.bar 传回调 |
| .o-mcfield data-prefix | § 或 &；默认 § | 格式码前缀 |
| .o-mcfield data-json-out | #目标id | 输出聊天 JSON；自身 data-json 保存结果 |
| html data-motion | reduced | CSS 减少动效路径；粒子/自写循环也需停用或减量 |

全局开关放设置页或所属状态栏，同类控件全站统一。Shell 已有的自动窗口按钮按现有行为使用，不另造一套外观开关。data-theme-pick 不应仅因 Widgets 避让了这个名字就被当成 Shell 已实现的点击 API；主题切换用 data-action=theme 或 Shell.theme。

## 主题变量

| 变量 | 暗色默认 | 亮色默认 |
|---|---|---|
| --o-text / --o-text-dim / --o-text-faint | #ffffff / #c4c5c8 / #8c8d90 | #2b2b2b / #4a4a4a / #6f6f6f |
| --o-accent / --o-accent-strong / --o-accent-ink | #6cc349 / #86d562 / #0b2a05 | #2f7d1d / #3c8527 / #ffffff |
| --o-tile / --o-tile-hover / --o-tile-edge | #3a3b3c / #444546 / #1e1e1f | #d6d6d6 / #e2e2e2 / #555555 |
| --o-well / --o-bar | rgba(0,0,0,.28) / #242425 | rgba(0,0,0,.10) / #b4b4b4 |
| --o-line / --o-line-strong | rgba(255,255,255,.08) / rgba(0,0,0,.55) | rgba(0,0,0,.14) / rgba(0,0,0,.35) |
| --o-ico | #e6e7e9 | #3a3a3a |
| --tier | 缺省 #b1b2b5 | 由稀有度类覆盖，见设计规范 |
| --ico | 无，回退 --o-ico | 只给单个图标改乘色 |

布局变量见首表，动效变量见 motion。私有变量加页面前缀。带 border-image 的按钮/窗口不要整体 -mc-tint，否则纹理也被染色。

## 图标、字体、翻译与格式

`<img class="o-ico" src="oriel:textures/icons/sword.png">` 默认 16×16；x2 类为 32×32。thin/bold 目录是线宽变体。MCP oriel_pages 可查名称，不猜文件名。新生成位图默认用 Mivo，保留许可证；已有图标先复用。

| 字体 id | family | 推荐字号 px |
|---|---|---|
| mc / unifont | minecraft:default / minecraft:uniform | 8 |
| pixel / pixel_12 / cubic | oriel:pixel / oriel:pixel_12 / oriel:cubic | 8 / 12 / 11 |
| sans / kai | oriel:sans / oriel:kai | 8 |
| smiley / kuaile / brush | oriel:smiley / oriel:kuaile / oriel:brush | 9 / 9 / 10 |

单元素类：f-mc、f-unifont、f-alt、f-pixel、f-pixel-12、f-cubic、f-sans、f-kai、f-smiley、f-kuaile、f-brush。自定义字体放 MC assets/ns/font/name.json，CSS font-family:ns:name，不用 @font-face。Oriel 自带字体声明在 META-INF/licenses/fonts/；MC 自带字体不应笼统称为本库 OFL 资产。

国际化：`<mc-text key="mymod.ui.save"></mc-text>`；JS `oriel.t('mymod.ui.save')`；Java `doc.host().translate("mymod.ui.save")`。资源在 assets/mymod/lang/zh_cn.json、en_us.json。kit 页面支持 `data-i18n`、属性翻译及 `Lang` 格式/复数辅助，参数和回退规则见 [国际化](i18n.md)。语言切换后的重载、长英文和中文断行要验证。

mc-0…mc-f 是 16 色；mc-l/o/n/m/k 为粗体/斜体/下划线/删除线/乱码。McFormat.render(doc,el,text) 渲染 §/& 代码；json(text) 转聊天 JSON；strip(text) 清格式。16 色不自动适配亮色背景。

## 粒子与装饰

```html
<canvas class="fx" data-fx="embers" data-rate="12" data-area="bottom" data-seed="3"
        style="position:absolute;inset:0;pointer-events:none"></canvas>
```

| 参数 | 默认/允许值 |
|---|---|
| data-fx | embers / sparks / glint / stars / notes / bubbles / dust / hearts |
| data-rate | 每秒发射数，0 仅 burst；上述预设依次 16/0/7/5/3/6/4/0 |
| data-area | full / bottom / top / centre / ring；跟随预设默认；注意 centre 拼写 |
| data-seed | 默认 1；固定用于重复截图 |
| data-color | 逗号分隔 CSS 色，从出生到死亡渐变；省略用预设 |

保留 Shell.setup 返回值，`fx.emitter(id).burst(name,x,y,count)` 在画布 px 坐标发射指定数量；`rate(float)` 改每秒速率，`colors(String...)` 改颜色，`count()` 查当前粒子数。每个 emitter 上限 600 个。用 CSS 明确画布框；装饰不能吃点击。装饰宿主 deco-host，子层 `<i class="deco grid"></i>`；现有 grid/dots/stripes/scan/glow/vignette/frame/corners/shine/aurora/floor。deco.over 覆在内容上；默认 pointer-events:none。不要为填空白无限增加常驻动画。
