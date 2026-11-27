package org.soutalab.celtium.glcompat.compat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Lets the client select radial fixed-function fog without making GLSM depend on client options.
 */
public final class FogDistanceModeBridge {
    /** Supplies the setting that selects radial fog across all active GL contexts. */
    private static volatile BooleanSupplier circularFogEnabled = () -> false;

    /** GLSM vertex shader mode for Euclidean distance from the eye. */
    private static final int EYE_RADIAL_FOG_MODE = 0;

    /** Prevents instances of this static bridge. */
    private FogDistanceModeBridge() {
    }

    /**
     * Installs the client option source used when GLSM builds a fixed-function fog shader variant.
     */
    public static void setCircularFogEnabledProvider(BooleanSupplier provider) {
        circularFogEnabled = Objects.requireNonNull(provider, "provider");
    }

    /**
     * Returns radial eye distance when circular fog is enabled, otherwise preserving GL fog state.
     */
    public static int resolveFogDistanceMode(int stateMode) {
        return circularFogEnabled.getAsBoolean() ? EYE_RADIAL_FOG_MODE : stateMode;
    }
}
