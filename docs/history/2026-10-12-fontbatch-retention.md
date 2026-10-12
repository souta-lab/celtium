# FontBatch: the retention fix and the target witness, with their CPU evidence

Owner: **glyph-fix**. `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java` (R3b, first half) and
`scripts/cpu-contract/FontBatchContractCheck.java`. No Gradle build and no leg were run for this unit; the wiring pass
and the single build belong to the Lead.

## The gap this closes

`celtium$flushPendingFontBatch` marked the run inactive and zeroed its bytes **before** it called
`submitImmediateBatch` (`VulkanTerrainGpuBackend.java:5040-5042`), so a `false` return or a thrown submission only set
`immediateLastRefusal` (`:5064-5066`) and the glyphs were gone. The re-queue that exists is **group-level** - the
immediate window's abort path puts `recordedImmediateGroups` back at the front (`:6138-6150`, and `:847`) - which does
not cover the run's own pending bytes. So "R3b keeps the behaviour" and "a refused run does not lose its glyphs" could
not both be true, and the Lead took the retention as the fix.

## What FontBatch does now

* **Retention, default ON.** A run the sink refuses or that throws is copied out of the pending buffer into a
  per-target FIFO queue and offered again at the next flush, when its own target is current. Bounded at 64 runs / 4 MiB;
  beyond the bound the drop is counted. Retained runs are offered **before** the open one, so older glyphs cannot be
  drawn after newer ones, and a run refused in a flush is **not** offered twice in that same flush (the first version
  did, measured as two refusals for one flush).
* **`-Dceltium.font.retaining=false` is a diagnostic control only** - the old drop-and-count behaviour, the same shape
  as `-Dceltium.vulkan.nativeTextureCopy=false`. Correctness does not depend on it.
* **The target identity is part of the run key.** The key today has no target (`:4982-4987`); the guard at `:4875` only
  prevents *starting* a run on a non-main target, so a run could span a switch and be drawn into another target's pass -
  the plan's section 5.4 hole. This is the one intentional behaviour change in the unit.
* **The verification string is untouched**: the retention counters live in `celtium$fontRetentionSummary()`, and
  `celtium$fontBatchSummary()` keeps its exact field names and order, which the check pins.

## The exercise state, stated plainly

**The retention is implemented and unexercised until a leg actually refuses a run.** No leg has been run for this unit
and no counter has fired outside the CPU check. `retainedRuns=0 retainedPendingRuns=0 retainedDroppedRuns=0
retainedControlDrops=0` on any leg that never refuses a run is the expected reading, not evidence of a fix in
production - the CPU check below is what exercises it, and a leg that refuses is what would show it in a run.

## CPU evidence (no GPU, no game, no Gradle build)

```sh
LWJGL=$(find ~/.gradle/caches -name 'lwjgl-3.4.1.jar' | head -1)
NATIVES=$(find ~/.gradle/caches -name 'lwjgl-3.4.1-natives-linux.jar' | head -1)
GL=$(find ~/.gradle/caches -name 'lwjgl-opengl-3.4.1.jar' | head -1)
javac -nowarn -cp "$LWJGL:$GL" -d /tmp/fontbatch-check \
    scripts/cpu-contract/FontBatchContractCheck.java celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java
java -cp "/tmp/fontbatch-check:$LWJGL:$GL:$NATIVES" FontBatchContractCheck                      # retention on
java -Dceltium.font.retaining=false -cp "/tmp/fontbatch-check:$LWJGL:$GL:$NATIVES" FontBatchContractCheck
```

**Retention on (default): `30 checks, 0 failed`, exit 0.** The target witness, measured:

```
ok   append for target 1                           measured=1 expected=1
ok   append for target 2 is taken                  measured=1 expected=1
ok   the target switch submitted the target-1 run  measured=3 expected=3
ok   that run says target 1                        measured=1 expected=1
ok   the target-2 run goes out separately          measured=4 expected=4
ok   that run says target 2                        measured=2 expected=2
ok   retained runs wait for their own target       measured=2 expected=2
ok   both retained runs are offered again          measured=4 expected=4
ok   the oldest retained run goes first            measured=retained expected=retained
ok   retained bytes survived the buffer reuse      measured=<checksum> expected=<checksum>
ok   summary keys are unchanged and in order       measured=[fontRunsStarted, fontQuadsAppended,
     fontStripSeparators, flushedIntervening, flushedBoundary, flushedStateChange, appendMillis] (same)
ok   retention counters stay out of that string    measured=false expected=false
```

The buffer-reuse check is the one that matters for the fix: after the refusal the source buffer is overwritten with
other bytes, and the run the sink receives on the retry still carries the original vertices' checksum - a retained run
that aliased the pending buffer would fail it.

**Control (`-Dceltium.font.retaining=false`): `24 checks, 0 failed`, exit 0**, with
`the control dropped the run measured=0 expected=0` and `the control counted the drop measured=1 expected=1` - i.e.
the flag reproduces the old behaviour and says how much it lost.

## Not established

* That a leg ever refuses a font run, and therefore that the retention fires in a game: unmeasured here.
* That the target witness changes any picture: the CPU check proves the key now differs across a target switch; whether
  a real run spanned targets before is not measured (the counters that would show it are the flush reasons, and a run
  spanning a switch would have been flushed as `state-change` only if some other witness also changed).
