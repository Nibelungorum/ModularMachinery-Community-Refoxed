package cn.howxu.mmcr.api.machine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Machine-level port count requirements checked before a matched structure forms.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PortRequirementSpec(Map<String, CountRange> requirements) {

    private static final PortRequirementSpec NONE = new PortRequirementSpec(Map.of());

    public PortRequirementSpec {
        if (requirements == null) throw new IllegalArgumentException("requirements null");
        requirements = Map.copyOf(requirements);
    }

    public static PortRequirementSpec none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return requirements.isEmpty();
    }

    public Optional<Failure> validate(PortCounts counts) {
        if (counts == null) throw new IllegalArgumentException("counts null");
        for (var entry : requirements.entrySet()) {
            String portId = entry.getKey();
            CountRange range = entry.getValue();
            int actual = counts.count(portId);
            if (actual < range.min()) {
                return Optional.of(new Failure(portId, actual, range.min(), range.max(), FailureReason.MISSING));
            }
            if (range.max().isPresent() && actual > range.max().getAsInt()) {
                return Optional.of(new Failure(portId, actual, range.min(), range.max(), FailureReason.TOO_MANY));
            }
        }
        return Optional.empty();
    }

    public record CountRange(int min, OptionalInt max) {
        public CountRange {
            if (min < 0) throw new IllegalArgumentException("min must be >= 0");
            if (max == null) throw new IllegalArgumentException("max null");
            if (max.isPresent() && max.getAsInt() < min) throw new IllegalArgumentException("max must be >= min");
        }

        public static CountRange min(int min) {
            return new CountRange(min, OptionalInt.empty());
        }

        public static CountRange range(int min, int max) {
            return new CountRange(min, OptionalInt.of(max));
        }
    }

    public record Failure(String portId, int actual, int requiredMin, OptionalInt requiredMax, FailureReason reason) {}

    public enum FailureReason {
        MISSING,
        TOO_MANY
    }

    public record PortCounts(Map<String, Integer> counts) {
        public PortCounts {
            if (counts == null) throw new IllegalArgumentException("counts null");
            Map<String, Integer> copy = new LinkedHashMap<>();
            for (var entry : counts.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) throw new IllegalArgumentException("port id blank");
                if (entry.getValue() == null || entry.getValue() < 0) throw new IllegalArgumentException("count must be >= 0");
                copy.put(entry.getKey(), entry.getValue());
            }
            counts = Map.copyOf(copy);
        }

        public static PortCounts empty() {
            return new PortCounts(Map.of());
        }

        public static PortCounts of(Map<String, Integer> counts) {
            return new PortCounts(counts);
        }

        public int count(String portId) {
            return counts.getOrDefault(portId, 0);
        }
    }

    public static final class Builder {
        private final Map<String, CountRange> requirements = new LinkedHashMap<>();

        private Builder() {}

        public Builder min(String portId, int min) {
            return put(portId, CountRange.min(min));
        }

        public Builder range(String portId, int min, int max) {
            return put(portId, CountRange.range(min, max));
        }

        private Builder put(String portId, CountRange range) {
            if (portId == null || portId.isBlank()) throw new IllegalArgumentException("port id blank");
            requirements.put(portId, range);
            return this;
        }

        public PortRequirementSpec build() {
            if (requirements.isEmpty()) return none();
            return new PortRequirementSpec(requirements);
        }
    }
}
