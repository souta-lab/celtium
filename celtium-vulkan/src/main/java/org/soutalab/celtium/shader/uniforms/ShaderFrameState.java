/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 * Pure Java frame-state snapshot supplying built-in shader pack uniforms with zero GLSM or
 * Minecraft class dependencies inside :celtium-vulkan.
 */
package org.soutalab.celtium.shader.uniforms;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.soutalab.celtium.shader.model.FogMode;

/**
 * Mutable per-frame state container populated by the host (Forge/Minecraft side in S5-b) or by
 * tests, and consumed by {@link Std140UniformPacker} to fill the single std140 pack uniform block
 * once per frame.
 */
public final class ShaderFrameState {

    public int viewWidth = 1280;
    public int viewHeight = 720;

    public long worldTime = 6000L;
    public int worldDay = 0;
    public int moonPhase = 0;
    public float sunAngle = 0.25f;
    public float shadowAngle = 0.25f;
    public float skyAngle = 0.0f;

    public float frameTime = 1.0f / 60.0f;
    public float frameTimeCounter = 0.0f;
    public int frameCounter = 0;

    public float rainStrength = 0.0f;
    public float wetness = 0.0f;
    public float thunderStrength = 0.0f;

    public int isEyeInWater = 0;
    public int eyeBrightnessBlock = 0;
    public int eyeBrightnessSky = 240;
    public float eyeBrightnessSmoothBlock = 0.0f;
    public float eyeBrightnessSmoothSky = 240.0f;
    public float centerDepthSmooth = 0.5f;

    public float nightVision = 0.0f;
    public float blindness = 0.0f;
    public float darknessFactor = 0.0f;
    public float darknessLightFactor = 0.0f;
    public float screenBrightness = 0.5f;

    public float nearPlane = 0.05f;
    public float farPlane = 192.0f;

    public final Vector3d cameraPosition = new Vector3d(0.0, 64.0, 0.0);
    public final Vector3d previousCameraPosition = new Vector3d(0.0, 64.0, 0.0);
    public final Vector3f skyColor = new Vector3f(0.47f, 0.65f, 1.0f);
    public final Vector3f fogColor = new Vector3f(0.75f, 0.85f, 1.0f);
    public float fogDensity = 0.0f;
    public float fogStart = 144.0f;
    public float fogEnd = 192.0f;
    public FogMode fogMode = FogMode.PER_VERTEX;
    public int fogShape = 0;

    public final Matrix4f gbufferModelView = new Matrix4f().identity();
    public final Matrix4f gbufferModelViewInverse = new Matrix4f().identity();
    public final Matrix4f gbufferPreviousModelView = new Matrix4f().identity();
    public final Matrix4f gbufferProjection = new Matrix4f().identity();
    public final Matrix4f gbufferProjectionInverse = new Matrix4f().identity();
    public final Matrix4f gbufferPreviousProjection = new Matrix4f().identity();
    public final Matrix4f shadowModelView = new Matrix4f().identity();
    public final Matrix4f shadowModelViewInverse = new Matrix4f().identity();
    public final Matrix4f shadowProjection = new Matrix4f().identity();
    public final Matrix4f shadowProjectionInverse = new Matrix4f().identity();

    public final Vector3f sunPosition = new Vector3f(0.0f, 100.0f, 0.0f);
    public final Vector3f moonPosition = new Vector3f(0.0f, -100.0f, 0.0f);
    public final Vector3f shadowLightPosition = new Vector3f(0.0f, 100.0f, 0.0f);
    public final Vector3f upPosition = new Vector3f(0.0f, 100.0f, 0.0f);

    public int heldItemId = -1;
    public int heldBlockLightValue = 0;
    public int heldItemId2 = -1;
    public int heldBlockLightValue2 = 0;
    public int entityId = -1;
    public int blockEntityId = -1;
    public int currentRenderedItemId = -1;
    public final Vector4f entityColor = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    public int biomeId = 1;
    public int biomeCategory = 0;
    public int biomePrecipitation = 1;
    public float biomeTemperature = 0.8f;
    public float biomeRainfall = 0.4f;

    public boolean hideGUI = false;
    public boolean firstPersonCamera = true;
    public boolean isSneaking = false;
    public boolean isSprinting = false;
    public boolean isOnGround = true;
    public boolean isSpectator = false;
    public boolean isHurt = false;
    public boolean isInvisible = false;
    public boolean isBurning = false;
    public float playerMood = 0.0f;
    public float currentPlayerHealth = 1.0f;
    public float maxPlayerHealth = 20.0f;
    public float currentPlayerHunger = 1.0f;
    public float maxPlayerHunger = 20.0f;
    public float currentPlayerAir = 1.0f;
    public float maxPlayerAir = 300.0f;

    public float aspectRatio() {
        return viewHeight > 0 ? (float) viewWidth / (float) viewHeight : 1.0f;
    }

    public Vector3dc cameraPosition() {
        return cameraPosition;
    }

    public Vector3dc previousCameraPosition() {
        return previousCameraPosition;
    }

    public Vector3fc skyColor() {
        return skyColor;
    }

    public Vector3fc fogColor() {
        return fogColor;
    }

    public Vector4fc entityColor() {
        return entityColor;
    }

    public Matrix4fc gbufferModelView() {
        return gbufferModelView;
    }

    public Matrix4fc gbufferModelViewInverse() {
        return gbufferModelViewInverse;
    }

    public Matrix4fc gbufferPreviousModelView() {
        return gbufferPreviousModelView;
    }

    public Matrix4fc gbufferProjection() {
        return gbufferProjection;
    }

    public Matrix4fc gbufferProjectionInverse() {
        return gbufferProjectionInverse;
    }

    public Matrix4fc gbufferPreviousProjection() {
        return gbufferPreviousProjection;
    }

    public Matrix4fc shadowModelView() {
        return shadowModelView;
    }

    public Matrix4fc shadowModelViewInverse() {
        return shadowModelViewInverse;
    }

    public Matrix4fc shadowProjection() {
        return shadowProjection;
    }

    public Matrix4fc shadowProjectionInverse() {
        return shadowProjectionInverse;
    }
}
