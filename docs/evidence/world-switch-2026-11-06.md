# World switch on world-accept: a fixture stall and an acquire-semaphore reuse (2026-11-06)

`-Dceltium.validation.worldSwitch=true` leaves the world at tick 100 and should rejoin from the title screen.

## Observed (AMD RX 6400, world-accept, guiScale 3, validation on)

| leg | renderer | result |
|---|---|---|
| `l8switch-amd` | `c3102913…` (r98-diag, today's teardown releases) | MISSING_REPORT; worldTicks stuck at 100 for ~5 min; 10x `VUID-vkAcquireNextImageKHR-semaphore-01779` |
| `l11switch-r85` | `3d4fd4fe…` (r85-stage, before today's changes) | the same stall at tick 100 and the same 10x 01779 |

So neither is caused by the record-twin / mod-pipeline teardown releases (295b3112b, b8256300c).

1. **The stall is the fixture's.** The rejoin waited for `mc.currentScreen instanceof GuiMainMenu`, but the
   world-accept instance runs CustomMainMenu, whose title screen is `lumien.custommainmenu.gui.GuiCustom`
   (`CELTIUM_FIXTURE_TICK … screen=lumien.custommainmenu.gui.GuiCustom`). Fixed in 5be34a974.
2. **The semaphore reuse is the renderer's.** Acquire semaphores were handed out round-robin, one per swapchain
   image; with the title screen running slowly (readback on, ~50 ms presents), a semaphore came back while the
   submission waiting on it had not executed. Fixed in 4e8ddde32: a semaphore is reused only after the image that holds
   it is acquired again (that acquire means the image's previous present - which followed the waiting submission -
   was consumed), and the pool grows by one when all are owned. Pure logic held by `AcquireSemaphoreOwnershipTest`.

## Check

`l12switch-amd` (renderer `82f20d80…` = 4e8ddde32, fixture `97685d8d…`, AMD, world-accept, validation on): **PASSED,
validation 0** - `leftWorldAtTick=100`, `rejoinedAtTick=100` into the save `qmagnet`, no 01779. The device contract
line reports `hostImageCopyAvailable=true` on the RX 6400 (RADV). Intel: `l14switch-intel` queued.
