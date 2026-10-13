#!/usr/bin/env python3
"""Compile the production ImmediateGroup and test its freelist with synthetic old-world objects."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = root / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java'
code = '''package org.soutalab.celtium.vulkan;
final class VulkanDynamicMesh {}
final class ImmediateGroupPoolCheck {
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(ImmediateGroup.POOL, "pool option not enabled");
        VulkanDynamicMesh oldMesh = new VulkanDynamicMesh();
        VulkanDynamicMesh newMesh = new VulkanDynamicMesh();
        float[] projection = new float[] {1};
        float[] modelView = new float[] {2};
        int[] state = new int[] {3};
        ImmediateGroup group = ImmediateGroup.obtain(oldMesh, projection, modelView, 4, 5, 6L, state, 7L);
        group.stamp = GroupStamp.obtain(8L, 9, true, 10);
        group.color = new float[] {11};
        group.prepared = new PreparedImmediate(new Object(), 12);
        group.retireSequence = 13;
        ImmediateGroup.recycle(group);
        check(group.mesh == null, "recycled group pins the previous world's mesh");
        check(group.projection == null && group.modelView == null && group.state == null,
              "recycled group pins snapshot arrays");
        check(group.stamp == null && group.prepared == null && group.color == null,
              "recycled group pins optional state");
        check(group.texture == 0 && group.stateKey == 0L && group.target == 0L,
              "recycled group holds old-world identity");
        ImmediateGroup reused = ImmediateGroup.obtain(newMesh, projection, modelView, 20, 21, 22L, state, 23L);
        check(reused == group, "group was not reused");
        check(reused.mesh == newMesh && reused.projection == projection && reused.modelView == modelView,
              "reused group lost required references");
        check(reused.formatId == 20 && reused.texture == 21 && reused.stateKey == 22L && reused.target == 23L,
              "reused group lost required values");
        check(reused.stamp == null && reused.color == null && reused.retireSequence == 0L,
              "reused group carried optional stale state");
        check(ImmediateGroup.poolCreated == 1L && ImmediateGroup.poolReused == 1L
              && ImmediateGroup.poolRecycled == 1L, "pool counters wrong");
        System.out.println("ImmediateGroup pool: no retained world state, reuse and counters PASS");
    }
}'''
with tempfile.TemporaryDirectory(prefix='celtium-pool-check-') as tmp:
    dest = Path(tmp)
    package_dir = dest / 'org/soutalab/celtium/vulkan'
    package_dir.mkdir(parents=True)
    (package_dir / 'ImmediateGroupPoolCheck.java').write_text(code)
    subprocess.run([shutil.which('javac'), '-d', str(dest), str(source),
                    str(package_dir / 'ImmediateGroupPoolCheck.java')], check=True)
    subprocess.run([shutil.which('java'), '-Dceltium.vulkan.groupPool=true',
                    '-cp', str(dest), 'org.soutalab.celtium.vulkan.ImmediateGroupPoolCheck'], check=True)
