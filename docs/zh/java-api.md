# Java 接入 API 与生命周期

[入口](index.md) · [接入依赖](integration.md) · [服务端](server.md) · [页面接口补充](../api/pages.md)

kit 新增 `dev.oriel.kit.bind.ViewModel` / `Bindings.attach(Document, ViewModel)` / `Bindings.close()`，供 Java 控制器按路径驱动 o-* 指令。参数、线程、自动 unload 清理、更新失败语义与可运行示例见 [Java 声明式绑定](bindings.md)。

以下按页面作者需要列出接口。client 包只能在客户端加载并在渲染线程操作；server 接口在服务器线程调用。0.x 不保证任意升级二进制兼容，固定并验证依赖版本。`public` 不等于稳定 SDK；内部平台、渲染器、网络包、演示处理器不要作为下游依赖。

## OrielScreens 与 Screen

`Document.reloadStyles()` 在文档线程调用，返回 `void`：使链接和 `@import` CSS 缓存失效，下一帧重算样式与必要布局；不重建节点、不重新执行脚本或控制器。滚动会按新布局范围夹取。HTML/脚本变更仍用完整页面重载，状态边界见 [验收工具](validation.md#游戏与热重载)。开发检查器属于内部可选工具，正式运行包不含其实现或页面。

包 `dev.oriel.mod.client`。参数 data 为 Gson JsonElement，可空。

| 方法 | 返回/用途 |
|---|---|
| OrielScreens.open(String url) | OrielScreen，资源页 |
| OrielScreens.open(String url, JsonElement data) | 资源页+初始数据 |
| OrielScreens.openInline(String html, JsonElement data) | 内联页；外部资源仍要可解析 |
| OrielScreens.onPageLoad(String url, Consumer<DocumentDriver> hook) | 每次打开/导航/重载在脚本前调用；服务端页也会触发 |
| OrielScreens.pages(String url) | 当前显示该 URL 的 driver 列表 |
| registerContainer(MenuType 或 Supplier, String url) | 注册菜单屏幕，客户端初始化、加载完成前 |
| registerContainer(MenuType 或 Supplier, String url, Function<M,JsonObject> data) | 在默认容器数据上添加业务字段，见服务端篇 |
| screen.driver() | 该页 DocumentDriver |
| screen.pauses(boolean) | 单人暂停，默认 false；与服务端交互页通常不暂停 |

```java
import dev.oriel.mod.client.OrielScreens;

// Called on the client thread.
var screen = OrielScreens.open("mymod:ui/settings.html");
screen.driver().onMessage("preview", value -> {
    // Validate the payload and update the mod's local model.
});
```

用 onPageLoad 填充敏感本地数据前检查 driver.serverSession()，避免把数据传给服务器控制的页。页面/脚本可被资源包覆盖，不把机密放进去。

## DocumentDriver

| 方法 | 参数/行为 |
|---|---|
| document() | Document 或 null（未加载/错误时），不要无条件解引用 |
| url() / data() / serverSession() | 当前 URL、保存的 JSON 数据、是否来自服务器会话 |
| push(JsonElement) | 完整替换数据；更新模板并通知 data |
| merge(JsonObject) | 合并顶层字段，保留其余；页面仍收到整份数据 |
| patch(String key, JsonElement value) | 顶层单字段；null/JsonNull 删除；不是 JSON Patch 路径 |
| receive(String channel, String json) | 注入普通消息，第二参数是 JSON **文本**，不保留为重载数据 |
| onMessage(String, Consumer<JsonElement>) | 接页面发给本地宿主的命名消息；返回 driver |
| onClose(Runnable) | 最终关闭/被另一 Screen 替代时一次；导航/重载不算关闭 |
| onKey(Predicate<KeyEvent>) | 处理页面未消费的按键，返回 true 消费；按注册顺序 |
| reload() / close() | 重载/释放文档；一般用 screen 正常关闭保持宿主生命周期 |

push/merge/patch 的 JSON 被保存为文本，之后修改原 Gson 对象不会更新页；调用接口才更新。重载保留这份宿主数据。receive 临时消息构建的状态不自动保存；控制器另用 saveState/restoreState。

屏幕的导航/重载保留 driver 监听，别每次 onPageLoad 无条件重复注册同一 driver 的消息回调；设计可重复执行的初始化。文字输入有焦点时普通键不给模组；Escape 仍有独立关闭规则。

## OrielHud

包 `dev.oriel.mod.client`；id 使用目标 MC 的 Identifier。

| 方法/模式 | 合同 |
|---|---|
| register(id,url) | 无输入 HUD |
| register(id,url,Input) | NONE / WHEN_CHAT_OPEN / WHEN_CURSOR_FREE |
| register(id,url,Predicate<Screen>) | 每帧/指针事件判断可交互的屏幕，判断要轻量 |
| show(id) | 返回当前 driver；从隐藏到显示会创建新的页 |
| hide(id) / isShown(id) | 关闭显示 / 查询 |
| push(id,data) / patch(id,key,value) | 隐藏时也保存，下次 show 使用 |

NONE 不接鼠标；WHEN_CHAT_OPEN 在聊天界面可点；WHEN_CURSOR_FREE 在任何已有 Screen 上可点；不是鼠标锁定游戏视角时抢输入。键盘仍交给底下 Screen。html/body 背景、pointer-events:none 可穿透；未被使用的滚轮可传下去；捕获到按下的 overlay 保留对应释放/拖拽。F1 随 HUD 隐藏。完整语义见 [HUD 补充](../api/hud.md)。

## OrielServer 与 OrielSession

包 `dev.oriel.mod.server`；服务器线程使用。

| 方法 | 合同 |
|---|---|
| OrielServer.open(ServerPlayer,String url,JsonElement data) | 创建该玩家会话；客户端须有资源 |
| OrielServer.openInline(ServerPlayer,String html,JsonElement data) | 发内联 HTML，仍须客户端库 |
| session.id() / player() / isOpen() | 会话标识、所属玩家、是否打开 |
| session.openStatus() | SENT 或 CLIENT_UNSUPPORTED；SENT 不等于页面已显示 |
| session.onOpenFailure(Consumer<OpenStatus>) | 已失败后注册也通知 |
| session.push(data) / patch(key,value) | 整体替换 / 顶层更新；返回 session |
| session.onMessage(channel,BiConsumer<ServerPlayer,JsonElement>) | 收真实发送玩家及值；业务必须再验权限/范围 |
| session.onClose(Runnable) | 最终清理；已关闭时注册立即执行 |
| session.close() | 关闭并清理 |

频率/大小/线程/失败、容器数据见 [服务端](server.md)。收到 JSON 或 channel 名不代表已经授权。

`ForgeMenu.open(player)` 默认固定到脚下非空气方块、最大距离 8 格；`open(player, new ForgeMenu.Workstation(level, pos, block, maxDistance))` 可配置固定工作站。只在服务器线程调用，业务方先授权；位置、方块存续、维度、距离及拥有者状态约束见 [锻造菜单合同](server.md#锻造菜单的接入合同)。两参数构造器仍是便携模式，固定工作站用三参数构造器传非 null 工作站。

## 项目内 Java 页面控制器

`dev.oriel.engine.host.PageControllers.register(url,Supplier<Controller>)` 为每次加载创建新 controller。Controller.attach(Document) 必须实现；saveState() 默认 null；restoreState(Object) 默认不做事。可复用最小例子见 [制作流程](ui-workflow.md)。这些接口可用于项目内开发，但不在下游稳定承诺中。

| 复用件 | 常用接口/单位 |
|---|---|
| Shell | setup(doc)、activate(element,Runnable)、theme/style/font/icons/window、go(doc,url,kind)、back |
| Dom | byId(doc,id)、query(node,selector)、text(el,text)、attr(el,name,value)、el(doc,tag,classes,text)、html(doc,markup)、on/onClick/onMouse/onKey |
| Dom.Style | 构造后 set(cssName,cssValue).apply()；CSS 字符串含单位 |
| Slider | new Slider(doc,element,steps)、set/value、onInput/onCommit；0–1 比例 |
| Notice | bar(doc,kind,text,action,Runnable)、closeBar(doc)、toast(doc,kind,title,sub,ms) |
| Motion | play(el,className,ms)、toggle(el,open)、count、indicator、reveal；遵守动效规范 |
| Lazy | ready(doc)、progress(doc,fraction,text)、reveal(el)、replay(el) |
| McFormat | render(doc,el,text)、json(text)、strip(text) |
| GameRegistry | entries()/entities()/name(id)；游戏读取注册表，预览用小型 fixture |

Java Host.send(channel,jsonText) 使用 JSON 文本，区别于 JS oriel.send 的对象和 Driver.onMessage 的 JsonElement。初始数据用 doc.initialData()（JSON 文本，可空）；后续用 doc.addDataListener((key,json) -> ...)：key=null 表示完整替换，其他 key 表示 patch，json=null 表示删除。业务自己保存更新后的模型；没有 doc.data() 或独立 DOM data 事件 API。普通 receive 会产生 message 事件，patch 不产生。

## 自动化与实体扩展

OrielAutomation 用于游戏内点击、输入、拖拽与等待；准确签名与线程见 [开发参考](../api/development.md)，预览动作 CLI 见 [验收工具](validation.md)。不能把它当服务端交易 API。

OrielEntities.registerPortraitState(EntityType<T>, PortraitState<? super T>) 为模组实体提供肖像 render state；回调 `(entity,partialTick)` 返回 state，null 回退，重复注册替换；每帧调用。Oriel 会去掉名称/世界阴影等并调整肖像朝向，详细字段见 [元素扩展](../api/elements.md#entity-render-states)。
