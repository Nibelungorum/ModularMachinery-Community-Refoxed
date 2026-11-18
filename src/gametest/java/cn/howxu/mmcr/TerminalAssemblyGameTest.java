package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.DisplayStack;
import cn.howxu.mmcr.api.machine.definition.MachineLevel;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.assembly.PlayerInventoryStructureItemSource;
import cn.howxu.mmcr.internal.assembly.PlayerInventoryStructureItemStorage;
import cn.howxu.mmcr.internal.assembly.StructureItemStorageResolver;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.item.TerminalInventoryMode;
import cn.howxu.mmcr.internal.item.TerminalAction;
import cn.howxu.mmcr.internal.item.TerminalService;
import cn.howxu.mmcr.internal.network.PktTerminalStatePayload;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Covers terminal-backed structure build and demolish operations in GameTest worlds.
 *
 * @author howxu <dev@howxu.cn>
 */
public class TerminalAssemblyGameTest {

    private static final Identifier EXCHANGE = MMCR.id("terminal_level_exchange");
    private static final Identifier COIL = MMCR.id("terminal_test_coil");
    private static final Identifier FRAME = MMCR.id("terminal_test_frame");
    private static final Identifier POLISHED = MMCR.id("terminal_test_polished");

    static void registerLevelExchange(StructureRegistration event) {
        event.registerLevelType(new LevelType(COIL, Component.translatable("block.minecraft.diorite")));
        event.registerLevelType(new LevelType(FRAME, Component.translatable("block.minecraft.granite")));
        event.registerLevel(new MachineLevel(MMCR.id("terminal_test_rough"), COIL, 0,
                BlockPredicate.blockState(Blocks.DIORITE.defaultBlockState()), DisplayStack.of(new ItemStack(Items.DIORITE)),
                ModifierDefinition.EMPTY));
        event.registerLevel(new MachineLevel(POLISHED, COIL, 1,
                BlockPredicate.blockState(Blocks.POLISHED_DIORITE.defaultBlockState()), DisplayStack.of(new ItemStack(Items.DIAMOND)),
                ModifierDefinition.EMPTY));
        event.registerLevel(new MachineLevel(FRAME, FRAME, 0,
                BlockPredicate.blockState(Blocks.GRANITE.defaultBlockState()), DisplayStack.of(new ItemStack(Items.GRANITE)),
                ModifierDefinition.EMPTY));
        event.registerStructure(EXCHANGE, structure -> structure.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("AAAAB", "AACAS", "AAAA ")
                        .where('A', BlockPredicate.block(Blocks.DIORITE))
                        .where('B', BlockPredicate.block(Blocks.GRANITE))
                        .where('S', BlockPredicate.block(Blocks.DIORITE))
                        .controller('C'))
                .requirements(requirements -> requirements.levelSlot('A', COIL).levelSlot('B', FRAME)))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C").layer(" ").layer("L")
                        .where('L', BlockPredicate.block(Blocks.DIORITE)).controller('C'))
                        .requirements(requirements -> requirements.levelSlot('L', COIL))));
    }

    public void replaceLevelReservesWholeOrder(GameTestHelper helper) {
        BlockPos origin = new BlockPos(4, 3, 4);
        helper.setBlock(origin, ModBlocks.controllerFor(EXCHANGE).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.UP).setValue(MachineControllerBlock.ROLL_FACING, Direction.EAST));
        var controller = helper.getBlockEntity(origin, MachineControllerBlockEntity.class);
        Machine machine = MachineRegistry.getMachine(EXCHANGE);
        controller.setMachine(machine);
        ServerPlayer player = servicePlayer(helper);
        var packets = capturePackets(helper, player);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(controller.getBlockPos().getCenter());
        ItemStack terminal = new ItemStack(ModItems.TERMINAL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        TerminalService.bindController(player, terminal, GlobalPos.of(player.level().dimension(), controller.getBlockPos()));
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.SET_LEVEL, 0, COIL, POLISHED).accepted(),
                "Select the polished coil level");
        List<BlockPos> slots = new ArrayList<>();
        List<BlockPos> protectedBlocks = new ArrayList<>();
        machine.structureStages().getFirst().pattern().symbolsByPosition().forEach((local, symbol) -> {
            BlockPos pos = controller.getBlockPos().offset(BlockRotator.rotateSouthTo(local, Direction.UP, Direction.EAST));
            if (symbol == 'A') slots.add(pos);
            if (symbol == 'B' || symbol == 'S') {
                player.level().setBlock(pos, Blocks.DIORITE.defaultBlockState(), 3);
                protectedBlocks.add(pos);
            }
        });
        var stageTwoSlot = controller.getBlockPos().offset(BlockRotator.rotateSouthTo(new BlockPos(0, 0, 2),
                Direction.UP, Direction.EAST));
        player.level().setBlock(stageTwoSlot, Blocks.DIORITE.defaultBlockState(), 3);
        List<BlockPos> old = slots.subList(0, 8);
        old.forEach(pos -> player.level().setBlock(pos, Blocks.DIORITE.defaultBlockState(), 3));
        player.level().setBlock(slots.get(8), Blocks.POLISHED_DIORITE.defaultBlockState(), 3);
        player.level().setBlock(slots.get(9), Blocks.GLASS.defaultBlockState(), 3);
        player.level().removeBlock(slots.get(10), false);
        player.getInventory().setItem(1, new ItemStack(Items.POLISHED_DIORITE, 5));
        var missing = TerminalService.execute(player, terminal, TerminalAction.REPLACE_LEVEL, 0, null, null);
        helper.assertTrue(!missing.accepted() && missing.messageKey().equals("message.mmcr.terminal.replace.missing"),
                "Five blocks cannot pay for an eight-coil replacement: " + missing.messageKey());
        helper.assertTrue(old.stream().allMatch(pos -> player.level().getBlockState(pos).is(Blocks.DIORITE))
                        && player.getInventory().countItem(Items.POLISHED_DIORITE) == 5
                        && player.getInventory().countItem(Items.DIORITE) == 0 && !controller.hasActiveBuildTask(),
                "Missing materials must leave all eight old coils and the complete inventory unchanged");

        player.getInventory().setItem(1, new ItemStack(Items.POLISHED_DIORITE, 8));
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.REPLACE_LEVEL, 0, null, null).accepted(),
                "A fully paid exchange succeeds");
        helper.assertTrue(old.stream().allMatch(pos -> player.level().getBlockState(pos).is(Blocks.POLISHED_DIORITE))
                        && player.getInventory().countItem(Items.POLISHED_DIORITE) == 0 && player.getInventory().countItem(Items.DIORITE) == 8,
                "Exactly eight materials consumed and eight old blocks returned");
        helper.assertTrue(player.level().getBlockState(stageTwoSlot).is(Blocks.DIORITE),
                "A later stage's coil is not exchanged while stage one is selected");
        helper.assertTrue(protectedBlocks.stream().allMatch(pos -> player.level().getBlockState(pos).is(Blocks.DIORITE))
                        && player.level().getBlockState(slots.get(9)).is(Blocks.GLASS) && player.level().getBlockState(slots.get(10)).isAir(),
                "Other types, casings, unrelated blocks and empty slots are untouched");
        var again = TerminalService.execute(player, player.getMainHandItem(), TerminalAction.REPLACE_LEVEL, 0, null, null);
        helper.assertTrue(again.messageKey().equals("message.mmcr.terminal.replace.none")
                        && player.getInventory().countItem(Items.DIORITE) == 8, "Repeated replacement is a no-op");

        player.setGameMode(GameType.CREATIVE);
        terminal = player.getMainHandItem();
        BlockPos busPos = origin.above(3);
        helper.setBlock(busPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        TerminalService.execute(player, terminal, TerminalAction.SET_INVENTORY_MODE, TerminalInventoryMode.CONTAINER.ordinal(), null, null);
        TerminalService.bindContainer(player, terminal, GlobalPos.of(player.level().dimension(), helper.absolutePos(busPos)));
        var bus = StructureItemStorageResolver.resolve(player, TerminalData.from(terminal)).orElseThrow();
        old.forEach(pos -> player.level().setBlock(pos, Blocks.DIORITE.defaultBlockState(), 3));
        bus.sink().accept(new ItemStack(Items.POLISHED_DIORITE, 5));
        player.getInventory().setItem(2, new ItemStack(Items.POLISHED_DIORITE, 8));
        var shortBus = TerminalService.execute(player, terminal, TerminalAction.REPLACE_LEVEL, 0, null, null);
        helper.assertTrue(!shortBus.accepted() && old.stream().allMatch(pos -> player.level().getBlockState(pos).is(Blocks.DIORITE))
                        && bus.source().canExtractAll(List.of(new ItemStack(Items.POLISHED_DIORITE, 5)))
                        && player.getInventory().countItem(Items.POLISHED_DIORITE) == 8,
                "Creative container mode neither borrows inventory nor partially empties the bus");
        TerminalService.execute(player, terminal, TerminalAction.SET_INVENTORY_MODE, TerminalInventoryMode.INVENTORY.ordinal(), null, null);
        int oldItems = player.getInventory().countItem(Items.DIORITE);
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.REPLACE_LEVEL, 0, null, null).accepted()
                        && old.stream().allMatch(pos -> player.level().getBlockState(pos).is(Blocks.POLISHED_DIORITE))
                        && player.getInventory().countItem(Items.DIORITE) == oldItems
                        && player.getInventory().countItem(Items.POLISHED_DIORITE) == 8,
                "Creative inventory mode replaces blocks without consuming or producing items");

        TerminalService.execute(player, terminal, TerminalAction.SET_BOUNDS_ENABLED, 1, null, null);
        var baseBounds = lastState(packets).bounds().orElseThrow();
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.SET_STAGE, 2, null, null).accepted(),
                "The extension is selectable");
        var fullBounds = lastState(packets).bounds().orElseThrow();
        var expected = BoundingBox.encapsulatingPositions(machine.structureStages().get(1).pattern().pattern().keySet().stream()
                .map(pos -> BlockRotator.rotateSouthTo(pos, Direction.UP, Direction.EAST)).toList()).orElseThrow();
        helper.assertTrue(fullBounds.equals(expected) && !baseBounds.equals(fullBounds),
                "Stage change projects the whole asymmetric extension using the vertical controller's roll");
        TerminalService.execute(player, terminal, TerminalAction.REPLACE_LEVEL, 0, null, null);
        helper.assertTrue(player.level().getBlockState(stageTwoSlot).is(Blocks.POLISHED_DIORITE),
                "Selecting the extension now replaces its coil");
        helper.succeed();
    }

    public void terminalServiceRejectsActionsWithoutHeldTerminal(GameTestHelper helper) {
        ServerPlayer player = servicePlayer(helper);

        TerminalService.Result result = TerminalService.execute(player, player.getMainHandItem(), TerminalAction.SET_STAGE,
                2, null, null);

        helper.assertTrue(!result.accepted() && result.messageKey().equals("message.mmcr.terminal.not_held"),
                "A packet action cannot mutate state when the main hand is not a terminal");
        helper.succeed();
    }

    public void terminalServiceRejectsLevelSelectionWithoutController(GameTestHelper helper) {
        ServerPlayer player = servicePlayer(helper);
        ItemStack terminal = new ItemStack(ModItems.TERMINAL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        var type = MachineLevelRegistry.types().getFirst();
        var level = MachineLevelRegistry.levelsForType(type.id()).getFirst();

        TerminalService.Result result = TerminalService.execute(player, terminal, TerminalAction.SET_LEVEL,
                0, type.id(), level.id());

        helper.assertTrue(!result.accepted() && result.messageKey().equals("message.mmcr.terminal.no_controller"),
                "A valid level selection without a controller is rejected without normalizing null");
        helper.succeed();
    }

    public void terminalHighlightIsIndependentAndBindingChangesClearIt(GameTestHelper helper) {
        ServerPlayer player = servicePlayer(helper);
        List<Packet<?>> packets = capturePackets(helper, player);
        ItemStack terminal = new ItemStack(ModItems.TERMINAL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        helper.assertTrue(!TerminalService.execute(player, terminal, TerminalAction.SET_HIGHLIGHT_ENABLED,
                1, null, null).accepted(), "Highlight requires an accessible bound controller");

        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        player.setPos(helper.absolutePos(controllerPos).getCenter());
        GlobalPos target = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(controllerPos));
        helper.assertTrue(TerminalService.bindController(player, terminal, target).accepted(), "Controller binds");
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.SET_HIGHLIGHT_ENABLED,
                1, null, null).accepted(), "Highlight can be enabled without ghost preview");
        helper.assertTrue(TerminalData.from(terminal).highlightEnabled() && !TerminalData.from(terminal).previewEnabled(),
                "Highlight does not enable ghost preview");
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.SET_BOUNDS_ENABLED,
                1, null, null).accepted(), "Unformed structure bounds do not require ghost preview");
        PktTerminalStatePayload state = lastState(packets);
        helper.assertTrue(MMCR.id("test_cube").equals(state.machineId()) && state.data().highlightEnabled(),
                "The client receives the selected machine and independent highlight setting");
        helper.assertTrue(state.data().boundsEnabled() && state.bounds().isPresent()
                        && controller.assemblyPattern(controller.boundMachine().orElseThrow(), 1).pattern().keySet().stream()
                                .allMatch(state.bounds().get()::isInside), "Bounds enclose the complete selected assembly pattern");
        helper.assertTrue(state.facing() == controller.getBlockState().getValue(BlockStateProperties.FACING),
                "The client receives the controller orientation for projection");
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.SET_PREVIEW_ENABLED,
                1, null, null).accepted(), "Ghost preview can be independently enabled");
        helper.assertTrue(TerminalData.from(terminal).highlightEnabled(), "Enabling ghost preview retains highlight");
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.SET_HIGHLIGHT_ENABLED,
                0, null, null).accepted(), "Highlight can be disabled independently");
        helper.assertTrue(!TerminalData.from(terminal).highlightEnabled() && TerminalData.from(terminal).previewEnabled(),
                "Disabling highlight does not disable ghost preview");
        TerminalService.execute(player, terminal, TerminalAction.SET_HIGHLIGHT_ENABLED, 1, null, null);
        helper.assertTrue(TerminalService.bindController(player, terminal, target).accepted(), "Controller rebinds");
        helper.assertTrue(!TerminalData.from(terminal).highlightEnabled() && !TerminalData.from(terminal).boundsEnabled(),
                "Rebinding clears both old overlays");
        TerminalService.execute(player, terminal, TerminalAction.SET_HIGHLIGHT_ENABLED, 1, null, null);
        TerminalService.clear(player, terminal);
        helper.assertTrue(TerminalData.from(terminal).controller() == null && !TerminalData.from(terminal).highlightEnabled(),
                "Clearing the terminal removes its highlight and binding");
        helper.succeed();
    }

    public void terminalServiceNormalizesStaleStageBeforeBuildAndDemolish(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerPlayer player = servicePlayer(helper);
        player.setPos(helper.absolutePos(controllerPos).getCenter());
        player.getAbilities().instabuild = true;
        ItemStack terminal = new ItemStack(ModItems.TERMINAL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        TerminalData staleData = TerminalData.DEFAULT.withController(GlobalPos.of(helper.getLevel().dimension(),
                helper.absolutePos(controllerPos))).withStage(2);
        terminal.set(ModDataComponents.TERMINAL_DATA.get(), staleData);

        TerminalService.Result build = TerminalService.execute(player, terminal, TerminalAction.BUILD, 0, null, null);

        helper.assertTrue(build.accepted(), "Build accepts a terminal with a stale stage after normalization");
        helper.assertTrue(TerminalData.from(terminal).stage() == 1,
                "Build writes the controller's available stage back to terminal data");
        helper.runAtTickTime(2, () -> {
            TerminalData staleDemolishData = TerminalData.from(terminal).withStage(2);
            terminal.set(ModDataComponents.TERMINAL_DATA.get(), staleDemolishData);

            TerminalService.Result demolish = TerminalService.execute(player, terminal, TerminalAction.DEMOLISH, 0, null, null);

            helper.assertTrue(demolish.accepted(), "Demolish accepts a terminal with a stale stage after normalization");
            helper.assertTrue(TerminalData.from(terminal).stage() == 1,
                    "Demolish writes the controller's available stage back to terminal data");
            helper.succeed();
        });
    }

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

    public void buildReplacesFlowingLiquidsButProtectsOccupiedBlocks(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        var placements = template(controller);
        BlockPos flowing = placements.get(0).pos();
        BlockPos source = placements.get(1).pos();
        BlockPos waterlogged = placements.get(2).pos();
        helper.getLevel().setBlock(flowing, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 2);
        helper.getLevel().setBlock(source, Blocks.WATER.defaultBlockState(), 2);
        helper.getLevel().setBlock(waterlogged, Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true), 2);

        var result = MultiblockAssemblyService.build(servicePlayer(helper), controller, true);
        controller.serverTick();

        helper.assertTrue(result.changedBlocks() == placements.size() - 2, "Planning includes flowing water but excludes occupied sources and slabs");
        helper.assertTrue(helper.getLevel().getBlockState(flowing).is(ModBlocks.CASING.get()), "Execution replaces flowing water");
        helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.WATER), "Existing source stays untouched");
        helper.assertTrue(helper.getLevel().getBlockState(waterlogged).is(Blocks.OAK_SLAB), "Waterlogged block stays untouched");
        helper.succeed();
    }

    public void creativeBuildPlacesOnlyFluidSources(GameTestHelper helper) {
        MachineControllerBlockEntity controller = fluidController(helper);
        ServerPlayer player = servicePlayer(helper);
        player.getAbilities().instabuild = true;
        player.setPos(controller.getBlockPos().getCenter());
        ItemStack terminal = new ItemStack(ModItems.TERMINAL.get());
        terminal.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.DEFAULT.withController(
                GlobalPos.of(helper.getLevel().dimension(), controller.getBlockPos())));
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);

        var result = TerminalService.execute(player, terminal, TerminalAction.BUILD, 0, null, null);
        helper.assertTrue(result.accepted(), "Creative inventory mode accepts fluid-only construction");
        controller.serverTick();
        assertFluidTargets(helper, controller);
        var pattern = controller.assemblyPattern(controller.boundMachine().orElseThrow());
        for (var entry : pattern.pattern().entrySet()) {
            if (entry.getKey().equals(BlockPos.ZERO) || entry.getValue().preferredState().orElseThrow().getFluidState().isSource()) continue;
            helper.getLevel().setBlock(controller.getBlockPos().offset(entry.getKey()),
                    Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 2);
        }
        helper.assertTrue(TerminalService.execute(player, terminal, TerminalAction.DEMOLISH, 0, null, null).accepted(),
                "Creative demolition accepts source and non-source fluid targets");
        for (BlockPos relative : pattern.pattern().keySet()) {
            if (relative.equals(BlockPos.ZERO)) continue;
            helper.assertTrue(helper.getLevel().getBlockState(controller.getBlockPos().offset(relative)).isAir(),
                    "Creative demolition clears both source and flowing fluid without returns");
        }
        helper.succeed();
    }

    static MachineControllerBlockEntity fluidController(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, ModBlocks.controllerFor(MMCR.id("fluid_assembly")).get().defaultBlockState());
        var controller = helper.getBlockEntity(pos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("fluid_assembly")));
        var pattern = controller.assemblyPattern(controller.boundMachine().orElseThrow());
        for (BlockPos relative : pattern.pattern().keySet()) {
            if (relative.equals(BlockPos.ZERO)) continue;
            for (Direction direction : Direction.values()) {
                if (direction == Direction.UP) continue;
                BlockPos wall = controller.getBlockPos().offset(relative).relative(direction);
                if (!wall.equals(controller.getBlockPos())) helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            }
        }
        return controller;
    }

    static void assertFluidTargets(GameTestHelper helper, MachineControllerBlockEntity controller) {
        var pattern = controller.assemblyPattern(controller.boundMachine().orElseThrow());
        for (var entry : pattern.pattern().entrySet()) {
            if (entry.getKey().equals(BlockPos.ZERO)) continue;
            var target = entry.getValue().preferredState().orElseThrow();
            var current = helper.getLevel().getBlockState(controller.getBlockPos().offset(entry.getKey()));
            helper.assertTrue(target.getFluidState().isSource()
                            ? current.is(Blocks.WATER) && current.getFluidState().isSource() : current.isAir(),
                    "Only source targets are built; explicit flowing targets remain empty");
        }
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
        MultiblockAssemblyService.Result result = MultiblockAssemblyService.demolish(player, controller, 1, stack -> true);

        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Demolish succeeds in service mode");
        helper.assertTrue(helper.getLevel().getBlockState(removedPos).isAir(), "Matching block is removed");
        helper.assertTrue(helper.getLevel().getBlockState(airPos).isAir(), "Existing air stays air");
        helper.assertTrue(helper.getLevel().getBlockState(nonMatchingPos).is(nonMatchingBlock), "Non-matching block is preserved");
        helper.assertTrue(helper.getLevel().getBlockState(cappedMatchingPos).is(ModBlocks.CASING.get()), "Cap preserves later matching blocks");
        helper.assertTrue(result.changedBlocks() == 1, "Demolish obeys the requested cap");
        helper.succeed();
    }

    public void demolishStopsBeforeRejectedDropAndPreservesLaterBlocks(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        BlockPos firstPos = template.get(0).pos();
        BlockPos rejectedPos = template.get(1).pos();
        BlockPos laterPos = template.get(2).pos();
        for (MultiblockAssemblyService.Placement placement : template) {
            helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
        }
        int[] accepted = {0};

        MultiblockAssemblyService.Result result = MultiblockAssemblyService.demolish(servicePlayer(helper), controller,
                template.size(), stack -> accepted[0]++ == 0);

        helper.assertTrue(result.changedBlocks() == 1, "Only the accepted first drop removes a block");
        helper.assertTrue(helper.getLevel().getBlockState(firstPos).isAir(), "First accepted block is removed");
        helper.assertTrue(!helper.getLevel().getBlockState(rejectedPos).isAir(), "Rejected drop keeps its block");
        helper.assertTrue(!helper.getLevel().getBlockState(laterPos).isAir(), "Later candidate remains after rejection");
        helper.succeed();
    }

    public void storageResolverRejectsUnavailableContainerTargets(GameTestHelper helper) {
        ServerPlayer player = servicePlayer(helper);
        TerminalData containerMode = TerminalData.DEFAULT.withInventoryMode(TerminalInventoryMode.CONTAINER);
        BlockPos unloadedPos = new BlockPos(1_000_000, 64, 1_000_000);
        BlockPos noStoragePos = new BlockPos(1, 1, 1);
        helper.setBlock(noStoragePos, Blocks.STONE.defaultBlockState());

        helper.assertTrue(StructureItemStorageResolver.resolve(player, TerminalData.DEFAULT)
                        .orElseThrow() instanceof PlayerInventoryStructureItemStorage,
                "Inventory mode resolves player inventory storage");
        helper.assertTrue(StructureItemStorageResolver.resolve(player, containerMode).isEmpty(),
                "Container mode without a binding is unavailable");
        helper.assertTrue(StructureItemStorageResolver.resolve(player, containerMode.withContainer(GlobalPos.of(
                        ResourceKey.create(Registries.DIMENSION, MMCR.id("missing_dimension")), BlockPos.ZERO))).isEmpty(),
                "Unknown container dimension is unavailable");
        helper.assertTrue(StructureItemStorageResolver.resolve(player, containerMode.withContainer(GlobalPos.of(
                        helper.getLevel().dimension(), unloadedPos))).isEmpty(),
                "Unloaded container chunk is unavailable");
        helper.assertTrue(StructureItemStorageResolver.resolve(player, containerMode.withContainer(GlobalPos.of(
                        helper.getLevel().dimension(), noStoragePos))).isEmpty(),
                "Container target without item storage capability is unavailable");
        helper.succeed();
    }

    public void blockStorageAcceptsCompleteStacksAndRollsBackPartialInsertions(GameTestHelper helper) {
        BlockPos busPos = new BlockPos(0, 1, 0);
        helper.setBlock(busPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        ItemBusBlockEntity bus = helper.getBlockEntity(busPos, ItemBusBlockEntity.class);
        TerminalData data = TerminalData.DEFAULT.withInventoryMode(TerminalInventoryMode.CONTAINER)
                .withContainer(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(busPos)));
        var storage = StructureItemStorageResolver.resolve(servicePlayer(helper), data).orElseThrow();

        helper.assertTrue(storage.sink().accept(new ItemStack(Items.STONE, 2)),
                "Block storage accepts a complete stack through its item capability");
        ResourceStorage<ItemResource> itemStorage = bus.itemStorage();
        helper.assertTrue(itemStorage.amount(0) == 2,
                "Successful block storage insertion commits to the item bus");

        try (Transaction transaction = Transaction.open(null)) {
            ItemResource existingResource = itemStorage.resource(0);
            if (existingResource != null) {
                itemStorage.extract(0, existingResource, itemStorage.amount(0), transaction);
            }
            itemStorage.insert(0, ItemResource.of(Items.STONE), 63, transaction);
            for (int slot = 1; slot < itemStorage.size(); slot++) {
                itemStorage.insert(slot, ItemResource.of(Items.DIRT), 64, transaction);
            }
            transaction.commit();
        }

        helper.assertTrue(!storage.sink().accept(new ItemStack(Items.STONE, 2)),
                "Block storage rejects a stack that only partially fits");
        helper.assertTrue(itemStorage.resource(0) != null && itemStorage.resource(0).is(Items.STONE)
                        && itemStorage.amount(0) == 63,
                "Rejected block storage insertion rolls back the partial first-slot insertion");
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

            MultiblockAssemblyService.Result result = MultiblockAssemblyService.demolish(player, controller, 16, stack -> true);

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
        helper.assertTrue(!new PlayerInventoryStructureItemSource(player).extractAll(List.of(new ItemStack(ModBlocks.CASING.get()))),
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
        controller.setStructureScanBatchesForTesting(ServerConfig.DEFAULT_STRUCTURE_SCAN_BATCHES);
        controller.setScanBatchTrackingForTesting(true);
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
            helper.assertTrue(!controller.scanBatchesPerTickForTesting().isEmpty(), "Explicitly enabled scan tracking records batches");
            helper.assertTrue(controller.scanBatchCountForTesting() > 0,
                    "Structure scanning starts after the build completes");
            helper.assertTrue(countPlacedStructureBlocks(helper, template) == expectedCount,
                    "Final placed structure block count is unchanged by duplicate submission");
            helper.assertTrue(controller.structureSnapshot().formed(), "Controller forms after the build completes");
            helper.succeed();
        });
    }

    public void fallingBuildBlockInvalidatesFormedStructure(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 3, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("falling_block_structure")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("falling_block_structure")));
        controller.setBuildBlocksPerTickForTesting(1);
        controller.setStructureCheckIntervalForTesting(1);
        BlockPos sandPos = MultiblockAssemblyService.createTemplatePlacements(controller.getBlockPos(),
                controller.assemblyPattern(controller.boundMachine().orElseThrow())).getFirst().pos();
        helper.getLevel().setBlock(sandPos.below(), Blocks.STONE.defaultBlockState(), 3);

        MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(servicePlayer(helper), controller, true);
        helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS, "Falling-block structure build is accepted");

        helper.runAtTickTime(1, () -> helper.assertTrue(helper.getLevel().getBlockState(sandPos.below()).is(Blocks.STONE),
                "Temporary support remains before the automatic build tick: sand="
                        + helper.getLevel().getBlockState(sandPos) + ", support="
                        + helper.getLevel().getBlockState(sandPos.below())));
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(),
                    "Automatically placed sand forms the structure before falling: sand="
                            + helper.getLevel().getBlockState(sandPos) + ", support="
                            + helper.getLevel().getBlockState(sandPos.below()));
            helper.assertTrue(helper.getLevel().getBlockState(sandPos).is(Blocks.SAND),
                    "Automatically placed sand is present before falling");
            helper.getLevel().removeBlock(sandPos.below(), false);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(sandPos).isAir(),
                    "Automatically placed sand falls after its support is removed");
            helper.assertTrue(!controller.structureSnapshot().formed(),
                    "A falling automatically placed block invalidates the formed structure");
            helper.succeed();
        });
    }

    public void completedBuildRequestsStructureDiagnostic(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        controller.setStructureCheckIntervalForTesting(1);
        controller.setStructureScanBatchesForTesting(ServerConfig.DEFAULT_STRUCTURE_SCAN_BATCHES);
        List<MultiblockAssemblyService.Placement> template = template(controller);
        helper.getLevel().setBlock(template.getFirst().pos(), Blocks.COBBLESTONE.defaultBlockState(), 3);

        int[] diagnostics = {0};
        controller.setStructureDiagnosticCallbackForTesting(() -> diagnostics[0]++);
        MultiblockAssemblyService.build(servicePlayer(helper), controller, true);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(diagnostics[0] == 1,
                    "Completed terminal builds request a structure diagnostic for the selected stage: deliveries="
                            + diagnostics[0]);
            helper.succeed();
        });
    }

    public void buildCompletionVerifiesOnlySelectedStageForMultiStageMachine(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("expandable_structure_stages")));
        controller.setStructureCheckIntervalForTesting(1);
        controller.setStructureScanBatchesForTesting(ServerConfig.DEFAULT_STRUCTURE_SCAN_BATCHES);
        controller.setBuildBlocksPerTickForTesting(1);
        Machine machine = controller.boundMachine().orElseThrow();
        List<MultiblockAssemblyService.Placement> stage1Template = MultiblockAssemblyService.createTemplatePlacements(
                controller.getBlockPos(), controller.assemblyPattern(machine, 1));
        BlockPos stage2OnlyPos = stageOnlyPos(controller, machine, 2, stage1Template);
        int[] diagnostics = {0};
        controller.setStructureDiagnosticCallbackForTesting(() -> diagnostics[0]++);

        MultiblockAssemblyService.build(servicePlayer(helper), controller, 1,
                new PlayerInventoryStructureItemSource(servicePlayer(helper)), true, Map.of());

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(diagnostics[0] == 0,
                    "After a stage-1 build completes successfully, no higher-stage diagnostic is sent: deliveries="
                            + diagnostics[0]);
            helper.assertTrue(helper.getLevel().getBlockState(stage2OnlyPos).isAir(),
                    "Stage-2-only positions remain untouched for a stage-1 build");
            helper.succeed();
        });
    }

    public void buildCompletionOmitsStageFormedMessageForSingleStageMachine(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        controller.setStructureCheckIntervalForTesting(1);
        controller.setBuildBlocksPerTickForTesting(ServerConfig.DEFAULT_BUILD_BLOCKS_PER_TICK);
        List<Component> sentMessages = new ArrayList<>();
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-terminal-gametest-singlestage".getBytes(StandardCharsets.UTF_8)),
                        "mmcr-terminal-singlestage"),
                ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(Component message) {
                sentMessages.add(message);
            }
        };
        MultiblockAssemblyService.build(player, controller, 1,
                new PlayerInventoryStructureItemSource(player), true, Map.of());

        helper.runAtTickTime(4, () -> {
            boolean stageFormedSent = sentMessages.stream()
                    .anyMatch(component -> component.getString().contains("等级")
                            || component.getString().toLowerCase().contains("stage"));
            helper.assertTrue(!stageFormedSent,
                    "Single-stage build completion never broadcasts a stage-formed message: messages=" + sentMessages);
            helper.succeed();
        });
    }

    public void verifyStageSuppressesFormMessageForSingleStageMachine(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        List<MultiblockAssemblyService.Placement> template = template(controller);
        for (MultiblockAssemblyService.Placement placement : template) {
            helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
        }
        List<Component> sentMessages = new ArrayList<>();
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-terminal-gametest-singlestage-verify".getBytes(StandardCharsets.UTF_8)),
                        "mmcr-terminal-singlestage-verify"),
                ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(Component message) {
                sentMessages.add(message);
            }
        };
        helper.runAtTickTime(2, () -> {
            controller.verifyStage(player, 1);
            helper.runAtTickTime(1, () -> {
                boolean stageFormedSent = sentMessages.stream()
                        .anyMatch(component -> component.getString().contains("等级")
                                || component.getString().toLowerCase().contains("stage"));
                helper.assertTrue(!stageFormedSent,
                        "Single-stage verifyStage emits no stage-formed chatter: messages=" + sentMessages);
                helper.succeed();
            });
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
        controller.setStructureCheckIntervalForTesting(1);
        for (MultiblockAssemblyService.Placement placement : template) {
            helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
        }
        helper.runAtTickTime(1, () -> controller.serverTick());
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Structure forms after the first check");
            int matcherBeforeMutation = controller.matcherInvocationCountForTesting();
            helper.getLevel().setBlock(changedPos, Blocks.AIR.defaultBlockState(), 3);
            controller.onStructureBlockChanged(changedPos);
            helper.runAtTickTime(4, () -> controller.serverTick());
            helper.runAtTickTime(20, () -> {
                helper.assertTrue(controller.matcherInvocationCountForTesting() > matcherBeforeMutation,
                        "A block change drives a fresh full match recheck");
                helper.getLevel().setBlock(changedPos, changedPlacement.state(), 3);
                controller.onStructureBlockChanged(changedPos);
                helper.runAtTickTime(22, () -> controller.serverTick());
                helper.runAtTickTime(40, () -> {
                    helper.assertTrue(controller.structureSnapshot().formed(),
                            "Restoring the block lets the structure form again on the next recheck");
                    helper.succeed();
                });
            });
        });
    }

    public void smallStructureDiagnosticIsDeliveredAfterNextScan(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("expandable_structure_stages")));
        controller.setStructureCheckIntervalForTesting(1);
        controller.setStructureScanBatchesForTesting(ServerConfig.DEFAULT_STRUCTURE_SCAN_BATCHES);
        ServerPlayer player = servicePlayer(helper);
        int[] diagnostics = {0};
        controller.setStructureDiagnosticCallbackForTesting(() -> diagnostics[0]++);

        controller.requestImmediateStructureCheck(player);
        helper.assertTrue(controller.isStructureDiagnosticRequestedForTesting(),
                "Shift-right-click diagnostic request is recorded before the ticker runs");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(diagnostics[0] == 1,
                        "Small-structure mismatch diagnostic feedback is delivered once: deliveries="
                                + diagnostics[0] + " batches=" + controller.scanBatchCountForTesting()
                                + " requested=" + controller.isStructureDiagnosticRequestedForTesting()
                                + " pendingInvalidation=" + controller.isPendingStructureInvalidationForTesting()
                                + " cursor=" + controller.structureScanCursorForTesting()))
                .thenExecute(() -> {
                    helper.assertTrue(!controller.isStructureDiagnosticRequestedForTesting(),
                            "Small-structure mismatch diagnostic is delivered by the scan result: requested="
                                    + controller.isStructureDiagnosticRequestedForTesting()
                                    + " deliveries=" + diagnostics[0]
                                    + " batches=" + controller.scanBatchCountForTesting());
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
        helper.succeedWhen(() -> {
            long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(helper.absolutePos(controllerPos)).inflate(1))
                    .stream()
                    .filter(entity -> entity.getItem().is(ModBlocks.CASING.get().asItem()))
                    .mapToLong(entity -> entity.getItem().getCount())
                    .sum();
            helper.assertTrue(dropped == template.size(),
                    "A disconnected builder receives every unplaced reserved material as drops");
        });
    }

    public void buildAlreadyFormedMultiStageReportsSpecificStage(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("expandable_structure_stages")).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("expandable_structure_stages")));
        controller.setStructureCheckIntervalForTesting(1);
        Machine machine = controller.boundMachine().orElseThrow();
        List<MultiblockAssemblyService.Placement> stage1Template = MultiblockAssemblyService.createTemplatePlacements(
                controller.getBlockPos(), controller.assemblyPattern(machine, 1));
        for (MultiblockAssemblyService.Placement placement : stage1Template) {
            helper.getLevel().setBlock(placement.pos(), placement.state(), 3);
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(),
                        "Stage 1 structure forms before terminal build"))
                .thenExecute(() -> {
                    helper.assertTrue(controller.structureSnapshot().matchedStage() == 1,
                            "Controller matched stage 1 before terminal build");

                    MultiblockAssemblyService.Result result = MultiblockAssemblyService.build(servicePlayer(helper), controller,
                            1, new PlayerInventoryStructureItemSource(servicePlayer(helper)), true, Map.of());

                    helper.assertTrue(result.interactionResult() == InteractionResult.SUCCESS,
                            "Build on an already-formed stage still reports success");
                    helper.assertTrue(result.message().key().equals("message.mmcr.terminal.build.none.staged"),
                            "Already-formed multi-stage build reports a stage-specific message");
                    helper.assertTrue(result.message().args().length == 1 && result.message().args()[0].equals(1),
                            "The stage-specific message carries the requested stage number");
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

    private static PktTerminalStatePayload lastState(List<Packet<?>> packets) {
        return packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktTerminalStatePayload)
                .map(packet -> (PktTerminalStatePayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .reduce((first, second) -> second).orElseThrow();
    }

    private static List<Packet<?>> capturePackets(GameTestHelper helper, ServerPlayer player) {
        List<Packet<?>> packets = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new Connection(PacketFlow.CLIENTBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet) {
                packets.add(packet);
            }
        };
        return packets;
    }

    private static ServerPlayer servicePlayer(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-terminal-gametest".getBytes(StandardCharsets.UTF_8)), "mmcr-terminal"),
                ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(Component message) {
                // The isolated GameTest player has no packet connection.
            }
        };
    }

}
