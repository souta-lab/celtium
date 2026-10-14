#!/usr/bin/env python3
"""Fail-closed contract: a capability report states the live terrain backend, never a constant.

Measured in the 268-mod E2E leg (`runtime-e2e-268-amd-pr92-stack-scope`): the run's
`logs/celtium-vulkan-device.json` said `effectiveTerrainBackend=opengl`,
`nativeTerrainImplemented=false`, `logicalDeviceCreated=false`, while the same run
printed `CELTIUM_NATIVE_DEVICE name="AMD Radeon RX 6400 (RADV NAVI24)"` and presented
a world frame (12214 distinct colours). Both report classes declared those two fields
as compile-time constants, `/celtium vulkan` told the player "Terrain still renders
with OpenGL", and the acceptance fixture asserted the constant - so the contradiction
could not fail.

The contract is that the value comes from the renderer at the one place that can see
it, that the report says where the value came from, and that the fixture checks
agreement instead of a constant. Nothing here inspects a GPU.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEVICE = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDeviceReport.java'
PROBE = ROOT / 'celtium-probe/src/main/java/org/soutalab/celtium/probe/ProbeReport.java'
COMMAND = ROOT / 'forge122/src/main/java/org/soutalab/celtium/forge/CeltiumCommand.java'
STATUS = ROOT / 'forge122/src/main/java/org/soutalab/celtium/forge/TerrainBackendStatus.java'
FIXTURE = ROOT / 'validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java'
PROBE_TEST = ROOT / 'celtium-probe/src/test/java/org/soutalab/celtium/probe/InteropEligibilityTest.java'
RENDERER_CLASS = 'org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend'


def main():
    checks = []

    def require(label, ok):
        checks.append(bool(ok))
        print(('ok  ' if ok else 'FAIL') + label)

    for name, path in (('device bootstrap', DEVICE), ('capability query', PROBE)):
        src = path.read_text(encoding='utf-8')
        require(f'{name}: no compile-time terrain-backend constant',
                'final String effectiveTerrainBackend = "opengl"' not in src
                and 'final boolean nativeTerrainImplemented' not in src)
        require(f'{name}: the fields are settable and default to an honest unknown',
                'String effectiveTerrainBackend = "unknown"' in src
                and 'boolean nativeTerrainImplemented = false' in src)
        require(f'{name}: the report records where the value came from',
                'terrainBackendSource' in src and 'not observed by the' in src)

    command = COMMAND.read_text(encoding='utf-8')
    require('the command fills the fields in from the live backend',
            'withLiveTerrainBackend(' in command and 'TerrainBackendStatus.nativeInstalled()' in command)
    require('the command no longer claims OpenGL unconditionally',
            'Terrain still renders with OpenGL' not in command)
    require('both report paths are filled in, not just the bootstrap',
            command.count('withLiveTerrainBackend(') >= 3)

    status = STATUS.read_text(encoding='utf-8')
    require('the live rule is the renderer class, not "some backend is installed"',
            RENDERER_CLASS in status and 'TerrainGpuBackends.current()' in status)

    fixture = FIXTURE.read_text(encoding='utf-8')
    require('the fixture no longer asserts the constant', 'equals("opengl") == false' not in fixture)
    require('the fixture asserts agreement with the live backend',
            'vulkanReportBackendAgreesWithLive' in fixture
            and 'capabilitiesBackendAgreesWithLive' in fixture)
    require('the depth-roundtrip claim stays an absolute',
            'Capability command claimed a depth roundtrip it did not prove' in fixture)

    test = PROBE_TEST.read_text(encoding='utf-8')
    require('the probe test pins the honest default',
            'assertEquals("unknown", report.effectiveTerrainBackend)' in test)

    if not all(checks):
        print(f'{checks.count(False)} contract check(s) failed')
        return 1
    print(f'{len(checks)} contract checks passed')
    return 0


if __name__ == '__main__':
    sys.exit(main())
