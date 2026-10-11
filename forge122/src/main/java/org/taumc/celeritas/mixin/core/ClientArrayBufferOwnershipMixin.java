package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * B09: hands the stub the game's own vertex buffer for the client-array draw that is about to be made from it.
 * <p>
 * The problem this closes, from the audit and from the code: {@code WorldVertexBufferUploader.func_181679_a} is the
 * single draw entry of Minecraft's immediate path, and when no array buffer is bound it draws from a <em>client
 * pointer</em> - an address with no length and no owner. The stub could only read such a pointer behind a
 * {@code /proc/self/maps} guard, with a 32 MiB cap and a refusal by name on any OS where that file does not exist;
 * and even a mapping hit says nothing about the buffer's lifetime. Here the {@code BufferBuilder} is in hand one
 * instruction before the draw, so its {@code ByteBuffer} address <em>and capacity</em> are published to
 * {@code CeltiumGlStub.publishClientArrayBuffer}, and the stub decodes that range without the guard. The pointer-only
 * path stays for calls that do not come through this uploader, with its tuples and refusals stated in the census.
 * <p>
 * The injection point is the {@code glDrawArrays} call itself, not the method head, and that placement is the whole
 * sequencing argument: {@link WorldVertexBufferUploaderMixin} cancels this body at HEAD for every batch the Vulkan
 * immediate path accepts, so a HEAD publication would be published for draws that never happen and would be waiting
 * for the next unrelated {@code glDrawArrays}. At the call site, a publication exists exactly when GL is about to
 * draw from that buffer. The preDraw loop between HEAD and here only sets pointers; it issues no draw.
 * <p>
 * {@code require = 0}: a shifted target must leave the stub's pointer-only path (and its refusal) in place rather than
 * fail the mixin. What says the hook is live is the bounded {@code CELTIUM_OWNED_BUFFER} line it prints, plus
 * {@code ownedBufferDecoded} in the stub's own census.
 */
@Mixin(WorldVertexBufferUploader.class)
public abstract class ClientArrayBufferOwnershipMixin {

    /** Publications described in full, bounded. */
    private static int celtium$ownedBufferDumps;
    /** Publications offered, so a run can tell "no publication happened" from "the stub did not use one". */
    private static long celtium$ownedBufferPublished;
    /** Publications the stub then decoded, read back from the stub's own counter at the same moments. */
    private static long celtium$ownedBufferUsed;
    private static int celtium$ownedBufferReports;

    @Inject(method = "func_181679_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;func_187439_f(III)V",
            shift = At.Shift.BEFORE), require = 0)
    private void celtium$publishOwnedBuffer(BufferBuilder buffer, CallbackInfo ci) {
        if (buffer == null || !org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            return;
        }
        try {
            java.nio.ByteBuffer data = ((BufferBuilderAccessor) buffer).celtium$byteBuffer();
            if (data == null) {
                return;
            }
            long address = org.lwjgl.system.MemoryUtil.memAddress(data);
            long bytes = data.capacity();
            if (address == 0L || bytes <= 0L) {
                return;
            }
            org.soutalab.celtium.vulkan.gl.CeltiumGlStub.publishClientArrayBuffer(address, bytes);
            celtium$ownedBufferPublished++;
            if (celtium$ownedBufferDumps < 4) {
                celtium$ownedBufferDumps++;
                net.minecraft.client.renderer.vertex.VertexFormat format =
                        ((BufferBuilderAccessor) buffer).celtium$vertexFormat();
                System.out.println("[Celtium] CELTIUM_OWNED_BUFFER address=0x" + Long.toHexString(address)
                        + " bytes=" + bytes + " vertices=" + buffer.getVertexCount()
                        + " stride=" + (format == null ? 0 : format.getSize())
                        + " published=" + celtium$ownedBufferPublished
                        + " site=[" + celtium$site() + "]");
            } else if (celtium$ownedBufferPublished % 20000L == 0L) {
                // The pairing that matters: publications offered against draws the stub decoded from them. A gap means
                // the stub never saw the draw (the publication is dropped, by design, when the draw does not arrive).
                long used = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.ownedClientBufferDraws();
                if (used != celtium$ownedBufferUsed) {
                    celtium$ownedBufferUsed = used;
                    celtium$ownedBufferReports++;
                    System.out.println("[Celtium] CELTIUM_OWNED_BUFFER published=" + celtium$ownedBufferPublished
                            + " decodedFromOwned=" + used + " reports=" + celtium$ownedBufferReports);
                }
            }
        } catch (RuntimeException | LinkageError error) {
            // A publication that cannot be made is not a draw that cannot happen: the stub's pointer-only path is the
            // fallback, and it is counted there. Printing once keeps a broken accessor visible without a line per batch.
            if (celtium$ownedBufferDumps < 4) {
                celtium$ownedBufferDumps++;
                System.out.println("[Celtium] CELTIUM_OWNED_BUFFER publish failed: " + error);
            }
        }
    }

    /** The game-side frames of this publication, innermost first: the same filter the stub's instruments use. */
    private static String celtium$site() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        int taken = 0;
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            if (name.startsWith("org.soutalab.celtium") || name.startsWith("org.taumc.celeritas")
                    || name.startsWith("org.spongepowered") || name.contains("$celtium$")) {
                continue;
            }
            if (taken > 0) {
                frames.append(" <- ");
            }
            frames.append(name).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            if (++taken >= 6) {
                break;
            }
        }
        return frames.toString();
    }
}
