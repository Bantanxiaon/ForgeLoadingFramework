package shanghan.early;
import java.lang.reflect.*;
import java.util.*;
/** Loader metadata is read only after Forge has published the game module layer. */
public final class LoaderIntrospection {
 private static final Method ACCESS=findAccess();
 private static Method findAccess(){
  ClassLoader loader=LoaderIntrospection.class.getClassLoader();
  try{
   return Class.forName("net.minecraftforge.fml.loading.FMLLoader",false,loader).getMethod("getLoadingModList");
  }catch(ReflectiveOperationException|LinkageError ignored){}
  try{
   Method method=Class.forName("net.minecraftforge.fml.loading.LoadingModList",false,loader).getMethod("getMods");
   return Modifier.isStatic(method.getModifiers())?method:null;
  }catch(ReflectiveOperationException|LinkageError ignored){return null;}
 }
 public static boolean registerNames(MediaSession media,boolean gameLayerReady){
  // Modern LoadingModList uses a lazy static holder. Calling getMods before ModSorter
  // publishes the list poisons that holder permanently; catching its exception is too late.
  if(!gameLayerReady)return false;
  if(ACCESS==null)return true;
  try{
   Object found=ACCESS.invoke(null);
   if(found==null)return false;
   Object mods=found instanceof Collection<?>?found:ACCESS.getReturnType().getMethod("getMods").invoke(found);
   if(!(mods instanceof Collection<?> collection)||collection.isEmpty())return false;
   for(Object info:collection){
    Method id=info.getClass().getMethod("getModId"),display=info.getClass().getMethod("getDisplayName");
    media.tasks.name(String.valueOf(id.invoke(info)),String.valueOf(display.invoke(info)));
   }
   media.progress.mods(collection.size());return true;
  }catch(ReflectiveOperationException|LinkageError ignored){return false;}
 }
 private LoaderIntrospection(){}
}
