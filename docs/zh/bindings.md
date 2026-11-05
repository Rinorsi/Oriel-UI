# Java ViewModel 声明式绑定

[入口](index.md) · [Java API](java-api.md) · [脚本路线](scripting.md) · [维护约定](maintenance.md)

`dev.oriel.kit.bind` 是 kit 的可选 Java 控制器能力，不依赖 Minecraft、Rhino 或反射。只有调用 `Bindings.attach(Document, ViewModel)` 才会处理 `o-*`；单独加载 HTML、stock 预览器或浏览器不会自动创建 ViewModel。不要让 JS 模板、Shell 与绑定共同修改同一节点的内容或同一个状态属性。

验证等级：Java 无头测试覆盖解析、布局、六种指令、更新、诊断与生命周期；没有据此宣称游戏、IME、联机或浏览器等价。库中现有画廊页面未迁移到本能力。

## 状态与路径

`new ViewModel(Map<String, ?> initial)` 显式提供状态。键为 `[A-Za-z_][A-Za-z0-9_]*`，值允许 string、boolean、不可变标准数值类型（Byte/Short/Integer/Long/Float/Double/BigInteger/BigDecimal）、null、Map、List 和 `ViewModel.Command`。Map/List 递归快照且只读；修改传入集合不会触发或改变模型。不接受任意 POJO、getter、数组或其他可变对象。

表达式只有大小写敏感的点分路径，例如 `player.name`。不做 trim；不接受空格、索引、运算符、否定、调用、插值、字面量或 JavaScript。字符串 `true` 代表名为 true 的路径；布尔常量应放入模型。属性名由 HTML 解析器小写化，故类名、属性名与事件后缀只能用小写。

| API | 参数 / 返回 | 默认、时机与限制 |
|---|---|---|
| ViewModel(Map<String, ?> initial) | 非 null 初始字段；构造模型 | 无隐式字段；允许用可变 Map 提供 null；初始数据类型错误抛 IllegalArgumentException |
| get(String path) | 返回快照中的值，允许 null | 必须存在所有路径段；只穿过 Map，不穿过 List |
| set(String path, Object value) | 修改已存在的路径；void | 同步通知相关绑定；相等值不通知。可替换整个 Map/List，但不能通过点路径修改列表索引或新增不存在的字段 |
| Bindings.attach(Document, ViewModel) | 返回 AutoCloseable 绑定会话 | 每个 Document 只持有一份；先校验全部活动指令语法，再首次渲染并监听模型 |
| Bindings.close() | void；幂等 | 停止监听、移除事件处理器，保留最后 DOM。正常 Document unload 自动调用；页面因引擎错误停止时宿主须显式 close；导航/热重载创建新 Document 和新绑定 |
| ViewModel.Command.execute(Event event) | 当前原生 DOM 事件；void | 用 Java lambda 实现；通过 event.currentTarget() 读取行元素。无隐式行参数、事件修饰符或自动 preventDefault |

所有调用都必须在 Document 所属线程执行；不提供线程切换或线程安全。业务模型可跨重载保留，旧绑定不能复用到新 Document。模型变化只检查相关路径及其父/子路径的绑定；不轮询、不每帧全页重建。指令属性在 attach 时编译，之后改属性或追加 HTML 不会自动重新绑定。

## 指令参数表

以下均运行在装入 kit 并显式 attach 的 Java 宿主/无头环境；游戏使用相同代码，但仍须针对页面另做游戏验收。没有指令时 DOM 保持原状。

| 名称 | 值 / 用途 | 缺省、类型与更新 | 限制 / 错误 |
|---|---|---|---|
| o-text | `player.name`，写 textContent | string/number/boolean 转文本；null 为空串；相同文本不写 DOM | 目标不能有子元素；不解析 HTML |
| o-if | `visible`，控制节点是否在树中 | 只能 boolean；false 脱离树，true 原位插回 | 保留同一节点、输入值与绑定；移出树会按引擎规则失去焦点/hover；不生成隐藏替代元素 |
| o-class:* | `o-class:active="active"`，切换一个类 | 只能 boolean；不影响其他类 | 后缀 `[a-z_][a-z0-9_-]*`；不接受类列表 |
| o-attr:* | `o-attr:title="tip"`，写一个属性 | string/number 转文本，true 写空属性，false/null 移除 | 同上后缀；禁止 class、style、on*、o-*；是属性绑定，不是 input.value 的双向绑定 |
| o-on:* | `o-on:click="save"`，调用命令 | 必须解析到 Command；事件发生时读取最新命令，重绑定不累积监听器 | 同上后缀；用 DOM addEventListener，无白名单或事件修饰符；只监听宿主实际派发的事件 |
| o-for | `row in inventory.rows`，按集合重复当前元素 | collection 必须是 List；空列表生成零行；必须同时写 o-key | 仅接受一个 alias、空格 + in + 空格、路径；不支持索引别名、对象枚举或模板标签 |
| o-key | `row.id`，o-for 的身份 | 必填；路径必须在该行 alias 下；值必须为非 null String 或 Byte/Short/Integer/Long | 同一列表内按 Java equals 判重；类型也参与身份；禁止使用可变/浮点 key。没有 o-for 时不可使用 |

循环别名可嵌套并遮蔽外层同名别名；未被别名遮蔽的路径从根模型读取。列表替换时按 key 复用节点和绑定，只新增/删除对应行，必要时在原树中移动行，保持其输入状态；列表外的兄弟不重建。`o-for` 和 `o-if` 同一元素时，先展开循环，再按行判断条件。所有结构占位使用空文本节点，不增加可见包装元素。

`<template>` 内容惰性，不遍历或执行其中的绑定；在 template 本身放指令会报错。循环即使为空也检查全部指令语法，但行内依赖的数据路径、类型在该行实际创建时检查。不要在重复行中放固定 id，否则会产生重复 DOM id；可用 `o-attr:id` 绑定独立字段。

## 错误与恢复

未知/错放指令、非法路径表达式、不存在的路径、错误值类型、重复/非法 key、命令异常均报告 `Diagnostic(ERROR, "binding-*", document.url(), line, column, ...)`，随后抛 IllegalArgumentException。位置指向原始标签起点，克隆行保留模板位置；程序创建的元素没有源文本，位置为 0。片段解析的位置相对该片段，source 仍为文档 URL。语法错误码为 binding-expression，另有 binding-path/type/key/directive/attribute/structure/command。

直接调用 ViewModel 构造/get/set 时的参数错误抛 IllegalArgumentException；它们没有页面位置。DOM 事件分发会由引擎捕获处理器异常，但 binding-command 诊断仍保存到 document.diagnostics()。

失败不是事务：set 在通知前已替换模型；一次失败可能已有其他绑定更新。重复 key 在修改该列表 DOM 前检查，保留该列表最后有效的行；随后 set 合法数据可以恢复。attach 失败会解除已装监听器，但可能留下结构占位/已变更的 DOM，应丢弃该 Document 并修正后重新加载。通知会尝试其他订阅者，再向调用者抛出错误。

## 可运行最小示例

HTML 位于 `kit/src/test/resources/bind/minimal.html`，独立于画廊：

```html
<main>
  <h1 o-text="title"></h1>
  <p o-if="visible" o-class:active="active" o-attr:title="tip">Ready</p>
  <ul><li o-for="row in rows" o-key="row.id" o-text="row.label"></li></ul>
  <button o-on:click="save">Save</button>
</main>
```

Java 接线（导入 java.util.Map/List、dev.oriel.kit.bind.ViewModel/Bindings 和 dev.oriel.engine.host.PageControllers）：

```java
ViewModel vm = new ViewModel(Map.of(
        "title", "Inventory", "visible", true, "active", true, "tip", "Local preview",
        "rows", List.of(Map.of("id", "iron", "label", "Iron")),
        "save", (ViewModel.Command) event -> {}));
vm.set("save", (ViewModel.Command) event -> vm.set("title", "Saved locally"));
PageControllers.register("example-bind:ui/minimal.html", () -> doc -> Bindings.attach(doc, vm));
```

注册必须早于 Document.parse/打开页面；按精确 URL 匹配。下游模组把示例 HTML 放入自己的 assets/<namespace>/ui/ 并同步注册 URL；无需调用 Shell.setup。外部预览启动端必须装入该 Java 注册逻辑，不能只传 HTML 给 stock preview CLI。浏览器 mock 需自行按相同状态合同更新 DOM；本能力不新增 MCP 的 Java 浏览器桥接。

仓库无头运行：`bash <工作区>/tools/gw.sh :kit:test --tests '*BindingExampleTest'`。测试注册控制器、解析完整示例，在 427×240 执行布局并触发 Save，断言六种指令的结果与零诊断。它只改变本地模型，不保存到服务端，也不启动游戏。
