package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockAssemblyServiceTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void requirementsAreAggregatedByItemAndComponents() {
        List<MultiblockAssemblyService.Placement> placements = List.of(
                new MultiblockAssemblyService.Placement(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), Blocks.IRON_BLOCK.asItem().getDefaultInstance()),
                new MultiblockAssemblyService.Placement(BlockPos.ZERO.above(), Blocks.IRON_BLOCK.defaultBlockState(), Blocks.IRON_BLOCK.asItem().getDefaultInstance())
        );

        var requirements = MultiblockAssemblyService.aggregateRequirements(placements);

        assertEquals(1, requirements.size());
        assertEquals(2, requirements.getFirst().getCount());
        assertEquals(Items.IRON_BLOCK, requirements.getFirst().getItem());
    }

    @Test
    void representativeStateSkipsAirAndUnsupportedPredicates() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.Air(),
                BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK),
                BlockPos.ZERO.west(), new BlockPredicate.Any()
        ));

        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, pattern);

        assertEquals(1, placements.size());
        assertEquals(Blocks.COPPER_BLOCK, placements.getFirst().state().getBlock());
    }

    @Test
    void placementRetainsPredicateForDemolishMatching() {
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.COPPER_BLOCK),
                new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)
        ));
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO.east(), predicate));

        var placement = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, pattern).getFirst();

        assertTrue(placement.matches(Blocks.COPPER_BLOCK.defaultBlockState()));
        assertTrue(placement.matches(Blocks.IRON_BLOCK.defaultBlockState()));
        assertFalse(placement.matches(Blocks.GOLD_BLOCK.defaultBlockState()));
    }

    @Test
    void levelCandidatesAreOrderedByPriorityDescending() {
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(MMCR.id("coil"), Component.literal("Coil")));
        MachineLevelRegistry.registerLevel(new MachineLevel(MMCR.id("basic"), MMCR.id("coil"), 1,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));
        MachineLevelRegistry.registerLevel(new MachineLevel(MMCR.id("advanced"), MMCR.id("coil"), 2,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));
        MachineLevelRegistry.freezeRegistration();
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState())
        ));

        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO,
                new BlockArray(Map.of(BlockPos.ZERO.east(), predicate)));

        assertEquals(1, placements.size());
        assertEquals(Blocks.IRON_BLOCK, placements.getFirst().state().getBlock());
    }

    @Test
    void selectsAllAffordablePlacementsAndSkipsMissingMaterials() {
        StructureItemSource source = PlayerInventoryStructureItemSource.forStacks(List.of(
                itemStack(Items.STONE, 2), itemStack(Items.DIRT, 1)));
        List<MultiblockAssemblyService.Placement> placements = List.of(
                new MultiblockAssemblyService.Placement(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1)),
                new MultiblockAssemblyService.Placement(BlockPos.ZERO.above(), Blocks.DIRT.defaultBlockState(), itemStack(Items.DIRT, 1)),
                new MultiblockAssemblyService.Placement(BlockPos.ZERO.above(2), Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1)),
                new MultiblockAssemblyService.Placement(BlockPos.ZERO.above(3), Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1)));

        assertEquals(List.of(BlockPos.ZERO, BlockPos.ZERO.above(), BlockPos.ZERO.above(2)),
                MultiblockAssemblyService.extractAvailablePlacements(placements, source).stream()
                        .map(MultiblockAssemblyService.Placement::pos)
                        .toList());
    }

    @Test
    void choosesAvailableLowerPriorityCandidateWhenPreferredCandidateIsUnavailable() {
        BlockPredicate predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK),
                new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        StructureItemSource source = PlayerInventoryStructureItemSource.forStacks(List.of(new ItemStack(Items.IRON_BLOCK)));
        MultiblockAssemblyService.Placement template = new MultiblockAssemblyService.Placement(BlockPos.ZERO,
                Blocks.DIAMOND_BLOCK.defaultBlockState(), new ItemStack(Items.DIAMOND_BLOCK), predicate);

        assertEquals(Blocks.IRON_BLOCK.defaultBlockState(),
                MultiblockAssemblyService.extractAvailablePlacements(List.of(template), source).getFirst().state());
    }

    private static ItemStack itemStack(net.minecraft.world.item.Item item, int count) {
        return new ItemStack(Holder.direct(item, DataComponentMap.EMPTY), count);
    }
}
