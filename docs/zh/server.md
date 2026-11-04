# 服务端与容器

Oriel 的页面运行在客户端。服务端决定业务状态和物品变化，通过现有协议 v1 发送页面与数据；不在服务端创建 Screen，也不依赖客户端渲染类。

## 选哪一类

| 类型 | 谁打开、谁拥有状态 | 使用场景 | 入口 |
|---|---|---|---|
| 纯客户端 | 客户端打开，状态只影响本地展示 | 设置、图鉴、HUD、演示 | OrielScreens、OrielHud |
| 服务端驱动页面 | 服务端针对一个玩家创建会话，校验消息并推送状态 | 商店、任务、对话、物品编辑表单 | OrielServer、OrielSession |
| 物品容器 | 服务端菜单拥有槽位和计算结果，原版同步物品 | 锻造台、仓库、真实物品交易 | MenuType、AbstractContainerMenu、OrielContainerScreen |

有真实物品拖拽、Shift 点击或快捷栏交换，就使用容器。普通按钮操作可以使用服务端页面，但购买数量、费用、库存等必须由服务端重新校验。不要让页面发送一份 ItemStack JSON 后直接覆盖玩家物品。

kit 的演示背包不是玩家真实背包；forge 演示页面也不会因为外观像锻造台就自动具有服务端槽位。菜单绑定是独立步骤。

## 纯客户端的最小例子

下面的方法仅从客户端初始化或客户端命令调用，不能放入专用服务器会加载的入口类：

```java
import dev.oriel.mod.client.OrielScreens;

public final class LocalPage {
    public static void open() {
        OrielScreens.openInline("<html><body><p>Local information</p></body></html>", null);
    }
}
```

调用不会创建 OrielServer 会话。它不能据此修改服务端物品或获得权限。文件页面使用 OrielScreens.open("mymod:ui/info.html")，对应 assets/mymod/ui/info.html。

## 服务端驱动页面的最小例子

下面是可直接加入公共源码的命令注册类。Fabric 在 CommandRegistrationCallback 中调用 register；NeoForge 在 RegisterCommandsEvent 中调用。此例把 HTML 直接发送给一个玩家，无需额外资源包，也不包含样式。

```java
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import dev.oriel.mod.server.OrielServer;
import dev.oriel.mod.server.OrielSession;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ServerPageExample {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("server-page").executes(context -> {
            var player = context.getSource().getPlayerOrException();
            JsonObject data = new JsonObject();
            data.addProperty("player", player.getGameProfile().name());
            OrielSession session = OrielServer.openInline(player, """
                    <html><body><button id="ping">Ping</button>
                    <script>
                    document.getElementById('ping').addEventListener('click', function () {
                        oriel.send('ping', null);
                    });
                    </script></body></html>
                    """, data);
            session.onOpenFailure(reason -> player.sendSystemMessage(
                    Component.literal("This feature needs Oriel on your client.")));
            if (session.openStatus() != OrielSession.OpenStatus.SENT) return 0;
            session.onMessage("ping", (sender, value) -> {
                if (!value.isJsonNull()) return;
                sender.sendSystemMessage(Component.literal("Pong"));
            });
            session.onClose(() -> { /* Release this player's business state. */ });
            return 1;
        }));
    }
}

```

文件页面用 OrielServer.open(player, "mymod:ui/shop.html", data)。服务端发送 URL，客户端必须有相应资源；服务端 JAR 中存在 assets 并不意味着未安装该 JAR 的客户端也能读取。

### 更新和生命周期

- session.push(data) 替换整份 oriel.data；session.patch("progress", value) 只更新顶层一个字段，null 删除字段。业务层应在变化时推送。
- onMessage 收到的是经过严格 JSON 解析的值和当前发送玩家。协议层保证会话归属，业务层仍须校验权限、资源数量、数据类型、范围、距离和当前菜单等条件。
- session.close() 关闭该会话；客户端关闭、玩家离线和服务器停止也会清理会话。onClose 在已关闭会话上注册会立即执行。
- openStatus() 返回 SENT 或 CLIENT_UNSUPPORTED。后者表示客户端没有协商接收通道，返回会话已关闭、服务端映射已移除。onOpenFailure 在 open 返回之后注册也会立即通知，不会错过失败。
- SENT 只表示通道支持且已发送，不是页面加载／显示成功的确认。客户端可以按配置拒绝服务端页面，资源也可能缺失。需要确认某一步时，在业务协议中增加明确的回执和超时，不要在发送时扣款。
- 所有服务端接口都在服务器线程调用。加载器包处理器已经切换线程；自己的异步任务必须 server.execute(...) 后再操作会话／菜单／玩家。
- 不要让一个玩家的会话引用另一个玩家的业务状态；保存会话应按玩家和业务 ID 区分，不能用单个全局 currentSession。

## 带物品槽的容器

MenuType 和菜单工厂属于公共注册，实际菜单实例在服务端拥有输入、输出和配方计算。客户端工厂建立相同数量和顺序的槽，只接收原版同步结果。OrielContainerScreen 将 HTML 的 slot 元素映射到菜单索引，点击、拖拽、Shift 点击、拾取物品走原版菜单包。

现有 /oriel demo chest 是最小可运行参考：OrielDemos.CHEST 注册为独立 MenuType；服务端 OrielDemos.openChest(player) 打开 27 格样例容器；客户端初始化已经注册 OrielScreens.registerContainer(OrielDemos.CHEST, OrielDemos.CHEST_PAGE)。可以直接从自己的服务端命令调用：

```java
import dev.oriel.mod.server.ForgeMenu;
import net.minecraft.server.level.ServerPlayer;

public final class ContainerExample {
    public static void open(ServerPlayer player) {
        ForgeMenu.open(player);
    }
}
```

只展示少量槽的页面骨架如下；生产页面要列出所有需要操作的槽。此处不设计布局：

```html
<html><body>
  <slot index="0"></slot>
  <slot index="1"></slot>
  <slot index="27"></slot>
</body></html>
```

index 是菜单 slots 列表索引，不是玩家 Inventory 的索引。未绘制、隐藏或被裁剪的槽会移出可点击区域。把槽画出来不会改变 mayPlace、mayPickup、quickMoveStack 或服务端校验。页面里用 item 展示的图标不是实际槽。

OrielContainerScreen 自动给页面提供 title、inventory 和 slots 数组（id/count/name）；额外字段来自 registerContainer 的数据函数。数据函数在客户端读取同步后的菜单，不能从客户端重新推断配方结果。

## 锻造菜单的接入合同

本轮只实现服务端和数据，不注册新 Screen、不改 kit 的 forge 页面。主会话完成页面及客户端注册前，/oriel-forge 只有服务端菜单入口，尚不是完整可用的玩家界面。

公共类型：ForgeMenu.TYPE，注册 ID oriel:forge。入口：/oriel-forge（管理员权限），或者在服务端调用 ForgeMenu.open(player)。它先检查客户端是否支持 Oriel 通道；不支持时返回 false，发送普通聊天提示，不发送自定义菜单打开包。

主会话需要在客户端入口、MenuScreens 注册事件之前接入以下代码，并自行提供对应页面：

```java
import dev.oriel.mod.client.OrielScreens;
import dev.oriel.mod.server.ForgeMenu;

public final class ForgeClientRegistration {
    public static void init() {
        OrielScreens.registerContainer(ForgeMenu.TYPE, "mymod:ui/forge.html", ForgeMenu::pageData);
    }
}

```

| 菜单索引 | 内容 | 服务端规则 |
|---|---|---|
| 0 | 输入物品 | 铁剑或钻石剑，最多 1 个 |
| 1 | 材料 | 铁锭或钻石，必须与配方相符 |
| 2 | 输出 | 禁止放入；结果在服务端完成时写入 |
| 3–29 | 玩家主背包 | 对应 Inventory 9–35 |
| 30–38 | 快捷栏 | 对应 Inventory 0–8 |

| 顶层数据字段 | 类型 | 含义 |
|---|---|---|
| progress | int | 已完成的服务端工作 tick |
| duration | int | 当前为 40 tick |
| state | int | 0=无可加工输入，1=加工中，2=输出占用 |
| selectedRecipe | string | 当前匹配的配方 ID，无匹配时空串 |
| availableRecipes | string[] | 服务器当前匹配的配方列表；目前最多一项 |

槽位由原版同步；四个整数用 ContainerData 同步。pageData() 将同步的配方索引转换为稳定 ID。页面只显示字段；无需发送“完成加工”消息，也不能提交自选结果物品。

**当前实现默认，非已定玩法**：两条修理配方 oriel:repair_iron_sword 与 oriel:repair_diamond_sword；消耗一件受损武器和一个对应材料，恢复最大耐久的四分之一，复制保留其组件。两条配方是后端联调样例，不是数据包配方系统。主会话可在确认玩法后替换配方实现，保持槽位与数据合同。取消／关闭／断线时返还剩余材料与已产出的结果；输出占用时不继续消耗；更换输入或材料会重置进度。

## 安全边界

协议版本仍为 1，没有改包格式。限制由 config/oriel.properties 中 server.*、client.* 控制：会话数量、消息突发量／速率、JSON 大小与深度等。通道名不是授权，合法 JSON 也可能包含巨大数字；钱、价格、配方、物品权威状态始终来自服务端。

/oriel edit 的 ItemEditor.save 是表单校验参考：再次验证命令权限、快捷栏索引和原物品快照，只应用改过的字段，检查范围并保留未知组件。它不等价于原版物品槽交互，不应作为容器拖拽的实现方式。

纯客户端类放客户端入口。公共注册／服务端菜单不得 import net.minecraft.client；不要通过在客户端调用同名服务端静态方法假装完成联网。

## 重复验证

在 Git Bash 中先配置本项目 JDK；运行目录必须放仓库外。新建专用测试目录会写 server.properties，脚本只监听 127.0.0.1；offline-mode 仅供本机验收，不可直接用于公开服务器。EULA 由操作者确认，已有 eula=true 时可省略 --accept-eula。

```bash
node tools/server-smoke.mjs fabric /absolute/external/fabric --multiplayer --accept-eula
node tools/server-smoke.mjs neoforge /absolute/external/neoforge --multiplayer --accept-eula
```

脚本对每个 Gradle 进程设 900 秒上限，启动一个专用服务器和 A/B 两个真实客户端。服务器只给 A 打开现有 templates 页面；A 等到真实文档加载后回消息；B 验证没有收到打开包／没有打开页面，并伪造 A 的消息和关闭包。服务端检查仅收到一次合法回调。最后客户端自行退出、脚本经 RCON stop 正常关闭服务器，保存 result.json 和三个日志。去掉 --multiplayer 可只验专用服务器启动／关闭。

GameTest 包含 unsupported_client、player_isolation、forge_menu、protocol_compatibility，分别验证无协商通道的明确结果／回调、玩家隔离、菜单加工／防重复领取和协议能力。正常游戏服务器要求客户端安装匹配协议的 Oriel；未安装或协议不匹配会在加入时断开。openStatus 的失败结果仍保留为防御性 API，不代表缺库客户端可正常进入服务器。

使用 server-smoke.mjs 的 --mismatch 参数可额外启动协议 999 的测试客户端，检查清晰的拒绝原因与正常退出；该覆盖与成功双客户端流程可一起运行。GameTestServer 的 mock 不进行网络协商，只对测试服务器豁免加入检查，正式专用／集成服务器均不豁免。

手动复现多人隔离：开启专用测试服务器，让两个装库客户端分别用 A/B 身份进入；控制台针对 A 调用自己的 OrielServer.open 命令（或启用 -PserverProbe 后执行 oriel-probe start OrielProbeA OrielProbeB），确认 B 仍在原屏幕。A 发送消息后服务端仅处理 A 的状态。若改用真实未装库的客户端，连接成功后调用 OrielServer.open，调用方必须收到 CLIENT_UNSUPPORTED；不能把客户端被加载器的注册表兼容检查拒绝登录误记为打开失败回调通过。
