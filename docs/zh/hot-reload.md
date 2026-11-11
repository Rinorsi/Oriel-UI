# 热更新：怎么配、怎么确认、什么会热

**先看这一页，不要自己发明开关。** 保存页面文件后，已经打开的页面在约 1 秒内重新加载，不用重启游戏。是否生效取决于"页面从哪里读"，下面按你的情况选一种。

## 1. 选路：你在哪种情况

| 你的情况 | 怎么做 | 要配置吗 |
|---|---|---|
| 想在游戏里预览自己写的 `.html`（没有工程） | 放进游戏的 `config/oriel/my-pages/`，`/oriel open oriel_mine:文件名.html` | 不用；**所有环境**都热，发布版也一样 |
| 做模组，用 Gradle 的 `runClient`，运行目录在工程里（默认的 `run/`） | 直接 `runClient`，改 `src/main/resources/assets/<模组id>/…` 保存 | **不用**，库从工作目录往上自动找到你的源码资源 |
| 做模组，`runClient` 的运行目录在工程外（例如指到别的盘、`daily/…/run`） | 给 `runClient` 加 JVM 参数 `-Doriel.sourceRoot=<工程目录>`（见第 2 节） | 加一行 |
| 不用 Gradle，只是把模组 jar 丢进 `mods/` 开发 | 在游戏的 `config/oriel/dev-sources.properties` 写一行 `命名空间=源码里 assets/命名空间 的目录`（见第 3 节） | 加一个小配置文件 |
| 开发 Oriel 库自己 | 直接 `./gradlew :fabric:26.1:runClient`，构建已带参数 | 不用 |

只有这几种，**没有第六种**。不要让模组读自己的配置去指向文件夹页，也不要写监视脚本复制文件：库已经内置，页面地址保持正式地址（`mymod:ui/index.html`），控制器、物品读写、服务端交互都照旧。

## 2. 运行目录在工程外：`-Doriel.sourceRoot`

告诉库"我的源码资源在哪"。值是**含有 `src/main/resources/assets` 的工程目录**；多模块工程填那个子工程目录。

```groovy
// build.gradle（Fabric Loom）—— 已实测
loom {
    runs {
        client {
            vmArg "-Doriel.sourceRoot=${project.projectDir.absolutePath}"
        }
    }
}
```

NeoForge（ModDevGradle）：`runs { client { systemProperty 'oriel.sourceRoot', project.projectDir.absolutePath } }`。

## 3. 只丢 jar：`config/oriel/dev-sources.properties`

文件在**游戏的** `config/oriel/` 下（不是工程里），一行一个命名空间：

```properties
# 命名空间=源码里 assets/<命名空间> 这个目录；写成 assets 目录也行，库会自己找下一层
mymod=C:/work/my-mod/src/main/resources/assets/mymod
```

- 页面、样式表、脚本、JSON 数据从这个目录读并监视；目录里**没有**的文件自动用 jar 里的，所以漏一个不会崩。
- 它在**任何环境**都生效，包括玩家环境：**别把这个文件发给玩家**。
- 保存后在游戏中确认页面已重载，实际时间受资源与环境影响。

## 4. 怎么确认配好了（看游戏日志）

| 日志里的这一行 | 意思 |
|---|---|
| `Oriel: dev mode, reading UI sources from [<你的 assets 目录>] and reloading pages when they are saved` | 第 1、2 种生效，读的是你工程的源码 |
| `Oriel: dev source: 'mymod:' pages are read from <目录> and reloaded when saved` | 第 3 种生效 |
| `Oriel: pages in 'oriel_mine:' come from <目录>` | `my-pages` 文件夹可用 |
| `Oriel: dev-sources.properties: 'mymod' needs an existing folder, got <路径>` | 第 3 种的路径写错了，目录不存在 |
| 都没有 | 没配好：对照第 1 节检查是不是运行目录在工程外、`-D` 参数有没有进到 `runClient` |

页面里写 `console.log('v1')`，日志里会看到 `[mymod:ui/index.html] v1`，保存后出现新的一行说明已重载。

## 5. 什么会热，什么不会

| 改动 | 结果 |
|---|---|
| 页面引用的外部 `.css` | 只清样式缓存，下一帧重算；DOM、控制器、脚本、焦点、输入、滚动都保留 |
| HTML（含内联 `<style>`）、脚本、页面读取的 JSON | 整页重建；控制器用 `saveState/restoreState` 保存的状态会恢复，其他 JS 状态不恢复 |
| **图片（PNG）** | **不热**（第 1–3 种里图片仍取自 jar/构建输出），换图要重新构建；放在 `my-pages` 里的图片是热的 |
| 语言文件、字体、纹理 | 不热，要 F3+T 资源重载 |
| Java 代码 | 开发环境能热替换一部分，增删字段/方法、改加载器元数据要重启；不要承诺免重启 |
| 开始运行**之后才新建**的文件 | 不触发刷新；保存一个已有文件，或重新打开页面 |

文件监视每 200ms 轮询"页面已经读取过的文件"，没读过的文件不会被监视。

## 6. 不灵的时候

| 现象 | 原因与办法 |
|---|---|
| 改了源码，游戏里没变，日志没有第 4 节任何一行 | 运行目录在工程外且没加 `-Doriel.sourceRoot`；或 jar 丢进 mods 但没写 `dev-sources.properties` |
| 日志有 `dev mode…`，页面还是旧的 | 你改的不是库读的那个目录（改了构建输出或另一份拷贝）；日志里那行方括号里的路径就是被读的目录 |
| 改的文件被重载了但样子没变 | 改的是图片或语言文件（见第 5 节），换图要重新构建，语言文件要 F3+T |
| 重载后页面状态丢了 | 只有 CSS 保留全部状态；HTML/脚本改动会整页重建，需要控制器 `saveState/restoreState` |
| 玩家环境也出现了热重载 | 把 `dev-sources.properties` 发出去了；删掉它 |
| 服务端打开的页面（`OrielServer.open`）不热 | 服务端打开的页面读不到文件夹页（第 1 种）；在第 1–3 种源码路径下它们是否热更新**没有验证** |

## 7. 给 AI 的规则

开始做页面**之前**先确认第 1 节里有一条已经配好（日志能看到第 4 节的某一行），再开始改。不要用"复制文件 + 后台监视脚本"、"读自定义配置再指向文件夹页"之类的绕路办法；也不要为了看效果每次重新构建。把改动发到对话里的截图和游戏里的实时效果配合使用，见 [制作流程](ui-workflow.md) 的"快速迭代"。

相关：[快速入门](hot-reload.md)、[页面接口里的文件夹命名空间](pages.md)、[验收工具里的热重载边界](validation.md#游戏与热重载)。
