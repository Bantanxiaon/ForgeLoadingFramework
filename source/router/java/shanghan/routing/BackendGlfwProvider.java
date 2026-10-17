package shanghan.routing;
import shanghan.early.*;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import java.lang.reflect.*;
import java.util.function.Supplier;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
/** Forge 62-65: Minecraft creates its own selected backend window; video owns a separate GL drawable. */
public final class BackendGlfwProvider extends ShangHanProvider {
 public net.minecraftforge.fml.loading.ImmediateWindowProvider selectBackend(String backend){return this;}
 public long setupMinecraftWindow(int width,int height,String title,long monitor,Supplier<Object> backend){
  if(fallback!=null)return invokeWindow(fallback,width,height,title,monitor,backend);
  Object graphics=backend.get();
  long created=createWindow(graphics,title,width,height,monitor);
  if(created==0)throw new IllegalStateException("Minecraft backend returned no window");
  gameWindow=created;
  if(!detached){
   stopMediaForStock();glfwDestroyWindow(window);window=0;return created;
  }
  contextLock.lock();
  try{
   media.nativeWindowFade=true;enableNativeFade();
   glfwSetWindowTitle(gameWindow,media.settings.title);
   glfwShowWindow(gameWindow);glfwPollEvents();handingOff=true;
   media.log("WINDOW_HANDOFF embedded=true topLevelWindows=1 backend="+graphics.getClass().getName());
   return gameWindow;
  }catch(Throwable error){
   media.log("BACKEND_EMBEDDING_FAILED "+error);
   if(nativeSurface!=null){nativeSurface.rollback();nativeSurface=null;}
   stopMediaForStock();if(window!=0){glfwDestroyWindow(window);window=0;}
   return gameWindow;
  }finally{contextLock.unlock();}
 }
 public long setupMinecraftWindow(int width,int height,String title,Supplier<Object> backend){
  return setupMinecraftWindow(width,height,title,0,backend);
 }
 private static long createWindow(Object backend,String title,int width,int height,long monitor){
  try{
   if(backend==null)throw new IllegalArgumentException("Minecraft selected no GLFW graphics backend");
   ClassLoader loader=backend.getClass().getClassLoader();
   Class<?> api=Class.forName("com.mojang.blaze3d.systems.GpuBackend",false,loader);
   if(!api.isInstance(backend))throw new IllegalArgumentException("Not a Minecraft GLFW backend");
   Class<?> window=Class.forName("com.mojang.blaze3d.platform.Window",false,loader);
   Method method=window.getMethod("createGlfwWindow",int.class,int.class,String.class,long.class,api);
   if(!Modifier.isStatic(method.getModifiers())||method.getReturnType()!=long.class)
    throw new NoSuchMethodException("Minecraft GLFW window creation descriptor changed");
   // Forge 62-65 uses Window's public factory, which applies this backend's
   // hints and reports backend-specific creation errors. The long is a monitor.
   return ((Number)method.invoke(null,width,height,title,monitor,backend)).longValue();
  }catch(ReflectiveOperationException error){throw new IllegalStateException("Minecraft backend window API mismatch",error);}
 }
 private static long invokeWindow(Object fallback,int width,int height,String title,long monitor,Supplier<Object> backend){
  try{
   try{return ((Number)ImmediateWindowProvider.class.getMethod("setupMinecraftWindow",int.class,int.class,String.class,long.class,Supplier.class).invoke(fallback,width,height,title,monitor,backend)).longValue();}
   catch(NoSuchMethodException ignored){return ((Number)ImmediateWindowProvider.class.getMethod("setupMinecraftWindow",int.class,int.class,String.class,Supplier.class).invoke(fallback,width,height,title,backend)).longValue();}
  }catch(ReflectiveOperationException error){throw new IllegalStateException("Stock backend window API mismatch",error);}
 }
}
