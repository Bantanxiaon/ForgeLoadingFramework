import shanghan.early.RuntimeProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/** Checks packaged distribution identity independently from adapter selection. */
public final class RuntimeProfileSmoke {
 public static void main(String[] args) throws Exception {
  if(args.length != 1)throw new AssertionError("Expected the test fixture directory");
  Path.of(args[0]); // No fixture files are required.
  for(String build : List.of("47.3.22", "47.3.33", "47.4.6", "47.4.16", "47.4.20", "47.99.1"))
   if(!RuntimeProfile.supports("1.20.1", build))
    throw new AssertionError("Compatible patch build blocked: " + build);
  if(!RuntimeProfile.supports("26.3", "66.0.12"))
   throw new AssertionError("The current SDL family was not selected");
  for(String[] pair : new String[][] {
    {"1.20.4", "47.4.16"}, {"1.20.1", "48.1.0"}, {"26.3", "67.0.0"},
    {"26.4", "66.0.12"}, {"26.4", "67.0.0"}, {"1.20.5", "50.2.14"},
    {"1.21.2", "53.1.16"}
  })if(RuntimeProfile.supports(pair[0], pair[1]))
   throw new AssertionError("Invalid Minecraft/Forge pair accepted: " + String.join("/", pair));

  var source = RuntimeProfile.class.getProtectionDomain().getCodeSource();
  if(source == null)throw new AssertionError("Bootstrap has no code source");
  Path bootstrap = Path.of(source.getLocation().toURI());
  if(!Files.isRegularFile(bootstrap))throw new AssertionError("Expected the real bootstrap JAR");
  JsonObject bootProfile;
  try(ZipFile jar = new ZipFile(bootstrap.toFile())) {
   bootProfile = profile(jar, "shanghan/early/runtime.json");
  }
  if(bootProfile.get("schemaVersion").getAsInt() != 2)
   throw new AssertionError("Expected the range-based runtime profile schema");
  if(!"[1.20.1,26.4)".equals(bootProfile.get("minecraft").getAsString())
    || !"[47.3.22,67)".equals(bootProfile.get("forgeRange").getAsString())
    || !"[47,67)".equals(bootProfile.get("loaderRange").getAsString()))
   throw new AssertionError("Unexpected packaged runtime ranges");
  Path client = bootstrap.getParent().resolve("ForgeLoadingFramework-" + RuntimeProfile.version() + ".jar");
  JsonObject clientProfile;
  try(ZipFile jar = new ZipFile(client.toFile())) {
   clientProfile = profile(jar, "shanghan/client/runtime.json");
  }
  if(!bootProfile.equals(clientProfile))
   throw new AssertionError("The real client and bootstrap profiles differ");
  requireAccepted(clientProfile);
  JsonObject wrongVersion = clientProfile.deepCopy();
  wrongVersion.addProperty("version", "0.3.3");
  requireRejected(wrongVersion, "mixed runtime version");
  JsonObject wrongSchema = clientProfile.deepCopy();
  wrongSchema.addProperty("schemaVersion", 1);
  requireRejected(wrongSchema, "different schema");
  JsonObject wrongRange = clientProfile.deepCopy();
  wrongRange.addProperty("forgeRange", "[47,48)");
  requireRejected(wrongRange, "same version but different compatibility range");
  JsonObject missingField = clientProfile.deepCopy();
  missingField.remove("loaderRange");
  requireRejected(missingField, "incomplete client profile");
  JsonObject extraField = clientProfile.deepCopy();
  extraField.addProperty("unexpectedDistributionSetting", true);
  requireRejected(extraField, "same version but different distribution settings");
  try {
   RuntimeProfile.requireClient(null);
   throw new AssertionError("Missing client profile accepted");
  } catch(IllegalStateException expected) {}
  System.out.println("RuntimeProfileSmoke PASS patch-family routing and invalid pair rejection; real schema2 "
    + "bootstrap/client profiles agree; complete identity comparison rejects changed or missing fields.");
 }

 private static JsonObject profile(ZipFile jar, String path) throws Exception {
  var entry = jar.getEntry(path);
  if(entry == null)throw new AssertionError("Missing packaged profile: " + path);
  try(var stream = jar.getInputStream(entry)) {
   return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
  }
 }
 private static void requireAccepted(JsonObject profile) throws Exception {
  try(var stream = new ByteArrayInputStream(profile.toString().getBytes(StandardCharsets.UTF_8))) {
   RuntimeProfile.requireClient(stream);
  }
 }
 private static void requireRejected(JsonObject profile, String reason) throws Exception {
  try {
   requireAccepted(profile);
   throw new AssertionError("Accepted " + reason);
  } catch(IllegalStateException expected) {}
 }
}
