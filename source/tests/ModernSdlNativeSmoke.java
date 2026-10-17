import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryStack;
import shanghan.early.MediaSession;
import shanghan.early.Settings;
import shanghan.early.VideoRenderer;
import shanghan.modern.boot.NativeFadeSurface;
import shanghan.modern.boot.SdlEmbeddedWindow;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLProperties.*;
import static org.lwjgl.sdl.SDLVideo.*;

/**
 * Real SDL/OpenGL regression with two owned windows and actual decoder cleanup.
 * It never inspects or manipulates another application's windows.
 */
public final class ModernSdlNativeSmoke {
 public static void main(String[] args) throws Exception {
  if(args.length!=3)throw new IllegalArgumentException("Expected fixture, FFmpeg binary, and video");
  Path fixture=Path.of(args[0]);Files.createDirectories(fixture);
  Path binary=fixture.resolve("ffmpeg.exe"),video=fixture.resolve("loading.mp4");
  linkOrCopy(binary,Path.of(args[1]));linkOrCopy(video,Path.of(args[2]));
  Settings settings=new Settings();settings.ffmpeg="ffmpeg.exe";settings.video="loading.mp4";
  settings.audio=false;settings.maximized=false;settings.fadeOutMs=200;
  settings.width=640;settings.height=360;settings.fps=30;
  MediaSession media=null;List<ProcessHandle> decoders=new ArrayList<>();
  NativeFadeSurface surface=null;
  long parent=0,child=0,parentContext=0,childContext=0;
  int parentId=0,childId=0;
  boolean destroyedParent=false,destroyedChild=false;
  User32 user=User32.INSTANCE;
  try {
   if(!SDL_Init(SDL_INIT_VIDEO))throw new AssertionError("SDL_Init failed");
   SDL_GL_ResetAttributes();
   SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION,3);
   SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION,3);
   SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK,SDL_GL_CONTEXT_PROFILE_CORE);
   SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER,1);
   SDL_GL_SetAttribute(SDL_GL_ALPHA_SIZE,0);
   parent=SDL_CreateWindow("Forge Loading Framework SDL regression",640,360,
     SDL_WINDOW_OPENGL|SDL_WINDOW_HIDDEN|SDL_WINDOW_RESIZABLE|SDL_WINDOW_HIGH_PIXEL_DENSITY);
   require(parent!=0,"Parent SDL window failed");parentId=SDL_GetWindowID(parent);
   parentContext=SDL_GL_CreateContext(parent);require(parentContext!=0,"Parent GL context failed");
   GLCapabilities parentCaps=GL.createCapabilities();
   child=SDL_CreateWindow("Forge Loading Framework owned video drawable",640,360,
     SDL_WINDOW_OPENGL|SDL_WINDOW_HIDDEN|SDL_WINDOW_RESIZABLE|SDL_WINDOW_HIGH_PIXEL_DENSITY);
   require(child!=0,"Child SDL window failed");childId=SDL_GetWindowID(child);
   childContext=SDL_GL_CreateContext(child);require(childContext!=0,"Child GL context failed");
   GLCapabilities childCaps=GL.createCapabilities();
   long parentHwnd=hwnd(parent),childHwnd=hwnd(child);
   HWND parentNative=handle(parentHwnd),childNative=handle(childHwnd);
   int originalParentEx=user.GetWindowLong(parentNative,-20);
   assertOpaqueParent(user,parentNative,parent,originalParentEx);
   require(SDL_ShowWindow(child),"Showing early video window failed");
   require(SDL_MaximizeWindow(child),"Maximizing early video window failed");
   require(SDL_SyncWindow(child),"Early video maximize did not settle");
   SdlEmbeddedWindow.prepare(child);
   require((SDL_GetWindowFlags(child)&SDL_WINDOW_BORDERLESS)!=0,"SDL cached borderless flag is missing");
   require((SDL_GetWindowFlags(child)&(SDL_WINDOW_MAXIMIZED|SDL_WINDOW_MINIMIZED))==0,"SDL child state is still maximized");
   surface=new NativeFadeSurface(childHwnd,parentHwnd);surface.attach();
   require(surface.isAttached(),"Video drawable was not embedded");
   require((user.GetWindowLong(childNative,-16)&0x40000000)!=0,"Video is not a child drawable");
   require(user.GetParent(childNative).equals(parentNative),"Unexpected video parent");
   require(topLevelCount(user,parentHwnd,childHwnd)==1,"Owned windows did not merge into one top-level window");
   SDL_ShowWindow(parent);pump();surface.fit();assertBorderless(user,childNative);
   media=new MediaSession(fixture,settings);media.detached=true;media.start();
   long decodeDeadline=System.nanoTime()+5_000_000_000L;
   while(media.decoded==0&&!media.videoFailed&&System.nanoTime()<decodeDeadline) {
    pump();Thread.sleep(20);
   }
   require(media.decoded>0&&!media.videoFailed,"The real FFmpeg video decoder produced no frames");
   collectDecoder(media,"videoProcess",decoders);
   require(!decoders.isEmpty()&&decoders.stream().anyMatch(ProcessHandle::isAlive),
     "No live decoder process was observed");
   for(float opacity:new float[]{1,.5f,0}) {
    surface.opacity(opacity);
    ByteByReference actualAlpha=new ByteByReference();IntByReference flags=new IntByReference();
    require(user.GetLayeredWindowAttributes(childNative,new IntByReference(),actualAlpha,flags),
      "Could not read back the child's native alpha");
    require((actualAlpha.getValue()&255)==NativeFadeSurface.opacityByte(opacity),
      "Native alpha differs from the requested fade");
    assertOpaqueParent(user,parentNative,parent,originalParentEx);
    for(int frame=0;frame<4;frame++) {
     pump();
     draw(parent,parentContext,parentCaps,0.05f,0.15f,.85f,false);
     draw(child,childContext,childCaps,.85f,.10f,.05f,true);
     Thread.sleep(20);
    }
   }
   int previousWidth=surface.width(),previousHeight=surface.height();
   require(SDL_SetWindowSize(parent,800,450),"SDL parent resize failed");
   for(int i=0;i<8;i++){pump();surface.fit();Thread.sleep(15);}
   RECT rect=new RECT();require(user.GetClientRect(parentNative,rect),"Parent client bounds unavailable");
   require(surface.width()==rect.right-rect.left&&surface.height()==rect.bottom-rect.top,
     "The child did not follow its parent's resized client bounds");
   require(surface.width()!=previousWidth||surface.height()!=previousHeight,"Resize produced no dimension change");
   assertOpaqueParent(user,parentNative,parent,originalParentEx);
   require(topLevelCount(user,parentHwnd,childHwnd)==1,"Resize detached a second top-level video window");
   require(SDL_MaximizeWindow(parent),"Game maximize failed");
   require(SDL_SyncWindow(parent),"Game maximize did not settle");pump();surface.fit();assertBorderless(user,childNative);
   require(SDL_RestoreWindow(parent),"Game restore failed");
   require(SDL_SyncWindow(parent),"Game restore did not settle");pump();surface.fit();assertBorderless(user,childNative);
   require(SDL_HideWindow(parent),"Game hide failed");pump();
   require(SDL_ShowWindow(parent),"Game show failed");pump();surface.fit();assertBorderless(user,childNative);
   require(topLevelCount(user,parentHwnd,childHwnd)==1,"Window state changes created a second top-level video");
   surface.opacity(1);
   require(SDL_GL_MakeCurrent(child,childContext),"Video texture context switch failed");
   GL.setCapabilities(childCaps);
   VideoRenderer renderer=new VideoRenderer(media);
   try {
    long textureDeadline=System.nanoTime()+3_000_000_000L;
    while(media.presented==0&&System.nanoTime()<textureDeadline) {
     renderer.render(surface.width(),surface.height(),1);
     require(glGetError()==GL_NO_ERROR,"Shared video renderer generated an OpenGL error on SDL");
     require(SDL_GL_SwapWindow(child),"Shared video renderer could not present on SDL");
     pump();Thread.sleep(20);
    }
    require(media.presented>0,"Shared video renderer did not upload a decoded H.264 frame on SDL");
   } finally {renderer.close();}
   media.markRenderReleased();
   require(media.readyForWindowClose(),"Renderer/decoder release did not complete its close barrier");
   SDL_GL_MakeCurrent(child,0);GL.setCapabilities(null);
   require(SDL_GL_DestroyContext(childContext),"Video context was not released");childContext=0;
   surface.restoreParent();SDL_DestroyWindow(child);child=0;destroyedChild=true;
   require(SDL_GetWindowFromID(childId)==0,"Destroyed SDL child is still registered");
   assertOpaqueParent(user,parentNative,parent,originalParentEx);
   require(SDL_GL_DestroyContext(parentContext),"Game context was not released");parentContext=0;
   SDL_DestroyWindow(parent);parent=0;destroyedParent=true;
   require(SDL_GetWindowFromID(parentId)==0,"Destroyed SDL parent is still registered");
  } finally {
   if(media!=null){media.close();media.finishLogging();}
   if(child!=0) {
    SDL_GL_MakeCurrent(child,0);GL.setCapabilities(null);
    if(childContext!=0)SDL_GL_DestroyContext(childContext);
    if(surface!=null)surface.restoreParent();
    SDL_DestroyWindow(child);
   }
   if(parent!=0) {
    if(parentContext!=0)SDL_GL_DestroyContext(parentContext);
    SDL_DestroyWindow(parent);
   }
   SDL_QuitSubSystem(SDL_INIT_VIDEO);
  }
  long deadline=System.nanoTime()+3_000_000_000L;
  while(decoders.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)Thread.sleep(20);
  require(decoders.stream().noneMatch(ProcessHandle::isAlive),"A decoder process remained alive after shutdown");
  require(destroyedChild&&destroyedParent,"Native windows did not complete normal teardown");
  System.out.println("ModernSdlNativeSmoke PASS real SDL/OpenGL contexts presented and verified owned pixels; "
    +"SDL flags and Win32 borderless client area after early maximize, game maximize/restore/hide/show; one top-level window, child native alpha 255/128/0, live resize, opaque parent, "
    +"shared video renderer uploaded H.264, both windows/contexts released, and actual FFmpeg decoder processes exited.");
 }
 private static void assertBorderless(User32 user,HWND child){
  require((user.GetWindowLong(child,-16)&0x00CF0000)==0,"SDL video native caption/resize decorations returned");
  require((user.GetWindowLong(child,-20)&0x00020341)==0,"SDL video native extended frame decorations remain");
  RECT client=new RECT(),outer=new RECT();
  require(user.GetClientRect(child,client)&&user.GetWindowRect(child,outer),"Video client bounds unavailable");
  require(client.right-client.left==outer.right-outer.left&&client.bottom-client.top==outer.bottom-outer.top,
    "SDL video client area has non-client border: client="+(client.right-client.left)+"x"+(client.bottom-client.top)+" outer="+(outer.right-outer.left)+"x"+(outer.bottom-outer.top));
 }
 private static void draw(long window,long context,GLCapabilities capabilities,float red,float green,float blue,
     boolean expectRed) {
  require(SDL_GL_MakeCurrent(window,context),"SDL context switch failed");GL.setCapabilities(capabilities);
  try(MemoryStack stack=MemoryStack.stackPush()) {
   var w=stack.mallocInt(1);var h=stack.mallocInt(1);
   require(SDL_GetWindowSizeInPixels(window,w,h),"SDL framebuffer dimensions unavailable");
   glViewport(0,0,w.get(0),h.get(0));glClearColor(red,green,blue,1);glClear(GL_COLOR_BUFFER_BIT);
   ByteBuffer pixel=stack.malloc(4);glReadPixels(Math.max(0,w.get(0)/2),Math.max(0,h.get(0)/2),1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
   int actualRed=pixel.get(0)&255,actualBlue=pixel.get(2)&255;
   require(expectRed?actualRed>150&&actualBlue<100:actualBlue>150&&actualRed<100,
     "Unexpected pixels in an owned OpenGL framebuffer");
   require(SDL_GL_SwapWindow(window),"SDL frame presentation failed");
  }
 }
 private static void assertOpaqueParent(User32 user,HWND parent,long sdlWindow,int originalEx) {
  require(user.GetWindowLong(parent,-20)==originalEx,"Top-level game extended styles changed");
  require((originalEx&0x00080000)==0,"Top-level game window is layered");
  require((SDL_GetWindowFlags(sdlWindow)&SDL_WINDOW_TRANSPARENT)==0,"Top-level game window is transparent");
 }
 private static int topLevelCount(User32 user,long parent,long child) {
  int[] count={0};
  user.EnumWindows((window,data)->{
   long value=Pointer.nativeValue(window.getPointer());
   if(value==parent||value==child)count[0]++;
   return true;
  },null);
  return count[0];
 }
 private static void pump(){try(MemoryStack stack=MemoryStack.stackPush()){var event=SDL_Event.malloc(stack);while(SDL_PollEvent(event)){} }}
 private static long hwnd(long sdlWindow){return SDL_GetPointerProperty(SDL_GetWindowProperties(sdlWindow),SDL_PROP_WINDOW_WIN32_HWND_POINTER,0);}
 private static HWND handle(long value){require(value!=0,"SDL HWND is unavailable");return new HWND(Pointer.createConstant(value));}
 private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 private static void collectDecoder(MediaSession media,String name,List<ProcessHandle> result)throws Exception{
  Field field=MediaSession.class.getDeclaredField(name);field.setAccessible(true);
  Process process=(Process)field.get(media);if(process!=null)result.add(process.toHandle());
 }
 private static void linkOrCopy(Path destination,Path source)throws Exception{
  if(Files.exists(destination))return;
  try{Files.createLink(destination,source);}catch(Exception unsupported){Files.copy(source,destination,StandardCopyOption.COPY_ATTRIBUTES);}
 }
}
