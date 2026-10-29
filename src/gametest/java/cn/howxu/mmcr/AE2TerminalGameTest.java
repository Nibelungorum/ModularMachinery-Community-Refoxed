package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.WirelessAccessPointMenu;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import cn.howxu.mmcr.internal.assembly.StructureItemStorage;
import cn.howxu.mmcr.internal.assembly.StructureItemStorageResolver;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.item.TerminalInventoryMode;
import cn.howxu.mmcr.internal.item.TerminalService;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity.RemovalReason;

/**
 * Verifies terminal material transfers through an AE2 wireless access point.
 *
 * @author howxu <dev@howxu.cn>
 */
public class AE2TerminalGameTest {
    public void nativeMenuLinksAndRelinksWithoutChangingSettings(GameTestHelper helper) {
        BlockPos firstPos = new BlockPos(0, 0, 0);
        BlockPos secondPos = new BlockPos(2, 0, 0);
        helper.setBlock(firstPos, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        helper.setBlock(secondPos, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        var first = helper.getBlockEntity(firstPos, WirelessAccessPointBlockEntity.class);
        var second = helper.getBlockEntity(secondPos, WirelessAccessPointBlockEntity.class);
        ServerPlayer player = player(helper);
        GlobalPos controller = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 0, 2)));
        for (TerminalInventoryMode mode : TerminalInventoryMode.values()) {
            TerminalData original = TerminalData.DEFAULT.withInventoryMode(mode)
                    .withController(controller).withContainer(controller)
                    .withSelectedLevel(MMCR.id("test_level_type"), MMCR.id("test_level"))
                    .withStage(2).withPreview(true, -1);
            ItemStack terminal = new ItemStack(ModItems.TERMINAL.get());
            terminal.set(ModDataComponents.TERMINAL_DATA.get(), original);
            terminal.set(DataComponents.CUSTOM_NAME, Component.literal("Named test terminal"));
            ItemStack linked = linkViaMenu(helper, player, first, terminal);
            helper.assertTrue(TerminalData.from(linked).equals(original.withAe2AccessPoint(first.getGlobalPos())),
                    "Native binding preserves all other settings in " + mode);
            helper.assertTrue(TerminalData.from(terminal).equals(original),
                    "Native menu links its output copy rather than mutating the original stack");
            linked = linkViaMenu(helper, player, second, linked);
            helper.assertTrue(TerminalData.from(linked).equals(original.withAe2AccessPoint(second.getGlobalPos())),
                    "Native rebinding replaces only the access point in " + mode);
            GridLinkables.get(linked.getItem()).unlink(linked);
            helper.assertTrue(TerminalData.from(linked).equals(original),
                    "Native unlink clears only the ME binding in " + mode);
        }
        var menu = new WirelessAccessPointMenu(1, player.getInventory(), first);
        helper.assertFalse(menu.getSlots(SlotSemantics.MACHINE_INPUT).getFirst().mayPlace(new ItemStack(Items.STONE)),
                "Native linking slot still rejects non-linkable items");
        player.setRemoved(RemovalReason.DISCARDED);
        helper.succeed();
    }

    private static ItemStack linkViaMenu(GameTestHelper helper, ServerPlayer player,
                                        WirelessAccessPointBlockEntity accessPoint, ItemStack terminal) {
        var menu = new WirelessAccessPointMenu(1, player.getInventory(), accessPoint);
        var input = menu.getSlots(SlotSemantics.MACHINE_INPUT).getFirst();
        var output = menu.getSlots(SlotSemantics.MACHINE_OUTPUT).getFirst();
        helper.assertTrue(input.mayPlace(terminal), "Native access point accepts the MMCR terminal");
        input.set(terminal);
        helper.assertFalse(input.hasItem(), "Native binding consumes the input slot");
        helper.assertTrue(output.hasItem(), "Native binding moves the linked terminal to the output slot");
        ItemStack linked = output.remove(1);
        helper.assertFalse(output.hasItem(), "Taking the linked terminal clears the output slot");
        ItemStack expected = terminal.copy();
        expected.set(ModDataComponents.TERMINAL_DATA.get(),
                TerminalData.from(terminal).withAe2AccessPoint(accessPoint.getGlobalPos()));
        helper.assertTrue(ItemStack.isSameItemSameComponents(linked, expected),
                "Native binding preserves every item component except the ME connection");
        return linked;
    }

    public void boundAccessPointTransfersBuildAndDemolishMaterials(GameTestHelper helper) {
        BlockPos accessPointPos = new BlockPos(0, 0, 0);
        BlockPos chestPos = new BlockPos(2, 0, 0);
        BlockPos energyPos = new BlockPos(0, 0, 2);
        helper.setBlock(accessPointPos, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        helper.runAtTickTime(2, () -> {
            WirelessAccessPointBlockEntity accessPoint = helper.getBlockEntity(accessPointPos,
                    WirelessAccessPointBlockEntity.class);
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos, MEChestBlockEntity.class);
            CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos,
                    CreativeEnergyCellBlockEntity.class);
            chest.setCell(AEItems.ITEM_CELL_1K.stack());
            helper.assertTrue(accessPoint.getMainNode().getNode() != null && chest.getMainNode().getNode() != null
                            && energy.getMainNode().getNode() != null,
                    "Wireless access point, ME chest, and energy cell initialize their grid nodes");
            GridHelper.createConnection(accessPoint.getMainNode().getNode(), chest.getMainNode().getNode());
            GridHelper.createConnection(accessPoint.getMainNode().getNode(), energy.getMainNode().getNode());
        });

        helper.runAtTickTime(4, () -> {
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos, MEChestBlockEntity.class);
            chest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT), 1L, Actionable.MODULATE,
                    IActionSource.empty());
            ServerPlayer player = player(helper);
            GlobalPos accessPoint = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(accessPointPos));
            ItemStack containerTerminal = new ItemStack(ModItems.TERMINAL.get());
            containerTerminal.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.DEFAULT
                    .withInventoryMode(TerminalInventoryMode.CONTAINER));
            player.setItemInHand(InteractionHand.MAIN_HAND, containerTerminal);
            helper.assertFalse(TerminalService.bindContainer(player, containerTerminal, accessPoint).accepted(),
                    "Wireless access points cannot be bound as ordinary containers");
            ItemStack terminal = new ItemStack(ModItems.TERMINAL.get());
            terminal.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.DEFAULT
                    .withInventoryMode(TerminalInventoryMode.AE2));
            player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
            helper.assertTrue(StructureItemStorageResolver.resolve(player,
                            TerminalData.from(terminal).withAe2AccessPoint(accessPoint)).isPresent(),
                    "The powered ME network is usable before testing the removed binding interaction");
            player.setPose(Pose.CROUCHING);
            TerminalData original = TerminalData.from(terminal);
            var hit = new BlockHitResult(accessPoint.pos().getCenter(), Direction.UP, accessPoint.pos(), false);
            InteractionResult result = terminal.getItem().useOn(new UseOnContext(
                    helper.getLevel(), player, InteractionHand.MAIN_HAND, terminal, hit));
            helper.assertTrue(result == InteractionResult.PASS && TerminalData.from(terminal).equals(original),
                    "Sneak-use on a usable access point no longer binds the ME connection");
            player.setPose(Pose.STANDING);

            terminal = linkViaMenu(helper, player,
                    helper.getBlockEntity(accessPointPos, WirelessAccessPointBlockEntity.class), terminal);
            player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
            StructureItemStorage storage = StructureItemStorageResolver.resolve(player, TerminalData.from(terminal))
                    .orElseThrow(() -> new AssertionError("Bound ME network resolves to terminal storage"));
            helper.assertTrue(storage.source().extractAll(List.of(new ItemStack(Items.IRON_INGOT))),
                    "Terminal build source extracts the requested material from the ME network");
            helper.assertTrue(storage.sink().accept(new ItemStack(Items.STONE)),
                    "Terminal demolish sink returns a complete material stack to the ME network");
            helper.assertTrue(chest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT), 1L,
                            Actionable.SIMULATE, IActionSource.empty()) == 0L,
                    "Build extraction removes the material from the ME network");
            helper.assertTrue(chest.getInventory().extract(AEItemKey.of(Items.STONE), 1L,
                            Actionable.SIMULATE, IActionSource.empty()) == 1L,
                    "Demolish insertion returns the material to the ME network");
            helper.succeed();
        });
    }

    public void boundAccessPointBuildsFluidSources(GameTestHelper helper) {
        BlockPos accessPointPos = new BlockPos(0, 0, 0);
        BlockPos chestPos = new BlockPos(2, 0, 0);
        BlockPos energyPos = new BlockPos(0, 0, 2);
        helper.setBlock(accessPointPos, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        MachineControllerBlockEntity controller = TerminalAssemblyGameTest.fluidController(helper);
        helper.runAtTickTime(2, () -> {
            var accessPoint = helper.getBlockEntity(accessPointPos, WirelessAccessPointBlockEntity.class);
            var chest = helper.getBlockEntity(chestPos, MEChestBlockEntity.class);
            var energy = helper.getBlockEntity(energyPos, CreativeEnergyCellBlockEntity.class);
            chest.setCell(AEItems.FLUID_CELL_1K.stack());
            GridHelper.createConnection(accessPoint.getMainNode().getNode(), chest.getMainNode().getNode());
            GridHelper.createConnection(accessPoint.getMainNode().getNode(), energy.getMainNode().getNode());
        });
        helper.runAtTickTime(4, () -> {
            var chest = helper.getBlockEntity(chestPos, MEChestBlockEntity.class);
            ServerPlayer player = player(helper);
            player.setPos(controller.getBlockPos().getCenter());
            TerminalData data = TerminalData.DEFAULT.withInventoryMode(TerminalInventoryMode.AE2)
                    .withAe2AccessPoint(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(accessPointPos)));
            var storage = StructureItemStorageResolver.resolve(player, data).orElseThrow();
            var source = storage.source();
            AEFluidKey water = AEFluidKey.of(Fluids.WATER);
            long bucket = AEFluidKey.AMOUNT_BLOCK;
            chest.getInventory().insert(water, bucket + bucket / 2, Actionable.MODULATE, IActionSource.empty());

            var missing = MultiblockAssemblyService.build(player, controller, 1, source, false);
            helper.assertTrue(missing.interactionResult() == InteractionResult.FAIL && !controller.hasActiveBuildTask(),
                    "A batch requiring two sources rejects less than two buckets");
            helper.assertTrue(chest.getInventory().extract(water, Long.MAX_VALUE, Actionable.SIMULATE,
                    IActionSource.empty()) == bucket + bucket / 2, "Rejected batch consumes no fluid");
            chest.getInventory().insert(water, bucket, Actionable.MODULATE, IActionSource.empty());
            var accepted = MultiblockAssemblyService.build(player, controller, 1, source, false);
            helper.assertTrue(accepted.interactionResult() == InteractionResult.SUCCESS && accepted.changedBlocks() == 2,
                    "Fluid-only ME batch includes both sources and skips the explicit flowing target");
            helper.assertTrue(chest.getInventory().extract(water, Long.MAX_VALUE, Actionable.SIMULATE,
                    IActionSource.empty()) == 2 * bucket + bucket / 2, "Accepted batch does not pre-extract fluids");
            controller.serverTick();
            TerminalAssemblyGameTest.assertFluidTargets(helper, controller);
            helper.assertTrue(chest.getInventory().extract(water, Long.MAX_VALUE, Actionable.SIMULATE,
                    IActionSource.empty()) == bucket / 2, "Two placed source blocks consume exactly two buckets");

            var inventoryRemoval = MultiblockAssemblyService.demolish(player, controller, 1, 16, ignored -> true, false);
            helper.assertTrue(inventoryRemoval.changedBlocks() == 0, "Ordinary inventory demolition preserves source fluids");
            var pattern = controller.assemblyPattern(controller.boundMachine().orElseThrow());
            BlockPos flowingTarget = pattern.pattern().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(BlockPos.ZERO)
                            && !entry.getValue().preferredState().orElseThrow().getFluidState().isSource())
                    .map(entry -> controller.getBlockPos().offset(entry.getKey())).findFirst().orElseThrow();
            helper.getLevel().setBlock(flowingTarget, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 2);
            var recovered = MultiblockAssemblyService.demolish(player, controller, 1, 16, storage.sink(), false);
            helper.assertTrue(recovered.changedBlocks() == 2, "ME demolition recovers only the two source blocks");
            helper.assertTrue(helper.getLevel().getBlockState(flowingTarget).is(Blocks.WATER)
                            && !helper.getLevel().getBlockState(flowingTarget).getFluidState().isSource(),
                    "ME demolition leaves non-source fluid untouched");
            for (var placement : MultiblockAssemblyService.createTemplatePlacements(controller.getBlockPos(), pattern)) {
                helper.assertTrue(helper.getLevel().getBlockState(placement.pos()).isAir(), "Recovered source block is removed");
            }
            helper.assertTrue(chest.getInventory().extract(water, Long.MAX_VALUE, Actionable.SIMULATE,
                    IActionSource.empty()) == 2 * bucket + bucket / 2, "Every removed source returns one bucket to ME storage");
            helper.getLevel().removeBlock(helper.absolutePos(accessPointPos), false);
            helper.assertTrue(source.copyFluidSources().isEmpty()
                            && !source.placeFluidSource(Fluids.WATER, () -> { throw new AssertionError("Disconnected source cannot place"); }),
                    "An existing task source rechecks its bound access point instead of using a stale network");
            helper.assertFalse(storage.sink().recoverFluidSource(Fluids.WATER,
                            () -> { throw new AssertionError("Disconnected storage cannot remove a source"); }),
                    "Disconnected recovery leaves the source block untouched");
            player.setRemoved(RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-ae2-terminal".getBytes(StandardCharsets.UTF_8)),
                        "mmcr-ae2-terminal"),
                ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(Component message) {
                // The isolated GameTest player has no packet connection.
            }
        };
    }
}
