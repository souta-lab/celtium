package org.soutalab.celtium.vulkan.batch;

/**
 * Executes one frame's {@link BatchPlan}: walks the plan's entries in plan order and turns
 * each into draw calls on a {@link DrawSink}, binding a pipeline only where the pipeline key
 * changes and rebinding the texture only where a per-texture entry's texture slot changes
 * (S5-2, slicing design section 3-3).
 *
 * <p>The plan already encodes every ordering decision: opaque items were sorted by key and
 * ordered items (GUI, HUD, font - anything whose blending observes submission order) kept
 * their capture sequence, so the executor never reorders, sorts or defers. Execution is
 * synchronous and completes inside the submission of the frame that captured the plan: the
 * executor holds no state between calls and takes no hold on the staging ring (ringmap
 * section 7 item 4) - a plan that is not executed in its own frame is not executed at all.
 *
 * <p>Whether a texture is bound at all is per entry, not per frame: an entry whose texture
 * rides in its instance records ({@link BatchPlan#textureInRecord(int)}) samples through the
 * bindless texture array and the sink is never asked to bind one for it. The per-texture
 * rebind points are exactly the texture-slot change points between per-texture entries,
 * which never fuse across a texture change (see {@link ImmediateBatcher#endFrame()}), so a
 * rebind here never splits a fused entry - it lands between two entries, where the old
 * per-group path would have bound the next group's texture anyway. A record-riding entry
 * between two per-texture entries forces the second one's rebind even when the slot is the
 * same, because the array entry's pipeline bind leaves the texture array bound at the set
 * index the per-texture set occupies.
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
 *
 * <p>The draw submission carries the accounting contract (G2-24): {@code drawIndexed}
 * answers whether the entry was accepted (recorded) or dropped, a sink that drops an
 * entry counts the drop at its own seat before answering {@code false}, and
 * {@link #execute} returns the accepted tally, so the dropped count of one execution is
 * {@code plan.entryCount()} minus the return value - by contract exactly the seat drop
 * counter's growth over the call. No caller ever has to take "submitted" for "drawn":
 * the old void signature did exactly that, and a draw the sink dropped was accounted as
 * drawn with no way to correct it after the fact.
 */
public final class BatchPlanExecutor {

    /** The command-recording side of one plan execution; see the class comment. */
    public interface DrawSink {

        /** Binds the pipeline {@code pipelineKey} names. Called only at key changes. */
        void bindPipeline(int pipelineKey);

        /**
         * Binds the per-texture descriptor set for {@code textureSlot} (a GL texture name).
         * Called only for entries whose texture does not ride in the record, at texture-slot
         * change points - and again for the first per-texture entry after a record-riding
         * one, even when its slot equals the previously bound one.
         */
        void bindTexture(int textureSlot);

        /**
         * Re-issues the command state {@code stateKey} names (viewport, scissor, depth
         * bias - whatever the caller bucketed into the key at capture). Called at state-key
         * change points, before the entry that carries the new key draws.
         */
        void bindState(int stateKey);

        /**
         * Records one indexed draw with the plan entry's fields, verbatim.
         *
         * @return true when the draw was recorded (accepted); false when the entry was
         *         dropped. A dropping sink counts the drop at its seat before returning
         *         false - a drop is never silent and never reported as drawn. A bind-side
         *         refusal (a texture whose set could not be rebuilt, say) is carried to
         *         the entry's draw and answered here, at the entry the refusal costs.
         */
        boolean drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset,
                            int firstInstance);
    }

    /**
     * Issues {@code plan}'s entries, in plan order, on {@code sink}.
     *
     * @return how many of the plan's entries the sink accepted; the rest were dropped
     *         (and counted at the sink's seat, per the {@link DrawSink} contract)
     */
    public int execute(BatchPlan plan, DrawSink sink) {
        int accepted = 0;
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
            if (plan.textureInRecord(entry)) {
                // The entry samples through the texture array, bound with its pipeline: no
                // per-texture bind is issued for it, and the array bind it leaves behind
                // invalidates the tracked per-texture binding, so the next per-texture
                // entry rebinds even when its slot equals the one bound before this entry.
                textureBound = false;
            } else {
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
            if (sink.drawIndexed(plan.indexCount(entry), plan.instanceCount(entry),
                    plan.firstIndex(entry), plan.vertexOffset(entry),
                    plan.firstInstance(entry))) {
                accepted++;
            }
        }
        return accepted;
    }
}
