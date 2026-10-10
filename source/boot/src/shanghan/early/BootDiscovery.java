package shanghan.early;
import cpw.mods.modlauncher.api.*;
import java.util.*;
/** Makes the provider visible in ModLauncher's SERVICE layer. No bytecode transformers. */
public final class BootDiscovery implements ITransformationService {
 public String name(){return "shanghanearlyboot";}
 public void initialize(IEnvironment environment){}
 public void onLoad(IEnvironment environment, Set<String> services){}
 public List<ITransformer> transformers(){return List.of();}
}
