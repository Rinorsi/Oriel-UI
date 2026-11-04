# 做一个新页面（给人和 AI 的流程）

## 0. 先看这三处

- `docs/zh/design.md`：尺寸、层级、颜色、光效、粒子的规则。
- `kit/src/main/resources/assets/oriel/ui/ore.css`：所有可用的类（`.o-window`、`.o-tile`、`.o-chip`、`.fx-*`……）。
- 现成页面：`gallery.html`（启动器）、`inventory.html`（三栏 + 拖拽）、`editor.html`（工具类界面）。

## 1. 生成骨架

```bash
node tools/new-page.mjs bestiary "怪物图鉴"
```

生成：

| 文件 | 内容 |
|---|---|
| `kit/src/main/resources/assets/oriel/ui/bestiary.html` | 页面：窗体、标题栏、一个示例块和粒子 |
| `kit/src/main/java/dev/oriel/kit/preset/BestiaryPreset.java` | 控制器：只做交互，数据和状态在这里 |
| `kit/src/test/java/dev/oriel/kit/preset/BestiaryPresetTest.java` | 测试：打开页面、点击、断言 |

并在 `OrielKit` 里登记 `OrielKit.BESTIARY`。

## 2. 写页面（HTML + CSS）

- 结构固定：`.o-app > .o-window > (.o-titlebar, .o-body > (.o-rail?, .o-main, .o-side?))`。
- 不写脚本。链接与常用动作用属性，不用写代码：
  - `data-go="forge"`：打开 kit 页面；
  - `data-action="back" | "close" | "theme"`：回画廊 / 关闭 / 换主题；
  - `<canvas class="fx" data-fx="stars">`：粒子。
- Minecraft 内容直接用元素：`<item id="minecraft:diamond">`、`<model block="minecraft:anvil">`（3D 方块）、`<model item="minecraft:iron_sword">`、`<entity player follow-mouse>`（玩家 3D 模型）、`<entity type="minecraft:allay">`。
- 3D 模型用 CSS 转：`-mc-yaw`、`-mc-pitch`，可以写进 `@keyframes`。

### 文字、字体与 MC 格式

- 全局字体：`Shell.font(doc, "kai")` 或任意元素加 `data-font-pick="kai"`（点了就换，所有页面跟着换）。可选：`mc`（原版）、`unifont`、`pixel`（缝合像素 8）、`pixel_12`、`cubic`（俐方体 11）、`sans`（黑体）、`kai`（文楷）、`smiley`（得意黑）、`kuaile`（快乐体）。全是 OFL 免费字体，许可证在 `META-INF/licenses/fonts/`。
- 单个元素：`f-pixel`、`f-pixel-12`、`f-cubic`、`f-sans`、`f-kai`、`f-smiley`、`f-kuaile`、`f-unifont`、`f-alt`（附魔台文字）。像素 12 / 俐方体 11 建议配 `font-size: 12px` 或 `11px`，像素格才整齐。
- 自定义字体：资源包里放 `assets/<命名空间>/font/<名字>.json`（Minecraft 的 ttf/bitmap 字体格式），CSS 写 `font-family: <命名空间>:<名字>`。缺的字会退回游戏字体。
- 文字颜色就是 CSS `color`。MC 的 16 色和样式有现成类：`mc-0`…`mc-f`、`mc-l` 粗体、`mc-o` 斜体、`mc-n` 下划线、`mc-m` 删除线、`mc-k` 乱码（`-mc-obfuscated: on`，游戏里每帧随机换字）。
- `§` / `&` 代码文本：`McFormat.render(doc, el, "§6§l标题§r正文")` 渲染成带样式的 span；`McFormat.json(...)` 得到聊天组件 JSON（给 `<mc-text json>`、`/tellraw`、物品名）；`McFormat.strip` 去掉代码。`§#rrggbb` 是 16 色以外的颜色。
- 格式输入框：`<div class="o-mcfield"><input class="ore-field"></div>`，自动加颜色/样式条和预览；选中文字点颜色就包上代码。`data-json-out="#id"` 把 JSON 写到别的元素，`data-prefix="&"` 用 `&` 代码。

### 通知与提示

- 底部提示条：按钮加 `data-notice="已保存" data-kind="success" data-notice-action="撤销"`；代码里 `Notice.bar(doc, "warn", 文字, 按钮文字, 回调)`。一次一条。
- 吐司：`data-toast="标题|副标题"`（`data-kind` 选 info/success/warn/error），或 `Notice.toast(doc, kind, 标题, 副标题, 毫秒)`，最多叠 4 条，自己消失。
- 页面里的提示条：`<div class="o-notice inline warn">`。悬停提示：`title="…"`，物品式提示 `title-json="聊天组件"`。

## 3. 写控制器（Java）

```java
public void attach(Document doc) {
    Particles fx = Shell.setup(doc);              // 主题、data-go/data-action、粒子
    Element list = Dom.byId(doc, "list");
    for (Mob m : MOBS) list.appendChild(Dom.html(doc, "<div class=\"o-row\">" + m.name() + "</div>"));
    Shell.activate(Dom.byId(doc, "next"), this::next);   // 点击 + Enter/空格
}
```

工具：`Dom`（查找、建元素、事件）、`Dom.Style`（行内样式）、`Flip`（重排动画）、`Slider`、`Particles`、`Shell`。

不用写代码的部分（`Shell.setup` 已接好）：

- 页签 `.ore-tabs`、分段 `.o-seg`、开关 `.ore-toggle`、可点芯片 `.o-chip.hot`、滑块 `.ore-slider`、下拉框 `.ore-select`（`.value` + `.menu > .option`）写在 HTML 里就能点。要自己接的控件给它一个 `id`，默认行为就不会碰它。
- 标题栏 `.o-apptabs` 里写 `data-go` 的项是页签式切页，带方向动画；每个标题栏都会自动加"窗口化 / 最大化"按钮。

布局、装饰和加载也都是类名（详见 `ore.css` 里各段开头的注释，组件目录里有可以点的演示）：

- 布局：`o-cols`（双栏）、`o-cols three`、`o-split`（侧栏 + 主区，`--side`）、`o-center`、`o-auto`（按宽度自动换列，`--min`）、`o-stack`、`o-spread`、`o-ratio`（`--ratio`）。
- 抽屉与浮层：`o-drawer`（挤压，收起时周围重新排）、`o-sheet`（浮动，盖在上面），把手都是 `.o-drawer-handle`；`data-pop="id"` + `.o-pop` 浮层；`data-context="id"` + `.o-context` 右键菜单；`.o-menubar` 菜单栏。
- 装饰：盒子加 `deco-host`，里面放 `<i class="deco grid">`、`dots`、`stripes`、`scan`、`glow`、`vignette`、`frame`、`corners`、`shine`、`aurora`、`floor`，可以叠几层。页面有空处时先用它们填，而不是留一大片灰。
- 加载：`o-lazy`（骨架占位，进入视野后逐个显示内容，重的模型和列表在此之前不渲染）；`o-preload`（加载页，`data-min` 毫秒后或 `Lazy.ready(doc)` 时揭开）。

要列出游戏里的物品（选择器、搜索）：用 `GameRegistry.entries()`。游戏里它是完整的物品注册表，包括所有模组的物品，名字跟随游戏当前语言；预览器里是一份小的内置清单。`GameRegistry.name(id)` 返回显示名。

## 4. 验证（每次改完都跑）

```bash
./gradlew :kit:test                                   # 控制器测试
./gradlew :preview:checkUi                            # 所有页面：错误、缺图、CSS 警告
./gradlew :preview:checkUi -Pui=kit/src/main/resources/assets/oriel/ui/bestiary.html
```

截图（无窗口）：

```bash
java -cp "preview/build/install/preview/lib/*" dev.oriel.preview.Preview \
  kit/src/main/resources/assets/oriel/ui/bestiary.html --size 640x360 --frames 60 --snapshot out.png \
  --boxes ".o-tile"          # 打印匹配元素的位置和大小，排查布局
```

交互脚本（`--actions a.txt`，每行一个：`wait 30`、`click #id`、`key Enter`、`type 文字`、`shot 名字`）。

给 AI 用的 MCP（stdio，无依赖）：`node tools/mcp/oriel-preview.mjs`（在仓库根目录运行），提供 `oriel_pages`、`oriel_check`、`oriel_snapshot`（可带交互动作与 `boxes`，直接返回 PNG）。客户端配置示例：`{"command": "node", "args": ["tools/mcp/oriel-preview.mjs"], "cwd": "<仓库根目录>"}`。

注意：预览器画不出 3D 模型和实体（显示为占位色块），字体也和游戏略有差别；最终效果以游戏为准。

## 5. 进游戏看

```bash
./gradlew :fabric:26.1:runClient          # 开发客户端
# 游戏里：/oriel open oriel:ui/bestiary.html   或 /oriel demo
```

开发客户端里改 HTML/CSS（以及页面读的 JSON）保存即热重载。改了 Java、图标/贴图、字体、语言文件时，窗口底部会出现提示条，写明哪些改动还没生效，点「热重载」就编译、热替换类、重新加载资源并重开页面，不用重启；只有类增删了字段或方法、或改了 `fabric.mod.json` / `neoforge.mods.toml` / mixin 配置时，提示条会说明需要重启游戏。自动巡检截图：`./gradlew :fabric:26.1:runClient -Pautopilot=oriel`（1280×720，截图在运行目录 `screenshots/`）。

## 6. 常见坑

| 现象 | 原因 | 办法 |
|---|---|---|
| 按钮变黑 / 纹理变色 | 给带 border-image 的元素设了 `-mc-tint` | 只给里面的 `.o-ico` 设 |
| 某个类的样式"串"了 | 类名撞车（如 `.hero` 既是按钮变体又是横幅） | 页面私有类起具体名字；用 `--boxes` 查 |
| 动画元素的截图是空的 | 截图时动画还在第 0 帧（透明） | `--frames 60` |
| 小窗口里点不到东西 | 427×240 时布局溢出被裁 | 加 `@media (max-width: 560px)` 收起侧栏 |
| 嵌套 `/* */` 编译失败 | Stonecutter 注释块里不能再套块注释 | 用 `//` |
