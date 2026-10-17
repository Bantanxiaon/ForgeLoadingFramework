import shanghan.routing.BackendGlfwProvider;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window;
import static org.lwjgl.opengl.GL32C.*;

/** Calls the production adapter's real factory against installed Minecraft 26.2. */
public final class ModernGlfwBackendNativeSmoke {
 public static void main(String[] args) throws Exception {
  ClassLoader loader=BackendGlfwProvider.class.getClassLoader();
  Class<?> backendApi=Class.forName("com.mojang.blaze3d.systems.GpuBackend",false,loader);
  Class<?> backendClass=Class.forName("com.mojang.blaze3d.opengl.GlBackend",false,loader);
  if(Arrays.stream(backendApi.getMethods()).anyMatch(method->method.getName().equals("createWindow")))
   throw new AssertionError("Wrong backend ABI: this regression requires the GLFW hints-based API");
  Class<?> windowApi=Class.forName("com.mojang.blaze3d.platform.Window",false,loader);
  Method vanilla=windowApi.getMethod("createGlfwWindow",int.class,int.class,String.class,long.class,backendApi);
  if(!Modifier.isStatic(vanilla.getModifiers())||vanilla.getReturnType()!=long.class)
   throw new AssertionError("Public Minecraft GLFW factory changed");
  String gameSource=String.valueOf(windowApi.getProtectionDomain().getCodeSource().getLocation());
  if(!gameSource.endsWith("/26.2/26.2.jar"))throw new AssertionError("Wrong actual Minecraft dependency: "+gameSource);
  Class<?> api=Class.forName("net.minecraftforge.fml.loading.ImmediateWindowProvider",false,loader);
  if(!String.valueOf(api.getProtectionDomain().getCodeSource().getLocation()).contains("26.2-65.1.5"))
   throw new AssertionError("Expected the real Forge 65 window interface");
  if(BackendGlfwProvider.class.getMethod("selectBackend",String.class).getReturnType()!=api)
   throw new AssertionError("Backend selection does not implement the active default method");
  api.getMethod("setupMinecraftWindow",int.class,int.class,String.class,long.class,java.util.function.Supplier.class);
  Object backend=backendClass.getConstructor().newInstance();
  Method create=BackendGlfwProvider.class.getDeclaredMethod("createWindow",Object.class,String.class,int.class,int.class,long.class);
  create.setAccessible(true);
  long window=0;
  try {
   if(!glfwInit())throw new AssertionError("GLFW initialization failed");
   glfwDefaultWindowHints();
   window=((Number)create.invoke(null,backend,"Forge Loading Framework GLFW regression",640,360,0L)).longValue();
   if(window==0||glfwGetWin32Window(window)==0)throw new AssertionError("The factory did not return a GLFW/Win32 window");
   if(glfwGetWindowMonitor(window)!=0)throw new AssertionError("Monitor zero became a flags or window handle");
   if(glfwGetWindowAttrib(window,GLFW_CLIENT_API)!=GLFW_OPENGL_API)
    throw new AssertionError("The selected OpenGL backend's window hints were not applied");
   glfwMakeContextCurrent(window);GL.createCapabilities();
   try(MemoryStack stack=MemoryStack.stackPush()){
    var width=stack.mallocInt(1);var height=stack.mallocInt(1);glfwGetFramebufferSize(window,width,height);
    if(width.get(0)!=640||height.get(0)!=360)throw new AssertionError("Factory changed the requested dimensions");
    glViewport(0,0,width.get(0),height.get(0));glClearColor(.1f,.8f,.2f,1);glClear(GL_COLOR_BUFFER_BIT);
    ByteBuffer pixel=stack.malloc(4);glReadPixels(320,180,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
    if((pixel.get(1)&255)<150||(pixel.get(0)&255)>100)
     throw new AssertionError("The actual backend-created OpenGL framebuffer did not render expected pixels");
    glfwSwapBuffers(window);
   }
  } finally {
   glfwMakeContextCurrent(0);GL.setCapabilities(null);
   if(window!=0)glfwDestroyWindow(window);
   glfwTerminate();
  }
  System.out.println("ModernGlfwBackendNativeSmoke PASS production adapter used the public Minecraft 26.2 "
    +"Window.createGlfwWindow factory with the actual Forge 65 ABI and actual GlBackend; "
    +"monitor/dimensions/backend hints, owned rendered pixels and native window teardown verified.");
 }
}
