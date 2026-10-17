import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import shanghan.modern.boot.SdlWindowProvider;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.*;

public final class ModernSdlApiSmoke {
 public static void main(String[] args) throws Exception {
  Class<?> provider=SdlWindowProvider.class;
  if(!ImmediateWindowProvider.class.isAssignableFrom(provider))
   throw new AssertionError("SDL provider lost the shared Forge provider ABI");
  for(Method method:ImmediateWindowProvider.class.getMethods()) {
   if(!Modifier.isAbstract(method.getModifiers()))continue;
   Method implementation=provider.getMethod(method.getName(),method.getParameterTypes());
   if(Modifier.isAbstract(implementation.getModifiers())
     ||!method.getReturnType().isAssignableFrom(implementation.getReturnType()))
    throw new AssertionError("Unimplemented active Forge provider method: "+method);
  }
  if(!SdlWindowProvider.create().name().equals("shanghanearlyloading"))
   throw new AssertionError("SDL service construction changed its provider name");
  provider.getMethod("setupMinecraftWindow",IntSupplier.class,IntSupplier.class,Supplier.class,LongSupplier.class);
  provider.getMethod("setupMinecraftWindow",int.class,int.class,String.class,Supplier.class);
  Method backend=provider.getMethod("selectBackend",String.class);
  if(backend.getReturnType()!=ImmediateWindowProvider.class)throw new AssertionError("Backend ABI differs");
  ClassLoader loader=provider.getClassLoader();
  Class<?> sdl=Class.forName("org.lwjgl.sdl.SDLVideo",false,loader);
  sdl.getMethod("SDL_CreateWindow",CharSequence.class,int.class,int.class,long.class);
  sdl.getMethod("SDL_GL_CreateContext",long.class);
  sdl.getMethod("SDL_GL_MakeCurrent",long.class,long.class);
  sdl.getMethod("SDL_GL_DestroyContext",long.class);
  sdl.getMethod("SDL_GetWindowProperties",long.class);
  sdl.getMethod("SDL_DestroyWindow",long.class);
  Class<?> properties=Class.forName("org.lwjgl.sdl.SDLProperties",false,loader);
  properties.getMethod("SDL_GetPointerProperty",int.class,CharSequence.class,long.class);
  System.out.println("ModernSdlApiSmoke PASS isolated SDL adapter and both provider handoff ABIs link "
    +"against official LWJGL 3.4.3; no provider.initialize or native window was executed.");
 }
}
