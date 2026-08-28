package cn.howxu.mmcr.api.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Immutable input supplied when a future repository is queried.
 * @author howxu <dev@howxu.cn>
 */
public record DataRepositoryContext(Identifier machineId, BlockPos controllerPos,
                                    String key, DataValueType requestedType) {
    public DataRepositoryContext {
        if (machineId == null) throw new IllegalArgumentException("machineId must not be null");
        if (controllerPos == null) throw new IllegalArgumentException("controllerPos must not be null");
        controllerPos = controllerPos.immutable();
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (requestedType == null) throw new IllegalArgumentException("requestedType must not be null");
    }
}
