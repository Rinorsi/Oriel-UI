# MCP 页面制作与接入

开发期 oriel_layout 的参数、JSONL 与限制见[布局尺寸诊断](d36-r06.md)。它不产生页面批准或游戏验收。

## 下游骨架：oriel_scaffold

默认只读检测下游工程并返回接入清单；不执行 Gradle、不安装 jar、不修改构建、元数据或已有 Java。支持 Fabric、NeoForge 和常见 Stonecutter 源码结构。

| 参数 | 类型 / 默认 | 说明 |
|---|---|---|
| `projectRoot` | string，必填 | 已有工程绝对路径；禁止 `..`、链接和 junction |
| `loader` / `minecraft` / `modId` | string，可省略 | 字面量不能唯一检测时显式选择；loader 为 fabric/neoforge，MC 为 26.1.2/26.2/26.3 |
| `resourcesDir` | string，可省略 | 工程内相对资源目录；常见目录唯一时自动选择，common 优先 |
| `javaPackage` / `javaDir` | string，可省略 | 检测已有包名/公共包前缀及源码目录；Java 目录限 src/main/java、src/client/java 或 common/fabric/neoforge 下相同结构；优先客户端目录 |
| `launchMode` | string，可省略 | gradle-inside / gradle-outside / jar；不推测运行方式，缺省返回待补项，对应热更新第 2/3/4 种情形 |
| `prepare` | boolean，false | 仅在工作区生成固定包（含完整清单与 Java），执行 oriel_check 的严格检查和双尺寸截图；返回 projectId/check |
| `apply` | boolean，false | 需要原参数和 reviewId；仅创建不存在的两个文件；不能与 prepare 同时 true |
| `reviewId` | string，apply 必填 | 对 prepare 生成的固定版本完成截图验收、用户在聊天里同意后，由 oriel_review.decide 逐字记录的批准回执 |

最小只读调用：只传 projectRoot。回执包含检测值、`missing`、`candidates`、`existingOriel`；信息齐全后包含 `snippets`（Gradle/depends/open/客户端命令）、`files`（目标与完整内容）及热更新指引。动态 Gradle、占位符及多版本无法唯一判断时补同名参数；不会擅选版本。Gradle 片段为 Groovy 写法，Kotlin DSL 工程按现有写法合并。

写入步骤：补齐 missing → 同参数 prepare=true → oriel_review.prepare(projectId, mode=screenshots) → 展示截图及完整文件清单 → 用户聊天批准 → oriel_review.decide → 同参数 apply=true, reviewId。只新建资源目录下 `assets/<modid>/ui/hello/index.html` 和 Java 目录下 `<包路径>/OrielHello.java`；已有文件一律跳过（`skipped`），并发创建不会被覆盖，失败回滚本次写入。返回 plans 回执、摘要及 gameVerified=false。目标、生成内容、规则或预览环境改变使批准失效；无批准报 APPROVAL_REQUIRED，目标改变报 SCAFFOLD_CHANGED，越界或链接报路径诊断。下游编译、命令及真实游戏仍须另行验证。

动画开工先调用 `oriel_rules`，参数 `{ "topic": "performance" }`，读取 [粒子与动画性能规范](performance.md)。默认仍为 overview；performance 与其他主题一样返回 profile/source/text/rulesHash，支持可选 feature 筛选并参与验收规则版本哈希。

[入口](index.md) · [完整流程](ui-workflow.md) · [Agent 提示词](agent-prompts.md) · [检查工具](validation.md)

Oriel MCP 为本机开发服务，使用 Node.js stdio；它不是安装模组后自动开放的网络端口。网页服务仅监听 127.0.0.1，游戏接入使用独立开发客户端。源码实现与实际运行验收应分别记录，本页不代表全部版本的游戏验收已完成。

## 一页看懂：MCP 是什么、在哪里跑、怎么和游戏联动

**它是一个开发期的外部工具，不是模组的一部分。** 玩家装的 jar 里没有它，游戏里没有 MCP 界面，游戏也不监听任何 MCP 端口。它的作用是让 AI 助手按固定流程做出界面、给你看、再放进游戏。

### 三个部件

| 部件 | 是什么 | 怎么启动 | 占用端口吗 |
|---|---|---|---|
| **MCP 服务器** | `tools/mcp/oriel-preview.mjs`，一个 Node 进程，通过**标准输入输出（stdio）**和 AI 客户端说话 | 由你的 AI 客户端按配置自动启动（见下面的"连接"） | **不占**：它不是网络服务，所以没有"广播在哪个端口" |
| **本机网页服务** | 供你在浏览器里交互预览和点击批准的网页 | `oriel_web_preview` 或 `oriel_review`（网页模式）启动，独立于 MCP 进程 | **占**：只监听 `127.0.0.1` 上自动分配的随机端口，验收页和被测页用不同端口，带会话令牌；最长存活 24 小时，可用 stop 提前结束 |
| **游戏预览任务** | 一个**独立的开发客户端**：用 Gradle 启动 `runClient -Pautopilot=mcp`，打开页面、执行场景、截图后自己退出 | `oriel_game_preview` 启动 | 不占：用任务文件和回执文件交换信息；不连你正在玩的游戏 |

### 它们怎么联动

```text
AI 客户端 ──stdio──▶ MCP 服务器 ──▶ 状态目录（项目、检查、验收、计划、作业记录）
                         │
                         ├──▶ 无头 Oriel 引擎：检查 + 截图（不开游戏）
                         ├──▶ 本机网页服务（127.0.0.1:随机端口）──▶ 你的浏览器（只在网页验收时用）
                         └──▶ 游戏预览：gradle runClient（独立开发客户端）
                                 读 game-task.json ──▶ 打开页面、截图 ──▶ 写 game-receipt.json 后退出
```

- **游戏里看不到 MCP 的状态，也不需要**：游戏预览是一次性的独立客户端，跑完就退出，不影响你正在玩的游戏。
- **看状态用 `oriel_status`**：返回当前阶段、检查/验收/接入/游戏回执和下一步。更细的看状态目录：`jobs/<作业号>/game.log`（游戏日志）、`game-receipt.json`（游戏回执）、截图。
- **状态目录**：环境变量 `ORIEL_MCP_STATE`；没设置时是工作区的 `daily/<日期>/scratch/mcp-workflow`，里面有 `projects/`、`revisions/`、`reviews/`、`plans/`、`jobs/`。MCP 服务器重启后，项目和验收记录可以从同一目录恢复。

### 两条路

- **快速迭代（默认）**：不用 MCP 的验收和接入。直接把页面写进用户游戏的 `config/oriel/my-pages/<项目名>/`，热重载（发布版 jar 也支持），用 `oriel_check` 和 `oriel_snapshot` 把关、把截图发到对话里。
- **正式交付**：下面这条完整链路，要用户批准。

### 一个页面的阶段（正式交付）

| 阶段 | 做什么 | 工具 |
|---|---|---|
| 登记 | 告诉工具页面包在哪、命名空间、入口、要看的场景 | `oriel_project` |
| 检查 | 用真实引擎做严格检查，产出双尺寸截图；不通过不能往下走 | `oriel_check` |
| 验收 | **网页验收**或**截图验收**，二选一（见下） | `oriel_review` |
| 批准 | 用户本人批准固定版本；内容一变就失效 | 网页点击，或聊天里同意 |
| 接入 | 把页面放进游戏能读到的位置（三种目标，见下及文末） | `oriel_integrate` |
| 游戏预览 | 独立开发客户端打开页面并截图 | `oriel_game_preview` |

### 两种验收方式

| | 网页验收（`mode=web`，缺省） | 截图验收（`mode=screenshots`） |
|---|---|---|
| 你看到什么 | 浏览器里可交互的页面 + 检查截图，自己点"批准/修改" | AI 把检查截图**直接发到聊天里**，你看图 |
| 怎么批准 | 在网页上点按钮；Agent 不能代点 | 你在聊天里明确说"可以"，Agent 用 `oriel_review` 的 `decide` **逐字引用你的话**记录 |
| 保证有多强 | 较强：批准来自网页的请求来源和会话令牌 | 较弱：工具看不到聊天，是 Agent 代记；回执标注 `chat-attested-by-agent`，并绑定当时的截图哈希，内容变了就失效 |
| 适合 | 要点交互、看动画 | 看布局和外观，更快 |

### 接入目标（另有下游 mod-project，见文末）

| | `oriel-dev`（缺省） | `oriel-folder` |
|---|---|---|
| 写到哪 | 本仓库 `common/src/main/resources/assets/<命名空间>` | 你用 `folder` 指定的文件夹，通常是游戏的 `config/oriel/my-pages` |
| 适合 | 做 Oriel 自己的页面 | **独立的模组**、不想改 Oriel 仓库、只想先在游戏里看 |
| 游戏里怎么打开 | `/oriel open 命名空间:入口` | `/oriel open oriel_mine:入口`，保存文件自动热重载 |
| 限制 | 要改仓库 | 页面里不能写自己的命名空间（`你的命名空间:…`），用相对路径引用自己的资源；规划时会创建不存在的文件夹 |

文件夹目标不需要任何 Java 代码：游戏自带的 `oriel_mine:` 命名空间就是 `config/oriel/my-pages/` 这个文件夹。

## 连接

客户端启动 Node 运行 tools/mcp/oriel-preview.mjs，工作目录为 Oriel 仓库根。通用配置示例（按客户端配置表单填写）：

~~~json
{
  "command": "node",
  "args": ["<Oriel-UI 仓库绝对路径>/tools/mcp/oriel-preview.mjs"],
  "env": {
    "ORIEL_ROOT": "<Oriel-UI 仓库绝对路径>",
    "ORIEL_WORKSPACE": "<仓库外的工作区目录，用来放截图、日志和验收记录>",
    "JAVA_HOME": "<JDK 25 安装目录>"
  }
}
~~~

需要 Node 22+、Java 25 和本仓库可用的 Gradle 环境。项目模式默认读取目标版本 common/versions 下的构建资源，也可用 ORIEL_MC_JAR 指定客户端资源；资源 JAR 摘要纳入验收，内部版本必须与 profile 一致。ORIEL_MCP_STATE 可指定固定状态目录，必须在工作区内、仓库外；默认工作区 daily/当天日期/scratch/mcp-workflow。跨日继续任务应使用原状态目录。运行记录跟随工作区清理策略，长期保留应按工作区约定添加 KEEP。

首次检查会执行 :preview:installDist；源代码变化后重新构建，规则及引擎身份变化将使旧验收失效。规则使用目标 MC 26.1.2 / 26.2 / 26.3、Fabric / NeoForge、Oriel 0.11.0。浏览器预览无法替代六目标游戏验收。

初始化响应提供 instructions，resources/list/read 提供 oriel://rules/<topic>，prompts/list/get 提供 workflow、revise、integrate。客户端不支持这些能力时直接调用 oriel_rules。MCP 不能提升为客户端系统权限；工具独立检查前置条件。

## 页面包

~~~text
my-ui-package/
  assets/my_ui/ui/index.html
  assets/my_ui/ui/editor.css
  assets/my_ui/ui/editor.js
  assets/my_ui/lang/zh_cn.json
  assets/my_ui/textures/...
  browser/mock.js                 可选，仅浏览器
~~~

登记独立命名空间，不能覆盖 oriel/minecraft。Oriel 主题由工具快照时补入。源包最多 1200 文件、32 MiB；拒绝符号链接。完整 assets 与 browser 内容均纳入摘要；运行产物不放源包。

~~~json
{
  "projectId": "editor-demo",
  "title": "组件编辑器 · 前端示例",
  "sourceRoot": "<Oriel-UI 仓库绝对路径>/tools/mcp/examples/workbench",
  "namespace": "workbench_demo",
  "entry": "ui/index.html",
  "profile": {"minecraft": "26.1.2", "loader": "fabric"},
  "behavior": "javascript",
  "fixture": {},
  "businessContract": "仅编辑演示工作副本，不修改真实物品。",
  "scenes": [{"name": "initial", "description": "初始页面", "actions": []}]
}
~~~

fixture 是 oriel.data 初值，最多 64 KiB；场景 1–16 个，名称唯一，每个最多 100 条动作。动作支持 wait/move/click/wheel/key/type，沿用预览器解释；wait 最多 600 帧。需要游戏验收的目标使用 CSS 选择器。游戏侧 wait 把 16ms 预览帧换算为 50ms tick，wheel 把 px 换算为滚轮格；它们不保证动画逐帧完全一致。

首版默认共享普通 DOM/JavaScript。外部 Java 控制器和 Oriel v-if/v-for 模板缺少通用浏览器执行器，检查会明确阻断，不宣称自动移植。支持编写专门适配器后扩展，不能仅复制 HTML 冒充 Java 行为已运行。

## 工具合同

所有工具返回可读 text 和 structuredContent；失败时 isError=true，并返回 code/message/details。参数中未声明的键会拒绝。

| 工具 | 参数与默认 | 返回与条件 |
|---|---|---|
| oriel_rules | topic 默认 overview；可选 feature、profile | 规则原文或带行号的匹配段、来源、rulesHash、指引。没有匹配不意味着支持 |
| oriel_project | projectId/sourceRoot/namespace 必填；其余见示例 | 登记或更新项目，行为 javascript/java；更新后旧验收按内容摘要判失效 |
| oriel_status | projectId 必填 | stage、currentHash、check、review、integration、game、nextActions |
| oriel_web_preview | action=start/status/stop，默认 start；start 用 projectId，其他用 sessionId | 本地 URL、sessionId、运行状态；打开的网页固定到一个快照 |
| oriel_check | projectId；旧调用可用 target、strict | 项目模式强制严格检查及双尺寸场景，返回 revisionId、diagnostics、shots、覆盖/未覆盖项；旧 target 不生成验收记录 |
| oriel_snapshot | projectId 或旧 page；可选 scene/actions、width=640、height=360、frames=60、boxes | PNG、日志与执行结果；最大 1920×1080、600 帧。项目模式 strict |
| oriel_review | action=prepare/status/decide，默认 prepare；prepare 用 projectId、mode=web（缺省）或 screenshots；status 用 reviewId；decide 用 reviewId、decision=approved/rejected、quote | prepare 要求当前严格检查通过：web 返回固定版本验收页，screenshots 把检查截图作为图片直接返回给 Agent 转发给用户；status 返回用户决定及是否仍有效；decide 只用于 screenshots，必须逐字引用用户在聊天里的话 |
| oriel_integrate | action=plan/apply，默认 plan；plan 用 reviewId；apply 用 planId；target=oriel-dev（缺省）、oriel-folder（folder 绝对路径）或 mod-project（参数见文末） | plan 给出目标清单和摘要；apply 校验批准、快照和目标冲突，再写入。失败恢复本次写入，遇并行变动保留并报冲突 |
| oriel_game_preview | action=start/status/cancel，默认 start；start 用 projectId、scene=initial；其他用 jobId | start 立即返回作业，之后查询状态、日志和游戏回执；只管理自身进程 |
| oriel_pages | 无 | kit 页面名与 PNG 图标名，保留原工具名 |

topic：overview/html/css/javascript/components/design/workflow/integration/validation。能力的 supported/unsupported/approximate/unverified 要按规则段落中的运行环境与条件解读，不能把关键词命中视为支持判定。

结构诊断包括 ruleId、severity、file、line、column、message、suggestion、environment，可附 viewport/scene。无可靠位置时为 null；CSS 诊断来自实际引擎，不用正则重新实现 CSS。静态检查针对已知禁用用法和显式资源引用，不能证明任意动态 JavaScript 的全部行为。未声明的资源引用仍需运行验证。

## 网页桥接和验收

**正式交付流程里**，用户验收（网页点击，或截图验收里用户在聊天中的明确同意）之前不得启动任何游戏客户端或游戏内自测，包括基础设施探针、冒烟测试及其他页面。编译、无头 Oriel 引擎与浏览器测试可以先执行；不能以工具开发验证为由通过 CLI 绕过顺序。

工具把资源 ID 映射到本地 URL，注入独立 oriel 桥接，提供 fixture、事件订阅、翻译和 nextTick。默认 send 记录模拟消息并返回 false；不会伪造服务器成功。可选 browser/mock.js 在共享页面脚本前加载。item/slot/entity/model/scene 是带说明的占位；原生字体、布局和 GPU 效果仍要看引擎/游戏结果。浏览器支持的完整平台不是新增的 Oriel 能力。

验收页与被测页面使用不同端口；被测 iframe 不拥有验收页源权限。验收按钮需要原页面的请求来源和会话令牌。MCP 不暴露 approved=true，也不允许 Agent 代点确认。截图验收是唯一例外：用户在聊天里看图同意后，Agent 用 decide 逐字引用用户的原话记录，回执标注 approvalStrength=chat-attested-by-agent，并绑定当时的截图哈希；没有引用原话、验收模式不是 screenshots、截图或内容已变化，均不予批准。这里是本机协作记录，不是密码学自然人身份认证；拥有本机完全权限的程序仍可能伪造请求。

快照绑定 HTML/CSS/JS、图片/字体、主题、语言、fixture、适配内容、规则及引擎身份。用户批准对应具体版本；任一受约束内容改变后应重新检查和验收。用户拒绝记录修改意见。网页批准不会自动启动游戏，下一步仍由 Agent 调用接入工具。

网页进程独立于 stdio 会话，便于用户持续查看；最长存活 24 小时，可用 stop 提前结束，不注册开机启动。更改源文件后重新 start 或 prepare，不在用户验收期间悄悄刷新为另一个版本。

## 游戏接入范围

内置资源与文件夹目标：oriel-dev 写入本仓库 common/src/main/resources/assets/<namespace>；oriel-folder 复制进 folder 参数指定的绝对路径文件夹（不能在 Oriel 仓库内，不存在会创建），游戏里页面命名空间固定为 oriel_mine，页面内不能写自己的命名空间（FOLDER_NAMESPACE_REFERENCE），应使用相对路径。两者都复用 OrielScreens 和 /oriel open，不生成业务保存接口。browser/ 不安装；oriel 主题仍由库提供。下游工程使用文末 mod-project；其他目标返回 TARGET_ADAPTER_REQUIRED。

游戏预览用 oriel-folder 接入时，worker 会把同样的文件放进自己独立运行目录的 config/oriel/my-pages 再启动，不动你正在玩的游戏。你自己的游戏想看：把 folder 设为实际的 config/oriel/my-pages 即可，保存后热重载，用 /oriel open oriel_mine:入口 打开；也可以在"模板中心 → 我的页面"里看到并点开该文件夹里所有 .html。

游戏预览通过 -Pautopilot=mcp 和 -PorielMcpTask 启动独立开发客户端，载入页面、执行验收场景并截图后退出。回执包含 jobId、hash、pageUrl、实际 Minecraft 版本、错误与诊断、动作失败数和截图。任务超时 10 分钟；取消只结束本次任务进程树，不停止共享 Gradle daemon。源码接入、游戏启动和游戏验证是不同状态。

不自动执行服务端物品修改，不自动补容器菜单和权限逻辑。IME、真实槽位、网络、复杂 GPU 效果仍按业务逐项验收。游戏截图和日志在状态目录 jobs 内，源包不混入运行数据。

## 常见阻断与恢复

| code | 含义与下一步 |
|---|---|
| UNSUPPORTED_PROFILE | 目标版本/加载器未支持；选择明确 profile |
| CHECK_REQUIRED | 当前版本未通过严格检查；运行 check 并修复 |
| APPROVAL_REQUIRED | 没有用户本人批准；提供验收页（或截图）并等待 |
| QUOTE_REQUIRED / REVIEW_MODE / REVIEW_CLOSED / NO_SCREENSHOTS | decide 缺少用户原话、验收不是 screenshots 模式、验收已处理、或检查没产生截图 |
| FOLDER_REQUIRED / FOLDER_IN_REPOSITORY / FOLDER_NAMESPACE_REFERENCE | oriel-folder 缺少 folder、文件夹在仓库内、或页面里写了自己的命名空间 |
| STALE_REVISION / SNAPSHOT_CHANGED | 当前内容或固定快照变化；重新 check/prepare |
| TARGET_CONFLICT / STALE_PLAN | 目标文件变化或计划已执行；核对后重新 plan |
| TARGET_ADAPTER_REQUIRED | 目标工程暂无接入适配器 |
| PREVIEW_BUILD_FAILED | 构建失败；按返回日志修复环境或源码 |
| GAME_BUSY | 本状态目录已有游戏任务；查询或取消后再启动 |
| PATH_ESCAPE / SYMLINK | 路径越界或链接资源；改为包内真实文件 |

服务器重启后，项目与验收记录可从同一 ORIEL_MCP_STATE 恢复。网页存活状态通过 health 查询。若游戏 worker 被外部强制结束而没有最终回执，不能把遗留 running 状态当成通过；检查作业日志和拥有的进程后处理。

## 传输自检

没有动态 MCP 挂载能力的 Agent 可使用 tools/mcp/client.mjs，它启动真实 stdio server，执行 initialize 和 tools/call，不绕过工具直接调用内部实现：

~~~bash
node tools/mcp/client.mjs oriel_rules
node tools/mcp/client.mjs oriel_project <工作区里的参数.json> <工作区里的回执.json>
node --test tools/mcp/workflow.test.mjs
~~~

参数/回执文件均使用工作区路径。正式 UI 制作不通过测试伪造批准；测试夹具与用户验收分开。

## 下游模组目标：mod-project

前置条件同其他目标：当前页面已经检查通过，且用户本人批准固定版本。Node MCP 测试覆盖资源接入；不代表下游游戏验证通过。

| 参数 | 类型、缺省与限制 |
|---|---|
| projectRoot | 必填字符串，已有工程绝对路径；拒绝 ..、符号链接和 junction |
| resourcesDir | 可选字符串，工程内使用 / 的相对资源目录；优先已有 common/src/main/resources，否则从 src/main/resources、fabric/src/main/resources、neoforge/src/main/resources 选唯一已有目录；未知或多候选返回 RESOURCE_DIRECTORY_REQUIRED 和 candidates，需明确选择后重试；显式目录可以尚不存在 |
| modId | 可选字符串，小写字母开头、2–64 位小写字母/数字/下划线，不能为 oriel/minecraft；从这些资源目录的 fabric.mod.json 或 neoforge.mods.toml 的 [[mods]] 字面量 id 读取；缺失、占位符或多个 id 返回 MOD_ID_REQUIRED，需明确传入 |
| pageName | 可选字符串，默认 projectId；1–80 位小写字母/数字/下划线/连字符，首位为字母或数字 |

~~~json
{"action":"plan","reviewId":"review-…","target":"mod-project","projectRoot":"C:/work/my-mod","modId":"mymod","pageName":"settings"}
~~~

再用回执 id 作为 planId 调用 action=apply。写入 `<工程>/<resourcesDir>/assets/<modId>/ui/<pageName>/…`。自身资源全部在 ui/ 下时去除共同 ui/ 前缀，默认入口为 mymod:ui/settings/index.html；否则保留命名空间内完整目录树。保留批准的原文件字节及相对引用。HTML/CSS/JS/JSON 不能包含自身原命名空间引用，MOD_NAMESPACE_REFERENCE 要求改成相对路径后重新检查、验收；其他命名空间如 oriel:ui/ore.css 不受影响。

plan 和 apply 回执的 guidance 包含 openCode、registration、hotReload、projectType。见 [接入指南](integration.md) 和 [热更新](hot-reload.md)。只写资源，不改 Java、Gradle 或元数据，不安装 jar。

同一 plan 只可 apply 一次，重复返回 STALE_PLAN；新建 plan 可以幂等重复安装。计划后目标变化返回 TARGET_CONFLICT，沿用原子写入及回滚，不安装包外文件。gameVerified=false，必须在下游游戏验证；oriel_game_preview 对此目标返回 DOWNSTREAM_GAME_REQUIRED，不启动本库独立客户端。
