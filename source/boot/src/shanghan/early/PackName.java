package shanghan.early;
import com.google.gson.*;
import java.nio.file.*;
public final class PackName {
 public static String resolve(Path game,String[] arguments){
  for(String file:new String[]{"modrinth.index.json","manifest.json"}){
   try{
    Path p=game.resolve(file);
    if(Files.isRegularFile(p)&&Files.size(p)<4_000_000){
     JsonObject obj=JsonParser.parseString(Files.readString(p)).getAsJsonObject();
     if(obj.has("name")&&obj.get("name").isJsonPrimitive()){
      String value=clean(obj.get("name").getAsString());if(!value.isBlank())return value;
     }
    }
   }catch(Exception ignored){}
  }
  // Standard launchers pass the actual selected instance ID through --version.
  for(int i=0;i+1<arguments.length;i++)if(arguments[i].equals("--version")){
   String value=clean(arguments[i+1]);if(!value.isBlank())return value;
  }
  Path name=game.getFileName();return name==null?"Minecraft":clean(name.toString());
 }
 private static String clean(String value){
  value=value.replaceAll("[\\p{Cntrl}]"," ").strip();return value.length()>120?value.substring(0,120):value;
 }
}
