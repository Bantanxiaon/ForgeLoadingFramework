package shanghan.modern.boot;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import java.util.Objects;

/**
 * An opaque video drawable embedded in the game's own Win32 window.
 * Inputs are HWND values, so GLFW and SDL adapters can share this implementation.
 * Only the child's opacity changes during the final fade; desktop transparency
 * is never enabled on the game's top-level window.
 */
public final class NativeFadeSurface {
 private static final int STYLE=-16, EX_STYLE=-20;
 private static final int CHILD=0x40000000, VISIBLE=0x10000000;
 private static final int CLIP_CHILDREN=0x02000000, LAYERED=0x00080000;
 private final long child,parent;
 private final Calls calls;
 private final int[] bounds=new int[4];
 private int width=-1,height=-1,childStyle,childExStyle,parentStyle,lastAlpha=-1;
 private long previousParent;
 private boolean attached,stylesSaved;

 /** Injectable boundary for deterministic lifecycle checks without native windows. */
 public interface Calls {
  int style(long hwnd,int index);
  void style(long hwnd,int index,int value);
  long parent(long hwnd);
  void parent(long child,long parent);
  boolean bounds(long hwnd,int[] rectangle);
  boolean position(long hwnd,int width,int height,int flags);
  boolean alpha(long hwnd,int value);
 }
 public NativeFadeSurface(long childHwnd,long parentHwnd) {
  this(childHwnd,parentHwnd,new WindowsCalls());
 }
 public NativeFadeSurface(long childHwnd,long parentHwnd,Calls calls) {
  if(childHwnd==0||parentHwnd==0||childHwnd==parentHwnd)
   throw new IllegalArgumentException("Distinct nonzero HWND values are required");
  this.child=childHwnd;this.parent=parentHwnd;this.calls=Objects.requireNonNull(calls);
 }
 public void attach() {
  if(attached)return;
  childStyle=calls.style(child,STYLE);childExStyle=calls.style(child,EX_STYLE);
  parentStyle=calls.style(parent,STYLE);previousParent=calls.parent(child);stylesSaved=true;
  try {
   int style=(childStyle&~(0x80000000|0x00C00000|0x000F0000|0x01000000|0x20000000))
     |CHILD|VISIBLE;
   calls.style(child,STYLE,style);
   calls.style(child,EX_STYLE,(childExStyle&~(0x00040000|0x00000080|0x00000008|0x00000001|0x00000040|0x00000100|0x00000200|0x00020000))|LAYERED);
   // Allow the parent to keep drawing the live menu underneath the fading child.
   calls.style(parent,STYLE,parentStyle&~CLIP_CHILDREN);
   calls.parent(child,parent);
   if(calls.parent(child)!=parent)throw new IllegalStateException("Video drawable could not attach to Minecraft");
   attached=true;
   opacity(1);
   if(!calls.position(child,1,1,0x0031))
    throw new IllegalStateException("Video drawable frame update failed");
   fit();
  } catch(Throwable error) {
   rollback();
   throw error;
  }
 }
 public void fit() {
  if(!attached||!calls.bounds(parent,bounds))return;
  int w=Math.max(1,bounds[2]-bounds[0]),h=Math.max(1,bounds[3]-bounds[1]);
  if(w==width&&h==height)return;
  if(calls.position(child,w,h,0x0010)){width=w;height=h;}
 }
 public void opacity(float value) {
  if(!attached)return;
  int alpha=opacityByte(value);
  if(alpha==lastAlpha)return;
  if(!calls.alpha(child,alpha))throw new IllegalStateException("Video drawable opacity update failed");
  lastAlpha=alpha;
 }
 /** NaN is kept opaque so invalid progress cannot reveal the desktop. */
 public static int opacityByte(float value) {
  if(!Float.isFinite(value))return value==Float.NEGATIVE_INFINITY?0:255;
  return Math.round(Math.max(0,Math.min(1,value))*255);
 }
 public int width(){return Math.max(1,width);}
 public int height(){return Math.max(1,height);}
 public boolean isAttached(){return attached&&calls.parent(child)==parent;}
 /** Restore the parent's own clipping bit; all unrelated style changes survive. */
 public void restoreParent() {
  if(!stylesSaved)return;
  int current=calls.style(parent,STYLE);
  calls.style(parent,STYLE,(current&~CLIP_CHILDREN)|(parentStyle&CLIP_CHILDREN));
 }
 public void rollback() {
  if(!stylesSaved)return;
  calls.parent(child,previousParent);
  calls.style(child,STYLE,childStyle);calls.style(child,EX_STYLE,childExStyle);
  restoreParent();
  calls.position(child,Math.max(1,width),Math.max(1,height),0x0037);
  attached=false;lastAlpha=-1;
 }
 private static final class WindowsCalls implements Calls {
  private final User32 user=User32.INSTANCE;
  private final RECT rect=new RECT();
  private HWND hwnd(long value){return value==0?null:new HWND(Pointer.createConstant(value));}
  public int style(long window,int index){return user.GetWindowLong(hwnd(window),index);}
  public void style(long window,int index,int value){user.SetWindowLong(hwnd(window),index,value);}
  public long parent(long window){HWND result=user.GetParent(hwnd(window));return result==null?0:Pointer.nativeValue(result.getPointer());}
  public void parent(long window,long parent){user.SetParent(hwnd(window),hwnd(parent));}
  public boolean bounds(long window,int[] result){
   if(!user.GetClientRect(hwnd(window),rect))return false;
   result[0]=rect.left;result[1]=rect.top;result[2]=rect.right;result[3]=rect.bottom;return true;
  }
  public boolean position(long window,int width,int height,int flags){
   return user.SetWindowPos(hwnd(window),null,0,0,width,height,flags);
  }
  public boolean alpha(long window,int value){
   return user.SetLayeredWindowAttributes(hwnd(window),0,(byte)value,2);
  }
 }
}
