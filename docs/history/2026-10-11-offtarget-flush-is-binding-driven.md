# BH-14/15/16: the off-target flush is binding-driven, so a target's groups wait for a bind that never comes

Owner: **fog-path**. Date: 2026-10-12, ~01:45 JST. GPU: AMD RX 6400 (RADV NAVI24).

## The mechanism, at file:line

`VulkanTerrainGpuBackend.flushImmediateWindow()` (line 5825) filters its work by the target that happens to be
**bound when it runs**:

```java
if (countGroupsForTarget(this.currentRenderTarget) == 0) return; // others remain owned in the queue   // :5867
```

`countGroupsForTarget` (line 6415) counts only groups whose `group.target` equals the bound identity, so a
flush draws the bound target's groups and leaves every other target's groups in `immediateGroups`. They stay
there until their own target is bound again - and a framebuffer that is bound once and never again (the
panorama's, the loading screen's) never gets that bind, so its groups are never drawn at all.

## The counter that shows it

`offTargetPerTarget` (the `celtium/vulkan-check` line) prints, per non-main identity, offers / groups at pass
time / drawing passes. Measured:

```
63482454: offered=677, atPass=32, drawnPasses=17
1d468ab3: offered=2,   atPass=1,  drawnPasses=1
0:        offered=70,  atPass=70, drawnPasses=70
```

The 677-to-32 gap is exactly the groups that never met a pass for their own target. The default pair (identity
0) draws all 70 because the game rebinds it constantly; the one-shot identities do not.

## Why this is the panorama's and the loading screen's root

- The panorama's cube faces are accepted for `target=572879803` (routed to the chain pair,
  `routeLabelForBinding`). No pass runs for that target, so the chain pair is never written.
- The copy's read now resolves to the chain pair (the previous unit's fix) and still reads
  `mean=255,255,255`: the image is empty, not mis-resolved.
- The loading screen's white window is the same shape: its groups are offered for a one-shot identity and the
  `offTargetPerTarget` columns show most of them never drawn.

## The fix direction (BH-14/15/16)

1. **Make the flush target-driven**: when a flush runs, drain every target with pending groups into its own
   pass - which needs the per-identity target of BH-14 (one owned pair per game FBO identity/generation, with
   its own size) rather than the current "everything non-main goes to the chain pair".
2. **A target that cannot be drawn must be dropped and counted**, never left queued indefinitely (BH-15: a
   failed creation must be an explicit failure for that target, not a silent re-route to the main frame).
3. **The clear must carry its target, mask, colour/depth and order** (BH-16), so a target's first clear lands
   on its own image rather than a fixed RGBA+depth applied once per frame.

`docs/performance_plan.md`'s OPT-10 is the contract this has to respect: the command-state cache is
invalidated at target/pass boundaries, and the immediate group's state is the producer-time snapshot - so a
target-driven flush has to open a pass per target and re-issue the state it needs rather than assuming the
bound pass's state.
