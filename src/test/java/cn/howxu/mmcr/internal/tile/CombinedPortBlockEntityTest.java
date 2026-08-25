package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
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

    @Test
    void ordinaryCombinedHostExposesItemAndFluidCapabilities() {
        CombinedPortBlockEntity port = combined("combined_input_reinforced");

        assertThat(port.capabilitySnapshot().capabilities()).hasSize(2)
                .extracting(MachineCapability::type)
                .containsExactlyInAnyOrder(
                        new CapabilityType(PortFamilyIds.ITEM),
                        new CapabilityType(PortFamilyIds.FLUID));
        assertThat(port.itemStorage()).isNotSameAs(port.fluidStorage());
        assertThat(port.itemStorage().size()).isEqualTo(12);
        assertThat(port.fluidStorage().size()).isEqualTo(2);
        assertThat(port.kind().ioType()).isEqualTo(IOType.INPUT);
    }

    @Test
    void ordinaryCombinedItemAndFluidStorageAreIndependent() {
        CombinedPortBlockEntity port = combined("combined_input_reinforced");
        ResourceStorage<ItemResource> items = port.itemStorage();
        ResourceStorage<FluidResource> fluids = port.fluidStorage();
        ItemResource iron = itemResource(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(items.insert(0, iron, 64L, transaction)).isEqualTo(64L);
            assertThat(fluids.insert(0, water, 256_000L, transaction)).isEqualTo(256_000L);
            transaction.commit();
        }

        assertThat(items.amount(0)).isEqualTo(64L);
        assertThat(fluids.amount(0)).isEqualTo(256_000L);
        assertThat(items.resource(0)).isEqualTo(iron);
        assertThat(fluids.resource(0)).isEqualTo(water);
    }

    @Test
    void extendedCombinedUsesThreeSixNineItemTypesAndOneTwoThreeFluidTypes() {
        assertExtendedCombined("extended_combined_input_advanced", 3, 1);
        assertExtendedCombined("extended_combined_input_reinforced", 6, 2);
        assertExtendedCombined("extended_combined_input_ultimate", 9, 3);
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
        TagValueOutput emptyOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);

        empty.saveAdditional(emptyOutput);

        ExtendedCombinedPortBlockEntity source = extendedCombined("extended_combined_input_ultimate");
        FluidResource water = FluidResource.of(Fluids.WATER);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(source.fluidStorage().insert(1, water, 1_234L, transaction)).isEqualTo(1_234L);
            transaction.commit();
        }
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
        source.saveAdditional(output);

        ExtendedCombinedPortBlockEntity restored = extendedCombined("extended_combined_input_ultimate");
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, lookup, output.buildResult()));

        assertThat(restored.fluidStorage().resource(0)).isNull();
        assertThat(restored.fluidStorage().resource(1)).isEqualTo(water);
        assertThat(restored.fluidStorage().amount(1)).isEqualTo(1_234L);
        assertThat(restored.fluidStorage().resource(2)).isNull();
    }

    private static void assertExtendedCombined(String id, int itemTypes, int fluidTypes) {
        IOPortBlockEntity port = port(id);
        ResourceStorage<ItemResource> items = port.itemStorage();
        ResourceStorage<FluidResource> fluids = port.fluidStorage();
        List<ItemResource> resources = List.of(
                itemResource(Items.IRON_INGOT), itemResource(Items.GOLD_INGOT), itemResource(Items.DIAMOND),
                itemResource(Items.EMERALD), itemResource(Items.COPPER_INGOT), itemResource(Items.REDSTONE),
                itemResource(Items.LAPIS_LAZULI), itemResource(Items.QUARTZ), itemResource(Items.COAL));
        ItemResource newItem = itemResource(Items.NETHER_STAR);
        FluidResource newFluid = FluidResource.of(Fluids.LAVA);

        assertThat(items.size()).isEqualTo(itemTypes);
        assertThat(fluids.size()).isEqualTo(fluidTypes);
        assertThat(items.capacity(0, resources.getFirst())).isEqualTo(Long.MAX_VALUE);
        assertThat(fluids.capacity(0, FluidResource.of(Fluids.WATER))).isEqualTo(Long.MAX_VALUE);
        try (Transaction transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < itemTypes; slot++) {
                assertThat(items.insert(slot, resources.get(slot), Long.MAX_VALUE, transaction))
                        .isEqualTo(Long.MAX_VALUE);
            }
            for (int slot = 0; slot < fluidTypes; slot++) {
                assertThat(fluids.insert(slot, FluidResource.of(Fluids.WATER), Long.MAX_VALUE, transaction))
                        .isEqualTo(Long.MAX_VALUE);
                assertThat(fluids.insert(slot, newFluid, 1L, transaction)).isZero();
            }
            assertThat(items.insert(0, newItem, 1L, transaction)).isZero();
            transaction.commit();
        }
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

    private static ItemResource itemResource(net.minecraft.world.item.Item item) {
        ItemStack stack = item.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return ItemResource.of(stack);
    }
}
