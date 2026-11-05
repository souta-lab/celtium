package org.soutalab.celtium.vulkan;

/**
 * Text signatures of a frame's pixels and a mesh's first vertices, for the diagnostic log lines that compare a
 * picture without dumping it. Moved out of {@link VulkanTerrainGpuBackend} unchanged (2026-11-06).
 */
final class PixelSignatures {
    private PixelSignatures() {
    }

    /**
     * The first four vertices of a group's mesh, as the floats the shader will read.
     * <p>
     * A quad that covers the framebuffer and samples a picture the pair holds can still paint one colour: what decides
     * which texel each fragment reads is the vertex's own texture coordinates, and a quad whose four UVs are equal -
     * or outside {@code 0..1} on a clamped sampler - is exactly the "uniform white with the picture in the source"
     * case. The mesh copies vertices verbatim at the captured stride and declares no attribute offsets of its own
     * ({@code Layout(stride, 0, -1, -1, -1, -1)}), so the floats are printed as they lie rather than at a guessed
     * offset: six floats cover position, texture coordinates and colour of every layout this path carries.
     */
    static String celtium$meshVertexPreview(VulkanDynamicMesh mesh) {
        if (mesh == null || mesh.isEmpty()) {
            return "none";
        }
        long base = mesh.vertexAddress();
        int stride = mesh.layout() == null ? 0 : mesh.layout().stride;
        int count = mesh.vertexCount();
        if (base == 0L || stride < 24 || count <= 0) {
            return "stride=" + stride + " count=" + count;
        }
        StringBuilder preview = new StringBuilder("stride=").append(stride).append(" count=").append(count)
                .append(" v={");
        // Twelve, not four: the splash's own group is two quads (the full-screen background whose four texture
        // coordinates are all (0,0), then the logo), and the logo's coordinates are the ones that say whether the
        // captured geometry runs backwards in x. Printing four vertices showed the background twice and the logo
        // never.
        int shown = Math.min(12, count);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                preview.append(' ');
            }
            for (int k = 0; k < 6; k++) {
                if (k > 0) {
                    preview.append(',');
                }
                preview.append(VulkanTerrainGpuBackend.round3(org.lwjgl.system.MemoryUtil.memGetFloat(base + (long) i * stride + k * 4L)));
            }
            // The colour attribute is four unsigned bytes at 20, and the shader multiplies the texel by it: read as a
            // float it prints as 0.0 for both 0x00000000 and 0xFFFFFFFF, which is exactly the difference between a
            // fragment that keeps the texture and one that is multiplied to black.
            preview.append("/0x").append(Integer.toHexString(
                    org.lwjgl.system.MemoryUtil.memGetInt(base + (long) i * stride + 20L)));
        }
        return preview.append('}').toString();
    }

    /**
     * The sky band of a read-back frame image, as the mean of the picture's top rows and of two rows further down.
     * <p>
     * The band is where the two frames of a still scene differ, and its shape is the whole question: a gradient from the
     * sky colour at the top to the fog colour at the horizon is the plane drawn with the world's fog, and one flat value
     * equal to the clear colour is the plane's pixels absent. Three rows rather than one, because a single row cannot
     * tell a gradient from a flat band that happens to match one of its ends.
     * <p>
     * The image is in GL texture order - row 0 is the picture's bottom - so the picture's top rows are the last ones.
     */
    static String celtium$skyBandSignature(int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            return "skyBand=none";
        }
        return "skyBand[top=" + celtium$rowMean(pixels, width, height, height - 4)
                + " quarter=" + celtium$rowMean(pixels, width, height, height - height / 4)
                + " middle=" + celtium$rowMean(pixels, width, height, height / 2)
                + " bottom=" + celtium$rowMean(pixels, width, height, 4) + "]";
    }

    /** {@return the mean colour of one image row}, in the image's own row order. */
    static String celtium$rowMean(int[] pixels, int width, int height, int row) {
        int y = Math.max(0, Math.min(height - 1, row));
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        for (int x = 0; x < width; x++) {
            int value = pixels[y * width + x];
            red += (value >> 16) & 0xFF;
            green += (value >> 8) & 0xFF;
            blue += value & 0xFF;
        }
        return "(" + (red / width) + "," + (green / width) + "," + (blue / width) + ")";
    }

    /**
     * The centre and the corner of a read-back rectangle as numbers.
     * <p>
     * The splash's logo is centred and dark on a white field, so a centre well below the corner is the picture and a
     * centre equal to the corner is a blank image. Reported beside the readback so "the pair is blank when the
     * composite samples it" and "the composite lost a picture the pair held" are told apart by numbers rather than by
     * a picture someone has to look at.
     */
    static String celtium$regionSignature(int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            return "signature=none";
        }
        long centreR = 0L;
        long centreG = 0L;
        long centreB = 0L;
        long cornerR = 0L;
        long cornerG = 0L;
        long cornerB = 0L;
        long centreCount = 0L;
        long cornerCount = 0L;
        long dark = 0L;
        long hudR = 0L;
        long hudG = 0L;
        long hudB = 0L;
        long hudCount = 0L;
        long crossR = 0L;
        long crossG = 0L;
        long crossB = 0L;
        long crossCount = 0L;
        int centreX = width / 2;
        int centreY = height / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = pixels[y * width + x];
                int red = (value >> 16) & 0xFF;
                int green = (value >> 8) & 0xFF;
                int blue = value & 0xFF;
                if (Math.abs(x - centreX) < 256 && Math.abs(y - centreY) < 256) {
                    centreR += red;
                    centreG += green;
                    centreB += blue;
                    centreCount++;
                }
                if (x < 64 && y < 64) {
                    cornerR += red;
                    cornerG += green;
                    cornerB += blue;
                    cornerCount++;
                }
                // The HUD's two landmarks, in this image's own row order (row 0 is the picture's bottom, which is the
                // hotbar's row): the hotbar's band, and the crosshair's patch around the picture's centre. They are
                // what says whether the HUD's recorded draws left pixels here at all - a band that stays the world's
                // colour across every window of the frame is "never written", and one that takes the hotbar's colours
                // in one window and the world's again in the next is "written and then painted over".
                if (y < 32 && Math.abs(x - centreX) < 120) {
                    hudR += red;
                    hudG += green;
                    hudB += blue;
                    hudCount++;
                }
                if (Math.abs(x - centreX) < 8 && Math.abs(y - centreY) < 8) {
                    crossR += red;
                    crossG += green;
                    crossB += blue;
                    crossCount++;
                }
                if ((red + green + blue) / 3 < 200) {
                    dark++;
                }
            }
        }
        return "centreRgb=(" + (centreCount == 0 ? -1 : centreR / centreCount) + ","
                + (centreCount == 0 ? -1 : centreG / centreCount) + ","
                + (centreCount == 0 ? -1 : centreB / centreCount) + ")"
                + " cornerRgb=(" + (cornerCount == 0 ? -1 : cornerR / cornerCount) + ","
                + (cornerCount == 0 ? -1 : cornerG / cornerCount) + ","
                + (cornerCount == 0 ? -1 : cornerB / cornerCount) + ")"
                + " hotbarRgb=(" + (hudCount == 0 ? -1 : hudR / hudCount) + ","
                + (hudCount == 0 ? -1 : hudG / hudCount) + ","
                + (hudCount == 0 ? -1 : hudB / hudCount) + ")"
                + " crossRgb=(" + (crossCount == 0 ? -1 : crossR / crossCount) + ","
                + (crossCount == 0 ? -1 : crossG / crossCount) + ","
                + (crossCount == 0 ? -1 : crossB / crossCount) + ")"
                + " darkPixels=" + dark;
    }
}
