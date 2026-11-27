package org.soutalab.celtium.glcompat.loading;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TransformerNarrower {

    private static final Logger LOGGER = LogManager.getLogger("GLSM");

    public record NarrowRule(String name, String broad, List<String> narrow) {}

    public static int narrow(Set<String> exceptions, Map<String, Object> blackboard,
                             String keyPrefix, String callerName, List<NarrowRule> rules) {
        int applied = 0;
        for (NarrowRule rule : rules) {
            String key = keyPrefix + ".narrow." + rule.name();
            if (Boolean.FALSE.equals(blackboard.get(key))) {
                continue;
            }
            if (exceptions.remove(rule.broad())) {
                for (String narrow : rule.narrow()) {
                    exceptions.add(narrow);
                }
                LOGGER.info("[{}] Narrowed {} transformer exclusion to allow GL redirection", callerName, rule.broad());
                applied++;
            }
        }
        return applied;
    }

    private TransformerNarrower() {}
}
