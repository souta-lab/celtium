package org.soutalab.celtium.vulkan.gl;
import org.junit.jupiter.api.Test;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import static org.junit.jupiter.api.Assertions.*;
class FramebufferColorAttachmentTest {
  @Test void deletingBoundDrawFramebufferInvalidatesCachedBinding() throws Exception {
    var ctor = GlsmFrontend.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = GlsmFrontend.class.getDeclaredField("installed");
    installed.setAccessible(true);
    var forget = GlsmFrontend.class.getDeclaredMethod("forgetFramebuffer", int.class);
    forget.setAccessible(true);
    var draw = GlsmFrontend.class.getDeclaredField("drawFbo");
    var read = GlsmFrontend.class.getDeclaredField("readFbo");
    draw.setAccessible(true);
    read.setAccessible(true);
    Object previous = installed.get(null);
    Object stub = ctor.newInstance();
    installed.set(null, stub);
    try {
      GlsmFrontend.celtium$bindFramebuffer(0x8CA9, 10);
      GlsmFrontend.celtium$bindFramebuffer(0x8CA8, 20);
      long before = GlsmFrontend.celtium$framebufferBindSerial();
      forget.invoke(stub, 10);
      assertEquals(0, draw.getInt(stub));
      assertEquals(20, read.getInt(stub));
      assertNotEquals(before, GlsmFrontend.celtium$framebufferBindSerial());
      long afterDrawDeletion = GlsmFrontend.celtium$framebufferBindSerial();
      forget.invoke(stub, 0);
      assertEquals(afterDrawDeletion, GlsmFrontend.celtium$framebufferBindSerial());
    } finally {
      installed.set(null, previous);
    }
  }
  @Test void attachmentTargetsFollowSeparateReadAndDrawBindings() throws Exception {
    var ctor = GlsmFrontend.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = GlsmFrontend.class.getDeclaredField("installed");
    installed.setAccessible(true);
    Object previous = installed.get(null);
    installed.set(null, ctor.newInstance());
    try {
      GlsmFrontend.celtium$bindFramebuffer(0x8CA9, 10); // GL_DRAW_FRAMEBUFFER
      GlsmFrontend.celtium$bindFramebuffer(0x8CA8, 20); // GL_READ_FRAMEBUFFER
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA9, 0x8CE0, 0x0DE1, 100, 0);
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA8, 0x8CE0, 0x0DE1, 200, 0);
      assertEquals(100, GlsmFrontend.celtium$framebufferColorTexture(10));
      assertEquals(200, GlsmFrontend.celtium$framebufferColorTexture(20));
      GlsmFrontend.celtium$framebufferTexture2D(0x8D40, 0x8CE0, 0x0DE1, 300, 0);
      assertEquals(300, GlsmFrontend.celtium$framebufferColorTexture(10));
      assertEquals(200, GlsmFrontend.celtium$framebufferColorTexture(20));
    } finally {
      installed.set(null, previous);
    }
  }
  @Test void detachesAndReattaches() {
    // The stub keeps the attachment maps in the primitive form it queries on the render path, so the test uses the
    // same type; a boxed HashMap stopped compiling when the maps became Int2IntMap and took the whole suite with it.
    Int2IntMap m = new Int2IntOpenHashMap();
    Int2IntMap inverse = new Int2IntOpenHashMap();
    GlsmFrontend.updateColorAttachment(m, inverse, 10, 200);
    assertEquals(200, m.get(10));
    assertEquals(10, inverse.get(200));
    GlsmFrontend.updateColorAttachment(m, inverse, 10, 0);
    assertFalse(m.containsKey(10));
    assertFalse(inverse.containsKey(200));
    GlsmFrontend.updateColorAttachment(m, inverse, 10, 300);
    assertEquals(300, m.get(10));
    assertEquals(10, inverse.get(300));
  }

  /**
   * The lazy supply asks this question before it builds an image: a framebuffer's colour attachment whose pixels the
   * stub does not hold is the owner's image, and a mirror built for it is superseded as soon as that owner publishes.
   * The answer is "wait" only while there is nothing a full upload could read - the two exceptions are the sources
   * {@code uploadFromGlTexture} actually has (a captured game upload and a pending patch backlog).
   */
  @Test void aFramebufferAttachmentWithNoCapturedPixelsWaitsForItsOwner() throws Exception {
    var ctor = GlsmFrontend.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = GlsmFrontend.class.getDeclaredField("installed");
    installed.setAccessible(true);
    Object previous = installed.get(null);
    installed.set(null, ctor.newInstance());
    try {
      int texture = 100;
      GlsmFrontend.celtium$bindFramebuffer(0x8CA9, 10); // GL_DRAW_FRAMEBUFFER
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA9, 0x8CE0, 0x0DE1, texture, 0);
      assertTrue(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(texture),
              "a render target with no capture at all has nothing to supply");
      assertFalse(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(200),
              "a name that is not any framebuffer's attachment is supplied as before");

      // The render-target idiom: glTexImage2D with a NULL data pointer leaves an extent and a zero surface, which is
      // not content. fillFromCapture refuses exactly this surface; the supply must not build a mirror for it either.
      GlsmFrontend.celtium$glBindTexture(0x0DE1, texture);
      GlsmFrontend.celtium$texImage2D(0x0DE1, 0, 0x8058, 16, 16, 0, 0x1908, 0x1401, 0L);
      assertTrue(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(texture),
              "a level allocated with no data is not a captured game surface");

      // A texture that really was uploaded before it became an attachment has pixels the supply can read.
      java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(16 * 16 * 4);
      try {
        org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(pixels), 0x7F, 16 * 16 * 4L);
        GlsmFrontend.celtium$texImage2D(0x0DE1, 0, 0x8058, 16, 16, 0, 0x1908, 0x1401,
                org.lwjgl.system.MemoryUtil.memAddress(pixels));
      } finally {
        org.lwjgl.system.MemoryUtil.memFree(pixels);
      }
      assertFalse(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(texture),
              "a captured game upload is content, so the supply runs and fills the mirror");
    } finally {
      installed.set(null, previous);
    }
  }

  /**
   * The rule's whole truth table: a colour attachment waits only when neither source the full upload reads exists. A
   * pending patch backlog is content (the game is filling the level now) and a captured game pixel is content, so both
   * keep the supply path; a name that is not an attachment is never deferred.
   */
  @Test void theDeferralWaitsOnlyWhenThereIsNothingToSupply() {
    assertTrue(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(true, false, false),
            "attachment with neither a capture nor a backlog");
    assertFalse(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(true, true, false),
            "a pending patch backlog is replayed by the full upload");
    assertFalse(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(true, false, true),
            "a captured game upload is read by the full upload");
    assertFalse(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(false, false, false),
            "a name that no framebuffer owns is supplied as before");
  }

  /**
   * The MRT slot record: a framebuffer can attach several colour textures at once (GL_COLOR_ATTACHMENT0+i), and
   * the record the backend's publication is filled from must carry every slot, keep slot 0's single-texture view
   * unchanged, and forget the whole tuple when the framebuffer is deleted.
   */
  @Test void colourAttachmentSlotsAreRecordedPerSlot() throws Exception {
    var ctor = GlsmFrontend.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = GlsmFrontend.class.getDeclaredField("installed");
    installed.setAccessible(true);
    var forget = GlsmFrontend.class.getDeclaredMethod("forgetFramebuffer", int.class);
    forget.setAccessible(true);
    Object previous = installed.get(null);
    Object stub = ctor.newInstance();
    installed.set(null, stub);
    try {
      GlsmFrontend.celtium$bindFramebuffer(0x8CA9, 10); // GL_DRAW_FRAMEBUFFER
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA9, 0x8CE0, 0x0DE1, 100, 0);
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA9, 0x8CE1, 0x0DE1, 101, 0);
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA9, 0x8CE2, 0x0DE1, 102, 0);
      int[] slots = GlsmFrontend.celtium$framebufferColorAttachments(10);
      assertEquals(8, slots.length);
      assertEquals(100, slots[0]);
      assertEquals(101, slots[1]);
      assertEquals(102, slots[2]);
      assertEquals(0, slots[3]);
      assertEquals(100, GlsmFrontend.celtium$framebufferColorTexture(10),
              "slot 0 keeps feeding the single-texture record");

      // Detaching one slot leaves the others alone.
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA9, 0x8CE1, 0x0DE1, 0, 0);
      slots = GlsmFrontend.celtium$framebufferColorAttachments(10);
      assertEquals(100, slots[0]);
      assertEquals(0, slots[1]);
      assertEquals(102, slots[2]);

      // The named route records the same tuple.
      GlsmFrontend.celtium$namedFramebufferTexture(10, 0x8CE3, 103, 0);
      assertEquals(103, GlsmFrontend.celtium$framebufferColorAttachments(10)[3]);

      // A depth attachment is not a colour slot and records nothing.
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA9, 0x8D00, 0x0DE1, 200, 0);
      assertEquals(0, GlsmFrontend.celtium$framebufferColorAttachments(10)[4]);

      forget.invoke(stub, 10);
      assertEquals(0, GlsmFrontend.celtium$framebufferColorAttachments(10)[0]);
      assertEquals(0, GlsmFrontend.celtium$framebufferColorAttachments(10)[2]);
    } finally {
      installed.set(null, previous);
    }
  }

  /** The slot helper's truth table, in the same pure-map form as {@code detachesAndReattaches}. */
  @Test void slotUpdatesDetachAndDropEmptyTuples() {
    it.unimi.dsi.fastutil.ints.Int2ObjectMap<int[]> slots =
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    GlsmFrontend.updateColorAttachmentSlot(slots, 10, 1, 101);
    assertEquals(101, slots.get(10)[1]);
    assertEquals(0, slots.get(10)[0]);
    GlsmFrontend.updateColorAttachmentSlot(slots, 10, 1, 0);
    assertFalse(slots.containsKey(10), "an all-detached tuple drops out of the map");
    GlsmFrontend.updateColorAttachmentSlot(slots, 0, 1, 101);
    assertTrue(slots.isEmpty(), "framebuffer 0 is never recorded");
    GlsmFrontend.updateColorAttachmentSlot(slots, 10, 8, 101);
    assertTrue(slots.isEmpty(), "a slot past the ledger is refused");
    assertEquals(0, GlsmFrontend.colorAttachmentSlot(0x8CE0));
    assertEquals(7, GlsmFrontend.colorAttachmentSlot(0x8CE7));
    assertEquals(-1, GlsmFrontend.colorAttachmentSlot(0x8CE8));
    assertEquals(-1, GlsmFrontend.colorAttachmentSlot(0x8D00));
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
  @Test void theOnePassDecisionMatchesTheTwoPredicatesItReplaced() throws Exception {
    var ctor = GlsmFrontend.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = GlsmFrontend.class.getDeclaredField("installed");
    installed.setAccessible(true);
    Object previous = installed.get(null);
    installed.set(null, ctor.newInstance());
    try {
      int plain = 300;
      assertEquals(GlsmFrontend.SUPPLY_NOTHING_TO_READ, GlsmFrontend.celtium$supplyDecision(plain, 0),
              "a name nothing has written has no record and no backlog");
      assertTrue(GlsmFrontend.celtium$levelHasNothingToRead(plain, 0), "the predicate it replaced agrees");

      GlsmFrontend.celtium$glBindTexture(0x0DE1, plain);
      GlsmFrontend.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401, 0L);
      assertEquals(GlsmFrontend.SUPPLY_NOW, GlsmFrontend.celtium$supplyDecision(plain, 0),
              "the zero surface is a record the upload reads, so it is not 'nothing to read'");
      assertFalse(GlsmFrontend.celtium$levelHasNothingToRead(plain, 0), "the predicate it replaced agrees");

      java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(8 * 8 * 4);
      try {
        org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(pixels), 0x21, 8 * 8 * 4L);
        GlsmFrontend.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401,
                org.lwjgl.system.MemoryUtil.memAddress(pixels));
      } finally {
        org.lwjgl.system.MemoryUtil.memFree(pixels);
      }
      assertEquals(GlsmFrontend.SUPPLY_NOW, GlsmFrontend.celtium$supplyDecision(plain, 0),
              "a real upload is content");

      // The attachment half: a name that is a framebuffer's colour attachment with no captured game pixels.
      int attachment = 301;
      GlsmFrontend.celtium$bindFramebuffer(0x8CA9, 11);
      GlsmFrontend.celtium$framebufferTexture2D(0x8CA9, 0x8CE0, 0x0DE1, attachment, 0);
      assertEquals(GlsmFrontend.SUPPLY_WAIT_FRAMEBUFFER, GlsmFrontend.celtium$supplyDecision(attachment, 0),
              "an attachment with nothing captured waits for the owner that publishes it");
      assertTrue(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(attachment), "the predicate it replaced agrees");

      // The framebuffer rule is asked first, so an attachment that also has nothing to read answers "wait", not
      // "nothing to read": the two deferrals are counted apart and the reason line differs.
      assertTrue(GlsmFrontend.celtium$levelHasNothingToRead(attachment, 0),
              "and it also has nothing to read, which is why the order is the thing being pinned");

      GlsmFrontend.celtium$glBindTexture(0x0DE1, attachment);
      java.nio.ByteBuffer attachmentPixels = org.lwjgl.system.MemoryUtil.memAlloc(4 * 4 * 4);
      try {
        org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(attachmentPixels), 0x55, 4 * 4 * 4L);
        GlsmFrontend.celtium$texImage2D(0x0DE1, 0, 0x8058, 4, 4, 0, 0x1908, 0x1401,
                org.lwjgl.system.MemoryUtil.memAddress(attachmentPixels));
      } finally {
        org.lwjgl.system.MemoryUtil.memFree(attachmentPixels);
      }
      assertEquals(GlsmFrontend.SUPPLY_NOW, GlsmFrontend.celtium$supplyDecision(attachment, 0),
              "a captured game upload is content, so the attachment is supplied rather than deferred");
      assertFalse(GlsmFrontend.celtium$mirrorWaitsForFramebuffer(attachment), "the predicate it replaced agrees");
    } finally {
      installed.set(null, previous);
    }
  }
  @Test void aDepthTextureSharedByTwoFramebuffersStillResolvesAfterOneDetaches() throws Exception {
    var ctor = GlsmFrontend.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = GlsmFrontend.class.getDeclaredField("installed");
    installed.setAccessible(true);
    Object previous = installed.get(null);
    installed.set(null, ctor.newInstance());
    try {
      // One depth texture attached to two framebuffers at once - the shape that raised the single-owner
      // question about the reverse map (a shadow map shared between passes is the real-world form).
      GlsmFrontend.celtium$bindFramebuffer(0x8D40, 10);
      GlsmFrontend.celtium$framebufferTexture2D(0x8D40, 0x8D00, 0x0DE1, 300, 0);
      GlsmFrontend.celtium$bindFramebuffer(0x8D40, 20);
      GlsmFrontend.celtium$framebufferTexture2D(0x8D40, 0x8D00, 0x0DE1, 300, 0);
      assertEquals(20, GlsmFrontend.celtium$framebufferForDepthTexture(300),
              "the last attach is the resolution's first answer");
      // Detaching from one framebuffer drops the reverse hint even though the other still holds the
      // texture. The resolution validates its hint against the forward record and rescans it on a miss,
      // so the surviving owner is still found: the single-owner reverse map costs a rescan, not the answer.
      GlsmFrontend.celtium$framebufferTexture2D(0x8D40, 0x8D00, 0x0DE1, 0, 0);
      assertEquals(10, GlsmFrontend.celtium$framebufferForDepthTexture(300),
              "the framebuffer that still holds the texture still resolves");
      GlsmFrontend.celtium$bindFramebuffer(0x8D40, 10);
      GlsmFrontend.celtium$framebufferTexture2D(0x8D40, 0x8D00, 0x0DE1, 0, 0);
      assertEquals(0, GlsmFrontend.celtium$framebufferForDepthTexture(300),
              "with no owner left, the resolution is empty");
    } finally {
      installed.set(null, previous);
    }
  }
  @Test void aMipLevelAttachmentIsRefusedByNameAndCounted() throws Exception {
    var ctor = GlsmFrontend.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = GlsmFrontend.class.getDeclaredField("installed");
    installed.setAccessible(true);
    var refusals = GlsmFrontend.class.getDeclaredField("framebufferLevelRefusals");
    refusals.setAccessible(true);
    Object previous = installed.get(null);
    installed.set(null, ctor.newInstance());
    try {
      GlsmFrontend.celtium$bindFramebuffer(0x8D40, 10);
      int before = refusals.getInt(null);
      GlsmFrontend.celtium$framebufferTexture2D(0x8D40, 0x8CE0, 0x0DE1, 400, 1);
      assertEquals(0, GlsmFrontend.celtium$framebufferColorTexture(10),
              "a level-1 attachment is not recorded as the framebuffer's level-0 texture");
      assertEquals(before + 1, refusals.getInt(null), "and the refusal is counted, not silent");
    } finally {
      installed.set(null, previous);
    }
  }
}
