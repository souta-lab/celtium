package org.soutalab.celtium.core.frame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrustumPlanesContractTest {

    @Test
    void copiesExactlySixPlanesAndKeepsTheSnapshotImmutable() {
        assertThrows(IllegalArgumentException.class, () -> FrustumPlanes.of(null));
        assertThrows(IllegalArgumentException.class, () -> FrustumPlanes.of(new float[23]));
        assertThrows(IllegalArgumentException.class, () -> FrustumPlanes.of(new float[25]));

        float[] source = new float[24];
        source[0] = 1.0f;
        FrustumPlanes planes = FrustumPlanes.of(source);
        source[0] = -1.0f;
        planes.coefficients()[0] = 2.0f;
        float[] destination = new float[30];
        assertEquals(27, planes.copyInto(destination, 3));
        assertEquals(1.0f, destination[3]);
        destination[3] = 3.0f;
        assertEquals(1.0f, planes.coefficient(FrustumPlanes.LEFT, 0));
        assertEquals(4.0f, planes.signedDistance(FrustumPlanes.LEFT, 4, -1, 2));
    }

    @Test
    void permissiveAcceptsFinitePointsAtAnyDistance() {
        FrustumPlanes planes = FrustumPlanes.permissive();
        double[][] points = {{0, 0, 0}, {512, -2048, 32768},
                {-10_000_000, 10_000_000, 10_000_000}, {10_000_000, -10_000_000, -10_000_000}};
        for (double[] point : points) {
            for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
                assertTrue(planes.signedDistance(plane, point[0], point[1], point[2]) >= 0.0f);
            }
        }
    }

    @Test
    void aNonFiniteCoefficientDisablesAllSixPlanes() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            for (int index = 0; index < FrustumPlanes.FLOAT_COUNT; index++) {
                float[] source = new float[FrustumPlanes.FLOAT_COUNT];
                source[3] = -1.0f;
                source[index] = value;
                assertSame(FrustumPlanes.permissive(), FrustumPlanes.of(source));
            }
        }
    }
}
