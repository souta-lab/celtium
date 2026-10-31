# FPS session evidence, 2026-10-31 (AMD RX 6400, Mesa 26.2.6)

Every ratio below is a **paired ABBA** on the same machine state: A1/Celeritas/B2/Celtium or the
reverse order, same instance, same scene, same fixture flags, warm window = world ticks 400-600.
Absolute milliseconds move with the machine's thermal state; the ratios are what this file is for.

## Ratios

| Date state | Celtium | Celeritas | Ratio | Notes |
|---|---|---|---|---|
| rc.9 pair (hot) | 3.10 | 1.83 | 1.69x | thermally loaded; replaced by the cool rows |
| rc.9 pair (cool) | 2.67 | 1.76 | 1.51x | |
| rc.12 ABBA | 2.565 | 1.755 | 1.461x | |
| rc.14 ABBA | 2.767 | 1.871 | 1.479x | b2 drifted up (heat) |
| rc.18 ABBA | 2.503 | 1.817 | 1.378x | Celeritas b2 hot |
| **rc.19 ABBA** | **2.468** | **1.742** | **1.417x** | Celtium stable 2.46-2.48; honest estimate ~1.40x |

ATM3 terrain RD4 (cool, rc.9): Celtium 2.27 vs Celeritas 1.56 = 1.45x. Under thermal load Celtium
doubles (4.35) while Celeritas barely moves (1.41): the machine state must be reported with the
ratio, and the ratio taken cool.

## Where the frame goes (Susy world, pass split p50 ms/frame)

frame 1.9 = flush 0.8 (winRecord 0.5: draws 0.2, begin-pass 0.1; winBarrier 0.1; winSubmit 0.1)
+ terrain 0.1 + sky 0.1. 30 windows/frame, 112 groups drawn/frame. Client thread is CPU-bound at a
cool machine too (94.3% of wall by /proc; GPU terrain 375us + immediate windows 48us and idle).

## Costs removed this session (each measured)

- raw-write flush coalescing: raw-write flushes 26.7-28.0 -> 17.3-17.5 per frame; refreshRawWrites
  8.3% -> 0.4% of client-thread CPU.
- published name-to-image resolution cached: transition loop 3.5% -> 0.8%.
- `EntityMembershipEpoch` lock spin removed: 3.0% -> 0.
- per-group `System.nanoTime` instrumented behind a flag: clock_gettime 1.2% -> 0.
- window `VkRenderPassBeginInfo` and pair clear structures reused; **the first version shipped a
  validation defect** (malloc'd `VkImageSubresourceRange` with only counts set → garbage
  baseArrayLayer) that the canonical gate caught; rc.15 withdrawn, rc.16 fixed.
- model runs skip pipeline/set/push/vertex/index binds when the previous draw is the same run, and
  consecutive compatible runs merge into one indirect range.

## Rejected with reasons (do not re-run blind)

- bind-time flush deferral: two attempts, two RADV SIGSEGVs in vkCmdBeginRenderPass; re-entry needs
  recording a deferred window pinned to its pair generation and dropping (counting) it when the
  generation moves.
- indirect immediate as shipped: every run was a singleton (2.39 -> 2.44 ms with the flag on).
- opaque material ordering: ABBA 2.538 on vs 2.508 off - reordering cannot remove per-group push.
  Only worth retrying when push is a record and the texture is out of the key (array-records).
- static frame reuse: would freeze clouds/animations, trading goal (3) for goal (1).

## The two remaining structural paths

1. **array-records for ordinary immediate groups**: bindless texture set (4096 combined samplers,
   update-after-bind) already built; records shaders and `createRecordsArray` exist; the record
   writer already stores the texture index at offset 216. What is missing is the run wiring for the
   ordinary groups (registration gating, run key without texture, image layout transitions before
   the run draw). Expect ~0.15-0.2 ms of the ~0.7 ms gap.
2. **native batch recording**: per-batch Java + FFM downcall overhead is the rest of the gap
   (~112 groups x ~1.8 us plus 30 windows x ~22 us). This is the plan.md "native implementation"
   item, not a round-scale change.

## Thermal state dominates every absolute number (reconfirmed 2026-11-01 03:00)

The same release, the same flags, three hours apart: rc.23 measured warm P50 2.449 ms at 00:00 and 5.884 ms at
03:00; rc.24 measured 5.79 ms beside it, and enabling or disabling the new retire-recording guard moved the
number by 0.05 ms. Celeritas in the same window stayed at 1.72-1.75 ms. So the frame time is the machine's
state, not a regression - and a ratio taken without stating the state is worthless. A "resource packs double
Celtium" reading earlier this night was this same effect: with and without the packs the P50 differed by 0.02 ms,
and the seed-options file differs from the instance's options only in guiScale.

Practical rule: a Celtium A/B must alternate arms within minutes, and a ratio is only quotable with the pairs'
absolute numbers beside it (as the table above does).
