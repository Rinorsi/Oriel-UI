# 参与维护 Oriel UI

先读 [AGENTS.md](AGENTS.md)、[文档维护约定](docs/zh/maintenance.md)与当前任务范围。能力与默认值以[中文手册](docs/zh/index.md)为准；历史任务报告和设计提案不是已实现能力的证明。

## 报告问题与提交改动

问题反馈使用本仓库的 [Issues](https://github.com/Rinorsi/Oriel-UI/issues)，代码评审使用 [Pull requests](https://github.com/Rinorsi/Oriel-UI/pulls)。目前没有启用 Discussions，也没有承诺额外的实时支持渠道。

报告问题时注明 Oriel 提交或版本、Minecraft 版本、加载器及版本、JDK、操作系统、最短复现步骤、预期与实际结果。IME/GPU/窗口问题另附实际输入法或显卡、GUI缩放和窗口尺寸。提供必要日志前先移除令牌、个人路径及其他敏感信息；不要将整个运行目录或凭据加入源码。

从已知基线建立独立分支或工作树，只提交本任务的改动。保留其他会话的未提交工作；需要合并时先备份和核对差异。缺陷先保留可复现用例，修复后作为回归；不能通过删除断言、刷新黄金或放宽容差掩盖变化。提交说明解释实际行为、验证命令和限制，区分未运行与失败，不把编译通过写成实机通过。

## 环境与文件位置

使用项目的 Gradle wrapper、JDK 25，Node 24 用于开发工具与工程回归。Windows shell 示例使用 Git Bash；只为当前进程设置环境，不要求修改系统或全局Git配置。支持目标见[版本维护](docs/versions.md)。

代码、测试和使用手册进仓库；日志、运行目录、临时文件、验收图和进度报告放仓库外工作区的当天目录。先按AGENTS.md创建当天目录，再将以下变量设置为实际的仓库外绝对路径：

```bash
export ORIEL_RUN_DIR=/absolute/external/daily/YYYY-MM-DD/run
export ORIEL_TEST_TMP=/absolute/external/daily/YYYY-MM-DD/scratch
mkdir -p "$ORIEL_RUN_DIR" "$ORIEL_TEST_TMP"
```

## 验证改动

根据改动选择验证；已有缓存完整时可以加 `--offline`，否则先正常解析依赖。以下命令不替代具体缺陷的回归：

```bash
node --test tools/backend-boundaries.test.mjs tools/release-boundaries.test.mjs
./gradlew compileJava :check
```

`:check` 包含当前版本的公共API快照、边界和下游模板门禁。改网络、菜单或服务端物品编辑逻辑时，再运行两加载器服务端回归；GameTest mock不等于真实连接验收：

```bash
./gradlew :fabric:26.1:runGameTest :neoforge:26.1:runGameTestServer
```

页面和预览验证参数见[检查与验收工具](docs/zh/validation.md)，后端发布包、严格编译及依赖信任更新见[构建与维护](docs/zh/backend-maintenance.md)。既有 Build CI 会检查六个正式包；以对应提交的运行结果为准，不能用本机结果替代。提交前按AGENTS.md运行外部工作区的 `tools/repo-hygiene.sh`，并显式传入待提交仓库或工作树路径。

## API、版本和安全边界

公共API与0.10迁移合同见[安装与稳定性](docs/api/setup.md)。`public` 不等于下游稳定API；不要依赖 `internal` 或 `@ApiStatus.Internal` 类型。1.0之前不兼容的公共API变更提升次版本，消费者限制并验证同一次版本范围；当前没有额外承诺统一的弃用宽限期。

有意更改API快照前先审查下游影响，同步迁移说明和模板；不能直接用 `-PupdateApi` 让意外破坏变绿。新增MC版本要提供对应加载器构建与运行证据，不能只放宽模组元数据范围。网络消息始终由服务器验证权限、载荷、槽位和当前权威状态，不能把通道名或合法JSON当成授权。

变更字段、默认值、限制或命令时，同次更新手册、参数表和示例。新增依赖和跨平台分类工件必须核对确切来源与哈希，不能关闭strict验证或添加整组信任通配。

## 来源、发布与回退

保留 [LICENSE](LICENSE)、[NOTICE](NOTICE)、Vellum版权及Rhino/字体等第三方声明。AUI只供思路参考，不能复制其代码或照搬结构命名。新增素材需记录来源及许可，不能将来源未明的素材默认写成可发布。

当前没有自动发布承诺；Git合并不等于发布到模组平台或Maven。发布需分别具备任务授权、目标账号/项目、凭据及产物验收。回退共享基线时使用经过审查的反向提交，保全用户WIP；不要用强推或硬重置覆盖其他人的工作。
