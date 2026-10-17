# Modern Forge adapter

This directory contains the SDL window adapter for the Minecraft 26.3 API family.
It is isolated from the legacy GLFW implementation and contains no Minecraft
class references. All released adapter classes are compiled with Java 17
(class-file version 61); Minecraft 26.3 itself requires Java 25.

## Verified official interfaces

Research baseline: Minecraft 26.3 and Forge 66.0.12.

- Provider interface: net.minecraftforge.fml.loading.ImmediateWindowProvider.
- New handoff: long setupMinecraftWindow(int, int, String, Supplier<Object>).
- Backend hook: ImmediateWindowProvider selectBackend(String).
- The other provider methods retain the common Java-only signatures.
- Window implementation: LWJGL 3.4.3 SDL, not GLFW.
- Loader: net.minecraftforge:modlauncher:10.2.6.
- Launch target: forge_client; the old --fml.mcVersion and
  --fml.forgeVersion command-line options are absent.
- The loader exposes FMLLoader.versionInfo().mcVersion()/forgeVersion().
- Modern LoadingModList.getMods() is static; the older
  FMLLoader.getLoadingModList() method no longer exists.
- FMLConfig and FMLPaths retain the configuration and game-directory APIs.
- The game overlay now uses extractRenderState(GuiGraphicsExtractor,int,int,float)
  and tick(). It no longer exposes the old immediate render(GuiGraphics,...).
  Game-side rendering must remain isolated behind the common client overlay router.

## Implementation

SdlWindowProvider owns a video SDL window and a dedicated OpenGL context.
Minecraft's own selected backend creates its separate SDL game window.
NativeFadeSurface attaches the video HWND as a child of that game HWND and
fades only the child's native alpha into the live game menu. The main game
window never becomes layered or transparent. Video rendering stays opaque in
its own context and continues while Minecraft constructs and reloads resources.

The provider uses ClientOverlayRouter.create with the five-argument media and
renderer contract and supplies the existing media fit/show/close/abort hooks.
It preserves the renderer-release barrier before destroying the video context.

The native surface is public and accepts HWND values, so the GLFW adapter can
share it without depending on SDL. Its Calls interface permits deterministic
lifecycle checks without native windows.

Windows embedding is implemented. Other operating systems use the stock screen.
The user's selected GPU backend is passed through unchanged, including Vulkan:
the separate video context still needs OpenGL. Vulkan game-backend coexistence
has not received a complete Minecraft launch test and is not represented as
verified end-to-end support.

## Official sources

- https://files.minecraftforge.net/net/minecraftforge/forge/
- https://maven.minecraftforge.net/net/minecraftforge/forge/26.3-66.0.12/forge-26.3-66.0.12-installer.jar
- https://github.com/MinecraftForge/MinecraftForge/tree/26.3
- https://github.com/MinecraftForge/MinecraftForge/blob/26.3/fmlloader/src/main/java/net/minecraftforge/fml/loading/ImmediateWindowProvider.java
- https://github.com/MinecraftForge/MinecraftForge/blob/26.3/patches/minecraft/net/minecraft/client/gui/screens/LoadingOverlay.java.patch
- https://piston-meta.mojang.com/mc/game/version_manifest_v2.json

Build inputs are recorded in build-sdk.json; run source/prepare_sdk.py from the repository root to fetch and verify them.
