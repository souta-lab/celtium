# Which groups sample the published chain-pair image (round 251, log read)

No leg, no code edit. Logs read: `.celtium-work/runtime/r212-world-hud28/logs/debug.log` (1842 `CELTIUM_MIRROR_DRAW`
lines, `mirrorDrawLog=3000`), plus hud30/hud31 (8 lines each).

## 1. The mirror-draw line that names the pair's view

```
CELTIUM_MIRROR_DRAW frame=0 groupTexture=6 preparedKey=0x6e700000006e6 resolvedKey=0x6e700000006e6
  resolvedSize=1281x720 descriptor=0x4c000000004c mirrorSource=published-image@1281x720 target=0 batches=1 view=0x39000000
```

- `groupTexture=6` - the game framebuffer's colour-texture name, i.e. the name `celtium$publishChainOutput`
  (`VulkanTerrainGpuBackend.java:11182`) publishes.
- `mirrorSource=published-image@1281x720` - the group samples the published image, not a mirror of its own.
- `target=0` - the group carries identity 0 (the default framebuffer per `FramebufferMixin:157`).
- `view=0x39000000…` - the pair's view (`0x390000000039`); the log line is truncated at the preview width, so the
  full handle and `standsForLivePair` are **not** visible in this window (see 4).
- Count: the hud28 log has **one** such line in the previewed set; a count of `view=0x390000000039` matches zero
  because the field is cut off before the trailing digits, which is a limitation of the line, not an absence.

## 2. The identity and the routing

`target=0` is identity 0, and `routeLabelForBinding(0, false)` is **default-pair** - the branch added in round 219
(`FramebufferMixin:157` reports `bindFramebuffer(0L,false)` for `unbindFramebuffer`, so identity 0 is the default
framebuffer). The routing therefore sends this group to the **default-pair** pass, while the descriptor it carries
samples the **chain pair's** view.

## 3. Is the feedback loop real here?

No. The draw lands in a pass whose attachment is the **default pair**; the sampled image is the **chain pair**. So the
group is not sampling the image its own pass attaches, and the `VUID-vkCmdDraw-None-09600` pair is not explained by a
same-pass feedback in these legs. The surviving explanation is the one from
`docs/notes-chain-pair-layout.md`: the descriptor written for the published wrapper records a layout
(`SHADER_READ_ONLY_OPTIMAL` after `VulkanGlTexture:1552`/`:2531`, or `GENERAL` if written earlier) while the image's
real layout at submit is the other one - a **stale recorded layout**, not a feedback loop. The same-pass hypothesis
would need a group whose route is chain-pair while its descriptor samples the pair's view; the log shows the opposite
route for the one group that samples it.

## 4. What these logs cannot decide, and the one bounded line

The truncated `view=` field and the absent `standsForLivePair` mean the logs cannot confirm whether *any* group both
samples the pair's view and is routed into a chain-pair pass. The bounded line to add (one print, at the descriptor
write for a published wrapper): the wrapper's GL name, the wrapper's tracked `currentLayout`, the
`VkDescriptorImageInfo.imageLayout` being written, and the route label of the group's target identity.

Values that separate the two explanations: if that line shows `route=chain-pair` for a group whose descriptor names the
pair's view, the feedback loop is real and the fix is the pair's own layout bookkeeping; if every such group shows
`route=default-pair` (as the single previewed line does), the message comes from the stale recorded layout and the fix
is to move the descriptor's recorded layout with the image (re-write the set on the transition, or key the set on the
wrapper's `currentLayout`).
