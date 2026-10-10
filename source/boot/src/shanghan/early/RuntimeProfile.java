package shanghan.early;
import com.google.gson.*;import java.io.*;import java.nio.charset.StandardCharsets;
public final class RuntimeProfile {
 private static final JsonObject PROFILE=load();
 private static JsonObject load(){
  try(InputStream in=RuntimeProfile.class.getResourceAsStream("/shanghan/early/runtime.json")){
   if(in==null)throw new IllegalStateException("Missing runtime profile");
   return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
  }catch(IOException e){throw new IllegalStateException(e);}
 }
 public static String version(){return PROFILE.get("version").getAsString();}
 public static void requireClient(InputStream in)throws IOException{
  if(in==null)throw new IllegalStateException("Missing client runtime profile");
  JsonObject client=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
  if(!version().equals(client.get("version").getAsString())||!PROFILE.get("minecraft").equals(client.get("minecraft")))throw new IllegalStateException("Bootstrap/client versions do not match; stock Forge overlay will be used");
 }
 public static boolean supports(String minecraft,String forge){
  if(!PROFILE.get("minecraft").getAsString().equals(minecraft))return false;
  for(JsonElement version:PROFILE.getAsJsonArray("forgeBuilds"))if(version.getAsString().equals(forge))return true;
  return false;
 }
}