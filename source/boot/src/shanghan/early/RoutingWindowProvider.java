package shanghan.early;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import shanghan.routing.RuntimeRouter;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
/**
 * Stable service facade. Its JDK-only overloads implement all four reviewed SPI families.
 * No SDL or Minecraft class is resolved before selection.
 */
public final class RoutingWindowProvider implements ImmediateWindowProvider {
 private static volatile boolean initialized;
 private Object delegate;
 private RuntimeRouter.Selection selection;
 private String backendPreference;
 static boolean initialized(){return initialized;}
 public String name(){return "shanghanearlyloading";}
 public Runnable initialize(String[] args){
  initialized=true;
  String[] versions=runtimeVersions(args);
  String mc=versions[0],forge=versions[1];
  selection=RuntimeRouter.select(mc,forge);
  RuntimeRouter.ProbeResult result=RuntimeRouter.probe(selection,getClass().getClassLoader());
  try{
   if(!result.supported())throw new IllegalStateException(result.reason());
   delegate=Class.forName(selection.providerClass(),true,getClass().getClassLoader()).getConstructor().newInstance();
   System.out.println("[LoadingFramework] ROUTE "+selection.id()+" minecraft="+mc+" forge="+forge+" java="+Runtime.version().feature());
  }catch(Throwable failure){
   System.err.println("[LoadingFramework] ROUTE_FALLBACK "+failure);
   delegate=stock();
  }
  if(backendPreference!=null)applyBackend();
  return (Runnable)call("initialize",new Class<?>[]{String[].class},(Object)args);
 }
 private Object stock(){
  try{
   try{return ImmediateWindowProvider.class.getMethod("getFallbackHandler").invoke(null);}
   catch(NoSuchMethodException ignored){return Class.forName("net.minecraftforge.fml.earlydisplay.DisplayWindow").getConstructor().newInstance();}
  }catch(ReflectiveOperationException error){throw new IllegalStateException("Stock Forge loading provider unavailable",error);}
 }
 private Object call(String method,Class<?>[] types,Object... arguments){
  if(delegate==null)throw new IllegalStateException("Loading provider has not initialized");
  try{return apiMethod(method,types).invoke(delegate,arguments);}
  catch(InvocationTargetException error){
   Throwable cause=error.getCause();if(cause instanceof RuntimeException r)throw r;if(cause instanceof Error e)throw e;throw new IllegalStateException(cause);
  }catch(ReflectiveOperationException error){throw new IllegalStateException("Adapter API mismatch: "+method,error);}
 }
 private Method apiMethod(String name,Class<?>... types)throws NoSuchMethodException{
  try{return ImmediateWindowProvider.class.getMethod(name,types);}
  catch(NoSuchMethodException ignored){return delegate.getClass().getMethod(name,types);}
 }
 public ImmediateWindowProvider selectBackend(String backend){
  backendPreference=backend;
  if(delegate!=null)applyBackend();
  return this;
 }
 private void applyBackend(){
  try{
   Object selected=apiMethod("selectBackend",String.class).invoke(delegate,backendPreference);
   if(selected!=null)delegate=selected;
  }catch(NoSuchMethodException ignored){}
  catch(InvocationTargetException error){throw new IllegalStateException("Backend selection failed",error.getCause());}
  catch(ReflectiveOperationException error){throw new IllegalStateException("Backend selection failed",error);}
 }
 static String[] runtimeVersions(String[] args){
  String mc=argument(args,"--fml.mcVersion"),forge=argument(args,"--fml.forgeVersion");
  if(mc.isEmpty()||forge.isEmpty()){
   try{
    Class<?> loader=Class.forName("net.minecraftforge.fml.loading.FMLLoader",false,RoutingWindowProvider.class.getClassLoader());
    Object info=loader.getMethod("versionInfo").invoke(null);
    if(info!=null){
     if(mc.isEmpty())mc=String.valueOf(info.getClass().getMethod("mcVersion").invoke(info));
     if(forge.isEmpty())forge=String.valueOf(info.getClass().getMethod("forgeVersion").invoke(info));
    }
   }catch(ReflectiveOperationException|LinkageError ignored){}
  }
  return new String[]{mc,forge};
 }
 public long setupMinecraftWindow(IntSupplier width,IntSupplier height,Supplier<String> title,LongSupplier monitor){
  return ((Number)call("setupMinecraftWindow",new Class<?>[]{IntSupplier.class,IntSupplier.class,Supplier.class,LongSupplier.class},width,height,title,monitor)).longValue();
 }
 public long setupMinecraftWindow(int width,int height,String title,long flags,Supplier<Object> backend){
  return ((Number)call("setupMinecraftWindow",new Class<?>[]{int.class,int.class,String.class,long.class,Supplier.class},width,height,title,flags,backend)).longValue();
 }
 public long setupMinecraftWindow(int width,int height,String title,Supplier<Object> backend){
  return ((Number)call("setupMinecraftWindow",new Class<?>[]{int.class,int.class,String.class,Supplier.class},width,height,title,backend)).longValue();
 }
 public void updateFramebufferSize(IntConsumer width,IntConsumer height){call("updateFramebufferSize",new Class<?>[]{IntConsumer.class,IntConsumer.class},width,height);}
 public boolean positionWindow(Optional<Object> monitor,IntConsumer w,IntConsumer h,IntConsumer x,IntConsumer y){
  return (Boolean)call("positionWindow",new Class<?>[]{Optional.class,IntConsumer.class,IntConsumer.class,IntConsumer.class,IntConsumer.class},monitor,w,h,x,y);
 }
 @SuppressWarnings("unchecked")
 public <T> Supplier<T> loadingOverlay(Supplier<?> mc,Supplier<?> reload,Consumer<Optional<Throwable>> finish,boolean fade){
  return (Supplier<T>)call("loadingOverlay",new Class<?>[]{Supplier.class,Supplier.class,Consumer.class,boolean.class},mc,reload,finish,fade);
 }
 public void updateModuleReads(ModuleLayer layer){call("updateModuleReads",new Class<?>[]{ModuleLayer.class},layer);}
 public void periodicTick(){call("periodicTick",new Class<?>[0]);}
 public String getGLVersion(){return String.valueOf(call("getGLVersion",new Class<?>[0]));}
 private static String argument(String[] a,String key){for(int i=0;i+1<a.length;i++)if(key.equals(a[i]))return a[i+1];return "";}
}
