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

## The direction is now upstream's too (read 2026-10-15)

Mojang has announced that Java Edition is moving to Vulkan - snapshots alongside OpenGL from summer 2026, a
toggle for players, and the OpenGL implementation removed once performance and stability are satisfactory
([Neowin, 2026-02-18](https://www.neowin.net/news/minecraft-java-edition-is-upgrading-to-vulkan-but-its-not-great-news-for-mods/)).
The part that matters here is what Mojang says to mod authors:

> "Switching from OpenGL to Vulkan will have an impact on the mods that currently use OpenGL for rendering, and we
> anticipate that updating from OpenGL to Vulkan will take modders more effort than the updates you undertake for
> each of our releases. To start with, **we recommend our modding community look at moving away from OpenGL
> usage**."

Three things follow for how this project's work should be read:

1. **The GL-less target is the direction the platform itself is taking**, not an exotic choice. What Celtium does
   on 1.12.2 - a window with no GL context and a stub that answers GL calls - is the same shape as what Java
   Edition will require of mods on modern versions.
2. **"Mods call GL" is acknowledged upstream as the migration's main cost.** This project has spent many rounds
   on exactly that class of failure (rounds 728-777: a mixin that lost a class-load race, a capabilities table
   with a zero ARB address, a native replacement that threw on wide argument types). None of it is a defect the
   platform disagrees with; it is the same problem arriving earlier here.
3. **The hybrid is what the peers ship** - Vulkanizator's Alpha 22 keeps the compatibility framebuffer, the sky,
   clouds, weather, entities, hand rendering and GUI produced by OpenGL and mirrors only terrain, particles and
   the block outline natively - so this project's GL-less results should be presented with that comparison in
   view rather than as the only way such a renderer can exist.

## The 3x3 readiness rule, stated by Sodium itself (read 2026-10-15)

The nether leg's failure has a mechanism in Sodium's own words, from
[PR #3713 "Add opt-in rendering for loaded chunk edges"](https://github.com/CaffeineMC/sodium/pull/3713):

> "Sodium currently merges readiness across the **3x3 chunk neighborhood**. **If any neighboring chunk is absent,
> the merged status becomes empty, which can keep an otherwise complete center chunk out of the renderer.**"

A dimension change is exactly the case where neighbours are missing, so a Celeritas-family renderer can draw
nothing at all for a while - and the vanilla behaviour it is compared against hides the same gap with the
24-block rule Sodium's issue 2232 describes. The same PR names the fix direction and its cost: an opt-in mode
that lets a centre chunk render provisionally when its own block and light data are complete, records which
neighbours were missing, and **rebuilds the mesh when they arrive** ("a mesh built while a neighbor was missing
is not treated as final").

Two consequences for this project:

- **A scene captured immediately after a dimension change is not evidence about the renderer** unless the leg
  says how long it waited. The RLCraft nether leg captured at `dimensionAtTick=80` with
  `firstPresentedWorldFrame=2`, which is inside that window.
- **Relaxing the 3x3 readiness rule is a real implementation item** (Sodium made it opt-in rather than default
  because a provisional mesh has to be rebuilt when the neighbours arrive). It belongs on the list of
  Celeritas-family behaviours to match - before the shadow and shaderpack work, since both depend on terrain
  being present in the frame.

## How other automation waits for a scene, and what this fixture should do (read 2026-10-15)

The nether leg captured at `dimensionAtTick=80` with `firstPresentedWorldFrame=2` - inside the window the 3x3
readiness rule leaves empty - and a screenshot tool in the same ecosystem already solves this properly:

**[Jeroen-45/screenshot-bot](https://github.com/Jeroen-45/screenshot-bot/)** documents its `screenshot` command as:
*"Wait until no chunk loading activity is detected anymore, then take a screenshot and save it"* - a gate on
loading activity, not a fixed delay.

That is the shape this fixture's dimension scenes need. The properties it has today (`dimensionAtTick`,
`dimensionWaitUntilTick`, `minDimensionFrames`) are all **time**-based, and time is the wrong unit when the
thing being waited for is "the renderer has meshed what it is going to mesh": a faster machine and a slower one
need different numbers, and a leg that guesses low produces a frame that says nothing about the renderer.

The concrete item, for whoever owns the fixture next: a **settle gate** for dimension changes - capture only
after the presented frame has stopped changing (for example, the presented dump's colour count and
non-background fraction are unchanged across N consecutive frames, both of which the fixture already measures),
with the time-based properties kept as a bounded fallback and recorded in the leg. Until that exists, every
dimension-change leg must state its wait and be read with that in mind - the nether FAILED is a capture taken
inside the empty window, not yet a statement about the renderer.
