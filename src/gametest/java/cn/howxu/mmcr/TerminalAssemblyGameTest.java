package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.Machine;
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

    public void buildExpandableControllerPlacesOnlyStageOne(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("expandable_structure_stages")));
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
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("expandable_structure_stages")));
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

    public void survivalBuildPartiallyWhenStageOneMaterialsAreMissing(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        ServerPlayer player = servicePlayer(helper);
        player.getInventory().add(new ItemStack(ModBlocks.CASING.get(), template.size() - 1));

        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(player, controller, false);

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Build succeeds with partial materials");
        helper.assertTrue(result.changedBlocks() == template.size() - 1, "All affordable structure blocks are placed");
        int airBlocks = 0;
        for (MultiblockAssemblyService.Placement placement : template) {
            if (helper.getLevel().getBlockState(placement.pos()).isAir()) {
                airBlocks++;
            } else {
                helper.assertTrue(helper.getLevel().getBlockState(placement.pos()).is(ModBlocks.CASING.get()),
                        "Placed structure blocks are casings");
            }
        }
        helper.assertTrue(airBlocks == 1, "Exactly one structure position remains air");
        helper.assertTrue(new PlayerInventoryStructureItemSource(player).canExtractAll(List.of(new ItemStack(ModBlocks.CASING.get()))) == false,
                "Placed blocks consume all available materials");
        helper.succeed();
    }

    public void survivalBuildContinuesAfterInventoryIsReplenished(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("iron_compressor")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("iron_compressor")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        int firstIndex = -1;
        int missingIndex = -1;
        int laterIndex = -1;
        for (int index = 0; index < template.size(); index++) {
            if (!template.get(index).state().is(ModBlocks.BLOCKS.get("item_input_bus").get())) continue;
            int firstCasing = -1;
            for (int earlier = 0; earlier < index; earlier++) {
                if (template.get(earlier).state().is(ModBlocks.CASING.get())) {
                    firstCasing = earlier;
                    break;
                }
            }
            for (int later = index + 1; later < template.size(); later++) {
                if (template.get(later).state().is(ModBlocks.CASING.get())) {
                    firstIndex = firstCasing;
                    missingIndex = index;
                    laterIndex = later;
                    break;
                }
            }
            if (firstIndex >= 0) break;
        }
        if (firstIndex < 0) {
            for (int first = 0; first < template.size() - 2 && firstIndex < 0; first++) {
                for (int missing = first + 1; missing < template.size() - 1 && firstIndex < 0; missing++) {
                    if (ItemStack.isSameItemSameComponents(template.get(first).requirement(), template.get(missing).requirement())) continue;
                    for (int later = missing + 1; later < template.size(); later++) {
                        if (!ItemStack.isSameItemSameComponents(template.get(missing).requirement(), template.get(later).requirement())) {
                            firstIndex = first;
                            missingIndex = missing;
                            laterIndex = later;
                            break;
                        }
                    }
                }
            }
        }
        helper.assertTrue(firstIndex >= 0 && firstIndex < missingIndex && missingIndex < laterIndex,
                "Template orders the first, missing, and later placements");
        MultiblockAssemblyService.Placement firstPlacement = template.get(firstIndex);
        MultiblockAssemblyService.Placement missingPlacement = template.get(missingIndex);
        MultiblockAssemblyService.Placement laterPlacement = template.get(laterIndex);
        BlockPos firstPosition = firstPlacement.pos();
        BlockPos missingPosition = missingPlacement.pos();
        BlockPos laterAffordablePosition = laterPlacement.pos();
        for (MultiblockAssemblyService.Placement placement : template) {
            if (!placement.pos().equals(firstPosition) && !placement.pos().equals(missingPosition)
                    && !placement.pos().equals(laterAffordablePosition)) {
                helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
            }
        }

        ServerPlayer player = servicePlayer(helper);
        player.getInventory().add(firstPlacement.requirement().copy());
        player.getInventory().add(laterPlacement.requirement().copy());

        MultiblockAssemblyService.Result first = MultiblockAssemblyService.build(player, controller, false);

        helper.assertTrue(first.changedBlocks() == 2, "First build places both affordable positions");
        helper.assertTrue(helper.getLevel().getBlockState(firstPosition).is(firstPlacement.state().getBlock()), "First placement is built");
        helper.assertTrue(helper.getLevel().getBlockState(missingPosition).isAir(), "Missing input bus remains air");
        helper.assertTrue(helper.getLevel().getBlockState(laterAffordablePosition).is(laterPlacement.state().getBlock()), "Later placement is built");

        player.getInventory().add(missingPlacement.requirement().copy());
        MultiblockAssemblyService.Result second = MultiblockAssemblyService.build(player, controller, false);

        helper.assertTrue(second.changedBlocks() == 1, "Second build places only the replenished input bus");
        helper.assertTrue(helper.getLevel().getBlockState(missingPosition).is(missingPlacement.state().getBlock()), "Missing placement is built");
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
