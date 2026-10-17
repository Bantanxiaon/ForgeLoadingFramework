import shanghan.early.WindowSurface;
import com.sun.jna.Pointer;import com.sun.jna.platform.win32.WinDef.RECT;import com.sun.jna.platform.win32.User32;import com.sun.jna.platform.win32.WinDef.HWND;
import static org.lwjgl.glfw.GLFW.*;import static org.lwjgl.glfw.GLFWNativeWin32.*;
public final class WindowEmbeddingSmoke {
 public static void main(String[] args){
  if(!glfwInit())throw new AssertionError("GLFW init");
  long child=0,parent=0;
  try{
   glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
   child=glfwCreateWindow(640,360,"Loading surface",0,0);parent=glfwCreateWindow(800,450,"Minecraft parent",0,child);
   if(child==0||parent==0)throw new AssertionError("Window creation");
   glfwShowWindow(child);glfwMaximizeWindow(child);glfwPollEvents();
   WindowSurface surface=new WindowSurface(child,parent);surface.attach();glfwShowWindow(parent);glfwPollEvents();
   if(!surface.isAttached())throw new AssertionError("Not a child drawable");
   if(glfwGetWindowAttrib(child,GLFW_DECORATED)!=GLFW_FALSE||glfwGetWindowAttrib(child,GLFW_RESIZABLE)!=GLFW_FALSE)
    throw new AssertionError("GLFW cached video decorations were not removed");
   HWND childH=new HWND(Pointer.createConstant(glfwGetWin32Window(child))),parentH=new HWND(Pointer.createConstant(glfwGetWin32Window(parent)));
   int[] count={0};User32.INSTANCE.EnumWindows((hw,data)->{if(hw.equals(childH)||hw.equals(parentH))count[0]++;return true;},null);
   if(count[0]!=1)throw new AssertionError("Two top-level windows "+count[0]);
   glfwSetWindowSize(parent,960,540);surface.fit();glfwPollEvents();int[] w={0},h={0};glfwGetFramebufferSize(child,w,h);
   if(w[0]!=960||h[0]!=540)throw new AssertionError("Embedded surface resize "+w[0]+"x"+h[0]);
   assertBorderless(childH);
   glfwMaximizeWindow(parent);glfwPollEvents();surface.fit();assertBorderless(childH);
   glfwRestoreWindow(parent);glfwPollEvents();surface.fit();assertBorderless(childH);
   surface.restoreParent();if(!User32.INSTANCE.IsWindowVisible(parentH))throw new AssertionError("Parent hidden while restoring clipping style");
   System.out.println("PASS native embedded loading drawable, exactly one top-level window, shared GL parent, resize 960x540, cached decoration flags and actual borderless client area after maximize/restore, visible parent");
  }finally{if(child!=0)glfwDestroyWindow(child);if(parent!=0)glfwDestroyWindow(parent);glfwTerminate();}
 }
 private static void assertBorderless(HWND child){
  User32 user=User32.INSTANCE;
  if((user.GetWindowLong(child,-16)&0x00CF0000)!=0||(user.GetWindowLong(child,-20)&0x00020341)!=0)
   throw new AssertionError("Embedded GLFW window has native frame decorations");
  RECT outer=new RECT(),client=new RECT();
  if(!user.GetWindowRect(child,outer)||!user.GetClientRect(child,client)
    ||outer.right-outer.left!=client.right-client.left||outer.bottom-outer.top!=client.bottom-client.top)
   throw new AssertionError("Embedded GLFW client area has a non-client border");
 }
}
