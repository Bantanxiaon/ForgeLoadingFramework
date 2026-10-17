import shanghan.routing.RuntimeRouter;
import shanghan.routing.RuntimeRouter.ProbeResult;
import shanghan.routing.RuntimeRouter.Selection;
import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.util.*;

/** Router contracts and ABI checks without Forge imports or native calls. */
public final class RuntimeRouterSmoke {
    private static final String API = "net.minecraftforge.fml.loading.ImmediateWindowProvider";

    public static void main(String[] args) throws Exception {
        String[][] official = {
            {"1.20.1", "47.4.28", "17"}, {"1.20.2", "48.1.0", "17"},
            {"1.20.3", "49.0.2", "17"}, {"1.20.4", "49.2.13", "17"},
            {"1.20.6", "50.2.14", "21"}, {"1.21", "51.0.33", "21"},
            {"1.21.1", "52.1.20", "21"}, {"1.21.3", "53.1.16", "21"},
            {"1.21.4", "54.1.22", "21"}, {"1.21.5", "55.1.18", "21"},
            {"1.21.6", "56.0.9", "21"}, {"1.21.7", "57.0.3", "21"},
            {"1.21.8", "58.1.26", "21"}, {"1.21.9", "59.0.5", "21"},
            {"1.21.10", "60.1.18", "21"}, {"1.21.11", "61.2.4", "21"},
            {"26.1", "62.0.9", "25"}, {"26.1.1", "63.0.2", "25"},
            {"26.1.2", "64.1.5", "25"}, {"26.2", "65.1.5", "25"},
            {"26.3", "66.0.12", "25"}
        };
        for (String[] row : official) {
            Selection selection = RuntimeRouter.select(row[0], row[1]);
            check(selection != null, "Official version not selected: " + Arrays.toString(row));
            check(selection.minJava() == Integer.parseInt(row[2]), "Wrong Java requirement");
            check(selection.clientFactoryClass().equals("shanghan.client.routing.ClientOverlayRouter"),
                "Client factory must be stable");
        }
        Selection old = RuntimeRouter.select("1.20.1", "47.4.6");
        for (String build : List.of("47.4.6", "47.4.16", "47.4.20", "47.99.1")) {
            Selection selected = RuntimeRouter.select("1.20.1", build);
            check(selected != null && selected.id().equals(old.id())
                && selected.providerClass().equals(old.providerClass()), "Patch whitelist survived: " + build);
        }
        check(RuntimeRouter.select("1.20.1", "1.20.1-47.4.20").forge().equals("47.4.20"),
            "Full matching Maven version was not normalized");
        for (String[] invalid : new String[][] {
            {"1.20.1", "48.1.0"}, {"1.20.4", "47.4.20"}, {"1.20.5", "50.2.14"},
            {"1.21.2", "53.1.16"}, {"26.3", "67.0.0"}, {"26.4", "67.0.0"},
            {"26.1", "63.0.2"}, {"26.1.1", "62.0.9"}, {"1.20.1", "1.20.4-47.4.20"},
            {"1.20.1", ""}, {"1.20.1", "47.4"}, {"1.20.1", "47.4.x"},
            {"1.20.1", "999999999999999999999.1.1"}, {null, "47.4.20"},
            {"1.20.1", null}
        }) check(RuntimeRouter.select(invalid[0], invalid[1]) == null,
            "Invalid pair accepted: " + Arrays.toString(invalid));

        ClassLoader supplier = fixture("supplier", false, true);
        ClassLoader backend = fixture("backend", false, true);
        ClassLoader selectable = fixture("backend", true, true);
        ClassLoader sdl = fixture("sdl", true, true);
        check(RuntimeRouter.probe(old, supplier).supported(), "Valid legacy SPI rejected");
        check(!RuntimeRouter.probe(old, backend).supported(), "Wrong legacy descriptor accepted");
        check(!RuntimeRouter.probe(null, supplier).supported(), "Null selection accepted");
        check(!RuntimeRouter.probe(old, null).supported(), "Null loader accepted");
        check(!RuntimeRouter.probe(old, new ClassLoader(ClassLoader.getPlatformClassLoader()) {}).supported(),
            "Missing Forge SPI accepted");
        check(!RuntimeRouter.probe(old, fixture("bad-return", false, true)).supported(),
            "Incorrect setup return type accepted");

        Selection firstBackend = RuntimeRouter.select("26.1", "62.0.9");
        expectJavaBound(firstBackend, backend);
        Selection selectedBackend = RuntimeRouter.select("26.2", "65.1.5");
        expectJavaBound(selectedBackend, selectable);
        ProbeResult missingSelector = RuntimeRouter.probe(selectedBackend, backend);
        check(!missingSelector.supported() && missingSelector.reason().contains("selectBackend"),
            "Missing backend selection method was not identified");
        Selection modern = RuntimeRouter.select("26.3", "66.0.12");
        expectJavaBound(modern, sdl);
        ProbeResult missingSdl = RuntimeRouter.probe(modern, fixture("sdl", true, false));
        check(!missingSdl.supported() && missingSdl.reason().contains("SDLVideo"),
            "Missing SDL class was not identified");
        check(!RuntimeRouter.probe(modern, selectable).supported(), "GLFW five-argument ABI treated as SDL");
        check(!RuntimeRouter.probe(firstBackend, sdl).supported(), "SDL four-argument ABI treated as GLFW");

        Selection altered = new Selection(old.id(), "untrusted.OtherProvider", old.clientFactoryClass(),
            old.minecraft(), old.forge(), old.minJava());
        check(!RuntimeRouter.probe(altered, supplier).supported(), "Altered adapter selection accepted");
        System.out.println("RuntimeRouterSmoke PASS 21 official pairs, future patch routing, invalid pairs, "
            + "three setup descriptors, backend selection, missing SDL, Java requirements, no native initialization");
    }

    private static void expectJavaBound(Selection selection, ClassLoader loader) {
        ProbeResult result = RuntimeRouter.probe(selection, loader);
        boolean enoughJava = Runtime.version().feature() >= selection.minJava();
        check(result.supported() == enoughJava, selection.id() + ": " + result.reason());
        if (!enoughJava) check(result.reason().startsWith("Java " + selection.minJava() + " required"),
            "Wrong Java failure: " + result.reason());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ClassLoader fixture(String shape, boolean selector, boolean sdl) throws IOException {
        String method = switch (shape) {
            case "supplier" -> "long setupMinecraftWindow(IntSupplier w, IntSupplier h, Supplier<String> t, LongSupplier m);";
            case "backend" -> "long setupMinecraftWindow(int w, int h, String t, long m, Supplier<Object> b);";
            case "sdl" -> "long setupMinecraftWindow(int w, int h, String t, Supplier<Object> b);";
            case "bad-return" -> "int setupMinecraftWindow(IntSupplier w, IntSupplier h, Supplier<String> t, LongSupplier m);";
            default -> throw new IllegalArgumentException(shape);
        };
        String methods = """
            String name();
            Runnable initialize(String[] args);
            void updateFramebufferSize(IntConsumer w, IntConsumer h);
            boolean positionWindow(Optional<Object> m, IntConsumer w, IntConsumer h, IntConsumer x, IntConsumer y);
            <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade);
            void updateModuleReads(ModuleLayer layer);
            void periodicTick();
            String getGLVersion();
            """;
        String selection = selector ? """
            default ImmediateWindowProvider selectBackend(String backend) { return this; }
            static ImmediateWindowProvider getFallbackHandler() { throw new AssertionError("Must not invoke fallback"); }
            """ : "";
        String source = "package net.minecraftforge.fml.loading; import java.util.*; import java.util.function.*; "
            + "public interface ImmediateWindowProvider { " + methods + method + selection
            + "Object INIT_TRAP = fail(); static Object fail() { throw new AssertionError(\"SPI initialized\"); } }";
        List<JavaFileObject> inputs = new ArrayList<>();
        inputs.add(new Source(API, source));
        if (sdl) inputs.add(new Source("org.lwjgl.sdl.SDLVideo", """
            package org.lwjgl.sdl;
            public final class SDLVideo {
                static { if (true) throw new AssertionError("Native class initialized"); }
            }
            """));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check(compiler != null, "RuntimeRouterSmoke needs a JDK");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, null);
             MemoryFiles files = new MemoryFiles(standard)) {
            boolean success = compiler.getTask(null, files, diagnostics,
                List.of("--release", "17", "-proc:none", "-classpath", "", "-sourcepath", ""), null, inputs).call();
            check(success, "Fixture compilation failed: " + diagnostics.getDiagnostics());
            Map<String, byte[]> bytecode = new HashMap<>();
            files.outputs.forEach((name, file) -> bytecode.put(name, file.bytes.toByteArray()));
            return new ClassLoader(ClassLoader.getPlatformClassLoader()) {
                @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                    byte[] bytes = bytecode.get(name);
                    if (bytes == null) throw new ClassNotFoundException(name);
                    return defineClass(name, bytes, 0, bytes.length);
                }
            };
        }
    }

    private static final class Source extends SimpleJavaFileObject {
        private final String source;
        Source(String name, String source) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
    }
    private static final class Bytes extends SimpleJavaFileObject {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Bytes(String name, Kind kind) {
            super(URI.create("memory:///" + name.replace('.', '/') + kind.extension), kind);
        }
        @Override public OutputStream openOutputStream() { return bytes; }
    }
    private static final class MemoryFiles extends ForwardingJavaFileManager<StandardJavaFileManager> {
        final Map<String, Bytes> outputs = new HashMap<>();
        MemoryFiles(StandardJavaFileManager delegate) { super(delegate); }
        @Override public JavaFileObject getJavaFileForOutput(Location location, String name,
                JavaFileObject.Kind kind, FileObject sibling) {
            Bytes output = new Bytes(name, kind);
            outputs.put(name, output);
            return output;
        }
    }
}
