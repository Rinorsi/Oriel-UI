# MCP 页面制作与接入

[入口](index.md) · [完整流程](ui-workflow.md) · [Agent 提示词](agent-prompts.md) · [检查工具](validation.md)

Oriel MCP 为本机开发服务，使用 Node.js stdio；它不是安装模组后自动开放的网络端口。网页服务仅监听 127.0.0.1，游戏接入使用独立开发客户端。源码实现与实际运行验收应分别记录，本页不代表全部版本的游戏验收已完成。

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

首次检查会执行 :preview:installDist；源代码变化后重新构建，规则及引擎身份变化将使旧验收失效。规则使用目标 MC 26.1.2 / 26.2 / 26.3、Fabric / NeoForge、Oriel 0.10.0。浏览器预览无法替代六目标游戏验收。

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
| oriel_review | action=prepare/status，默认 prepare；prepare 用 projectId，status 用 reviewId | prepare 要求当前严格检查通过，返回固定版本验收页；status 返回用户决定及是否仍有效 |
| oriel_integrate | action=plan/apply，默认 plan；plan 用 reviewId；apply 用 planId；target=oriel-dev | plan 给出目标清单和摘要；apply 校验批准、快照和目标冲突，再写入。失败恢复本次写入，遇并行变动保留并报冲突 |
| oriel_game_preview | action=start/status/cancel，默认 start；start 用 projectId、scene=initial；其他用 jobId | start 立即返回作业，之后查询状态、日志和游戏回执；只管理自身进程 |
| oriel_pages | 无 | kit 页面名与 PNG 图标名，保留原工具名 |

topic：overview/html/css/javascript/components/design/workflow/integration/validation。能力的 supported/unsupported/approximate/unverified 要按规则段落中的运行环境与条件解读，不能把关键词命中视为支持判定。

结构诊断包括 ruleId、severity、file、line、column、message、suggestion、environment，可附 viewport/scene。无可靠位置时为 null；CSS 诊断来自实际引擎，不用正则重新实现 CSS。静态检查针对已知禁用用法和显式资源引用，不能证明任意动态 JavaScript 的全部行为。未声明的资源引用仍需运行验证。

## 网页桥接和验收

用户网页验收之前不得启动任何游戏客户端或游戏内自测，包括基础设施探针、冒烟测试及其他页面。编译、无头 Oriel 引擎与浏览器测试可以先执行；不能以工具开发验证为由通过 CLI 绕过顺序。

工具把资源 ID 映射到本地 URL，注入独立 oriel 桥接，提供 fixture、事件订阅、翻译和 nextTick。默认 send 记录模拟消息并返回 false；不会伪造服务器成功。可选 browser/mock.js 在共享页面脚本前加载。item/slot/entity/model/scene 是带说明的占位；原生字体、布局和 GPU 效果仍要看引擎/游戏结果。浏览器支持的完整平台不是新增的 Oriel 能力。

验收页与被测页面使用不同端口；被测 iframe 不拥有验收页源权限。验收按钮需要原页面的请求来源和会话令牌。MCP 不暴露 approved=true，也不允许 Agent 代点确认。这里是本机协作记录，不是密码学自然人身份认证；拥有本机完全权限的程序仍可能伪造请求。

快照绑定 HTML/CSS/JS、图片/字体、主题、语言、fixture、适配内容、规则及引擎身份。用户批准对应具体版本；任一受约束内容改变后应重新检查和验收。用户拒绝记录修改意见。网页批准不会自动启动游戏，下一步仍由 Agent 调用接入工具。

网页进程独立于 stdio 会话，便于用户持续查看；最长存活 24 小时，可用 stop 提前结束，不注册开机启动。更改源文件后重新 start 或 prepare，不在用户验收期间悄悄刷新为另一个版本。

## 游戏接入范围

首版适配器 oriel-dev 写入本仓库 common/src/main/resources/assets/<namespace>，复用 OrielScreens 和 /oriel open，不生成业务保存接口。browser/ 不安装；oriel 主题仍由库提供。外部非标准模组工程需新增适配器，返回 TARGET_ADAPTER_REQUIRED，不能猜路径覆盖项目。

游戏预览通过 -Pautopilot=mcp 和 -PorielMcpTask 启动独立开发客户端，载入页面、执行验收场景并截图后退出。回执包含 jobId、hash、pageUrl、实际 Minecraft 版本、错误与诊断、动作失败数和截图。任务超时 10 分钟；取消只结束本次任务进程树，不停止共享 Gradle daemon。源码接入、游戏启动和游戏验证是不同状态。

不自动执行服务端物品修改，不自动补容器菜单和权限逻辑。IME、真实槽位、网络、复杂 GPU 效果仍按业务逐项验收。游戏截图和日志在状态目录 jobs 内，源包不混入运行数据。

## 常见阻断与恢复

| code | 含义与下一步 |
|---|---|
| UNSUPPORTED_PROFILE | 目标版本/加载器未支持；选择明确 profile |
| CHECK_REQUIRED | 当前版本未通过严格检查；运行 check 并修复 |
| APPROVAL_REQUIRED | 没有用户本人批准；提供验收页并等待 |
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
