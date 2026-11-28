package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.glcompat.capture.DirectGlCaptureEntry;
import org.soutalab.celtium.glcompat.capture.GlFn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The framebuffer attachment contracts, retargeted at E4-c from the monolith's static doors to
 * their surviving seats: binds and attachment filing ride {@link DirectGlCaptureEntry}'s routes,
 * the answers are read from {@link FramebufferAttachmentTable} and {@link FramebufferBindings},
 * and texture uploads are driven through the entry's own dispatch. The two pure-helper tests
 * the monolith carried (its static map updaters) retired with it: the table's attach behaviour
 * is what the route-driven tests below assert, and the slot mapping survives on the entry.
 */
class FramebufferColorAttachmentTest {

    /** The records are global seats now, so each test starts from forgotten framebuffers. */
    @BeforeEach
    void resetSeats() {
        FramebufferBindings.current().reset();
        FramebufferAttachmentTable table = FramebufferAttachmentTable.current();
        table.framebufferDeleted(10);
        table.framebufferDeleted(11);
        table.framebufferDeleted(20);
    }

    private static DirectGlCaptureEntry installEntry() throws Exception {
        var ctor = DirectGlCaptureEntry.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        DirectGlCaptureEntry entry = ctor.newInstance();
        installedField().set(null, entry);
        return entry;
    }

    private static Field installedField() throws Exception {
        Field installed = DirectGlCaptureEntry.class.getDeclaredField("installed");
        installed.setAccessible(true);
        return installed;
    }

    private static Object previousEntry() throws Exception {
        return installedField().get(null);
    }

    private static void restoreEntry(Object previous) throws Exception {
        installedField().set(null, previous);
    }

    private static void forget(DirectGlCaptureEntry entry, int framebuffer) throws Exception {
        Method forget = DirectGlCaptureEntry.class.getDeclaredMethod("forgetFramebuffer", int.class);
        forget.setAccessible(true);
        forget.invoke(entry, framebuffer);
    }

    private static void dispatch(DirectGlCaptureEntry entry, String name, long... args) throws Exception {
        Method dispatch = DirectGlCaptureEntry.class.getDeclaredMethod("dispatchArgs", GlFn.class, long[].class);
        dispatch.setAccessible(true);
        long[] c = new long[16];
        System.arraycopy(args, 0, c, 0, args.length);
        dispatch.invoke(entry, DirectGlCaptureEntry.row(DirectGlCaptureEntry.ids().get(name)), c);
    }

    private static int[] slots(int framebuffer) {
        int[] out = new int[8];
        FramebufferAttachmentTable.current().colorAttachments(framebuffer, out);
        return out;
    }

    @Test
    void deletingBoundDrawFramebufferInvalidatesCachedBinding() throws Exception {
        Object previous = previousEntry();
        DirectGlCaptureEntry entry = installEntry();
        try {
            DirectGlCaptureEntry.bindFramebufferRoute(0x8CA9, 10);
            DirectGlCaptureEntry.bindFramebufferRoute(0x8CA8, 20);
            long before = FramebufferBindings.current().bindSerial();
            forget(entry, 10);
            assertEquals(0, FramebufferBindings.current().drawFramebuffer());
            assertEquals(20, FramebufferBindings.current().readFramebuffer());
            assertNotEquals(before, FramebufferBindings.current().bindSerial());
            long afterDrawDeletion = FramebufferBindings.current().bindSerial();
            forget(entry, 0);
            assertEquals(afterDrawDeletion, FramebufferBindings.current().bindSerial());
        } finally {
            restoreEntry(previous);
        }
    }

    @Test
    void attachmentTargetsFollowSeparateReadAndDrawBindings() throws Exception {
        Object previous = previousEntry();
        installEntry();
        try {
            DirectGlCaptureEntry.bindFramebufferRoute(0x8CA9, 10); // GL_DRAW_FRAMEBUFFER
            DirectGlCaptureEntry.bindFramebufferRoute(0x8CA8, 20); // GL_READ_FRAMEBUFFER
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA9, 0x8CE0, 0x0DE1, 100, 0);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA8, 0x8CE0, 0x0DE1, 200, 0);
            assertEquals(100, FramebufferAttachmentTable.current().colorTexture(10));
            assertEquals(200, FramebufferAttachmentTable.current().colorTexture(20));
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8D40, 0x8CE0, 0x0DE1, 300, 0);
            assertEquals(300, FramebufferAttachmentTable.current().colorTexture(10));
            assertEquals(200, FramebufferAttachmentTable.current().colorTexture(20));
        } finally {
            restoreEntry(previous);
        }
    }

    /**
     * The lazy supply asks this question before it builds an image: a framebuffer's colour attachment whose pixels the
     * stub does not hold is the owner's image, and a mirror built for it is superseded as soon as that owner publishes.
     * The answer is "wait" only while there is nothing a full upload could read - the two exceptions are the sources
     * {@code uploadFromGlTexture} actually has (a captured game upload and a pending patch backlog).
     */
    @Test
    void aFramebufferAttachmentWithNoCapturedPixelsWaitsForItsOwner() throws Exception {
        Object previous = previousEntry();
        DirectGlCaptureEntry entry = installEntry();
        try {
            int texture = 100;
            DirectGlCaptureEntry.bindFramebufferRoute(0x8CA9, 10); // GL_DRAW_FRAMEBUFFER
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA9, 0x8CE0, 0x0DE1, texture, 0);
            assertTrue(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(texture),
                    "a render target with no capture at all has nothing to supply");
            assertFalse(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(200),
                    "a name that is not any framebuffer's attachment is supplied as before");

            // The render-target idiom: glTexImage2D with a NULL data pointer leaves an extent and a zero surface, which is
            // not content. fillFromCapture refuses exactly this surface; the supply must not build a mirror for it either.
            dispatch(entry, "glBindTexture", 0x0DE1, texture);
            dispatch(entry, "glTexImage2D", 0x0DE1, 0, 0x8058, 16, 16, 0, 0x1908, 0x1401, 0L);
            assertTrue(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(texture),
                    "a level allocated with no data is not a captured game surface");

            // A texture that really was uploaded before it became an attachment has pixels the supply can read.
            java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(16 * 16 * 4);
            try {
                org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(pixels), 0x7F, 16 * 16 * 4L);
                dispatch(entry, "glTexImage2D", 0x0DE1, 0, 0x8058, 16, 16, 0, 0x1908, 0x1401,
                        org.lwjgl.system.MemoryUtil.memAddress(pixels));
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(pixels);
            }
            assertFalse(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(texture),
                    "a captured game upload is content, so the supply runs and fills the mirror");
        } finally {
            restoreEntry(previous);
        }
    }

    /**
     * The rule's whole truth table: a colour attachment waits only when neither source the full upload reads exists. A
     * pending patch backlog is content (the game is filling the level now) and a captured game pixel is content, so both
     * keep the supply path; a name that is not an attachment is never deferred.
     */
    @Test
    void theDeferralWaitsOnlyWhenThereIsNothingToSupply() {
        assertTrue(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(true, false, false),
                "attachment with neither a capture nor a backlog");
        assertFalse(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(true, true, false),
                "a pending patch backlog is replayed by the full upload");
        assertFalse(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(true, false, true),
                "a captured game upload is read by the full upload");
        assertFalse(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(false, false, false),
                "a name that no framebuffer owns is supplied as before");
    }

    /**
     * The MRT slot record: a framebuffer can attach several colour textures at once (GL_COLOR_ATTACHMENT0+i), and
     * the record the backend's publication is filled from must carry every slot, keep slot 0's single-texture view
     * unchanged, and forget the whole tuple when the framebuffer is deleted.
     */
    @Test
    void colourAttachmentSlotsAreRecordedPerSlot() throws Exception {
        Object previous = previousEntry();
        DirectGlCaptureEntry entry = installEntry();
        try {
            DirectGlCaptureEntry.bindFramebufferRoute(0x8CA9, 10); // GL_DRAW_FRAMEBUFFER
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA9, 0x8CE0, 0x0DE1, 100, 0);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA9, 0x8CE1, 0x0DE1, 101, 0);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA9, 0x8CE2, 0x0DE1, 102, 0);
            int[] slots = slots(10);
            assertEquals(8, slots.length);
            assertEquals(100, slots[0]);
            assertEquals(101, slots[1]);
            assertEquals(102, slots[2]);
            assertEquals(0, slots[3]);
            assertEquals(100, FramebufferAttachmentTable.current().colorTexture(10),
                    "slot 0 keeps feeding the single-texture record");

            // Detaching one slot leaves the others alone.
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA9, 0x8CE1, 0x0DE1, 0, 0);
            slots = slots(10);
            assertEquals(100, slots[0]);
            assertEquals(0, slots[1]);
            assertEquals(102, slots[2]);

            // The named route records the same tuple.
            DirectGlCaptureEntry.namedFramebufferTexture(10, 0x8CE3, 103, 0);
            assertEquals(103, slots(10)[3]);

            // A depth attachment is not a colour slot and records nothing.
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA9, 0x8D00, 0x0DE1, 200, 0);
            assertEquals(0, slots(10)[4]);

            forget(entry, 10);
            assertEquals(0, slots(10)[0]);
            assertEquals(0, slots(10)[2]);
        } finally {
            restoreEntry(previous);
        }
    }

    /**
     * The slot mapping half of the retired pure-helper test: the attachment enum to slot index
     * rule survives on the capture entry. (The tuple-updater half retired with the monolith's
     * static helpers; the table's own detach/drop behaviour is asserted through the routes in
     * {@link #colourAttachmentSlotsAreRecordedPerSlot()}.)
     */
    @Test
    void colourAttachmentSlotMapping() throws Exception {
        Method slot = DirectGlCaptureEntry.class.getDeclaredMethod("colorAttachmentSlot", int.class);
        slot.setAccessible(true);
        assertEquals(0, slot.invoke(null, 0x8CE0));
        assertEquals(7, slot.invoke(null, 0x8CE7));
        assertEquals(-1, slot.invoke(null, 0x8CE8));
        assertEquals(-1, slot.invoke(null, 0x8D00));
    }

    /**
     * The one-pass decision answers exactly what the two predicates it replaced answered, in the same order.
     * <p>
     * The lazy supply used to ask {@code celtium$mirrorWaitsForFramebuffer} and then {@code celtium$levelHasNothingToRead},
     * which read the same two capture records through nine map lookups between them. The combined query reads them once,
     * so the thing that can go wrong is the order or the truth table - and that is what this pins, on the real stub, in
     * every state the two rules distinguish: nothing written, a zero surface, a real upload, and a framebuffer's colour
     * attachment with and without captured content.
     */
    @Test
    void theOnePassDecisionMatchesTheTwoPredicatesItReplaced() throws Exception {
        Object previous = previousEntry();
        DirectGlCaptureEntry entry = installEntry();
        try {
            int plain = 300;
            assertEquals(TextureCaptureStore.SUPPLY_NOTHING_TO_READ, VulkanTextureRegistry.captureStore().celtium$supplyDecision(plain, 0),
                    "a name nothing has written has no record and no backlog");
            assertTrue(VulkanTextureRegistry.captureStore().celtium$levelHasNothingToRead(plain, 0), "the predicate it replaced agrees");

            dispatch(entry, "glBindTexture", 0x0DE1, plain);
            dispatch(entry, "glTexImage2D", 0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401, 0L);
            assertEquals(TextureCaptureStore.SUPPLY_NOW, VulkanTextureRegistry.captureStore().celtium$supplyDecision(plain, 0),
                    "the zero surface is a record the upload reads, so it is not 'nothing to read'");
            assertFalse(VulkanTextureRegistry.captureStore().celtium$levelHasNothingToRead(plain, 0), "the predicate it replaced agrees");

            java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(8 * 8 * 4);
            try {
                org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(pixels), 0x21, 8 * 8 * 4L);
                dispatch(entry, "glTexImage2D", 0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401,
                        org.lwjgl.system.MemoryUtil.memAddress(pixels));
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(pixels);
            }
            assertEquals(TextureCaptureStore.SUPPLY_NOW, VulkanTextureRegistry.captureStore().celtium$supplyDecision(plain, 0),
                    "a real upload is content");

            // The attachment half: a name that is a framebuffer's colour attachment with no captured game pixels.
            int attachment = 301;
            DirectGlCaptureEntry.bindFramebufferRoute(0x8CA9, 11);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8CA9, 0x8CE0, 0x0DE1, attachment, 0);
            assertEquals(TextureCaptureStore.SUPPLY_WAIT_FRAMEBUFFER, VulkanTextureRegistry.captureStore().celtium$supplyDecision(attachment, 0),
                    "an attachment with nothing captured waits for the owner that publishes it");
            assertTrue(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(attachment), "the predicate it replaced agrees");

            // The framebuffer rule is asked first, so an attachment that also has nothing to read answers "wait", not
            // "nothing to read": the two deferrals are counted apart and the reason line differs.
            assertTrue(VulkanTextureRegistry.captureStore().celtium$levelHasNothingToRead(attachment, 0),
                    "and it also has nothing to read, which is why the order is the thing being pinned");

            dispatch(entry, "glBindTexture", 0x0DE1, attachment);
            java.nio.ByteBuffer attachmentPixels = org.lwjgl.system.MemoryUtil.memAlloc(4 * 4 * 4);
            try {
                org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(attachmentPixels), 0x55, 4 * 4 * 4L);
                dispatch(entry, "glTexImage2D", 0x0DE1, 0, 0x8058, 4, 4, 0, 0x1908, 0x1401,
                        org.lwjgl.system.MemoryUtil.memAddress(attachmentPixels));
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(attachmentPixels);
            }
            assertEquals(TextureCaptureStore.SUPPLY_NOW, VulkanTextureRegistry.captureStore().celtium$supplyDecision(attachment, 0),
                    "a captured game upload is content, so the attachment is supplied rather than deferred");
            assertFalse(VulkanTextureRegistry.captureStore().celtium$mirrorWaitsForFramebuffer(attachment), "the predicate it replaced agrees");
        } finally {
            restoreEntry(previous);
        }
    }

    @Test
    void aDepthTextureSharedByTwoFramebuffersStillResolvesAfterOneDetaches() throws Exception {
        Object previous = previousEntry();
        installEntry();
        try {
            // One depth texture attached to two framebuffers at once - the shape that raised the single-owner
            // question about the reverse map (a shadow map shared between passes is the real-world form).
            DirectGlCaptureEntry.bindFramebufferRoute(0x8D40, 10);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8D40, 0x8D00, 0x0DE1, 300, 0);
            DirectGlCaptureEntry.bindFramebufferRoute(0x8D40, 20);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8D40, 0x8D00, 0x0DE1, 300, 0);
            assertEquals(20, FramebufferAttachmentTable.current().framebufferForDepthTexture(300),
                    "the last attach is the resolution's first answer");
            // Detaching from one framebuffer drops the reverse hint even though the other still holds the
            // texture. The resolution validates its hint against the forward record and rescans it on a miss,
            // so the surviving owner is still found: the single-owner reverse map costs a rescan, not the answer.
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8D40, 0x8D00, 0x0DE1, 0, 0);
            assertEquals(10, FramebufferAttachmentTable.current().framebufferForDepthTexture(300),
                    "the framebuffer that still holds the texture still resolves");
            DirectGlCaptureEntry.bindFramebufferRoute(0x8D40, 10);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8D40, 0x8D00, 0x0DE1, 0, 0);
            assertEquals(0, FramebufferAttachmentTable.current().framebufferForDepthTexture(300),
                    "with no owner left, the resolution is empty");
        } finally {
            restoreEntry(previous);
        }
    }

    @Test
    void aMipLevelAttachmentIsRefusedByNameAndCounted() throws Exception {
        Field refusals = DirectGlCaptureEntry.class.getDeclaredField("framebufferLevelRefusals");
        refusals.setAccessible(true);
        Object previous = previousEntry();
        installEntry();
        try {
            DirectGlCaptureEntry.bindFramebufferRoute(0x8D40, 10);
            int before = refusals.getInt(null);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8D40, 0x8CE0, 0x0DE1, 400, 1);
            assertEquals(0, FramebufferAttachmentTable.current().colorTexture(10),
                    "a level-1 attachment is not recorded as the framebuffer's level-0 texture");
            assertEquals(before + 1, refusals.getInt(null), "and the refusal is counted, not silent");
        } finally {
            restoreEntry(previous);
        }
    }
}
