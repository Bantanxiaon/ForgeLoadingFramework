package shanghan.early;
import net.minecraftforge.fml.loading.*;
import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import org.lwjgl.opengl.GL;
import org.lwjgl.glfw.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import static org.lwjgl.glfw.GLFW.*;
/** Independent official ImmediateWindowProvider. Does not patch Forge or Minecraft classes. */
public class ShangHanProvider implements ImmediateWindowProvider {
 protected static volatile boolean initialized;
 static boolean initialized(){return initialized;}
 protected final ReentrantLock contextLock=new ReentrantLock();
 protected volatile boolean handingOff;
 protected long window,gameWindow;
 protected boolean detached;
 protected WindowSurface surface;
 protected shanghan.modern.boot.NativeFadeSurface nativeSurface;
 protected long lastSurfaceFit;
 protected volatile boolean splashClosed;
 protected String glVersion="3.2";
 protected ScheduledExecutorService ticker;
 protected ScheduledFuture<?> task;
 protected MediaSession media;
 protected VideoRenderer renderer;
 protected org.lwjgl.opengl.GLCapabilities earlyCapabilities;
 protected boolean namesResolved;
 protected volatile boolean gameLayerReady;
 protected final int[] framebufferWidth={0},framebufferHeight={0};
 protected long lastProgressPoll,lastDraw;
 protected ImmediateWindowProvider fallback;
 protected Method overlayFactory,vanillaFactory;
 protected ModuleLayer gameLayer;
 public String name(){return "shanghanearlyloading";}
 public Runnable initialize(String[] arguments){
  initialized=true;
  Path game=FMLPaths.GAMEDIR.get(),dir=Settings.directory(game);
  Settings settings=Settings.load(dir);if(settings.title==null||settings.title.isBlank())settings.title=PackName.resolve(game,arguments);
  if(!settings.enabled)return fallback(arguments);
  try{
   if(!RuntimeProfile.supports(argument(arguments,"--fml.mcVersion"),argument(arguments,"--fml.forgeVersion")))
    throw new IllegalStateException("Unsupported Minecraft/Forge build for this runtime profile");
   if(!glfwInit())throw new IllegalStateException("GLFW init failed");
   int[][] versions={{4,6},{4,5},{4,3},{3,3},{3,2}};
   for(int[] version:versions){
    glfwDefaultWindowHints();glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,version[0]);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,version[1]);
    glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE);
    glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_RESIZABLE,GLFW_TRUE);
    glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER,GLFW_FALSE);
    if(settings.maximized)glfwWindowHint(GLFW_MAXIMIZED,GLFW_TRUE);
    window=glfwCreateWindow(settings.width,settings.height,settings.title,0,0);
    if(window!=0){glVersion=version[0]+"."+version[1];break;}
   }
   if(window==0)throw new IllegalStateException("No GL window");
   glfwMakeContextCurrent(window);earlyCapabilities=GL.createCapabilities();glfwSwapInterval(0);
   detached=settings.independentWindow;
   media=new MediaSession(dir,settings);media.detached=detached;
   media.showGame=this::showGameWindow;media.closeWindow=this::closeSplashWindow;media.abortLoading=this::stopMediaForStock;media.fitWindow=this::fitSurface;
   renderer=new VideoRenderer(media);media.start();
   glfwShowWindow(window);if(settings.maximized)glfwMaximizeWindow(window);
   // Warm up GL resources on the main thread, before handing rendering to the ticker.
   renderer.render(settings.width,settings.height,1);glfwSwapBuffers(window);
   glfwMakeContextCurrent(0);
   ticker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"ShangHan-render");t.setDaemon(true);return t;});
   task=ticker.scheduleAtFixedRate(this::renderEarly,0,1_000_000_000L/settings.fps,TimeUnit.NANOSECONDS);
   media.log("PROVIDER_READY GL="+glVersion+" targetFPS="+settings.fps+" windowOpaque="+(glfwGetWindowAttrib(window,GLFW_TRANSPARENT_FRAMEBUFFER)==GLFW_FALSE));
   return this::periodicTick;
  }catch(Throwable e){
   System.err.println("[ShangHan] Provider initialization failed, stock Forge fallback: "+e);e.printStackTrace();
   if(media!=null)media.close();if(window!=0){glfwMakeContextCurrent(window);if(renderer!=null)renderer.close();glfwMakeContextCurrent(0);glfwDestroyWindow(window);window=0;}
   return fallback(arguments);
  }
 }
 protected static String argument(String[] args,String name){for(int i=0;i+1<args.length;i++)if(name.equals(args[i]))return args[i+1];return "";}
 protected Runnable fallback(String[] args){fallback=new DisplayWindow();return fallback.initialize(args);}
 protected void renderEarly(){
  long tick=System.nanoTime();if(tick-lastDraw<500_000_000L/media.settings.fps)return;
  if((handingOff&&!detached)||splashClosed||!contextLock.tryLock())return;lastDraw=tick;
  try{
   glfwMakeContextCurrent(window);GL.setCapabilities(earlyCapabilities);
   int[] w=framebufferWidth,h=framebufferHeight;glfwGetFramebufferSize(window,w,h);
   if(!namesResolved)namesResolved=LoaderIntrospection.registerNames(media,gameLayerReady);
   long now=System.nanoTime();if(now-lastProgressPoll>=100_000_000L){media.progress.tick();lastProgressPoll=now;}
   if(detached){org.lwjgl.opengl.GL32C.glClearColor(0,0,0,1);org.lwjgl.opengl.GL32C.glClear(org.lwjgl.opengl.GL32C.GL_COLOR_BUFFER_BIT);}
   float opacity=media.opacity();media.fade(opacity);
    if(nativeSurface!=null)nativeSurface.opacity(opacity);
    renderer.render(w[0],h[0],media.nativeWindowFade?1:opacity);glfwSwapBuffers(window);
   if(detached&&media.opacity()<=0){
    renderer.close();splashClosed=true;if(task!=null)task.cancel(false);if(ticker!=null)ticker.shutdown();media.log("FADE_RENDER_DONE");media.markRenderReleased();
   }
  }catch(Throwable e){if(media!=null)media.log("EARLY_RENDER_ERROR "+e);}
  finally{glfwMakeContextCurrent(0);contextLock.unlock();}
 }
 public void periodicTick(){
  if(fallback!=null){fallback.periodicTick();return;}
  fitSurface();glfwPollEvents();
  if(window!=0&&!splashClosed&&glfwWindowShouldClose(window)){media.close();throw new IllegalStateException("Loading window closed by user");}
  // Main thread supplies repaints during the interval between window handoff and overlay creation.
  if(handingOff&&!detached&&renderer!=null){int[] w={0},h={0};glfwGetFramebufferSize(window,w,h);renderer.render(w[0],h[0],1);}
 }
 public long setupMinecraftWindow(IntSupplier width,IntSupplier height,Supplier<String> title,LongSupplier monitor){
  if(fallback!=null)return fallback.setupMinecraftWindow(width,height,title,monitor);
  if(detached&&monitor.getAsLong()==0&&org.lwjgl.system.Platform.get()==org.lwjgl.system.Platform.WINDOWS){
   int[] w={0},h={0};glfwGetWindowSize(window,w,h);
   glfwDefaultWindowHints();int[] version=java.util.Arrays.stream(glVersion.split("\\.")).mapToInt(Integer::parseInt).toArray();
   glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,version[0]);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,version[1]);
   glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE);
   glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_FOCUS_ON_SHOW,GLFW_FALSE);glfwWindowHint(GLFW_RESIZABLE,GLFW_TRUE);
   gameWindow=glfwCreateWindow(w[0],h[0],title.get(),0,window);
   if(gameWindow!=0){
    int[] x={0},y={0};glfwGetWindowPos(window,x,y);glfwSetWindowPos(gameWindow,x[0],y[0]);
    contextLock.lock();
    try{
     surface=new WindowSurface(window,gameWindow);surface.attach();
     glfwSetWindowTitle(gameWindow,media.settings.title);
     glfwShowWindow(gameWindow);glfwPollEvents();
     glfwMakeContextCurrent(gameWindow);GL.createCapabilities();glfwSwapInterval(0);handingOff=true;
     media.log("WINDOW_HANDOFF embedded=true topLevelWindows=1");return gameWindow;
    }catch(Throwable error){
     media.log("EMBEDDING_FAILED "+error);if(surface!=null)surface.rollback();surface=null;
     // Never leave a second floating loading window on embedding failure.
     glfwDestroyWindow(gameWindow);gameWindow=0;
    }finally{contextLock.unlock();}
   }
   media.log("SECOND_WINDOW_FAILED: using single window");
  }
  detached=false;media.detached=false;
  handingOff=true;if(task!=null)task.cancel(false);if(ticker!=null)ticker.shutdown();
  contextLock.lock();
  try{glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(0);glfwSetWindowTitle(window,title.get());media.log("WINDOW_HANDOFF");return window;}
  finally{contextLock.unlock();}
 }
 public void updateFramebufferSize(IntConsumer width,IntConsumer height){
  if(fallback!=null){fallback.updateFramebufferSize(width,height);return;}
  int[] w={0},h={0};glfwGetFramebufferSize(gameWindow!=0?gameWindow:window,w,h);width.accept(w[0]);height.accept(h[0]);
 }
 public boolean positionWindow(Optional<Object> monitor,IntConsumer width,IntConsumer height,IntConsumer x,IntConsumer y){
  if(fallback!=null)return fallback.positionWindow(monitor,width,height,x,y);
  int[] w={0},h={0},px={0},py={0};glfwGetWindowSize(gameWindow!=0?gameWindow:window,w,h);glfwGetWindowPos(gameWindow!=0?gameWindow:window,px,py);width.accept(w[0]);height.accept(h[0]);x.accept(px[0]);y.accept(py[0]);return true;
 }
 protected void stopMediaForStock(){
  if(task!=null)task.cancel(false);if(ticker!=null)ticker.shutdown();
  long context=glfwGetCurrentContext();var capabilities=context==0?null:GL.getCapabilities();
  contextLock.lock();
  try{
   if(window!=0&&!splashClosed){glfwMakeContextCurrent(window);GL.setCapabilities(earlyCapabilities);renderer.close();splashClosed=true;media.markRenderReleased();}
   glfwMakeContextCurrent(context);GL.setCapabilities(capabilities);
   if(detached){showGameWindow();closeSplashWindow();}
  }finally{contextLock.unlock();}
 }
 protected void enableNativeFade(){
  if(!media.detached||gameWindow==0||nativeSurface!=null)return;
  if(org.lwjgl.system.Platform.get()!=org.lwjgl.system.Platform.WINDOWS)throw new IllegalStateException("Native video fade requires Windows");
  nativeSurface=new shanghan.modern.boot.NativeFadeSurface(
   org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(window),
   org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(gameWindow));
  nativeSurface.attach();
 }
 protected void fitSurface(){
  if(System.nanoTime()-lastSurfaceFit>100_000_000L){if(surface!=null)surface.fit();if(nativeSurface!=null)nativeSurface.fit();lastSurfaceFit=System.nanoTime();}
 }
 protected void showGameWindow(){
  if(!detached||gameWindow==0)return;
  glfwShowWindow(gameWindow);
  if(surface!=null)return;
  try{
   long a=org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(window),b=org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(gameWindow);
   com.sun.jna.platform.win32.User32.INSTANCE.SetWindowPos(new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer.createConstant(b)),new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer.createConstant(a)),0,0,0,0,0x13);
  }catch(Throwable e){media.log("WINDOW_ORDER_WARNING "+e);}
 }
 protected void closeSplashWindow(){
  if(!detached||window==0)return;
  if(!splashClosed)return;
  contextLock.lock();
  try{
   boolean focus=glfwGetWindowAttrib(window,GLFW_FOCUSED)==GLFW_TRUE;
   if(nativeSurface!=null){nativeSurface.restoreParent();nativeSurface=null;}if(surface!=null)surface.restoreParent();glfwDestroyWindow(window);window=0;surface=null;
   if(focus)glfwFocusWindow(gameWindow);
   media.log("SPLASH_WINDOW_CLOSED");
  }finally{contextLock.unlock();}
 }
 public String getGLVersion(){return fallback!=null?fallback.getGLVersion():glVersion;}
 public void updateModuleReads(ModuleLayer layer){
  if(fallback!=null){fallback.updateModuleReads(layer);return;}
  gameLayer=layer;
  for(Module m:layer.modules()){
   getClass().getModule().addReads(m);
   Class<?> c=Class.forName(m,"shanghan.client.routing.ClientOverlayRouter");
   if(c!=null){
    overlayFactory=Arrays.stream(c.getMethods()).filter(x->x.getName().equals("create")).findFirst().orElse(null);
    try{c.getMethod("installDetails",MediaSession.class).invoke(null,media);}catch(Throwable e){media.log("DETAIL_BRIDGE_UNAVAILABLE "+e);}
   }
   {
    Class<?> v=Class.forName(m,"net.minecraftforge.client.loading.NoVizFallback");
    if(v!=null)vanillaFactory=Arrays.stream(v.getMethods()).filter(x->x.getName().equals("loadingOverlay")).findFirst().orElse(null);
   }
  }
  gameLayerReady=true;
  if(overlayFactory==null)media.log("CLIENT_OVERLAY_MISSING: early media will stop at resource overlay");
 }
 @SuppressWarnings("unchecked")
 public <T> Supplier<T> loadingOverlay(Supplier<?> mc,Supplier<?> ri,Consumer<Optional<Throwable>> finish,boolean fade){
  if(fallback!=null)return fallback.loadingOverlay(mc,ri,finish,fade);
  var completionStarted=new java.util.concurrent.atomic.AtomicBoolean();
  Consumer<Optional<Throwable>> guardedFinish=result->{completionStarted.set(true);finish.accept(result);};
  Function<Throwable,RuntimeException> propagate=error->{
   Throwable cause=error instanceof InvocationTargetException target&&target.getCause()!=null?target.getCause():error;
   if(cause instanceof Error fatal)throw fatal;
   return cause instanceof RuntimeException runtime?runtime:new IllegalStateException("Loading overlay creation failed",cause);
  };
  Supplier<Supplier<T>> stockOverlay=()->{
   try{return (Supplier<T>)vanillaFactory.invoke(null,mc,ri,finish,fade);}
   catch(Throwable failure){throw propagate.apply(failure);}
  };
  try{
   handingOff=true;
   if(overlayFactory!=null&&!media.stopped){
    Supplier<T> custom=(Supplier<T>)overlayFactory.invoke(null,mc,ri,guardedFinish,media,renderer);
    if(media.nativeWindowFade)enableNativeFade();
    // Only construction is guarded. Later render/reload/finish exceptions keep
    // their original path and cannot cause a second completion callback.
    return ()->{
     try{return Objects.requireNonNull(custom.get(),"Loading overlay supplier returned null");}
     catch(Throwable failure){
      if(completionStarted.get())throw propagate.apply(failure);
      if(media!=null)media.log("OVERLAY_CREATION_FALLBACK "+failure);
      stopMediaForStock();
      return stockOverlay.get().get();
     }
    };
   }
   stopMediaForStock();
   return stockOverlay.get();
  }catch(Throwable failure){
   if(completionStarted.get())throw propagate.apply(failure);
   if(media!=null)media.log("OVERLAY_FALLBACK "+failure);
   if(renderer!=null)stopMediaForStock();
   return stockOverlay.get();
  }
 }
}
