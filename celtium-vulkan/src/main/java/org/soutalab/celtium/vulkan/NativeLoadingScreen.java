package org.soutalab.celtium.vulkan;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** Startup UI owned by Vulkan; no game framebuffer, GL name, GL state or shared context. */
final class NativeLoadingScreen {
    private final VulkanContext context;
    private VulkanGlTexture image;
    private ByteBuffer pixels;
    private BufferedImage canvas;

    NativeLoadingScreen(VulkanContext context) {
        this.context = context;
    }

    VulkanGlTexture update(int width, int height, BufferedImage logo, String[] labels, int[] steps, int[] totals) {
        if (this.canvas == null || this.canvas.getWidth() != width || this.canvas.getHeight() != height) {
            this.canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            if (this.pixels != null) MemoryUtil.memFree(this.pixels);
            this.pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        }
        Graphics2D g = this.canvas.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            int scale = Math.max(1, Math.min(width / 640, height / 360));
            if (logo != null) {
                int edge = Math.min(256 * scale, height / 2);
                g.drawImage(logo, (width - edge) / 2, Math.max(0, height / 2 - edge), edge, edge, null);
            }
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12 * scale));
            int barWidth = Math.min(width - 32 * scale, 480 * scale);
            int left = (width - barWidth) / 2;
            int top = height / 2 + 16 * scale;
            for (int i = 0; i < Math.min(3, labels.length); i++) {
                int y = top + i * 48 * scale;
                g.setColor(new Color(45, 45, 45));
                String text = labels[i] + "  " + steps[i] + "/" + totals[i];
                while (text.length() > 1 && g.getFontMetrics().stringWidth(text) > barWidth) {
                    text = text.substring(0, text.length() - 1);
                }
                g.drawString(text, left, y);
                g.setColor(new Color(220, 220, 220));
                g.fillRect(left, y + 8 * scale, barWidth, 8 * scale);
                g.setColor(new Color(190, 45, 45));
                int fill = totals[i] <= 0 ? 0 : (int) ((long) barWidth * Math.max(0, Math.min(steps[i], totals[i])) / totals[i]);
                g.fillRect(left, y + 8 * scale, fill, 8 * scale);
            }
        } finally {
            g.dispose();
        }
        this.pixels.clear();
        // The shared presenter reads a bottom-origin source, as it does for the world's Vulkan frame image.
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                int argb = this.canvas.getRGB(x, y);
                this.pixels.put((byte) (argb >>> 16)).put((byte) (argb >>> 8)).put((byte) argb).put((byte) 255);
            }
        }
        this.pixels.flip();
        if (this.image == null) this.image = VulkanGlTexture.create(this.context, width, height, true);
        this.image.uploadFromImage(width, height, this.pixels);
        return this.image;
    }

    /** Caller has confirmed completion of the present that last read the image. */
    void delete() {
        if (this.image != null) this.image.delete();
        if (this.pixels != null) MemoryUtil.memFree(this.pixels);
        this.image = null;
        this.pixels = null;
        this.canvas = null;
    }
}
