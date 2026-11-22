package com.gtnewhorizons.angelica.compat.mojang;

/**
 * Compile-time stub of Angelica's Camera type, present only so that mixin handlers targeting
 * Demonica/Angelica methods can declare the target's exact descriptor (item 648: an
 * {@code @Inject} handler must name every parameter with its real type - widening to
 * {@code Object} or omitting the parameter are both rejected by the injector).
 * <p>
 * This class lives in the {@code stubs} source set, whose output is on the main source set's
 * compile classpath only. It is never packaged into Celtium's distributable jar; at runtime
 * the real Angelica class supplies the type. Do not add members: nothing here may shadow the
 * real class's API, because nothing here is ever loaded.
 */
public final class Camera {
    private Camera() {
    }
}
