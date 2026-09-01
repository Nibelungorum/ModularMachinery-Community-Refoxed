package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedCombinedPortSize;
import cn.howxu.mmcr.internal.port.ExtendedEnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.port.CombinedPortSize;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.api.port.PortDefinitionRegistry;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedCombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedEnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedFluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PortKinds {

    public record Simple(
            String id,
            IOType ioType,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {
        @Override
        public PortDefinition definition() {
            return PortDefinition.of(MMCR.id(id), List.of());
        }
    }

    public record ItemBusKind(
            String id,
            IOType ioType,
            ItemBusSize size,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {

        @Override
        public Optional<ItemBusSize> itemBusSize() {
            return Optional.of(size);
        }

        @Override
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(PortFamilyIds.ITEM, ioType, size.ordinal(),
                    List.of(ioType == IOType.INPUT ? "item_input_bus" : "item_output_bus")));
        }

        @Override
        public PortDefinition definition() {
            return PortKinds.definition(id, ioType, families(), List.of(BuiltinCapabilityDefinitions.ITEM_TYPE));
        }
    }

    public record FluidHatchKind(
            String id,
            IOType ioType,
            FluidHatchSize size,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {

        @Override
        public Optional<FluidHatchSize> fluidHatchSize() {
            return Optional.of(size);
        }

        @Override
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType, size.ordinal(),
                    List.of(ioType == IOType.INPUT ? "fluid_input_hatch" : "fluid_output_hatch")));
        }

        @Override
        public PortDefinition definition() {
            return PortKinds.definition(id, ioType, families(), List.of(BuiltinCapabilityDefinitions.FLUID_TYPE));
        }
    }

    public record EnergyHatchKind(
            String id,
            IOType ioType,
            EnergyHatchSize size,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {

        @Override
        public Optional<EnergyHatchSize> energyHatchSize() {
            return Optional.of(size);
        }

        @Override
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(PortFamilyIds.ENERGY, ioType, size.ordinal(),
                    List.of(ioType == IOType.INPUT ? "energy_input_hatch" : "energy_output_hatch")));
        }

        @Override
        public PortDefinition definition() {
            return PortKinds.definition(id, ioType, families(), List.of(BuiltinCapabilityDefinitions.ENERGY_TYPE));
        }
    }

    public record ExtendedItemBusKind(
            String id,
            IOType ioType,
            ExtendedItemBusSize size,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {

        @Override
        public Optional<ExtendedItemBusSize> extendedItemBusSize() {
            return Optional.of(size);
        }

        @Override
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(PortFamilyIds.ITEM, ioType,
                    ItemBusSize.LUDICROUS.ordinal() + 1, List.of(itemAlias(ioType))));
        }

        @Override
        public PortDefinition definition() {
            return PortKinds.definition(id, ioType, families(), List.of(BuiltinCapabilityDefinitions.ITEM_TYPE));
        }
    }

    public record ExtendedFluidHatchKind(
            String id,
            IOType ioType,
            ExtendedFluidHatchSize size,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {

        @Override
        public Optional<ExtendedFluidHatchSize> extendedFluidHatchSize() {
            return Optional.of(size);
        }

        @Override
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType,
                    FluidHatchSize.VACUUM.ordinal() + 1, List.of(fluidAlias(ioType))));
        }

        @Override
        public PortDefinition definition() {
            return PortKinds.definition(id, ioType, families(), List.of(BuiltinCapabilityDefinitions.FLUID_TYPE));
        }
    }

    public record ExtendedEnergyHatchKind(
            String id,
            IOType ioType,
            ExtendedEnergyHatchSize size,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {

        @Override
        public Optional<ExtendedEnergyHatchSize> extendedEnergyHatchSize() {
            return Optional.of(size);
        }

        @Override
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(PortFamilyIds.ENERGY, ioType,
                    EnergyHatchSize.ULTIMATE.ordinal() + 1, List.of(energyAlias(ioType))));
        }

        @Override
        public PortDefinition definition() {
            return PortKinds.definition(id, ioType, families(), List.of(BuiltinCapabilityDefinitions.ENERGY_TYPE));
        }
    }

    public record CombinedPortKind(
            String id,
            IOType ioType,
            CombinedPortSize size,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {

        @Override
        public Optional<CombinedPortSize> combinedPortSize() {
            return Optional.of(size);
        }

        @Override
        public List<PortFamilyDescriptor> families() {
            return List.of(
                    new PortFamilyDescriptor(PortFamilyIds.ITEM, ioType, size.itemTier(), List.of(itemAlias(ioType))),
                    new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType, size.fluidTier(), List.of(fluidAlias(ioType))));
        }

        @Override
        public PortDefinition definition() {
            return PortKinds.definition(id, ioType, families(),
                    List.of(BuiltinCapabilityDefinitions.ITEM_TYPE, BuiltinCapabilityDefinitions.FLUID_TYPE));
        }
    }

    public record ExtendedCombinedPortKind(
            String id,
            IOType ioType,
            ExtendedCombinedPortSize size,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {

        @Override
        public Optional<ExtendedCombinedPortSize> extendedCombinedPortSize() {
            return Optional.of(size);
        }

        @Override
        public List<PortFamilyDescriptor> families() {
            return List.of(
                    new PortFamilyDescriptor(PortFamilyIds.ITEM, ioType,
                            size.itemTier(), List.of(itemAlias(ioType))),
                    new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType,
                            size.fluidTier(), List.of(fluidAlias(ioType))));
        }

        @Override
        public PortDefinition definition() {
            return PortKinds.definition(id, ioType, families(),
                    List.of(BuiltinCapabilityDefinitions.ITEM_TYPE, BuiltinCapabilityDefinitions.FLUID_TYPE));
        }
    }

    public record CombinedKind(
            String id,
            IOType ioType,
            List<PortFamilyDescriptor> families,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory,
            PortDefinition definition)
            implements IOPortKind {
        public CombinedKind(String id, IOType ioType, List<PortFamilyDescriptor> families,
                            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory,
                            PortDefinition definition) {
            this.id = id;
            this.ioType = ioType;
            this.entityFactory = entityFactory;
            this.definition = definition;
            if (id == null) throw new IllegalArgumentException("id null");
            if (ioType == null) throw new IllegalArgumentException("ioType null");
            if (families == null || families.isEmpty()) throw new IllegalArgumentException("combined kind families empty");
            if (entityFactory == null) throw new IllegalArgumentException("entityFactory null");
            if (definition == null) throw new IllegalArgumentException("definition null");
            List<PortFamilyDescriptor> copiedFamilies = List.copyOf(families);
            Set<Identifier> familyIds = new HashSet<>();
            for (PortFamilyDescriptor family : copiedFamilies) {
                if (family.ioType() != ioType) {
                    throw new IllegalArgumentException("combined kind families must use the kind IO direction");
                }
                if (!familyIds.add(family.familyId())) {
                    throw new IllegalArgumentException("duplicate combined kind family: " + family.familyId());
                }
                if (family.familyId().equals(PortFamilyIds.ENERGY)) {
                    throw new IllegalArgumentException("combined kind cannot expose the energy family");
                }
            }
            Set<Identifier> builtInFamilies = Set.of(
                    PortFamilyIds.ITEM, PortFamilyIds.FLUID, PortFamilyIds.ENERGY);
            if (familyIds.stream().anyMatch(builtInFamilies::contains)
                    && !familyIds.equals(Set.of(PortFamilyIds.ITEM, PortFamilyIds.FLUID))) {
                throw new IllegalArgumentException("combined kind must expose exactly item and fluid families");
            }
            if (definition.bindings().size() != copiedFamilies.size()) {
                throw new IllegalArgumentException("combined kind family and binding counts must match");
            }
            Set<Identifier> bindingIds = new HashSet<>();
            definition.bindings().forEach(binding -> {
                if (binding.ioType() != ioType) {
                    throw new IllegalArgumentException("combined kind bindings must use the kind IO direction");
                }
                if (!familyIds.contains(binding.type().id()) || !bindingIds.add(binding.type().id())) {
                    throw new IllegalArgumentException("combined kind binding does not match its families");
                }
            });
            if (!bindingIds.equals(familyIds)) {
                throw new IllegalArgumentException("combined kind bindings must match its families");
            }
            this.families = copiedFamilies;
        }
    }

    private static PortDefinition definition(String id, IOType ioType, List<PortFamilyDescriptor> families,
                                             List<CapabilityType> types) {
        return PortDefinition.of(MMCR.id(id), types.stream()
                .map(type -> IOPortKind.binding(type, ioType, families)).toList());
    }

    private static final List<IOPortKind> DEFAULTS = createDefaults();
    private static final List<IOPortKind> REGISTRY = new CopyOnWriteArrayList<>(DEFAULTS);

    static {
        DEFAULTS.forEach(kind -> PortDefinitionRegistry.register(kind.definition()));
    }

    public static final IOPortKind ITEM_INPUT = byId("item_input_bus");
    public static final IOPortKind ITEM_OUTPUT = byId("item_output_bus");
    public static final IOPortKind FLUID_INPUT = byId("fluid_input_hatch");
    public static final IOPortKind FLUID_OUTPUT = byId("fluid_output_hatch");
    public static final IOPortKind ENERGY_INPUT = byId("energy_input_hatch");
    public static final IOPortKind ENERGY_OUTPUT = byId("energy_output_hatch");
    public static final IOPortKind EXTENDED_ITEM_INPUT = byId("extended_item_input_bus_basic");
    public static final IOPortKind EXTENDED_ITEM_OUTPUT = byId("extended_item_output_bus_basic");
    public static final IOPortKind EXTENDED_FLUID_INPUT = byId("extended_fluid_input_hatch_basic");
    public static final IOPortKind EXTENDED_FLUID_OUTPUT = byId("extended_fluid_output_hatch_basic");
    public static final IOPortKind EXTENDED_ENERGY_INPUT = byId("extended_energy_input_hatch_reinforced");
    public static final IOPortKind EXTENDED_ENERGY_OUTPUT = byId("extended_energy_output_hatch_reinforced");
    public static final IOPortKind COMBINED_INPUT = byId("combined_input_basic");
    public static final IOPortKind COMBINED_OUTPUT = byId("combined_output_basic");
    public static final IOPortKind EXTENDED_COMBINED_INPUT = byId("extended_combined_input_advanced");
    public static final IOPortKind EXTENDED_COMBINED_OUTPUT = byId("extended_combined_output_advanced");

    public static void register(IOPortKind kind) {
        if (kind == null) throw new IllegalArgumentException("kind null");
        PortDefinitionRegistry.register(kind.definition());
        REGISTRY.add(kind);
    }

    public static List<IOPortKind> all() { return Collections.unmodifiableList(REGISTRY); }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        REGISTRY.clear();
        REGISTRY.addAll(DEFAULTS);
        PortDefinitionRegistry.clearForTesting();
        DEFAULTS.forEach(kind -> PortDefinitionRegistry.register(kind.definition()));
    }

    private static List<IOPortKind> createDefaults() {
        List<IOPortKind> defaults = new CopyOnWriteArrayList<>();
        for (ItemBusSize size : ItemBusSize.values()) {
            defaults.add(new ItemBusKind(itemId("item_input_bus", size), IOType.INPUT, size, ItemInputBusBlockEntity::new));
        }
        for (ItemBusSize size : ItemBusSize.values()) {
            defaults.add(new ItemBusKind(itemId("item_output_bus", size), IOType.OUTPUT, size, ItemOutputBusBlockEntity::new));
        }
        for (FluidHatchSize size : FluidHatchSize.values()) {
            defaults.add(new FluidHatchKind(fluidId("fluid_input_hatch", size), IOType.INPUT, size, FluidInputHatchBlockEntity::new));
        }
        for (FluidHatchSize size : FluidHatchSize.values()) {
            defaults.add(new FluidHatchKind(fluidId("fluid_output_hatch", size), IOType.OUTPUT, size, FluidOutputHatchBlockEntity::new));
        }
        for (EnergyHatchSize size : EnergyHatchSize.values()) {
            defaults.add(new EnergyHatchKind(energyId("energy_input_hatch", size), IOType.INPUT, size, EnergyInputHatchBlockEntity::new));
        }
        for (EnergyHatchSize size : EnergyHatchSize.values()) {
            defaults.add(new EnergyHatchKind(energyId("energy_output_hatch", size), IOType.OUTPUT, size, EnergyOutputHatchBlockEntity::new));
        }
        for (ExtendedItemBusSize size : ExtendedItemBusSize.values()) {
            defaults.add(new ExtendedItemBusKind(tieredId("extended_item_input_bus", size.id()),
                    IOType.INPUT, size, ExtendedItemBusBlockEntity::new));
        }
        for (ExtendedItemBusSize size : ExtendedItemBusSize.values()) {
            defaults.add(new ExtendedItemBusKind(tieredId("extended_item_output_bus", size.id()),
                    IOType.OUTPUT, size, ExtendedItemBusBlockEntity::new));
        }
        for (ExtendedFluidHatchSize size : ExtendedFluidHatchSize.values()) {
            defaults.add(new ExtendedFluidHatchKind(tieredId("extended_fluid_input_hatch", size.id()),
                    IOType.INPUT, size, ExtendedFluidHatchBlockEntity::new));
        }
        for (ExtendedFluidHatchSize size : ExtendedFluidHatchSize.values()) {
            defaults.add(new ExtendedFluidHatchKind(tieredId("extended_fluid_output_hatch", size.id()),
                    IOType.OUTPUT, size, ExtendedFluidHatchBlockEntity::new));
        }
        for (ExtendedEnergyHatchSize size : ExtendedEnergyHatchSize.values()) {
            defaults.add(new ExtendedEnergyHatchKind(tieredId("extended_energy_input_hatch", size.id()),
                    IOType.INPUT, size, ExtendedEnergyHatchBlockEntity::new));
        }
        for (ExtendedEnergyHatchSize size : ExtendedEnergyHatchSize.values()) {
            defaults.add(new ExtendedEnergyHatchKind(tieredId("extended_energy_output_hatch", size.id()),
                    IOType.OUTPUT, size, ExtendedEnergyHatchBlockEntity::new));
        }
        for (CombinedPortSize size : CombinedPortSize.values()) {
            defaults.add(new CombinedPortKind(tieredId("combined_input", size.id()),
                    IOType.INPUT, size, CombinedPortBlockEntity::new));
        }
        for (CombinedPortSize size : CombinedPortSize.values()) {
            defaults.add(new CombinedPortKind(tieredId("combined_output", size.id()),
                    IOType.OUTPUT, size, CombinedPortBlockEntity::new));
        }
        for (ExtendedCombinedPortSize size : ExtendedCombinedPortSize.values()) {
            defaults.add(new ExtendedCombinedPortKind(tieredId("extended_combined_input", size.id()),
                    IOType.INPUT, size, ExtendedCombinedPortBlockEntity::new));
        }
        for (ExtendedCombinedPortSize size : ExtendedCombinedPortSize.values()) {
            defaults.add(new ExtendedCombinedPortKind(tieredId("extended_combined_output", size.id()),
                    IOType.OUTPUT, size, ExtendedCombinedPortBlockEntity::new));
        }
        return List.copyOf(defaults);
    }

    private static String itemAlias(IOType ioType) {
        return ioType == IOType.INPUT ? "item_input_bus" : "item_output_bus";
    }

    private static String fluidAlias(IOType ioType) {
        return ioType == IOType.INPUT ? "fluid_input_hatch" : "fluid_output_hatch";
    }

    private static String energyAlias(IOType ioType) {
        return ioType == IOType.INPUT ? "energy_input_hatch" : "energy_output_hatch";
    }

    private static String tieredId(String base, String size) {
        return base + "_" + size;
    }

    private static String itemId(String base, ItemBusSize size) {
        return size == ItemBusSize.NORMAL ? base : base + "_" + size.id();
    }

    private static String fluidId(String base, FluidHatchSize size) {
        return size == FluidHatchSize.NORMAL ? base : base + "_" + size.id();
    }

    private static String energyId(String base, EnergyHatchSize size) {
        return size == EnergyHatchSize.NORMAL ? base : base + "_" + size.id();
    }

    private static IOPortKind byId(String id) {
        return REGISTRY.stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing port kind: " + id));
    }

    private PortKinds() {}
}
