package org.soutalab.celtium.vulkan.batch;

/**
 * Executes one frame's {@link BatchPlan}: walks the plan's entries in plan order and turns
 * each into draw calls on a {@link DrawSink}, binding a pipeline only where the pipeline key
 * changes and - on the per-texture fallback - rebinding the texture only where the texture
 * slot changes (S5-2, slicing design section 3-3).
 *
 * <p>The plan already encodes every ordering decision: opaque items were sorted by key and
 * ordered items (GUI, HUD, font - anything whose blending observes submission order) kept
 * their capture sequence, so the executor never reorders, sorts or defers. Execution is
 * synchronous and completes inside the submission of the frame that captured the plan: the
 * executor holds no state between calls and takes no hold on the staging ring (ringmap
 * section 7 item 4) - a plan that is not executed in its own frame is not executed at all.
 *
 * <p>The per-texture rebind points are exactly the plan's texture-slot change points. On the
 * fallback ({@code bindlessEnabled} false) entries never fuse across a texture change (see
 * {@link ImmediateBatcher#endFrame()}), so a rebind here never splits a fused entry - it
 * lands between two entries, where the old per-group path would have bound the next group's
 * texture anyway. With bindless texturing the texture rides in each draw's instance record,
 * the sink is never asked to bind one, and a texture change is not a state change at all.
 *
 * <p>The state points are the plan's state-key change points, on every path: entries never
 * fuse across a state change either, so wherever the state key changes the sink is asked to
 * re-issue the command state the key names (the viewport and scissor the caller captured
 * with the item, the depth bias) before the entry draws. A state change does not by itself
 * rebind a pipeline or a texture.
 *
 * <p>The sink is the seam to the command buffer: production binds the frame slot's vertex
 * buffer and the shared quad index buffer once around the call, then answers
 * {@link DrawSink#bindPipeline} with the pipeline object the key names,
 * {@link DrawSink#bindTexture} with the per-texture descriptor set for the slot, and
 * {@link DrawSink#drawIndexed} with one {@code vkCmdDrawIndexed} carrying the entry's fields
 * verbatim. Tests answer it with a recording fake, which is what fixes the executor's own
 * contract here: order, bind points and argument pass-through.
 */
public final class BatchPlanExecutor {

    /** The command-recording side of one plan execution; see the class comment. */
    public interface DrawSink {

        /** Binds the pipeline {@code pipelineKey} names. Called only at key changes. */
        void bindPipeline(int pipelineKey);

        /**
         * Binds the per-texture descriptor set for {@code textureSlot} (a GL texture name).
         * Called only on the per-texture fallback, at texture-slot change points.
         */
        void bindTexture(int textureSlot);

        /**
         * Re-issues the command state {@code stateKey} names (viewport, scissor, depth
         * bias - whatever the caller bucketed into the key at capture). Called at state-key
         * change points, before the entry that carries the new key draws.
         */
        void bindState(int stateKey);

        /** Records one indexed draw with the plan entry's fields, verbatim. */
        void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset,
                         int firstInstance);
    }

    /**
     * Issues {@code plan}'s entries, in plan order, on {@code sink}.
     *
     * @param bindlessEnabled the batcher's shape for this frame: true means textures ride in
     *                        instance records and no texture bind is ever issued; false is
     *                        the per-texture fallback, whose change points rebind
     */
    public void execute(BatchPlan plan, boolean bindlessEnabled, DrawSink sink) {
        int entries = plan.entryCount();
        boolean pipelineBound = false;
        int boundPipeline = 0;
        boolean textureBound = false;
        int boundTexture = 0;
        boolean stateBound = false;
        int boundState = 0;
        for (int entry = 0; entry < entries; entry++) {
            int pipelineKey = plan.pipelineKey(entry);
            if (!pipelineBound || pipelineKey != boundPipeline) {
                sink.bindPipeline(pipelineKey);
                boundPipeline = pipelineKey;
                pipelineBound = true;
            }
            if (!bindlessEnabled) {
                int textureSlot = plan.textureSlot(entry);
                if (!textureBound || textureSlot != boundTexture) {
                    sink.bindTexture(textureSlot);
                    boundTexture = textureSlot;
                    textureBound = true;
                }
            }
            int stateKey = plan.stateKey(entry);
            if (!stateBound || stateKey != boundState) {
                sink.bindState(stateKey);
                boundState = stateKey;
                stateBound = true;
            }
            sink.drawIndexed(plan.indexCount(entry), plan.instanceCount(entry),
                    plan.firstIndex(entry), plan.vertexOffset(entry),
                    plan.firstInstance(entry));
        }
    }
}
