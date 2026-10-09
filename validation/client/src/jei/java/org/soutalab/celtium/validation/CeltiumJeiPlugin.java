package org.soutalab.celtium.validation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.IJeiRuntime;

/**
 * Gives the fixture a way to open JEI's own recipe screen.
 * <p>
 * Loading a mod proves it is compatible; opening its screens proves that the mod's own rendering goes through the renderer
 * under test. JEI's supported way to be reached is this plugin interface, so the fixture implements it and keeps the runtime
 * the mod hands over - no reflection, which is both this project's policy and what the mod's own API asks for.
 * <p>
 * Compiled only when the JEI jar is present ({@code src/jei/java} is added to the source set conditionally), so a normal
 * build and the ordinary runs are unaffected.
 */
@JEIPlugin
public class CeltiumJeiPlugin implements IModPlugin {

    private static volatile IJeiRuntime runtime;

    /** The runtime JEI handed over, or null when JEI is not installed. */
    public static IJeiRuntime runtime() {
        return runtime;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    /** Opens the first recipe category the mod offers, which is the screen this test is about. */
    public static boolean openFirstRecipeCategory() {
        IJeiRuntime jei = runtime;
        if (jei == null || jei.getRecipeRegistry() == null || jei.getRecipesGui() == null) {
            return false;
        }
        // The 1.12.2 shape of this API, read from the jar with javap rather than assumed: the registry lists categories and
        // the GUI takes a list of their uids.
        java.util.List<mezz.jei.api.recipe.IRecipeCategory> categories = jei.getRecipeRegistry().getRecipeCategories();
        if (categories == null || categories.isEmpty()) {
            return false;
        }
        String uid = categories.get(0).getUid();
        if (uid == null) {
            return false;
        }
        jei.getRecipesGui().showCategories(java.util.Collections.singletonList(uid));
        return true;
    }
}
