package org.soutalab.celtium.shaderpack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A validated single-entrypoint SPIR-V stage blob; does NOT certify Vulkan descriptor,
 * vertex/fragment interface or shadow-pass compatibility. Only bytecode structure,
 * version and execution model are checked here; full driver validation belongs to S3.
 */
public record SpirvModule(Stage stage, byte[] binary, String sha256, int spirvVersion) {
    public SpirvModule {
        binary = binary.clone();
    }

    @Override public byte[] binary() { return binary.clone(); }

    public static SpirvModule inspect(Stage expected, byte[] raw) {
        if (expected != Stage.VERTEX && expected != Stage.FRAGMENT) {
            throw new IllegalArgumentException("S2 stage not supported yet: " + expected);
        }
        if (raw.length < 20 || raw.length > 8 * 1024 * 1024 || (raw.length & 3) != 0) {
            throw new IllegalArgumentException("SPIR-V is empty, truncated, unaligned or oversized");
        }
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(0) != 0x07230203 || b.getInt(16) != 0 || b.getInt(12) <= 0) {
            throw new IllegalArgumentException("invalid SPIR-V header");
        }
        int version = b.getInt(4);
        if (version < 0x00010000 || version > 0x00010500) {
            throw new IllegalArgumentException("unsupported SPIR-V version for Vulkan 1.2: " + Integer.toHexString(version));
        }
        int expectedModel = expected == Stage.VERTEX ? 0 : 4;
        boolean found = false;
        int nwords = raw.length / 4;
        for (int i = 5; i < nwords;) {
            int op = b.getInt(i * 4);
            int count = op >>> 16;
            int code = op & 0xffff;
            if (count == 0 || count > nwords - i) {
                throw new IllegalArgumentException("malformed SPIR-V instruction length at word " + i);
            }
            if (code == 15) { // OpEntryPoint: execution model, entry point id, name
                if (count < 4) throw new IllegalArgumentException("invalid SPIR-V entrypoint");
                int model = b.getInt((i + 1) * 4);
                if (model == expectedModel) found = true;
                else throw new IllegalArgumentException("SPIR-V entrypoint stage mismatch: " + model);
            }
            i += count;
        }
        if (!found) throw new IllegalArgumentException("SPIR-V missing expected stage entrypoint");
        try {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            return new SpirvModule(expected, raw, sha, version);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
