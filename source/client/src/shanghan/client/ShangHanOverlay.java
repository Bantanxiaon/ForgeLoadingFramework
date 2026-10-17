package shanghan.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import shanghan.early.*;
import java.util.*;
import java.util.function.*;
/** SRG names target the exact Forge 1.20.1 production runtime; no game bytecode is transformed. */
public final class ShangHanOverlay extends LoadingOverlay {
 private final Minecraft mc;
 private final ReloadInstance reload;
 private final Consumer<Optional<Throwable>> finish;
 private final MediaSession media;
 private final VideoRenderer renderer;
 private long fadeStart,lastProgressPoll;
 private boolean finished,removed,fadeBegun;
 public ShangHanOverlay(Minecraft mc,ReloadInstance reload,Consumer<Optional<Throwable>> finish,MediaSession media,VideoRenderer renderer){
  super(mc,reload,finish,false);this.mc=mc;this.reload=reload;this.finish=finish;this.media=media;this.renderer=renderer;
 }
 public static void installDetails(MediaSession media){DetailBridge.install(media);}
 public static Supplier<LoadingOverlay> create(Supplier<?> mc,Supplier<?> ri,Consumer<Optional<Throwable>> finish,MediaSession media,VideoRenderer renderer){
  try(var in=ShangHanOverlay.class.getResourceAsStream("/shanghan/client/runtime.json")){RuntimeProfile.requireClient(in);}catch(Exception e){throw new IllegalStateException(e);}
  return ()->new ShangHanOverlay((Minecraft)mc.get(),(ReloadInstance)ri.get(),finish,media,renderer);
 }
 @Override public void m_88315_(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
  if(removed)return;media.fitWindow.run();
  long now=System.nanoTime();if(now-lastProgressPoll>=100_000_000L){media.progress.tick();media.progress.resource(reload.m_7750_());lastProgressPoll=now;}
  if(!finished&&reload.m_7746_()){
   finished=true;
   try{reload.m_7748_();finish.accept(Optional.empty());media.progress.complete();fadeStart=System.nanoTime();if(!media.detached)media.log("MINECRAFT_READY clock="+media.clock());}
   catch(Throwable e){finish.accept(Optional.of(e));remove();return;}
   if(mc.f_91080_!=null)mc.f_91080_.m_6574_(mc,mc.m_91268_().m_85445_(),mc.m_91268_().m_85446_());
  }
  float alpha=finished?Math.max(0,1-(System.nanoTime()-fadeStart)/(media.settings.fadeOutMs*1_000_000f)):1;
  if(finished&&mc.f_91080_!=null)mc.f_91080_.m_88315_(graphics,mouseX,mouseY,partialTick);
  graphics.m_280262_();
  if(media.detached){
   if(finished&&!fadeBegun){
    renderer.captureMenu(mc.m_91268_().m_85441_(),mc.m_91268_().m_85442_());fadeBegun=true;media.showGame.run();media.beginFade();media.log("MINECRAFT_READY clock="+media.clock());
   }
   if(finished&&media.readyForWindowClose()){media.closeWindow.run();mc.m_91150_(null);removed=true;media.log("OVERLAY_CLOSED");media.finishLogging();}
   return;
  }
  media.fade(alpha);
  renderer.render(mc.m_91268_().m_85441_(),mc.m_91268_().m_85442_(),alpha);
  if(finished&&alpha<=0)remove();
 }
 private void remove(){if(removed)return;removed=true;if(media.detached){media.abortLoading.run();}else renderer.close();mc.m_91150_(null);media.log("OVERLAY_CLOSED");media.finishLogging();}
 @Override public boolean m_7859_(){return !finished;}
}
