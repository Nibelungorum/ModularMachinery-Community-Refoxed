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
    @Nullable
    private static Matrix4f viewRotation;
    @Nullable
    private static Matrix4f projection;

    private PreviewSceneCameraContext() {
    }

    public static void with(Matrix4f viewRotationMatrix, Matrix4f projectionMatrix, Runnable render) {
        set(viewRotationMatrix, projectionMatrix);
        try {
            render.run();
        } finally {
            clear();
        }
    }

    public static void set(Matrix4f viewRotationMatrix, Matrix4f projectionMatrix) {
        viewRotation = new Matrix4f(viewRotationMatrix);
        projection = new Matrix4f(projectionMatrix);
    }

    public static void clear() {
        viewRotation = null;
        projection = null;
    }

    public static boolean isActive() {
        return viewRotation != null && projection != null;
    }

    @Nullable
    public static Matrix4f viewRotation() {
        return viewRotation == null ? null : new Matrix4f(viewRotation);
    }

    @Nullable
    public static Matrix4f projection() {
        return projection == null ? null : new Matrix4f(projection);
    }
}
