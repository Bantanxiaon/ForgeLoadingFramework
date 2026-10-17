package shanghan.early;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Streaming decoder subprocesses. CPU-decoded YUV queue is bounded; no hardware decoder is requested. SoundEngine/OpenAL are never touched. */
public final class MediaSession implements AutoCloseable {
 public record Frame(long index,byte[] rgba){}
 public final Settings settings;
 public final Path dir;
 public final LoadingProgress progress=new LoadingProgress();
 public final ModTaskObserver tasks=new ModTaskObserver();
 private volatile ProgressProvider progressProvider=progress;
 public void setProgressProvider(ProgressProvider provider){progressProvider=Objects.requireNonNull(provider);}
 public ProgressProvider.Snapshot progressState(){try{var state=progressProvider.snapshot();if(state==null||!Double.isFinite(state.overall())||state.stage()==null||state.sourceLabel()==null)throw new IllegalArgumentException("Invalid progress snapshot");return state;}catch(Throwable e){progressProvider=progress;log("PROGRESS_PROVIDER_FALLBACK "+e);return progress.snapshot();}}
 public final ArrayBlockingQueue<Frame> frames;
 private final ArrayBlockingQueue<byte[]> buffers;
 public volatile boolean videoFailed, audioFailed, stopped;
 public volatile long decoded,dropped,presented;
 private volatile Process videoProcess,audioProcess;
 private volatile SourceDataLine line;
 private volatile long origin,failedAudioAt;
 private volatile double failedAudioClock;
 private volatile boolean soundClock;
 private final AudioClock audioClock=new AudioClock();
 private final CountDownLatch firstFrame=new CountDownLatch(1);
 private final AtomicBoolean started=new AtomicBoolean(),closing=new AtomicBoolean();
 private Thread videoThread,audioThread,shutdownHook;
 private boolean logClosed;
 private volatile float fade=1;
 private volatile long fadeBegin;
 public volatile boolean detached;
 public volatile boolean nativeWindowFade;
 private volatile boolean renderReleased;
 public void markRenderReleased(){renderReleased=true;}
 public boolean readyForWindowClose(){return stopped&&renderReleased;}
 public Runnable showGame=()->{},closeWindow=()->{},abortLoading=()->{},fitWindow=()->{};
 public void beginFade(){if(fadeBegin==0)fadeBegin=System.nanoTime();}
 public float opacity(){long t=fadeBegin;return t==0?(settings.fadeInMs==0||origin==0?1:Math.min(1,(System.nanoTime()-origin)/(settings.fadeInMs*1_000_000f))):Math.max(0,1-(System.nanoTime()-t)/(settings.fadeOutMs*1_000_000f));}
 private final PrintWriter log;
 public MediaSession(Path dir,Settings settings){
  this.dir=dir;this.settings=settings;frames=new ArrayBlockingQueue<>(settings.queueFrames);
  buffers=new ArrayBlockingQueue<>(settings.queueFrames+2);
  int frameBytes=settings.width*settings.height*3/2;
  for(int i=0;i<settings.queueFrames+2;i++)buffers.add(new byte[frameBytes]);
  PrintWriter l;
  try{Files.createDirectories(dir);l=new PrintWriter(Files.newBufferedWriter(dir.resolve("runtime.log")),true);}
  catch(IOException e){l=new PrintWriter(System.err,true);}log=l;
  tasks.attach(org.apache.logging.log4j.LogManager.getLogger("net.minecraftforge.fml.javafmlmod.FMLModContainer"));
  LoadingApi.bind(this);log("START fps="+settings.fps+" queue="+settings.queueFrames+" size="+settings.width+"x"+settings.height);
 }
 public void start(){
  if(!started.compareAndSet(false,true))return;
  videoThread=new Thread(this::decodeVideo,"LoadingFramework-video");videoThread.setDaemon(true);videoThread.start();
  if(settings.audio){audioThread=new Thread(this::decodeAudio,"LoadingFramework-audio");audioThread.setDaemon(true);audioThread.start();}
  shutdownHook=new Thread(()->{close();finishLogging();},"LoadingFramework-cleanup");Runtime.getRuntime().addShutdownHook(shutdownHook);
 }
 public synchronized void startClock(){if(origin==0)origin=System.nanoTime();}
 public double clock(){
  SourceDataLine l=line;
  if(soundClock&&l!=null)return audioClock.update(l.getLongFramePosition()/48000.0,System.nanoTime());
  if(failedAudioAt!=0)return failedAudioClock+(System.nanoTime()-failedAudioAt)/1e9;
  long o=origin;return o==0?0:(System.nanoTime()-o)/1e9;
 }
 public void fade(float alpha){fade=Math.max(0,Math.min(1,alpha));}
 private List<String> command(String input){
  Path binary=settings.resource(dir,settings.ffmpeg);
  if(!Files.isRegularFile(binary))throw new IllegalStateException("FFmpeg missing: "+binary);
  return new ArrayList<>(List.of(binary.toString(),"-nostdin","-hide_banner","-loglevel","error","-filter_threads","1","-filter_complex_threads","1","-hwaccel","none","-threads","1","-stream_loop","-1","-i",settings.resource(dir,input).toString()));
 }
 private Process spawn(List<String> c,String tag)throws IOException{
  ProcessBuilder b=new ProcessBuilder(c);b.directory(dir.toFile());b.redirectError(dir.resolve(tag+"-decoder.log").toFile());return b.start();
 }
 private void decodeVideo(){
  try{
   if(!Files.isRegularFile(settings.resource(dir,settings.video)))throw new IOException("Video missing");
   List<String> c=command(settings.video);
   Collections.addAll(c,"-an","-vf","scale="+settings.width+":"+settings.height+":force_original_aspect_ratio=decrease:out_color_matrix=bt709:out_range=tv,pad="+settings.width+":"+settings.height+":(ow-iw)/2:(oh-ih)/2,fps="+settings.fps+",setsar=1","-pix_fmt","yuv420p","-threads","1","-f","rawvideo","pipe:1");
   videoProcess=spawn(c,"video");
   try(InputStream in=videoProcess.getInputStream()){
    long i=0;int bytes=settings.width*settings.height*3/2;
    while(!stopped){
     byte[] rgba=buffers.poll(100,TimeUnit.MILLISECONDS);if(rgba==null)continue;
     int count=in.readNBytes(rgba,0,bytes);
     if(count!=bytes)throw new EOFException("Video decoder ended, status="+(videoProcess.isAlive()?"running":videoProcess.exitValue()));
     Frame f=new Frame(i++,rgba);decoded++;firstFrame.countDown();
     if(f.index()/(double)settings.fps<clock()-.10){dropped++;release(f);continue;}
     while(!stopped&&!frames.offer(f,50,TimeUnit.MILLISECONDS)){
      if(f.index()/(double)settings.fps<clock()-.10){dropped++;release(f);break;}
     }
    }
   }
  }catch(Exception e){
   if(!stopped){videoFailed=true;log("VIDEO_FALLBACK "+e);}
   firstFrame.countDown();
   Process p=videoProcess;if(p!=null)p.destroyForcibly();
  }
 }
 private void decodeAudio(){
  try{
   AudioFormat format=new AudioFormat(48000,16,2,true,false);
   SourceDataLine output;
   if(settings.audioMixer==null||settings.audioMixer.isBlank())output=AudioSystem.getSourceDataLine(format);
   else{
    Mixer.Info info=Arrays.stream(AudioSystem.getMixerInfo()).filter(x->x.getName().equals(settings.audioMixer)).findFirst().orElseThrow(()->new IllegalArgumentException("Audio mixer unavailable: "+settings.audioMixer));
    output=(SourceDataLine)AudioSystem.getMixer(info).getLine(new DataLine.Info(SourceDataLine.class,format));
   }
   output.open(format,48000/10*4);line=output;
   List<String> c=command(settings.audioFile==null||settings.audioFile.isBlank()?settings.video:settings.audioFile);
   Collections.addAll(c,"-vn","-af","aresample=async=1:first_pts=0","-ac","2","-ar","48000","-f","s16le","pipe:1");
   audioProcess=spawn(c,"audio");
   if(!firstFrame.await(5,TimeUnit.SECONDS))log("AUDIO_VIDEO_WARMUP_TIMEOUT");
   if(stopped)return;
   soundClock=true;output.start();startClock();
   log("AUDIO_READY "+output.getLineInfo());
   try(InputStream in=audioProcess.getInputStream()){
    byte[] pcm=new byte[4096];
    while(!stopped){
     int n=in.readNBytes(pcm,0,pcm.length);if(n==0)throw new EOFException("Audio decoder ended");n-=n%4;
     // Apply volume/fade to PCM, works on devices without MASTER_GAIN support.
     float gain=settings.volume*Math.min(fade,opacity());
     for(int i=0;i+1<n;i+=2){int x=(short)((pcm[i]&255)|(pcm[i+1]<<8));int y=Math.round(x*gain);pcm[i]=(byte)y;pcm[i+1]=(byte)(y>>8);}
     int off=0;while(!stopped&&off<n)off+=output.write(pcm,off,n-off);
    }
   }
  }catch(Exception e){
   if(!stopped){
    failedAudioClock=clock();failedAudioAt=System.nanoTime();soundClock=false;audioFailed=true;
    log("AUDIO_FALLBACK "+e);startClock();
   }
  }finally{
   SourceDataLine l=line;if(l!=null){l.stop();l.flush();l.close();}
   line=null;soundClock=false;
   Process p=audioProcess;if(p!=null)p.destroyForcibly();
  }
 }
 public Frame latest(){
  // Wait for audio warm-up briefly, then use monotonic clock if unavailable.
  if(origin==0&&(!settings.audio||audioFailed)){startClock();}
  double now=clock();Frame result=null;
  // Keep valid due frames in order. Choosing the newest at each tick turns harmless device/render jitter into repeats and skipped frames.
  while(true){
   Frame head=frames.peek();if(head==null||head.index()/(double)settings.fps>now+.5/settings.fps)break;
   Frame f=frames.poll();if(f==null)continue;
   if(f.index()/(double)settings.fps<now-.10){dropped++;release(f);continue;}
   result=f;presented++;break;
  }
  return result;
 }
 public void release(Frame frame){if(frame!=null&&!stopped)buffers.offer(frame.rgba());}
 public synchronized void log(String s){if(!logClosed)log.println(System.currentTimeMillis()+" "+s);}
 public synchronized void finishLogging(){if(!logClosed){log.flush();log.close();logClosed=true;}}
 public void close(){
  if(!closing.compareAndSet(false,true))return;stopped=true;
  Process v=videoProcess,a=audioProcess;if(v!=null)v.destroyForcibly();if(a!=null)a.destroyForcibly();
  SourceDataLine l=line;if(l!=null){l.stop();l.flush();l.close();}
  for(Thread t:new Thread[]{videoThread,audioThread})if(t!=null&&t!=Thread.currentThread()){t.interrupt();try{t.join(300);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
  if(shutdownHook!=null&&shutdownHook!=Thread.currentThread())try{Runtime.getRuntime().removeShutdownHook(shutdownHook);}catch(IllegalStateException ignored){}
  LoadingApi.clear(this);tasks.close();frames.clear();buffers.clear();log("STOP decoded="+decoded+" presented="+presented+" dropped="+dropped);
  log.flush();
 }
}
