# What the other Vulkan-on-1.12.2 projects do (read 2026-10-15)

Two projects occupy the same space as Celtium, and both are worth knowing when this project explains
what it is trying to be - and why the GL-less work is the hard part.

**Vulkanizator** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/vulkanizator), 1.12.2) is explicit
about how far it goes:

> "**Is Minecraft fully rendered by Vulkan? Not yet.** When takeover succeeds, these stages are native Vulkan:
> Vulkan instance, physical device and logical device, GPU command buffers and synchronization, Win32 Vulkan
> surface and child-window swapchain, persistent staging buffers and upload images, final image copy/blit and
> `vkQueuePresentKHR`, optional experimental terrain vertex pipeline. **The main scene is still produced by
> LWJGL 2/OpenGL before being transferred to Vulkan.** Block textures/lightmap, entities, particles, weather
> and GUI therefore remain compatibility-rendered."

Its activation log says the same thing in one line: *"Vulkan presenter is waiting for a visible Minecraft
client area; **temporary OpenGL bootstrap remains active**."*

**VulkanMod** ([Modrinth](https://modrinth.com/project/JYQhtZtO), modern Minecraft, the architectural reference
this project already cites) warns from the other side: *"mods that make direct calls to OpenGL might crash the
game."*

Three consequences for this project's own documentation and for how its results are read:

1. **Celtium's target is strictly harder than the peer project's**, and that is a deliberate choice rather than
   an accident: it aims at a window with no GL context at all (`GLFW_NO_API`), where Vulkanizator keeps a GL
   context and produces the scene in OpenGL. Every mod that touches GL therefore lands on Celtium's stub, and
   the stub's surface has to cover what those mods call - which is exactly the class of failure this project
   spent rounds 728-777 on.
2. **"The mods call GL" is not a Celtium-specific defect.** VulkanMod states the incompatibility outright for
   a renderer that replaces OpenGL on a modern version with far fewer legacy mods in play. A pack leg that
   fails because a mod calls GL is evidence about the mod, not about the renderer being wrong.
3. **A hybrid fallback is a legitimate option** and the peer project's own scope statement is the precedent.
   Celtium's plan keeps the bridge as an opt-out (`-Dceltium.vulkan.glless=false`) rather than a supported
   mode, and that is worth stating plainly wherever the GL-less results are summarised: the hybrid is the
   easier target, and this project has chosen the harder one on purpose.
