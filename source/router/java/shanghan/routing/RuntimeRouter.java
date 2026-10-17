package shanghan.routing;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Selects an adapter family without loading Forge, Minecraft, or native classes.
 * A release number selects the expected family; probe verifies the actual SPI
 * before the caller is allowed to load a family-specific implementation.
 */
public final class RuntimeRouter {
    public static final String GLFW_SUPPLIERS = "forge-glfw-suppliers";
    public static final String GLFW_BACKEND = "forge-glfw-backend";
    public static final String GLFW_BACKEND_SELECT = "forge-glfw-backend-select";
    public static final String SDL = "forge-sdl";

    private static final String PROVIDER = "net.minecraftforge.fml.loading.ImmediateWindowProvider";
    private static final String CLIENT_FACTORY = "shanghan.client.routing.ClientOverlayRouter";
    private static final Map<String, Target> TARGETS = Map.ofEntries(
        Map.entry("1.20.1", new Target(47, 17)),
        Map.entry("1.20.2", new Target(48, 17)),
        Map.entry("1.20.3", new Target(49, 17)),
        Map.entry("1.20.4", new Target(49, 17)),
        Map.entry("1.20.6", new Target(50, 21)),
        Map.entry("1.21", new Target(51, 21)),
        Map.entry("1.21.1", new Target(52, 21)),
        Map.entry("1.21.3", new Target(53, 21)),
        Map.entry("1.21.4", new Target(54, 21)),
        Map.entry("1.21.5", new Target(55, 21)),
        Map.entry("1.21.6", new Target(56, 21)),
        Map.entry("1.21.7", new Target(57, 21)),
        Map.entry("1.21.8", new Target(58, 21)),
        Map.entry("1.21.9", new Target(59, 21)),
        Map.entry("1.21.10", new Target(60, 21)),
        Map.entry("1.21.11", new Target(61, 21)),
        Map.entry("26.1", new Target(62, 25)),
        Map.entry("26.1.1", new Target(63, 25)),
        Map.entry("26.1.2", new Target(64, 25)),
        Map.entry("26.2", new Target(65, 25)),
        Map.entry("26.3", new Target(66, 25))
    );

    private RuntimeRouter() {}

    public record Selection(String id, String providerClass, String clientFactoryClass,
                            String minecraft, String forge, int minJava) {}
    public record ProbeResult(boolean supported, String reason) {}
    private record Target(int forgeMajor, int minJava) {}

    /**
     * A pure selection function. Returns null for an unknown or inconsistent
     * Minecraft/Forge pair. All numeric patch builds of a known pair use the same
     * family and still require a successful probe before activation.
     */
    public static Selection select(String minecraft, String forge) {
        if (minecraft == null || forge == null) return null;
        String mc = minecraft.trim();
        String build = forge.trim();
        Target target = TARGETS.get(mc);
        if (target == null || build.isEmpty() || build.length() > 128) return null;
        if (build.startsWith(mc + "-")) build = build.substring(mc.length() + 1);
        if (!build.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9][A-Za-z0-9._+-]*)?")) return null;
        int major;
        try {
            major = Integer.parseInt(build.substring(0, build.indexOf('.')));
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (major != target.forgeMajor()) return null;
        String id;
        String implementation;
        if (major <= 61) {
            id = GLFW_SUPPLIERS;
            implementation = "shanghan.early.ShangHanProvider";
        } else if (major <= 64) {
            id = GLFW_BACKEND;
            implementation = "shanghan.routing.BackendGlfwProvider";
        } else if (major == 65) {
            id = GLFW_BACKEND_SELECT;
            implementation = "shanghan.routing.BackendGlfwProvider";
        } else {
            id = SDL;
            implementation = "shanghan.modern.boot.SdlWindowProvider";
        }
        return new Selection(id, implementation, CLIENT_FACTORY, mc, build, target.minJava());
    }

    /**
     * Checks public method descriptors only. Classes are loaded without class
     * initialization; this method never creates a window or calls native APIs.
     * Missing classes, incompatible methods and linkage failures are reported to
     * the caller so it can retain the standard Forge loading window.
     */
    public static ProbeResult probe(Selection selection, ClassLoader loader) {
        if (selection == null) return unsupported("Unknown Minecraft/Forge pair");
        Selection canonical = select(selection.minecraft(), selection.forge());
        if (!selection.equals(canonical)) return unsupported("Invalid adapter selection");
        if (loader == null) return unsupported("Missing Forge class loader");
        try {
            Class<?> api = Class.forName(PROVIDER, false, loader);
            if (!api.isInterface()) return unsupported("ImmediateWindowProvider is not an interface");
            require(api, "name", String.class);
            require(api, "initialize", Runnable.class, String[].class);
            require(api, "updateFramebufferSize", void.class, IntConsumer.class, IntConsumer.class);
            require(api, "positionWindow", boolean.class, Optional.class, IntConsumer.class,
                IntConsumer.class, IntConsumer.class, IntConsumer.class);
            require(api, "loadingOverlay", Supplier.class, Supplier.class, Supplier.class,
                Consumer.class, boolean.class);
            require(api, "updateModuleReads", void.class, ModuleLayer.class);
            require(api, "periodicTick", void.class);
            require(api, "getGLVersion", String.class);
            switch (selection.id()) {
                case GLFW_SUPPLIERS ->
                    require(api, "setupMinecraftWindow", long.class, IntSupplier.class,
                        IntSupplier.class, Supplier.class, LongSupplier.class);
                case GLFW_BACKEND ->
                    require(api, "setupMinecraftWindow", long.class, int.class, int.class,
                        String.class, long.class, Supplier.class);
                case GLFW_BACKEND_SELECT -> {
                    require(api, "setupMinecraftWindow", long.class, int.class, int.class,
                        String.class, long.class, Supplier.class);
                    requireBackendSelection(api);
                }
                case SDL -> {
                    require(api, "setupMinecraftWindow", long.class, int.class, int.class,
                        String.class, Supplier.class);
                    requireBackendSelection(api);
                    Class.forName("org.lwjgl.sdl.SDLVideo", false, loader);
                }
                default -> {
                    return unsupported("Unknown adapter family");
                }
            }
            int currentJava = Runtime.version().feature();
            if (currentJava < selection.minJava())
                return unsupported("Java " + selection.minJava() + " required; running Java " + currentJava);
            return new ProbeResult(true, "Compatible " + selection.id() + " API");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            String detail = failure.getMessage();
            return unsupported("Incompatible early-window API: " + failure.getClass().getSimpleName()
                + (detail == null || detail.isBlank() ? "" : ": " + detail));
        }
    }

    private static ProbeResult unsupported(String reason) {
        return new ProbeResult(false, reason);
    }

    private static Method require(Class<?> owner, String name, Class<?> result, Class<?>... arguments)
            throws NoSuchMethodException {
        Method method = owner.getMethod(name, arguments);
        if (method.getReturnType() != result || Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(owner.getName() + "." + name + " has an incompatible descriptor");
        return method;
    }

    private static void requireBackendSelection(Class<?> api) throws NoSuchMethodException {
        require(api, "selectBackend", api, String.class);
        Method fallback = api.getMethod("getFallbackHandler");
        if (fallback.getReturnType() != api || !Modifier.isStatic(fallback.getModifiers()))
            throw new NoSuchMethodException(api.getName() + ".getFallbackHandler has an incompatible descriptor");
    }
}
