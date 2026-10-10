import shanghan.early.*;import java.nio.file.*;
public final class StreamPlaybackSmoke {
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);Files.createDirectories(dir.resolve("bin"));
  Files.copy(Path.of(args[1]),dir.resolve("bin/ffmpeg.exe"),StandardCopyOption.REPLACE_EXISTING);
  Files.copy(Path.of(args[2]),dir.resolve("loop.mp4"),StandardCopyOption.REPLACE_EXISTING);
  Settings s=new Settings();s.video="loop.mp4";s.audioFile="loop.mp4";s.volume=0;s.audio=true;
  MediaSession m=new MediaSession(dir,s);m.start();long limit=System.nanoTime()+10_000_000_000L;int received=0;double maxLag=0;
  try{
   while(System.nanoTime()<limit&&m.clock()<5){
    var f=m.latest();if(f!=null){received++;maxLag=Math.max(maxLag,Math.abs(m.clock()-f.index()/(double)s.fps));m.release(f);}
    if(m.frames.size()>s.queueFrames)throw new AssertionError("Queue grew");
    if(m.videoFailed)throw new AssertionError("Video failed");Thread.sleep(8);
   }
   if(m.clock()<4.5||received<120)throw new AssertionError("Clock/frame playback stalled: "+m.clock()+" "+received);
   if(maxLag>.15)throw new AssertionError("A/V frame scheduling lag "+maxLag);
   System.out.println("PASS loop (>2s input), external audio, received="+received+", clock="+m.clock()+", maxFrameLagSeconds="+maxLag+", audioFallback="+m.audioFailed+", queue<="+s.queueFrames);
  }finally{m.close();m.finishLogging();}
  if(ProcessHandle.current().children().anyMatch(ProcessHandle::isAlive))throw new AssertionError("Decoder process leak");
 }
}
