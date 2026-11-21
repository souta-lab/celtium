package org.taumc.celeritas.mixin.features.textures;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The visible-only animation gate's aim, pinned after the VanillaFix leg (run-compat445a) killed the
 * game at startup: the gate's {@code @ModifyExpressionValue} used to target the {@code List.iterator()}
 * call inside {@code updateAnimations}, an expression VanillaFix's {@code @Overwrite} of that method
 * makes ambiguous (its body calls it twice), which failed the whole mixin in prepare and with it the
 * TextureMap class. The gate now targets the read of the sprite list itself - unique in both bodies -
 * and these tests hold both halves of that: the annotation shape (read by reflection; the class is
 * loaded but not initialized) and the filtering view's behaviour. A second failure is pinned here
 * too, measured on the first leg of the retargeted gate (run-compat445d): the filtering-view helper
 * was a non-private static {@code @Unique} method, and Mixin rejects a mixin class declaring one
 * outright - the same startup death, a different cause. The helper is private now, and one test
 * below holds every {@code @Unique} static in the class to that.
 */
class TextureAtlasMixinTest {

    @Test
    void filteringViewFiltersOnlyTheIteration() throws Exception {
        List<String> backing = new ArrayList<>(Arrays.asList("water", "stone", "lava", "glass"));
        List<String> consulted = new ArrayList<>();

        List<String> view = filteringView(backing, element -> {
            consulted.add(element);
            return element.contains("a");
        });

        // The list itself is untouched: size and random access still describe every element.
        assertEquals(4, view.size());
        assertEquals("stone", view.get(1));
        assertEquals(Arrays.asList("water", "stone", "lava", "glass"), backing);

        List<String> iterated = new ArrayList<>();
        for (String element : view) {
            iterated.add(element);
        }
        assertEquals(Arrays.asList("water", "lava", "glass"), iterated);
        // The predicate is consulted once per element over a full pass - the counting the
        // CELTIUM_ANIM_TICK instrument does inside its predicate depends on that.
        assertEquals(Arrays.asList("water", "stone", "lava", "glass"), consulted);
    }

    @Test
    void theAnimationGateAimsAtTheSpriteListFieldRead() throws Exception {
        List<Method> gates = new ArrayList<>();
        for (Method method : mixinClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(ModifyExpressionValue.class)) {
                gates.add(method);
            }
        }
        assertEquals(1, gates.size(), "exactly one @ModifyExpressionValue gate is expected");

        ModifyExpressionValue gate = gates.get(0).getAnnotation(ModifyExpressionValue.class);
        assertTrue(Arrays.asList(gate.method()).contains("updateAnimations"));
        assertEquals(0, gate.require(),
                "the gate is an optimization: an unrecognised body shape must lose the gate, not the class");

        At[] ats = gate.at();
        assertEquals(1, ats.length, "the gate must carry exactly one @At");
        At at = ats[0];
        assertEquals("FIELD", at.value());
        assertEquals(Opcodes.GETFIELD, at.opcode());
        assertTrue(at.target().contains("listAnimatedSprites"),
                "the gate must aim at the animated-sprite list read, got: " + at.target());
    }

    @Test
    void uniqueStaticHelpersArePrivate() throws Exception {
        // Mixin rejects a mixin class that declares a non-private static method
        // (InvalidMixinException: "contains non-private static method"), aborting the whole
        // mixin and its target class - measured on leg run-compat445d, where the filtering-view
        // helper without `private` killed TextureMap at startup. Every @Unique static helper
        // in this class must therefore be private.
        boolean foundHelper = false;
        for (Method method : mixinClass().getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getName().equals("celtium$filteringView")) {
                foundHelper = true;
            }
            if (method.isAnnotationPresent(org.spongepowered.asm.mixin.Unique.class)) {
                assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                        "@Unique static helper " + method.getName() + " must be private, "
                                + "or Mixin rejects the whole mixin class at apply time");
            }
        }
        assertTrue(foundHelper, "the celtium$filteringView helper is expected to exist");
    }

    @Test
    void noInjectorTargetsTheAmbiguousIteratorCall() throws Exception {
        for (Method method : mixinClass().getDeclaredMethods()) {
            for (Annotation annotation : method.getDeclaredAnnotations()) {
                for (At at : atValues(annotation)) {
                    assertFalse(at.target().contains("List;iterator()"),
                            method.getName() + " targets the List.iterator() call, which a foreign "
                                    + "@Overwrite of updateAnimations makes ambiguous");
                }
            }
        }
    }

    /** The helper is private (see uniqueStaticHelpersArePrivate), so tests reach it by reflection. */
    @SuppressWarnings("unchecked")
    private static List<String> filteringView(List<String> backing,
                                              com.google.common.base.Predicate<String> predicate)
            throws Exception {
        Method helper = mixinClass().getDeclaredMethod("celtium$filteringView",
                List.class, com.google.common.base.Predicate.class);
        helper.setAccessible(true);
        return (List<String>) helper.invoke(null, backing, predicate);
    }

    private static Class<?> mixinClass() throws Exception {
        return Class.forName("org.taumc.celeritas.mixin.features.textures.TextureAtlasMixin",
                false, TextureAtlasMixinTest.class.getClassLoader());
    }

    /** The {@code at} element of an injector annotation: a single @At, or an @At[] as on @Inject. */
    private static List<At> atValues(Annotation annotation) {
        List<At> values = new ArrayList<>();
        Object at;
        try {
            Method element = annotation.annotationType().getMethod("at");
            at = element.invoke(annotation);
        } catch (ReflectiveOperationException e) {
            return values;
        }
        if (at instanceof At) {
            values.add((At) at);
        } else if (at instanceof At[]) {
            values.addAll(Arrays.asList((At[]) at));
        }
        return values;
    }
}
