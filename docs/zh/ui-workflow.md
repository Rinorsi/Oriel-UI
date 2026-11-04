# 从需求、H5 原型到游戏界面

[入口](index.md) · [能力](capabilities.md) · [参数](css-guide.md) · [验收命令](validation.md)

复用同一份页面结构、样式和业务状态合同。**HTML/CSS 直接作为资源进入游戏，由 Oriel 解析并渲染，不经过 H5 → Java 自动转译。** 浏览器用于设计和模拟交互；引擎预览负责真实子集；游戏负责原生资源、输入和联网验收。

## 1. 先写页面合同

每个新页面先列以下内容，放业务说明或使用文档。不要先做依赖浏览器专有 API 的成品，再研究迁移。

| 内容 | 物品组件编辑器示例 |
|---|---|
| 界面与入口 | 服务端页面；管理员命令；URL mymod:ui/item_editor.html |
| 主操作 | 改工作副本 → 保存 → 等服务端接受/拒绝 |
| 状态 | loading / ready / dirty / saving / saved / rejected / unavailable |
| 数据 | 物品快照、可编辑字段、只读组件树、附魔/属性选项；逐字段类型/空值/范围 |
| 动作 | select、edit、reset、save、cancel；区分本地与服务端 |
| 回执 | 成功给新快照；拒绝给原因；切槽/物品变化拒绝 |
| 小尺寸 | 640×360 设计；427×240 保留保存/返回和主要输入，详情可折叠 |
| 原生内容 | item/model 预览；只有真实拖放才需要 slot |
| 文案 | zh_cn/en_us 键、空状态、错误、长文本与中文输入 |

现有编辑器合同见 [item-editor](item-editor.md)，不要猜字段。新增功能先定义业务合同，不把提议写成库已支持的能力。

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

游戏 URL `mymod:ui/editor.html`；editor.css/js 相对当前目录；图片可 `mymod:textures/ui/icon.png`；主题 `oriel:ui/ore.css`。保持大小写和主题纹理引用，不写本机盘符进发布页。新视觉素材默认 Mivo，先复用现有资源；SVG 转 PNG，字体用 MC font JSON。临时原型、mock、截图、日志放工作区 daily。

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
- 能力变化同次同步 [维护映射](maintenance.md)，报告实际运行的验证。

## 给下一位 agent 的任务模板

> 先读 docs/zh/index.md、capabilities.md、ui-workflow.md，再查 CSS/元素/组件/脚本表。页面类型 ___，URL ___，业务字段合同 ___，MC/加载器 ___。只用已列能力，缺项明确边界，不猜 API。浏览器迭代，按四层验收，产物放 daily。能力变更同次更新 maintenance.md 指向的表。
