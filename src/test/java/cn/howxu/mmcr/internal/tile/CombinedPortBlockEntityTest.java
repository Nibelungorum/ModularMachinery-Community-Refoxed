package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.CombinedPortSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies ordinary and extended combined port storage hosts.
 *
 * @author howxu <dev@howxu.cn>
 */
class CombinedPortBlockEntityTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void ordinaryCombinedHostExposesItemAndFluidCapabilities() {
        CombinedPortBlockEntity port = combined("combined_input_reinforced");

        assertThat(port.capabilitySnapshot().capabilities()).hasSize(2)
                .extracting(MachineCapability::type)
                .containsExactly(
                        new CapabilityType(PortFamilyIds.ITEM),
                        new CapabilityType(PortFamilyIds.FLUID));
        assertThat(port.itemStorage()).isNotSameAs(port.fluidStorage());
        assertThat(port.itemStorage().size()).isEqualTo(12);
        assertThat(port.fluidStorage().size()).isEqualTo(2);
        assertThat(port.kind().ioType()).isEqualTo(IOType.INPUT);
    }

    @Test
    void noArgumentAutoIoToggleReadsAndWritesTheSamePrimaryCapabilityProfile() {
        CombinedPortBlockEntity port = combined("combined_input_basic");
        CapabilityType primary = port.capabilitySnapshot().capabilities().getFirst().type();

        port.toggleAutoIOEnabled();
        assertThat(port.autoIOConfig().enabled()).isTrue();
        assertThat(port.autoIOConfig(primary).enabled()).isTrue();

        port.toggleAutoIOEnabled();
        assertThat(port.autoIOConfig().enabled()).isFalse();
        assertThat(port.autoIOConfig(primary).enabled()).isFalse();

        port.toggleAutoIOSide(Direction.NORTH);
        assertThat(port.autoIOConfig().isSideEnabled(Direction.NORTH)).isFalse();
        assertThat(port.autoIOConfig(primary).isSideEnabled(Direction.NORTH)).isFalse();
    }

    @Test
    void ordinaryCombinedItemAndFluidStorageAreIndependent() {
        CombinedPortBlockEntity port = combined("combined_input_reinforced");
        var items = port.itemStorage();
        var fluids = port.fluidStorage();
        ItemStack iron = itemStack(Items.IRON_INGOT);
        FluidStack water = new FluidStack(Fluids.WATER, 1);

        assertThat(items.forceInsert(0, iron, 64L, false)).isEqualTo(64L);
        assertThat(fluids.forceInsert(0, water, 256_000L, false)).isEqualTo(256_000L);

        assertThat(items.amount(0)).isEqualTo(64L);
        assertThat(fluids.amount(0)).isEqualTo(256_000L);
        assertThat(items.resource(0)).isEqualTo(iron);
        assertThat(fluids.resource(0)).isEqualTo(water);
    }

    @Test
    void extendedCombinedUsesExpandedItemAndFluidTypeCounts() {
        assertExtendedCombined("extended_combined_input_advanced", 6, 2);
        assertExtendedCombined("extended_combined_input_reinforced", 12, 4);
        assertExtendedCombined("extended_combined_input_ultimate", 18, 6);
    }

    @Test
    void combinedCapabilitySnapshotsKeepItemBeforeFluidForBothDirections() {
        assertCapabilityOrder("combined_input_basic");
        assertCapabilityOrder("combined_output_basic");
        assertCapabilityOrder("extended_combined_input_advanced");
        assertCapabilityOrder("extended_combined_output_advanced");
    }

    @Test
    void combinedDefinitionsKeepItemBeforeFluidAndBindTheirPortDirection() {
        assertBindingOrder("combined_input_basic", IOType.INPUT);
        assertBindingOrder("combined_output_basic", IOType.OUTPUT);
        assertBindingOrder("extended_combined_input_advanced", IOType.INPUT);
        assertBindingOrder("extended_combined_output_advanced", IOType.OUTPUT);
    }

    @Test
    void combinedFamiliesUseDirectionSpecificAliases() {
        assertThat(combined("combined_input_basic").kind().families())
                .extracting(PortFamilyDescriptor::countAliases)
                .containsExactlyInAnyOrder(List.of("item_input_bus"), List.of("fluid_input_hatch"));
        assertThat(combined("combined_output_basic").kind().families())
                .extracting(PortFamilyDescriptor::countAliases)
                .containsExactlyInAnyOrder(List.of("item_output_bus"), List.of("fluid_output_hatch"));
    }

    @Test
    void ordinaryCombinedFluidFamiliesUseTheFourHighestFluidDetectionTiers() {
        List<FluidHatchSize> expected = List.of(
                FluidHatchSize.BIG, FluidHatchSize.HUGE, FluidHatchSize.LUDICROUS, FluidHatchSize.VACUUM);

        assertThat(List.of(CombinedPortSize.values()))
                .extracting(size -> port("combined_input_" + size.id()).kind().families().stream()
                        .filter(family -> family.familyId().equals(PortFamilyIds.FLUID))
                        .findFirst().orElseThrow().detectionTier())
                .containsExactlyElementsOf(expected.stream().map(FluidHatchSize::ordinal).toList());
    }

    @Test
    void extendedCombinedExposesBothHighestLevelFamilyDescriptors() {
        assertThat(port("extended_combined_output_advanced").kind().families())
                .extracting(PortFamilyDescriptor::familyId)
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(port("extended_combined_output_advanced").kind().families())
                .allSatisfy(family -> assertThat(family.detectionTier()).isGreaterThan(0));
    }

    @Test
    void extendedCombinedFluidStorageSavesEmptySlotsAndRoundTripsPopulatedSlots() {
        HolderLookup.Provider lookup = HolderLookup.Provider.create(Stream.empty());
        ExtendedCombinedPortBlockEntity empty = extendedCombined("extended_combined_input_ultimate");
        CompoundTag emptyOutput = new CompoundTag();

        empty.saveAdditional(emptyOutput, lookup);

        ExtendedCombinedPortBlockEntity source = extendedCombined("extended_combined_input_ultimate");
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        assertThat(source.fluidStorage().forceInsert(1, water, 1_234L, false)).isEqualTo(1_234L);
        CompoundTag output = new CompoundTag();
        source.saveAdditional(output, lookup);

        ExtendedCombinedPortBlockEntity restored = extendedCombined("extended_combined_input_ultimate");
        restored.loadAdditional(output, lookup);

        assertThat(restored.fluidStorage().resource(0)).isEmpty();
        assertThat(restored.fluidStorage().resource(1)).isEqualTo(water);
        assertThat(restored.fluidStorage().amount(1)).isEqualTo(1_234L);
        assertThat(restored.fluidStorage().resource(2)).isEmpty();
    }

    private static void assertExtendedCombined(String id, int itemTypes, int fluidTypes) {
        IOPortBlockEntity port = port(id);
        var items = port.itemStorage();
        var fluids = port.fluidStorage();
        List<ItemStack> resources = List.of(
                itemStack(Items.IRON_INGOT), itemStack(Items.GOLD_INGOT), itemStack(Items.DIAMOND),
                itemStack(Items.EMERALD), itemStack(Items.COPPER_INGOT), itemStack(Items.REDSTONE),
                itemStack(Items.LAPIS_LAZULI), itemStack(Items.QUARTZ), itemStack(Items.COAL),
                itemStack(Items.NETHERITE_INGOT), itemStack(Items.RAW_IRON), itemStack(Items.RAW_GOLD),
                itemStack(Items.RAW_COPPER), itemStack(Items.COBBLESTONE), itemStack(Items.STONE),
                itemStack(Items.DIRT), itemStack(Items.SAND), itemStack(Items.GRAVEL));
        ItemStack newItem = itemStack(Items.NETHER_STAR);
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        FluidStack newFluid = new FluidStack(Fluids.LAVA, 1);

        assertThat(items.size()).isEqualTo(itemTypes);
        assertThat(fluids.size()).isEqualTo(fluidTypes);
        assertThat(items.capacity(0)).isEqualTo(Long.MAX_VALUE);
        assertThat(fluids.capacity(0)).isEqualTo(Long.MAX_VALUE);
        for (int slot = 0; slot < itemTypes; slot++) {
            assertThat(items.forceInsert(slot, resources.get(slot), Long.MAX_VALUE, false)).isEqualTo(Long.MAX_VALUE);
        }
        for (int slot = 0; slot < fluidTypes; slot++) {
            assertThat(fluids.forceInsert(slot, water, Long.MAX_VALUE, false)).isEqualTo(Long.MAX_VALUE);
            assertThat(fluids.forceInsert(slot, newFluid, 1L, false)).isZero();
        }
        assertThat(items.forceInsert(0, newItem, 1L, false)).isZero();
    }

    private static void assertCapabilityOrder(String id) {
        assertThat(port(id).capabilitySnapshot().capabilities())
                .extracting(MachineCapability::type)
                .containsExactly(new CapabilityType(PortFamilyIds.ITEM), new CapabilityType(PortFamilyIds.FLUID));
    }

    private static void assertBindingOrder(String id, IOType ioType) {
        List<CapabilityBinding> bindings = port(id).kind().bindings();
        assertThat(bindings)
                .extracting(CapabilityBinding::type)
                .containsExactly(new CapabilityType(PortFamilyIds.ITEM), new CapabilityType(PortFamilyIds.FLUID));
        assertThat(bindings)
                .extracting(binding -> binding.directions().supports(ioType))
                .containsExactly(true, true);
    }

    private static CombinedPortBlockEntity combined(String id) {
        return (CombinedPortBlockEntity) port(id);
    }

    private static ExtendedCombinedPortBlockEntity extendedCombined(String id) {
        return (ExtendedCombinedPortBlockEntity) port(id);
    }

    private static IOPortBlockEntity port(String id) {
        PortKinds.all().stream().filter(kind -> kind.id().equals(id)).findFirst().orElseThrow();
        BlockState state = ModBlocks.BLOCKS.get(id).get().defaultBlockState();
        return (IOPortBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state);
    }

    private static ItemStack itemStack(Item item) {
        ItemStack stack = item.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }
}
