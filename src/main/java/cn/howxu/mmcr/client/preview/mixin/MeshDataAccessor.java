/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

/**
 * Replaces the index buffer on cached preview meshes after translucent resorting.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(MeshData.class)
public interface MeshDataAccessor {
    @Accessor("indexBuffer")
    @Nullable ByteBuffer mmcr$getIndexBuffer();

    @Accessor("indexBuffer")
    @Mutable
    void mmcr$setIndexBuffer(@Nullable ByteBuffer indexBuffer);
}
