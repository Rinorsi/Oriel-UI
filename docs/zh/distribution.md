# 分发指南：瘦包、完整版与嵌入依赖

[文档入口](index.md) · [接入指南](integration.md) · [0.11 → 0.12 迁移](upgrading.md) · [安全与设置](security.md)

**下游模组接入、打包或升级前先读本页。** 本页规定 0.12 起的分发合同：瘦包 **0.12.0**、完整版 **0.12.1**，两者的 mod id 都是 `oriel`。六目标双变体已构建并通过发行检查；嵌套检测已在 Fabric / NeoForge 26.3 专服各五场景实测，设置与缓存通过单元测试，通知页通过桌面预览。**游戏客户端弹窗、设置页游戏内行为及 Editor 客户端/业务/热重载仍未验证**；Editor 六目标专服的嵌入与混装已测，具体范围见下文。

## 1. 两种分发方式

### 嵌入 vs 单独发布：怎么选、怎么互转

这里的“单独发布”指 Oriel 作为玩家另装的必需依赖。两种方式都使用同一核心 API。

| 维度 | 嵌入瘦包（Jar-in-Jar） | 单独发布（外部必需依赖，推荐） |
|---|---|---|
| 玩家要装什么 | 下游模组；Oriel 已内嵌，Fabric API 等其他依赖仍须安装 | 下游模组 + 匹配加载器/MC/范围的独立 Oriel，以及其他必需依赖 |
| 下游 jar 体积 | Editor 六目标实测约 **11.07 MB** | 同源码外部版实测约 **1.17 MB**；独立 Oriel 另计，不代表总下载量只有 1.17 MB |
| Oriel 怎么更新 | 更新内嵌库一般需重新构建/发布下游；也可另装满足范围的新版 Oriel，须验混装 | 玩家可直接从官方渠道更新独立库；跨 minor 仍需迁移下游 |
| 多个模组同用 | 加载器先满足全部范围，再选用一份；已测 0.12.0/0.12.1 混装取较高的 0.12.1。有核心差异时记录风险并可能提示，真正客户端提示窗仍**未验证** | 所有消费者都用外部依赖时，全局独立安装一份；若仍有其他模组内嵌，仍按左侧混装规则处理 |
| Modrinth / CurseForge 依赖声明 | 不把独立 Oriel 标为必需下载；其他必需依赖照常声明 | 把 Oriel 标为必需依赖；模组内部对 `oriel` 的必需依赖声明两种模式都保留 |
| 适合谁 | 希望玩家只找一个下游文件、能自行维护内嵌版本的作者 | 希望下游包小、库独立更新、多个模组共享官方发行版的作者 |

**服务端是否安装 Oriel 取决于下游使用的能力，两个连接方向并不对称。** 有服务端功能时，嵌入模式随下游 jar 提供库，外部模式在服务端另装 Oriel jar；纯客户端路径的源码判断、当前拒绝行为及未实测边界见[服务端到底要不要装 Oriel](#服务端到底要不要装-oriel)。

互转时保留自己的 mod id、页面命名空间和业务代码，Oriel 的 mod id 仍为 `oriel`、API 不变。两加载器元数据始终声明同 minor 必需依赖：Fabric `>=0.12.0 <0.13.0`，NeoForge `[0.12.0,0.13.0)`。模板只需切换构建属性和发布平台依赖声明：

1. **外部 → 嵌入**：`-PorielEmbedded=true`，Fabric 启用 include、NeoForge 启用 jarJar，嵌入 Maven 的瘦包 `0.12.0`；发布平台取消独立 Oriel 的必需下载声明。玩家不必再单独安装 Oriel；若已经安装同范围完整版，它可继续参与加载器选择，其他模组需要它时应保留。
2. **嵌入 → 外部**：`-PorielEmbedded=false`（模板默认），移除嵌套、保留编译依赖及内部必需范围；发布平台声明 Oriel 必需。玩家及需要库的服务器必须安装独立 Oriel。
3. 两模式输出同名 jar，分别保存并检查成品的嵌套列表。编译依赖固定为瘦包，不将完整版放进 Maven。`jijAcceptance` 是测试专用，不用于下游正常发布。

**已有实测**：Editor 的 Fabric / NeoForge × 26.1.2 / 26.2 / 26.3，六目标嵌入版和外部版都 assemble 成功；两组专服场景共 **12/12**：只嵌瘦包选 0.12.0，嵌入 + 独立完整版选 0.12.1，Editor 初始化、RCON 版本回读及正常关服通过。此记录不包含外部版独立启动、真实客户端或业务回归。体积来自同源码两模式实际 jar，不能外推所有下游。

**配置迁移保留性：未验证。** 同一个下游从嵌入版升级到外部依赖版或反向升级，都使用同一份 `config/oriel.properties`，理论上无需丢弃配置，但尚未实测迁移后的保留情况。

**下游再分发许可证合规：未验证。** Oriel MIT、Vellum MIT、Rhino MPL-2.0：保留 jar 内声明文件。

如果加载器选中了其他模组内嵌的更老副本，你的页面可能缺少新功能；旧副本不带检测功能时也不会提示。正确声明依赖范围是前提，检测与提示不能替代加载器兼容约束。

**两种方式都支持，由下游自行选择；推荐外部必需依赖。** 所有消费者都使用外部依赖时，没有嵌套副本，也就没有嵌套冲突提示。若别的模组仍内嵌 Oriel，独立安装完整版也须按第 5 节核对，不能保证不提示。Modrinth / CurseForge 等只是分发渠道示例，实际可用下载地址以项目发布公告为准；没有公开 Maven 仓库的承诺，不要使用上游 Vellum 的仓库。

## 服务端到底要不要装 Oriel

**只在客户端安装 Oriel 的本地功能路径可用，这是源码判断，未实测；服务器安装了 Oriel，则加入的客户端必须也安装并匹配协议。** 不能把“服务端装库后要求客户端安装”反推为“客户端装库后服务器也必须安装”。是否需要服务端部分由下游功能决定，与嵌入或外部依赖的打包方式无关。

### A. 客户端有 Oriel，服务端没有

从当前源码判断，`OrielScreens.open/openInline` 打开的本地页面、使用本地数据的 HUD、客户端热重载和本地 `oriel.send` 监听不依赖服务端 Oriel。NeoForge 的传输通道已注册为 `optional()`，这些纯客户端能力可由下游使用。**该连接场景未实测**：注册表同步、连接无 Oriel 的 Fabric / NeoForge 专服及原版服务端均**待验证**；可选通道并不等于已经验证了所有连接环节。

服务端未安装 Oriel 时，`OrielServer` 会话、服务端推送页面/数据和 `ForgeMenu` 等服务端功能不可用。本地 `oriel.send` 可交给客户端监听器处理，不会凭空产生服务端业务或权限。需要这些服务端能力的下游，必须在服务端提供库：内嵌时随下游 jar 提供，外部依赖时另装 Oriel jar。

`DocumentDriver.serverSession()` **只表示当前页面是否由服务端打开、属于服务端会话**；本地页在装有 Oriel 的服务器上也会返回 false。它不是“服务器是否安装 Oriel”的探测，不能用作下游能力判断。目前没有统一的客户端侧服务端能力探测 API；需要等待服务端确认的业务应自行设计回执和超时，不能无限等待或把发送成功当成业务成功。

### B. 服务端有 Oriel，客户端没有

**当前不支持，源码已确认。** Fabric 在玩家 JOIN、NeoForge 在 PlayerLoggedInEvent 调用 `requireOnClient`，缺少 Oriel 或协议不兼容时主动断开，并发送中英文提示。服务器装库后，即使某个玩家暂时不打开页面，也不能据此免除客户端依赖。NeoForge 的 optional 传输注册不会取消这项应用层检查。

这项源码结论与 A 方向分别记录，不把本轮文档核对写成新的联网实测。完整流程见[服务端与容器](server.md)。

### 纯客户端下游怎么声明

纯客户端下游只使用本地页面/HUD/监听等能力，不调用 `OrielServer`、`ForgeMenu` 或依赖服务端会话；自己的逻辑、入口和元数据应限制在客户端。这样设计时，专服不必安装该下游或 Oriel，但**实际纯客户端下游连接场景未验证**。

Fabric：按 [fabric.mod.json 规范](https://wiki.fabricmc.net/documentation:fabric_mod_json_spec)，在**下游自己的**元数据中声明 `"environment": "client"`，并使用客户端入口。以下为合并到现有文件的字段片段，Oriel 仍是客户端必需依赖：

```json
{
  "environment": "client",
  "entrypoints": { "client": ["dev.example.MyClient"] },
  "depends": { "oriel": ">=0.12.0 <0.13.0" }
}
```

NeoForge：按 [Mod Files 文档](https://docs.neoforged.net/docs/gettingstarted/modfiles/)，在下游的 `neoforge.mods.toml` 中将 Oriel **依赖项**设为 `side="CLIENT"`，客户端入口使用 `@Mod(value = "mymod", dist = Dist.CLIENT)`（`Dist` 来自 `net.neoforged.api.distmarker`）：

```toml
[[dependencies.mymod]]
modId = "oriel"
type = "required"
versionRange = "[0.12.0,0.13.0)"
ordering = "NONE"
side = "CLIENT"
```

`side="CLIENT"` 限定的是该依赖在哪个物理端必须存在，**不是整个下游 jar 的环境声明，也不等价于 Fabric 的 environment**；`@Mod` 的 dist 限定对应入口的加载端。不要仅改依赖 side 就认为公共入口、其他注册或嵌套库已经被自动隔离。上述语法已按加载器文档核对，NeoForge 客户端入口写法也与仓库模板一致；下游最终 jar 的端侧行为、注册表同步及连接兼容性仍**未验证**，须按实际目标版本检查。

### 已测边界与计划验证

目前这项下游专服验收只测了**带服务端部分的 Editor**：六目标的单嵌与加独立 full 混装共 12/12，见前述选择指南。它不能证明 A 方向、纯客户端下游或原版服务器兼容。

计划验证（尚未执行）：

- [ ] 客户端安装 Oriel 和纯客户端下游，连接**未安装 Oriel** 的 Fabric 专服，记录 MC/加载器/库版本、注册表同步、连接与本地功能结果。
- [ ] 同样连接**未安装 Oriel** 的 NeoForge 专服，检查 optional 通道之外的注册表同步和连接流程。
- [ ] 同样连接匹配 MC 版本的**原版服务端**，验证连接、本地页面/HUD/热重载/本地消息以及业务超时。

完成验证后，将本节对应的“未实测/待验证”替换为注明目标、场景和结果的实测记录，不根据元数据或 optional 标记提前宣称兼容。

## 2. 变体与版本规则

| 内容 | 瘦包 `slim` | 完整版 `full` |
|---|---|---|
| 引擎、Rhino、script-rhino、mc-extension、kit 基础层（Shell、Motion、PageControllers、Lang、Widgets 等） | 保留 | 保留相同核心 |
| `oriel:ui/ore.css`、基础样式、字体、`textures/ore`、`textures/icons`、基础语言键 | 保留 | 保留 |
| 设置页、关于页、`oriel_mine` 我的页面与热重载 | 保留 | 保留 |
| 嵌套检测与提示页 | 0.12 起提供；专服/单元/预览已测，游戏弹窗未验证 | 同瘦包 |
| 官方模板与封面、头像、模板目录及模板中心 | 不含 | 包含 |
| showcase / demo 页面与图片、组件库和图标库展示页、Mobdex、OrielDemos 演示注册 | 不含 | 包含 |
| `data/oriel/test_instance`、GameTest 等参考/测试内容 | 不含 | 完整版/测试分发按构建规则提供 |
| 开发检查器、自动验收、开发 agent 等开发工具 | `-Poriel_slim` 同时应用 `-Poriel_release` 的排除规则 | 开发构建可含；发布玩家包另加 `-Poriel_release` 排除 |

图标与基础控件可以复用，**展示图标/控件的页面不属于核心**。瘦包的参考入口不注册或明确说明“本构建不含演示内容”，Shell 接线前移除不存在的内置 `data-go` 目标。两种变体都提供 `/oriel settings`、`/oriel about`；瘦包的 Home 动作打开模组设置，`Shell.back()` 直接关闭页面。这里的 Home 指“打开优易库首页”快捷键动作，玩家环境默认不绑定。我的页面仍通过 `/oriel open oriel_mine:<目录>/index.html` 打开并保留热重载；瘦包关于页的模板头像回退到核心 emblem。瘦包启动时 INFO 记录变体、实际版本及“参考内容未包含（官方模板/showcase/demo）”。这些入口已有控制器隔离/JVM 链接验证，真实游戏交互和热重载仍未验证。

从 0.12 起遵循以下规则：

- 核心每次有任何改动，包括小 bug 修复，都提升 minor，例如 `0.12 → 0.13`。瘦包版本永远为 `X.Y.0`。
- 完整版为 `X.Y.N`，`N ≥ 1`。只改参考内容时只发完整版，例如 `0.12.1 → 0.12.2`；不得夹带核心改动。
- **为什么同 minor 完整版更高？** 两者使用同一个 `oriel` id；当加载器在满足全部消费者约束后采用较高版本时，会选择包含参考内容的完整版。同 minor 内核心必须相同，因此增加参考内容不应改变核心合同。实际混装选择仍以加载器实测为准，不是“任意条件都选最新版”的保证。
- 版本单一来源为仓库根 `gradle.properties` 的核心 `X.Y.0` 和 `oriel_full_patch`（≥1），不在两处分别手写版本。`-Poriel_slim` 构建瘦包，不加则构建完整版；文件名里的版本不同，互不覆盖。
- Maven 坐标不加变体分类器：`dev.oriel:oriel-<loader>-<mc>:X.Y.0`，**只发布瘦包**。完整版 `X.Y.N` 只作为 jar 分发到 Modrinth / CurseForge / dist，不进入 Maven，包括 MavenLocal 和模板私有仓库。同坐标的更高完整版若进入 Maven，下游范围解析可能选中它而误嵌约 24 MB 参考内容。
- 下游声明同 minor 范围：Fabric `>=0.12.0 <0.13.0`，NeoForge `[0.12.0,0.13.0)`，两种变体均满足。0.11 的消费者（包括 Editor）必须迁移、重新编译并验证，不能只放宽上限。

## 3. 下游应依赖什么

运行时依赖目标加载器的 **Oriel 核心合同**，遵循[公共 API 与稳定性范围](integration.md#稳定性与兼容性)。瘦包包含某个类不等于该类是稳定 API；不导入 `dev.oriel.mod.internal`。

**不要把官方模板、showcase、demo、模板目录或演示注册当成运行时依赖。** 需要某个模板或示例时，从仓库源码或完整版取出所需 HTML/CSS/JS、图片及其他引用资源，复制到自己模组的 `assets/<你的模组id>/…`，改为自己的 URL，保留适用许可声明。具体操作见 [Showcase 复用指南](showcase.md#怎么复用到你的模组)和[官方模板目录](templates.md)。复制后仅在必要的基础样式、字体和图标上引用 `oriel:`；不要让页面继续跳向 `oriel:ui/templates/…` 或 `oriel:oriel/showcase/…`。

`ForgeMenu` 是公开 API，核心保留其类、`TYPE` 注册与公开 API；瘦包取消默认演示 screen 和 `/oriel-forge` 演示命令，自己的菜单页面与业务应由自己的模组接入。发布前至少用瘦包检查一次资源和类依赖，以发现被完整版掩盖的缺失。

## 4. Gradle 与加载器元数据

以下为 **0.12 配方**，适用 Groovy DSL、Java 25、Minecraft 26.1.2 / 26.2 / 26.3；坐标必须匹配实际加载器和游戏版本。仓库模板默认 `orielEmbedded=false`（外部必需依赖）：玩家可单独更新官方库，下游 jar 较小。构建模板时加 `-PorielEmbedded=true` 嵌入瘦包 `0.12.0`，加 `-PorielEmbedded=false` 切回外部；两种模式的模组元数据均保留必需依赖范围。开关仅接受 `true/false`，嵌入模式拒绝非瘦包版本。`jijAcceptance` 为验收专用，优先于开关：只用 compileOnly，实际运行从测试准备的 mods 目录加载父 jar。复制工程和游戏验证须单独执行。

取得对应工件并放入自己的 Maven 仓库，或在 Oriel 仓库执行 `./gradlew publishToMavenLocal -Poriel_slim` 发布瘦包 `0.12.0`，然后使用 `mavenLocal()`。**不带 `-Poriel_slim` 的任何 Maven 发布都会在任务执行前报错**，包括 `publish`、`-Poriel_maven_dir` 对应发布任务、`publishToMavenLocal` 和 `templateCompile` 的私有仓库。模板编译使用 `./gradlew templateCompile -Poriel_slim`；聚合 `check` 包含模板门禁，也须带此属性。

下游编译依赖固定为 `X.Y.0`，嵌入解析可使用严格同 minor 范围加 `prefer(X.Y.0)`，**不要依赖完整版**。模板在解析后还核对实际内嵌版本，若旧镜像或额外约束将版本提升为 full 就明确失败；prefer 只是偏好，不是绝对锁定。复制出去的[模板](../../template/README.md)按相同原则获取依赖。

### Fabric：嵌入瘦包

```groovy
repositories { mavenLocal() }
dependencies {
    implementation("dev.oriel:oriel-fabric-26.3:0.12.0") { transitive = false }
    include("dev.oriel:oriel-fabric-26.3") {
        transitive = false
        version {
            strictly("[0.12.0,0.13.0)")
            prefer("0.12.0")
        }
    }
}
```

Loom 负责生成嵌套 jar 列表，不手填不存在的内部路径。Fabric 的 include 是独立解析配置，不能只依赖 implementation 的版本约束；模板为 include 单独设置严格范围/瘦包优先值，并在解析后拒绝非 `0.12.0` 的 Oriel。NeoForge jarJar 同样核验实际版本。直接固定 `X.Y.0` 也可，不使用动态最新版。自己的 `fabric.mod.json` 将以下字段合并到现有 `depends`，不要覆盖 Minecraft、Fabric API 等其他依赖：

```json
{
  "depends": {
    "oriel": ">=0.12.0 <0.13.0"
  }
}
```

### NeoForge：嵌入瘦包

ModDevGradle 的嵌套依赖同时表达兼容范围与优先打包版本，避免把嵌套选择锁死为单个版本：

```groovy
repositories { mavenLocal() }
dependencies {
    implementation("dev.oriel:oriel-neoforge-26.3:0.12.0") { transitive = false }
    jarJar("dev.oriel:oriel-neoforge-26.3") {
        transitive = false
        version {
            strictly("[0.12.0,0.13.0)")
            prefer("0.12.0")
        }
    }
}
```

在 `META-INF/neoforge.mods.toml` 中增加必需依赖；`mymod` 换成自己的 mod id：

```toml
[[dependencies.mymod]]
modId = "oriel"
type = "required"
versionRange = "[0.12.0,0.13.0)"
ordering = "NONE"
side = "BOTH"
```

还应检查最终父 jar 的 `META-INF/jarjar/metadata.json`，确认版本范围与实际内嵌的版本吻合。只在 TOML 写范围不等于 JarJar 清单也正确。

### 外部必需依赖

只加运行/编译依赖，不加 `include` 或 `jarJar`；构建仍依赖瘦包 `X.Y.0`，玩家可以独立安装满足元数据范围的完整版：

```groovy
repositories { mavenLocal() }
dependencies {
    implementation("dev.oriel:oriel-fabric-26.3:0.12.0") { transitive = false }
    // NeoForge 工程改用 dev.oriel:oriel-neoforge-26.3:0.12.0
}
```

Fabric / NeoForge 元数据仍使用上面的必需依赖范围，并在发布页面设 Oriel 为必需依赖。玩家从 Oriel 渠道安装范围内的版本；`implementation` 本身不会替玩家安装库。只有本地 jar 的用法见[最小 Fabric 工程](quickstart.md)。

### Maven 模块合同

下游优先使用目标 loader 工件，它已平铺包含 engine、kit、script-rhino、mc-extension 与 relocated Rhino；消费时 `transitive=false`，由下游自己声明 Minecraft / 加载器 / Fabric API。不要再把这些独立模块重复嵌入父 jar。

`oriel-common-<mc>` 是独立编译模块，不是可安装模组，也不是包含所有类型的 fat jar。其 runtime 元数据依赖同版本 `dev.oriel:oriel-engine`、`oriel-kit`、`oriel-script-rhino`、`oriel-mc-extension`；kit 以 `oriel-kit` 发布，命名与其他模块一致。独立模块按源码发布，**不适用 loader 瘦包的参考内容裁剪合同**。common 的 API variant 不传递这些 implementation 依赖；common 编译需要引擎/kit 类型时，最简单是 compileOnly 使用对应版本的自包含 loader 工件，见[接入指南](integration.md)。

发布到新的临时 Maven 目录后，必须运行 `node tools/check-release.mjs --maven-dir=<目录>`：递归检查所有 POM/module 的 `dev.oriel` 及子组依赖在该目录有同坐标 POM 和 jar（`.module` 可选，relocated Rhino 仅发布 POM），缺项退出 1；不要求第三方依赖发布到我们的仓库。可与运行包审计参数同时使用，详见[维护约定](maintenance.md#双变体发布与专服复核)。

## 5. 多个模组都带 Oriel 时

加载器先满足全部消费者约束，再选用一份 Oriel；范围无交集应拒绝加载。跨 minor 混装不能靠嵌套检测修复 API 不兼容，也不能靠关闭提示绕过加载器的依赖检查。

**嵌套检测与提示 0.12 起提供；专服、单元和预览的验证范围见下表。** 生效副本在客户端和专服枚举已加载模组 jar：Fabric 递归查 `fabric.mod.json` 的 `jars`，NeoForge 递归查 `META-INF/jarjar/metadata.json`，从父 jar 读取被加载器淘汰的副本；独立安装的顶层 Oriel 也计入。来源使用父模组元数据的显示名，生效者依据加载器实际选择的来源与版本识别，检测器不自行选版本。

- 每份新 jar 生成 `META-INF/oriel/manifest.json`（`schema=1`），包含版本、`variant`（`slim|full`）、loader、mc、`coreHash`、`apiHash` 和 `{path,sha256}` 库存。清单上限 **200 KiB**；class 按包聚合，普通核心资源逐条记录，`apiHash` 对 `api/<mc>.txt` 的**原始字节**取 SHA-256（Minecraft 26.1.2 使用项目快照 `api/26.1.txt`）。生成器与发行检查共用 `tools/release/boundaries.json` 和核心哈希逻辑；分类表同时打入 `META-INF/oriel/boundaries.json`，作为运行时忽略规则的唯一来源且属于必需核心。清单自身不参与核心哈希，避免自引用。
- 风险方向是“被淘汰副本里有，生效副本里没有或不同”的核心条目；`apiHash` 不同也算风险。参考内容和开发工具不参与风险比较。版本、核心相同不产生风险提示；不同版本但核心与 API 相同也不发风险 WARN。旧 jar **没有清单**时只比较版本；未知或损坏清单及扫描错误明确 WARN，不能把坏清单当成正常旧副本或兼容证据。
- 每次启动一行 **INFO** 摘要，列出各副本版本、来源和生效副本；有风险时一行 **WARN** 点名涉及的模组，**DEBUG** 展开条目差异。没有风险仍有启动摘要；摘要不等于业务/API 兼容验收。专服只写日志，不链接客户端提示类。
- `config/oriel/nested-check.json` 保存 installation stamp、逻辑指纹、完整报告和已提示指纹集合。stamp 包含已装列表、路径/尺寸/mtime 与活动清单摘要；stamp 相同走缓存快速路径，不再展开嵌套 archive。逻辑指纹包含模组列表/版本及副本 `coreHash`/来源；每个指纹至多提示一次，切换安装后回到旧指纹也不重复提示。缓存原子写入；损坏缓存拒绝使用，保存为 `nested-check.invalid.json` 后重建。
- 客户端首次进入没有 loading overlay 的标题界面（TitleScreen）时处理 Ore 风格提示；正文可滚动，底栏固定。提示说明哪些模组内置了不同版本、当前用了谁带的哪版，以及界面样式可能异常。继续/返回保留原标题屏；提示本身不阻止游戏，不保证任意组合的业务或二进制兼容。**标题时机、鼠标操作与真实弹窗仍未验证。**
- `client.nestedNotice` 默认 **`on`**，配置允许 **`on/off`**；设置页以同名开关呈现，内部类型是 Choice/enum（`NestedNotice.ON/OFF`）。提示页勾选“不再提示”立即将 `client.nestedNotice=off` 写回 `config/oriel.properties`；恢复默认回到 `on`，保留已提示指纹历史。关闭只抑制客户端提示，不关闭扫描和风险日志，也不改变版本选择。白名单、非法值/boolean 拒绝、持久化和恢复已单测；游戏内行为未验证。完整协议见[安全与设置](security.md#设置)。

**已知限制：如果加载器最终选中的旧副本不含检测功能，就没有扫描或提示。** 本次 legacy 实测是旧 0.11.0 副本被淘汰、新 full 生效，不能证明旧副本生效时也能检测。范围无交集由加载器在选择阶段拒绝，检测器不干预；没有提示不证明只有一份库或组合安全。

| 场景 | 当前证据等级 |
|---|---|
| 0.12 六目标双变体 | 已构建并审计十二包，同目标 coreHash 一致；三版本 API 检查通过 |
| 26.3 Fabric / NeoForge：`slim` 单父嵌瘦包 | 专服实测，活动版本 0.12.0，1 副本，无风险 WARN |
| 26.3 Fabric / NeoForge：`top-full` 顶层 full + 父嵌 slim | 专服实测，活动版本 0.12.1，2 副本，无风险 WARN |
| 26.3 Fabric / NeoForge：`same-core` 两父分别嵌 slim/full | 专服实测，活动版本 0.12.1，2 副本，无风险 WARN |
| 26.3 Fabric / NeoForge：`different-core` 被淘汰副本的实际 Ore CSS 不同 | 专服实测，活动版本 0.12.1，一行风险 WARN 点名两父，差异含 `ore.css` |
| 26.3 Fabric / NeoForge：`legacy` 真实 0.11.0 无清单旧副本 | 专服实测，新 full 0.12.1 生效，一行风险 WARN 点名两父，差异为 `version-only` |
| 26.3 Fabric / NeoForge：不相交范围 `conflict` | A 阶段专服已测，明确因依赖范围拒绝；B 最终五场景未重跑此项 |
| API 哈希差异、比较方向、stamp 免扫描、指纹历史、坏清单/坏缓存 | 单元测试已测；API 差异不是本次专服夹具场景 |
| 设置白名单、on/off 校验、持久化、恢复默认、提示消息 | 单元测试已测；不等于游戏内开关/重启验收 |
| 通知页中英文 | 桌面预览 strict 检查零警告；已查看中文 640×360、427×240 和英文 427×240 截图 |
| 真正游戏内弹窗、标题时机、勾选、重启显示、设置页恢复及设置/关于交互 | **未验证**；单元或预览截图不能代替 |
| Editor 六目标（含 26.1.2 / 26.2）单嵌与独立 full 混装 | 专服 12/12，分别选择 0.12.0 / 0.12.1；详见第 1 节 |
| Editor 客户端/业务/热重载、真实多人、嵌入与外部互转后的配置保留 | **未验证** |

上述 B 专服五场景共 10/10 通过，覆盖两加载器 26.3，均回读 `/oriel version` 并正常停服。全量 `:preview:checkUi` 为 85 页、84 OK、1 个既有 about 设计 warning、0 failed；新通知页零警告不能写成全项目零警告。

## 6. 发布前检查清单

下面是发布要求，**不是已通过记录**。Oriel 维护者逐目标检查，Fabric / NeoForge × 26.1.2 / 26.2 / 26.3 不能互相代替；下游作者至少检查自己的目标与分发方式。

- [ ] 只将瘦包全部 publication 发布到新的临时 Maven 目录，未带 `-Poriel_slim` 的发布必须失败；完整版只分发 jar。执行 `node tools/check-release.mjs --maven-dir=<目录>`，确保没有未发布的内部依赖坐标；不能靠旧目录残留掩盖缺项。
- [ ] 两种变体均能构建；名称不覆盖，元数据 id 都为 `oriel`；瘦包为 `X.Y.0`，完整版为同 minor 的 `X.Y.N`（N≥1），均满足模板范围。
- [ ] 审计压缩包条目与大小：瘦包没有参考内容和开发工具，必需核心条目齐全；完整版发布时也应用开发工具排除。许可、Rhino 与字体声明保留。
- [ ] 同一目标的两变体核心哈希一致。核心条目（非参考、非开发工具）按路径排序，对 `(路径, sha256)` 序列再取 SHA-256，class 按字节参与；不同 MC/加载器目标分别记录。
- [ ] 发布时在受控文件 `tools/release/core-hash.properties` 记录 `X.Y` 的各目标核心哈希；后续同 minor 核心变化应失败并提示“核心有变，需要升 minor”，不覆盖旧值掩盖变化。由 `tools/check-release.mjs` 与 `tools/release-boundaries.test.mjs` 核验；0.12 十二产物已通过同目标配对检查，六条目标 pin 已首次冻结，随后普通检查通过。每次发布仍须对最终产物重验，命令见[维护约定](maintenance.md#双变体发布与专服复核)。
- [ ] 核对嵌套清单字段与体积（≤200 KiB；class 按包聚合），比较方向、参考内容排除、无清单旧副本行为。
- [ ] 下游只依赖核心；把所用模板/示例及资源复制到自己的命名空间。分别检查最终父 jar 的加载器元数据、嵌套列表、许可证和同 minor 范围。
- [ ] 执行核心/kit/预览、公共 API、后端边界、发布边界、文档和链接检查。普通开发包通过不等于瘦包通过。
- [ ] 双加载器专服分别验证单嵌、顶层 full + 嵌套 slim、两父模组不同版本、范围冲突、核心差异 WARN；记录实际被选版本。
- [ ] 客户端另验瘦包启动、设置/关于/我的页面/热更新、参考入口降级、标题页提示、“不再提示”、重启缓存与设置恢复；没有实测就保留“未验证”。
- [ ] 已有 0.11 下游（如 Editor）完成[迁移](upgrading.md)；发布说明、模板、MCP 规则及发布包的 `AGENTS-snippet.md` 均指向本页的同版本副本。

## 7. 可选署名（不强制）

鼓励在模组信息中写明 **“界面使用优易库 Oriel UI（作者：云诺羲Rinorsi）”**，并给项目或作者链接；**不强制**，不以此作为接入条件。这个展示署名是可选的，LICENSE / NOTICE 中适用的许可及版权声明仍须保留。

Fabric `fabric.mod.json` 的字段片段：

```json
{
  "credits": "界面使用优易库 Oriel UI（作者：云诺羲Rinorsi）",
  "contact": {
    "oriel_source": "https://github.com/Rinorsi/Oriel-UI",
    "oriel_bilibili": "https://b23.tv/hjUvs0c",
    "oriel_afdian": "https://afdian.com/a/Rinorsi"
  }
}
```

合并到自己的元数据；保留自己模组的 `homepage` / `sources`，不要把它们换成库地址。上述扩展 contact 键是附加信息，各启动器是否展示未验证。

NeoForge 在自己已有的 `[[mods]]` 表内增加：

```toml
credits = "界面使用优易库 Oriel UI（作者：云诺羲Rinorsi）；https://github.com/Rinorsi/Oriel-UI"
```

作者链接：[B站](https://b23.tv/hjUvs0c) · [爱发电](https://afdian.com/a/Rinorsi) · [仓库](https://github.com/Rinorsi/Oriel-UI)。也可放进模组描述或发布页；客户端是否把 credits 中的链接渲染为可点击未验证。
