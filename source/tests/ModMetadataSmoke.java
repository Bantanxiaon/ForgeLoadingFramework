import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import shanghan.early.RuntimeProfile;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/** Uses Forge's real TOML/Maven parsers against current and defective old JARs. */
public final class ModMetadataSmoke {
 private static final String MOD_ID = "shanghanearlyloading";
 private static final String[][] MATRIX = {
  {"1.20.1", "47.4.28"}, {"1.20.2", "48.1.0"}, {"1.20.3", "49.0.2"},
  {"1.20.4", "49.2.13"}, {"1.20.6", "50.2.14"}, {"1.21", "51.0.33"},
  {"1.21.1", "52.1.20"}, {"1.21.3", "53.1.16"}, {"1.21.4", "54.1.22"},
  {"1.21.5", "55.1.18"}, {"1.21.6", "56.0.9"}, {"1.21.7", "57.0.3"},
  {"1.21.8", "58.1.26"}, {"1.21.9", "59.0.5"}, {"1.21.10", "60.1.18"},
  {"1.21.11", "61.2.4"}, {"26.1", "62.0.9"}, {"26.1.1", "63.0.2"},
  {"26.1.2", "64.1.5"}, {"26.2", "65.1.5"}, {"26.3", "66.0.12"}
 };

 public static void main(String[] args) throws Exception {
  if(args.length != 1)throw new AssertionError("Expected the test fixture directory");
  Path.of(args[0]);
  var codeSource = RuntimeProfile.class.getProtectionDomain().getCodeSource();
  if(codeSource == null)throw new AssertionError("Bootstrap has no code source");
  Path bootstrap = Path.of(codeSource.getLocation().toURI());
  if(!Files.isRegularFile(bootstrap))throw new AssertionError("Bootstrap JAR is unavailable");
  JsonObject bootProfile;
  try(ZipFile jar = new ZipFile(bootstrap.toFile())) {
   bootProfile = readProfile(jar, "shanghan/early/runtime.json");
  }
  String version = RuntimeProfile.version();
  Path client = bootstrap.getParent().resolve("ForgeLoadingFramework-" + version + ".jar");
  verifyCurrentClient(client, bootProfile, version);

  Path previous = bootstrap.getParent().resolve("ForgeLoadingFramework-0.3.3.jar");
  if(!Files.isRegularFile(previous)) previous = bootstrap.getParent().getParent()
    .resolve("source/tests/fixtures/ForgeLoadingFramework-0.3.3.jar");
  if(previous.equals(client))throw new AssertionError("Negative fixture must be a different artifact");
  try(ZipFile jar = new ZipFile(previous.toFile())) {
   JsonObject oldProfile = readProfile(jar, "shanghan/client/runtime.json");
   if(!oldProfile.get("version").getAsString().equals("0.3.3")
     || !oldProfile.getAsJsonArray("forgeBuilds").contains(new JsonPrimitive("47.4.16")))
    throw new AssertionError("0.3.3 no longer represents the original missed-range defect");
   VersionRange oldRange = dependencyRange(readToml(jar), "forge");
   reject(oldRange, "47.4.16", "original defective Forge declaration");
   try {
    requireLegacyAdvertisedBuilds(oldRange, oldProfile);
    throw new AssertionError("The consistency check did not detect the original 0.3.3 defect");
   } catch(RejectedBuild expected) {
    if(!expected.build.equals("47.4.16"))throw expected;
   }
  }
  System.out.println("ModMetadataSmoke PASS actual client TOML/profile schema2 versions and ranges agree; "
    + "21 official Minecraft/Forge pairs and future patch builds accepted; Forge67/MC26.4 excluded; "
    + "the original 0.3.3 missed 47.4.16 declaration is detected.");
 }

 private static void verifyCurrentClient(Path client, JsonObject bootProfile, String version) throws Exception {
  try(ZipFile jar = new ZipFile(client.toFile())) {
   JsonObject clientProfile = readProfile(jar, "shanghan/client/runtime.json");
   if(!clientProfile.equals(bootProfile))
    throw new AssertionError("Bootstrap and client runtime profiles differ");
   if(clientProfile.get("schemaVersion").getAsInt() != 2
     || !clientProfile.get("version").getAsString().equals(version))
    throw new AssertionError("Client runtime identity differs from the bootstrap");
   UnmodifiableConfig toml = readToml(jar);
   UnmodifiableConfig mod = onlyEntry(toml, List.of("mods"), MOD_ID);
   if(!version.equals(mod.get("version")))
    throw new AssertionError("mods.toml mod version differs from runtime.json");

   String loaderSpec = toml.get("loaderVersion");
   String forgeSpec = dependencySpecification(toml, "forge");
   String minecraftSpec = dependencySpecification(toml, "minecraft");
   if(!"[47,67)".equals(loaderSpec) || !"[47.3.22,67)".equals(forgeSpec)
     || !"[1.20.1,26.4)".equals(minecraftSpec))
    throw new AssertionError("Unexpected distributable compatibility ranges");
   if(!loaderSpec.equals(clientProfile.get("loaderRange").getAsString())
     || !forgeSpec.equals(clientProfile.get("forgeRange").getAsString())
     || !minecraftSpec.equals(clientProfile.get("minecraft").getAsString()))
    throw new AssertionError("The TOML and runtime profile advertise different ranges");
   VersionRange loader = VersionRange.createFromVersionSpec(loaderSpec);
   VersionRange forge = VersionRange.createFromVersionSpec(forgeSpec);
   VersionRange minecraft = VersionRange.createFromVersionSpec(minecraftSpec);
   for(String[] pair : MATRIX) {
    accept(minecraft, pair[0], "Minecraft");
    accept(forge, pair[1], "Forge");
    accept(loader, pair[1].substring(0, pair[1].indexOf('.')), "FML loader");
    if(!RuntimeProfile.supports(pair[0], pair[1]))
     throw new AssertionError("Advertised official pair has no runtime route");
   }
   for(String patch : List.of("47.3.22", "47.4.6", "47.4.16", "47.4.20", "47.99.1", "66.99.1"))
    accept(forge, patch, "Forge patch");
   for(String excluded : List.of("47.3.21", "46.99.1", "67", "67.0.0", "68.0.0"))
    reject(forge, excluded, "Forge bound");
   for(String excluded : List.of("46", "67", "67.0.0"))
    reject(loader, excluded, "FML loader bound");
   for(String excluded : List.of("1.20", "26.4", "26.4.1", "27.1"))
    reject(minecraft, excluded, "Minecraft bound");
   if(RuntimeProfile.supports("1.20.5", "50.2.14")
     || RuntimeProfile.supports("1.21.2", "53.1.16"))
    throw new AssertionError("Broad metadata range bypassed the runtime version-pair guard");
  }
 }

 private static void accept(VersionRange range, String version, String kind) {
  if(!range.containsVersion(new DefaultArtifactVersion(version)))
   throw new AssertionError(kind + " version rejected: " + version);
 }
 private static void reject(VersionRange range, String version, String kind) {
  if(range.containsVersion(new DefaultArtifactVersion(version)))
   throw new AssertionError(kind + " version accepted: " + version);
 }
 private static void requireLegacyAdvertisedBuilds(VersionRange range, JsonObject profile) {
  if(profile.getAsJsonArray("forgeBuilds").size() == 0)
   throw new AssertionError("Negative fixture has no advertised builds");
  for(var build : profile.getAsJsonArray("forgeBuilds")) {
   String value = build.getAsString();
   if(!range.containsVersion(new DefaultArtifactVersion(value)))throw new RejectedBuild(value);
  }
 }
 private static VersionRange dependencyRange(UnmodifiableConfig toml, String id) throws Exception {
  return VersionRange.createFromVersionSpec(dependencySpecification(toml, id));
 }
 private static String dependencySpecification(UnmodifiableConfig toml, String id) {
  UnmodifiableConfig dependency = onlyEntry(toml, List.of("dependencies", MOD_ID), id);
  if(!Boolean.TRUE.equals(dependency.get("mandatory")))
   throw new AssertionError(id + " dependency must remain mandatory");
  if(!"CLIENT".equals(dependency.get("side")))
   throw new AssertionError(id + " dependency must remain client-side");
  String specification = dependency.get("versionRange");
  if(specification == null || specification.isBlank())throw new AssertionError("Missing " + id + " range");
  return specification;
 }
 private static UnmodifiableConfig onlyEntry(UnmodifiableConfig toml, List<String> path, String id) {
  Object raw = toml.get(path);
  if(!(raw instanceof List<?> entries))throw new AssertionError("Missing TOML array: " + path);
  UnmodifiableConfig found = null;
  for(Object entry : entries) {
   if(!(entry instanceof UnmodifiableConfig config))throw new AssertionError("Unexpected TOML array entry");
   if(id.equals(config.get("modId"))) {
    if(found != null)throw new AssertionError("Duplicate metadata for " + id);
    found = config;
   }
  }
  if(found == null)throw new AssertionError("Missing metadata for " + id);
  return found;
 }
 private static JsonObject readProfile(ZipFile jar, String path) throws Exception {
  return JsonParser.parseString(readEntry(jar, path)).getAsJsonObject();
 }
 private static UnmodifiableConfig readToml(ZipFile jar) throws Exception {
  return new TomlParser().parse(new StringReader(readEntry(jar, "META-INF/mods.toml")));
 }
 private static String readEntry(ZipFile jar, String path) throws Exception {
  var entry = jar.getEntry(path);
  if(entry == null)throw new AssertionError("Missing JAR entry " + path + " in " + jar.getName());
  try(var input = jar.getInputStream(entry)) {
   return new String(input.readAllBytes(), StandardCharsets.UTF_8);
  }
 }
 private static final class RejectedBuild extends AssertionError {
  final String build;
  RejectedBuild(String build) {
   super("mods.toml rejects a build advertised by runtime.json: " + build);
   this.build = build;
  }
 }
}
