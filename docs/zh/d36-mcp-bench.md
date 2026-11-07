# D36 统一 CPU 性能比较工具

入口：`node tools/bench/run.mjs`，需要 Node 24、Git Bash、同一 JDK 25、预先构建好的旧/新预览器。它不构建、不启动游戏、不更新黄金图。FrameBenchmark 不导出独立样式/布局成本，因此默认只提供整页 CPU 帧门禁，不能代表完整 R10 通过。

## 准备与冻结

先分别使用工作区 `tools/gws.sh <worktree> :preview:installDist` 构建两份产物；测量期间不得构建。将配置保存到工作区当天 scratch。所有路径用绝对路径，Windows JSON 路径推荐 `/`。替换下例的尖括号：

```json
{
  "workspace": "C:/Storage/Minecraft/project/Mods/Oriel-UI-Workspace",
  "java": "C:/Users/XINDONG/.jdks/jdk-25.0.5+8/bin/java.exe",
  "pages": "<同一固定资源树>/assets/oriel/ui",
  "resources": ["<同一固定资源树>", "<Minecraft合并jar绝对路径>"],
  "historicalBaseline": "<mcp工作树>/preview/benchmarks/frames.json",
  "env": {"ORIEL_MC_JAR": "<Minecraft合并jar绝对路径>"},
  "old": {"root": "<旧实现worktree>", "jvmArgs": ["-D你的开关=old"]},
  "new": {"root": "<新实现worktree>", "jvmArgs": ["-D你的开关=new"]},
  "rounds": 3,
  "warmup": 60,
  "frames": 120,
  "maxNoise": 0.05,
  "timeoutMs": 120000,
  "scenario": {"seed": 0, "action": "normal document frame", "clock": "60Hz"}
}
```

```bash
node tools/bench/run.mjs --config C:/external/config.json --manifest C:/external/frozen.json --freeze
node tools/bench/run.mjs --config C:/external/config.json --manifest C:/external/frozen.json --out C:/external/new-run
```

实际产物放 `workspace/daily/<日期>/`；上述 external 是占位。输出不得位于任何 Git 仓库；新清单/输出必须不存在，防止覆盖证据。`--freeze` 只写资源清单，不产生性能数字，不代表基线批准。当前 kit 有 **21** 页，历史 frames.json 只有20页；清单的 historicalPageDelta.added 明确记录 forge_station.html，旧时间基线不会被覆盖。以后变更页面/资源/场景须另建清单并记录原因。

页面树、资源树及 Minecraft jar 逐文件 SHA-256；采样前后复核。old/new 的 installDist lib 各自记录摘要且不允许过程中变更。必须给定相同资源来源，不得给新版替换轻量页面。源 SHA、开关意图及真实动作覆盖由报告补充；工具不把 JVM 参数解释成旧/新实现的自动证明。

## 采样与判定

测量自动进入配置中工作区的 tools/bench-window.sh，与 gws 共享排他约定。**不要从外面再包一次 bench-window**，否则会等待自己的锁。构建必须提前完成。默认子进程最长120秒，整个持锁测量最长20分钟；超时保留日志，不产生 PASS。长集合须预先按固定显式分组分别冻结/测量，再汇总所有分组，不能测到失败后删页。

先同一旧实现 A/A，再旧/新 A/B；至少三个奇数轮，顺序为 `A0,A1 / A1,A0 / A0,A1`、`old,new / new,old / old,new`。每份 FrameBenchmark 报告本身仍用三个独立内轮，配置、页面集合、JDK/OS/CPU数必须一致。默认保存12份进程原始报告、命令、日志与聚合 JSON、Markdown。

每页每项 p50/p95 取外轮中位数；噪声底为 A/A 配对相对差 `abs(a1-a0)/min(a0,a1)` 的最大值。A/B 增幅大于对应噪声即 FAIL。任一噪声底超过默认5%则 INCONCLUSIVE，不把忙机器判通过。maxNoise 可在0至0.25间显式设置，改变必须说明理由，不得自动调高。p50改善不能抵消p95回退，任何单页失败都阻止总 PASS。

退出码：0=PASS、1=性能 FAIL、2=INCONCLUSIVE/配置或执行错误（通过 comparison.json 或 failure.json 区分）。缺失指标、不同页面集、不同参数、异常进程退出或中途资源变化均不能通过。不沿用旧 benchmarkFrames 的25%容差。

## 合成压力集与阶段适配器

```bash
node tools/bench/run.mjs --synthetic C:/external/new-synthetic
```

生成 1k/5k/20k 元素 × 200/1k/5k 规则 × class/hover/attribute 的27页确定性组合与 suite.json。一个共享 rAF 修改继承 opacity，避免逐节点帧循环。**FrameBenchmark 不注入鼠标，hover 仅覆盖未悬停匹配，不能冒称 hover 迁移性能通过。** 没有新增 Java 样式/布局计时接口，也未宣称所有规模已实测。

阶段合成程序可替换 old/new 对象：

```json
{"root":"<worktree>","artifacts":["<被测jar或class目录>"],"command":["<java绝对路径>","-cp","<classpath>","your.Benchmark","{out}","{pages}"]}
```

命令是 argv 数组，不经 shell；支持 `{out}`、`{pages}`、`{baseline}`、`{root}`。适配器必须直接运行已构建程序，不能调 Gradle或起后台子进程。成功时在 out 写 FrameBenchmark 同形 JSON，含 warmup、frames、rounds、aggregate、viewport、backend、clearBeforeFrame、java、os、processors、pages（页名→p50_ms/p95_ms，有限正数）。页集合必须精确相等。专项 style/layout 可作为另一个明确命名的固定集合输出，不能用整页时间猜它们。命令及额外依赖须纳入 artifacts 摘要。

## 15效果帧序列入口

```bash
node tools/bench/effects.mjs C:/external/old-frames.json C:/external/new-frames.json C:/external/effect-diff.json
```

此入口比较已捕获帧，不启动捕获、不执行旧批量生成器或安装器、不修改用户我的页面。清单格式：

```json
{
  "schemaVersion": 1,
  "context": {"clock":"preview-16ms","seed":0,"resourceHash":"<摘要>","scenarioHash":"<摘要>"},
  "frames": [
    {"id":"09","width":427,"height":240,"state":"start","timeMs":0,"actionsHash":"<64位sha256>","pageHash":"<64位sha256>","png":"09/start.png"}
  ]
}
```

必须覆盖编号 `09,10,13,18,22,25,27,31,33,37,38,42,43,48,50` × 427×240/640×360 × start/entrance/peak/exit，共120帧。各页时点依据工作区研究 E 的3.5节及既有 action 文件选择，不能把源片30fps数字当预览16ms步数。两边上下文、同帧时点/页面/动作摘要必须一致；缺帧、重复、尺寸不符均报错。

支持非交错 RGB/RGBA8 PNG并检查CRC；逐RGBA像素零差才PASS，不设容差。输出每帧变化像素数、最大通道差与解码后摘要，不受PNG编码/文本元数据差异影响。捕获者须冻结素材/动作、扫描错误日志并检查状态断言。此入口不能证明输入已执行、源片等价、游戏模型/GPU/声音/IME正确。旧已知视觉差异保留，不借此更新黄金图。

## 工具自身验证

```bash
ORIEL_TEST_TMP=C:/external/existing-scratch node --test tools/bench/bench.test.mjs
```

覆盖尾延迟、噪声、缺失证据、页集合变化、环境不一致、真实CLI进程交替与锁入口、超时、PNG解码和120帧比较。测试的常数适配器只验证算法/协议，不是引擎性能数据。
