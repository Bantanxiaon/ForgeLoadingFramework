package shanghan.modern.boot;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.sdl.SDL_Event;
import shanghan.early.MediaSession;
import shanghan.early.PackName;
import shanghan.early.Settings;
import shanghan.early.VideoRenderer;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLProperties.SDL_GetPointerProperty;
import static org.lwjgl.sdl.SDLVideo.*;

/**
 * Minecraft 26.3 SDL window adapter, isolated from the GLFW provider.
 *
 * Compiled to Java 17 against the older common provider interface. The primitive
 * overload and selectBackend exactly match Forge 66 without introducing Java 25
 * classes into the shared service entry point. No Minecraft types are linked here.
 *
 * On Windows the original video context remains in an embedded SDL drawable.
 * Minecraft creates its own SDL window through its selected GPU backend. The
 * native child fades into that live game window, so no GL calls are made from
 * modern GUI render-state extraction, and Vulkan is never silently changed.
 */
public final class SdlWindowProvider implements ImmediateWindowProvider {
 private final ReentrantLock contextLock=new ReentrantLock();
 private volatile long window,context,gameWindow;
 private volatile int framebufferWidth=640,framebufferHeight=360;
 private volatile boolean splashReleased,usingStock,aborted;
 private volatile NativeFadeSurface surface;
 private volatile String backendPreference="default";
 private String glVersion="3.3";
 private ScheduledExecutorService ticker;
 private ScheduledFuture<?> task;
 private GLCapabilities capabilities;
 private MediaSession media;
 private VideoRenderer renderer;
 private ImmediateWindowProvider stock;
 private Method overlayFactory;
 private long lastProgressPoll;
 private boolean namesResolved;
 private volatile boolean gameLayerReady;
 private volatile Throwable renderFailure;

 public String name(){return "shanghanearlyloading";}
 public static ImmediateWindowProvider create(){return new SdlWindowProvider();}

 /** This method is present only on newer Forge; keep it off the common ABI. */
 public ImmediateWindowProvider selectBackend(String backend) {
  backendPreference=backend==null?"default":backend.toLowerCase(Locale.ROOT);
  if(Platform.get()!=Platform.WINDOWS) {
   System.err.println("[LoadingFramework] SDL video embedding requires Windows; keeping the stock loading screen.");
   return stockProvider();
  }
  if(!List.of("default","opengl","vulkan").contains(backendPreference)) {
   System.err.println("[LoadingFramework] Unrecognized graphics backend; keeping the stock loading screen.");
   return stockProvider();
  }
  return this;
 }

 public Runnable initialize(String[] arguments) {
  Path game=FMLPaths.GAMEDIR.get(),directory=Settings.directory(game);
  Settings settings=Settings.load(directory);
  if(settings.title==null||settings.title.isBlank())settings.title=PackName.resolve(game,arguments);
  if(!settings.enabled||Platform.get()!=Platform.WINDOWS)return fallback(arguments);
  try {
   if(!SDL_Init(SDL_INIT_VIDEO))throw failure("SDL initialization failed");
   int[][] versions={{4,6},{4,5},{4,3},{3,3}};
   for(int[] version:versions) {
    SDL_GL_ResetAttributes();
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION,version[0]);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION,version[1]);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK,SDL_GL_CONTEXT_PROFILE_CORE);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_FLAGS,SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG);
    SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER,1);
    SDL_GL_SetAttribute(SDL_GL_ALPHA_SIZE,0);
    window=SDL_CreateWindow(settings.title,settings.width,settings.height,
      SDL_WINDOW_OPENGL|SDL_WINDOW_HIDDEN|SDL_WINDOW_RESIZABLE|SDL_WINDOW_HIGH_PIXEL_DENSITY);
    if(window==0)continue;
    context=SDL_GL_CreateContext(window);
    if(context!=0){glVersion=version[0]+"."+version[1];break;}
    SDL_DestroyWindow(window);window=0;
   }
   if(window==0||context==0)throw failure("No SDL OpenGL video context");
   require(SDL_GL_MakeCurrent(window,context),"Making video context current");
   capabilities=GL.createCapabilities();SDL_GL_SetSwapInterval(0);
   media=new MediaSession(directory,settings);media.detached=true;
   try{MediaSession.class.getField("nativeWindowFade").setBoolean(media,true);}
   catch(NoSuchFieldException ignored){media.log("NATIVE_FADE_CAPABILITY_MISSING: using the adapter-owned fade");}
   media.showGame=this::showGameWindow;
   media.closeWindow=this::closeSplashWindow;
   media.abortLoading=this::stopMediaForStock;
   media.fitWindow=this::fitSurface;
   renderer=new VideoRenderer(media);media.start();
   refreshFramebuffer();
   renderer.render(framebufferWidth,framebufferHeight,1);
   SDL_GL_SwapWindow(window);
   SDL_ShowWindow(window);
   if(settings.maximized)SDL_MaximizeWindow(window);
   SDL_GL_MakeCurrent(window,0);GL.setCapabilities(null);
   ticker=Executors.newSingleThreadScheduledExecutor(action->{
    Thread thread=new Thread(action,"LoadingFramework-SDL-render");thread.setDaemon(true);return thread;
   });
   task=ticker.scheduleAtFixedRate(this::renderEarly,0,
     1_000_000_000L/settings.fps,TimeUnit.NANOSECONDS);
   media.log("PROVIDER_READY adapter=SDL GL="+glVersion+" targetFPS="+settings.fps
     +" windowOpaque=true gameBackend="+backendPreference);
   return this::periodicTick;
  } catch(Throwable error) {
   System.err.println("[LoadingFramework] SDL video initialization failed; stock loading screen: "+error);
   error.printStackTrace();
   releaseInitialization();
   return fallback(arguments);
  }
 }

 private void renderEarly() {
  if(splashReleased||aborted||renderFailure!=null||!contextLock.tryLock())return;
  try {
   require(SDL_GL_MakeCurrent(window,context),"Making video render context current");
   GL.setCapabilities(capabilities);
   long now=System.nanoTime();
   if(now-lastProgressPoll>=100_000_000L) {
    media.progress.tick();resolveModNames();lastProgressPoll=now;
   }
   glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
   // Native child opacity supplies the fade; its framebuffer always stays opaque.
   renderer.render(framebufferWidth,framebufferHeight,1);
   require(SDL_GL_SwapWindow(window),"Presenting video");
   if(media.opacity()<=0) {
    try{renderer.close();media.log("FADE_RENDER_DONE");}
    finally{
     media.close();splashReleased=true;
     if(task!=null)task.cancel(false);if(ticker!=null)ticker.shutdown();
     media.markRenderReleased();
    }
   }
  } catch(Throwable error) {
   if(renderFailure==null&&media!=null)media.log("EARLY_RENDER_ERROR "+error);
   renderFailure=error;
  } finally {
   if(window!=0)SDL_GL_MakeCurrent(window,0);GL.setCapabilities(null);contextLock.unlock();
  }
 }

 /** No SDL events are removed after Minecraft owns its event loop. */
 public void periodicTick() {
  if(usingStock){stockProvider().periodicTick();return;}
  if(renderFailure!=null){stopMediaForStock();return;}
  if(gameWindow==0&&!aborted) {
   try(MemoryStack stack=MemoryStack.stackPush()) {
    var event=SDL_Event.malloc(stack);
    while(SDL_PollEvent(event)) {
     if(event.type()==SDL_EVENT_QUIT||event.type()==SDL_EVENT_WINDOW_CLOSE_REQUESTED) {
      if(media!=null)media.close();
      throw new IllegalStateException("Loading window closed by user");
     }
    }
   }
   refreshFramebuffer();
  }
  fitSurface();
 }

 /**
  * Legacy signature is intentionally not an SDL implementation. A routing error
  * fails clearly instead of interpreting a GLFW monitor value as an SDL backend.
  */
 @Override public long setupMinecraftWindow(IntSupplier width,IntSupplier height,
     Supplier<String> title,LongSupplier monitor) {
  throw new IllegalStateException("SDL adapter selected for a GLFW window ABI");
 }

 /** Exact Forge 66 primitive-window overload; cannot use @Override with Forge 47. */
 public long setupMinecraftWindow(int width,int height,String title,Supplier<Object> backend) {
  if(usingStock)return stockWindow(width,height,title,backend);
  Object selected=backend.get();
  if(selected==null)throw new IllegalStateException("Minecraft selected no graphics backend");
  try {
   int w=framebufferWidth,h=framebufferHeight,x=0,y=0;
   try(MemoryStack stack=MemoryStack.stackPush()) {
    var a=stack.mallocInt(1);var b=stack.mallocInt(1);
    if(SDL_GetWindowSize(window,a,b)){w=a.get(0);h=b.get(0);}
    if(SDL_GetWindowPosition(window,a,b)){x=a.get(0);y=b.get(0);}
   }
   // Keep the game's selected backend, including Vulkan. It owns its device/context.
   Class<?> backendApi=Class.forName("com.mojang.renderpearl.api.device.GpuBackend",false,selected.getClass().getClassLoader());
   Method create=backendApi.getMethod("createWindow",String.class,int.class,int.class,long.class);
   gameWindow=((Number)create.invoke(selected,title,w,h,
     SDL_WINDOW_HIDDEN|SDL_WINDOW_RESIZABLE|SDL_WINDOW_HIGH_PIXEL_DENSITY)).longValue();
   if(gameWindow==0)throw failure("Minecraft backend could not create its SDL window");
   SDL_SetWindowMinimumSize(gameWindow,320,240);
   SDL_SetWindowPosition(gameWindow,x,y);
   SDL_SetWindowTitle(gameWindow,media.settings.title);
   try {
    NativeFadeSurface embedding=new NativeFadeSurface(hwnd(window),hwnd(gameWindow));
    embedding.attach();surface=embedding;
    SDL_ShowWindow(gameWindow);
    if(media.settings.maximized)SDL_MaximizeWindow(gameWindow);
    fitSurface();
    media.log("WINDOW_HANDOFF adapter=SDL embedded=true topLevelWindows=1 backend="
      +selected.getClass().getName());
   } catch(Throwable embeddingError) {
    media.log("EMBEDDING_FAILED "+embeddingError);
    if(surface!=null){surface.rollback();surface=null;}
    // A valid game window remains; the video is closed before returning it.
    stopMediaForStock();SDL_ShowWindow(gameWindow);
   }
   return gameWindow;
  } catch(Throwable error) {
   stopMediaForStock();
   throw new IllegalStateException("Minecraft SDL window handoff failed",error);
  }
 }

 public void updateFramebufferSize(IntConsumer width,IntConsumer height) {
  if(usingStock){stockProvider().updateFramebufferSize(width,height);return;}
  width.accept(framebufferWidth);height.accept(framebufferHeight);
 }

 public boolean positionWindow(Optional<Object> monitor,IntConsumer width,IntConsumer height,
     IntConsumer x,IntConsumer y) {
  if(usingStock)return stockProvider().positionWindow(monitor,width,height,x,y);
  long handle=gameWindow!=0?gameWindow:window;
  try(MemoryStack stack=MemoryStack.stackPush()) {
   var a=stack.mallocInt(1);var b=stack.mallocInt(1);
   if(SDL_GetWindowSize(handle,a,b)){width.accept(a.get(0));height.accept(b.get(0));}
   if(SDL_GetWindowPosition(handle,a,b)){x.accept(a.get(0));y.accept(b.get(0));}
  }
  return true;
 }

 public void updateModuleReads(ModuleLayer layer) {
  stockProvider().updateModuleReads(layer);
  for(Module module:layer.modules()) {
   getClass().getModule().addReads(module);
   Class<?> factory=Class.forName(module,"shanghan.client.routing.ClientOverlayRouter");
   if(factory==null)continue;
   try {
    overlayFactory=factory.getMethod("create",Supplier.class,Supplier.class,Consumer.class,
      MediaSession.class,VideoRenderer.class);
    if(media!=null)factory.getMethod("installDetails",MediaSession.class).invoke(null,media);
   } catch(Throwable error) {
    if(media!=null)media.log("CLIENT_OVERLAY_LINK_ERROR "+error);
   }
  }
  gameLayerReady=true;
  if(!usingStock&&overlayFactory==null&&media!=null)
   media.log("CLIENT_OVERLAY_MISSING: the stock resource loading screen will be used");
 }

 @SuppressWarnings("unchecked")
 public <T> Supplier<T> loadingOverlay(Supplier<?> minecraft,Supplier<?> reload,
     Consumer<Optional<Throwable>> finish,boolean fade) {
  if(usingStock)return stockProvider().loadingOverlay(minecraft,reload,finish,fade);
  var completionStarted=new java.util.concurrent.atomic.AtomicBoolean();
  Consumer<Optional<Throwable>> guardedFinish=result->{completionStarted.set(true);finish.accept(result);};
  java.util.function.Function<Throwable,RuntimeException> propagate=error->{
   Throwable cause=error instanceof java.lang.reflect.InvocationTargetException target&&target.getCause()!=null?target.getCause():error;
   if(cause instanceof Error fatal)throw fatal;
   return cause instanceof RuntimeException runtime?runtime:new IllegalStateException("Loading overlay creation failed",cause);
  };
  Supplier<Supplier<T>> stockOverlay=()->stockProvider().loadingOverlay(minecraft,reload,finish,fade);
  if(!aborted&&overlayFactory!=null&&media!=null&&!media.stopped) {
   try {
    Supplier<T> custom=(Supplier<T>)overlayFactory.invoke(null,minecraft,reload,guardedFinish,media,renderer);
    // Class generation/constructor linkage happens when Forge calls get(), not
    // when the factory returns its supplier. Guard exactly that creation call.
    return ()->{
     try{return java.util.Objects.requireNonNull(custom.get(),"Loading overlay supplier returned null");}
     catch(Throwable failure){
      if(completionStarted.get())throw propagate.apply(failure);
      if(media!=null)media.log("OVERLAY_CREATION_FALLBACK "+failure);
      stopMediaForStock();
      return stockOverlay.get().get();
     }
    };
   } catch(Throwable failure) {
    if(completionStarted.get())throw propagate.apply(failure);
    if(media!=null)media.log("OVERLAY_FALLBACK "+failure);
   }
  }
  stopMediaForStock();
  return stockOverlay.get();
 }

 public String getGLVersion(){return usingStock?stockProvider().getGLVersion():glVersion;}

 private void fitSurface() {
  if(renderFailure!=null){stopMediaForStock();return;}
  NativeFadeSurface embedding=surface;
  if(embedding==null||window==0)return;
  try {
   embedding.fit();
   framebufferWidth=embedding.width();framebufferHeight=embedding.height();
   if(media!=null)embedding.opacity(media.opacity());
  } catch(Throwable error) {
   if(media!=null)media.log("SURFACE_FALLBACK "+error);
   stopMediaForStock();
  }
 }
 private void refreshFramebuffer() {
  if(window==0)return;
  try(MemoryStack stack=MemoryStack.stackPush()) {
   var a=stack.mallocInt(1);var b=stack.mallocInt(1);
   if(SDL_GetWindowSizeInPixels(window,a,b)) {
    framebufferWidth=Math.max(1,a.get(0));framebufferHeight=Math.max(1,b.get(0));
   }
  }
 }
 private void showGameWindow(){if(gameWindow!=0)SDL_ShowWindow(gameWindow);}
 private void closeSplashWindow() {
  if(window==0||!splashReleased)return;
  contextLock.lock();
  try {
   NativeFadeSurface embedding=surface;
   if(embedding!=null){
    try{embedding.opacity(0);}catch(Throwable error){if(media!=null)media.log("SURFACE_HIDE_WARNING "+error);}
    try{embedding.restoreParent();}catch(Throwable error){if(media!=null)media.log("SURFACE_RESTORE_WARNING "+error);}
    surface=null;
   }
   if(context!=0){SDL_GL_DestroyContext(context);context=0;}
   SDL_DestroyWindow(window);window=0;
   if(media!=null)media.log("SPLASH_WINDOW_CLOSED");
  } finally {contextLock.unlock();}
 }
 private void stopMediaForStock() {
  if(usingStock||aborted)return;
  aborted=true;
  if(task!=null)task.cancel(false);if(ticker!=null)ticker.shutdown();
  contextLock.lock();
  long videoContext=context;
  long previousWindow=SDL_GL_GetCurrentWindow(),previousContext=SDL_GL_GetCurrentContext();
  GLCapabilities previousCapabilities=null;
  try {previousCapabilities=GL.getCapabilities();}catch(IllegalStateException ignored){}
  try {
   if(window!=0&&context!=0&&!splashReleased) {
    boolean current=SDL_GL_MakeCurrent(window,context);
    try{
     if(current){GL.setCapabilities(capabilities);if(renderer!=null)renderer.close();}
     else if(media!=null)media.log("RENDER_RELEASE_WARNING "+SDL_GetError());
    }catch(Throwable error){if(media!=null)media.log("RENDER_RELEASE_WARNING "+error);}
    finally{
     if(media!=null)media.close();
     if(current)SDL_GL_MakeCurrent(window,0);splashReleased=true;
     if(media!=null)media.markRenderReleased();
    }
   }
   showGameWindow();closeSplashWindow();
  } finally {
   if(previousContext!=0&&previousContext!=videoContext){
    SDL_GL_MakeCurrent(previousWindow,previousContext);GL.setCapabilities(previousCapabilities);
   }else GL.setCapabilities(null);
   contextLock.unlock();
  }
  if(media!=null)media.finishLogging();
 }
 private void resolveModNames() {
  if(!namesResolved)namesResolved=shanghan.early.LoaderIntrospection.registerNames(media,gameLayerReady);
 }
 private ImmediateWindowProvider stockProvider() {
  if(stock!=null)return stock;
  try {
   stock=(ImmediateWindowProvider)ImmediateWindowProvider.class.getMethod("getFallbackHandler").invoke(null);
   return stock;
  } catch(ReflectiveOperationException error) {
   throw new IllegalStateException("Modern Forge stock provider is unavailable",error);
  }
 }
 private Runnable fallback(String[] arguments){usingStock=true;return stockProvider().initialize(arguments);}
 private long stockWindow(int width,int height,String title,Supplier<Object> backend) {
  try {
   Method method=stockProvider().getClass().getMethod("setupMinecraftWindow",
     int.class,int.class,String.class,Supplier.class);
   // DummyProvider's class is package-private; call the public interface method.
   method=ImmediateWindowProvider.class.getMethod("setupMinecraftWindow",
     int.class,int.class,String.class,Supplier.class);
   return ((Number)method.invoke(stockProvider(),width,height,title,backend)).longValue();
  } catch(ReflectiveOperationException error){throw new IllegalStateException("Stock SDL window failed",error);}
 }
 private void releaseInitialization() {
  if(ticker!=null)ticker.shutdownNow();
  if(window!=0&&context!=0) {
   boolean current=SDL_GL_MakeCurrent(window,context);
   try{
    if(current&&renderer!=null){if(capabilities!=null)GL.setCapabilities(capabilities);renderer.close();}
   }catch(Throwable error){if(media!=null)media.log("INITIALIZATION_RELEASE_WARNING "+error);}
   finally{
    if(media!=null)media.close();
    if(current)SDL_GL_MakeCurrent(window,0);GL.setCapabilities(null);
    SDL_GL_DestroyContext(context);context=0;
   }
  } else if(media!=null)media.close();
  if(window!=0){SDL_DestroyWindow(window);window=0;}
  if(media!=null)media.finishLogging();
 }
 private static long hwnd(long window) {
  long handle=SDL_GetPointerProperty(SDL_GetWindowProperties(window),SDL_PROP_WINDOW_WIN32_HWND_POINTER,0);
  if(handle==0)throw failure("SDL did not expose a Win32 window handle");
  return handle;
 }
 private static void require(boolean result,String message){if(!result)throw failure(message);}
 private static IllegalStateException failure(String message){return new IllegalStateException(message+": "+SDL_GetError());}
}
