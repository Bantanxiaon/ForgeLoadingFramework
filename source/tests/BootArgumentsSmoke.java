import shanghan.early.BootDiscovery;
import cpw.mods.modlauncher.api.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
public final class BootArgumentsSmoke {
 public static void main(String[] args)throws Exception{
  Path path=Path.of(args[0]);
  new TypesafeMap(IEnvironment.class);
  IEnvironment env=(IEnvironment)Proxy.newProxyInstance(IEnvironment.class.getClassLoader(),new Class<?>[]{IEnvironment.class},(proxy,method,values)->{
   if(method.getName().equals("getProperty")){
    Object key=values[0];
    if(key==IEnvironment.Keys.GAMEDIR.get())return Optional.of(path);
    if(key==IEnvironment.Keys.VERSION.get())return Optional.of("Closing Song test");
    if(key==IEnvironment.Keys.LAUNCHTARGET.get())return Optional.of("forgeserver");
    return Optional.empty();
   }
   throw new UnsupportedOperationException(method.getName());
  });
  Method parse=BootDiscovery.class.getDeclaredMethod("launchArguments",IEnvironment.class,String.class);parse.setAccessible(true);
  for(String command:List.of("BootstrapLauncher --username tester --accessToken SECRET --fml.forgeVersion 47.3.22 --fml.mcVersion 1.20.1","BootstrapLauncher --fml.mcVersion \"1.20.1\" --fml.forgeVersion \"47.3.33\"")){
   String[] parsed=(String[])parse.invoke(null,env,command);List<String> values=Arrays.asList(parsed);
   if(!values.contains(path.toString())||!values.contains("1.20.1")||(!values.contains("47.3.22")&&!values.contains("47.3.33"))||values.contains("SECRET")||values.contains("tester"))throw new AssertionError(values);
  }
  String[] unknown=(String[])parse.invoke(null,env,"BootstrapLauncher --accessToken SECRET");
  if(Arrays.asList(unknown).contains("--fml.forgeVersion"))throw new AssertionError("Missing version invented");
  IEnvironment beforeArguments=(IEnvironment)Proxy.newProxyInstance(IEnvironment.class.getClassLoader(),new Class<?>[]{IEnvironment.class},(proxy,method,values)->{
   throw new AssertionError("onLoad must not access launch properties before argument processing");
  });
  new BootDiscovery().onLoad(beforeArguments,Set.of());
  new BootDiscovery().initialize(env);
  System.out.println("BootArgumentsSmoke PASS versions, quoted values, private arguments excluded, pre-argument onLoad deferred, server initialization skipped");
 }
}
