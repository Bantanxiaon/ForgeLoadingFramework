import shanghan.early.*;
import java.nio.file.*;import java.awt.image.*;import java.util.*;
public final class ThemeConfigSmoke{
 public static void main(String[] args)throws Exception{
  Path game=Path.of(args[0]),dir=game.resolve("loading");Files.createDirectories(dir);
  Files.writeString(dir.resolve("config.json"),"{broken");if(Settings.load(dir).width!=640)throw new AssertionError("Bad config fallback");
  Files.writeString(dir.resolve("config.json"),"{\"schemaVersion\":99,\"width\":1280}");if(Settings.load(dir).width!=640)throw new AssertionError("Unknown schema");
  Files.writeString(dir.resolve("config.json"),"{\"schemaVersion\":1,\"audio\":\"audio/music.ogg\",\"font\":\"fonts/bad.ttf\",\"logo\":\"images/bad.png\",\"ui\":{\"title\":{\"text\":\"Custom theme\",\"x\":\"50%\",\"y\":\"10%\",\"anchor\":\"top-center\",\"color\":\"invalid\"},\"progress\":null}}");
  Settings s=Settings.load(dir);s.title="Auto pack";if(!s.audio||!s.audioFile.equals("audio/music.ogg"))throw new AssertionError("External audio");
  if(s.ui.progress==null||!Settings.directory(game).equals(dir))throw new AssertionError("Layout/directory");
  for(String p:new String[]{"../escape.mp4","D:/escape.mp4"}){try{s.resource(dir,p);throw new AssertionError("Unsafe resource accepted");}catch(IllegalArgumentException expected){}}
  Files.createDirectories(dir.resolve("fonts"));Files.write(dir.resolve("fonts/bad.ttf"),new byte[64]);Files.createDirectories(dir.resolve("images"));Files.write(dir.resolve("images/bad.png"),new byte[64]);
  MediaSession media=new MediaSession(dir,s);
  try{
   Theme theme=new Theme(media);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);
   var g=image.createGraphics();theme.draw(g,media.progressState(),List.of());g.dispose();
   boolean nonempty=false;for(int y=72;y<120;y++)for(int x=400;x<900;x++)if((image.getRGB(x,y)>>>24)>0)nonempty=true;
   if(!nonempty)throw new AssertionError("External theme not rendered");
   media.setProgressProvider(()->{throw new IllegalStateException("provider failed");});if(media.progressState()==null)throw new AssertionError("Provider fallback");
   media.progress.resource(.375f);if(media.progressState().stageFraction()!=.375)throw new AssertionError("Real resource fraction");
  }finally{media.close();media.finishLogging();}
  BufferedImage colors=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);colors.setRGB(0,0,0x80ff0000);colors.setRGB(1,0,0xff00ff00);
  java.nio.ByteBuffer pixels=java.nio.ByteBuffer.allocateDirect(8);var method=VideoRenderer.class.getDeclaredMethod("rgba",BufferedImage.class,java.nio.ByteBuffer.class);method.setAccessible(true);method.invoke(null,colors,pixels);
  byte[] expected={(byte)255,0,0,(byte)128,0,(byte)255,0,(byte)255};byte[] actual=new byte[8];pixels.get(actual);if(!java.util.Arrays.equals(expected,actual))throw new AssertionError("RGBA channel/alpha conversion");
  // Closed log handle must allow a Windows rename.
  Files.move(dir.resolve("runtime.log"),dir.resolve("closed-runtime.log"));
  System.out.println("PASS malformed config/schema, relative paths, external audio/font/logo, custom layout, provider isolation, real stage fraction closed log and RGBA channel order.");
 }
}
