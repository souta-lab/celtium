package org.soutalab.celtium.vulkan.gl;
import org.junit.jupiter.api.Test;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import static org.junit.jupiter.api.Assertions.*;
class FramebufferColorAttachmentTest {
  @Test void deletingBoundDrawFramebufferInvalidatesCachedBinding() throws Exception {
    var ctor = CeltiumGlStub.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = CeltiumGlStub.class.getDeclaredField("installed");
    installed.setAccessible(true);
    var forget = CeltiumGlStub.class.getDeclaredMethod("forgetFramebuffer", int.class);
    forget.setAccessible(true);
    var draw = CeltiumGlStub.class.getDeclaredField("drawFbo");
    var read = CeltiumGlStub.class.getDeclaredField("readFbo");
    draw.setAccessible(true);
    read.setAccessible(true);
    Object previous = installed.get(null);
    Object stub = ctor.newInstance();
    installed.set(null, stub);
    try {
      CeltiumGlStub.celtium$bindFramebuffer(0x8CA9, 10);
      CeltiumGlStub.celtium$bindFramebuffer(0x8CA8, 20);
      long before = CeltiumGlStub.celtium$framebufferBindSerial();
      forget.invoke(stub, 10);
      assertEquals(0, draw.getInt(stub));
      assertEquals(20, read.getInt(stub));
      assertNotEquals(before, CeltiumGlStub.celtium$framebufferBindSerial());
      long afterDrawDeletion = CeltiumGlStub.celtium$framebufferBindSerial();
      forget.invoke(stub, 0);
      assertEquals(afterDrawDeletion, CeltiumGlStub.celtium$framebufferBindSerial());
    } finally {
      installed.set(null, previous);
    }
  }
  @Test void attachmentTargetsFollowSeparateReadAndDrawBindings() throws Exception {
    var ctor = CeltiumGlStub.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = CeltiumGlStub.class.getDeclaredField("installed");
    installed.setAccessible(true);
    Object previous = installed.get(null);
    installed.set(null, ctor.newInstance());
    try {
      CeltiumGlStub.celtium$bindFramebuffer(0x8CA9, 10); // GL_DRAW_FRAMEBUFFER
      CeltiumGlStub.celtium$bindFramebuffer(0x8CA8, 20); // GL_READ_FRAMEBUFFER
      CeltiumGlStub.celtium$framebufferTexture2D(0x8CA9, 0x8CE0, 0x0DE1, 100, 0);
      CeltiumGlStub.celtium$framebufferTexture2D(0x8CA8, 0x8CE0, 0x0DE1, 200, 0);
      assertEquals(100, CeltiumGlStub.celtium$framebufferColorTexture(10));
      assertEquals(200, CeltiumGlStub.celtium$framebufferColorTexture(20));
      CeltiumGlStub.celtium$framebufferTexture2D(0x8D40, 0x8CE0, 0x0DE1, 300, 0);
      assertEquals(300, CeltiumGlStub.celtium$framebufferColorTexture(10));
      assertEquals(200, CeltiumGlStub.celtium$framebufferColorTexture(20));
    } finally {
      installed.set(null, previous);
    }
  }
  @Test void detachesAndReattaches() {
    // The stub keeps the attachment maps in the primitive form it queries on the render path, so the test uses the
    // same type; a boxed HashMap stopped compiling when the maps became Int2IntMap and took the whole suite with it.
    Int2IntMap m = new Int2IntOpenHashMap();
    Int2IntMap inverse = new Int2IntOpenHashMap();
    CeltiumGlStub.updateColorAttachment(m, inverse, 10, 200);
    assertEquals(200, m.get(10));
    assertEquals(10, inverse.get(200));
    CeltiumGlStub.updateColorAttachment(m, inverse, 10, 0);
    assertFalse(m.containsKey(10));
    assertFalse(inverse.containsKey(200));
    CeltiumGlStub.updateColorAttachment(m, inverse, 10, 300);
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
    var ctor = CeltiumGlStub.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = CeltiumGlStub.class.getDeclaredField("installed");
    installed.setAccessible(true);
    Object previous = installed.get(null);
    installed.set(null, ctor.newInstance());
    try {
      int texture = 100;
      CeltiumGlStub.celtium$bindFramebuffer(0x8CA9, 10); // GL_DRAW_FRAMEBUFFER
      CeltiumGlStub.celtium$framebufferTexture2D(0x8CA9, 0x8CE0, 0x0DE1, texture, 0);
      assertTrue(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(texture),
              "a render target with no capture at all has nothing to supply");
      assertFalse(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(200),
              "a name that is not any framebuffer's attachment is supplied as before");

      // The render-target idiom: glTexImage2D with a NULL data pointer leaves an extent and a zero surface, which is
      // not content. fillFromCapture refuses exactly this surface; the supply must not build a mirror for it either.
      CeltiumGlStub.celtium$glBindTexture(0x0DE1, texture);
      CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 16, 16, 0, 0x1908, 0x1401, 0L);
      assertTrue(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(texture),
              "a level allocated with no data is not a captured game surface");

      // A texture that really was uploaded before it became an attachment has pixels the supply can read.
      java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(16 * 16 * 4);
      try {
        org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(pixels), 0x7F, 16 * 16 * 4L);
        CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 16, 16, 0, 0x1908, 0x1401,
                org.lwjgl.system.MemoryUtil.memAddress(pixels));
      } finally {
        org.lwjgl.system.MemoryUtil.memFree(pixels);
      }
      assertFalse(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(texture),
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
    assertTrue(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(true, false, false),
            "attachment with neither a capture nor a backlog");
    assertFalse(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(true, true, false),
            "a pending patch backlog is replayed by the full upload");
    assertFalse(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(true, false, true),
            "a captured game upload is read by the full upload");
    assertFalse(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(false, false, false),
            "a name that no framebuffer owns is supplied as before");
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
    var ctor = CeltiumGlStub.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    var installed = CeltiumGlStub.class.getDeclaredField("installed");
    installed.setAccessible(true);
    Object previous = installed.get(null);
    installed.set(null, ctor.newInstance());
    try {
      int plain = 300;
      assertEquals(CeltiumGlStub.SUPPLY_NOTHING_TO_READ, CeltiumGlStub.celtium$supplyDecision(plain, 0),
              "a name nothing has written has no record and no backlog");
      assertTrue(CeltiumGlStub.celtium$levelHasNothingToRead(plain, 0), "the predicate it replaced agrees");

      CeltiumGlStub.celtium$glBindTexture(0x0DE1, plain);
      CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401, 0L);
      assertEquals(CeltiumGlStub.SUPPLY_NOW, CeltiumGlStub.celtium$supplyDecision(plain, 0),
              "the zero surface is a record the upload reads, so it is not 'nothing to read'");
      assertFalse(CeltiumGlStub.celtium$levelHasNothingToRead(plain, 0), "the predicate it replaced agrees");

      java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(8 * 8 * 4);
      try {
        org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(pixels), 0x21, 8 * 8 * 4L);
        CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401,
                org.lwjgl.system.MemoryUtil.memAddress(pixels));
      } finally {
        org.lwjgl.system.MemoryUtil.memFree(pixels);
      }
      assertEquals(CeltiumGlStub.SUPPLY_NOW, CeltiumGlStub.celtium$supplyDecision(plain, 0),
              "a real upload is content");

      // The attachment half: a name that is a framebuffer's colour attachment with no captured game pixels.
      int attachment = 301;
      CeltiumGlStub.celtium$bindFramebuffer(0x8CA9, 11);
      CeltiumGlStub.celtium$framebufferTexture2D(0x8CA9, 0x8CE0, 0x0DE1, attachment, 0);
      assertEquals(CeltiumGlStub.SUPPLY_WAIT_FRAMEBUFFER, CeltiumGlStub.celtium$supplyDecision(attachment, 0),
              "an attachment with nothing captured waits for the owner that publishes it");
      assertTrue(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(attachment), "the predicate it replaced agrees");

      // The framebuffer rule is asked first, so an attachment that also has nothing to read answers "wait", not
      // "nothing to read": the two deferrals are counted apart and the reason line differs.
      assertTrue(CeltiumGlStub.celtium$levelHasNothingToRead(attachment, 0),
              "and it also has nothing to read, which is why the order is the thing being pinned");

      CeltiumGlStub.celtium$glBindTexture(0x0DE1, attachment);
      java.nio.ByteBuffer attachmentPixels = org.lwjgl.system.MemoryUtil.memAlloc(4 * 4 * 4);
      try {
        org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(attachmentPixels), 0x55, 4 * 4 * 4L);
        CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 4, 4, 0, 0x1908, 0x1401,
                org.lwjgl.system.MemoryUtil.memAddress(attachmentPixels));
      } finally {
        org.lwjgl.system.MemoryUtil.memFree(attachmentPixels);
      }
      assertEquals(CeltiumGlStub.SUPPLY_NOW, CeltiumGlStub.celtium$supplyDecision(attachment, 0),
              "a captured game upload is content, so the attachment is supplied rather than deferred");
      assertFalse(CeltiumGlStub.celtium$mirrorWaitsForFramebuffer(attachment), "the predicate it replaced agrees");
    } finally {
      installed.set(null, previous);
    }
  }
}
