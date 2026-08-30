package cn.howxu.mmcr.api.publicapi.machine;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Structure stage declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record StructureStage(
        Kind kind,
        PatternDefinition pattern,
        PortRequirements portRequirements,
        PortTiers portTiers,
        StructureRequirements requirements) {

    public enum Kind {
        FULL,
        EXPANSION,
        EXTENSION
    }

    public StructureStage {
        kind = Objects.requireNonNull(kind, "kind");
        pattern = Objects.requireNonNull(pattern, "pattern");
        portRequirements = portRequirements == null ? PortRequirements.none() : portRequirements;
        portTiers = portTiers == null ? PortTiers.none() : portTiers;
        requirements = requirements == null ? StructureRequirements.EMPTY : requirements;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for structure stage declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private Kind kind = Kind.FULL;
        private PatternDefinition pattern;
        private PortRequirements portRequirements = PortRequirements.none();
        private PortTiers portTiers = PortTiers.none();
        private final StructureRequirements.Builder requirements = StructureRequirements.builder();

        public Builder full() {
            kind = Kind.FULL;
            return this;
        }

        public Builder expansion() {
            kind = Kind.EXPANSION;
            return this;
        }

        public Builder extension() {
            kind = Kind.EXTENSION;
            return this;
        }

        public Builder pattern(UnaryOperator<PatternBuilder> builder) {
            pattern = Objects.requireNonNull(builder, "builder").apply(PatternBuilder.pattern()).build();
            return this;
        }

        public Builder ports(UnaryOperator<PortRequirements.Builder> builder) {
            portRequirements = Objects.requireNonNull(builder, "builder").apply(PortRequirements.builder()).build();
            return this;
        }

        public Builder portTiers(UnaryOperator<PortTiers.Builder> builder) {
            portTiers = Objects.requireNonNull(builder, "builder").apply(PortTiers.builder()).build();
            return this;
        }

        public Builder requirements(UnaryOperator<StructureRequirements.Builder> builder) {
            Objects.requireNonNull(builder, "builder").apply(requirements);
            return this;
        }

        public Builder modifier(char symbol, ModifierUse use) {
            requirements.modifier(symbol, use);
            return this;
        }

        public StructureStage build() {
            return new StructureStage(kind, pattern, portRequirements, portTiers, requirements.build());
        }
    }
}
