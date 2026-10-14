package org.taumc.celeritas.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.Name("Celeritas")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class CeleritasLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {
    /**
     * The GL-less window hint is set from a transformer rather than only from a mixin, because a mixin can lose a
     * race it cannot see: measured 2026-10-15, RandomPatches' tweak defines {@code org.lwjgl.opengl.Display} at
     * 8.975 s in the 268-mod E2E pack while Mixin prepares its configurations at 9.837 s (leg
     * {@code gv-e2e-268-xlog}), so {@code core.LwjglxDisplayMixin} never applied and the window got a real GL
     * context. This array is read when the coremod loads, before either.
     */
    @Override
    public @Nullable String[] getASMTransformerClass() {
        return new String[] {"org.taumc.celeritas.core.CeltiumGllessWindowTransformer"};
    }

    @Override
    public @Nullable String getModContainerClass() {
        return null;
    }

    @Override
    public @Nullable String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> map) {

    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.celeritas.json");
    }
}
