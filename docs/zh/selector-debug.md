# 选择器诊断与实验匹配器

[CSS 合同](css-guide.md#选择器实现与迁移开关) · [验收](validation.md) · [性能](performance.md)

默认仍为 `-Doriel.selector=old`；`new` 启用编译匹配和属性失效计划，`both` 比较两套匹配/索引并返回旧结果。both 不执行新模式的增量重算，增量结果必须另用 old/new 文档逐字段比较。三种模式都支持 `:target`。尚未满足整页性能门禁时不可切默认。

## 显式查询（开发工具）

构建 `:preview:installDist` 后：

```bash
preview page.html --why '.card.on[data-ready]' '#card' --frames 60 --size 640x360
```

`--why` 后是两个独立参数：要解释的 CSS 选择器和定位元素的查询。默认加载后推进 1 帧，`--frames` 可调整；每帧 16ms。只运行无头预览，不开窗口/游戏；不能与 `--canvas-test`、`--actions`、`--snapshot` 混用。定位查询按 DOM 顺序返回后代，不包含查询根本身；无元素或非法选择器退出 2。解释中的 `:scope` 与样式规则一致，指向文档根，不是 `Element.matches` 的当前元素。

MCP `oriel_inspect`：page、selector、element 三个字符串必填；可选 frames 默认为 1，范围 1–600。例：page=path/page.html，selector=.card.on，element=#card，frames=60。返回 passed、exitCode、explanations 和原始 output；加载失败、预算超限、无元素均不算通过。工具不创建批准记录，不接入游戏。

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

## new 模式的增量边界

- 单纯 class/id/属性的主体谓词，只重新匹配受影响元素；单 compound 的 `:is/:where/:not` 同样适用。
- `:has(> .on)` 反向标记父元素，`:has(.on)` 标记祖先，`:has(+ .on)` 标记前一个元素兄弟，`:has(~ .on)` 标记前方元素兄弟。原元素的其他主体依赖仍单独处理，移除属性同样失效。
- `:nth-child(of S)` 的简单属性筛选影响同父兄弟；排名缓存只活在一个匹配上下文，下一次 DOM/交互重算重新建立，并保留 hover/focus 依赖。
- 多段相对 has、复杂祖先/兄弟组合、复杂过滤器、继承 disabled 状态，以及结构/文本/表单状态变化，保留全树重新匹配。样式表/媒体变化重建索引。仍遍历元素更新继承，优化的是候选匹配与重复过滤，不承诺只遍历某个子树。
- 反向依赖目前按属性名合并，不按 class token 或 id 值分别建表。例如任一活动规则对 class 有非局部依赖时，class 变化仍走全量回退；不能把简单选择器用例的局部收益推广到任意样式表。
- Shadow DOM、`:host`、`@scope`、`@layer`、容器查询不因此获得支持。这里不修改层叠层或作用域解析。

## :target

`:target` 匹配文档 URL 中 # 后的非空片段对应的 id。百分号编码只接受 ASCII 十六进制数字并按 UTF-8 解码，+ 保留为加号；错误或不完整的 % 序列按字面字符保留。无片段不匹配，改 id 后重新计算。例如 `test:page.html#panel` 对应 `<section id=panel>`。

匹配依据 Document 创建时的 URL，不增加 history/location/hashchange API，不保证宿主的同页锚点导航会更新现有 Document URL。解析/引擎回归与真实游戏导航必须分别验证。
