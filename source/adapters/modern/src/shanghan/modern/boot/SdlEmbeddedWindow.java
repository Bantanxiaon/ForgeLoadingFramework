package shanghan.modern.boot;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLVideo.*;

/** Keep SDL's cached decorations consistent with the Win32 child drawable.
 * Must run on SDL's main thread, before native reparenting. */
public final class SdlEmbeddedWindow {
 public static void prepare(long window) {
  if(window==0)throw new IllegalArgumentException("Video window is missing");
  // A maximized top-level window must leave that state before it becomes a child.
  require(SDL_RestoreWindow(window),"Restoring the video window");
  require(SDL_SyncWindow(window),"Waiting for video window restoration");
  // SDL's borderless WM_NCCALCSIZE fixes a non-resizable window to its cached
  // floating size. Keep its resize bookkeeping active; NativeFadeSurface removes
  // the actual resize frame after this call, so only the parent controls sizing.
  require(SDL_SetWindowResizable(window,true),"Enabling video size tracking");
  require(SDL_SetWindowBordered(window,false),"Removing video window decorations");
  long flags=SDL_GetWindowFlags(window);
  if((flags&SDL_WINDOW_BORDERLESS)==0||(flags&(SDL_WINDOW_MAXIMIZED|SDL_WINDOW_MINIMIZED))!=0)
   throw new IllegalStateException("SDL video window did not enter the borderless child state");
 }
 private static void require(boolean result,String action){
  if(!result)throw new IllegalStateException(action+": "+SDL_GetError());
 }
 private SdlEmbeddedWindow(){}
}
