The texture batch (N07/N08/N17/N05) needs no change in VulkanTerrainGpuBackend any more - the two acceptance
numbers reach the fixture's report through the existing call site. This file records what was checked, and the one
small change that was considered and rejected, so the next reader does not repeat either.

What the report carries now, with no backend edit:
  VulkanTextureRegistry.externalSummary() keeps its signature (instance, no arguments) and appends
  " layout[" + VulkanGlTexture.foreignLayoutSummary() + "] patchLevels[" + VulkanGlTexture.mirrorLevelSummary() + "]"
  to the string it already returned. verificationSummary() calls it at the "externalImages=[...]" term, so the
  fixture's rendererDebug now contains, verbatim from the 2026-10-11 10:58 run:

    externalImages=[published=0 republished=0 unpublished=0 supersededMirrors=0 invalidatesIgnored=0 live=0
      layout[transitions=0 skips=0 commits=1109 abandons=0 dropped=0 open=0 unbound=0 declaredMismatches=0]
      patchLevels[levels=[0:328 1:328 2:328 3:328 4:328 ] written=1640 beyondProducerChain=0 samplerReach=1
      reachUnwritten=0 refusals=0]]

  The old describe()-only line still prints the per-registry counts; nothing was removed.

Rejected: making externalSummary() static and adding imageSummary() for the per-registry counts. It reads better
(only the process-wide facts would be static), but the already-built VulkanTerrainGpuBackend.class invokes
externalSummary() with invokevirtual, so a static method of the same name throws IncompatibleClassChangeError the
first time the fixture asks for the debug string. Measured: a run with exactly that change died with
"Expecting non-static method ... externalSummary()" at VulkanTerrainGpuBackend.verificationSummary:4457 and never
reached the world. Changing a public method's staticness is an ABI change for every already-compiled caller; the
instance signature stays.

No patch is required. If a future edit does need one, the anchor is:
  celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java, verificationSummary(),
  the "externalImages=[" term.

The run quoted above (10:58) is a known regression under investigation, not the scene's normal state. Every GL-less
allBlocks run from 09:33 onward reports presentedNonBackgroundFraction=0.364906 with background 0xB3CFFF - sky only,
no terrain pixels - while the last runs that still drew the floor are 09:06-09:16 (0.657788-0.822577). The window is
09:17-09:30 (b7b634179 unbound-framebuffer, 30a30a375 the projection hook, 9655ff681 the depth format); the depth
format is ruled out on AMD by the probe, so the projection hook is the suspect. The counters in this note are
texture-upload and layout-handshake counts, which is why they remain valid evidence even though the frame is not. Do
not read "validationClean=true" beside those numbers as a statement about the picture.
