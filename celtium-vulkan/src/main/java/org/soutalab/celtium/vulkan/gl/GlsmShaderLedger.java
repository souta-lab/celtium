package org.soutalab.celtium.vulkan.gl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * GLSM stage 3, GAP-PROG-1 (census S-21's non-bucket half, and S-10's program half):
 * the GLSM-side ledger for shader source/stage/deletion and the current program.
 * <p>
 * GlsmPackState classifies uniform members of linked programs; it never owned the
 * shader records ModProgramRegistry keeps as statics (SHADER_SOURCES / SHADER_STAGES /
 * DELETED_SHADERS). This ledger is their stage-3 home, with the registry's own rules:
 * setting a stage or a source revives a deleted shader (the pending delete only takes
 * effect while no live program references it - that reference check stays the registry's),
 * and deletion of an unreferenced shader drops source and stage with it. The current
 * program also lands here so SEM_USE_PROGRAM stops being owned twice (registry + stub).
 */
public final class GlsmShaderLedger {
    private static final GlsmShaderLedger CURRENT = new GlsmShaderLedger();

    public static GlsmShaderLedger current() { return CURRENT; }

    private final Map<Integer, String> sources = new HashMap<Integer, String>();
    private final Map<Integer, Integer> stages = new HashMap<Integer, Integer>();
    private final Set<Integer> deleted = new HashSet<Integer>();
    private int currentProgram;
    private long version;

    private GlsmShaderLedger() {
    }

    public void createShader(int shader, int glStage) {
        this.stages.put(shader, glStage);
        if (this.deleted.remove(shader)) this.sources.remove(shader);
        this.version++;
    }

    public void shaderSource(int shader, String source) {
        this.sources.put(shader, source);
        this.deleted.remove(shader);
        this.version++;
    }

    public String source(int shader) { return this.sources.get(shader); }

    /** @return the GL stage enum, or 0 when the shader is unknown. */
    public int stage(int shader) {
        Integer stage = this.stages.get(shader);
        return stage == null ? 0 : stage.intValue();
    }

    /** Marks a shader deleted; the caller drops it fully once no live program references it. */
    public void markDeleted(int shader) { this.deleted.add(shader); this.version++; }

    public boolean isDeleted(int shader) { return this.deleted.contains(shader); }

    /** The deferred delete taking effect: source and stage go with the flag. */
    public void dropShader(int shader) {
        this.deleted.remove(shader);
        this.sources.remove(shader);
        this.stages.remove(shader);
        this.version++;
    }

    public void useProgram(int program) { this.currentProgram = program; this.version++; }

    public int currentProgram() { return this.currentProgram; }

    public void clear() {
        this.sources.clear();
        this.stages.clear();
        this.deleted.clear();
        this.currentProgram = 0;
        this.version++;
    }

    public long version() { return this.version; }
}
