# 官方模板目录

游戏入口：/oriel showcase → 模板中心 → 官方模板。旧版风格使用同一中心的「旧版」分类。

## 唯一源码入口

kit/src/main/resources/assets/oriel/ui/templates/ 下的 01/、02/……53/ 为同层目录。打开每项 README.md 即可看到真实名称、介绍、分类、版本、作者；index.html、CSS、JS、art/ 是实际运行源码。每项 template.json 是元数据编辑入口，locales 自带名称、介绍、分类、标签与状态。catalog.json 只保存顺序和资源路径。node tools/templates/sync.mjs 根据每项文件生成索引、README 与 files.json，不覆盖 template.json，也不读写全局语言文件。

| 编号 | 分类 |
|---|---|
| 01—10 | 基础组件与轻量提示 |
| 11—21 | 信息与任务交互 |
| 22—28 | 数据型面板 |
| 29—38 | 事件动效与轨迹 |
| 39—44 | 地图与局部绘制，44 为背景参考 |
| 45—53 | 游戏宿主 UI 原型 |

53 项连续编号对应 52 个原型和 44 背景参考说明。44 可打开阅读背景使用说明，不伪装成已完成的场景动画；45—53 不前移。编号稳定，不按验收状态改号。编号原型版本 0.1.0-preview，参考说明 1.0.0-reference；目录归属不代表全部通过游戏验收。

作者 / 制作者统一为「云诺羲」；玩家示例名仍为「云诺羲Rinorsi」。字体沿用 minecraft:default，透明或 Minecraft 宿主背景。第三方来源和版权见资源目录 SOURCES.md，作者字段不能覆盖上游版权。

## 旧版

legacy/ 下保存 rpg、mcdungeons、military、dungeon、arcane、fantasy、terraria、scifi、xianxia 九个旧版模板。每项保留名称、介绍、分类、原版本和日期，作者为云诺羲。旧 ui/styles/<id>.html 仅作兼容跳转，实际源码在 legacy/<id>/index.html。旧收藏和已保存 HTML 的标识保持有效。

## 预览、复制与保存

编号页可直接打开 oriel:ui/templates/09/index.html。模板中心按 01—53 声明顺序排列；切换名称排序仍保持编号顺序。旧版单独筛选；所有模板可按分类、关键词和收藏检索。封面按视口分批读取，打开列表不一次加载全部素材。

「保存副本」将编号页面的 HTML、CSS、JS、素材和元数据按 files.json 整体复制到 config/oriel/my-pages/templates/NN/。先写临时目录，全部成功后再移入目标；已有目录不会覆盖。保存后在「我的页面」编辑副本；官方源不随个人修改改变。旧版新副本保存在 templates/legacy/<id>/，同样包含自身元数据与封面；已有 id.html + showcase.css 副本继续可打开，不覆盖用户修改。

运行资源随 JAR 分发，不依赖 daily 目录。个人副本另存于游戏配置目录；模板源码和工作区 archive/ui-templates 长期归档不受 daily 自动清理影响。启动器迁移个人页面时跳过已原样官方化的旧副本，用户修改过的副本仍保留。

## 开发维护

不再在 tools/mcp/examples/batch-* 中维护编号页；历史生成器与提取资料留工作区长期归档，不参与当前构建，也不能覆盖编号源码。生成器不是当前模板的第二个编辑入口。

验证：node --test tools/templates/catalog.test.mjs；:kit:test 覆盖连续编号、旧版筛选、封面分批、官方入口、完整复制、已有修改保护与复制失败清理。实机画面、输入和流畅度仍需逐项验收，不用目录测试代替游戏验收。


## 模板自己的语言


在每个 template.json 的 locales 中编辑：


~~~json
{
  "locales": {
    "zh_cn": {
      "name": "队员加入与离开",
      "description": "组队事件、联机状态通知",
      "category": "基础组件与轻量提示"
    },
    "en_us": {
      "name": "Party arrivals and departures",
      "description": "Party events and multiplayer connection notices.",
      "category": "Controls and notices"
    }
  }
}
~~~


名称、介绍、分类、tags 和 status 不经过全局语言键解析。编号由稳定 id 添加，不要写进各语言 name；作者/版本/日期等语言无关数据在 locales 外。


每个字段优先使用当前语言；缺少或为空时依次找英文（en_us、en、其他 en_*）、中文（zh_cn、zh、其他 zh_*）、其余语言（语言代码字母序，保证稳定）。语言码忽略大小写，连字符按下划线处理。当前法语即使未提供，也能显示英文；只有中文或其他语言时仍有内容，不显示翻译键。可只提供一种语言；至少应有可用名称、介绍和分类。


模板中心通过当前宿主资源读取每项 template.json；进入中心或切换语言后重新读取。保存的副本在 index.html 旁携带 template.json，目录列表读取副本自己的语言元数据。旧无元数据页面仍读取 HTML title/subtitle。


搜索、收藏、旧版等模板中心通用操作标签继续使用全局语言文件。既有旧版演示页面内部的其他文案保持原有机制；本规则针对模板目录元数据。

