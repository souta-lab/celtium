package org.soutalab.celtium.glcompat.gtnh.client.renderer.cel.util;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


public class MathUtil {

    public static boolean roughlyEqual(float a, float b) {
        return Math.abs(b - a) < 0.00001f;
    }
}
