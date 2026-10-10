package shanghan.early;
import java.util.Optional;import java.util.concurrent.atomic.AtomicReference;
/** Small bootstrap-layer extension point. No dependence on Minecraft GUI or Forge event classes. */
public final class LoadingApi {
 private static final AtomicReference<MediaSession> SESSION=new AtomicReference<>();
 private LoadingApi(){}
 static void bind(MediaSession session){SESSION.set(session);}
 static void clear(MediaSession session){SESSION.compareAndSet(session,null);}
 public static boolean useProgressProvider(ProgressProvider provider){MediaSession session=SESSION.get();if(session==null||session.stopped)return false;session.setProgressProvider(provider);return true;}
 public static Optional<ProgressProvider.Snapshot> currentProgress(){MediaSession session=SESSION.get();return session==null?Optional.empty():Optional.of(session.progressState());}
}
