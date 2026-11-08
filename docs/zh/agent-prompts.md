# Agent 调用提示词

**接入、升级或发布前必读：[分发指南](distribution.md)**，MCP 调用 `oriel_rules` 的 `topic=distribution`。按 0.12 同 minor 范围选择嵌入瘦包或外部必需依赖；所用模板/showcase/demo 复制到自己模组。验证范围以分发指南为准：26.3 双加载器专服五场景、单元/通知页预览已有证据；游戏客户端弹窗与设置页、26.1.2/26.2 混装和真实 Editor 升级仍未验证，不能扩大已测范围。

工具合同先查oriel_capabilities；协作所有权/模板见[编排规范](agent-orchestration.md)，日志/why-tree/截图分页和独立黄金审批见[增量合同](d36-agent-tools.md)。截图验收逐页交用户查看，不能把首12图当全部场景批准；页面批准不能批准黄金更新。

本文件的三个二级标题对应 MCP prompts/get 的 workflow、revise、integrate；MCP 直接读取正文，避免维护两套提示词。完整阶段合同见 [制作流程](ui-workflow.md)，参数见 [MCP 手册](mcp.md)。

## workflow

排查绘制命令可调用只读 `oriel_paint`；按 [绘制检查](d36-paint.md) 解释过滤、缓存与原生边界，不把命令导出当成 GPU 验收或交付批准。

制作或修改动画时，开工先调用 `oriel_rules` 的 `topic=performance`，读取 [粒子与动画性能规范](performance.md)。密集装饰不能逐粒子增删 DOM，也不能在脚本中逐扫描线绘制；同一动作对照和真实游戏测试的范围必须分别报告。

**先判断走哪条路。** 用户在边做边看（默认）：把页面写进用户游戏的 config/oriel/my-pages/<项目名>/（热重载，发布版 jar 也支持），每轮先 oriel_check 警告清零，再用 oriel_snapshot 截 640×360 和 427×240 发到对话；不需要网页验收、不需要批准、不要自己启动游戏。只有用户要把页面正式放进模组或仓库时，才按下面的完整流程（验收 → 批准 → 接入 → 游戏预览）。

你正在使用 Oriel MCP 制作 Minecraft UI。先调用 oriel_rules 读取 quickref（做页面前必读的硬规则：字体字号、尺寸、最小骨架、data-oriel-shell、放哪里怎么看）、overview 和目标主题的规则，再调用 oriel_project 登记 sourceRoot、namespace、entry、精确 MC 版本、加载器、演示数据、消息合同和交互场景。仅使用支持范围内的 HTML/CSS/JS；不确定的能力先查询，不能把浏览器能运行视为游戏支持。

制作共享页面，浏览器 mock 与游戏资源分离。调用 oriel_web_preview 提供浏览器可交互预览；迭代调用 oriel_check 和 oriel_snapshot，修复阻断问题并查看真实引擎截图。检查必须覆盖 640×360 和 427×240，以及声明的交互场景。原生物品、实体、槽位的浏览器替身只能说明布局，不能证明游戏功能正确。

检查通过后调用 oriel_review 的 prepare，向用户展示固定版本的网页、Oriel 截图、场景和已知差异。用户本人点击验收页批准或拒绝；用户若更想看图，用 prepare 的 mode=screenshots 把截图直接发到聊天里，用户明确同意后用 decide 逐字引用其原话记录（不得编造或改写用户的话）。Agent 不得代点确认、伪造批准、修改验收文件，或把测试记录充当用户验收。等待时可处理独立工作，但不得越过游戏接入关卡。

正式交付流程里，用户验收（网页或截图）前不得启动任何游戏客户端或游戏内测试，包括工具自测、基础设施探针和冒烟测试。此前只允许源码/编译检查、无头 Oriel 预览与网页测试。不得以测试另一张页面为由提前启动游戏，也不得用 Gradle/CLI 绕过 MCP 的批准关卡。

通过 oriel_status 查询批准回执。仅当当前内容与批准版本一致时，调用 oriel_integrate 的 plan、apply；mod-project 目标按回执在下游游戏验证，其余目标调用 oriel_game_preview 的 start/status。内容、资源、规则或目标改变后重新检查和验收。分别报告浏览器、引擎和游戏实际验证了什么；未接真实业务的按钮必须明确显示演示，不声称完成保存或联网。MCP 初始化指引不是客户端系统权限，工具的校验关卡不可通过提示词跳过。

## revise

规则未生效时可用 `oriel_inspect(page, selector, element)` 查询候选规则、失败谓词与声明赢家；默认推进 1 帧，可传 frames（1–600）。它只做无头诊断，不创建批准记录，不代替检查、截图或用户验收，详见 [选择器诊断](selector-debug.md)。

先调用 oriel_status 读取原项目与验收状态，再调用 oriel_rules 查询本次修改涉及的能力。修改共享源文件，保留未知业务数据。修改后重新 oriel_check、oriel_snapshot，并用 oriel_review.prepare 创建新版本。不得使用旧版批准安装新版页面；旧回执只能追踪原版本。向用户说明可见改动与需要重新验收的场景。

## integrate

先读 `oriel_rules topic=distribution`，确认下游采用嵌入瘦包还是外部依赖；0.12 配方及验证状态以[分发指南](distribution.md)为准。profile 与脚手架版本读取当前工作树的 `gradle.properties`，默认瘦包、可指定当前完整版；旧 profile 返回 `UNSUPPORTED_PROFILE`，须按实际 jar 重新登记和验收，不能复用旧版批准。

下游模组首次接入先用 oriel_scaffold(projectRoot) 只读检测，按 missing/candidates 补信息（不要猜 Stonecutter 版本或启动方式）。工具给出依赖、元数据、打开和客户端命令片段。需要示例文件时传 prepare=true，在工作区生成并检查骨架；展示完整清单与截图，用户聊天同意后以 oriel_review.decide 逐字记录，再用原参数 apply=true 与 reviewId。仅新建不存在的 HTML/Java，已有文件跳过；不改 Gradle/元数据/已有 Java，不自动启动游戏。普通页面资源交付仍用下述 oriel_integrate。

先调用 oriel_status 和 oriel_review.status，核对项目、reviewId、内容摘要、profile 和用户决定。未批准、被拒绝、检查失败或内容陈旧就停止接入并说明原因。调用 oriel_integrate.plan 查看写入清单与冲突，再使用返回的 planId 调用 apply。不得手工复制文件绕过失败的工具。mod-project 目标按回执代码在下游游戏验证；其余目标调用 oriel_game_preview.start 并轮询 status，核对实际 MC 版本、加载器、页面 URL、截图和诊断。成功复制不等于游戏验证通过；不要替用户执行真实世界数据修改。

属性解释：预览器新增 `--why-property <property> <element-query>`；MCP `oriel_inspect` 的 selector/property 二选一，page/element 必填，frames 默认 1（1–600）。仅加载无头预览，不批准、不接入或启动游戏。字段、伪元素、实验开关和限制见 [实验层叠与属性解释](d36-r04.md)。
