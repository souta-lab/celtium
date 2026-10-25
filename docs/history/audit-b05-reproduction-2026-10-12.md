# Audit B05: reproduced on the GPU, and two fixes measured insufficient (2026-10-12)

The maximum audit lists B05 as **code-confirmed**: `HostBuffer.ensureCapacity` memcopies into a replacement and then
deletes, unmaps and frees the old buffer immediately, while an earlier window of the same frame was already recorded
against the old handle. This is the record of turning that into GPU evidence, and of two attempts to fix it that the
validation layer refused.

## Reproducing it: the growth must be crossed

On the pinned validation scene a frame peaks at **781 KB of vertices and 215 KB of indices**, against the arena's
1 MiB / 256 KiB initial sizes, so the defaults never reach the growth path: the first before/after pair at 81 sections
was clean *because nothing grew* (`retiredAllocations=0`). The acceptance is therefore only meaningful when the
growth is crossed, and the lever for that is new and documented:
`-Dceltium.vulkan.frameArenaInitialVertexBytes` / `-Dceltium.vulkan.frameArenaInitialIndexBytes`
(`VulkanFrameArena.java`, defaults unchanged at 1 MiB / 256 KiB).

Legs (AMD RX 6400, GL-less, layers ON, fixture-driven, 854x480, duration 150,
`frameArenaInitialVertexBytes=262144`, `frameArenaInitialIndexBytes=65536`, so the same scene crosses the growth
repeatedly; `hostBuffersCreated=16` in both legs against 8 with the defaults):

| | before (free on growth) | after (retire to a fence) | after (retire to the slot epoch) |
|---|---|---|---|
| jar | `4451cff1858e3c6d…` | `252190e83494fca5…` | `18598ff89377da55…` |
| `validationMessageCount` | **4** | **4** | **4** |
| `validationClean` | **false** | **false** | **false** |
| VUID | `VUID-vkDestroyBuffer-buffer-00922` | same | same |
| retired allocations | - | 8 retired, 8 released | 8 released, 0 deferred |
| `peakVertexBytes` | 784,448 | 803,248 | 782,640 |

**B05 is now GPU-reproduced** - `VUID-vkDestroyBuffer-buffer-00922` ("buffer must not be currently in use by a command
buffer") at the growth, with `validationMessageCount=4` and `validationClean=false`.

## Why both fixes failed, and what the next one is

- **Retire to the newest deferred submission's fence** (the upload batch that filled the buffer): the fence taken at
  growth time belongs to an *older* batch, because the batch that has already recorded the old handle is flushed
  *later* in the frame. Its fence therefore signals too early and the destroy still races the submission.
- **Retire to the arena slot's completion epoch**: at growth time the slot's epoch is `-1`, because growth happens
  while the frame is still being *recorded* - the submission that will read the old handle does not exist yet. The
  epoch form then takes its "no reader" path and frees immediately (`retiredAllocations=0` in that leg).

The shape that follows from both measurements: the old allocation must be retired **into the slot**, not into an
existing ticket, and released at the **slot reset** - where the arena already holds the precondition it needs
(`!awaitingCompletion && epoch <= completedEpoch`, `VulkanFrameArena.java:93`). That is a change to the slot's
lifetime rather than to anyone's fence, and it is not in this record because it was not taken: the two attempts above
are what the evidence supports so far, and neither is a fix.

Nothing was widened to hide this: no drain, no budget, no counter suppression. The default arena sizes and the
shipping behaviour are unchanged; only the measurement lever and this record were added.

---

# B05 closed: the slot-retire form is clean on the growth-crossing leg (2026-10-12)

The shape the two failures pointed at was taken: the old allocation is retired **into the slot** and freed at that
slot's **reset**, whose completion precondition already exists (`VulkanFrameArena.java:93`,
`!awaitingCompletion && epoch <= completedEpoch`). The growth now builds a replacement object, copies the contents,
and keeps the old object in the slot's retired list (`VulkanFrameArena.Slot.retiredBuffers`), released in `reset()` and
in `freeBuffers()`.

Leg: jar `bbc3bf95319fa23b…`, AMD RX 6400, GL-less, **layers ON**, fixture-driven, 854x480, duration 150, the same
growth-crossing lever (`frameArenaInitialVertexBytes=262144`, `frameArenaInitialIndexBytes=65536`),
`hostBuffersCreated=16` (the growth really is crossed, as in the legs that failed):

| | before | retire-to-fence | retire-to-epoch | **slot retire** |
|---|---|---|---|---|
| `validationMessageCount` | 4 | 4 | 4 | **0** |
| `validationClean` | false | false | false | **true** |
| VUID | `vkDestroyBuffer-buffer-00922` | same | same | **none** |

So B05 moves from code-confirmed through GPU-reproduced to **fixed and witnessed** on the configuration that
reproduces it. The default arena sizes and the shipping behaviour are unchanged; the fix is a lifetime rule, not a
drain or a widened budget.
