# Translucent wedges (measurement) and the depth-format contract — 2026-10-11

Measurement only: no renderer file was edited. Everything below is reproducible from the commands and the
per-run logs quoted here. Runs used the mission command (`--headless --force-x11`, RX 6400,
`-Dceltium.vulkan.glless=true`, `-Dceltium.validation.allBlocks=true`, `minWorldFrames=5`,
`--duration 110`) on the jar built at 01:16 and the validation client built at 01:24.

## 1. Root cause of the translucent wedges

### 1.0 Status after `454fb3600` (read this first)

A separate audit found a real and larger defect and fixed it: `celtium$buildModelMeshes` started one
vertex per quad instead of one per corner, so a mesh held Q vertices while its indices addressed
`base..base+3`. That is genuinely the shape of the wedges and the shards - the rasteriser joining
vertices that were never written - and in the verifier's pinned-camera scene (allBlocks, camera pinned at
`x=0.5 y=12 z=0.5 yaw=180 pitch=20`) it removed the wedges and the black sky triangle, with 36.9% of
pixels changing.

**Re-measured here on the fixed jar (09:11), with the mission's own command and the settled camera, the
wedges are still visible.** `w3-base` vs `w2-base`, both `frameDumpEvery=1`, same scene and flags:

| frame | `w2-base` (pre-fix) | `w3-base` (post-fix) |
|---|---|---|
| 14-22 | clear 0.340, distinct ~22350 | clear 0.423, distinct ~20580 |

and the two late frames are the same picture in character: the lavender quad over the left half of the
sky, the pale yellow-green band at the wall, the black triangle at the top, the black shard over the
floor. So the model fix changed 42% of the sky band's clear fraction here without removing the wedges,
and **mission A is not closed for this scene**. The difference from the verifier's result is most likely
the camera: pitch +20 looks down, so the sky band - where every wedge but the floor shard lives - is
mostly out of frame there, while the mission command settles at pitch -14.

### 1.1 What is established

The wedges are native-path only (the GL bridge reference of the same scene has white cloud puffs and no
wedges), they are camera-relative, and they are drawn by geometry the GL-less path takes and the bridge
refuses. Which batch, and why the picture is run-dependent, is *not* established at the level the model
defect was; what follows is the evidence I have, with its limits stated.

The chain below is source-verified and independent of any wedges:

* `GlStateManager.matrixMode` and `loadIdentity` are hooked, so the mirror's projection is set to the
  identity by `EntityRenderer.java:1346` / `1502`.
* The call that fills it back in is `Project.gluPerspective` (line 1347 / 1503), whose class is outside
  the transforming loader. `EntityRendererMixin`'s own comment records the measurement: "its counter has
  stayed at zero".
* `TerrainGpuBackend.perspective(float, float, float, float)` exists for exactly this, is implemented in
  full at `VulkanTerrainGpuBackend:2055` (with `frustum` at 2074 and their counters), and **has no caller
  anywhere in the tree.**

So there is a window, from `matrixMode(GL_PROJECTION); loadIdentity()` to the next mirrored projection
call, in which the native path's projection is the identity whereas GL's is a perspective. The renderer
prints the window: `CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5` - `0.0,0.0,0.5,0.5` being the identity's
depth row `(0,0,1,0)` after `remapClipDepth` (`z' = 0.5z + 0.5`) - for the format 1 (sunrise fan), 6 (sun
and moon) and 8 (`renderCloudsFancy`, `RenderGlobal.java:1676`) world-pass groups, while the format-5
groups in the same frames carry the real projection `(0.0,0.0,-1.001,-0.1)`:

```
w3-base: CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5 ... fmt=1 texture=113 batches=1 worldPass=true
w3-base: CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5 ... fmt=6 texture=114 batches=1 worldPass=true
w3-base: CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5 ... fmt=6 texture=115 batches=1 worldPass=true
w2-mtx:  CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5 ... fmt=8 texture=44  batches=4 worldPass=true
w2-mtx:  CELTIUM_PROJ_PROBE fmt=5 rawDepth=(0.0,0.0,-1.001,-0.1) storedDepth=(0.0,0.0,-1.001,-0.1)
```

**The limit of this evidence:** `CELTIUM_IMMEDIATE_ENQ` prints only the first eight groups of a run, so
those lines characterise the first world frame, not the frames the wedge metric was taken from. The
identity window is real and unfixed; that it is what covers *those* frames is a hypothesis, not a
measurement. The measurement that would settle it is the one-method mixin in §1.3 - if the wedges go
when `perspective(...)` is finally fed, this is the cause; if they stay, the cause is elsewhere and the
window is only a latent defect.

### 1.2 What the format-8 and format-6 refusals are worth now

Measured pre-fix, refusing format 8 or format 6 raised the sky band's clear fraction (0.340 -> 0.784 and
0.731) while refusing format 7 did not (0.393). Those are *refusal* experiments: a refusal calls
`flushPendingBeforeGlDraw()` and moves the frame's window boundary, so they cannot separate "this layout
draws the wedge" from "refusing this layout moved the boundary past the wedge's draw" - the confound I
flagged before running them, and which the parent's own control run (`wedge-ctl9`) was designed to test.

On the fixed build the direct test is available and negative for format 8: with **only** layout 8 drawn
natively (`w3-only8`, everything else to the stubbed GL) the frames are clean - terrain, flat sky, and no
cloud geometry visible at all (`frame-23`: 18.2% of the sky band non-clear, and that 18.2% is the wall,
not sky). So format 8 does not by itself produce the wedge, and the earlier attribution to the fancy cloud
batch should not be carried forward.

What survives as a real, separate contract gap: the cloud draw is the one layout the immediate path takes
in GL-less mode that the bridge refuses, through a shutter (`boundProgram != 0`) that a stub GL context
cannot answer. Whether it matters visually is now open - the only-8 frame says it draws nothing
objectionable on its own.

Inside the world pass the immediate path takes its matrices from the *mirror*, never from OpenGL
(`VulkanTerrainGpuBackend.captureGlMatrices`, the `worldPassActive && worldSnapshotValid` branch:
"No GL read on this path. The matrices are the mirror's ..."). The sky and cloud passes install their
projection like this (decompiled 1.12.2, `EntityRenderer.java`):

```java
GlStateManager.matrixMode(5889);        // hooked -> mirror.matrixMode
GlStateManager.loadIdentity();          // hooked -> mirror projection := identity
Project.gluPerspective(...);            // LWJGL 2 GLU -> GL11.glMultMatrixf, NOT hooked
renderglobal.renderSky(partialTicks, pass);          // line 1349
```

— lines 1345-1349 for the sky and 1501-1507 for the clouds (`renderGlobalIn.renderClouds`). Between the
mirrored `loadIdentity()` and the next projection call the mirror therefore holds the **identity** while
GL holds a perspective. Every immediate batch offered in that window is captured with the identity and
drawn with it:

| format | batch | where |
|---|---|---|
| 1 `POSITION_COLOR` | the sunrise/sunset triangle fan | `RenderGlobal.renderSky` |
| 6 `POSITION_TEX` | the sun and the moon quads | `RenderGlobal.renderSky` |
| 8 `POSITION_TEX_COLOR_NORMAL` | the fancy cloud cells, 96x96 blocks, alpha 0.8 | `RenderGlobal.renderCloudsFancy`, `RenderGlobal.java:1676` |

The capture is visible in the renderer's own output. `0.0,0.0,0.5,0.5` is the identity's depth row
`(0,0,1,0)` after `remapClipDepth` (`z' = 0.5z + 0.5`):

```
w2-base: CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5 mvT=0.0,-1.62,0.05 fmt=1 texture=112 batches=1 worldPass=true
w2-base: CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5 ... fmt=6 texture=113 batches=1 worldPass=true
w2-base: CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5 ... fmt=6 texture=114 batches=1 worldPass=true
w2-mtx:  CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5 ... fmt=8 texture=44  batches=4 worldPass=true
w2-mtx:  CELTIUM_PROJ_PROBE fmt=5 rawDepth=(0.0,0.0,-1.001,-0.1) storedDepth=(0.0,0.0,-1.001,-0.1)
```

No other group family in any run carries the identity, and the format-5 groups in the same frames carry
the real world projection `(0.0,0.0,-1.001,-0.1)`. That is the whole defect: three layouts, all of them
sky, all of them captured inside the one window where the mirror is blind.

Why it is run-dependent, which is how it was reported: whether a batch falls inside that window depends
on where the immediate window opens and closes, and that is timing-dependent. Refusing a layout changes
the boundary, which is why refusing format 6 also clears the picture.

### 1.3 Pre-fix counters and the sky-band measurement (format-8 row is pre-fix and now superseded)

* `CELTIUM_IMMEDIATE_ENQ` `proj=` per group: `0.0,0.0,0.5,0.5` (identity, remapped) for the fmt 1/6/8
  world-pass groups against `0.0,0.0,-1.001,-0.1` for fmt 5 in the same frames. This is the counter that
  names the defect.
* `CELTIUM_ARRIVALS`/`CELTIUM_ACCEPTED byFormat` cannot separate them: the census fires once, at 500
  batches, so it lands inside the loading screen in one run and inside the first world frame in another
  (`8:128` when it lands in a world frame - the clouds are exactly 128 batches per frame, 8x8 cells x 2
  colour sub-passes - and `8:0` when it does not).
* `stubTransformCalls=... glMultMatrixf=700 ...` in the acceptance census: 700 `glMultMatrixf` arrivals,
  the channel the GLU projection uses and the mirror has no hook for.
* Sky-band clear fraction (fraction of rows 0-300 that is exactly the clear colour `0xB3CFFF`):

| run | extra property | clear | verdict |
|---|---|---|---|
| `w2-base` | none | 0.340 | wedges: lavender quad, pale yellow-green band, black top quad |
| `w2-no8` | `refuseImmediateFormat=8` | 0.784 | wedges gone |
| `w2-no6` | `refuseImmediateFormat=6` | 0.731 | wedges gone |
| `w2-no7` | `refuseImmediateFormat=7` (control) | 0.393 | wedge unchanged |
| `w2-only8` | `immediateFormats=8` (only layout 8 drawn), pre-fix | no wedge at all | repeated post-fix as `w3-only8`: still no wedge, and no cloud geometry at all |

`w2-only8` is the control that rules out the cloud geometry itself: when the clouds are the *only* layout
taken, nothing is drawn in the wrong place. The defect is the captured matrix, not the batch.

### 1.4 The GL comparison

The wedges are native-path only. In bridge mode the same scene with real GL shows the clouds as white
puffs and no wedges (`.celtium-work/tesr-evidence/reference-gl-bridge.png`), for two independent reasons:
GL's cloud shader draws the cells, and the batch is refused there anyway because a real context answers
the `boundProgram != 0` shutter. Under the GL-less stub that shutter cannot fire, so the batch is taken.
`tesr4` is not a clean comparison: its whole sky is the flat clear colour, because the vanilla sky plane,
void plane and stars are drawn with `skyVBO.drawArrays(7)`/`callList`, and `glDrawArrays` is a no-op in
`CeltiumGlStub`. `tesr5` and `tesr7` are byte-identical in rows 0-350 (0 differing pixels); they differ
only from row 364, which is the model geometry `modelDraw=false` removed from `tesr5`.

### 1.5 Patch text — root fix (one mixin method; the mirror target already exists)

The missing wire is narrow and the house has already built the receiving end. `TerrainGpuBackend`
declares, with a doc comment that names this exact problem:

```java
    /**
     * Mirrors {@code Project.gluPerspective(fov, aspect, near, far)}, which builds the world's projection and
     * installs it with {@code GL11.glFrustum} - it never passes through {@code GlStateManager}, so a Mixin on that
     * class alone cannot see it.
     */
    default void perspective(float fovDegrees, float aspect, float near, float far) {
    }
```

and `VulkanTerrainGpuBackend.perspective` (line 2055) / `frustum` (line 2074) implement it in full, with
the `perspectiveCalls`/`frustumCalls` counters and the note "glFrustum multiplies, so the mirrored
projection stack must multiply too". `EntityRendererMixin`'s class comment states the consequence and the
current workaround: "the mirrored projection becomes the identity, and the call that would fill it is
`Project.gluPerspective`, whose class sits outside the transforming loader - measured, not assumed: its
counter has stayed at zero." The workaround derives the projection from `func_78481_a` at the camera
boundary only, which is why the sky and cloud windows are uncovered: they replace the projection *inside*
the pass, three lines after the boundary, and only the `loadIdentity` of that pair is seen.
**`perspective(...)` has no caller anywhere in the tree** - that is the whole bug.

New mixin class, same package and idiom as the others:

```java
package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fills the one projection channel the mirror cannot reach. {@code GlStateManager.matrixMode} and
 * {@code loadIdentity} are hooked, so the mirrored projection becomes the identity; the call that fills it is
 * {@code Project.gluPerspective}, whose class sits outside the transforming loader and whose counter has stayed at
 * zero. That is harmless while the projection is only replaced at the camera boundary, which
 * {@code EntityRendererMixin} covers by derivation - but {@code renderWorldPass} and {@code renderClouds} replace it
 * again mid-pass:
 *
 * <pre>
 * GlStateManager.matrixMode(5889);   // mirrored: the mirror's projection becomes the identity
 * GlStateManager.loadIdentity();
 * Project.gluPerspective(...);       // not mirrored: nothing fills it back in
 * </pre>
 *
 * so every immediate batch offered in that window - the sunrise fan, the sun, the moon and the fancy cloud cells -
 * captures the identity and is drawn as huge translucent quads across the sky. Forwarding the four numbers to the
 * backend, which owns the {@code gluPerspective} arithmetic, covers the sky, the clouds, the third-person view and
 * the hand pass with one hook and gives the exact far plane instead of a derived one.
 */
@Mixin(org.lwjgl.util.glu.Project.class)
public class GluProjectionMixin {

    @Inject(method = "gluPerspective", at = @At("HEAD"), require = 0)
    private static void celtium$gluPerspective(float fovy, float aspect, float near, float far, CallbackInfo info) {
        GlStateManagerMixin.celtium$mirror(backend -> backend.perspective(fovy, aspect, near, far));
    }
}
```

`celtium$mirror` is `private static` in `GlStateManagerMixin` (line 409): widen it to package-private, or
move this one method into `GlStateManagerMixin` itself, which is the smaller change. Nothing else is
needed - `backend.perspective` already does the multiply on the projection stack, and the GL body must keep
running for the bridge configuration.

### 1.6 Patch text — defensive refusal, `VulkanTerrainGpuBackend.java` (file the mission names)

The root fix above removes the cause. This is the fail-closed guard that turns the same condition into a
named refusal instead of a wrong picture, and it is what makes the defect visible if the mirror is ever
incomplete again for another utility path. Helper, next to `celtium$formatAccepted` (line 1062):

```java
    /**
     * Whether a captured projection is the identity - the value the mirror holds between a mirrored
     * {@code loadIdentity()} and the next projection call it can see. A projection a world pass actually draws with
     * is never the identity: the camera transform installs a perspective, and the GUI's own ortho has a non-zero
     * depth row as well.
     */
    private static boolean celtium$projectionIsIdentity(float[] projection) {
        if (projection == null || projection.length < 16) {
            return false;
        }
        for (int i = 0; i < 16; i++) {
            float expected = (i % 5 == 0) ? 1.0f : 0.0f;
            if (Math.abs(projection[i] - expected) > 1.0e-4f) {
                return false;
            }
        }
        return true;
    }
```

and, immediately after the stride check that ends `this.offerStage = 7;` (the block that refuses with
`"layout id " + formatId + " has stride " + vertexStride`):

```java
        if (this.worldPassActive && celtium$projectionIsIdentity(this.glProjection)) {
            // The mirror, not OpenGL, supplies the projection inside the world pass (captureGlMatrices reads
            // VulkanGlState rather than the GL query). renderSky and renderCloudsFancy reinstall theirs as
            // matrixMode(GL_PROJECTION) + loadIdentity() + Project.gluPerspective(...): the loadIdentity is mirrored
            // and the gluPerspective is not, because it multiplies in through GL11.glMultMatrixf from outside the
            // transforming loader. Every batch offered in that window - the sunrise fan (1), the sun and moon (6) and
            // the cloud cells (8) - therefore captures the identity and would be drawn as huge translucent quads
            // across the sky. Refused with the condition named; the GLU mirror hook is what makes this unreachable.
            this.immediateLastRefusal = "world-pass batch captured the identity projection: the mirror missed the"
                    + " GLU-installed projection (matrixMode/loadIdentity are hooked, Project.gluPerspective is not)";
            this.refusedBeforeAppend++;
            this.flushPendingBeforeGlDraw();
            return false;
        }
```

## 2. Depth format contract for the GL-less owned frame target

### 2.1 What the game's framebuffer requires

`client/shader/Framebuffer.java` asks for exactly one of two things:

* line 123, `stencilEnabled == false`: `glRenderbufferStorage(GL_RENDERBUFFER, 33190 /* GL_DEPTH_COMPONENT24 */)`,
  attached as `GL_DEPTH_ATTACHMENT`;
* line 128, `stencilEnabled == true`: `GL_DEPTH24_STENCIL8_EXT`, attached as depth **and** stencil.

No file under `net/minecraft/` calls any stencil entry point (no `glStencilFunc`/`glStencilOp`/`glStencilMask`
anywhere in the decompiled tree), and `CeltiumGlStub` lists all of them as no-ops. So the requirement the
contract has to meet is **24-bit depth**, with the stencil aspect optional and unused.

`celtium-capabilities.json` reports `requiredDepthFormat = D24_UNORM_S8_UINT` in every run on both GPUs, but
that value is the literal default in `ProbeReport.java:19`; nothing queries it, and the GL-less validation
JSON cannot corroborate it (`depthInternalFormat: 0, depthBits: 0, stencilBits: 0` - it measures the stub).
So "the game's framebuffer requires D24S8" is an assumption, and on the AMD GPU here it is an assumption
that no Vulkan format satisfies.

### 2.2 What the devices support for a depth attachment that can also be sampled

`scripts/vulkan_depth_format_probe.py` (standalone, physical-device queries only, no `VkDevice`, safe to run
while a game instance is live):

```
VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/radeon_icd.json scripts/vulkan_depth_format_probe.py
device 0: AMD Radeon RX 6400 (RADV NAVI24)
  format                 depthSten  sampled  linear   create   sampleCounts/mips
  D16_UNORM              yes        yes      NO       ok       1x1=yes mips=15
  X8_D24_UNORM_PACK32    NO         NO       NO       NO(-11)  result=-11
  D32_SFLOAT             yes        yes      NO       ok       1x1=yes mips=15
  S8_UINT                yes        yes      NO       ok       1x1=yes mips=15
  D16_UNORM_S8_UINT      yes        yes      NO       ok       1x1=yes mips=15
  D24_UNORM_S8_UINT      NO         NO       NO       NO(-11)  result=-11
  D32_SFLOAT_S8_UINT     yes        yes      NO       ok       1x1=yes mips=15

VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/intel_icd.json scripts/vulkan_depth_format_probe.py
device 0: Intel(R) UHD Graphics 630 (CFL GT2)
  D16_UNORM              yes        yes      NO       ok
  X8_D24_UNORM_PACK32    yes        yes      NO       ok
  D32_SFLOAT             yes        yes      NO       ok
  S8_UINT                yes        yes      NO       ok
  D16_UNORM_S8_UINT      NO         NO       NO       NO(-11)
  D24_UNORM_S8_UINT      yes        yes      NO       ok
  D32_SFLOAT_S8_UINT     yes        yes      NO       ok
```

The two GPUs disagree on every 24-bit form, which is the reason a fixed format cannot be a contract:
RADV NAVI24 has no `X8_D24_UNORM_PACK32` and no `D24_UNORM_S8_UINT` at all (this is the spec's permitted
alternative: it supports `D16_UNORM_S8_UINT` and `D32_SFLOAT_S8_UINT` instead), while Intel has both 24-bit
forms but no `D16_UNORM_S8_UINT`. `D32_SFLOAT` and `D16_UNORM` are mandatory in core and work on both;
`D32_SFLOAT` is the only one that meets the game's 24-bit requirement. This agrees with the external-image
query already in `celtium-capabilities.json`, where `D24_UNORM_S8_UINT` reads `queryResult: -11` on AMD -
so it is not merely an interop-export limitation of that query.

### 2.3 Proposed contract

* **Preferred:** `VK_FORMAT_D32_SFLOAT`.
* **Ordered fallback:** `D32_SFLOAT` -> `D24_UNORM_S8_UINT` -> `D32_SFLOAT_S8_UINT` -> `X8_D24_UNORM_PACK32`
  -> `D16_UNORM_S8_UINT` -> `D16_UNORM`. The order prefers a >=24-bit depth first and a stencil aspect
  second, so the format matches the game's own `GL_DEPTH_COMPONENT24` whenever that is possible, and the
  last two entries only exist so that a device that is unusual in a different way still starts.
* **Acceptance predicate, applied to every candidate:** vkGetPhysicalDeviceFormatProperties ->
  `optimalTilingFeatures` has `DEPTH_STENCIL_ATTACHMENT_BIT | SAMPLED_IMAGE_BIT`; and
  vkGetPhysicalDeviceImageFormatProperties(2D, OPTIMAL, `DEPTH_STENCIL_ATTACHMENT | SAMPLED | TRANSFER_SRC |
  TRANSFER_DST`, flags 0) returns `VK_SUCCESS` with `sampleCounts & VK_SAMPLE_COUNT_1_BIT`.
* **Named refusal when none is available:** the target is not created and the run refuses with
  `"no sampled depth format: none of D32_SFLOAT, D24_UNORM_S8_UINT, D32_SFLOAT_S8_UINT, X8_D24_UNORM_PACK32,
  D16_UNORM_S8_UINT, D16_UNORM supports DEPTH_STENCIL_ATTACHMENT|SAMPLED_IMAGE as a single-sample 2D
  optimal-tiling attachment (celtium.vulkan.depthFormat=<override>)"`, plus a
  `-Dceltium.vulkan.depthFormat=` override so a device-specific choice can be forced and measured.
* Two places must follow the chosen format rather than a constant: `GllessFrameTarget.allocateImage`
  (line 185, `VK_FORMAT_D32_SFLOAT`, and the usage there is `DEPTH_STENCIL_ATTACHMENT | TRANSFER_SRC |
  TRANSFER_DST` - **no `SAMPLED_BIT`**, which the "can also be sampled" contract requires) and
  `VulkanTerrainPipeline.DEPTH_FORMAT` (line 85, used at line 896 to build the depth-stencil state, so a
  chosen format that differs from the pipeline's is a pipeline/attachment mismatch). The capability that
  was chosen belongs in `logs/celtium-capabilities.json` next to `requiredDepthFormat`, so a run says which
  format it used instead of leaving it to be inferred.

### 2.4 Patch text — the query, `celtium-probe/.../VulkanProbe.java` (file not owned)

`inspectDevice`'s existing `imageFormats` list answers a different question: every entry goes through
`VkPhysicalDeviceExternalImageFormatInfo`, so on RADV all 24-bit forms read `-11` because they cannot be
*exported*, which says nothing about the owned target. Add the plain query, unconditionally:

```java
            // The owned frame target's question, asked without the external-memory chain: can this format be a
            // single-sample 2D depth attachment that is also sampled? The external query above cannot answer it - on
            // RADV every 24-bit depth format is reported unavailable for export, which is not a statement about a
            // depth attachment. Recorded per device so a run says which format the target may use.
            result.depthFormats.add(depthFormat(device, "D32_SFLOAT", VK_FORMAT_D32_SFLOAT));
            result.depthFormats.add(depthFormat(device, "D24_UNORM_S8_UINT", VK_FORMAT_D24_UNORM_S8_UINT));
            result.depthFormats.add(depthFormat(device, "D32_SFLOAT_S8_UINT", VK_FORMAT_D32_SFLOAT_S8_UINT));
            result.depthFormats.add(depthFormat(device, "X8_D24_UNORM_PACK32", VK_FORMAT_X8_D24_UNORM_PACK32));
            result.depthFormats.add(depthFormat(device, "D16_UNORM_S8_UINT", VK_FORMAT_D16_UNORM_S8_UINT));
            result.depthFormats.add(depthFormat(device, "D16_UNORM", VK_FORMAT_D16_UNORM));
```

```java
    private static ProbeReport.DepthFormat depthFormat(VkPhysicalDevice device, String name, int format) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ProbeReport.DepthFormat result = new ProbeReport.DepthFormat();
            result.name = name;
            result.vulkanFormat = format;
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(device, format, properties);
            int optimal = properties.optimalTilingFeatures();
            result.depthStencilAttachment = (optimal & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0;
            result.sampledImage = (optimal & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) != 0;
            VkPhysicalDeviceImageFormatInfo2 info = VkPhysicalDeviceImageFormatInfo2.calloc(stack).sType$Default()
                    .format(format).type(VK_IMAGE_TYPE_2D).tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
            VkImageFormatProperties2 props = VkImageFormatProperties2.calloc(stack).sType$Default();
            result.createResult = vkGetPhysicalDeviceImageFormatProperties2(device, info, props);
            if (result.createResult == VK_SUCCESS) {
                result.singleSample = (props.imageFormatProperties().sampleCounts() & VK_SAMPLE_COUNT_1_BIT) != 0;
            }
            return result;
        }
    }
```

with, in `ProbeReport`:

```java
    public final List<DepthFormat> depthFormats = new ArrayList<DepthFormat>();

    /** One depth candidate for the owned frame target, answered without the external-memory chain. */
    public static final class DepthFormat {
        public String name;
        public int vulkanFormat;
        public boolean depthStencilAttachment;
        public boolean sampledImage;
        public boolean singleSample;
        public int createResult;

        public boolean usableForOwnedTarget() {
            return createResult == 0 && depthStencilAttachment && sampledImage && singleSample;
        }
    }
```

## 3. Still open

* The immediate path's group mesh carries one layout but `VulkanDynamicMesh.copyVertices` (line 220)
  assigns `this.layout = new Layout(stride, 0, -1, -1, -1, -1)` on *every* append. The group key includes
  `currentGroupFormatId`, and the stride is validated against the format's expected stride, so a mixed mesh
  was not reachable in these runs - but the invariant is not enforced anywhere. Worth a refusal or an
  assertion there before another layout is added.
* `reportGroupBounds` (line 3233) is dead code: nothing calls it, so the per-group NDC bounds and `colour0`
  the previous round asked for have never been printed. It is the instrument a wedges-shaped defect wants.
* The `CELTIUM_ARRIVALS`/`CELTIUM_ACCEPTED` census fires once, at 500 batches, and is therefore almost
  always inside the loading screen. A cadence that survives to the capture tick is what the per-format
  attribution the mission asked for actually needs.
