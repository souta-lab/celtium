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
 * loaded but not initialized) and the filtering view's behaviour.
 */
class TextureAtlasMixinTest {

    @Test
    void filteringViewFiltersOnlyTheIteration() {
        List<String> backing = new ArrayList<>(Arrays.asList("water", "stone", "lava", "glass"));
        List<String> consulted = new ArrayList<>();

        List<String> view = TextureAtlasMixin.celtium$filteringView(backing, element -> {
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
