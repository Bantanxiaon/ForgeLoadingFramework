import shanghan.modern.boot.NativeFadeSurface;
import java.util.HashMap;
import java.util.Map;

public final class ModernNativeFadeSmoke {
 private static final long CHILD=10,PARENT=20;
 private static final int INITIAL_CHILD=0x80CF0000,INITIAL_EX=0x00040080,INITIAL_PARENT=0x02CF0000;
 public static void main(String[] args) {
  if(NativeFadeSurface.opacityByte(1)!=255||NativeFadeSurface.opacityByte(0)!=0
    ||NativeFadeSurface.opacityByte(.5f)!=128||NativeFadeSurface.opacityByte(2)!=255
    ||NativeFadeSurface.opacityByte(-1)!=0||NativeFadeSurface.opacityByte(Float.NaN)!=255)
   throw new AssertionError("Invalid native fade alpha conversion");
  for(long[] pair:new long[][]{{0,PARENT},{CHILD,0},{CHILD,CHILD}}) {
   try{new NativeFadeSurface(pair[0],pair[1],new Fake());throw new AssertionError("Invalid HWND pair accepted");}
   catch(IllegalArgumentException expected){}
  }
  Fake calls=new Fake();NativeFadeSurface surface=new NativeFadeSurface(CHILD,PARENT,calls);
  surface.attach();
  if(!surface.isAttached()||calls.parents.get(CHILD)!=PARENT||calls.lastAlpha!=255)
   throw new AssertionError("Opaque embedded surface missing");
  if((calls.style(CHILD,-16)&0x40000000)==0||(calls.style(CHILD,-20)&0x00080000)==0)
   throw new AssertionError("Native child styles missing");
  if(calls.style(PARENT,-20)!=0x100)
   throw new AssertionError("Parent top-level transparency style was changed");
  int opacityCalls=calls.opacityCalls,positionCalls=calls.positionCalls;
  surface.opacity(1);surface.fit();
  if(calls.opacityCalls!=opacityCalls||calls.positionCalls!=positionCalls)
   throw new AssertionError("Unchanged surface generated redundant native calls");
  surface.opacity(.5f);
  if(calls.lastAlpha!=128)throw new AssertionError("Video child did not fade");
  calls.width=1024;calls.height=576;surface.fit();
  if(surface.width()!=1024||surface.height()!=576)
   throw new AssertionError("Surface did not track the live game dimensions");
  calls.style(PARENT,-16,calls.style(PARENT,-16)|0x100);
  surface.restoreParent();
  if(calls.style(PARENT,-16)!=(INITIAL_PARENT|0x100))
   throw new AssertionError("Parent style restoration lost unrelated changes");
  surface.rollback();
  if(surface.isAttached()||calls.parent(CHILD)!=0||calls.style(CHILD,-16)!=INITIAL_CHILD
    ||calls.style(CHILD,-20)!=INITIAL_EX||calls.style(PARENT,-16)!=(INITIAL_PARENT|0x100))
   throw new AssertionError("Surface rollback did not restore its native state");
  Fake failing=new Fake();failing.rejectAlpha=true;
  try{new NativeFadeSurface(CHILD,PARENT,failing).attach();throw new AssertionError("Native alpha failure ignored");}
  catch(IllegalStateException expected){}
  if(failing.parent(CHILD)!=0||failing.style(CHILD,-16)!=INITIAL_CHILD
    ||failing.style(CHILD,-20)!=INITIAL_EX||failing.style(PARENT,-16)!=INITIAL_PARENT)
   throw new AssertionError("Partial attachment was not rolled back");
  System.out.println("ModernNativeFadeSmoke PASS opaque initial child, alpha fade, resize, native-call deduplication, "
    +"parent preservation, and failure rollback; no native window was opened.");
 }
 private static final class Fake implements NativeFadeSurface.Calls {
  final Map<Long,Map<Integer,Integer>> styles=new HashMap<>();
  final Map<Long,Long> parents=new HashMap<>();
  int width=640,height=360,lastAlpha=-1,opacityCalls,positionCalls;boolean rejectAlpha;
  Fake(){
   styles.put(CHILD,new HashMap<>(Map.of(-16,INITIAL_CHILD,-20,INITIAL_EX)));
   styles.put(PARENT,new HashMap<>(Map.of(-16,INITIAL_PARENT,-20,0x100)));parents.put(CHILD,0L);
  }
  public int style(long hwnd,int index){return styles.get(hwnd).get(index);}
  public void style(long hwnd,int index,int value){styles.get(hwnd).put(index,value);}
  public long parent(long hwnd){return parents.getOrDefault(hwnd,0L);}
  public void parent(long child,long parent){parents.put(child,parent);}
  public boolean bounds(long hwnd,int[] result){result[0]=0;result[1]=0;result[2]=width;result[3]=height;return true;}
  public boolean position(long hwnd,int width,int height,int flags){positionCalls++;return true;}
  public boolean alpha(long hwnd,int value){opacityCalls++;lastAlpha=value;return !rejectAlpha;}
 }
}
