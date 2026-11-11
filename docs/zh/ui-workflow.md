# 从需求、H5 原型到游戏界面

## 产品边界与制作约定

- Oriel 提供界面引擎、组件和工具；商店、任务和物品编辑等业务由下游实现。
- 默认不替换原版界面；玩家环境快捷键默认未绑定。个人 dev-sources 配置不随发行包发送。
- 先从官方编号 UI 选参考，按设计规范使用字体、主题和动效；旧版示例可检索实现，但不当作当前作品展示。
- 只使用公开 API，内部类不作为下游合同。界面和工具清单中的每项都应有实际内容。
- 核心改动提升 minor；版本规则见分发指南，历史变化只写 CHANGELOG。
- 解析、预览、游戏、联网和 OS 输入法分别验收，不能用一种结果代替另一种。

协作执行按[编排规范](mcp.md)。MCP截图验收超过12图时必须按pagination.nextOffset逐页返回并交用户查看，完整覆盖后才能逐字代记批准；工具/JSONL/why-tree/黄金审批的字段见[增量工具合同](maintainers/mcp-tools.md)。黄金图审批与本页面交付流程分开，不能复用批准。
排查绘制顺序与裁剪时可用只读 `oriel_paint`；参数及预览/GPU 边界见 [绘制检查](maintainers/rendering.md)。它不替代页面检查、用户验收或接入批准。

下游首次接入可先用 `oriel_scaffold` 只读输出清单；可选 prepare/check → 截图验收与用户聊天批准 → apply，仅新建示例 HTML/Java。参数与边界见 [MCP 手册](mcp.md#下游骨架oriel_scaffold)、[接入指南](integration.md#一步接入检测并生成最小骨架)。

[入口](index.md) · [能力](capabilities.md) · [参数](css-guide.md) · [验收命令](validation.md) · **[设计规范与最小骨架](design.md)**

## 两条路：快速迭代（默认）和正式交付

**开始制作或修改动画前，先读 [粒子与动画性能规范](performance.md)**：确定原生元素/共享画布的分工，禁止密集粒子逐节点和脚本逐扫描线绘制，预先确定最重动作与对照验证方式。

**快速迭代（边改边看，默认）。** 把页面写进用户游戏的 `config/oriel/my-pages/<项目名>/`，用户的游戏开着就热重载：保存页面读过的任何文件（页面、样式表、脚本、图片，或它请求过但当时还不存在的文件）都会重载，**发布版 jar 也一样**，不只是开发环境（见 [页面接口](pages.md)）。流程：`oriel_check`（警告清零）→ 保存文件 → 用 `oriel_snapshot` 截 640×360 和 427×240 发到对话里 → 用户在游戏里 `/oriel open oriel_mine:<项目名>/index.html` 或模板中心「我的页面」查看、提意见 → 再改。不需要网页验收和批准，也不要自己再启动一个游戏；等于把游戏本体当浏览器用。热更新怎么配、怎么确认、什么会热，见 [热更新](hot-reload.md)。

**正式交付（要进模组或本仓库时）。** 走下面的 MCP 主工作流：网页或截图验收、用户批准固定版本、`oriel_integrate`、`oriel_game_preview`。

## MCP 主工作流：验收，再接入游戏（正式交付）

Agent 从 [调用提示词](mcp.md) 开始；工具连接和完整参数见 [MCP 手册](mcp.md)。HTML/CSS 仍直接由引擎加载，接入工具整理资源与宿主入口，不把任意浏览器应用自动翻译成 Java。

1. 调用 oriel_rules，取得目标版本能力、禁止项和替代写法。profile 缺省为当前工作树的核心版本，也接受当前完整版；版本与脚手架范围从 `gradle.properties` 读取，升级后旧规则哈希失效，详见 [MCP 手册](mcp.md)。用 oriel_project 登记独立页面包、数据合同、模拟数据及交互场景。
2. 制作共享 HTML/CSS/JS，通过 oriel_web_preview 给用户可点击的网页。浏览器桥接和 mock 与游戏资产分开。
3. 每轮调用 oriel_check 和 oriel_snapshot，查看结构诊断、640×360/427×240 引擎截图及场景结果；失败先修，不把浏览器成功当游戏兼容。
4. 检查通过后调用 oriel_review.prepare，生成不可变版本的验收页。用户本人批准或填写意见；Agent 不代点批准。
5. 用 oriel_status / oriel_review.status 核对批准是否仍对应当前内容。调用 oriel_integrate.plan 看文件清单，再 apply 安装；冲突、过期或无批准时工具阻断。
6. mod-project 目标在下游游戏中打开验证（不调用库的游戏预览工具）；其余目标调用 oriel_game_preview.start/status，打开已接入页面并获取游戏截图和诊断。仅接入成功不代表游戏验证通过，真实业务另行验收。

源文件、主题资源、fixture、适配器、规则或目标变化后重新检查和验收。旧固定版本保留用于追踪，不悄悄更新用户正在看的页面。下文的页面合同、行为路线、资源规则与四层验收仍然适用。

**正式交付流程里，用户验收（网页点击，或截图验收里用户在聊天中的明确同意）前，不启动任何游戏测试，包括工具自身探针、冒烟测试或另一张示例页。** 这一阶段只执行编译、无头引擎和网页检查，不通过 Gradle/CLI 绕过顺序。

复用同一份页面结构、样式和业务状态合同。**HTML/CSS 直接作为资源进入游戏，由 Oriel 解析并渲染，不经过 H5 → Java 自动转译。** 浏览器用于设计和模拟交互；引擎预览负责真实子集；游戏负责原生资源、输入和联网验收。

## 1. 先写页面合同

浏览器约束仍以 [CSS 选择器合同](css-guide.md#选择器实现与诊断开关) 为准。引擎维护者的实验匹配器开关不作为页面依赖，也不替代网页批准或游戏验收。

每个新页面先列以下内容，放业务说明或使用文档。不要先做依赖浏览器专有 API 的成品，再研究迁移。

| 内容 | 物品组件编辑器示例 |
|---|---|
| 界面与入口 | 服务端页面；管理员命令；URL mymod:ui/editor.html |
| 主操作 | 改工作副本 → 保存 → 等服务端接受/拒绝 |
| 状态 | loading / ready / dirty / saving / saved / rejected / unavailable |
| 数据 | 物品快照、可编辑字段、只读组件树、附魔/属性选项；逐字段类型/空值/范围 |
| 动作 | select、edit、reset、save、cancel；区分本地与服务端 |
| 回执 | 成功给新快照；拒绝给原因；切槽/物品变化拒绝 |
| 小尺寸 | 640×360 设计；427×240 保留保存/返回和主要输入，详情可折叠 |
| 原生内容 | item/model 预览；只有真实拖放才需要 slot |
| 文案 | zh_cn/en_us 键、空状态、错误、长文本与中文输入 |

字段合同写进你自己的文档，不要猜字段。新增功能先定义业务合同，不把提议写成库已支持的能力。

## 2. 选择行为路线

| 路线 | 页面 | 浏览器 | 引擎/游戏 |
|---|---|---|---|
| kit / Java 控制器 | HTML/CSS + Java attach | mock 相同状态转换 | PageControllers 按精确 URL 注册；attach 接 Shell.setup |
| 脚本页 | HTML/CSS + Rhino 支持的 JS | 同一脚本+薄桥接，只用已列 DOM/API | oriel.data/on/send + 宿主 handler；不用重写全部 JS |

已有 kit 页保持 Java 路线。第三方轻量表单可用脚本。不要让 Java 和 JS 同时拥有同一按钮/输入，否则重复提交或状态覆盖。只加载 ore.css 的脚本页，Shell 的 data-go/toast/粒子不会自动接好；自己实现行为或显式注册控制器。

## 3. 创建文件和资源

在本库加 kit 页（仓库根执行）：

```bash
node tools/new-page.mjs bestiary "怪物图鉴"
```

生成 HTML、Java Preset、测试并登记。它不是下游工程生成器，不自动生成网络/菜单。下游文件：

```text
src/main/resources/assets/mymod/ui/editor.html
src/main/resources/assets/mymod/ui/editor.css
src/main/resources/assets/mymod/ui/editor.js
src/main/resources/assets/mymod/textures/ui/icon.png
src/main/resources/assets/mymod/lang/zh_cn.json
src/main/resources/assets/mymod/lang/en_us.json
```

游戏 URL `mymod:ui/editor.html`；editor.css/js 相对当前目录；图片可 `mymod:textures/ui/icon.png`；主题 `oriel:ui/ore.css`。保持大小写和主题纹理引用，不写本机盘符进发布页。先复用现有资源，新增素材记录来源与许可；SVG 转 PNG，字体用 MC font JSON。临时原型、mock、截图、日志放工作区 daily。

## 4. 浏览器原型如何便于移植

1. 用 640×360、427×240 **CSS 视口**，保持 DOM 层级、类、id、token；1280×720 截图像素不等于 GUI 视口。
2. 只用 CSS 表已列属性和值；sticky、嵌套、@layer、SVG、3D transform、完整 Canvas 不进入最终页。考虑字体/UA 差异。
3. 开发服务器将资源 ID 映射到 assets 目录。浏览器不认识 oriel: 协议；用预览入口或受控路径改写，不把最终游戏 URL 改成本机路径。
4. MC 元素在浏览器换同尺寸替身并标明占位。替身只证明空间安排；游戏恢复原生元素，不能把图片当真实槽位。
5. mock 只模拟已定义消息，不编造 oriel.player/oriel.inventory API。mock 与真实宿主共用请求/回执结构。
6. 覆盖初始、空、长文字、悬停、按下、焦点、展开、禁用、保存中、成功、拒绝、重载。动效看起点/中间/终点。

浏览器入口单独加载 mock，再加载共享脚本。**mock 不打包给游戏。** 可定义自己的适配层（下表不是 Oriel 新 API）：

| 业务桥接 | 浏览器 | Oriel |
|---|---|---|
| readSnapshot() | fixture | oriel.data / Java 模型 |
| subscribeData(fn) | mock 监听 | oriel.on('data',fn) / Java data 事件 |
| sendAction(name,payload) | 模拟延迟、拒绝与回执 | oriel.send / Host.send |
| closePage() | 切原型状态 | oriel.close / Host.close |
| translate(key,args) | 相同词典 | oriel.t / Host.translate |

浏览器自身不执行 v-if/v-for；Oriel 模板也不是完整 Vue。浏览器 mock 按状态渲染同一结构，或两端用普通 DOM 操作；不能把安装 Vue 当成游戏支持 Vue 的证据。

## 5. 可复用的最小脚本交互页

下面只做本地名称预览，不声称保存到服务器；使用 Ore 外观但不依赖 Shell。临时预览时按 [外部资源根步骤](validation.md)把示例放在 daily 内 assets/mymod/ui/example.html，并让同一根下含 Oriel 主题资源；不能只丢一个 HTML 到任意目录。

```html
<!doctype html>
<html><head><meta charset="utf-8"><title>名称预览</title>
<link rel="stylesheet" href="oriel:ui/ore.css">
<style>
 body { padding:8px; }
 .demo-form { display:flex;flex-direction:column;gap:8px;max-width:300px; }
 .demo-form input { width:100%; }
</style></head><body>
<main class="demo-form">
 <label for="name">物品名称</label>
 <input class="ore-field" id="name" maxlength="50" placeholder="输入名称">
 <p id="preview">尚未输入</p>
 <button type="button" class="ore-btn primary" id="apply">预览</button>
</main>
<script>
 const nameField = document.getElementById('name');
 const preview = document.getElementById('preview');
 document.getElementById('apply').addEventListener('click', function () {
   preview.textContent = nameField.value || '尚未输入';
 });
</script></body></html>
```

接真实保存时发 `oriel.send('save',{name:nameField.value})`，字段必须符合宿主合同。按钮进入 saving，只有服务端 result.status=saved 才显示成功。send 返回 true 不代表已保存。示例文案换翻译键后再发布。

## 6. Java 控制器最小接线

```java
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Shell;

public final class ExampleController implements PageControllers.Controller {
    private Document document;
    private int clicks;
    public static void register() {
        PageControllers.register("mymod:ui/example.html", ExampleController::new);
    }
    @Override public void attach(Document doc) {
        document = doc;
        Shell.setup(doc);
        Element button = doc.getElementById("apply");
        Shell.activate(button, () -> { clicks++; render(); });
        render();
    }
    private void render() {
        document.getElementById("preview").setTextContent(Integer.toString(clicks));
    }
    @Override public Object saveState() { return clicks; }
    @Override public void restoreState(Object state) {
        if (state instanceof Integer n) { clicks = n; render(); }
    }
}
```

页面要有 apply/preview 两个 id，删除上例 script 避免重复监听。打开前调用 register，随后 OrielScreens.open。按**精确 URL**匹配，复制到另一 namespace/文件路径不会自动匹配旧注册。stock 预览器只登记本库 kit 控制器；外部控制器须在自己的预览启动端装入并注册，不是复制 HTML 就能运行 Java。

## 7. 四层验收

| 层 | 验什么 | 不能替代什么 |
|---|---|---|
| 浏览器 | 设计、状态、方向、时长、可用性 | Oriel 子集与 MC 字体 |
| h5ref | 同一静态页像素差、资源、诊断 | 动态交互；Chrome 禁 JS，动画固定 0ms，引擎首帧 |
| Oriel 预览/checkUi/actions | 真实解析/布局/控制器、输入、逐帧状态 | MC 3D、IME、容器/网络 |
| 游戏 | 字体/中文/IME、缩放、3D/裁剪、tooltip、Esc、HUD 输入、联机保存 | 多目标发行仍须验证受影响 MC/加载器 |

命令、参数与退出码见 [验收工具](validation.md)。第一版骨架即 checkUi，每新增一类效果就预览；不要浏览器全部完成后才第一次在 Oriel 打开。

## 8. 完成标准

资源页通过 `/oriel open mymod:ui/editor.html` 或 API 打开；服务端页面由服务端 open；槽位注册菜单绑定。两端缺资源/版本不一致、无权限、手持物变化要有明确结果，不能只让按钮变绿。

- 427×240 必需操作可达，长中文/英文不出框；焦点、Tab/Enter/Space/Esc 正确，减少动效仍可用。
- 按页面承诺验证主题/风格；tooltip 不被遮挡，装饰不吃鼠标。
- 覆盖 loading/empty/dirty/saving/saved/rejected；取消不改真实物品，成功再读取；重载不重复监听/保存。
- mock 不发布；页面、资源、控制器和消息合同属于相同版本。
- 能力变化同次同步 [维护映射](maintainers/maintenance.md)，报告实际运行的验证。

## 给下一位 agent 的任务模板

排查选择器时可调用 `oriel_inspect(page, selector, element)`，或预览器 `--why <selector> <element-query>`。参数、默认帧数、JSONL 字段和限制见 [选择器诊断](maintainers/styles.md)；查询不创建批准记录，也不启动游戏。

> 先读 docs/zh/index.md、capabilities.md、ui-workflow.md，再查 CSS/元素/组件/脚本表。页面类型 ___，URL ___，业务字段合同 ___，MC/加载器 ___。只用已列能力，缺项明确边界，不猜 API。浏览器迭代，按四层验收，产物放 daily。能力变更同次更新 maintainers/maintenance.md 指向的表。

属性解释：预览器新增 `--why-property <property> <element-query>`；MCP `oriel_inspect` 的 selector/property 二选一，page/element 必填，frames 默认 1（1–600）。仅加载无头预览，不批准、不接入或启动游戏。字段、伪元素、实验开关和限制见 [实验层叠与属性解释](maintainers/styles.md)。

## 按参考制作页面

> **这是开发者用的流程，不是做页面的默认流程。** 普通做页面（自己设计界面）看 [做页面前先读](design.md) 和 [制作流程](ui-workflow.md)。只有当目标是"复刻一个已有的东西"——一段视频里的界面动画、一个网页、原版或别的模组的界面——才使用本节。

**复刻的对象是原版界面的视觉、动效与交互反馈。能解析、能显示、通过检查，都不等于复刻完成。**

### 1. 依据与执行顺序

1. 先读 Oriel 手册（[首页](index.md)、[做页面前先读](design.md)、[能力](capabilities.md)、[制作流程](ui-workflow.md)）以及这次涉及的 CSS、脚本、元素合同；不翻引擎源码猜可用能力。
2. **读原动画的源码**和它引用的组件：布局、字体绘制、键帽、扫光、粒子、缓动、声音。只看封面猜效果不行。
3. **同时看原动画**，保存开始、入场落定、停留、交互中、完成闪光、退出中、结束的参考帧。源码定参数，视频查合成结果。
4. **动手前先填写下方的参数对照表。** 先列清全部视觉层和时间轴，再写页面。查不清的参数标"待核对"，不能用个人偏好补齐后声称一致。
5. **同一时刻只推进一个模板。** 这一个没通过用户在游戏里的验收，不展开下一个。总清单是路线图，不是批量制作授权。

原版效果和库默认样式不同时，**以原版效果为视觉目标**，库文档决定可用的实现手段；不要用默认按钮、通知、时长去覆盖原版特征。需要调整或无法复现的部分，要具体列出差异。

### 2. 必须一致的项目

| 项目 | 必须核对并保持的内容 | 不合格示例 |
|---|---|---|
| 构图 | 锚点、相对屏幕位置、整体比例、排列、层级、块间距、留白、遮挡 | 擅自居中、放大面板、换背景 |
| 几何 | 宽高、切角位置与大小、轮廓、边线粗细、内外框、厚度、底部投影 | 用普通方按钮代替双切角立体键帽 |
| 字体 | 字形、字面大小、字重、字距、行高、基线；见第 5 节的字体规则 | 只因用了像素字体就算一致 |
| 色彩材质 | 源码色值、各层透明度、渐变方向、实色与发光的区别 | 把硬边光带改成模糊渐变，或所有面都发光 |
| 图标与物品 | 原物品、方向、尺寸、外框、遮挡、投影和落位 | 换成别的物品；用原生 `<item>` 就默认一致 |
| 动画路径 | 从哪里进入、方向、距离、回弹幅度、停留位置、退出方向 | 突然显隐；只有入场没有退出 |
| 时间轴 | 各元素启动、持续、停顿、先后、重叠、完成后余韵 | 所有元素同时进入；按满立刻消失 |
| 缓动 | 进入/退出曲线、回弹速度变化 | 用同一个 ease-out 代替所有曲线 |
| 扫光 | 光带数量、主副宽度、间隔、倾斜角、颜色、不透明度、裁剪边界、起止、次数 | 单条竖向渐变代替斜向双光带 |
| 粒子与闪光 | 触发点、数量、大小、颜色、轨迹、重力、寿命、扩张衰减 | 画一团泛光代替十字闪光 |
| 按键与反馈 | 待机、按下、持续按住、松开取消、按满、完成反馈、退出、重播 | 按下与松开无区别；完成后立刻删节点 |
| 音效 | 原版存在的声音、触发时刻、音量音高；用合法可用的游戏声音等价实现（`oriel.playSound`） | 只复制画面，静默漏掉默认开启的声音 |

每一项都要有**证据**，不能写"效果差不多"。色值可直接比；形状、字体、图标要看同尺寸叠图或并排图。

### 3. 尺寸与时间的换算

- 记录原画布尺寸和帧率；默认在 640×360 GUI 像素下核对，并检查 427×240。例如原版 1920×1080，基准长度换算为 1/3，不能把屏幕像素当 GUI 像素。
- 先统一缩放，再按像素栅格统一取整；不能只放大字、缩小图标、压缩间距去掩盖布局错误。小尺寸要保留原有视觉层级，必要调整单列记录。
- 时间换算成毫秒：帧号 ÷ 原帧率 × 1000。记录显示起止、入场结束、扫光起止、按住时长、完成闪光、退场结束，不只记总时长。
- 检查容差：非文字关键边界的位置/尺寸误差不超过 1 GUI px，事件起止误差不超过 1 个原始帧。这是检查阈值，不能借容差累积出不同的构图，也不能代替用户对整体观感的判断。字形、光带数量、颜色、事件顺序、缺失效果**不适用容差**：不同就是差异。

### 4. 允许和禁止的改动

**游戏化的改动是允许的**（要写进对照表）：
- 背景透明，或沿用 Minecraft 宿主页面/世界；去掉视频场景、镜头、装饰性黑边，不自己画替代场景。UI 自身的面板、阴影和局部特效保留。
- 游戏里动画必须流畅：视频为低帧率做的位移/透明度/进度量化，不机械照搬成卡顿；保留时间轴、曲线趋势、位移和效果形态，改用连续插值。原版有意设计的停顿要保留。
- 长按等操作由**真实输入**驱动：视频里模拟的自动按住换成玩家的真实按键，完整保留按下、填充、松开、完成、退出反馈。
- 原型放进"模板中心 → 我的页面"，每个模板有自己的页面、标题和真实封面（写法见 [做页面前先读](design.md)）；不新增 Demo 登记，不做大型测试按钮或多模板工作台。

**可以换实现方式，但不能换表现**：SVG 轮廓可换 CSS polygon 或纹理；视频帧驱动可换引擎动画；Canvas 文字可换字体资源或文字绘制。替换后仍要满足第 2 节。不支持的能力先列"原表现 → 替代办法 → 可见差异 → 验证方法"；存在无法消除的差异，不能自行当作通过，用户明确接受才算例外。

### 5. 字体规则（复刻用，与普通做页面不同）

- **标题、选中项、正文、按钮、数字都用原生文本节点和 `minecraft:default`，常规字重。** 不要把原字体导出成贴图，也不要用 Fusion Pixel 贴图：低分辨率栅格化再放大，笔画会粘连成块（实践中被用户否决过）。
- 不要人工加粗、重描边、重阴影来"模仿"原版粗字；窄卡片里的文字按内宽分行，**不靠溢出、截断或整体缩小掩盖排版错误**。
- 两种 GUI 尺寸都要检查文字的**真实绘制边界**，不能只看元素框。
- 和 [做页面前先读](design.md) 的区别：那里是"自己设计的页面，中文用 `oriel:pixel_12`"；复刻时**以原版字体为准**。像素字只在原生字号和 2 倍清晰，所以字号取 8、16、24。

### 6. 动画与输入的实现要求

- 优先用库已记录的原生动画与 `transform` / `opacity`；单纯播放不要每帧用 JS 改一批 `left/top/width/height` 再反复读布局。
- 不在每帧反复查询字体、主题、减少动效设置；没有活动动画时不留空转的帧循环。
- 粒子参数预先确定；结束后不再修改隐藏粒子。粒子外溢、扫光裁剪、键帽填充裁剪分别处理，不用一个裁剪层误切所有效果。
- 按键自动重复不得反复重启长按；松开取消未完成操作；失焦取消长按；重播清理定时器和旧动画；离页后不残留任务。
- 完成流程要完整：达到阈值 → 完成配色/闪光 → 短暂停留 → 可见退场 → 隐藏/销毁。退场开始前不能先把父节点隐藏。
- 减少动效模式保留内容、进度和操作结果，关闭强闪与装饰粒子，不能变成空白页。

### 7. 制作时就做的检查

1. **结构阶段**：原版与当前同尺寸并排，核对构图、形状、字体、图标；这些不对就不继续堆特效。
2. **动效阶段**：原版和当前逐阶段录像对照，至少看入场中、落定、每次扫光中、离场中和末帧。静态截图不能证明动画流畅。
3. **交互阶段**：真实按住到一半、提前松开、重新按住、按满、完成闪光、退场、重播、失焦、Esc。必须走真实游戏键盘路径，调内部函数不能替代。
4. **引擎阶段**：严格检查 0 警告 0 错误，640×360 与 427×240；日志里有脚本异常时，即使截图命令成功也算失败。
5. **同步阶段**：检查通过后同步到游戏的 `my-pages`（边改边看，见 [热更新](hot-reload.md)），校对源文件和运行文件哈希，确认用户打开的确是这个版本。没同步就不能让用户去验证新版。
6. **游戏阶段**：用用户现有的客户端，不擅自另开或重启；确认透明背景、字体、物品、裁剪、输入和热刷新。卡顿时对比同场景有/无 UI 的帧时间，记录条件，不能以引擎预览代替。
7. **入口清理**：我的页面卡片正常、封面存在；不该有的 Demo 卡片要清理到运行结果，不只删源码。CSS 隐藏、临时过滤只能记为临时处理。
8. **验收**：列出已完成、待实测、可见差异，展示当前真实版本；用户对游戏效果确认后才记完成，再推进下一个。

### 8. 状态怎么记

用 **未开始 / 制作中 / 已检查 / 已同步 / 待游戏验收 / 用户验收通过**。代码改了 ≠ 已同步；已同步 ≠ 用户实际看到；实际看到 ≠ 通过验收。发现差异立刻退回对应阶段，不能用"原型""等价实现"免除一致性要求。收工更新总清单：检查命令、证据路径、同步版本、剩余差异；有提交才写提交号。

### 9. 每个模板必填的对照表

```text
模板 / 候选组：
原动画入口与引用组件：
原尺寸 / 帧率 / 基准截图与录像：
游戏 URL / 实际运行目录 / 当前文件哈希：

项目 | 原版参数/表现 | 游戏实现/参数 | 对照证据 | 状态/差异
位置与尺寸 | | | |
字体与文字效果 | | | |
几何、材质、图标 | | | |
入场/停留/退出 | | | |
扫光/粒子/闪光 | | | |
输入/取消/完成 | | | |
音效 | | | |
小尺寸/减少动效 | | | |

已允许的游戏化改动：
仍存在的可见差异：
引擎检查：
游戏录像及真实输入检查：
源文件/运行文件一致性：
用户验收原话：未验收时填写"未验收"。
```

### 10. 和官方两条路的关系

复刻用的是[制作流程](ui-workflow.md)里的**快速迭代**：页面写进游戏的 `my-pages`，热更新，截图发到对话，用户在游戏里看。复刻页要进模组或仓库时，再走**正式交付**（验收、批准、接入）。多个模板并行的总清单（路线图）留在你自己的工作区，不进库。
