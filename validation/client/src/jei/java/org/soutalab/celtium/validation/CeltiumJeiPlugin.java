package org.soutalab.celtium.validation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IIngredientFilter;
import mezz.jei.api.IIngredientListOverlay;

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

    /**
     * {@return the mod's own account of its item list, so the fixture's report says what the search did}, or the reason
     * it could not be read.
     * <p>
     * The search box is driven through the screen's keyboard path (see the {@code jeiSearch} knob in
     * {@code ValidationClient}), so "the keystrokes were sent" is not the same claim as "the filter ran": they can land
     * on a field that is not focused, and JEI's own handler decides that. This reads what the mod itself holds
     * afterwards - how many ingredients the overlay lists, how many the filter matches, whether the list has the
     * keyboard and what the filter text is - so the filter's effect is a number rather than an impression from the
     * picture.
     * <p>
     * Every call here is the mod's public API ({@code mezz.jei.api}); the method set was read from the HEI 4.31.2 jar
     * with {@code javap} rather than assumed, and no HEI internal is reflected into.
     */
    public static String listState() {
        IJeiRuntime jei = runtime;
        if (jei == null) {
            return "unavailable: no JEI runtime was handed over";
        }
        try {
            IIngredientListOverlay overlay = jei.getIngredientListOverlay();
            IIngredientFilter filter = jei.getIngredientFilter();
            int visible = overlay == null ? -1 : overlay.getVisibleIngredients().size();
            boolean focus = overlay != null && overlay.hasKeyboardFocus();
            String text = filter == null ? null : filter.getFilterText();
            int filtered = filter == null ? -1 : filter.getFilteredIngredients().size();
            // The recipe screen carries its own search box. `IRecipesGui.getSearchFilter()`/`getSearchMode()` exist in
            // the HEI 4.31.2 API the run uses but not in the JEI 4.22 API this source set compiles against (read from
            // both jars with javap), so they are resolved reflectively here: the mod's own public API either way, and
            // the same shape the fixture uses for everything mod-specific. An API without them reports "unavailable"
            // rather than failing the build or the run.
            mezz.jei.api.IRecipesGui recipes = jei.getRecipesGui();
            String recipeSearch = "n/a";
            String recipeMode = "n/a";
            if (recipes != null) {
                try {
                    recipeSearch = String.valueOf(recipes.getClass().getMethod("getSearchFilter").invoke(recipes));
                    recipeMode = String.valueOf(recipes.getClass().getMethod("getSearchMode").invoke(recipes));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    recipeSearch = "unavailable: " + error;
                }
            }
            return "visible=" + visible + " filtered=" + filtered + " keyboardFocus=" + focus
                    + " filterText=\"" + text + "\" recipeSearch=\"" + recipeSearch + "\" recipeSearchMode="
                    + recipeMode;
        } catch (RuntimeException | LinkageError error) {
            return "unavailable: " + error;
        }
    }

    /**
     * Sets the mod's ingredient filter through the mod's own public API and {@return what it holds afterwards}.
     * <p>
     * This is the second half of the search acceptance, and it exists because the first half was measured not to work
     * from a fixture: {@code -Dceltium.validation.jeiSearch} delivers characters through {@code GuiScreen.keyTyped} -
     * the method Minecraft's keyboard loop calls, and the same reflective route the fixture uses for
     * {@code mouseClicked} - and the mod's filter still reads {@code keyboardFocus=false filterText=""} afterwards,
     * because the mod's own keyboard handler reads the LWJGL event state and a fixture cannot synthesise a keystroke
     * honestly. So the filter is set the way the mod offers for another mod to set it
     * ({@code IIngredientFilter.setFilterText}, the same kind of entry point as the {@code IRecipesGui.showCategories}
     * call the recipe knob already uses), and the path is named in the artifact beside the state it produced - it is
     * an API drive, not a keystroke, and the report says which.
     */
    public static String setFilterText(String text) {
        IJeiRuntime jei = runtime;
        if (jei == null) {
            return "unavailable: no JEI runtime was handed over";
        }
        IIngredientFilter filter = jei.getIngredientFilter();
        if (filter == null) {
            return "unavailable: no ingredient filter";
        }
        try {
            filter.setFilterText(text);
        } catch (RuntimeException | LinkageError error) {
            return "unavailable: setFilterText failed: " + error;
        }
        return listState();
    }
}
