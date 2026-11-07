# 脚本、DOM 与交互合同

[入口](index.md) · [Java API](java-api.md) · [元素](elements.md) · [安全与设置](security.md)

页面可以带 JavaScript：`<script>` 元素（内联或 `src`）、内联处理器（`onclick="..."`），以及不写任何脚本就能把 HTML 绑定到数据上的 Vue 风格模板。本页说明脚本能用什么、模板怎么工作、Oriel 与浏览器有哪些不同。

Java 路线另有 [ViewModel 声明式绑定](bindings.md)：`o-text`、`o-for`/`o-key`、`o-if`、`o-class:*`、`o-attr:*`、`o-on:*`。它只读取 Java 模型的点分路径，必须显式 `Bindings.attach`；不执行 JS，不与下文 `v-*` 模板互换。完整参数、生命周期、错误和无头最小例子以该文档为准。

脚本在打过补丁的 [Mozilla Rhino](https://github.com/mozilla/rhino) 1.9 中运行，处在沙箱里，因为页面可能来自服务器。没有浏览器进程；Vue 风格模板是 Oriel 自己的模板绑定，不是 Vue 运行时。kit 预设用 Java 控制器，是项目组织方式，**不表示库不支持脚本**。

- [语言](#语言)
- [脚本怎么运行](#脚本怎么运行)
- [全局对象](#全局对象)
- [DOM](#dom)
- [事件](#事件)
- [动画](#动画)
- [oriel 对象](#oriel-对象)
- [模板](#模板)
- [限额与沙箱](#限额与沙箱)

## 语言

Rhino 实现了 ES2015 的大部分和后续版本的不少内容。Oriel 用补丁（`rhino/patches`）构建它，补上页面最常用而上游缺少的部分。下列都能用：

- `let` 与 `const`，块级作用域，每次循环迭代有新绑定（`for (let i ...)`、`for (const x of list)`、`for (const k in obj)`），所以循环里创建的闭包看到各自迭代的值
- 箭头函数、模板字符串（含 `String.raw`）、默认参数和 rest 参数
- 带默认值的解构（`const {a, b: [c] = []} = obj`），数组和对象字面量里的展开（`[...a, 1]`、`{...defaults, size: 2}`）以及调用里的展开（`f(...args)`、`new C(...args)`）
- `class`：构造器、方法、getter/setter、静态成员、计算名、`extends`（含内建类如 `Error`、`Array`、`Map`）、`super(...)`、`super.method()`、公有实例字段和静态字段、`static {}` 块、构造器里的 `new.target`
- `async function`、async 箭头函数和 async 方法、`await`；`await` 在 promise 落定时恢复，如果已经落定则在同一个 entry 里恢复（见[脚本怎么运行](#脚本怎么运行)）
- 生成器和自定义可迭代对象、标签、可选链 `a?.b?.()`、空值合并 `??` 和 `??=`、`**`、可省略的 `catch {}` 绑定
- `Map`、`Set`、`WeakMap`、`Symbol`、`Promise`（含 `all`、`allSettled`、`any`）、`Proxy`、`Reflect`、`BigInt`
- 对象字面量里的 getter、setter、简写方法和计算键；正则里的具名捕获组和后行断言
- 较新的库方法：`Array.prototype.flat/includes/findLast/at/toSorted`、`Object.entries/fromEntries/hasOwn`、`String.prototype.padStart/replaceAll/at` 等

下面这些**不能用**，除非另有说明，每一项都是语法错误：

| 不支持 | 改写成 |
|---|---|
| 私有类成员：`#count`、`this.#count` | 普通属性，习惯上叫 `_count` |
| async 生成器（`async function*`）、`for await` | 在 async 函数里遍历一个 promise 数组 |
| async 函数之外的 `await`，包括脚本顶层 | `(async () => { ... })()` |
| 解构里的对象 rest：`const {a, ...rest} = obj` | 复制后 `delete`，或只挑需要的字段 |
| `import` / `export`、模块加载、npm 模块运行时 | 用多个 `<script>`，它们共用一个全局作用域；或构建成不依赖浏览器专有能力的单文件，并在 Rhino 里验证 |
| `Intl`（未定义） | 自己格式化数字和日期，或用宿主翻译 |

与浏览器行为有差异的地方：

- 在声明之前读取 `let`、`const` 或 `class` 绑定得到 `undefined`，而不是 `ReferenceError`；所以在 `class Shop {}` 之前写 `new Shop()` 会得到 `TypeError`（不是构造函数）。
- 给 `const` 赋值不一定报错：在非严格代码里可能被忽略，也可能改变值。两种都不要依赖。
- 类字段按 `this.x = value` 的方式设置，所以父类上同名的 setter 会运行。计算字段名（`[key] = 1`）对每个新实例都重新求值，不是只求一次。
- 不带接收者调用的函数或方法，`this` 是全局对象，类体里也是如此。
- `new.target` 只能在类构造器（及其中的箭头函数）里用，别处是语法错误。

## 脚本怎么运行

脚本在文档解析完成后，按文档顺序运行（类似 `defer`）。随后触发 `DOMContentLoaded` 和 `load`。之后由脚本插入的 `<script>` 在接入文档时运行。注册到精确 URL 的 Java 控制器在脚本运行之后、`DOMContentLoaded` 之前 attach。

页面离开时（它的界面关闭、HUD 叠加层隐藏、链接或 `location.href` 加载了另一个页面、或被重载），先后在 `window` 上触发 `pagehide` 和 `unload`（`addEventListener('unload', fn)`）。此时脚本仍能运行，所以页面可以保存状态或用 `oriel.send` 发最后一条消息；但它启动的定时器不会再触发。持久数据交给宿主，不要依赖卸载一定成功。

一切都在同一个线程上运行：游戏的渲染线程。脚本不要做阻塞或大循环。每次引擎调用进脚本（运行 `<script>`、事件监听器、内联处理器、定时器、动画帧、来自服务器的消息）就是一个 **entry**。一个 entry 结束时，Oriel 依次：

1. 运行微任务队列（promise 回调、`queueMicrotask`、`await` 之后恢复的 async 函数），
2. 重新渲染模板（见[模板](#模板)），
3. 报告没有人处理的 promise 拒绝。

错误永远不会逃到游戏里。未捕获的异常通过宿主报告（游戏里是日志，测试里是 `host.errors`），带脚本名和行号，例如：
`Error in script ns:oriel/shop.html#script: TypeError: Cannot read property "price" from undefined (ns:oriel/shop.html#script:12)`，页面继续工作。内联脚本的行号从 `<script>` 标签开始数。页面没有崩溃，不表示业务请求成功。

## 全局对象

全局对象是 `window`（也叫 `self`、`globalThis`）。每个 `<script>` 顶层的 `var`、`let`、`const` 和函数声明都是全局的。

| 全局 | 说明 |
|---|---|
| `document` | 页面。 |
| `console.log/info/debug/warn/error` | 按对应级别写到宿主日志。字符串原样输出，其他值输出为 JSON；开头的字符串可用 `%s %d %i %f %o %O %c`。 |
| `setTimeout(fn, ms, ...args)`、`setInterval`、`clearTimeout`、`clearInterval` | 按帧时钟运行。`fn` 可以是一段代码字符串。 |
| `requestAnimationFrame(fn)`、`cancelAnimationFrame(id)` | `fn(timestamp)` 运行一次，在下一帧绘制之前、到期定时器之后。 |
| `performance.now()` | 当前帧时间（毫秒），即 `requestAnimationFrame` 时间戳用的时钟。在一个 entry 内不前进。 |
| `queueMicrotask(fn)` | |
| `structuredClone(value)` | 一次 JSON 往返：只能是普通数据。 |
| `localStorage`、`sessionStorage` | `getItem`、`setItem`、`removeItem`、`clear`、`key(i)`、`length`。只在当前页面内存里，页面打开时都是空的；**不是落盘 API**，重新打开会清空。 |
| `location` | `href`（读取得到页面 URL，赋值则导航）、`assign(url)`、`replace(url)`、`reload()`。相对 URL 按页面解析。 |
| `innerWidth`、`innerHeight` | 视口大小，GUI 像素。 |
| `devicePixelRatio` | Minecraft 的 GUI 缩放。 |
| `getComputedStyle(el, pseudo?)` | 只读：`getPropertyValue('margin-top')` 或 `.marginTop`。值是 CSS 文本。 |
| `addEventListener`、`removeEventListener`、`dispatchEvent` | 在 `window` 上调用时转给文档。 |
| `close()` | 关闭界面（同 `oriel.close()`）。 |
| `JSON`、`Math`、`Date`、`RegExp`、`Promise` 等 | 标准库。 |
| `Event`、`CustomEvent` | 可构造。`Node`、`Element`（别名 `HTMLElement`）、`Text`、`Document`、`DocumentFragment`、`MouseEvent`、`WheelEvent`、`KeyboardEvent`、`FocusEvent`、`InputEvent`、`TransitionEvent`（别名 `AnimationEvent`）、`CSSStyleDeclaration`、`DOMTokenList`、`Animation`、`Storage` 存在，可用于 `instanceof`。 |

没有 `fetch`、`XMLHttpRequest`、`WebSocket`、`alert`、`prompt`，也没有模块加载器，也没有 `Java`/`Packages` 和任意文件访问。与外界通信走宿主：[`oriel.send` 和 `oriel.on`](#oriel-对象)。

## DOM

同一个节点每次拿到的都是同一个对象（`document.getElementById('a') === list.firstElementChild`）。`childNodes`、`children`、`querySelectorAll()`、`getElementsByClassName()` 这类列表返回真正的**数组快照**，文档变化时不会更新，也不是 live 的 HTMLCollection。

**Node**（元素、文本、文档和文档片段）：`nodeType`、`nodeName`、`ownerDocument`、`isConnected`、`parentNode`、`parentElement`、`childNodes`、`firstChild`、`lastChild`、`previousSibling`、`nextSibling`、`children`、`childElementCount`、`firstElementChild`、`lastElementChild`、`previousElementSibling`、`nextElementSibling`、`textContent`、`hasChildNodes()`、`contains(node)`、`appendChild`、`insertBefore`、`removeChild`、`replaceChild`、`cloneNode(deep)`、`remove()`、`append(...)`、`prepend(...)`、`before(...)`、`after(...)`、`replaceWith(...)`、`replaceChildren(...)`（这些接受节点和字符串）、`querySelector`、`querySelectorAll`、`getElementsByTagName`、`getElementsByClassName`、`addEventListener`、`removeEventListener`、`dispatchEvent`，以及 `on<event>` 处理器属性（`button.onclick = fn`）。

**Element**：

- 身份与属性：`tagName`（大写）、`localName`、`id`、`className`、`classList`、`dataset`、`attributes`（`{name, value}` 数组）、`getAttribute`、`setAttribute`、`removeAttribute`、`hasAttribute`、`hasAttributes`、`toggleAttribute(name, force?)`、`getAttributeNames()`。
- 反射属性：`name`、`type`、`placeholder`、`href`、`src`、`title`、`tabIndex`，以及布尔标志 `disabled`、`hidden`、`readOnly`、`required`、`autofocus`。
- `classList`：`add(...)`、`remove(...)`、`toggle(token, force?)`、`contains`、`replace(old, new)`、`item(i)`、`length`、`value`、`forEach(fn)`。
- `dataset`：`data-*` 属性以驼峰属性出现（`data-slot-count` 是 `dataset.slotCount`）。
- `style`：内联样式。任何 CSS 属性的驼峰形式（`el.style.backgroundColor = '#333'`，厂商前缀写成 `webkitTransform` 或 `WebkitTransform`），另有 `setProperty(name, value, priority?)`（也适用于 `--custom` 属性）、`getPropertyValue`、`getPropertyPriority`、`removeProperty`、`cssText`、`length`、`item(i)`。赋 `null` 或 `''` 移除该属性；给 `el.style` 赋字符串相当于设置 `cssText`。
- 标记：`innerHTML`、`outerHTML`、`insertAdjacentHTML(position, html)`、`innerText`（等同 `textContent`）。**用 `textContent` 插入用户文字**；`innerHTML`/`v-html` 会解析事件和标签，不能拼接未经处理的物品名或聊天文本。新增的标记里的模板不会自动编译。
- 选择器：`matches(selector)`、`closest(selector)`。无效选择器抛 `SyntaxError`。
- 表单状态：`value`、`checked`，对 `<select>` 还有 `selectedIndex`、`options`，选项上有 `selected`。`value` 和 `checked` 是运行时状态，属性保留初始值。复选框的 `value` 缺省是 `"on"`，除非有 `value` 属性。选中一个 radio 会取消同组其他项。把 select 的 `value`（或 `selectedIndex`）设成没有任何选项拥有的值，则什么也不选（`selectedIndex` 为 -1）。
- 几何（先把布局更新到最新）：`getBoundingClientRect()`（`x`、`y`、`width`、`height`、`top`、`right`、`bottom`、`left`，GUI 像素：绘制时的盒子，所以已计入滚动和变换）、`offsetLeft/Top/Width/Height`（相对视口；`offsetParent` 始终为 null）、`clientWidth/Height`、`scrollWidth/Height`、`scrollLeft/Top`（可赋值）、`scrollTo(x, y)` 或 `scrollTo({left, top, behavior})`、`scrollBy(...)`、`scrollIntoView()`（`true`/`false`，或 `{block, inline, behavior}`）。没有 `behavior` 的滚动（包括给 `scrollTop` 赋值）遵循元素的 `scroll-behavior`，在 Oriel 里缺省是 `smooth`：位置会在接下来几帧里缓动过去，不是赋值后同一行就到目标。需要确定性截图时，明确写 `scroll-behavior:auto` 或等待。`scroll` 事件每帧最多触发一次。
- `focus()`、`blur()`、`click()`、`animate(keyframes, options)`。
- Canvas：`width`/`height`（可赋值；改尺寸会清空画布）和 `getContext('2d')`（返回该画布唯一的 2D 上下文，其他类型和其他元素返回 null）。上下文支持 `fillStyle` 与 `strokeStyle`（CSS 颜色）、`lineWidth`、`globalAlpha`、`save()`/`restore()`、`fillRect`、`strokeRect`、`clearRect`、`getImageData`、`putImageData`（可带脏矩形）、`createImageData(w, h)` 或 `(imageData)`，以及 3、5 或 9 个参数的 `drawImage(canvas, ...)`（来源只能是另一个画布，按最近邻缩放）。图像数据是 `{width, height, data}`，`data` 是 RGBA 字节的 `Uint8ClampedArray`（`putImageData` 也接受普通数组）。坐标是画布像素，边缘贴齐整像素：没有抗锯齿、文字、路径、变换、渐变或图案，也没有 WebGL。画布在加入页面之前就可以绘制。

**Text**：`data`、`nodeValue`、`length`。

### Canvas 凸四边形扩展

`ctx.fillQuad(x0, y0, x1, y1, x2, y2, x3, y3)` 是 **Oriel 扩展**，不属于浏览器 Canvas 标准。用于旋转矩形、粗线段和四边形粒子；由引擎一次处理扫描线，避免脚本逐行调用 fillRect。

| 字段 | 合同 |
|---|---|
| 前置条件 | 本版引擎/预览器的 2D 上下文；先检测 `typeof ctx.fillQuad === "function"`。旧 0.11.0 jar 可能没有此方法，需要更新库并重启客户端 |
| 参数 | 必须传 8 个可转为有限数值的坐标，单位为画布像素，允许小数/负值/出画布坐标；没有省略值 |
| 顶点顺序 | 沿凸四边形周界顺时针或逆时针；自交、凹形、非有限坐标抛 TypeError；全部共线/面积为零不绘制 |
| 样式 | 使用当前 fillStyle 和 globalAlpha，source-over 混合；save/restore 规则不变 |
| 裁剪与采样 | 限制在画布边界内；像素中心扫描线，端点按 fillRect 的整数边界取样；无抗锯齿 |
| 返回值 | undefined；不改变填色、透明度或现有状态 |
| 范围 | Java 引擎与 Rhino 脚本均可用；不是通用路径、曲线、渐变、transform 或 GPU 绘制接口 |
| 验证 | 引擎像素差分覆盖旋转、两种绕序、越界和透明度；真实游戏 GPU/FPS 须另测 |

```html
<canvas id="fx" width="80" height="60"></canvas>
<script>
const ctx = document.getElementById('fx').getContext('2d');
if (typeof ctx.fillQuad !== 'function') throw new Error('This page needs Oriel fillQuad');
ctx.fillStyle = '#ffd25a';
ctx.globalAlpha = 0.7;
ctx.fillQuad(40, 8, 64, 30, 40, 52, 16, 30);
</script>
```

粒子分层、兼容回退和性能验证见 [开工规范](performance.md)。页面代码可为旧库/浏览器保留明确的兼容实现，但不能把旧路径的性能当成新版。

**Document**：`documentElement`、`head`、`body`、`title`（可赋值；是界面的标题，朗读器会读它，所以赋值就是给界面改名）、`activeElement`（获得焦点的元素，没有则为 `body`）、`readyState`、`URL`、`location`、`defaultView`、`getElementById`、`createElement`、`createTextNode`、`createDocumentFragment`。

## 事件

`addEventListener(type, listener, options)` 接受函数或带 `handleEvent` 的对象；`options` 是 capture 布尔值或 `{capture, once}`。`removeEventListener` 按同一个函数和 capture 标志匹配。监听器内的 `this` 是挂监听器的元素。事件按捕获/冒泡路径传递。

每个事件都有 `type`、`target`、`currentTarget`、`eventPhase`、`bubbles`、`cancelable`、`defaultPrevented`、`timeStamp`、`preventDefault()`、`stopPropagation()`、`stopImmediatePropagation()`。一次派发的所有监听器看到同一个事件对象。按种类：

| 事件 | 字段 |
|---|---|
| `MouseEvent`（`click`、`mousedown`、`mousemove`、`mouseover` 等） | `clientX`、`clientY`（以及 `pageX`、`pageY`）、`offsetX`、`offsetY`、`button`、`buttons`、`detail`（点击次数）、`relatedTarget`、`shiftKey`、`ctrlKey`、`altKey`、`metaKey` |
| `WheelEvent`（`wheel`） | 鼠标字段，加 `deltaX`、`deltaY`（GUI 像素）、`deltaMode`（始终为 0） |
| `KeyboardEvent`（`keydown`、`keyup`） | `key`（`"a"`、`"Enter"`、`"ArrowLeft"`、`"Escape"` 等）、`code`（`"KeyA"`）、`keyCode`（物理按键的传统编码，与浏览器在美式键盘上报告的一致）、`repeat` 以及修饰键 |
| `FocusEvent`（`focus`、`blur`、`focusin`、`focusout`） | `relatedTarget` |
| `InputEvent`（`beforeinput`、`input`、`change`） | `data`、`inputType` |
| `TransitionEvent` / `AnimationEvent` | `propertyName`、`animationName`、`elapsedTime`、`pseudoElement`（由目标的伪元素运行时是 `"::before"` 或 `"::after"`，否则是 `""`） |
| `CustomEvent` | `detail` |

用 `new Event(type, {bubbles, cancelable})` 或 `new CustomEvent(type, {detail, bubbles, cancelable})` 创建自己的事件（和浏览器一样，两者缺省都不冒泡），再 `el.dispatchEvent(event)`；有监听器调用了 `preventDefault()` 时它返回 false。

Oriel 的 `activationsound` 是可冒泡、可取消的 `Event`：原生按钮/复选等控件即将播放默认声音时派发，原生 select 打开也会派发。取消它只替换/抑制声音，不取消表单与复选动作。Shell 用它统一选择 UI token，自定义控件通过 `Shell.activate` 的键盘入口也会派发；这是库扩展事件，浏览器不提供。

内联处理器（`<button onclick="buy(this.dataset.item, event)">`）运行时 `this` 是该元素，`event` 在作用域内；返回 `false` 取消事件，`on<event>` 属性处理器返回 `false` 同样取消。与浏览器不同，内联处理器**看不到元素的属性变量**：写 `this.value`，不要写 `value`。

按 Esc 会关闭界面，除非 `keydown` 监听器调用了 `preventDefault()`；但 Shift+Esc 或快速多次 Esc 由宿主强制关闭，所以要保留明确的关闭入口。显示页面的 mod 可以处理页面没有占用的任何按键，例如打开界面的那个键。想为页面保留某个键，在 `keydown` 监听器里取消它。文本框有焦点时，除 Esc 之外的按键都是输入，永远不会传给 mod。

`title` 属性显示提示框（规则见[元素](elements.md#提示框与物品提示)）。Minecraft 的朗读器会读页面标题、获得焦点或悬停的元素的名称和角色（`aria-label`、`aria-labelledby`、`role`、`aria-hidden`），以及文字变化时的实时区域（`aria-live`、`role="status"`、`"log"`、`"alert"`）。规则见[朗读](narration.md)。脚本通过改变这些属性和区域文字来改变朗读内容。

## 动画

`el.animate(keyframes, options)` 在元素 CSS 之上运行 Web Animations 风格的动画：

```js
const anim = panel.animate(
  [{opacity: 0, transform: 'translateY(8px)'}, {opacity: 1, transform: 'none'}],
  {duration: 200, easing: 'ease-out', fill: 'forwards'});
anim.finished.then(() => console.log('shown'));
```

- 关键帧是对象数组（每个关键帧的 `offset` 和 `easing` 可省略，缺失的 offset 均匀分布），或数组对象（`{opacity: [0, 1], marginLeft: ['0px', '8px']}`）。属性名用驼峰或 CSS 名；值是 CSS 文本。
- options 是毫秒数，或 `{duration, delay, easing, iterations, direction, fill}`。`easing` 可为 `linear`（缺省）、`ease`、`ease-in`、`ease-out`、`ease-in-out`、`step-start`、`step-end`、`cubic-bezier(...)` 或 `steps(n, jump-start|jump-end|jump-none|jump-both)`；`iterations` 可为 `Infinity`；`direction` 是 `normal`、`reverse`、`alternate` 或 `alternate-reverse`；`fill` 是 `none`、`forwards`、`backwards` 或 `both`。
- 返回的 `Animation` 有 `play()`、`pause()`、`cancel()`、`finish()`、`reverse()`、`playState`（`idle`、`running`、`paused`、`finished`）、`currentTime`、`playbackRate`、`onfinish`、`oncancel`，以及 `finished` promise（取消时以 `AbortError` 拒绝）。
- 属性仍受 [CSS 插值类型](css-guide.md) 限制，不能把 `filter` 之类的离散属性当成平滑动画。

## oriel 对象

| 成员 | 说明 |
|---|---|
| `oriel.data` | 服务器或打开页面的 mod 最近一次发来的数据（解析后的 JSON 值；数据到达之前是 `{}`）。模板从这里读。`push` 会整体替换它；直接赋值不会传给服务端。 |
| `oriel.on(channel, fn)` | 对 `channel` 上的消息调用 `fn(value)`；`'data'` 在 `oriel.data` 被替换或打补丁时触发，`'patch'` 在其中一个字段被打补丁时触发（见[补丁](#补丁)）。返回一个移除该监听器的函数。 |
| `oriel.off(channel, fn)` | 移除监听器。 |
| `oriel.send(channel, value)` | 把 `JSON.stringify(value)` 发给服务器或 mod。突发 20 条，之后每秒 20 条，所以第一秒最多 40 条。多余的消息会被丢弃并给出警告，`send` 返回 false；返回 true 也不是业务成功的回执。默认单条消息上限 8192 字符。 |
| `oriel.state(object)` | 让该对象的属性对[模板](#模板)可见，并返回它（缺省是空对象）。 |
| `oriel.close()` | 关闭界面（HUD 则关闭对应叠加层）。 |
| `oriel.playSound(id, volume = 1, pitch = 1)` | 播放声音事件，如 `'minecraft:ui.button.click'`，或 kit 的 [UI 声音 token](capabilities.md#ui-音效)，如 `'tab'`（token 的基础音量/音高乘以参数）；未知裸 token 报错。受 `client.uiSounds`、`client.uiSoundVolume`、音量上限和限频控制。 |
| `oriel.t(key, ...args)` | 翻译语言键，如 `oriel.t('gui.done')`，使用当前宿主语言；参数转成字符串。 |
| `oriel.open(url)` | 打开另一个 Oriel 页面（可以相对当前页）。 |
| `oriel.nextTick(fn)` | 在[模板](#更新)渲染完当前状态之后调用 `fn` 一次（下一帧）。 |

消息也会作为文档上的 `message` 事件到达页面，`detail` 为 `[channel, json文本]`。Java 控制器读取宿主 JSON 的接法见 [Java API](java-api.md)。

### 补丁

mod 或服务器可以只修改 `oriel.data` 的一个顶层字段，不必发送全部数据（`driver.patch(key, value)`、`session.patch(key, value)`）。页面只解析新值并就地设置 `oriel.data[key]`；值为 null 则删除该字段，所以 `oriel.data` 里其他对象仍是同一个对象，对它们的 `v-for` 保留各行。然后：

1. `'patch'` 监听器收到 `{key, value}`（字段被删除时 `value` 是 `undefined`）；
2. `'data'` 监听器收到 `(oriel.data, key)`，所以只监听 `'data'` 的页面仍能看到每次变化。整体更新之后第二个参数是 `undefined`；
3. 模板在下一帧更新，与任何 entry 之后一样。

```js
oriel.on('patch', ({key, value}) => {
  if (key === 'gold') animateCoins(value);
});
oriel.on('data', (data, key) => {
  if (key === undefined || key === 'items') rebuildIndex(data.items);   // 跳过补丁没动过的工作
});
```

补丁不是消息，所以不触发 `message` 事件。

### 处理服务端拒绝的示例

```js
let draft = {name: ''};
let saving = false;
function rejectionText(reason, locale) {
  if (!reason || typeof reason.key !== 'string' || !Array.isArray(reason.args)) {
    return oriel.t('mymod.error.bad_data');
  }
  if (reason.count === undefined) return oriel.t(reason.key, ...reason.args);
  if (typeof locale !== 'string') throw new Error('Host must provide clientLocale for plural messages');
  const suffix = locale.toLowerCase().startsWith('en') && Math.abs(reason.count) === 1 ? '.one' : '.other';
  return oriel.t(reason.key + suffix, reason.count, ...reason.args);
}
oriel.on('data', function (data, key) {
  if (key === 'result' && data.result) {
    saving = false;
    if (data.result.status === 'saved') {
      draft = structuredClone(data.result.item);
    } else {
      document.getElementById('error').textContent = rejectionText(data.result.reason, data.clientLocale);
    }
  }
});
function save() {
  if (saving) return;
  saving = oriel.send('save', {name: draft.name});
}
```

这是使用结构化拒绝回执（status/reason）的自定义页面片段，要求有 `error` 节点、相应的宿主会话，以及由客户端宿主提供的 `clientLocale`（如 `zh_cn`/`en_us`，语言切换时同步）。`clientLocale` 是此示例的接线要求，不是既有编辑器自动提供的字段，也不应使用服务器语言代替。JS 目前没有 `LocalizedMessage` 或复数辅助绑定；不能直接把协议 2 的 reason 对象赋给 `textContent`。既有 Java 控制器用 `LocalizedMessage.fromJson(reason).render(document)`，无需新增该字段。生产页还应有等待超时和重新读取入口；超时不得把未确认请求当成功，也不要无条件重发非幂等交易。

## 模板

模板用类 Vue 的语法把 HTML 绑定到数据上，不需要构建步骤。它们在页面加载完成、脚本运行之后编译一次，所以页面完全不需要 `<script>` 就能使用：

```html
<div class="mc-panel">
  <h1>{{ title }}</h1>
  <p v-if="items.length === 0">没有可卖的东西。</p>
  <ul v-else>
    <li v-for="item in items" :key="item.id" :class="{ sold: item.stock === 0 }">
      <item :id="item.id"></item> {{ item.name }} - {{ item.price }} 绿宝石
      <button :disabled="item.stock === 0" @click="oriel.send('buy', item.id)">购买</button>
    </li>
  </ul>
</div>
```

服务器把 `{"title": "盔甲匠", "items": [...]}` 作为数据发来，页面随每次更新而更新。

### 表达式与作用域

`{{ expr }}`、指令的值和处理器都是 JavaScript。名字按下面的顺序查找：

1. 外层 `v-for` 的循环变量（处理器里还有 `$event`），
2. `oriel.data`，
3. 传给 `oriel.state()` 的对象，按注册顺序，
4. 全局：你脚本里的函数和变量、`Math`、`JSON`、`oriel` 等。

给名字赋值（`@click="count++"`）会写入上面第一个拥有它的对象，否则创建一个全局变量。处理器语句里 `this` 是该元素。方法按方法调用：在 `@click="shop.buy"` 里，以及 `@click="buy"`（`buy` 是某个 `oriel.state()` 对象的方法）里，`buy` 内的 `this.stock` 读的是那个对象的 `stock`。

因为 `oriel.data` 在前面，数据里的键会遮住同名的状态和全局变量。如果页面用 `{"count": 3}` 打开，`{{ count }}` 永远读数据，`count++` 写进 `oriel.data`（下一次数据更新会把它替换掉）而不是你的状态；数据里有个键和你的某个函数同名，会让调用该函数的处理器失效。**把页面打开时给的数据都放在一个键下**，例如 `{"options": {"title": "商店"}}`，读作 `{{ options.title }}`，这样它的名字永远不会和页面自己的名字冲突；本地状态用不冲突的名字。

```html
<button @click="count++">点击了 {{ count }} 次</button>
<script>
  const counter = oriel.state({count: 0});
</script>
```

文本插值按原样显示字符串，`null` 和 `undefined` 显示为空，对象和数组显示为 JSON。纯表达式不应修改状态；`{{ n++ }}` 会导致反复更新并触及上限。

### 更新

模板在页面加载时渲染。任何 entry 之后（事件处理器、定时器、动画帧、消息、`v-model` 输入等），Oriel 在下一帧开始时把所有绑定重新求值一次，只在值变了的地方改 DOM。它会重复到不再变化为止，最多 10 次；如果模板此后仍在变（比如求值时修改状态的表达式 `{{ n++ }}`），它会记一条警告并停止。没有什么需要调用的：改你的状态，页面在下一帧之前就会跟上。

和 Vue 一样，你的代码运行期间 DOM 不会改变：处理器里先设置 `count` 再读按钮文字，看到的是旧文字。要在 DOM 更新之后再读，把回调传给 `oriel.nextTick(fn)`。

模板只编译一次。之后新增的标记（`innerHTML` 或 `v-html`）不会被编译。

### 指令

| 指令 | 说明 |
|---|---|
| `{{ expr }}` | 用在文本和属性值里（`title="等级 {{ level }}"`）。 |
| `:attr="expr"` / `v-bind:attr` | 设置属性。`null`、`undefined`、`false` 移除它；`true` 设成空（`:disabled="busy"`）。表单控件上的 `:value` 和 `:checked` 设置它们的运行时状态。 |
| `:class` / `v-class` | 字符串、数组或 `{类名: 条件}` 对象；叠加在静态 `class` 之上。 |
| `:style` / `v-style` | 字符串、数组或 `{属性: 值}` 对象（驼峰或 CSS 名；值为 `null` 和 `false` 的会被跳过）；叠加在静态 `style` 之上。 |
| `v-if`、`v-else-if`、`v-else` | 用在相邻的兄弟元素上。只有第一个条件成立的分支在文档里。分支的元素创建一次，隐藏时保留，所以它的状态（输入的文字、滚动位置）得以保留。 |
| `v-for="item in items"` | 也可写 `(item, index) in items`、`(value, key, index) in object`、`n in 5`（1 到 5）；`of` 与 `in` 相同。重复该元素。 |
| `:key="expr"` / `v-key` | 在 `v-for` 元素上：条目的身份。给有 key 的列表重排会移动已有元素；没有 key 则按位置复用元素。key 应当唯一。在其他任何元素上：key 变化时元素被替换为新副本，所以 `<h1 class="title" :key="selected">` 每次选择都会重播入场动画。有 key 的 `v-if` 分支也以同样方式重新开始。 |
| `v-show="expr"` | 隐藏元素（给它 `v-hidden` 属性，默认样式表据此隐藏）但不移除它。 |
| `@event="..."` / `v-on:event` | 一条语句（`count++`）、一个方法名（`save`，以事件调用）或一个函数（`e => pick(e.key)`）。事件是 `$event`。 |
| `v-model="path"` | `input`、`textarea`、`select` 的双向绑定（见下）。 |
| `v-text="expr"`、`v-html="expr"` | 用文本或标记替换元素内容；后者不会重新编译模板。 |
| `v-cloak` | 模板渲染完成后移除。加上 `[v-cloak] { display: none }` 可在此之前隐藏原始的 `{{ }}`。 |
| `v-pre` | 跳过该元素及其内容，不编译。 |

在同时带 `v-for` 的元素上写 `v-if`，是过滤条目：`<li v-for="q in quests" v-if="!q.done">`。

`v-if` 和 `v-for` 要放在真实元素上。**不支持用 `<template>` 包裹**（它的内容永远不会渲染），也**没有组件系统**。

### 事件修饰符

写在事件名之后：`@click.prevent.stop="..."`。

| 修饰符 | 说明 |
|---|---|
| `.prevent`、`.stop` | 调用 `preventDefault()` / `stopPropagation()`。 |
| `.once` | 只运行一次。 |
| `.self` | 仅当事件目标是该元素本身（不是子元素）。 |
| `.capture` | 在捕获阶段监听。 |
| `.enter`、`.esc`、`.space`、`.tab`、`.delete`、`.up`、`.down`、`.left`、`.right` | 键盘事件：只对这些键生效。 |
| `.left`、`.middle`、`.right` | 鼠标事件：只对这个按键生效。 |
| `.ctrl`、`.shift`、`.alt`、`.meta` | 仅当按住该修饰键时。 |

### v-model

```html
<input v-model="name">                                  <!-- 字符串 -->
<input type="number" v-model="amount">                  <!-- 数字 -->
<input type="range" min="0" max="10" v-model="volume">  <!-- 数字 -->
<input type="checkbox" v-model="agreed">                <!-- 布尔 -->
<input type="checkbox" value="iron" v-model="picked">   <!-- 数组：选中时包含 "iron" -->
<input type="radio" name="size" value="2" v-model.number="size">
<select v-model="mode"><option>easy</option><option>hard</option></select>
```

文本框在每个 `input` 事件上更新模型；`.lazy` 等到 `change`（回车或失去焦点）。`.trim` 去掉首尾空白，`.number` 把能解析成数字的文本变成数字（`type="number"` 和 `type="range"` 本来就这样）。模型必须是可赋值的东西：一个名字或属性路径。

反过来，文本框显示它的模型：每次更新都比较模型和文本框的文字，不同就替换文字，所以读完输入后立刻清空模型（`send(); draft = ''`）会清空文本框。文本框有焦点时，只被修饰符改变的文字保持输入的原样：`.trim` 的空格、`.number` 的 `1.`，以及 `.lazy` 在 `change` 之前的所有内容。

## 限额与沙箱

脚本被沙箱隔离，因为页面可能来自服务器。服务器推送的内联页与资源页使用同一个沙箱；推送的 JSON 数据按 JSON 解析，不作为代码执行。下面的数字是缺省值，玩家和服务器主人可以在 `config/oriel.properties` 里修改（见[安全与设置](security.md#设置)）。

- **没有 Java 访问**：`java`、`Packages` 等不存在，任何 Java 对象都不会被脚本看到，也没有反射、`Runtime`/`ProcessBuilder`、Java 线程或 `Worker`/`SharedWorker`。Rhino 的非标准全局（`Continuation`、`Script`、`With`、`Call`、`JavaException`）也被移除。Promise、微任务和动态生成的代码共用当前 entry 的预算，不会取得新线程或更高权限。引擎测试不能代替真实服务器联机验收，宿主资源与导航授权的边界见[安全与设置](security.md)。
- **没有网络、文件或其他页面**：唯一的出口是 `oriel.send`。
- **服务端会话不能读客户端注册的 folder 命名空间**：包括通过 `oriel.open`/`location` 导航或重载读取页面，以及加载脚本、样式等文本/二进制资源。被拒绝时资源读取返回 null，并记录 ERROR 级 `server-folder-access` 诊断（来源是被拒绝的 URL，行号未知时为 0）。本地客户端页面仍可读取自己注册的目录。
- **CPU**：每个 entry 最多运行约 5000 万条指令或 1 秒，以先到者为准。超出的脚本被终止（它的 `catch` 和 `finally` 不会运行），错误被报告，页面仍可用。限额涵盖这个 entry 做的一切，包括微任务、模板更新和正则表达式；因此被终止的 async 函数不会恢复。两类一次性的工作在游戏刚启动时会很慢，所以不计入时钟：编译（脚本、处理器、模板首次显示）按源码每个字符一条指令计，而不是按时间；加载页面的 entry（它的脚本、首次模板渲染、`DOMContentLoaded`）可以用 10 秒。累计 3 个 entry 超出之后，页面被终止并显示它的错误。这些是保护上限，不是页面性能预算。
- **定时器和动画帧**：最多 10,000 个待处理。一帧里它们最多运行 100 ms，其余留到下一帧。如果页面的帧持续耗时超过 200 ms（连续 25 次），页面被终止。
- **内存**：一个 entry 最多分配 256 MiB。脚本运行时如果游戏内存超过 90% 已满，页面被终止并释放内存。内存耗尽只停掉页面。
- **在 Java 里实现的内建函数**会拒绝会冻结或撑满游戏的尺寸，抛 `RangeError`：任何 `Array.prototype` 方法、`Array.from`、展开、`apply` 和 `JSON.stringify` 处理长度超过 1,048,576 个元素的数组（及类数组）；`split`、`match`、`matchAll` 和字符串迭代会产生超过 1,048,576 段；超过 16 MiB 的 `ArrayBuffer` 和类型化数组；结果超过 16,777,216 个字符的 `repeat`、`padStart`、`padEnd`、`replace`、`join`；超过 65,536 位的 BigInt。
- **JSON.parse**：`JSON.parse(input, reviver)` 把 input 转成字符串一次，对**同一份文本**检查 `maxDepth`（缺省 512）并解析；reviver 仍是可选回调。超过深度时拒绝解析并抛 `SyntaxError`，同时记录 ERROR 级诊断（`code=script-json-depth`，含脚本来源和行号），即使脚本捕获了异常也保留诊断。例如 `JSON.parse('['.repeat(513) + '0' + ']'.repeat(513))` 在缺省配置下被拒绝。这个检查限制输入文本的深度，不限制 reviver 自己构造的对象，后者受脚本预算约束。
- **递归**限制在 1000 层嵌套调用（一个可以捕获的 `InternalError`）。穿过 DOM 的递归（监听器点击自己的元素）会以"too much recursion"终止这个 entry。
- **文档**最多 100,000 个节点，嵌套最深 512 层；超出会抛错。更深的标记会被解析器压平。
- `innerHTML`、`outerHTML`、`insertAdjacentHTML` 和 `v-html` 最多接受 100 万个字符。
- **画布**每边最多 2048 px，一个页面的所有画布合计最多 16,777,216 个像素（四块 2048 px 的方块）；会超出的画布抛错并得到 1×1 的画面。
- `localStorage` 和 `sessionStorage` 各最多 256K 个字符（键加值）；超出会抛 `RangeError`，消息以 `QuotaExceededError` 开头。
- `v-for` 最多渲染 10,000 个条目。
- `oriel.send` 突发 20 条消息、之后每秒 20 条，`oriel.playSound` 每秒最多播放 8 个声音。两者都按界面计数，所以重载页面不会重置（对应设置 `client.messagesPerSecond`、`client.messageBurst`、`client.soundsPerSecond`）。
- **不要每个输入事件都发一条消息**。滚轮和指针事件每个回调触发一次，触控板每秒可以滚动 60 到 120 次。在页面里保存最新状态，最多每 100 ms 左右发一次，或在拖动/滚动结束时发。
- `console` 和脚本错误最多每秒 50 次进入日志，每条消息截断到 4096 个字符。

用 `+` 拼接的字符串本身没有上限，所以避免构造巨大的字符串：做这件事的 entry 会被内存预算停掉。

输入后端开关、事件 JSONL、预览/MCP 动作、inert 与可选空间导航的参数及限制见[输入诊断与导航](d36-input.md)。缺省后端仍为 old；六目标真实 IME 与手柄另验。
