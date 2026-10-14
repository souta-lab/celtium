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

## The peers' scope, in their own words (read 2026-10-15)

**Vulkanizator Alpha 22** ([CurseForge file page](https://www.curseforge.com/minecraft/mc-mods/vulkanizator/files/8494271)) lists
what its "safe default renderer path" actually does, and the list is the clearest statement of the hybrid design
available anywhere:

> 1. Minecraft's remaining compatibility framebuffer is captured through the asynchronous **OpenGL PBO ring**.
> 2. The image is copied into a Vulkan upload image.
> 3. Opaque, cutout and translucent chunk VBOs are mirrored into persistent world-space Vulkan buffers and rendered
>    into the offscreen Vulkan colour/depth target.
> 4. Vanilla ParticleManager layers and the targeted-block outline are rendered through the native Vulkan
>    dynamic-scene pipeline.
> 5. The completed image is transferred to the overlay swapchain using `vkCmdBlitImage`.
> 6. The final frame is displayed by `vkQueuePresentKHR`.
>
> Sky, clouds, weather, entities, hand rendering and GUI remain compatibility-produced and are preserved in the
> captured base image.

It also states that its crash-prone fullscreen graphics composite "is not created by default" and needs an explicit
`-Dvulcanizator.experimental.graphicsComposite=true` - a reminder that shipping the safe path by default and gating
the ambitious one is a legitimate shape for a renderer like this, and one this project could copy for its own
GL-less mode.

**VulkanMod-Next** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/vulkanmod-next), 1.12.2) draws terrain
in Vulkan and "**shares one depth buffer** so everything lines up" with the game's remaining GL rendering, and its
compatibility table lists:

> | Celeritas, Actinium | 🟡 | Sodium ports - a second terrain renderer. **This one steps aside.** |

So the peer that replaces the terrain renderer **defers to Celeritas** rather than competing with it. That is the
slot this project occupies, and it is worth stating in the project's own compatibility documentation: on a pack with
Celtium installed, a mod like VulkanMod-Next is expected to stand aside, and the reverse should be true too.

## The primary source for the Vulkan switch, and the architecture it lands on (read 2026-10-15)

The earlier note cites a news article; the announcement itself is
[Minecraft.net, 2026-02-18](https://www.minecraft.net/en-us/article/another-step-towards-vibrant-visuals-for-java-edition):

> "Following this work, we're now preparing to make a change to the underlying technology we use to render the game, by
> switching from OpenGL to Vulkan. ... you'll start to see Vulkan rolling out side-by-side with OpenGL in snapshots while
> we work to minimize any issues arising from this implementation. Once this happens, players will be able to switch
> between OpenGL rendering and Vulkan rendering ... while we iron out issues with stability and any bugs. ... Once we're
> happy with the performance and stability of Vulkan across devices we will remove the OpenGL implementation."

That is the same shape this project works in on 1.12.2 - a Vulkan renderer beside an OpenGL one, with the old path
kept until the new one is proven - and it is worth citing the announcement rather than a report of it.

**What the modern renderer's architecture looks like**, per [minecraftdocs.dev's Blaze3D pages](https://minecraftdocs.dev/systems/rendering/blaze3d),
is also worth having in view for this project's own "common contracts" work: the game talks to `GpuDevice`,
`CommandEncoder` and `RenderPass`, and a backend under `com/mojang/renderpearl/backend/opengl` or `.../vulkan` turns
that into real calls. Blend mode, depth test, cull and polygon mode are fields of a `RenderPipeline` declared once and
applied by the backend; `RenderSystem` contains no GL call at all; OpenGL is imported by exactly fifteen files, all but
one inside the GL backend. Presentation is a four-step protocol - `GpuSurface.configure`, `acquireNextTexture`,
`blitFromTexture`, `present` - rather than a swap, and pacing is a two-deep submit fence rather than the present.

The parallel with this project's own state is direct: the goal asks for a common allocator, buffer, descriptor,
pipeline and resource registry with one contract for draw state and resource lifetime, which is the same separation
between what the game declares and what a backend does with it. Where this project differs is that the boundary has to
exist **inside** a 1.12.2 client whose mods call GL directly, so the "backend" here also has to answer calls the game
never routed through any abstraction.

## What upstream's own Vulkan backend gets wrong, and what that says about this project's failure modes

Two reports on Mojang's tracker are worth having beside this project's own notes, because they are the same failure
modes arriving in a renderer that has far more resources behind it.

**[MC-311954](https://mojira.dev/MC-311954) - OpenGL cannot be used under XWayland.** The game cannot retrieve the
OpenGL functions and the backend fails to load:

```
com.mojang.renderpearl.api.device.BackendCreationException: OpenGL is not supported: Could not retrieve OpenGL functions
    at com.mojang.renderpearl.backend.opengl.GlBackend.loadLibrary(GlBackend.java:36)
```

That is the failure this project's GL-less work exists to remove the *need* for: a client whose GL function table
cannot be filled. Upstream's answer is to fall back to the other backend; Celtium's answer is to keep the window and
answer the calls anyway, which is a strictly harder promise - and the reason the stub's coverage of what mods call
matters more here than it would in a renderer that can hand the frame back to OpenGL.

**[MC-307503](https://mojira.dev/MC-307503) - the game can crash on a Vulkan synchronisation timeout.**

```
java.lang.IllegalStateException: 5s timeout reached when waiting for VK semaphore
    at com.mojang.blaze3d.vulkan.VulkanCommandEncoder.submit(VulkanCommandEncoder.java:258)
    at net.minecraft.client.Minecraft.renderFrame(Minecraft.java:1361)
```

A renderer that replaces the pipeline inherits the job of pacing it: the goal's "CPU/GPU synchronisation and idle"
item and its fence-and-retirement work are the same problem, and upstream has not solved it either. A stall of this
kind is a real risk for Celtium's own submit path, and worth a leg of its own once the GL-less surface is stable.

## The atlas limit, and a peer mod that does exactly what this project's fix does (read 2026-10-15)

The StitcherException this project hit has three independent confirmations that the atlas size **is** the maximum
texture size, and that forcing it is an established answer:

- **OptiFine issue #2228** explains a crash with the same message shape - `atlas: 4096x4096, atlasMax: 4096x4096` - as
  "the GPU maximum texture size is 4096x4096 which is pretty small, most current GPUs support 16384x16384", and
  suggests disabling connected textures or removing texture-heavy mods.
- **Galacticraft issue #2972** has the same message and the maintainer's explanation that every mod's textures go into
  one atlas, that the limit is the hardware's, and that "the normal is 16384x16384".
- **Texture Atlas Size Fix** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/texture-atlas-size-fix)) is a mod
  whose entire purpose is the fix this project just made: "This mod simply forces the texture atlas size to be
  16k x 16k, allowing lower-end devices to run more mods without the worry of the texture atlas crash."

So the fix is not a workaround invented here: the message's `atlas:` field is the value under test, 16384 is the
conventional ceiling, and forcing it is what other projects ship. The corollary matters for the packs this project
accepts against: a 268-mod pack or RLCraft may genuinely need more than 16384, and the mitigation then is the one
**Texture Scaler** uses - downscale the sprites that would not fit - not a larger claim, because claiming more than
16384 is a lie about the hardware that the next `glTexImage2D` would expose.
