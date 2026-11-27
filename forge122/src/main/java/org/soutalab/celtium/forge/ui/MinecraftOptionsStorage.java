package org.soutalab.celtium.forge.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Native bridge to vanilla {@link GameSettings} ({@code options.txt}).
 */
public class MinecraftOptionsStorage {
    private static final Logger LOGGER = LogManager.getLogger("Celtium");

    public GameSettings getData() {
        return Minecraft.getMinecraft().gameSettings;
    }

    /**
     * The brightness slider works in whole percents while {@code GameSettings.gammaSetting} stores a
     * float fraction. The read-back must round, not truncate: {@code (int) (gamma / 0.01D)} returns one
     * less than the written percent for 50 of the 101 slider values (every value whose float fraction
     * lands just below the exact hundredth, e.g. 29 -&gt; 0.29f -&gt; 28). With rounding,
     * percent -&gt; gamma -&gt; percent is the identity for all 101 values.
     */
    public static float percentToGamma(int percent) {
        return (float) (percent * 0.01D);
    }

    public static int gammaToPercent(float gamma) {
        return (int) Math.round(gamma / 0.01D);
    }

    public void save() {
        this.getData().saveOptions();
        LOGGER.info("Flushed changes to Minecraft configuration");
    }
}
