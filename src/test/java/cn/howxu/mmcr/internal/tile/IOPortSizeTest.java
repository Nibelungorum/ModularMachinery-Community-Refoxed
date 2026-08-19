package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.world.level.material.Fluids;
import static org.assertj.core.api.Assertions.assertThat;

class IOPortSizeTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void itemBusUsesKindSlotCount() {
        ItemBusBlockEntity tiny = itemBus("item_input_bus_tiny");
        ItemBusBlockEntity normal = itemBus("item_input_bus");
        ItemBusBlockEntity ludicrous = itemBus("item_output_bus_ludicrous");

        assertThat(tiny.getItemStackHandler(null).getSlots()).isEqualTo(1);
        assertThat(normal.getItemStackHandler(null).getSlots()).isEqualTo(6);
        assertThat(ludicrous.getItemStackHandler(null).getSlots()).isEqualTo(32);
    }

    @Test
    void itemBusCachesInventoryEmptyState() {
        ItemBusBlockEntity bus = itemBus("item_output_bus");

        assertThat(bus.isInventoryEmpty()).isTrue();

        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        assertThat(bus.isInventoryEmpty()).isFalse();

        bus.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        assertThat(bus.isInventoryEmpty()).isTrue();
    }

    @Test
    void fluidHatchCachesTankEmptyState() {
        FluidHatchBlockEntity hatch = fluidHatch("fluid_output_hatch");

        assertThat(hatch.isTankEmpty()).isTrue();

        tank(hatch).forceInsert(new FluidStack(Fluids.WATER, 100), false);
        assertThat(hatch.isTankEmpty()).isFalse();

        tank(hatch).forceExtract(100, false);
        assertThat(hatch.isTankEmpty()).isTrue();
    }

    private static ItemBusBlockEntity itemBus(String id) {
        return (ItemBusBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static FluidHatchBlockEntity fluidHatch(String id) {
        return (FluidHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static BlockState state(String id) {
        kind(id);
        return ModBlocks.BLOCKS.get(id).get().defaultBlockState();
    }

    private static IOPortKind kind(String id) {
        return PortKinds.all().stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static LongFluidStorage tank(FluidHatchBlockEntity hatch) {
        return hatch.getMutableFluidStorage();
    }

}
