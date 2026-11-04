# HTML 与 CSS 解析

解析语法层由 Oriel 自己实现，不依赖 jsoup 或 CSS 库。属性值计算、选择器匹配和布局不属于这次语法层替换。

HTML 使用拉取式分词和独立建树：标签、属性、文字、注释、字符引用、void、raw-text/RCDATA、文档骨架和作用域内的隐式结束标签。片段创建的 script 保持惰性。支持 UI 已使用的自闭合标签约定，不声称实现完整 adoption agency 或全部 HTML 浏览器行为；不支持的元素仍由能力诊断明确报告。

CSS 使用 CSS Syntax 3 词法规则、迭代组件值栈以及规则／声明解析。保留原 Token/ComponentValue 数据接口，使 Properties、Numeric、Images 等值解析器无需换算法。嵌套深度受引擎预算限制；@media、@keyframes、@font-face 等先作为语法节点解析，再交给原能力层决定如何使用或报告。

语法问题保留原文件名和一基 line、column。无效输入仍会恢复，但报告 css-syntax 或 html-syntax；BAD_STRING/BAD_URL 等 token 继续交给声明恢复处理。新增 Diagnostic.column()，旧六参数构造器仍可用。既有资源／属性能力诊断保留各自位置口径，不伪造原始 HTML 内联样式的位置。

## 双跑迁移验收

迁移期间使用系统属性 oriel.parser=old、new、both；both 还必须指定仓库外的绝对 oriel.parser.report 路径。每次 HTML 文档／片段、CSS 规则／组件值／声明及诊断都比较字段，不用对象地址或截图相似度替代解析结果。每行 JSON 保存类型、来源、比较字段数和具体差异。

完成默认切换后将删除旧实现。旧实现删除后，不再接受 old/both，避免名为双跑、实际重复调用同一个实现。需要复现旧新对拍时使用删除前的迁移提交；对拍报告和回归证据保存在外部工作区。发布后的默认与 new 都使用新实现。

## 有意的行为修正

- 原始文本结束名须有标签分隔符：script 不会被字符串中的“</scripted>”提前截断。
- CSS 声明列表中的 at-rule 在自身块结束时恢复，后面的合法声明不会被一起吞掉。
- CSS 输入中的 NUL 和孤立代理码位按 Unicode replacement 处理；EOF 后反斜杠转义成为 U+FFFD，而非静默分裂为另一 token。
- 原来静默接受／跳过的重复属性、无效引用、错配结束标签、未闭合字符串／注释／块等，现在产生带位置的语法诊断；对应可恢复的 DOM 或有效声明尽量保持不变。

每项修正有专门回归测试；没有修改页面、样式、Preset 或黄金图来迁就新解析器。对已有 kit 页要求树／规则／诊断双跑零差，以及新实现下的像素不变。合法组件值的原始 source/start/end 保留，以支持变量和 CSS 序列化。
