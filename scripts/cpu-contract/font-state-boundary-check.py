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
* the stub fires the same boundary before its records change, for the capabilities and calls the group
  capture reads live (blend enable/factors/equation, cull enable/face, colour mask, scissor box and test,
  depth test), because callers that bypass {@code GlStateManager} land in the records directly.

This check pins that wiring: every setter that mutates a state the capture reads must pass through the
boundary, and the stub's tracked record writes must fire it. A setter added later without the flush is the
regression this exists to catch.

    python3 scripts/cpu-contract/font-state-boundary-check.py

Exit 0 when the wiring is present, 1 with a named list of the gaps.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java'
STUB = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java'

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

# Stub record writes that must fire noteDrawStateBoundary() first: dispatch case label -> assignment text.
STUB_SITES = [
    ('SEM_ENABLE', 'enables[(int) a0] = 1'),
    ('SEM_DISABLE', 'enables[(int) a0] = 0'),
    ('SEM_CULL_FACE', 'cullFaceValue ='),
    ('SEM_BLEND_FUNC:', 'blendSrcRgb ='),
    ('SEM_BLEND_FUNC_SEPARATE', 'blendSrcRgb = (int) c[0]'),
    ('SEM_BLEND_EQUATION:', 'blendEquationRgb = (int) a0'),
    ('SEM_BLEND_EQUATION_SEPARATE', 'blendEquationAlpha = (int) a1'),
    ('SEM_COLOR_MASK', 'colorMaskBits ='),
    ('SEM_SCISSOR', 'scissorBox[0] ='),
]


def method_body(text, name):
    """Return the body of `public void name(...)` ... } by brace matching, or None."""
    match = re.search(r'public void ' + re.escape(name), text)
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

    if 'celtium$setDrawStateBoundaryHook' not in text:
        failures.append('backend: the stub boundary hook is never registered')


def check_stub(text, failures):
    for case, write in STUB_SITES:
        match = re.search(r'case ' + re.escape(case), text)
        if not match:
            failures.append('stub: case %s not found' % case)
            continue
        # The case body runs to the next 'case ' label.
        nxt = text.find('case SEM_', match.end())
        body = text[match.end():nxt if nxt > 0 else len(text)]
        if 'noteDrawStateBoundary()' not in body:
            failures.append('stub: %s record changes without the boundary' % case)
        elif write in body and body.index('noteDrawStateBoundary()') > body.index(write):
            failures.append('stub: %s fires the boundary AFTER the write' % case)


def main():
    failures = []
    check_backend(BACKEND.read_text(encoding='utf-8'), failures)
    check_stub(STUB.read_text(encoding='utf-8'), failures)
    if failures:
        for failure in failures:
            print('FAIL', failure)
        print('%d wiring gap(s): a deferred font run can outlive the state it was drawn under' % len(failures))
        return 1
    print('font draw-state boundary: every mirror setter and stub record flushes before it mutates - PASS')
    return 0


if __name__ == '__main__':
    sys.exit(main())
