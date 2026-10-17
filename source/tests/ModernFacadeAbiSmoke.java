import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import shanghan.early.RoutingWindowProvider;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Runs against the real Forge 66 API and the published bootstrap service JAR. */
public final class ModernFacadeAbiSmoke {
 public static void main(String[] args) throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("Expected fixture directory");
  Path fixture=Path.of(args[0]);Files.createDirectories(fixture.resolve("loading"));
  Files.writeString(fixture.resolve("loading/config.json"),"{\"enabled\":false}");
  ClassLoader loader=RoutingWindowProvider.class.getClassLoader();
  Class<?> api=ImmediateWindowProvider.class;
  String apiSource=String.valueOf(api.getProtectionDomain().getCodeSource().getLocation());
  if(!apiSource.contains("fmlloader-26.3-66.0.12.jar"))
   throw new AssertionError("Expected the real official Forge 66 ABI, got "+apiSource);
  Class<?> facade=RoutingWindowProvider.class;
  String facadeSource=String.valueOf(facade.getProtectionDomain().getCodeSource().getLocation());
  if(!facadeSource.endsWith(".jar"))throw new AssertionError("Facade is not loaded from the published JAR");
  for(Method method:api.getMethods()) {
   if(Modifier.isStatic(method.getModifiers()))continue;
   Method implementation=facade.getMethod(method.getName(),method.getParameterTypes());
   if(implementation.getReturnType()!=method.getReturnType()||Modifier.isAbstract(implementation.getModifiers()))
    throw new AssertionError("Service facade does not implement the active descriptor: "+method);
   if(method.getName().equals("selectBackend")&&implementation.getDeclaringClass()!=facade)
    throw new AssertionError("The new default selectBackend bypasses the facade");
  }
  Class<?> paths=Class.forName("net.minecraftforge.fml.loading.FMLPaths",false,loader);
  paths.getMethod("loadAbsolutePaths",Path.class).invoke(null,fixture);
  Method versions=facade.getDeclaredMethod("runtimeVersions",String[].class);versions.setAccessible(true);
  String[] detected=(String[])versions.invoke(null,(Object)new String[]{"--launchTarget","forge_client"});
  if(!Arrays.equals(detected,new String[]{"26.3","66.0.12"}))
   throw new AssertionError("No-argument official version detection failed: "+Arrays.toString(detected));
  ImmediateWindowProvider service=ServiceLoader.load(ImmediateWindowProvider.class).stream()
    .filter(candidate->candidate.type()==facade).findFirst().orElseThrow(
      ()->new AssertionError("Packaged service declaration did not discover the routing facade")).get();
  Object selected=api.getMethod("selectBackend",String.class).invoke(service,"vulkan");
  if(selected!=service)throw new AssertionError("Interface backend selection did not return the facade");
  Runnable tick=service.initialize(new String[]{"--launchTarget","forge_client","--gameDir",fixture.toString()});
  tick.run();
  Field route=facade.getDeclaredField("selection");route.setAccessible(true);
  Object selection=route.get(service);
  String id=String.valueOf(selection.getClass().getMethod("id").invoke(selection));
  if(!id.equals("forge-sdl"))throw new AssertionError("The actual service facade selected "+id);
  Field delegateField=facade.getDeclaredField("delegate");delegateField.setAccessible(true);
  Object delegate=delegateField.get(service);
  if(!delegate.getClass().getName().equals("shanghan.modern.boot.SdlWindowProvider"))
   throw new AssertionError("Facade selected the wrong concrete adapter: "+delegate);
  Field preference=delegate.getClass().getDeclaredField("backendPreference");preference.setAccessible(true);
  if(!"vulkan".equals(preference.get(delegate)))
   throw new AssertionError("The pre-initialize interface backend preference did not reach the adapter");
  for(String name:List.of("window","context","gameWindow")){
   Field field=delegate.getClass().getDeclaredField(name);field.setAccessible(true);
   if(field.getLong(delegate)!=0)throw new AssertionError("Disabled test theme created a native window/context");
  }
  // The public facade must also dispatch through Forge's package-private stock provider.
  ImmediateWindowProvider unknown=(ImmediateWindowProvider)facade.getConstructor().newInstance();
  api.getMethod("selectBackend",String.class).invoke(unknown,"opengl");
  unknown.initialize(new String[]{"--fml.mcVersion","99.0","--fml.forgeVersion","999.0.0"}).run();
  Object stock=delegateField.get(unknown);
  if(!stock.getClass().getName().contains("ImmediateWindowHandler$DummyProvider"))
   throw new AssertionError("Unknown runtimes did not retain the official stock provider");
  unknown.periodicTick();
  if(unknown.getGLVersion()==null)throw new AssertionError("Stock provider public-interface dispatch failed");
  System.out.println("ModernFacadeAbiSmoke PASS packaged routing service implements the real Forge 66 ABI, "
    +"overrides default selectBackend with actual interface dispatch, detects absent --fml versions from "
    +"official VersionInfo, selects the SDL delegate without changing Vulkan, and calls the package-private "
    +"stock fallback through its public interface. Theme disabled; no window initialization occurred.");
 }
}
