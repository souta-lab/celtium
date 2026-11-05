# Array-records for ordinary immediate groups: where it stands (2026-11-06)

The structural path named in [fps-session](fps-session-2026-10-31.md) ("array-records for ordinary immediate groups",
expected 0.15-0.2 ms of the gap) has its pieces built: a bindless texture array, records shaders, and a record writer
that stores the texture index. It is opt-in: `-Dceltium.vulkan.indirectImmediate=true -Dceltium.vulkan.arrayRecords=true`.

## Measured on the current tree (r108 = d489d5c9c, AMD, world-accept, guiScale 3)

- `l33arr-amd` (validation on): **PASSED, validation 0** - the switches are safe to turn on.
- `l34arr-amd` (warm flags, `-Dceltium.vulkan.frameStats=true`, PASSED):
  `indirect[runs=55834 singletons=55834 groups=55834]` - **every run is a single group**; nothing coalesces.
  - `runFlush{texture-twin=30399, family=13618, key-change=8791, record-full=3026}` (all as singletons)
  - `refuse{positionOnly=64005, family:VulkanImmediateItemPipeline=20427}`

## What has to change, in order of the counts

1. **Records twins for the position-only textured variant** (`texture-twin`, `positionOnly` 64k refusals): the run is
   broken whenever a group without a records twin arrives.
2. **The unlit item family** (`family:VulkanImmediateItemPipeline`, 20k): `celtium$tryIndirect` accepts only lit items.
3. **A run key without the per-group texture descriptor and buffers** (`key-change`, 8.8k): the key still carries
   `prepared.descriptor` and the mesh's vertex/index buffers; with the array index in the record the descriptor is the
   shared array set, and groups must share one arena buffer for a run to span them.
4. **Record capacity** (`record-full`, 3k): the record buffer fills within a frame.

Until (1)-(3) hold, the switches only add work; they stay off.
