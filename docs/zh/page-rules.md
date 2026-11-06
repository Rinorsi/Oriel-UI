# 做页面前先读：十条硬规则（给 AI 和人）

这一页只放**踩过的坑和必须照做的规则**，每条都有来源（设计检查的报错、引擎限制或实测）。细节在各专题手册，这里不重复；做页面前读完它，不要靠猜。MCP 里用 `oriel_rules` 的 `topic=quickref` 读到的就是这一页。

## 1. 字体与字号：像素字只在"原生字号"和"2 倍"上清晰

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

## 2. 尺寸：先按 640×360 做，再保证 427×240 不坏

- 1 CSS px = 1 GUI px。设计基准 **640×360**，最小要撑住 **427×240**（1280×720 窗口、GUI 缩放 3）。小屏用 `@media (max-width: 560px)`、`(max-height: 300px)` 收起次要内容。
- 间距只用 2 / 4 / 6 / 8 / 12。图标 16×16，放大只用 2 倍。
- **任何文字都不能出框**：长文本用 `overflow: hidden; text-overflow: ellipsis; white-space: nowrap`，或让它换行；统计行、标签行要么换行要么在小屏隐藏。

- **已知引擎限制：限高滚动框会把父级撑到完整内容高度。** 子元素写 `max-height: 40px; overflow-y: auto`，父级自动高度却按子元素完整内容算（实测 8 行内容的父级是 108px，应为 52px），页面下面就多出一块空白。弹性布局和普通块布局都会这样。**绕开：滚动框用固定的 `height` 而不是 `max-height`**（实测父级正确变成 52px）；或者滚动框外面再包一层固定高度的容器。

## 3. 最小骨架：直接复制这个

放在 `assets/<你的模组id>/ui/index.html`（或 `config/oriel/my-pages/` 里）。这份已用 `preview --check --strict` 检查通过，0 警告。

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

## 4. 不写 Java，也要库的动效：`data-oriel-shell`

涟漪（`m-ripple`）、指针聚光（`m-spot`）、数字计数（`data-count`）、主题/图标切换、`data-action` 的关闭与返回、粒子画布，这些原来要 Java 控制器调用 `Shell.setup`。**纯 HTML 页面在 `<html>` 上加 `data-oriel-shell` 就等于调用了它**，不需要控制器。动效只用 `ore.css` 里已有的 `m-*` 类和 `--m-*` 曲线，不要自己写时长和缓动。有自己控制器的页面不用加。

## 5. 放哪里、怎么在游戏里看

- 放进游戏的 `config/oriel/my-pages/`（子文件夹照放），进游戏 `/oriel open oriel_mine:子文件夹/文件名.html`，也会出现在 模板中心 → 我的页面；**保存即热重载**。
- 页面里引用同文件夹的 css、图片、js，用**相对路径**；不要写 `oriel_mine:` 或你自己的命名空间前缀。
- 在卡片上显示的信息写在 `<head>`：`<title>` 是标题（≤40 字）；`<meta name="oriel-subtitle" content="…">` 是副标题（≤80 字，没有就用 `description`）；封面是 `<meta name="oriel-cover" content="相对路径.png">`，没写就找同目录的 `cover.png`，再找 `页面名.png`（16:9 的 PNG，必须在文件夹内）。

- **热更新有两条路，都不用自己造开关**：① 页面放进 `config/oriel/my-pages/`，所有环境保存即重载；② 模组自己的页面：Gradle `runClient` 且运行目录在工程里时自动热更新；运行目录在别处加 `-Doriel.sourceRoot=<工程目录>`；只把 jar 丢进 `mods/` 就在 `config/oriel/dev-sources.properties` 写一行 `命名空间=源码资源目录`（写法见 [热更新](hot-reload.md)）。**开始做页面前就先确认其中一条已配好**，不要等改了半天才发现不刷新。

## 6. 用什么检查

- 本机：`preview --check <页面> --strict`（见 [检查与验收工具](validation.md)）；用 MCP 就 `oriel_check`。**警告当成错误处理**，不要带着警告交付。
- 至少看 640×360 和 427×240 两个尺寸的截图，再进游戏看一次。

## 7. 不要做的事

- 不要猜资源名、类名、字段名：图标用 `oriel_pages` 查，类和属性查 [组件手册](components.md)、[CSS 参考](css-guide.md)。
- 不要在 Oriel 仓库里加应用功能（物品编辑器、商店逻辑等）：它们属于你自己的模组或页面，库只提供 UI 能力。
- 块里不再放块（见 [设计规范](design.md)）；颜色、圆角、阴影用 `ore.css` 的变量，不手写一套。
- 不要把测试数据或本机路径写进发布的页面。
