package cn.howxu.mmcr.api.data.view;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable public data repository request result.
 *
 * @author howxu <dev@howxu.cn>
 */
public record DataRepositoryRequest(ResourceLocation repositoryId, BlockPos controllerPos, String key,
                                    DataValueType requestedType, DataValue requestedValue,
                                    Optional<DataReservation> reservation) {
    public DataRepositoryRequest(ResourceLocation repositoryId, BlockPos controllerPos, String key,
                                 DataValueType requestedType, DataValue requestedValue) {
        this(repositoryId, controllerPos, key, requestedType, requestedValue, Optional.empty());
    }

    public DataRepositoryRequest {
        if (repositoryId == null) throw new IllegalArgumentException("repositoryId must not be null");
        if (controllerPos == null) throw new IllegalArgumentException("controllerPos must not be null");
        controllerPos = controllerPos.immutable();
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (requestedType == null) throw new IllegalArgumentException("requestedType must not be null");
        if (requestedValue == null) throw new IllegalArgumentException("requestedValue must not be null");
        if (requestedValue.type() != requestedType) {
            throw new IllegalArgumentException("requestedValue type must match requestedType");
        }
        if (reservation == null) throw new IllegalArgumentException("reservation must not be null");
    }

    public static DataRepositoryRequest available(ResourceLocation repositoryId, BlockPos controllerPos, String key,
                                                   DataValueType requestedType, DataValue requestedValue,
                                                   DataReservation reservation) {
        return new DataRepositoryRequest(repositoryId, controllerPos, key, requestedType, requestedValue,
                Optional.of(Objects.requireNonNull(reservation, "reservation")));
    }

    public static DataRepositoryRequest unavailable(ResourceLocation repositoryId, BlockPos controllerPos, String key,
                                                     DataValueType requestedType, DataValue requestedValue) {
        return new DataRepositoryRequest(repositoryId, controllerPos, key, requestedType, requestedValue);
    }
}
