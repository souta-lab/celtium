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

## Two blockers cleared, a correctness blocker found (2026-11-06 07:30-07:55)

- `-Dceltium.vulkan.recordsPositionTwins=true` (0f45e4b72): the position variants' twins again - `l35arr-amd` PASSED,
  validation 0; `texture-twin` 30,399 -> 9. The new top breakers: `key-change` 45,942 and `stride` 30,896.
- **bd6c83de3** (default path): an empty arena mesh opened its slice at 16-byte alignment because the copy paths set
  the layout after taking the write address. Fixed; the default-flags picture leg `l37pic-amd` PASSED, validation 0.
- With all switches on (`l36arr-amd`, r110 `b650af93`): `stride` breaks are gone and runs start to span groups
  (`runs=133756 … groups=138713`, 1,657 multi-group runs) - and the picture **FAILS** (validation 0): the hand draws
  black, the water and the crop cutouts are missing ([image](2026-11-06-array-records-multigroup-runs.png)). The cause is
  not established. The run key holds the descriptor, so a multi-group run shares one texture; candidates are (a) the
  reopened position twins reading the record or the vertex stream differently from the per-group draw, (b) the
  command's first-vertex/first-index derived from arena offsets that are now stride-aligned but not rebased per
  group, (c) state the record does not carry (the earlier note on texgen). A/B by switch: run with
  `recordsPositionTwins` off on r110 - if the picture passes, (a).

Next: separate (a)-(c) with the switch A/B above; then wire `createRecordsArray` and the texture array's set (the
record already carries the array index) and take the descriptor out of the run key. The switches stay off.

## The discriminating leg says it is not the multi-group runs (2026-11-06 08:05)

`l38arr-amd` (r110, `indirectImmediate` + `arrayRecords`, position twins **off**): every run a singleton
(`runs=48590 singletons=48590`) and the picture still **FAILS** (validation 0). The same switches on r108 passed
(`l33arr-amd`), and r110 with no switches passes (`l37pic-amd`). So the failure is the interaction of bd6c83de3 (the
empty arena mesh takes the stride before its first write) with the indirect path - candidate (a) is ruled out, the
multi-group runs are not needed to break it. Next discriminator: the indirect path's record/command for a group
whose slice now starts at a stride multiple - compare `vertexBindOffset()`/first-vertex between r108 and r110 on one
group (a bounded log line in `celtium$tryIndirect`).

## Stride-first A/B on one jar (`l39stride-{true,false}`, r111 `23dc650c`, indirect + array switches, 2026-11-06)

`-Dceltium.vulkan.arenaStrideFirst=true`: **FAILED_PRESENTED_IMAGE**, validation 0; `=false`: **PASSED**, validation 0.
The first six `CELTIUM_INDIRECT_PROBE` lines are identical in both (stride 24, offsets multiples of 24), so the
breaking group is a later one. The switch is now **off by default** (the default path gains nothing from it and the
run path breaks with it); the stride run-breaks therefore return when the run path is resumed, and the bad group has
to be found first (raise the probe's line limit and diff the two arms).
