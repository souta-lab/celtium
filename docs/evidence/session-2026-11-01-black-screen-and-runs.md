# Session 2026-11-01 — the black screen, and why runs do not form

Runs from ~22:00 2026-10-31 to 04:00 2026-11-01. Releases shipped: rc.24, rc.25.

## The tester's black screen: cause found on their own launch

Their two reports end identically: `font atlas built: 4096x4224 ... texture=225`, the announcement upload of that
atlas, then `radv: GPUVM fault detected at address 0x800101a00000` with PERMISSION_FAULTS 5 and RW 1. One earlier
report did the same after a **256x256** announcement, which is what retired the "too big an upload" reading.

Reproducing through the real launcher (PrismLauncher 9.4 `--launch Supersymmetry`) and putting the validation flag
where the relauncher forwards it - `config/relauncher.json` `"args"`, not the instance's JvmArgs - named it:

```
VUID-vkEndCommandBuffer-commandBuffer-00059
vkEndCommandBuffer() called on a command buffer whose bound objects were invalidated:
  VkImage 0x6e000000006e destroyed, VkImage 0x6b000000006b destroyed, VkFramebuffer 0xb700000000b7 destroyed
```

A frame's command buffer is recorded, and its windows already name framebuffers and images, before it is submitted.
The retire queue only asked whether every *submission* had completed - not whether recording was still open - so a
drain in that gap destroyed exactly what the open buffer held. rc.24 gates both mid-frame drains on
`celtium$recordingOpen()` and retires the post chain's replaced main framebuffer instead of destroying it. rc.25 adds
`celtium-device-lost.log` (trigger + whole breadcrumb ring, written beside the game) so the next "still black"
report arrives with the artifact.

**Owed: the tester's launch of rc.25.** The real-Prim trigger cannot be automated here (single-instance forwarding
swallows `--launch`, the account dialog blocks 11.1, and the harness refuses the instance's own game directory), and
the harness does not reproduce the loss even at 854x480 with the instance's real options, fullscreen and resize
transitions - so only their launch can confirm the fix.

## Why indirect runs stay singletons (measured)

- Run-flush census: every run closed after one command; reasons split `positionOnly=78,766`, `family=17,038`.
- Record-reading twins for the position variants were written (4 shaders + variant-aware factory): refusals fell to
  the unlit item family, but runs still closed every group - the indirect key names each group's own vertex and index
  buffer, and groups do not share one (`key-change=26,950`, `stride` refusals `18,711`), so the run path only cost
  more (P50 2.66 -> 5.94 ms with the flag on). Twins stay behind the refusal.
- A shared geometry arena (copy each group's vertices/indices into one buffer, drop the handles from the key) was
  wired and built, then reverted: it crashes the JVM with SIGSEGV in `jbyte_disjoint_arraycopy` on the first world
  frame. The helpers (`bindRawAtZero`, `drawIndirectRangeBuffers`) are in the tree for the next attempt; the crash
  needs understanding first.

## Measurement rules learned the hard way

- **Idle window only.** Same release and flags: 2.449 ms at midnight, 5.7 ms at 03:00; CPU 3.9 GHz, package 63 C, so
  not throttling - the desktop's screen-streaming host (swayrtc + cloudflared) captures the session during a leg and
  the CPU-bound renderer loses milliseconds while the GPU-bound reference does not.
- **Leg duration >= 300 s, harness timeout >= 900 s.** A 150 s leg ends before the world is loaded on this machine.
- The gate's seed-options file differs from the instance's options only in guiScale; it does not change the frame.

## Where things stand

| Goal | State |
|---|---|
| (2) no Celtium errors | rc.24/rc.25 PASSED the canonical plain gate; menu smoke 3899/3899 presents, no device loss, no VUID |
| (3) fully working | same, on this machine; the tester's launch is the outstanding evidence for the black screen |
| (1) FPS above Celeritas | not met: cool-idle Susy 1.27-1.48x (best 1.275), ATM3 ~1.45x; current machine state unmeasurable |

Environment left for the tester: their instance carries rc.25 in `mods/`, `config/relauncher.json` `args` has
`-Dceltium.vulkan.validation=true -Dceltium.vulkan.presentedDumpEvery=0`, and backups of the original jar,
`instance.cfg` and `relauncher.json` are in `.celtium-work/prism-backup/`.

## Release certification at the end of the session

- rc.25, ATM3 (RD4 terrain leg, the second pack): VUID 0, device loss 0, draw-array refusals 0, patch rejections 0,
  warm P50 4.39 ms in the loaded machine state (the same state that read 5.7 ms for Susy; not a cool number).
- rc.25, Susy menu smoke: 3899/3899 presents, no device loss, no validation messages, no device-loss file.
- rc.25, Susy world: canonical plain gate PASSED with floors enforced and the presented image judged.

## The fatal transition itself, on rc.25

The tester's startup goes windowed (854x480) then fullscreen (1920x1080) - the transition the earlier session
already called the launcher's fatal one. On rc.25 the same transition is exercised deliberately in one world leg:
`debugResizeAfterFrames=120 debugResizeTo=1280x720` reported `ok=true` and `debugFullscreenAfterFrames=240`
reported `ok=true`, the swapchain rebuilt, and the leg finished with **device loss 0, GPUVM fault 0, VUID 0**, no
device-loss file, and the world gate still judging the presented image. That is the mechanism the two rc.24 fixes
target, exercised end to end on this machine; the tester's own launch of rc.25 remains the confirmation owed.
