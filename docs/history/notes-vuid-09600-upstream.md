# `VUID-vkCmdDraw-None-09600` — external state of the false-positive class (2026-10-15)

This note records what an outside source says about the message Celtium's P1-a gate reports. It is **evidence
about the validator, not about our renderer**: the gate stays open until our own measurement closes it
(`AGENTS.md`: a failing gate is evidence until a measurement says it is wrong).

## What the plan reports (our measurement, unchanged)

`docs/plan.md` round 546/553/554: the same image gets **two `VUID-vkCmdDraw-None-09600` messages in opposite
directions** — "expects `GENERAL` -- instead `SHADER_READ_ONLY`" and "expects `SHADER_READ_ONLY` -- instead
`GENERAL`" — on a pair image whose render pass declares a constant `GENERAL` attachment layout, with the picture
still correct (`status=PASSED`). Closure condition on record: fix the layout transitions for that draw and see
`validationClean=true` with 0 VUIDs in two consecutive runs.

## What the upstream tracker says (fetched 2026-10-15)

- [KhronosGroup/Vulkan-ValidationLayers#10185](https://github.com/KhronosGroup/Vulkan-ValidationLayers/issues/10185)
  is titled "Timeline semaphore submission order & invalid image layout false positive" and its report body is the
  **same VUID and the same message shape** we see: `expects VkImage … to be in layout
  VK_IMAGE_LAYOUT_GENERAL--instead, current layout is VK_IMAGE_LAYOUT_UNDEFINED`. The reporter's own diagnosis is
  that the layer validates at `vkQueueSubmit` before the semaphore dependencies that change the layout are
  resolved; a maintainer replies that submit-time validation "should do this when semaphore dependencies are
  resolved" and that the image-layout part "probably" is not fixed yet.
- A commenter reports the **mirror-image case of ours**: a preceding command transitions the image
  `GENERAL → SHADER_READ_ONLY_OPTIMAL` and samples it without a warning, while the next command using the same
  image in the same layout warns that the image is "still in `GENERAL`" — and adds that the false positive
  **"gets hit at random points in time when executing the exact same test case"**.
- [Issue #2441](https://github.com/KhronosGroup/Vulkan-ValidationLayers/issues/2441) (the issue the maintainer
  links as the same root) describes layout state being updated at submission time while semaphores were supposed
  to order the usages, and offers the workaround of filtering
  `UNASSIGNED-CoreValidation-DrawState-InvalidImageLayout` via `khronos_validation.message_id_filter`.
- The issue is **closed** with a maintainer statement that it "should be fixed in the latest code and will be
  available in the next SDK", and it is referenced by
  [PR #10316 "layers: Validate global image layouts during queue retire phase"](https://github.com/KhronosGroup/Vulkan-ValidationLayers/pull/10316).
  [Issue #2487](https://github.com/KhronosGroup/Vulkan-ValidationLayers/issues/2487) documents the same design
  ("there are both record time and submit time checks… effectively we track the *first* use of a given resource
  and check that against the global (submit) image layout at submit time").

## The layer this project actually loads (measured 2026-10-15, GPU-free)

The harness exports the repo's extracted layers itself (`scripts/prism_validation.py:474-482` sets `VK_LAYER_PATH`
to `.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d` and `LD_LIBRARY_PATH` to the matching
`usr/lib/x86_64-linux-gnu`), and that manifest reads:

```
VK_LAYER_KHRONOS_validation  api_version 1.4.363  implementation_version 1  library_path libVkLayer_khronos_validation.so
```

(read with `python3 -c "import json; print(json.load(open('.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d/VkLayer_khronos_validation.json'))['layer'])"`;
the system loader is `libvulkan.so.1.4.363`, and no other `VkLayer_khronos_validation` exists on this machine).

PR #10316 landed between the `vulkan-sdk-1.4.313.0` and `vulkan-sdk-1.4.321.0` tags, so **the layer every leg
loads already carries the queue-retire image-layout fix** — this is the measurement the previous section said was
missing, and it points the other way: the upstream false-positive class does **not** explain our messages on this
machine.

## What this changes, and what it does not

**Does**: it removes the false-positive explanation for the current legs. What remains is either our own layout
accounting — round 249 measured that the chain pair's render pass declares a constant `GENERAL` attachment layout
while the sampling path moves the image to `SHADER_READ_ONLY_OPTIMAL`, and that the pair has no per-frame layout
bookkeeping — or a different upstream bug than #10185. The next measurement is a **current-jar leg reading
`validationMessageCount` and the message text**: the round-722 regression legs on `main` are exactly that, and the
268-mod pack leg already reported `validationMessageCount=0` on a different scene.

**Does not**: it does not close P1-a. Two runs of one jar with the same message count are our evidence of
non-determinism only if we run them (round 255 planned exactly that and it is still the closure test), and a
false-positive *class* existing upstream is not a measurement of *our* four messages. Filtering the message
(`message_id_filter`) would hide a real violation of the same ID and is **not** a fix we accept; the closure
condition on record stays as it is.
