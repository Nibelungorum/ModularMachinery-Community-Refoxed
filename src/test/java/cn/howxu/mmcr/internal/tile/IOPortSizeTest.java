package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
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

    @Test
    void extendedItemBusUsesLongResourceSlotsAndRejectsAResourceAfterAllTypesAreOccupied() {
        ExtendedItemBusBlockEntity bus = extendedItemBus("extended_item_input_bus");
        ResourceStorage<ItemResource> storage = bus.itemStorage();
        ItemResource iron = itemResource(Items.IRON_INGOT);
        ItemResource gold = itemResource(Items.GOLD_INGOT);
        ItemResource diamond = itemResource(Items.DIAMOND);

        assertThat(bus.capabilitySnapshot().capabilities()).hasSize(1)
                .first().isInstanceOf(ItemBusCapability.class);
        assertThat(storage.size()).isEqualTo(2);
        assertThat(storage.capacity(0, iron)).isEqualTo(Long.MAX_VALUE);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, iron, Long.MAX_VALUE, transaction)).isEqualTo(Long.MAX_VALUE);
            assertThat(storage.insert(1, gold, Long.MAX_VALUE, transaction)).isEqualTo(Long.MAX_VALUE);
            assertThat(storage.insert(0, diamond, 1L, transaction)).isZero();
            assertThat(storage.insert(1, diamond, 1L, transaction)).isZero();
            transaction.commit();
        }
    }

    @Test
    void extendedFluidHatchUsesLongResourceTanksAndRejectsAResourceAfterAllTypesAreOccupied() {
        ExtendedFluidHatchBlockEntity hatch = extendedFluidHatch("extended_fluid_input_hatch");
        ResourceStorage<FluidResource> storage = hatch.fluidStorage();
        FluidResource water = FluidResource.of(Fluids.WATER);
        FluidResource lava = FluidResource.of(Fluids.LAVA);
        FluidResource honey = FluidResource.of(Fluids.WATER);

        assertThat(hatch.capabilitySnapshot().capabilities()).hasSize(1)
                .first().isInstanceOf(FluidHatchCapability.class);
        assertThat(storage.size()).isEqualTo(2);
        assertThat(storage.capacity(0, water)).isEqualTo(Long.MAX_VALUE);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, water, Long.MAX_VALUE, transaction)).isEqualTo(Long.MAX_VALUE);
            assertThat(storage.insert(1, lava, Long.MAX_VALUE, transaction)).isEqualTo(Long.MAX_VALUE);
            assertThat(storage.insert(0, honey, 1L, transaction)).isZero();
            assertThat(storage.insert(1, honey, 1L, transaction)).isZero();
            transaction.commit();
        }
    }

    @Test
    void extendedEnergyUsesLongStorageWithoutIntegerNarrowing() {
        ExtendedEnergyHatchBlockEntity reinforced = extendedEnergyHatch("extended_energy_input_hatch");
        ExtendedEnergyHatchBlockEntity ultimate = extendedEnergyHatch("extended_energy_input_hatch_ultimate");

        assertThat(reinforced.capabilitySnapshot().capabilities()).hasSize(1)
                .first().isInstanceOf(EnergyHatchCapability.class);
        assertThat(reinforced.getEnergyStorage().capacity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(ultimate.getEnergyStorage().capacity()).isEqualTo(Long.MAX_VALUE);
        assertThat(ultimate.getEnergyStorage().insert((long) Integer.MAX_VALUE + 1L, false))
                .isEqualTo((long) Integer.MAX_VALUE + 1L);
    }

    @Test
    void extendedPortsUseTheHighestExistingFamilyDetectionTier() {
        assertThat(extendedItemBus("extended_item_output_bus").kind().families())
                .singleElement()
                .satisfies(family -> assertThat(family.detectionTier())
                        .isGreaterThanOrEqualTo(ItemBusSize.LUDICROUS.ordinal()));
        assertThat(extendedFluidHatch("extended_fluid_output_hatch").kind().families())
                .singleElement()
                .satisfies(family -> assertThat(family.detectionTier())
                        .isGreaterThanOrEqualTo(FluidHatchSize.VACUUM.ordinal()));
    }

    private static ItemBusBlockEntity itemBus(String id) {
        return (ItemBusBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static FluidHatchBlockEntity fluidHatch(String id) {
        return (FluidHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static ExtendedItemBusBlockEntity extendedItemBus(String id) {
        return (ExtendedItemBusBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static ExtendedFluidHatchBlockEntity extendedFluidHatch(String id) {
        return (ExtendedFluidHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static ExtendedEnergyHatchBlockEntity extendedEnergyHatch(String id) {
        return (ExtendedEnergyHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static ItemResource itemResource(net.minecraft.world.item.Item item) {
        ItemStack stack = item.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return ItemResource.of(stack);
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
        return hatch.fluidStorage();
    }

}
