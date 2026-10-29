package org.soutalab.celtium.validation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;

/** Opt-in GPU probe for the raw-FBO fallback; never part of the renderer distribution. */
final class RawFboIdentityProbe {
    private boolean attempted;
    private boolean passed;
    private String failure = "not attempted";

    boolean attempted() { return attempted; }
    boolean passed() { return passed; }
    String failure() { return failure; }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (attempted || event.getType() != RenderGameOverlayEvent.ElementType.ALL
                || mc.world == null || mc.player == null || mc.player.ticksExisted < 80) return;
        attempted = true;
        try {
            run();
            passed = true;
            failure = "none";
        } catch (Throwable failure) {
            this.failure = failure.toString();
            System.err.println("[Celtium] CELTIUM_RAW_FBO_ID_PROBE status=FAILED " + failure);
            failure.printStackTrace();
        }
    }

    private static void run() throws Exception {
        final int fbo = 170000001;
        final int texture = 170000002;
        if (!CeltiumGlStub.isInstalled() || CeltiumGlStub.celtium$framebufferColorTexture(fbo) != 0
                || CeltiumGlStub.celtium$textureExtent(texture) != null) {
            throw new IllegalStateException("GL-less stub unavailable or synthetic names already in use");
        }
        Object backend = Class.forName("org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends")
                .getMethod("current").invoke(null);
        if (backend == null || !backend.getClass().getName().endsWith("VulkanTerrainGpuBackend")) {
            throw new IllegalStateException("Vulkan backend unavailable");
        }
        Class<?> backendClass = backend.getClass();
        Field registryField = backendClass.getDeclaredField("targetRegistry");
        registryField.setAccessible(true);
        Object registry = registryField.get(backend);
        Class<?> registryClass = registry.getClass();
        long previousIdentity = ((Number) registryClass.getMethod("currentIdentity").invoke(registry)).longValue();
        boolean previousMain = (Boolean) registryClass.getMethod("currentIsMain").invoke(registry);
        Field currentFramebufferIdentity = backendClass.getDeclaredField("currentFramebufferIdentity");
        currentFramebufferIdentity.setAccessible(true);
        long previousReportedIdentity = currentFramebufferIdentity.getLong(backend);
        long identity = ((Number) registryClass.getMethod("rawFramebufferIdentity", int.class)
                .invoke(null, fbo)).longValue();

        Class<?> hint = Class.forName("org.taumc.celeritas.core.CeltiumGllessHint");
        Field rawDraw = hint.getDeclaredField("rawDrawFramebuffer");
        rawDraw.setAccessible(true);
        int previousRawDraw = rawDraw.getInt(null);
        Field installed = CeltiumGlStub.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object stub = installed.get(null);
        Field draw = CeltiumGlStub.class.getDeclaredField("drawFbo");
        Field read = CeltiumGlStub.class.getDeclaredField("readFbo");
        draw.setAccessible(true);
        read.setAccessible(true);
        int previousDraw = draw.getInt(stub);
        int previousRead = read.getInt(stub);

        try {
            CeltiumGlStub.recordTextureAllocation(texture, 0, 64, 64);
            hint.getMethod("glBindFramebuffer", int.class, int.class).invoke(null, 0x8D40, fbo);
            // Deliberately bypass the hint's attachment report to exercise the fallback rather than its normal bind path.
            CeltiumGlStub.celtium$framebufferTexture2D(0x8D40, 0x8CE0, 0x0DE1, texture, 0);
            if (((Number) registryClass.getMethod("currentIdentity").invoke(registry)).longValue() != identity) {
                throw new IllegalStateException("raw bind did not select the expected target");
            }
            Method targetMethod = backendClass.getDeclaredMethod("offTargetTarget");
            targetMethod.setAccessible(true);
            Object target = targetMethod.invoke(backend);
            if (target == null) throw new IllegalStateException("GL-less target unavailable");
            target.getClass().getMethod("ensureChainPair", long.class, int.class, int.class)
                    .invoke(target, identity, 64, 64);
            long drawnImage = ((Number) target.getClass().getMethod("chainPairColorImage", long.class)
                    .invoke(target, identity)).longValue();
            Method adopt = backendClass.getDeclaredMethod("celtium$adoptRawFramebufferColorTexture", int.class);
            Method publish = backendClass.getDeclaredMethod("celtium$publishFramebufferColorTexture", int.class);
            adopt.setAccessible(true);
            publish.setAccessible(true);
            if (!Boolean.TRUE.equals(adopt.invoke(backend, texture))
                    || !Boolean.TRUE.equals(publish.invoke(backend, texture))) {
                throw new IllegalStateException("raw attachment adoption or publication failed");
            }
            Field images = backendClass.getDeclaredField("publishedFrameImages");
            images.setAccessible(true);
            Object sampled = ((Map<?, ?>) images.get(backend)).get(Integer.valueOf(texture));
            long sampledImage = sampled == null ? 0L : ((Number) sampled).longValue();
            if (drawnImage == 0L || drawnImage != sampledImage) {
                throw new IllegalStateException("draw image 0x" + Long.toHexString(drawnImage)
                        + " differs from sampled image 0x" + Long.toHexString(sampledImage));
            }
            // The window is 1280x720, but this attachment is only 64x64. Test both the valid edge and
            // out-of-bounds rectangles before they can reach vkCmdCopyImageToBuffer or vkCmdBlitImage.
            Field copyField = backendClass.getDeclaredField("textureCopyService");
            copyField.setAccessible(true);
            Object copyService = copyField.get(backend);
            Method readPixels = copyService.getClass().getMethod("celtium$readPixels", int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, long.class);
            Method serveCopy = copyService.getClass().getMethod("serveTextureCopy", int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, int.class, int.class);
            java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(65 * 64 * 4);
            try {
                long address = org.lwjgl.system.MemoryUtil.memAddress(buffer);
                if (!Boolean.TRUE.equals(readPixels.invoke(copyService, fbo, 0, 0, 64, 64, 0x1908, 0x1401, address))) {
                    throw new IllegalStateException("in-bounds framebuffer read was refused");
                }
                if (Boolean.TRUE.equals(readPixels.invoke(copyService, fbo, 0, 0, 65, 64, 0x1908, 0x1401, address))
                        || Boolean.TRUE.equals(readPixels.invoke(copyService, fbo, 1, 0, 64, 64, 0x1908, 0x1401, address))) {
                    throw new IllegalStateException("out-of-bounds framebuffer read was accepted");
                }
                String copyResult = (String) serveCopy.invoke(copyService, texture, 0, 0, 0, 0, 0, 65, 64, fbo);
                if ("served".equals(copyResult)) {
                    throw new IllegalStateException("out-of-bounds framebuffer copy was accepted");
                }
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(buffer);
            }
            if (Boolean.getBoolean("celtium.validation.opaqueCopyProbe")) {
                verifyOpaqueCopy(backend, copyService, target, identity, drawnImage);
            }
            // Bloom chains can keep more than eight targets alive at once. Creating a new one must not
            // discard the still-live images that earlier passes will sample.
            Method ensure = target.getClass().getMethod("ensureChainPair", long.class, int.class, int.class);
            Method imageFor = target.getClass().getMethod("chainPairColorImage", long.class);
            long[] identities = new long[12];
            long[] handles = new long[12];
            for (int i = 0; i < identities.length; i++) {
                identities[i] = ((Number) registryClass.getMethod("rawFramebufferIdentity", int.class)
                        .invoke(null, fbo + i)).longValue();
                ensure.invoke(target, identities[i], 64, 64);
                handles[i] = ((Number) imageFor.invoke(target, identities[i])).longValue();
                if (handles[i] == 0L) throw new IllegalStateException("target " + i + " has no image");
            }
            for (int i = 0; i < identities.length; i++) {
                long current = ((Number) imageFor.invoke(target, identities[i])).longValue();
                if (current != handles[i]) throw new IllegalStateException("live target " + i + " was evicted");
            }
            Method ownerFor = target.getClass().getMethod("chainPairIdentityForImage", long.class);
            Method isPairImage = target.getClass().getMethod("isChainPairColorImage", long.class);
            for (int i = 0; i < identities.length; i++) {
                if (!Boolean.TRUE.equals(isPairImage.invoke(target, handles[i]))
                        || ((Number) ownerFor.invoke(target, handles[i])).longValue() != identities[i]) {
                    throw new IllegalStateException("wrong reverse image owner " + i);
                }
            }
            // A failed creation must not make the null Vulkan handle look like a live image.
            long failedIdentity = ((Number) registryClass.getMethod("rawFramebufferIdentity", int.class)
                    .invoke(null, fbo + 40)).longValue();
            try {
                ensure.invoke(target, failedIdentity, 0, 64);
                throw new IllegalStateException("invalid image size was accepted");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
            }
            if (((Number) ownerFor.invoke(target, 0L)).longValue() != Long.MIN_VALUE
                    || Boolean.TRUE.equals(isPairImage.invoke(target, 0L))) {
                throw new IllegalStateException("null image has a chain pair owner");
            }
            // Replacement must remove the old handle immediately, while an invalid resize keeps the old owner.
            long resizedIdentity = identities[identities.length - 1];
            long oldHandle = handles[handles.length - 1];
            try {
                ensure.invoke(target, resizedIdentity, 0, 32);
                throw new IllegalStateException("invalid resize was accepted");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
            }
            if (((Number) ownerFor.invoke(target, oldHandle)).longValue() != resizedIdentity) {
                throw new IllegalStateException("invalid resize lost the live image owner");
            }
            ensure.invoke(target, resizedIdentity, 32, 32);
            long newHandle = ((Number) imageFor.invoke(target, resizedIdentity)).longValue();
            if (newHandle == 0L || newHandle == oldHandle
                    || ((Number) ownerFor.invoke(target, oldHandle)).longValue() != Long.MIN_VALUE
                    || ((Number) ownerFor.invoke(target, newHandle)).longValue() != resizedIdentity) {
                throw new IllegalStateException("image owner not updated on resize");
            }
            // A raw FBO deletion must keep the texture's contents alive. Deleting the texture afterwards
            // must then retire the orphaned target. Use LWJGL entry points to exercise the transformer route.
            org.lwjgl.opengl.GL30C.glDeleteFramebuffers(fbo);
            if (CeltiumGlStub.celtium$framebufferColorTexture(fbo) != 0
                    || ((Number) imageFor.invoke(target, identity)).longValue() != drawnImage
                    || !((Map<?, ?>) images.get(backend)).containsKey(Integer.valueOf(texture))) {
                throw new IllegalStateException("raw FBO deletion did not preserve its live texture image");
            }
            org.lwjgl.opengl.GL11C.glDeleteTextures(texture);
            if (((Number) imageFor.invoke(target, identity)).longValue() != 0L
                    || ((Map<?, ?>) images.get(backend)).containsKey(Integer.valueOf(texture))) {
                throw new IllegalStateException("raw FBO/texture deletion did not retire the target");
            }
            Method retire = backendClass.getMethod("celtium$forgetDeletedFramebuffer", long.class);
            for (int i = 1; i < identities.length; i++) {
                long retiredIdentity = identities[i];
                long retiredImage = ((Number) imageFor.invoke(target, retiredIdentity)).longValue();
                retire.invoke(backend, retiredIdentity);
                if (((Number) imageFor.invoke(target, retiredIdentity)).longValue() != 0L
                        || ((Number) ownerFor.invoke(target, retiredImage)).longValue() != Long.MIN_VALUE) {
                    throw new IllegalStateException("deleted target remains live: 0x"
                            + Long.toHexString(retiredIdentity));
                }
            }
            retire.invoke(backend, failedIdentity);
            if (((Map<?, ?>) images.get(backend)).containsKey(Integer.valueOf(texture))) {
                throw new IllegalStateException("deleted target's texture is still published");
            }
            // Reverse deletion order: retain the pair while its FBO exists, then retire it on FBO deletion.
            int secondFbo = fbo + 20;
            int secondTexture = texture + 20;
            CeltiumGlStub.recordTextureAllocation(secondTexture, 0, 64, 64);
            hint.getMethod("glBindFramebuffer", int.class, int.class).invoke(null, 0x8D40, secondFbo);
            CeltiumGlStub.celtium$framebufferTexture2D(0x8D40, 0x8CE0, 0x0DE1, secondTexture, 0);
            long secondIdentity = ((Number) registryClass.getMethod("rawFramebufferIdentity", int.class)
                    .invoke(null, secondFbo)).longValue();
            ensure.invoke(target, secondIdentity, 64, 64);
            long secondImage = ((Number) imageFor.invoke(target, secondIdentity)).longValue();
            if (!Boolean.TRUE.equals(adopt.invoke(backend, secondTexture))
                    || !Boolean.TRUE.equals(publish.invoke(backend, secondTexture))) {
                throw new IllegalStateException("second raw attachment publication failed");
            }
            org.lwjgl.opengl.GL11C.glDeleteTextures(secondTexture);
            if (secondImage == 0L || ((Number) imageFor.invoke(target, secondIdentity)).longValue() != secondImage) {
                throw new IllegalStateException("texture deletion retired a still-live FBO");
            }
            org.lwjgl.opengl.GL30C.glDeleteFramebuffers(secondFbo);
            if (((Number) imageFor.invoke(target, secondIdentity)).longValue() != 0L) {
                throw new IllegalStateException("texture/FBO deletion did not retire the target");
            }
            // Exercise the real vanilla delete hook as well as the backend's retirement method.
            net.minecraft.client.shader.Framebuffer vanilla = new net.minecraft.client.shader.Framebuffer(64, 64, true);
            vanilla.bindFramebuffer(true);
            long vanillaIdentity = System.identityHashCode(vanilla);
            ensure.invoke(target, vanillaIdentity, 64, 64);
            if (((Number) imageFor.invoke(target, vanillaIdentity)).longValue() == 0L) {
                throw new IllegalStateException("vanilla target was not allocated");
            }
            vanilla.deleteFramebuffer();
            if (((Number) imageFor.invoke(target, vanillaIdentity)).longValue() != 0L) {
                throw new IllegalStateException("vanilla delete did not retire its target");
            }
            System.out.println("[Celtium] CELTIUM_RAW_FBO_ID_PROBE status=PASSED identity=0x"
                    + Long.toHexString(identity) + " image=0x" + Long.toHexString(drawnImage)
                    + " extent=64x64 fallback=exercised liveTargets=" + identities.length
                    + " retired=true rawDeleteBothOrders=true vanillaDelete=true reverseOwners=true extentBounds=true");
        } finally {
            CeltiumGlStub.celtium$bindFramebuffer(0x8CA9, previousDraw);
            CeltiumGlStub.celtium$bindFramebuffer(0x8CA8, previousRead);
            rawDraw.setInt(null, previousRawDraw);
            backendClass.getMethod("bindFramebuffer", long.class, boolean.class)
                    .invoke(backend, previousIdentity, previousMain);
            currentFramebufferIdentity.setLong(backend, previousReportedIdentity);
            Method forget = CeltiumGlStub.class.getDeclaredMethod("forgetFramebuffer", int.class);
            forget.setAccessible(true);
            forget.invoke(stub, fbo);
            CeltiumGlStub.forgetTextureAllocation(texture);
        }
    }

    /** A nonopaque source and a partially updated destination expose both alpha and out-of-rectangle errors. */
    private static void verifyOpaqueCopy(Object backend, Object service, Object rawTarget, long identity,
            long sourceImage) throws Exception {
        var contextField = backend.getClass().getDeclaredField("context");
        contextField.setAccessible(true);
        var context = (org.soutalab.celtium.vulkan.VulkanContext) contextField.get(backend);
        var target = (org.soutalab.celtium.vulkan.GllessFrameTarget) rawTarget;
        var opaqueField = service.getClass().getDeclaredField("opaqueCopy");
        opaqueField.setAccessible(true);
        Object opaque = opaqueField.get(service);
        if (opaque == null) throw new IllegalStateException("opaque copy was not exercised in the menu");
        var prepare = opaque.getClass().getDeclaredMethod("prepare", int.class, int.class, long.class, long.class,
                long.class);
        prepare.setAccessible(true);
        var blit = org.soutalab.celtium.vulkan.VulkanGlTexture.class.getDeclaredMethod("blitRectangleFrom",
                org.lwjgl.vulkan.VkCommandBuffer.class, long.class, int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class, opaque.getClass());
        blit.setAccessible(true);
        var sourceClear = context.beginCommands();
        if (!target.applyGameClearToChainPair(identity, sourceClear, 0x4000,
                new float[] {0.25f, 0.5f, 0.75f, 0.25f}, 1.0)) {
            throw new IllegalStateException("source clear was refused");
        }
        context.endCommands(sourceClear, "opaque probe source");
        var texture = org.soutalab.celtium.vulkan.VulkanGlTexture.create(context, 8, 8, false);
        java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(8 * 8 * 4);
        boolean general = false;
        try {
            for (int i = 0; i < 64; i++) pixels.put((byte) 7).put((byte) 11).put((byte) 19).put((byte) 37);
            pixels.flip();
            texture.uploadFromImage(8, 8, pixels);
            // Two different sizes force scratch replacement; the rectangles are disjoint.
            for (int[] rect : new int[][] {{3, 2, 2, 3}, {2, 3, 5, 1}}) {
                prepare.invoke(opaque, rect[0], rect[1], sourceImage, target.chainPairColorView(identity), 1L);
                var commands = context.beginCommands();
                Object refusal = blit.invoke(texture, commands, sourceImage, 64, 64, 1, 1,
                        rect[0], rect[1], rect[2], rect[3], opaque);
                if (refusal != null) throw new IllegalStateException("opaque blit refused: " + refusal);
                context.endCommands(commands, "opaque probe copy");
            }
            transitionProbeImage(context, texture.image(), org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
            general = true;
            int[] actual = ((org.soutalab.celtium.vulkan.TextureCopyService) service)
                    .readImageRectangle(texture.image(), 0, 0, 8, 8);
            if (actual == null || actual.length != 64) throw new IllegalStateException("opaque probe read failed");
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int p = actual[y * 8 + x];
                boolean changed = (x >= 2 && x < 5 && y >= 3 && y < 5)
                        || (x >= 5 && x < 7 && y >= 1 && y < 4);
                int r = (p >>> 16) & 255, g = (p >>> 8) & 255, b = p & 255, a = p >>> 24;
                if (changed ? (Math.abs(r - 64) > 1 || Math.abs(g - 128) > 1 || Math.abs(b - 191) > 1 || a != 255)
                        : (r != 7 || g != 11 || b != 19 || a != 37)) {
                    throw new IllegalStateException("opaque copy pixel mismatch at " + x + "," + y
                            + ": 0x" + Integer.toHexString(p));
                }
            }
            System.out.println("[Celtium] CELTIUM_OPAQUE_COPY_PROBE status=PASSED partial=true alpha=true resize=true");
        } finally {
            if (general) transitionProbeImage(context, texture.image(), org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL,
                    org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            org.lwjgl.system.MemoryUtil.memFree(pixels);
            texture.delete();
        }
    }

    private static void transitionProbeImage(org.soutalab.celtium.vulkan.VulkanContext context, long image,
            int oldLayout, int newLayout) {
        var command = context.beginCommands();
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var barrier = org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack).sType$Default()
                    .oldLayout(oldLayout).newLayout(newLayout)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED).image(image)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_READ_BIT | org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_READ_BIT | org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_WRITE_BIT);
            barrier.get(0).subresourceRange().set(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(command, org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier);
        }
        context.endCommands(command, "opaque probe layout");
    }
}
