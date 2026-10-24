# Native loading progress and real-play diagnostics (2026-10-24)

Renderer SHA256 `2a78cd54727cad1804dc424eb69e4ad36817c081cee22cf1496e2639c23d7b5e`.
Fixture SHA256 `16c2eb7caef5637bdb657f9a96365b766b5b43cdc507c060304f47f3b029cd4c`.
Full commands, GPU identities, camera, windows and picture identities: [JSON](native-loading-2026-10-24.json).

- Forge's `ProgressManager` supplies its actual title/message/step/total. A client-thread refresh (at most 10 Hz)
  sends these to a Vulkan-owned loading canvas/texture/present path. No GL context, GL state, GL texture name or
  shared splash thread is used by this UI. The image is released after confirmed GPU completion at loader finish.
  The former elapsed-time bar was drawn after presentation, did not show Forge progress, and was removed.
  [Presented loading image](native-loading-loading-2026-10-24.png) shows `Constructing Mods 1/7`.
- The real Japanese-profile log, renderer `fa32e6e98eb23d6b59c7d54fb0c360c450fc00569f13dc9ebfb2e7e7c3da159e`,
  contained **226,648 CELTIUM_UNIT_BIND lines** (46,158,811 bytes total log). When IDs 40–60 bound repeatedly, the
  general budget stayed below its limit and bypassed the range budget indefinitely. The budgets now apply to
  disjoint cases; the new legs each emitted 96 bind lines. This proves bounded diagnostics, not an FPS improvement.
- `native-loading-20261024c` (AMD RX 6400) and `native-loading-intel` (UHD 630): qmagnet → menu → **same qmagnet**,
  1280×720, camera 217.5/48/217.5, yaw180/pitch10, distance18, worldTime1500, validation ON.
  Both calibrated world gates PASSED and logs contain zero VUIDs. Menu buttons are visible in the retained images.
- The former worldSwitch fixture rejoined `celtium-validation` instead of the initial save, so the earlier
  `worldswitch-20261024a` and `native-loading-20261024b` final-world gates do **not** establish qmagnet re-entry.
  The initial menu observations remain observations of returning from qmagnet. The corrected fixture reports
  `saveSlot=qmagnet`, `rejoinedSaveSlot=qmagnet`, and the actual render distance (the old field was hard-coded 8).
- `native-loading-hires-amd`: 1920×1080, GUI scale3, Unicode font, distance18, same camera/re-entry, validation OFF,
  presentReadback OFF, periodic presented dumps OFF. Warm ticks400..900: **22,028 samples; p50 1.060573ms,
  p95 1.392063ms, p99 2.619851ms**, upload fence waits0. JFR was enabled (JVM uptime30..65s).
  This is one stationary run with profiling, not a paired speed comparison. It does **not** reproduce or resolve
  the user's ~20 FPS during real play. [1920×1080 returned menu](native-loading-hires-menu-2026-10-24.png)
  contains all buttons; actual Japanese labels and motion remain unverified.

Build and 140 Vulkan CPU tests passed; 69 Python tests passed; packaging verifier and docs/index checks passed.
CPU tests do not prove graphics or throughput. Shadows, native frame compilation and the reported real-play lag
remain incomplete; this change does not establish the full product goal.

Four screen evidence: returned menu above; [video settings](native-loading-video-2026-10-24.png) gate PASSED;
[HEI inventory](native-loading-inventory-2026-10-24.png) and [recipe](native-loading-recipe-2026-10-24.png)
were visually inspected with zero VUIDs, but their gates are UNCALIBRATED (exit2), **not accepted**.
The same renderer/fixture hashes were used for all six legs.

The follow-up [moving-view replay](camera-sweep-2026-10-24.json) uses the same renderer, a new fixture
`165e6fd56ea22c70c720d1a26b3c6769ad5b1ca8e3574b26242a2ea889d31ad4`, 1920×1080/distance18/Unicode,
and 24 degrees per second from tick400 (two full turns). Warm ticks400..1000: 12,350 samples,
p50 2.085024ms / p95 4.292191ms / p99 5.615022ms. [Actual moving view](camera-sweep-2026-10-24.png).
The primary gate covers the earlier fixed view; this diagnostic moving image is not calibrated.
Two retained warm outliers are tick463=364ms (unattributed) and tick503=314ms (the requested image dump overlaps).
The user's sustained ~20 FPS remains unreplicated; translation is still unverified.
