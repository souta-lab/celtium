# Celtium rendering-corruption audit — 2026-10-10

Audited remotely on s0u7a-optiplex3060, repository /home/s0u7a/projects/celtium.
At the final source check (23:36 JST), HEAD was 7c40de541bc634a0d0b0bd7d0cfbc4213e1dee96, with ongoing worker edits.
This is an evidence handoff. No renderer code was changed and no additional game/GPU run was launched by this audit.

## Finding

The current bridge-mode black terrain is narrowed to the terrain shader's input/sampling/shading path. A constant-colour terrain fragment experiment reaches the visible framebuffer. The exact bad input is still unproven.

Two defects in the diagnostic experiments can send the investigation in the wrong direction:
1. The vertex readback loses the association between each draw offset and its region buffer.
2. The purported white lightmap poison is a white/black checkerboard, and normal daylight vertices sample its black texel.

Do not turn those diagnostic results into claims that the GPU upload is broken or that the terrain does not sample the atlas.

## Observed runs

All paths below are relative to /home/s0u7a/projects/celtium/.celtium-work/runtime/.

| Evidence | Observation | What it establishes |
| --- | --- | --- |
| noimm3/screenshots/celtium-validation.png | Sky, hand and HUD visible; terrain black. The run reports PASSED. | The bridge fixture can pass despite incorrect pixels. This is not an all-black swapchain/window. |
| glonly/screenshots/celtium-validation.png | Sky replaces the terrain with refuseTerrainPass=true and skipColorReturn=true. | Removing Vulkan terrain removes the black region. GL fallback does not draw equivalent terrain here; this is not an independent OpenGL reference. |
| nocopy | skipColorReturn=true still produces black terrain. | A broken colour copy-back alone does not explain it. |
| magenta/screenshots/frame-15.png | 605,164 pixels are exact RGBA (255,0,255,255), with visible sky and hand. The terrain fragment SPIR-V differs from the normal module. | The constant-fragment experiment can produce visible terrain colour. This argues against a permanently black attachment, colour write mask or presentation path for this bridge run. It does not validate terrain texture/lighting inputs. |
| p559-glless/screenshots/frame-120.png | Earlier genuine GL-less run has large wedges and a black band. | Older GL-less geometry corruption is a separate acceptance case. Do not infer its geometry is fixed from bridge-mode magenta. |

The harness's recorded constant-fragment worker task explicitly asked for an early flat magenta output before texture modulation/fog. The image and changed shader artifact agree with that experiment. The magenta run itself had fixtureStatus=FAILED; this controlled visual evidence is not a general validation PASS.

During the audit, HEAD advanced from 4bcff8de7 to 7c40de541 (“mirrored matrices for the GL reads vanilla makes, off-target pass routing, evidence”). The older p559 image therefore needs a fresh GL-less comparison after that change.

## Concrete diagnostic defect A: wrong region in vertex readback

Source: celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java

At lines 5198–5203, each draw remembers its byte offset in readbackOffsets[slot], but assigns its RegionResources to one shared readbackFrom field. At lines 5237–5239, every remembered offset is copied from that single final region:

    readbackOffsets[slot] = batch.baseVertex(command) * commonVertexStride;
    readbackFrom = resources;
    ...
    recordReadback(readbackFrom, readbackOffsets[slot], slot);

recordReadback reads resources.vertexArena. Offsets from earlier regions are consequently applied to the last selected region's buffer. The slots also persist across passes until readbackDone, so earlier slots can be recopied from another pass's region.

The desc-vertices log contains zero vertices and implausible values, but this instrument cannot establish that the bytes used by the corresponding draw were zero/corrupt.

Repair the diagnostic before relying on it:
- Store source buffer/resource, generation and offset per slot, together with the draw/pass identity.
- Copy only newly collected slots for a pass and respect the source range.
- For an indexed draw, identify the actual fetched vertices from the index buffer plus baseVertex, rather than assuming the vertex at baseVertex is representative.
- Compare the same source range against the CPU bytes retained for that draw. Keep the existing arenaVerify evidence separate: upload equality does not establish the draw uses the correct offsets.

## Concrete diagnostic defect B: white poison blacks daylight

Source: celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java, copyToImage.
The poison alternates argb with argb XOR 0x00FFFFFF. Therefore 12=FFFFFFFF is not a solid-white lightmap.

Measured device-side PNG:
poison2/mirror-dump/mirror-gl12-16x16-l0-n2-obj5dd54651.png

- 128 pixels are (255,255,255,255).
- 128 pixels are (0,0,0,255).
- Alpha is 255 for all 256 pixels.
- Texel (0,15) is black.

terrain_solid_vanilla.vert samples clamp((lightCoord + 8) / 256, 0.5/16, 15.5/16). The desc-vertices CPU encoder logs show ordinary daylight with block=0 and sky=240. That maps exactly to the centre of texel (0,15), including with linear filtering. The poisoned lightmap therefore makes these vertices black by construction.

For contrast, an unpoisoned atlas-fix2 device dump has texel (0,15)=(250,250,250,255) at n116, with alpha 255 throughout.

The fragment shader then multiplies atlas RGB by v_Color RGB and by m_color.a. Thus “atlas poison does not change black pixels” can mean a downstream light/colour multiplier is zero. It does not prove the atlas is unsampled.

Use a truly uniform RGBA (255,255,255,255) lightmap, including its mip levels, for the lighting bypass. Keep a checkerboard only for atlas/UV diagnostics. Ensure the bypass survives all full uploads and patches, and verify the actual descriptor's image at the captured draw/frame. In particular, poisonColour is assigned in uploadFromGlTexture; images created solely through the announced/fromImage path need explicit instrumentation if they are part of this experiment.

## Artifact checks that narrow the search

The packaged normal terrain shaders in p530-tp, p513-glow, p559-glless, noimm3 and desc-vertices had identical SHA-256 prefixes:

| Shader resource | SHA-256 prefix |
| --- | --- |
| terrain_solid.frag.spv | bc1e741db9983d32 |
| terrain_solid_vanilla.vert.spv | 9ee25c4e5eca40d8 |
| terrain_solid.vert.spv | 67cd62eb9977522f |

Normal fragment full SHA-256:
bc1e741db9983d3209379d6257f761515e1cdab74dd7a5f0071cfe6bf70c3fb5

Magenta experiment fragment full SHA-256:
b931818a969f1636e0c0cd50a1976eb1e27c87fae913d36bc0966a5dc2fdbc6d

This rules out a change to those packaged normal shader bytes across these runs. It does not prove the resource actually loaded by every runtime; log resource URL, length and hash at module creation if that remains in doubt. The handles run jar had the normal fragment hash again.

noimm3 atlas descriptor logs report the same live/written view and key, 512×512 full content, and verdict=fresh on inspected draws. That weakens the simple stale-atlas-view hypothesis for those draws. It says nothing by itself about correct UVs, lightmap identity, or sampled colour.

## Next diagnostic steps for the active worker

1. Incorporate the two diagnostic corrections above; do not repeat the magenta experiment already evidenced.
2. Bisect the shader's values in a small fixed-scene diagnostic batch: atlas sample alone; vertex colour/light multiplier alone (RGB and alpha separately); their product before fog; then the normal output. Use truly constant-white lighting for the atlas-only case.
3. Associate each output with the same camera, frame, real pipeline/descriptor handles, image generation and shader hash. Inspect GPU-side UV/light attributes and the dynamic UBO for the same draw if a stage first turns black.
4. Fix the shared data/state flow identified by that first bad stage. Avoid further blind row flips, filter changes or per-entity work while this common terrain path is broken.
5. Verify the final normal shader on both bridge and genuine GL-less configurations. Compare the actual terrain ROI, excluding hand/HUD/sky. A present count, clean validation layer, nonzero screenshot colour count, or PASSED alone is insufficient.

The earlier red/teal checkerboard reference uses a special fixture world; unusual floor patterns are expected there. A bridge framebuffer already containing Vulkan terrain is not an independent original-Celeritas GL reference.

Do not mark the exact root cause fixed until normal texture, vertex-colour and lighting outputs reproduce the expected scene. This report does not request a goal rewrite or a change to the user's final goal instruction.
