package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import java.util.stream.IntStream;
import net.minecraft.world.item.Item;
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
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(MMCR.id("coil"), Component.literal("Coil")));
        TestBootstrap.registerLevel(new MachineLevel(MMCR.id("basic"), MMCR.id("coil"), 1,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()), ItemStack.EMPTY, ModifierDefinition.EMPTY));
        TestBootstrap.registerLevel(new MachineLevel(MMCR.id("advanced"), MMCR.id("coil"), 2,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY, ModifierDefinition.EMPTY));
        TestBootstrap.freezeRegistration();
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
    void rejectsAllPlacementsWhenAnyMaterialIsMissing() {
        StructureItemSource source = PlayerInventoryStructureItemSource.forStacks(List.of(
                itemStack(Items.STONE, 2), itemStack(Items.DIRT, 1)));
        List<MultiblockAssemblyService.Placement> placements = List.of(
                new MultiblockAssemblyService.Placement(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1)),
                new MultiblockAssemblyService.Placement(BlockPos.ZERO.above(), Blocks.DIRT.defaultBlockState(), itemStack(Items.DIRT, 1)),
                new MultiblockAssemblyService.Placement(BlockPos.ZERO.above(2), Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1)),
                new MultiblockAssemblyService.Placement(BlockPos.ZERO.above(3), Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1)));

        assertTrue(MultiblockAssemblyService.extractAvailablePlacements(placements, source).isEmpty());
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

    @Test
    void limitsOneOperationToGtceuScaleBatchSize() {
        List<MultiblockAssemblyService.Placement> placements = IntStream.range(0, 163841)
                .mapToObj(index -> new MultiblockAssemblyService.Placement(
                        new BlockPos(index, 0, 0), Blocks.STONE.defaultBlockState(),
                        itemStack(Items.STONE, 1)))
                .toList();

        assertEquals(163840, MultiblockAssemblyService.MAX_BLOCKS_PER_OPERATION);
        assertEquals(163840, MultiblockAssemblyService.limitOperation(placements).size());
    }

    @Test
    void buildTaskConsumesOnlyItsTickBudget() {
        List<BlockPos> placed = new ArrayList<>();
        List<MultiblockAssemblyService.Placement> placements = IntStream.range(0, 5)
                .mapToObj(index -> new MultiblockAssemblyService.Placement(
                        new BlockPos(index, 0, 0), Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1)))
                .toList();
        MultiblockAssemblyService.BuildTask task = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, placements, 2);

         assertEquals(2, task.advance(placement -> placed.add(placement.pos()) || true));
         assertEquals(2, task.advance(placement -> placed.add(placement.pos()) || true));
         assertEquals(1, task.advance(placement -> placed.add(placement.pos()) || true));
        assertTrue(task.isComplete());
        assertEquals(5, placed.size());
    }

    @Test
    void completedTaskCannotBeAdvancedTwice() {
        List<BlockPos> placed = new ArrayList<>();
        MultiblockAssemblyService.BuildTask task = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, List.of(new MultiblockAssemblyService.Placement(
                        BlockPos.ZERO, Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1))), 4);

        task.advance(placement -> placed.add(placement.pos()) || true);

        assertEquals(0, task.advance(placement -> placed.add(placement.pos()) || true));
        assertEquals(1, placed.size());
    }

    @Test
    void registryRejectsDuplicateControllerTasks() {
        MultiblockAssemblyService.BuildTaskRegistry registry = new MultiblockAssemblyService.BuildTaskRegistry();
        MultiblockAssemblyService.BuildTask task = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, List.of(), 1);

        assertTrue(registry.submit(task));
        assertFalse(registry.submit(MultiblockAssemblyService.BuildTask.create(BlockPos.ZERO, List.of(), 1)));
        assertTrue(registry.hasActiveTask(BlockPos.ZERO));
    }

    @Test
    void cancelledTaskExposesOnlyUnconsumedPlacementsForRefund() {
        List<MultiblockAssemblyService.Placement> placements = IntStream.range(0, 3)
                .mapToObj(index -> new MultiblockAssemblyService.Placement(
                        new BlockPos(index, 0, 0), Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1)))
                .toList();
        MultiblockAssemblyService.BuildTask task = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, placements, 1);

        task.advance(ignored -> true);

        assertEquals(List.of(BlockPos.ZERO.east(), BlockPos.ZERO.east(2)),
                task.unconsumedPlacements().stream().map(MultiblockAssemblyService.Placement::pos).toList());
    }

    @Test
    void skippedPlacementsAreRefundableAfterTaskCompletion() {
        MultiblockAssemblyService.BuildTask task = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, List.of(new MultiblockAssemblyService.Placement(
                        BlockPos.ZERO, Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1))), 1);

        assertEquals(1, task.advance(ignored -> false));
        assertTrue(task.isComplete());
        assertEquals(List.of(Items.STONE), task.refundRequirements().stream().map(ItemStack::getItem).toList());
    }

    @Test
    void creativeTaskDoesNotRefundUnreservedMaterials() {
        MultiblockAssemblyService.BuildTask task = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, List.of(new MultiblockAssemblyService.Placement(
                        BlockPos.ZERO, Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1))), 1, false);

        task.advance(ignored -> false);

        assertTrue(task.refundRequirements().isEmpty());
    }

    @Test
    void completionReportIsAvailableOnlyOnce() {
        MultiblockAssemblyService.BuildTask task = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, List.of(new MultiblockAssemblyService.Placement(
                        BlockPos.ZERO, Blocks.STONE.defaultBlockState(), itemStack(Items.STONE, 1))), 1);

        task.advance(ignored -> true);

        assertTrue(task.takeCompletionReport().isPresent());
        assertTrue(task.takeCompletionReport().isEmpty());
    }

    @Test
    void rejectedDropStopsBeforeRemovingCurrentAndLaterBlocks() {
        List<MultiblockAssemblyService.Removal> removed = new ArrayList<>();
        StructureItemSink sink = new StructureItemSink() {
            private int accepted;

            @Override
            public boolean accept(ItemStack stack) {
                return accepted++ == 0;
            }
        };
        List<MultiblockAssemblyService.Removal> removals = List.of(
                new MultiblockAssemblyService.Removal(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), new ItemStack(Items.STONE)),
                new MultiblockAssemblyService.Removal(BlockPos.ZERO.east(), Blocks.DIRT.defaultBlockState(), new ItemStack(Items.DIRT)),
                new MultiblockAssemblyService.Removal(BlockPos.ZERO.east(2), Blocks.COBBLESTONE.defaultBlockState(), new ItemStack(Items.COBBLESTONE)));

        int count = MultiblockAssemblyService.removeAcceptedDrops(removals, sink, removal -> removed.add(removal));

        assertEquals(1, count);
        assertEquals(List.of(BlockPos.ZERO), removed.stream().map(MultiblockAssemblyService.Removal::pos).toList());
    }

    @Test
    void buildTaskExposesTheStageItWasCreatedFor() {
        List<MultiblockAssemblyService.Placement> placements = List.of(
                new MultiblockAssemblyService.Placement(BlockPos.ZERO, Blocks.STONE.defaultBlockState(),
                        Items.STONE.getDefaultInstance()));

        MultiblockAssemblyService.BuildTask explicitStage = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, 2, placements, 1, false);

        assertEquals(2, explicitStage.stage());
    }

    @Test
    void legacyCreateFallsBackToStageOne() {
        MultiblockAssemblyService.BuildTask legacy = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, List.of(), 1);
        MultiblockAssemblyService.BuildTask legacyWithReservation = MultiblockAssemblyService.BuildTask.create(
                BlockPos.ZERO, List.of(), 1, true);

        assertEquals(1, legacy.stage());
        assertEquals(1, legacyWithReservation.stage());
    }

    @Test
    void replacementAllowsOnlyAirAndFlowingLiquidBlocks() {
        assertTrue(MultiblockAssemblyService.canReplace(Blocks.AIR.defaultBlockState()));
        assertTrue(MultiblockAssemblyService.canReplace(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1)));
        assertTrue(MultiblockAssemblyService.canReplace(Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 8)));
        assertFalse(MultiblockAssemblyService.canReplace(Blocks.WATER.defaultBlockState()));
        assertFalse(MultiblockAssemblyService.canReplace(Blocks.LAVA.defaultBlockState()));
        assertFalse(MultiblockAssemblyService.canReplace(Blocks.STONE.defaultBlockState()));
        assertFalse(MultiblockAssemblyService.canReplace(Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true)));
        assertFalse(MultiblockAssemblyService.isFluidSource(Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true)));
    }

    @Test
    void templateIncludesFluidSourcesButSkipsExplicitFlowingStates() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.WATER),
                BlockPos.ZERO.west(), new BlockPredicate.OfBlock(Blocks.LAVA),
                BlockPos.ZERO.north(), new BlockPredicate.OfBlockState(
                        Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1))));

        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, pattern);

        assertEquals(2, placements.size());
        assertTrue(placements.stream().allMatch(placement -> MultiblockAssemblyService.isFluidSource(placement.state())));
        assertTrue(MultiblockAssemblyService.aggregateRequirements(placements).isEmpty());
    }

    @Test
    void fluidSelectionReservesTotalSourceCountWithoutConsumingStorage() {
        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, new BlockArray(Map.of(
                BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.WATER),
                BlockPos.ZERO.west(), new BlockPredicate.OfBlock(Blocks.WATER))));
        StructureItemSource oneSource = fluidSource(1);
        StructureItemSource twoSources = fluidSource(2);

        assertTrue(MultiblockAssemblyService.extractAvailablePlacements(placements, oneSource).isEmpty());
        assertTrue(MultiblockAssemblyService.extractAvailablePlacements(placements,
                PlayerInventoryStructureItemSource.forStacks(List.of(new ItemStack(Items.WATER_BUCKET)))).isEmpty());
        assertEquals(2, MultiblockAssemblyService.extractAvailablePlacements(placements, twoSources).size());
        assertEquals(2L, twoSources.copyFluidSources().get(Fluids.WATER));
    }

    @Test
    void taskConsumesFluidOnlyForSuccessfulExecutedPlacements() {
        StructureItemSource source = fluidSource(2);
        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, new BlockArray(Map.of(
                BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.WATER),
                BlockPos.ZERO.west(), new BlockPredicate.OfBlock(Blocks.WATER))));
        var task = MultiblockAssemblyService.BuildTask.create(BlockPos.ZERO, 1, placements, 1, true, source);

        task.advance(ignored -> false);
        assertEquals(2L, source.copyFluidSources().get(Fluids.WATER));
        task.advance(ignored -> true);
        assertEquals(1L, source.copyFluidSources().get(Fluids.WATER));
        assertEquals(1, task.placedCount());
        assertTrue(task.refundRequirements().isEmpty());
    }

    @Test
    void cancelledFluidTaskLeavesUnexecutedSourcesInStorage() {
        StructureItemSource source = fluidSource(2);
        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO, new BlockArray(Map.of(
                BlockPos.ZERO.east(), new BlockPredicate.OfBlock(Blocks.WATER),
                BlockPos.ZERO.west(), new BlockPredicate.OfBlock(Blocks.WATER))));
        var registry = new MultiblockAssemblyService.BuildTaskRegistry();
        registry.submit(MultiblockAssemblyService.BuildTask.create(BlockPos.ZERO, 1, placements, 1, true, source));

        registry.advance(BlockPos.ZERO, ignored -> true);
        var cancelled = registry.cancel(BlockPos.ZERO);

        assertEquals(1L, source.copyFluidSources().get(Fluids.WATER));
        assertEquals(1, cancelled.unconsumedPlacements().size());
        assertTrue(cancelled.refundRequirements().isEmpty());
    }

    @Test
    void creativeFluidTaskDoesNotNeedAMaterialSource() {
        var placement = new MultiblockAssemblyService.Placement(BlockPos.ZERO, Blocks.WATER.defaultBlockState(), ItemStack.EMPTY);
        var task = MultiblockAssemblyService.BuildTask.create(BlockPos.ZERO, List.of(placement), 1, false);

        task.advance(ignored -> true);

        assertEquals(1, task.placedCount());
        assertTrue(task.refundRequirements().isEmpty());
    }

    @Test
    void fluidPreferredAlternativeCanFallBackToAnAvailableBlockItem() {
        var predicate = new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlock(Blocks.WATER),
                new BlockPredicate.OfBlock(Blocks.STONE)));
        var placements = MultiblockAssemblyService.createTemplatePlacements(BlockPos.ZERO,
                new BlockArray(Map.of(BlockPos.ZERO.east(), predicate)));
        var source = PlayerInventoryStructureItemSource.forStacks(List.of(new ItemStack(Items.STONE)));

        var selected = MultiblockAssemblyService.extractAvailablePlacements(placements, source);

        assertEquals(1, selected.size());
        assertEquals(Blocks.STONE, selected.getFirst().state().getBlock());
        assertEquals(1, selected.getFirst().requirement().getCount());
    }

    @Test
    void ordinaryDemolitionSkipsFluidsAndStillRemovesLaterItemBlocks() {
        List<MultiblockAssemblyService.Removal> removed = new ArrayList<>();
        var source = new MultiblockAssemblyService.Removal(BlockPos.ZERO, Blocks.WATER.defaultBlockState(), ItemStack.EMPTY);
        var flowing = new MultiblockAssemblyService.Removal(BlockPos.ZERO.east(),
                Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), ItemStack.EMPTY);
        var stone = new MultiblockAssemblyService.Removal(BlockPos.ZERO.west(), Blocks.STONE.defaultBlockState(), new ItemStack(Items.STONE));

        int count = MultiblockAssemblyService.removeAcceptedDrops(List.of(source, flowing, stone), ignored -> true, removed::add);

        assertEquals(1, count);
        assertEquals(List.of(stone), removed);
    }

    private static StructureItemSource fluidSource(long sources) {
        return new StructureItemSource() {
            private long remaining = sources;

            @Override
            public List<ItemStack> copyStacks() { return List.of(); }

            @Override
            public boolean canExtractAll(List<ItemStack> requirements) { return requirements.isEmpty(); }

            @Override
            public boolean extractAll(List<ItemStack> requirements) { return requirements.isEmpty(); }

            @Override
            public Map<Fluid, Long> copyFluidSources() { return Map.of(Fluids.WATER, remaining); }

            @Override
            public boolean placeFluidSource(Fluid fluid, BooleanSupplier placement) {
                if (fluid != Fluids.WATER || remaining < 1 || !placement.getAsBoolean()) return false;
                remaining--;
                return true;
            }
        };
    }

    private static ItemStack itemStack(Item item, int count) {
        return new ItemStack(Holder.direct(item, DataComponentMap.EMPTY), count);
    }
}
