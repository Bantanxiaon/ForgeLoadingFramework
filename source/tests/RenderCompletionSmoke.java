import shanghan.early.*;
import java.nio.file.*;
public final class RenderCompletionSmoke {
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);Files.createDirectories(dir);MediaSession media=new MediaSession(dir,new Settings());
  try{
   if(media.readyForWindowClose())throw new AssertionError("Ready before fade/cleanup");
   media.close();
   if(!media.stopped||media.readyForWindowClose())throw new AssertionError("Decoder stop is not renderer release");
   media.markRenderReleased();
   if(!media.readyForWindowClose())throw new AssertionError("Renderer release was not published");
  }finally{media.close();media.finishLogging();}
  System.out.println("PASS game overlay waits for renderer release after decoder stop before destroying embedded surface and closing logs.");
 }
}
