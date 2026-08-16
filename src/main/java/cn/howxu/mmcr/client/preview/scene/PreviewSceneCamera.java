/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewCamera;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Matrices derived from the structure-preview camera for an off-screen scene render.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneCamera {
    private static final float FIELD_OF_VIEW = (float) Math.toRadians(60.0D);
    private static final float NEAR_PLANE = 0.05F;
    private static final float FAR_PLANE = 1_000.0F;

    private final Vector3f eye;
    private final Vector3f lookAt;
    private final Vector3f up;
    private final Matrix4f view;
    private final Matrix4f projection;

    private PreviewSceneCamera(Vector3f eye, Vector3f lookAt, int width, int height) {
        this.eye = new Vector3f(eye);
        this.lookAt = new Vector3f(lookAt);
        this.up = new Vector3f(0.0F, 1.0F, 0.0F);
        this.view = new Matrix4f().lookAt(this.eye, this.lookAt, up);
        this.projection = new Matrix4f().perspective(FIELD_OF_VIEW, (float) width / height, NEAR_PLANE, FAR_PLANE);
    }

    public static PreviewSceneCamera from(PreviewCamera camera, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Scene viewport dimensions must be positive");
        }
        return new PreviewSceneCamera(camera.position(), camera.lookAt(), width, height);
    }

    public Vector3f eye() {
        return new Vector3f(eye);
    }

    public Vector3f lookAt() {
        return new Vector3f(lookAt);
    }

    public Vector3f up() {
        return new Vector3f(up);
    }

    public Matrix4f view() {
        return new Matrix4f(view);
    }

    public Matrix4f projection() {
        return new Matrix4f(projection);
    }

    public Matrix4f viewRotation() {
        return new Matrix4f(view).m30(0.0F).m31(0.0F).m32(0.0F);
    }
}
