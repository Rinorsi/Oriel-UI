# HUD 叠加层

[入口](index.md) · [接入指南](integration.md) · [Java API 速查](java-api.md) · [页面接口](pages.md)

画在 HUD 之上的页面，以及在选定界面之上接收输入的叠加层。

```java
// 客户端初始化：
OrielHud.register(id("tracker"), "mymod:oriel/tracker.html");

OrielHud.show(id("tracker"));
OrielHud.push(id("tracker"), questJson);   // oriel.data
OrielHud.patch(id("tracker"), "progress", new JsonPrimitive(3));   // 其中一个字段
OrielHud.hide(id("tracker"));
```

叠加层是按 GUI 缩放后的窗口大小排版的页面，画在标题层之上，随 HUD 一起隐藏（F1）。页面可以用 `oriel.close()` 隐藏自己，例如动画结束时。叠加层隐藏期间 push 或 patch 的数据会保留到下一次 `show`。`patch` 的作用与 `DocumentDriver.patch` 相同（见[页面接口](pages.md)）。`show` 返回该页面的 driver，用于 `onMessage` 和 `onClose` 处理器；它们持续到叠加层被隐藏为止（再次显示会产生新的 driver）。

一串提示或公告，可以做成一个做完就自己关闭的页面，加一个显示下一条的 `onClose` 处理器。在 `onClose` 里调用 `show` 没问题：叠加层此时已经放开了旧页面。

```java
private static final Deque<JsonObject> QUEUE = new ArrayDeque<>();

static void toast(JsonObject toast) {
    QUEUE.add(toast);
    if (!OrielHud.isShown(TOAST)) next();
}

private static void next() {
    JsonObject toast = QUEUE.poll();
    if (toast == null) return;
    OrielHud.push(TOAST, toast);
    OrielHud.show(TOAST).onClose(Toasts::next); // 页面在动画结束时调用 oriel.close()
}
```

缺省情况下叠加层不接收输入。指定玩家可以在哪些界面上用鼠标操作它，它就会画在那些界面之上，并先于它们获得指针输入：

```java
OrielHud.register(id("ask"), "mymod:oriel/ask.html", OrielHud.Input.WHEN_CHAT_OPEN);

OrielHud.show(id("ask"))
        .onMessage("answer", value -> Approvals.answer(value.getAsString()));
```

```html
<div class="toast">
  Rivet wants to take 12 cobblestone.
  <button onclick="oriel.send('answer', 'once'); oriel.close()">Allow</button>
  <button onclick="oriel.send('answer', 'deny'); oriel.close()">Deny</button>
</div>
```

| 注册方式 | 在哪些界面上可交互 |
|---|---|
| `register(id, url)` 或 `Input.NONE` | 都不可 |
| `Input.WHEN_CHAT_OPEN` | 聊天（`ChatScreen`，包括在床上的聊天） |
| `Input.WHEN_CURSOR_FREE` | 所有界面，包括全屏的（物品栏、菜单、Oriel 界面） |
| `register(id, url, Predicate<Screen> interactiveOver)` | 谓词接受的界面，例如 `screen -> screen instanceof ChatScreen \|\| screen instanceof InventoryScreen` |

- 在它可交互的界面之上，叠加层画在该界面之上，指针输入先交给叠加层：悬停（以及 `title` 提示）、点击和滚轮。
- 在指针下只有页面背景（`<html>`、`<body>`，不管它们的样式）或什么都没有的地方，输入会穿过去交给下面的界面。想让点击穿过的内容可以写 `pointer-events: none`。页面没有用到的滚轮（没有东西可滚动、没有监听器取消它）也会到达界面。
- 交给叠加层的按下事件，它之后的释放和拖动也留给叠加层。
- 没有打开任何界面时，或在其他任何界面之下，叠加层作为普通叠加层画在 HUD 层里：在界面之下，没有指针。它所在的界面关闭（或被另一个替换）时，悬停结束（触发 `mouseleave`）。
- 键盘输入留给界面：在聊天里输入仍然输入到聊天里。
- 谓词在每一帧、每个指针事件都会被调用，参数是当前打开的界面；要保持轻量。

试一试：`/oriel showcase toast`，然后按 T 点一个按钮。
