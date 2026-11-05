# 发行 JAR 消费者验证

从仓库根目录用 JDK 25 运行，编译目录和输出目录均在仓库外：

```sh
javac -Xlint:all -Werror -d <external-classes> tools/compat/BundleFixtures.java tools/compat/RealReleaseProbe.java
java -cp <external-classes> RealReleaseProbe fabric <real-0.9.0.jar> <real-0.10.0.jar> <external-output>
java -cp <external-classes> RealReleaseProbe neoforge <real-0.9.0.jar> <real-0.10.0.jar> <external-output>
```

工具校验输入元数据版本，复用 BundleFixtures 的 ZIP 夹具逻辑，将原始 JAR 逐字节嵌入两个父模组；分别对旧版与新版编译独立消费者，从父 JAR 加载消费者，并在隔离类加载器中用取出的每个真实嵌套版本运行 DOM 解析、查询、修改与关闭操作。另把对 0.9 编译的 `Owner.slots()` 调用在两版运行：旧版应完成方法解析后因空接收者抛出 `NullPointerException`，新版应因旧方法描述符不存在抛出 `NoSuchMethodError`。这是实际二进制链接验证，不需要启动游戏。输出输入 JAR SHA-256；任一编译、字节保持或预期结果断言失败即非零退出。四个参数分别为加载器、0.9.0 JAR、0.10.0 JAR、仓库外输出目录。

此工具不下载或重新构建发行包，也不改输入版本。它验证共同 DOM 子集及一条已知槽回调的 Java 二进制行为，**不执行加载器择优、不启动 Minecraft、不证明全部 API 兼容或公开发行来源**。输入应有独立的发行来源与源码记录；文件名和元数据检查不能替代来源认证。

父模组携带各自 minor 的严格范围：0.9 为 `[0.9.0,0.10.0)`，0.10 为 `[0.10.0,0.11.0)`（Fabric 使用对应谓词）。约束无交集，真实加载器应给出冲突而不是强行加载单个版本。不得为让测试通过而放宽消费者范围或重写真实 JAR。后续经过用户网页验收后，仍须验证加载器冲突诊断及消费者迁移后的实际游戏行为。原有三个参数的 BundleFixtures 命令继续只生成同源、改元数据的 0.10.1/0.10.2 夹具，结论不能混用。
