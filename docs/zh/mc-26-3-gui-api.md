# Minecraft 26.3 客户端 GUI API 笔记（维护者资料）

[入口](index.md) · [版本适配](versions.md) · [后端构建与维护](backend-maintenance.md)

**读者：维护 `common/` 里 Minecraft 后端、或把库移植到新 Minecraft 版本的人。** 做页面和接入的人不需要读这份。它是针对 26.3 核对过的笔记，对照的是原版 26.3（NeoForm 26.3-1）、NeoForge 26.3.0.26-beta 和 Fabric API 0.161.0+26.3；26.1 和 26.2 的差异见[版本适配](versions.md)。

反编译的原版源码：第一次构建之后，从 `common/versions/26.3/build/moddev/artifacts/` 解压 `vanilla-26.3-1-sources.jar`。NeoForge 打过补丁的源码：从 `neoforge/versions/26.3/build/moddev/artifacts/` 取 `minecraft-patched-26.3.0.26-beta-sources.jar`。

## 0. 26.x 变了什么

- `GuiGraphics` 现在是 **`net.minecraft.client.gui.GuiGraphicsExtractor`**。绘制是"提取"：每次调用都把一个渲染状态对象记录进 `GuiRenderState`；`GuiRenderer` 之后批量绘制。
- `render(...)` 现在是 `Renderable`/`Screen` 上的 `extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float a)`。`renderBackground` 变成 `extractBackground`；`renderLabels`/`renderSlot` 变成 `extractLabels`/`extractSlot`。
- `ResourceLocation` 现在是 **`net.minecraft.resources.Identifier`**：`fromNamespaceAndPath`、`withDefaultNamespace`、`parse`、`tryParse`。
- GPU API：`com.mojang.renderpearl.api.*`（`RenderPipeline`、`GpuTextureView`、`GpuSampler`、`GpuFormat`、`PrimitiveTopology`、`FilterMode`）。有 OpenGL 和 Vulkan 两个后端；GLSL 被编译成 SPIR-V。`com.mojang.blaze3d.*` 仍有 `RenderSystem`、`NativeImage`、`VertexConsumer`、`PoseStack`、`Window`、`InputConstants`。
- 窗口和输入使用 SDL3（GLFW 没了）。键码是 SDL 扫描码（`KEY_ESCAPE=41`、`KEY_RETURN=40`）。鼠标按键是 `LEFT=1`、`MIDDLE=2`、`RIGHT=3`。修饰键掩码：`MOD_SHIFT=3`、`MOD_CONTROL=192`、`MOD_ALT=768`。
- 界面：`minecraft.gui.setScreen(screen)` 和 `minecraft.gui.screen()`。没有 `Minecraft.setScreen`。在 NeoForge 上，`Screen.onClose()` 调用 `minecraft.gui.popScreenLayer()`。

## 1. GuiGraphicsExtractor

`new GuiGraphicsExtractor(Minecraft, GuiRenderState, int mouseX, int mouseY)`。`Gui.extractRenderState` 每帧创建一个。`pose` 是 `Matrix3x2fStack(16)`。

```java
Matrix3x2fStack pose()              // org.joml: pushMatrix()/popMatrix()/translate(float,float)/scale(float,float)/rotate(float rad)
int guiWidth(); int guiHeight();
void nextStratum();                 // 新的顶层图层：此后画的一切都在此前画的一切之上
void blurBeforeThisStratum();       // 菜单模糊；每帧调用两次会抛异常
void enableScissor(int x0,int y0,int x1,int y1); // 按 pose 变换（轴对齐包围盒），与父裁剪求交
void disableScissor();
boolean containsPointInScissor(int x,int y);
void requestCursor(CursorType);     // 在帧末应用一次
```

- pose 栈深度限制在大约 15 次 push。不要每个 DOM 元素 push 一次。自己维护一个矩阵栈并 `pose().set(...)`，或 push 一层、之后每次绘制前设置矩阵。
- 颜色是 ARGB 整数。辅助函数在 `net.minecraft.util.ARGB`（`color`、`multiplyAlpha`、`alpha/red/green/blue`、`srgbLerp` 等）。
- alpha 为 0 时 `text(...)` 被跳过。**没有分组不透明度**：要把 alpha 乘进每个元素。**物品不能染色或淡出**；它们是从物品图集 blit 出来的。

### 矩形

```java
void fill(int x0,int y0,int x1,int y1,int col)                       // RenderPipelines.GUI
void fill(RenderPipeline pipeline,int x0,int y0,int x1,int y1,int col)
void fillGradient(int x0,int y0,int x1,int y1,int colTop,int colBottom) // 只有垂直方向
void outline(int x,int y,int width,int height,int color)
void horizontalLine(int x0,int x1,int y,int col); void verticalLine(int x,int y0,int y1,int col);
void textHighlight(int x0,int y0,int x1,int y1, boolean invertText)
```

- 坐标是整数。要亚像素位置，用浮点数平移或缩放 pose。
- `fill` 发出的顶点顺序是 (x0,y0)、(x0,y1)、(x1,y1)、(x1,y0)。
- GUI 管线会剔除背面。镜像的 pose（负缩放）会把四边形剔除。自定义管线应使用 `.withCull(false)`。

### 纹理和精灵

```java
void blit(RenderPipeline p, Identifier tex, int x,int y, float u,float v, int w,int h, int texW,int texH[, int color])
void blit(RenderPipeline p, Identifier tex, int x,int y, float u,float v, int w,int h, int srcW,int srcH, int texW,int texH[, int color])
void blit(Identifier tex, int x0,int y0,int x1,int y1, float u0,float u1,float v0,float v1)      // GUI_TEXTURED，归一化 UV
void blit(GpuTextureView view, GpuSampler sampler, int x0,int y0,int x1,int y1, float u0,float u1,float v0,float v1)
void blitSprite(RenderPipeline p, Identifier sprite, int x,int y,int w,int h[, float alpha | int color])
void blitSprite(RenderPipeline p, Identifier sprite, int spriteW,int spriteH,int texX,int texY,int x,int y,int w,int h[, int color])
```

- `blitSprite(Identifier...)` 自动处理九宫格和平铺。它读取精灵 `.mcmeta` 里的 `gui.scaling`：`stretch`、`tile{width,height}` 或 `nine_slice{width,height,border,stretch_inner}`。例如 `widget/button.png.mcmeta`：`{"gui":{"scaling":{"type":"nine_slice","width":200,"height":20,"border":3}}}`。
- `assets/<ns>/textures/gui/sprites/<path>.png` 里的精灵会作为 `Identifier(ns, "<path>")` 加入 GUI 图集。
- `.png` 纹理以 REPEAT + NEAREST 采样，除非它的 `.mcmeta` 写了 `{"texture":{"blur":true,"clamp":true}}`。所以 `background-repeat` 可以用大于 1 的 u/v 去 blit。
- 管线：`RenderPipelines.GUI_TEXTURED`、`GUI_TEXTURED_PREMULTIPLIED_ALPHA`、`GUI`（无纹理）。
- 纹理 id：`TextureManager.getTexture(Identifier)` 自动加载 `ns:textures/....png`。

### 文字

```java
void text(Font f, String s, int x,int y,int color[, boolean dropShadow])   // 缺省带阴影
void text(Font f, FormattedCharSequence|Component s, int x,int y,int color[, boolean dropShadow])
void centeredText(...); int textWithWordWrap(...); void textWithBackdrop(...)
```

### 物品和实体

```java
void item(ItemStack s, int x,int y[, int seed]); void fakeItem(ItemStack s,int x,int y)
void itemDecorations(Font f, ItemStack s, int x,int y[, String countText])   // 数量、耐久、冷却
void entity(EntityRenderState rs, float scale, Vector3fc translation, Quaternionfc rotation,
            @Nullable Quaternionfc overrideCameraAngle, int x0,int y0,int x1,int y1)
```

物品是 16×16。它们预先渲染进 `GuiItemAtlas`，大小是 16×GUI 缩放 px，再按 pose blit，所以大于 1 的缩放会出现像素化。

### 提示框（延迟到帧末绘制在最上层）

```java
setTooltipForNextFrame(Component c, int x,int y)
setTooltipForNextFrame(Font f, ItemStack s, int x,int y)
setComponentTooltipForNextFrame(Font f, List<Component> lines, int x,int y)
```

### 绘制顺序

`GuiRenderState` 是一个**层（stratum）**列表，每层是一条节点链。新元素被放在它的边界所相交的、已有内容的最高节点之上一个节点。同一节点内，元素按（裁剪、管线、纹理）重新排序以便批处理。所以画家顺序只对**重叠**的元素成立，而那正是顺序唯一可见的情形。调用 `nextStratum()` 强制"此后的一切都在最上面"，例如下拉框、模态框和提示框。

**`bounds()` 为 null 的元素会被丢弃。**

### 自定义渲染元素（任意四边形、自定义着色器）

```java
public interface GuiElementRenderState extends ScreenArea {      // ScreenArea: @Nullable ScreenRectangle bounds();
    void buildVertices(VertexConsumer vertexConsumer);
    RenderPipeline pipeline();
    TextureSetup textureSetup();
    @Nullable ScreenRectangle scissorArea();
}
// 原版：ColoredRectangleRenderState、BlitRenderState
vertexConsumer.addVertexWith2DPose(pose, x, y).setUv(u, v).setColor(argb);
```

- `TextureSetup.noTexture()`、`TextureSetup.singleTexture(GpuTextureView, GpuSampler)`。
- 采样器：`RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR|NEAREST)`、`getRepeat(FilterMode)`。
- 边界：`new ScreenRectangle(x, y, w, h).transformMaxBounds(pose)`，再与裁剪求交。它们不能为 null。

**提交一个自定义元素。** `guiRenderState` 在原版里是私有的：

- **NeoForge** 增加了 `g.submitGuiElementRenderState(state)`、`g.submitPictureInPictureRenderState(state)` 和 `g.peekScissorStack()`。
- **Fabric** 的传递访问扩展（access widener）暴露了 `g.guiRenderState`（`addGuiElement`、`addPicturesInPictureState`）和 `g.scissorStack`。
- **公共代码**：Oriel 不用 Mixin。通过访问扩展 / access transformer 放宽可见性（见 `common/src/main/resources/oriel.accesswidener` 和 `META-INF/accesstransformer.cfg`），或者经加载器服务来访问。

对于曲面细分的几何体，使用 `RenderPipelines.GUI`，它是 `POSITION_COLOR` 加 QUADS 拓扑。每个四边形 4 个顶点，三角形就是最后一个顶点重复的四边形。

**GuiRenderer。** 管线、裁剪或 `TextureSetup` 一变就开始新的一次绘制。只绑定 `Sampler0..2`、`DynamicTransforms`、`Projection`、`Fog`、`Globals` 和 `Lighting`，所以**没有自定义 uniform**。每个元素的着色器参数必须作为顶点属性传递。`BufferBuilder` 可以写 `Position`、`Color`、`UV0`（RG32F）、`UV1`/`UV2`（RG16 SINT）、`UV3`（RG32F，`setUv3`）、`Normal` 和 `LineWidth`，全部按名字绑定。投影是 `setupOrtho(..., width/guiScale, height/guiScale)`，所以 GUI 单位是缩放后的像素。

**自定义管线（例如 SDF 圆角矩形）。**

```java
RenderPipeline.builder()
  .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/sdf_rect"))
  .withBindGroupLayout(BindGroupLayouts.GLOBALS).withBindGroupLayout(BindGroupLayouts.PROJECTION)
  .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
  .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/sdf_rect"))     // assets/<ns>/shaders/core/sdf_rect.vsh
  .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/sdf_rect"))
  .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
  .withVertexBinding(0, FORMAT).withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false).build();
```

- 着色器是 GLSL `#version 330`，带 `#extension GL_ARB_separate_shader_objects : require`。varying 需要显式的 `layout(location=N)`。include 写作 `#include <minecraft:dynamictransforms.glsl>`。
- 注册：
  - NeoForge：`RegisterRenderPipelinesEvent.registerPipeline`。
  - Fabric：通过访问扩展调用 `RenderPipelines.register`。
  - 未注册的管线也会在首次使用时惰性编译。

## 2. 文字和字体

`Font`：

- `lineHeight = 9`
- `int width(String|FormattedText|FormattedCharSequence)`（向上取整）
- `List<FormattedCharSequence> split(FormattedText, int maxWidth)`
- `plainSubstrByWidth`
- `StringSplitter getSplitter()`，它的方法有：
  - `float stringWidth(...)`
  - `int plainIndexAtWidth(String, int maxWidth, Style)`
  - `findLineBreak`
  - `splitLines`

`Style`（`net.minecraft.network.chat.Style`，不可变，`with*`）：

- `withColor(int rgb)`、`withShadowColor(int argb)`、`withoutShadow()`
- `withBold/withItalic/withUnderlined/withStrikethrough/withObfuscated(Boolean)`
- `withFont(FontDescription)`

用 `Component.literal(s).withStyle(style)` 构建文字。粗体会加宽字形，所以要用同一个样式去量。

字体：`new FontDescription.Resource(Identifier)`（`FontDescription.DEFAULT` = `minecraft:default`）。原版带 `default`、`uniform`、`alt` 和 `illageralt`，由 `assets/<ns>/font/<name>.json` 定义（提供者类型 `bitmap`、`ttf`、`space`、`unihex`、`reference`）。

阴影：`ARGB.scaleRGB(color, 0.25)`，偏移 1px。样式颜色替换 RGB，但保留传给 `text` 的 alpha。

缩放的文字：

```java
g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(s, s); g.text(font, str, 0, 0, color, false); g.pose().popMatrix();
```

## 3. 画中画（GUI 里的实体）

- `PictureInPictureRenderer<T extends PictureInPictureRenderState>` 渲染到一张离屏纹理再 blit。
- 注册：
  - NeoForge：`RegisterPictureInPictureRenderersEvent.register(Class<T>, Supplier<...>)`，再 `g.submitPictureInPictureRenderState`。
  - Fabric：`PictureInPictureRendererRegistry.register(ctx -> ...)`。
- 实体辅助：`InventoryScreen.extractEntityInInventoryFollowsMouse(GuiGraphicsExtractor g, int x0,int y0,int x1,int y1, int size, float offsetY, float mouseX, float mouseY, LivingEntity e)`。
- 自定义肖像：构建 `EntityRenderState`，再调用 `g.entity(state, scale, translation, rotation, tilt, x0,y0,x1,y1)`。Oriel 自己的做法见 `OrielEntities.registerPortraitState`（[元素](elements.md#实体渲染状态java)）。
- 一幅图是怎么画的（`PictureInPictureRenderer.prepare`）：一张 `(x1-x0)*guiScale × (y1-y0)*guiScale` 像素的纹理，正交投影，pose 先 `translate(w/2, getTranslateY(h))` 再 `scale(guiScale*state.scale(), 同值, -同值)`，所以一个单位是 `state.scale()` 个 GUI 像素，y 向下。`renderToTexture` 提交到一个 `SubmitNodeCollector`；然后这幅图被 blit（`blitTexture`），用 `GUI_TEXTURED_PREMULTIPLIED_ALPHA` 和颜色 `-1`。纹理视图是私有的，所以要带颜色 blit（染色或淡出）的渲染器需要把它放宽（Oriel 的 AT/AW）。`textureIsReadyToBlit(state)` 在纹理里仍是正确的图时跳过渲染（原版的超大物品用它）。
- 池：NeoForge 优先对一个*相等*的状态（record 的 `equals`）复用上一帧的渲染器，其次是同样纹理大小的任何一个；同一帧里两个相等的状态会让它丢掉一个渲染器，所以同一帧内各状态必须不同。Fabric 按帧内顺序复用。
- 实体：`GuiEntityRenderer.renderToTexture` 设置 `Lighting.Entry.ENTITY_IN_UI`，平移、旋转，按倾斜设置相机，再调用 `entityRenderDispatcher.submit(state, camera, 0,0,0, pose, collector)`。`EntityType.create(level, reason)` 对和平世界里的怪物返回 null（`canSpawn`）；展示用实体在检查 `type.isEnabled(level.enabledFeatures())` 之后用 `create(level, new EntitySpawnRequest(reason, true))`。
- 方块：`new BlockModelResolver(mc.getModelManager()).update(BlockModelRenderState, BlockState, BlockDisplayContext.create())`，然后 `state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0)`；模型跨度 0..1，包含方块实体的特殊模型（箱子）。`mc.getModelManager().getBlockModelSet().get(blockState)` 在资源重载时会改变对象身份。用 `BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, "oak_stairs[facing=east]", false)` 解析状态。
- 物品：`mc.getItemModelResolver().updateForTopItem(new TrackingItemStackRenderState(), stack, context, level, null, seed)`。每一层都保留该模型对 `context` 的变换（`ItemStackRenderState.LayerRenderState` 里一个私有的 `ItemTransform`，在 `submit` 里应用）：在 `ItemDisplayContext.GUI` 下，结果是围绕原点的物品栏图标，一个槽位是一个单位（Oriel 的 `<model item>` 用的就是这个）；在 `NONE` 下没有变换。`usesBlockLight()` 区分类方块模型（有光照的 `ITEMS_3D`）和扁平模型（`ITEMS_FLAT`）；`getModelIdentity()`/`isAnimated()` 说明什么时候可以保留一幅图；`getModelBoundingBox()` 给出模型的范围。原版 GUI 里大多数方块的视图是 `block/block` 的展示变换：旋转 30、225、0，缩放 0.625，在 `pose.scale(1, -1, -1)` 之后；有些模型用别的（`block/stairs`：30、135、0）。
- 量一个实体：把 `EntityRenderDispatcher.submit` 提交到一个 `SubmitNodeStorage` 的子类里。它自己的 `submit*` 方法走到 `order(0)`，但层（护甲、发光部分、旋风人的风）直接调用 `order(n).submitModel`，所以量测用的 storage 要重写 `order(int)`，返回一个 `SubmitNodeCollection`（公共构造器），它的 `submitModel` 在 `model.setupAnim(state)` 之后读取 `model.root().getExtentsForGui(pose, ...)`。

## 4. 纹理和画布

- `NativeImage(w, h, zero)`：`getPixel/setPixel(x,y,argb)`、`fillRect`、`close()`。
- `DynamicTexture(Supplier<String> label, NativeImage img)` 在创建时上传；有 `getPixels()`、`upload()` 和 `close()`。
- `TextureManager.register(Identifier, AbstractTexture)`。
- 不注册就 blit：`g.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), x0,y0,x1,y1, u0,u1,v0,v1)`。

## 5. 界面生命周期和输入

`Screen`（`net.minecraft.client.gui.screens.Screen`）：

- 生命周期：`init()`、`repositionElements()`、`resize(w,h)`、`tick()`、`added()`、`removed()`、`onClose()`。
- 行为标志：`shouldCloseOnEsc()`、`isPauseScreen()`（缺省 true）、`isInGameUi()`（缺省 false；true 表示世界之上的半透明背景）。
- 绘制：`extractBackground(g, mx, my, a)`、`extractRenderState(g, mx, my, a)`。`a` 是以 tick 为单位的帧间隔，不是 partial tick；动画时间用 `Util.getMillis()`。

输入（`net.minecraft.client.input.*` 里的 record）：

```java
void    mouseMoved(double x, double y)
boolean mouseClicked(MouseButtonEvent e, boolean doubleClick)
boolean mouseReleased(MouseButtonEvent e)
boolean mouseDragged(MouseButtonEvent e, double dx, double dy)
boolean mouseScrolled(double x, double y, double scrollX, double scrollY)
boolean keyPressed(KeyEvent e); boolean keyReleased(KeyEvent e)
boolean charTyped(CharacterEvent e)
boolean preeditUpdated(@Nullable PreeditEvent e)   // 输入法
```

- `MouseButtonEvent(double x, double y, MouseButtonInfo(int button, int modifiers))`
- `KeyEvent(int key /*SDL 扫描码*/, int keycode /*SDL 键码*/, int modifiers)`，带辅助方法 `isEscape() isConfirmation() isLeft() hasShiftDown() hasControlDown() isSelectAll() isCopy() isPaste() isCut()`。快捷键修饰键在 macOS 上是 Cmd。
- `CharacterEvent(int codepoint)`，带 `codepointAsString()`。

`InputConstants`：

- 字母：`KEY_A=4`……
- 数字：`KEY_1=30`……`KEY_0=39`
- 编辑：`KEY_RETURN=40`、`KEY_ESCAPE=41`、`KEY_BACKSPACE=42`、`KEY_TAB=43`、`KEY_SPACE=44`、`KEY_DELETE=76`
- 导航：`KEY_HOME=74`、`KEY_PAGEUP=75`、`KEY_END=77`、`KEY_PAGEDOWN=78`、`KEY_RIGHT=79`、`KEY_LEFT=80`、`KEY_DOWN=81`、`KEY_UP=82`
- 修饰键：`KEY_LCONTROL=224`、`KEY_LSHIFT=225`

**文字输入必须通过 SDL 启用**，否则 `charTyped` 永远不会触发。文本框获得或失去焦点时，调用 `Minecraft.getInstance().onTextInputFocusChange(GuiEventListener owner, boolean focused)`。`textInputManager().setTextInputArea(x0,y0,x1,y1)` 给输入法窗口定位。原版 `EditBox.setFocused` 展示了这个写法。

其他：

- 剪贴板：`minecraft.keyboardHandler.getClipboard()` / `setClipboard(String)`。
- 光标：每帧 `g.requestCursor(CursorTypes.POINTING_HAND)`。`CursorTypes` 有 `ARROW, IBEAM, CROSSHAIR, POINTING_HAND, RESIZE_NS, RESIZE_EW, RESIZE_ALL, NOT_ALLOWED`。
- 窗口：`getGuiScale()`、`getGuiScaledWidth/Height()`。
- 声音：`minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F))`。

## 6. AbstractContainerScreen 与槽位

- 字段：`imageWidth/imageHeight`（protected final，在构造器里设置）、`leftPos/topPos`（在 `init()` 里居中）、`menu`、`hoveredSlot`。
- 渲染流程：`extractContents` 把 pose 平移 `(leftPos, topPos)`，然后调用 `extractLabels` 和 `extractSlots` → `extractSlot(g, slot, mx, my)`（在 `slot.x, slot.y` 处画物品和装饰）。之后是 `extractCarriedItem`，它开启一个新的层。
- 命中测试走私有的 `getHoveredSlot` → `isHovering(slot, mx, my)`。它使用相对于 `leftPos/topPos` 的 `slot.x/slot.y`（16×16）。
- `Slot.x` 和 `Slot.y` 是 `public final int`。要把槽位放到由布局算出的位置，需要让它们可变：
  - NeoForge AT：`public-f net.minecraft.world.inventory.Slot x`
  - Fabric：`mutable field net/minecraft/world/inventory/Slot x I`

  然后要么设 `leftPos = topPos = 0` 并使用绝对位置，要么保持槽位相对位置。重写 `hasClickedOutside`。
- NeoForge 增加了 getter `getHoveredSlot() getLeftPos() getTopPos()` 和 `renderSlotContents` 钩子。
- 注册界面：
  - NeoForge：`RegisterMenuScreensEvent.register(menuType, Screen::new)`。
  - Fabric：通过访问扩展调用 `MenuScreens.register(menuType, Screen::new)`。

## 7. HUD 层

做法是一个公共的 `static void extract(GuiGraphicsExtractor g, DeltaTracker d)`，在 `mc.player == null || mc.gui.hud.isHidden()` 时直接返回。由加载器注册它：

- NeoForge：`RegisterGuiLayersEvent`，`e.registerAbove(VanillaGuiLayers.TITLE, id, Hud::extract)`。
- Fabric：`HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE, id, Hud::extract)`。

## 8. 加载器钩子

| 需要 | NeoForge | Fabric |
|---|---|---|
| 自定义 `GuiElementRenderState` | `g.submitGuiElementRenderState(s)` | `g.guiRenderState.addGuiElement(s)`（AW） |
| 管线 | `RegisterRenderPipelinesEvent` | `RenderPipelines.register`（AW） |
| PiP 渲染器 | `RegisterPictureInPictureRenderersEvent` | `PictureInPictureRendererRegistry` |
| HUD | `RegisterGuiLayersEvent` | `HudElementRegistry` |
| 菜单界面 | `RegisterMenuScreensEvent` | `MenuScreens.register`（AW） |
| 载荷 | `RegisterPayloadHandlersEvent` / `RegisterClientPayloadHandlersEvent` | `PayloadTypeRegistry` + `ClientPlayNetworking`/`ServerPlayNetworking` |
