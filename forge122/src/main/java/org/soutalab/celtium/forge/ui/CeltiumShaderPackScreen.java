package org.soutalab.celtium.forge.ui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.soutalab.celtium.shader.shaderpack.option.Profile;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuBooleanOptionElement;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuContainer;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuElement;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuElementScreen;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuLinkElement;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuOptionElement;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuProfileElement;
import org.soutalab.celtium.shader.shaderpack.option.menu.OptionMenuStringOptionElement;
import org.soutalab.celtium.shader.shaderpack.option.values.OptionValues;
import org.soutalab.celtium.vulkan.pack.ShaderPackRuntime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The shader pack screen behind the video settings' Shaders tab: a pack list (Off plus every
 * pack in the shaderpacks folder) and, for the active pack, its own option menu - profiles,
 * boolean toggles and string choices - rendered as rows in the video settings' visual
 * language. Edits accumulate in the runtime's pending option values; Apply recompiles the
 * pack with them and persists the selection and overrides to the config.
 */
public class CeltiumShaderPackScreen extends GuiScreen {

    private static final int BG_PANEL = 0xCC10141C;
    private static final int BG_ROW = 0xB0181F2B;
    private static final int BG_ROW_HOVER = 0xD0243044;
    private static final int BG_ROW_SELECTED = 0xE02A3C58;
    private static final int ACCENT = 0xFF4FA8FF;
    private static final int BORDER = 0xFF334257;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFB0BCCF;
    private static final int TEXT_CHANGED = 0xFF7CC4FF;

    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 30;

    private final GuiScreen parent;
    private final Deque<OptionMenuElementScreen> screenStack = new ArrayDeque<>();
    private final List<Row> rows = new ArrayList<>();
    private int scrollOffset;

    private record Row(int x, int y, int w, int h, String label, String value, boolean selected,
                       boolean changed, Runnable action) {
        boolean contains(int mx, int my) {
            return action != null && mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public CeltiumShaderPackScreen(GuiScreen parent) {
        this.parent = parent;
    }

    private ShaderPackRuntime runtime() {
        return ShaderPackAccess.runtime();
    }

    private int panelX() {
        return Math.max(10, (this.width - 460) / 2);
    }

    private int panelW() {
        return Math.min(460, this.width - 20);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int px = panelX();
        int pw = panelW();
        int top = 12;
        int bottom = this.height - 12;
        drawRect(px, top, px + pw, bottom, BG_PANEL);
        drawRect(px, top, px + pw, top + 1, BORDER);
        drawRect(px, bottom - 1, px + pw, bottom, BORDER);

        String title = I18n.format("celtium.options.shaders.title");
        drawString(fontRenderer, title, px + 12, top + 10, TEXT_PRIMARY);
        String active = runtime().activePackName();
        String activeLabel = active == null
                ? I18n.format("celtium.options.shaders.off")
                : active;
        drawString(fontRenderer, activeLabel, px + pw - 12 - fontRenderer.getStringWidth(activeLabel),
                top + 10, ACCENT);

        rows.clear();
        int y = top + HEADER_HEIGHT - scrollOffset;
        int contentBottom = bottom - FOOTER_HEIGHT;

        // Pack list.
        y = addRow(px, pw, y, contentBottom, I18n.format("celtium.options.shaders.off"), "",
                active == null, false, () -> ShaderPackAccess.selectPack(""));
        List<String> packs = runtime().availablePacks();
        for (String pack : packs) {
            y = addRow(px, pw, y, contentBottom, pack, "", pack.equals(active), false,
                    () -> ShaderPackAccess.selectPack(pack));
        }
        if (packs.isEmpty()) {
            drawString(fontRenderer, I18n.format("celtium.options.shaders.none"),
                    px + 12, y + 5, TEXT_MUTED);
            y += ROW_HEIGHT;
        }

        // The active pack's option menu.
        OptionMenuContainer menu = runtime().optionMenu();
        if (menu != null) {
            y += 6;
            OptionMenuElementScreen screen = screenStack.isEmpty() ? menu.mainScreen : screenStack.peek();
            Map<String, String> pending = runtime().pendingOptionValues();
            for (OptionMenuElement element : screen.elements) {
                y = addElementRow(px, pw, y, contentBottom, element, menu, pending);
            }
        }

        int maxScroll = Math.max(0, (y + scrollOffset) - contentBottom);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        // Footer: Back (in a sub-screen), Apply, Done.
        int footerY = bottom - FOOTER_HEIGHT + 6;
        int buttonW = 90;
        int doneX = px + pw - 12 - buttonW;
        drawButton(doneX, footerY, buttonW, I18n.format("gui.done"), mouseX, mouseY);
        int applyX = doneX - 8 - buttonW;
        boolean canApply = active != null;
        drawButton(applyX, footerY, buttonW, I18n.format("celtium.options.shaders.apply"), mouseX, mouseY);
        if (!screenStack.isEmpty()) {
            drawButton(px + 12, footerY, buttonW, I18n.format("celtium.options.shaders.back"), mouseX, mouseY);
        }
        rows.add(new Row(doneX, footerY, buttonW, 18, "", "", false, false,
                () -> mc.displayGuiScreen(parent)));
        if (canApply) {
            rows.add(new Row(applyX, footerY, buttonW, 18, "", "", false, false,
                    ShaderPackAccess::applyPendingOptions));
        }
        if (!screenStack.isEmpty()) {
            rows.add(new Row(px + 12, footerY, buttonW, 18, "", "", false, false, screenStack::pop));
        }
    }

    private int addElementRow(int px, int pw, int y, int contentBottom, OptionMenuElement element,
                              OptionMenuContainer menu, Map<String, String> pending) {
        if (element == OptionMenuElement.EMPTY) {
            return y + 6;
        }
        if (element instanceof OptionMenuBooleanOptionElement bool) {
            OptionValues values = bool.getPendingOptionValues(pending);
            boolean on = values.getBooleanValueOrDefault(bool.optionId);
            boolean changed = pending.containsKey(bool.optionId);
            return addRow(px, pw, y, contentBottom, bool.option.getName(), on ? "ON" : "OFF",
                    false, changed,
                    () -> pending.put(bool.optionId, Boolean.toString(!on)));
        }
        if (element instanceof OptionMenuStringOptionElement str) {
            OptionValues values = str.getPendingOptionValues(pending);
            String current = values.getStringValueOrDefault(str.optionId);
            boolean changed = pending.containsKey(str.optionId);
            return addRow(px, pw, y, contentBottom, str.option.getName(), current, false, changed,
                    () -> {
                        List<String> allowed = str.option.getAllowedValues();
                        if (!allowed.isEmpty()) {
                            int index = allowed.indexOf(current);
                            pending.put(str.optionId, allowed.get((index + 1) % allowed.size()));
                        }
                    });
        }
        if (element instanceof OptionMenuProfileElement profile) {
            OptionValues values = profile.getPendingOptionValues(pending);
            var result = profile.profiles.scan(profile.options, values);
            String currentName = result.current
                    .map(p -> p.name)
                    .orElse(I18n.format("celtium.options.shaders.custom"));
            return addRow(px, pw, y, contentBottom,
                    I18n.format("celtium.options.shaders.profile"), currentName, false, false,
                    () -> {
                        if (result.next != null) {
                            pending.putAll(result.next.optionValues);
                        }
                    });
        }
        if (element instanceof OptionMenuLinkElement link) {
            OptionMenuElementScreen target = menu.subScreens.get(link.targetScreenId);
            if (target != null) {
                return addRow(px, pw, y, contentBottom, link.targetScreenId, ">", false, false,
                        () -> screenStack.push(target));
            }
            return y;
        }
        return y;
    }

    private int addRow(int px, int pw, int y, int contentBottom, String label, String value,
                       boolean selected, boolean changed, Runnable action) {
        if (y + ROW_HEIGHT >= HEADER_HEIGHT + 12 && y <= contentBottom) {
            int x = px + 8;
            int w = pw - 16;
            drawRect(x, y, x + w, y + ROW_HEIGHT - 2, selected ? BG_ROW_SELECTED : BG_ROW);
            int labelColor = changed ? TEXT_CHANGED : TEXT_PRIMARY;
            drawString(fontRenderer, label, x + 8, y + 5, labelColor);
            if (!value.isEmpty()) {
                drawString(fontRenderer, value,
                        x + w - 8 - fontRenderer.getStringWidth(value), y + 5, TEXT_MUTED);
            }
            rows.add(new Row(x, y, w, ROW_HEIGHT - 2, label, value, selected, changed, action));
        }
        return y + ROW_HEIGHT;
    }

    private void drawButton(int x, int y, int w, String label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        drawRect(x, y, x + w, y + 18, hover ? BG_ROW_HOVER : BG_ROW);
        drawCenteredString(fontRenderer, label, x + w / 2, y + 5, TEXT_PRIMARY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            for (Row row : rows) {
                if (row.contains(mouseX, mouseY)) {
                    row.action().run();
                    return;
                }
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollOffset = Math.max(0, scrollOffset - Integer.signum(wheel) * ROW_HEIGHT * 3);
        }
    }
}
