# Oriel UI workflow MCP

动画开工：`oriel_rules({topic:"performance"})` 读取权威性能规范；包括密集粒子、原生 fillQuad、清理与前后测量要求。

本地 stdio 入口：node tools/mcp/oriel-preview.mjs。使用说明与完整参数见 [MCP 手册](../../docs/zh/mcp.md)，调用顺序见 [制作流程](../../docs/zh/ui-workflow.md)，可复制指引见 [Agent 提示词](../../docs/zh/agent-prompts.md)。

Node 22+、Java 25；工作目录为仓库根，ORIEL_WORKSPACE 和 ORIEL_MCP_STATE 指定仓库外状态目录。stdout 仅用于 JSON-RPC。旧 oriel_pages/check/snapshot 工具仍提供；项目模式增加严格诊断、固定版本、人工验收与接入关卡。

client.mjs 是真实 stdio 协议客户端，用于不支持动态工具挂载的 Agent 和集成测试。网页验收页由用户本人操作，Agent 不代点批准。examples/workbench 是工具的前端演示夹具，不是完整编辑器业务实现。

下游模组资源接入：oriel_integrate 的 target=mod-project，projectRoot 为工程绝对路径，可指定 modId/resourcesDir/pageName。只安装已检查且用户批准的资源；回执给出 Java 打开代码与热更新指引，不改工程代码，不启动下游游戏。完整参数与限制见 [MCP 手册](../../docs/zh/mcp.md#下游模组目标mod-project)。
