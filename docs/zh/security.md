# 安全与设置

[入口](index.md) · [脚本](scripting.md) · [服务端与容器](server.md) · [接入](integration.md)

玩家受什么保护、页面仍然能做什么、mod 和服务器作者必须做什么，以及 `config/oriel.properties` 里的每一个设置。

玩家会加入自己不能控制的服务器，服务器也会放进自己不能控制的客户端。Oriel 对双方都按最坏情况设计。服务器打开的页面是不可信的，无论是服务器直接发来 HTML（`openInline`）还是来自某个 mod：服务器资源包可以替换任何 mod 的页面、脚本或样式表，所以无论怎样，页面的代码都可能是服务器的。你自己内置的页面也适用同样的规则。客户端发来的消息同样不可信，因为被修改的客户端可以发任何东西。

## 安装方向与连接检查

服务端安装了 Oriel，就要求加入的客户端也安装并匹配协议：两个加载器当前都会在玩家加入时执行 `requireOnClient`，缺库或不兼容时主动断开并提示中英文原因。这是源码确认的现状，NeoForge 的 optional 网络注册不会取消该检查。

反方向不能据此推导：客户端单装 Oriel 的本地页面、HUD、热重载及本地消息监听不依赖服务端库。最终下游的注册、业务与连接兼容性仍须按目标验证。服务端没有库时不能提供 `OrielServer` 会话或页面/数据推送；`ForgeMenu` 已是完整版参考内容，需要请复制到自己模组。精确目标和限制见[分发指南](distribution.md#已测边界)。

## 玩家受到哪些保护

- 脚本在沙箱里运行：没有 Java、没有网络、没有文件。它们唯一的出口是 `oriel.send`，发给打开该页面的服务器或 mod 自己的处理器。脚本不能读剪贴板（只有玩家自己在文本框里按 Ctrl+V 才会读），也不能写（只有 Ctrl+C 或 Ctrl+X）。
- 页面不能让游戏一直忙。每次脚本调用有预算（5000 万条指令或 1 秒），定时器和帧回调每帧获得 100 ms，一个持续超出预算、或让帧一直慢于 200 ms 的页面会被终止。
- 页面不能撑满游戏内存。一次脚本调用最多分配 256 MiB，一个页面最多 100,000 个节点、嵌套 512 层、1600 万个画布像素；内建函数拒绝超过 100 万个元素的数组和超过 1600 万字符的字符串；脚本发现垃圾回收后堆仍超过 90% 的页面会被终止并释放。`localStorage` 和 `sessionStorage` 各 256K 字符，只存活到页面关闭，所以不会写到磁盘。
- 页面不能让游戏崩溃。任何错误，包括栈溢出和内存耗尽，都只会停掉那个页面，页面随后显示它的错误。它每秒最多写 50 行日志，每行截断到 4096 个字符。
- 页面只通过 Minecraft 的资源管理器加载样式表、脚本、图片和字体，别无他途。带协议的 URL 和其他 URL 一样只是资源 id（`https://example.com/a.png` 指的是 `assets/https/...`），所以不会有请求离开游戏，第三方不会得知玩家的 IP，也不会读取资源包之外的文件。开发环境里页面还会从 `common/src/main/resources/assets` 读取，但绝不从它之外读取。
- mod 可以用玩家可编辑的文件夹来提供一个命名空间（见[玩家可编辑的页面](pages.md#玩家可编辑的页面)）。这些页面只能读该文件夹内的文件，包括 `..` 和指向文件夹之外的符号链接。服务器打开的页面不能读它们，服务器也不能打开它们。这同样适用于服务器会话里导航或重载之后。被拒绝的文本或二进制读取返回 null，并记录 ERROR 级诊断（`server-folder-access`，来源是被拒绝的 URL，行号未知时为 0）。
- 画布只能绘制其他画布，所以 `getImageData` 读不到纹理、皮肤或屏幕的像素。没有 `toDataURL`。
- Shift+Escape 永远会关闭 Oriel 界面，页面看不到这个键。即使页面用 `preventDefault()` 保留了 Escape，1.5 秒内按三次 Escape 也会关闭它。
- 服务器页面只会替换"没有界面"、容器界面或另一个 Oriel 界面。玩家在聊天、暂停菜单、选项、告示牌或任何其他界面时，页面会等他们离开。服务器抓不到输入聊天的按键，带"断开连接"按钮的暂停菜单始终可达。
- 如果服务器在玩家用 Escape 关闭页面后一秒内又打开页面，连续三次，它在 30 秒内不能再打开页面，并在聊天里告知玩家。打开有限速（一次 5 个，之后每秒 1 个；等待中的页面会被更新的页面替换）。
- 指向网页的链接会先询问（像聊天链接那样），而且只在页面里点击或按键之后，所以脚本不能一遍遍弹出这个询问。`client.webLinks=block` 完全关闭网页链接。其他链接只加载资源里的 `.html` 页面。
- `oriel.send` 每秒最多发送 20 条消息，按界面计数，所以重载页面或点链接不会重置计数。
- `oriel.playSound` 只播放游戏认识的声音，每个界面每秒最多 8 个，音量不超过 `client.maxSoundVolume`，再按玩家的声音设置缩放。
- 玩家在服务器页面里输入文字时，Oriel 在屏幕底部显示一条提示。页面不能遮住它，也无法得知它是否存在。
- 来自服务器的数据在脚本解析之前，会限制大小和嵌套深度。任一方向上格式错误的数据包都会断开发送方，不会让任何东西崩溃。
- `client.serverPages=ask` 在每次访问某服务器、其第一个页面显示之前询问一次；`block` 拒绝所有服务器页面。被拒绝的页面会被报告为已关闭，所以服务器的会话随之结束。

## 页面仍然能做什么

- **看起来像任何东西**。借助 Minecraft 的字体和精灵，页面可以仿制原版界面、微软登录表单，或一个带链接的"已断开连接"界面。输入提示是唯一的破绽。**永远不要在 Minecraft 界面里输入密码。**
- 读取玩家在它自己的字段里输入或粘贴的内容，并发给服务器。
- 了解客户端并回传：某个资源是否存在、图片的大小（来自布局）、翻译（包括其他 mod 的：`oriel.t`、`<mc-text key>`、可翻译的 `<mc-text json>`）、玩家的按键绑定（`keybind` 组件）、GUI 缩放和窗口大小，以及 `client.reducedMotion`。服务器据此可以得知玩家装了哪些 mod 和资源包，原版可翻译告示牌文字也曾允许这样做。
- 按网络 id 绘制客户端知道的任何实体。这些实体是服务器发来的，所以对它不泄露任何东西，但它可以向玩家显示游戏本来会隐藏的实体。
- 让客户端向 Mojang 查询任意玩家名，用于 `<player-head name>`，和原版玩家头颅物品一样。
- 链接到另一个 mod 的页面（`<a href>`、`oriel.open`）。页面仍在服务器的会话里，所以它的 `oriel.send` 消息发给服务器，`onPageLoad` 钩子也会为它运行。
- **用满自己的预算**。页面每帧都可以分配到上限，让游戏更频繁地做垃圾回收，反复导航或重载自己，并保留 Escape。玩家仍可以用 Shift+Escape 离开。
- 想推多频繁就推多频繁。每次推送都在 CPU 预算内运行页面的监听器。想让客户端变慢的服务器，原版也有办法做到。

## mod 和服务器作者必须做什么

服务器端：

- **把每条消息都当成恶意的**。被修改的客户端可以在自己的会话里的任何通道上发任何 JSON 值，只受限速约束。检查类型（`getAsString()` 之前先 `isJsonPrimitive()`）、范围（负数、零和巨大的数量；`1e999` 会解析成无穷大），以及玩家此刻是否可以这样做：仍在方块附近、仍拿着物品、商店仍有库存。Oriel 会记录处理器抛出的异常并继续。
- ID、价格和名称从你自己的状态里取，不要取自消息。消息应当只说玩家选了什么，花多少由服务器决定。
- 不要指望 `onClose` 很快到达。客户端可能永远不报告关闭。一个玩家打开的会话达到 `server.maxSessionsPerPlayer` 时，Oriel 会结束他最旧的会话，他离开时结束全部。
- 消息按严格方式解析（没有 `NaN`、注释或尾随文本），最多嵌套 `server.maxMessageDepth` 层，并附带真实的发送玩家。客户端不能触及别的玩家的会话，也不能触及已关闭的会话。
- **不要用玩家写的文字拼接内联 HTML**。把名字或聊天内容放进 `openInline` 的标记、`innerHTML` 或 `v-html`，可以加入带 `onclick` 处理器的元素，它们在观看者的页面里运行，并能以该玩家的身份发消息。这类文字请放进 `oriel.data` 传递，并用 `{{ }}` 或 `textContent` 显示，它们永远不会解析 HTML。
- `push` 的数据要小。客户端每次都会完整解析它。

客户端：

- `OrielScreens.onPageLoad` 钩子也会为服务器页面运行。在给页面提供服务器不该拿到的数据之前，先检查 `driver.serverSession()`。它仅表示页面是否由服务器打开、属于服务端会话；不是服务器是否安装 Oriel 的探测，本地页面在有库服务器上也会返回 false。
- 当前没有统一的客户端侧服务端能力探测 API。等待服务器确认的业务应自行设计回执和超时，不把本地 `oriel.send` 的监听结果、发送成功或 `serverSession()` 当成服务器业务可用的证明。
- **不要把秘密放进页面和 `oriel.data`**。玩家能读到你发的任何东西，资源包也能替换任何页面的脚本。
- 如果你的页面用 `keydown` 监听器保留了 Escape，也要提供一个关闭按钮。玩家可以用 Shift+Escape 或连按三次 Escape 离开，但很少有人知道。

## 设置

`config/oriel.properties` 在游戏启动时由两端读取。第一次启动会把所有键、缺省值和注释写进去，之后的启动会补上缺少的键。缺失、格式错误或超出范围的值会回退到缺省值，并在日志里给出警告；Oriel 绝不会因此启动失败。`server.` 开头的键在服务器上有效（专用服务器，或单人世界内部的服务器），`client.` 开头的键在客户端有效，`limits.` 开头的键在页面运行的任何地方有效。

服务器发送和接受什么：

| 键 | 缺省 | 范围 | 含义 |
|---|---|---|---|
| `server.maxInlineHtmlChars` | 200000 | 1 到 200000 | `openInline` 发送的最大页面；更大的会抛异常。 |
| `server.maxDataChars` | 100000 | 1 到 100000 | `open` 和 `push` 发送的 `oriel.data` 最大 JSON 长度；更大的会抛异常。 |
| `server.maxMessageChars` | 8192 | 1 到 8192 | 接受页面发来的最长消息；更长的被丢弃。 |
| `server.maxMessageDepth` | 32 | 1 到 255 | 消息里数组和对象的最大嵌套深度。 |
| `server.messageBurst` | 40 | 1 到 10000 | 一个会话一次可接受的消息数。 |
| `server.messagesPerSecond` | 20 | 0.1 到 10000 | 突发之后，一个会话每秒接受的消息数。 |
| `server.maxSessionsPerPlayer` | 8 | 1 到 1024 | 每个玩家打开的会话数；再开一个会结束最旧的。 |

客户端允许页面做什么：

`client.nestedNotice`，默认 `on`，配置值为 `on/off`，内部为 Choice/enum（`NestedNotice.ON/OFF`），设置页以开关呈现。提示页勾选“不再提示”立即写回 `off`；配置只接受 on/off，不接受 boolean。关闭只抑制客户端提示，扫描与风险日志继续工作；恢复默认不清空已提示指纹历史。嵌套风险日志、缓存与旧副本限制见[分发指南](distribution.md#5-多个模组都带-oriel-时)。

可在模组设置页修改的项：`client.uiSounds`、`client.uiSoundVolume`、`client.reducedMotion`、`client.typingNotice`、`client.serverPages`、`client.webLinks`、`client.shapeShader`、`client.nestedNotice`。保存成功后立即更新内存并原子替换 `config/oriel.properties`，保留注释与其他键的顺序；恢复默认只改这八项。音量控件步长为 0.05，宿主接受范围内的有限数值，不钳制非法值。`shapeShader` 每帧读取，不需重启；减少动效是否被页面脚本遵守取决于页面实现。文件保存失败或值不合法时保留原值并记录诊断。

设置页的内部宿主协议：仅本地 `OrielKit.page("modsettings")` 页面接入，服务器会话不会得到设置数据或写入处理器。页面通过 `OrielKit.CHANNEL` 发送 `{"op":"set","key":"client.uiSounds","value":"false"}` 或 `{"op":"reset"}`；页面控件的值统一发送字符串，由对应 Setting 解析；宿主也接受类型相符的 JSON 布尔值或数值，选项为字符串（`ALLOW`/`ASK`/`BLOCK`，网页链接仅 `ASK`/`BLOCK`，嵌套提示为 `ON`/`OFF`，解析不区分大小写；嵌套提示不接受 JSON boolean 或 `true/false` 字符串）。未知操作忽略，未知键、缺省/null/错误类型的值拒绝；不开放限额、调试或强制关闭设置。请求为最多三个字段的扁平对象，紧凑 JSON 不超过 1024 字符，沿用按界面跨重载的 `client.messageBurst` / `client.messagesPerSecond` 限速。

加载及每次已送达请求后，宿主以 `driver.patch("settings", state)` 完整替换 `settings` 子字段（不覆盖其他页面数据）。`state.items` 的每项为 `{key,type,value,default,min,max,step,options,live}`，`type` 为 `bool`/`number`/`choice`；当前八项 `live` 均为 `true`，非数字项的 `min/max/step` 为 null，非选项项的 `options` 为 `[]`，选项值使用大写枚举名。拒绝后的回执仍含原值；重复设置是幂等的，重载后重发当前内存状态。此协议是内部页面合同，纯 Java 回归与三版本编译/API 检查和游戏里的声音、动效、着色器验收须分别验证。

| 键 | 缺省 | 范围 | 含义 |
|---|---|---|---|
| `client.nestedNotice` | `on` | `on`、`off` | 仅客户端的嵌套风险提示开关，设置页以开关呈现；提示页“不再提示”写 `off` 到 `config/oriel.properties`，恢复默认为 `on`。关闭不影响扫描、风险日志或加载器选择；专服不弹窗。白名单/持久化/恢复已单测，游戏内仍未验证。 |
| `client.serverPages` | `allow` | `allow`、`ask`、`block` | 服务器打开的页面是否显示。`ask` 每次访问问一次。 |
| `client.maxInlineHtmlChars` | 200000 | 1 到 200000 | 显示的最大内联页面。 |
| `client.maxDataChars` | 100000 | 1 到 100000 | 接受的服务器数据最大长度。 |
| `client.maxDataDepth` | 64 | 1 到 512 | 接受的服务器数据最大嵌套深度。 |
| `client.openBurst` | 5 | 1 到 1000 | 服务器一次可打开的页面数。 |
| `client.opensPerSecond` | 1 | 0.01 到 1000 | 突发之后，服务器每秒可打开的页面数。 |
| `client.messageBurst` | 20 | 1 到 10000 | 页面一次可发送的消息数，按界面计。 |
| `client.messagesPerSecond` | 20 | 0.1 到 10000 | 页面每秒可发送的消息数，按界面计，跨重载。 |
| `client.forceCloseKey` | `key.keyboard.escape` | 按键名 | 与 Shift 一起关闭任何 Oriel 界面。名字同 `options.txt`。 |
| `client.forceClosePresses` | 3 | 2 到 10 | 在 1.5 秒内按几次 Escape 就关闭一个保留了 Escape 的页面。 |
| `client.reopenStrikes` | 3 | 1 到 100 | 连续几次重新打开会让服务器的页面被停用。 |
| `client.reopenBlockSeconds` | 30 | 1 到 3600 | 停用多久。 |
| `client.soundsPerSecond` | 8 | 0 到 1000 | 页面每秒（和同时）可播放的声音数，按界面计；0 表示静音页面。 |
| `client.maxSoundVolume` | 1 | 0 到 1 | 页面可请求的最大音量。 |
| `client.uiSounds` | `true` | `true`、`false` | 是否播放 Oriel 的 UI 声音，含脚本和默认控件声。 |
| `client.uiSoundVolume` | 1 | 0 到 1 | UI 音量乘数，作用在 `maxSoundVolume` 限幅之后，再由游戏声音设置缩放；0 静音。 |
| `client.webLinks` | `ask` | `ask`、`block` | 网页链接：先确认（在点击或按键之后），或永不打开。 |
| `client.typingNotice` | `true` | `true`、`false` | 在服务器页面里输入时的提示。 |
| `client.reducedMotion` | `false` | `true`、`false` | 页面看到 `prefers-reduced-motion: reduce`。 |
| `client.inspector` | `false` | `true`、`false` | F12 检查器仅在包含开发工具的构建可用；开发环境默认开启。正式包（`-Poriel_release`）排除其实现，此开关不会启用检查器。 |
| `client.shapeShader` | `true` | `true`、`false` | 圆角框、边框和 box-shadow 由着色器绘制，边缘平滑、模糊真实（受支持的 26.x 目标）。`false` 则画成平面方块。 |

引擎对每个页面（无论是 mod 的还是服务器的）设的上限。每个值至少为 1，`heapLimitPercent` 最大 100（100 表示关闭堆检查）。它们是 `dev.oriel.engine.Limits` 的字段，其 `describe` 给出每个键的注释；宿主可以从 `Host.limits()` 返回自己的值。

| 键 | 缺省 | 含义 |
|---|---|---|
| `limits.instructionBudget` | 50000000 | 一次脚本调用可运行的指令数。 |
| `limits.timeBudgetMs` | 1000 | 一次脚本调用可用的墙钟毫秒数。 |
| `limits.loadTimeBudgetMs` | 10000 | 页面加载期间的同一预算。 |
| `limits.maxStackDepth` | 1000 | 嵌套的脚本调用。 |
| `limits.maxBudgetOverruns` | 3 | 被预算终止的调用累计多少次后页面停止。 |
| `limits.frameScriptTimeMs` | 100 | 每帧的定时器和帧回调毫秒数；其余留到下一帧。 |
| `limits.slowFrameMs` | 200 | 慢于此的帧算作慢帧。 |
| `limits.maxSlowFrames` | 25 | 连续多少个慢帧后页面停止。 |
| `limits.entryAllocation` | 268435456 | 一次脚本调用可分配的字节数（256 MiB）。 |
| `limits.heapLimitPercent` | 90 | 一次回收后堆占用超过多少就停止正在运行的页面。 |
| `limits.maxStringLength` | 16777216 | `repeat`、`padStart`、`padEnd`、`replace`、`join` 产生的字符数。 |
| `limits.maxArrayLength` | 1048576 | 内建函数遍历的数组、`apply` 的参数、`split` 和 `match` 的片段数。 |
| `limits.maxBufferBytes` | 16777216 | `ArrayBuffer` 或类型化数组的字节数。 |
| `limits.maxBigIntBits` | 65536 | BigInt 的位数（对整个游戏）。 |
| `limits.maxTimers` | 10000 | 待处理的定时器和动画帧数。 |
| `limits.maxMarkupLength` | 1048576 | `innerHTML`、`outerHTML`、`insertAdjacentHTML` 和 `v-html` 接受的字符数。 |
| `limits.storageQuota` | 262144 | `localStorage` 和 `sessionStorage` 各自的字符数。 |
| `limits.maxLogLength` | 4096 | 一条控制台消息的字符数。 |
| `limits.logRate` | 50 | 每秒日志行数。 |
| `limits.maxForItems` | 10000 | 一个 `v-for` 的条目数。 |
| `limits.maxTemplatePasses` | 10 | 每帧的模板遍数。 |
| `limits.maxNodes` | 100000 | 页面的节点数。 |
| `limits.maxDepth` | 512 | 元素嵌套深度，以及 `JSON.parse` 里的 JSON 嵌套深度。 |
| `limits.maxCssNesting` | 32 | 嵌套的 CSS 函数和块。 |
| `limits.maxSelectorParts` | 256 | 一个选择器的组成部分数。 |
| `limits.maxListItems` | 64 | 阴影列表的条目数。 |
| `limits.maxGridTracks` | 100000 | 网格轨道列表的轨道数。 |
| `limits.maxVarLength` | 65536 | `var()` 替换之后一个值的字符数。 |
| `limits.maxCanvasSize` | 2048 | 画布每边的像素数。 |
| `limits.maxCanvasPixels` | 16777216 | 一个页面所有画布的像素总数。 |

`client.serverPages` 缺省是 `allow`。服务器界面正是 Oriel 的用途，原版也允许服务器不经询问就打开容器界面，而且上述保护不需要玩家出手就能起作用。想有发言权的玩家可以设成 `ask`。
