package cn.howxu.mmcr.publicapi.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.Optional;

/** Repository result retaining the requested value and optional user reservation.
 * @author howxu <dev@howxu.cn>
 */
public record RepositoryRequest(ResourceLocation repositoryId, BlockPos controllerPos, String key,
                                DataKind requestedType, DataKey requestedValue, Optional<Reservation> reservation) {
    public RepositoryRequest(ResourceLocation id, BlockPos pos, String key, DataKind kind, DataKey value) {
        this(id, pos, key, kind, value, Optional.empty());
    }
    public RepositoryRequest {
        Objects.requireNonNull(repositoryId, "repositoryId");
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        Objects.requireNonNull(requestedType, "requestedType");
        Objects.requireNonNull(requestedValue, "requestedValue");
        if (requestedValue.type() != requestedType) throw new IllegalArgumentException("requestedValue type must match requestedType");
        Objects.requireNonNull(reservation, "reservation");
    }
    public static RepositoryRequest available(ResourceLocation id, BlockPos pos, String key, DataKind kind,
                                              DataKey value, Reservation reservation) {
        return new RepositoryRequest(id, pos, key, kind, value, Optional.of(reservation));
    }
    public static RepositoryRequest unavailable(ResourceLocation id, BlockPos pos, String key, DataKind kind, DataKey value) {
        return new RepositoryRequest(id, pos, key, kind, value);
    }
}
