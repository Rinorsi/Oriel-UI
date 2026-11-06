# 朗读（Narrator）

[入口](index.md) · [元素](elements.md) · [脚本](scripting.md)

Minecraft 的朗读器会从页面里读什么。

开启 Minecraft 的朗读器（Ctrl+B）后，Oriel 界面和原版界面一样朗读。界面打开时，朗读器先读界面标题。之后在按键或按钮（200 ms）或指针停住（750 ms）后，读获得焦点的元素，没有的话读指针下的元素，用的是原版控件的措辞："Reply 1: About the letter button. Left click to activate"。它只读上次开口之后发生变化的内容。

标题是页面的 `<title>`。脚本设置 `document.title` 会改变它，`Screen.getTitle()` 也返回它。没有标题的页面叫 "Oriel"（容器界面保留它菜单的标题）。同一个界面里指向另一个页面的链接，读新页面的标题。

指针下的元素是控件、链接、槽位、tab 停靠点或 `<item tooltip>`，或者它有 `title`、`title-json` 或 `aria-label` 时才会被读。指向这类元素内部的文字，读的是该元素。普通文字和装饰不读，写了空 `title` 的元素之后的内容也不读。

元素叫什么，即它的名称，取下面第一个有内容的：

1. `aria-label`；
2. `aria-labelledby` 指向的那些元素的文字（id，用空格分隔）；
3. 图片的 `alt`、按钮型 input 的 `value`、`<item>` 或 `<slot>` 里的物品、控件的 `<label>`；
4. 它自己的文字内容，截到约 100 个字符（文本框、图片和槽位不用）；
5. 它自己的 `title`，或它的 `title-json` 的文字；
6. 文本框的 `placeholder`。

名称之后，朗读器读 `aria-describedby` 指向的元素的文字，没有的话读适用于该元素、且不是名称的那个 title。带 title 的按钮先读标签，再读它的提示，和原版读控件提示的方式一样。`aria-hidden="true"` 里面的任何东西都不会被读。

| 元素 | 读作 |
|---|---|
| `<button>`、`<summary>`、按钮型 input、`role="button"` | "Done button" |
| `<a href>`、`role="link"` | "Map link" |
| `<input type=checkbox>`、带 `aria-checked` 的 `role="checkbox"` 或 `"switch"` | "Checkbox: Show hints: ON" |
| `<input type=radio>`、带 `aria-checked` 的 `role="radio"` | "Radio button: Easy: OFF" |
| `<input type=range>`、带 `aria-valuetext` 或 `aria-valuenow` 的 `role="slider"` | "Volume: 40 slider" |
| 文本输入、`<textarea>`、`role="textbox"` | "Name edit box: Steve"（密码的文字不读） |
| `<select>` | "Difficulty: Hard button" |
| `role="tab"` | "Quests tab. Selected tab 2 out of 3"，按原版的顺序（先读 title，再读位置）：它在所属 `role="tablist"`（或其父元素）里的位置，或 `aria-posinset` 和 `aria-setsize` |
| `<item>`、`<slot>` | "Item: Iron Sword"；有标签的槽位读作 "Fuel. Item: Coal"。没有任何东西标注的空槽位不读。 |
| 其他 | 它的名称 |

控件会像原版控件那样附上使用方法（"Left click to activate"、"Press Enter to activate"、"Drag the slider to change its value"）；复选框获得焦点时说 "Press Space to check"，因为空格切换它。被禁用的控件不说使用方法。页面有多个 tab 停靠点时，朗读器会说该元素在其中的位置（"Screen element 2 out of 5"）。

文字按排版后的样子朗读：空白被折叠，隐藏的文字被略去（`aria-hidden`、`display: none`、`visibility: hidden`、脚本），图片读作它的 `alt`，物品读作它的名称。每个块（块、flex 或 grid 盒子，或以 `<br>` 结尾的一行）是单独的一句，所以说话人的名字在他说的话上面时，读作 "说话人. 他说的话"。

## 实时区域（Live regions）

带 `aria-live="polite"`、`role="status"` 或 `role="log"` 的元素，在文字变化时朗读它，排在朗读器正在说的内容之后。带 `aria-live="assertive"` 或 `role="alert"` 的会打断朗读器；同一帧里有多个播报时，第一个插进去，其余跟在后面，assertive 的排在前面。`aria-live="off"` 让某个 role 静音。log 只读它新增的内容：它的新子元素，所以每行是一个元素、里面有说话人和他说的话的对话，每新增一行就读作 "说话人. 他说的话"。嵌套在另一个区域里的区域自己说话，并从外层的文字里略去。同一帧里发生的变化只读一次，读的是它们最终的文字。

与浏览器不同，页面打开时也会在标题之后读它的区域（log 读最后一条），之后加入的区域同样如此。所以对话打开时会先读最后说的一句。

```html
<head>
  <title>Emperor Cualius</title>
  <style>.speaker { display: block }</style>
</head>
<body>
  <div class="log" role="log">
    <p class="line"><b class="speaker">Emperor Cualius</b>Greetings, stranger! Have you done what I asked?</p>
  </div>
  <button aria-label="Reply 1: About the letter">1 About “Word to Candacona”…</button>
  <div class="flourish" aria-hidden="true">~ ~ ~</div>
</body>
```

界面打开时朗读器说 "Emperor Cualius"，然后说 "Emperor Cualius. Greetings, stranger! Have you done what I asked?"。指针停在回复项上时，它说 "Reply 1: About the letter button. Left click to activate"。当脚本把 `<p class="line"><b class="speaker">Emperor Cualius</b>Then take it to Candacona, and quickly.</p>` 追加进 log 时，它说 "Emperor Cualius. Then take it to Candacona, and quickly."。装饰性的 flourish 永远不读。

HUD 叠加层也读它的实时区域，也在 HUD 层里读。在它之上可交互的界面里，指针停留满 750 ms 后它会自己读指针下的元素，因为它下面的界面对叠加层一无所知。它永远不读获得焦点的元素：按键留给界面。预览器用 `--narrate` 打印朗读器会拿到的内容（见 `preview/README.md`）。

脚本一次显示几个字的文字，就会一次几个字地被读；整行一次放进去，用 CSS 逐步显示。物品变化的槽位在指针下次停住时会被重读，但包含它的实时区域不会播报这个变化。
