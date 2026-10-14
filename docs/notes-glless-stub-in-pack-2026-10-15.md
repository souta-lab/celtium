# The 268-mod pack leg is not GL-less: `core.LwjglxDisplayMixin` is not applied (measured 2026-10-15)

## What was measured

Leg `gv-e2e-268-step4` (E2E 1.93a, 268 JARs, AMD RX 6400, celtium `51f4bde06332be1a…`, fixture `082f28d859b3ef3e…`,
`-Dceltium.vulkan.glless=true`) versus the 7-mod cohort leg `gv-cohort-main-a` (celtium `644fdbb9293aa8ec…`, same
flags). Both render and both present a world frame; the difference is what the window is.

| observation | cohort | 268-mod pack |
|---|---|---|
| `CELTIUM_GLLESS stub GL provider installed` | **present** | **absent** |
| `CELTIUM_GLLESS window hinted GLFW_NO_API` | **present** | **absent** |
| CleanMix line for `core.LwjglxDisplayMixin` | `Mixing … into org.lwjgl.opengl.Display` + `APPLY` | **no line at all** |
| distinct celeritas mixins applied (CleanMix audit) | **65** | **64** (65 minus the `core.ExplicitGcMixin` exclusion PR #87 makes when LoliASM is present) |
| plugin log | `Found 66 mixin classes` | `Found 66 mixin classes` |
| the class is in the built jar | yes | **yes** (same entry count in both jars) |
| window creation | GLFW_NO_API, no context | `[LWJGLXX] Attempting to create OpenGL 4.6 context` → `Obtained OpenGL 4.6 window handle` |
| `logs/celtium-capabilities.json` `openGl` | — | `renderer="AMD Radeon RX 6400 (radeonsi, navi24, ACO, DRM 3.64, …)"`, `version="4.6 (Compatibility Profile) Mesa 26.2.4-1"`, `contextSource="current-gl-context"` |
| fixture's own probe | `celtium$glLessInstalled()` true | `pictureGlLessProbe_world = "probe: the stub class is present but reports no installation"`, `pictureScreenshotUsable_world=true` |

So in the pack run **a real GL 4.6 context is created and the stub is never installed**: the mixin is registered (the
plugin found all 66 classes) but Mixin never applies it in that configuration. The world frame is still presented
from Vulkan — the renderer drew it natively — but the run is a **hybrid**, not the GL-less path.

## Why this matters

- Completion condition **(b)** ("no GL context is created and a normal frame needs no GL draw/readback/bridge/swap")
  is **not demonstrated for the pack leg** by these runs. It is demonstrated for the 7-mod cohort, where the stub is
  installed and the window is `GLFW_NO_API`.
- It also explains the oddities recorded in rounds 721-723 for the pack leg: a *usable* GL screenshot, and a picture
  identity that says `presented-dump` while the probe says `gl-screenshot`.
- The pack's picture evidence stays valid as **rendering** evidence (a Vulkan-presented 1280x720 frame, 13564
  colours, `validationMessageCount=0`); what it does not show is that the pack runs without a GL context.

## Not yet explained, and the experiment that would explain it

Why Mixin skips that one mixin in the pack is **not** established. The candidates are a coremod interaction or
ordering effect (40 coremods are present in E2E; LoliASM is the one PR #87 already had to work around), or the target
class being owned by another transformer first. The discriminating run is cheap: the **7-mod cohort plus LoliASM
alone**, one leg, reading the same four lines (`stub GL provider installed`, `window hinted GLFW_NO_API`, CleanMix's
`LwjglxDisplayMixin` line, `Found N mixin classes`). If the stub line disappears there, LoliASM is the cause and the
next step is the pack minus it; if it does not, the interaction is with another coremod and the same bisect continues.

## Fix direction (not implemented, deliberately)

The GL-less path currently depends on **one** mixin surviving every coremod in a pack. A stub installed from a
mixin-independent hook (the mod's own construction/loading path, which runs before the display is created) would
make `GL.createCapabilities()` resolve against the stub regardless of that mixin, while the `GLFW_NO_API` hint needs
its own robust path — a stub installed without the hint still leaves a GL context, which is exactly the hybrid state
measured here. Neither change is made yet: the finding is recorded first, and the experiment above decides which of
the two is the one to fix.
