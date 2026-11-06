# 检查与验收工具

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
