# 分发指南：瘦包、完整版与嵌入依赖

[文档入口](index.md) · [接入指南](integration.md) · [0.11 → 0.12 迁移](upgrading.md) · [安全与设置](security.md)

**下游模组接入、打包或升级前先读本页。** 本页规定 0.12 起的分发合同：瘦包 **0.12.0**、完整版 **0.12.1**，两者的 mod id 都是 `oriel`。六目标双变体已构建并通过发行检查；嵌套检测已在 Fabric / NeoForge 26.3 专服各五场景实测，设置与缓存通过单元测试，通知页通过桌面预览。**Fabric 26.3 客户端已验证瘦包入口、热重载及嵌套提示／关闭／恢复；NeoForge 26.3 已验证瘦包启动与设置／关于页。** 六目标发行检查是原冻结 0.12 产物的结果；当前 showcase 核心修复触发 pin 拒绝，须提升 minor 后重新发布。其他版本客户端、NeoForge 嵌套提示及真实下游客户端业务仍未验证，具体范围见第 5 节。

## 1. 两种分发方式

| 方式 | 怎么交付 | 好处 | 代价与限制 |
|---|---|---|---|
| 嵌入（Jar-in-Jar） | 把对应 Minecraft / 加载器的瘦包嵌入自己的 jar，仍声明 `oriel` 必需依赖 | 玩家只装你的模组，首次安装无需另找库 | 下游 jar 增大；更新核心通常需要重新发布下游；多个模组内置不同副本时须验共存，可能出现嵌套提示 |
| 随 Oriel UI 一起分发（外部必需依赖） | 下游只声明依赖；玩家从 Oriel 官方渠道另装 Oriel，发布平台同时标记必需依赖 | 下游 jar 更小；共享独立安装的一份库，玩家可直接获得官方更新与修复 | 玩家必须安装匹配范围的库；跨 minor 更新仍需先迁移下游 |

**两种方式都支持，由下游自行选择；推荐外部必需依赖。** 所有消费者都使用外部依赖时，没有嵌套副本，也就没有嵌套冲突提示。若别的模组仍内嵌 Oriel，独立安装完整版也须按第 5 节核对，不能保证不提示。Modrinth / CurseForge 等只是分发渠道示例，实际可用下载地址以项目发布公告为准；没有公开 Maven 仓库的承诺，不要使用上游 Vellum 的仓库。

## 2. 变体与版本规则

| 内容 | 瘦包 `slim` | 完整版 `full` |
|---|---|---|
| 引擎、Rhino、script-rhino、mc-extension、kit 基础层（Shell、Motion、PageControllers、Lang、Widgets 等） | 保留 | 保留相同核心 |
| `oriel:ui/ore.css`、基础样式、字体、`textures/ore`、`textures/icons`、基础语言键 | 保留 | 保留 |
| 设置页、关于页、`oriel_mine` 我的页面与热重载 | 保留 | 保留 |
| 嵌套检测与提示页 | 0.12 起提供；Fabric 26.3 游戏提示已测，NeoForge 游戏提示未验证 | 同瘦包 |
| 官方模板与封面、头像、模板目录及模板中心 | 不含 | 包含 |
| showcase / demo 页面与图片、组件库和图标库展示页、Mobdex、OrielDemos 演示注册 | 不含 | 包含 |
| `data/oriel/test_instance`、GameTest 等参考/测试内容 | 不含 | 完整版/测试分发按构建规则提供 |
| 开发检查器、自动验收、开发 agent 等开发工具 | `-Poriel_slim` 同时应用 `-Poriel_release` 的排除规则 | 开发构建可含；发布玩家包另加 `-Poriel_release` 排除 |

图标与基础控件可以复用，**展示图标/控件的页面不属于核心**。瘦包的 `/oriel showcase` 在当前源码保留为说明入口（原冻结 0.12.0 报参数错误；修复须随下一 minor 发布），执行时明确提示“本构建不含演示内容”，不列出演示子命令；其他参考入口不注册，Shell 接线前移除不存在的内置 `data-go` 目标。两种变体都提供 `/oriel settings`、`/oriel about`；瘦包的 Home 动作打开模组设置，`Shell.back()` 直接关闭页面。这里的 Home 指“打开优易库首页”快捷键动作，玩家环境默认不绑定。我的页面仍通过 `/oriel open oriel_mine:<目录>/index.html` 打开并保留热重载；瘦包关于页的模板头像回退到核心 emblem。瘦包启动时 INFO 记录变体、实际版本及“参考内容未包含（官方模板/showcase/demo）”。Fabric 26.3 已用实际瘦包验证命令、Home 动作（开发环境 F8）、Shell 返回、移除死导航及我的页面保存热重载；NeoForge 26.3 已验证实际瘦包启动与设置／关于页。

从 0.12 起遵循以下规则：

- 核心每次有任何改动，包括小 bug 修复，都提升 minor，例如 `0.12 → 0.13`。瘦包版本永远为 `X.Y.0`。
- 完整版为 `X.Y.N`，`N ≥ 1`。只改参考内容时只发完整版，例如 `0.12.1 → 0.12.2`；不得夹带核心改动。
- **为什么同 minor 完整版更高？** 两者使用同一个 `oriel` id；当加载器在满足全部消费者约束后采用较高版本时，会选择包含参考内容的完整版。同 minor 内核心必须相同，因此增加参考内容不应改变核心合同。实际混装选择仍以加载器实测为准，不是“任意条件都选最新版”的保证。
- 版本单一来源为仓库根 `gradle.properties` 的核心 `X.Y.0` 和 `oriel_full_patch`（≥1），不在两处分别手写版本。`-Poriel_slim` 构建瘦包，不加则构建完整版；文件名里的版本不同，互不覆盖。
- Maven 坐标不加变体分类器：`dev.oriel:oriel-<loader>-<mc>:<版本>`。本次瘦包用 `0.12.0`，完整版用 `0.12.1`；例如 `oriel-fabric-26.1.2-0.12.0.jar` 与 `oriel-fabric-26.1.2-0.12.1.jar`。
- 下游声明同 minor 范围：Fabric `>=0.12.0 <0.13.0`，NeoForge `[0.12.0,0.13.0)`，两种变体均满足。0.11 的消费者（包括 Editor）必须迁移、重新编译并验证，不能只放宽上限。

## 3. 下游应依赖什么

运行时依赖目标加载器的 **Oriel 核心合同**，遵循[公共 API 与稳定性范围](integration.md#稳定性与兼容性)。瘦包包含某个类不等于该类是稳定 API；不导入 `dev.oriel.mod.internal`。

**不要把官方模板、showcase、demo、模板目录或演示注册当成运行时依赖。** 需要某个模板或示例时，从仓库源码或完整版取出所需 HTML/CSS/JS、图片及其他引用资源，复制到自己模组的 `assets/<你的模组id>/…`，改为自己的 URL，保留适用许可声明。具体操作见 [Showcase 复用指南](showcase.md#怎么复用到你的模组)和[官方模板目录](templates.md)。复制后仅在必要的基础样式、字体和图标上引用 `oriel:`；不要让页面继续跳向 `oriel:ui/templates/…` 或 `oriel:oriel/showcase/…`。

`ForgeMenu` 是公开 API，核心保留其类、`TYPE` 注册与公开 API；瘦包取消默认演示 screen 和 `/oriel-forge` 演示命令，自己的菜单页面与业务应由自己的模组接入。发布前至少用瘦包检查一次资源和类依赖，以发现被完整版掩盖的缺失。

## 4. Gradle 与加载器元数据

以下为 **0.12 配方**。六目标库产物已构建，仓库 `templateCompile` 已用瘦包编译 Fabric / NeoForge 两模板；复制出去的工程、本地 Maven 发布配方和真实下游运行仍未验证。适用 Groovy DSL、Java 25、Minecraft 26.1.2 / 26.2 / 26.3；坐标必须匹配实际加载器和游戏版本。取得对应工件并放入自己的 Maven 仓库，或在 Oriel 仓库执行 `./gradlew publishToMavenLocal -Poriel_slim` 发布瘦包 `0.12.0`，不加 `-Poriel_slim` 发布完整版 `0.12.1`，然后使用 `mavenLocal()`；复制出去的[模板](../../template/README.md)也按这个原则获取依赖。

### Fabric：嵌入瘦包

```groovy
repositories { mavenLocal() }
dependencies {
    include(implementation("dev.oriel:oriel-fabric-26.3:0.12.0"))
}
```

Loom 负责生成嵌套 jar 列表，不手填不存在的内部路径。自己的 `fabric.mod.json` 将以下字段合并到现有 `depends`，不要覆盖 Minecraft、Fabric API 等其他依赖：

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
    implementation("dev.oriel:oriel-neoforge-26.3:0.12.0")
    jarJar("dev.oriel:oriel-neoforge-26.3") {
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

只加运行/编译依赖，不加 `include` 或 `jarJar`；本例用完整版便于查看参考内容，自己的页面仍只依赖核心：

```groovy
repositories { mavenLocal() }
dependencies {
    implementation("dev.oriel:oriel-fabric-26.3:0.12.1")
    // NeoForge 工程改用 dev.oriel:oriel-neoforge-26.3:0.12.1
}
```

Fabric / NeoForge 元数据仍使用上面的必需依赖范围，并在发布页面设 Oriel 为必需依赖。玩家从 Oriel 渠道安装范围内的版本；`implementation` 本身不会替玩家安装库。只有本地 jar 的用法见[最小 Fabric 工程](quickstart.md)。

## 5. 多个模组都带 Oriel 时

加载器先满足全部消费者约束，再选用一份 Oriel；范围无交集应拒绝加载。跨 minor 混装不能靠嵌套检测修复 API 不兼容，也不能靠关闭提示绕过加载器的依赖检查。

**嵌套检测与提示 0.12 起提供；专服、单元和预览的验证范围见下表。** 生效副本在客户端和专服枚举已加载模组 jar：Fabric 递归查 `fabric.mod.json` 的 `jars`，NeoForge 递归查 `META-INF/jarjar/metadata.json`，从父 jar 读取被加载器淘汰的副本；独立安装的顶层 Oriel 也计入。来源使用父模组元数据的显示名，生效者依据加载器实际选择的来源与版本识别，检测器不自行选版本。

- 每份新 jar 生成 `META-INF/oriel/manifest.json`（`schema=1`），包含版本、`variant`（`slim|full`）、loader、mc、`coreHash`、`apiHash` 和 `{path,sha256}` 库存。清单上限 **200 KiB**；class 按包聚合，普通核心资源逐条记录，`apiHash` 对 `api/<mc>.txt` 的**原始字节**取 SHA-256（Minecraft 26.1.2 使用项目快照 `api/26.1.txt`）。生成器与发行检查共用 `tools/release/boundaries.json` 和核心哈希逻辑；分类表同时打入 `META-INF/oriel/boundaries.json`，作为运行时忽略规则的唯一来源且属于必需核心。清单自身不参与核心哈希，避免自引用。
- 风险方向是“被淘汰副本里有，生效副本里没有或不同”的核心条目；`apiHash` 不同也算风险。参考内容和开发工具不参与风险比较。版本、核心相同不产生风险提示；不同版本但核心与 API 相同也不发风险 WARN。旧 jar **没有清单**时只比较版本；未知或损坏清单及扫描错误明确 WARN，不能把坏清单当成正常旧副本或兼容证据。
- 每次启动一行 **INFO** 摘要，列出各副本版本、来源和生效副本；有风险时一行 **WARN** 点名涉及的模组，**DEBUG** 展开条目差异。没有风险仍有启动摘要；摘要不等于业务/API 兼容验收。专服只写日志，不链接客户端提示类。
- `config/oriel/nested-check.json` 保存 installation stamp、逻辑指纹、完整报告和已提示指纹集合。stamp 包含已装列表、路径/尺寸/mtime 与活动清单摘要；stamp 相同走缓存快速路径，不再展开嵌套 archive。逻辑指纹包含模组列表/版本及副本 `coreHash`/来源；每个指纹至多提示一次，切换安装后回到旧指纹也不重复提示。缓存原子写入；损坏缓存拒绝使用，保存为 `nested-check.invalid.json` 后重建。
- 客户端首次进入没有 loading overlay 的标题界面（TitleScreen）时处理 Ore 风格提示；正文可滚动，底栏固定。提示说明哪些模组内置了不同版本、当前用了谁带的哪版，以及界面样式可能异常。继续/返回保留原标题屏；提示本身不阻止游戏，不保证任意组合的业务或二进制兼容。**Fabric 26.3 已在真实标题界面验证弹窗和鼠标勾选，截图列出两个父模组及生效版本；NeoForge 客户端提示未验证。**
- `client.nestedNotice` 默认 **`on`**，配置允许 **`on/off`**；设置页以同名开关呈现，内部类型是 Choice/enum（`NestedNotice.ON/OFF`）。提示页勾选“不再提示”立即将 `client.nestedNotice=off` 写回 `config/oriel.properties`；恢复默认回到 `on`，保留已提示指纹历史。关闭只抑制客户端提示，不关闭扫描和风险日志，也不改变版本选择。白名单、非法值/boolean 拒绝、持久化和恢复已单测；Fabric 26.3 实机已验证勾选写入 off、重启不提示、全新缓存下 off 抑制提示、设置开关恢复 on 后重启提示。设置页“恢复默认”按钮本轮未做实机验证。完整协议见[安全与设置](security.md#设置)。

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
| 真正游戏内弹窗、标题时机、勾选、重启显示、设置开关恢复 | Fabric 26.3 **已验证**：不同核心提示／同核心不提示／off 抑制／恢复 on；NeoForge **未验证** |
| 瘦包设置/关于交互与我的页面热重载 | Fabric 26.3 **已验证**；NeoForge 26.3 设置/关于 **已验证**，热重载 **未验证** |
| 26.1.2 / 26.2 客户端、真实 Editor 客户端业务、多人 | 本轮 **未验证** |

上述 B 专服五场景共 10/10 通过，覆盖两加载器 26.3，均回读 `/oriel version` 并正常停服。全量 `:preview:checkUi` 为 85 页、84 OK、1 个既有 about 设计 warning、0 failed；新通知页零警告不能写成全项目零警告。

## 6. 发布前检查清单

下面是发布要求，**不是已通过记录**。Oriel 维护者逐目标检查，Fabric / NeoForge × 26.1.2 / 26.2 / 26.3 不能互相代替；下游作者至少检查自己的目标与分发方式。

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
