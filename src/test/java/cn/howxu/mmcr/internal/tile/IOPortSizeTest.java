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
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
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

import java.util.List;

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
    void extendedItemBusUsesExpandedLongResourceSlotsAndRejectsAResourceAfterAllTypesAreOccupied() {
        List<ItemResource> resources = itemResources();
        for (ExtendedItemBusSize size : ExtendedItemBusSize.values()) {
            ExtendedItemBusBlockEntity bus = extendedItemBus("extended_item_input_bus_" + size.id());
            ResourceStorage<ItemResource> storage = bus.itemStorage();

            assertThat(bus.capabilitySnapshot().capabilities()).hasSize(1)
                    .first().isInstanceOf(ItemBusCapability.class);
            assertThat(storage.size()).isEqualTo(size.slots());
            assertThat(storage.capacity(0, resources.getFirst())).isEqualTo(Long.MAX_VALUE);
            try (Transaction transaction = Transaction.openRoot()) {
                for (int slot = 0; slot < size.slots(); slot++) {
                    assertThat(storage.insert(slot, resources.get(slot), Long.MAX_VALUE, transaction))
                            .isEqualTo(Long.MAX_VALUE);
                }
                ItemResource overflow = itemResource(Items.NETHER_STAR);
                for (int slot = 0; slot < size.slots(); slot++) {
                    assertThat(storage.insert(slot, overflow, 1L, transaction)).isZero();
                }
                transaction.commit();
            }
        }
    }

    @Test
    void extendedFluidHatchUsesExpandedLongResourceTanksAndRejectsAResourceAfterAllTypesAreOccupied() {
        for (ExtendedFluidHatchSize size : ExtendedFluidHatchSize.values()) {
            ExtendedFluidHatchBlockEntity hatch = extendedFluidHatch("extended_fluid_input_hatch_" + size.id());
            ResourceStorage<FluidResource> storage = hatch.fluidStorage();
            FluidResource water = FluidResource.of(Fluids.WATER);
            FluidResource lava = FluidResource.of(Fluids.LAVA);

            assertThat(hatch.capabilitySnapshot().capabilities()).hasSize(1)
                    .first().isInstanceOf(FluidHatchCapability.class);
            assertThat(storage.size()).isEqualTo(size.slots());
            assertThat(storage.capacity(0, water)).isEqualTo(Long.MAX_VALUE);
            try (Transaction transaction = Transaction.openRoot()) {
                for (int slot = 0; slot < size.slots(); slot++) {
                    assertThat(storage.insert(slot, water, Long.MAX_VALUE, transaction))
                            .isEqualTo(Long.MAX_VALUE);
                }
                for (int slot = 0; slot < size.slots(); slot++) {
                    assertThat(storage.insert(slot, lava, 1L, transaction)).isZero();
                }
                transaction.commit();
            }
        }
    }

    @Test
    void extendedEnergyUsesLongStorageWithoutIntegerNarrowing() {
        ExtendedEnergyHatchBlockEntity reinforced = extendedEnergyHatch("extended_energy_input_hatch_reinforced");
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
        assertThat(extendedItemBus("extended_item_output_bus_basic").kind().families())
                .singleElement()
                .satisfies(family -> assertThat(family.detectionTier())
                        .isGreaterThanOrEqualTo(ItemBusSize.LUDICROUS.ordinal()));
        assertThat(extendedFluidHatch("extended_fluid_output_hatch_basic").kind().families())
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

    private static List<ItemResource> itemResources() {
        return List.of(
                itemResource(Items.IRON_INGOT), itemResource(Items.GOLD_INGOT), itemResource(Items.DIAMOND),
                itemResource(Items.COPPER_INGOT), itemResource(Items.COAL), itemResource(Items.REDSTONE),
                itemResource(Items.LAPIS_LAZULI), itemResource(Items.QUARTZ), itemResource(Items.AMETHYST_SHARD),
                itemResource(Items.EMERALD), itemResource(Items.NETHERITE_INGOT), itemResource(Items.RAW_IRON),
                itemResource(Items.RAW_GOLD), itemResource(Items.RAW_COPPER), itemResource(Items.COBBLESTONE),
                itemResource(Items.STONE), itemResource(Items.DIRT), itemResource(Items.SAND),
                itemResource(Items.GRAVEL), itemResource(Items.OAK_LOG), itemResource(Items.SPRUCE_LOG),
                itemResource(Items.BIRCH_LOG), itemResource(Items.JUNGLE_LOG), itemResource(Items.ACACIA_LOG),
                itemResource(Items.DARK_OAK_LOG), itemResource(Items.CRIMSON_STEM), itemResource(Items.WARPED_STEM),
                itemResource(Items.GLASS), itemResource(Items.BRICK), itemResource(Items.BOOK),
                itemResource(Items.PAPER), itemResource(Items.WHEAT));
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
