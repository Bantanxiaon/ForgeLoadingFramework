package shanghan.early;
import cpw.mods.modlauncher.api.*;
import net.minecraftforge.fml.loading.*;
import java.util.*;
import java.util.regex.*;
/** One Java-17 service entry; provider implementations are loaded only after version and ABI probing. */
public final class BootDiscovery implements ITransformationService {
 public String name(){return "shanghanearlyboot";}
 public void onLoad(IEnvironment environment,Set<String> services){}
 public void initialize(IEnvironment environment){
  if(RoutingWindowProvider.initialized())return;
  String target=environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("");
  if(!Set.of("forgeclient","forgeclientuserdev","forgeclientdev","forge_client","forge_client_dev","forge_client_userdev").contains(target))return;
  if(!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)
    || !"shanghanearlyloading".equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER)))return;
  Thread thread=Thread.currentThread();ClassLoader previous=thread.getContextClassLoader();
  try{
   String[] args=launchArguments(environment,System.getProperty("sun.java.command",""));
   if(!RuntimeProfile.supports(argument(args,"--fml.mcVersion"),argument(args,"--fml.forgeVersion"))){
    System.err.println("[LoadingFramework] No installed adapter for the detected runtime; stock Forge loading retained.");return;
   }
   thread.setContextClassLoader(getClass().getClassLoader());
   ImmediateWindowHandler.load(target,args);
   if(!RoutingWindowProvider.initialized())throw new IllegalStateException("Routing provider was not visible from the SERVICE layer");
   System.out.println("[LoadingFramework] SERVICE_PROVIDER_READY version="+RuntimeProfile.version());
  }catch(Throwable error){
   System.err.println("[LoadingFramework] Service activation failed: "+error);
   error.printStackTrace();
  }finally{thread.setContextClassLoader(previous);}
 }
 static String[] launchArguments(IEnvironment environment,String command){
  List<String> result=new ArrayList<>();
  environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).ifPresent(path->{result.add("--gameDir");result.add(path.toString());});
  environment.getProperty(IEnvironment.Keys.VERSION.get()).ifPresent(version->{result.add("--version");result.add(version);});
  Map<String,String> versions=new LinkedHashMap<>();
  for(String name:List.of("--fml.mcVersion","--fml.forgeVersion")){
   Matcher matcher=Pattern.compile("(?:^|\\s)"+Pattern.quote(name)+"\\s+\\\"?([0-9]+(?:\\.[0-9]+)+)\\\"?(?=\\s|$)").matcher(command);
   if(matcher.find())versions.put(name,matcher.group(1));
  }
  // Forge 66 no longer passes --fml.* arguments. VersionInfo is official public metadata.
  if(versions.size()<2){
   try{
    Class<?> loader=Class.forName("net.minecraftforge.fml.loading.FMLLoader",false,BootDiscovery.class.getClassLoader());
    Object info=loader.getMethod("versionInfo").invoke(null);
    if(info!=null){
     versions.putIfAbsent("--fml.mcVersion",String.valueOf(info.getClass().getMethod("mcVersion").invoke(info)));
     versions.putIfAbsent("--fml.forgeVersion",String.valueOf(info.getClass().getMethod("forgeVersion").invoke(info)));
    }
   }catch(ReflectiveOperationException|LinkageError ignored){}
  }
  for(var entry:versions.entrySet()){result.add(entry.getKey());result.add(entry.getValue());}
  return result.toArray(String[]::new);
 }
 private static String argument(String[] args,String name){for(int i=0;i+1<args.length;i++)if(name.equals(args[i]))return args[i+1];return "";}
 public List<ITransformer> transformers(){return List.of();}
}
