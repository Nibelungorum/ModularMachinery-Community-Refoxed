package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.assembly.PlayerInventoryStructureItemSource;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Covers terminal-backed structure build and demolish operations in GameTest worlds.
 *
 * @author howxu <dev@howxu.cn>
 */
public class TerminalAssemblyGameTest {

    public void buildSkipsOccupiedPositionsAndPlacesOnlyMissingBlocks(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        Block preexistingBlock = Blocks.COBBLESTONE;

        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        BlockPos occupiedPos = template.get(0).pos();
        BlockPos missingPos = template.get(1).pos();
        helper.getLevel().setBlock(occupiedPos, preexistingBlock.defaultBlockState(), 3);

        ServerPlayer player = servicePlayer(helper);
        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(player, controller, true);

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Build succeeds in creative service mode");
        helper.assertTrue(result.changedBlocks() == template.size() - 1, "Build places only missing structure blocks");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getLevel().getBlockState(missingPos).is(ModBlocks.CASING.get()), "Missing block is built");
                    helper.assertTrue(helper.getLevel().getBlockState(occupiedPos).is(preexistingBlock), "Occupied block is preserved");
                })
                .thenExecute(helper::succeed);
    }

    public void demolishSkipsAirAndNonMatchingBlocks(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        Block nonMatchingBlock = Blocks.COBBLESTONE;

        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        BlockPos airPos = template.get(0).pos();
        BlockPos nonMatchingPos = template.get(1).pos();
        BlockPos removedPos = template.get(2).pos();
        BlockPos cappedMatchingPos = template.get(3).pos();
        for (MultiblockAssemblyService.Placement placement : template) {
            if (!placement.pos().equals(airPos) && !placement.pos().equals(nonMatchingPos)) {
                helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
            }
        }
        helper.getLevel().setBlock(nonMatchingPos, nonMatchingBlock.defaultBlockState(), 3);

        ServerPlayer player = servicePlayer(helper);
        MultiblockAssemblyService.Result result = MultiblockAssemblyService.demolish(player, controller, 1, stack -> {});

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Demolish succeeds in service mode");
        helper.assertTrue(helper.getLevel().getBlockState(removedPos).isAir(), "Matching block is removed");
        helper.assertTrue(helper.getLevel().getBlockState(airPos).isAir(), "Existing air stays air");
        helper.assertTrue(helper.getLevel().getBlockState(nonMatchingPos).is(nonMatchingBlock), "Non-matching block is preserved");
        helper.assertTrue(helper.getLevel().getBlockState(cappedMatchingPos).is(ModBlocks.CASING.get()), "Cap preserves later matching blocks");
        helper.assertTrue(result.changedBlocks() == 1, "Demolish obeys the requested cap");
        helper.succeed();
    }

    public void demolishExpandableFormedStageTwoRemovesCompleteSnapshot(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("expandable_structure_stages")));
        Machine machine = controller.boundMachine().orElseThrow();
        List<MultiblockAssemblyService.Placement> stage2Template = MultiblockAssemblyService.createTemplatePlacements(
                controller.getBlockPos(), controller.assemblyPattern(machine, 2));
        BlockPos stage1Pos = template(controller).getFirst().pos();
        BlockPos stage2Pos = stageOnlyPos(controller, machine, 2, template(controller));
        for (MultiblockAssemblyService.Placement placement : stage2Template) {
            helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
        }
        ServerPlayer player = servicePlayer(helper);
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Stage 2 structure forms before demolish");
            helper.assertTrue(controller.structureSnapshot().matchedStage() == 2, "Controller matched stage 2 before demolish");

            MultiblockAssemblyService.Result result = MultiblockAssemblyService.demolish(player, controller, 16, stack -> {});

            helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Demolish succeeds in service mode");
            helper.assertTrue(result.changedBlocks() == 2, "Demolish removes the complete stage 2 snapshot");
            helper.assertTrue(helper.getLevel().getBlockState(stage1Pos).isAir(), "Stage 1 block is removed");
            helper.assertTrue(helper.getLevel().getBlockState(stage2Pos).isAir(), "Stage 2-only block is removed");
            helper.succeed();
        });
    }

    public void defaultBuildMissingMaterialsExcludeStageTwo(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("expandable_structure_stages")));
        Machine machine = controller.boundMachine().orElseThrow();
        BlockPos stage2OnlyPos = stageOnlyPos(controller, machine, 2, template(controller));
        ServerPlayer player = servicePlayer(helper);
        player.getInventory().add(new ItemStack(ModBlocks.CASING.get()));

        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(player, controller, false);

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "One stage-1 casing is enough for default build");
        helper.assertTrue(result.changedBlocks() == 1, "Survival build places only the required stage-1 block");
        helper.assertTrue(new PlayerInventoryStructureItemSource(player).extractAll(List.of(new ItemStack(ModBlocks.CASING.get()))) == false,
                "No stage-2-only casing was consumed");
        helper.assertTrue(helper.getLevel().getBlockState(stage2OnlyPos).isAir(), "Stage 2-only block is not built");
        helper.succeed();
    }

    public void survivalBuildRejectsWhenStageOneMaterialsAreMissing(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        ServerPlayer player = servicePlayer(helper);
        player.getInventory().add(new ItemStack(ModBlocks.CASING.get(), template.size() - 1));

        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(player, controller, false);

        helper.assertTrue(result.interactionResult() == InteractionResult.FAIL, "Build rejects partial materials");
        helper.assertTrue(result.changedBlocks() == 0, "No structure blocks are placed");
        for (MultiblockAssemblyService.Placement placement : template) {
            helper.assertTrue(helper.getLevel().getBlockState(placement.pos()).isAir(),
                    "Structure remains unchanged");
        }
        helper.assertTrue(new PlayerInventoryStructureItemSource(player).canExtractAll(
                        List.of(new ItemStack(ModBlocks.CASING.get(), template.size() - 1))),
                "Missing-material failure does not consume available materials");
        helper.succeed();
    }

    public void buildCompletesAcrossTicksAndRejectsDuplicateSubmission(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        controller.setBuildBlocksPerTickForTesting(1);
        controller.setStructureCheckIntervalForTesting(1);
        controller.setStructureScanBatchesForTesting(Config.DEFAULT_STRUCTURE_SCAN_BATCHES);
        long acceptedAt = helper.getLevel().getGameTime();
        ServerPlayer player = servicePlayer(helper);
        MultiblockAssemblyService.Result accepted = MultiblockAssemblyService.build(player, controller, true);
        MultiblockAssemblyService.Result duplicate = MultiblockAssemblyService.build(player, controller, true);

        helper.assertTrue(accepted.interactionResult() == InteractionResult.SUCCESS, "Large build request is accepted");
        helper.assertTrue(duplicate.interactionResult() == InteractionResult.FAIL, "Duplicate build request is rejected while active");
        int expectedCount = template.size();
        helper.assertTrue(countPlacedStructureBlocks(helper, template) == 0,
                "Accepted build waits for the real block ticker");
        int completionTick = expectedCount + 2;
        int scanWaitTicks = 150 - completionTick;
        helper.runAtTickTime(completionTick + 1, controller::requestImmediateStructureCheck);
        helper.runAtTickTime(completionTick + scanWaitTicks + 1, () -> {
            helper.assertTrue(helper.getLevel().getGameTime() > acceptedAt + 1, "Build advances across multiple server ticks");
            for (int placementsThisTick : controller.buildTaskPlacementsPerTickForTesting().values()) {
                helper.assertTrue(placementsThisTick <= 1, "The server ticker respects the per-tick build budget");
            }
            for (int batchesThisTick : controller.scanBatchesPerTickForTesting().values()) {
                helper.assertTrue(batchesThisTick <= 1, "The structure scanner performs at most one batch per tick");
            }
            helper.assertTrue(controller.scanBatchCountForTesting() >= Config.DEFAULT_STRUCTURE_SCAN_BATCHES,
                    "All five structure scan batches execute");
            helper.assertTrue(countPlacedStructureBlocks(helper, template) == expectedCount,
                    "Final placed structure block count is unchanged by duplicate submission");
            helper.assertTrue(controller.structureSnapshot().formed(), "Controller forms after the build completes");
            helper.succeed();
        });
    }

    public void completedBuildRequestsStructureDiagnostic(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        controller.setStructureCheckIntervalForTesting(1);
        controller.setStructureScanBatchesForTesting(Config.DEFAULT_STRUCTURE_SCAN_BATCHES);
        List<MultiblockAssemblyService.Placement> template = template(controller);
        helper.getLevel().setBlock(template.getFirst().pos(), Blocks.COBBLESTONE.defaultBlockState(), 3);

        int[] diagnostics = {0};
        controller.setStructureDiagnosticCallbackForTesting(() -> diagnostics[0]++);
        MultiblockAssemblyService.build(servicePlayer(helper), controller, true);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(diagnostics[0] == 1,
                    "Completed terminal builds request a structure diagnostic when the structure is still invalid");
            helper.succeed();
        });
    }

    public void incrementalScanRestartsAfterPendingInvalidation(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        MultiblockAssemblyService.Placement changedPlacement = template.getLast();
        BlockPos changedPos = changedPlacement.pos();
        controller.setStructureScanBatchesForTesting(Config.DEFAULT_STRUCTURE_SCAN_BATCHES);
        controller.setStructureCheckIntervalForTesting(1);
        for (MultiblockAssemblyService.Placement placement : template) {
            helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
        }
        helper.runAtTickTime(1, controller::requestImmediateStructureCheck);
        helper.runAtTickTime(3, () -> {
            controller.serverTick();
            int cursorBeforeMutation = controller.structureScanCursorForTesting();
            helper.assertTrue(cursorBeforeMutation >= 0,
                    "The structure scan is still active before the mutation");
            helper.getLevel().setBlock(changedPos, Blocks.AIR.defaultBlockState(), 3);
            controller.onStructureBlockChanged(changedPos);
            helper.assertTrue(controller.isPendingStructureInvalidationForTesting(),
                    "A block change during the scan records pending invalidation");
            helper.getLevel().setBlock(changedPos, changedPlacement.state(), 3);
            controller.onStructureBlockChanged(changedPos);
            // The nine-entry test pattern needs nine real ticker batches with its sentinel budget.
            helper.runAtTickTime(20, () -> {
                helper.assertTrue(controller.scanBatchCountForTesting() >= 10,
                        "A fresh scan runs after the pending invalidation");
                helper.assertTrue(controller.scanBatchCountForTesting() > cursorBeforeMutation,
                        "The invalidated scan does not reuse its old cursor as the final result");
                helper.assertTrue(controller.structureSnapshot().formed(), "The restored structure forms after a fresh scan");
                helper.succeed();
            });
        });
    }

    public void smallStructureDiagnosticIsDeliveredAfterNextScan(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("expandable_structure_stages")));
        controller.setStructureCheckIntervalForTesting(1);
        controller.setStructureScanBatchesForTesting(Config.DEFAULT_STRUCTURE_SCAN_BATCHES);
        ServerPlayer player = servicePlayer(helper);
        int[] diagnostics = {0};
        controller.setStructureDiagnosticCallbackForTesting(() -> diagnostics[0]++);

        controller.requestImmediateStructureCheck(player);
        helper.assertTrue(controller.isStructureDiagnosticRequestedForTesting(),
                "Shift-right-click diagnostic request is recorded before the ticker runs");
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!controller.isStructureDiagnosticRequestedForTesting(),
                    "Small-structure mismatch diagnostic is delivered by the scan result: requested="
                            + controller.isStructureDiagnosticRequestedForTesting() + " deliveries=" + diagnostics[0]
                            + " batches=" + controller.scanBatchCountForTesting());
            helper.assertTrue(diagnostics[0] == 1, "Small-structure mismatch diagnostic feedback is delivered once: deliveries="
                    + diagnostics[0]);
            helper.succeed();
        });
    }

    public void disconnectedBuilderDropsReservedMaterials(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        controller.setBuildBlocksPerTickForTesting(1);
        ServerPlayer player = servicePlayer(helper);
        for (MultiblockAssemblyService.Placement placement : template) {
            player.getInventory().add(placement.requirement().copy());
        }

        MultiblockAssemblyService.Result accepted = MultiblockAssemblyService.build(player, controller, false);
        helper.assertTrue(accepted.interactionResult() == InteractionResult.SUCCESS, "Survival build reserves materials");
        player.setRemoved(RemovalReason.DISCARDED);
        helper.runAtTickTime(2, () -> {
            long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(helper.absolutePos(controllerPos)).inflate(1))
                    .stream()
                    .filter(entity -> entity.getItem().is(ModBlocks.CASING.get().asItem()))
                    .mapToLong(entity -> entity.getItem().getCount())
                    .sum();
            helper.assertTrue(dropped == template.size(),
                    "A disconnected builder receives every unplaced reserved material as drops");
            helper.succeed();
        });
    }

    private static List<MultiblockAssemblyService.Placement> template(MachineControllerBlockEntity controller) {
        var machine = controller.boundMachine().orElseThrow();
        return MultiblockAssemblyService.createTemplatePlacements(controller.getBlockPos(),
                controller.assemblyPattern(machine));
    }

    private static int countPlacedStructureBlocks(GameTestHelper helper,
                                                   List<MultiblockAssemblyService.Placement> template) {
        int count = 0;
        for (MultiblockAssemblyService.Placement placement : template) {
            if (placement.matches(helper.getLevel().getBlockState(placement.pos()))) count++;
        }
        return count;
    }

    private static BlockPos stageOnlyPos(MachineControllerBlockEntity controller, Machine machine, int stageNumber,
                                         List<MultiblockAssemblyService.Placement> previousTemplate) {
        var previous = new HashSet<>(previousTemplate.stream().map(MultiblockAssemblyService.Placement::pos).toList());
        return MultiblockAssemblyService.createTemplatePlacements(controller.getBlockPos(), controller.assemblyPattern(machine, stageNumber))
                .stream()
                .map(MultiblockAssemblyService.Placement::pos)
                .filter(pos -> !previous.contains(pos))
                .findFirst()
                .orElseThrow();
    }

    private static ServerPlayer servicePlayer(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-terminal-gametest".getBytes(StandardCharsets.UTF_8)), "mmcr-terminal"),
                ClientInformation.createDefault());
    }

}
