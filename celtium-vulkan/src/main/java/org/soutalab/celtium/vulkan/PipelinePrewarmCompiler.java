package org.soutalab.celtium.vulkan;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The production {@link PipelinePrewarm.Compiler}: turns a pipeline key into a real pipeline
 * creation by dispatching to the creation action the pipeline's owner registered for that key.
 *
 * <p>{@link PipelinePrewarm} deliberately owns scheduling only - what "compile key K" means is
 * the compiler the owner plugs in. This class is that plug. An owner whose draw path resolves
 * pipelines by a 64-bit state key registers, per key (or once, as the fallback, when one routine
 * decodes every key of its family), the action that builds the pipeline through the ordinary
 * creation path - {@code VulkanContext.createGraphicsPipelines}, on top of the persistent
 * {@code VkPipelineCache} - and puts it where the draw path looks. Running that action on the
 * prewarm thread, or synchronously from {@code warmUp} during startup, performs the same
 * {@code vkCreateGraphicsPipelines} the frame path would otherwise pay for mid-frame; that is
 * the whole point of the prewarm.
 *
 * <p>A key with no registered action is an honest failure, not a silent success: {@link #compile}
 * returns false, the prewarm marks the key FAILED, and the draw path keeps using the fallback
 * pipeline for it. Registering the key's action afterwards and calling
 * {@link PipelinePrewarm#retry(long)} is the recovery path.
 *
 * <p>Threading: registration is expected during setup but is safe at any time; {@code compile}
 * runs on the prewarm's background thread or on the startup thread calling {@code warmUp}, so
 * registered actions must tolerate being invoked off the render thread - the same constraint the
 * asynchronous compile design already imposes on pipeline creation.
 */
public final class PipelinePrewarmCompiler implements PipelinePrewarm.Compiler {

    /**
     * Builds the pipeline for one key and puts it where the draw path looks.
     *
     * @return true when the draw path may use the pipeline for this key
     */
    @FunctionalInterface
    public interface Creator {
        boolean create(long pipelineKey) throws Exception;
    }

    private final ConcurrentHashMap<Long, Creator> creators = new ConcurrentHashMap<Long, Creator>();
    private volatile Creator fallbackCreator;
    private final AtomicLong compiled = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong unknownKeys = new AtomicLong();

    /** Registers the creation action for one key, replacing any previous one. */
    public void register(long pipelineKey, Creator creator) {
        if (creator == null) {
            throw new IllegalArgumentException("creator");
        }
        this.creators.put(pipelineKey, creator);
    }

    /** Removes a key's creation action. {@return whether one was registered} */
    public boolean unregister(long pipelineKey) {
        return this.creators.remove(pipelineKey) != null;
    }

    /**
     * Sets the creation action used for keys without a specific registration - the shape of a
     * pipeline family whose single routine decodes any key of the family. Null clears it.
     */
    public void setFallbackCreator(Creator creator) {
        this.fallbackCreator = creator;
    }

    public int registeredCount() {
        return this.creators.size();
    }

    @Override
    public boolean compile(long pipelineKey) throws Exception {
        Creator creator = this.creators.get(pipelineKey);
        if (creator == null) {
            creator = this.fallbackCreator;
        }
        if (creator == null) {
            this.unknownKeys.incrementAndGet();
            return false;
        }
        boolean ok = creator.create(pipelineKey);
        (ok ? this.compiled : this.failed).incrementAndGet();
        return ok;
    }

    /** {@return the compile census so far}, in the census style of the stub's summaries. */
    public String summary() {
        return "prewarmCompiled=" + this.compiled.get()
                + " prewarmCompileFailed=" + this.failed.get()
                + " prewarmUnknownKeys=" + this.unknownKeys.get()
                + " prewarmRegisteredKeys=" + this.creators.size();
    }
}
