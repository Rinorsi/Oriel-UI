# Minecraft 版本适配

[入口](index.md) · [接入指南](integration.md) · [后端构建与维护](backend-maintenance.md)

Oriel 用 [Stonecutter](https://stonecutter.kikugie.dev) 从同一份源码树为三个 Minecraft 版本构建：

| Minecraft | NeoForge | Fabric | Java |
|---|---|---|---|
| 26.3 | 26.3.0.26-beta | Loader 0.19.5，Fabric API 0.161.0+26.3 | 25 |
| 26.2 | 26.2.0.88 | Loader 0.19.5，Fabric API 0.161.0+26.2 | 25 |
| 26.1.2（项目名 26.1） | 26.1.2.126 | Loader 0.19.5，Fabric API 0.155.3+26.1.2 | 25 |

每个版本有各自的 Gradle 子项目，以版本命名：`:common:26.1`、`:neoforge:26.1`、`:fabric:26.1`。它们的设置在 `stonecutter.properties.toml`。引擎、Rhino 和预览器只构建一次，所有版本共用。

jar 命名为 `oriel-<加载器>-<MC 版本>-<版本>.jar`（如 `oriel-neoforge-26.1.2-0.11.0.jar`），位于 `<加载器>/versions/<MC 版本>/build/libs/`。发布时会为每个版本、以同一个 Oriel 版本号发布 `dev.oriel:oriel-{common,neoforge,fabric}-<MC 版本>`。

源码以 26.3 为活动版本。26.x 内部的 API 差异使用 `>=26.2`、`>=26.3`、`<26.3` 等 Stonecutter 条件，包名与键名迁移放在 `stonecutter.gradle`。**提交前恢复活动版本 26.3。**

26.1 项目实际使用 Minecraft 26.1.2，是主要验收版本。26.1/26.2 使用 GLFW，26.3 使用 SDL；26.1 的渲染目标、纹理上传与世界创建接口通过 `>=26.2` 条件适配。开发期 tour 仍只在 26.3 启用。

## 26.2 与 26.3 的差异

26.2 上一切都能工作，只有开发 tour（`-Ptour`）仅限 26.3。26.2 就是 26.3 的代码，差别体现为 `>=26.3` 注释，以及 `stonecutter.gradle` 里的 `blaze3d`/`renderpearl` 重命名：

- 渲染 API 是 `com.mojang.blaze3d.pipeline.RenderPipeline` 和 `com.mojang.blaze3d.textures.FilterMode`/`GpuTextureView`；26.3 把它们移到了 `com.mojang.renderpearl.api.*`。
- 按键是 GLFW 的，不是 SDL 的：`KeyEvent(key, scancode, modifiers)` 持有 GLFW 键码，`KeyNames` 也是 GLFW 的。鼠标按键是 GLFW 的（0 左、1 右、2 中）；没有 `onTextInputFocusChange`。
- `PoseStack` 有 `mulPose(Quaternionfc)`，但没有 `rotate` 或 `rotateDegrees`；`SubmitNodeCollection` 有无参构造器，`submitModel` 接收的是精灵和破坏覆盖层，而 26.3 接收的是 `UvMapping`。
- `GuiGraphicsExtractor.setTooltipForNextFrame` 没有带物品样式的 `replaceExisting` 重载（NeoForge 的只接收物品栈和样式）；`ScreenNarrationCollector.update` 没有 `NarrationTrigger`。
- `MouseHandler.onButton`、`onScroll`、`onMove` 和 `KeyboardHandler.keyPress`、`charTyped` 是私有的：它们被放宽了可见性（`#? if <26.3`），`PictureInPictureRenderer.textureView` 则连同 `blaze3d` 类型一起被放宽。
- 形状着色器的顶点打包方式不同。26.2 的 `BufferBuilder` 只写 `Position`、`Color`、`UV0`、`UV1`、`UV2`、`Normal` 和 `LineWidth`（没有 `UV3`），所以在 26.2 上 `SdfRenderState` 把盒子四边扩大同样的边距，并把边距放进 `Position.z`，着色器据此算出半尺寸。阴影的扩散值以 float 的位模式放在 `UV2` 里传递。26.2 的管线定义了 `ORIEL_26_2`，由它选取 `sdf_box.vsh` 和 `.fsh` 里属于 26.2 的部分：用 `#moj_import` 而不是 `#include`，属性和 varying 不写 `layout(location)`，因为 26.2 按名字匹配它们。一个编译失败的管线在资源重载之前一直保持失败，所以 `SdfRenderState.available()` 里的重试只有在重载之后才有用。
- 开发 tour 是关闭的：`DevTour` 和 `TourCursor` 是空桩（它们的 `else` 块），因为 tour 要通过 SDL 隐藏和读取系统光标。

## 六目标冒烟

在 Git Bash 中运行：

```sh
bash tools/smoke-all.sh /absolute/path/outside/the/repo/run-smoke
```

脚本先编译完整矩阵，再依次启动 Fabric、NeoForge 的 26.1/26.2/26.3 客户端；每个客户端限时 600 秒。`-Pautopilot=smoke` 进入世界后打开首页和组件目录，等待页面稳定并截图，记录页面错误与检查失败数，然后正常退出。运行目录为 `<根目录>/<loader>-<版本>`，截图为其下的 `screenshots/oriel_smoke_gallery.png` 和 `oriel_smoke_catalog.png`。

根目录也可用 `ORIEL_RUN_DIR` 指定，未指定时使用相邻工作区的 `daily/<当天>/run-smoke`。根目录必须位于仓库外；日志在 `logs/`，完整结果及截图路径在 `summary.tsv`。缺少完成标记、截图未更新、页面错误、自动检查失败、超时或非零退出码都会使脚本失败，单个目标失败后仍继续检查剩余目标。

默认离线构建。依赖未缓存时可加 `--online`；工作区已有 `PROXY_ARGS` 时会一起传给 Gradle，其他 Gradle 参数放在 `--` 后。脚本不安装软件，也不启动代理。

脚本自身的结果判定可用假 Gradle 进程验证，不会启动游戏：

```sh
bash tools/test-smoke-all.sh /absolute/path/outside/the/repo/smoke-script-test
```

## 新增一个 Minecraft 版本

先在仓库外的隔离副本干跑，不直接扩展发布支持范围：

- 从 Mojang 官方版本清单取得确切版本 ID 和元数据 SHA-1，并核对 Java 要求；快照 ID 与未来正式版号不能混用。下载输入与日志放工作区 daily。
- 分别确认该 ID 对应的 Fabric API、Loader、NeoForge、NeoForm 及插件实际可用。任一加载器或映射未发布就记录阻塞，不借用相邻版本或只改 Minecraft 范围来宣称支持。可以只准备元数据，不必提前下载或启动游戏。
- 同步检查下文配置之外的固定版本列表：`tools/smoke-all.sh`、`.github/workflows/build.yml` 的正式包构建/核查循环、`tools/backend-boundaries.test.mjs` 的目标与生成包断言，以及下游 `template/*/gradle.properties` 的示例基准。新增版本不应漏出 CI、许可检查或依赖来源校验。
- 将"元数据预备 / Gradle 配置 / 编译 / 无头测试 / 网页批准 / 游戏 / 联机"分开记录；缺依赖时在预备阶段停止，不能将干跑称为完成兼容。

步骤：

1. 把该版本加进 `settings.gradle` 的 `minecraftVersions`。
2. 在 `stonecutter.properties.toml` 里加一个 `["<版本>"]` 表，键与其他版本相同。NeoForge 和 NeoForm 的版本在 [projects.neoforged.net](https://projects.neoforged.net/neoforged/neoforge)，Fabric 的在 [fabricmc.net/develop](https://fabricmc.net/develop/)。Fabric 使用适用于未混淆版本的 Loom。
3. 让它能编译：先 `./gradlew :common:<版本>:compileJava`，再编译各加载器。小差异用 Stonecutter 注释（`//? if >=26.3 {`）或 `stonecutter.gradle` 里的重命名处理。只有整个文件都需要写成不同形式的版本，才在 `common/versions/<MC 版本>/src` 里放它自己的一份；这时把共享文件包在一个条件里，让它对该版本不生效。access transformer 和 access widener 由所有版本共用，一个版本需要的部分用 `#? if` 块（被禁用的块里每行用 `##` 注释，不是 `#`：widener 解析器会拒绝单个 `#` 留下的前导空格）。也要检查 mod 元数据：NeoForge 26.x 需要在 `neoforge.mods.toml` 里用 `iconFile` 代替 `logoFile`（`neoforge_logo_key`），因为 NeoForge 的弃用警告界面会让客户端在启动时停下。相邻 26.x 版本之间常见的破坏：包移动（`blaze3d` 到 `renderpearl`）、需要放宽的私有输入处理器、以及提示框和模型提交代码里变化的签名；26.2 就是一个现成的例子（`>=26.3` 注释和 `stonecutter.gradle` 里的 `renderpearl` 重命名）。
4. 先运行编译与无头检查，按 [UI 工作流](ui-workflow.md) 取得用户对固定版本的网页批准后，才运行游戏测试与客户端。根 `build` 可能包含 GameTest，不可在批准前作为快捷步骤。批准后再运行 `./gradlew build` 及 `./gradlew :neoforge:<版本>:runClient -Pautopilot`，Fabric 同样验证。在 macOS 上，先把一个能工作的外部运行目录里的 `options.txt`（带 `enableVsync:false`）和 `config/fml.toml`（带 `earlyWindowControl = false`）复制到新版本的运行目录。把 `ORIEL_RUN_DIR` 设成仓库之外的工作区目录。
5. 把该版本加到本文件顶部的表里，列出在它上面表现不同的任何东西，并在 `CHANGELOG.md` 的下一个版本下加一行。

新增 Minecraft 版本或升级 Loom 后，还须审查 `gradle/generated-minecraft.json` 中本地生成包的排除范围：确切版本与受限模块/文件正则。`verifyMinecraftContent` 在 Fabric 编译及根 `:check` 前检查当前工作树的 Loom 本地来源、文件形态及版本，不再固定已应用访问扩展的生成字节。访问扩展修改无需更新清单；有意升级时先核对输入与命名规则，再更新 JSON，并运行 `writeDependencyVerification` 同步和审查 XML。失败信息会列出版本、文件、期望/实际与更新步骤；不要用整个 `net.minecraft` 组的信任例外绕过检查。外部下载依赖 SHA-256 与 Linux 等平台分类器须保留。刷新命令、真实访问扩展回归命令与信任限制见 [后端维护](backend-maintenance.md)。

## 提交与发布

提交时保持共享源码为 26.3。当前仓库仅配置 `.github/workflows/build.yml`，没有 release.yml 或自动标签发布流程。Build 在 main 推送、PR 或手动触发时运行，不使用路径过滤；执行全版本构建、根检查、NeoForge 26.1 服务端 GameTest 及下游模板编译，并用 Node 24 运行后端/发布边界回归与无游戏的冒烟结果计数测试。运行和测试临时目录放在 runner.temp。上传前再以 `-Poriel_release` 构建六个加载器/版本 JAR 并检查开发类排除及许可条目。源码 JAR 仍保留源码。配置存在不代表远端运行已通过，须查看对应提交的 GitHub Actions 回执。

发布插件保留 Maven、Modrinth、CurseForge 的配置入口，但地址/项目 ID、凭据、产物许可和平台权限均需实际确认，不能把历史 maven.maxleiter.com 地址当成本项目已获发布授权的目的地，也不会因创建标签自动发布。同一个 JAR 兼容多个 MC 版本时，可在对应版本表填写 `publish_minecraft_versions = "26.2, 26.2.1"`；支持声明仍须运行证据。

本 fork 的接入及本地依赖获取见[接入指南](integration.md)。Modrinth/CurseForge 发布需要已确认的项目 ID、令牌和明确发布授权。
