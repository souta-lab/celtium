/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium (Minecraft-independent block identifier match).
 */
package org.soutalab.celtium.shader.shaderpack.materialmap;

import java.util.Objects;

public class BlockMatch {
    private final NamespacedId blockId;
    private final Integer meta;

    public BlockMatch(NamespacedId blockId, Integer meta) {
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.meta = meta;
    }

    public NamespacedId getBlockId() {
        return blockId;
    }

    public Integer getMeta() {
        return meta;
    }

    public boolean matches(NamespacedId blockId, int meta) {
        return this.blockId.equals(blockId) && (this.meta == null || this.meta == meta);
    }
}
