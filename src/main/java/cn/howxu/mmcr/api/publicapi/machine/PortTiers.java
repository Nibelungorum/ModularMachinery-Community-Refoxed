package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.util.IOType;

import java.util.ArrayList;
import java.util.List;

/**
 * Port category and tier declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PortTiers(List<Requirement> requirements) {

    private static final PortTiers NONE = new PortTiers(List.of());

    public PortTiers {
        requirements = List.copyOf(requirements == null ? List.of() : requirements);
    }

    public static PortTiers none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public enum PortCategory {
        ITEM,
        FLUID,
        ENERGY
    }

    public enum ItemTier {
        TINY("tiny"),
        SMALL("small"),
        NORMAL("normal"),
        REINFORCED("reinforced"),
        BIG("big"),
        HUGE("huge"),
        LUDICROUS("ludicrous");

        private final String id;

        ItemTier(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public enum FluidTier {
        TINY("tiny"),
        SMALL("small"),
        NORMAL("normal"),
        REINFORCED("reinforced"),
        BIG("big"),
        HUGE("huge"),
        LUDICROUS("ludicrous"),
        VACUUM("vacuum");

        private final String id;

        FluidTier(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public enum EnergyTier {
        TINY("tiny"),
        SMALL("small"),
        NORMAL("normal"),
        REINFORCED("reinforced"),
        BIG("big"),
        HUGE("huge"),
        LUDICROUS("ludicrous"),
        ULTIMATE("ultimate");

        private final String id;

        EnergyTier(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Requirement(PortCategory category, IOType ioType, int minTier, String minTierId) {
        public Requirement {
            if (category == null) throw new IllegalArgumentException("category null");
            if (ioType == null) throw new IllegalArgumentException("ioType null");
            if (minTier < 0) throw new IllegalArgumentException("minTier must be >= 0");
            if (minTierId == null || minTierId.isBlank()) throw new IllegalArgumentException("minTierId blank");
            String expectedTierId = switch (category) {
                case ITEM -> minTier < ItemTier.values().length ? ItemTier.values()[minTier].id() : null;
                case FLUID -> minTier < FluidTier.values().length ? FluidTier.values()[minTier].id() : null;
                case ENERGY -> minTier < EnergyTier.values().length ? EnergyTier.values()[minTier].id() : null;
            };
            if (!minTierId.equals(expectedTierId)) {
                throw new IllegalArgumentException("minTier and minTierId do not match");
            }
        }
    }

    /**
     * Builder for port category and tier declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private final List<Requirement> requirements = new ArrayList<>();

        public Builder anyItemInput() { return minItemInput(ItemTier.TINY); }

        public Builder anyItemOutput() { return minItemOutput(ItemTier.TINY); }

        public Builder anyFluidInput() { return minFluidInput(FluidTier.TINY); }

        public Builder anyFluidOutput() { return minFluidOutput(FluidTier.TINY); }

        public Builder anyEnergyInput() { return minEnergyInput(EnergyTier.TINY); }

        public Builder anyEnergyOutput() { return minEnergyOutput(EnergyTier.TINY); }

        public Builder minItemInput(ItemTier size) {
            return add(PortCategory.ITEM, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minItemOutput(ItemTier size) {
            return add(PortCategory.ITEM, IOType.OUTPUT, size.ordinal(), size.id());
        }

        public Builder minFluidInput(FluidTier size) {
            return add(PortCategory.FLUID, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minFluidOutput(FluidTier size) {
            return add(PortCategory.FLUID, IOType.OUTPUT, size.ordinal(), size.id());
        }

        public Builder minEnergyInput(EnergyTier size) {
            return add(PortCategory.ENERGY, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minEnergyOutput(EnergyTier size) {
            return add(PortCategory.ENERGY, IOType.OUTPUT, size.ordinal(), size.id());
        }

        private Builder add(PortCategory category, IOType ioType, int minTier, String minTierId) {
            requirements.add(new Requirement(category, ioType, minTier, minTierId));
            return this;
        }

        public PortTiers build() {
            if (requirements.isEmpty()) return none();
            return new PortTiers(requirements);
        }
    }
}
