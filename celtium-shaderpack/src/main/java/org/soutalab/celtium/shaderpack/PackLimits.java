package org.soutalab.celtium.shaderpack;

/**
 * Hard resource bounds applied while reading a pack. The defaults are far above
 * anything a real OptiFine/Iris pack needs (BSL v10.1.8 ships 309 entries and
 * ~1.5 MB total) and far below anything that could hurt the JVM or disk.
 */
public record PackLimits(
        int maxEntries,
        long maxTotalUncompressedBytes,
        long maxFileBytes,
        double maxCompressionRatio,
        long minBytesForRatioCheck,
        int maxIncludeDepth,
        int maxIncludeExpansions,
        long maxExpandedBytesPerProgram,
        int maxLineLength) {

    public static final PackLimits DEFAULT = new PackLimits(
            16_384,               // maxEntries
            512L * 1024 * 1024,   // 512 MiB total uncompressed
            16L * 1024 * 1024,    // 16 MiB per file
            250.0,                // compression ratio, only when >= minBytesForRatioCheck
            1024 * 1024,          // 1 MiB floor before the ratio is considered
            64,                   // include depth
            8192,                 // total #include expansions per program
            8L * 1024 * 1024,     // 8 MiB expanded source per program
            65_536);              // characters per line
}
