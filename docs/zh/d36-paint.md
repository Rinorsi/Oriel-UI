# 绘制命令检查与实验缓存

本页描述 0.11.0 的绘制诊断与窄扩展。默认绘制已走新路径；实验缓存不代表整个绘制引擎已替换，也不代表真实 GPU 验收通过。

## 绘制模式

启动 JVM 时使用 -Doriel.paintMode=old|new|both，缺省 new（`old` 回到旧绘制路径）；其他值立即报错。Java 宿主也可使用 document.painter().mode(Painter.Mode.NEW)，仅影响该文档。

| 模式 | 行为 |
|---|---|
| old | 保留原来的逐帧绘制 |
| new | 缓存不依赖外部资源的渐变装饰命令；文本、原生内容、纹理和效果组继续实时执行 |
| both | 对缓存命中项重新生成命令，与缓存逐参数比较，差异立即报页面、元素和命令；实际后端只画一次 |

both 是装饰命令语义检查，不会在游戏中读取 GPU 像素。逐 RGBA 对照通过独立软件后端测试执行；两者不能互相代替。所有模式保留后端自己的圆角、边框和阴影原语，因此不会将 SDF 强制降为普通四边形。

缓存键包含元素身份、稳定计算样式快照、布局树、设备像素与完整装饰几何。动画及其继承计算产生的样式副本直接绘制，避免逐帧录制不能复用的程序。移动、缩放、重新布局或改变样式会重建对应程序；外部图片、Canvas 来源、精灵、border-image 不进入缓存，不猜测宿主资源世代。它不是完整子树缓存，也没有局部屏幕重绘。最多 1024 项、4 MiB 估计命令载荷，超限清空缓存；计数不代表 Java 堆精确占用或 GPU 纹理字节。

只合并相邻且元素标签相同的四边形调用，每批最多 4096 个；不会跨 save/restore、变换、透明度、裁剪、遮罩、效果组或原生绘制重排。原生元素收到真实后端 Canvas，命令流保留其执行位置，回放时仍执行实时内容。

游戏后端在 JVM new/both 模式下保留原有 64px 尺寸桶，并在确认上一帧已绘制、目标全部可回收时，将闲置目标保留量限制到 64 MiB（颜色与深度按 4/8 字节每像素估计），优先保留最近使用项。资源重载沿用原来的清池。此限制不是活动帧峰值显存上限，不能在仍被 GPU 使用时销毁目标；真实六目标 GPU 行为仍须实机核验。

另可在游戏启动时显式设置 -Doriel.paintMetrics=true，让已有层诊断日志附加 Canvas 的 liveTextureBytes、dirtyRowUploadBytes、dirtyRowUploads。缺省关闭；计数仅覆盖成功提交的脏行上传，不包括初建 DynamicTexture 的上传、驱动内部复制或 GPU 耗时。liveTextureBytes 是活跃 Canvas 的 4 字节像素载荷，不包含离屏深度附件或驱动开销。这是内部开发诊断，不是下游模组公开 API。

## 诊断入口

    preview path/to/page.html --snapshot /external/page.png --frames 2 --paint-trace /external/paint.json
    preview path/to/page.html --snapshot /external/page.png --paint-trace /external/paint.json --paint-element "#reward"

仅支持页面快照，不能与 --actions 或 --canvas-test 合用。输出最后一帧的 JSON（page、mode、cache、commands），以及同路径附加 .jsonl 和 .txt 的逐命令版本。目录须已存在；不能写进源码仓库。--paint-element 是元素标签子串过滤，不是 CSS 选择器。重复或缺失 id 不能唯一定位节点；匿名盒子单独标注。

MCP oriel_paint 接受 page，可选 width（1–1920，缺省 640）、height（1–1080，缺省 360）、frames（1–600，缺省 2）、element。返回最多 500 条命令、完整文件路径、commandCount、truncated、缓存计数与 display 的命令/四边形批次/组/原生边界计数。它复用无头预览，不启动游戏，不构成页面交付批准。

每次导出是最后一帧的完整快照，覆盖指定输出文件，不是增量补丁。`commands` 每项的 `command` 是原始零起始序号，`element` 是标签，`operation` 是调用名，`arguments` 是按该 Canvas 方法签名排列的参数数组。坐标是当前变换下的 GUI 像素，颜色是有符号 32 位 ARGB；`boundedGroup` 的最后一个参数是后端是否接受组，`native` 的首项只导出宿主类型名，`skip` 的首项是跳过原因。JSONL 每行一条同结构命令。元素过滤保留原始序号，不重新编号；MCP 的 commandCount 是过滤后数量，display 是未过滤的整帧计数。超过 500 条只截短 MCP 回执，磁盘快照保留完整命令。

Java：先调用 document.painter().trace(true)，绘制后读取 lastDisplayList()，使用 json(filter)、jsonLines(filter)、text(filter) 或 replay(canvas)。每份录制最多 100000 条命令，超限明确报错，不静默截断。回放要求相同初始状态、资源和兼容后端；效果组接受能力变化会明确报错。trace(false) 清掉上次快照。默认不构造命令诊断字符串或逐帧诊断对象；仍有模式/开关判断。

文字命令导出的字体只含 families、size、bold、italic；不序列化宿主字体对象、字形缓存或资源句柄。该导出描述逻辑字体请求，不是字体资源包。

命令包含变换、裁剪、组、mask、backdrop 与原生内容边界。透明度为零、隐藏、匿名装饰会记录 skip 原因；尚不提供逐像素遮挡归因、完整 display:none 子树解释、上传字节或 GPU 耗时。工具输出不能宣称某原生物品 Sheen 已经正确。

## Canvas 线段与混合窄子集

ctx.strokeLine(x0,y0,x1,y1,cap?) 是 Oriel 扩展，不是浏览器标准方法。四个坐标必须是有限数值，单位为画布像素；cap 为 butt（缺省）、round 或 square。使用 strokeStyle、lineWidth、globalAlpha 和 globalCompositeOperation；返回 undefined，不改变样式。零长 round 画圆点，零长 butt/square 不绘制。非有限长度、未知 cap、参数数量错误抛 TypeError。像素中心覆盖，无抗锯齿；同一线段的端帽与主体只混合一次，没有通用路径、连接、曲线或滤镜。

ctx.globalCompositeOperation 缺省 source-over，仅支持 source-over、lighter、screen；不支持的值抛 TypeError（这里刻意不同于浏览器忽略非法赋值）。状态随 save/restore 保存；影响 fillRect、strokeRect、fillQuad、strokeLine、drawImage。clearRect 与 putImageData 保留直接替换语义。旧页面不设置时像素算法不变。

    const ctx = document.getElementById('fx').getContext('2d');
    if (typeof ctx.strokeLine !== 'function') throw new Error('Oriel strokeLine is required');
    ctx.save();
    ctx.strokeStyle = '#ffd08080';
    ctx.lineWidth = 3;
    ctx.globalCompositeOperation = 'lighter';
    ctx.strokeLine(4, 8, 60, 28, 'round');
    ctx.restore();

线段由 CPU 在有界像素区域栅格化；不是 GPU Canvas。Java 库变化需要更新 JAR 并重启。现有 clip-path 的动画仍是离散插值；本次不承诺 Canvas 渐变、完整路径或原生物品扫光已补齐。
