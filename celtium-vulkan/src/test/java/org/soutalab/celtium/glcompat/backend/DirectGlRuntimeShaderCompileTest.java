package org.soutalab.celtium.glcompat.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The shader record's compile contract on the converter's program machine: the status query
 * answers the record's compiled flag, so a shader that only ever received its source must
 * answer 0, and the answer becomes 1 only once compilation has passed over the record. This
 * is the machine half of the glCompileShader route; the live half (the GL-less rewrite layer
 * forwarding the call here) is pinned in the forge122 module.
 */
class DirectGlRuntimeShaderCompileTest {

    private static final int GL_VERTEX_SHADER = 0x8B31;

    @Test
    void compileRaisesTheStatusAnswerAndNewSourceLowersItAgain() {
        DirectGlRuntime runtime = new DirectGlRuntime();
        int shader = runtime.createShader(GL_VERTEX_SHADER);
        runtime.shaderSource(shader, "void main() { }");
        assertEquals(0, runtime.getShaderi(shader, DirectGlRuntime.GL_COMPILE_STATUS),
                "a sourced shader that was never compiled answers 0");
        runtime.compileShader(shader);
        assertEquals(1, runtime.getShaderi(shader, DirectGlRuntime.GL_COMPILE_STATUS),
                "after compilation the status query answers 1");
        runtime.shaderSource(shader, "void main() { }");
        assertEquals(0, runtime.getShaderi(shader, DirectGlRuntime.GL_COMPILE_STATUS),
                "replacing the source lowers the flag until the next compile");
    }
}
