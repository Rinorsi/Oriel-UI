# Minecraft versions

[All docs](index.md)

Oriel builds for three Minecraft versions from one source tree, with [Stonecutter](https://stonecutter.kikugie.dev):

| Minecraft | NeoForge | Fabric | Java |
|---|---|---|---|
| 26.3 | 26.3.0.26-beta | Loader 0.19.5, Fabric API 0.161.0+26.3 | 25 |
| 26.2 | 26.2.0.88 | Loader 0.19.5, Fabric API 0.161.0+26.2 | 25 |
| 26.1.2（项目名 26.1） | 26.1.2.126 | Loader 0.19.5, Fabric API 0.155.3+26.1.2 | 25 |

Each version has its own Gradle projects, named after it: `:common:26.1`, `:neoforge:26.1`, `:fabric:26.1`.
Their settings are in `stonecutter.properties.toml`. The engine, Rhino and the previewer are built once and shared.
The jars are `oriel-<loader>-<minecraft>-<version>.jar` (`oriel-neoforge-26.1.2-0.9.0.jar`) in
`<loader>/versions/<minecraft>/build/libs/`, and a release publishes
`dev.oriel:oriel-{common,neoforge,fabric}-<minecraft>` for every version at the same Oriel version.

源码以 26.3 为活动版本。26.x 内部的 API 差异使用 `>=26.2`、`>=26.3`、`<26.3` 等 Stonecutter 条件，包名与键名迁移放在 `stonecutter.gradle`。提交前恢复活动版本 26.3。

Everything works on 26.2, except that the dev tour (`-Ptour`) is 26.3 only. 26.2 is the 26.3 code with these
differences, which are `>=26.3` comments and the `blaze3d`/`renderpearl` rename in `stonecutter.gradle`:

- The render API is `com.mojang.blaze3d.pipeline.RenderPipeline` and `com.mojang.blaze3d.textures.FilterMode`/`GpuTextureView`; 26.3 moved them to `com.mojang.renderpearl.api.*`.
- Keys are GLFW's, not SDL's: `KeyEvent(key, scancode, modifiers)` holds a GLFW key code, and `KeyNames` is the GLFW one. Mouse buttons are GLFW's (0 left, 1 right, 2 middle); there is no `onTextInputFocusChange`.
- `PoseStack` has `mulPose(Quaternionfc)` but no `rotate` or `rotateDegrees`; `SubmitNodeCollection` has a no-argument constructor and `submitModel` takes a sprite and a crumbling overlay where 26.3 takes a `UvMapping`.
- `GuiGraphicsExtractor.setTooltipForNextFrame` has no `replaceExisting` overload with an item style (NeoForge's takes the stack and style only); `ScreenNarrationCollector.update` has no `NarrationTrigger`.
- `MouseHandler.onButton`, `onScroll`, `onMove` and `KeyboardHandler.keyPress`, `charTyped` are private: they are widened (`#? if <26.3`), and `PictureInPictureRenderer.textureView` is widened with the `blaze3d` type.
- The shape shader's vertices are packed differently. 26.2's `BufferBuilder` writes only `Position`, `Color`, `UV0`, `UV1`, `UV2`, `Normal` and `LineWidth` (no `UV3`), so on 26.2 `SdfRenderState` grows the box by the same margin on every side and sends the margin in `Position.z`; the shader works out the half size from that. A shadow's spread travels as the bits of a float in `UV2`. The 26.2 pipelines define `ORIEL_26_2`, which picks the 26.2 parts of `sdf_box.vsh` and `.fsh`: `#moj_import` instead of `#include`, and attributes and varyings without `layout(location)`, since 26.2 matches them by name. A pipeline that fails to compile stays failed until resources reload, so the retry in `SdfRenderState.available()` only helps after one.
- The dev tour is off: `DevTour` and `TourCursor` are stubs (their `else` blocks), since the tour hides and reads the system cursor through SDL.

26.1 项目实际使用 Minecraft 26.1.2，是主要验收版本。26.1/26.2 使用 GLFW，26.3 使用 SDL；26.1 的渲染目标、纹理上传与世界创建接口通过 `>=26.2` 条件适配。开发期 tour 仍只在 26.3 启用。

## 六目标冒烟

在 Git Bash 中运行：

```sh
bash tools/smoke-all.sh /absolute/path/outside/the/repo/run-smoke
```

脚本先编译完整矩阵，再依次启动 Fabric、NeoForge 的 26.1/26.2/26.3 客户端；每个客户端限时 600 秒。
`-Pautopilot=smoke` 进入世界后打开首页和组件目录，等待页面稳定并截图，记录页面错误与检查失败数，然后正常退出。
运行目录为 `<根目录>/<loader>-<版本>`，截图为其下的 `screenshots/oriel_smoke_gallery.png` 和 `oriel_smoke_catalog.png`。

根目录也可用 `ORIEL_RUN_DIR` 指定，未指定时使用相邻工作区的 `daily/<当天>/run-smoke`。
根目录必须位于仓库外；日志在 `logs/`，完整结果及截图路径在 `summary.tsv`。缺少完成标记、截图未更新、
页面错误、自动检查失败、超时或非零退出码都会使脚本失败，单个目标失败后仍继续检查剩余目标。

默认离线构建。依赖未缓存时可加 `--online`；工作区已有 `PROXY_ARGS` 时会一起传给 Gradle，
其他 Gradle 参数放在 `--` 后。脚本不安装软件，也不启动代理。

脚本自身的结果判定可用假 Gradle 进程验证，不会启动游戏：

```sh
bash tools/test-smoke-all.sh /absolute/path/outside/the/repo/smoke-script-test
```

## Adding a Minecraft version

先在仓库外的隔离副本干跑，不直接扩展发布支持范围：

- 从 Mojang 官方版本清单取得确切版本 ID 和元数据 SHA-1，并核对 Java 要求；快照 ID 与未来正式版号不能混用。下载输入与日志放工作区 daily。
- 分别确认该 ID 对应的 Fabric API、Loader、NeoForge、NeoForm 及插件实际可用。任一加载器或映射未发布就记录阻塞，不借用相邻版本或只改 Minecraft 范围来宣称支持。可以只准备元数据，不必提前下载或启动游戏。
- 同步检查下文配置之外的固定版本列表：`tools/smoke-all.sh`、`.github/workflows/build.yml` 的正式包构建/核查循环、`tools/backend-boundaries.test.mjs` 的目标与生成包断言，以及下游 `template/*/gradle.properties` 的示例基准。新增版本不应漏出 CI、许可检查或依赖来源校验。
- 将“元数据预备 / Gradle 配置 / 编译 / 无头测试 / 网页批准 / 游戏 / 联机”分开记录；缺依赖时在预备阶段停止，不能将干跑称为完成兼容。

1. Add the version to `minecraftVersions` in `settings.gradle`.
2. Add a `["<version>"]` table to `stonecutter.properties.toml` with the same keys as the others. NeoForge and
   NeoForm versions are on [projects.neoforged.net](https://projects.neoforged.net/neoforged/neoforge), Fabric's on
   [fabricmc.net/develop](https://fabricmc.net/develop/). Fabric 使用适用于未混淆版本的 Loom。
3. Make it compile: `./gradlew :common:<version>:compileJava`, then the loaders. Small differences are Stonecutter
   comments (`//? if >=26.3 {`) or renames in `stonecutter.gradle`. Only a version that needs a file written
   differently throughout gets its own copy in `common/versions/<minecraft>/src`; wrap the shared
   file in a condition that leaves it out for that version. The access transformer and access widener are shared by every version, with `#? if` blocks
   for what one version needs (a disabled block's lines are commented with `##`, not `#`: the widener parser rejects
   the leading space a single `#` leaves behind). Check the mod metadata too: NeoForge 26.x needs `iconFile` instead
   of `logoFile` in `neoforge.mods.toml` (`neoforge_logo_key`), since NeoForge's deprecation warning screen stops the
   client at startup. Typical breakage between neighboring 26.x versions: package moves (`blaze3d` to `renderpearl`),
   private input handlers that need widening, and changed signatures in tooltip and model submission code;
   26.2 is a worked example (the `>=26.3` comments and the `renderpearl` rename in `stonecutter.gradle`).
4. 先运行编译与无头检查，按 [UI 工作流](zh/ui-workflow.md) 取得用户对固定版本的网页批准后，才运行游戏测试与客户端。根 `build` 可能包含 GameTest，不可在批准前作为快捷步骤。批准后再运行 `./gradlew build` 及
   `./gradlew :neoforge:<version>:runClient -Pautopilot`，Fabric 同样验证。
   On macOS, copy `options.txt` (with `enableVsync:false`) and `config/fml.toml` (with `earlyWindowControl = false`)
   from a working external run directory into the new version's run directory first. Set `ORIEL_RUN_DIR` to a workspace directory outside the repository.
5. Add the version to the table at the top of this file, list anything that works differently on it, and add a line
   to `CHANGELOG.md` under the next version.

新增 Minecraft 版本或升级 Loom 后，还须审查 `gradle/generated-minecraft.json` 中本地生成包的排除范围：确切版本与受限模块/文件正则。
`verifyMinecraftContent` 在 Fabric 编译及根 `:check` 前检查当前工作树的 Loom 本地来源、文件形态及版本，不再固定已应用访问扩展的生成字节。
访问扩展修改无需更新清单；有意升级时先核对输入与命名规则，再更新 JSON，并运行 `writeDependencyVerification` 同步和审查 XML。
失败信息会列出版本、文件、期望/实际与更新步骤；不要用整个 `net.minecraft` 组的信任例外绕过检查。外部下载依赖 SHA-256 与 Linux 等平台分类器须保留。
刷新命令、真实访问扩展回归命令与信任限制见 [后端维护](zh/backend-maintenance.md)。

提交时保持共享源码为26.3。当前仓库仅配置 `.github/workflows/build.yml`，没有 release.yml 或自动标签发布流程。
Build 在 main 推送、PR 或手动触发时运行，不使用路径过滤；执行全版本构建、根检查、NeoForge26.1服务端GameTest及下游模板编译，
并用Node24运行后端/发布边界回归与无游戏的冒烟结果计数测试。运行和测试临时目录放在 runner.temp。
上传前再以 `-Poriel_release` 构建六个加载器/版本JAR并检查开发类排除及许可条目。源码JAR仍保留源码。
配置存在不代表远端运行已通过，须查看对应提交的GitHub Actions回执。

发布插件保留 Maven、Modrinth、CurseForge 的配置入口，但地址/项目ID、凭据、产物许可和平台权限均需实际确认，
不能把历史 maven.maxleiter.com 地址当成本项目已获发布授权的目的地，也不会因创建标签自动发布。
同一JAR兼容多个MC版本时，可在对应版本表填写 `publish_minecraft_versions = "26.2, 26.2.1"`；支持声明仍须运行证据。

本 fork 的接入及本地依赖获取见[接入指南](zh/integration.md)。Modrinth/CurseForge 发布需要已确认的项目 ID、令牌和明确发布授权。
