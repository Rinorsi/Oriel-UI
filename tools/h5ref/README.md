# H5 对照流水线

同一份 HTML 分别交给 Chromium 和 Oriel 预览器，输出原始截图、逐像素热力图、带标注的三联图，以及 Markdown / JSON 报告。工具只测量差异，不修改语料、不自动修复引擎。

## 安装（工作区内，一次即可）

Node 22.12+、Java 25。以下命令在 Git Bash 执行；依赖、npm 缓存、浏览器全部在仓库外，不改变系统环境变量。已验证的版本为 Puppeteer 25.16.0、pngjs 7.0.0、Chrome 156.0.8078.47。

```bash
source D:/Minecraft/project/Mods/Oriel-UI-Workspace/daily/2026-11-04/scratch/env.sh
mkdir -p D:/Minecraft/project/Mods/Oriel-UI-Workspace/tools/chrome
cd D:/Minecraft/project/Mods/Oriel-UI-Workspace/tools/chrome
export PUPPETEER_CACHE_DIR="$PWD/cache"
export npm_config_cache="$PWD/npm-cache"
npm init -y
npm install --save-exact puppeteer@25.16.0 pngjs@7.0.0
# 新版 npm 可能不执行安装脚本，因此显式下载浏览器。
npx puppeteer browsers install chrome
```

下载使用默认网络。不要在源码仓库里运行 npm install，也不要全局安装 Chromium。

## 运行

回到自己的源码仓库或 worktree 根目录：

```bash
source D:/Minecraft/project/Mods/Oriel-UI-Workspace/daily/2026-11-04/scratch/env.sh
# 本环境已有的 Minecraft 资源；省略时预览器自行搜索，找不到会使用后备字体。
export ORIEL_MC_JAR=C:/Users/XD/.gradle/caches/fabric-loom/26.3/minecraft-client.jar
./gradlew --offline :preview:installDist

# 递归跑默认语料，包括 _dev。
node tools/h5ref/compare.mjs

# 仅跑一页；另选输出目录可保留上一轮批量报告。
node tools/h5ref/compare.mjs tools/h5ref/corpus/_dev/flex.html \
  --out D:/Minecraft/project/Mods/Oriel-UI-Workspace/daily/2026-11-04/workers/h5ref/single

# 自定义语料、底色与浏览器。
node tools/h5ref/compare.mjs --corpus path/to/corpus --background '#202830' \
  --chromium D:/Minecraft/project/Mods/Oriel-UI-Workspace/tools/chrome/cache/chrome/win64-156.0.8078.47/chrome-win64/chrome.exe
```

参数：

| 参数 | 默认 / 含义 |
|---|---|
| 位置参数或 `--corpus`（二选一） | `tools/h5ref/corpus/`；也可传单个 `.html` |
| `--out` | 工作区 `daily/2026-11-04/workers/h5ref/out/` |
| `--workspace` | 向上查找 `Oriel-UI-Workspace` 或其同级目录 |
| `--chrome-home` | 工作区 `tools/chrome/`；其中需要 `node_modules` 和 `cache` |
| `--chromium` | Puppeteer 在上述缓存中对应的浏览器；显式路径也必须在工作区内 |
| `--background` | `#ffffff`；对照脚本只接受不透明 `#rrggbb` |
| `--java` | `JAVA_HOME/bin/java`，否则 PATH 中的 java |
| `--timeout` | 每页浏览器加载／引擎子进程 30000ms |

输出目录必须位于工作区、且不在任何 Git 仓库或 worktree 内。浏览器用户配置也写入输出目录；每日清理时可一并清理。没有自动下载、构建或启动 Minecraft 的隐式行为。

可用 --preview-lib 指定另一个 worktree 已安装的 preview/build/install/preview/lib 目录。工具只读取其中的 JAR，不在目标 worktree 构建；未指定时仍使用本仓库的安装目录。report.json 保存该路径和每页实际 Java classpath，以核对本轮使用的引擎。

## 输出与分数

- 每页目录：`chrome.png`、`engine.png`、`heatmap.png`、`comparison.png`、完整引擎 stdout/stderr。
- `report.md`：每页分数与三联图、原始诊断、浏览器资源加载失败、按属性／特性出现页数排序的缺口表。
- `report.json`：同样的数据，以及视口、浏览器版本、引擎命令、资源路径、诊断代码与来源。图片路径相对于报告目录。
- 三联图顺序：Chrome | Oriel engine | Difference heatmap；热力图黑色代表相同，红→黄代表最大 RGB 通道差由 0→255。
- 分数为 **所有像素 RGB 平均绝对误差 / 255 × 100**，0 完全一致，100 最大差异。变化像素比例采用任一通道差大于 8 的阈值；阈值不影响 MAE。
- 诊断页数对同一页面去重，次数保留所有诊断。属性不同值会聚合到同一属性；不能识别的新诊断仍按诊断代码保留。
- 退出码 0 表示所有页面完成渲染和比较，**不代表页面等价**；1 表示至少一页执行失败或配置错误。执行失败页没有伪造的零分，其他页面继续运行。

报告每次覆盖同一个输出目录下的报告文件；本次报告只引用本次成功生成的图片。需要历史记录时给每轮单独指定 `--out`。

## 可比范围

两端都是 427×240 CSS / GUI px、2 倍缩放，截图 854×480 px。Chrome 使用底层背景色覆写，预览器使用 `--background`；不会往原 HTML 注入额外背景元素。预览器的该选项也支持 `white`、`rgb(...)` 等不透明 CSS 颜色，省略后仍显示原世界背景。

语料应为静态、可本地加载的 HTML/CSS。Chrome 页面 JavaScript 禁用，CSS 动画固定在 0ms；引擎比较首帧。脚本驱动界面、视频帧、原生 marquee 滚动、字体加载时序和网络资源不属于确定性测试范围，建议将资源放在页面旁边。开发样例 silent.html 保留了 marquee，因此该页的分数可能轻微波动。

Minecraft 字体与 Chrome 字体的字形、宽度、换行、抗锯齿不同。报告标出是否存在文本，但不会自动断言“仅文字区域不同”，也不会把文本差异从总分中扣掉。默认 UA 样式差异属于真实测量结果，工具不注入 reset。**没有诊断不代表引擎已经支持所有写法**：静默忽略项需要通过热力图和页面复核发现。

## 开发样例与测试

本分支的五个开发样例均放在 corpus/_dev/。其他分支维护的风格语料可通过 --corpus 直接读取，无需复制进本分支。

`blocks.html` 验证纯色、边框、圆角和线性渐变；`flex.html` 验证无文字 Flex 几何；`text.html` 展示已知文字差异。`_dev/scifi.html`、`_dev/silent.html` 从工作区原样复制，刻意保留不支持的 CSS、SVG 和不存在的资源，以观察诊断缺口，不能当作成功等价的基准。

```bash
node --test tools/h5ref/lib.test.mjs
./gradlew --offline :preview:test :kit:test :preview:checkUi
```

检查覆盖像素分数端点、阈值、诊断解析与按页聚合、路径归属判断、失败页报告，以及预览器底色解析、跨帧复用与窗口尺寸变化。新增语料无需登记：递归发现 `.html` 即可。
