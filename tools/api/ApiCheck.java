import com.sun.source.util.JavacTask;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Compile-time API inspection: no Minecraft class initialization or client launch. */
public class ApiCheck {
    private static final List<String> API = List.of("server.OrielServer", "server.OrielSession", "server.ForgeMenu",
            "client.OrielScreens", "client.OrielScreen", "client.OrielContainerScreen", "client.OrielHud",
            "client.OrielAutomation", "client.DocumentDriver", "client.OrielEntities");
    private static final SortedSet<String> lines = new TreeSet<>();
    private static final List<String> errors = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            record("java.util.List<dev.oriel.mod.internal.Hidden[]>");
            if (errors.size() != 1) throw new AssertionError("Internal generic/array types escaped the API guard");
            System.out.println("API leak guard self-test PASS");
            return;
        }
        Path source = Path.of(args[0]);
        String classpath = Files.readString(Path.of(args[1]));
        Path snapshot = Path.of(args[2]);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<Path> paths = API.stream().map(name -> source.resolve("dev/oriel/mod/" + name.replace('.', '/') + ".java")).toList();
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-classpath", classpath), null, files.getJavaFileObjectsFromPaths(paths));
            task.parse();
            task.analyze();
            for (var diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR)
                errors.add(diagnostic.toString());
            for (String name : API) inspect(task.getElements().getTypeElement("dev.oriel.mod." + name));
        }
        if (!errors.isEmpty()) throw new AssertionError(String.join(System.lineSeparator(), errors));
        String text = String.join(String.valueOf((char) 10), lines) + (char) 10;
        if (args.length > 3 && args[3].equals("--update")) {
            Files.createDirectories(snapshot.getParent());
            Files.writeString(snapshot, text);
            System.out.println("Updated reviewed API snapshot: " + snapshot);
        } else if (!Files.exists(snapshot) || !Files.readString(snapshot).replace(String.valueOf((char)13), "").equals(text)) {
            Path actual = Path.of(args[1]).resolveSibling("api-actual.txt");
            Files.writeString(actual, text);
            throw new AssertionError("Public API changed. Review " + actual + "; update deliberately with -PupdateApi.");
        } else System.out.println("API PASS " + snapshot + " (" + lines.size() + " signatures)");
    }

    private static boolean internal(Element e) {
        return e.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals("org.jetbrains.annotations.ApiStatus.Internal"));
    }

    private static boolean exposed(Element e) {
        return !internal(e) && (e.getModifiers().contains(Modifier.PUBLIC) || e.getModifiers().contains(Modifier.PROTECTED));
    }

    private static void inspect(TypeElement type) {
        if (type == null) { errors.add("Missing documented API class"); return; }
        String header = type.getQualifiedName() + " " + type.getKind() + " " + type.getModifiers()
                + " typeparams=" + type.getTypeParameters() + " extends=" + type.getSuperclass() + " implements=" + type.getInterfaces();
        record(header);
        for (TypeParameterElement parameter : type.getTypeParameters()) record(type.getQualifiedName() + " bound " + parameter + "=" + parameter.getBounds());
        for (Element member : type.getEnclosedElements()) {
            if (!exposed(member)) continue;
            if (member instanceof TypeElement nested) { inspect(nested); continue; }
            if (member instanceof ExecutableElement method) {
                record(type.getQualifiedName() + " " + member.getModifiers() + " " + method.getReturnType() + " "
                        + method + " throws=" + method.getThrownTypes() + " varargs=" + method.isVarArgs());
                for (TypeParameterElement parameter : method.getTypeParameters())
                    record(type.getQualifiedName() + " " + method + " bound " + parameter + "=" + parameter.getBounds());
            } else if (member instanceof VariableElement field) {
                record(type.getQualifiedName() + " " + member.getModifiers() + " " + field.asType() + " " + field.getSimpleName()
                        + (field.getConstantValue() == null ? "" : "=" + field.getConstantValue()));
            }
        }
    }

    private static void record(String signature) {
        if (signature.contains(".internal.")) errors.add("Public API exposes internal type: " + signature);
        lines.add(signature);
    }
}
