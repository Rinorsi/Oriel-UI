# Minecraft 域扩展

保存游戏宿主和预览器共用的场景、NBT、实体取景和 Minecraft 字符宽度表。它们不再随纯核心引擎加载；类的包名、公共方法和源文件字节保持不变，页面中的 scene 等写法仍由原有宿主注册。

本模块不依赖 net.minecraft、加载器或 GPU API；实际游戏类型仍由 common 的宿主适配。Minecraft 与预览器显式依赖本模块，六个加载器复制其类与资源。核心测试中的场景回归显式安装本扩展。

EntityFocus、字体公共常量与 MC 属性枚举名仍作为兼容壳记在结构允许清单；实际策略和解析实现已由扩展提供，后续 API 迁移逐条删除兼容壳，不能把整个允许清单刷新为新的现状。

新增 CompatibilityProfile/FontProvider 负责原有 8px、9/8 行高、字宽基线、字体映射、UA 样式和 MC 资源策略。PropertySchema 在原注册位置安装十二个 MC 属性的解析器和复合值，ElementExpansion 保留富文本生命周期，ImageProvider 与 ElementContentProvider 负责精灵语法和元素。

公开 EntityFocus、ComputedStyle/Prop 字段以及 Image.Sprite/Canvas.drawSprite/Host.formatText 等兼容接口仍保持原签名。三个 EntityFocus 结构条目留作 API 迁移债务；原解析器的第四条已经迁出，不能据此声称领域概念为零。
