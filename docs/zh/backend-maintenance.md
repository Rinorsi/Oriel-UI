# 后端构建与性能门禁

这些任务不改页面、样式或黄金图。运行目录和验收日志放仓库外；完整验收使用项目 wrapper 与 JDK 25。

## Gradle 10 准备

用以下命令把实际执行路径中的 Gradle 弃用警告变成失败：

```bash
./gradlew --offline --no-configuration-cache compileJava :check --rerun-tasks --warning-mode fail
```

仓库已将 artifactId、exceptionFormat 改为赋值语法；Rhino 准备任务用注入的 FileSystemOperations 删除自己的生成目录；API 检查任务在配置期捕获文件、工具链和选项，避免执行期读取 Task.project。Shadow 的服务文件显式使用 INCLUDE，让合并器收到全部条目。

验证环境是 Gradle 9.7.1，Loom 1.18.2、ModDevGradle 2.0.147、Stonecutter 0.9.8、Shadow 9.6.1。Rhino relocated runtime 也声明正式publication，避免script-rhino发布依赖指向未发布项目。

**尚存插件弃用**：Loom 1.18.2 的 CompileConfiguration.getLockFile 调用执行期注入的 Project，嵌套模板构建中会触发 Gradle 10 禁用警告。因此全套 --warning-mode fail 当前仍会失败，不能称已完全兼容 Gradle 10。仓库没有绕过插件或隐藏该警告。上游相关记录：[Loom #1582](https://github.com/FabricMC/fabric-loom/issues/1582)（已关闭，旧版本的执行期Project访问报告；调用点不同，不代表本问题已修复）。2026-11-05检索getLockFile未找到精确匹配的issue；没有擅自发帖。升级 Loom 后用上述命令复查；普通 --warning-mode all 用于保留完整追踪。

2026-11-06 核对官方发布元数据及稳定版 [Loom 1.19.3 源码](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.19.3/fabric-loom-1.19.3-sources.jar)：CompileConfiguration.getLockFile 第359、361、365行仍读取 getProject()。未找到解决此调用点的可用稳定版本，因此保持1.18.2，没有采用预发布插件。

## JDK 25 编译警告

2026-11-06 核对 [JOML Central 元数据](https://repo.maven.apache.org/maven2/org/joml/joml/maven-metadata.xml)，最新仍为1.10.9；#398仍开放并指向未发布的1.10.10里程碑，因此保留下述窄例外和复现开关。

engine、kit、preview、mc-extension 以及 common/两个加载器开启 -Xlint:all 和 -Werror，测试源码也受检查。预览 Swing 控件及服务端异常类补齐 serialVersionUID，过时的物品附魔访问改为数据组件，重复悬空 Javadoc 已整理。

两处 Screen 构造器仅对 this-escape 作局部注解：DocumentDriver 构造器只保存 owner，不调用回调或发布它；实际回调从构造完成后的 init/resize 开始。保留可继承 API，不为消除静态分析误报把它改成 final。

**外部依赖例外**：MC 26.3 的 JOML 1.10.9 在 Java 1.2 classfile 中带有 Java 8 类型注解，JDK 25 报 classfile 警告。上游已记录 [JOML #398](https://github.com/JOML-CI/JOML/issues/398)。不改 Minecraft 自带库、不重打包它；26.3 的游戏适配层暂时仅关闭 classfile 类别，其他源码警告仍为错误。26.1/26.2 不需要该例外。以下开关可恢复完整外部 classfile 检查，当前会复现该上游问题：

```bash
./gradlew --offline :common:26.3:compileJava --rerun-tasks -PstrictExternalClassfiles
```

没有启用 Error Prone 编译器。当前缓存只有 error_prone_annotations，没有可离线验证的 error_prone_core/Gradle 集成。compileOnly 的注解包用于让 javac 读懂 Gson/Guava 发布的类型注解，不是 Error Prone 插件，也不会打进运行时。

Error Prone 后续接入以[官方安装说明](https://errorprone.info/docs/installation)为准：在 fork 的 javac JVM 上为 jdk.compiler/com.sun.tools.javac.{api,file,main,model,parser,processing,tree,util} 分别设置 --add-exports=…=ALL-UNNAMED，并为 code、comp 设置 --add-opens=…=ALL-UNNAMED；不能只作为普通源码编译选项。还需 core、完整传递依赖、-Xplugin:ErrorProne 与编译策略配置，或已验证的 Gradle 集成。2026-11-06 本机缓存仍无 error_prone_core，故未启用、未进行 JDK25 Error Prone 运行验证；普通 javac 严格编译通过不能替代该验证。

## 依赖 SHA-256 校验

gradle/verification-metadata.xml 固定实际构建、测试运行及 NeoForm 工具图的工件，并保留已经核对的其他平台分类工件。正常构建使用 Gradle 默认 strict 验证模式；没有全局关闭验证，也不信任整组通配依赖。Windows 上成功不代表 Linux 原生工件已经进入清单。

唯一刷新命令：

```bash
./gradlew --offline --no-configuration-cache --dependency-verification off writeDependencyVerification
```

刷新是明确的信任更新：检查 XML diff、确认依赖变化来源，再提交。该命令读取已解析的缓存和现有 XML，保留外部工件既有 SHA-256 及来源说明，再增补当前图的工件；不下载或修改共享缓存。在 Windows 刷新时不能删除已核对的 Linux 分类工件。废弃版本或旧哈希不会自动清除，须明确核对后从 XML 删除，不能把增量刷新当作依赖裁剪。下述三个版本的本地生成工件排除内容哈希校验，另检查本地来源与文件形态。两个仅在 settings 中加载的插件也被显式固定；升级它们时同时更新任务中的版本。

Gradle 自带的全量 --write-verification-metadata 模式会让本环境的 Loom 请求缓存缺少的可选平台描述，故刷新任务只解析实际构建图。XML 验证 JAR/ZIP 等工件内容，verify-metadata=false：不承诺 POM/module 描述或发布者签名验证。新增平台、依赖或工具版本时，缺少条目会明确失败；先核对该平台的确切工件，再增补清单。当前26.1.2、26.2、26.3的Linux x64原生工件按Mojang HTTPS版本清单核对大小与SHA-1后计算SHA-256，来源在对应条目中注明；其余首次校验值仍可能来自本机缓存。这不是发布者签名或全部依赖来源认证。

Rhino 源码归档另由 gradle.properties 固定的 rhino_sha256 检查；Minecraft/NeoForm 的专用下载由相应插件的清单机制管理，不能把它们误称为都由 Gradle 模块 XML 覆盖。

### 官方来源交叉核对（按需联网）

威胁模型：固定 SHA-256 防止固定之后的工件替换，但首次值若来自已污染缓存，仍可能固定错误内容。POM/module 能改变依赖图；发布者账号、官方仓库、TLS/本机代理或本地清单被攻破也超出内容哈希的保证。verify-metadata=false、verify-signatures=false 保持原状；下述工具不认证描述文件、签名或发布者身份。

只读命令从硬编码的 Maven Central 官方 HTTPS 地址下载指定坐标在 XML 中列出的每个工件，计算 SHA-256，与已提交值比较。它不读取 Gradle 缓存、不写 XML、不回退到镜像；来源不可达、坐标未固定、工件缺失或哈希不匹配均退出1。成功输出含时间、URL及哈希的JSON，应重定向到仓库外。需要 Node 与 curl，每个工件最多60秒、64MiB，不跟随重定向。

```bash
# 本机使用既有开发代理，其他环境可省略 ORIEL_VERIFY_PROXY。
ORIEL_VERIFY_PROXY=http://127.0.0.1:18080 node tools/verify-central.mjs org.joml:joml:1.10.9 com.google.errorprone:error_prone_annotations:2.48.0 com.google.code.gson:gson:2.14.0 > /external/central-source-check.json
node --test tools/verify-central.test.mjs
```

这独立于本机依赖缓存，仍信任 HTTPS、代理和官方仓库；无法证明首次下载时的来源或抵抗官方仓库本身被攻破。仅覆盖显式选择且 Central 存在的工件，不代表全部依赖认证；Fabric/NeoForge 专用仓库和 POM/module/签名仍需单独审查。它是显式联网审计，不加入正常离线构建；夹具回归进入 CI，CI 不自动重新建立信任。

### Loom本地生成的Minecraft合并包

Loom 1.18.2 的合并包已应用访问扩展；修改一行访问扩展会同时改变生成模块的十位哈希和 class 字节。它是本地构建结果，不能作为固定依赖内容。`gradle/generated-minecraft.json` 使用 schemaVersion=2、policy=`exclude-local-loom-output`，仅列出当前三个确切 Minecraft 版本与 `minecraft-merged-[0-9a-f]{10}` 模块/文件正则；不再固定 canonicalSha256 或 sourceArchiveSha256。XML 使用相同的受限正则例外，其他下载工件的 SHA-256 条目保持 strict 校验，没有信任整个 net.minecraft 组。

保留 Fabric 每个版本的 `verifyMinecraftContent` 任务名，Java 编译与根 `:check` 仍依赖它。任务要求恰好一个生成 JAR，实际路径必须位于当前工作树 `.gradle/loom-cache/minecraftMaven` 内，并匹配该版本的文件正则及父目录；使用真实路径检查，下载缓存中的同名文件也会拒绝。它检查来源与版本，不再承诺生成字节完整性。失败信息包含 Minecraft 版本、文件、期望/实际及清单更新方式；新增版本或 Loom 命名变化仍需审查。

访问扩展修改不需要更新任何校验清单。Loom 原始下载按 Mojang 版本清单的 SHA-1 核对，构建插件自身仍由原工件 SHA 校验；本地构建过程或首次缓存来源的完整性不等于发布者签名认证。此排除不覆盖下载依赖、未知模块形态或其他 Minecraft 版本。

```bash
./gradlew --offline :fabric:26.1:verifyMinecraftContent :fabric:26.2:verifyMinecraftContent :fabric:26.3:verifyMinecraftContent
```

有意升级 Minecraft 或 Loom 时，先审查输入及命名变化，再修改 JSON 的版本/文件规则，使用上面的 `writeDependencyVerification` 命令同步 XML 并审查 diff，保留外部下载依赖已有 SHA-256。不要直接复制未知依赖的实际哈希为期望值，也不要扩大为整个组的信任。

永久回归测试复现“注释一条真实访问扩展规则后，三个 Fabric 构建门禁应通过”，测试后恢复原文件，不启动游戏。需在没有其他进程修改本工作树时运行：

```bash
ORIEL_TEST_TMP=/external/existing-test-temp ORIEL_TEST_GW=/absolute/workspace/tools/gw.sh ORIEL_TEST_LOOM=1 \
  node --test tools/backend-boundaries.test.mjs tools/release-boundaries.test.mjs
```

普通 Node 回归不设置 `ORIEL_TEST_LOOM` 时跳过真实 Gradle 构建，仍检查例外范围、本地来源拒绝及诊断字段。

```bash
./gradlew --offline verifyDependencyGraph :check
```

verifyDependencyGraph 解析已声明的编译、运行、测试及 NeoForm 工具配置；出现未记录的工件或哈希变化即失败。

## Jar-in-Jar 共存验收

先构建当前模板和本地库产物，再分别启动两个仅监听本机的专用验收服务器：

```bash
./gradlew --offline templateCompile
node tools/server-smoke.mjs fabric /external/jij-fabric --jij --accept-eula
node tools/server-smoke.mjs neoforge /external/jij-neoforge --jij --accept-eula
```

EULA 必须由操作者确认；已有 eula=true 时不需要 --accept-eula。每次运行都有900秒上限，正常关闭使用 RCON stop，不停止全局 Gradle daemon。

脚本在外部运行目录生成两个父模组，各内嵌一份当前 Oriel 代码，测试元数据版本分别为0.10.1和0.10.2，范围都为 [0.10.0,0.11.0)。Fabric 使用标准 nested jars 元数据，NeoForge 使用 JarJar 元数据。模板在该模式仅 compileOnly 依赖 Oriel，不在顶层运行时添加本库，防止顶层开发副本使测试失真。

验收断言：两个父模组都加载、仅有一次 Oriel 初始化、运行版本为0.10.2，且真实 /oriel version 命令返回同一版本。日志与 result.json 留在外部目录。夹具的代码相同、元数据版本不同，证明加载器的选择和单实例启动；它不替代未来不同真实发行版本间的业务/API兼容测试。

正常客户端和服务端启动均记录加载器实际选中的版本；/oriel version 在对应侧输出该版本，不读取编译期常量。

## 正式产物的开发工具边界与许可检查

Build CI 在上传工件前运行下述发布包条目检查，并运行后端/发布边界Node回归及无游戏的冒烟计数测试；覆盖六个目标。CI运行目录在runner.temp。CI文件存在及本地复跑通过不代表对应提交的远端Actions已经成功，回执须另行核对；本仓库当前没有自动标签发布流程。

使用 `-Poriel_release` 构建玩家用 JAR，移除自动验收、演示巡游、Java 热重载和自附加 agent 的实现类。客户端先检查对应类资源是否存在，再启用开发入口；在缺少实现的正式包中，设置 `oriel.autopilot` 或 `oriel.tour` 也不会启用这些工具。资源存在性检查本身不会初始化开发类。

正式运行包同时排除 `DevInspector`、引擎 `inspect` 实现、Rhino `InspectorBindings` 及 `inspector.html` 等开发页面。宿主仅保留不引用开发实现类型的内部接缝；正式包即使设置 `client.inspector=true` 也不会启用 F12 检查器。普通开发构建仍支持该配置，开发环境默认启用。`tools/check-release.mjs` 将上述类与资源残留视为失败；此条目检查本身不等于实际客户端启动验收。

所有版本子项目的 JAR（含源码包）携带根目录 LICENSE 和 NOTICE，沿用带模组名的文件名；正式加载器 JAR 还应保留 Rhino 的 LICENSE/NOTICE/SOURCE 与字体许可。源码包保留源码，不适用运行包的开发类排除规则。

```bash
./gradlew --offline :fabric:26.1:jar :neoforge:26.1:jar -Poriel_release
node tools/check-release.mjs path/to/release.jar
ORIEL_TEST_TMP=/external/test-temp node --test tools/release-boundaries.test.mjs
# 对实际正式包执行隔离类加载及脚本启动（classpath.txt 由 :common:26.3:checkPublicApi 生成）
java tools/fixtures/ReleaseInspectorProbe.java path/to/release.jar common/versions/26.3/build/api-check/classpath.txt
```

`JAVA_HOME` 应指向项目 JDK，临时目录必须已存在且位于仓库外。发布包检查命令用于每个待交付加载器 JAR，缺项目声明、Rhino 声明、字体许可，或发现已排除的开发类/测试夹具时返回非零。该检查只核对工件条目，不代替完整许可审查、任意字节码链接检查或真实游戏启动。回归测试另用真实 DevFlags 源码验证实现类存在/缺失、开发参数开启/关闭四种组合，确认可选热重载调用在缺类时不执行。

## 单独的帧耗时回归任务

```bash
./gradlew --offline :preview:benchmarkFrames -PframeReport=/external/frames-actual.json
```

此任务不挂入 check：它受 CPU、JIT、GC 和其他程序负载影响，应在相同机器与空闲环境比较。需要 ORIEL_MC_JAR 或预览器能找到的相同 Minecraft 资源；缺图、控制器错误或页面集合变化会失败，不能用降级字体掩盖资源缺失。

固定427×240、scale 1、1 GiB堆。每页默认三个独立轮次，每轮60帧预热、120帧测量；每帧清空目标后运行正常 document frame/脏布局更新及 ImageCanvas paint。时间步固定为60Hz，度量不含页面解析、资源初始化和清屏。每轮求p50/p95，再取三个轮次的中位数，减少单次JIT/调度抖动。检查每一页的两项成本是否超出已提交基线的25%，任何一项超限都失败并打印具体页面与数值。

基线在 preview/benchmarks/frames.json，保留JDK、系统和处理器数量。它是 CPU 预览后端的成本，**不是游戏 GPU 帧率或视觉质量承诺**。页面的动画与脏标记照常执行，任务没有修改页面来制造较快结果。

明确审查后才更新基线：

```bash
./gradlew --offline :preview:benchmarkFrames -PupdateFrameBaseline -PframeReport=/external/frames-new.json
```

可用 benchWarmup、benchFrames、benchRounds 调整采样；配置与基线必须相同。benchRounds 为至少3的奇数。benchTolerance 默认0.25，改变它应在验收记录说明，不以扩大阈值掩盖回退。frameBaseline 可指向仓库外文件，用于验证人为压低的基线确实会让门禁失败。不要在失败时自动覆盖基线。
