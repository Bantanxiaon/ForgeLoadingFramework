import shanghan.client.routing.UniversalOverlay;
import shanghan.early.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Tests generated subclasses and complete reload/menu/fade lifecycle without Minecraft or GL. */
public final class UniversalOverlaySmoke {
 public static class GuiGraphics {}
 public static class GuiGraphicsExtractor {}
 public static class Overlay {}
 public static class Reload {
  public boolean done;
  public float getActualProgress(){return done?1:.35f;}
 }
 public static class Minecraft implements Executor {
  public volatile Overlay overlay;
  public final List<String> events=Collections.synchronizedList(new ArrayList<>());
  public void setOverlay(Overlay value){overlay=value;events.add(value==null?"clear":"attach");}
  public void execute(Runnable task){task.run();}
 }
 public static class Gui {
  private volatile Overlay active;
  public Overlay overlay(){return active;}
  public void setOverlay(Overlay next){active=next;}
 }
 public static class ModernMinecraft implements Executor {
  public final Gui gui=new Gui();
  public void execute(Runnable task){task.run();}
 }
 public static class Legacy extends Overlay {
  final Minecraft mc;final Reload reload;final Consumer<Optional<Throwable>> finish;boolean done;int frames;
  public Legacy(Minecraft mc,Reload reload,Consumer<Optional<Throwable>> finish,boolean fadeIn){this.mc=mc;this.reload=reload;this.finish=finish;}
  public void render(GuiGraphics graphics,int x,int y,float partial){
   frames++;
   if(reload.done&&!done){done=true;finish.accept(Optional.empty());}
   if(done)mc.setOverlay(null); // Stock menu rendering and final removal occur together.
  }
 }
 public static class Modern extends Overlay {
  final ModernMinecraft mc;final Reload reload;final Consumer<Optional<Throwable>> finish;boolean done;int frames;
  public Modern(ModernMinecraft mc,Reload reload,Consumer<Optional<Throwable>> finish,boolean fadeIn){this.mc=mc;this.reload=reload;this.finish=finish;}
  public void tick(){if(reload.done&&!done){done=true;finish.accept(Optional.empty());}}
  public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float partial){
   frames++;if(done)mc.gui.setOverlay(null);
  }
 }
 public static class Content extends Overlay {
  final Minecraft mc;final Reload reload;final Consumer<Optional<Throwable>> finish;boolean done;int frames;
  public Content(Minecraft mc,Reload reload,Consumer<Optional<Throwable>> finish,boolean fadeIn){this.mc=mc;this.reload=reload;this.finish=finish;}
  public final void render(GuiGraphics graphics,int x,int y,float partial){
   renderContents(graphics,partial);if(done)mc.setOverlay(null);
  }
  protected void renderContents(GuiGraphics graphics,float partial){
   frames++;if(reload.done&&!done){done=true;finish.accept(Optional.empty());}
  }
 }
 public static class ExtractContent extends Overlay {
  final ModernMinecraft mc;final Reload reload;final Consumer<Optional<Throwable>> finish;boolean done;int frames;
  public ExtractContent(ModernMinecraft mc,Reload reload,Consumer<Optional<Throwable>> finish,boolean fadeIn){this.mc=mc;this.reload=reload;this.finish=finish;}
  public final void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float partial){
   extractContentRenderState(graphics,partial);if(done)mc.gui.setOverlay(null);
  }
  protected void extractContentRenderState(GuiGraphicsExtractor graphics,float partial){
   frames++;if(reload.done&&!done){done=true;finish.accept(Optional.empty());}
  }
 }
 public static class SrgMinecraft implements Executor {
  private volatile Overlay hidden;
  public void m_91150_(Overlay next){hidden=next;}
  public Overlay m_91265_(){return hidden;}
  public void execute(Runnable task){task.run();}
 }
 public static class Srg extends Overlay {
  final SrgMinecraft mc;final Reload reload;final Consumer<Optional<Throwable>> finish;boolean done;
  public Srg(SrgMinecraft mc,Reload reload,Consumer<Optional<Throwable>> finish,boolean fadeIn){this.mc=mc;this.reload=reload;this.finish=finish;}
  public void m_88315_(GuiGraphics graphics,int x,int y,float partial){
   if(reload.done&&!done){done=true;finish.accept(Optional.empty());}
   if(done)mc.m_91150_(null);
  }
 }
 public static class Unknown extends Overlay {
  public Unknown(Minecraft mc,Reload reload,Consumer<Optional<Throwable>> finish,boolean fadeIn){}
  public void drawSomethingElse(GuiGraphics graphics){}
 }
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static Object make(Class<?> stock,Object mc,Reload reload,Consumer<Optional<Throwable>> finish,MediaSession media)throws Throwable{
  Method factory=UniversalOverlay.class.getDeclaredMethod("construct",Class.class,Object.class,Object.class,Consumer.class,MediaSession.class);factory.setAccessible(true);
  try{return factory.invoke(null,stock,mc,reload,finish,media);}catch(InvocationTargetException e){throw e.getCause();}
 }
 private static MediaSession session(Path path){
  Settings settings=new Settings();settings.audio=false;settings.fadeOutMs=100;
  MediaSession media=new MediaSession(path,settings);media.detached=true;return media;
 }
 private static void await(BooleanSupplier condition,String why)throws InterruptedException{
  long stop=System.nanoTime()+2_000_000_000L;
  while(!condition.getAsBoolean()&&System.nanoTime()<stop)Thread.sleep(5);
  check(condition.getAsBoolean(),why);
 }
 private static void runRoute(Path root,Class<?> stock,boolean modern,boolean content)throws Throwable{
  MediaSession media=session(root.resolve(stock.getSimpleName()));
  Reload reload=new Reload();AtomicInteger finish=new AtomicInteger(),show=new AtomicInteger(),close=new AtomicInteger(),abort=new AtomicInteger();
  Object mc=modern?new ModernMinecraft():new Minecraft();
  media.showGame=show::incrementAndGet;media.closeWindow=()->{close.incrementAndGet();if(mc instanceof Minecraft old)old.events.add("close-child");};media.abortLoading=abort::incrementAndGet;
  Object overlay=make(stock,mc,reload,result->{check(result.isEmpty(),"Unexpected reload failure");finish.incrementAndGet();},media);
  if(mc instanceof ModernMinecraft next)next.gui.setOverlay((Overlay)overlay);else ((Minecraft)mc).setOverlay((Overlay)overlay);
  // The ordinary stock constructor and method must run; the bridge only decorates them.
  Method render=stock.getMethod(modern?"extractRenderState":"render",modern?GuiGraphicsExtractor.class:GuiGraphics.class,int.class,int.class,float.class);
  Object graphics=modern?new GuiGraphicsExtractor():new GuiGraphics();
  render.invoke(overlay,graphics,0,0,0f);
  check(finish.get()==0&&media.progress.value<1,"Incomplete reload marked complete");
  reload.done=true;
  if(stock==Modern.class)stock.getMethod("tick").invoke(overlay);
  render.invoke(overlay,graphics,0,0,0f);
  check(finish.get()==1&&media.progress.value==1,"Stock completion callback or total progress missing");
  if(content)await(()->mc instanceof ModernMinecraft n?n.gui.overlay()==overlay:((Minecraft)mc).overlay==overlay,"Outer stock removal was not recovered");
  render.invoke(overlay,graphics,0,0,0f);
  if(content)render.invoke(overlay,graphics,0,0,0f);
  check(show.get()==1,"Native menu frame did not start video fade exactly once");
  // stopped becomes true before rendering resources are freed; the child must remain alive.
  media.close();
  render.invoke(overlay,graphics,0,0,0f);
  check(close.get()==0,"Child closed before renderer released");
  media.markRenderReleased();render.invoke(overlay,graphics,0,0,0f);
  check(close.get()==1,"Child not closed after renderer released");
  check(mc instanceof ModernMinecraft n?n.gui.overlay()==null:((Minecraft)mc).overlay==null,"Overlay retained after video close");
  check(abort.get()==0&&finish.get()==1,"Normal reload was aborted or finish called twice");
  if(mc instanceof Minecraft old)check(old.events.indexOf("close-child")<old.events.lastIndexOf("clear"),"Overlay cleared before child window closed");
  String log=Files.readString(media.dir.resolve("runtime.log"));
  check(log.contains("RESOURCE_RELOAD_COMPLETE")&&log.contains("MINECRAFT_READY")&&log.contains("OVERLAY_CLOSED"),"Lifecycle diagnostics missing");
 }
 public static void main(String[] args)throws Throwable{
  Path root=Path.of(args[0]);
  runRoute(root,Legacy.class,false,false);
  runRoute(root,Modern.class,true,false);
  runRoute(root,Content.class,false,true);
  runRoute(root,ExtractContent.class,true,true);
  MediaSession srgMedia=session(root.resolve("srg"));SrgMinecraft srgMc=new SrgMinecraft();Reload srgReload=new Reload();AtomicInteger srgClosed=new AtomicInteger(),srgShow=new AtomicInteger();
  srgMedia.closeWindow=srgClosed::incrementAndGet;srgMedia.showGame=srgShow::incrementAndGet;
  Srg srg=(Srg)make(Srg.class,srgMc,srgReload,result->{},srgMedia);srgMc.m_91150_(srg);srgReload.done=true;
  srg.m_88315_(new GuiGraphics(),0,0,0);srg.m_88315_(new GuiGraphics(),0,0,0);
  check(srgShow.get()==1,"Production SRG getter/render names were not routed");
  srgMedia.close();srgMedia.markRenderReleased();srg.m_88315_(new GuiGraphics(),0,0,0);
  check(srgClosed.get()==1&&srgMc.m_91265_()==null,"SRG overlay did not finish");
  try{UniversalOverlay.describe(Unknown.class);throw new AssertionError("Unknown graphics API accepted");}catch(IllegalStateException expected){}
  MediaSession media=session(root.resolve("replaced"));Minecraft mc=new Minecraft();Reload reload=new Reload();AtomicInteger abort=new AtomicInteger();
  media.abortLoading=abort::incrementAndGet;
  Legacy overlay=(Legacy)make(Legacy.class,mc,reload,result->{},media);mc.setOverlay(overlay);
  overlay.render(new GuiGraphics(),0,0,0);
  Overlay replacement=new Overlay();mc.setOverlay(replacement);
  await(()->abort.get()==1,"Competing overlay was not preserved on abort");
  check(mc.overlay==replacement,"Bridge replaced another component's overlay");
  media.close();media.finishLogging();
  MediaSession removedMedia=session(root.resolve("removed"));Minecraft removedMc=new Minecraft();AtomicInteger removedAbort=new AtomicInteger();
  removedMedia.abortLoading=removedAbort::incrementAndGet;
  Legacy removed=(Legacy)make(Legacy.class,removedMc,new Reload(),result->{},removedMedia);removedMc.setOverlay(removed);
  removed.render(new GuiGraphics(),0,0,0);removedMc.setOverlay(null);
  await(()->removedAbort.get()==1,"Early external removal left the video running");
  removedMedia.close();removedMedia.finishLogging();
  await(()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&t.getName().equals("LoadingFramework-overlay-monitor")),"Overlay monitor thread leaked");
  System.out.println("PASS UniversalOverlaySmoke: generated legacy render, modern extraction/tick, protected render/extract hooks, stock finish lifecycle, total progress, game-thread removal recovery, renderer release ordering and competing-overlay preservation");
 }
}
