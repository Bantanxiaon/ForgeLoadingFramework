import com.google.gson.*;
import javax.swing.*;import javax.swing.border.EmptyBorder;
import java.awt.*;import java.awt.datatransfer.*;import java.io.*;import java.nio.file.*;import java.security.*;import java.time.*;import java.time.format.*;import java.util.*;import java.util.function.*;import java.util.regex.*;
public final class AuthorTool{
 static final JsonObject PROFILE=profile();
 static JsonObject profile(){try{return JsonParser.parseString(new String(resource("/runtime.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 static final String VERSION=PROFILE.get("version").getAsString(),CLIENT="ForgeLoadingFramework-"+VERSION+".jar",BOOT="ForgeLoadingFramework-bootstrap-"+VERSION+".jar",COORD="shanghan:earlyboot:"+VERSION,LIB="shanghan/earlyboot/"+VERSION+"/earlyboot-"+VERSION+".jar";
 static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 static final Path HOME=home();
 static volatile Process transcode;
 static Path home(){try{return Path.of(AuthorTool.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();}catch(Exception e){throw new IllegalStateException(e);}}
 static Path config(Path instance){Path modern=instance.resolve("loading"),legacy=instance.resolve("config/shanghan_loading");return Files.exists(modern)?modern:Files.exists(legacy)?legacy:modern;}
 static JsonObject json(Path path)throws IOException{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
 static void write(Path path,JsonObject data)throws IOException{Files.createDirectories(path.getParent());Files.writeString(path,GSON.toJson(data));}
 static byte[] resource(String path)throws IOException{try(InputStream in=AuthorTool.class.getResourceAsStream(path)){if(in==null)throw new IOException("缺少工具资源："+path);return in.readAllBytes();}}
 static String hash(byte[] bytes,String algorithm)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));}
 static Path version(Path instance){return instance.resolve(instance.getFileName()+".json");}
 static String forge(Path instance)throws IOException{
  JsonArray game=json(version(instance)).getAsJsonObject("arguments").getAsJsonArray("game");
  String mc="",forge="";
  for(int i=0;i+1<game.size();i++)if(game.get(i).isJsonPrimitive()){
   String key=game.get(i).getAsString();
   if(key.equals("--fml.forgeVersion"))forge=game.get(i+1).getAsString();
   if(key.equals("--fml.mcVersion"))mc=game.get(i+1).getAsString();
  }
  boolean compatible=false;for(JsonElement supported:PROFILE.getAsJsonArray("forgeBuilds"))if(supported.getAsString().equals(forge))compatible=true;
  if(!mc.equals(PROFILE.get("minecraft").getAsString())||!compatible)throw new IOException("目前支持 Minecraft 1.20.1 + Forge 47.3.22 / 47.3.33。此实例为 "+mc+" / "+forge);
  if(!instance.getParent().getFileName().toString().equals("versions"))throw new IOException("请选择 .minecraft/versions 下的实例目录。");
  return forge;
 }
 static Path libraryRoot(Path instance){return instance.getParent().getParent().resolve("libraries");}
 static Path backup(Path instance,String kind)throws IOException{
  Path b=instance.resolve("shanghan_loading_backups").resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now())+"-"+kind);
  Files.createDirectories(b);return b;
 }
 static void save(Path source,Path backup,String name)throws IOException{if(Files.isRegularFile(source)){Path out=backup.resolve(name);Files.createDirectories(out.getParent());Files.copy(source,out);}}
 static void move(Path source,Path target)throws IOException{Files.createDirectories(target.getParent());Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}
 static boolean ownedMod(Path file){
  if(!file.getFileName().toString().endsWith(".jar"))return false;
  try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file.toFile())){
   var entry=zip.getEntry("META-INF/mods.toml");if(entry==null)return false;
   try(InputStream in=zip.getInputStream(entry)){String text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);var blocks=Pattern.compile("(?s)\\[\\[mods\\]\\](.*?)(?=\\r?\\n\\s*\\[|\\z)").matcher(text);while(blocks.find())if(Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"\']shanghanearlyloading[\"\']").matcher(blocks.group(1)).find())return true;return false;}
  }catch(IOException ignored){return false;}
 }
 static Path install(Path raw)throws Exception{
  Path instance=raw.toAbsolutePath().normalize();forge(instance);
  byte[] boot=resource("/payload/"+BOOT),client=resource("/payload/"+CLIENT); // Validate payload before modifying anything.
  Path b=backup(instance,"install");save(version(instance),b,"version.json");save(instance.resolve("config/fml.toml"),b,"fml.toml");save(config(instance).resolve("config.json"),b,"config.json");
  JsonObject manifest=new JsonObject();manifest.addProperty("instance",instance.toString());manifest.addProperty("version",VERSION);
  JsonArray oldMods=new JsonArray();Path mods=instance.resolve("mods");Files.createDirectories(mods);
  java.util.List<Path> previous;
  try(var stream=Files.list(mods)){previous=stream.filter(AuthorTool::ownedMod).toList();}
  for(Path file:previous)oldMods.add(file.getFileName().toString());
  manifest.add("oldMods",oldMods);manifest.addProperty("newMod",CLIENT);manifest.addProperty("clientWritten",false);
  write(b.resolve("backup.json"),manifest);
  try{
   for(Path file:previous)move(file,b.resolve("mods").resolve(file.getFileName()));
   manifest.addProperty("clientWritten",true);write(b.resolve("backup.json"),manifest);
   Path lib=libraryRoot(instance).resolve(LIB);Files.createDirectories(lib.getParent());Files.write(lib,boot);Files.write(mods.resolve(CLIENT),client);
   JsonObject data=json(version(instance));JsonArray libs=data.getAsJsonArray("libraries"),updated=new JsonArray();
   for(JsonElement entry:libs)if(!entry.getAsJsonObject().get("name").getAsString().startsWith("shanghan:earlyboot:"))updated.add(entry);
   JsonObject entry=new JsonObject(),downloads=new JsonObject(),artifact=new JsonObject();entry.addProperty("name",COORD);artifact.addProperty("path",LIB);artifact.addProperty("size",boot.length);artifact.addProperty("sha1",hash(boot,"SHA-1"));downloads.add("artifact",artifact);entry.add("downloads",downloads);updated.add(entry);data.add("libraries",updated);
   write(version(instance),data);
   Path fml=instance.resolve("config/fml.toml");String text=Files.exists(fml)?Files.readString(fml):"";String provider="earlyWindowProvider = \"shanghanearlyloading\"";
   if(Pattern.compile("(?m)^\\s*earlyWindowProvider\\s*=").matcher(text).find())text=text.replaceAll("(?m)^\\s*earlyWindowProvider\\s*=.*$",provider);else text+="\n"+provider+"\n";
   Files.createDirectories(fml.getParent());Files.writeString(fml,text);
   for(String folder:new String[]{"video","audio","fonts","images","cache","bin"})Files.createDirectories(config(instance).resolve(folder));
   Path ff=config(instance).resolve("bin/ffmpeg.exe");Files.createDirectories(ff.getParent());
   byte[] executable=resource("/payload/ffmpeg.exe");
   if(!Files.exists(ff)||!hash(Files.readAllBytes(ff),"SHA-256").equals(hash(executable,"SHA-256")))Files.write(ff,executable);
   JsonObject cfg=Files.exists(config(instance).resolve("config.json"))?json(config(instance).resolve("config.json")):new JsonObject();
   defaults(cfg);write(config(instance).resolve("config.json"),cfg);
   manifest.addProperty("complete",true);write(b.resolve("backup.json"),manifest);
   return b;
  }catch(Exception error){restoreBackup(instance,b);throw error;}
 }
 static void defaults(JsonObject cfg){
  if(!cfg.has("schemaVersion"))cfg.addProperty("schemaVersion",1);
  if(!cfg.has("enabled"))cfg.addProperty("enabled",true);
  if(!cfg.has("audio"))cfg.addProperty("audio",true);
  if(!cfg.has("maximized"))cfg.addProperty("maximized",true);
  if(!cfg.has("volume"))cfg.addProperty("volume",.8);
  if(!cfg.has("fadeOutMs"))cfg.addProperty("fadeOutMs",450);
  if(!cfg.has("width"))cfg.addProperty("width",640);
  if(!cfg.has("height"))cfg.addProperty("height",360);
  if(!cfg.has("fps"))cfg.addProperty("fps",30);
  if(!cfg.has("video"))cfg.addProperty("video","video/loading.mp4");
  if(!cfg.has("fallback"))cfg.addProperty("fallback","images/fallback.png");
  if(!cfg.has("ffmpeg"))cfg.addProperty("ffmpeg","bin/ffmpeg.exe");
  cfg.addProperty("independentWindow",true);
 }
 static void restoreBackup(Path instance,Path b)throws Exception{
  JsonObject m=json(b.resolve("backup.json"));if(!Path.of(m.get("instance").getAsString()).equals(instance))throw new IOException("备份不属于此实例。");
  Path current=instance.resolve("mods").resolve(m.get("newMod").getAsString());if(m.has("clientWritten")&&m.get("clientWritten").getAsBoolean()&&Files.exists(current))move(current,b.resolve("rollback-archive").resolve(current.getFileName()));
  for(JsonElement name:m.getAsJsonArray("oldMods")){Path old=b.resolve("mods").resolve(name.getAsString());if(Files.exists(old))move(old,instance.resolve("mods").resolve(old.getFileName()));}
  Files.copy(b.resolve("version.json"),version(instance),StandardCopyOption.REPLACE_EXISTING);
  for(String name:new String[]{"fml.toml","config.json"}){
   Path target=name.equals("fml.toml")?instance.resolve("config/fml.toml"):config(instance).resolve("config.json");
   if(Files.exists(b.resolve(name)))Files.copy(b.resolve(name),target,StandardCopyOption.REPLACE_EXISTING);
   else if(Files.exists(target))move(target,b.resolve("rollback-archive").resolve(name));
  }
  m.addProperty("restored",true);write(b.resolve("backup.json"),m);
 }
 static Path restore(Path instance)throws Exception{
  Path root=instance.resolve("shanghan_loading_backups");
  if(!Files.isDirectory(root))throw new IOException("没有安装备份。");
  try(var dirs=Files.list(root)){
   for(Path b:dirs.sorted(Comparator.reverseOrder()).toList()){
    Path manifest=b.resolve("backup.json");if(!Files.exists(manifest))continue;
    JsonObject m=json(manifest);if(m.has("restored")&&m.get("restored").getAsBoolean())continue;
    restoreBackup(instance,b);return b;
   }
  }
  throw new IOException("没有未恢复的安装备份。");
 }
 static Path decoder()throws IOException{
  Path ff=HOME.resolve("author-work/bin/ffmpeg.exe");Files.createDirectories(ff.getParent());
  if(!Files.exists(ff))Files.write(ff,resource("/payload/ffmpeg.exe"));return ff;
 }
 static void convert(Path instance,Path input,int width,int height,double volume,Consumer<String> progress)throws Exception{
  forge(instance);if(!Files.isRegularFile(input))throw new IOException("请先选择视频。");
  Path dir=config(instance);Files.createDirectories(dir);Path temp=dir.resolve("intro.import-"+UUID.randomUUID()+".mp4");
  java.util.List<String> command=new ArrayList<>(java.util.List.of(decoder().toString(),"-nostdin","-y","-hide_banner","-threads","2","-filter_threads","1","-i",input.toAbsolutePath().toString(),"-map","0:v:0","-map","0:a:0?","-vf","scale="+width+":"+height+":force_original_aspect_ratio=decrease:out_color_matrix=bt709:out_range=tv,pad="+width+":"+height+":(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30","-c:v","libx264","-preset","fast","-crf","20","-pix_fmt","yuv420p","-color_range","tv","-colorspace","bt709","-color_primaries","bt709","-color_trc","bt709","-threads","2","-c:a","aac","-ar","48000","-ac","2","-b:a","128k","-movflags","+faststart","-progress","pipe:1","-nostats",temp.toString()));
  Process process=new ProcessBuilder(command).redirectErrorStream(true).start();transcode=process;StringBuilder diagnostic=new StringBuilder();double duration=0;long last=0;
  Pattern durationPattern=Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
  try(BufferedReader reader=process.inputReader(java.nio.charset.StandardCharsets.UTF_8)){
   String line;
   while((line=reader.readLine())!=null){
    if(diagnostic.length()<16000)diagnostic.append(line).append('\n');
    var match=durationPattern.matcher(line);if(match.find())duration=Integer.parseInt(match.group(1))*3600+Integer.parseInt(match.group(2))*60+Double.parseDouble(match.group(3));
    if(line.startsWith("out_time_us=")&&duration>0&&System.nanoTime()-last>200_000_000L){
     try{int percent=(int)Math.min(99,Long.parseLong(line.substring(12))/1e6/duration*100);progress.accept("转换视频："+percent+"%");last=System.nanoTime();}catch(NumberFormatException ignored){}
    }
   }
  }
  int status=process.waitFor();transcode=null;Files.writeString(dir.resolve("last-import.log"),diagnostic.toString());
  if(status!=0||!Files.isRegularFile(temp))throw new IOException("视频转换失败，原视频未替换。详情见 "+dir.resolve("last-import.log"));
  Path intro=dir.resolve("video/loading.mp4");Files.createDirectories(intro.getParent());if(Files.exists(intro)){Path b=backup(instance,"video");move(intro,b.resolve("intro.mp4"));}
  move(temp,intro);JsonObject cfg=Files.exists(dir.resolve("config.json"))?json(dir.resolve("config.json")):new JsonObject();defaults(cfg);
  cfg.addProperty("video","video/loading.mp4");cfg.addProperty("fps",30);cfg.addProperty("width",width);cfg.addProperty("height",height);cfg.addProperty("volume",volume);write(dir.resolve("config.json"),cfg);
 }
 static void mainCli(String[] args)throws Exception{
  switch(args[0]){
   case "--install"->System.out.println("INSTALL_OK backup="+install(Path.of(args[1])));
   case "--restore"->System.out.println("RESTORE_OK backup="+restore(Path.of(args[1])));
   case "--convert"->{convert(Path.of(args[1]),Path.of(args[2]),640,360,.8,System.out::println);System.out.println("CONVERT_OK");}
   default->throw new IllegalArgumentException("Unknown command");
  }
 }
 static JFrame frame;static JTextField instanceField=new JTextField(),videoField=new JTextField();static JComboBox<String> quality=new JComboBox<>(new String[]{"兼容优先 · 640×360","清晰优先 · 1280×720"});
 static JSpinner volume=new JSpinner(new SpinnerNumberModel(80,0,100,5));static JLabel status=new JLabel("选择整合包和视频，然后点击安装。");static JProgressBar bar=new JProgressBar();
 static final java.util.List<JButton> buttons=new ArrayList<>();
 static JButton button(String text,Runnable action){JButton b=new JButton(text);b.addActionListener(e->action.run());buttons.add(b);return b;}
 static Path instance(){return Path.of(instanceField.getText()).toAbsolutePath().normalize();}
 static JPanel row(String label,JComponent field,JComponent control){JPanel row=new JPanel(new BorderLayout(12,0));row.add(new JLabel(label),BorderLayout.WEST);row.add(field);if(control!=null)row.add(control,BorderLayout.EAST);return row;}
 static void chooseInstance(){
  JFileChooser chooser=new JFileChooser(HOME.toFile());chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setDialogTitle("选择 versions 下的整合包实例目录");
  if(chooser.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION){
   instanceField.setText(chooser.getSelectedFile().getAbsolutePath());
   try{
    String version=forge(instance());status.setText("Minecraft 1.20.1 · Forge "+version+" · 请先退出该实例游戏和 PCL。");
    Path cfg=config(instance()).resolve("config.json");if(Files.exists(cfg)){JsonObject c=json(cfg);if(c.has("volume"))volume.setValue((int)Math.round(c.get("volume").getAsDouble()*100));if(c.has("width"))quality.setSelectedIndex(c.get("width").getAsInt()>640?1:0);}
   }catch(Exception e){status.setText(e.getMessage());}
  }
 }
 static void chooseVideo(){JFileChooser chooser=new JFileChooser(HOME.toFile());chooser.setDialogTitle("选择启动视频");if(chooser.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)videoField.setText(chooser.getSelectedFile().getAbsolutePath());}
 static void work(Callable action){
  for(JButton b:buttons)b.setEnabled(false);bar.setIndeterminate(true);
  new SwingWorker<String,String>(){
   protected String doInBackground()throws Exception{return action.call();}
   protected void done(){bar.setIndeterminate(false);for(JButton b:buttons)b.setEnabled(true);try{status.setText(get());}catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();status.setText("未完成："+cause.getMessage());JOptionPane.showMessageDialog(frame,cause.getMessage(),"操作未完成",JOptionPane.ERROR_MESSAGE);}}
  }.execute();
 }
 interface Callable{String call()throws Exception;}
 static void convertSelected(Path instance)throws Exception{
  int q=quality.getSelectedIndex();Path input=Path.of(videoField.getText());double v=((Number)volume.getValue()).doubleValue()/100;
  convert(instance,input,q==0?640:1280,q==0?360:720,v,message->SwingUtilities.invokeLater(()->status.setText(message)));
 }
 static void gui(){
  frame=new JFrame("Forge 启动视频设置 · "+VERSION);frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);frame.setSize(820,360);frame.setLocationRelativeTo(null);
  JPanel panel=new JPanel(new GridLayout(0,1,0,12));panel.setBorder(new EmptyBorder(20,24,20,24));
  panel.add(new JLabel("Minecraft 1.20.1 · Forge 47.3.22 / 47.3.33 · 更换视频无需重新编译"));
  instanceField.setEditable(false);videoField.setEditable(false);
  panel.add(row("整合包",instanceField,button("选择实例",AuthorTool::chooseInstance)));
  panel.add(row("启动视频",videoField,button("选择视频",AuthorTool::chooseVideo)));
  videoField.setTransferHandler(new TransferHandler(){
   public boolean canImport(TransferSupport s){return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
   public boolean importData(TransferSupport s){try{java.util.List<?> files=(java.util.List<?>)s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);videoField.setText(((File)files.get(0)).getAbsolutePath());return true;}catch(Exception e){return false;}}
  });
  JPanel choices=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));choices.add(quality);choices.add(new JLabel("    音量 %  "));choices.add(volume);panel.add(choices);
  JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
  actions.add(button("安装 / 更新",()->work(()->{
   Path i=instance();forge(i);Path cfg=config(i).resolve("config.json");
   if(videoField.getText().isBlank()&&!Files.exists(config(i).resolve("video/loading.mp4"))&&!Files.exists(config(i).resolve("intro.mp4"))&&!Files.exists(config(i).resolve("loading.mp4")))throw new IOException("请先选择启动视频。");
   Path backup=install(i);try{if(!videoField.getText().isBlank())convertSelected(i);}catch(Exception error){restoreBackup(i,backup);throw error;}
   return "已安装。重启 PCL 后启动游戏。备份："+backup.getFileName();
  })));
  actions.add(button("替换视频",()->work(()->{if(videoField.getText().isBlank())throw new IOException("请先选择视频。");convertSelected(instance());return "视频已替换，下次启动生效。";})));
  actions.add(button("打开视频目录",()->{try{Path dir=config(instance());Files.createDirectories(dir);Desktop.getDesktop().open(dir.toFile());}catch(Exception e){status.setText(e.getMessage());}}));
  actions.add(button("恢复上次安装",()->{if(JOptionPane.showConfirmDialog(frame,"恢复上一次安装的组件和启动配置？视频将保留。请先退出游戏和 PCL。","恢复",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)work(()->{restore(instance());return "已恢复上次安装配置，重启 PCL 后生效。";});}));
  panel.add(actions);panel.add(status);panel.add(bar);frame.setContentPane(panel);frame.setVisible(true);
 }
 public static void main(String[] args)throws Exception{Runtime.getRuntime().addShutdownHook(new Thread(()->{Process p=transcode;if(p!=null&&p.isAlive())p.destroyForcibly();},"Author-video-cleanup"));if(args.length>0){mainCli(args);return;}SwingUtilities.invokeLater(AuthorTool::gui);}
}
