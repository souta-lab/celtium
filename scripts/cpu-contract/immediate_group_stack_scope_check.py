#!/usr/bin/env python3
"""Fail-closed native stack lifetime contract for per-group Vulkan draw recording.

A real E2E 268-mod/AMD run exhausted LWJGL MemoryStack while recording item draws:
`VulkanDynamicMesh.bindAndDraw` -> MemoryStack.longs. drawPendingImmediate
previously opened ONE MemoryStack for every pending group and then repeatedly
allocated descriptor/viewport/vertex bind arrays from it, with no group reset.
The per-group scope fixed that (b904ea8d6), a perf refactor later dropped the
try and kept only the comment, the check was deleted, and the shipped 0.3.0 jar
exhausted the stack again on a 4,685-group window (an enchanting-table sheet).
This check pins the restored shape so the scope cannot silently regress again.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java'
MESH = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java'


def brace_body(src, start):
    """Return (body, end_index) of the brace block whose '{' first follows start.

    Comments and string/char literals are blanked for the counting pass only, so
    braces inside them cannot unbalance the match.
    """
    open_at = src.index('{', start)
    out = []
    i = open_at
    depth = 0
    while i < len(src):
        ch = src[i]
        nxt = src[i + 1] if i + 1 < len(src) else ''
        if ch == '/' and nxt == '/':
            j = src.find('\n', i)
            i = len(src) if j < 0 else j
            continue
        if ch == '/' and nxt == '*':
            j = src.find('*/', i + 2)
            i = len(src) if j < 0 else j + 2
            continue
        if ch in '"\'':
            j = i + 1
            while j < len(src) and src[j] != ch:
                j += 2 if src[j] == '\\' else 1
            i = j + 1
            continue
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                return src[start:i + 1], i + 1
        i += 1
    raise ValueError('unbalanced braces')


def main():
    src = BACKEND.read_text(encoding='utf-8')
    begin = src.index('private void drawPendingImmediate(Long onlyTarget)')
    end = src.index('private boolean groupViewportNarrowed', begin)
    method = src[begin:end]
    checks = []

    def require(label, ok):
        checks.append(bool(ok))
        print(('ok  ' if ok else 'FAIL') + label)

    outer = method.index('try (MemoryStack stack = MemoryStack.stackPush())')
    buffer = method.index('ByteBuffer push = stack.calloc(256);')
    loop = method.index('for (int orderIndex = 0; orderIndex < groups.size(); orderIndex++)')
    scoped = 'try (MemoryStack groupStack = MemoryStack.stackPush())'
    require('outer shared stack still owns 256-byte push constants', outer < buffer < loop)
    require('one explicitly closed frame per group draw, not one per flush',
            method.count(scoped) == 1 and loop < method.index(scoped))
    require('the viewport uses the group-local stack',
            'celtium$setGroupViewport(groupStack, group,' in method)
    require('viewport does not allocate on the outer flush stack',
            'celtium$setGroupViewport(stack, group,' not in method)
    if method.count(scoped) != 1:
        # Without the scoped try there is no body to inspect; the checks above
        # already failed, so report and stop instead of raising.
        print(f'\nimmediate group stack lifetime: {sum(checks)}/{len(checks)} checks PASS')
        return 1
    body, body_end = brace_body(method, method.index(scoped))
    require('group frame begins before first viewport/native bind',
            body.index(scoped) < body.index('celtium$setGroupViewport(groupStack, group,'))
    require('texture pipeline draw is inside bounded group scope',
            'value.draw(this.commandBuffer, mesh, push, bindDescriptor, this.immediateBindCache)' in body)
    require('item/block/particle pipeline draws are inside bounded group scope',
            body.count('value.draw(this.commandBuffer, mesh, push, prepared.descriptor, this.immediateBindCache)') >= 3)
    require('mod-program draw is inside bounded group scope',
            'value.draw(this.commandBuffer, mesh, this.immediateBindCache)' in body)
    require('base pipeline draw is inside bounded group scope',
            '((VulkanImmediatePipeline) pipeline).draw(this.commandBuffer, mesh, push, this.immediateBindCache)' in body)
    require('indirect consume stays inside the group scope, so a run flush it triggers lands on the group frame',
            'celtium$tryEnqueueIndirect(group, mesh, prepared, push)' in body)
    require('no control-flow exits inside group scope can bypass close',
            'continue;' not in body and 'return;' not in body)
    require('group frame closes before draw-count accounting',
            method.index('if (DrawCounters.BATCH_TIMINGS) this.immediateStatistics.addGroupDrawNanos') > body_end)
    mesh = MESH.read_text(encoding='utf-8')
    require('mesh bind arrays still allocate from the current stack frame (the pattern the scope bounds)',
            'MemoryStack.stackGet()' in mesh and 'stack.longs(' in mesh)
    failed = len(checks) - sum(checks)
    print(f'\nimmediate group stack lifetime: {sum(checks)}/{len(checks)} checks PASS')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
