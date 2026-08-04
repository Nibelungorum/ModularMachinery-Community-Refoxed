package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PortKinds {

    public record Simple(
            String id,
            IOType ioType,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {}

    public static final IOPortKind ITEM_INPUT =
            new Simple("item_input_bus", IOType.INPUT, ItemInputBusBlockEntity::new);
    public static final IOPortKind ITEM_OUTPUT =
            new Simple("item_output_bus", IOType.OUTPUT, ItemOutputBusBlockEntity::new);
    public static final IOPortKind FLUID_INPUT =
            new Simple("fluid_input_hatch", IOType.INPUT, FluidInputHatchBlockEntity::new);
    public static final IOPortKind FLUID_OUTPUT =
            new Simple("fluid_output_hatch", IOType.OUTPUT, FluidOutputHatchBlockEntity::new);
    public static final IOPortKind ENERGY_INPUT =
            new Simple("energy_input_hatch", IOType.INPUT, EnergyInputHatchBlockEntity::new);
    public static final IOPortKind ENERGY_OUTPUT =
            new Simple("energy_output_hatch", IOType.OUTPUT, EnergyOutputHatchBlockEntity::new);

    private static final List<IOPortKind> REGISTRY = new CopyOnWriteArrayList<>(List.of(
            ITEM_INPUT, ITEM_OUTPUT,
            FLUID_INPUT, FLUID_OUTPUT,
            ENERGY_INPUT, ENERGY_OUTPUT));

    public static void register(IOPortKind kind) { REGISTRY.add(kind); }

    public static List<IOPortKind> all() { return Collections.unmodifiableList(REGISTRY); }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        REGISTRY.clear();
        REGISTRY.addAll(List.of(
                ITEM_INPUT, ITEM_OUTPUT,
                FLUID_INPUT, FLUID_OUTPUT,
                ENERGY_INPUT, ENERGY_OUTPUT));
    }

    private PortKinds() {}
}