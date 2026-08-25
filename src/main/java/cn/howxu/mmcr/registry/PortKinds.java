package cn.howxu.mmcr.registry;

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
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.capability.CapabilityFactories.CapabilityFactory;
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
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PortKinds {

    public record Simple(
            String id,
            IOType ioType,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {
        @Override
        public List<CapabilityFactory> capabilityFactories() {
            return List.of();
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
        public List<CapabilityFactory> capabilityFactories() {
            return List.of(CapabilityFactories.ITEM_BUS);
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
        public List<CapabilityFactory> capabilityFactories() {
            return List.of(CapabilityFactories.FLUID_HATCH);
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
        public List<CapabilityFactory> capabilityFactories() {
            return List.of(CapabilityFactories.ENERGY_HATCH);
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
        public List<CapabilityFactory> capabilityFactories() {
            return List.of(CapabilityFactories.ITEM_BUS);
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
        public List<CapabilityFactory> capabilityFactories() {
            return List.of(CapabilityFactories.FLUID_HATCH);
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
        public List<CapabilityFactory> capabilityFactories() {
            return List.of(CapabilityFactories.ENERGY_HATCH);
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
                    new PortFamilyDescriptor(PortFamilyIds.ITEM, ioType, itemTier(size), List.of(itemAlias(ioType))),
                    new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType, FluidHatchSize.VACUUM.ordinal(), List.of(fluidAlias(ioType))));
        }

        @Override
        public List<CapabilityFactory> capabilityFactories() {
            return List.of(CapabilityFactories.ITEM_BUS, CapabilityFactories.FLUID_HATCH);
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
                            ItemBusSize.LUDICROUS.ordinal() + 1, List.of(itemAlias(ioType))),
                    new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType,
                            FluidHatchSize.VACUUM.ordinal() + 1, List.of(fluidAlias(ioType))));
        }

        @Override
        public List<CapabilityFactory> capabilityFactories() {
            return List.of(CapabilityFactories.ITEM_BUS, CapabilityFactories.FLUID_HATCH);
        }
    }

    public record CombinedKind(
            String id,
            IOType ioType,
            List<PortFamilyDescriptor> families,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory,
            List<CapabilityFactory> capabilityFactories)
            implements IOPortKind {
        public CombinedKind {
            if (ioType == null) throw new IllegalArgumentException("ioType null");
            if (families == null || families.size() != 2) {
                throw new IllegalArgumentException("combined kind must have exactly two families");
            }
            families = List.copyOf(families);
            boolean item = false;
            boolean fluid = false;
            for (PortFamilyDescriptor family : families) {
                if (family == null || family.ioType() != ioType) {
                    throw new IllegalArgumentException("combined family direction mismatch");
                }
                if (family.familyId().equals(PortFamilyIds.ITEM)) {
                    if (item) throw new IllegalArgumentException("duplicate item family");
                    item = true;
                } else if (family.familyId().equals(PortFamilyIds.FLUID)) {
                    if (fluid) throw new IllegalArgumentException("duplicate fluid family");
                    fluid = true;
                } else {
                    throw new IllegalArgumentException("combined kind family must be item or fluid");
                }
            }
            if (!item || !fluid) throw new IllegalArgumentException("combined kind must include item and fluid families");
            if (capabilityFactories == null || capabilityFactories.size() != 2
                    || !capabilityFactories.contains(CapabilityFactories.ITEM_BUS)
                    || !capabilityFactories.contains(CapabilityFactories.FLUID_HATCH)) {
                throw new IllegalArgumentException("combined kind must expose item and fluid capabilities only");
            }
            capabilityFactories = List.copyOf(capabilityFactories);
        }
    }

    private static final List<IOPortKind> DEFAULTS = createDefaults();
    private static final List<IOPortKind> REGISTRY = new CopyOnWriteArrayList<>(DEFAULTS);

    public static final IOPortKind ITEM_INPUT = byId("item_input_bus");
    public static final IOPortKind ITEM_OUTPUT = byId("item_output_bus");
    public static final IOPortKind FLUID_INPUT = byId("fluid_input_hatch");
    public static final IOPortKind FLUID_OUTPUT = byId("fluid_output_hatch");
    public static final IOPortKind ENERGY_INPUT = byId("energy_input_hatch");
    public static final IOPortKind ENERGY_OUTPUT = byId("energy_output_hatch");
    public static final IOPortKind EXTENDED_ITEM_INPUT = byId("extended_item_input_bus");
    public static final IOPortKind EXTENDED_ITEM_OUTPUT = byId("extended_item_output_bus");
    public static final IOPortKind EXTENDED_FLUID_INPUT = byId("extended_fluid_input_hatch");
    public static final IOPortKind EXTENDED_FLUID_OUTPUT = byId("extended_fluid_output_hatch");
    public static final IOPortKind EXTENDED_ENERGY_INPUT = byId("extended_energy_input_hatch");
    public static final IOPortKind EXTENDED_ENERGY_OUTPUT = byId("extended_energy_output_hatch");
    public static final IOPortKind COMBINED_INPUT = byId("combined_input_port");
    public static final IOPortKind COMBINED_OUTPUT = byId("combined_output_port");
    public static final IOPortKind EXTENDED_COMBINED_INPUT = byId("extended_combined_input_port");
    public static final IOPortKind EXTENDED_COMBINED_OUTPUT = byId("extended_combined_output_port");

    public static void register(IOPortKind kind) { REGISTRY.add(kind); }

    public static List<IOPortKind> all() { return Collections.unmodifiableList(REGISTRY); }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        REGISTRY.clear();
        REGISTRY.addAll(DEFAULTS);
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
            defaults.add(new ExtendedItemBusKind(extendedId("extended_item_input_bus", size.id(), ExtendedItemBusSize.BASIC.id()),
                    IOType.INPUT, size, ExtendedItemBusBlockEntity::new));
        }
        for (ExtendedItemBusSize size : ExtendedItemBusSize.values()) {
            defaults.add(new ExtendedItemBusKind(extendedId("extended_item_output_bus", size.id(), ExtendedItemBusSize.BASIC.id()),
                    IOType.OUTPUT, size, ExtendedItemBusBlockEntity::new));
        }
        for (ExtendedFluidHatchSize size : ExtendedFluidHatchSize.values()) {
            defaults.add(new ExtendedFluidHatchKind(extendedId("extended_fluid_input_hatch", size.id(), ExtendedFluidHatchSize.BASIC.id()),
                    IOType.INPUT, size, ExtendedFluidHatchBlockEntity::new));
        }
        for (ExtendedFluidHatchSize size : ExtendedFluidHatchSize.values()) {
            defaults.add(new ExtendedFluidHatchKind(extendedId("extended_fluid_output_hatch", size.id(), ExtendedFluidHatchSize.BASIC.id()),
                    IOType.OUTPUT, size, ExtendedFluidHatchBlockEntity::new));
        }
        for (ExtendedEnergyHatchSize size : ExtendedEnergyHatchSize.values()) {
            defaults.add(new ExtendedEnergyHatchKind(extendedId("extended_energy_input_hatch", size.id(), ExtendedEnergyHatchSize.REINFORCED.id()),
                    IOType.INPUT, size, ExtendedEnergyHatchBlockEntity::new));
        }
        for (ExtendedEnergyHatchSize size : ExtendedEnergyHatchSize.values()) {
            defaults.add(new ExtendedEnergyHatchKind(extendedId("extended_energy_output_hatch", size.id(), ExtendedEnergyHatchSize.REINFORCED.id()),
                    IOType.OUTPUT, size, ExtendedEnergyHatchBlockEntity::new));
        }
        for (CombinedPortSize size : CombinedPortSize.values()) {
            defaults.add(new CombinedPortKind(extendedId("combined_input_port", size.id(), CombinedPortSize.BASIC.id()),
                    IOType.INPUT, size, CombinedPortBlockEntity::new));
        }
        for (CombinedPortSize size : CombinedPortSize.values()) {
            defaults.add(new CombinedPortKind(extendedId("combined_output_port", size.id(), CombinedPortSize.BASIC.id()),
                    IOType.OUTPUT, size, CombinedPortBlockEntity::new));
        }
        for (ExtendedCombinedPortSize size : ExtendedCombinedPortSize.values()) {
            defaults.add(new ExtendedCombinedPortKind(extendedId("extended_combined_input_port", size.id(), ExtendedCombinedPortSize.ADVANCED.id()),
                    IOType.INPUT, size, ExtendedCombinedPortBlockEntity::new));
        }
        for (ExtendedCombinedPortSize size : ExtendedCombinedPortSize.values()) {
            defaults.add(new ExtendedCombinedPortKind(extendedId("extended_combined_output_port", size.id(), ExtendedCombinedPortSize.ADVANCED.id()),
                    IOType.OUTPUT, size, ExtendedCombinedPortBlockEntity::new));
        }
        return List.copyOf(defaults);
    }

    private static int itemTier(CombinedPortSize size) {
        return switch (size) {
            case BASIC -> ItemBusSize.NORMAL.ordinal();
            case ADVANCED -> ItemBusSize.REINFORCED.ordinal();
            case REINFORCED -> ItemBusSize.BIG.ordinal();
            case ULTIMATE -> ItemBusSize.HUGE.ordinal();
        };
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

    private static String extendedId(String base, String size, String defaultSize) {
        return size.equals(defaultSize) ? base : base + "_" + size;
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
