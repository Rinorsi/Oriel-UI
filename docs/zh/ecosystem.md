# 前端库的受限兼容

[入口](index.md) · [脚本合同](scripting.md) · [性能](performance.md) · [安全](security.md)

适用：0.11.0 的 Rhino 运行时；本增量仍为 WIP，**默认保留 old 绑定**。宿主 JVM 显式加 -Doriel.ecosystem=new 才启用下述 DOM 新入口；old 关闭，未知值报错。游戏要在启动参数里设置并重启；预览器使用 Java 命令时将参数放在主类前。性能与完整黄金门禁未通过前不切默认。这里列的是固定构建的窄场景；不代表任意 npm 包、组件、插件或浏览器行为。Java 绑定更新需要更新 jar / 重启客户端，页面热更新不能替换引擎。游戏 GPU、IME 与六目标客户端仍须分别验证。

## 引用与许可

游戏只读取资源包，不访问 CDN。把已核对版本和许可的 UMD/IIFE 文件放到自己的资源命名空间，再用普通 `<script src="../vendor/react.js"></script>`；ESM 必须预打包成 classic script。不要把下载地址直接写进游戏页面。

`tools/ecosystem/libraries.json` 固定实验构建的 URL、字节数与 SHA-256。准备脚本将示例与库放到仓库外：

```text
node tools/ecosystem/prepare.mjs --out <外部目录>
node tools/ecosystem/prepare.mjs --out <外部目录> --cache <已有库目录>
node tools/ecosystem/bundle-compute.mjs --modules <外部node_modules> --esbuild <esbuild/lib/main.js> --out <准备结果中的vendor目录>
node tools/ecosystem/prepare-marked.mjs --libs <准备结果中的vendor目录> --esbuild <esbuild/lib/main.js>
```

两种方式均核对内容哈希；`--cache` 不联网。输出 `assets/ecosystem/ui/<react|preact|gsap|anime|confetti>/index.html` 和共用的 `vendor/`。用预览器检查这些输出页；游戏接入遵循 [制作流程](ui-workflow.md)。源码中的示例引用旁边的 vendor，准备前缺第三方脚本是预期错误。

第三方包没有加入本仓库或发行 jar。React、Preact、anime、Tween、dayjs、Mithril 为 MIT；htm 为 Apache-2.0；canvas-confetti 为 ISC。GSAP **3.12.5 是自定义许可**，不因其他版本许可变化而改变；使用和再分发前阅读该构建的许可。准备工具下载固定实验构建，不替使用者授予再分发权限；发布自己的模组时附相应许可和版权声明。

计算模块使用已有外部工具链打包：nanostores 0.11.3、d3-scale 4.0.2、d3-shape 3.2.0、esbuild；工具核对前三者版本并输出构建哈希回执。缺少依赖时在外部工作目录准备，不能安装到本仓库。第三条命令生成 nano-compute.js / d3-compute.js，第四条核对固定 marked 原包哈希，显式降级到 es2015 并关闭 arrow 语法，生成保留 UMD 全局的 marked-compat.js 与包含 esbuild 版本、输入输出哈希的回执，供完整库回归使用；传递依赖与 esbuild 的锁文件由该外部工具工程维护。

## 固定构建与场景

| 构建 | 可复用范围 | 限制 |
|---|---|---|
| React 18.3.1 + ReactDOM production UMD | createRoot / flushSync、useState、click、keyed 列表、unmount | 未认证并发、Suspense、任意第三方组件或 iframe |
| Preact 10.25.4 + htm 3.1.1 UMD | HTML 渲染、受控 input、input 事件、列表 | 没有 SVG；不包含 preact/hooks 包 |
| anime.js 3.2.2 | DOM translateX / opacity、timeline、seek / pause / remove | 未认证 SVG、路径或全部 CSS 属性 |
| GSAP 3.12.5 | 2D transform / opacity、timeline、stagger、ease、数值对象、kill | 不包含 ScrollTrigger、SVG、3D 或其他插件 |
| canvas-confetti 1.9.3 | 下述桥接的 flat 方粒子 | 不支持 circle / star / bitmap / Path2D、worker、自适应画布 |
| Tween 23.1.3、dayjs 1.11.13、Mithril 2.2.2 | 显式时钟数值补间、日期运算、m.render + click | 不认证自动 redraw、路由、网络或日期时区插件 |
| marked 12.0.2，经 prepare-marked.mjs 显式语法降级 | 标题、段落、加粗等受控 Markdown 生成 HTML | 原始 UMD 仍解析失败；不是 HTML 消毒器，不给不可信 HTML 额外权限 |
| nanostores atom 0.11.3、d3-scale 4.0.2 / d3-shape 3.2.0 | 只预打包纯计算模块 | atom set/get、线性比例尺与 line 路径字符串；不扩大到 D3 全包或 SVG 绘制 |

回归入口：`EcosystemBindingsTest` / `EcosystemShimTest` 始终运行；`EcosystemLibrariesTest` 需要 `ORIEL_ECOSYSTEM_LIBS` 指向准备工具打印的 vendor 目录。没设置时 JUnit 明确跳过，不能把跳过算作库兼容验证。脚本异常与断言同样检查，预览进程退出码 0 不足以证明脚本成功。

## 新增 DOM 探测合同

| 名称 | 行为与用途 |
|---|---|
| `document.createElementNS(namespace, name)` | Preact 的 HTML 建树路径；namespace 接受 XHTML URI、空字符串、null / undefined；返回普通 HTML 元素。拒绝其他命名空间和含冒号的名称，抛带 `NotSupportedError` 说明的 `TypeError` 并提示 HTML/Canvas 替代。不是通用 XML API。 |
| `element.namespaceURI` | 只读 `http://www.w3.org/1999/xhtml`，与本引擎的 HTML 元素模型一致。 |
| `HTMLInputElement` | 用于 anime 类型分支，input（含 clone / createElement / createElementNS）正确匹配，并继承 HTMLElement / Element / Node。类型由标签决定，不区分 input.type。 |
| `HTMLIFrameElement` | ReactDOM 的安全类型探测；本引擎没有 iframe 实例，普通元素不匹配。 |
| `SVGElement` | anime 的类型探测；本引擎没有 SVG 实例，不把 HTML 假装成 SVG。 |
| `NodeList` / `HTMLCollection` | 只提供探测构造器；现有集合继续为 Array 快照，不匹配这两个类型。anime 通过其 Array 分支处理。 |

上述构造器不能 `new`，会抛 `TypeError: Illegal constructor`；没有引入 Java 反射、文件、网络或新的预算。`navigator`、`matchMedia`、ResizeObserver、IntersectionObserver 未因生态实验加入：尚无仅补这些 API 即可运行目标库的证据，不能用空实现伪装支持。

## 可选官方桥接

只有需要方粒子时才引用，普通页面无需加载：

```html
<script src="oriel:ui/oriel-shims.js"></script>
<script src="../vendor/confetti.js"></script>
<script>
var fire = OrielShims.squareConfetti(document.querySelector('canvas'), confetti);
fire({particleCount:24, ticks:60, origin:{x:0.5,y:0.8}});
// Before replacing the effect: fire.reset(); Before permanently removing it: fire.dispose();
</script>
```

- `OrielShims.squareConfetti(canvas, library)` 返回可调用实例，默认补 `flat:true, shapes:['square']`；显式传 false 的 flat、其他 shapes、worker / resize 会报错，不悄悄替换请求的形状。创建时固定 worker / resize 为 false。页面 unload 自动 reset；主动 dispose 幂等并移除监听器，之后调用 fire 报错。减少动效和页面级粒子预算由作者控制，桥接不放宽沙箱限额。
- `OrielShims.quadContext(canvas)` 只为该画布上下文添加 beginPath / moveTo / lineTo / closePath / fill；不修改全局原型。只接受一条四点路径，非有限坐标、第五点、多子路径、Path2D / fill-rule 参数会报错。不能作为通用 Canvas polyfill。
- 四点凸形调用一次原生 fillQuad；舍入产生的凹形和交叉形分解成最多两次原生三角填充（用重复顶点的凸四边形表示）。没有脚本逐扫描线绘制，保留 confetti 的顶点和运动；原生 fillQuad 仍拒绝凹形。
- 导入不启动循环；rAF 沿用宿主帧时钟和取消机制，不包第二层计时器。创建多个 confetti 实例会各有其任务，应复用同一个实例，重播前 reset。
- 显式调用 `OrielShims.capabilities()` 返回机器可读对象（version、htmlNamespace、reactIframeProbe、animeTypeProbes、canvas、confetti）；在页面中显式调用 `console.log(JSON.stringify(...))` 取一行 JSON 诊断；CLI 标准输出和 MCP oriel_snapshot 的 output 保留这行日志（宿主带日志级别前缀）。默认不输出日志。它说明桥接范围，不是第三方库认证结果。

## 已知阻断与替代

Rhino 已设为 `VERSION_ECMASCRIPT`；单纯升语言级别不能消除以下解析限制。保持 VM / 安全补丁不变，本轮不修改 Rhino。

| 库 | 具体失败位置 / 后续缺口 | 替代路线 |
|---|---|---|
| Vue 3.5.13、Alpine 3.14.8 | 原包 / es2015 展开构建有 rest 箭头参数解析问题；继续降低 arrow 后，Vue 出现 Expected argument of type object（undefined）且状态更新失败，Alpine 明确缺 MutationObserver。具体探针见工作区报告。 | 优先 Preact / React；不能把 petite-vue 宣称为已兼容，它也有模板接管缺口。 |
| Chart.js 4.4.7 | `{[key]: value}` 计算属性解构不受支持；后续完整 Canvas 能力也未验证。 | DOM 条形图 + d3-scale 纯计算。 |
| marked 12.0.2 原包 | 私有类成员；仅 target:es2015 仍在 rest 箭头参数失败。 | 使用上述 prepare-marked.mjs（额外 supported.arrow=false），已回归受控 Markdown 场景；不可信文字仍使用 textContent。 |
| tsParticles 2.12.0 | 原包对象 rest；es2015 后的逗号表达式含 yield 被拒。 | 受限 confetti / 原生 Canvas 批量粒子。 |
| D3 7.9.0 全包 | `(value, yield item)` 一类 yield 表达式解析失败。 | 仅打包 scale / shape 计算模块；路径字符串仍不是可渲染 SVG。 |
| lottie-web 5.12.2 Canvas | navigator 注册守卫；临时页面守卫后，通过配置 error 事件已定位 Canvas setTransform 缺失，isLoaded=false。路径 / 矩阵 / clip / 图片链路也没有完整合同。 | 不伪造 navigator / Image；可离线导出帧图，但这不算 Lottie 运行时兼容。 |

Lottie 子集渲染器是独立建议，**未实现**：先限定 solid / shape 层、2D transform / opacity、线性关键帧，设计 JSON → 帧命令适配；完整版本还需贝塞尔路径、裁剪、填充规则、图片、字体、遮罩 / 混合、缓存及像素对拍。现有 fillQuad 仅能覆盖矩形 / 简单多边形的非常小子集，不适合承诺任意 AE 动画。
