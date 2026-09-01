package cn.howxu.mmcr.api.capability.sync;

import net.minecraft.resources.Identifier;

import java.util.Arrays;

/**
 * One bounded capability sync payload addressed by type and occurrence index.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CapabilitySyncEntry(Identifier typeId, int capabilityIndex, byte[] payload) {
    public static final int MAX_PAYLOAD_BYTES = 65_536;

    public CapabilitySyncEntry {
        if (typeId == null) throw new IllegalArgumentException("Capability type must not be null");
        if (capabilityIndex < 0) throw new IllegalArgumentException("Capability index must be non-negative");
        if (payload == null || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid capability sync payload");
        }
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
