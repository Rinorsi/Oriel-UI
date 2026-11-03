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

注意：预览器画不出 3D 模型和实体（显示为占位色块），字体也和游戏略有差别；最终效果以游戏为准。

## 5. 进游戏看

```bash
./gradlew :fabric:26.1:runClient          # 开发客户端
# 游戏里：/oriel open oriel:ui/bestiary.html   或 /oriel demo
```

开发客户端里改 HTML/CSS 保存即热重载。自动巡检截图：`./gradlew :fabric:26.1:runClient -Pautopilot=oriel`（1280×720，截图在运行目录 `screenshots/`）。

## 6. 常见坑

| 现象 | 原因 | 办法 |
|---|---|---|
| 按钮变黑 / 纹理变色 | 给带 border-image 的元素设了 `-mc-tint` | 只给里面的 `.o-ico` 设 |
| 某个类的样式"串"了 | 类名撞车（如 `.hero` 既是按钮变体又是横幅） | 页面私有类起具体名字；用 `--boxes` 查 |
| 动画元素的截图是空的 | 截图时动画还在第 0 帧（透明） | `--frames 60` |
| 小窗口里点不到东西 | 427×240 时布局溢出被裁 | 加 `@media (max-width: 560px)` 收起侧栏 |
| 嵌套 `/* */` 编译失败 | Stonecutter 注释块里不能再套块注释 | 用 `//` |
