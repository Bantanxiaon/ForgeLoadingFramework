package shanghan.client;
import org.apache.logging.log4j.LogManager;
import shanghan.early.MediaSession;
/** Attach within the GAME layer as well, before ModContainer construction begins. */
public final class DetailBridge {
 public static void install(MediaSession media){
  media.tasks.attach(LogManager.getLogger("net.minecraftforge.fml.javafmlmod.FMLModContainer"));
 }
}
