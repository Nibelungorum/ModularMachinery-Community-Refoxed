package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MMCRPortKinds {

    public record Simple(
            String id,
            BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
            implements IOPortKind {}

    public static final IOPortKind ITEM   = new Simple("item",   ItemBusBlockEntity::new);
    public static final IOPortKind FLUID  = new Simple("fluid",  FluidHatchBlockEntity::new);
    public static final IOPortKind ENERGY = new Simple("energy", EnergyHatchBlockEntity::new);

    private static final List<IOPortKind> REGISTRY = new CopyOnWriteArrayList<>(
            List.of(ITEM, FLUID, ENERGY));

    public static void register(IOPortKind kind) { REGISTRY.add(kind); }

    public static List<IOPortKind> all() { return Collections.unmodifiableList(REGISTRY); }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        REGISTRY.clear();
        REGISTRY.addAll(List.of(ITEM, FLUID, ENERGY));
    }

    private MMCRPortKinds() {}
}