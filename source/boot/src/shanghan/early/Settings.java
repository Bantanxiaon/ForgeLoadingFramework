package shanghan.early;
import com.google.gson.*;
import java.nio.file.*;
/** Versioned external theme. Legacy configs are read in place; no absolute resource paths. */
public final class Settings {
 public int schemaVersion=1;
 public boolean enabled=true,maximized=true,audio=true,independentWindow=true;
 public String video="video/loading.mp4",audioFile="",fallback="images/fallback.png",logo="images/logo.png",font="",ffmpeg="bin/ffmpeg.exe",audioMixer="";
 public String title="",subtitle="正在初始化游戏内容，请勿关闭游戏";
 public int width=640,height=360,fps=30,queueFrames=3,fadeInMs=0,fadeOutMs=450;
 public float volume=.8f;
 public Theme.Config ui=new Theme.Config();
 public static Path directory(Path game){
  Path modern=game.resolve("loading"),legacy=game.resolve("config/shanghan_loading");
  return Files.exists(modern)?modern:Files.exists(legacy)?legacy:modern;
 }
 public Path resource(Path dir,String value){
  if(value==null||value.isBlank())throw new IllegalArgumentException("Empty resource path");
  Path relative=Path.of(value);
  if(relative.isAbsolute()||value.matches("^[A-Za-z]:.*"))throw new IllegalArgumentException("Resource paths must be relative: "+value);
  Path base=dir.toAbsolutePath().normalize(),out=base.resolve(relative).normalize();
  if(!out.startsWith(base))throw new IllegalArgumentException("Resource escapes theme directory: "+value);
  try{if(Files.exists(out)&&!out.toRealPath().startsWith(base.toRealPath()))throw new IllegalArgumentException("Resource link escapes theme directory: "+value);}catch(java.io.IOException e){throw new IllegalArgumentException(e);}
  return out;
 }
 public static Settings load(Path dir) {
  Settings s=new Settings();
  try{
   Path f=dir.resolve("config.json");
   if(Files.exists(f)){
    if(Files.size(f)>1_000_000)throw new IllegalArgumentException("Config exceeds 1 MB");
    JsonObject obj=JsonParser.parseString(Files.readString(f)).getAsJsonObject();
    if(obj.has("audio")&&obj.get("audio").isJsonPrimitive()&&obj.getAsJsonPrimitive("audio").isString()){obj.add("audioFile",obj.remove("audio"));obj.addProperty("audio",true);}
    s=new Gson().fromJson(obj,Settings.class);
    if(s==null||s.schemaVersion!=1)throw new IllegalArgumentException("Unsupported schemaVersion");
   }
  }catch(Exception e){System.err.println("[LoadingFramework] Invalid config, defaults used: "+e);s=new Settings();}
  if(s.ui==null)s.ui=new Theme.Config();s.ui.normalize();
  s.width=Math.max(320,Math.min(1920,s.width));s.height=Math.max(180,Math.min(1080,s.height));s.width-=s.width%2;s.height-=s.height%2;
  s.fps=Math.max(30,Math.min(60,s.fps));s.queueFrames=Math.max(2,Math.min(6,s.queueFrames));
  s.fadeInMs=Math.max(0,Math.min(5000,s.fadeInMs));s.fadeOutMs=Math.max(100,Math.min(5000,s.fadeOutMs));
  s.volume=Float.isFinite(s.volume)?Math.max(0,Math.min(1,s.volume)):.8f;
  for(String old:new String[]{"intro.mp4","loading.mp4"})if(!regular(s,dir,s.video)&&regular(s,dir,old)){s.video=old;break;}
  if(!regular(s,dir,s.logo)&&regular(s,dir,"logo.png"))s.logo="logo.png";
  if(!regular(s,dir,s.fallback)&&regular(s,dir,"fallback.png"))s.fallback="fallback.png";
  return s;
 }
 private static boolean regular(Settings s,Path dir,String path){try{return Files.isRegularFile(s.resource(dir,path));}catch(Exception e){return false;}}
}
