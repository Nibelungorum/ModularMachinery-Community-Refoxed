package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.util.IOType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Machine-level minimum tier requirements for IO ports checked during structure formation.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PortTierRequirementSpec(List<Requirement> requirements) {

    private static final PortTierRequirementSpec NONE = new PortTierRequirementSpec(List.of());

    public PortTierRequirementSpec {
        if (requirements == null) throw new IllegalArgumentException("requirements null");
        requirements = List.copyOf(requirements);
    }

    public static PortTierRequirementSpec none() {
        return NONE;
    }

    public static PortTierRequirementSpec from(PortTiers tiers) {
        if (tiers == null) throw new IllegalArgumentException("tiers null");
        if (tiers.requirements().isEmpty()) return none();
        return new PortTierRequirementSpec(tiers.requirements().stream()
                .map(requirement -> new Requirement(
                        switch (requirement.category()) {
                            case ITEM -> PortCategory.ITEM;
                            case FLUID -> PortCategory.FLUID;
                            case ENERGY -> PortCategory.ENERGY;
                        }, requirement.ioType(), requirement.minTier(), requirement.minTierId()))
                .toList());
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return requirements.isEmpty();
    }

    public Optional<Failure> validate(Iterable<IOPortKind> ports) {
        if (ports == null) throw new IllegalArgumentException("ports null");
        List<IOPortKind> list = new ArrayList<>();
        for (IOPortKind port : ports) {
            if (port != null) list.add(port);
        }

        for (Requirement requirement : requirements) {
            if (list.stream().noneMatch(requirement::matches)) {
                List<String> actual = list.stream()
                        .filter(requirement::sameFamily)
                        .map(IOPortKind::id)
                        .toList();
                return Optional.of(new Failure(requirement, actual));
            }
        }
        return Optional.empty();
    }

    public enum PortCategory {
        ITEM,
        FLUID,
        ENERGY
    }

    public record Requirement(PortCategory category, IOType ioType, int minTier, String minTierId) {
        public Requirement {
            if (category == null) throw new IllegalArgumentException("category null");
            if (ioType == null) throw new IllegalArgumentException("ioType null");
            if (minTier < 0) throw new IllegalArgumentException("minTier must be >= 0");
            if (minTierId == null || minTierId.isBlank()) throw new IllegalArgumentException("minTierId blank");
        }

        public String id() {
            return baseId() + ">=" + minTierId;
        }

        private String baseId() {
            return switch (category) {
                case ITEM -> ioType == IOType.INPUT ? "item_input_bus" : "item_output_bus";
                case FLUID -> ioType == IOType.INPUT ? "fluid_input_hatch" : "fluid_output_hatch";
                case ENERGY -> ioType == IOType.INPUT ? "energy_input_hatch" : "energy_output_hatch";
            };
        }

        private boolean matches(IOPortKind port) {
            return sameFamily(port) && tier(port) >= minTier;
        }

        private boolean sameFamily(IOPortKind port) {
            if (port.ioType() != ioType) return false;
            return switch (category) {
                case ITEM -> port.itemBusSize().isPresent();
                case FLUID -> port.fluidHatchSize().isPresent();
                case ENERGY -> port.energyHatchSize().isPresent();
            };
        }

        private int tier(IOPortKind port) {
            return switch (category) {
                case ITEM -> port.itemBusSize().map(Enum::ordinal).orElse(-1);
                case FLUID -> port.fluidHatchSize().map(Enum::ordinal).orElse(-1);
                case ENERGY -> port.energyHatchSize().map(Enum::ordinal).orElse(-1);
            };
        }
    }

    public record Failure(Requirement requirement, List<String> actualPortIds) {
        public Failure {
            if (requirement == null) throw new IllegalArgumentException("requirement null");
            actualPortIds = List.copyOf(actualPortIds == null ? List.of() : actualPortIds);
        }
    }

    public static final class Builder {
        private final List<Requirement> requirements = new ArrayList<>();

        private Builder() {}

        public Builder anyItemInput() {
            return minItemInput(ItemBusSize.TINY);
        }

        public Builder anyItemOutput() {
            return minItemOutput(ItemBusSize.TINY);
        }

        public Builder anyFluidInput() {
            return minFluidInput(FluidHatchSize.TINY);
        }

        public Builder anyFluidOutput() {
            return minFluidOutput(FluidHatchSize.TINY);
        }

        public Builder anyEnergyInput() {
            return minEnergyInput(EnergyHatchSize.TINY);
        }

        public Builder anyEnergyOutput() {
            return minEnergyOutput(EnergyHatchSize.TINY);
        }

        public Builder minItemInput(ItemBusSize size) {
            return add(PortCategory.ITEM, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minItemOutput(ItemBusSize size) {
            return add(PortCategory.ITEM, IOType.OUTPUT, size.ordinal(), size.id());
        }

        public Builder minFluidInput(FluidHatchSize size) {
            return add(PortCategory.FLUID, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minFluidOutput(FluidHatchSize size) {
            return add(PortCategory.FLUID, IOType.OUTPUT, size.ordinal(), size.id());
        }

        public Builder minEnergyInput(EnergyHatchSize size) {
            return add(PortCategory.ENERGY, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minEnergyOutput(EnergyHatchSize size) {
            return add(PortCategory.ENERGY, IOType.OUTPUT, size.ordinal(), size.id());
        }

        private Builder add(PortCategory category, IOType ioType, int minTier, String minTierId) {
            requirements.add(new Requirement(category, ioType, minTier, minTierId));
            return this;
        }

        public PortTierRequirementSpec build() {
            if (requirements.isEmpty()) return none();
            return new PortTierRequirementSpec(requirements);
        }
    }
}
