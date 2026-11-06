# 只有 jar 时的最小 Fabric 工程

适用：你拿到的是打包好的库 jar（`oriel-fabric-<MC 版本>-0.10.0.jar`），没有本仓库，也没有 Maven 仓库。下面的文件在 Minecraft 26.1.2 + Fabric 上**实际编译并启动过**：构建通过，游戏日志里同时出现你的 mod 和 `oriel 0.10.0`。页面是否按预期渲染，需要你在游戏里打开确认（见末尾）。

[入口](index.md) · [接入指南](integration.md) · [Java API](java-api.md)

## 目录

```
my-mod/
  settings.gradle
  gradle.properties
  build.gradle
  libs/oriel-fabric-26.1.2-0.10.0.jar          ← 库 jar 放这里
  src/main/java/com/example/mymod/MyModClient.java
  src/main/resources/fabric.mod.json
  src/main/resources/assets/mymod/oriel/hello.html
```

Gradle wrapper 用你自己的（`gradle wrapper`，Gradle 9.x，JDK 25）。把下面所有 `mymod` / `com.example.mymod` 换成你自己的模组 id 与包名，二者必须一致：**页面资源的命名空间 = 模组 id**。

## 版本对应

| Minecraft | `minecraft_version` | `fabric_version` | 库 jar |
|---|---|---|---|
| 26.1.2 | `26.1.2` | `0.155.3+26.1.2` | `oriel-fabric-26.1.2-0.10.0.jar` |
| 26.2 | `26.2` | `0.161.0+26.2` | `oriel-fabric-26.2-0.10.0.jar` |
| 26.3 | `26.3` | `0.161.0+26.3` | `oriel-fabric-26.3-0.10.0.jar` |

`fabric_loader_version` 目前都是 `0.19.5`。以上来自 [版本适配](versions.md)；换版本时以该页为准。

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
oriel_version=0.10.0

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

这里不需要任何 Maven 仓库配置（Loom 自带 Minecraft 与 Fabric 的仓库）。如果你有本仓库，也可以用 `publishToMavenLocal` 后改成 `implementation "dev.oriel:oriel-fabric-${minecraft_version}:${oriel_version}"`，见 [接入指南](integration.md)。

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
        "oriel": ">=0.10.0 <0.11.0"
    }
}
```

`oriel` 这一行就是对库的**必需依赖**，范围限定在 0.10 系列；库升级到 0.11 前不会影响你。

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
    h1 { margin: 0 0 6px; font-size: 12px; }
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
| 不建工程，先看一个 .html | 把页面（连同它的 css、图片）放进游戏的 `config/oriel/my-pages/`，进游戏 `/oriel open oriel_mine:文件名.html`（子文件夹照写，如 `oriel_mine:item-editor/index.html`）；也可在 模板中心 → 我的页面 里点开。页面里用相对路径引用同文件夹的资源。保存即热重载 |
| 改了页面看效果 | 开发环境保存文件会热重载已打开的页面；也可以 `/oriel reload` |
| 看库自带的例子 | `/oriel showcase`（首页画廊，后面可加页面名）；示例页面源码在库 jar 的 `assets/oriel/oriel/showcase/` 下（可用 `unzip` 查看） |
| 确认库已加载 | 日志里应有 `Oriel UI runtime version: 0.10.0`，模组列表里有 `oriel` |
| 页面写错了 | 不支持的 CSS/HTML 会在日志里报带文件和位置的诊断，不会静默忽略 |

## 页面路径约定

资源 URL 是 `命名空间:路径`，路径本身没有强制目录。本手册给你的页面用 `assets/<模组id>/oriel/…`；库自己的内置页面放在 `assets/oriel/ui/…` 和 `assets/oriel/oriel/…`，两者只是不同包的各自习惯，并不冲突。
