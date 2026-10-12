# BH-09: is there a no-flag scene where a GUI clip genuinely opens?

Read-only audit, 2026-10-12. No game instance run. Every number marked **[source]** was read from the
decompiled/original source at the cited line; **[derived]** is arithmetic on those.

## 1. The gate, and the content heights

`OptionPageFrame.setupFrame` (`common/src/main/java/org/embeddedt/embeddium/impl/gui/frame/OptionPageFrame.java:39-60`) **[source]**:

```java
int y = 0;
for (OptionGroup group : this.page.getGroups()) {
    int visibleOptionCount = (int) group.getOptions().stream().filter(optionFilter::test).count();
    y += visibleOptionCount * 18;
    if (visibleOptionCount > 0 && group != lastGroup) { y += 4; }
}
this.dim = this.dim.withHeight(y);
```

So content height = **rows × 18** plus 4 px per group boundary. The gate is
`ScrollableFrame.setupFrame` (`.../frame/ScrollableFrame.java:38-47`) **[source]**: `canScrollVertical` is set only when
`!this.dim.canFitDimension(this.frame.dim) && this.dim.getLimitY() < this.frame.dim.getLimitY()`, decided **once at
construction**.

Viewport at the 320x240 logical floor: the video-options frame section is `TabFrame.java:44` **[source]**,
`dim.height()`, and the logical floor is 240 (`ScaledResolution.java:30` **[source]**), so the frame is
**240 px** tall and the page viewport is `240 − 60` (header/tab strip) = **180 px** **[derived]** — the Lead's figure,
adopted here rather than re-derived.

| page | rows | content height | viewport | gate opens? |
|---|---|---|---|---|
| `SodiumGameOptionPages.general()` | **11** **[source]** | 198 px **[derived]** | 180 | **YES** (198 > 180) |
| `SodiumGameOptionPages.quality()` | **17** **[source]** | 306 px **[derived]** | 180 | **YES** (306 > 180) |
| `SodiumGameOptionPages.advanced()` | **2** **[source]** | 36 px **[derived]** | 180 | no |
| `CommonOptionPages.performance(...)` | **6** **[source]** | 108 px **[derived]** | 180 | no |

Row counts were taken by counting `new Option(`/`.add(` per page method in
`forge122/src/main/java/org/taumc/celeritas/impl/gui/SodiumGameOptionPages.java` and
`common/.../options/CommonOptionPages.java` **[source]**; the ×18 and the +4-per-group are the accumulation above, and
the per-page group counts were **not** read, so the heights are lower bounds **[derived]**.

**The important consequence: the gate should already be open on the default page.** `general()` at 198 px against a
180 px viewport exceeds it by 18 px, and `quality()` by 126 px. So a no-flag scene is expected to exist, and the
`r163-bh09-scissor` leg's `scissorCalls=0` is **not** explained by "no page is tall enough". Three things could
explain it and each is cheap to check from that leg's own report: (a) the page it landed on was `advanced`/`performance`
(36/108 px, both under the viewport); (b) the viewport was taller than 180 because the window was not at the logical
floor — the leg was 640x480 with `guiScale=2`, which *is* the floor, but the report's `guiScaleForced`/scale field
settles it; (c) the option filter hid rows, since the count uses `optionFilter::test`.

## 2. The sidebar

`TabFrame.java:36-44` **[source]**: `tabSectionY = (tabCount + modGroupCount) * 18`, and `tabSectionInner` is made
taller than the section only when `tabSectionY > this.dim.height()`.

At the 180-240 px frame height, `tabSectionY > 180` needs **(tabCount + modGroupCount) > 10**. The fixture's cohort has
no content mod (the Lead's jar scan found no scissor user, and the r163 run's `mods/` holds the fixture, Fugue and
Scalar), so tabs come from the four pages' mod ids — well under 10. **The sidebar lever is not close**: it needs either
ten tab groups or a much shorter frame, and the frame height is already at its floor. **Not the route.**

## 3. Vanilla screens that clip

`scripts/mc-sources.sh --grep 'enableScissor'` and `--grep 'glScissor'` return **no caller in vanilla** — the Lead
established this and the earlier reading agrees: 1.12.2's `GlStateManager` has **no scissor method at all**, and the
only scissor emitter in the tree is `VintageDrawContext.enableScissor`
(`forge122/src/main/java/org/taumc/celeritas/impl/gui/VintageDrawContext.java:143`). So **there is no vanilla screen to
reach**, with or without knobs: `GuiSlot`, `GuiContainerCreative` and the chat all clip by *not drawing* out-of-range
rows rather than by a GL clip rectangle.

## 4. The JEI/HEI overlay

The Lead's jar scan of the whole cohort (`unzip -p <jar> | strings | grep -icE 'glScissor|ScissorStack|enableScissor|GL_SCISSOR_TEST'`)
found **0 in every jar**, HEI included, and the Lead additionally checked `GuiItemStackGroup`/`IngredientListOverlay`
with `javap`. That is the same conclusion reached independently here, so the overlay is **not** a route either. (I did
not re-run the scan; it is cited as the Lead's measurement.)

## Answer

- **(a) Tab count for the sidebar to clip: > 10 tab+group entries at the 180 px frame; the fixture is far from it
  (four pages, no content mod). Not the route.**
- **(b) YES — a no-flag scene is expected to exist**, on the video-settings screen's own `general()` (198 px) and
  `quality()` (306 px) pages against the 180 px viewport. What is *not* yet explained is why the one leg taken reported
  `scissorCalls=0`; that is a property of that leg (page shown, filter, or viewport), not of the gate.
- **(c) Most promising route: `scene=videoSettings` at the 320x240 logical floor (640x480 window, `guiScale=2`), landing
  on the `quality` page** (306 px, the tallest, 126 px of overflow). The default `general()` page should be enough. **No
  new flag is needed** for this — and that matters, because the approved `frameViewportHeight` knob would be *forcing*
  the condition the source says is already met. The knob's value is as a **control**: one leg with it at 0 and one with
  it small is a clean A/B for "the clip changed the picture", which is acceptance condition 2.
