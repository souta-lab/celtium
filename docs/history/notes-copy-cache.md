# The texture-copy cache contract: does every content change move the key?

Task-73. Read-only audit of `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TextureCopyService.java` and the
paths that can serve stale pixels. No Java was edited for this note and no leg was run.

## What changed and why

The cache marker was `lastDrawnEpoch() * 1000003 + lastDrawnFrame()`; it now also folds in
`immediateWindowsThisFrame()` (`TextureCopyService.java:383-384`):

```java
long copyMarker = (this.host.lastDrawnEpoch() * 1000003L + this.host.lastDrawnFrame()) * 131071L
        + this.host.immediateWindowsThisFrame();
```

Measured on the main menu: `textureCopyReadbacks=216` over 216 presents with `textureCopiesServed=1512` - seven copies
served per present, one readback. Vanilla's `GuiMainMenu.rotateAndBlurSkybox` runs seven iterations, each blurring what
the previous one copied; six of the seven were answered from the first iteration's pixels because the blur quads are
immediate-window draws and did not move the epoch. The menu background was therefore one blur pass instead of seven.

## 1. Readers of the cache state

| site | line | verdict |
|---|---|---|
| `textureCopiesServedThisFrame` map | `:176` | correct - the only cache store |
| `copyKey` = dest/level/offset/rect | `:366` | correct as an identity of the *request*; it is not a content version, which is why the marker carries the content side |
| marker (epoch, frame, immediate windows) | `:383-384` | correct for the measured defect: any immediate window between two copies makes the second a miss |
| frame rollover: `textureCopyPresent != presentSerial()` then `clear()` | `:174`, `:388-392` | correct - the map cannot outlive a present |
| hit test `servedAt == copyMarker` | `:399-400` | correct; a hit is only taken when the whole marker matches |
| put on the host route | `:440` | correct |
| put on the native route | `:770` | correct |
| `windowsThisFrame` in the two dump lines | `:626`, `:641` | diagnostic only |

**Can the new marker miss unnecessarily?** Yes, in one direction and it is the intended one: *any* immediate window
between two copies of the same rectangle now forces a miss, even when that window did not draw into the copied region
or into the destination mirror. On the menu that is exactly right (the blur quads write the viewport the next copy
reads). On scenes where an immediate window runs between two identical copies of an unrelated rectangle, the marker
costs one extra readback per occurrence. I did not enumerate those scenes; the marker has no notion of *which* image a
window wrote, so the note records the shape rather than a scene list.

## 2. Other paths that can serve stale pixels

| path | site | its own "same request" test | verdict |
|---|---|---|---|
| `VulkanTextureRegistry.announceFullPixels` | `:914` | none observed: it applies the pixels it is given | correct (no cache) |
| announcement retention (survives delete-and-reallocate) | `:1436`, `:1456` | `announcementUnconsumed(id)` - a *consumed* announcement is dropped, an unconsumed one survives | **stale possible in principle**: the rule deliberately keeps the newest unconsumed announcement for a name; a name that is deleted and reused for different content while an announcement is unconsumed keeps the old pixels. The comment at `:1450-1458` states this is the game's own newest statement about that name, so it is a contract choice, not an oversight - but it is the same shape as the copy cache's defect |
| fill counters `filledFromAnnouncement` | `:95`, `:1070`, `:1165` | none - census | correct |
| patch path (`announcePartial`/`announceFullPixels` variants) | `:914` and the patch entry points | none observed | correct (no cache) |
| `PublishedFrameTextures.colorTextures` | `:54` (`get(identity)`) | keyed by framebuffer identity, overwritten on each publish | correct - identity is the key and a republish replaces the entry |
| native blit route | `TextureCopyService.java:363-400` | the same marker gates it (`:383`), and `blitRefusal` checks the rectangle | correct after the marker change; the ordering guard is the flush at `:373-375` |
| `VulkanGlTexture` level caching (captured levels, `uploadCapturedLevels`) | not audited in this pass | - | **not established** |

## 3. Cost of the change

- Vanilla's loop: **7 copies per menu frame** (measured: 1512 copies / 216 presents).
- Before the change: 1 readback per present (`textureCopyReadbacks=216` over 216 presents).
- After the change: up to **7 readbacks per menu frame** on the host route, because six of the seven copies now miss.
- The host route pays, per readback, a full command buffer plus a fence wait (the route's own comment and the
  `textureCopyReadbackNanos` counter are the measurement).

So the change buys correctness with readbacks on the route that is not the target. **What the native-copy path has to
restore**: the blit must key on the same content version so the seven iterations re-blit on the GPU (cheap, no host
round trip) instead of forcing the host route - i.e. `blitInto` needs the marker (or an equivalent content version) to
move for the blur windows, and the host route should stay the fallback. The target to write down: **1 readback per
present with 7 blits per menu frame** (`textureCopyBlits` rising, `textureCopyReadbacks` flat).

## What is not established

- Which scenes (other than the menu) pay an extra readback for an unrelated immediate window.
- Whether the patch/`announcePartial` path has a hidden reuse test further down (only the entry point was read).
- `VulkanGlTexture`'s level caching (captured levels and their reuse across uploads) - not audited.
- Whether the retention rule for unconsumed announcements can in practice serve pixels for a reused name (the
  contract says it may; no measured case was produced here).
