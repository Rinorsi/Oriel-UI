# Showcase 源码导览：每个效果是怎么做的

**0.12 起瘦包不含模板、showcase、demo 和相关展示资源；本页的游戏演示入口只适用于完整版。** 当前源码保留 `/oriel showcase` 为“未包含演示内容”的说明入口，不提供演示子命令；该修复须随下一 minor 发布，原冻结的 0.12.0 会报参数错误。 下游不能依赖这些页面恰好在库里，所用内容必须复制到自己的模组，见[分发指南](distribution.md)。0.12 瘦包下的复制示例运行未验证。

官方模板入口：`/oriel showcase` → 模板中心 → 官方模板；原有风格位于「旧版」。源码目录和元数据见[官方模板目录](templates.md)。

Showcase 是 Oriel 的**主参考库**：8 个整页示例 + 4 个基础演示，全部是普通的 HTML/CSS/JS，几乎不依赖 Java，源码里有大量写给人看的注释。要做类似的效果，**直接打开对应页面的源码照着读、照着改**，不要从零发明。模板中心的官方模板（`kit/.../ui/templates/`）及 `legacy/` 下旧版风格模板和各预设页（设置、背包、锻造等）是**次级参考**：它们展示"整套界面风格"和"控制器写法"，不是效果的出处。

## 在哪里

| 内容 | 源码 | 游戏里看 |
|---|---|---|
| 8 个展示页 + 共用样式 | `common/src/main/resources/assets/oriel/oriel/showcase/`（`*.html`、`showcase.css`） | 模板中心 → 旧版 → 演示；`/oriel showcase <页面>` |
| 4 个基础演示、标题卡、toast、箱子 | `common/src/main/resources/assets/oriel/oriel/demo/` | 模板中心 → 旧版 → 演示；`/oriel showcase layout\|animation\|livedata\|forms\|titlecard\|toast\|chest` |
| 生物图鉴的游戏数据 | `common/src/main/java/dev/oriel/mod/internal/client/showcase/Mobdex.java` | 同上 |
| 完整版 jar 里（瘦包没有） | `assets/oriel/oriel/showcase/`、`assets/oriel/oriel/demo/`（解压即可读） | |

下面的行号是写本文时的位置；源码改动后行号会移，**按注释里的关键词搜索**最可靠。

## 怎么复用到你的模组

1. 从源码或完整版把要的 `*.html` **和同目录的 `showcase.css`**（8 个展示页都链接它）复制到你模组的 `assets/<你的模组id>/oriel/` 下，并沿 HTML/CSS/脚本引用复制图片、JSON、结构等资源。官方模板复制完整目录。`minecraft:` 原版纹理及 `oriel:ui/ore.css`、核心字体/图标可以保留；参考内容的 `oriel:` 地址与页面跳转必须改到自己的命名空间，不能假设装了库就有。
2. Java 里 `OrielScreens.open("<模组id>:oriel/shop.html")`，或游戏里 `/oriel open <模组id>:oriel/shop.html`。想先预览，放进 `config/oriel/my-pages/` 即可（见 [快速入门](quickstart.md)）。
3. 生物图鉴需要自己提供 `oriel.data.mobs`，可以阅读 `Mobdex.java` 的数据推送写法，但不要导入这个内部演示类；伙伴控制台的关键词假回复要由自己的消息处理接入真实业务，见[服务端与容器](server.md)。小屋页引用的 `oriel:structures/cottage.nbt` 是参考资源，也应复制到自己模组并改 URL。演示容器/HUD 的注册与业务逻辑由自己的模组接入，不因复制 HTML 自动获得。
4. 想要库的动效（涟漪、聚光、计数），在 `<html>` 上加 `data-oriel-shell`，见 [做页面前先读](page-rules.md)。

## 怎么查：先搜效果，再读代码

要做某个效果，**先用查询脚本找到最接近的现成实现**，它直接打印位置、做法和那段源码：

```bash
node tools/showcase/query.mjs 视差 无缝            # 多个词都要匹配（效果、做法、标签、页面）
node tools/showcase/query.mjs box-shadow --list    # 只列标题，不打印代码
node tools/showcase/query.mjs --list --page showcase/shop.html   # 看一页的全部效果
node tools/showcase/query.mjs 进度条 --lines 30 --json          # 多打印几行；--json 给程序读
```

MCP 里 `oriel_rules` 的 `topic=showcase` 读的是下面这份文档，`feature` 填关键词可以只取匹配的几行。索引数据在 `tools/showcase/index.json`：每条是 *效果 → 页面 → 锚点（源码里一行注释的开头）*，脚本按锚点实时定位行号，**源码移动后行号不会过期**；锚点找不到时 `node --test tools/showcase/index.test.mjs` 会失败。**新增或改名示例页时，同一次变更里给它加索引条目**，然后运行 `node tools/showcase/query.mjs --write-doc` 刷新下面的表。

## 效果索引（示例页与效果一一对应）

下面的表由 `index.json` 生成，不要手改；位置是 `文件:行号`（行号为生成时的位置）。

<!-- showcase-index:start -->

### `showcase/title.html`：标题画面：纯 CSS 的动态场景（昼夜、视差山、像素树、3D 挤出标题、毛玻璃菜单）

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 昼夜循环的三层天空 | `title.html:33` | 三层渐变叠放，黄昏层和夜层按周期淡入淡出 |
| 星星：脚本生成、各自闪烁 | `title.html:56` | 脚本给每颗星随机的闪烁时长和延迟，不会同时闪 |
| 流星 | `title.html:82` | 一条短渐变条，每隔几秒划过天空 |
| 用 box-shadow 画像素物（飞鸟） | `title.html:100` | 1px 盒子，阴影列表就是其余像素；扇翅=切换阴影列表 |
| 方形太阳与多层光晕 | `title.html:118` | 炽热核心 + 浅色外圈 + 两层柔光，全部 box-shadow，无图片 |
| 月亮与环形坑 | `title.html:152` | 方形月亮，两个坑是小渐变方块，日落时升起 |
| 视差山层（无缝循环） | `title.html:169` | 每层是两倍宽的条带，里面两份相同瓦片，平移条带即可无缝 |
| 方块云 | `title.html:207` | 扁平板加一块更窄的顶板，随昼夜染色 |
| 云杉树：多层 box-shadow 叠成 | `title.html:264` | 一个 2×3 盒子，每层阴影不模糊、扩展逐级增加，六层成 27px 的树 |
| 萤火虫上浮 | `title.html:292` | 从森林里漂上来的小点，各自随机时长 |
| 页面从黑色淡入 | `title.html:316` | 一层全屏黑，动画淡出 |
| 标题 3D 挤出 | `title.html:350` | 32px 是字体的 4 倍所以清晰；挤出 = 四层逐级变暗的 1px 阴影 |
| 字母依次高亮的扫光 | `title.html:357` | 模板给每个字母错开动画延迟，高光依次扫过 |
| 倾斜脉动的 splash 文字 | `title.html:378` | -20° 倾斜加缩放脉动，像原版 |
| 毛玻璃面板 | `title.html:400` | 半透明填充、较亮顶边、斜向高光、柔和落影 |
| 菜单行逐条浮入 | `title.html:419` | 逐行延迟的上浮动画；用 backwards 而不是 both，避免动画结束后锁住悬停位移 |
| 悬停右滑并发金光 | `title.html:443` | 悬停时右移并变金色，物品图标开始起伏 |
| 带种子的随机数（风景每次一样） | `title.html:534` | 一个小 LCG，保证每次打开生成同样的风景 |
| 鼠标视差 | `title.html:628` | 越近的山层移动越多，CSS transition 负责平滑 |

### `showcase/hud.html`：HUD 套件：罗盘、Boss 血条、任务目标、toast、状态效果、血量与快捷栏

| 效果 | 位置 | 怎么做 |
|---|---|---|
| HUD 背景：黑曜石柱用真方块纹理 | `hud.html:37` | 远处的柱子用更小的纹理块并加暗 |
| 罗盘、Boss 条、快捷栏居中 | `hud.html:95` | left 取 50%，再按自身宽度的一半拉回 |
| 罗盘刻度按方位滑动、标签按距离淡出 | `hud.html:730` | 脚本每帧平移刻度并按到中心的距离改透明度 |
| Boss 血条：原版紫轨道里放自制填充 | `hud.html:177` | 原版轨道图加 1px 框，自己的填充放里面 |
| 无限滚动的渐变填充 | `hud.html:195` | 填充裁剪一条很宽的重复渐变，让它一直横向滚动 |
| 浮起并消失的伤害数字 | `hud.html:222` | 从填充末端生成，CSS 动画结束后脚本删除节点 |
| 任务目标列表 | `hud.html:268` | 每个目标一个网格：第一行 [标记|文字]，第二行 [ |条|数量] |
| 带扫光的细进度条 | `hud.html:324` | ::after 伪元素的亮带从左到右扫过 |
| toast 离场：先滑出再折叠高度 | `hud.html:384` | 先位移淡出，再把高度收到 0，下面的 toast 平滑补位 |
| 状态效果列表 | `hud.html:433` | 图标 + 倒计时的紧凑行 |
| 原版风格血量、饥饿、快捷栏 | `hud.html:469` | 用原版精灵拼出，只在整屏模式显示 |
| 心跳：lub-dub 缩放涟漪 | `hud.html:502` | 每颗心比前一颗晚 50ms 开始同一脉冲 |
| 1px 黑描边的数字 | `hud.html:533` | 四个方向的 text-shadow 拼出描边 |
| 模拟战斗节奏 | `hud.html:794` | 一阵阵连击，中间停顿，偶尔打断连击 |
| toast 约 5 秒后自动离场 | `hud.html:816` | 到时间先加离场类，动画完再删除 |

### `showcase/shop.html`：商人集市：交易卡片、稀有度边框、物品飞入背包、数字滚动

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 靛蓝布料斜织纹 | `shop.html:27` | 斜向重复渐变叠在竖向暗纹上 |
| 条纹遮阳篷与扇形边 | `shop.html:34` | 条纹 = 重复渐变；扇形边 = 径向渐变沿底边平铺 |
| 暖光轻微闪烁 | `shop.html:62` | 一层暖色光的不规则透明度动画 |
| 标题与工具栏先后落下 | `shop.html:98` | 工具栏比标题晚一拍 |
| 一条下划线滑到当前标签下 | `shop.html:157` | 脚本只改位置，CSS transition 负责滑动 |
| 卡片悬停：抬起、放大、点亮边框 | `shop.html:244` | transform + 边框色过渡，物品图标开始起伏 |
| 物品 2 倍清晰 | `shop.html:274` | 32px 正好是 16px 物品的 2 倍，保持像素清晰 |
| 价格：小绿宝石加数字 | `shop.html:297` | 卡片和背包里同一套价格样式 |
| 渐变边框 | `shop.html:315` | 透明边框，再给背景叠几层、分别裁到不同盒子 |
| 传奇物品常驻漂浮 | `shop.html:350` | 更慢更低的漂浮动画，无论是否悬停 |
| 特效层：飞入背包的物品、礼花、toast | `shop.html:508` | 一层只放特效的全屏容器，脚本往里加元素 |
| 数字滚动 | `shop.html:724` | 脚本按时间插值更新文本 |
| 物品沿弧线飞进背包再弹出角标 | `shop.html:763` | 算出起点终点，用动画沿弧线飞行，落点后角标弹跳 |
| 按稀有度排序、按标签和搜索过滤 | `shop.html:666` | 一个纯函数返回要显示的列表，模板渲染 |

### `showcase/mobdex.html`：生物图鉴：数据驱动的掌机，列表、全息舞台、统计面板

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 类别色通过 --c 传给 CSS | `mobdex.html:29` | 脚本按类别算色，写成每个元素的自定义属性 |
| 掌机外壳：厚度阴影与落影 | `mobdex.html:48` | 第一层阴影是机身厚度，第二层是落影 |
| 模制接缝 | `mobdex.html:67` | 一条暗线加其下一条亮线 |
| 喇叭栅格 | `mobdex.html:149` | 重复渐变做出条缝 |
| 列表底部渐隐而不是硬切 | `mobdex.html:213` | 遮罩渐变 |
| 列表行依次滑入 | `mobdex.html:323` | 每行内联设置延迟 |
| 小捕捉球 | `mobdex.html:363` | 两个渐变色标加一个半径 |
| 全息舞台 | `mobdex.html:434` | 聚光、透视无关的地板网格、角标、扫描线、转台 |
| 生物自转，悬停暂停 | `mobdex.html:461` | -mc-yaw 用 @keyframes 自转，悬停舞台时暂停 |
| 选中时的一次性闪光 | `mobdex.html:507` | :key 让元素重建，CSS 进场动画重播 |
| LIVE 标签 | `mobdex.html:544` | 舞台角落的小标签 |
| 统计面板标签对齐成一列 | `mobdex.html:576` | 每行以标签开头，标签共用宽度 |
| 游戏数据 oriel.data.mobs | `mobdex.html:839` | 由 Mobdex.java 推送，FLAVOUR 给原版生物补手写条目 |
| 由外部指定打开哪只生物 | `mobdex.html:995` | 数据 {start: {mob, filter}} |

### `showcase/journal.html`：日志：皮革封面、纸张、墨迹、地图、翻页

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 皮革封面 | `journal.html:67` | 渐变加纹理的封面 |
| 纸张 | `journal.html:101` | 米色纸，文字用墨色 |
| 书脊折痕 | `journal.html:142` | 一条细暗线 |
| 花边分隔 | `journal.html:182` | 两条渐隐线加一个旋转 45° 的方块 |
| 荧光笔选中 | `journal.html:216` | 背景渐变只涂下半部分 |
| 墨水进度条 | `journal.html:250` | 阴影线填充放在带尺线的框里 |
| 蜡封 | `journal.html:306` | 径向渐变团，边缘用 box-shadow 做不规则，压印字母 |
| 首字花饰（金箔扫光） | `journal.html:333` | 金色底、双层框、间歇扫过的高光 |
| 点线引导 | `journal.html:447` | 弹性填充元素加点状下边框 |
| CSS 网格地图（噪声着色） | `journal.html:469` | 9px 的瓦片，每格一个 div，颜色由小噪声函数决定 |
| 到任务点的虚线轨迹 | `journal.html:515` | 一条虚线旋转到位 |
| 比例尺 | `journal.html:572` | 墨色与纸色交替的方块 |
| 翻页纸张 | `journal.html:585` | 默认隐藏，由脚本驱动抬起，靠抬起的一边更暗 |
| 缎带燕尾 | `journal.html:636` | 两个边框三角形在中间留一个 V 形缺口 |
| 切换任务时墨迹重新写出 | `journal.html:752` | 按任务设 :key，元素重建 |

### `showcase/console.html`：伙伴控制台：终端风格的对话、权限询问、任务列表

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 面板角标 | `console.html:66` | 伪元素画对角两个角 |
| 状态环永远旋转 | `console.html:115` | 圆的上、右边框点亮，整体旋转 |
| 思考时环加速并变琥珀色 | `console.html:137` | 换类名改动画时长和颜色 |
| 血条（亮头） | `console.html:220` | 弹性行，填充撑满高度，前沿更亮 |
| 输入中的三点依次弹跳 | `console.html:318` | 同一动画，三点各错开延迟 |
| 条纹进度条（条纹行进） | `console.html:439` | 内层条带在循环里平移 |
| 当前任务的小转圈 | `console.html:496` | 一个点亮一侧的环 |
| 新消息出现后滚到底 | `console.html:666` | 模板渲染完再滚动 |
| 逐字回复与权限询问 | `console.html:691` | opts: think 延迟、ask 询问某项权限、onYes 回调 |
| 任务列表自动滚到进行中的一项 | `console.html:716` | 脚本滚动容器 |
| 接入模型：oriel.send('chat', text) | `console.html:773` | 页面里用关键词假回复占位，真实接入发给服务端 |

### `showcase/models.html`：3D 转台：方块、物品、生物的 3D 展示（纯标记，无脚本）

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 展示格：台座光晕与底部标签 | `models.html:65` | 每格一个 <model>/<entity>，下面一团光 |
| 大舞台：拖动旋转、悬停摆动并俯视 | `models.html:112` | -mc-yaw / 拖动由引擎处理，页面只设样式 |
| 半身像在画框底部渐隐 | `models.html:200` | 遮罩渐变 |

### `showcase/scene.html`：小屋：会自己搭起来的 3D 场景，场景内标注、分层切片

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 场景占满整页，界面浮在上面 | `scene.html:24` | <scene structure=…> 铺满，面板绝对定位 |
| 未搭好的步骤完全透明 | `scene.html:52` | 完全透明的组既不绘制也不被点中 |
| 场景里的 HTML 标注 | `scene.html:93` | 用方块坐标把标注钉在建筑上 |
| 面板与工具栏 | `scene.html:161` | 浮在场景上的普通 HTML |
| 建造者朝向（yaw 计算） | `scene.html:362` | 按 Minecraft 的 yaw：0 朝南、90 朝西 |
| 最短方向转向 | `scene.html:401` | -mc-yaw 的过渡按数值走，所以要自己算最短弧 |
| 分层切片 | `scene.html:446` | 每层一组，第一次要用才创建 |
| 从某一步打开 | `scene.html:515` | 数据 {start: {step: 3}} |

### `demo/layout.html`：布局：flexbox、命名区域网格、自适应瓦片

| 效果 | 位置 | 怎么做 |
|---|---|---|
| flexbox：同样三块在不同 justify-content 下 | `layout.html:7` | 一排对比 |
| 带命名区域的网格仪表盘 | `layout.html:28` | grid-template-areas |
| 随窗口宽度重排的自动填充瓦片 | `layout.html:59` | auto-fill + minmax |

### `demo/animation.html`：动画：关键帧与过渡

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 关键帧：旋转、脉动、滑入、起伏 | `animation.html:7` | 四个 @keyframes 和它们的使用 |

### `demo/templates.html`：实时数据：模板、数据绑定、表单、服务端推送

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 模板取值顺序 | `templates.html:94` | 循环变量 → oriel.data → 状态 → 全局 |
| 回车提交表单 | `templates.html:71` | 输入框回车与按钮同样提交 |
| 服务端会话推数据，绑定自动更新 | `templates.html:116` | 服务端回应 refresh，页面不用自己更新 |

### `demo/settings.html`：表单：原版风格控件

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 不加样式的控件本来就是原版外观 | `settings.html:7` | 页面只负责排版 |

### `demo/toast.html`：可交互 HUD 叠加层

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 可交互 HUD 叠加层（按 T 后可点） | `toast.html:7` | OrielHud.Input.WHEN_CHAT_OPEN，其他区域点击穿透到聊天 |

### `demo/hud.html`：标题卡淡入淡出叠加层

| 效果 | 位置 | 怎么做 |
|---|---|---|
| 标题卡淡入淡出叠加层 | `hud.html:7` | 两个 @keyframes |
<!-- showcase-index:end -->

## 次级参考：模板与预设

- **官方与旧版风格模板**（`kit/src/main/resources/assets/oriel/ui/templates/`，旧版在 `legacy/`）：看**整套风格怎么配色、排版、统一**，不是单个效果的出处；完整版可在模板中心保存副本后改，发布下游时再复制到自己的资源目录。
- **预设页**（`kit/.../ui/*.html` 和 `kit/.../preset/*Preset.java`）：设置、背包、锻造、音乐、组件目录。想看 **Java 控制器怎么写**、`Shell.setup`、`Motion`、`Widgets` 的用法，读这里。
- 组件与参数查手册：[组件](components.md)、[CSS 参考](css-guide.md)；设计规则：[设计规范](design.md)、[动效](motion.md)。
