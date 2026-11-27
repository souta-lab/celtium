package org.soutalab.celtium.glcompat.loading;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import java.util.List;

/** Checks optional backend dependencies without initializing their native bindings. */
public final class DependencyVerifier {
    private DependencyVerifier() {}

    /** A resource that the backend needs and the message shown when it is absent. */
    public record Check(String resourcePath, String errorMessage) {}

    /** Returns the first missing requirement, or null when the backend can resolve every resource. */
    public static Check firstMissing(Class<?> anchor, List<Check> checks) {
        for (Check check : checks) {
            if (anchor.getResource(check.resourcePath()) == null) {
                return check;
            }
        }
        return null;
    }
}
