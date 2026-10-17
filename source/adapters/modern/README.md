# Modern Windows window adapter

Minecraft 26.3 / Forge 66 uses SDL. The isolated SDL provider keeps the video OpenGL drawable inside the game's own Windows window while preserving Minecraft's selected graphics backend.

## Borderless embedding

Before native attachment, SdlEmbeddedWindow restores the early maximized window and sets SDL_WINDOW_BORDERLESS through SDL's public API. SDL's internal resizable flag stays enabled so WM_NCCALCSIZE follows the live size; NativeFadeSurface removes the actual child caption, resize controls and extended frame edges.

These changes run on the window-creation thread under the render lock. The parent game window keeps its normal decorations and opaque framebuffer. Video opacity changes apply to the child drawable.

GLFW routes use WindowSurface.prepareChild to keep GLFW decoration flags consistent with their native child styles.

## Development

Use the root build and test scripts. Shared sources compile for Java 17 against the older API; the SDL provider and helper compile separately with official LWJGL 3.4.3 SDK inputs. Immutable compile URLs and hashes are recorded in build-sdk.json; source/prepare_sdk.py fetches and verifies those inputs.

ModernNativeFadeSmoke checks native-call ownership of styles, opacity, resize and rollback. ModernSdlNativeSmoke exercises owned SDL/OpenGL windows, expected pixel readback, cached borderless flags, actual client bounds, maximize/restore/hide/show, H.264 presentation and process/context/window cleanup.

## Official references

- https://wiki.libsdl.org/SDL3/SDL_SetWindowBordered
- https://github.com/libsdl-org/SDL/blob/main/src/video/windows/SDL_windowsevents.c
- https://github.com/MinecraftForge/MinecraftForge
- https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
