package org.soutalab.celtium.vulkan.gl;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.HashMap;
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
    Map<Integer,Integer> m = new HashMap<>();
    Map<Integer,Integer> inverse = new HashMap<>();
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
}
