#!/usr/bin/env python3
"""Fail-closed native stack lifetime contract for per-group Vulkan draw recording.

A real E2E 268-mod/AMD run exhausted LWJGL MemoryStack while recording item draws:
`VulkanDynamicMesh.bindAndDraw` -> MemoryStack.longs. drawPendingImmediate
previously opened ONE MemoryStack for every pending group and then repeatedly
allocated descriptor/viewport/vertex bind arrays from it, with no group reset.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java'
MESH = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDynamicMesh.java'
PIPELINES = ['VulkanImmediateTexturePipeline', 'VulkanImmediateItemPipeline',
             'VulkanImmediateBlockPipeline', 'VulkanImmediateParticlePipeline', 'VulkanImmediatePipeline']


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
    buffer = method.index('ByteBuffer push = stack.calloc(216);')
    loop = method.index('for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++)')
    scoped = 'try (MemoryStack groupStack = MemoryStack.stackPush())'
    require('outer shared stack still owns 216-byte push constants', outer < buffer < loop)
    require('one explicitly closed frame per group draw, not one per flush', method.count(scoped) == 1 and loop < method.index(scoped))
    require('the viewport uses the group-local stack', 'celtium$setGroupViewport(groupStack, group);' in method)
    require('viewport does not allocate on the outer flush stack', 'celtium$setGroupViewport(stack, group);' not in method)
    body_start = method.index(scoped)
    body_end = method.index('if (BATCH_TIMINGS) this.immediateStatistics.addGroupDrawNanos', body_start)
    body = method[body_start:body_end]
    require('group frame begins before first viewport/native bind', body.index(scoped) < body.index('celtium$setGroupViewport(groupStack, group);'))
    for name in PIPELINES:
        require(f'{name} draw is inside bounded group scope', f'pipeline instanceof {name} value' in body
                if name != 'VulkanImmediatePipeline' else '((VulkanImmediatePipeline) pipeline).draw' in body)
    require('group frame closes before draw-count accounting',
            bool(re.search(r'else \(\(VulkanImmediatePipeline\) pipeline\)\.draw\([^;]+;\s*}', body)))
    require('no control-flow exits inside group scope can bypass close', 'continue;' not in body and 'return;' not in body)
    require('old one-shot stackGet vertex-buffer allocation remains inside per-group dispatch',
            'MemoryStack.stackGet().longs(vertexBuffer())' in MESH.read_text(encoding='utf-8'))
    failed = len(checks) - sum(checks)
    print(f'\nimmediate group stack lifetime: {sum(checks)}/{len(checks)} checks PASS')
    return 1 if failed else 0

if __name__ == '__main__':
    sys.exit(main())
