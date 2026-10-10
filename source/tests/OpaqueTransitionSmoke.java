import shanghan.early.*;
import java.nio.*;import java.nio.file.*;import java.util.*;
import org.lwjgl.opengl.GL;import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL32C.*;import static org.lwjgl.glfw.GLFW.*;
public final class OpaqueTransitionSmoke{
 public static void main(String[] args)throws Exception{
  if(!glfwInit())throw new AssertionError("GLFW");
  glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER,GLFW_FALSE);
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,2);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
  long early=glfwCreateWindow(64,64,"Opaque loading test",0,0),game=glfwCreateWindow(64,64,"Menu test",0,early);
  if(early==0||game==0)throw new AssertionError("GL windows");
  if(glfwGetWindowAttrib(early,GLFW_TRANSPARENT_FRAMEBUFFER)!=GLFW_FALSE)throw new AssertionError("Native window transparency");
  glfwMakeContextCurrent(early);var caps=GL.createCapabilities();
  Settings settings=new Settings();settings.width=320;settings.height=180;settings.audio=false;settings.title="Test";
  MediaSession media=new MediaSession(Path.of(args[0]),settings);media.detached=true;VideoRenderer renderer=new VideoRenderer(media);
  byte[] frame=new byte[320*180*3/2];int y=320*180;
  Arrays.fill(frame,0,y,(byte)81);Arrays.fill(frame,y,y+y/4,(byte)90);Arrays.fill(frame,y+y/4,frame.length,(byte)240);
  media.frames.add(new MediaSession.Frame(0,frame));
  glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);renderer.render(64,64,1);
  if((sample(32,10)[3]&255)!=255)throw new AssertionError("Loading UI alpha");
  glfwMakeContextCurrent(game);GL.createCapabilities();
  glClearColor(0,0,1,1);glClear(GL_COLOR_BUFFER_BIT);
  renderer.captureMenu(64,64);
  glfwMakeContextCurrent(early);GL.setCapabilities(caps);
  for(float opacity:new float[]{1,.5f,0}){
   glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);renderer.render(64,64,opacity);
   byte[] top=sample(32,60),bottom=sample(32,10);
   if((top[3]&255)!=255||(bottom[3]&255)!=255)throw new AssertionError("Transition leaked desktop at "+opacity);
   if(opacity==0&&((top[2]&255)<240||(top[0]&255)>10))throw new AssertionError("Prepared menu not visible");
   if(opacity==.5f&&((top[2]&255)<100||(top[0]&255)<100))throw new AssertionError("Crossfade not composited");
  }
  System.out.println("PASS opaque loading/UI, shared-context menu crossfade, alpha=255 through entire transition.");
  renderer.close();glfwDestroyWindow(game);glfwDestroyWindow(early);glfwTerminate();
 }
 static byte[] sample(int x,int y){ByteBuffer b=MemoryUtil.memAlloc(4);glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b);byte[] v=new byte[4];b.get(v);MemoryUtil.memFree(b);return v;}
}