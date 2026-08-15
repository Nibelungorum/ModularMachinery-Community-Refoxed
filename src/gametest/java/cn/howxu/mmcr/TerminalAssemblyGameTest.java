package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.assembly.PlayerInventoryStructureItemSource;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
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
        List<MultiblockAssemblyService.Placement> template = template(controller);
        BlockPos occupiedPos = template.get(0).pos();
        BlockPos missingPos = template.get(1).pos();
        helper.getLevel().setBlock(occupiedPos, preexistingBlock.defaultBlockState(), 3);

        ServerPlayer player = servicePlayer(helper);
        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(player, controller, true);

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Build succeeds in creative service mode");
        helper.assertTrue(helper.getLevel().getBlockState(missingPos).is(ModBlocks.CASING.get()), "Missing block is built");
        helper.assertTrue(helper.getLevel().getBlockState(occupiedPos).is(preexistingBlock), "Occupied block is preserved");
        helper.assertTrue(result.changedBlocks() == template.size() - 1, "Build places only missing structure blocks");
        helper.succeed();
    }

    public void demolishSkipsAirAndNonMatchingBlocks(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        Block nonMatchingBlock = Blocks.COBBLESTONE;

        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
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

    public void buildExpandableControllerPlacesOnlyStageOne(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        Machine machine = controller.boundMachine().orElseThrow();
        List<MultiblockAssemblyService.Placement> stage1Template = template(controller);
        BlockPos stage1Pos = stage1Template.getFirst().pos();
        BlockPos stage2OnlyPos = stageOnlyPos(controller, machine, 2, stage1Template);

        ServerPlayer player = servicePlayer(helper);
        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(player, controller, true);

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Build succeeds in creative service mode");
        helper.assertTrue(result.changedBlocks() == 1, "Default build places only stage 1");
        helper.assertTrue(helper.getLevel().getBlockState(stage1Pos).is(ModBlocks.CASING.get()), "Stage 1 block is built");
        helper.assertTrue(helper.getLevel().getBlockState(stage2OnlyPos).isAir(), "Stage 2-only block is not built");
        helper.succeed();
    }

    public void demolishExpandableFormedStageTwoRemovesCompleteSnapshot(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        Machine machine = controller.boundMachine().orElseThrow();
        List<MultiblockAssemblyService.Placement> stage2Template = MultiblockAssemblyService.createTemplatePlacements(
                controller.getBlockPos(), controller.assemblyPattern(machine, 2));
        BlockPos stage1Pos = template(controller).getFirst().pos();
        BlockPos stage2Pos = stageOnlyPos(controller, machine, 2, template(controller));
        for (MultiblockAssemblyService.Placement placement : stage2Template) {
            helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
        }
        controller.serverTick();
        helper.assertTrue(controller.isFormed(), "Stage 2 structure forms before demolish");
        helper.assertTrue(controller.getMatchedStructureStage() == 2, "Controller matched stage 2 before demolish");

        ServerPlayer player = servicePlayer(helper);
        MultiblockAssemblyService.Result result = MultiblockAssemblyService.demolish(player, controller, 16, stack -> {});

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Demolish succeeds in service mode");
        helper.assertTrue(result.changedBlocks() == 2, "Demolish removes the complete stage 2 snapshot");
        helper.assertTrue(helper.getLevel().getBlockState(stage1Pos).isAir(), "Stage 1 block is removed");
        helper.assertTrue(helper.getLevel().getBlockState(stage2Pos).isAir(), "Stage 2-only block is removed");
        helper.succeed();
    }

    public void defaultBuildMissingMaterialsExcludeStageTwo(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
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

    public void survivalBuildFailsAtomicallyWhenStageOneMaterialsAreMissing(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        List<MultiblockAssemblyService.Placement> template = template(controller);
        ServerPlayer player = servicePlayer(helper);
        player.getInventory().add(new ItemStack(ModBlocks.CASING.get(), template.size() - 1));

        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(player, controller, false);

        helper.assertTrue(result.interactionResult() == InteractionResult.FAIL, "Build fails when any stage-1 block is missing");
        helper.assertTrue(result.changedBlocks() == 0, "No partial placement is reported");
        for (MultiblockAssemblyService.Placement placement : template) {
            helper.assertTrue(helper.getLevel().getBlockState(placement.pos()).isAir(), "No structure blocks are placed");
        }
        helper.assertTrue(new PlayerInventoryStructureItemSource(player).canExtractAll(List.of(new ItemStack(ModBlocks.CASING.get(), template.size() - 1))),
                "Dry-run failure preserves all available materials");
        helper.succeed();
    }

    public void survivalBuildContinuesAfterInventoryIsReplenished(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("iron_compressor")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        List<MultiblockAssemblyService.Placement> template = template(controller);
        MultiblockAssemblyService.Placement missingPlacement = template.stream()
                .filter(placement -> placement.state().is(ModBlocks.BLOCKS.get("item_input_bus").get()))
                .findFirst()
                .orElseThrow();
        List<MultiblockAssemblyService.Placement> casingPlacements = template.stream()
                .filter(placement -> placement.state().is(ModBlocks.CASING.get()))
                .limit(2)
                .toList();
        BlockPos firstPosition = casingPlacements.getFirst().pos();
        BlockPos missingPosition = missingPlacement.pos();
        BlockPos laterAffordablePosition = casingPlacements.getLast().pos();
        for (MultiblockAssemblyService.Placement placement : template) {
            if (!placement.pos().equals(firstPosition) && !placement.pos().equals(missingPosition)
                    && !placement.pos().equals(laterAffordablePosition)) {
                helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
            }
        }

        ServerPlayer player = servicePlayer(helper);
        player.getInventory().add(new ItemStack(ModBlocks.CASING.get(), 2));

        MultiblockAssemblyService.Result first = MultiblockAssemblyService.build(player, controller, false);

        helper.assertTrue(first.changedBlocks() == 2, "First build places both affordable casing positions");
        helper.assertTrue(helper.getLevel().getBlockState(firstPosition).is(ModBlocks.CASING.get()), "First casing is built");
        helper.assertTrue(helper.getLevel().getBlockState(missingPosition).isAir(), "Missing input bus remains air");
        helper.assertTrue(helper.getLevel().getBlockState(laterAffordablePosition).is(ModBlocks.CASING.get()), "Later casing is built");

        player.getInventory().add(new ItemStack(ModBlocks.BLOCKS.get("item_input_bus").get()));
        MultiblockAssemblyService.Result second = MultiblockAssemblyService.build(player, controller, false);

        helper.assertTrue(second.changedBlocks() == 1, "Second build places only the replenished input bus");
        helper.assertTrue(helper.getLevel().getBlockState(missingPosition).is(ModBlocks.BLOCKS.get("item_input_bus").get()), "Missing input bus is built");
        helper.succeed();
    }

    private static List<MultiblockAssemblyService.Placement> template(MachineControllerBlockEntity controller) {
        var machine = controller.boundMachine().orElseThrow();
        return MultiblockAssemblyService.createTemplatePlacements(controller.getBlockPos(),
                controller.assemblyPattern(machine));
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
