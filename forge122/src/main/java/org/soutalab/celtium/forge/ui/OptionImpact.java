package org.soutalab.celtium.forge.ui;

import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.client.resources.I18n;

/**
 * Approximate performance impact displayed in option tooltips.
 */
public enum OptionImpact {
    LOW("celtium.option_impact.low", ChatFormatting.GREEN),
    MEDIUM("celtium.option_impact.medium", ChatFormatting.YELLOW),
    HIGH("celtium.option_impact.high", ChatFormatting.GOLD),
    EXTREME("celtium.option_impact.extreme", ChatFormatting.RED),
    VARIES("celtium.option_impact.varies", ChatFormatting.WHITE);

    private final String translationKey;
    private final ChatFormatting color;

    OptionImpact(String translationKey, ChatFormatting color) {
        this.translationKey = translationKey;
        this.color = color;
    }

    public String formatted() {
        return this.color + I18n.format(this.translationKey) + ChatFormatting.RESET;
    }
}
