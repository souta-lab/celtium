# World exit and real-settings reproduction (2026-10-24)

`delete()` destroyed shadow/records/model pipelines, indirect/record/model buffers and the texture-array descriptor
pool **before** its stalled-GPU guard and completion wait. The gate now precedes every destruction operation.
[Normal transition evidence](native-retirement-2026-10-24.json): renderer
`fcfb581f9667989ce3bf75dcffb2b96e0322d252862c3b3a562d8f54be8dfda0`, fixture
`32d40dc473c696dd6acd0032662f8f4f19ed674455bb272a42959206d34bbc5a`, validation ON, qmagnet→menu→same qmagnet.
AMD/X11, Intel/X11 (1280×720), AMD/native Wayland (1920×1080/Unicode): gates PASSED, validation0.
These are normal transitions, not an injected in-flight destruction reproduction.

The real settings add an important condition the earlier legs missed: the source options are Japanese at startup,
whereas forcing Unicode after initialization leaves a different texture-name allocation order. The runner can now
seed the actual options and select native Wayland, with source hashes and the requested platform recorded.
[Real-options measurements](user-options-2026-10-24.json), AMD RX6400/native Wayland/1920×1080/distance18,
camera from the source save (230.26729/64.05453/230.25113, yaw-190.36536/pitch47.39999), validation/readback/periodic dumps OFF:

| renderer | warm ticks400..1000 p50/p95/p99 ms | samples | texture-bind log lines / total log bytes |
|---|---|---|---|
| old `fa32e6e98eb23d6b59c7d54fb0c360c450fc00569f13dc9ebfb2e7e7c3da159e` | 3.367621 / 4.765422 / 5.625833 | 8298 | 703428 / 141383556 |
| fixed `fcfb581f9667989ce3bf75dcffb2b96e0322d252862c3b3a562d8f54be8dfda0` | 1.644496 / 2.247954 / 3.375652 | 17118 | 96 / 870132 |

Same fixture and source-options hash; JFR delay28s/duration30s in both. One sequential pair, not a guarded,
repeated KPI or Celeritas comparison. Both image gates are UNCALIBRATED because the camera differs from the
calibrated view. Actual desktop/Prism console-reader behavior and sustained ~20FPS remain unreplicated.

The Japanese world-exit leg `native-retirement-ja-menu` **FAILED/timeout**. A live thread dump captured the client
waiting in `Futures.getUnchecked` from `IntegratedServer.initiateShutdown`, with no Server thread remaining.
The scheduled cleanup could not run after the server loop had exited. This is distinct from the Vulkan deletion
order and does not prove the reported blank buttons had either cause.

The shutdown awaiter now polls the future while checking vanilla's `isServerStopped`, set after
`stopServer`/`handleServerStopped`. Once the consumer has stopped, an unfinished task is cancelled without
interrupting it. Completed results, failures and the caller's interruption state retain their contracts.
The first implementation referenced `getServerThread`, which exists in the build-time tree but not on the actual
Cleanroom 0.7 runtime: **FAILED with NoSuchMethodError**, retained in [final evidence](shutdown-await-2026-10-24.json).
The final version uses the existing completion flag instead. Runtime Mixin export proves the redirect is installed.

Final renderer `4049f684cedc7c99bab9b83f920750ec727505e2ebf57f0c5770d7762a1d13b4`:
Japanese source-options/native Wayland/AMD/1920×1080 and X11/Intel/1280×720 both passed qmagnet→menu→same qmagnet
with validation ON and zero messages. [Returned Japanese menu](shutdown-await-ja-menu-2026-10-24.png) contains all
buttons and labels. The separate reported missing-buttons rendering symptom remains unreplicated.
