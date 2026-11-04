# 可选 Rhino 脚本运行时

这里保留原有脚本沙箱、DOM 绑定和模板算法。实现类保持原包名与文件内容，核心只保留 ScriptRuntime、ScriptRuntimeProvider 和兼容入口 Scripting.rhino()。

运行时通过 META-INF/services/dev.oriel.engine.script.ScriptRuntimeProvider 注册。宿主选择脚本时加入本模块；没有安装提供者却请求 Rhino 时明确抛错。纯核心无需 Rhino 即可加载。

Minecraft 与预览器默认继续安装此提供者；六个加载器在资源处理时复制其类和服务声明。测试中的脚本／安全用例显式依赖本模块，生产 engine 没有该依赖。Mozilla Rhino 本体、原有补丁及 MPL-2.0 来源说明仍由 rhino 模块管理。
