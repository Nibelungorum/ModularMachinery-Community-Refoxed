package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.network.RequestBody;
import cn.howxu.mmcr.api.network.RequestFailed;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable server-thread work item for a machine network request.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PendingRequest(GlobalPos sourceEndpoint, GlobalPos targetEndpoint, @Nullable GlobalPos sourceController, MachineReference target,
                              Identifier requestId, RequestBody body, long enqueueTick, @Nullable RequestFailed sourceFailure) {
    public PendingRequest(GlobalPos sourceEndpoint, GlobalPos targetEndpoint, @Nullable GlobalPos sourceController,
                          MachineReference target, Identifier requestId, RequestBody body, long enqueueTick) {
        this(sourceEndpoint, targetEndpoint, sourceController, target, requestId, body, enqueueTick, null);
    }

    public PendingRequest {
        Objects.requireNonNull(sourceEndpoint, "sourceEndpoint");
        Objects.requireNonNull(targetEndpoint, "targetEndpoint");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(body, "body");
    }
}
