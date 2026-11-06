# 开发参考：命令、检查器与自动化

[入口](index.md) · [验收工具](validation.md) · [接入指南](integration.md) · [安全与设置](security.md)

命令、热重载，以及用代码驱动页面（用于自动巡检和测试）。网页预览、MCP 与验收流程见[验收工具](validation.md)和[制作流程](ui-workflow.md)。

## 命令与开发环境

| 命令 | 端 | 说明 |
|---|---|---|
| `/oriel open <url>` | 客户端 | 打开任意页面，如 `/oriel open mymod:oriel/shop.html`。 |
| `/oriel showcase [名称]` | 客户端 | 唯一的演示入口。不带名称打开首页画廊；名称可以是 Oriel 的预设页 `inventory`、`forge`、`music`、`settings`、`editor`（演示用的编辑界面）、`catalog`、`icons`、`templates`（模板中心）；`potions`（开关药水 HUD 叠加层）、`toast`（开关一个可交互的 HUD 叠加层：按 T 然后点它）；引擎基础小演示 `layout`（弹性盒与网格）、`animation`、`livedata`（实时数据：待办、计数器）、`forms`（原版风格表单）、`titlecard`（开关淡入淡出的标题卡）；或展示页 `title`、`hud`、`shop`、`mobdex`（游戏里的每种生物，附你的击杀统计）、`journal`、`console`、`models`（3D 的方块、物品和生物，以及头肩肖像）、`scene`。 |
| `/oriel showcase chest` | 服务端 | 在真实箱子菜单上的物品栏演示（需要作弊权限）。 |
| `/oriel showcase live` | 服务端 | 作为带实时数据的服务端会话的模板演示。 |
| `/oriel reload` | 客户端 | 重载所有打开的页面。资源重载（F3+T）也会。 |
| `/oriel canvastest` | 客户端 | 不经引擎绘制每一种 Minecraft 画布原语，用来检查渲染器。 |

- 页面加载失败或引擎抛异常时，界面显示错误和堆栈，而不是崩溃；`/oriel reload` 会再试一次。
- 开发环境里，Oriel 直接从你的源码读取页面，并在页面读过的文件被保存时重载已打开的页面。它从运行目录向上直到项目根目录（有 `settings.gradle` 的目录），查找 `src/main/resources/assets` 和 `common/src/main/resources/assets`。
- `config/oriel.properties`：`client.reducedMotion=true` 让页面匹配 `@media (prefers-reduced-motion: reduce)`。这个文件也包含所有其他设置（见[安全与设置](security.md#设置)）。

## 检查器

纯 CSS 文件保存保留 DOM、控制器、脚本和输入状态；其他文件保存仍重建页面并恢复控制器主动保存的状态。完整边界见[验收工具](validation.md#游戏与热重载)。

开发环境中 F12 打开右侧元素检查器；普通开发构建在其他环境可用 `client.inspector=true` 开启。`-Poriel_release` 正式运行包不包含检查器实现与页面，该开关不会启用它。检查时原页面在剩余空间继续运行。

- 树列出页面的元素。点一行选中它，点它的箭头展开或收起，悬停一行会在页面里高亮该元素（外边距橙色、边框黄色、内边距绿色、内容蓝色）。
- 点"拾取"，再点页面里的元素，会选中指针下的东西并把树展开到它。Escape 停止拾取。
- 选中元素后可以看到：它的祖先（点一个就选中它）、它的盒子、它的属性、内联样式和文字，以及与初始值不同的计算值（带过滤）。编辑某个字段后按 Enter 或点到别处，就会改动页面。新属性需要名称和值。"删除元素"会移除它。
- 检查器的编辑只影响当前页，不写入文件。HTML/脚本变更及手动重载会重建页面；纯 CSS 文件保存只刷新样式，保留当前的 DOM 编辑。
- 拖动面板的左边缘可以加宽或收窄（宽度保持到游戏关闭），拖动树与详情之间的条可以分配它们的高度。
- Escape 或 F12 关闭检查器。检查器的字段有焦点时，按键会输入到字段里。

这个面板本身就是一个 Oriel 页面（`oriel:oriel/dev/inspector.html`），它通过只有它才有的 `inspector` 全局来读取和编辑被检查的页面。预览器的 F12 会为指针下的元素画出同样的盒子高亮。

## 开发自动化

`dev.oriel.mod.client.OrielAutomation` 用代码驱动页面，用于开发自动巡检和游戏内检查。它用 CSS 选择器找元素，并通过 Minecraft 自己的鼠标和键盘处理器（真实输入走的那条路）发送输入，所以容器界面里原版的槽位高亮、点击和 Shift 点击也能工作。只能在渲染线程使用；位置单位是 GUI 像素。

```java
OrielAutomation.screen().ifPresent(page -> {      // 当前打开的 OrielScreen 或 OrielContainerScreen
    page.click("#amount");                         // 指针移到元素上，按下，释放
    page.type("64");                               // 每个字符：按键按下、字符、按键抬起
    page.key("Enter");
    page.leave();                                  // 指针离开页面：不留下悬停或提示
});

// 然后每个 tick 一次，直到页面稳定下来：
OrielAutomation.screen().filter(OrielAutomation::settled).ifPresent(page -> {
    boolean ok = page.eval("state.amount").map(v -> v.getAsInt() == 64).orElse(false);
    // ……截图，进入下一步
});
```

| 方法 | 说明 |
|---|---|
| `static Optional<OrielAutomation> screen()` | 当前打开的 Oriel 界面（如果它的页面在显示）。 |
| `static Optional<OrielAutomation> hud(Identifier id)` | 一个正在显示的 HUD 叠加层。它的页面总是可读；只有可交互时才接收输入（见下）。 |
| `boolean exists(String selector)` | 是否有元素匹配。 |
| `Optional<float[]> rect(String selector)` | 第一个匹配元素的边框盒，按绘制后的样子（计入滚动和变换，同 `getBoundingClientRect()`）：`{x, y, width, height}`。 |
| `Optional<String> text(String selector)` | 它的 `textContent`。 |
| `boolean hover(String selector)` | 把指针移到第一个匹配元素上（见下）。真实指针无法到达时返回 false，此时指针保持原位。 |
| `Optional<float[]> pointerTarget(String selector)` | `hover` 会把指针放到的 `{x, y}`，但不移动；`hover` 会返回 false 时为空。用于自己设计指针路径，比如朝元素滑过去。 |
| `boolean click(String selector)`、`click(String selector, int button)` | 先悬停，再按下并释放 `button`：0 左键，1 中键，2 右键。`hover` 为 false 时返回 false，什么也不发。指针留在元素上。 |
| `boolean wheel(String selector, double notches)` | 先悬停，再转滚轮；正值向下滚。`hover` 为 false 时返回 false。指针留在元素上。 |
| `boolean drag(String selector, float dx, float dy)` | 先悬停，按下左键，分几步移动 `(dx, dy)` GUI 像素再释放，全部一次完成（见下）。`hover` 为 false 时返回 false，什么也不发。指针留在拖动结束的位置。 |
| `void leave()` | 把指针移到窗口外，在那里它不悬停任何东西：下一帧不会留下 `:hover` 样式、`title` 提示、物品提示或槽位高亮，对任何 Oriel 页面和原版界面都一样。没有界面打开时什么也不做。 |
| `boolean scrollIntoView(String selector)` | 滚动第一个匹配元素所在的滚动容器使它显示，立即完成，滚动量最小。之后有任何一部分显示则返回 true。 |
| `boolean key(String domKey)` | 按下并释放在当前键盘布局上产生该 DOM 键名（`"Enter"`、`"ArrowDown"`、`"a"`、`"A"`）的按键。没有按键产生它、或页面不接收输入时返回 false。 |
| `void type(String text)` | 输入文字。没有按键能产生的字符只作为文字发送。 |
| `Optional<JsonElement> eval(String js)` | 在页面的脚本沙箱里运行 `js`；其完成值以 JSON 返回。undefined、函数和错误（它们像任何脚本错误一样被报告）返回空。 |
| `boolean tooltipShown()` | 页面最后一帧是否显示了提示框：超过延迟的 `title`，或 `<item tooltip>` 的提示。容器界面的槽位显示自己的物品提示时为 false。 |
| `Optional<String> narration()` | 现在朗读器会为页面的界面说的全部内容，就像原版在界面打开时拼出来的那样：标题，然后是获得焦点或悬停的元素（`"Emperor Cualius. Reply 1: About the letter button. Left click to activate"`）。朗读器关闭时也能用。HUD 叠加层为空。 |
| `static List<String> recordNarration()` | 开始记录 Oriel 交给朗读器的、界面自己的朗读之外的内容：实时区域的播报，以及 HUD 叠加层在指针下读的内容。记录期间，页面在朗读器关闭时也会计算这些。返回它记录到的那个列表，在 `stopRecordingNarration()` 之前始终是同一个。 |
| `static void stopRecordingNarration()` | 停止记录。 |
| `boolean settled()` | 页面是否已停止自己变化（见下）。 |

`hover`、`click` 和 `wheel` 瞄准元素可见部分的中心：它的边框盒按窗口和它所在的每一个滚动容器（或其他 `overflow` 裁剪）裁剪后的结果。若完全不可见，先让它的滚动容器像 `scrollIntoView` 那样把它滚进视野。然后对那个点做命中测试。如果画在那里最上层的东西不是该元素或其内部的东西（上面有定位的兄弟元素、界面上方有 HUD 叠加层、或元素是 `pointer-events: none`），它们返回 false 且什么也不发，因为真实点击会落在别处。

`click` 或 `wheel` 之后指针留在原处，就像真鼠标那样：元素保持 `:hover`，延迟之后显示它的 `title` 提示。截图前调用 `leave()`。页面从第一帧起就跟随指针，所以你接下来打开的页面，也会在指针留下的位置被悬停。

`drag` 在一次调用里，通过鼠标处理器发出一次按下、几次按住按键的移动和一次释放。界面收到真实拖动会带来的 `mouseMoved` 和 `mouseDragged` 调用，所以 `rotatable` 元素按距离旋转和倾斜，范围滑块或滚动条滑块也会跟随。拖动期间不过去时间，所以转台松手时没有速度可以继续旋转。

HUD 叠加层在它对当前打开界面可交互时接收输入：该界面是它的 `Input` 或谓词接受的，并且已有一帧把叠加层画在那个界面之上。`hover`、`click`、`wheel` 此时走和界面相同的路径，叠加层在下一帧看到指针移动，和真鼠标一样。其余时候它们返回 false：叠加层在没有指针的 HUD 层里，真实点击会落到界面或游戏上。按键交给拥有键盘焦点的东西，而叠加层永远没有。通过 `hud(id)`，`key` 和 `type` 到达叠加层所可交互的那个界面（`WHEN_CHAT_OPEN` 时是聊天框），不可交互时什么也不做。

```java
mc.gui.setScreen(new ChatScreen("", false));
// 从下一个 tick 起，直到它返回 true：
OrielAutomation.hud(id("ask")).map(toast -> toast.click(".allow")).orElse(false);
```

`settled()` 在页面正在做的事短时间内不会再改变它时为 true。每个 tick 轮询一次，不要等固定的 tick 数。它在下面这些情况为 false：

- 有样式重算、重排或重绘待处理，如输入、`eval` 或打开页面之后，直到下一帧绘制；
- 平滑滚动正在移动，或有 `scroll` 事件在等待；
- 有模板更新或 `oriel.nextTick` 回调在等待；
- 有过渡或有限动画（CSS 或 `element.animate()`）在运行，或它们的事件在等待；
- 有拖动被按住，或 `rotatable` 元素仍在旋转；
- 有 `title` 提示在等它的延迟；
- 被替换的内容仍在后台加载什么（`ReplacedContent.loading()`）。

无限动画不计，否则标题界面永远不会稳定。定时器（`setTimeout`、`setInterval`）和 `requestAnimationFrame` 回调也不计：页面用它们做时钟、轮询和永不结束的循环。定时器改变页面时，用 `text` 或 `eval` 等那个变化。文本框闪烁的光标不计。定时器不断启动过渡或动画的页面可能永远不会稳定（展示 HUD 的战斗循环就是一个），所以要给等待设上限。HUD 叠加层在它的页面加载完之前（首次绘制时）不算稳定；显示着错误面板的页面算稳定。

Oriel 自己的自动巡检（`./gradlew :neoforge:26.3:runClient -Pautopilot`，或 `:fabric:26.3`、`:neoforge:26.1`、`:fabric:26.1`；`-Pautopilot=scene` 只运行场景展示的那些步骤）用到了以上全部：它等每个页面稳定，悬停展示标题界面，拖动转台，在静止光标下打开页面并检查它在最初几帧内被悬停，悬停物品和 title 取提示，检查 `-mc-tooltip-delay: 0ms` 的地图标记在第一帧就显示它的 title、检查朗读器在对话上拿到什么（标题、悬停的回复项、日志的每一行新内容），通过 `onKey` 用 mod 的键关闭页面，填写模板演示，点击 Mobdex 列表里滚出视野的一行，并通过聊天回答演示 toast（叠加层朗读它悬停的按钮）。结束时记录有多少检查失败。

## 无界面地运行游戏

自动巡检和 `OrielAutomation` 需要真实的客户端。下面这些配置已知可以让 26.3 的 `runClient` 工作：

- macOS：在 `runs/client/options.txt` 里加 `enableVsync:false`，否则客户端可能在第一帧挂起；NeoForge 上还要在 `runs/client/config/fml.toml` 里设 `earlyWindowControl = false`。
- 没有 GPU 的 Linux（容器或 CI）：
  - 运行一个 X 服务器：`Xvfb :1 -screen 0 854x480x24 +extension GLX +render` 并设 `DISPLAY=:1`。
  - 通过 Mesa 的软件驱动使用 Vulkan：安装 `mesa-vulkan-drivers` 并设 `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json`。这样 Minecraft 会选 Vulkan。OpenGL 后端在那里会失败（"Couldn't find matching GLX visual"），因为 26.3 要求 sRGB 帧缓冲，而 Xvfb 下的 llvmpipe 不提供。
  - NeoForge 上，在 `runs/client/config/fml.toml` 里设 `earlyWindowControl = false`，否则它的早期窗口在 GLX 上失败，客户端会停住。
  - 截图（`Screenshot.grab`、自动巡检用的）可以工作，`ffmpeg -f x11grab` 可以录制窗口。
