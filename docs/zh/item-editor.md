# 物品组件编辑器：字段与保存合同

[入口](index.md) · [服务端](server.md) · [组件](components.md)

这是当前 `/oriel edit` → `oriel:ui/item_editor.html` 的合同，不是承诺任意第三方组件都可编辑的通用 SDK。玩家需持有物品并具管理员权限；服务端保存时再次验权限、快捷栏位置和物品快照。页面适合用 Ore 外壳、字段表单、3D 预览和只读组件树组合制作。

## 打开时的数据（服务端 → 页面）

| 字段 | 类型/可空 | 用途与写入规则 |
|---|---|---|
| id | string | 物品注册 ID，只读，不支持更换物品类型 |
| count | int | 当前数量，只读，不支持从此接口复制物品 |
| defaultName | string | 原物品显示名，只读 |
| name | string，可省略 | 自定义纯文本名；未自定义时没有字段 |
| nameColor | string，可省略 | 自定义名的 #rrggbb；未设置时没有字段 |
| rarity | string | common / uncommon / rare / epic；这是 MC 物品稀有度，不是主题的六档稀有度 |
| lore | string[] | 当前说明的纯文本形式 |
| enchantments | {id:string,level:int}[] | 当前普通附魔，不等于 stored_enchantments 等任意组件 |
| damage / maxDamage | int | 当前损耗/最大耐久；仅可损坏物品适用 |
| unbreakable | boolean | 是否存在不可破坏组件 |
| glint | boolean 或 null | true 强制闪光、false 禁止、null 按原逻辑 |
| attributes | 对象数组 | 见下表 |
| enchantmentChoices | {id,name,max}[] | 服务端注册表选项；max 是自然等级提示，服务端编辑上限另见下文 |
| attributeChoices | {id,name}[] | 属性注册表选项 |
| components | object，键为组件 ID | 所有可序列化组件的只读 JSON；不可编码值为说明字符串 |

attributes 每项：

| 字段 | 类型/值 |
|---|---|
| attribute | 服务端属性注册 ID，必须存在 |
| modifier | 修饰器 ID；有效时保留，无效/省略时保存生成 oriel:edit_N |
| amount | 有限 double，[-2048,2048] |
| operation | add_value / add_multiplied_base / add_multiplied_total |
| slot | 目标 MC 的 EquipmentSlotGroup 序列化名；当前页面提供 any/mainhand/offhand/hand/head/chest/legs/feet/armor/body；服务端枚举校验，不接受自造名字 |

## 保存请求（页面 → 服务端）

频道 `save`，载荷是 JSON 对象。只提交需要修改的可写字段；字段**省略表示不改**。除了明确列出 null 语义的字段，不发送 null；数组需要数组，不能把字符串 JSON 再嵌套一次。

| 字段 | 上限/行为 |
|---|---|
| name | 最长 50（Java 字符串长度）；空/空白或 null 移除自定义名称；不是任意聊天 JSON |
| nameColor | #rrggbb；无效颜色不附加。**与 name 成对提交**：只提交 nameColor 而缺 name 会进入名称重建分支并移除名称 |
| rarity | 只接受 MC 四个枚举名 |
| lore | 最多 16 行，每行最多 100；空数组移除；重写时为纯文本说明，不保留旧每行富文本结构 |
| enchantments | 最多 32 项，level 1–255，ID 须存在；提交整个列表替换。空数组清空 |
| maxDamage | 可损坏物品，1–100000；非可损坏物品不因此获得耐久系统 |
| damage | 可损坏物品，0 ≤ damage < 生效后的 maxDamage；改 maxDamage 时同时考虑 damage，避免不一致 |
| unbreakable | true/false，增删组件 |
| glint | true/false/null；null 移除覆盖，恢复物品默认 |
| attributes | 最多 16 项，按上表校验，整个列表替换；空数组清空 |

```js
oriel.send('save', {
  name: '测试剑', nameColor: '#55ff55',
  lore: ['测试说明'],
  enchantments: [{id: 'minecraft:sharpness', level: 3}],
  glint: null
});
```

id/count/defaultName/choices/components 不在可写字段内；发它们不会自动执行变更。只读组件树可包含其他模组的组件，保存从当前 stack 副本开始，仅改指定字段，未知组件保留。此表单不提供任意组件树写回、任意 SNBT 执行或跨版本组件转换。

## 回执（服务端 patch result）

| result 字段 | 成功 | 失败 |
|---|---|---|
| status | saved | rejected |
| item | 新完整快照 | 不提供 |
| reason | 不提供 | 协议 2 的 `{key,args,count?}` 语言中立对象 |

失败原因由 `LocalizedMessage.fromJson(reason).render(document)` 按客户端语言解析；不要直接把对象当作文字，也不要猜测翻译用户输入。协议 1 的字符串拒绝格式不能与协议 2 混用。完整格式见 [国际化](i18n.md)。快照中的 `unencodableComponents` 列出无法编码的组件 id，仅这些组件的显示占位可翻译，原始用户组件字符串保持不变。

保存成功后服务端更新原始快照，允许下一次继续编辑；页面须用 result.item 更新自己的基线。它不是把新 item 自动替换到原来的所有顶层字段；自定义页须处理回执。失败不能丢弃草稿或显示成功。通道/配额拒绝等情况不保证总能收到业务回执，等待超时显示未确认并允许重新打开读取。

关闭/取消若没发送 save 不改变原物品。这个接口没有内置撤销历史；“撤销已保存”需要新的、有权限校验的保存或业务设计，不能只给 notice 的按钮写一个文字。

## 建议的 UI 状态模型

保留 snapshot（服务端确认）、draft（工作副本）、dirtyFields（改动字段）、saving、error。输入只改 draft，预览读取 draft；保存仅提交 dirtyFields（名称/颜色例外成对提交）。成功接新 snapshot 并清 dirty；拒绝保留 draft。打开期间切换手持物/槽位，提示重新打开。

必验：不提交取消、普通保存、拒绝后草稿保留、成功再读、改名和颜色组合、清空数组、glint 三态、未知组件保留、重复点击、物品被外部改变、玩家无权限、两个玩家隔离。预览器 fixture 只能验 UI，真实 ItemStack 与服务端验证必须联机。
