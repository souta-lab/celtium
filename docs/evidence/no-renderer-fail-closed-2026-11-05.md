# A renderer that cannot start ends the game with a report, from any loop (2026-11-05)

Condition: the Vulkan renderer cannot be created (here: validation requested with an lwjgl-vulkan 3.4.1 binding, so
`VkDebugUtilsMessengerCallbackEXTI` is missing), on the `cleanroom-0.7.11-alpha` instance whose `qmagnet` save was
written with Xaero's minimap, so the integrated server stops on Forge's missing-registry startup query.

| leg | build | attempts logged | outcome |
|---|---|---|---|
| `r86-norenderer` | `1abdadae…` | 2 | refused frames until the 200 s limit, no report |
| `r87-norenderer` | + retry check at the swap point | 8, then the throw | the fixture's tick handler caught it; game kept running to the limit |
| `r88-norenderer` | + fixture rethrows renderer-start failures | 8 | hung after the throw, no report |
| `r89-norenderer` | + `displayCrashReport` before the throw | 8 | **exit after 24 s**, `crash-…-client.txt` "Celtium could not start its Vulkan renderer" naming the binding |

Why each step was needed:
- The bounded retry-then-throw ran only at `Minecraft.runTick` HEAD. The startup query's loop (inside
  `launchIntegratedServer` via `LoadingScreenRenderer`) draws frames without ticking, so the check never ran again.
  It now also runs at the swap point (`func_175601_h` HEAD), outside the present's own catch.
- Thrown from there, the main loop's crash handling unloads the world first, which stops the integrated server and
  waits for it - and the server is parked on the startup query. `displayCrashReport` saves the report and exits through
  Forge's `handleExit` without touching the server; the throw stays as the fallback.
