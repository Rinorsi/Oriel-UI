# HTML 与 Minecraft 元素参数

[入口](index.md) · [CSS](css-guide.md) · [脚本](scripting.md) · [容器](server.md)

Java 控制器可显式启用 o-text/o-for/o-key/o-if/o-class:*/o-attr:*/o-on:* 属性，完整参数、默认、错误与运行环境见 [Java 声明式绑定](bindings.md)。它们不是原生 HTML 行为。

本页的默认尺寸是 GUI px，按当前 MC 扩展 UA 样式计算；加载 `ore.css` 或作者样式后可能被覆盖。布尔属性按存在判断：`disabled="false"` 通常仍是禁用，应移除属性。自定义元素写完整结束标签，不使用 JSX 自闭合语法。

## 文档和普通标签

```html
<!doctype html>
<html><head><meta charset="utf-8"><title>物品编辑</title>
  <link rel="stylesheet" href="oriel:ui/ore.css">
  <link rel="stylesheet" href="editor.css">
</head><body><main id="editor"></main></body></html>
```

| 类别 | 标签/属性 | 行为与边界 |
|---|---|---|
| 文档 | html、head、body、title、meta | title 用于窗口标题与朗读；不是完整浏览器 head 平台 |
| 外部资源 | link `rel=stylesheet href`、style、script `src` 或内联 | 相对路径按页面 URL；脚本按文档顺序在解析后运行 |
| 布局文本 | div、span、main、section、article、header、footer、aside、nav、p、h1–h6、br、hr、strong、b、em、i、small、pre、code、sub、sup、ul/ol/li | 元素名不保证完整浏览器语义；外观写 CSS；列表不提供完整 CSS counter 系统 |
| 链接 | a `href` | 资源页导航；网页链接由宿主询问/阻止；不是把远程网页嵌入游戏 |
| 折叠 | details `open` + summary | summary 切换父 details；open 缺省收起 |
| 模板 | template、v-*、:attribute、@event | 属于受限模板运行时，见脚本手册 |
| 不支持的浏览器内容 | svg、table 布局、iframe、video、audio、object/embed | 不要据标签能解析判断可用；用 Grid、PNG、原生模型替代 |

通用属性：`id`、`class`、`style`、`hidden`、`tabindex`、`title`、`title-json`、`title-nowrap`、`data-*`。data-* 本身只存字符串，除本文/组件手册列出的监听者外，不会自动产生行为。

焦点：可交互的 div 应给 `tabindex="0"` 并接 Enter/Space；优先原生 button/input。tabindex=-1 允许程序聚焦而不加入普通 Tab 顺序。朗读使用 aria-label、aria-labelledby、aria-describedby、aria-hidden、aria-live 等，准确范围见 [朗读参考](narration.md)。

## 原版外观类

下列类来自内置样式表（`ua.css`），页面可直接使用，与原版 GUI 一致。按钮、文本框、滑块、复选框和 `<slot>` 不加类就已是原版外观。

| 类 | 效果 |
|---|---|
| `mc-panel` | 浅灰容器面板，带凸起斜边和黑色外框，像箱子界面 |
| `mc-label` | 容器标题文字：深灰、无阴影（如"物品栏"） |
| `mc-inset` | 槽位周围的凹陷斜边 |
| `mc-tooltip` | 提示框：近黑底加紫色边框 |
| `mc-dark` | 菜单列表背后的半透明深色面板 |
| `mc-dim` | 游戏内菜单背后压暗世界的渐变（写在 `html` 上） |
| `mc-center` | 让页面内容在屏幕居中，如原版容器界面（写在 `body` 上；`display:flex` 居中，`min-height:100vh`） |

几行代码就能写出一个原版箱子，见 `common/src/main/resources/assets/oriel/oriel/demo/chest.html`。

## Minecraft 字体里的文字与形状

### 字体与颜色的 Minecraft 扩展

- `font-family: minecraft:default | minecraft:uniform | minecraft:alt | minecraft:illageralt | <任意字体 id>`；`monospace` 即 uniform。
- `text-shadow: minecraft`：游戏自己的文字阴影。
- `-mc-tint`：对图片、精灵、实体和模型乘色；物品不能染色。
- `sprite(ns:path)` 可作背景；聊天颜色可直接用名字：`mc-gold`、`mc-gray` 等。
- 长度单位是 GUI 像素：`1px` 随 GUI 缩放，`1dp` 是一个设备像素。

### 小块文字的排版细节

Oriel 完全按原版方式量字，所以在小盒子里会出现字体的两个习惯：

- 每个字形的步进都包含其后 1 px 的间隙，最后一个也算。一个单字徽标写 `padding: 0 2px`，字形左边留 2 px 背景、右边留 3 px，居中文字会偏左半个像素，和原版按钮一样。要精确居中，右边少补一像素：`padding: 0 1px 0 2px`。
- 默认字号一行高 9 px，字形贴顶：大写字母占前 7 px，后 2 px 留给下伸部分和行距。因此盒子里只有一行大写时，上方没有空隙、下方有 2 px，要把上内边距比下内边距多加 2 px 才平衡。紧凑居中的单字徽标写 `padding: 2px 1px 0 2px`。

### 平滑圆角还是像素锐利

在受支持的 Minecraft 26.x 目标上，圆角、圆角边框和 box-shadow 由着色器绘制，所以曲线在任何 GUI 缩放或旋转下边缘都平滑，模糊是真正的高斯模糊；直角两种情况下都保持像素精确。

想要生硬的像素边缘，给元素写 `shape-rendering: crispEdges`。它会继承，所以 `html { shape-rendering: crispEdges; }` 管整页。`auto`（缺省）和 `geometricPrecision` 允许平滑形状，`optimizeSpeed` 与 `crispEdges` 相同。玩家也可以在设置里用 `client.shapeShader=false` 为所有页面关掉着色器（见 [安全与设置](security.md#设置)）。预览器按着色器的方式绘制形状，也认 `crispEdges`。

## 表单

| 元素 | 字段/缺省 | 使用说明 |
|---|---|---|
| button | type=button/submit/reset；缺省 submit | 普通操作显式写 type=button，避免触发表单提交 |
| input 文本 | type=text，value、placeholder、name、maxlength、readonly、disabled、autofocus | UA 150×20；password 隐藏字符；未知类型按文本处理，不能因此假定 date/file/color 有原生选择器 |
| textarea | 文本子节点为初值；placeholder、maxlength、readonly、disabled | UA 150×60，多行滚动；运行时读写 value |
| checkbox / radio | checked 布尔；value 缺省 on；radio 用同一 name 分组 | 用户变化发 input/change；脚本赋值不等于用户操作 |
| input range | min=0、max=100、step=1；step=any 连续；value | 按范围/步长钳制，未设值取中间；max 不小于 min。业务上限仍由服务器再验。Ore 自制滑块另见组件表 |
| select / option | value、selected、disabled | selectedIndex=-1 表示无匹配；option 文本是显示名；不要假定浏览器多选/搜索下拉能力 |
| progress / meter | value、max，meter 有 min | 缺省 max=1，meter min=0；按比例绘制 |
| label | for=控件id 或包住控件 | 点击转给目标控件 |
| form | submit/reset 事件 | 没有浏览器 HTTP 提交流程；阻止默认行为后发宿主命名消息 |

value/checked 是运行时状态，HTML 属性主要是初始值。不要靠 CSS disabled 类代替业务权限或服务端校验。原生 disabled 与自绘组件样式禁用要分清。

### 文本编辑、中文断行与输入法

文本控件的光标、选区偏移仍为 UTF-16 索引；左右移动、Backspace、Delete 和鼠标命中按扩展字素边界处理（含组合重音、肤色 emoji、ZWJ 家庭与旗帜），落在字素内部的选区端点向前吸附。maxlength 仍以 UTF-16 单位计数。

textarea 在空白与中文允许的边界换行；相同字体、字距、内容宽度和保留空白条件下，中文及中英混排与普通 p 的正常断行一致。避头尾的连续标点和无断点的长单词允许溢出，不强行拆开；p 默认折叠空白，textarea 保留空白与硬换行，因此两者的空白处理不等价。文本控件尚不按 word-break / overflow-wrap 切换断行策略。

预编辑由宿主通过 InputHandler.preedit(text, caret, focusStart, focusEnd) 提供；三个偏移均为预编辑字符串中的 UTF-16 索引，null/空字符串取消显示，已提交字符通过 charTyped 输入。预编辑不改变 value；组合中编辑按键留给宿主输入法，Escape 取消预编辑，失焦取消且保留已提交值和选区。一次组合开始会分隔撤销组。预编辑会滚动到组合光标；textCaretRect() 返回 {x,y,height}（视口 GUI px），计入滚动与 CSS 变换，实际窗口/GUI 缩放由 MC 宿主换算。

以上为引擎回归验证范围。真实中文输入法的候选窗、系统取消/提交事件顺序与 GUI 缩放仍需 Windows 上按 Fabric/NeoForge × 26.1/26.2/26.3 六目标人工验收；自动输入中文不能替代实机。

## 物品、槽位、图片

| 元素 | 参数 | 默认/约束 |
|---|---|---|
| item | id：完整物品 ID；count：数量；components：组件 SNBT；tooltip：布尔 | UA 16×16，count 缺省 1；纯展示，不授予物品、不接管槽位 |
| slot | index：菜单 slots 列表下标 | UA 18×18；只能在绑定的容器 Screen 生效；不是 Inventory 的原始下标 |
| player-head | name 或 uuid，都省略为当前玩家 | UA 显示 8×8；需要 16×16 要显式设 CSS；皮肤解析须游戏检查 |
| sprite | src=命名空间:GUI精灵路径 | 自然尺寸由精灵决定；九宫格/tile 由元数据控制 |
| img | src=相对PNG / ns:textures/...png / sprite:ns:path / canvas:id | 自然尺寸来自资源；不能依赖浏览器 data URL/远程图片加载 |
| canvas | width、height：像素缓冲区 | 缺省 300×150，每边最大 2048；CSS 尺寸与缓冲区尺寸不同；仅支持 [Canvas 子集](scripting.md) |
| mc-text | key + 可选 args，或 json | 翻译键/聊天组件转普通文本与 span；args 为逗号分隔字符串；添加元素和相关属性变化时展开 |

```html
<item id="minecraft:diamond_sword" count="1" tooltip
      components='{"minecraft:enchantments":{"minecraft:sharpness":5}}'></item>
<img class="o-ico" src="oriel:textures/icons/sword.png">
<sprite src="minecraft:widget/button"></sprite>
<mc-text key="item.minecraft.diamond"></mc-text>
```

components 是目标 MC 版本使用的 SNBT；不能把编辑器业务 JSON 或整个序列化 ItemStack 塞入。item 的数量/耐久条、tooltip 由游戏绘制；不支持像普通 PNG 那样任意染色或连续透明，低于半透明阈值会隐藏。slot 未绘制、隐藏、裁出或低透明度时会失去可操作位置。

## 实体和模型

| 元素/参数 | 类型/允许值 | 缺省与前置条件 |
|---|---|---|
| entity type | 实体注册 ID | 创建客户端展示副本，如 minecraft:pig |
| entity player | 布尔属性 | 展示自己 |
| entity id | 当前世界实体 ID | 展示已有实体；type/player/id 选择一种来源，别混用 |
| entity baby | 布尔属性 | 仅支持幼年状态的创建实体 |
| entity variant / color | 目标实体组件所需值 | 如 cat variant=minecraft:black、sheep color=pink；不是所有实体都接受 |
| entity components | SNBT | 实体组件，如 minecraft:wolf/collar；不等于物品组件 |
| entity 装备 | mainhand、offhand、head、chest、legs、feet、body、saddle | 值为物品 ID；由该实体/版本的渲染支持决定 |
| entity walk | 布尔或速度数值，如 0.4 | 不写则不摆腿；只控制展示动画 |
| entity follow-mouse | 布尔 | 头随指针，配 gaze 属性 |
| entity/model rotatable | 布尔 | 拖拽旋转加到 CSS 角度上；拖拽俯仰限约 ±60°，释放可惯性旋转 |
| model block | /setblock 风格 block state | 如 minecraft:oak_stairs[facing=east]；air/流体等无模型者不显示 |
| model item | 物品 ID | 与 block 二选一；支持 count/components |

entity 默认 32×48，model 默认 32×32。width/height 给展示框，object-position 定位；物品/头像用 contain。实体 body 模式默认站在框底中心，不是图片式中心点。

| CSS | 默认 | 类型、单位与作用 |
|---|---|---|
| -mc-yaw / -mc-pitch | 0deg | 角度；正 pitch 从上方看，yaw 绕竖轴 |
| -mc-roll | 0deg | scene 分组绕 z 轴，不是普通 HTML 的 3D 变换 |
| -mc-model-scale | 1 | 非负倍率，乘在自动适配尺寸上 |
| -mc-entity-focus | body | body=全身贴底；center=全身中心缩放；eyes=头肩特写 |
| -mc-gaze-reach | 40px | 非负长度，越大跟随越缓 |
| -mc-gaze-limit | none | yaw [up [down]]，非负角度或 none，省略后项重复前项；none 不连续插值 |
| -mc-tint | 白色 | 乘色；图像、精灵和支持它的模型内容；不保证物品染色 |
| -mc-translate | 0 0 0 | scene 中 x/y/z，方块单位，无 px；省略后项补 0 |
| -mc-scene-center | auto | auto 或完整 x y z 方块坐标；scene 旋转中心 |

```html
<entity player follow-mouse rotatable style="width:96px;height:112px;
  -mc-entity-focus:eyes;-mc-gaze-reach:80px;-mc-gaze-limit:30deg 9deg"></entity>
<model block="minecraft:anvil" style="width:64px;height:64px;-mc-yaw:30deg"></model>
```

eyes 以实体直立轴眼高取景，猪/狐狸等头向前伸的实体旋转时可能偏框。模组实体用 OrielEntities.registerPortraitState 定制肖像 render state，见 [原生渲染扩展](elements.md#实体渲染状态java)。

### 摆放与取景

`object-position` 像摆放图片一样摆放每个 Minecraft 元素：1 到 4 个值，可用关键字、长度和百分比（`right 4px bottom`、`25% 75%`）。物品和头像在默认样式里是 `object-fit: contain`，取盒子短边的正方形；模型也取这个正方形，再乘 `-mc-model-scale`。

`<entity>` 有三种取景：

- `-mc-entity-focus: body`（缺省）：容纳整个实体并留出旋转空间，`object-position` 摆放这块空间。不写 `object-position` 时它站在底边中央（`50% 100%`），不在盒子正中。`-mc-model-scale` 从这个位置放大，所以放大后看到的是脚。
- `-mc-entity-focus: center`：取景同 `body`，但 `-mc-model-scale` 绕实体中心缩放，实体中心保持原位。用于可缩放的查看器。
- `-mc-entity-focus: eyes`：裁成头肩特写。盒子短边对应实体眼高（`Entity.getEyeHeight()`，所以幼体取景更近）的 0.7，`object-position` 指定眼睛位置，缺省在 `50% 40%`。`-mc-model-scale` 绕眼睛缩放，旋转、俯仰和 `follow-mouse` 都不改变眼睛的位置。

```html
<entity id="…" follow-mouse style="width:32px; height:32px; -mc-entity-focus: eyes; object-position: 50% 40%"></entity>
```

眼点取在实体直立轴的眼高处。这适合头在身体正上方的生物（玩家、村民、傀儡、苦力怕）；猪、狐狸的头在该轴前方，侧过身时会跑出取景框。展示橱窗（`/oriel showcase models`）里有一排会跟随指针的肖像。

### 跟随指针

`follow-mouse` 让实体头部像背包界面转玩家那样朝指针转动：指针离眼睛 `d` px 时，左右和上下各转 `40° × atan(d / 40px)`，最多约 63°。身体跟着倾斜一半，头转剩下的部分。两个属性可调：

- `-mc-gaze-reach: <长度>` 替换那个 40px。距离加倍，指针要加倍远才产生同样的转动。
- `-mc-gaze-limit: <左右> [<上> [<下>]]` 限制头向两侧、向上、向下转的最大角度。每个值是角度或 `none`，省略的值重复前一个：`30deg 9deg` 表示左右各 30°、上下各 9°；`30deg 12deg 4deg` 让头能比向下看更高地向上看。身体仍按限制后转动的一半倾斜。

在对话卡片上，指针通常停在回复项上，远低于说话者的头像，用缺省值时说话者会盯着自己的脚。Chronicle 的卡片让头基本保持水平并轻轻转动：

```css
entity.speaker { -mc-gaze-reach: 80px; -mc-gaze-limit: 30deg 9deg; }
```

两者只影响绘制，可以做动画（`transition: -mc-gaze-limit 300ms`）。`none` 不能插值，所以变到或变自 `none` 会立即生效，在 `@keyframes` 里则在中途生效。它们只作用于指针增加的部分：`-mc-yaw`、`-mc-pitch` 和拖动 `rotatable` 实体照常转动，注视在此基础上再转。指针距离：`-mc-entity-focus: eyes` 时从眼点量，`body` 时从盒子往下三分之一处量，与背包界面一致。


## scene 小场景

scene 是展示用小世界，不是服务端方块编辑器。默认 160×120，零角度从东北上方看，自动适配框。

```html
<scene structure="mymod:structures/hut.nbt" rotatable>
  <block x="1" y="0" z="0" state="minecraft:stone"></block>
  <block-group id="roof" y="3..5"></block-group>
  <entity type="minecraft:villager" x="2.5" y="1" z="2.5"></entity>
  <scene-anchor x="1.5" y="2" z="0.5">标签</scene-anchor>
</scene>
```

| 字段 | 规则 |
|---|---|
| structure | assets 下结构 NBT 路径；读方块、状态和方块实体数据，不导入结构实体 |
| block x/y/z、state、nbt | 整数坐标；state 是方块状态，air 擦除；nbt 是方块实体 SNBT；后写 block 覆盖对应位置 |
| block-group x/y/z | 整数或闭区间 2..5；省略某轴表示全轴；文档顺序第一个认领的分组拥有方块；也可包 block |
| scene 内 entity x/y/z | 可以小数，方块单位 |
| scene-anchor x/y/z | HTML 盒左上角对准投影点；底中对齐用 transform:translate(-50%,-100%) |
| notooltips | 禁用方块名称提示 |
| blockhover / blockclick | detail 为 {x,y,z,state,group}；group 是分组元素或 null；离开时 hover detail 为 null；拖拽不触发普通点击 |

坐标 x 东、y 上、z 南；拾取按整方块盒，不按模型细部。分组支持 opacity、位移和角度动画；半透明方块实体有阈值限制。没有内置时间线，分步展示由 CSS 与控制器定时驱动。

### 分组动画与镜头

分组决定什么显示、淡出和移动。分组每帧跟随自己的样式，所以过渡和 `@keyframes` 都能用：

- `display: none`、`visibility: hidden`、`opacity: 0` 隐藏分组，隐藏的分组不能被悬停。
- `opacity` 淡出分组。淡出的分组半透明绘制；其中的方块实体只在不透明度至少一半时显示。
- `-mc-translate: x y z` 按方块单位移动，省略的值为 0：`-mc-translate: 0 -0.5`。
- `-mc-yaw` 绕穿过方块中心的竖轴旋转，与 `<model>` 相同：正值从上往下看是顺时针，如 Minecraft 转实体（南到西）。
- `-mc-pitch` 绕 x 轴（东到西）倾斜，`-mc-roll` 绕 z 轴（北到南）倾斜，也都过中心。正值把顶部向北和向东倾，缺省视角下朝向观察者。
- 三者合用时，先 roll，再 pitch，再按 yaw 旋转，与 Minecraft 摆放实体的顺序一致。

`-mc-translate` 对实体和锚点同样有效。每个分组只网格化一次，方块变化时才重做，所以移动和淡出分组很便宜；从脚本里增删或修改 `<block>`、`<block-group>` 子元素，下一帧就更新场景。分层视图也是分组：每层一个，高于当前层的隐藏。

相机使用 `<model>` 的那些属性。`-mc-yaw` 和 `-mc-pitch` 为 0 时，场景像背包界面看方块那样：从东北上方 30° 看，能看到北面和东面。`-mc-yaw: -90deg` 看到南面和东面，`180deg` 看到南面和西面。场景在任何旋转下都适配盒子，`-mc-model-scale` 缩放，`-mc-scene-center: x y z` 是旋转并保持居中的点（缺省 `auto`，即方块的中心）。这些都可以做动画。

悬停方块时显示方块名称提示（除非场景写了 `notooltips`），并在场景上触发 `blockhover`，`detail` 为 `{x, y, z, state, group}`（`group` 是容纳该方块的 `<block-group>`，没有则为 null）。指针离开方块时再触发一次，`detail` 为 null。没有拖动的点击触发 `blockclick`，`detail` 相同。两者都把方块当作整立方体。

### 分步搭建示例

Oriel 没有时间线。一个自己搭起来的建筑，由分组、CSS 和定时器组成，就像小屋展示（`/oriel showcase scene`）的做法：

```html
<style>
  scene { transition: -mc-yaw 1600ms ease-in-out, -mc-pitch 1600ms ease-in-out; -mc-yaw: -90deg; }
  scene.roof-view { -mc-yaw: -20deg; -mc-pitch: 10deg; }
  block-group.pending { opacity: 0; }
  block-group.drop { animation: drop 900ms ease-out both; }
  @keyframes drop { from { opacity: 0; -mc-translate: 0 5 0; } 45% { opacity: 1; } }
  .caption { transform: translate(-50%, -100%); transition: -mc-translate 1200ms ease-in-out; }
</style>
<scene structure="mymod:structures/hut.nbt">
  <block-group class="pending" x="0..6" y="0" z="0..6"></block-group>
  <block-group class="pending" x="0..6" y="1..3" z="0..6"></block-group>
  <block-group class="pending" x="0..6" y="4..6" z="0..6"></block-group>
  <scene-anchor class="caption" x="0" y="0" z="0"></scene-anchor>
</scene>
<script>
  const steps = [
    {text: '地基', point: '3 1 3'},
    {text: '墙', point: '3 4 6'},
    {text: '屋顶', point: '3 7 3', view: 'roof-view'},
  ];
  const scene = document.querySelector('scene');
  const groups = scene.querySelectorAll('block-group');
  const caption = scene.querySelector('.caption');
  let step = 0;
  function next() {
    if (step === steps.length) return;
    groups[step].className = 'drop';
    caption.textContent = steps[step].text;
    caption.style.setProperty('-mc-translate', steps[step].point);
    scene.className = steps[step].view || '';
    step++;
    setTimeout(next, 2000);
  }
  next();
</script>
```

标题靠自己的 `-mc-translate` 过渡在各点之间滑动，相机靠自己的过渡转动。实体也一样走动：给它的 `-mc-translate` 加过渡，移动时加上 `walk`。

渲染范围：方块模型（同分组内相邻面被剔除）、按状态连接的方块（结构文件保存的栅栏、玻璃板、墙和楼梯已是连接状态；在 `<block>` 上自己写连接）、流体、用各自渲染器绘制但处于静止的方块实体（箱子、床、告示牌、旗帜），以及实体。方块按背包界面的光照满亮绘制，所以世界的时间和维度不会体现。两个分组之间的面会绘制，因此分组移开不会留下空洞。预览器按方块模型加简单明暗绘制，实体只画剪影，不画方块实体。

## 提示框与物品提示

任何元素都可以有 `title`（纯文本，换行符断行）或 `title-json`（聊天组件，用于彩色文字）。指针在元素上停留满它的 `-mc-tooltip-delay`（缺省半秒，除非规则另设）后，在指针处显示原版提示框，像控件提示一样在 170 px 处折行。给元素加 `title-nowrap` 则整行保持完整，只在换行符处断开。从悬停元素向上最近的 title 生效，容器槽位里的物品提示优先于它。

`-mc-tooltip-delay: <时间>` 可继承，所以地图或商店列表设一次，就能像原版槽位物品那样立即显示提示。延迟取显示 title 的那个元素的值，不是指针下元素的值。它只被输入读取，因此改它不会触发任何布局或绘制的重新计算。

```css
.map { -mc-tooltip-delay: 0ms; }           /* 地图上的每个标记 */
.map .legend { -mc-tooltip-delay: 1s; }    /* 图例除外，它要等 */
```

在 `<item tooltip>` 上，物品自己的提示立即显示，适用的 title（从物品向上最近的一个）把它的行加在物品提示之后，同一个框里。物品行和 title 行都不折行。这样商店行就有了原版商人的布局：物品、价格、备注。

```html
<div class="row" title-json='{"text":"","extra":[{"text":"6 个绿宝石购买","color":"green"},
     {"text":"\n铁走了很远才到这里","color":"gray","italic":true}]}'>
  <item id="minecraft:iron_sword" tooltip></item> 铁剑
</div>
```

这是缺省行为，因为放了物品的行上的 title 通常就是在描述那个物品。只想显示物品自己的提示，给物品写空 title：`<item id="minecraft:iron_sword" tooltip title="">`。行内其他位置，title 在延迟后单独显示，除非行上有 `title-nowrap` 否则折行。在 NeoForge 上，物品的堆叠会像任何物品提示一样交给 NeoForge 的提示事件。

## 实体渲染状态（Java）

适用于全部受支持的 Minecraft 26.x 目标。

Oriel 按你的渲染器生成的渲染状态来绘制实体。如果你的渲染器为世界添加了东西（对话气泡、标签、特效），请注册一个专为界面生成状态的函数：

```java
// 客户端初始化：
OrielEntities.registerPortraitState(MyEntities.AUTOMATON.get(), AutomatonRenderer::portraitState);
```

```java
public static <T extends Entity> void registerPortraitState(EntityType<T> type, PortraitState<? super T> state)

@FunctionalInterface
public interface PortraitState<T extends Entity> {   // OrielEntities.PortraitState
    @Nullable EntityRenderState create(T entity, float partialTick);
}
```

函数拿到实体和 partial tick，对屏幕上每个该类型的 `<entity>` 每帧运行一次。返回 null 则当帧退回渲染器自己的状态。对同一类型再次注册会替换原函数。方法引用或 lambda 都可以。

随后 Oriel 会像处理自己的状态一样，在你的状态上设置这些字段：

| 字段 | 设为 |
|---|---|
| `shadowPieces` | 清空 |
| `outlineColor`、`nameTag`、`scoreText`、`leashStates`、`passengerOffset` | 无 |
| `lightCoords` | 满亮 |
| `ageInTicks` | 时钟（用于页面创建的实体，即 `type="…"`） |
| `bodyRot` | 来自 `-mc-yaw`、拖动和 `follow-mouse` 的倾斜 |
| `walkAnimationPos`、`walkAnimationSpeed` | 来自 `walk` 属性（不写则静止） |
| `scale` | 1，同时 `boundingBoxWidth`、`boundingBoxHeight`、`eyeHeight` 除以原缩放 |
| `yRot`、`xRot`（头部） | `follow-mouse` 时朝向指针；否则保持你的状态原值 |

从 `bodyRot` 往下的字段只在生物实体的状态上设置。没有注册函数时，除非 `follow-mouse` 转动，头部朝正前方。身体取景按你返回的状态来量，所以它没包含的部分在盒子里不占空间。


## 提示与验收

浏览器用同尺寸 PNG/色块替身并标记占位。预览器也不能证明 3D 正确。真实皮肤、装备、附魔光、槽位、实体朝向、裁剪深度与提示均须进游戏检查。
