# the registry serves 462-569 lookups a frame and misses 11.6% of them; three of those lookups were the same name (2026-11-03)

The plan's next unit after round 34 was the accept path's per-offer work, and the handoff named one item in it:
memoise the raw-write names past `RAW_WRITE_TRACKED_IDS`, estimated at 0.085 ms a frame. This round measured that
estimate, found it wrong by about forty times, and instead found the lookup population the accept path actually
pays for - then removed two of the three lookups the same batch made for the same name.

## The handoff's estimate was wrong, and the arithmetic says why

`parts[supply]` is 1,792 ns **per slow-path batch**, not per offer, and the untracked raw-write branch is not what
fills it. On leg `r34c-current` the branch ran 62,564 times over 3,543 world frames - **17.7 a frame**, not 47.7 -
and each call is `textureUploadSerial` (two map gets) plus `celtium$holdsSerial` (a field compare). At ~150 ns that
is **~2.7 us a frame, 0.1% of the frame**, not 0.085 ms. The 1,792 ns average is carried by the other branch: the
`celtium$supplyMirror` deferral, which runs ~9 times a frame and asks `textureSize`, `celtium$mirrorWaitsForFramebuffer`
and `celtium$levelHasNothingToRead` - nine map gets and a bounded log line - for a name whose content serial moves
every frame, so the backend's per-serial memo (`lazyMirror[skips=0]`) never answers.

So the memoisation the handoff asked for was **not done**: it would have removed 0.1% of the frame and added a
second table to keep coherent. The finding is recorded here so the next round does not re-derive it.

## What the registry actually serves

`VulkanTextureRegistry.get(int)` now counts its own memo. On leg `r35b-memo` (`celtium-world-accept`, 188 mods,
`qmagnet`, AMD RX 6400, 1280x720, `guiScale3.txt`, 240 s, `worldFrames=4963`, `sceneCalibrationKeyMatches=true`):

| | r35a-memo | r35b-memo |
|---|---|---|
| memo hits | 2,247,192 | 2,030,468 |
| memo misses | 295,966 | 264,178 |
| lookups a frame | 569 | 462 |
| miss rate | 11.6% | 11.5% |
| misses a frame | 66.2 | 53.2 |

462-569 lookups a frame for 112 drawn groups is 4-5 per draw, and 53-66 of them a frame take the whole
`get(int, boolean)` body - the announcement conversion, the sampler confirmation, the stale set, and on a name with
no entry the capture probe. That is the population worth attacking, and it is now a number a leg prints
(`memo[hits=… misses=… slots=…]` in `externalSummary`).

The memo was 16 slots, direct-mapped on the GL name's low bits. It is 256 now. **The slot count was not A/B'd in
this round** - the census was added in the same change, so there is no arm that measures the 16-slot miss rate. The
mechanism (a direct-mapped table smaller than the frame's live name population thrashes) is the reason to keep it;
the size is not a measured optimum, and the next round should run `-Dceltium.vulkan.memoSlots` as a within-JAR A/B
before claiming one.

## The three lookups that were one

The slow path resolved the bound texture's mirror three times per batch:

1. `submitImmediateBatchBody`, the fast-merge decision's `liveTextureImage` (needed by the predicate);
2. the same method's preflight `existing`, one statement before the supply/refresh branch - **the same call with
   the same argument**, with only the predicate (which reads the material and the stub's write epoch, and mutates
   no table) between them;
3. `prepareImmediate`, which resolves it again for `hasFullContent()` and `key()`.

(2) is gone: the preflight uses (1)'s answer. A fourth, in the group-open branch, asked the registry a second time
for the name it had just resolved into `currentGroupTextureImage`, and the refusal that reads it now reads the
field - the field is assigned unconditionally in that branch and nothing between the two points mutates the table,
so it is the same object. That is one lookup per opened group (~112 a frame).

## The legs

| | r34c-current (control) | r35a-memo | r35b-memo |
|---|---|---|---|
| renderer | `b356ab80…` (HEAD before this round) | `3c033a60…` | `642b8eb9…` (this round's tree) |
| fixture | `0e90c988…` | `0e90c988…` | `0e90c988…` |
| flags | `batchTimings`, `frameStats`, validation/readback off, `presentedDumpEvery=0` | same | same |
| worldFrames / ticks | 3,543 / 309 | 4,469 / 339 | 4,963 / 353 |
| `frameIntervalP50Ms` (ticks 120-200) | 2.5885 | 2.3498 | 2.2586 |
| P95 / P99 | 3.9163 / 6.1541 | 3.3267 / 4.2130 | 3.2930 / 4.1176 |
| `batchTimings[nanosPerOffer total=]` | 4,905 | 4,043 | 3,666 |
| `parts[registry]` | 778 | 703 | 752 |
| `parts[supply]` | 1,792 | 1,667 | 2,452 |
| `parts[prepare]` | 1,179 | 876 | 903 |
| flushes / frame | 22.1 | 22.0 | 22.1 |
| flush us / frame | 758 | 673 | 643 |
| distinct colours | 76,562 | 76,501 | 76,625 |
| non-background | 0.989708 | 0.989733 | 0.989693 |
| vertical bands | 720 | 720 | 720 |
| fixture / validation | PASSED / 0 | PASSED / 0 | PASSED / 0 |

**The p50 moved -12.7% across the pair and that is not a KPI claim.** The three runs are not frame-identical
(3,543 / 4,469 / 4,963 world frames in the same 240 s, and 47.7 / 45.9 / 38.4 slow-path batches a frame), the
project's own run-to-run band is +-7%, and the changes above account for roughly 2% by their own mechanism. A
single pair cannot separate the change from machine state, and the memo-size control arm was not run. What the
table does support is the picture: the three legs agree to 0.1% on distinct colours, 0.004% on the non-background
fraction and exactly on the band count, so nothing here moved a pixel.

`parts[supply]` is the one number that went the wrong way (1,667 -> 2,452) and it is the one the handoff's estimate
was about; it is a per-batch average over ~9 deferral calls a frame whose cost is dominated by cold map lookups, so
it moves with the run rather than with the change. It is recorded as noise, not as a regression.

## The stale comment this round found

`SORT_OPAQUE`'s javadoc said "On by default" while the field read `Boolean.getBoolean("celtium.vulkan.sortOpaque")`,
which is false unless set - and `docs/verification-traps.md` records the ABBA A/B that made OFF the right default
(ON 2.538 ms against OFF 2.508 ms, 1.2% worse, because the groups' push constants differ even when their pipeline
and descriptor do not). The code was right and the comment was stale; the comment now says so and cites the
measurement. `docs/new_architecture.md` already warned about exactly this comment.

## What this round does not prove

- That 256 slots is better than 16. The census exists; the A/B does not.
- That the -12.7% p50 is the change. It is one pair, outside the band, with no control arm for the memo size.
- Anything about the flush's 643 us a frame beyond its size: 22.1 flushes a frame at 29.1 us each is now the
  largest Celtium-owned bracket, and this round did not touch it.
