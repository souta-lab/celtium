package org.taumc.celeritas.mixin.core;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderManager;
import net.minecraft.client.shader.ShaderUniform;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.soutalab.celtium.vulkan.VulkanPostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends the vanilla post-process chain to the native executor instead of drawing it through GL.
 * <p>
 * {@code ShaderGroup.render} is a list of fullscreen passes over the group's framebuffers. Each pass is collected
 * with its live uniform values and the whole chain runs natively inside a GL-to-Vulkan handoff, ordered exactly
 * where the GL chain ran - after the world, before the HUD. Anything the executor declines still renders through GL
 * unchanged.
 * <p>
 * The group's main framebuffer is the game's own for every reachable chain, the entity-outline group included: its
 * silhouette does not live in the group's main but in a <em>named target</em> the game fills itself -
 * {@code RenderGlobal.entityOutlineFramebuffer}, the outline group's {@code final} target - and the chain both reads
 * that target first and writes it last. The framebuffer a chain reads is therefore what this hook reports: when it
 * is not the game's own framebuffer it is an image the renderer already keeps under that framebuffer's identity
 * (the silhouette was drawn into it while the game had that framebuffer bound), so its identity, the GL colour
 * texture behind it and its size go to the backend, which adopts that image for the chain and publishes it under
 * the texture name the game's own composite binds. Reporting it here, at the moment the chain is about to run, is
 * what lets the chain sample and write the silhouette instead of an empty chain target - and what keeps the
 * silhouette off the scene it used to be painted flat onto.
 */
@Mixin(ShaderGroup.class)
public abstract class ShaderGroupMixin {

    @Shadow
    private List<Shader> listShaders;

    @Shadow
    private float time;

    @Shadow
    private float lastStamp;

    @Shadow
    private net.minecraft.client.shader.Framebuffer mainFramebuffer;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$renderChainNatively(float partialTicks, CallbackInfo info) {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean mainIsGame = this.mainFramebuffer == minecraft.getFramebuffer();
        TerrainGpuBackend current = TerrainGpuBackends.current();
        if (!(current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend backend)
                || !backend.celtium$postChainAvailable()) {
            return;
        }
        if (!mainIsGame) {
            // Every pass that names "main" resolves to the shared frame image in the executor: that is the game's own
            // framebuffer, and only while the group's main framebuffer *is* the game's own does the name mean what the
            // chain reads and writes. A group built around a different framebuffer (vanilla builds none - ShaderGroup
            // is constructed with Minecraft.getFramebuffer() - but a mod can) would have its "main" passes sample and
            // write the wrong image while reporting the chain as run, so that group keeps its GL chain instead. The
            // audit's N04 rule: accept a chain only when its real input can be supplied natively.
            return;
        }
        // The framebuffer the chain reads first - its entry point. For every chain that starts from the game's main
        // image this is the game framebuffer itself; for the entity-outline group it is the outline target the game
        // filled with the silhouette, which the chain also writes last. That framebuffer is the game's own buffer for
        // the composite: the name it binds is read from here, so this is where the associations the executor needs
        // come from.
        net.minecraft.client.shader.Framebuffer chainInput = celtium$chainInput();
        boolean inputIsGame = chainInput == null || chainInput == minecraft.getFramebuffer();
        if (!inputIsGame && chainInput.framebufferTexture <= 0) {
            // An input the game filled without a colour texture cannot stand for anything.
            return;
        }
        int inputIdentity = 0;
        int inputTexture = 0;
        int inputW = 0;
        int inputH = 0;
        if (!inputIsGame) {
            // The image under this identity is the one the renderer kept when the game drew the silhouette into it,
            // and the GL name is what the game binds again to composite the finished outline. Both are reported
            // before the passes are collected, because they say where the input lives even when the chain itself
            // cannot be described natively and stays on GL - and because the bind hook's own report is a bind-time
            // association this one refreshes at the moment of use.
            inputIdentity = System.identityHashCode(chainInput);
            inputTexture = chainInput.framebufferTexture;
            inputW = chainInput.framebufferTextureWidth;
            inputH = chainInput.framebufferTextureHeight;
            backend.bindFramebufferColorTexture(inputIdentity, inputTexture, inputW, inputH);
        }

        // The time accumulation is the group's own; the mixin cannot call the original, so it keeps the contract.
        if (partialTicks < this.lastStamp) {
            this.time += 1.0F - this.lastStamp;
            this.time += partialTicks;
        } else {
            this.time += partialTicks - this.lastStamp;
        }
        for (this.lastStamp = partialTicks; this.time > 20.0F; this.time -= 20.0F) {
            ;
        }
        float passTime = this.time / 20.0F;

        List<VulkanPostChain.Pass> passes = new ArrayList<>(this.listShaders.size());
        for (Shader shader : this.listShaders) {
            VulkanPostChain.Pass pass = collect(shader, passTime, minecraft, this.mainFramebuffer);
            if (pass == null) {
                // A pass that cannot be described natively keeps the whole group on GL - partial chains are not a
                // mode the GL and native outputs can be compared on.
                return;
            }
            passes.add(pass);
        }
        if (passes.isEmpty()) {
            return;
        }
        if (!inputIsGame) {
            // The framebuffer whose image the executor must adopt, on the pass it reads it from: identity, GL colour
            // texture and size. Zero - the game's own framebuffer - marks the shared frame image instead, which the
            // chain already resolves without an adoption.
            VulkanPostChain.Pass first = passes.get(0);
            first.groupMainFbId = inputIdentity;
            first.groupMainTextureId = inputTexture;
            first.groupMainW = inputW;
            first.groupMainH = inputH;
        }
        if (mainIsGame) {
            // The handoff validates the bound framebuffer, and under adoption the game framebuffer's binding is the
            // shared one - exactly what a mid-frame acquire needs to see. The bind is also what flushes the batches
            // the game left pending for the framebuffer the chain is about to read: for the outline group those are
            // the silhouette draws, and they must have reached the image before the first pass samples it.
            minecraft.getFramebuffer().bindFramebuffer(true);
        }
        if (backend.celtium$postChainRun(passes)) {
            info.cancel();
        }
    }

    /** {@return the framebuffer the chain's first pass reads}, or null when the group has no pass at all. */
    private net.minecraft.client.shader.Framebuffer celtium$chainInput() {
        return this.listShaders.isEmpty() ? null : this.listShaders.get(0).framebufferIn;
    }

    private static VulkanPostChain.Pass collect(Shader shader, float passTime, Minecraft minecraft,
            net.minecraft.client.shader.Framebuffer groupMain) {
        try {
            ShaderManager manager = shader.getShaderManager();
            net.minecraft.client.shader.Framebuffer in = shader.framebufferIn;
            net.minecraft.client.shader.Framebuffer out = shader.framebufferOut;
            // The dynamic uniforms Shader.render would set. ProjMat was installed at createBindFramebuffers, and
            // the chain's own uniform values came with the pass JSON - both already sit in the uniform objects.
            manager.getShaderUniformOrDefault("ProjMat").set(((ShaderAccessor) shader).celtium$projectionMatrix());
            manager.getShaderUniformOrDefault("InSize").set(
                    (float) in.framebufferTextureWidth, (float) in.framebufferTextureHeight);
            manager.getShaderUniformOrDefault("OutSize").set(
                    (float) out.framebufferTextureWidth, (float) out.framebufferTextureHeight);
            manager.getShaderUniformOrDefault("Time").set(passTime);
            manager.getShaderUniformOrDefault("ScreenSize").set(
                    (float) minecraft.displayWidth, (float) minecraft.displayHeight);
            List<Object> auxFramebuffers = ((ShaderAccessor) shader).celtium$auxFramebuffers();
            for (int i = 0; i < auxFramebuffers.size(); ++i) {
                Object aux = auxFramebuffers.get(i);
                if (aux instanceof net.minecraft.client.shader.Framebuffer) {
                    net.minecraft.client.shader.Framebuffer fbo = (net.minecraft.client.shader.Framebuffer) aux;
                    manager.getShaderUniformOrDefault("AuxSize" + i).set(
                            (float) fbo.framebufferTextureWidth, (float) fbo.framebufferTextureHeight);
                }
            }

            VulkanPostChain.Pass pass = new VulkanPostChain.Pass();
            pass.program = programName(manager);
            // 'main' in a chain means the group's own main framebuffer; how the executor resolves it - shared
            // image for the game's, seeded chain target for an external one - is the run's decision.
            pass.inIsMain = in == groupMain;
            pass.outIsMain = out == groupMain;
            // Identity keys, not GL framebuffer ids: the executor only matches them, and identity keeps each
            // framebuffer distinct even where the GL object name would be zero (a context-less path assigns no
            // real ids at all).
            pass.inFbId = System.identityHashCode(in);
            pass.inW = in.framebufferTextureWidth;
            pass.inH = in.framebufferTextureHeight;
            pass.outFbId = System.identityHashCode(out);
            pass.outW = out.framebufferTextureWidth;
            pass.outH = out.framebufferTextureHeight;
            if (!auxFramebuffers.isEmpty()) {
                pass.auxFbIds = new int[auxFramebuffers.size()];
                pass.auxW = new int[auxFramebuffers.size()];
                pass.auxH = new int[auxFramebuffers.size()];
                for (int i = 0; i < auxFramebuffers.size(); ++i) {
                    Object aux = auxFramebuffers.get(i);
                    if (!(aux instanceof net.minecraft.client.shader.Framebuffer)) {
                        return null;
                    }
                    net.minecraft.client.shader.Framebuffer fbo = (net.minecraft.client.shader.Framebuffer) aux;
                    // 0 marks the group's main framebuffer, resolved the same way intarget is.
                    pass.auxFbIds[i] = fbo == groupMain ? 0 : System.identityHashCode(fbo);
                    pass.auxW[i] = fbo.framebufferTextureWidth;
                    pass.auxH[i] = fbo.framebufferTextureHeight;
                }
            }
            int[] outBytes = new int[1];
            List<String> blockNames = new ArrayList<>();
            List<Integer> blockOffsets = new ArrayList<>();
            float[] block = pack(manager, outBytes, blockNames, blockOffsets);
            if (block == null || outBytes[0] <= 0 || outBytes[0] > block.length * 4) {
                return null;
            }
            pass.push = block;
            pass.pushBytes = outBytes[0];
            // The same layout said member by member. The native executor compares these offsets with the ones the
            // shader modules declare before it writes a block: a length alone cannot tell a permuted block from a
            // correct one, and the buffer route in particular writes at whatever offset this reports.
            pass.blockNames = blockNames.toArray(new String[0]);
            pass.blockOffsets = new int[blockOffsets.size()];
            for (int i = 0; i < blockOffsets.size(); ++i) {
                pass.blockOffsets[i] = blockOffsets.get(i).intValue();
            }
            return pass;
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    /** Whether a declared uniform name is part of the block's fixed head, packed at its known offset. */
    private static boolean isHeadUniform(String name) {
        return "ProjMat".equals(name) || "InSize".equals(name) || "OutSize".equals(name)
                || "Time".equals(name);
    }

    /**
     * {@return the program's push block in the normalized schema the ported shaders declare} - ProjMat at 0,
     * InSize at 64, OutSize at 72, Time at 80, then every remaining declared uniform in JSON order, aligned like
     * GLSL does (matrices and vec3+ at 16 bytes, vec2 at 8, scalars at 4) - with the same layout reported member by
     * member through {@code names} and {@code offsets}.
     * <p>
     * The extras start at 84, not at 88: Time is a float, so the next member is aligned from the byte after it. A
     * first extra of vec2/vec3/vec4 lands at 88/96/96 either way, but a first extra that is a scalar - invert's
     * InverseAmount, bits' Resolution - lands at 84, and packing it at 88 made the native pass read the four bytes
     * between them instead of the value (measured: invert read 0.0 for an InverseAmount the GL chain uploaded as
     * 0.8, and bits' Resolution/Saturation/MosaicSize were all shifted by four). The shaders declare those at 84
     * because that is where the compiler puts them; the executor now compares the two member by member.
     */
    private static float[] pack(ShaderManager manager, int[] outBytes, List<String> names, List<Integer> offsets) {
        float[] block = new float[64];
        if (!writeNamed(block, 0, manager, "ProjMat") || !writeNamed(block, 64, manager, "InSize")
                || !writeNamed(block, 72, manager, "OutSize") || !writeNamed(block, 80, manager, "Time")) {
            return null;
        }
        // The head is written at fixed offsets whether or not the program declares it, so its layout is reported the
        // same way: the executor compares by name, and a head member missing from the report would read as a member
        // the packer never placed.
        names.add("ProjMat");
        offsets.add(Integer.valueOf(0));
        names.add("InSize");
        offsets.add(Integer.valueOf(64));
        names.add("OutSize");
        offsets.add(Integer.valueOf(72));
        names.add("Time");
        offsets.add(Integer.valueOf(80));
        int offset = 84;
        List<ShaderUniform> uniforms = ((ShaderManagerAccessor) manager).celtium$shaderUniforms();
        for (ShaderUniform uniform : uniforms) {
            if (isHeadUniform(uniform.getShaderName())) {
                continue;
            }
            ShaderUniformAccessor accessor = (ShaderUniformAccessor) uniform;
            int type = accessor.celtium$uniformType();
            int count = accessor.celtium$uniformCount();
            int align = type >= 8 ? 16 : (count >= 3 ? 16 : (count == 2 ? 8 : 4));
            offset = (offset + align - 1) & ~(align - 1);
            if (offset + count * 4 > block.length * 4) {
                return null;
            }
            if (!writeUniform(block, offset, uniform)) {
                return null;
            }
            names.add(uniform.getShaderName());
            offsets.add(Integer.valueOf(offset));
            offset += count * 4;
        }
        outBytes[0] = offset;
        return block;
    }

    /** Writes a named uniform's value at a byte offset; an absent or unset uniform packs as zeros. */
    private static boolean writeNamed(float[] block, int byteOffset, ShaderManager manager, String name) {
        ShaderUniform uniform = manager.getShaderUniform(name);
        if (uniform == null) {
            return true;
        }
        return writeUniform(block, byteOffset, uniform);
    }

    private static boolean writeUniform(float[] block, int byteOffset, ShaderUniform uniform) {
        ShaderUniformAccessor accessor = (ShaderUniformAccessor) uniform;
        int type = accessor.celtium$uniformType();
        int count = accessor.celtium$uniformCount();
        if (type < 4) {
            IntBuffer buffer = accessor.celtium$uniformIntBuffer();
            if (buffer == null) {
                return false;
            }
            for (int i = 0; i < count; ++i) {
                block[byteOffset / 4 + i] = Float.intBitsToFloat(buffer.get(i));
            }
        } else {
            FloatBuffer buffer = accessor.celtium$uniformFloatBuffer();
            if (buffer == null) {
                return false;
            }
            for (int i = 0; i < count; ++i) {
                block[byteOffset / 4 + i] = buffer.get(i);
            }
        }
        return true;
    }

    /** {@return the program id reduced to the shader name}, for example {@code shaders/program/invert.json -> invert}. */
    private static String programName(ShaderManager manager) {
        String name = ((ShaderManagerAccessor) manager).celtium$programFilename();
        if (name == null) {
            return "";
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        return name;
    }
}
