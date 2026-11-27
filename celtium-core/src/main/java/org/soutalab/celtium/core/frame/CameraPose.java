package org.soutalab.celtium.core.frame;

/**
 * A camera's world position and look direction, in the game's own units and degrees. Used for the
 * main view and for the shadow view (whose pose the pack's shadow directives decide); the culling
 * stage turns a pose plus a {@link FrustumPlanes} into the visible set, on the GPU.
 */
public record CameraPose(double x, double y, double z, float yawDegrees, float pitchDegrees) {
}
