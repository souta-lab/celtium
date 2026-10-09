# Celtium: what is Vulkan, what is not, and what is not supported

This file is the single place where the project states its boundary. Everything in it is either a measurement recorded in
`docs/history/2026-10-09-investigation-log.md` or a decision recorded in `docs/plan.md`; nothing here is a projection.
It is written because the objective requires the supported and unsupported scope to be explicit, and because the honest answer
to "is it Vulkan yet" is a list rather than a word.

## 1. What is drawn by native Vulkan, measured

Each entry below is a measured acceptance count from the per-format counters (`CELTIUM_ACCEPTED`, printed incrementally to
stdout) and the end-of-run report, on AMD RX 6400 and Intel UHD 630.

| Content | Layout | Evidence |
|---|---|---|
| Terrain | the mod's own terrain pipelines | `vulkanTerrainPasses` 915-1653 (AMD), 330-834 (Intel); `glDrawsCancelled` matching one for one |
| GUI, font, HUD, hand | `POSITION_COLOR`, `POSITION_TEX_COLOR` | `outsideWorldPassAccepted` in the thousands per run, refusals 0 |
| Blocks | `BLOCK` | per-format acceptance, plus the S05/S06 audit fixtures |
| Items and entities | `ITEM` (position, texture, colour, normal) | `4:427` in the JEI recipe run - 431,617 arrivals of it in one partial run, all mapped |
| Particles | `PARTICLE_...` | per-format acceptance; the particle fixture exercises it |
| Sky, clouds, weather, water surface | colourless `POSITION_TEX` | `6:1353`-`6:2374` (AMD), `6:660`-`732` (Intel) |
| Position-only content | `POSITION` with the colour passed in the push block | `7:1008`-`1097` (AMD), `7:396` (Intel) |
| The composite's layout | `POSITION_TEX_NORMAL` | `9:12` on both GPUs |
| The game's other framebuffers | off-target render targets | `offTargetDrawn` 273-567 with `offTargetCopiedBack` 267-554, zero refusals |
| Final composite and present | the Vulkan swapchain | present traces cycling the swapchain images, `presentFailures=0`, `outOfDate=0` |

**Unmapped arrivals: zero**, verified on both GPUs and in a partial Intel run carrying **674,500** uploads.

Appearance: with these layouts left to OpenGL versus drawn by Vulkan, the frame differs by **0.267 per channel** against a
measured **scene noise floor of about 1.0** - equal to the GL path on a local comparison, which is not a claim of
pixel-identity.

## 2. What is still OpenGL, stated plainly

- **The GL context itself exists**, and the GL/VK bridge is built on it (a shared image and semaphore pair). This is the
  hybrid stage: the renderer does not yet start or draw without a GL context, and that is completion condition (3).
- **The bridge's synchronisation and the window's buffer swap** remain GL's.
- **Terrain mesh building and the mod's own chunk bookkeeping** are not drawing and were never in scope for replacement.
- **Nothing observed is left unmapped on the immediate path.** In the runs recorded here, not one upload was refused for being
  unrecognised; the legacy path is a fallback that currently has no traffic, and it is kept for the content this project has
  not seen.

## 3. Environment limits on verification

- **The physical screen cannot be observed here.** The X display reports a `0x0` screen (`ffmpeg -f x11grab -i :0.0` ->
  `Invalid size 0x0`) and no capture tooling is installed, so "does the presented image reach the monitor" cannot be closed on
  this machine. What *is* established: the present runs and repeats, and its output is provably **not** in the framebuffer GL
  reads (a solid magenta present leaves GL's back buffer showing the ordinary scene).
- **Intel plus JEI is borderline against the harness's duration cap.** The same jar completed at 720 seconds and timed out at
  720 seconds on consecutive attempts. Partial runs still yield evidence because the arrival and acceptance counters print
  incrementally.
- **The fixture's tick hooks advance one per game tick**, so on the slower GPU a fixed wall-clock budget reaches fewer of them;
  the hooks fire as early as the world is certainly up and the end-of-run tick count is reported.

## 4. Not supported, with reasons

- **Normal Forge (old Java, LWJGL 2) and other Forge + lwjgl3ify/RFB setups**: out of scope. Celtium is a Cleanroom renderer
  and uses Cleanroom's modern runtime. This is a decision, not a limitation discovered later.
- **Direct LWJGL, GLSL and native-GL mods**: not emulated. The project does not translate GL call by call and will not; such
  mods are investigated individually and their drawing sites either get a specific equivalent or are listed as unsupported.
- **Mesh shaders**: never required, by policy.
- **Zink**: not adopted. If ever considered, its conditions are checked and it is kept separate from the pure native path.
- **"Forge API swap makes every mod work"**: not claimed anywhere, and it will not be.

## 5. Java versions

- **Celtium compiles to `--release 21`** (class major 65) and its code uses no Java 22+ API, language feature or preview.
- **The game runs and is verified on Java 25.** The class files of Cleanroom's own launcher are major 69 and that is not a
  blocker.
- **It is not claimed that the whole game, Cleanroom included, runs on Java 21.** Only the Celtium classes are compiled for 21.

## 6. Licensing and lineage

- The pinned upstream base is Celeritas at `989ebf2e`, whose lineage runs through Embeddium and Sodium 0.5.11 or earlier -
  FOSS code. Celtium is LGPL-3.
- **No code from Sodium 0.6 or later (PolyForm Shield 1.0.0) has been copied, translated, renamed or cherry-picked.** Where an
  idea comes from published descriptions, its source is recorded in `docs/plan.md` rather than presented as original.
- Rewriting is not treated as a way to make a licensing question disappear.

## 7. How the claims above are measured

- **Arrival census**: what the frame offered, by mapped id and by element signature for anything unrecognised, printed every
  five hundred arrivals to stdout.
- **Acceptance counters**: what this renderer took, by id, printed every five hundred acceptances and in the end-of-run report.
- **Refusal reasons**: the backend's own text for a refused batch, reported once per format by the uploader hook. Every one of
  the eight guards and defects on these two paths was found this way rather than by reading the mapping.
- **Attribution switch**: the acceptance mask, which lets the same scene be rendered with a layout on Vulkan or left to
  OpenGL, for the frame comparison above.
- **Provenance**: each run's lock records the git head, subject, dirty state, effective JVM arguments, the fixture hash and, at
  the end, the driver.
