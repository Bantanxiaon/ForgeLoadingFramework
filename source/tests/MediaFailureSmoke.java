import shanghan.early.*;
import java.nio.file.*;
public final class MediaFailureSmoke{
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);Files.createDirectories(root);
  run(root.resolve("missing"),args[1],"missing.mp4",false,"",true,false);
  Path bad=root.resolve("corrupt");Files.createDirectories(bad);Files.write(bad.resolve("bad.mp4"),new byte[1024]);
  run(bad,args[1],"bad.mp4",false,"",true,false);
  run(root.resolve("audio-device"),args[1],args[2],true,"ShangHan-Smoke-Missing-Mixer",false,true);
  System.out.println("PASS missing/corrupt video, unavailable audio device, bounded queue and decoder cleanup");
 }
 static void run(Path dir,String ffmpeg,String video,boolean audio,String mixer,boolean vf,boolean af)throws Exception{
  Files.createDirectories(dir);Files.createDirectories(dir.resolve("bin"));Files.copy(Path.of(ffmpeg),dir.resolve("bin/ffmpeg.exe"),StandardCopyOption.REPLACE_EXISTING);
  if(Files.isRegularFile(Path.of(video)))Files.copy(Path.of(video),dir.resolve("input.mp4"),StandardCopyOption.REPLACE_EXISTING);
  Settings s=new Settings();s.width=320;s.height=180;s.queueFrames=3;s.ffmpeg="bin/ffmpeg.exe";s.video=Files.isRegularFile(Path.of(video))?"input.mp4":video;s.audio=audio;s.audioMixer=mixer;
  MediaSession m=new MediaSession(dir,s);m.start();long end=System.nanoTime()+5_000_000_000L;
  try{
   while(System.nanoTime()<end){
    if(vf&&m.videoFailed)break;if(af&&m.audioFailed&&m.decoded>0)break;Thread.sleep(20);
   }
   if(vf&&!m.videoFailed)throw new AssertionError("Video error did not fall back");
   if(af&&!m.audioFailed)throw new AssertionError("Audio error did not fall back");
   if(m.frames.size()>s.queueFrames)throw new AssertionError("Unbounded frame queue");
   if(af){Thread.sleep(100);if(m.clock()<=0)throw new AssertionError("Silent clock did not advance");}
  }finally{m.close();m.finishLogging();}
  Thread.sleep(100);
  if(ProcessHandle.current().children().anyMatch(ProcessHandle::isAlive))throw new AssertionError("Decoder process leaked");
 }
}
