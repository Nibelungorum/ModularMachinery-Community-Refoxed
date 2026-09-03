package cn.howxu.mmcr.api.network;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Identifies a received request and its peer machine.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RequestInfo(Identifier requestId, MachineReference peer) {
    public RequestInfo {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(peer, "peer");
    }
}
