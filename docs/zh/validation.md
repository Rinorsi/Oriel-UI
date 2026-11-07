# 检查与验收工具

开发期布局 old/new/both、--boxes 的约束 JSON 输出与 oriel_layout MCP 参数见[布局尺寸诊断](d36-r06.md)。默认 old，不替代常规检查。

下游骨架 `oriel_scaffold` 默认只读；prepare 使用 `oriel_check` 的严格引擎诊断与双尺寸截图，用户聊天批准后 apply 仅新建不存在的 HTML/Java。`node --test tools/mcp/scaffold.test.mjs` 用伪造工程覆盖检测、路径、批准及幂等；`ORIEL_TEST_TMP` 指向仓库外临时目录。Java 注册片段未据此证明下游编译或游戏可用，详见 [MCP 手册](mcp.md#下游骨架oriel_scaffold)。

粒子/动画的性能验收见 [性能规范](performance.md)（MCP：`oriel_rules` 的 `topic=performance`）。strict 零诊断不等于帧率达标；保留同动作基线、离线与实机证据分开记录。

[入口](index.md) · [制作流程](ui-workflow.md)

命令在 **Oriel-UI 仓库根目录、Git Bash** 执行，Java 25。PowerShell 用 gradlew.bat 并转换变量语法。构建/测试输出之外，截图、脚本、日志、临时页写仓库外 daily。

## 准备目录和预览器

```bash
DAY=$(bash ../Oriel-UI-Workspace/tools/new-day.sh)
mkdir -p "$DAY/screenshots/ui" "$DAY/scratch/ui" "$DAY/logs"
./gradlew :preview:installDist
```

ORIEL_MC_JAR 或 -Doriel.mcJar 指向实际客户端 JAR。缺资源会用后备字体/缺图纹理，不能据此判游戏一致。不要复制 MC 资源进仓库。

**外部临时页还需自己的资源根。** 预览器不会因为 classpath 有 kit.jar 就自动把其 assets 当资源包加载。制作流程里的示例若保存到 daily，按下面布置；复制的是 Oriel 自己已有资源，不是 MC JAR：

```bash
mkdir -p "$DAY/scratch/ui/assets/mymod/ui"
cp -R kit/src/main/resources/assets/oriel "$DAY/scratch/ui/assets/"
# 把示例 HTML 保存为 $DAY/scratch/ui/assets/mymod/ui/example.html
```

此时页面 URL 是 mymod:ui/example.html，同一资源根下能找到 oriel:ui/ore.css 及其纹理。自己的样式、脚本、图片也按 assets/namespace 布置。只把 HTML 放到任意临时路径会遇到 missing-stylesheet/missing-image；即使普通 snapshot 返回 0，也不能忽略资源诊断。

## 页面检查与截图

```bash
./gradlew :kit:test :preview:checkUi
./gradlew :preview:checkUi -PuiStrict
./gradlew :preview:checkUi -PuiStrict -Plang=en_us
./gradlew :preview:checkUi -Pui=kit/src/main/resources/assets/oriel/ui/gallery.html
./gradlew :preview:checkUi -Pui=kit/src/main/resources/assets/oriel/ui/gallery.html -PuiStrict
java -cp "preview/build/install/preview/lib/*" dev.oriel.preview.Preview \
  kit/src/main/resources/assets/oriel/ui/gallery.html \
  --size 640x360 --scale 2 --frames 60 \
  --snapshot "$DAY/screenshots/ui/gallery.png" --boxes ".o-tile"
```

checkUi 查加载、控制器异常、资源、CSS 诊断和部分设计问题。外部页需显式指定路径，默认 kit 扫描不等于包括下游页面。框正确不代表文字/遮挡/交互正确，必须看截图。

默认checkUi/CLI的 --check 将CSS与设计警告列为WARN但仍可返回0，页面异常或缺图返回1；不是所有警告都会阻止构建。需要警告阻断时用 -PuiStrict，或CLI的 --check TARGET --strict；此时仅警告返回3，页面错误仍返回1。--lang可与--strict组合。空目录返回2。此选项只改变检查退出码，不改页面样式或渲染。

CI 对全部 kit 页面分别执行默认语言（zh_cn）和 en_us 的严格检查；任一语言出现警告即阻止构建。Gradle 会把预览器的非零退出码报告为任务失败，具体退出码见任务日志。此门禁不替代截图、游戏或联机验收。

### 布局后诊断与 PNG 检查

以下诊断用于预览器、MCP 的检查路径；检查的是当前数据和当前布局，不替代实际游戏操作。双尺寸检查的任一视口出现警告都会使 strict 失败；快照和动作预览也在最后一帧运行这些规则。

| 诊断 code | 触发条件与修复 |
|---|---|
| text-ellipsis-tooltip | 实际绘制出了 text-overflow 省略号，但文字与祖先没有有效悬停提示。添加完整内容的 title / title-json，或放宽、换行；只声明 ellipsis 但文字能完整显示不会报。空 title 会屏蔽祖先提示，data-tip 没有内置提示行为，不能代替 title。 |
| text-clipped | nowrap 文字片段被自身或祖先 overflow 的横向裁剪切掉，且没有实际省略号。给文字所在盒子设置省略号并加 title，或换行、放宽容器；也检查滚动容器内部分可见的文字，不报完全不可见的行。 |
| flex-content-clipped | flex 行子项发生收缩且内容被切。图标、角标用 flex:none / min-width，文本区单独收缩并设置省略号和提示。已完整显示或已使用省略号的文本不会报此项。 |
| image-too-large | 页面 img 或计算样式背景、边框、遮罩引用的 PNG 宽或高 >2048px，或文件 >1 MiB。按显示尺寸缩图、压缩；支持资源命名空间与页面旁文件，同一 URL 去重，只读 PNG 文件头与文件大小，不解码整图。 |
| images-without-lazy | 同页非 canvas/sprite/data 的 img 引用与 URL 背景层合计 >24 处，且这些源完全没有 `loading=lazy` / `data-oriel-lazy`。含隐藏和视口外元素，重复 URL 按使用处计数；渐变不计。给列表图启用懒加载并预留宽高，配置见[性能规范](performance.md#列表图片懒加载)。 |

每个规则、每个视口最多显示前 5 处，超出另报一条总数汇总；结构化报告保留 code/source/line/column/suggestion。文本规则跳过隐藏、完全不可见、尚未稳定的入场动画、循环动画及旋转/斜切内容；已结束的入场动画、轴向缩放/平移仍检查。非 nowrap、多行裁剪、复杂遮挡不在 text-clipped 的覆盖范围内。动态生成节点没有原始行号时用元素标识定位。离线字体和数据必须与目标页面匹配。

**库内示例迁移例外：** 上表新增规则在本仓库真实路径下的 kit/ui、common/showcase 和 common/demo 页面保留 INFO，提示“库内示例待修复”，不阻断 strict；这些页面的视觉修复单独验收。仅凭 oriel 命名空间不能豁免，外部页面和 tools/mcp/examples 仍为 WARNING。原有 CSS、缺图和设计规则不放宽，因此 INFO 不等于该页面已完整通过视觉或旧规则验收。

| Preview 参数 | 默认/作用 |
|---|---|
| 位置参数 | 文件路径；assets/ns 下可映射为 ns:URL |
| --size WxH | 缺省 427x240；分别用 640x360、427x240 |
| --scale N | 缺省 2；640×360、scale2 输出 1280×720 |
| --frames N | 缺省 1；snapshot 前帧数，帧间隔 16ms |
| --snapshot PATH | 无窗口输出最后帧 PNG；明确 daily 路径 |
| --actions PATH | 动作文件 |
| --out DIR | 动作截图目录；不写可能落工作目录，务必显式写 |
| --data PATH | 初始 JSON；不表示服务端连接 |
| --boxes SELECTOR | 打印匹配元素盒子 |
| --background COLOR | 不透明底色；省略预览背景 |
| --narrate | 打印朗读文本 |
| --strict | 严格诊断 |
| --canvas-test | 画布原语页 |
| --check TARGET | 检查页/目录；MCP oriel_check 使用 |

## 交互脚本

保存到 daily/scratch/ui/actions.txt，id 必须对应被测页：

```text
wait 30
move #apply
shot hover
click #name
key Ctrl+a
type 测试名称
click #apply
wait 15
shot applied
key Tab
shot focus
```

```bash
java -cp "preview/build/install/preview/lib/*" dev.oriel.preview.Preview \
  "$DAY/scratch/ui/assets/mymod/ui/example.html" --strict --size 427x240 --scale 2 \
  --actions "$DAY/scratch/ui/actions.txt" --out "$DAY/screenshots/ui/actions"
```

| 动作 | 参数/行为 |
|---|---|
| wait N | N 帧；16ms/帧 |
| move TARGET / click TARGET | selector 或 x y GUI 坐标；click 左键按下释放 |
| wheel TARGET PX | 正数向下，一格约 24px |
| key NAME | Enter、ArrowDown、Escape；组合 Shift+Tab、Ctrl+a |
| type TEXT | 行剩余部分逐字符输入；**不是 IME 预编辑测试** |
| shot NAME | NAME.png |
| bench N | N 帧计时；不是游戏 GPU 性能测试 |

输入后自动一帧；命中考虑滚动/变换，会尝试滚入可见区域；被覆盖可报错。退出码：0 成功，1 页面失败，actions 格式/目标失败可为 2，strict 诊断失败为 3。看具体行号，不只看是否生成 PNG。

## H5 静态对照

先按 [工具说明](../../tools/h5ref/README.md)准备浏览器，再执行：

```bash
node tools/h5ref/compare.mjs "$DAY/scratch/ui/static-state.html" \
  --out "$DAY/screenshots/ui/h5ref"
```

固定 427×240、scale2；Chrome JS 禁用、CSS 动画 0ms，引擎首帧。脚本状态先物化成静态 DOM/CSS，不能拿空列表与有数据列表对比。MC 元素、字体、UA 差异单独判读。

输出 chrome/engine/heatmap/comparison PNG 和 report.md/json。MAE=RGB 平均绝对误差/255×100；变化像素比例阈值为任一通道差>8。**退出码 0 只表示比较完成，没有自动等价通过阈值。** 无诊断也不是完整支持证明。

## MCP（agent 使用）

完整项目工作流和新增工具参数见 [MCP 手册](mcp.md)。项目模式使用 oriel_project 登记后调用 oriel_check(projectId)，强制严格诊断与双尺寸场景；结果包含结构化问题和截图。通过后创建用户验收页，取得有效批准才能接入游戏。下表为兼容的基础预览工具。

CLI 的 --check 支持 --report <工作区路径.json>，输出逐页、逐视口的原始诊断（severity/code/source/line/column/message/suggestion）、运行错误、缺图、设计提示和截断计数；原文本输出与退出码保留。

stdio：node tools/mcp/oriel-preview.mjs，cwd 仓库根，可设 ORIEL_ROOT。工具在首次使用或预览源码发生变化时构建 installDist；状态与产物写入 ORIEL_MCP_STATE（默认工作区 daily/当天/scratch/mcp-workflow），不再使用系统临时目录。网页服务及游戏作业有独立生命周期和停止入口，详见 MCP 手册。

| 工具 | 输入 | 输出 |
|---|---|---|
| oriel_pages | 无 | kit 根目录页面和图标名；不保证递归列风格子页 |
| oriel_check | target 可选：页名/路径/目录，默认 kit | 错误、缺图、CSS 诊断，看退出信息 |
| oriel_snapshot | page 必填；width=640、height=360、frames=60；actions:string[]、boxes:string 可选 | PNG+文本；有 actions 时走动作时间轴，不用 frames；scale2 |

这不是游戏控制/网络测试工具。实际多人数据仍须联机验。

## 自动化分层与覆盖边界

预览器的 actions 是无头文档动作脚本，MCP snapshot 通过同一路径执行。游戏内 OrielAutomation 则把选择器动作送入 Minecraft 原生输入处理器；DevAutopilot 负责编排世界、菜单、页面和退出。两者不是同一套脚本解释器，不能把预览器 actions 文件直接交给游戏运行，也没有已实现的 Java ViewModel o-text/o-for/o-if/o-class/o-attr/o-on 声明式绑定合同。现有 Java Controller 与 JS 模板按各自手册接线。

页面局部逻辑可先用预览器动作验证；容器、权限、网络、真实IME及GPU行为必须走游戏或服务器验收。新增游戏业务的验收步骤需明确注册，脚手架生成页面不等于自动覆盖游戏行为。tools/test-smoke-all.sh 只测试冒烟结果计数，假进程退出码不证明游戏通过。

## 游戏与热重载

怎么配、怎么确认、下游模组怎么做：见 [热更新](hot-reload.md)。下面是保存后重载的边界。

```bash
./gradlew :fabric:26.1:runClient
# 游戏内：/oriel open mymod:ui/editor.html
```

启动前确认运行目录按工作区构建约定指向 daily，不新建仓库内 run。kit 巡检可用 -Pautopilot=oriel，但不会自动覆盖下游新业务。

保存已读取 HTML/CSS/JSON 可重载；driver push/patch 与控制器 saveState/restoreState 有各自状态保留规则。Java 热替换受 VM 限制，增删字段/方法、改加载器元数据可能要重启；语言/字体/纹理需资源重载。不要承诺任意更改都免重启。

文件观察器每 200ms 轮询已读取文件（预览窗口使用自身轮询周期）。同一批变更全部是外部 `.css` 时，游戏与预览窗口仅清空链接及 `@import` 样式缓存，下一帧重算样式/布局，不重建 Document/DOM，不重跑脚本或控制器；焦点、输入、选区、滚动及事件监听保留。布局尺寸改变时滚动仍会按新范围夹取，CSS 动画/transition 按新样式重新计算。缺失或错误 CSS 继续给出引擎诊断。

HTML（含内联 `<style>`）、脚本、JSON 或混合变更仍整页重建，并尝试控制器 `saveState/restoreState`；该分支仅恢复控制器主动保存的状态，不自动恢复任意 JS 闭包、临时事件或输入法组合。`/oriel reload`、F3+T、语言变化及 Java 热替换仍是整页重载。尚未读取/未观察到的文件不会触发此机制。`Document.reloadStyles()` 可由宿主显式请求同样的 CSS 缓存失效，在线程约定上与其他 Document 操作一致。

| 症状 | 检查 |
|---|---|
| CSS 不生效 | 属性/值表、层叠、选择器、var、诊断 |
| 点了没反应 | 只引 CSS 没注册控制器；id 控件未接；有覆盖层 |
| 字体/图标错或粉黑 | URL、MC JAR、font JSON、资源包 |
| 截图全透明 | 动画时钟；frames/等待，减少动效时仍要显示 |
| 槽位不能放物品 | 真容器 Screen、index、服务端 mayPlace/mayPickup |
| send 成功没保存 | 回执、频道、类型、权限/快照/限额 |
| 预览能输入游戏不行 | 单独验 IME，type 中文不替代候选窗链路 |

实际验收记录版本/加载器/提交/资源/视口/动作/结果，放 daily；使用手册只留一般流程和已确认边界。

mod-project 接入只验证资源复制与批准关卡，回执 gameVerified=false；需在下游游戏用回执 pageUrl 打开验证。oriel_game_preview 对此目标返回 DOWNSTREAM_GAME_REQUIRED。参数和路径限制见 [MCP 手册](mcp.md#下游模组目标mod-project)。

## 选择器迁移双跑

维护者在当前 shell 设置 `JAVA_TOOL_OPTIONS='-Doriel.selector=both -Doriel.selector.audit=<工作区绝对路径>/selector-%p.jsonl'`，再运行 `:engine:test :kit:test :preview:test :preview:checkUi`。`old` 是默认实现，`new` 是实验实现，`both` 执行独立匹配与规则候选索引并在差异时抛异常；不改变解析器开关 `oriel.parser`。完成后清除当前 shell 的 `JAVA_TOOL_OPTIONS`，不要改系统环境变量。

审计文件为 UTF-8 JSONL，按进程创建，`%p` 替换为 PID；父目录须预先存在。每行包含 `kind`（selector/index）、`equal`、旧新匹配/优先级或命中数量、`count`（这组字段实际比较次数）。合计 count，不用文件行数冒充双跑次数。正常 JVM 关闭时写完，强制终止的文件不能当完整证据。写入失败报错；无 audit 参数时仍比较并在差异时报错。

双跑及审计会增加耗时和分配，不用其数值评价运行时性能。现有 `StylePerformanceTest` 门限保持不变，分别在 old/new 下运行；完整 both 回归可通过临时 Gradle init 脚本仅排除该类，将它单独在 old/new 核验。

预览黄金断言可选 `-Doriel.snapshotCapture=<工作区绝对目录>` 保存每帧实际图；仍执行原黄金断言，不重写黄金。分别运行 old/new，逐 RGBA 比较这些帧；默认不输出额外图片。通过 `JAVA_TOOL_OPTIONS` 传给测试 JVM。图像、日志与性能数据放仓库外 daily 目录。默认切换须等 Lead 验收，双跑退出码零不自动切换。


`--why <selector> <element-query>` 与 MCP `oriel_inspect` 的字段、退出码、预算边界见 [选择器诊断](selector-debug.md)。`both` 返回旧匹配路径，不验证 `new` 增量失效；另做 old/new 动态 computed style 对照。

属性解释：预览器新增 `--why-property <property> <element-query>`；MCP `oriel_inspect` 的 selector/property 二选一，page/element 必填，frames 默认 1（1–600）。仅加载无头预览，不批准、不接入或启动游戏。字段、伪元素、实验开关和限制见 [实验层叠与属性解释](d36-r04.md)。

输入后端开关、事件 JSONL、预览/MCP 动作、inert 与可选空间导航的参数及限制见[输入诊断与导航](d36-input.md)。缺省后端仍为 old；六目标真实 IME 与手柄另验。
