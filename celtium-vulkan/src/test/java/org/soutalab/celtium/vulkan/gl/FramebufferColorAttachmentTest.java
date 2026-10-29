package org.soutalab.celtium.vulkan.gl;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
class FramebufferColorAttachmentTest {
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
