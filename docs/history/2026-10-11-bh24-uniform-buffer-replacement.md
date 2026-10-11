# BH-24: the uniform buffer's growth builds its replacement before it publishes it

Owner: **fog-path** (round-22 package). Date: 2026-10-12, ~00:57 JST. GPU: AMD RX 6400 (RADV NAVI24).

## The bug

`VulkanUniformBuffer.ensureCapacity` saved the old handles in locals and then called `allocate`, which wrote
`this.buffer` first and `this.memory`/`this.mappedAddress`/`this.size` later. A failure anywhere after the first
write left the object holding a zero or half-published handle while the old buffer existed only in a local -
leaked, and unreachable for the next write or the next growth.

## The change (`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanUniformBuffer.java`)

- The allocation moved into `create(bytes, what)`, which builds its own `Allocation` (buffer, memory, mapped
  address, size) and touches **no field of the object**. Every failure path goes through `destroyReplacement`,
  which releases only what that replacement had created and returns the exception to throw.
- `ensureCapacity` builds the replacement, copies the old contents into it, then `publish`es it and only then
  destroys the old set. A failed growth therefore leaves the previous buffer, memory and mapped address exactly
  as they were, and the caller keeps a working buffer.
- Counters: `growths`, `growthFailures`, `lastGrowthFailure`, reported as `uniformgrowths=… growthFailures=…
  lastGrowthFailure=…` through `VulkanTerrainGpuBackend.verificationSummary()`.
- The allocation site label is now distinct per path (`VulkanUniformBuffer:initial`, `…:growth`), so the
  existing fault injector can fail a **growth** specifically instead of always the constructor's first
  allocation.

## Verification, both legs scene=terrain, GL-less, `--validate`, AMD RX 6400

| leg | counter | verdict |
|---|---|---|
| `bh24-normal` (jar `fog-path-bh24.jar`) | `growths=2 growthFailures=0 lastGrowthFailure=none`, `uniformPreparationFailures=0` | **PASSED**, nonBg **0.759463** |
| `bh24-inject` (`-Dceltium.vulkan.failAllocationSite=VulkanUniformBuffer:growth`) | `growths=2 growthFailures=1 lastGrowthFailure=injected_allocation_failure:_VulkanUniformBuffer:growth`, `uniformPreparationFailures=1` | **PASSED**, nonBg **0.759463** |

The injected leg is the measurement: the growth was made to fail, it was counted, one preparation failed
because of it - and the frame is **identical** to the normal leg (same non-background fraction, same verdict,
`worldTicksAtEnd=206`), which is only possible if the previous buffer survived the failure and kept serving the
draws. `validationMessageCount=0` in both legs (no validation messages were produced).

## Not claimed

The failure was injected at the allocation step (the label covers the memory-type lookup, the injected point,
`vkAllocateMemory`, the bind and the map). A failure specifically inside `vkBindBufferMemory`/`vkMapMemory`
after a successful `vkAllocateMemory` is on the same `destroyReplacement` path but was not exercised
separately.
