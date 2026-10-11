# task-59 — F3 is very laggy: the measured cost, and the order-safe batching design

Date: 2026-10-11. Owner: gui-stage. Status: **diagnosis complete, batching not yet implemented.** This file is written
so the implementation is a transcription rather than a re-derivation.

## 1. The cost, attributed from the code and the user's own numbers

User's session (`logs/latest.log`, 20:05:06-20:33:14):

```
CELTIUM_FONT_GLYPH_SUMMARY windows=1012751 defaultWindows=1012751 unicodeWindows=0 bridgeWindows=0
  quads=1012751 submitted=1012751 accepted=873822 refused=138929
  submitMillis=47246 reasons={backend=138929}
```

- **46.6 microseconds per glyph**, and it is `submitMillis` alone: `47,246 ms / 1,012,751 submitted`. That is time spent
  **inside `backend.submitImmediateBatch`**, not window creation and not vertex writing.
- `windows == quads == submitted == 1,012,751` — **one window, one quad, one backend call per glyph.** The path is
  `FontRendererMixin` → `openWindow` per `renderDefaultChar`/`renderUnicodeChar` → `GlStateManagerImmediateMixin`
  (`glBegin`/`glTexCoord2f`/`glVertex3f`/`glEnd`) → `CeltiumFontGlyphCapture.submit()` → one
  `submitImmediateBatch(POSITION_TEX_COLOR, 24, 2, address, 4, GL_QUADS)`.
- What that call costs, per `VulkanTerrainGpuBackend.submitImmediateBatchTimed`: the state capture, the state key, the
  texture-registry lookup, `prepareImmediate` (pipeline cache lookup + descriptor set), the group-key comparison, the
  group creation, and the mesh append — **all of it recomputed for four vertices**. The per-call constant is the cost,
  which is why batching divides it by the batch size and why nothing about the vertices has to change.
- **13.7 % refused, `reasons={backend=138929}`** — refused by `backend.lastRefusal()`, i.e. the backend's own preflight,
  not the capture's local checks (`refuse("no-backend")`, `"submit-threw"`, `"address-extent"` would each appear under
  their own reason). Under GL-less a refused quad is drawn by nobody. **The exact refusal string still has to be read
  out of the run** — `refuse("backend", …)` records `backend.lastRefusal()` verbatim and prints the first of each
  reason, so one F3-on leg names it; it was not available in the user's summary line, which carries only the map key.
- Also worth measuring before widening anything: the same per-call cost is paid by **every** immediate batch, and
  `play48b` reports `immediateFlushes=982` per frame in normal play. If consecutive same-state batches can be coalesced
  by the same pending-batch mechanism, that may be the larger win. **Measure before touching anything outside the font
  path.**

## 2. The design, corrected by the Lead's review

**The naive version is wrong, and the reason matters.** Holding glyph quads in the capture and flushing them when the
*next* glyph arrives is not order-safe: if something else was submitted in between, that batch has already gone out, so
the held glyphs land after it — `[panel, glyph1, glyph2]` where vanilla draws `[glyph1, panel, glyph2]`. Detecting the
counter move is necessary but not sufficient.

**The order-safe construction:**

1. The **backend** owns a *pending font batch*: a vertex buffer plus the state it was started with (texture, colour,
   format id, stride, draw mode).
2. The font **appends** each glyph quad to it — `celtium$fontAppend(format, stride, formatId, address, vertexCount,
   drawMode)` — instead of calling `submitImmediateBatch` per glyph. A state change flushes the pending batch through
   the normal path first, then starts a new one.
3. The pending batch is flushed **at the entry of `submitImmediateBatch`**, before the intervening batch is appended,
   and at every **group / pass / frame boundary** (`closeCurrentImmediateGroup`, the pass-end flush, and
   `captureWorldSnapshot`). Then held geometry is always appended before anything that came after it, and coalescing is
   correct by construction rather than by assuming a string boundary.
4. **The batching must be observable**: counters for how many coalesced glyph batches were flushed *by an intervening
   submission* versus *at a boundary*, and how many glyph quads went out in each call. A batching that silently changes
   ordering is exactly the class of change this project has been bitten by.

`celtium$immediateSequence()` (this commit) is the witness that makes the entry flush cheap to decide and makes a
mistake visible: it returns `immediateBatchesOffered`, which the font compares against its own submission count.

**The direct boundary**: `FontRendererMixin` (start/end of `renderString`) would allow a whole-string hold, and the
1.12.2 source is worth checking first — `renderStringAtPos` issues one `tessellator.draw()` per character, which is why
`windows == quads == 1012751`. But it is the same ordering requirement, it only covers that one path, and
`forge122/.../mixin/core` is shared with sky-draws this round, so it needs the Lead's approval before the file is
touched.

## 3. What is landed, and what is not

- **Landed**: `-Dceltium.validation.worldTime` (`f95c6ed08`, default 6000, unblocked four tasks) and
  `celtium$immediateSequence()` on `VulkanTerrainGpuBackend` (the approved font-support read).
- **Not landed**: the pending font batch itself. A first attempt batched on the *font* side and was reverted
  unbuilt — it had exactly the ordering flaw above, and leaving it in would have been a picture bug with a
  performance label on it.
- **Still to measure** (the acceptance): F3-on vs F3-off p50/p95 in one leg, with the overlay enabled through
  `GameSettings.showDebugInfo` — the flag the overlay's own draw path reads — **stated as such in the report**, since
  the key path cannot be synthesised honestly; `windows` per glyph before/after; the text crop identical;
  `validationMessageCount=0`.

## 4. Fixture items queued behind this (both small, both in `ValidationClient.java`)

1. **Cloud knob** (Lead, from fog-path): `-Dceltium.validation.renderClouds=<0|1|2>` setting `GameSettings.renderClouds`
   at the same point the fixture pins the time, reported beside `pinnedWorldTime`, **defaulting to the game's current
   value** so no existing leg or key moves. fog-path measured that no run of this fixture can contain a cloud pixel.
2. **Two provenance defects found by sky-draws' `guiscale-leg`**: (a) the video-settings restore is not idempotent for
   a multi-state row — it reports "restored" while the value cycled on (`GUI Scale: Auto` -> `GUI Scale: 2`) and the
   scene key carries the changed `guiScale`; the restore must step until the captured value matches or set the field
   directly and say so, and report the achieved value. (b) `videoSettingsRowNames` lists `ENABLE_VSYNC` while the
   finder reported "no such row", so the finder's token matching misses a row that is present — compare the requested
   token against each row's name field and report near-misses, because a "missing row" verdict was recorded from it.
