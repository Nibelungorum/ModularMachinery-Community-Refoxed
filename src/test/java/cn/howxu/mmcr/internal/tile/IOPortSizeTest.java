package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

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

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void itemBusUsesKindSlotCount() {
        ItemBusBlockEntity tiny = itemBus("item_input_bus_tiny");
        ItemBusBlockEntity normal = itemBus("item_input_bus");
        ItemBusBlockEntity ludicrous = itemBus("item_output_bus_ludicrous");

        assertThat(tiny.itemStorage().size()).isEqualTo(1);
        assertThat(normal.itemStorage().size()).isEqualTo(6);
        assertThat(ludicrous.itemStorage().size()).isEqualTo(32);
    }

    @Test
    void itemBusCachesInventoryEmptyState() {
        ItemBusBlockEntity bus = itemBus("item_output_bus");

        assertThat(isStorageEmpty(bus.itemStorage())).isTrue();

        bus.itemStorage().forceInsert(0, new ItemStack(Items.IRON_INGOT), 1L, false);
        assertThat(isStorageEmpty(bus.itemStorage())).isFalse();

        bus.itemStorage().forceExtract(0, bus.itemStorage().amount(0), false);

        assertThat(isStorageEmpty(bus.itemStorage())).isTrue();
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
        List<ItemStack> resources = itemResources();
        for (ExtendedItemBusSize size : ExtendedItemBusSize.values()) {
            ExtendedItemBusBlockEntity bus = extendedItemBus("extended_item_input_bus_" + size.id());
            LongItemStorage storage = bus.itemStorage();

            assertThat(bus.capabilitySnapshot().capabilities()).hasSize(1)
                    .first().isInstanceOf(ItemBusCapability.class);
            assertThat(storage.size()).isEqualTo(size.slots());
            assertThat(storage.capacity(0)).isEqualTo(Long.MAX_VALUE);
            for (int slot = 0; slot < size.slots(); slot++) {
                assertThat(storage.forceInsert(slot, resources.get(slot), Long.MAX_VALUE, false))
                        .isEqualTo(Long.MAX_VALUE);
            }
            ItemStack overflow = itemStack(Items.NETHER_STAR);
            for (int slot = 0; slot < size.slots(); slot++) {
                assertThat(storage.forceInsert(slot, overflow, 1L, false)).isZero();
            }
        }
    }

    @Test
    void extendedFluidHatchUsesExpandedLongResourceTanksAndRejectsAResourceAfterAllTypesAreOccupied() {
        for (ExtendedFluidHatchSize size : ExtendedFluidHatchSize.values()) {
            ExtendedFluidHatchBlockEntity hatch = extendedFluidHatch("extended_fluid_input_hatch_" + size.id());
            LongFluidStorage storage = hatch.fluidStorage();
            FluidStack water = new FluidStack(Fluids.WATER, 1);
            FluidStack lava = new FluidStack(Fluids.LAVA, 1);

            assertThat(hatch.capabilitySnapshot().capabilities()).hasSize(1)
                    .first().isInstanceOf(FluidHatchCapability.class);
            assertThat(storage.size()).isEqualTo(size.slots());
            assertThat(storage.capacity(0)).isEqualTo(Long.MAX_VALUE);
            for (int slot = 0; slot < size.slots(); slot++) {
                assertThat(storage.forceInsert(slot, water, Long.MAX_VALUE, false)).isEqualTo(Long.MAX_VALUE);
            }
            for (int slot = 0; slot < size.slots(); slot++) {
                assertThat(storage.forceInsert(slot, lava, 1L, false)).isZero();
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

    private static ItemStack itemStack(Item item) {
        ItemStack stack = item.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static List<ItemStack> itemResources() {
        return List.of(
                itemStack(Items.IRON_INGOT), itemStack(Items.GOLD_INGOT), itemStack(Items.DIAMOND),
                itemStack(Items.COPPER_INGOT), itemStack(Items.COAL), itemStack(Items.REDSTONE),
                itemStack(Items.LAPIS_LAZULI), itemStack(Items.QUARTZ), itemStack(Items.AMETHYST_SHARD),
                itemStack(Items.EMERALD), itemStack(Items.NETHERITE_INGOT), itemStack(Items.RAW_IRON),
                itemStack(Items.RAW_GOLD), itemStack(Items.RAW_COPPER), itemStack(Items.COBBLESTONE),
                itemStack(Items.STONE), itemStack(Items.DIRT), itemStack(Items.SAND),
                itemStack(Items.GRAVEL), itemStack(Items.OAK_LOG), itemStack(Items.SPRUCE_LOG),
                itemStack(Items.BIRCH_LOG), itemStack(Items.JUNGLE_LOG), itemStack(Items.ACACIA_LOG),
                itemStack(Items.DARK_OAK_LOG), itemStack(Items.CRIMSON_STEM), itemStack(Items.WARPED_STEM),
                itemStack(Items.GLASS), itemStack(Items.BRICK), itemStack(Items.BOOK),
                itemStack(Items.PAPER), itemStack(Items.WHEAT));
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

    private static boolean isStorageEmpty(LongItemStorage storage) {
        return IntStream.range(0, storage.size())
                .allMatch(slot -> storage.amount(slot) == 0L);
    }

}
