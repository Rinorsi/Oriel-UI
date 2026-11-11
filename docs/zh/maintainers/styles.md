# 样式诊断：选择器、层叠与失效

## 选择器匹配与诊断

[CSS 合同](../css-guide.md#选择器实现与诊断开关) · [验收](../validation.md) · [性能](../performance.md)

默认为 `-Doriel.selector=new`（old 回到旧匹配）；`new` 启用编译匹配和属性失效计划，`both` 比较两套匹配/索引并返回旧结果。both 不执行新模式的增量重算，增量结果必须另用 old/new 文档逐字段比较。三种模式都支持 `:target`。修改默认实现前须完成固定输入的功能与性能检查。

## 显式查询（开发工具）

构建 `:preview:installDist` 后：

```bash
preview page.html --why '.card.on[data-ready]' '#card' --frames 60 --size 640x360
```

`--why` 后是两个独立参数：要解释的 CSS 选择器和定位元素的查询。默认加载后推进 1 帧，`--frames` 可调整；每帧 16ms。只运行无头预览，不开窗口/游戏；不能与 `--canvas-test`、`--actions`、`--snapshot` 混用。定位查询按 DOM 顺序返回后代，不包含查询根本身；无元素或非法选择器退出 2。解释中的 `:scope` 与样式规则一致，指向文档根，不是 `Element.matches` 的当前元素。

MCP `oriel_inspect`：page、element 必填；selector 与 property 必须且只能指定一个。property 用法见 [属性解释](styles.md)。可选 frames 默认为 1，范围 1–600。例：page=path/page.html，selector=.card.on，element=#card，frames=60。返回 passed、exitCode、explanations 和原始 output；加载失败、预算超限、无元素均不算通过。工具不创建批准记录，不接入游戏。

Java：`document.styleEngine().explainSelectors(element, selector)` 返回 `Map<String,Object>`；元素必须连接在该文档中。调用会先刷新待处理样式，再以编译匹配器独立探测。

## JSONL 字段

CLI 标准输出每个元素一行 JSON，资源提示和人可读概要在标准错误。页面自行打印的文本不属于结构化记录；MCP 仅收集 kind=selector-explanation 的记录。

| 字段 | 含义 |
|---|---|
| page / element / query | 文档 URL、元素标签、待解释选择器 |
| probeBackend | 固定为 compiled；不是声明当前页面已切换默认 |
| queryResults | 选择器列表各项的 matched、specificity、pseudoElement、trace |
| trace | 实际访问的 compound 序号（从 0 起）、谓词、元素和 matched；包含嵌套函数。false 是该路径失败，不一定表示整条规则失败 |
| traceDropped | 每条规则最多保留 4096 项；超出条目显式计数，匹配本身继续受工作预算约束 |
| candidateCountOld / candidateCountNew | 当前活动规则在旧/新必要条件桶中的候选数量，包含 UA 和伪元素；同一规则多选择器分别计数 |
| rules | 当前 media/import 展平后的规则、候选状态、匹配结果、sourceOrder 和来源。未激活 media 的规则不在此列表 |
| declarationWinners | 按伪元素、长属性或自定义属性列出的声明赢家，保留 importance、origin、specificity、sourceOrder、url、value；inline 不提供虚构的 specificity/sourceOrder |
| workUsed / workLimit | compound/simple 求值次数；规则探测各自采用原工作上限，顶层 workUsed 是规则探测之和，不含 queryResults |
| elapsedNs / elapsedHistogramNs | 带轨迹探测耗时及五档计数；不是运行时性能基准 |

声明赢家在 var 解析、继承、动画之前；未声明的初始值/继承值不伪装为某条规则。源码暂只有 URL 和 selector/compound 定位，没有虚构行号。未匹配记录中的关系缺失说明与 false 谓词需结合看，不把分支探索的失败当作整条规则失败。

诊断没有自动开启的全局钩子。普通匹配编译得到无 trace 包装的程序，不计时、不生成 JSON；显式查询另编译带轨迹的临时程序，不替换生产缓存。
仅含基础谓词的 compound 直接调用谓词；纯基础谓词在剩余预算足够时批量记入实际求值次数，接近上限时恢复逐项检查。函数程序仍逐项计数，预算上限、短路次序及超限诊断不变。

## new 模式的增量边界

- 属性候选桶从元素实际存在的属性名反查，不逐个探测样式表中不存在于该元素的属性；最终仍执行完整选择器匹配。
- 单纯 class/id/属性的主体谓词，只重新匹配受影响元素；单 compound 的 `:is/:where/:not` 同样适用。
- `:has(> .on)` 反向标记父元素，`:has(.on)` 标记祖先，`:has(+ .on)` 标记前一个元素兄弟，`:has(~ .on)` 标记前方元素兄弟。原元素的其他主体依赖仍单独处理，移除属性同样失效。
- `:nth-child(of S)` 的简单属性筛选影响同父兄弟；排名缓存只活在一个匹配上下文，下一次 DOM/交互重算重新建立，并保留 hover/focus 依赖。
- 多段相对 has、复杂祖先/兄弟组合、复杂过滤器、继承 disabled 状态，以及结构/文本/表单状态变化，保留全树重新匹配。样式表/媒体变化重建索引。仍遍历元素更新继承，优化的是候选匹配与重复过滤，不承诺只遍历某个子树。
- 反向依赖目前按属性名合并，不按 class token 或 id 值分别建表。例如任一活动规则对 class 有非局部依赖时，class 变化仍走全量回退；不能把简单选择器用例的局部收益推广到任意样式表。
- Shadow DOM、`:host`、`@scope`、`@layer`、容器查询不因此获得支持。这里不修改层叠层或作用域解析。

## :target

`:target` 匹配文档 URL 中 # 后的非空片段对应的 id。百分号编码只接受 ASCII 十六进制数字并按 UTF-8 解码，+ 保留为加号；错误或不完整的 % 序列按字面字符保留。无片段不匹配，改 id 后重新计算。例如 `test:page.html#panel` 对应 `<section id=panel>`。

匹配依据 Document 创建时的 URL，不增加 history/location/hashchange API，不保证宿主的同页锚点导航会更新现有 Document URL。解析/引擎回归与真实游戏导航必须分别验证。

## 实验层叠、依赖失效与属性解释

[CSS 合同](../css-guide.md) · [选择器诊断](styles.md) · [性能测量](benchmark.md)

层叠默认使用 new；真实层序仍须显式开启。不能由开关存在推断性能门禁、真机、GPU 或 IME 已通过。以下都是 JVM 系统属性，必须在创建文档之前设置；Java 库更新需要重启宿主。

| 开关 | 默认 | 用途 |
|---|---|---|
| `oriel.cascade=old/new/both` | `new` | old 保留原层叠与遍历；new 使用单次声明排名和依赖路径；both 在新路径之后独立做旧全树重算，逐字段比较 |
| `oriel.cascade.audit=/external/computed-%p.jsonl` | 关闭 | both 退出时写计算次数与字段次数；`%p` 为进程号，避免多测试进程覆盖 |
| `oriel.css.layers=true` | `false` | 仅实验 new/both 解析真实层序；旧模式仍压平。扩展页不要用 both 和旧压平结果要求零差 |

`oriel.selector` 与 `oriel.cascade` 独立。非法 cascade 模式抛出错误；普通 new/old 不加载字段反射或生成诊断 JSON。both 比较全部公开非静态 computed 字段，包括变量表、伪元素和计算过的动画 used 值；全树复算可发现新路径跳过节点导致的遗漏。它共用经过回归的值解析、单位、继承和变量求值代码，**不是两套独立 CSS 规范实现**。

## 局部变更的处理

属性/class/id、hover/active/focus 的读取依赖在建规则索引时编成关系路径。多次修改先合并；目标节点及其祖先路径进入一次遍历。支持后代、子代、兄弟、逻辑选择器、相对 `:has()`、`nth-child(of ...)` 的保守反向定位；继承值、变量表或 `display:contents` 的容器变化继续向下传播。

树结构/文本空状态变化、表单内部状态、未知宿主失效、样式表/媒体/语言/根字号变化仍走保守完整路径。disabled/enabled/checked 的复杂属性依赖也完整回退；尚未按 class token 的旧新差集缩小到单个 token。不能称任意变更都只触碰一个节点，也不能把离线结果称为游戏 FPS。

## 真实层序（显式启用）

```css
@layer base, theme;
@layer theme { .card { color: blue } }
@layer base { #detail { color: red } }
```

运行参数：`-Doriel.cascade=new -Doriel.css.layers=true`。`id=detail class=card` 为蓝色：普通声明先比层序，再比 specificity/源码顺序；未分层普通声明优先。important 反转层序，分层 important 高于未分层 important；inline important 仍高于作者层，UA important 仍保留最高来源优先级。

支持顺序声明、具名/匿名块、点分层名、嵌套和跨样式表重开。子层保持在父层区间内，父层直接声明在普通顺序中位于子层之后。重复名字不重新排列；同名 keyframes 也按层序选定义。格式错误报 `invalid-layer`（文件、行列）并跳过对应块。

限制：没有 `@import ... layer(...)`、`@scope`、Shadow DOM 或容器查询；`revert-layer`/`revert` 仍沿原合同按 unset 近似，不可用于退回前一层。默认压平页不受实验层序影响。

## 为什么这个属性是这个值

```bash
preview page.html --why-property width '#detail' --frames 60
preview page.html --why-property '::before/color' '#detail'
```

MCP：`oriel_inspect({page, element:'#detail', property:'width', frames:60})`。`selector` 与 `property` 必须且只能给一个；frames 默认 1，范围 1–600。旧 selector 调用保持兼容。

输出为每元素一行 JSON：`kind=cascade-explanation`、page/element/pseudo/property、schema、declarations、baseValue/usedValue、inheritedFrom、variableExpansion。声明包含文件/值所在行、来源、important、层序、specificity、源码顺序、赢家和淘汰原因；变量展开给引用名、解析值与查到的声明来源（最多32层、256项）。未设置继承声明时给直接继承父元素；不是完整动画时间线或所有布局 used length 的解释。无生成内容的伪元素返回空值。

只支持长属性和 `--自定义属性`；缩写、未知属性、未知伪元素、找不到元素或不属于当前文档的元素明确报错。诊断在请求时构建，不为每帧保存追踪；诊断耗时不可用作生产性能数据。

`variableResolution` 区分成功替换、缺失变量、替换后类型无效和自定义属性失效；替换成 inherit/unset 或替换后无效时，`inheritedFrom` 按实际回退规则给出来源。
变量链在声明所属的元素或伪元素作用域中展开，`declaredPseudo` 标明伪元素来源；子元素覆盖同名变量不会改写祖先已计算好的变量链。

## 属性 schema 与保留缺口

`PropertySchema.properties()` / `named(name)` 从实际长属性注册表读取名称、继承性、初值、插值类型、影响布局/绘制。包括已安装扩展和列表组件；未知名称返回 null。初值可能是 currentColor 等上下文值，不承诺固定像素或固定颜色。affectsPaint 为保守失效范围，动画组件依实际插值类型解释。原有 `PropertySchema.register(Phase)` 扩展 SPI 保持兼容。

本阶段 schema 是现有属性注册的只读描述，未提供任意运行时类型注册，**`@property` 仍不支持并明确提示**；也未改变自定义属性动画的离散行为。`color-mix()` 已支持，不重复实现；`light-dark()` 依赖尚无的 color-scheme 合同，继续拒绝。`all`、真实 revert/revert-layer、类型化变量及其插值留作已知缺口，未加入解析占位。
