package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.resources.Identifier;
import java.util.Locale;

import java.util.ArrayList;
import java.util.Arrays;
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

    public static PortTiers combine(PortTiers... declarations) {
        if (declarations == null) throw new NullPointerException("declarations");
        List<Requirement> combined = new ArrayList<>();
        for (PortTiers declaration : declarations) {
            if (declaration != null) combined.addAll(declaration.requirements());
        }
        return combined.isEmpty() ? none() : new PortTiers(combined);
    }

    public enum PortCategory {
        ITEM(PortFamilyIds.ITEM, "item_%s_bus"),
        FLUID(PortFamilyIds.FLUID, "fluid_%s_hatch"),
        ENERGY(PortFamilyIds.ENERGY, "energy_%s_hatch"),
        CHEMICAL(MekanismPortFamilies.CHEMICAL, "chemical_%s_hatch"),
        RADIOACTIVE_CHEMICAL(MekanismPortFamilies.RADIOACTIVE_CHEMICAL, "radioactive_chemical_%s_hatch"),
        HEAT(MekanismPortFamilies.HEAT, "heat_%s_hatch"),
        CUSTOM(null, null);

        private final Identifier familyId;
        private final String alias;
        PortCategory(Identifier familyId, String alias) { this.familyId = familyId; this.alias = alias; }
        public Identifier familyId() { return familyId; }
        public String alias(IOType direction) { return alias.formatted(direction.name().toLowerCase(Locale.ROOT)); }
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

    /** Chemical hatch tiers mapped to the registered detection tiers.
     * @author howxu <dev@howxu.cn>
     */
    public enum ChemicalTier {
        BASIC("basic", 2),
        ADVANCED("advanced", 3),
        ELITE("elite", 4),
        ULTIMATE("ultimate", 5);

        private final String id;
        private final int detectionTier;

        ChemicalTier(String id, int detectionTier) {
            this.id = id;
            this.detectionTier = detectionTier;
        }

        public String id() { return id; }

        public int detectionTier() { return detectionTier; }
    }

    /** Legacy aliases retain named tiers. Extensions use alias>=N or namespace:family@input>=N. */
    public static PortTiers parse(String expression) {
        String[] parts = expression.split(">=", -1);
        if (parts.length > 2) throw new IllegalArgumentException("Invalid port tier: " + expression);
        String name = parts[0];
        Identifier family = null;
        IOType direction = null;
        for (var category : PortCategory.values()) {
            if (category == PortCategory.CUSTOM) continue;
            for (var io : IOType.values()) {
                if (category.alias(io).equals(name)) { family = category.familyId(); direction = io; }
            }
        }
        for (var port : PortKinds.all()) {
            for (var descriptor : port.families()) {
                if (!descriptor.countAliases().contains(name) && !qualified(descriptor.familyId(), descriptor.ioType()).equals(name)) continue;
                if (family != null && (!family.equals(descriptor.familyId()) || direction != descriptor.ioType())) {
                    throw new IllegalArgumentException("Ambiguous port alias " + name + ": " + family + "/" + direction
                            + " and " + descriptor.familyId() + "/" + descriptor.ioType());
                }
                family = descriptor.familyId();
                direction = descriptor.ioType();
            }
        }
        if (family == null) throw new IllegalArgumentException("Unknown port family: " + name);
        for (var category : PortCategory.values()) {
            if (!family.equals(category.familyId())) continue;
            if (category == PortCategory.RADIOACTIVE_CHEMICAL || category == PortCategory.HEAT) {
                if (parts.length != 1) throw new IllegalArgumentException("Single-tier port: " + name);
                return new PortTiers(List.of(new Requirement(category, direction, 0, "any")));
            }
            if (parts.length != 2) throw new IllegalArgumentException("Missing minimum tier: " + name);
            String tier = parts[1].toUpperCase(Locale.ROOT);
            int minimum = switch (category) {
                case ITEM -> ItemTier.valueOf(tier).ordinal();
                case FLUID -> FluidTier.valueOf(tier).ordinal();
                case ENERGY -> EnergyTier.valueOf(tier).ordinal();
                case CHEMICAL -> ChemicalTier.valueOf(tier).detectionTier();
                default -> throw new IllegalStateException("Not a tiered legacy family");
            };
            return new PortTiers(List.of(new Requirement(category, direction, minimum, parts[1].toLowerCase(Locale.ROOT))));
        }
        if (parts.length != 2) throw new IllegalArgumentException("Missing numeric minimum tier: " + name);
        return new PortTiers(List.of(new Requirement(PortCategory.CUSTOM, direction, Integer.parseInt(parts[1]), parts[1], family)));
    }

    public static String qualified(Identifier family, IOType io) {
        return family + "@" + io.name().toLowerCase(Locale.ROOT);
    }

    public record Requirement(PortCategory category, IOType ioType, int minTier, String minTierId, Identifier familyId) {
        public Requirement(PortCategory category, IOType ioType, int minTier, String minTierId) {
            this(category, ioType, minTier, minTierId, category == null ? null : category.familyId());
        }

        public Requirement {
            if (category == null) throw new IllegalArgumentException("category null");
            if (ioType == null) throw new IllegalArgumentException("ioType null");
            if (minTier < 0) throw new IllegalArgumentException("minTier must be >= 0");
            if (minTierId == null || minTierId.isBlank()) throw new IllegalArgumentException("minTierId blank");
            if (familyId == null || category != PortCategory.CUSTOM && !familyId.equals(category.familyId())) {
                throw new IllegalArgumentException("familyId does not match category");
            }
            String expectedTierId = switch (category) {
                case ITEM -> minTier < ItemTier.values().length ? ItemTier.values()[minTier].id() : null;
                case FLUID -> minTier < FluidTier.values().length ? FluidTier.values()[minTier].id() : null;
                case ENERGY -> minTier < EnergyTier.values().length ? EnergyTier.values()[minTier].id() : null;
                case CHEMICAL -> Arrays.stream(ChemicalTier.values())
                        .filter(tier -> tier.detectionTier() == minTier).map(ChemicalTier::id).findFirst().orElse(null);
                case CUSTOM -> Integer.toString(minTier);
                case RADIOACTIVE_CHEMICAL, HEAT -> minTier == 0 ? "any" : null;
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

        public Builder anyChemicalInput() { return minChemicalInput(ChemicalTier.BASIC); }

        public Builder anyChemicalOutput() { return minChemicalOutput(ChemicalTier.BASIC); }

        public Builder anyRadioactiveChemicalInput() { return add(PortCategory.RADIOACTIVE_CHEMICAL, IOType.INPUT, 0, "any"); }

        public Builder anyRadioactiveChemicalOutput() { return add(PortCategory.RADIOACTIVE_CHEMICAL, IOType.OUTPUT, 0, "any"); }

        public Builder anyHeatInput() { return add(PortCategory.HEAT, IOType.INPUT, 0, "any"); }

        public Builder anyHeatOutput() { return add(PortCategory.HEAT, IOType.OUTPUT, 0, "any"); }

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

        public Builder minChemicalInput(ChemicalTier size) {
            return add(PortCategory.CHEMICAL, IOType.INPUT, size.detectionTier(), size.id());
        }

        public Builder minChemicalOutput(ChemicalTier size) {
            return add(PortCategory.CHEMICAL, IOType.OUTPUT, size.detectionTier(), size.id());
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
