package shanghan.client.routing;
import shanghan.early.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
/** GUI routing is based on the installed method shapes, rather than a list of Forge patch numbers. */
public final class ClientOverlayRouter {
 public static void installDetails(MediaSession media){UniversalOverlay.installDetails(media);}
 public static Supplier<?> create(Supplier<?> minecraft,Supplier<?> reload,Consumer<Optional<Throwable>> finish,MediaSession media,VideoRenderer renderer){
  try(var in=ClientOverlayRouter.class.getResourceAsStream("/shanghan/client/runtime.json")){RuntimeProfile.requireClient(in);}catch(Exception error){throw new IllegalStateException(error);}
  Object mc=minecraft.get(),ri=reload.get();
  if(legacyCompatible(mc,ri)){
   media.log("OVERLAY_ADAPTER legacy-shared-gl");
   return shanghan.client.ShangHanOverlay.create(minecraft,reload,finish,media,renderer);
  }
  media.nativeWindowFade=true;
  media.log("OVERLAY_ADAPTER generated-lifecycle");
  return UniversalOverlay.create(minecraft,reload,finish,media,renderer);
 }
 private static boolean legacyCompatible(Object mc,Object reload){
  // Exact, complete legacy contract. A missing member selects the generic bridge before loading the legacy subclass.
  try{
   ClassLoader loader=mc.getClass().getClassLoader();
   Class<?> minecraft=Class.forName("net.minecraft.client.Minecraft",false,loader);
   Class<?> overlay=Class.forName("net.minecraft.client.gui.screens.Overlay",false,loader);
   Class<?> loading=Class.forName("net.minecraft.client.gui.screens.LoadingOverlay",false,loader);
   Class<?> graphics=Class.forName("net.minecraft.client.gui.GuiGraphics",false,loader);
   Class<?> screen=Class.forName("net.minecraft.client.gui.screens.Screen",false,loader);
   Class<?> reloadType=Class.forName("net.minecraft.server.packs.resources.ReloadInstance",false,loader);
   minecraft.getField("f_91080_");
   Class<?> window=minecraft.getMethod("m_91268_").getReturnType();
   for(String name:new String[]{"m_85441_","m_85442_","m_85445_","m_85446_"})if(window.getMethod(name).getReturnType()!=int.class)return false;
   minecraft.getMethod("m_91150_",overlay);
   loading.getConstructor(minecraft,reloadType,Consumer.class,boolean.class);
   loading.getMethod("m_88315_",graphics,int.class,int.class,float.class);
   loading.getMethod("m_7859_");
   screen.getMethod("m_6574_",minecraft,int.class,int.class);
   screen.getMethod("m_88315_",graphics,int.class,int.class,float.class);
   graphics.getMethod("m_280262_");
   reloadType.getMethod("m_7750_");reloadType.getMethod("m_7746_");reloadType.getMethod("m_7748_");
   return minecraft.isInstance(mc)&&reloadType.isInstance(reload);
  }catch(ReflectiveOperationException|LinkageError unavailable){return false;}
 }
 private ClientOverlayRouter(){}
}
