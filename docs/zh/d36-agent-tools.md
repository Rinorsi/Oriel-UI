# MCP 诊断与协作工具 v1

这些增量工具保留既有工具JSON/structuredContent及错误code/message/details；新增字段向后兼容。当前运行环境仍是离线预览，未实现连接/控制现有游戏。

## 能力发现与构建状态

- oriel_capabilities：可选contractVersion=1、profile；返回schemaVersion、每个当前注册工具的真实inputSchema、输出编码/版本/错误合同、能力状态、限制、源码摘要和未覆盖项。未知合同/目标版本明确拒绝。不构建、不把源码声明当运行时验证，why能力必须先合入并构建R03。旧工具没有完整输出JSON Schema，此入口不伪造它们。
- oriel_build_status：无参数，只读Git Bash /tmp的oriel-gradle.lock和三个oriel-gslot目录，返回observedAt、occupiedSlots、benchmark/slots及非原子的buildAllowedHint。目录约定缺owner/PID/心跳，明确liveness unknown；不能据此证明死锁、删锁或绕过gws。

MCP在工作区tools/gws.sh存在时通过它执行Gradle（包含ensurePreview和测试中的gradle --version），默认及预览构建为70分钟含槽位等候；调用者显式超时仍生效，游戏任务保留原600000ms限制。预览构建完整日志及路径写在STATE/jobs与preview-build.json。没有该脚本的普通仓库仍用原生wrapper。状态查询不代表有owner/心跳租约；gws限制全机并发，不提供同工作树跨STATE互斥，客户端须不同树或由Lead串行。

## JSONL诊断与读取

schema在tools/mcp/schemas/diagnostic.v1.json。采集默认关闭；启动MCP前设ORIEL_MCP_DIAGNOSTICS=1开启，每进程一个diag会话，必有schemaVersion/sessionId/递增seq/time/environment/phase/severity/code/message；补requestId、durationMs与data.tool，不记录参数、输出正文或用户输入。文件在本STATE的diagnostics目录，不写stdout。关闭时不构造事件或写日志；已有历史日志仍可读。当前仅MCP工具完成/失败，不是Java引擎trace或任意日志读取。

oriel_logs不带sessionId列会话、currentSessionId及recordingEnabled（关闭时currentSessionId=null）；指定sessionId可传after（默认0）、limit（默认100、最多200）、levels（info/warning/error），返回events/nextCursor/remaining/dropped/truncated。每会话最多10000事件或8MiB，超额停止存新事件并计dropped；损坏JSONL、未知合同或会话/序号不一致明确拒绝。截断不是完整证据；写入失败在stderr报证据不完整。

## Selector why-tree

oriel_inspect沿R03已有page/selector/element/frames合同，frames默认1、最多600，新增maxNodes默认200、最多500。只调用当前工作树已集成的preview --why，不切到work/d36、不改引擎。保留explanations/exitCode/output，增加whyTrees；把引擎的queryResults、谓词轨迹、候选、声明赢家组织成树，不从computed猜原因，不提供布局约束trace。

无--why或有效selector-explanation则WHY_UNAVAILABLE；坏JSONL拒绝。最多50条原始解释、512000字符，截断明确标记；每棵树独立节点预算，raw解释仍保留以兼容R03。基于b03985a的独立mcp树只能测适配器与缺能力拒绝；合入并构建R03后才可实际运行解释CLI。

## 截图验收分页

oriel_review.prepare(mode=screenshots)默认首12图，shots/shotHashes仍绑定完整集合；增加pagination.total/offset/returned/remaining/nextOffset/complete/unpresentedCount。action=page、reviewId、offset、limit（1–12）继续，32图分12/12/8。每页核版本/摘要，缺图不略过。

返回记录绑定索引及hash，即使不同场景图片相同也不能跳页。未完整返回时decide(approved)报REVIEW_INCOMPLETE；rejected不要求全部页。返回不等于用户看完，Agent仍须原样展示并等用户明确决定，逐字quote，保留chat-attested-by-agent强度。旧pending截图验收须page补齐；已完成批准不被重写。

## 独立黄金图候选

oriel_baseline动作：

1. propose：baseline为仓库相对已有PNG，仅限preview/src/test/snapshots、engine/src/test/resources、kit/src/test/resources；candidate为本STATE内相对PNG路径；reason解释变化和浏览器依据；oracle可指定同STATE的固定浏览器参考PNG。冻结旧/新/oracle，返回旧新两图及改变像素数/最大通道差/hash。仅支持同尺寸非交错RGB/RGBA8，读取上限8MiB，候选按仓库卫生规则不超过300KiB。propose/status不改黄金图。
2. status：proposalId，查询并返回固定图；已安装状态核目标摘要。
3. decide：proposalId、decision=approved/rejected、逐字quote；独立kind=human-baseline，不能拿页面批准替代，Agent不得编原话。
4. promote：仅该候选独立批准、黄金/固定图未变、oracle冻结且候选RGBA逐像素一致，才事务替换这一张PNG。当前树引擎/预览/kit/脚本/MC扩展源码、手册、MCP实现及预览lib的sourceFingerprint须仍一致；代码/规则/资源改变即重新审批。无oracle、参考不等、过期批准或未知锁均拒绝。备份/回执放工作区。该指纹不自动验证外部Minecraft资源jar或浏览器捕获过程，生产者仍须提供独立证据。

浏览器参考来源由捕获者提供；只标candidateMatchesProvidedBrowserPixels=true、browserRuntimeVerified=false，不能自动证明来源或Chrome版本，报告须补真实oracle证据。仅支持PNG，不改frames.json/布局夹具、不放宽容差。同黄金路径用工作区当天共享目录排他；未知锁不自动删除，跨日期由Lead协调，不冒称有心跳租约。

## 入口与边界

oriel_rules的orchestration/agent-tools/benchmark主题提供合同。iterate、deliver、engine-research/implementation按任务授权；新工具不授权发布、开游戏或代用户批准。错误沿用isError=true及code/message/details，不静默换对象。
