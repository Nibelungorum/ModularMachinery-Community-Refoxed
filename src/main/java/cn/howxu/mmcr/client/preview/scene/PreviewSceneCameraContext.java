/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Thread-confined camera matrices published during a preview scene render.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneCameraContext {
    private static final ThreadLocal<CameraMatrices> CURRENT = new ThreadLocal<>();

    private PreviewSceneCameraContext() {
    }

    public static void with(Matrix4f viewRotationMatrix, Matrix4f projectionMatrix, Runnable render) {
        CameraMatrices previous = CURRENT.get();
        set(viewRotationMatrix, projectionMatrix);
        try {
            render.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void set(Matrix4f viewRotationMatrix, Matrix4f projectionMatrix) {
        CURRENT.set(new CameraMatrices(viewRotationMatrix, projectionMatrix));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    @Nullable
    public static Matrix4f viewRotation() {
        CameraMatrices matrices = CURRENT.get();
        return matrices == null ? null : new Matrix4f(matrices.viewRotation);
    }

    @Nullable
    public static Matrix4f projection() {
        CameraMatrices matrices = CURRENT.get();
        return matrices == null ? null : new Matrix4f(matrices.projection);
    }

    private record CameraMatrices(Matrix4f viewRotation, Matrix4f projection) {
        private CameraMatrices(Matrix4f viewRotation, Matrix4f projection) {
            this.viewRotation = new Matrix4f(viewRotation);
            this.projection = new Matrix4f(projection);
        }
    }
}
