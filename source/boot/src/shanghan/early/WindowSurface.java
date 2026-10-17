package shanghan.early;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import org.lwjgl.glfw.GLFWNativeWin32;
/** Own-process child drawable: loading and Minecraft use separate GL contexts inside one top-level window. */
public final class WindowSurface {
 private final HWND child,parent;
 private final long childWindow;
 private int width=-1,height=-1,childStyle,childExStyle,parentStyle;
 private final RECT bounds=new RECT();
 private final User32 user=User32.INSTANCE;
 public WindowSurface(long childWindow,long parentWindow){
  this.childWindow=childWindow;
  child=new HWND(Pointer.createConstant(GLFWNativeWin32.glfwGetWin32Window(childWindow)));
  parent=new HWND(Pointer.createConstant(GLFWNativeWin32.glfwGetWin32Window(parentWindow)));
 }
 public static void prepareChild(long window){
  org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);
  org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_RESIZABLE,org.lwjgl.glfw.GLFW.GLFW_FALSE);
  org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_DECORATED,org.lwjgl.glfw.GLFW.GLFW_FALSE);
  if(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_DECORATED)!=org.lwjgl.glfw.GLFW.GLFW_FALSE)
   throw new IllegalStateException("GLFW video decorations remain enabled");
 }
 public void attach(){
  prepareChild(childWindow);
  childStyle=user.GetWindowLong(child,-16);childExStyle=user.GetWindowLong(child,-20);parentStyle=user.GetWindowLong(parent,-16);
  int style=childStyle;
  style=(style&~(0x80000000|0x00C00000|0x000F0000|0x01000000|0x20000000))|0x40000000|0x10000000|0x04000000;
  user.SetWindowLong(child,-16,style);
  user.SetWindowLong(child,-20,user.GetWindowLong(child,-20)&~(0x00040000|0x00000080|0x00000008|0x00000001|0x00000040|0x00000100|0x00000200|0x00020000));
  user.SetWindowLong(parent,-16,user.GetWindowLong(parent,-16)|0x02000000);
  user.SetParent(child,parent);
  if(!parent.equals(user.GetParent(child)))throw new IllegalStateException("Loading surface could not attach to Minecraft");
  if(!user.SetWindowPos(child,null,0,0,0,0,0x0037))throw new IllegalStateException("Loading surface frame update failed");
  fit();
 }
 public void fit(){
  if(!user.GetClientRect(parent,bounds))return;
  int w=Math.max(1,bounds.right-bounds.left),h=Math.max(1,bounds.bottom-bounds.top);
  if(w==width&&h==height)return;
  if(user.SetWindowPos(child,null,0,0,w,h,0x0014)){width=w;height=h;}
 }
 public void restoreParent(){int current=user.GetWindowLong(parent,-16);user.SetWindowLong(parent,-16,(current&~0x02000000)|(parentStyle&0x02000000));}
 public void rollback(){user.SetParent(child,null);user.SetWindowLong(child,-16,childStyle);user.SetWindowLong(child,-20,childExStyle);restoreParent();user.SetWindowPos(child,null,0,0,0,0,0x0037);}
 public boolean isAttached(){return parent.equals(user.GetParent(child))&&(user.GetWindowLong(child,-16)&0x40000000)!=0;}
}
