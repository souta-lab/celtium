# The device/capability contract: B02, B03, B04 (task-30) — 2026-10-11 17:1x

Three P1 items from the user's full audit (`docs/history/2026-10-11-full-audit-1554.md` §B02–B04), all in the
"what does the device and the lifetime actually guarantee" family. Each fix is in the tree and measured by a
GL-less run; the numbers are below with the run names.

## B02 — the GL-less path no longer requires or enables the fd-sharing extensions

**Was:** `VulkanContext` required `VK_KHR_external_memory_fd` and `VK_KHR_external_semaphore_fd` of the matched
device and enabled both unconditionally, and the check ran *after* the candidate was chosen, so a device
without them was selected and then refused. Under GL-less there is no GL image or semaphore on the other end
of a fd, so those extensions were a leftover of the retired interop experiment that excluded otherwise valid
devices and tied the path to Linux fd semantics.

**Now:** the contract depends on the configuration. `interopPath` is `openGl != null` (a GL identity exists to
share with), set in `initialize` before matching. On the interop path the two extensions are still required,
and the check moved *into* the candidate loop, so a device that lacks them is passed over and named in the
failure (`skippedForInteropExtensions`). On the native path they are neither required nor enabled. The swap
chain extension is enabled when present-from-composite is on, as before.

**Evidence** (`t30-final-keys`, GL-less, AMD RX 6400, jar `609eb69a…`-era build, fd extensions hidden from
enumeration with `-Dceltium.vulkan.hideExternalFdExtensions=true` — a test-only switch that removes them from
the advertised set so the "device without them" profile can be exercised on a device that has them):

```
CELTIUM_DEVICE_CONTRACT name="AMD Radeon RX 6400 (RADV NAVI24)" uuid=00000000030000000000000000000000
  deviceType=2 apiVersion=1.4 queueFamily=0
  required=[api>=1.2, queue=graphics+transfer, VK_KHR_swapchain, color=R8G8B8A8_UNORM
            depth=D32_SFLOAT(0x7e) samples=1]
  fdInterop=false enabledDeviceExtensions=[VK_KHR_swapchain]
  limits=[maxPushConstantsSize=256 immediatePushBytes=208 maxImageDimension2D=16384]
  advertised=[externalMemoryFd=false externalSemaphoreFd=false swapchain=true]
```

The leg reached a native picture (`pictureEvidenceSource=presented-dump`, `picturePresent=true`,
`nativeDeviceName="AMD Radeon RX 6400 (RADV NAVI24)"`, `gpuRequirementMet` n/a on this leg) with the two
extensions absent from the advertised set, from `required`, and from `enabledDeviceExtensions`. The same
scene without the switch (`t30-final-plain`) reports `advertised=[externalMemoryFd=true
externalSemaphoreFd=true swapchain=true]` with the same `required` and `enabledDeviceExtensions` lists: a
device that has them is not asked for them either.

The contract line is printed once the queue family is known and with the depth format resolved
(`depthFormat.chosen(device)` at that point), so it names the queue (`queueFamily=0`), the formats, the API
version and the push-constant limit the renderer actually spends (`immediatePushBytes=208` against the
device's `maxPushConstantsSize=256`) rather than the retired interop extensions.

**Not verified:** the *interop* direction on a device that lacks the extensions (a candidate would be skipped
with its name in the error) is verified by the code path and the skips list only — the bridge is retired and
no run was taken with a GL identity, where `openGl != null` and the extensions are required.

## B03 — the descriptor pools grow and the exhaustion is counted

**Was:** `VulkanImmediateTexturePipeline` created one pool of `maxSets=64` with `descriptorCount=64`, reserved
one set at creation, and `descriptorSetFor` cached one set per texture key for the pipeline's whole lifetime
with no eviction. So the 63rd distinct key was the last drawable one; after that `vkAllocateDescriptorSets`
failed, the method returned 0, and the batch was refused — and under GL-less a refused batch is drawn by
nobody. The ITEM pipeline had `maxSets(8192)` *with the same* `descriptorCount(64)`, so it had the same
64-set bound with a larger-looking constant hiding it.

**Now:** both pipelines keep a list of pools and `allocateDescriptorSet` grows a new
`DESCRIPTOR_SETS_PER_POOL`-set pool when the newest one is full, with a `DESCRIPTOR_POOL_LIMIT` cap; `delete()`
destroys every pool. Counters exist per family and aggregate in one summary
(`CELTIUM_IMMEDIATE_DESCRIPTORS`, and `immediateDescriptors=[…]` in the run report):
`pools`, `growths`, `sets`, `keysCached`, `failures`, `hits`, `misses`. A growth prints one bounded line.
`-Dceltium.vulkan.descriptorPoolSets=<n>` (1..64) lowers the pool size so the growth path is reachable by a
scene that does not hold 64 distinct keys; the mechanism, the counters and the no-refusal property are the
same, only the threshold moves, and the run that uses it names the property here and in its report.

**Evidence:** the `t30-*` legs run the heaviest texture scene the fixture has (JEI/inventory screen + the
entity set + a full resource reload at tick 100) with `descriptorPoolSets=8`. See the "measured" section below
for the counters from the leg that carries these fields.

## B04 — a wait that did not confirm completion never releases what the queue may own

**Was:** after a successful `vkQueueSubmit`, a `VK_TIMEOUT` from `vkWaitForFences` quarantined the command
buffer and fence and marked the context unusable (the earlier A02 fix), but any other non-success result
(`OUT_OF_HOST_MEMORY`, `OUT_OF_DEVICE_MEMORY`, `UNKNOWN`) fell into `check(waited)` → throw → the `finally`
destroyed the fence and freed the command buffer while the queue might still own them. The bootstrap
`endCommands` path had the same shape after its queue wait, and `delete()` called `vkDeviceWaitIdle` without
checking the result before releasing the quarantined resources.

**Now:** four named states, one per outcome, with the cleanup that each one justifies:

| outcome | state | what happens |
|---|---|---|
| `VK_SUCCESS` | completion-confirmed | the submission completed; the fence is destroyed and the buffer freed |
| `VK_TIMEOUT` | quarantined | buffer and fence held, context marked unusable, named failure |
| `VK_ERROR_DEVICE_LOST` | lost-device | **released**: a lost device is SUCCESS-equivalent for pending/in-use purposes ([R4]); the context is marked unusable and the failure names the state |
| anything else (`OUT_OF_HOST_MEMORY`, `OUT_OF_DEVICE_MEMORY`, `UNKNOWN`) | unconfirmed | buffer and fence **quarantined**, context unusable, named failure saying it is not a lost device |

The bootstrap path (`endCommands`) now checks `vkQueueWaitIdle`: `SUCCESS` and `DEVICE_LOST` free the buffer,
anything else quarantines it (with no fence, since that path submits without one). `delete()` checks
`vkDeviceWaitIdle` and only releases the quarantined buffers and fences when that wait confirms idle or the
device is lost; otherwise it reports the count it deliberately held instead of freeing them.

The matrix is reported as `waitOutcomes=[completions=… timeouts=… lostDevices=… unconfirmed=…
queueWaitFailures=… deviceIdleFailures=…]` plus `quarantined=[buffers=… fences=…]` in the run report and in
`CELTIUM_WAIT_CENSUS` at teardown.

**Not verified:** no OOM/UNKNOWN or DEVICE_LOST wait has been *injected* on a real device; the state machine is
verified by the code path and by the counters being live (`completions` non-zero, `unconfirmed=0`,
`lostDevices=0`) in the runs below. Fault injection for those three outcomes is the next step if the audit
wants them exercised end to end.

## Measured (fill from the runs)

- `t30-count-keys` / `t30-final-keys` / `t30-final-plain`: GL-less, AMD RX 6400, scene = JEI + entities +
  reload, `descriptorPoolSets=8`, `hideExternalFdExtensions=true` on the first two.
- Fixture verdict, histogram, refusal census, VUID count and `validationMessageCount` are quoted in the Lead's
  report from the same runs.
