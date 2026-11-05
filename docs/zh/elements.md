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

焦点：可交互的 div 应给 `tabindex="0"` 并接 Enter/Space；优先原生 button/input。tabindex=-1 允许程序聚焦而不加入普通 Tab 顺序。朗读使用 aria-label、aria-labelledby、aria-describedby、aria-hidden、aria-live 等，准确范围见 [朗读参考](../api/narration.md)。

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

eyes 以实体直立轴眼高取景，猪/狐狸等头向前伸的实体旋转时可能偏框。模组实体用 OrielEntities.registerPortraitState 定制肖像 render state，见 [原生渲染扩展](../api/elements.md#entity-render-states)。

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

## 提示与验收

title 缺省等 500ms，继承属性 -mc-tooltip-delay 可改；title-json 是聊天组件，title-nowrap 禁止折行。普通长提示约 170px 折行。item tooltip 的原版提示立即显示，祖先 title 追加其后；空 title 可阻止继承。

浏览器用同尺寸 PNG/色块替身并标记占位。预览器也不能证明 3D 正确。真实皮肤、装备、附魔光、槽位、实体朝向、裁剪深度与提示均须进游戏检查。
