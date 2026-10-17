import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.security.*;
public final class AuthorDiscoverySmoke {
 static int checks;
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 interface Action{void run()throws Exception;}
 static void rejected(Action a,String label)throws Exception{
  try{a.run();throw new AssertionError("accepted "+label);}catch(IOException e){check(e.getMessage()!=null&&!e.getMessage().contains("Cannot invoke"),label);}
 }
 static Path make(Path dir)throws Exception{
  Files.createDirectories(dir);
  Files.writeString(dir.resolve(dir.getFileName()+".json"),"{\"arguments\":{\"game\":[\"--fml.mcVersion\",\"1.20.1\",\"--fml.forgeVersion\",\"47.3.22\"]},\"libraries\":[]}");
  return dir.toAbsolutePath().normalize();
 }
 static Map<String,String> snapshot(Path dir)throws Exception{
  Map<String,String> result=new TreeMap<>();
  try(var files=Files.walk(dir)){for(Path f:files.filter(Files::isRegularFile).toList())
   result.put(dir.relativize(f).toString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));}
  return result;
 }
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]).toAbsolutePath().normalize();Files.createDirectories(root);
  Path single=make(root.resolve("single/.minecraft/versions/中文实例"));
  Path custom=make(root.resolve("custom"));
  Path wrapped=make(root.resolve("wrapper/整合包/.minecraft/versions/Pack"));
  Path a=make(root.resolve("multi/.minecraft/versions/A")),b=make(root.resolve("multi/.minecraft/versions/B"));
  Path invalid=root.resolve("invalid");Files.createDirectories(invalid);
  Files.writeString(invalid.resolve("invalid.json"),"not json");
  Path fabric=root.resolve("fabric");Files.createDirectories(fabric);Files.writeString(fabric.resolve("fabric.json"),"{\"libraries\":[]}");
  Path badConfig=make(root.resolve("badConfig"));Files.createDirectories(badConfig.resolve("loading"));Files.writeString(badConfig.resolve("loading/config.json"),"[]");
  Map<String,String> before=snapshot(root);
  check(AuthorTool.TOOL_VERSION.equals("0.4.1"),"tool version");
  check(AuthorTool.VERSION.equals("0.4.0"),"runtime version unchanged");
  check(AuthorTool.resolveInstance(root.resolve("single")).equals(single),"outer pack");
  check(AuthorTool.resolveInstance(single.getParent().getParent()).equals(single),"minecraft root");
  check(AuthorTool.resolveInstance(single.getParent()).equals(single),"versions root");
  check(AuthorTool.resolveInstance(single).equals(single),"direct standard instance");
  check(AuthorTool.resolveInstance(custom).equals(custom),"custom layout");
  check(AuthorTool.resolveInstance(root.resolve("wrapper")).equals(wrapped),"one wrapper");
  check(AuthorTool.discoverInstances(root.resolve("multi")).size()==2,"multiple candidates");
  rejected(()->AuthorTool.resolveInstance(root.resolve("multi")),"CLI ambiguous instance");
  check(AuthorTool.selectCandidate(List.of(a,b),items->b).equals(b),"explicit multiple choice");
  check(AuthorTool.selectCandidate(List.of(a,b),items->null)==null,"cancel selection");
  rejected(()->AuthorTool.selectCandidate(List.of(a,b),items->custom),"reject unrelated choice");
  rejected(()->AuthorTool.resolveInstance(Path.of("D:/")),"drive root");
  rejected(()->AuthorTool.version(Path.of("D:/")),"root version path");
  rejected(()->AuthorTool.resolveInstance(root.resolve("missing")),"missing folder");
  rejected(()->AuthorTool.resolveInstance(invalid),"invalid JSON");
  rejected(()->AuthorTool.resolveInstance(fabric),"non Forge JSON");
  AuthorTool.instanceField.setText("previous");
  rejected(()->AuthorTool.applyInstanceSelection(badConfig),"invalid config");
  check(AuthorTool.instanceField.getText().equals("previous"),"invalid selection retains previous");
  AuthorTool.applyInstanceSelection(single);
  check(AuthorTool.instanceField.getText().equals(single.toString()),"valid selection applied");
  check(snapshot(root).equals(before),"discovery and validation are read only");
  byte[] original=Files.readAllBytes(AuthorTool.version(single));
  Path backup=AuthorTool.install(root.resolve("single"));
  check(Files.isRegularFile(single.resolve("mods/"+AuthorTool.BOOT)),"bootstrap installed");
  check(Files.isRegularFile(single.resolve("mods/"+AuthorTool.CLIENT)),"client installed");
  check(Arrays.equals(original,Files.readAllBytes(AuthorTool.version(single))),"standard Forge JSON untouched");
  check(Files.readString(single.resolve("config/fml.toml")).contains("shanghanearlyloading"),"provider configured");
  check(Files.isRegularFile(single.resolve("loading/bin/ffmpeg.exe")),"decoder installed");
  AuthorTool.restore(root.resolve("single"));
  check(!Files.exists(single.resolve("mods/"+AuthorTool.BOOT)),"restore removes bootstrap to backup");
  check(Arrays.equals(original,Files.readAllBytes(AuthorTool.version(single))),"restore preserves JSON");
  check(Files.isRegularFile(backup.resolve("rollback-archive/"+AuthorTool.CLIENT)),"restore archives replaced files");
  Path noLib=make(root.resolve("withoutLibraries"));
  Files.writeString(AuthorTool.version(noLib),"{\"arguments\":{\"game\":[\"--fml.mcVersion\",\"1.20.1\",\"--fml.forgeVersion\",\"47.3.22\"]}}");
  AuthorTool.install(noLib);AuthorTool.restore(noLib);check(true,"flags only profile install and restore");
  System.out.println("AUTHOR_CHECKS_OK "+checks);
 }
}
