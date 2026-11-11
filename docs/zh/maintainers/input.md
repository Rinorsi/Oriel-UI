# 输入后端、诊断与导航

[文档入口](../index.md) · [文本控件](../elements.md) · [检查工具](../validation.md)

本页描述输入实验后端与新增接口。现有鼠标、滚轮、Tab、文本编辑、UTF-16 选区、IME 预编辑与候选窗矩形合同继续适用。不提供富文本 DOM 编辑，也没有系统手柄驱动或原生拖放认证。

## 后端开关

JVM 属性 `-Doriel.inputBackend=old|new|both` 在文档、测量器、编辑器创建时读取，**缺省 new**（旧实现用 `old` 回退）。非法值抛出 IllegalArgumentException。同一文档生命周期内不要修改属性。游戏需要重启才能更换 Java 实现；保存 HTML 不会替换后端。

- old 保留原事件路径、焦点排序、字素和线性光标查询、文本宽度缓存。
- new 使用可重入事件路径缓冲、稳定焦点排序、ASCII 字素快路径、行和光标二分索引、双预算测量缓存、大块布局的保守命中网格。IME 事务、文本撤销和控件默认动作仍复用既有实现；不能把此阶段写成全部输入代码已独立重写。
- both 对无副作用的路径、焦点顺序、命中、测量、行与光标查询对拍，差异抛错。监听器与默认动作只执行一次，不重复提交表单。完整事件结果由独立文档录制回放测试比较；该开关本身不是任意页面完整双文档回放器。

命中网格仅用于至少 128 个可命中块的纯 display:block 树；有行内文字、替换元素、定位、滚动、变换、圆角、透明分组等内容时，沿用绘制遍历。最多保留 32768 个单元引用，每帧和几何失效后重建；复杂页面不承诺命中加速。Document.hitTest 仍是绘制层公共查询，输入后端用 InputHandler.hit。

新测量缓存最多保留 8192 个字符串、262144 个 UTF-16 单元；单串超预算直接测量。字形推进另限 8192 项。`FontMetrics.generation(): long` 缺省 0，可变字体宿主应在字宽变化时递增，缓存和文本控件布局随之失效。此接口不等于全部 Minecraft 字体重载路径已接入；宿主保持 0 时，只保证相同字体资源生命周期内有效。不新增整形、双向文字或 HarfBuzz 支持。

## 诊断

Java `document.input().setTrace(Consumer<String>)` 开启 JSONL 流；传 null 关闭，缺省关闭，不保存历史。关闭时不构造 JSON 或目标链。不记录键值、输入内容、密码、预编辑原文；元素用标签和 id 标识。消费端应自行限制保存量，写到工作区，不要在游戏回调同步写磁盘。

| kind | 字段 | 含义 |
|---|---|---|
| input-event | type, stage, target, currentTarget, phase, defaultPrevented, propagationStopped | 捕获、目标、冒泡节点和取消状态；complete 表示分派结束，default-performed/default-prevented/no-default/unhandled/no-scroll-room 表示默认动作入口结果，不保证业务成功 |
| composition | target, state, utf16Length | start/update/end；end 只表示预编辑结束，提交或取消须结合之后的 input，宿主 API 无独立结束原因 |
| why-no-click | element, hit, x, y, reason | 命中与几何原因，不执行点击或滚动 |
| focus | element, focused, focusWithin, focusVisible, source, inert | 焦点来源和匹配解释 |

`whyNoClick(Element, x, y)` 返回 JSON 字符串，坐标是视口 GUI px。reason 包括 disconnected/inert/disabled/not-rendered/visibility/pointer-events/outside-visible-bounds/outside-hit-shape/occluded/receives-pointer。receives-pointer 不保证监听器不会取消 click。实际目标来自绘制命中，矩形解释不代替形状或裁剪算法。

`explainFocus(Element)` 返回 JSON 字符串，source 为 initial/pointer/keyboard/programmatic/navigation-action。focusWithin 表示焦点在自身或后代；focusVisible 仍遵循原键盘导航状态。程序化 focus 保留旧可见焦点规则，source 不伪装为键盘输入。

预览器 `--actions` 和 MCP `oriel_snapshot.actions` 可使用：

```text
trace-input on
click #name
preedit zhongwen
preedit
type 中文
focus-info #name
why-no-click #submit
trace-input off
```

结果在预览器标准输出或 MCP 回执 output 中。why-no-click 使用首个匹配元素 border box 中心，不触发滚动；找不到元素报错。preedit 无参数结束组合，带文本更新组合。trace-input 只接受 on/off。MCP 仍限制最多 100 条、每条小于 2000 字符、单行；既有验收权限不改变。

## 可选输入能力

HTML 布尔属性 `inert` 存在即阻止自身与后代的宿主指针、键盘焦点、文本输入、Element.click() 入口，Tab 跳过子树。给焦点祖先添加 inert 会失焦并结束组合。缺省不生效；inert="false" 仍启用，应移除属性恢复。内容继续布局绘制，脚本 dispatchEvent 仍能分派合成事件。当前实现阻止命中区域输入，不承诺穿透至下方兄弟；几何 hitTest 不等于可点击性。它不是脚本安全隔离机制。

```html
<section inert><input value="暂不可编辑"><button>确认</button></section>
```

空间导航缺省关闭。Java 宿主可配置 `input.setNavigationKeys(Map.of("ArrowRight", "right", "ArrowLeft", "left"))`，方向允许 left/right/up/down；空 Map 关闭，非法方向报错。Tab 和文本、select、range、radio 自身按键优先；Ctrl/Alt/Meta 不触发导航。候选沿用 Tab 可达集合，在目标方向选最近中心，横向偏离距离平方权重 4，等分按 Tab 顺序，不循环。

手柄适配层可将动作映射到 `input.navigate("down")`，返回是否移动；没有焦点时从首个 Tab 元素开始。这里只提供动作入口，设备绑定、死区、遮挡策略与 Minecraft 按键冲突需调用方另验。

beforeinput 可取消；取消或监听器替换 value 时不应用旧编辑计划，不发对应 input。typing、删除、剪切粘贴、撤销重做沿用原 inputType。预编辑不改已提交值，不发送伪造 input，真正提交经宿主 charTyped 到达。没有 HTML DragEvent/DataTransfer 协议；已有文本拖选、滑块、滚动条捕获继续可用。跨页面、物品槽或系统文件拖放另行设计。

验证等级：接口与限制对应源码和引擎回归。六目标真实 IME、系统候选窗、手柄、游戏 GPU 与输入延迟仍需实机记录，不由离线测试证明。

MCP 可用 oriel_rules 的 topic=input 读取本页；坐标必须是有限数值，Java whyNoClick 查询会先刷新布局。

字体宿主仍须通过既有资源重载流程触发布局与重绘；generation 只保护缓存，不会自行重排所有静态段落。命中网格数据每帧失效，合成基准的稳态重复查询收益不能等同于每帧一次查询或实机 FPS。

与基础布局接缝：TextMeasure.generation() 同时反映显式 invalidate/fontsChanged 和 FontMetrics.generation() 变化，不等于宿主返回值。显式重载清除字符串与字形缓存；fontsChanged 可替换服务但保留 TextMeasure 身份。静态段落仍需宿主请求重排。
