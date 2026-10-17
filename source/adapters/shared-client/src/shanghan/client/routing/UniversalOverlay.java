package shanghan.client.routing;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import shanghan.early.MediaSession;
import shanghan.early.VideoRenderer;

/**
 * Uses the installed game's LoadingOverlay lifecycle. Only our subclass is generated:
 * Minecraft and Forge class files are never changed. No graphics API is called here.
 */
public final class UniversalOverlay {
 private static final AtomicInteger IDS=new AtomicInteger();
 private static final String STATE=Type.getInternalName(State.class);
 private UniversalOverlay(){}

 public static void installDetails(MediaSession media){
  media.tasks.attach(org.apache.logging.log4j.LogManager.getLogger("net.minecraftforge.fml.javafmlmod.FMLModContainer"));
 }

 public static Supplier<?> create(Supplier<?> minecraft,Supplier<?> reload,
    Consumer<Optional<Throwable>> finish,MediaSession media,VideoRenderer renderer){
  Objects.requireNonNull(renderer,"renderer");
  return ()->{
   Object mc=Objects.requireNonNull(minecraft.get(),"minecraft"),ri=Objects.requireNonNull(reload.get(),"reload");
   try{
    Class<?> stock=Class.forName("net.minecraft.client.gui.screens.LoadingOverlay",false,mc.getClass().getClassLoader());
    return construct(stock,mc,ri,finish,media);
   }catch(Throwable problem){throw new IllegalStateException("No compatible LoadingOverlay lifecycle",unwrap(problem));}
  };
 }

 /** Exposed without Minecraft compile dependencies so startup diagnostics can name the actual route. */
 public static String describe(Class<?> stock){
  Method hook=findHook(stock);
  return hook.getName()+Type.getMethodDescriptor(hook);
 }

 static Object construct(Class<?> stock,Object mc,Object reload,Consumer<Optional<Throwable>> finish,MediaSession media)throws Throwable{
  if(!media.detached)throw new IllegalStateException("Universal overlay requires an embedded early window");
  if(Modifier.isFinal(stock.getModifiers()))throw new IllegalStateException("LoadingOverlay is final");
  Method hook=findHook(stock);
  Constructor<?> base=findConstructor(stock,mc,reload);
  Access access=Access.find(mc,stock);
  State state=new State(mc,reload,finish,media,access,base.getParameterTypes()[1]);
  Class<?> generated=generate(stock,base,hook);
  Consumer<Optional<Throwable>> wrapped=state::finish;
  Object overlay=generated.getConstructor(base.getParameterTypes()[0],base.getParameterTypes()[1],Consumer.class,State.class)
      .newInstance(mc,reload,wrapped,state);
  state.bind(overlay);
  media.log("OVERLAY_ROUTE "+describe(stock));
  return overlay;
 }

 private static Constructor<?> findConstructor(Class<?> stock,Object mc,Object reload){
  for(Constructor<?> c:stock.getDeclaredConstructors()){
   Class<?>[] p=c.getParameterTypes();
   if(p.length==4&&p[0].isInstance(mc)&&p[1].isInstance(reload)&&p[2]==Consumer.class&&p[3]==boolean.class
      &&(Modifier.isPublic(c.getModifiers())||Modifier.isProtected(c.getModifiers())))return c;
  }
  throw new IllegalStateException("LoadingOverlay constructor does not match the public reload lifecycle");
 }
 private static Method findHook(Class<?> stock){
  List<Method> methods=new ArrayList<>();
  for(Class<?> type=stock;type!=null;type=type.getSuperclass())Collections.addAll(methods,type.getDeclaredMethods());
  // A top-level hook sees the stock overlay removal immediately. Content-only hooks use
  // the bounded game-thread monitor because stock may remove itself after the hook returns.
  for(String name:new String[]{"extractRenderState","render","m_88315_","extractContentRenderState","renderContents"}){
   for(Method m:methods){
    int flags=m.getModifiers();Class<?>[] p=m.getParameterTypes();
    if(!m.getName().equals(name)||m.getReturnType()!=void.class||Modifier.isStatic(flags)||Modifier.isFinal(flags)
       ||Modifier.isAbstract(flags)||Modifier.isPrivate(flags))continue;
    if(!(Modifier.isPublic(flags)||Modifier.isProtected(flags)))continue;
    boolean graphics=p.length>0&&(p[0].getSimpleName().equals("GuiGraphics")||p[0].getSimpleName().equals("GuiGraphicsExtractor"));
    if(graphics&&((p.length==4&&p[1]==int.class&&p[2]==int.class&&p[3]==float.class)
       ||(p.length==2&&p[1]==float.class)))return m;
   }
  }
  throw new IllegalStateException("Unknown LoadingOverlay graphics entry point");
 }

 private static Class<?> generate(Class<?> stock,Constructor<?> constructor,Method hook)throws IllegalAccessException{
  String own="shanghan/client/routing/GeneratedLoadingOverlay"+IDS.incrementAndGet();
  String parent=Type.getInternalName(stock),stateDesc="L"+STATE+";";
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
  w.visit(Opcodes.V17,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,own,null,parent,null);
  w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"state",stateDesc,null,null).visitEnd();
  Class<?>[] base=constructor.getParameterTypes();
  String ctor="("+Type.getDescriptor(base[0])+Type.getDescriptor(base[1])+"Ljava/util/function/Consumer;"+stateDesc+")V";
  MethodVisitor v=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>",ctor,null,null);v.visitCode();
  v.visitVarInsn(Opcodes.ALOAD,0);v.visitVarInsn(Opcodes.ALOAD,1);v.visitVarInsn(Opcodes.ALOAD,2);v.visitVarInsn(Opcodes.ALOAD,3);
  v.visitInsn(Opcodes.ICONST_0);
  v.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>",Type.getConstructorDescriptor(constructor),false);
  v.visitVarInsn(Opcodes.ALOAD,0);v.visitVarInsn(Opcodes.ALOAD,4);v.visitFieldInsn(Opcodes.PUTFIELD,own,"state",stateDesc);
  v.visitInsn(Opcodes.RETURN);v.visitMaxs(0,0);v.visitEnd();
  String desc=Type.getMethodDescriptor(hook);
  v=w.visitMethod(Opcodes.ACC_PUBLIC,hook.getName(),desc,null,null);v.visitCode();
  v.visitVarInsn(Opcodes.ALOAD,0);v.visitFieldInsn(Opcodes.GETFIELD,own,"state",stateDesc);
  v.visitMethodInsn(Opcodes.INVOKEVIRTUAL,STATE,"before","()V",false);
  v.visitVarInsn(Opcodes.ALOAD,0);
  int slot=1;
  for(Class<?> p:hook.getParameterTypes()){
   Type t=Type.getType(p);v.visitVarInsn(t.getOpcode(Opcodes.ILOAD),slot);slot+=t.getSize();
  }
  v.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,hook.getName(),desc,false);
  v.visitVarInsn(Opcodes.ALOAD,0);v.visitFieldInsn(Opcodes.GETFIELD,own,"state",stateDesc);
  v.visitMethodInsn(Opcodes.INVOKEVIRTUAL,STATE,"after","()V",false);
  v.visitInsn(Opcodes.RETURN);v.visitMaxs(0,0);v.visitEnd();w.visitEnd();
  return MethodHandles.lookup().defineClass(w.toByteArray());
 }

 private static Throwable unwrap(Throwable e){return e instanceof InvocationTargetException i&&i.getCause()!=null?i.getCause():e;}
 private static Object invoke(Method method,Object instance,Object... arguments){
  try{return method.invoke(instance,arguments);}
  catch(ReflectiveOperationException e){throw new IllegalStateException("Minecraft lifecycle invocation failed: "+method,unwrap(e));}
 }
 private static Method method(Class<?> type,String... names){
  for(String name:names)for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==0)return m;
  return null;
 }

 /** All Minecraft/Gui reads and writes run in its Executor, or in its render/extract callback. */
 public static final class State {
  private final Object minecraft,reload;
  private final Consumer<Optional<Throwable>> downstream;
  private final MediaSession media;
  private final Access access;
  private final Method progress;
  private final Executor executor;
  private final ScheduledExecutorService monitor;
  private final AtomicBoolean pending=new AtomicBoolean();
  private Object overlay;
  private boolean finished,failed,closed,installed,nativeRemoved,fadeBegun;
  private int menuFrames;
  private long lastProgress;
  State(Object mc,Object reload,Consumer<Optional<Throwable>> finish,MediaSession media,Access access,Class<?> reloadApi){
   this.minecraft=mc;this.reload=reload;this.downstream=Objects.requireNonNull(finish);this.media=media;this.access=access;
   if(!(mc instanceof Executor e))throw new IllegalStateException("Minecraft game-thread executor unavailable");
   executor=e;
   progress=method(reloadApi,"getActualProgress","m_7750_");
   if(progress==null||progress.getReturnType()!=float.class)throw new IllegalStateException("Reload progress method unavailable");
   monitor=Executors.newSingleThreadScheduledExecutor(task->{Thread t=new Thread(task,"LoadingFramework-overlay-monitor");t.setDaemon(true);return t;});
  }
  void bind(Object overlay){
   this.overlay=overlay;
   monitor.scheduleWithFixedDelay(()->{
    if(!pending.compareAndSet(false,true))return;
    try{executor.execute(()->{try{if(!closed&&!failed)observe(false);}catch(Throwable e){fail(e);}finally{pending.set(false);}});}
    catch(Throwable e){pending.set(false);fail(e);}
   },50,50,TimeUnit.MILLISECONDS);
  }
  void finish(Optional<Throwable> result){
   if(closed||failed)return;
   try{
    downstream.accept(result);
    if(result.isPresent()){fail(result.get());return;}
    if(!finished){finished=true;media.progress.complete();media.log("RESOURCE_RELOAD_COMPLETE");}
   }catch(Throwable e){fail(e);throw e;}
  }
  public void before(){
   if(closed||failed)return;
   installed=true;
   media.fitWindow.run();
   long now=System.nanoTime();
   if(now-lastProgress>=100_000_000L){
    // Complete is terminal: later resource polling must not reset its completion marker.
    if(!finished){media.progress.tick();media.progress.resource(((Number)invoke(progress,reload)).floatValue());}
    lastProgress=now;
   }
  }
  public void after(){
   if(closed||failed)return;
   try{observe(true);}catch(Throwable e){fail(e);throw e;}
  }
  private void observe(boolean rendered){
   Object active=access.get();
   if(!installed){if(active!=overlay)return;installed=true;}
   if(active!=overlay){
    if(!finished){fail(new IllegalStateException("Startup overlay removed or replaced before reload completed"));return;}
    if(active!=null){fail(new IllegalStateException("Startup overlay replaced by another overlay"));return;}
    nativeRemoved=true;access.set(overlay);
   }
   if(!nativeRemoved)return;
   // A second stock-rendered/extracted frame is needed before exposing the game window.
   // Extraction callbacks do not own a graphics context; captureMenu/GL are never used.
   if(rendered&&++menuFrames>=2&&!fadeBegun){
    fadeBegun=true;media.showGame.run();media.beginFade();media.log("MINECRAFT_READY clock="+media.clock());
   }
   if(fadeBegun&&media.readyForWindowClose()){
    media.closeWindow.run();
    if(access.get()==overlay)access.set(null);
    closed=true;monitor.shutdownNow();media.log("OVERLAY_CLOSED");media.finishLogging();
   }
  }
  private synchronized void fail(Throwable problem){
   if(failed||closed)return;failed=true;monitor.shutdownNow();
   media.log("OVERLAY_ABORT "+problem);media.abortLoading.run();
  }
 }

 private static final class Access {
  private final Object owner;
  private final Method getter,setter;
  private final Field field;
  private Access(Object owner,Method getter,Method setter,Field field){this.owner=owner;this.getter=getter;this.setter=setter;this.field=field;}
  static Access find(Object mc,Class<?> stock){
   Access direct=on(mc,stock);if(direct!=null)return direct;
   // Newer Minecraft keeps the lifecycle on Minecraft.gui instead of Minecraft.
   for(Field f:mc.getClass().getFields())if(!Modifier.isStatic(f.getModifiers())){
    try{Object gui=f.get(mc);if(gui!=null){Access nested=on(gui,stock);if(nested!=null)return nested;}}
    catch(IllegalAccessException ignored){}
   }
   throw new IllegalStateException("Minecraft overlay getter/setter unavailable");
  }
  private static Access on(Object owner,Class<?> stock){
   for(Method setter:owner.getClass().getMethods()){
    Class<?>[] p=setter.getParameterTypes();
    if((setter.getName().equals("setOverlay")||setter.getName().equals("m_91150_"))&&p.length==1&&p[0].isAssignableFrom(stock)){
     for(String name:new String[]{"overlay","getOverlay","m_91265_"})for(Method getter:owner.getClass().getMethods())
      if(getter.getName().equals(name)&&getter.getParameterCount()==0&&p[0].isAssignableFrom(getter.getReturnType()))return new Access(owner,getter,setter,null);
     for(Field field:owner.getClass().getFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==p[0])return new Access(owner,null,setter,field);
    }
   }
   return null;
  }
  Object get(){
   if(getter!=null)return invoke(getter,owner);
   try{return field.get(owner);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
  }
  void set(Object overlay){invoke(setter,owner,overlay);}
 }
}
