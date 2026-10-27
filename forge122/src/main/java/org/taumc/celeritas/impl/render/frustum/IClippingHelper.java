package org.taumc.celeritas.impl.render.frustum;

import org.joml.FrustumIntersection;

public interface IClippingHelper {
    FrustumIntersection celeritas$getJomlFrustum();

    /** The P * MV the planes were built from, or null before the first init; read by the terrain search's viewport. */
    org.joml.Matrix4fc celeritas$getViewProjection();
}
