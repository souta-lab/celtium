package org.soutalab.celtium.vulkan;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Forge's loading screen ({@code net.minecraftforge.fml.client.SplashProgress}), drawn on the client thread into an
 * image the Vulkan swapchain presents.
 * <p>
 * Forge draws it on a second thread through a shared GL context, which a GL-less window cannot provide, so its thread
 * is not started. What is reproduced here is its frame, from its own sources: the 640x480 virtual space centred on the
 * window, the 512x512 Mojang logo, the memory bar, the first and the last two progress bars (400x20, 20 below their
 * text, 55 apart), the text in {@code textures/font/ascii.png} at twice its size with vanilla's glyph widths, the
 * animated Forge logo in the bottom-right corner ({@code fml:textures/gui/forge.png}, one frame per five steps) and
 * the colours, logo offset, rotation and memory switch of {@code config/splash.properties}.
 * <p>
 * Startup UI owned by Vulkan; no game framebuffer, GL name, GL state or shared context. Render thread only.
 */
final class NativeLoadingScreen {
    /** The colours and switches {@code splash.properties} sets, with Forge's defaults. */
    static final class Style {
        int background = 0xFFFFFF;
        int barBorder = 0xC0C0C0;
        int bar = 0xCB3D35;
        int barBackground = 0xFFFFFF;
        int memoryGood = 0x78CB34;
        int memoryWarn = 0xE6E84A;
        int memoryLow = 0xE42F2F;
        boolean showMemory = true;
        boolean rotate;
        int logoOffset;
    }

    /** One progress bar as Forge's {@code ProgressManager.ProgressBar} reports it. */
    static final class Bar {
        final String title;
        final String message;
        final int step;
        final int steps;

        Bar(String title, String message, int step, int steps) {
            this.title = title;
            this.message = message;
            this.step = step;
            this.steps = steps;
        }
    }

    private static final int BAR_WIDTH = 400;
    private static final int BAR_HEIGHT = 20;
    private static final int TEXT_HEIGHT_2 = 20;
    private static final int BAR_OFFSET = 55;

    private final VulkanContext context;
    private VulkanGlTexture image;
    private ByteBuffer pixels;
    private BufferedImage canvas;
    private BufferedImage font;
    private final int[] charWidth = new int[256];
    private final BufferedImage[] glyphs = new BufferedImage[256];
    private float memoryColorPercent;
    private long memoryColorChangeTime;

    NativeLoadingScreen(VulkanContext context) {
        this.context = context;
    }

    VulkanGlTexture update(int width, int height, Style style, BufferedImage logo, BufferedImage forge,
            BufferedImage fontImage, Bar[] bars, int angle) {
        if (this.canvas == null || this.canvas.getWidth() != width || this.canvas.getHeight() != height) {
            this.canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            if (this.pixels != null) MemoryUtil.memFree(this.pixels);
            this.pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        }
        if (fontImage != null && fontImage != this.font) {
            readFont(fontImage);
        }
        Graphics2D g = this.canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setColor(new Color(style.background));
            g.fillRect(0, 0, width, height);
            // glOrtho(320 - w/2, 320 + w/2, 240 + h/2, 240 - h/2): Forge's units are pixels around a 640x480 centre.
            g.translate(width / 2 - 320, height / 2 - 240);
            if (logo != null) {
                g.drawImage(logo, 320 - 256, 240 - 256, 512, 512, null);
            }
            if (style.showMemory) {
                AffineTransform saved = g.getTransform();
                g.translate(320 - BAR_WIDTH / 2, 20);
                drawMemoryBar(g, style);
                g.setTransform(saved);
            }
            if (bars.length > 0) {
                AffineTransform saved = g.getTransform();
                g.translate(320 - BAR_WIDTH / 2, 310);
                for (int i = 0; i < bars.length; i++) {
                    if (i > 0) {
                        g.translate(0, BAR_OFFSET);
                    }
                    drawBar(g, style, bars[i]);
                }
                g.setTransform(saved);
            }
            if (forge != null) {
                drawForgeLogo(g, style, forge, width, height, angle);
            }
        } finally {
            g.dispose();
        }
        // The shared presenter reads a bottom-origin source, as it does for the world's Vulkan frame image. Converted
        // from the raster's own int array: the per-row getRGB form cost most of a frame at 1920x1080, and this frame is
        // drawn on the thread that is loading the game.
        int[] argb = ((java.awt.image.DataBufferInt) this.canvas.getRaster().getDataBuffer()).getData();
        java.nio.IntBuffer out = this.pixels.clear().order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] row = new int[width];
        for (int y = height - 1, dst = 0; y >= 0; y--, dst += width) {
            int src = y * width;
            for (int x = 0; x < width; x++) {
                int c = argb[src + x];
                // ARGB to the bytes R, G, B, A (opaque) of a little-endian int: 0xFF_BB_GG_RR.
                row[x] = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >>> 16) & 0xFF);
            }
            out.put(dst, row);
        }
        this.pixels.limit(width * height * 4);
        if (this.image == null) this.image = VulkanGlTexture.create(this.context, width, height, true);
        this.image.uploadFromImage(width, height, this.pixels);
        return this.image;
    }

    private void drawBar(Graphics2D g, Style style, Bar bar) {
        drawString(g, bar.title + " - " + bar.message, 0, 0);
        AffineTransform saved = g.getTransform();
        g.translate(0, TEXT_HEIGHT_2);
        box(g, style.barBorder, 0, 0, BAR_WIDTH, BAR_HEIGHT);
        g.translate(1, 1);
        box(g, style.barBackground, 0, 0, BAR_WIDTH - 2, BAR_HEIGHT - 2);
        // Forge's own fill: (step + 1) / (steps + 1), because step can be 0.
        box(g, style.bar, 0, 0, (BAR_WIDTH - 2) * (bar.step + 1) / (bar.steps + 1), BAR_HEIGHT - 2);
        String progress = bar.step + "/" + bar.steps;
        drawString(g, progress, (BAR_WIDTH - 2) / 2 - stringWidth(progress), 2);
        g.setTransform(saved);
    }

    private void drawMemoryBar(Graphics2D g, Style style) {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024L / 1024L);
        int totalMemory = (int) (Runtime.getRuntime().totalMemory() / 1024L / 1024L);
        int freeMemory = (int) (Runtime.getRuntime().freeMemory() / 1024L / 1024L);
        int usedMemory = totalMemory - freeMemory;
        float usedPercent = usedMemory / (float) maxMemory;
        drawString(g, "Memory Used / Total", 0, 0);
        AffineTransform saved = g.getTransform();
        g.translate(0, TEXT_HEIGHT_2);
        box(g, style.barBorder, 0, 0, BAR_WIDTH, BAR_HEIGHT);
        g.translate(1, 1);
        box(g, style.barBackground, 0, 0, BAR_WIDTH - 2, BAR_HEIGHT - 2);
        long time = System.currentTimeMillis();
        if (usedPercent > this.memoryColorPercent || time - this.memoryColorChangeTime > 1000) {
            this.memoryColorChangeTime = time;
            this.memoryColorPercent = usedPercent;
        }
        int memoryBarColor = this.memoryColorPercent < 0.75f ? style.memoryGood
                : this.memoryColorPercent < 0.85f ? style.memoryWarn : style.memoryLow;
        box(g, style.memoryLow, (BAR_WIDTH - 2) * totalMemory / maxMemory - 2, 0, 2, BAR_HEIGHT - 2);
        box(g, memoryBarColor, 0, 0, (BAR_WIDTH - 2) * usedMemory / maxMemory, BAR_HEIGHT - 2);
        String progress = pad(usedMemory) + " MB / " + pad(maxMemory) + " MB";
        drawString(g, progress, (BAR_WIDTH - 2) / 2 - stringWidth(progress), 2);
        g.setTransform(saved);
    }

    private static String pad(int value) {
        String text = Integer.toString(value);
        while (text.length() < 4) text = " " + text;
        return text;
    }

    private void drawForgeLogo(Graphics2D g, Style style, BufferedImage forge, int width, int height, int angle) {
        int frameWidth = forge.getWidth();
        int frameHeight = forge.getHeight();
        int frames = 1;
        if (frameHeight > frameWidth && frameHeight % frameWidth == 0) {
            frames = frameHeight / frameWidth;
            frameHeight = frameWidth;
        }
        float fw = frameWidth / 2.0f;
        float fh = frameHeight / 2.0f;
        int frame = (angle / 5) % frames;
        AffineTransform saved = g.getTransform();
        if (style.rotate) {
            float sh = Math.max(fw, fh);
            g.translate(320 + width / 2.0 - sh - style.logoOffset, 240 + height / 2.0 - sh - style.logoOffset);
            g.rotate(Math.toRadians(angle));
        } else {
            g.translate(320 + width / 2.0 - fw - style.logoOffset, 240 + height / 2.0 - fh - style.logoOffset);
        }
        int sy = frame * frameHeight;
        g.drawImage(forge, Math.round(-fw), Math.round(-fh), Math.round(fw), Math.round(fh),
                0, sy, frameWidth, sy + frameHeight, null);
        g.setTransform(saved);
    }

    private static void box(Graphics2D g, int rgb, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.setColor(new Color(rgb));
        g.fillRect(x, y, w, h);
    }

    /** {@code FontRenderer.drawString(text, x, y, 0x000000)} at Forge's 2x scale: opaque black glyphs. */
    private void drawString(Graphics2D g, String text, int x, int y) {
        if (this.font == null) return;
        float cursor = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 256) continue;
            BufferedImage glyph = this.glyphs[c];
            if (glyph != null && c != ' ') {
                g.drawImage(glyph, x + Math.round(cursor * 2), y, glyph.getWidth() * 2, glyph.getHeight() * 2, null);
            }
            cursor += this.charWidth[c];
        }
    }

    /** {@code FontRenderer.getStringWidth} for the ASCII page. */
    private int stringWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 256) width += this.charWidth[c];
        }
        return width;
    }

    /**
     * Vanilla's {@code FontRenderer.readFontTexture}: a 16x16 grid of cells, each character as wide as its rightmost
     * non-transparent column, scaled to 8, plus one; the space is 4. Each glyph is kept pre-tinted black.
     */
    private void readFont(BufferedImage image) {
        this.font = image;
        int w = image.getWidth();
        int h = image.getHeight();
        int cellH = h / 16;
        int cellW = w / 16;
        float scale = 8.0f / cellW;
        for (int ch = 0; ch < 256; ch++) {
            int column = ch % 16;
            int rowIndex = ch / 16;
            int l;
            for (l = cellW - 1; l >= 0; l--) {
                boolean empty = true;
                for (int yy = 0; yy < cellH && empty; yy++) {
                    if ((image.getRGB(column * cellW + l, rowIndex * cellH + yy) >>> 24) != 0) empty = false;
                }
                if (!empty) break;
            }
            l++;
            this.charWidth[ch] = ch == 32 ? 4 : (int) (0.5 + l * scale) + 1;
            BufferedImage glyph = new BufferedImage(cellW, cellH, BufferedImage.TYPE_INT_ARGB);
            for (int yy = 0; yy < cellH; yy++) {
                for (int xx = 0; xx < cellW; xx++) {
                    int alpha = image.getRGB(column * cellW + xx, rowIndex * cellH + yy) >>> 24;
                    glyph.setRGB(xx, yy, alpha << 24);
                }
            }
            this.glyphs[ch] = glyph;
        }
        if (cellW != 8) {
            // A larger font page draws each cell at the 8-unit size vanilla scales it to.
            for (int ch = 0; ch < 256; ch++) {
                BufferedImage scaled = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gg = scaled.createGraphics();
                gg.drawImage(this.glyphs[ch], 0, 0, 8, 8, null);
                gg.dispose();
                this.glyphs[ch] = scaled;
            }
        }
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
