import cpw.mods.modlauncher.api.ITransformationService;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import shanghan.early.RuntimeProfile;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Checks the bootstrap JAR's automatic-module service declarations and discovery
 * from a separate SERVICE-style layer. It never initializes a window or launcher.
 * This is a module discovery check, not a complete Forge startup test.
 */
public final class ModuleServiceSmoke {
 public static void main(String[] args) throws Exception {
  if(args.length != 1)throw new AssertionError("Expected the test fixture directory");
  Path.of(args[0]); // Accept the runner's fixture without creating any files.
  var codeSource = RuntimeProfile.class.getProtectionDomain().getCodeSource();
  if(codeSource == null)throw new AssertionError("Bootstrap JAR has no code source");
  Path bootstrap = Path.of(codeSource.getLocation().toURI());
  if(!Files.isRegularFile(bootstrap) || !bootstrap.getFileName().toString().endsWith(".jar"))
   throw new AssertionError("Expected packaged bootstrap JAR, got " + bootstrap);

  ModuleFinder finder = ModuleFinder.of(bootstrap);
  var references = finder.findAll();
  if(references.size() != 1)throw new AssertionError("Expected exactly one bootstrap module");
  ModuleDescriptor descriptor = references.iterator().next().descriptor();
  if(!descriptor.isAutomatic())throw new AssertionError("Bootstrap should remain an automatic module");
  if(!descriptor.name().equals("shanghanearlyboot"))
   throw new AssertionError("Unexpected bootstrap module: " + descriptor.name());
  requireProvides(descriptor, ImmediateWindowProvider.class.getName(), "shanghan.early.RoutingWindowProvider");
  requireProvides(descriptor, ITransformationService.class.getName(), "shanghan.early.BootDiscovery");

  ModuleLayer parent = ModuleLayer.boot();
  Configuration configuration = parent.configuration().resolve(
    finder, ModuleFinder.of(), Set.of(descriptor.name()));
  NativeGuardLoader guard = new NativeGuardLoader(ClassLoader.getSystemClassLoader());
  ModuleLayer serviceLayer = parent.defineModulesWithOneLoader(configuration, guard);
  Module module = serviceLayer.findModule(descriptor.name()).orElseThrow();
  if(module.getLayer() != serviceLayer)throw new AssertionError("Bootstrap did not enter the new module layer");

  ImmediateWindowProvider windowProvider = findWindowProvider(serviceLayer);
  ITransformationService transformationService = findTransformationService(serviceLayer);
  requireLayer(windowProvider.getClass(), serviceLayer);
  requireLayer(transformationService.getClass(), serviceLayer);
  if(!guard.attempts.isEmpty())
   throw new AssertionError("Constructing service providers attempted native graphics loading: " + guard.attempts);
  System.out.println("ModuleServiceSmoke PASS automatic module provides both services; "
    + "SERVICE-style ModuleLayer discovers RoutingWindowProvider and BootDiscovery without SDL/native graphics linkage. "
    + "No provider.initialize or full Forge startup was executed.");
 }

 private static final class NativeGuardLoader extends ClassLoader {
  final java.util.List<String> attempts = new java.util.ArrayList<>();
  NativeGuardLoader(ClassLoader parent) { super(parent); }
  @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
   if(name.startsWith("org.lwjgl.sdl.") || name.equals("org.lwjgl.system.Library")
     || name.equals("org.lwjgl.system.JNI") || name.equals("org.lwjgl.glfw.GLFW")
     || name.equals("org.lwjgl.opengl.GL")) {
    attempts.add(name);
    throw new ClassNotFoundException("Native graphics classes are prohibited during service construction: " + name);
   }
   return super.loadClass(name, resolve);
  }
 }

 private static void requireProvides(ModuleDescriptor descriptor, String service, String implementation) {
  var declaration = descriptor.provides().stream()
    .filter(value -> value.service().equals(service)).findFirst().orElseThrow(
      () -> new AssertionError("Missing module provides declaration for " + service));
  if(!declaration.providers().equals(List.of(implementation)))
   throw new AssertionError("Unexpected provider declaration for " + service + ": " + declaration.providers());
 }

 private static ImmediateWindowProvider findWindowProvider(ModuleLayer layer) {
  for(var declaration : ServiceLoader.load(layer, ImmediateWindowProvider.class).stream().toList()) {
   if(declaration.type().getName().equals("shanghan.early.RoutingWindowProvider")) {
    ImmediateWindowProvider provider = declaration.get();
    if(!provider.name().equals("shanghanearlyloading"))
     throw new AssertionError("Unexpected loading provider name");
    return provider;
   }
  }
  throw new AssertionError("ModuleLayer ServiceLoader did not find the loading provider");
 }

 private static ITransformationService findTransformationService(ModuleLayer layer) {
  for(var declaration : ServiceLoader.load(layer, ITransformationService.class).stream().toList()) {
   if(declaration.type().getName().equals("shanghan.early.BootDiscovery")) {
    ITransformationService provider = declaration.get();
    if(!provider.name().equals("shanghanearlyboot"))
     throw new AssertionError("Unexpected transformation service name");
    return provider;
   }
  }
  throw new AssertionError("ModuleLayer ServiceLoader did not find BootDiscovery");
 }

 private static void requireLayer(Class<?> implementation, ModuleLayer expected) {
  if(implementation.getModule().getLayer() != expected)
   throw new AssertionError("Provider came from a different module layer: " + implementation);
 }
}
