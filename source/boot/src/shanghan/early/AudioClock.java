package shanghan.early;
/** Interpolates the coarse position reported by Windows sound devices without moving backwards. */
final class AudioClock {
 private double estimate,lastDevice;
 private long lastTick,lastAdvance;
 synchronized double update(double device,long now){
  if(lastTick==0){estimate=device;lastDevice=device;lastTick=lastAdvance=now;return estimate;}
  double elapsed=Math.max(0,(now-lastTick)/1e9);
  if(device!=lastDevice){
   lastAdvance=now;lastDevice=device;
   // Device counters arrive in blocks. Correct drift gradually instead of skipping video frames at every block.
   estimate+=elapsed;
   double error=device-estimate;
   if(error>0)estimate+=Math.min(.002,error);
   else if(error<-.050000001)estimate-=Math.min(Math.min(elapsed,.002),-error-.05);
  }else if(now-lastAdvance<=100_000_000L)estimate+=elapsed;
  lastTick=now;return estimate;
 }
}
