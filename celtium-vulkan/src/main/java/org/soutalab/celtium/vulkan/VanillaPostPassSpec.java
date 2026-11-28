package org.soutalab.celtium.vulkan;

import java.nio.FloatBuffer;

/** A collected ShaderGroup pass. Array inputs and accessors are defensive copies. */
public record VanillaPostPassSpec(String program,
                                  int inFbId, int inW, int inH, int outFbId, int outW, int outH,
                                  boolean inIsMain, boolean outIsMain, int[] auxFbIds, int[] auxW, int[] auxH,
                                  float[] push, int pushBytes, String[] blockNames, int[] blockOffsets,
                                  int groupMainTextureId, int groupMainFbId, int groupMainW, int groupMainH) {
    public VanillaPostPassSpec {
        auxFbIds = auxFbIds.clone();
        auxW = auxW.clone();
        auxH = auxH.clone();
        push = push.clone();
        blockNames = blockNames == null ? null : blockNames.clone();
        blockOffsets = blockOffsets == null ? null : blockOffsets.clone();
    }

    @Override public int[] auxFbIds() { return auxFbIds.clone(); }
    @Override public int[] auxW() { return auxW.clone(); }
    @Override public int[] auxH() { return auxH.clone(); }
    @Override public float[] push() { return push.clone(); }
    @Override public String[] blockNames() { return blockNames == null ? null : blockNames.clone(); }
    @Override public int[] blockOffsets() { return blockOffsets == null ? null : blockOffsets.clone(); }

    int uniformWordCount() { return push.length; }

    void writeUniforms(FloatBuffer destination) {
        destination.put(push, 0, pushBytes / Float.BYTES);
    }
}
