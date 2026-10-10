package shanghan.client;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
@Mod("shanghanearlyloading")
public final class ShangHanMod {
 public ShangHanMod(){ModLoadingContext.get().registerDisplayTest("IGNORE_SERVER_VERSION",(version,isServer)->true);}
}
