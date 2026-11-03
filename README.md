# Oriel UI（优易库）

基于 [Vellum](https://github.com/MaxLeiter/vellum)（MIT）开发。

Oriel UI 是面向 Minecraft Java 版的模组 UI 库：用类 HTML/CSS 的声明式文件写界面，
引擎负责样式、布局、动画与输入，再用游戏自己的 GUI 渲染器画出来。第一套主题对齐 Ore UI 的像素斜角风格。

> 0.x 阶段，公共 API 随时可能有破坏性变更。

## 构建

需要 JDK 25 与 git（构建时会下载 Rhino 源码并打补丁）。

```bash
./gradlew build
```

## 运行演示

```bash
./gradlew :fabric:26.3:runClient
```

进入世界后在聊天栏输入 `/vellum demo`。

## 许可证

见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。
