package org.soutalab.celtium.vulkan.terrain.lane1;

import org.soutalab.celtium.core.frame.CameraPose;

/** Camera origin split for Lane 1 push constants, with each remainder measured from its section. */
record CameraSection(int x, int y, int z, float fracX, float fracY, float fracZ) {

    static CameraSection of(CameraPose origin) {
        int x = sectionOf(origin.x());
        int y = sectionOf(origin.y());
        int z = sectionOf(origin.z());
        return new CameraSection(x, y, z,
                (float) (origin.x() - x * 16.0),
                (float) (origin.y() - y * 16.0),
                (float) (origin.z() - z * 16.0));
    }

    private static int sectionOf(double coordinate) {
        return (int) Math.floor(coordinate / 16.0);
    }
}
