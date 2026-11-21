package org.taumc.celeritas.impl.gui;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.taumc.celeritas.CeleritasVintage;

public class MinecraftOptionsStorage implements OptionStorage<GameSettings> {
    private final Minecraft client;

    public MinecraftOptionsStorage() {
        this.client = Minecraft.getMinecraft();
    }

    @Override
    public GameSettings getData() {
        return this.client.gameSettings;
    }

    /**
     * The brightness slider works in whole percents while {@code GameSettings.gammaSetting} stores a
     * float fraction. The read-back must round, not truncate: {@code (int) (gamma / 0.01D)} returns one
     * less than the written percent for 50 of the 101 slider values (every value whose float fraction
     * lands just below the exact hundredth, e.g. 29 -&gt; 0.29f -&gt; 28), so the options screen showed a
     * brightness one point below the one actually in effect. With rounding, percent -&gt; gamma -&gt;
     * percent is the identity for all 101 values.
     */
    public static float percentToGamma(int percent) {
        return (float) (percent * 0.01D);
    }

    public static int gammaToPercent(float gamma) {
        return (int) Math.round(gamma / 0.01D);
    }

    @Override
    public void save(Set<OptionFlag> flags) {
        this.getData().saveOptions();

        CeleritasVintage.logger().info("Flushed changes to Minecraft configuration");
    }
}