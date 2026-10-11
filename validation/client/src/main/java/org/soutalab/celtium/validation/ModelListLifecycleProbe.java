package org.soutalab.celtium.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.opengl.GL11;
import org.soutalab.celtium.vulkan.VulkanDynamicMesh;
import org.soutalab.celtium.vulkan.VulkanModelQuadCapture;

/**
 * Drives the display-list lifetime acceptance for audit N02 from inside a validation run.
 * <p>
 * The four cases the acceptance names - adding a small model, recompiling the same id, deleting and re-using an id, and
 * a reload or world switch - cannot all be produced by vanilla traffic: {@code ModelRenderer} compiles each part once
 * and never recompiles it, so nothing in a normal scene recompiles a list that already has geometry. This probe
 * produces them deterministically, through the game's own API, and reads the result from the renderer's counters and
 * from the invariant the renderer's table has to keep:
 * <ul>
 *   <li>it compiles quads into a display list of its own with {@code VulkanModelQuadCapture.quad}, which is the same
 *       entry point {@code TexturedQuad} uses, so the path under test is the production one and no entity is
 *       special-cased;</li>
 *   <li>at each step it prints the list's captured generation and quad count beside the committed mesh's own generation
 *       and vertex/index counts, so an append to the old geometry, a reused stale mesh or a missing rebuild is a
 *       readable number rather than a picture argument;</li>
 *   <li>the last step deletes the list again, so the run ends with the probe's own buffers released rather than
 *       leaving the scene carrying them.</li>
 * </ul>
 * Off unless asked for: {@code -Dceltium.validation.modelLifecycle=steps} drives the cases and
 * {@code -Dceltium.validation.modelLifecycle=state} only prints the counters periodically, which is what a reload or
 * world-switch run uses to compare the live buffer count before and after.
 */
public final class ModelListLifecycleProbe {

    private static final String PROPERTY = "celtium.validation.modelLifecycle";
    private static final String MODE = System.getProperty(PROPERTY, "off");
    /** {@code steps} drives the cases; {@code state} prints the state only; anything else is off. */
    private static final boolean STEPS = "steps".equalsIgnoreCase(MODE) || "true".equalsIgnoreCase(MODE);
    private static final boolean ENABLED = STEPS || "state".equalsIgnoreCase(MODE);
    private static final String[] STEP_NAMES = { "baseline", "add", "verify-add", "recompile", "verify-recompile",
            "delete", "verify-delete", "reuse", "verify-reuse", "finish" };
    /**
     * How many client ticks each step waits before the next one.
     * <p>
     * The sequence is spaced by ticks after the world first renders rather than pinned to absolute world ticks: the
     * fixture judges a picture at its own tick and ends the run there, so a sequence pinned late would be cut in half by
     * a verdict that has nothing to do with the meshes. Ten ticks is several frames - a frame is all a change needs to
     * be built, and the verify steps read the result.
     */
    private static final int STEP_SPACING_TICKS = 6;
    /**
     * How long the sequence may wait for the renderer's model path to build its first mesh before starting anyway.
     * <p>
     * Measured: with the steps pinned to the world being visible, the whole sequence ran before the first
     * {@code recordRenderPass} (the terrain path became active around world tick 100 in that run), so the add and the
     * recompile were both overwritten by the state the first build saw and their verify steps read "no mesh yet". That
     * is a timing artefact of the fixture, not a lifetime result, so the sequence waits for
     * {@link VulkanDynamicMesh#built()} to move - one mesh built is the renderer's own statement that this path runs -
     * and starts anyway at this tick so a run where it never happens still says so.
     */
    private static final int READY_FALLBACK_TICKS = 400;
    /** How often the state-only mode prints, so a reload or world switch (the fixture's own tick 100) has a before and an after. */
    private static final int STATE_PERIOD_TICKS = 100;
    private static final int STATE_LOG_LIMIT = 40;

    private static int nextStep;
    private static int ticksUntilNextStep = -1;
    private static int startTick = -1;
    private static int list;
    private static int addGeneration;
    private static int recompileGeneration;
    private static int stateLogs;

    private ModelListLifecycleProbe() {
    }

    /**
     * One client tick of the probe.
     *
     * @param mc         the client, for the atlas texture the captured quads bind
     * @param worldTicks the fixture's world tick, which is the clock the steps are spaced on
     */
    public static void tick(Minecraft mc, int worldTicks) {
        if (!ENABLED || mc == null || mc.world == null) {
            return;
        }
        if (STEPS && celtium$ready(mc, worldTicks)) {
            if (nextStep >= STEP_NAMES.length) {
                return;
            }
            if (startTick < 0) {
                startTick = worldTicks;
                ticksUntilNextStep = 0;
            }
            if (ticksUntilNextStep-- <= 0) {
                int step = nextStep++;
                ticksUntilNextStep = STEP_SPACING_TICKS;
                celtium$run(mc, step);
            }
        }
        if (worldTicks > 0 && worldTicks % STATE_PERIOD_TICKS == 0) {
            celtium$state("periodic-tick-" + worldTicks);
        }
    }

    /**
     * {@return whether the sequence may run: the world is on screen and the renderer's model path has built a mesh}.
     * <p>
     * The probe's list is built by the renderer's frame path, so the sequence must not start before that path runs: on a
     * slow load the first step would compile into the capture and the verify step would read "no mesh yet", which is a
     * timing artefact and not a lifetime result. {@link VulkanDynamicMesh#built()} moving is the renderer's own answer
     * that it built something, which is exactly the fact the sequence needs, and no backend field has to be read.
     */
    private static boolean celtium$ready(Minecraft mc, int worldTicks) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return false;
        }
        return VulkanDynamicMesh.built() > 0 || worldTicks >= READY_FALLBACK_TICKS;
    }

    private static void celtium$run(Minecraft mc, int step) {
        if (step == 0) {
            System.out.println("[Celtium] CELTIUM_MODEL_LIFECYCLE_SEQUENCE started step=" + STEP_NAMES[step]
                    + " spacingTicks=" + STEP_SPACING_TICKS + " startTick=" + startTick
                    + " meshesBuiltBeforeStart=" + VulkanDynamicMesh.built());
        }
        switch (step) {
            case 0:
                celtium$state(STEP_NAMES[step]);
                break;
            case 1:
                celtium$add(mc);
                break;
            case 2:
                celtium$verifyAdd();
                break;
            case 3:
                celtium$recompile(mc);
                break;
            case 4:
                celtium$verifyRecompile();
                break;
            case 5:
                celtium$delete();
                break;
            case 6:
                celtium$verifyDelete();
                break;
            case 7:
                celtium$reuse(mc);
                break;
            case 8:
                celtium$verifyReuse();
                break;
            default:
                celtium$finish();
                break;
        }
    }

    /** A small model added: one new display list, two quads, compiled through the game's own GL API. */
    private static void celtium$add(Minecraft mc) {
        list = GlStateManager.glGenLists(1);
        celtium$compile(mc, 2);
        addGeneration = VulkanModelQuadCapture.generationOf(list);
        celtium$report("add", list > 0 && addGeneration > 0,
                "list=" + list + " generation=" + addGeneration + " quads="
                        + VulkanModelQuadCapture.quadsForList(list));
    }

    private static void celtium$verifyAdd() {
        int quads = VulkanModelQuadCapture.quadsForList(list);
        String mesh = VulkanDynamicMesh.describeMesh(list);
        celtium$report("verify-add", quads == 2 && VulkanModelQuadCapture.generationOf(list) == addGeneration
                && mesh.contains("meshVertices=8") && mesh.contains("meshIndices=12"), mesh);
    }

    /** The same id compiled again, with a different quad count: the generation must move and the geometry restart. */
    private static void celtium$recompile(Minecraft mc) {
        celtium$compile(mc, 3);
        recompileGeneration = VulkanModelQuadCapture.generationOf(list);
        int quads = VulkanModelQuadCapture.quadsForList(list);
        celtium$report("recompile", recompileGeneration != addGeneration && quads == 3,
                "list=" + list + " addGeneration=" + addGeneration + " recompileGeneration=" + recompileGeneration
                        + " quads=" + quads + " (an append would read 5)");
    }

    private static void celtium$verifyRecompile() {
        int quads = VulkanModelQuadCapture.quadsForList(list);
        String mesh = VulkanDynamicMesh.describeMesh(list);
        celtium$report("verify-recompile", VulkanModelQuadCapture.generationOf(list) == recompileGeneration
                && quads == 3 && mesh.contains("meshVertices=12") && mesh.contains("meshIndices=18"), mesh);
    }

    private static void celtium$delete() {
        GlStateManager.glDeleteLists(list, 1);
        celtium$report("delete", !VulkanModelQuadCapture.hasList(list)
                && VulkanModelQuadCapture.generationOf(list) == 0,
                "list=" + list + " hasList=" + VulkanModelQuadCapture.hasList(list) + " generation="
                        + VulkanModelQuadCapture.generationOf(list));
    }

    private static void celtium$verifyDelete() {
        String mesh = VulkanDynamicMesh.describeMesh(list);
        celtium$report("verify-delete", !VulkanModelQuadCapture.hasList(list)
                && VulkanModelQuadCapture.generationOf(list) == 0 && mesh.contains("committed=false"), mesh);
    }

    /** The deleted id handed back out: the new compile must not inherit anything the old one held. */
    private static void celtium$reuse(Minecraft mc) {
        celtium$compile(mc, 1);
        int generation = VulkanModelQuadCapture.generationOf(list);
        celtium$report("reuse", generation != addGeneration && generation != recompileGeneration
                && VulkanModelQuadCapture.quadsForList(list) == 1,
                "list=" + list + " addGeneration=" + addGeneration + " recompileGeneration=" + recompileGeneration
                        + " reuseGeneration=" + generation + " quads=" + VulkanModelQuadCapture.quadsForList(list));
    }

    private static void celtium$verifyReuse() {
        String mesh = VulkanDynamicMesh.describeMesh(list);
        celtium$report("verify-reuse", VulkanModelQuadCapture.quadsForList(list) == 1
                && mesh.contains("meshVertices=4") && mesh.contains("meshIndices=6"), mesh);
    }

    /** Removes the probe's own list, so the run ends with its buffers released and not left in the scene. */
    private static void celtium$finish() {
        if (list > 0) {
            GlStateManager.glDeleteLists(list, 1);
        }
        celtium$report("finish", !VulkanModelQuadCapture.hasList(list), VulkanDynamicMesh.describeMesh(list));
    }

    /**
     * Compiles {@code quads} quads into the probe's display list, through the capture's own entry point.
     * <p>
     * The texture is bound rather than assumed: a quad whose texture cannot be established is refused by the capture,
     * and the acceptance would then be reading an empty list. The previous binding is restored, because this runs
     * outside the model path's own bind.
     */
    private static void celtium$compile(Minecraft mc, int quads) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture = celtium$texture(mc);
        if (texture <= 0) {
            // The capture refuses a quad whose texture cannot be established, and a probe that captured nothing would
            // read as a lifetime failure. The binding that was already there is a real name; only if even that is
            // missing is a name made up, and then only because this exercises the lifetime, not the texture.
            texture = previousTexture > 0 ? previousTexture : 1;
        }
        GlStateManager.bindTexture(texture);
        GlStateManager.glNewList(list, GL11.GL_COMPILE);
        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] zs = new float[4];
        float[] us = new float[4];
        float[] vs = new float[4];
        for (int quad = 0; quad < quads; quad++) {
            float z = quad;
            xs[0] = 0.0f; ys[0] = 0.0f; zs[0] = z;
            xs[1] = 1.0f; ys[1] = 0.0f; zs[1] = z;
            xs[2] = 1.0f; ys[2] = 0.0f; zs[2] = z + 1.0f;
            xs[3] = 0.0f; ys[3] = 0.0f; zs[3] = z + 1.0f;
            us[0] = 0.0f; vs[0] = 0.0f;
            us[1] = 1.0f; vs[1] = 0.0f;
            us[2] = 1.0f; vs[2] = 1.0f;
            us[3] = 0.0f; vs[3] = 1.0f;
            VulkanModelQuadCapture.quad(4, xs, ys, zs, us, vs, 0.0f, 1.0f, 0.0f);
        }
        GlStateManager.glEndList();
        GlStateManager.bindTexture(previousTexture);
    }

    /** {@return a texture name the capture can record, or 0 when none can be established}. */
    private static int celtium$texture(Minecraft mc) {
        try {
            if (mc.getTextureMapBlocks() != null) {
                return mc.getTextureMapBlocks().getGlTextureId();
            }
            ITextureObject blocks = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            return blocks == null ? 0 : blocks.getGlTextureId();
        } catch (RuntimeException | LinkageError error) {
            return 0;
        }
    }

    private static void celtium$state(String step) {
        stateLogs++;
        if (stateLogs > STATE_LOG_LIMIT && stateLogs % 10 != 0) {
            return;
        }
        celtium$report(step, true, list > 0 ? VulkanDynamicMesh.describeMesh(list) : "no probe list yet");
    }

    private static void celtium$report(String step, boolean pass, String detail) {
        System.out.println("[Celtium] CELTIUM_MODEL_LIFECYCLE step=" + step + " verdict=" + (pass ? "PASS" : "FAIL")
                + " " + detail + " | " + VulkanDynamicMesh.describeModel() + " | " + VulkanModelQuadCapture.lifecycle()
                + " | " + VulkanDynamicMesh.verifyModelMeshes());
    }
}
