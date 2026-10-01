package cn.howxu.mmcr.publicapi.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Immutable repository query. @author howxu <dev@howxu.cn> */
public record RepositoryContext(ResourceLocation machineId, BlockPos controllerPos, String key, DataKind requestedType) {
    public RepositoryContext {
        Objects.requireNonNull(machineId, "machineId");
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        Objects.requireNonNull(requestedType, "requestedType");
    }
}
