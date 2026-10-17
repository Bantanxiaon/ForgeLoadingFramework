package shanghan.early;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import shanghan.routing.RuntimeRouter;
/** Distribution identity is separate from runtime routing; patch builds are not a whitelist. */
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
  if(!PROFILE.equals(client))throw new IllegalStateException("Bootstrap/client runtime profiles do not match");
 }
 public static boolean supports(String minecraft,String forge){return RuntimeRouter.select(minecraft,forge)!=null;}
}
