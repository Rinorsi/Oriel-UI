# 用有向距离着色器绘制盒子（维护者资料）

[入口](index.md) · [版本适配](versions.md) · [元素：平滑圆角还是像素锐利](elements.md#平滑圆角还是像素锐利) · [26.3 GUI API 笔记](mc-26-3-gui-api.md)

**读者：维护渲染后端的人。** 页面作者只需要知道一件事：圆角、边框和阴影可以用 `shape-rendering: crispEdges` 回到像素锐利，或用设置 `client.shapeShader=false` 全局关闭着色器（见[元素](elements.md#平滑圆角还是像素锐利)）。

## 状态

已实现，受设置 `client.shapeShader` 控制：`Canvas.fillBoxShadow`、`SmoothShape`、`SdfRenderState`、`assets/oriel/shaders/core/sdf_box` 和 `McCanvas` 里的重写。页面可用 `shape-rendering: crispEdges` 要求硬边，预览器会画出同样的形状。

实机证据（2026-11-06）：六个目标（Fabric 和 NeoForge 的 26.1、26.2、26.3）的游戏日志里都输出了 `Oriel's shape shader is ready (rounded boxes and soft shadows on the GPU)`，即着色器管线编译成功。26.1 的具体差异以 `SdfRenderState` 里的 Stonecutter 条件为准。尚未验证的是：平滑风格的视觉正确性（需要看截图）、故意让着色器失败时的回退外观。

尚未做：向加载器注册管线（见下文"实际构建"）。

## 为什么

每个圆角矩形、边框和 box-shadow，原来都在 CPU 上被细分成纯色四边形（`engine/.../paint/Shapes.java`、`Gradients.java`、`Shadows.java`，由 `QuadBatch` 收集），再用原版的 `RenderPipelines.GUI` 绘制。这有三个代价：

- **边缘有锯齿。** 四边形没有抗锯齿，曲线在每个设备像素处出现台阶。8 px 圆角、GUI 缩放 2 时还可以，但在大圆角、缩放 1 或 `transform: rotate()` 下会很明显。
- **模糊是假的。** 模糊的阴影是 2 到 6 个同心环加 smoothstep 渐变（`Shadows.ramp`）。大模糊会出现色带，每个阴影有几十个四边形。
- **顶点数多。** 一个带边框和一个阴影的圆角盒子有几百个顶点，每帧重建，并在 `McCanvas.fillQuads` 里复制（它不能保留调用方的数组）。

有向距离（SDF）片段着色器把其中每一个都画成**一个四边形**：着色器逐像素算出它离形状边缘多远，再把距离换算成覆盖率。边缘在任何缩放或旋转下都是抗锯齿的，高斯阴影也有廉价的闭式解。

## 范围

第一版替换 26.x 上的三样东西：

| 图元 | 引擎入口 | 着色器处理的条件 | 否则 |
|---|---|---|---|
| 圆角填充 | `Canvas.fillRoundedRect` | 每个角都是圆形（rx = ry） | 细分（原路径） |
| 边框 | `Canvas.fillBorder` | 四边同色同宽、圆角为圆形 | 细分 |
| 盒子阴影 | 新增 `Canvas.fillBoxShadow` | 圆角为圆形；未偏移的外阴影还要求盒子不透明 | 细分 |

这覆盖了页面实际用到的东西：`.mc-panel`、按钮、卡片、辉光。椭圆角、每边不同颜色或宽度、虚线/点线/凹槽/凸脊/双线边框仍走原来的代码，它仍是参考行为。

不在范围内：渐变（它们已经通过 `QuadBatch` 裁剪成圆角形状；移到着色器是以后的步骤）和**圆角 `overflow: hidden` 裁剪**。SDF 做不了裁剪，因为裁剪必须作用于盒子里的一切，包括用原版管线绘制的文字、物品、精灵和 3D 场景。这需要遮罩：模板缓冲，或先把子树画到离屏目标再通过 SDF blit。这是另一个项目。

## 引擎侧（`engine`，所有版本）

1. 给 `Canvas` 加一个方法，缺省实现运行原来的细分：

   ```java
   /**
    * 绘制盒子的一个 box-shadow。shape 是阴影经偏移和扩散后的矩形，radii 是它的角半径（同 fillRoundedRect），
    * blur 是 CSS 模糊半径。内阴影被裁剪到 clip/clipRadii（内边距盒）。boxOpaque 表示边框盒遮住了它下面的东西。
    */
   default void fillBoxShadow(float[] shape, float[] radii, float blur, int argb, boolean inset,
                              float[] clip, float[] clipRadii, boolean boxOpaque) { ... }
   ```

   `Shadows` 把它的几何代码保留在这个缺省实现之后；`BoxPainter` 调用画布，而不是直接调 `shadows.outer/inset(batch, ...)`。

2. 引擎别处不变。`fillRoundedRect` 和 `fillBorder` 本来就是为此存在的（`Canvas` 的 javadoc 写着"后端可以用更好的东西，比如 SDF 着色器，重写它们"）。

3. 测试：`RecordingCanvas` 记录新调用，已有的阴影测试改成通过缺省实现调用 `Shadows`。

## Minecraft 侧（`common`）

### 管线

一条管线 `oriel:pipeline/sdf_box`，着色器在 `assets/oriel/shaders/core/sdf_box.{vsh,fsh}`，按 [GUI API 笔记](mc-26-3-gui-api.md)所述构建：QUADS、半透明混合、不剔除（所以镜像变换不需要重绕）、`GLOBALS`、`PROJECTION` 和 `DYNAMIC_TRANSFORMS` 绑定组。

26.2 上这些类在 `com.mojang.blaze3d.*`，26.3 上在 `com.mojang.renderpearl.api.*`；管线构建器放在各版本的 `McGui` 源码里。

### 把参数交给着色器

`GuiRenderer` 不绑定自定义 uniform，所以一切都按顶点传递。每个四边形在它的四个顶点上重复同样的形状参数。设计时的顶点格式：

| 属性 | 类型 | 携带 |
|---|---|---|
| `Position` | 3 × float | 屏幕位置：形状的边界按模糊扩大，再加 1 个设备像素用于抗锯齿边缘 |
| `Color` | RGBA8 | 颜色，不透明度已乘进去（同原来） |
| `UV0` | 2 × float | 顶点在盒子局部空间里相对其中心的位置 |
| `UV3` | 2 × float | 半宽、半高 |
| `UV1`、`UV2` | 各 2 × int16 | 四个角半径，单位 1/16 px（最大 2047 px） |
| `LineWidth` | float | 模式和大小：0 = 填充；> 0 = 该宽度的边框；< 0 = 阴影，σ = −值（CSS σ = blur / 2） |

在局部空间里计算，并用 `fwidth(distance)` 抗锯齿，可以让边缘在任何 `transform`（包括旋转）下保持一个设备像素宽，`McCanvas` 对四边形本来就支持旋转。

### 片段着色器

- **填充：**覆盖率 = `clamp(0.5 - d / fwidth(d), 0, 1)`，其中 `d` 是圆角盒子的距离（Inigo Quilez 的 `sdRoundBox`，每个象限一个半径）。
- **边框：**`max(d_outer, -d_inner)`，其中 inner 是盒子按宽度收缩、半径取 `max(0, r - width)`，这就是均匀边框的 CSS 内圆角。
- **阴影：**Evan Wallace 的模糊圆角矩形闭式解：沿 x 的精确高斯积分，沿 y 几个采样。四到八个采样在任何模糊程度下都像真正的高斯，色带因此消失。
- **内阴影：**1 减去外轮廓，再用内边距盒裁剪作为第二个距离。

### McCanvas

`McCanvas` 重写这三个方法。每个方法检查着色器能不能画这个调用（圆形半径、单一边框颜色等），像四边形那样计算屏幕边界，并添加一个 `SdfBoxRenderState`（与 `QuadsRenderState` 并列的新 `GuiElementRenderState`）；否则调用 `Canvas` 的缺省实现。裁剪、不透明度和变换都不变。

`GuiRenderer` 在一个节点内按（裁剪、管线、纹理）排序，所以同一图层的盒子仍会批处理成一次绘制。SDF 盒子和原版的填充交错，只在它们重叠的地方每次切换多付一次绘制调用。

## 让预览器保持诚实

预览器的 `ImageCanvas` 是关着抗锯齿绘制的，所以在着色器出现之前，它和游戏逐像素一致。游戏一旦给盒子抗锯齿，预览器也必须这样，否则在预览器里调好的页面在游戏里看起来不同，而预览器存在的目的就是避免这个。

实际做法：`engine/.../paint/SmoothShape` 决定着色器画什么并描述它（四边形、盒子、半径、边框宽度或模糊），并持有片段着色器的 Java 移植（`coverage`）。`McCanvas` 把一个 `SmoothShape` 变成着色器的顶点；预览器的 `ShapeRasterizer` 在四边形下每个设备像素的中心对 `coverage` 采样，并像 GPU 那样混合它。所以两者画同样的形状，由同一个地方决定，预览器与游戏在字体和一个亚像素的布局取整之内一致。`SmoothShapeTest` 检查覆盖率的数学。

## 实际构建

- **顶点格式。**管线可以声明自己的 `VertexFormat`，`BufferBuilder` 能把原版八个属性名中的任意一个写进去，所以布局接近上表。阴影需要的比能放下的多，所以阴影的扩散量放在 `Position.z`（GUI 管线没有深度测试），它的标志放在 `Normal`。带偏移的内阴影还需要两个 float，所以它们走细分。阴影用一个角半径模糊，所以角各不相同的阴影也走细分。
- **每种一条管线。**`oriel:pipeline/sdf_box`（填充和边框）和 `oriel:pipeline/sdf_shadow`（同样的着色器，定义了 `SHADOW`），不剔除，所以镜像变换不需要重绕。
- **还没有加载器注册。**原版会在首次使用时编译一个没被告知的管线；`SdfRenderState.available()` 请求两者，失败（着色器没加载、驱动拒绝它们）会被记录，形状改走细分，直到资源重载（`OrielClient.onResourceReload`）时再请求一次。
- **镂空。**设计里的 `boxOpaque` 标志其实不需要：`fillBoxShadow` 传入盒子和 `Shadow`，所以着色器知道盒子，并在未偏移的外阴影之下把它留空，和细分的做法一样。
- **方形仍是四边形。**不带圆角的填充和边框、不模糊的方形阴影保持精确的四边形，它们在整设备像素上本来就同样锐利。

### 26.3

在 NeoForge 26.3 上，用 Mesa 软件 Vulkan（OpenGL 后端在那个虚拟显示上开不了窗口）通过开发自动巡检检查过：每个页面都能画，自动巡检的 26 项检查通过，`dev/shapes.html` 覆盖了上述情形。

### 26.2

- 26.2 的 `BufferBuilder` 写七个属性名（`Position`、`Color`、`UV0`、`UV1`、`UV2`、`Normal`、`LineWidth`），没有 `UV3`。在 26.2 上，`SdfRenderState`（它的 `<26.3` 块）把四边形做成盒子在每一边都扩大同一个边距（调用对任意一边要求的最大值），并把边距放进 `Position.z`，这样着色器得到半尺寸就是 `|UV0|` 减去边距。阴影只有一个半径，放得进 `UV1`，它的扩散量以 float 的位模式拆在 `UV2` 的两个 short 里传递。
- 着色器源码是共用的。26.2 的管线定义 `ORIEL_26_2`，它把 `sdf_box.vsh` 和 `.fsh` 切换到 26.2 的约定：用 `#moj_import` 而不是 `#include`（原版的 include 文件在两个版本里对 `DynamicTransforms` 的排序不同，所以着色器必须用它们），输入输出不写 `layout(location)`，因为 26.2 的两个后端都按名字匹配属性和 varying。
- `GpuDevice.precompilePipeline(...).isValid()` 代替 26.3 的 `getCompiledPipelineNullable`。设备会把失败的管线一直保留到资源重载，也就是 `available()` 再次询问的时候。
- 在 macOS 上用 OpenGL 后端（Metal 上的 GL 4.1），通过开发自动巡检在 NeoForge 26.2 上检查过：形状页的样子和 26.3 一样。

## 尚未回答的问题

- 26.3 的 OpenGL 后端画出来和它的 Vulkan 后端一样吗？26.3 只在 Vulkan 上跑过，26.2 只在 OpenGL 上跑过。
- 未偏移的外阴影会把边框盒下面的部分挖掉，这样半透明盒子不会变暗。着色器还需要盒子自己的形状，而不只是阴影的形状，这放不进顶点预算。设计里只在盒子不透明时才走着色器路径。这是否足够常见，还是应该给镂空单独的属性？
