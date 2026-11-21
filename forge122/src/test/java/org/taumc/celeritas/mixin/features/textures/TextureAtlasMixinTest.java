package org.taumc.celeritas.mixin.features.textures;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The visible-only animation gate's shape, pinned by three measured startup deaths on the
 * VanillaFix legs. The gate used to be an expression injector inside {@code updateAnimations}:
 * first aimed at the {@code List.iterator()} call, which VanillaFix's {@code @Overwrite} of that
 * method makes ambiguous (its pass calls it twice) - the whole mixin failed prepare and with it
 * the TextureMap class (leg run-compat445a). Retargeted at the read of the sprite list itself,
 * the helper behind it was a non-private static {@code @Unique} method, which Mixin rejects
 * outright (leg run-compat445d), and once that was fixed the FIELD target still did not resolve
 * in the overwritten body, whose field reference is owned by VanillaFix's shadow rather than by
 * TextureMap (leg run-compat445f). The gate is therefore a cancellable HEAD inject that runs the
 * pass itself while the option is on - the method boundary is the only aim that applied in both
 * body shapes - and these tests hold that shape, plus the private-static rule the second death
 * taught. Shapes are read by reflection; the class is loaded but not initialized.
 */
class TextureAtlasMixinTest {

    @Test
    void theVisibleOnlyGateIsACancellableHeadInject() throws Exception {
        List<Method> gates = new ArrayList<>();
        for (Method method : mixinClass().getDeclaredMethods()) {
            Inject inject = method.getAnnotation(Inject.class);
            if (inject != null && inject.cancellable()
                    && Arrays.asList(inject.method()).contains("updateAnimations")) {
                gates.add(method);
            }
        }
        assertEquals(1, gates.size(),
                "exactly one cancellable gate on updateAnimations is expected");

        At[] ats = gates.get(0).getAnnotation(Inject.class).at();
        assertEquals(1, ats.length, "the gate must carry exactly one @At");
        assertEquals("HEAD", ats[0].value(),
                "the gate must sit at the method boundary: expression targets inside "
                        + "updateAnimations do not survive a foreign @Overwrite of that method");
    }

    @Test
    void updateAnimationsInjectorsStayAtTheMethodBoundary() throws Exception {
        // HEAD and RETURN are the only injection points whose existence does not depend on the
        // body a foreign @Overwrite substitutes. Any injector selecting updateAnimations must
        // use only those, or the VanillaFix shape kills the mixin again.
        for (Method method : mixinClass().getDeclaredMethods()) {
            for (Annotation annotation : method.getDeclaredAnnotations()) {
                if (!methodSelectors(annotation).contains("updateAnimations")) {
                    continue;
                }
                for (At at : atValues(annotation)) {
                    assertTrue(at.value().equals("HEAD") || at.value().equals("RETURN"),
                            method.getName() + " aims @" + at.value() + " inside updateAnimations; "
                                    + "only HEAD/RETURN survive a foreign @Overwrite of that method");
                }
            }
        }
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

    @Test
    void uniqueStaticHelpersArePrivate() throws Exception {
        // Mixin rejects a mixin class that declares a non-private static method
        // (InvalidMixinException: "contains non-private static method"), aborting the whole
        // mixin and its target class - measured on leg run-compat445d, where a filtering-view
        // helper without `private` killed TextureMap at startup. Every @Unique static helper
        // in this class must therefore be private.
        boolean foundHelper = false;
        for (Method method : mixinClass().getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getName().equals("celtium$reportAnimationFailure")) {
                foundHelper = true;
            }
            if (method.isAnnotationPresent(org.spongepowered.asm.mixin.Unique.class)) {
                assertTrue(Modifier.isPrivate(method.getModifiers()),
                        "@Unique static helper " + method.getName() + " must be private, "
                                + "or Mixin rejects the whole mixin class at apply time");
            }
        }
        assertTrue(foundHelper, "the celtium$reportAnimationFailure helper is expected to exist");
    }

    private static Class<?> mixinClass() throws Exception {
        return Class.forName("org.taumc.celeritas.mixin.features.textures.TextureAtlasMixin",
                false, TextureAtlasMixinTest.class.getClassLoader());
    }

    /** The {@code method} element of an injector annotation, as a list of selectors. */
    private static List<String> methodSelectors(Annotation annotation) {
        List<String> selectors = new ArrayList<>();
        Object value;
        try {
            value = annotation.annotationType().getMethod("method").invoke(annotation);
        } catch (ReflectiveOperationException e) {
            return selectors;
        }
        if (value instanceof String) {
            selectors.add((String) value);
        } else if (value instanceof String[]) {
            selectors.addAll(Arrays.asList((String[]) value));
        }
        return selectors;
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
