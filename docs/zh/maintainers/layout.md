# 布局诊断：块流、Flex 与 Grid

## 块流与尺寸诊断

[入口](../index.md) · [验收工具](../validation.md) · [MCP](../mcp.md)

块流后端缺省 new；Flex/Grid 缺省 old，具体选择见各节。new 目前只选择新的块流游标与溢出汇总实现；行内排版、盒树构建、定位和 Flex/Grid 共用既有实现。对照和诊断开关不表示完整布局引擎已经替换，也不替代性能与实机验收。页面语法不变。

## 后端选择

创建 Document 前设置 JVM 属性 `-Doriel.layout.block=old|new|both`，缺省 new（`old` 回到旧块流），非法值抛 IllegalArgumentException。不在运行中改变已创建文档的模式。Gradle 的 engine/preview 测试与 preview:checkUi 可用 `-PblockLayoutBackend=both` 传递该值。

- old：原有块流与溢出路径。
- new：块流游标与新的溢出路由，其余算法共用。
- both：先 old 后 new，逐盒比较最终矩形、边距/边框/内边距、滚动范围、向祖先贡献的溢出、基线、行片段、视口与可见矩形。差异报页面 URL、元素、盒 ID、字段和值；旧路径钳制一次滚动位置，新路径只比较范围。会重复测量替换元素自然尺寸，仅用于确定性开发宿主。

对拍使用精确浮点文本，包括 NaN/无穷的字符串表示，不放宽夹具容差。可见矩形是既有 Coordinates 的包围盒裁剪结果，不是精确圆角/旋转命中区域，也不证明实际命中次序相同。两次都调用既有算法的部分，不算独立实现对拍。

## why-size 与几何 JSON

加载前加 `-Doriel.layoutTrace=true`，预览器已有的 `--boxes` 会同时输出人读矩形与 JSON 行：

    java -Doriel.layout.block=both -Doriel.layoutTrace=true -Doriel.layoutGeometry=true -cp preview/build/install/preview/lib/* dev.oriel.preview.Preview /external/page.html --frames 1 --snapshot /external/page.png --boxes body

路径按工作区规则选择。oriel.layoutGeometry 缺省 false；启用时在 --boxes 输出后附整棵盒树，可能很大。每条 JSON 含 schemaVersion:1、kind（why-size 或 layout-geometry）、page 和 data。未修改原有 CSS 诊断合同。
最后另发 layout-complete 记录，data.whySizeRecords 与 data.geometryRecords 为输出数量。MCP 核对完整记录数；截断丢失前部记录或末尾完成记录均不能通过。

why-size 记录最后一次实际布局输入与中间结果：父/自身格式化上下文、父分配宽度、强制高度及确定性、包含块尺寸、百分比高度基准、内容测量高度、min/max 高度和使用高度，以及相关计算后 CSS 值。不从最终矩形猜测层叠胜者；Flex/Grid 具体分配轨迹未记录。普通 inline 元素没有独立尺寸约束，返回明确错误；应检查所属行容器与行片段。
成功记录的 boxId 与同一文档几何快照的 principal 盒 ID 相同，可区分标签和类名相同的目标。

关闭 trace 不创建约束 map；关闭 snapshot/both/geometry 不遍历生成诊断快照。仍有模式判断分支，尚不能声称成本为零。

约束 data 的值均为字符串：尺寸为 GUI px 的浮点文本，NaN 表示不确定/未施加，Infinity 表示无界；布尔字段为 true/false 文本。heightRule 来自实际执行分支：parent-assigned（父分配）、content-with-min-max（内容并钳制）、ratio-with-content-minimum（比例并保留内容下限）、resolved-height（已解析高度）。style.* 是相关计算后 CSS 值。盒树几何 numbers 同样使用字符串；不省略 NaN，以便精确对拍。

### MCP：oriel_layout

| 参数 | 类型 / 默认 | 范围与含义 |
|---|---|---|
| page | string，必填 | 与 oriel_snapshot.page 相同的页面路径；运行预览，不写游戏 |
| selector | string，body | 非空 CSS 选择器，最多 2000 字符 |
| backend | string，old | old / new / both |
| geometry | boolean，false | 是否附整个布局快照 |
| width / height | integer，640 / 360 | GUI px，1–1920 / 1–1080 |
| frames | integer，1 | 1–600，按预览器固定时钟推进 |

最小调用仅提供 page；例如 page=/external/page.html、selector=#panel、backend=both、geometry=true。沿用已有预览器构建与启动流程，返回 passed、真实进程 exitCode、records、工作区 jsonl 路径与截图路径。无匹配目标、输出截断或缺少请求的几何记录时报错；没有独立尺寸约束时 passed=false，进程仍可能正常退出。不会批准或接入页面，不替代 oriel_check 或游戏验收。

### Java 数据合同

- LayoutEngine.snapshot()：不 flush、不修改布局，调用者先完成布局；返回不可变 LayoutSnapshot。principal 盒 ID 在同一文档中按元素身份稳定，伪元素区分 before/after；匿名盒按生成顺序编号，匿名内容结构变化时不保证 ID 不变。元素键为弱引用，不保留脱离的 DOM。ID 不跨文档/重载稳定。
- LayoutSnapshot.requireSame(other, context)：首次差异抛 IllegalStateException；供测试与迁移检查，不是生产期每帧缓存。
- LayoutEngine.whySize(element)：需预先开启 trace，会 flush；外来/脱离/无独立盒的元素报错，返回不可变约束 map。
- LayoutEngine.fontsChanged()：字体宽度变化后由宿主显式调用，重新读取 Host.fonts()、清空词宽缓存、递增 TextMeasure.generation() 并请求布局。保留共享 TextMeasure 对象身份，绘制/控件已有引用不会失效；支持宿主替换字体度量服务。不会自动监听资源包，现有宿主重建文档流程不变。单独 TextMeasure.invalidate() 只清缓存，不重新读取宿主服务或请求布局。

## 固定行高列表范围原语

FixedRowRange.calculate(count, rowHeight, scrollTop, viewportHeight, overscan) 只计算 [from,to) 与 leading/trailing 占位高度，不创建或回收 DOM。count/overscan 为非负整数；尺寸/偏移是有限 double，rowHeight 大于 0、viewportHeight 不小于 0、总高度有限。非法参数报错。偏移自动钳制；空列表或零高视口返回空范围。

例如 1000 行、每行 20px、scrollTop=205、视口 50px、缓冲 2 行，返回 [8,15)，前占位 160px，后占位 19700px。部分可见行计入范围。

这是虚拟列表的几何基础，没有 kit 组件、绑定复用、焦点/拖动/IME 行保留或原生槽位生命周期处理，不能仅据此回收输入中的行。变高列表未实现。

## 当前边界

aspect-ratio、overflow:clip、min()/max()/clamp() 及滚动/纯变换不重排是既有能力，不算本次新增。sticky 仍按 relative 近似并诊断，contain/content-visibility 布局隔离仍未支持。共享快照目前供诊断/差分使用；绘制/命中仍读原有 Box/Coordinates，没有跨帧几何缓存。尚无子树脏传播、增量布局缓存或字体自动世代通知。完整功能、像素、固定浏览器基线、交替性能门禁和真实游戏须分别验证，本工具不能替代。

输入集成：fontsChanged 在 old/new/both 输入后端均清除字符串与字形测量缓存。TextMeasure.generation() 同时跟踪显式重载和宿主 FontMetrics.generation() 变化，文本控件因此能识别两种失效来源；宿主世代变化本身不会主动请求静态段落重排。

## Flex / Grid 候选实现与求解诊断

[手册首页](../index.md) · [CSS 参数](../css-guide.md) · [性能规范](../performance.md)

开发者迁移开关默认关闭。Flex、Grid 默认均为旧实现；候选代码保留原有 MIT 派生关系，不宣称完全独立原创。本文中的 Grid 扩展仅在候选实现中可用，不能当作默认发布版本的 CSS 合同。

## 实现选择

JVM 参数分别为 -Doriel.layout.flex=old|new|both、-Doriel.layout.grid=old|new|both，缺省 old，非法值报错。候选实现不作为默认；按表中模式检查对应求解路径。

| 值 | 行为 |
|---|---|
| old | 冻结的旧实现，作为迁移参照 |
| new | 候选实现；Flex 使用 pass 内 item 暂存池、固有尺寸缓存及合并扫描；Grid 使用按 item 索引的贡献缓存、无切片 BitSet 占用查询及简化的轨道临时集合 |
| both | 同一 DOM/样式建立独立旧、新盒树；精确比较盒矩形、边距、边框、内边距、基线、行与片段矩形；差异报页面、元素、盒路径、字段及旧/新值 |

两个开关互相独立。both 共享宿主的纯文字度量缓存，不共享盒树或尺寸约束缓存；参照树不执行溢出汇总和滚动钳制。新 Grid 命名线与基线会有意改变旧实现不支持的页面，这些新增能力用专门测试验证，不在 both 中豁免差异。

Flex 暂存池只活在一次 LayoutPass 内，递归期间借出的 item 不会被复用。Grid 固有贡献缓存在单次容器求解内复用，百分比行二次求解保留固定列宽下的测量；不跨帧、跨字体或跨样式。两遍布局没有完全合并，不以合并部分扫描冒充取消全部测量。

## why-size / why-position

预览器的独立无窗口入口：

    preview page.html --why-size --why-position --frames 20 --size 427x240 > /external/layout.jsonl

两个标志都不带值，可单独使用。命令仅在本次运行使用两个候选布局器；stdout 每行是一个 JSON 对象，普通宿主日志、页面错误与诊断写 stderr。支持 --data、--lang、--scale、--size、--frames、--strict；不与 --canvas-test、--actions、--snapshot、--narrate、--boxes 混用。返回码沿用页面失败 1、参数错误 2、strict 警告 3。没有 Flex/Grid 容器时可能没有事件。

嵌入引擎时可用 -Doriel.layout.flex.trace=why-size|why-position|all 和 -Doriel.layout.grid.trace=why-size|why-position|all。缺省 off，不创建事件或输出缓冲。使用 old 时没有候选求解事件。

| 查询 | 内容 |
|---|---|
| Flex why-size | 每个 item 的 base、hypothetical、target、free space、min/max violation、冻结状态及迭代编号 |
| Flex why-position | 行号、主/交叉轴偏移、方向、对齐原因 |
| Grid why-size | 每个 item 的 min-content / max-content / definite-minimum 缓存填充；每条轨道 initial / intrinsic / maximize / flexible / stretch 阶段的 base、growth limit、约束、类型及 collapsed 状态 |
| Grid why-position | 最终放置的零基列/行起止线、指定起始值、auto-flow、尺寸、容器相对坐标及 baseline shim |

坐标均在 relative/transform 之前，不能当作视口命中坐标。测量与实际布局都可发事件，同一容器可能出现多次；Grid 记录自动放置结果，不记录每次被拒绝的占用探测。

统一事件信封：schemaVersion=1、sessionId、单进程递增 seq、ISO time、environment（engine/preview）、phase=layout、severity=info、code（layout.flex.* / layout.grid.*）、message、source（url/line）和 data。data 包含 context/query/phase/container/item/sourceLine/reason 及对应求解字段；没有定义的浮点值为 null，reason/message 可供人阅读。编号从 0 开始，seq 从 1 开始。

非 CLI 模式默认通过宿主 DEBUG 日志输出；可设置 -Doriel.layout.traceFile=/absolute/external/layout.jsonl 追加纯 JSONL。父目录必须存在，写失败报错，不自动清空或轮转。日志不要放源码仓库。MCP 桥接由独立通道提供；这里不宣称已经新增 MCP 工具或验证游戏诊断。

## Grid 候选扩展

- 保留 grid-template-columns/rows 的 [name] 元数据，支持普通命名起止线、整数 repeat 和 auto-fill/auto-fit 的命名线展开。多个名字可在同一行。当前名称引用取首个匹配；带名称的第 N 次引用和命名 span 仍沿用旧解析限制，不宣称完整支持。
- grid-template-areas 仍检查等长行与矩形区域；区域隐式的 name-start/name-end 可用于放置。旧 computed style 文本保持既有序列化，不因保留名字改变输出。仅名称变化也会使布局失效。
- align-items/align-self:baseline 为同一起始行的非自动上下边距 item 建立首基线组；shim 同时参与行固有尺寸贡献与最终位置。没有基线时用 border-box 底边。百分比 block size/min/max 会形成对行高的依赖，暂按 start 回退，不参加固有基线组；不支持 last baseline 或竖排基线组。

示例（需要 grid=new）：

    <div style="display:grid;grid-template-columns:[left] 20px [body] 60px [right];align-items:baseline">
      <div style="grid-column:left/body;height:20px"></div>
      <div style="grid-column:body/right;height:10px"></div>
    </div>

subgrid 本切片不实现。它会让子项固有贡献反向影响父轨道，同时父轨道又决定子项百分比、换行和基线；现有一次列后行的求解顺序不能直接承载这种环。下一切片需定义父子共享轨道的测量依赖图、环检测与 CSS 指定的循环百分比处理，再独立加浏览器参照，不能简单复用父轨道可变数组。masonry 不在范围内。

## 验证边界

gap 的两个值仍是 row-gap、column-gap；column Flex 主/交叉轴互换，place-content 仍展开为 align-content、justify-content。冻结的 1257 夹具、黄金图与容差不改，新增差分测试另跑独立树对拍；页面测试在同一动画时刻比较 computed style 与 RGBA，覆盖两种窗口尺寸。

这些验证不代表完整浏览器规范支持，不替代游戏、GPU、IME 或多人环境验收。性能只根据独占窗口交替测量判断；默认切换需要中位数与 p95 门禁均通过，不能以分配下降替代整页时间门禁。
