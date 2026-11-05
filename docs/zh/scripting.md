# 脚本、DOM 与交互合同

[入口](index.md) · [Java API](java-api.md) · [完整脚本细节](../SCRIPTING.md)

页面 JS 在打过补丁的 Rhino 中运行。没有浏览器进程；Vue 风格模板是 Oriel 自己的模板绑定，不是 Vue 运行时。kit 预设用 Java 控制器，是项目组织方式，**不表示库不支持脚本**。

## 生命周期与线程

1. 解析 HTML/CSS，按文档顺序运行 script（src 或内联，类似 defer）。
2. 注册到精确 URL 的 Java 控制器 attach，然后 DOMContentLoaded/load；模板按运行时更新。
3. 点击、输入、定时器、动画帧、宿主消息都在页面线程执行；游戏中是渲染线程。脚本不要做阻塞/大循环。
4. entry 结束处理微任务，模板在下一帧更新；读取更新后 DOM 用 oriel.nextTick(fn)。
5. 关闭/导航/重载先 pagehide、unload；此时还能发最后消息，但新定时器不会继续。持久数据交给宿主，不依赖卸载一定成功。

## oriel 对象（全部成员）

| 成员 | 参数/返回/默认 | 行为 |
|---|---|---|
| data | 最新 JSON，尚未到达时空对象 | push 替换；直接赋值不传给服务端 |
| on(channel,fn) | fn(value)，返回注销函数 | data 监听整体/patch 更新；patch 监听 `{key,value}` |
| off(channel,fn) | 原监听函数 | 注销 |
| send(channel,value) | JSON 可序列化值，返回是否被宿主发送入口接受 | false 可表示限流丢弃；true 不是业务成功回执 |
| state(object) | 默认空对象；返回该对象 | 将字段暴露给模板 |
| close() | 无 | 关闭页面/对应 HUD |
| open(url) | 资源 URL，可相对当前页 | 导航到另一个 Oriel 页 |
| playSound(id,volume,pitch) | volume/pitch 默认 1 | 播放游戏 sound event；仍受客户端音量/限频控制 |
| t(key,...args) | 参数转字符串，返回翻译文本 | 当前宿主语言 |
| nextTick(fn) | 回调 | 模板更新后执行 |

patch 只改一个顶层字段，null 删除该字段；patch 监听删除时 value=undefined，随后 data 监听 `(oriel.data,key)`。整体更新 data 的第二参数为 undefined。普通宿主消息还发 document message 事件，detail=[channel,json文本]；patch 不发这个 message。Java 控制器读宿主 JSON 的接法见 Java API。

```js
let draft = {name: ''};
let saving = false;
function rejectionText(reason, locale) {
  if (!reason || typeof reason.key !== 'string' || !Array.isArray(reason.args)) {
    return oriel.t('oriel.server.item_editor.bad_data');
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

这是使用物品编辑回执结构的自定义页面片段，要求 error 节点、相应宿主会话，以及由客户端宿主提供的 `clientLocale`（如 `zh_cn`/`en_us`，语言切换时同步）。`clientLocale` 是此示例的接线要求，不是既有编辑器自动提供的字段，也不应使用服务器语言代替。JS 目前没有 `LocalizedMessage` 或复数辅助绑定；不能直接把协议 2 的 reason 对象赋给 textContent。既有 Java 控制器用 `LocalizedMessage.fromJson(reason).render(document)`，无需新增该字段。生产页还应有等待超时和重新读取入口；超时不得把未确认请求当成功，也不要无条件重发非幂等交易。

## 支持的语言与全局对象

可用：let/const、箭头函数、模板字符串、默认/rest 参数、数组/对象展开、class、async/await（函数内）、Promise、Map/Set、Symbol、Proxy/Reflect、BigInt、可选链、空值合并、JSON/Math/Date/RegExp。不是 ECMAScript 全兼容：

| 不支持/有差异 | 写法 |
|---|---|
| import/export、模块加载、npm 模块运行时 | 多个 script 共用全局，或构建为不依赖浏览器专有能力的单文件后在 Rhino 验证 |
| 私有类成员 #field | 普通属性 |
| async generator、for await、顶层 await | async 函数里处理 Promise 数组 |
| 对象解构 rest `{a,...rest}` | 显式复制/挑字段 |
| Intl | 宿主翻译或明确格式化 |
| TDZ/const/类字段部分语义不同 | 不依赖声明前访问报错、const 赋值异常或浏览器类字段细节 |

全局：window/self/globalThis、document、console 五级日志、setTimeout/setInterval/clear*、requestAnimationFrame/cancelAnimationFrame、performance.now、queueMicrotask、structuredClone、location、innerWidth/innerHeight、devicePixelRatio、getComputedStyle、Event/CustomEvent。

- 定时器按帧时钟；performance.now 在一次 entry 内不前进。
- structuredClone 是 JSON 往返，仅普通数据。
- localStorage/sessionStorage 仅当前页面内存，重新打开清空，不是落盘 API。
- 没有 fetch/XHR/WebSocket、alert/prompt、Java/Packages、任意文件访问。与外界通信走宿主。

## DOM 方法清单

| 对象 | 可用成员 |
|---|---|
| Document | documentElement/head/body/title/activeElement/readyState/URL/location/defaultView；getElementById、createElement、createTextNode、createDocumentFragment |
| Node | parentNode/parentElement/childNodes/children/firstChild/lastChild/previousSibling/nextSibling/firstElementChild/lastElementChild/previousElementSibling/nextElementSibling/childElementCount/textContent/nodeType/nodeName/ownerDocument/isConnected |
| Node 修改 | appendChild、insertBefore、removeChild、replaceChild、cloneNode(deep)、remove、append/prepend/before/after/replaceWith/replaceChildren、hasChildNodes、contains |
| 查找 | querySelector/querySelectorAll/getElementsByTagName/getElementsByClassName；Element.matches/closest |
| Element 身份 | tagName/localName/id/className/classList/dataset/attributes |
| 属性 | getAttribute/setAttribute/removeAttribute/hasAttribute/hasAttributes/toggleAttribute/getAttributeNames |
| 反射属性 | name/type/placeholder/href/src/title/tabIndex；disabled/hidden/readOnly/required/autofocus |
| classList | add/remove/toggle/contains/replace/item/length/value/forEach |
| style | camelCase 属性；setProperty/getPropertyValue/getPropertyPriority/removeProperty/cssText/length/item；空值移除 |
| 内容 | innerHTML/outerHTML/insertAdjacentHTML/innerText（等同 textContent） |
| 表单 | value/checked；select.selectedIndex/options；option.selected |
| 尺寸 | getBoundingClientRect；offsetLeft/Top/Width/Height、clientWidth/Height、scrollWidth/Height、scrollLeft/Top |
| 操作 | focus/blur/click、scrollTo/scrollBy/scrollIntoView、animate |
| Text | data/nodeValue/length |

querySelectorAll/children 等返回 **数组快照**，不是 live HTMLCollection。offset 坐标相对视口，offsetParent 始终 null；getBoundingClientRect 是滚动/变换后的 GUI px。scroll 默认可平滑，不要求赋 scrollTop 后同一行马上到目标；需要确定性截图时明确 scroll-behavior:auto 或等待。

用 textContent 插入用户文字；innerHTML/v-html 会解析事件和标签，不能拼接未经处理的物品名/聊天文本。新增 markup 的模板不会自动编译。

## 事件

addEventListener(type,fn,boolean 或 {capture,once})；removeEventListener 使用原函数和 capture。所有事件有 type/target/currentTarget/eventPhase/bubbles/cancelable/defaultPrevented/timeStamp、preventDefault/stopPropagation/stopImmediatePropagation。

| 类型 | 字段 |
|---|---|
| MouseEvent | clientX/Y、pageX/Y、offsetX/Y、button/buttons/detail、relatedTarget、shiftKey/ctrlKey/altKey/metaKey |
| WheelEvent | 鼠标字段 + deltaX/Y、deltaMode=0；GUI px |
| KeyboardEvent | key/code/keyCode/repeat、修饰键；key 例 Enter/ArrowLeft/Escape |
| FocusEvent | relatedTarget |
| InputEvent | data/inputType；beforeinput/input/change |
| TransitionEvent / AnimationEvent | propertyName/animationName/elapsedTime/pseudoElement |
| CustomEvent | detail |

事件路径支持捕获/冒泡；inline onclick 有 this/event，返回 false 取消默认。不能直接写 value 引用元素值，应 this.value。输入框有焦点时普通键用于文本；Esc 可被页面取消，但 Shift+Esc/快速多次 Esc 为宿主强制关闭。保留明确关闭入口。

## 模板指令

| 指令 | 合同 |
|---|---|
| `{{ expr }}` | 文本/属性插值；null/undefined 空文本，数组/对象 JSON 文本 |
| :attr / v-bind:attr | false/null/undefined 移除，true 空布尔属性；value/checked 写 live 状态 |
| :class / v-class | 字符串/数组/条件对象，叠加静态类 |
| :style / v-style | 字符串/数组/对象，叠加静态样式 |
| v-if / v-else-if / v-else | 相邻条件分支，隐藏时保留实例状态 |
| v-for | item in items；(item,index)；(value,key,index)；n in 5 为 1–5 |
| :key / v-key | 列表唯一身份；其他元素 key 变化重建，可能重播进场 |
| v-show | v-hidden 控制显隐 |
| @event / v-on:event | 语句/函数/方法，事件是 $event |
| v-model | 输入/textarea/select；lazy 等 change，trim 去空格，number 数值化 |
| v-text / v-html | 替换文本/markup，后者不重新编译模板 |
| v-cloak / v-pre | 初始化隐藏标记 / 跳过编译 |

事件修饰符：prevent/stop/once/self/capture；键 enter/esc/space/tab/delete/up/down/left/right；鼠标 left/middle/right；ctrl/shift/alt/meta。v-if/v-for 放真实元素，不能用 template 包裹渲染；没有 Vue 组件系统。

作用域优先级：循环变量 → oriel.data → 按注册顺序的 state 对象 → 全局。服务端字段可能遮住同名函数；建议业务数据放 `options`/`item` 等命名子对象，本地状态用不冲突的名字。纯表达式不修改状态；`{{ n++ }}` 会导致反复更新并触及上限。

## 动画和 Canvas

el.animate(keyframes,options)：数组关键帧或属性数组，options 为毫秒或 {duration,delay,easing,iterations,direction,fill}；返回动画有 play/pause/cancel/finish/reverse、playState/currentTime/playbackRate、onfinish/oncancel/finished。CSS 属性仍受 [插值类型](css-guide.md) 限制，不能把 filter 离散属性当成平滑动画。

Canvas 2D 只提供 fillStyle/strokeStyle/lineWidth/globalAlpha、save/restore、fillRect/strokeRect/clearRect、getImageData/putImageData/createImageData、drawImage（来源仅另一 Canvas，3/5/9 参数）。像素整数、最近邻，无路径/文字/变换/渐变/图案/WebGL。图像数据 {width,height,data}，RGBA Uint8ClampedArray。

## 限额和错误

宿主限制消息频率、大小、脚本 CPU/内存、节点、Canvas、计时器。默认消息上限 8192 字符，脚本单次预算 1000ms/5000万指令，加载期 10000ms；这些是保护上限，不是页面性能预算。全部配置键和默认/范围见 [安全与配置](../api/security.md#settings)。异常报告脚本来源/行号；页面未崩溃不表示业务请求成功。
