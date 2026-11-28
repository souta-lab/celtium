#!/usr/bin/env python3
"""The deferred font run must be submitted before the draw state it was appended under mutates.

Owner: **gui-fix**. Unit: "GUI text darkens - the main-pass strokes that lose to the shadow's equal-depth write".

## The contract this checks

The coalesced font run in {@code FontBatch} holds quads that were appended under one draw state and is
submitted later - at the next batch, boundary or state difference seen at append time. The group's state is
captured at *submit* time ({@code VulkanTerrainGpuBackend#celtium$captureState}), so a run that outlives a
state change draws under the state that came after. The measured case is the vanilla tooltip: Forge brackets
it with {@code GlStateManager.disableDepth() ... enableDepth()} ({@code GuiUtils.drawHoveringText}), the run
holding the shadow pass is still pending when the second call arrives, and a later flush submits it
depth-tested - the shadow's equal-depth write then kills the main pass's interior strokes (measured: 585 of
797 shadow pixels had no main ink at (x-1,y-1); {@code font-shadow-main-check.py} on the presented frame).

The fix is a boundary at the mutation site, not at the next append:

* the mirror setters flush the pending run before writing ({@code celtium$fontDrawStateBoundary}), and only
  when the value actually changes - {@code GlStateManager} calls its setters on its own cache misses too;
* the capture entry holds no draw-state records of its own (E4-c removed the monolith that did): its
  dispatch routes the capabilities and calls the group capture reads live (blend enable/factors/equation,
  cull enable/face, colour mask, scissor box and test, depth test) to the owners that fire the same
  boundary before their records change - the raster owner's before-change hook inside its setters, the
  backend's mirror setters, and the GLSM executor doors that apply state through the backend runtime -
  because callers that bypass {@code GlStateManager} land in those records directly.

This check pins that wiring: every setter that mutates a state the capture reads must pass through the
boundary, and the entry's dispatch must keep routing to the owners that fire it rather than growing a
record of its own. A setter added later without the flush is the regression this exists to catch.

    python3 scripts/cpu-contract/font-state-boundary-check.py

Exit 0 when the wiring is present, 1 with a named list of the gaps.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java'
ENTRY = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/capture/DirectGlCaptureEntry.java'
GLSM_BLEND = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/GLSMBlend.java'
GLSM_RASTERIZER = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/glcompat/GLSMRasterizer.java'
RASTER = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeRasterState.java'

# (setter name, mutation call that must come after the boundary) in VulkanTerrainGpuBackend.
MIRROR_SETTERS = [
    ('setBlendEnabled', 'immediateState.blendEnabled('),
    ('setBlendFunc', 'immediateState.blendFunc('),
    ('blendEquation', 'immediateState.blendEquation('),
    ('colorMask', 'immediateState.colorMask('),
    ('setDepthMask', 'immediateState.depthMask('),
    ('setDepthTest', 'immediateState.depthTestEnabled('),
    ('setAlphaFunc', 'immediateState.alphaFunc('),
    ('setLightingEnabled', 'immediateState.lightingEnabled('),
    ('depthFunc', 'immediateState.depthFunc('),
    ('blendFunc(int sourceFactor', 'immediateState.blendFunc('),
]

# Entry dispatch cases whose state the group capture reads live: case label -> the routing door
# the case body must call. The doors land on owners that fire the boundary before they mutate
# (the raster owner's hook, the backend's mirror setters, the GLSM executor's runtime), so the
# contract follows the route rather than a record write inside the entry - the entry has none.
ENTRY_SITES = [
    ('SEM_ENABLE', 'setCapability('),
    ('SEM_DISABLE', 'setCapability('),
    ('SEM_CULL_FACE', 'GLSMRasterizer.glCullFace('),
    ('SEM_BLEND_FUNC:', 'GLSMBlend.glBlendFunc('),
    ('SEM_BLEND_FUNC_SEPARATE', 'GLSMBlend.glBlendFuncSeparate('),
    ('SEM_BLEND_EQUATION:', 'GLSMBlend.glBlendEquation('),
    ('SEM_BLEND_EQUATION_SEPARATE', 'GLSMBlend.glBlendEquationSeparate('),
    ('SEM_COLOR_MASK', 'GLStateManager.glColorMask('),
    ('SEM_SCISSOR', 'NativeRasterState.current().scissor('),
]

# The GLSM executor doors behind the entry's routes: each must apply through the executor
# (EXECUTOR.), which is what carries the write to the backend runtime's boundary-firing setters.
GLSM_DOORS = [
    (GLSM_RASTERIZER, 'glCullFace'),
    (GLSM_BLEND, 'glBlendFunc'),
    (GLSM_BLEND, 'glBlendFuncSeparate'),
    (GLSM_BLEND, 'glBlendEquation'),
    (GLSM_BLEND, 'glBlendEquationSeparate'),
    (GLSM_BLEND, 'glColorMask'),
]

# NativeRasterState setters the capture reads live: each must fire the owner's before-change
# hook (changing()) before it stores, which is where the stub-side boundary now lives.
RASTER_SETTERS = [
    'scissor', 'scissorTest', 'cullFace', 'cullEnabled', 'blendEnabled', 'blendFunc',
    'blendEquation', 'colorMask', 'frontFace', 'polygonOffsetFillEnabled',
]


def method_body(text, name):
    """Return the body of `[modifier] void name(...)` ... } by brace matching, or None."""
    match = re.search(r'(?:public|private|protected)(?:\s+(?:static|final))*' + r'\s+void\s+' + re.escape(name), text)
    if not match:
        return None
    start = text.index('{', match.end() - 1)
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        if char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
            if depth == 0:
                return text[start:index]
    return None


def check_backend(text, failures):
    for name, mutation in MIRROR_SETTERS:
        body = method_body(text, name)
        if body is None:
            failures.append('backend: setter %s not found' % name)
            continue
        boundary = body.find('celtium$fontDrawStateBoundary()')
        if boundary < 0:
            failures.append('backend: %s mutates without a font boundary' % name)
            continue
        # The same accessor name appears in the change guard that precedes the flush; the write the boundary
        # protects is the one the setter performs after it - look for any mutation call past the boundary.
        if body.find(mutation, boundary + 1) < 0:
            failures.append('backend: %s boundary present but the %s write is missing or precedes it'
                            % (name, mutation))

    # The raster records moved into NativeRasterState (one owner for the stub and the mirror); the backend registers
    # the font boundary as that owner's before-change callback.
    if 'celtium$setDrawStateBoundaryHook' not in text and not re.search(
            r'rasterState\.beforeChange\([^;]*celtium\$fontDrawStateBoundary', text):
        failures.append('backend: the stub boundary hook is never registered')


def check_entry(text, failures):
    raster_text = RASTER.read_text(encoding='utf-8')
    # The raster owner's setters fire the boundary through the before-change hook the backend
    # registers (check_backend verifies the registration); a setter that stores without firing
    # it is the gap this half exists to catch.
    for setter in RASTER_SETTERS:
        body = method_body(raster_text, setter)
        if body is None:
            failures.append('entry: NativeRasterState.%s not found' % setter)
        elif 'changing()' not in body:
            failures.append('entry: NativeRasterState.%s stores without firing the boundary hook' % setter)
    # The capability producer: its raster writes must name boundary-firing setters, and its one
    # flat write is the enable sheet the queries answer from (no group-capture state of its own).
    producer = method_body(text, 'setCapability')
    if producer is None:
        failures.append('entry: setCapability not found')
    else:
        if 'GlsmEnableState.current().set(capability, enabled)' not in producer:
            failures.append('entry: setCapability no longer lands the flat enable record')
        for setter in re.findall(r'NativeRasterState\.current\(\)\.(\w+)\(', producer):
            if setter.startswith('note'):
                continue  # diagnostic counters, not state writes
            body = method_body(raster_text, setter)
            if body is None or 'changing()' not in body:
                failures.append('entry: setCapability writes through NativeRasterState.%s, '
                                'which fires no boundary' % setter)
    # Every capture-read case must route through its door; a case that grows a record write of
    # its own inside the entry bypasses every boundary-firing owner at once.
    for case, door in ENTRY_SITES:
        match = re.search(r'case ' + re.escape(case), text)
        if not match:
            failures.append('entry: case %s not found' % case)
            continue
        # The case body runs to the next 'case ' label.
        nxt = text.find('case SEM_', match.end())
        body = text[match.end():nxt if nxt > 0 else len(text)]
        if door not in body:
            failures.append('entry: case %s no longer routes through %s' % (case, door))
    # The GLSM doors must apply through their boundary-firing machinery - the state stack's
    # beforeModify hook, the executor that carries the write to the backend runtime, or a
    # delegation to a sibling door that does; a door that starts storing state bare would
    # bypass every boundary at once.
    for path, door in GLSM_DOORS:
        body = method_body(path.read_text(encoding='utf-8'), door)
        if body is None:
            failures.append('entry: GLSM door %s not found in %s' % (door, path.name))
        elif ('EXECUTOR.' not in body and 'beforeModify(' not in body
                and 'tryBlend' not in body and 'GLSMBlend.' not in body):
            failures.append('entry: GLSM door %s no longer applies through its boundary machinery' % door)


def main():
    failures = []
    check_backend(BACKEND.read_text(encoding='utf-8'), failures)
    check_entry(ENTRY.read_text(encoding='utf-8'), failures)
    if failures:
        for failure in failures:
            print('FAIL', failure)
        print('%d wiring gap(s): a deferred font run can outlive the state it was drawn under' % len(failures))
        return 1
    print('font draw-state boundary: every mirror setter and entry route flushes before it mutates - PASS')
    return 0


if __name__ == '__main__':
    sys.exit(main())
