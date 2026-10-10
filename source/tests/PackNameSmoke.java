import java.nio.file.*;
import shanghan.early.*;
public final class PackNameSmoke {
 public static void main(String[] args)throws Exception{
  Path a=Path.of(args[0]);Files.createDirectories(a);
  String[] launch={"--version","Example Instance"};
  if(!PackName.resolve(a,launch).equals("Example Instance"))throw new AssertionError("instance fallback");
  Files.writeString(a.resolve("modrinth.index.json"),"{\"name\":\"Dynamic Pack\"}");
  if(!PackName.resolve(a,launch).equals("Dynamic Pack"))throw new AssertionError("manifest title");
  Files.writeString(a.resolve("modrinth.index.json"),"{\"name\":\"Changed Pack\"}");
  if(!PackName.resolve(a,launch).equals("Changed Pack"))throw new AssertionError("changed manifest title");
  Settings s=Settings.load(a);
  if(s.width!=640||s.height!=360||s.fps!=30||s.queueFrames!=3)throw new AssertionError("low overhead defaults");
  System.out.println("PASS pack name changes, launch-name fallback, 640x360 / 30fps / bounded queue defaults");
 }
}
