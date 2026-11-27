package org.soutalab.celtium.core.frame;

/**
 * The receiving end of {@code PackTerrainDriver.gatherShadowBlockEntities}: the terrain driver walks
 * the shadow frustum and hands over every block entity that has to be drawn into the shadow pass, so
 * the non-terrain lane can render them through its {@code shadow_*} pipelines. The type id is the
 * registry id the game assigned the block entity's class; resolving it is the consumer's business.
 * <p>
 * This sink exists as an explicit interface because the predecessor design collected these silently
 * inside a compatibility callback and dropped them (design section 2.3, gap G-7).
 */
@FunctionalInterface
public interface BlockEntitySink {

    void accept(int blockX, int blockY, int blockZ, int blockEntityTypeId);
}
