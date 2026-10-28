#!/usr/bin/env python3
"""Execute the production camera helper, frustum mixins and Viewport against CPU-only JOML planes.

Requires JDK 21 and the build's cached JOML 1.10.5 (or JOML_JAR). No Minecraft,
LWJGL, native allocator, GPU, Gradle, or source-substring assertions are involved.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FORGE = ROOT / "forge122/src/main/java"
COMMON = ROOT / "common/src/main/java"


def joml_jar():
    explicit = os.environ.get("JOML_JAR")
    if explicit:
        path = Path(explicit).expanduser().resolve()
        if not path.is_file():
            raise SystemExit(f"JOML_JAR is not a file: {path}")
        return path
    gradle = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    matches = sorted((gradle / "caches/modules-2/files-2.1/org.joml/joml/1.10.5").glob("*/joml-1.10.5.jar"))
    if not matches:
        raise SystemExit("Cached JOML 1.10.5 not found; set JOML_JAR to the build dependency jar")
    return matches[0]


PROBE = r"""import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Random;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.taumc.celeritas.impl.render.terrain.CameraHelper;
import org.taumc.celeritas.mixin.core.frustum.FrustumMixin;
import org.taumc.celeritas.mixin.core.frustum.ClippingHelperImplMixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor;
import net.minecraft.client.renderer.culling.ClippingHelper;

public class FrustumCameraOffsetProbe {
    static int comparisons;
    static int oldFalseNegatives;
    static int oldFalsePositives;
    static int visible;
    static int rejected;

    static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    static void close(double actual, double expected, double tolerance, String label) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                label + ": " + actual + " != " + expected);
    }

    static void setModelView(Matrix4f matrix) {
        FloatBuffer buffer = FloatBuffer.allocate(22);
        for (int i = 0; i < buffer.capacity(); i++) buffer.put(i, 12345.0f);
        buffer.position(3);
        matrix.get(buffer);
        ActiveRenderInfoAccessor.modelView = buffer;
    }

    static class PlaneHook extends ClippingHelperImplMixin {}

    static FrustumIntersection planes(Matrix4f projection, Matrix4f modelView) throws Exception {
        return updatePlanes(new PlaneHook(), projection, modelView);
    }

    static FrustumIntersection updatePlanes(PlaneHook hook, Matrix4f projection, Matrix4f modelView) throws Exception {
        projection.get(hook.projectionMatrix);
        modelView.get(hook.modelviewMatrix);
        Method update = ClippingHelperImplMixin.class.getDeclaredMethod("updateJoml", CallbackInfo.class);
        update.setAccessible(true);
        update.invoke(hook, new CallbackInfo());
        return hook.celeritas$getJomlFrustum();
    }

    static FrustumMixin camera(FrustumIntersection planes, Vector3d feet) throws Exception {
        FrustumMixin result = new FrustumMixin();
        Field helper = FrustumMixin.class.getDeclaredField("clippingHelper");
        helper.setAccessible(true);
        helper.set(result, new ClippingHelper(planes));
        for (String axis : new String[]{"x", "y", "z"}) {
            Field coordinate = FrustumMixin.class.getDeclaredField(axis);
            coordinate.setAccessible(true);
            coordinate.setDouble(result, axis.equals("x") ? feet.x : axis.equals("y") ? feet.y : feet.z);
        }
        return result;
    }

    static Matrix4f modelView(float yaw, float pitch, float distance, float roll, boolean front) {
        // EntityRenderer.orientCamera: camera-space displacement, Forge camera rotations, then eye height.
        Matrix4f matrix = new Matrix4f();
        if (front) matrix.rotateY((float)Math.PI).rotateX((float)-Math.PI);
        matrix.translate(0.0f, 0.0f, distance == 0.0f ? 0.05f : -distance);
        if (front) matrix.rotateX((float)Math.PI);
        return matrix.rotateZ((float)Math.toRadians(roll))
                .rotateX((float)Math.toRadians(pitch))
                .rotateY((float)Math.toRadians(yaw + 180.0f))
                .translate(0.0f, -1.62f, 0.0f);
    }

    static void compare(FrustumMixin vanilla, Viewport corrected, Viewport old,
                        Vector3d feet, Vector3f center, float half, String label) {
        double minX = feet.x + center.x - half, maxX = feet.x + center.x + half;
        double minY = feet.y + center.y - half, maxY = feet.y + center.y + half;
        double minZ = feet.z + center.z - half, maxZ = feet.z + center.z + half;
        boolean expected = vanilla.isBoxInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
        boolean actual = corrected.isBoxVisible(minX, minY, minZ, maxX, maxY, maxZ);
        require(actual == expected, label + " visibility differs at " + center + " half=" + half);
        comparisons++;
        if (expected) visible++; else rejected++;
        boolean previous = old.isBoxVisible(minX, minY, minZ, maxX, maxY, maxZ);
        if (expected && !previous) oldFalseNegatives++;
        if (!expected && previous) oldFalsePositives++;
    }

    static void fixture(String label, Matrix4f matrix, Vector3d feet, long seed) throws Exception {
        setModelView(matrix);
        Vector3f offset = CameraHelper.getThirdPersonOffset();
        require(offset.isFinite(), label + " offset is not finite");
        require(matrix.transformPosition(new Vector3f(offset)).length() < 0.00001f,
                label + " inverse offset does not locate eye-space origin");
        require(ActiveRenderInfoAccessor.modelView.position() == 3, "helper changed matrix buffer position");
        require(ActiveRenderInfoAccessor.modelView.get(2) == 12345.0f
                && ActiveRenderInfoAccessor.modelView.get(19) == 12345.0f, "helper overwrote matrix bounds");
        Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(70), 16.0f / 9.0f,
                0.05f, 181.019f, false);
        FrustumIntersection planes = planes(projection, matrix);
        FrustumMixin vanilla = camera(planes, feet);
        Viewport corrected = vanilla.sodium$createViewport();
        Vector3d optical = new Vector3d(feet).add(offset);
        close(corrected.getTransform().x, optical.x, 1e-9, label + " optical x");
        close(corrected.getTransform().y, optical.y, 1e-9, label + " optical y");
        close(corrected.getTransform().z, optical.z, 1e-9, label + " optical z");
        require(corrected.getBlockCoord().y() == (int)Math.floor(optical.y), label + " graph starts at feet");
        require(corrected.getChunkCoord().x() == ((int)Math.floor(optical.x) >> 4), label + " optical section x");
        Viewport old = new Viewport(planes::testAab, optical);
        Matrix4f inverse = new Matrix4f(matrix).invert();
        Random random = new Random(seed);
        // Probe both sides of all planes and several box sizes, not just boxes well inside the cone.
        for (int i = 0; i < 4000; i++) {
            float depth = 0.2f + random.nextFloat() * 210.0f;
            Vector3f center = inverse.transformPosition(new Vector3f(
                    (random.nextFloat() * 2.8f - 1.4f) * depth,
                    (random.nextFloat() * 2.0f - 1.0f) * depth, -depth));
            compare(vanilla, corrected, old, feet, center, i % 3 == 0 ? 0.03f : i % 3 == 1 ? 0.5f : 8.0f, label);
        }
        for (int x = -12; x <= 12; x += 2) {
            for (int y = -12; y <= 12; y += 2) {
                for (int z = -12; z <= 12; z += 2) {
                    compare(vanilla, corrected, old, feet, new Vector3f(x, y, z), 0.2f, label);
                }
            }
        }
        // The viewport captures one camera offset; a subsequent ActiveRenderInfo update must not change it.
        setModelView(new Matrix4f().translate(32.0f, 64.0f, -16.0f));
        compare(vanilla, corrected, old, feet,
                inverse.transformPosition(new Vector3f(0.0f, 0.0f, -4.0f)), 0.25f, label + " snapshot");
    }

    static void invalid(Matrix4f matrix, String label) throws Exception {
        setModelView(matrix);
        Vector3f offset = CameraHelper.getThirdPersonOffset();
        require(offset.isFinite() && offset.lengthSquared() == 0.0f, label + " did not use finite zero fallback");
        Vector3d feet = new Vector3d(-127.125, 15.75, 20000000.375);
        FrustumIntersection planes = new FrustumIntersection(new Matrix4f().perspective(
                (float)Math.toRadians(70), 16.0f / 9.0f, 0.05f, 128.0f));
        FrustumMixin vanilla = camera(planes, feet);
        Viewport viewport = vanilla.sodium$createViewport();
        close(viewport.getTransform().x, feet.x, 0, label + " fallback x");
        close(viewport.getTransform().y, feet.y, 0, label + " fallback y");
        compare(vanilla, viewport, viewport, feet, new Vector3f(0, 0, -4), 0.2f, label);
        compare(vanilla, viewport, viewport, feet, new Vector3f(0, 0, 4), 0.2f, label);
    }

    static void permissivePlanes(Matrix4f projection, Matrix4f modelView, String label) throws Exception {
        PlaneHook hook = new PlaneHook();
        FrustumIntersection valid = updatePlanes(hook, new Matrix4f(), new Matrix4f());
        require(!valid.testAab(128, 128, 128, 129, 129, 129), label + " valid precondition did not reject");
        FrustumIntersection planes = updatePlanes(hook, projection, modelView);
        // Planes a viewport already captured stay as they were: the terrain search tests against them on its own
        // thread while the next init runs.
        require(!valid.testAab(128, 128, 128, 129, 129, 129), label + " captured planes changed under a later init");
        setModelView(modelView);
        FrustumMixin camera = camera(planes, new Vector3d(29999900.125, 12.0, -29999900.375));
        Viewport viewport = camera.sodium$createViewport();
        for (float coordinate : new float[]{-10000000.0f, -128.0f, 0.0f, 128.0f, 10000000.0f}) {
            require(planes.testAab(coordinate, coordinate, coordinate,
                    coordinate + 1, coordinate + 1, coordinate + 1), label + " empty frustum rejected remote AABB");
            require(viewport.isBoxVisible(coordinate, coordinate, coordinate,
                    coordinate + 1.0, coordinate + 1.0, coordinate + 1.0),
                    label + " permissive viewport rejected remote world AABB");
        }
        FrustumIntersection recovered = updatePlanes(hook, new Matrix4f(), new Matrix4f());
        require(!recovered.testAab(128, 128, 128, 129, 129, 129), label + " valid planes did not recover");
    }

    public static void main(String[] args) throws Exception {
        setModelView(modelView(0, 0, 0, 0, false));
        Vector3f firstPerson = CameraHelper.getThirdPersonOffset();
        close(firstPerson.x, 0.0, 1e-6, "first-person offset x");
        close(firstPerson.y, 1.62, 1e-6, "first-person eye height");
        close(firstPerson.z, 0.05, 1e-6, "first-person near-plane shift");
        fixture("level", modelView(0, 0, 0, 0, false), new Vector3d(0.5, 12.0, 0.5), 1);
        fixture("pinned-yaw180-down20", modelView(180, 20, 0, 0, false), new Vector3d(0.5, 12.0, 0.5), 2);
        fixture("yaw90-up35", modelView(90, -35, 0, 0, false), new Vector3d(-31.25, 15.75, 63.875), 3);
        fixture("pitched-roll", modelView(-43, 74, 0, 7, false), new Vector3d(127.125, 63.75, -255.5), 4);
        fixture("third-person-behind", modelView(37, 23, 4, 0, false), new Vector3d(0.5, 15.0, 0.5), 5);
        fixture("third-person-front", modelView(-117, -21, 4, 0, true), new Vector3d(-128.25, 64.5, 127.875), 6);
        fixture("large-coordinates", modelView(141, 31, 4, -3, false),
                new Vector3d(29999900.125, 253.75, -29999900.375), 7);
        invalid(new Matrix4f().zero(), "singular");
        invalid(new Matrix4f().m00(Float.NaN), "NaN");
        invalid(new Matrix4f().m31(Float.POSITIVE_INFINITY), "infinity");
        permissivePlanes(new Matrix4f().zero(), new Matrix4f(), "zero projection");
        permissivePlanes(new Matrix4f(), new Matrix4f().zero(), "zero model-view");
        permissivePlanes(new Matrix4f().m00(Float.NaN), new Matrix4f(), "NaN projection");
        permissivePlanes(new Matrix4f(), new Matrix4f().m31(Float.POSITIVE_INFINITY), "infinite model-view");
        require(!planes(new Matrix4f(), new Matrix4f()).testAab(100, 100, 100, 101, 101, 101),
                "identity must not be confused with the permissive zero-plane fallback");
        require(visible > 1000 && rejected > 1000, "fixture did not exercise both visibility outcomes");
        require(oldFalseNegatives > 100 && oldFalsePositives > 100,
                "negative control failed to expose double-offset holes and overdraw: "
                + oldFalseNegatives + "/" + oldFalsePositives);
        System.out.println("frustum camera offset: " + comparisons + " production/vanilla comparisons PASS"
                + " (first-person, rotations, roll, third-person, large coordinates, snapshot, invalid matrices, permissive planes;"
                + " old false-negative/positive=" + oldFalseNegatives + "/" + oldFalsePositives + ")");
    }
}
"""


def main():
    jar = joml_jar()
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        raise SystemExit("JDK 21 javac/java are required")
    region_source = (COMMON / "org/embeddedt/embeddium/impl/render/chunk/region/RenderRegion.java").read_text()
    region_width = re.search(r"public static final int REGION_WIDTH\s*=\s*\d+;", region_source)
    if region_width is None:
        raise SystemExit("Cannot extract production CameraTransform region-width constant")
    sources = {
        "org/jetbrains/annotations/Nullable.java": """package org.jetbrains.annotations;
@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE}) public @interface Nullable {}""",
        "org/spongepowered/asm/mixin/Mixin.java": "package org.spongepowered.asm.mixin; public @interface Mixin {Class<?>[] value();}",
        "org/spongepowered/asm/mixin/Shadow.java": "package org.spongepowered.asm.mixin; public @interface Shadow {}",
        "org/spongepowered/asm/mixin/Final.java": "package org.spongepowered.asm.mixin; public @interface Final {}",
        "org/spongepowered/asm/mixin/Overwrite.java": "package org.spongepowered.asm.mixin; public @interface Overwrite {}",
        "org/spongepowered/asm/mixin/Unique.java": "package org.spongepowered.asm.mixin; public @interface Unique {}",
        "org/spongepowered/asm/mixin/injection/At.java": "package org.spongepowered.asm.mixin.injection; public @interface At {String value();}",
        "org/spongepowered/asm/mixin/injection/Inject.java": """package org.spongepowered.asm.mixin.injection;
public @interface Inject {String[] method(); At at();}""",
        "org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java": "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo {}",
        "net/minecraft/client/renderer/culling/ClippingHelperImpl.java": "package net.minecraft.client.renderer.culling; public class ClippingHelperImpl extends ClippingHelper {}",
        "net/minecraft/client/renderer/culling/Frustum.java": "package net.minecraft.client.renderer.culling; public class Frustum {}",
        "net/minecraft/client/renderer/culling/ClippingHelper.java": """package net.minecraft.client.renderer.culling;
public class ClippingHelper implements org.taumc.celeritas.impl.render.frustum.IClippingHelper {
 public float[] projectionMatrix=new float[16], modelviewMatrix=new float[16];
 private final org.joml.FrustumIntersection planes;
 public ClippingHelper() {this(new org.joml.FrustumIntersection());}
 public ClippingHelper(org.joml.FrustumIntersection planes) {this.planes=planes;}
 public org.joml.FrustumIntersection celeritas$getJomlFrustum() {return planes;}
 public org.joml.Matrix4fc celeritas$getViewProjection() {return null;}
}""",
        "org/taumc/celeritas/mixin/core/terrain/ActiveRenderInfoAccessor.java": """package org.taumc.celeritas.mixin.core.terrain;
public class ActiveRenderInfoAccessor {
 public static java.nio.FloatBuffer modelView;
 public static java.nio.FloatBuffer getModelViewMatrix() {return modelView;}
}""",
        "org/embeddedt/embeddium/impl/render/chunk/region/RenderRegion.java": (
            "package org.embeddedt.embeddium.impl.render.chunk.region; public class RenderRegion {"
            + region_width.group() + "}"
        ),
        "FrustumCameraOffsetProbe.java": PROBE,
    }
    for name in ["org/taumc/celeritas/mixin/core/frustum/FrustumMixin.java",
                 "org/taumc/celeritas/mixin/core/frustum/ClippingHelperImplMixin.java",
                 "org/taumc/celeritas/impl/render/terrain/CameraHelper.java",
                 "org/taumc/celeritas/impl/render/frustum/IClippingHelper.java"]:
        sources[name] = (FORGE / name).read_text()
    for name in ["org/embeddedt/embeddium/impl/render/viewport/Viewport.java",
                 "org/embeddedt/embeddium/impl/render/viewport/ViewportProvider.java",
                 "org/embeddedt/embeddium/impl/render/viewport/CameraTransform.java",
                 "org/embeddedt/embeddium/impl/render/viewport/frustum/Frustum.java",
                 "org/embeddedt/embeddium/impl/util/PositionUtil.java",
                 "org/embeddedt/embeddium/impl/common/util/MathUtil.java"]:
        sources[name] = (COMMON / name).read_text()
    with tempfile.TemporaryDirectory(prefix="celtium-frustum-camera-") as tmp:
        directory = Path(tmp)
        paths = []
        for name, text in sources.items():
            path = directory / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            paths.append(str(path))
        classes = directory / "classes"
        subprocess.run([javac, "--release", "21", "-cp", str(jar), "-d", str(classes), *paths], check=True)
        # The fixtures intentionally use heap FloatBuffers. JOML's Unsafe backend assumes direct buffers;
        # its supported safe backend keeps this a pure Java CPU test and avoids native-address access.
        subprocess.run([java, "-Djoml.nounsafe=true", "-cp", os.pathsep.join([str(classes), str(jar)]),
                        "FrustumCameraOffsetProbe"], check=True, cwd=directory)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
