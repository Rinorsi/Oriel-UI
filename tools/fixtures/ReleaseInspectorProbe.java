import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Actual release JAR linkage and script startup, without booting a game or using development output. */
public class ReleaseInspectorProbe {
    public static void main(String[] args) throws Exception {
        Path release = Path.of(args[0]).toAbsolutePath();
        List<URL> urls = new ArrayList<>();
        urls.add(release.toUri().toURL());
        for (String entry : Files.readString(Path.of(args[1])).split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            String normalized = entry.replace((char) 92, '/');
            if (!entry.endsWith(".jar") || normalized.matches(".*/(engine|script-rhino|kit|mc-extension|rhino)/build/.*")) continue;
            urls.add(Path.of(entry).toUri().toURL());
        }
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            for (String name : List.of("dev.oriel.engine.inspect.Inspection", "dev.oriel.engine.inspect.BoxHighlight",
                    "dev.oriel.engine.script.InspectorBindings", "dev.oriel.mod.internal.client.DevInspector")) {
                try {
                    Class.forName(name, false, loader);
                    throw new AssertionError("Development class in release classpath: " + name);
                } catch (ClassNotFoundException expected) {
                    // Absence is required; no fallback to compiled development directories.
                }
            }
            for (String name : List.of("dev.oriel.mod.client.DocumentDriver", "dev.oriel.mod.internal.client.McHost",
                    "dev.oriel.mod.internal.client.InspectorAccess", "dev.oriel.engine.host.Host",
                    "dev.oriel.mod.internal.OrielCommon", "dev.oriel.mod.internal.client.OrielClient",
                    "dev.oriel.mod.internal.client.OrielClientCommands", "dev.oriel.mod.internal.client.OrielKeys",
                    "dev.oriel.mod.internal.server.OrielCommand", "dev.oriel.mod.internal.client.MyPages",
                    "dev.oriel.engine.script.RhinoScriptRuntime")) {
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredMethods(); type.getDeclaredFields(); type.getDeclaredConstructors();
            }
            if (loader.getResource("assets/oriel/ui/templates/catalog.json") == null) {
                loader.loadClass("dev.oriel.kit.OrielKit").getMethod("register").invoke(null);
                Object ids = loader.loadClass("dev.oriel.kit.TemplateCatalog").getMethod("ids").invoke(null);
                if (!List.of().equals(ids)) throw new AssertionError("Slim catalog is not empty");
                Object command = loader.loadClass("dev.oriel.mod.internal.client.OrielClientCommands").getMethod("create").invoke(null);
                Object tree = command.getClass().getMethod("build").invoke(command);
                var child = tree.getClass().getMethod("getChild", String.class);
                for (String name : List.of("settings", "about", "open", "reload", "showcase"))
                    if (child.invoke(tree, name) == null) throw new AssertionError("Missing core command " + name);
                Object unavailable = child.invoke(tree, "showcase");
                if (unavailable.getClass().getMethod("getCommand").invoke(unavailable) == null)
                    throw new AssertionError("Slim showcase must explain missing reference content");
                if (!((java.util.Collection<?>) unavailable.getClass().getMethod("getChildren").invoke(unavailable)).isEmpty())
                    throw new AssertionError("Slim showcase must not advertise missing pages");
                for (String name : List.of("canvastest"))
                    if (child.invoke(tree, name) != null) throw new AssertionError("Reference command " + name);
            }
            Class<?> flags = Class.forName("dev.oriel.mod.internal.client.DevFlags", true, loader);
            if (flags.getField("INSPECTOR_AVAILABLE").getBoolean(null)) throw new AssertionError("Inspector enabled");
            if (!Boolean.FALSE.equals(loader.loadClass("dev.oriel.mod.internal.client.InspectorAccess")
                    .getMethod("enabled").invoke(null))) throw new AssertionError("Inspector entry enabled");
            if (loader.loadClass("dev.oriel.mod.internal.client.InspectorAccess").getMethod("open",
                    loader.loadClass("dev.oriel.mod.client.DocumentDriver")).invoke(null, new Object[]{null}) != null) {
                throw new AssertionError("Release constructed an inspector");
            }
            Class<?> hostType = loader.loadClass("dev.oriel.engine.host.Host");
            Class<?> fontType = loader.loadClass("dev.oriel.engine.host.FontMetrics");
            Object font = Proxy.newProxyInstance(loader, new Class<?>[]{fontType}, (proxy, method, values) -> {
                if (method.getName().equals("width")) return ((String) values[0]).length() * 6f;
                return InvocationHandler.invokeDefault(proxy, method, values);
            });
            Object host = Proxy.newProxyInstance(loader, new Class<?>[]{hostType}, (proxy, method, values) -> {
                return switch (method.getName()) {
                    case "fonts" -> font;
                    case "loadText" -> null;
                    case "reportError" -> throw new AssertionError(java.util.Arrays.toString(values));
                    case "createScriptRuntime" -> {
                        Object factory = loader.loadClass("dev.oriel.engine.script.Scripting").getMethod("rhino").invoke(null);
                        yield java.util.function.Function.class.getMethod("apply", Object.class).invoke(factory, values[0]);
                    }
                    default -> InvocationHandler.invokeDefault(proxy, method, values);
                };
            });
            Class<?> documentType = loader.loadClass("dev.oriel.engine.dom.Document");
            Object document = documentType.getMethod("parse", hostType, String.class, String.class).invoke(null, host,
                    "test:release.html", "<body id=page><script>document.body.setAttribute('data-release','ok')</script></body>");
            if (documentType.getMethod("error").invoke(document) != null) throw new AssertionError("Stopped document");
            Object body = documentType.getMethod("getElementById", String.class).invoke(document, "page");
            Object value = body.getClass().getMethod("getAttribute", String.class).invoke(body, "data-release");
            if (!"ok".equals(value)) throw new AssertionError("Script did not execute");
            documentType.getMethod("close").invoke(document);
            System.out.println("PASS release inspector isolation/linkage/script startup: " + release);
        }
    }
}
