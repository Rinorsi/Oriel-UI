# 绘制诊断与 SDF 着色器

## 绘制命令检查与实验缓存

本页描述当前版本的绘制诊断与窄扩展。默认绘制已走新路径；实验缓存不代表整个绘制引擎已替换，也不代表真实 GPU 验收通过。

## 绘制模式

启动 JVM 时使用 -Doriel.paintMode=old|new|both，缺省 new（`old` 回到旧绘制路径）；其他值立即报错。Java 宿主也可使用 document.painter().mode(Painter.Mode.NEW)，仅影响该文档。

| 模式 | 行为 |
|---|---|
| old | 保留原来的逐帧绘制 |
| new | 缓存不依赖外部资源的渐变装饰命令；文本、原生内容、纹理和效果组继续实时执行 |
| both | 对缓存命中项重新生成命令，与缓存逐参数比较，差异立即报页面、元素和命令；实际后端只画一次 |

both 是装饰命令语义检查，不会在游戏中读取 GPU 像素。逐 RGBA 对照通过独立软件后端测试执行；两者不能互相代替。所有模式保留后端自己的圆角、边框和阴影原语，因此不会将 SDF 强制降为普通四边形。

缓存键包含元素身份、稳定计算样式快照、布局树、设备像素与完整装饰几何。动画及其继承计算产生的样式副本直接绘制，避免逐帧录制不能复用的程序。移动、缩放、重新布局或改变样式会重建对应程序；外部图片、Canvas 来源、精灵、border-image 不进入缓存，不猜测宿主资源世代。它不是完整子树缓存，也没有局部屏幕重绘。最多 1024 项、4 MiB 估计命令载荷，超限清空缓存；计数不代表 Java 堆精确占用或 GPU 纹理字节。

只合并相邻且元素标签相同的四边形调用，每批最多 4096 个；不会跨 save/restore、变换、透明度、裁剪、遮罩、效果组或原生绘制重排。原生元素收到真实后端 Canvas，命令流保留其执行位置，回放时仍执行实时内容。

游戏后端在 JVM new/both 模式下保留原有 64px 尺寸桶，并在确认上一帧已绘制、目标全部可回收时，将闲置目标保留量限制到 64 MiB（颜色与深度按 4/8 字节每像素估计），优先保留最近使用项。资源重载沿用原来的清池。此限制不是活动帧峰值显存上限，不能在仍被 GPU 使用时销毁目标；真实六目标 GPU 行为仍须实机核验。

另可在游戏启动时显式设置 -Doriel.paintMetrics=true，让已有层诊断日志附加 Canvas 的 liveTextureBytes、dirtyRowUploadBytes、dirtyRowUploads。缺省关闭；计数仅覆盖成功提交的脏行上传，不包括初建 DynamicTexture 的上传、驱动内部复制或 GPU 耗时。liveTextureBytes 是活跃 Canvas 的 4 字节像素载荷，不包含离屏深度附件或驱动开销。这是内部开发诊断，不是下游模组公开 API。

## 诊断入口

    preview path/to/page.html --snapshot /external/page.png --frames 2 --paint-trace /external/paint.json
    preview path/to/page.html --snapshot /external/page.png --paint-trace /external/paint.json --paint-element "#reward"

仅支持页面快照，不能与 --actions 或 --canvas-test 合用。输出最后一帧的 JSON（page、mode、cache、commands），以及同路径附加 .jsonl 和 .txt 的逐命令版本。目录须已存在；不能写进源码仓库。--paint-element 是元素标签子串过滤，不是 CSS 选择器。重复或缺失 id 不能唯一定位节点；匿名盒子单独标注。

MCP oriel_paint 接受 page，可选 width（1–1920，缺省 640）、height（1–1080，缺省 360）、frames（1–600，缺省 2）、element。返回最多 500 条命令、完整文件路径、commandCount、truncated、缓存计数与 display 的命令/四边形批次/组/原生边界计数。它复用无头预览，不启动游戏，不构成页面交付批准。

每次导出是最后一帧的完整快照，覆盖指定输出文件，不是增量补丁。`commands` 每项的 `command` 是原始零起始序号，`element` 是标签，`operation` 是调用名，`arguments` 是按该 Canvas 方法签名排列的参数数组。坐标是当前变换下的 GUI 像素，颜色是有符号 32 位 ARGB；`boundedGroup` 的最后一个参数是后端是否接受组，`native` 的首项只导出宿主类型名，`skip` 的首项是跳过原因。JSONL 每行一条同结构命令。元素过滤保留原始序号，不重新编号；MCP 的 commandCount 是过滤后数量，display 是未过滤的整帧计数。超过 500 条只截短 MCP 回执，磁盘快照保留完整命令。

Java：先调用 document.painter().trace(true)，绘制后读取 lastDisplayList()，使用 json(filter)、jsonLines(filter)、text(filter) 或 replay(canvas)。每份录制最多 100000 条命令，超限明确报错，不静默截断。回放要求相同初始状态、资源和兼容后端；效果组接受能力变化会明确报错。trace(false) 清掉上次快照。默认不构造命令诊断字符串或逐帧诊断对象；仍有模式/开关判断。

文字命令导出的字体只含 families、size、bold、italic；不序列化宿主字体对象、字形缓存或资源句柄。该导出描述逻辑字体请求，不是字体资源包。

命令包含变换、裁剪、组、mask、backdrop 与原生内容边界。透明度为零、隐藏、匿名装饰会记录 skip 原因；尚不提供逐像素遮挡归因、完整 display:none 子树解释、上传字节或 GPU 耗时。工具输出不能宣称某原生物品 Sheen 已经正确。

## Canvas 线段与混合窄子集

ctx.strokeLine(x0,y0,x1,y1,cap?) 是 Oriel 扩展，不是浏览器标准方法。四个坐标必须是有限数值，单位为画布像素；cap 为 butt（缺省）、round 或 square。使用 strokeStyle、lineWidth、globalAlpha 和 globalCompositeOperation；返回 undefined，不改变样式。零长 round 画圆点，零长 butt/square 不绘制。非有限长度、未知 cap、参数数量错误抛 TypeError。像素中心覆盖，无抗锯齿；同一线段的端帽与主体只混合一次，没有通用路径、连接、曲线或滤镜。

ctx.globalCompositeOperation 缺省 source-over，仅支持 source-over、lighter、screen；不支持的值抛 TypeError（这里刻意不同于浏览器忽略非法赋值）。状态随 save/restore 保存；影响 fillRect、strokeRect、fillQuad、strokeLine、drawImage。clearRect 与 putImageData 保留直接替换语义。旧页面不设置时像素算法不变。

    const ctx = document.getElementById('fx').getContext('2d');
    if (typeof ctx.strokeLine !== 'function') throw new Error('Oriel strokeLine is required');
    ctx.save();
    ctx.strokeStyle = '#ffd08080';
    ctx.lineWidth = 3;
    ctx.globalCompositeOperation = 'lighter';
    ctx.strokeLine(4, 8, 60, 28, 'round');
    ctx.restore();

线段由 CPU 在有界像素区域栅格化；不是 GPU Canvas。Java 库变化需要更新 JAR 并重启。现有 clip-path 的动画仍是离散插值；本次不承诺 Canvas 渐变、完整路径或原生物品扫光已补齐。

## 用有向距离着色器绘制盒子（维护者资料）

[入口](../index.md) · [版本适配](versions.md) · [元素：平滑圆角还是像素锐利](../elements.md#平滑圆角还是像素锐利) · [26.3 GUI API 笔记](minecraft-26.3.md)

**读者：维护渲染后端的人。** 页面作者只需要知道一件事：圆角、边框和阴影可以用 `shape-rendering: crispEdges` 回到像素锐利，或用设置 `client.shapeShader=false` 全局关闭着色器（见[元素](../elements.md#平滑圆角还是像素锐利)）。

## 状态

已实现，受设置 `client.shapeShader` 控制：`Canvas.fillBoxShadow`、`SmoothShape`、`SdfRenderState`、`assets/oriel/shaders/core/sdf_box` 和 `McCanvas` 里的重写。页面可用 `shape-rendering: crispEdges` 要求硬边，预览器会画出同样的形状。

通过目标客户端日志确认管线初始化，并分别检查视觉、缩放及失败回退；预览通过不能代替 GPU 验收。

加载器管线初始化见下文“实际构建”。

## 为什么

每个圆角矩形、边框和 box-shadow，原来都在 CPU 上被细分成纯色四边形（`engine/.../paint/Shapes.java`、`Gradients.java`、`Shadows.java`，由 `QuadBatch` 收集），再用原版的 `RenderPipelines.GUI` 绘制。这有三个代价：

- **边缘有锯齿。** 四边形没有抗锯齿，曲线在每个设备像素处出现台阶。8 px 圆角、GUI 缩放 2 时还可以，但在大圆角、缩放 1 或 `transform: rotate()` 下会很明显。
- **模糊是假的。** 模糊的阴影是 2 到 6 个同心环加 smoothstep 渐变（`Shadows.ramp`）。大模糊会出现色带，每个阴影有几十个四边形。
- **顶点数多。** 一个带边框和一个阴影的圆角盒子有几百个顶点，每帧重建，并在 `McCanvas.fillQuads` 里复制（它不能保留调用方的数组）。

有向距离（SDF）片段着色器把其中每一个都画成**一个四边形**：着色器逐像素算出它离形状边缘多远，再把距离换算成覆盖率。边缘在任何缩放或旋转下都是抗锯齿的，高斯阴影也有廉价的闭式解。

## 范围

26.x 的 SDF 路径处理三类图元：

| 图元 | 引擎入口 | 着色器处理的条件 | 否则 |
|---|---|---|---|
| 圆角填充 | `Canvas.fillRoundedRect` | 每个角都是圆形（rx = ry） | 细分（原路径） |
| 边框 | `Canvas.fillBorder` | 四边同色同宽、圆角为圆形 | 细分 |
| 盒子阴影 | 新增 `Canvas.fillBoxShadow` | 圆角为圆形；未偏移的外阴影还要求盒子不透明 | 细分 |

这覆盖了页面实际用到的东西：`.mc-panel`、按钮、卡片、辉光。椭圆角、每边不同颜色或宽度、虚线/点线/凹槽/凸脊/双线边框仍走原来的代码，它仍是参考行为。

不在范围内：渐变（它们已经通过 `QuadBatch` 裁剪成圆角形状；移到着色器是以后的步骤）和**圆角 `overflow: hidden` 裁剪**。SDF 做不了裁剪，因为裁剪必须作用于盒子里的一切，包括用原版管线绘制的文字、物品、精灵和 3D 场景。这需要遮罩：模板缓冲，或先把子树画到离屏目标再通过 SDF blit。这是另一个项目。

## 引擎侧（`engine`，所有版本）

1. `Canvas` 提供入口，缺省实现运行细分：

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

一条管线 `oriel:pipeline/sdf_box`，着色器在 `assets/oriel/shaders/core/sdf_box.{vsh,fsh}`，按 [GUI API 笔记](minecraft-26.3.md)所述构建：QUADS、半透明混合、不剔除（所以镜像变换不需要重绕）、`GLOBALS`、`PROJECTION` 和 `DYNAMIC_TRANSFORMS` 绑定组。

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

实际做法：`engine/.../paint/SmoothShape` 决定着色器画什么并描述它（四边形、盒子、半径、边框宽度或模糊），并持有片段着色器的 Java 移植（`coverage`）。`McCanvas` 把一个 `SmoothShape` 变成着色器的顶点；预览器的 `ShapeRasterizer` 在四边形下每个设备像素的中心对 `coverage` 采样，并像 GPU 那样混合它。所以两者画同样的形状，由同一个地方决定，两端共用形状与覆盖率计算，实际结果仍须按目标后端核对。`SmoothShapeTest` 检查覆盖率的数学。

## 实际构建

- **顶点格式。**管线可以声明自己的 `VertexFormat`，`BufferBuilder` 能把原版八个属性名中的任意一个写进去，所以布局接近上表。阴影需要的比能放下的多，所以阴影的扩散量放在 `Position.z`（GUI 管线没有深度测试），它的标志放在 `Normal`。带偏移的内阴影还需要两个 float，所以它们走细分。阴影用一个角半径模糊，所以角各不相同的阴影也走细分。
- **每种一条管线。**`oriel:pipeline/sdf_box`（填充和边框）和 `oriel:pipeline/sdf_shadow`（同样的着色器，定义了 `SHADOW`），不剔除，所以镜像变换不需要重绕。
- **管线初始化与回退。**原版首次使用时按需编译管线；`SdfRenderState.available()` 请求两者，失败（着色器没加载、驱动拒绝它们）会被记录，形状改走细分，直到资源重载（`OrielClient.onResourceReload`）时再请求一次。
- **镂空。**设计里的 `boxOpaque` 标志其实不需要：`fillBoxShadow` 传入盒子和 `Shadow`，所以着色器知道盒子，并在未偏移的外阴影之下把它留空，和细分的做法一样。
- **方形仍是四边形。**不带圆角的填充和边框、不模糊的方形阴影保持精确的四边形，它们在整设备像素上本来就同样锐利。

### 26.3

26.3 管线与类型使用 renderpearl API。运行目标客户端时检查管线初始化、形状页和失败回退，并分别覆盖 OpenGL/Vulkan；不能用软件预览替代 GPU 对照。

### 26.2

- 26.2 的 `BufferBuilder` 写七个属性名（`Position`、`Color`、`UV0`、`UV1`、`UV2`、`Normal`、`LineWidth`），没有 `UV3`。在 26.2 上，`SdfRenderState`（它的 `<26.3` 块）把四边形做成盒子在每一边都扩大同一个边距（调用对任意一边要求的最大值），并把边距放进 `Position.z`，这样着色器得到半尺寸就是 `|UV0|` 减去边距。阴影只有一个半径，放得进 `UV1`，它的扩散量以 float 的位模式拆在 `UV2` 的两个 short 里传递。
- 着色器源码是共用的。26.2 的管线定义 `ORIEL_26_2`，它把 `sdf_box.vsh` 和 `.fsh` 切换到 26.2 的约定：用 `#moj_import` 而不是 `#include`（原版的 include 文件在两个版本里对 `DynamicTransforms` 的排序不同，所以着色器必须用它们），输入输出不写 `layout(location)`，因为 26.2 的两个后端都按名字匹配属性和 varying。
- `GpuDevice.precompilePipeline(...).isValid()` 代替 26.3 的 `getCompiledPipelineNullable`。设备会把失败的管线一直保留到资源重载，也就是 `available()` 再次询问的时候。
- 每个目标平台分别检查 OpenGL/Vulkan 的形状、阴影与资源重载，不沿用旧发行包结论。

## 限制与验证范围

- OpenGL 与 Vulkan 的管线编译、抗锯齿与混合须分别实测。
- 半透明盒子、非一致圆角及带偏移的内阴影按上述条件走细分；扩大 SDF 范围前须核对顶点预算与画面。
- 字体、宿主原生内容和像素取整仍可能使预览与游戏不同，不保证逐像素等价。
