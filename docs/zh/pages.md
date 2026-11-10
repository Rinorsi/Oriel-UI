# 页面接口：打开、数据、消息与可编辑页面

[入口](index.md) · [接入指南](integration.md) · [Java API 速查](java-api.md) · [安全与设置](security.md)

从客户端或服务器打开页面、与页面交换数据和消息，以及玩家可编辑的页面。接入的总览和最短路径见[接入指南](integration.md)，方法签名速查见 [Java API](java-api.md)，本页是它们背后的完整行为说明。

## 在客户端打开页面

### 独立页面比例（0.13）

`screen.driver().uiScale(0)` 默认跟随 Minecraft GUI 比例；`uiScale(1)` 至 `uiScale(4)`
仅改变该 driver 的页面比例。其它值明确抛错，不改全局选项。`uiScale()` 读取设置值。
可在 `onPageLoad` 中首次设置，也可在页面打开时即时调整；调整使用同一个 Document，
保留脚本、草稿、输入焦点和撤销状态，不触发导航或重载。宿主自行持久化这个设置。

页面宽高、DPR、绘制和裁剪使用一致比例。DOM 坐标仍是页面逻辑像素；driver 的鼠标、
`elementAt/contentAt` 接受原版 GUI 坐标，`toPage(double)` / `toGui(double)` 供自定义宿主换算。
原版物品图标跟随画布比例，title/物品 tooltip 使用页面比例布局绘制，IME 候选位置转换回 GUI 坐标。
`OrielAutomation.rect/pointerTarget` 仍返回原版 GUI 坐标。

原版菜单槽由原版屏幕处理固定尺寸，当前有 `Owner.slots()` 的 driver 不允许非自动比例，
调用会抛出明确异常，避免槽位显示与点击不一致；普通 OrielScreen（包括下游编辑器）不受此限制。
GPU、真实输入法及游戏内坐标仍需按目标加载器/版本实测，单元测试不替代实机验收。

```java
OrielScreen screen = OrielScreens.open("mymod:oriel/settings.html");
screen.driver().onMessage("save", value -> MyConfig.save(value.getAsJsonObject()));

// 带初始数据（页面里的 oriel.data）：
OrielScreens.open("mymod:oriel/journal.html", journalJson);

// 内联 HTML，例如运行时生成的：
OrielScreens.openInline("<h1>Hello</h1><p>{{ name }}</p>", data);
```

- `oriel.close()`（或 Escape，除非页面自己处理了它）关闭界面。Shift+Escape 永远会关闭它，快速连按三次 Escape 也会（见[安全](security.md)）。
- 页面里的 `oriel.send(channel, value)` 会用该值（`JsonElement`）调用每个 `onMessage(channel, ...)` 处理器。页面可以突发发送 20 条消息，之后每秒 20 条（`client.messageBurst`、`client.messagesPerSecond`），所以第一秒最多到达 40 条。
- `screen.driver().push(json)` 替换 `oriel.data`；模板绑定更新，`oriel.on('data', fn)` 监听器运行。
- `screen.driver().patch(key, json)` 只设置一个顶层字段 `oriel.data[key]`，其余不动，所以大页面的小更新很便宜：只序列化和解析那个值，页面里的其他对象仍是原来的对象。值为 null（或 `JsonNull`）则移除该字段。绑定更新，`oriel.on('patch', fn)` 监听器收到 `{key, value}`，然后 `'data'` 监听器收到 `(oriel.data, key)`。如果 `oriel.data` 不是对象，它会变成只有这个字段的对象。
- `screen.driver().onClose(() -> ...)` 在页面彻底关闭时运行一次：界面被关闭，或被另一个界面替换。页面导航或重载时不算，其上打开链接确认框时也不算。页面自己的 `pagehide` 和 `unload` 监听器在它之前运行，此时脚本仍然存活，所以它们最后发的一条 `oriel.send` 会先到达你的 `onMessage` 处理器。
- `screen.pauses(true)` 在界面打开时暂停单人世界，像原版的书。缺省是关闭，因为和服务器交互的页面（商店、对话）需要世界继续运行：`OrielScreens.open("mymod:oriel/book.html", data).pauses(true)`。
- `<a href="other.html">` 在同一个界面里加载另一个页面；`https://` 链接先询问确认。
- `screen.driver().onKey(handler)` 把页面没有占用的按键交给你的 mod，先于界面自己的按键（Escape、容器界面的物品栏键）。用它做自己的按键映射；页面不知道它们。页面使用的按键永远不会到达处理器：被 `keydown` 监听器用 `preventDefault()` 取消的、被获得焦点的控件处理了的（按钮上的 Enter）、以及文本框有焦点时除 Escape 外的所有键，所以在 `<input>` 里输入 "j" 仍然是文字。返回 true 表示消费该键。处理器按添加顺序运行，直到有一个返回 true，并在导航和重载之间保持。
- 在静止的光标下打开的页面，从第一帧起就在那里显示 `:hover`，和原版界面一样，延迟之后显示它的 `title` 提示，鼠标不用动。页面的指针每帧都跟随 Minecraft 的鼠标处理器，所以向界面发送 `mouseMoved` 的代码必须同时移动鼠标处理器，否则下一帧页面的指针会被移回去。`OrielAutomation` 两件事都做了。
- `screen.driver().merge(jsonObject)` 只设置它带的顶层字段，保留 `oriel.data` 的其余部分。只序列化那些字段，但页面和 `push` 一样重新拿到完整数据，并整个解析一遍。经常变化的字段请改用 `patch`。
- 你 push、merge 或 patch 的内容会保留到页面下一次加载：重载（`/oriel reload`、资源重载、开发时保存文件）显示的是当前的数据。`driver.receive(channel, json)` 只投递一条消息，所以页面重载时会丢掉靠它构建的东西。Oriel 自己保留一份 JSON 文本副本，所以传入之后再修改 `JsonObject` 不会有任何效果。
- `OrielScreens.onPageLoad(url, driver -> ...)` 在该页面每次加载时运行，无论怎样到达（打开、链接、重载），在它的脚本运行之前：用 `driver.push(json)` 给它实时数据，或用 `driver.merge(fields)` 保留打开者传入的数据，并用 `onMessage` 处理它的消息。`OrielScreens.pages(url)` 返回当前显示该页面的 driver，用于推送更新。服务器打开或链接到该页面时钩子也会运行：此时 `driver.serverSession()` 为 true，不应把服务器不该看到的东西交给页面。

```java
OrielScreens.open("mymod:oriel/notes.html", notesJson).driver()
        .onMessage("save", value -> Notes.save(value.getAsJsonObject()))   // 页面在它的 unload 监听器里保存
        .onClose(Notes::flush);

// 书在 mod 自己的键上关闭，像 E 关闭物品栏、M 打开地图：
OrielScreen book = OrielScreens.open("chronicle:oriel/book.html");
book.driver().onKey(e -> {
    if (ChronicleKeys.OPEN.matches(e)) {   // 一个 KeyMapping
        book.onClose();
        return true;
    }
    if (ChronicleKeys.MAP.matches(e)) {
        OrielScreens.open("chronicle:oriel/map.html");
        return true;
    }
    return false;
});
```

## 玩家可编辑的页面

mod 可以用一个文件夹来提供一个命名空间，玩家改文件就能改页面，不用做资源包。Hudsmith 就这样做它的 HUD，HUD 放在 `config/hudsmith/` 里：

```java
import dev.oriel.mod.client.OrielPages;   // 公开 API；不要用 dev.oriel.mod.internal 下的同名内部类

// 客户端初始化。用自己专属的命名空间，不要用你的 mod id，这样内置页面仍来自你的 jar。
OrielPages.folder("hudsmith_hud", configDir.resolve("hudsmith"));
OrielHud.register(HUD, "hudsmith_hud:hud.html");
```

- `hudsmith_hud:hud.html` 就是该文件夹里的 `hud.html`。它的相对样式表、脚本和图片从它旁边解析，和任何页面一样。图片必须是 PNG。
- 保存页面读取过的任何文件（页面、样式表、图片，或它请求过但当时还不存在的文件）都会重载页面，在所有环境里都如此，不只是开发环境。删除后重建文件、或编辑器通过临时文件保存，都算保存。
- 该文件夹拥有它的命名空间。它的 id 永远不会回退到资源包，提供同一命名空间的资源包会被它忽略，并在日志里给出警告。`minecraft:` 和其他命名空间仍来自资源，所以 `<item>`、精灵和纹理照常工作。
- 页面只读文件夹内的文件：通过 `..` 或指向别处的符号链接离开文件夹的路径，被当作不存在。文件夹本身可以在链接之后，因为启动器经常链接 `.minecraft` 或 `config`。
- 服务器打开的页面读不到文件夹里的文件，服务器也不能打开文件夹页面。
- 如果命名空间已被注册，或是 `minecraft`、`oriel` 或某个已加载 mod 的 id，`folder` 会抛异常。写坏的页面显示常规错误：界面上是一个面板，HUD 叠加层上是角落里的一行。

## 在服务器上打开页面

```java
OrielSession session = OrielServer.open(player, "mymod:oriel/shop.html", stockJson)
        .onMessage("buy", (p, value) -> Shop.buy(p, value.getAsString()))
        .onClose(() -> Shop.forget(player));

session.push(updatedStockJson);   // 页面的 oriel.data 改变；绑定重新渲染
session.patch("gold", new JsonPrimitive(12));   // 其中一个字段；只发送这个值
session.close();                  // 关闭玩家的界面
```

- 页面必须存在于客户端（你的 mod 装在那里，或服务器资源包带了它）。纯服务端 mod 可以自己发送页面：`OrielServer.openInline(player, html, data)`，用内联的 `<style>` 和 `<script>`。
- 页面发来的消息（`oriel.send(channel, value)`）带着发送玩家，在服务器线程上到达。它们有限速（突发 40 条，之后每秒 20 条）；格式错误或嵌套过深的 JSON、来自其他玩家的消息会被丢弃。把每条消息都当作恶意输入：见[安全](security.md)。
- `onClose` 在玩家关闭界面、打开另一个会话、离开，或你调用 `close()` 时运行一次。每个玩家最多 8 个打开的会话；再开一个会结束最旧的。
- 限制：内联页面 200,000 个字符，数据 100,000 个字符（按 JSON 计），消息 8,192 个字符。超过时 `open`、`push` 和 `patch`（针对值）抛 `IllegalArgumentException`，`patch` 的键超过 256 个字符也一样。服务器可以在 `config/oriel.properties` 里调低这些值。
- `session.patch(key, value)` 的作用与客户端的 `driver.patch` 一样。客户端像检查数据那样检查这个值（`client.maxDataDepth` 把它所在的对象计入深度），并丢弃会让页面数据超过 `client.maxDataChars` 的补丁。Oriel 0.7 或更早的客户端不能接收补丁：补丁在那里被丢弃，服务器日志里有警告。
- 服务器发来的页面在沙箱里运行：没有 Java 访问、没有网络、没有文件，并有 CPU 和内存预算。它们能做什么、不能做什么见[安全](security.md)。
- 服务器页面是否显示完全由玩家决定（`client.serverPages`），而且玩家在聊天或菜单里时页面会等待。不要假设 `open` 一返回页面就已在屏幕上。

官方编号模板与旧版归档、元数据及保存副本规则见 [官方模板目录](templates.md)。
