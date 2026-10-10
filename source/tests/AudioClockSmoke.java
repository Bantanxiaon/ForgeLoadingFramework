public final class AudioClockSmoke {
 public static void main(String[] args)throws Exception{
  Class<?> c=Class.forName("shanghan.early.AudioClock");var ctor=c.getDeclaredConstructor();ctor.setAccessible(true);
  var update=c.getDeclaredMethod("update",double.class,long.class);update.setAccessible(true);Object clock=ctor.newInstance();
  int frame=-1,changes=0;double previous=0,maxLag=0;long start=1_000_000_000L;
  for(int tick=0;tick<300;tick++){
   double time=tick/30.0,device=Math.floor(time/.05)*.05;
   double value=(double)update.invoke(clock,device,start+Math.round(time*1e9));
   if(value<previous)throw new AssertionError("Audio clock moved backwards");previous=value;
   int current=(int)Math.floor((value+1.0/60)*30);
   if(current!=frame){frame=current;changes++;}maxLag=Math.max(maxLag,Math.abs(time-value));
  }
  if(changes<295||maxLag>.07)throw new AssertionError("Coarse device counter skipped video: "+changes+" lag="+maxLag);
  double held=(double)update.invoke(clock,Math.floor((299/30.0)/.05)*.05,start+12_000_000_000L);
  if(held>previous+.11)throw new AssertionError("Frozen audio device extrapolated indefinitely");
  var settings=new shanghan.early.Settings();settings.audio=false;
  var media=new shanghan.early.MediaSession(java.nio.file.Path.of(args[0]),settings);
  try{
   var origin=shanghan.early.MediaSession.class.getDeclaredField("origin");origin.setAccessible(true);origin.setLong(media,System.nanoTime()-50_000_000L);
   media.frames.add(new shanghan.early.MediaSession.Frame(0,new byte[640*360*3/2]));
   media.frames.add(new shanghan.early.MediaSession.Frame(1,new byte[640*360*3/2]));
   var due=media.latest();if(due==null||due.index()!=0||media.frames.size()!=1)throw new AssertionError("Still-valid due frame skipped by scheduling jitter");
  }finally{media.close();media.finishLogging();}
  System.out.println("PASS 50ms sound-device position blocks: "+changes+"/300 video ticks, monotonic clock, max lag="+maxLag+", stalled device bounded, valid due frames retained in order");
 }
}
