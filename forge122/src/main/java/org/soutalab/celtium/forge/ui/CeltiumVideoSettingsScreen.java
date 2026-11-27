package org.soutalab.celtium.forge.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.soutalab.celtium.forge.ui.OptionEntry.ConfigTarget;
import org.soutalab.celtium.forge.ui.OptionEntry.CycleOption;
import org.soutalab.celtium.forge.ui.OptionEntry.OptionGroup;
import org.soutalab.celtium.forge.ui.OptionEntry.OptionPage;
import org.soutalab.celtium.forge.ui.OptionEntry.SliderOption;
import org.soutalab.celtium.forge.ui.OptionEntry.ToggleOption;
import org.taumc.celeritas.CeleritasVintage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Celtium's native Video Settings screen, written directly on vanilla {@link GuiScreen} using
 * {@link Gui#drawRect} and {@link net.minecraft.client.gui.FontRenderer}.
 */
public class CeltiumVideoSettingsScreen extends GuiScreen {

    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 32;
    private static final int GROUP_HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 2;
    private static final int GROUP_GAP = 6;
    private static final int SCROLLBAR_WIDTH = 6;

    // Palette
    private static final int BG_PANEL = 0xCC10141C;
    private static final int BG_ROW = 0xB0181F2B;
    private static final int BG_ROW_HOVER = 0xD0243044;
    private static final int BG_ROW_CHANGED = 0xC01C2C3B;
    private static final int BG_TAB = 0xB0181F2B;
    private static final int BG_TAB_ACTIVE = 0xE02A3C58;
    private static final int BG_TAB_HOVER = 0xD0223048;
    private static final int ACCENT = 0xFF4FA8FF;
    private static final int BORDER = 0xFF334257;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFB0BCCF;
    private static final int TEXT_CHANGED = 0xFF7CC4FF;
    private static final int TEXT_DISABLED = 0xFF6B7482;

    private final GuiScreen prevScreen;
    private final CeltiumConfig celtiumOpts;
    private final MinecraftOptionsStorage vanillaOpts;
    private final List<OptionPage> pages;

    private int activePageIndex;
    private int scrollOffset;
    private int maxScroll;

    private SliderOption draggingSlider;
    private int draggingSliderLeft;
    private int draggingSliderWidth;

    private final List<RowLayout> visibleRows = new ArrayList<>();

    private record RowLayout(OptionEntry<?> entry, int x, int y, int w, int h, int controlX, int controlW) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public CeltiumVideoSettingsScreen(GuiScreen prevScreen) {
        this.prevScreen = prevScreen;
        this.celtiumOpts = CeleritasVintage.options();
        this.vanillaOpts = new MinecraftOptionsStorage();
        this.pages = CeltiumVideoSettingsPages.createPages(this.celtiumOpts, this.vanillaOpts);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        clampScroll();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        this.draggingSlider = null;
    }

    public List<OptionPage> getPages() {
        return this.pages;
    }

    public int getActivePageIndex() {
        return this.activePageIndex;
    }

    public void selectPage(int index) {
        if (index >= 0 && index < this.pages.size() && index != this.activePageIndex) {
            this.activePageIndex = index;
            this.scrollOffset = 0;
            this.draggingSlider = null;
            clampScroll();
        }
    }

    public boolean hasPendingChanges() {
        for (OptionPage page : this.pages) {
            for (OptionGroup group : page.getGroups()) {
                for (OptionEntry<?> entry : group.getOptions()) {
                    if (entry.hasChanged()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void undoChanges() {
        for (OptionPage page : this.pages) {
            for (OptionGroup group : page.getGroups()) {
                for (OptionEntry<?> entry : group.getOptions()) {
                    entry.reset();
                }
            }
        }
    }

    public void applyChanges() {
        Set<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
        boolean dirtyVanilla = false;
        boolean dirtyCeltium = false;

        for (OptionPage page : this.pages) {
            for (OptionGroup group : page.getGroups()) {
                for (OptionEntry<?> entry : group.getOptions()) {
                    if (entry.hasChanged()) {
                        flags.addAll(entry.getFlags());
                        if (entry.getTarget() == ConfigTarget.VANILLA) {
                            dirtyVanilla = true;
                        } else {
                            dirtyCeltium = true;
                        }
                        entry.apply();
                    }
                }
            }
        }

        if (dirtyVanilla) {
            this.vanillaOpts.save();
        }
        if (dirtyCeltium && !this.celtiumOpts.isReadOnly()) {
            this.celtiumOpts.save();
        }

        applySideEffects(flags);
    }

    private void applySideEffects(Set<OptionFlag> flags) {
        Minecraft client = this.mc != null ? this.mc : Minecraft.getMinecraft();
        if (client == null) {
            return;
        }
        if (client.world != null) {
            if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
                client.renderGlobal.loadRenderers();
            } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                client.renderGlobal.setDisplayListEntitiesDirty();
            }
        }
        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            client.getTextureMapBlocks().setMipmapLevels(client.gameSettings.mipmapLevels);
            client.refreshResources();
        }
    }

    private int listLeft() {
        int panelWidth = Math.min(440, this.width - 24);
        return (this.width - panelWidth) / 2;
    }

    private int listWidth() {
        return Math.min(440, this.width - 24);
    }

    private int listTop() {
        return HEADER_HEIGHT + 4;
    }

    private int listBottom() {
        return this.height - FOOTER_HEIGHT - 4;
    }

    private int computeContentHeight(OptionPage page) {
        if (page.isPlaceholder()) {
            return 80;
        }
        int y = 4;
        for (OptionGroup group : page.getGroups()) {
            y += GROUP_HEADER_HEIGHT;
            y += group.getOptions().size() * (ROW_HEIGHT + ROW_GAP);
            y += GROUP_GAP;
        }
        return y;
    }

    private void clampScroll() {
        OptionPage page = this.pages.get(this.activePageIndex);
        int viewHeight = Math.max(1, listBottom() - listTop());
        int contentHeight = computeContentHeight(page);
        this.maxScroll = Math.max(0, contentHeight - viewHeight);
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        } else if (this.scrollOffset > this.maxScroll) {
            this.scrollOffset = this.maxScroll;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        clampScroll();

        int left = listLeft();
        int width = listWidth();
        int top = listTop();
        int bottom = listBottom();

        // Header & tabs
        drawTabBar(left, width, mouseX, mouseY);

        // Main panel background
        Gui.drawRect(left - 2, top - 2, left + width + 2, bottom + 2, BORDER);
        Gui.drawRect(left - 1, top - 1, left + width + 1, bottom + 1, BG_PANEL);

        OptionPage page = this.pages.get(this.activePageIndex);
        this.visibleRows.clear();

        // Scissor to list viewport
        ScaledResolution sr = new ScaledResolution(this.mc);
        int scale = sr.getScaleFactor();
        int scissorX = (left - 1) * scale;
        int scissorY = (this.height - bottom - 1) * scale;
        int scissorW = (width + 2) * scale;
        int scissorH = Math.max(0, (bottom - top + 2) * scale);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        OptionEntry<?> hoveredEntry = null;
        if (page.isPlaceholder()) {
            drawPlaceholderPage(page, left, top, width);
        } else {
            hoveredEntry = drawOptionPage(page, left, top, bottom, width, mouseX, mouseY);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar
        if (this.maxScroll > 0) {
            drawScrollbar(left + width - SCROLLBAR_WIDTH - 2, top + 2, bottom - top - 4);
        }

        // Footer buttons (Undo / Apply / Done)
        drawFooterButtons(left, width, mouseX, mouseY);

        // Tooltip for hovered row
        if (hoveredEntry != null && mouseY >= top && mouseY <= bottom) {
            drawOptionTooltip(hoveredEntry, mouseX, mouseY);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static final int TAB_GAP = 3;
    private static final int TAB_Y = 10;
    private static final int TAB_HEIGHT = 22;

    /**
     * Width of each tab, sized to its title text so no title is ever clipped: every tab gets its
     * natural width (title + padding) and any surplus panel width is shared evenly. Only when the
     * panel is too narrow for the natural widths do tabs shrink proportionally (the draw code
     * still trims as a last resort).
     */
    private int[] tabWidths(int totalWidth) {
        int count = this.pages.size();
        int[] widths = new int[count];
        int natural = 0;
        for (int i = 0; i < count; i++) {
            widths[i] = this.fontRenderer.getStringWidth(this.pages.get(i).getTitle()) + 18;
            natural += widths[i];
        }
        int available = totalWidth - TAB_GAP * (count - 1);
        int used = 0;
        if (natural >= available) {
            for (int i = 0; i < count; i++) {
                if (i == count - 1) {
                    widths[i] = Math.max(24, available - used);
                } else {
                    widths[i] = Math.max(24, (int) ((long) widths[i] * available / natural));
                    used += widths[i];
                }
            }
            return widths;
        }
        int surplusEach = (available - natural) / count;
        for (int i = 0; i < count; i++) {
            if (i == count - 1) {
                widths[i] = available - used;
            } else {
                widths[i] += surplusEach;
                used += widths[i];
            }
        }
        return widths;
    }

    private void drawTabBar(int left, int totalWidth, int mouseX, int mouseY) {
        int tabCount = this.pages.size();
        int[] widths = tabWidths(totalWidth);
        int tabY = TAB_Y;
        int tabH = TAB_HEIGHT;

        int tx = left;
        for (int i = 0; i < tabCount; i++) {
            int tw = widths[i];
            boolean active = (i == this.activePageIndex);
            boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= tabY && mouseY < tabY + tabH;

            int bg = active ? BG_TAB_ACTIVE : (hovered ? BG_TAB_HOVER : BG_TAB);
            Gui.drawRect(tx, tabY, tx + tw, tabY + tabH, bg);
            if (active) {
                Gui.drawRect(tx, tabY + tabH - 2, tx + tw, tabY + tabH, ACCENT);
            } else {
                Gui.drawRect(tx, tabY + tabH - 1, tx + tw, tabY + tabH, BORDER);
            }

            String title = this.pages.get(i).getTitle();
            String clipped = this.fontRenderer.trimStringToWidth(title, tw - 6);
            int textX = tx + (tw - this.fontRenderer.getStringWidth(clipped)) / 2;
            int textY = tabY + (tabH - this.fontRenderer.FONT_HEIGHT) / 2;
            this.fontRenderer.drawStringWithShadow(clipped, textX, textY, active ? TEXT_PRIMARY : TEXT_MUTED);
            tx += tw + TAB_GAP;
        }
    }

    private void drawPlaceholderPage(OptionPage page, int left, int top, int width) {
        int textX = left + 14;
        int y = top + 18;
        String heading = page.getTitle();
        this.fontRenderer.drawStringWithShadow(heading, textX, y, ACCENT);
        y += 16;
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(page.getPlaceholderNotice(), width - 28);
        for (String line : lines) {
            this.fontRenderer.drawStringWithShadow(line, textX, y, TEXT_MUTED);
            y += 11;
        }
    }

    private OptionEntry<?> drawOptionPage(OptionPage page, int left, int top, int bottom, int width,
                                          int mouseX, int mouseY) {
        int contentWidth = this.maxScroll > 0 ? width - SCROLLBAR_WIDTH - 10 : width - 8;
        int rowX = left + 4;
        int y = top + 4 - this.scrollOffset;
        OptionEntry<?> hoveredEntry = null;

        for (OptionGroup group : page.getGroups()) {
            String title = group.getTitle();
            if (!title.isEmpty() && y + GROUP_HEADER_HEIGHT >= top && y <= bottom) {
                this.fontRenderer.drawStringWithShadow(title, rowX + 4, y + 5, ACCENT);
                Gui.drawRect(rowX + 2, y + GROUP_HEADER_HEIGHT - 2, rowX + contentWidth - 2, y + GROUP_HEADER_HEIGHT - 1, BORDER);
            }
            y += GROUP_HEADER_HEIGHT;

            for (OptionEntry<?> entry : group.getOptions()) {
                if (y + ROW_HEIGHT >= top && y <= bottom) {
                    int controlW = Math.min(148, Math.max(96, contentWidth / 3));
                    int controlX = rowX + contentWidth - controlW - 6;
                    RowLayout layout = new RowLayout(entry, rowX, y, contentWidth, ROW_HEIGHT, controlX, controlW);
                    this.visibleRows.add(layout);

                    boolean hovered = mouseY >= top && mouseY <= bottom && layout.contains(mouseX, mouseY);
                    if (hovered) {
                        hoveredEntry = entry;
                    }
                    drawOptionRow(layout, hovered);
                }
                y += ROW_HEIGHT + ROW_GAP;
            }
            y += GROUP_GAP;
        }

        return hoveredEntry;
    }

    private void drawOptionRow(RowLayout layout, boolean hovered) {
        OptionEntry<?> entry = layout.entry();
        boolean changed = entry.hasChanged();
        int bg = hovered ? BG_ROW_HOVER : (changed ? BG_ROW_CHANGED : BG_ROW);
        Gui.drawRect(layout.x(), layout.y(), layout.x() + layout.w(), layout.y() + layout.h(), bg);
        if (changed) {
            Gui.drawRect(layout.x(), layout.y(), layout.x() + 2, layout.y() + layout.h(), ACCENT);
        }

        int labelMaxW = Math.max(40, layout.controlX() - layout.x() - 12);
        String label = this.fontRenderer.trimStringToWidth(entry.getName(), labelMaxW);
        int textY = layout.y() + (layout.h() - this.fontRenderer.FONT_HEIGHT) / 2;
        this.fontRenderer.drawStringWithShadow(label, layout.x() + 8, textY, changed ? TEXT_CHANGED : TEXT_PRIMARY);

        if (entry instanceof SliderOption slider) {
            drawSliderControl(slider, layout.controlX(), layout.y(), layout.controlW(), layout.h());
        } else if (entry instanceof ToggleOption toggle) {
            drawToggleControl(toggle, layout.controlX(), layout.y(), layout.controlW(), layout.h());
        } else if (entry instanceof CycleOption<?> cycle) {
            drawCycleControl(cycle, layout.controlX(), layout.y(), layout.controlW(), layout.h());
        }
    }

    private void drawSliderControl(SliderOption slider, int cx, int y, int cw, int h) {
        String val = slider.getDisplayValue();
        int valW = this.fontRenderer.getStringWidth(val);
        int trackLeft = cx;
        int trackRight = cx + cw - valW - 8;
        int trackW = Math.max(30, trackRight - trackLeft);
        int trackY = y + h / 2 - 1;

        Gui.drawRect(trackLeft, trackY, trackLeft + trackW, trackY + 2, 0xFF4A586E);
        int fillW = Math.round(slider.getFraction() * trackW);
        Gui.drawRect(trackLeft, trackY, trackLeft + fillW, trackY + 2, ACCENT);
        int thumbX = trackLeft + fillW;
        Gui.drawRect(thumbX - 2, y + 5, thumbX + 2, y + h - 5, TEXT_PRIMARY);

        int textY = y + (h - this.fontRenderer.FONT_HEIGHT) / 2;
        this.fontRenderer.drawStringWithShadow(val, cx + cw - valW, textY, TEXT_PRIMARY);
    }

    private void drawToggleControl(ToggleOption toggle, int cx, int y, int cw, int h) {
        boolean on = Boolean.TRUE.equals(toggle.getValue());
        int boxSize = 12;
        int boxX = cx + cw - boxSize - 2;
        int boxY = y + (h - boxSize) / 2;

        Gui.drawRect(boxX - 1, boxY - 1, boxX + boxSize + 1, boxY + boxSize + 1, BORDER);
        Gui.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, on ? ACCENT : 0xFF1A222E);

        String val = toggle.getDisplayValue();
        int valW = this.fontRenderer.getStringWidth(val);
        int textY = y + (h - this.fontRenderer.FONT_HEIGHT) / 2;
        this.fontRenderer.drawStringWithShadow(val, boxX - valW - 6, textY, on ? TEXT_PRIMARY : TEXT_MUTED);
    }

    private void drawCycleControl(CycleOption<?> cycle, int cx, int y, int cw, int h) {
        String val = cycle.getDisplayValue();
        String clipped = this.fontRenderer.trimStringToWidth(val, cw - 8);
        int valW = this.fontRenderer.getStringWidth(clipped);
        int textY = y + (h - this.fontRenderer.FONT_HEIGHT) / 2;
        this.fontRenderer.drawStringWithShadow(clipped, cx + cw - valW - 2, textY, TEXT_PRIMARY);
    }

    private void drawScrollbar(int x, int y, int height) {
        Gui.drawRect(x, y, x + SCROLLBAR_WIDTH, y + height, 0x801A222E);
        OptionPage page = this.pages.get(this.activePageIndex);
        int contentH = Math.max(1, computeContentHeight(page));
        int thumbH = Math.max(20, (int) ((float) height * height / contentH));
        int trackRange = Math.max(1, height - thumbH);
        int thumbY = y + (int) ((float) this.scrollOffset / this.maxScroll * trackRange);
        Gui.drawRect(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbH, ACCENT);
    }

    private void drawFooterButtons(int left, int width, int mouseX, int mouseY) {
        boolean changed = hasPendingChanges();
        int btnW = 76;
        int btnH = 20;
        int btnY = this.height - FOOTER_HEIGHT + 6;
        int right = left + width;

        int doneX = right - btnW;
        int applyX = doneX - btnW - 6;
        int undoX = applyX - btnW - 6;

        drawActionButton(undoX, btnY, btnW, btnH, I18n.format("celtium.options.buttons.undo"), changed, mouseX, mouseY);
        drawActionButton(applyX, btnY, btnW, btnH, I18n.format("celtium.options.buttons.apply"), changed, mouseX, mouseY);
        drawActionButton(doneX, btnY, btnW, btnH, I18n.format("gui.done"), !changed, mouseX, mouseY);
    }

    private void drawActionButton(int x, int y, int w, int h, String label, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = !enabled ? 0x80141A24 : (hovered ? BG_TAB_ACTIVE : BG_TAB);
        Gui.drawRect(x, y, x + w, y + h, BORDER);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        int textX = x + (w - this.fontRenderer.getStringWidth(label)) / 2;
        int textY = y + (h - this.fontRenderer.FONT_HEIGHT) / 2;
        this.fontRenderer.drawStringWithShadow(label, textX, textY, enabled ? TEXT_PRIMARY : TEXT_DISABLED);
    }

    private void drawOptionTooltip(OptionEntry<?> entry, int mouseX, int mouseY) {
        int maxW = Math.min(260, this.width - 24);
        List<String> lines = new ArrayList<>();
        for (String paragraph : entry.getTooltip().split("\n")) {
            if (paragraph.isEmpty()) {
                lines.add("");
            } else {
                lines.addAll(this.fontRenderer.listFormattedStringToWidth(paragraph, maxW));
            }
        }
        if (entry.getImpact() != null) {
            lines.add("");
            lines.add(I18n.format("celtium.options.performance_impact_string", entry.getImpact().formatted()));
        }

        int boxW = 0;
        for (String line : lines) {
            boxW = Math.max(boxW, this.fontRenderer.getStringWidth(line));
        }
        boxW += 12;
        int boxH = lines.size() * 10 + 10;

        int bx = Math.min(mouseX + 10, this.width - boxW - 6);
        int by = mouseY + 12;
        if (by + boxH > this.height - 6) {
            by = Math.max(6, mouseY - boxH - 8);
        }
        bx = Math.max(6, bx);

        Gui.drawRect(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, ACCENT);
        Gui.drawRect(bx, by, bx + boxW, by + boxH, 0xF0101620);

        int ty = by + 5;
        for (String line : lines) {
            this.fontRenderer.drawStringWithShadow(line, bx + 6, ty, TEXT_PRIMARY);
            ty += 10;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int left = listLeft();
        int width = listWidth();

        // Check tab bar clicks
        int tabCount = this.pages.size();
        int[] widths = tabWidths(width);
        int tabY = TAB_Y;
        int tabH = TAB_HEIGHT;
        if (mouseButton == 0 && mouseY >= tabY && mouseY < tabY + tabH) {
            int tx = left;
            for (int i = 0; i < tabCount; i++) {
                int tw = widths[i];
                if (mouseX >= tx && mouseX < tx + tw) {
                    selectPage(i);
                    return;
                }
                tx += tw + TAB_GAP;
            }
        }

        // Check footer button clicks
        if (mouseButton == 0) {
            boolean changed = hasPendingChanges();
            int btnW = 76;
            int btnH = 20;
            int btnY = this.height - FOOTER_HEIGHT + 6;
            int right = left + width;
            int doneX = right - btnW;
            int applyX = doneX - btnW - 6;
            int undoX = applyX - btnW - 6;

            if (mouseY >= btnY && mouseY < btnY + btnH) {
                if (changed && mouseX >= undoX && mouseX < undoX + btnW) {
                    undoChanges();
                    return;
                }
                if (changed && mouseX >= applyX && mouseX < applyX + btnW) {
                    applyChanges();
                    return;
                }
                if (!changed && mouseX >= doneX && mouseX < doneX + btnW) {
                    this.mc.displayGuiScreen(this.prevScreen);
                    return;
                }
            }
        }

        // Check option row clicks
        if (mouseY >= listTop() && mouseY <= listBottom()) {
            for (RowLayout layout : this.visibleRows) {
                if (!layout.contains(mouseX, mouseY)) {
                    continue;
                }
                OptionEntry<?> entry = layout.entry();
                if (entry instanceof SliderOption slider && mouseButton == 0) {
                    String val = slider.getDisplayValue();
                    int valW = this.fontRenderer.getStringWidth(val);
                    int trackLeft = layout.controlX();
                    int trackW = Math.max(30, layout.controlW() - valW - 8);
                    this.draggingSlider = slider;
                    this.draggingSliderLeft = trackLeft;
                    this.draggingSliderWidth = trackW;
                    updateSliderFromMouse(mouseX);
                    return;
                } else if (entry instanceof ToggleOption toggle && mouseButton == 0) {
                    toggle.toggle();
                    return;
                } else if (entry instanceof CycleOption<?> cycle) {
                    int delta = (mouseButton == 1) ? -1 : 1;
                    cycle.cycle(delta);
                    return;
                }
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (this.draggingSlider != null && clickedMouseButton == 0) {
            updateSliderFromMouse(mouseX);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        this.draggingSlider = null;
    }

    private void updateSliderFromMouse(int mouseX) {
        if (this.draggingSlider == null || this.draggingSliderWidth <= 0) {
            return;
        }
        float fraction = (float) (mouseX - this.draggingSliderLeft) / (float) this.draggingSliderWidth;
        this.draggingSlider.setFromFraction(fraction);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0 && this.maxScroll > 0) {
            int delta = dWheel > 0 ? -24 : 24;
            this.scrollOffset += delta;
            clampScroll();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.prevScreen);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
