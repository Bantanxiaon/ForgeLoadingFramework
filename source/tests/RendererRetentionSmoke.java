import shanghan.early.*;
import java.nio.*;import java.nio.file.*;import java.util.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.glfw.GLFW.*;
public final class RendererRetentionSmoke{
 public static void main(String[] args)throws Exception{
  if(!glfwInit())throw new AssertionError("GLFW");
  glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,2);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
  long w=glfwCreateWindow(64,64,"Renderer retention test",0,0);if(w==0)throw new AssertionError("GL window");
  glfwMakeContextCurrent(w);GL.createCapabilities();
  Settings s=new Settings();s.width=320;s.height=180;s.audio=false;s.title="Test";
  MediaSession m=new MediaSession(Path.of(args[0]),s);VideoRenderer r=new VideoRenderer(m);
  byte[] frame=new byte[s.width*s.height*3/2];int y=s.width*s.height;
  Arrays.fill(frame,0,y,(byte)81);Arrays.fill(frame,y,y+y/4,(byte)90);Arrays.fill(frame,y+y/4,frame.length,(byte)240);
  m.frames.add(new MediaSession.Frame(0,frame));
  glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
  r.render(64,64,1);byte[] a=sample();
  if((sampleAt(32,10)[3]&255)!=255)throw new AssertionError("UI made the framebuffer transparent");
  // The renderer restores pre-existing bindings after each draw.
  // The next draw has no new frame and must rebind the retained chroma planes.
  glEnable(GL_FRAMEBUFFER_SRGB);r.render(64,64,1);byte[] b=sample();
  if(!glIsEnabled(GL_FRAMEBUFFER_SRGB))throw new AssertionError("sRGB state not restored");
  if(!Arrays.equals(a,b))throw new AssertionError("Repeated frame color changed: "+Arrays.toString(a)+" -> "+Arrays.toString(b));
  if((a[0]&255)<200||(a[1]&255)>60||(a[2]&255)>60)throw new AssertionError("BT709 test red incorrect: "+Arrays.toString(a));
  System.out.println("PASS repeated frame colors stable: "+Arrays.toString(a));
  r.close();glfwDestroyWindow(w);glfwTerminate();
 }
 static byte[] sample(){return sampleAt(32,60);}
 static byte[] sampleAt(int x,int y){ByteBuffer b=org.lwjgl.system.MemoryUtil.memAlloc(4);glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b);byte[] a=new byte[4];b.get(a);org.lwjgl.system.MemoryUtil.memFree(b);return a;}
}
