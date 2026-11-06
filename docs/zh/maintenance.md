# 文档维护约定（所有 agent 必读）

[文档入口](index.md)

本套手册是页面作者的能力合同。**新增、删除、改名、改变默认值/限制/行为的代码与对应文档必须在同一次变更交付。合并其他分支也适用。** 仅改代码、让下一位页面作者自行查源码补清单，不算完成。

## 适用基准与事实等级

本轮文档核对日期：2026-11-05；源码基准：`9928647`；项目版本：0.11.0。分支名称不代表版本合同，读取文档时使用与正在运行的 JAR 同一版本的手册。

| 标记 | 允许表达的结论 |
|---|---|
| 源码核对 | 有实现路径、接口/默认值与源码对应；不等于运行验证 |
| 解析核验 | 指定语法可以解析；不等于绘制或浏览器等价 |
| 预览核验 | 在注明尺寸/状态/资源下通过预览检查或截图 |
| 游戏核验 | 必须有确切 MC 版本、加载器、操作与结果 |
| 未支持 / 近似 / 未验证 | 写清边界和替代办法；不写成“完整支持” |

不允许把编译成功写成已联网、把 h5ref 退出码 0 写成像素等价、把 checkUi 无警告写成所有交互正确。具体运行证据保存在工作区，不把日志复制进用户手册。

## 改什么，就更新什么

| 变更源 | 必须同步的文档/字段 |
|---|---|
| 属性注册、Prop、默认样式、MC PropertySchema | `css-guide.md`：属性名、可接受值、默认、继承、重排、插值、别名、示例、语义例外；`capabilities.md` |
| CSS tokenizer/parser、选择器、at 规则、媒体查询 | CSS 表的语法范围、诊断、降级；`ui-workflow.md` 的浏览器约束 |
| HTML、原生/替换元素、表单 | `elements.md`：标签、属性、类型、默认、单位、组合条件、预览/游戏差异 |
| ore.css、Shell、Widgets、Motion、Fonts、Particles | `components.md`、`design.md`、`motion.md`：类、必需子结构、状态类、data-*、CSS 变量、Java 接线要求 |
| 脚本 bindings、模板、沙箱 | `scripting.md`：JS 名称、参数、返回、事件、生命周期、不支持项 |
| kit/bind 的 Java ViewModel 与 o-* 指令 | `bindings.md`：API、路径语法、指令类型/缺省、key、诊断与生命周期；`scripting.md`、`scripting.md`、`java-api.md`、`capabilities.md` 与索引同步入口 |
| OrielScreens / Driver / HUD / Server / Session / Entities | `java-api.md`、`integration.md`、`pages.md`、`hud.md`、`containers.md`、`development.md`：签名、线程、关闭/重载、错误、稳定性 |
| 页面推送数据、消息频道、保存结果 | 页面专属字段合同（如 `server.md` 里的服务端页面合同）：方向、类型、可空、缺省、范围、只读、请求/回执、失败条件 |
| 朗读器读取的内容、aria 属性、实时区域 | `narration.md`、`elements.md` |
| 沙箱限额、`limits.*` 与 `client.*`/`server.*` 设置键 | `security.md`、`scripting.md` 的限额一节、`integration.md` |
| 不兼容变更、命令改名、默认值或可见行为变化、版本号 | `upgrading.md`（"之前 → 现在"迁移表）、`../../CHANGELOG.md`、`principles.md`（若触及准则）、`../../template/README.md` 与模板里的依赖范围 |
| 新增或改名公开类（`dev.oriel.mod.client` / `server`） | `api/*.txt` 快照（`-PupdateApi`）、`tools/api/ApiCheck.java` 与 `tools/backend-boundaries.test.mjs` 的名单、`java-api.md` |
| 新增或改名示例页（showcase / demo） | `tools/showcase/index.json` 条目，运行 `node tools/showcase/query.mjs --write-doc` 刷新 `showcase.md` 的效果表 |
| 热更新机制、开发环境读取源码的方式 | `hot-reload.md`、`quickstart.md`「边改边看」、`page-rules.md` |
| 配置、限额、协议、依赖与发布坐标 | `security.md`、`integration.md`、`server.md`、`capabilities.md`、`../../template/README.md` |
| i18n 接口、语言 key、回退逻辑 | `components.md` 的字体/翻译段、`scripting.md`、新增的专属 i18n 参考及索引；不得覆盖原流程 |
| preview CLI / MCP / h5ref / 脚手架 | `validation.md`、`ui-workflow.md`、工具 README：参数、默认、退出码、路径、覆盖范围 |
| 增删模块、MC 版本、加载器 | `architecture.md`、`integration.md`、`versions.md` 与本页适用基准 |

## 每个新增能力的文档字段

MCP 工作流、规则资源、验收状态与接入适配器变化时，同步 ui-workflow.md、mcp.md、agent-prompts.md、validation.md 和 capabilities.md：工具参数、规则来源、人工批准边界、版本失效及适配范围。prompts 直接读取文档；测试覆盖禁止自行批准与陈旧验收绕过。

新增表项至少包含：**名称、用途、类型/语法、默认或缺省行为、允许值/单位/范围、运行环境、前置条件、最小例子、限制/错误、验证等级**。

新增消息/数据还必须写：发送方和接收方、完整替换或局部修改、null 和省略的区别、谁有权限写、成功/拒绝回执、重载与重复提交语义。不要只贴一份示例 JSON，让读者猜其余字段。

重命名不能只改表头：更新示例、链接、脚手架说明、检索别名和旧版迁移说明。删除能力也要给替代路径。仅迁移内部实现包而公共行为未变时，不应改页面语法。

## 一次变更的完成流程

1. 开始前读仓库 `AGENTS.md` 和本页。确认正在改的能力对应哪张表，读该表的当前合同。
2. 改能力的维护者核对实现；页面作者正常只读手册。发现文档无法决定的写法，先用已有支持的替代办法；不可自行猜测为可用。
3. 同步上述关联文档，确保所有入口指向同一份定义。新例子在预览器加载；新业务字段做有意义的请求/回执验证。
4. 检查链接、CSS 注册项覆盖、元素属性/消息字段覆盖、过时术语、默认值与错误提示。测试存在时运行对应测试；测试不存在时如实说明，不能写“自动门禁已启用”。
5. 合并前再次对照目标分支最新变化；合并后以最终代码复核文档，尤其是解析器、国际化、协议/API、模板依赖。不要用整文件选择 ours/theirs 丢掉另一边新增能力。
6. 最终交付说明本次同步了哪些手册。源码检查与游戏验收分开报告。更新了能力但没更新文档，任务仍未完成。

## 自动检查与“实时更新”的准确含义

文档是仓库中的版本化文件，没有后台服务会自动观察所有源码并改写文字。这里要求的及时更新是：**行为变更当次更新，合入前完成，不能延期给下一位 agent。**

已有 `CssReferenceTest` 检查机器探测表是否过期，更新命令为 `ORIEL_UPDATE_DOCS=1 ./gradlew :engine:test --tests '*CssReferenceTest'`。它的输出路径是 **css-reference.md**，不生成或比较 **css-guide.md**：前者是机器探测表，后者是页面作者的语义合同。尚无覆盖全部文档字段的持续集成门禁，不能声称全部文档已经全自动守护。

CSS 能力改变时应同时更新生成表与 css-guide 的相应字段/示例/限制。候选值探测通常不穷尽合法取值，也不证明能绘制；不能让生成器抹掉 sticky、滤镜插值、毛玻璃等行为限制。使用入口始终先指向 css-guide；机器表作为维护补充，不能覆盖语义结论。

维护者新增自动化时必须明确检测范围：属性集合、默认、示例解析、API 签名、配置键、消息字段与视觉行为是不同层次。当前没有的命令不能先写进使用流程。

## 文档归属与并行修改

- `docs/zh/index.md` 是唯一中文导航；同一字段只保留一个权威定义，其他页面链接过去。旧路径可保留短入口，不留相反的旧合同。
- AUI 的研究结论不描述 Oriel；Vellum 上游说明必须按本 fork 复核后才能纳入。保留版权，不把来源研究混入使用说明。
- 维护规则放本页和 AGENTS；方案提议、问题单、验收记录、分支冲突表放仓库外工作区。
- 其他会话在运行时，不为了文档检查停止共享 Gradle daemon、切换它的分支或覆盖它的工作文件。只处理自己的文档差异。
