package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
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
            implements IOPortKind {}

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
    }

    private static final List<IOPortKind> DEFAULTS = createDefaults();
    private static final List<IOPortKind> REGISTRY = new CopyOnWriteArrayList<>(DEFAULTS);

    public static final IOPortKind ITEM_INPUT = byId("item_input_bus");
    public static final IOPortKind ITEM_OUTPUT = byId("item_output_bus");
    public static final IOPortKind FLUID_INPUT = byId("fluid_input_hatch");
    public static final IOPortKind FLUID_OUTPUT = byId("fluid_output_hatch");
    public static final IOPortKind ENERGY_INPUT = byId("energy_input_hatch");
    public static final IOPortKind ENERGY_OUTPUT = byId("energy_output_hatch");

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
        return List.copyOf(defaults);
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
