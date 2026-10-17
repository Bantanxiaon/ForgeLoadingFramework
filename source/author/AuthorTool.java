import shanghan.routing.RuntimeRouter;
import com.google.gson.*;
import javax.swing.*;import javax.swing.border.EmptyBorder;
import java.awt.*;import java.awt.datatransfer.*;import java.io.*;import java.nio.file.*;import java.security.*;import java.time.*;import java.time.format.*;import java.util.*;import java.util.function.*;import java.util.regex.*;
public final class AuthorTool{
 static final JsonObject PROFILE=profile();
 static JsonObject profile(){try{return JsonParser.parseString(new String(resource("/runtime.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 static final String VERSION=PROFILE.get("version").getAsString(),CLIENT="ForgeLoadingFramework-"+VERSION+".jar",BOOT="ForgeLoadingFramework-bootstrap-"+VERSION+".jar",COORD="shanghan:earlyboot:"+VERSION,LIB="shanghan/earlyboot/"+VERSION+"/earlyboot-"+VERSION+".jar";
 static final String TOOL_VERSION=toolVersion();
 static String toolVersion(){try{return new String(resource("/author-version.txt"),java.nio.charset.StandardCharsets.UTF_8).trim();}catch(IOException ignored){return VERSION;}}
 static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 static final Path HOME=home();
 static volatile Process transcode;
 static Path home(){try{return Path.of(AuthorTool.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();}catch(Exception e){throw new IllegalStateException(e);}}
 static Path config(Path instance){Path modern=instance.resolve("loading"),legacy=instance.resolve("config/shanghan_loading");return Files.exists(modern)?modern:Files.exists(legacy)?legacy:modern;}
 static JsonObject json(Path path)throws IOException{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
 static void write(Path path,JsonObject data)throws IOException{Files.createDirectories(path.getParent());Files.writeString(path,GSON.toJson(data));}
 static byte[] resource(String path)throws IOException{try(InputStream in=AuthorTool.class.getResourceAsStream(path)){if(in==null)throw new IOException("缺少工具资源："+path);return in.readAllBytes();}}
 static String hash(byte[] bytes,String algorithm)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));}
 static Path version(Path instance)throws IOException{
  if(instance==null||instance.getFileName()==null)throw new IOException("请选择整合包文件夹或实际游戏实例，不要选择盘符根目录。");
  return instance.resolve(instance.getFileName()+".json");
 }
 static java.util.List<Path> childDirectories(Path directory,int limit)throws IOException{
  if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))return java.util.List.of();
  try(var stream=Files.list(directory)){
   var result=stream.filter(f->Files.isDirectory(f,LinkOption.NOFOLLOW_LINKS)).sorted().limit(limit+1L).toList();
   if(result.size()>limit)throw new IOException("文件夹中子目录过多，请选择更接近游戏实例的目录。");
   return result;
  }
 }
 static void scanLayout(Path base,java.util.Set<Path> candidates)throws IOException{
  if(!Files.isDirectory(base,LinkOption.NOFOLLOW_LINKS))return;
  candidates.add(base);
  candidates.addAll(childDirectories(base.resolve("versions"),256));
  if(base.getFileName()!=null&&base.getFileName().toString().equalsIgnoreCase("versions"))
   candidates.addAll(childDirectories(base,256));
 }
 static java.util.List<Path> supportedCandidates(java.util.Set<Path> candidates){
  java.util.List<Path> supported=new ArrayList<>();
  for(Path path:candidates)try{forge(path);supported.add(path);}catch(IOException ignored){}
  return java.util.List.copyOf(supported);
 }
 /** Read-only, bounded discovery of installed standard Minecraft version JSON layouts. */
 static java.util.List<Path> discoverInstances(Path raw)throws IOException{
  if(raw==null)throw new IOException("请先选择整合包或游戏实例。");
  Path selected=raw.toAbsolutePath().normalize();
  if(selected.getFileName()==null)throw new IOException("请选择整合包文件夹或实际游戏实例，不要选择盘符根目录。");
  if(!Files.isDirectory(selected,LinkOption.NOFOLLOW_LINKS))throw new IOException("所选文件夹不存在或不是普通目录。");
  try{forge(selected);return java.util.List.of(selected);}catch(IOException invalidDirect){
   if(Files.isRegularFile(version(selected),LinkOption.NOFOLLOW_LINKS))throw invalidDirect;
  }
  java.util.Set<Path> candidates=new LinkedHashSet<>();
  for(Path base:java.util.List.of(selected,selected.resolve(".minecraft"),selected.resolve("minecraft")))
   scanLayout(base,candidates);
  var found=supportedCandidates(candidates);
  if(!found.isEmpty())return found;
  var skip=java.util.Set.of("mods","libraries","assets","config","loading","shanghan_loading_backups","java","pcl","logs","resourcepacks","saves","bin","tools");
  for(Path child:childDirectories(selected,128)){
   if(skip.contains(child.getFileName().toString().toLowerCase(Locale.ROOT)))continue;
   for(Path base:java.util.List.of(child,child.resolve(".minecraft"),child.resolve("minecraft")))scanLayout(base,candidates);
  }
  found=supportedCandidates(candidates);
  if(found.isEmpty())throw new IOException("未找到已安装且受支持的 Forge 实例。请选择包含 .minecraft/versions 的整合包文件夹，或实际实例目录；尚未安装的导入包请先用启动器安装。");
  return found;
 }
 static Path resolveInstance(Path raw)throws IOException{
  var candidates=discoverInstances(raw);
  if(candidates.size()!=1)throw new IOException("找到多个 Forge 实例，请指定实际实例目录："+candidates);
  return candidates.get(0);
 }
 static String forge(Path instance)throws IOException{
  if(instance==null||!Files.isDirectory(instance,LinkOption.NOFOLLOW_LINKS))throw new IOException("游戏实例目录不存在。");
  Path metadata=version(instance);
  if(!Files.isRegularFile(metadata,LinkOption.NOFOLLOW_LINKS))throw new IOException("所选目录不是已安装的游戏实例，请选择整合包外层目录或实际实例目录。");
  try{return forgeMetadata(json(metadata));}
  catch(IOException e){throw e;}
  catch(RuntimeException e){throw new IOException("实例版本 JSON 无效，请在启动器中检查或重新选择实例。",e);}
 }
 static String forgeMetadata(JsonObject data)throws IOException{
  String mc="",forge="";
  JsonObject arguments=data.has("arguments")?data.getAsJsonObject("arguments"):null;
  JsonArray game=arguments!=null&&arguments.has("game")?arguments.getAsJsonArray("game"):new JsonArray();
  for(int i=0;i+1<game.size();i++)if(game.get(i).isJsonPrimitive()&&game.get(i+1).isJsonPrimitive()){
   String key=game.get(i).getAsString();
   if(key.equals("--fml.forgeVersion"))forge=game.get(i+1).getAsString();
   if(key.equals("--fml.mcVersion"))mc=game.get(i+1).getAsString();
  }
  if(data.has("libraries"))for(JsonElement item:data.getAsJsonArray("libraries")){
   JsonObject library=item.getAsJsonObject();if(!library.has("name"))continue;
   String[] coordinate=library.get("name").getAsString().split(":");
   if(coordinate.length<3||!coordinate[0].equals("net.minecraftforge")||!java.util.Set.of("forge","fmlloader").contains(coordinate[1]))continue;
   int split=coordinate[2].indexOf('-');if(split<0)continue;
   String detectedMc=coordinate[2].substring(0,split),detectedForge=coordinate[2].substring(split+1);
   if(!mc.isEmpty()&&!mc.equals(detectedMc)||!forge.isEmpty()&&!forge.equals(detectedForge))
    throw new IOException("实例中的 Forge 版本声明不一致。");
   mc=detectedMc;forge=detectedForge;
  }
  RuntimeRouter.Selection route=RuntimeRouter.select(mc,forge);
  if(route==null)throw new IOException("此实例尚无适配：Minecraft "+mc+" / Forge "+forge+"。支持范围为 1.20.1 至 26.3 的已发行 Forge 分支。");
  boolean minimum=true;
  if(forge.startsWith("47.")){String[] parts=forge.split("[.-]");minimum=Integer.parseInt(parts[1])>3||Integer.parseInt(parts[1])==3&&Integer.parseInt(parts[2])>=22;}
  if(route==null||!minimum)throw new IOException("此实例尚无适配：Minecraft "+mc+" / Forge "+forge+"。支持范围为 1.20.1 至 26.3 的已发行 Forge 分支。");
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
   var entry=zip.getEntry("META-INF/mods.toml");
   if(entry==null)return zip.getEntry("shanghan/early/runtime.json")!=null
     &&zip.getEntry("META-INF/services/net.minecraftforge.fml.loading.ImmediateWindowProvider")!=null
     &&(zip.getEntry("shanghan/early/RoutingWindowProvider.class")!=null||zip.getEntry("shanghan/early/ShangHanProvider.class")!=null);
   try(InputStream in=zip.getInputStream(entry)){String text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);var blocks=Pattern.compile("(?s)\\[\\[mods\\]\\](.*?)(?=\\r?\\n\\s*\\[|\\z)").matcher(text);while(blocks.find())if(Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"\']shanghanearlyloading[\"\']").matcher(blocks.group(1)).find())return true;return false;}
  }catch(IOException ignored){return false;}
 }
 static Path install(Path raw)throws Exception{
  Path instance=resolveInstance(raw);
  byte[] boot=resource("/payload/"+BOOT),client=resource("/payload/"+CLIENT); // Validate payload before modifying anything.
  Path b=backup(instance,"install");save(version(instance),b,"version.json");save(instance.resolve("config/fml.toml"),b,"fml.toml");save(config(instance).resolve("config.json"),b,"config.json");
  JsonObject manifest=new JsonObject();manifest.addProperty("instance",instance.toString());manifest.addProperty("version",VERSION);
  JsonArray oldMods=new JsonArray();Path mods=instance.resolve("mods");Files.createDirectories(mods);
  java.util.List<Path> previous;
  try(var stream=Files.list(mods)){previous=stream.filter(AuthorTool::ownedMod).toList();}
  for(Path file:previous)oldMods.add(file.getFileName().toString());
  manifest.add("oldMods",oldMods);manifest.addProperty("newMod",CLIENT);
  JsonArray newMods=new JsonArray();newMods.add(CLIENT);newMods.add(BOOT);manifest.add("newMods",newMods);manifest.addProperty("clientWritten",false);
  write(b.resolve("backup.json"),manifest);
  try{
   for(Path file:previous)move(file,b.resolve("mods").resolve(file.getFileName()));
   manifest.addProperty("clientWritten",true);write(b.resolve("backup.json"),manifest);
   Files.write(mods.resolve(BOOT),boot);Files.write(mods.resolve(CLIENT),client);
   JsonObject data=json(version(instance));JsonArray libs=data.has("libraries")?data.getAsJsonArray("libraries"):new JsonArray(),updated=new JsonArray();
   for(JsonElement entry:libs)if(!entry.getAsJsonObject().get("name").getAsString().startsWith("shanghan:earlyboot:"))updated.add(entry);
   // Normal installations need only two mods. Remove an older Maven injection only when upgrading it.
   if(updated.size()!=libs.size()){data.add("libraries",updated);write(version(instance),data);}
   Path fml=instance.resolve("config/fml.toml");String text=Files.exists(fml)?Files.readString(fml):"";String provider="earlyWindowProvider = \"shanghanearlyloading\"";
   if(Pattern.compile("(?m)^\\s*earlyWindowProvider\\s*=").matcher(text).find())text=text.replaceAll("(?m)^\\s*earlyWindowProvider\\s*=.*$",provider);else text+="\n"+provider+"\n";
   if(Pattern.compile("(?m)^\\s*earlyWindowControl\\s*=").matcher(text).find())text=text.replaceAll("(?m)^\\s*earlyWindowControl\\s*=.*$","earlyWindowControl = true");else text+="\nearlyWindowControl = true\n";
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
  JsonArray installed=m.has("newMods")?m.getAsJsonArray("newMods"):new JsonArray();
  if(!m.has("newMods"))installed.add(m.get("newMod"));
  if(m.has("clientWritten")&&m.get("clientWritten").getAsBoolean())for(JsonElement name:installed){
   Path current=instance.resolve("mods").resolve(name.getAsString());
   if(Files.exists(current))move(current,b.resolve("rollback-archive").resolve(current.getFileName()));
  }
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
  instance=resolveInstance(instance);
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
  instance=resolveInstance(instance);if(!Files.isRegularFile(input))throw new IOException("请先选择视频。");
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
 static Path instance()throws IOException{
  if(instanceField.getText().isBlank())throw new IOException("请先选择整合包或游戏实例。");
  return resolveInstance(Path.of(instanceField.getText()));
 }
 static JPanel row(String label,JComponent field,JComponent control){JPanel row=new JPanel(new BorderLayout(12,0));row.add(new JLabel(label),BorderLayout.WEST);row.add(field);if(control!=null)row.add(control,BorderLayout.EAST);return row;}
 static String candidateLabel(Path path){
  try{return path+" · Forge "+forge(path);}catch(IOException e){return path.toString();}
 }
 static Path selectCandidate(java.util.List<Path> candidates,Function<java.util.List<Path>,Path> picker)throws IOException{
  Path selected=candidates.size()==1?candidates.get(0):picker.apply(candidates);
  if(selected!=null&&!candidates.contains(selected))throw new IOException("实例选择无效，请重新选择。");
  return selected;
 }
 static Path promptCandidate(java.util.List<Path> candidates){
  Object[] labels=candidates.stream().map(AuthorTool::candidateLabel).toArray();
  Object value=JOptionPane.showInputDialog(frame,"找到多个 Forge 实例，请选择要安装动画的实例：","选择游戏实例",JOptionPane.QUESTION_MESSAGE,null,labels,labels[0]);
  if(value==null)return null;
  for(int i=0;i<labels.length;i++)if(labels[i].equals(value))return candidates.get(i);
  return null;
 }
 static void applyInstanceSelection(Path selected)throws IOException{
  String detected=forge(selected);int chosenVolume=80,chosenQuality=0;
  Path cfg=config(selected).resolve("config.json");
  if(Files.exists(cfg))try{
   JsonObject c=json(cfg);
   if(c.has("volume"))chosenVolume=(int)Math.round(Math.max(0,Math.min(1,c.get("volume").getAsDouble()))*100);
   if(c.has("width"))chosenQuality=c.get("width").getAsInt()>640?1:0;
  }catch(RuntimeException e){throw new IOException("此实例的启动动画配置无效，请先检查 config.json。",e);}
  instanceField.setText(selected.toString());volume.setValue(chosenVolume);quality.setSelectedIndex(chosenQuality);
  status.setText("Forge "+detected+" · 已识别实例 · 请先退出该实例游戏和启动器。");
 }
 static void chooseInstance(){
  JFileChooser chooser=new JFileChooser(HOME.toFile());chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setDialogTitle("选择整合包文件夹或已安装的游戏实例");
  if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
  for(JButton button:buttons)button.setEnabled(false);bar.setIndeterminate(true);status.setText("正在查找 Forge 实例…");
  Path selected=chooser.getSelectedFile().toPath();
  new SwingWorker<java.util.List<Path>,Void>(){
   protected java.util.List<Path> doInBackground()throws Exception{return discoverInstances(selected);}
   protected void done(){
    bar.setIndeterminate(false);for(JButton button:buttons)button.setEnabled(true);
    try{
     Path candidate=selectCandidate(get(),AuthorTool::promptCandidate);
     if(candidate==null){status.setText("已取消选择，原实例保持不变。");return;}
     applyInstanceSelection(candidate);
    }catch(Exception error){
     Throwable cause=error.getCause()==null?error:error.getCause();
     status.setText("未选择新实例："+cause.getMessage());
     JOptionPane.showMessageDialog(frame,cause.getMessage(),"请选择有效实例",JOptionPane.INFORMATION_MESSAGE);
    }
   }
  }.execute();
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
  frame=new JFrame("Forge 启动视频设置 · "+TOOL_VERSION);frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);frame.setSize(820,360);frame.setLocationRelativeTo(null);
  JPanel panel=new JPanel(new GridLayout(0,1,0,12));panel.setBorder(new EmptyBorder(20,24,20,24));
  panel.add(new JLabel("Minecraft 1.20.1–26.3 · 自动识别 Forge 版本 · 更换视频无需重新编译"));
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
