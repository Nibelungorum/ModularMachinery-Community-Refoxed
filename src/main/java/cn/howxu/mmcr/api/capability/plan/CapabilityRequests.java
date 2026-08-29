package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.util.IOType;

import java.util.List;

/**
 * Immutable operation requests shared by built-in requirement handlers and capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityRequests {
    private CapabilityRequests() {
    }

    public record ResourceAction<R>(int slot, R resource, long amount, boolean insert) {
        public ResourceAction {
            if (slot < 0) throw new IllegalArgumentException("slot must be non-negative");
            if (resource == null) throw new IllegalArgumentException("resource must not be null");
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        }
    }

    public record ResourceRequest<R>(CapabilityType type, IOType ioType, long parallelism,
                                     List<ResourceAction<R>> actions) implements CapabilityRequest {
        public ResourceRequest {
            if (type == null || ioType == null) throw new IllegalArgumentException("request identity must not be null");
            if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
            actions = List.copyOf(actions);
        }
    }

    public record ValueRequest(CapabilityType type, IOType ioType, long parallelism,
                               long amount, boolean insert) implements CapabilityRequest {
        public ValueRequest {
            if (type == null || ioType == null) throw new IllegalArgumentException("request identity must not be null");
            if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        }
    }

    public record SmartValueRequest(CapabilityType type, IOType ioType, long parallelism,
                                    String interfaceType, float value) implements CapabilityRequest {
        public SmartValueRequest {
            if (type == null || ioType == null) throw new IllegalArgumentException("request identity must not be null");
            if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
            if (interfaceType == null || interfaceType.isBlank() || !Float.isFinite(value)) {
                throw new IllegalArgumentException("invalid smart value request");
            }
        }
    }
}
