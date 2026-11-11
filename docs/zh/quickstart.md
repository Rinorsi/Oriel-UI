# 只有 jar 时的最小 Fabric 工程

**先读[分发指南](distribution.md)。** 本页是 Oriel 0.15 的本地 jar 外部必需依赖配方，面向没有源码仓库或 Maven 仓库的下游。使用匹配目标的 0.15.0 瘦包；独立玩家安装也可选同 minor 完整版 0.15.1。示例仍需在自己的游戏目标中验证，不能用库的构建检查替代下游验收。

[入口](index.md) · [接入指南](integration.md) · [Java API](java-api.md)

## 目录

```
my-mod/
  settings.gradle
  gradle.properties
  build.gradle
  libs/oriel-fabric-26.1.2-0.15.0.jar          ← 库 jar 放这里
  src/main/java/com/example/mymod/MyModClient.java
  src/main/resources/fabric.mod.json
  src/main/resources/assets/mymod/oriel/hello.html
```

Gradle wrapper 用你自己的（`gradle wrapper`，Gradle 9.x，JDK 25）。把下面所有 `mymod` / `com.example.mymod` 换成你自己的模组 id 与包名，二者必须一致：**页面资源的命名空间 = 模组 id**。

## 版本对应

| Minecraft | `minecraft_version` | `fabric_version` | 库 jar |
|---|---|---|---|
| 26.1.2 | `26.1.2` | `0.155.3+26.1.2` | `oriel-fabric-26.1.2-0.15.0.jar` |
| 26.2 | `26.2` | `0.161.0+26.2` | `oriel-fabric-26.2-0.15.0.jar` |
| 26.3 | `26.3` | `0.161.0+26.3` | `oriel-fabric-26.3-0.15.0.jar` |

`fabric_loader_version` 目前都是 `0.19.5`。以上来自 [版本适配](maintainers/versions.md)；换版本时以该页为准。

## settings.gradle

```groovy
pluginManagement {
    repositories {
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
    }
}
plugins {
    // 自动下载构建所需的 Java 工具链
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}
rootProject.name = 'my-mod'
```

## gradle.properties

```properties
minecraft_version=26.1.2
fabric_loader_version=0.19.5
fabric_version=0.155.3+26.1.2
oriel_version=0.15.0

mod_id=mymod
mod_name=My Mod
mod_version=1.0.0
mod_group_id=com.example.mymod
mod_authors=You
mod_description=My first Oriel page.
mod_license=MIT
```

## build.gradle

```groovy
plugins {
    // 26.x 未混淆，用这个 Loom（不是旧的 fabric-loom 重映射版）
    id 'net.fabricmc.fabric-loom' version '1.18.2'
}

version = mod_version
group = mod_group_id
base.archivesName = mod_id

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${fabric_loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"
    // 库就是 libs/ 里这个 jar；玩家把同一个 jar 当作单独的 mod 安装
    implementation files("libs/oriel-fabric-${minecraft_version}-${oriel_version}.jar")
}

tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8' }

processResources {
    def props = [minecraft_version: minecraft_version, fabric_loader_version: fabric_loader_version,
                 oriel_version: oriel_version, mod_id: mod_id, mod_name: mod_name, mod_version: mod_version,
                 mod_authors: mod_authors, mod_description: mod_description, mod_license: mod_license]
    inputs.properties(props)
    filesMatching('fabric.mod.json') { expand(props) }
}
```

这里不需要任何 Maven 仓库配置（Loom 自带 Minecraft 与 Fabric 的仓库）。如果你有本仓库，也可以用 `publishToMavenLocal -Poriel_slim` 发布瘦包后改成 `implementation "dev.oriel:oriel-fabric-${minecraft_version}:${oriel_version}"`（Maven 只允许瘦包，省略 `-Poriel_slim` 会报错；完整版仅作玩家安装的 jar 分发，不作为 Maven 依赖），见 [接入指南](integration.md)。

## src/main/resources/fabric.mod.json

```json
{
    "schemaVersion": 1,
    "id": "${mod_id}",
    "version": "${mod_version}",
    "name": "${mod_name}",
    "description": "${mod_description}",
    "authors": ["${mod_authors}"],
    "license": "${mod_license}",
    "environment": "*",
    "entrypoints": { "client": ["com.example.mymod.MyModClient"] },
    "depends": {
        "fabricloader": ">=${fabric_loader_version}",
        "fabric-api": "*",
        "minecraft": "~${minecraft_version}",
        "java": ">=25",
        "oriel": ">=0.15.0 <0.16.0"
    }
}
```

`oriel` 这一行就是对库的**必需依赖**，范围限定在 0.15 系列，瘦包 `0.15.0` 与完整版 `0.15.1` 均满足。用完整版时同步修改 `oriel_version` 和实际 jar 文件；跨 minor 需要重新迁移和验证。

## 入口类：一个命令打开页面

```java
package com.example.mymod;

import dev.oriel.mod.client.OrielScreens;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

public final class MyModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("hello").executes(context -> {
                    // 等聊天界面关闭后再开页面，否则页面会被一起关掉
                    Minecraft.getInstance().schedule(() -> OrielScreens.open("mymod:oriel/hello.html"));
                    return 1;
                })));
    }
}
```

## 页面：src/main/resources/assets/mymod/oriel/hello.html

```html
<!doctype html>
<html class="mc-dim">
<head>
  <title>Hello</title>
  <style>
    .box { width: 200px; padding: 8px; background: #2b2b2b; border: 1px solid #777; }
    h1 { margin: 0 0 6px; font-size: 16px; }
    button { margin-top: 6px; }
  </style>
</head>
<body class="mc-center">
  <div class="box">
    <h1>Hello, Oriel</h1>
    <p>这是我的第一个页面。</p>
    <button onclick="oriel.close()">关闭</button>
  </div>
</body>
</html>
```

`mc-dim`、`mc-center` 给页面原版风格的暗背景和居中，见 [元素参数](elements.md#原版外观类)。`oriel.close()` 见 [脚本与交互合同](scripting.md)。

## 构建与运行

```sh
./gradlew build        # 产物：build/libs/<mod_id>-<mod_version>.jar
./gradlew runClient    # 开发环境启动游戏；库 jar 会作为 mod 一起加载
```

进游戏后在聊天里输入 `/hello`。

## 没有本仓库时怎么检查

仓库里的页面检查、预览窗口、MCP 工具（见 [验收工具](validation.md)）**不在库 jar 里**，只有拿到本仓库才能用。只有 jar 时请用游戏内的办法：

| 想做什么 | 做法 |
|---|---|
| 打开任意页面 | `/oriel open mymod:oriel/hello.html` |
| 不建工程，先看一个 .html | 把页面（连同它的 css、图片）放进游戏的 `config/oriel/my-pages/`，进游戏 `/oriel open oriel_mine:文件名.html`（子文件夹照写，如 `oriel_mine:item-editor/index.html`）；完整版也可在 模板中心 → 我的页面 里点开。页面里用相对路径引用同文件夹的资源。保存即热重载 |
| 改了页面看效果 | 开发环境保存文件会热重载已打开的页面；也可以 `/oriel reload` |
| 看库自带的例子 | **仅完整版**：`/oriel showcase`；示例源码在完整版 jar 的 `assets/oriel/oriel/showcase/` 下。瘦包不含演示，复用时[复制资源到自己模组](templates.md#怎么复用到你的模组) |
| 确认库已加载 | 日志中的实际运行版本应与加载器选中结果一致（瘦包 `0.15.0` / 完整版 `0.15.1`），模组列表里有 `oriel`；混装不按编译时版本猜测 |
| 页面写错了 | 不支持的 CSS/HTML 会在日志里报带文件和位置的诊断，不会静默忽略 |

## 我的页面：标题、副标题、封面，以及库的动效

瘦包与完整版均保留 `config/oriel/my-pages/` 的 `oriel_mine` 注册和热重载。下述模板中心卡片说明只适用于完整版；瘦包使用 `/oriel open oriel_mine:<目录>/index.html`，Home 动作打开设置，也可直接用 `/oriel settings`、`/oriel about`。完整版中卡片内容写在页面自己的 `<head>` 里，全部可选：

```html
<html data-oriel-shell>   <!-- 想要涟漪、聚光、数字计数、主题切换等库的效果就加；纯展示可以不加 -->
<head>
  <title>物品工坊</title>                                          <!-- 标题，最长 40 字，没写用文件夹名/文件名 -->
  <meta name="oriel-subtitle" content="编辑手里物品的组件">          <!-- 副标题，最长 80 字；没写用 description，再没有就显示路径 -->
  <meta name="oriel-cover" content="images/cover.png">             <!-- 封面 PNG，相对页面；没写则找同目录的 cover.png，再找 页面名.png -->
```

封面推荐 16:9（官方封面 320×180），其他比例会被裁切；封面必须是文件夹内的 PNG。鼠标放到卡片上会显示完整标题、副标题、文件路径、修改时间和大小，超长内容会被截断，不会出框。

## 开发时热更新

按[热更新指南](hot-reload.md)选择源码目录或 dev-sources 配置，确认启动日志显示实际读取位置后再制作页面。页面保存可热重载；替换 Java 库、绑定或引擎仍须重启客户端。

## 页面路径约定

资源 URL 是 `命名空间:路径`，路径本身没有强制目录。本手册给你的页面用 `assets/<模组id>/oriel/…`；库自己的内置页面放在 `assets/oriel/ui/…` 和 `assets/oriel/oriel/…`，两者只是不同包的各自习惯，并不冲突。
