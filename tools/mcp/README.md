# Oriel UI workflow MCP

下游首次接入：`oriel_scaffold` 只传 projectRoot 默认只读返回检测值、待补项和清单。补齐参数后 prepare=true 生成并严格检查固定骨架；通过截图验收、用户聊天批准后 apply=true + reviewId，只新建不存在的 HTML/Java，保留已有文件。详细字段与热更新模式见 [工具参数](../../docs/zh/mcp.md#下游骨架oriel_scaffold)。测试：`node --test tools/mcp/scaffold.test.mjs`，ORIEL_TEST_TMP 指向工作区临时目录；不访问真实下游工程。

动画开工：`oriel_rules({topic:"performance"})` 读取权威性能规范；包括密集粒子、原生 fillQuad、清理与前后测量要求。

本地 stdio 入口：node tools/mcp/oriel-preview.mjs。使用说明与完整参数见 [MCP 手册](../../docs/zh/mcp.md)，调用顺序见 [制作流程](../../docs/zh/ui-workflow.md)，可复制指引见 [Agent 提示词](../../docs/zh/agent-prompts.md)。

Node 22+、Java 25；工作目录为仓库根，ORIEL_WORKSPACE 和 ORIEL_MCP_STATE 指定仓库外状态目录。stdout 仅用于 JSON-RPC。旧 oriel_pages/check/snapshot 工具仍提供；项目模式增加严格诊断、固定版本、人工验收与接入关卡。

client.mjs 是真实 stdio 协议客户端，用于不支持动态工具挂载的 Agent 和集成测试。网页验收页由用户本人操作，Agent 不代点批准。examples/workbench 是工具的前端演示夹具，不是完整编辑器业务实现。

下游模组资源接入：oriel_integrate 的 target=mod-project，projectRoot 为工程绝对路径，可指定 modId/resourcesDir/pageName。只安装已检查且用户批准的资源；回执给出 Java 打开代码与热更新指引，不改工程代码，不启动下游游戏。完整参数与限制见 [MCP 手册](../../docs/zh/mcp.md#下游模组目标mod-project)。

绘制诊断新增 oriel_paint（page，width/height/frames/element 可选）：复用无头预览，输出命令和缓存计数，不启动游戏。详见 [参数与限制](../../docs/zh/d36-paint.md)。
