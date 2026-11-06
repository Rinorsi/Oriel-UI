# Oriel mod 模板

模板不会自动转译 H5，也不会替业务生成权限校验与菜单。开发见[制作流程](../docs/zh/ui-workflow.md)、[Java API](../docs/zh/java-api.md)，完成后按[验收工具](../docs/zh/validation.md)验证。

两个小 mod 可供起步，一个 NeoForge、一个 Fabric，面向 Minecraft 26.3。每个都加了一个 `/notes` 命令，打开一个笔记页面：mod 把你的笔记交给页面，页面把你的增删发回来。

![笔记页面](notes.png)

## 开始一个 mod

复制你的加载器对应的文件夹：

```bash
cp -R template/neoforge my-mod
```

（或 `template/fabric`）。然后在 `my-mod` 里：

1. 在 `gradle.properties` 里设置 `mod_id`、`mod_name`、`mod_group_id` 等。
2. 把包 `com.example.examplemod` 和文件夹 `assets/examplemod` 改成对应的名字。在 NeoForge 上，`ExampleModClient` 里的 `MOD_ID` 也要一致。
3. `./gradlew runClient`，进入一个世界，输入 `/notes`。

在 macOS 上，如果客户端在第一帧卡住，在 `runs/client/options.txt` 里加 `enableVsync:false`。

## 里面有什么

- `src/main/resources/assets/examplemod/oriel/notes.html` 是页面：HTML、CSS 和一个 Vue 风格模板，加几行脚本。
- `Notes.java` 带着数据打开页面，并处理它发来的消息。
- `ExampleModClient.java` 注册 `/notes`。
- `build.gradle` 从本仓库的 `build/template-maven` 或 `mavenLocal()` 获取 Oriel。在仓库里运行 `./gradlew templateCompile`，会把当前工件发布到私有构建目录并编译两个模板。对复制出去的工程，请在本仓库运行 `publishToMavenLocal`，或传 `-PorielRepository=<本地仓库>`。没有仓库、只有 jar 时见[只有 jar 时的最小 Fabric 工程](../docs/zh/quickstart.md)。
- 当前模板用 0.10 系列依赖范围：Fabric `>=0.10.0 <0.11.0`，NeoForge `[0.10.0,0.11.0)`。1.0 之前，不兼容的变更会提升 minor 版本。推荐必需依赖；Jar-in-Jar 是可选的。
- `neoforge.mods.toml` 或 `fabric.mod.json` 把 Oriel 设成必需依赖。玩家像装其他 mod 一样安装它，如果你在 Modrinth 和 CurseForge 页面上把它列为必需依赖，启动器会替他们装。想只发一个 jar，就把 Oriel 打进你的 jar（见[接入指南](../docs/zh/integration.md#依赖写法)）。

`runClient` 运行期间，保存 `notes.html` 会重载打开的页面，不用重启游戏就能改。[预览器](../preview/README.md)可以完全不启动 Minecraft 就显示页面。

## 下一步

- [页面接口](../docs/zh/pages.md)：数据、消息、从服务器打开页面。
- [容器页面](../docs/zh/containers.md)：带真实槽位的物品栏。
- [脚本](../docs/zh/scripting.md)：JavaScript 和模板。
- [全部文档](../docs/zh/index.md)
