# 容器页面：页面里的物品槽

[入口](index.md) · [服务端与容器](server.md) · [元素](elements.md)

用页面上的 `<slot>` 元素摆放槽位的物品栏和其他菜单。本页讲页面一侧；服务端菜单、校验和锻造菜单的接入合同见[服务端与容器](server.md)。

任何 mod 都可以给自己的菜单类型一个 Oriel 界面。页面用 `<slot index="n">` 摆放槽位：菜单的第 `n` 个槽位会出现在该元素被绘制的位置，每帧更新，所以槽位跟随滚动、变换和动画。物品、高亮、提示和手上拿的物品仍由原版绘制，点击、拖动和 Shift 点击也由原版处理。没有被绘制的槽位（没有元素、被隐藏、不透明度低于一半、或滚出了容器）会被隐藏。

```java
// 客户端初始化（两个加载器都要，在游戏加载完成之前）：
OrielScreens.registerContainer(MyMenus.FORGE, "mymod:oriel/forge.html");   // 一个 MenuType 或注册表持有者
```

```html
<section class="grid">
  <slot v-for="i in range(0, 27)" :index="i"></slot>
</section>
```

配方查看器（JEI、REI、EMI）围绕容器界面的 GUI 区域来排版。对 Oriel 容器来说，这个区域就是页面的内容，每帧按绘制后的样子量：`<body>` 的在流内的子元素（居中的面板），或者页面标记了任何元素时，带 `data-oriel-bounds` 属性的元素。当面板不是 `<body>` 的直接子元素、定位的部分（侧边标签、浮动的物品栏）也属于它、或者全屏包装元素会占掉整个屏幕时，请标记它们：

```html
<body>
  <div class="backdrop">            <!-- 盖住整个屏幕：不是 GUI -->
    <main class="panel" data-oriel-bounds>...</main>
    <aside class="tabs" data-oriel-bounds>...</aside>
  </div>
</body>
```

这个区域是被标记元素的边框盒的并集。两者都没有的页面，整个屏幕就是它的 GUI 区域，配方查看器因此会避开它。

容器页面的 `oriel.data` 是 `{title, inventory, slots}`，其中 `slots[n]` 是菜单槽位 `n` 的 `{id, count, name}`。它在菜单内容变化时更新（也只在那时更新），所以页面可以显示合计或高亮搜索结果。点击页面内容之外（指针下只有 `<html>`/`<body>` 的地方）会丢下手上拿的物品，和点击原版容器之外一样。

想给页面自己的数据（机器人的实体 id、它的等级），传一个以菜单为参数的函数。它的字段会加到 `oriel.data` 里，与 `title`、`inventory`、`slots` 并列（与它们同名的字段会替换它们）：

```java
OrielScreens.registerContainer(MyMenus.BOT, "mymod:oriel/bot.html", menu -> {
    JsonObject data = new JsonObject();
    data.addProperty("entity", menu.botId());
    data.addProperty("tier", menu.tier());
    return data;   // 没有则返回 null
});
```

这个函数在界面打开时运行，之后每个客户端 tick 都运行，只要它的结果或槽位变了，页面就会得到新数据，所以它可以读取菜单、同步的 `ContainerData`，或你的网络包更新的客户端状态。（`driver().push` 会替换整个 `oriel.data`，包括槽位，直到下一次变化；请改用这个函数。）

试一试：`/oriel showcase chest` 打开一个箱子，它的界面是 `assets/oriel/oriel/demo/chest.html`。

该样例及 `ForgeMenu.open(player)` 缺省固定到脚下的非空气方块，最大距离 8 格，并检查拥有者、存活、关闭状态和方块是否存续。自定义固定位置使用 `ForgeMenu.Workstation`；两参数的 `ForgeMenu` 构造器保留便携模式。参数、失败和授权合同见[服务端菜单](server.md#锻造菜单的接入合同)。
