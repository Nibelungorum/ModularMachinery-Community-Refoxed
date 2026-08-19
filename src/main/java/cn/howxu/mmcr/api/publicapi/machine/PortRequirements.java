package cn.howxu.mmcr.api.publicapi.machine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Basic port count declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PortRequirements(Map<String, CountRange> requirements) {

    private static final PortRequirements NONE = new PortRequirements(Map.of());

    public PortRequirements {
        requirements = Map.copyOf(requirements == null ? Map.of() : requirements);
    }

    public static PortRequirements none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public record CountRange(int min, OptionalInt max) {
        public CountRange {
            if (min < 0) throw new IllegalArgumentException("min must be >= 0");
            if (max == null) throw new IllegalArgumentException("max null");
            if (max.isPresent() && max.getAsInt() < min) throw new IllegalArgumentException("max must be >= min");
        }
    }

    /**
     * Builder for basic port count declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private final Map<String, CountRange> requirements = new LinkedHashMap<>();

        public Builder min(String portId, int min) {
            return put(portId, new CountRange(min, OptionalInt.empty()));
        }

        public Builder range(String portId, int min, int max) {
            return put(portId, new CountRange(min, OptionalInt.of(max)));
        }

        private Builder put(String portId, CountRange range) {
            if (portId == null || portId.isBlank()) throw new IllegalArgumentException("port id blank");
            requirements.put(portId, range);
            return this;
        }

        public PortRequirements build() {
            if (requirements.isEmpty()) return none();
            return new PortRequirements(requirements);
        }
    }
}
