package cn.howxu.mmcr;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.menu.SlotSemantics;
import appeng.menu.AEBaseMenu;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2InterfaceMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import appeng.helpers.InventoryAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedAEMenuTypes;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedInterfaceMenu;
import com.glodblock.github.extendedae.client.ExSemantics;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * End-to-end GameTest coverage for the AE2 stocking input interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public class AE2StockingInterfaceGameTest {
    private static final long ITEM_AMOUNT = 128L;
    private static final long FLUID_AMOUNT = 5_000L;
    private static final long OVERSIZE_TRANSFER_AMOUNT = 20_000L;

    public void stockingInterfaceReadsAndWatchesNetworkStorage(GameTestHelper helper) {
        helper.assertTrue(AE2Bridge.get().available(),
                "AE2 must be loaded for this integration test");

        BlockPos portPos = new BlockPos(0, 0, 0);
        BlockPos itemChestPos = new BlockPos(3, 0, 0);
        BlockPos fluidChestPos = new BlockPos(3, 0, 2);
        BlockPos energyPos = new BlockPos(3, 0, 4);
        helper.setBlock(portPos,
                ModBlocks.BLOCKS.get("ae2_me_stocking_input_interface").get().defaultBlockState());
        helper.setBlock(itemChestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(fluidChestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        StockingInterfaceBlockEntity port = helper.getBlockEntity(portPos,
                StockingInterfaceBlockEntity.class);
        MEChestBlockEntity itemChest = helper.getBlockEntity(itemChestPos, MEChestBlockEntity.class);
        MEChestBlockEntity fluidChest = helper.getBlockEntity(fluidChestPos, MEChestBlockEntity.class);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos,
                CreativeEnergyCellBlockEntity.class);

        itemChest.setCell(AEItems.ITEM_CELL_1K.stack());
        fluidChest.setCell(AEItems.FLUID_CELL_1K.stack());
        port.getInterfaceLogic().getConfig().setStack(0,
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 64L));
        port.getInterfaceLogic().getConfig().setStack(1,
                new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L));
        List<IGridConnection> connections = new ArrayList<>();

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(port.getMainNode().getNode() != null,
                    "Stocking interface has initialized its AE2 grid node");
            helper.assertTrue(itemChest.getMainNode().getNode() != null,
                    "Item ME Chest has initialized its AE2 grid node");
            helper.assertTrue(fluidChest.getMainNode().getNode() != null,
                    "Fluid ME Chest has initialized its AE2 grid node");
            helper.assertTrue(energy.getMainNode().getNode() != null,
                    "Creative energy cell has initialized its AE2 grid node");
            connections.add(GridHelper.createConnection(port.getMainNode().getNode(), itemChest.getMainNode().getNode()));
            connections.add(GridHelper.createConnection(port.getMainNode().getNode(), fluidChest.getMainNode().getNode()));
            connections.add(GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode()));
        });

        helper.runAtTickTime(10, () -> {
            MEStorage itemStorage = itemChest.getInventory();
            MEStorage fluidStorage = fluidChest.getInventory();
            helper.assertTrue(itemStorage.insert(AEItemKey.of(Items.IRON_INGOT),
                            ITEM_AMOUNT, Actionable.MODULATE, IActionSource.empty()) == ITEM_AMOUNT,
                    "Item test storage accepts the configured item");
            helper.assertTrue(fluidStorage.insert(AEFluidKey.of(Fluids.WATER),
                            FLUID_AMOUNT, Actionable.MODULATE, IActionSource.empty()) == FLUID_AMOUNT,
                    "Fluid test storage accepts the configured fluid");
        });

        helper.runAtTickTime(11, () -> {
            BlockState portState = helper.getLevel().getBlockState(helper.absolutePos(portPos));
            IInWorldGridNodeHost gridHost = helper.getLevel().getCapability(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, helper.absolutePos(portPos), null);
            helper.assertTrue(gridHost == port,
                    "AE2 IN_WORLD_GRID_NODE_HOST capability is exposed for stocking");
            helper.assertTrue(helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Stocking interface does not expose GENERIC_INTERNAL_INV");
            helper.assertTrue(helper.getLevel().getCapability(AECapabilities.ME_STORAGE,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Stocking interface does not expose ME_STORAGE");
            helper.assertTrue(helper.getLevel().getCapability(ModCapabilities.ITEM_BLOCK,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Stocking interface does not expose an external item handler");
            helper.assertTrue(helper.getLevel().getCapability(ModCapabilities.FLUID_BLOCK,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Stocking interface does not expose an external fluid handler");

            ServerPlayer menuPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.nameUUIDFromBytes(
                            "mmcr-ae2-stocking-menu-test".getBytes(StandardCharsets.UTF_8)),
                            "mmcr-ae2-stocking-menu"),
                    ClientInformation.createDefault());
            AE2InterfaceMenu menu = new AE2InterfaceMenu(AE2MenuTypes.INTERFACE, 0,
                    menuPlayer.getInventory(), port);
            var displaySlot = menu.getSlots(SlotSemantics.STORAGE).getFirst();
            helper.assertTrue(!displaySlot.getItem().isEmpty(),
                    "Stocking interface exposes a display fake stack for the configured item");
            GenericStack displayedItem = GenericStack.unwrapItemStack(displaySlot.getItem());
            helper.assertTrue(displayedItem != null && displayedItem.amount() == ITEM_AMOUNT,
                    "Stocking item display preserves the complete network amount");
            GenericStack displayedFluid = GenericStack.unwrapItemStack(
                    menu.getSlots(SlotSemantics.STORAGE).get(1).getItem());
            helper.assertTrue(displayedFluid != null && displayedFluid.amount() == FLUID_AMOUNT,
                    "Stocking fluid display preserves the complete network amount");
            helper.assertFalse(displaySlot.mayPickup(menuPlayer),
                    "Stocking display fake stack cannot be picked up");
            helper.assertFalse(displaySlot.mayPlace(Items.IRON_INGOT.getDefaultInstance()),
                    "Stocking display fake stack cannot accept external insertion");
            var mirrorBefore = port.getInterfaceLogic().getStorage().getStack(0);
            var fluidMirrorBefore = port.getInterfaceLogic().getStorage().getStack(1);
            menu.clicked(displaySlot.index, 0, ContainerInput.PICKUP, menuPlayer);
            menu.quickMoveStack(menuPlayer, displaySlot.index);
            helper.assertTrue(menu.getCarried().isEmpty(), "Stocking pickup and shift actions create no cursor resources");
            helper.assertTrue(displaySlot.remove(1).isEmpty(), "Direct removal cannot materialize the network mirror");
            var fluidSlot = menu.getSlots(SlotSemantics.STORAGE).get(1);
            menu.setCarried(Items.BUCKET.getDefaultInstance());
            menu.doAction(menuPlayer, InventoryAction.FILL_ITEM, fluidSlot.index, 0);
            menu.doAction(menuPlayer, InventoryAction.FILL_ENTIRE_ITEM, fluidSlot.index, 0);
            helper.assertTrue(ItemStack.matches(menu.getCarried(), Items.BUCKET.getDefaultInstance()),
                    "Mirror cannot fill or duplicate a held bucket");
            menu.setCarried(Items.WATER_BUCKET.getDefaultInstance());
            menu.doAction(menuPlayer, InventoryAction.EMPTY_ITEM, fluidSlot.index, 0);
            menu.doAction(menuPlayer, InventoryAction.EMPTY_ENTIRE_ITEM, fluidSlot.index, 0);
            helper.assertTrue(ItemStack.matches(menu.getCarried(), Items.WATER_BUCKET.getDefaultInstance()),
                    "Mirror cannot drain a held bucket");
            helper.assertTrue(Objects.equals(mirrorBefore, port.getInterfaceLogic().getStorage().getStack(0))
                            && Objects.equals(fluidMirrorBefore, port.getInterfaceLogic().getStorage().getStack(1))
                            && menuPlayer.getInventory().getNonEquipmentItems().stream().allMatch(ItemStack::isEmpty),
                    "GUI operations preserve the mirror and create no player resources");
            helper.assertTrue(itemChest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE,
                            Actionable.SIMULATE, IActionSource.empty()) == ITEM_AMOUNT
                            && fluidChest.getInventory().extract(AEFluidKey.of(Fluids.WATER), Long.MAX_VALUE,
                            Actionable.SIMULATE, IActionSource.empty()) == FLUID_AMOUNT,
                    "GUI operations leave real item and fluid network inventories unchanged");
            menu.setCarried(ItemStack.EMPTY);

            CapabilitySnapshot snapshot = port.capabilitySnapshot();
            helper.assertTrue(snapshot.capabilities().size() == 2,
                    "Stocking interface exposes item and fluid MMCR capabilities");
            for (MachineCapability capability : snapshot.capabilities()) {
                helper.assertTrue(capability.type().id().equals(PortFamilyIds.ITEM)
                                || capability.type().id().equals(PortFamilyIds.FLUID),
                        "Stocking capability has an item or fluid family");
                helper.assertTrue(capability.facet(TransferFacet.class).isEmpty(),
                        "Stocking capability does not expose TransferFacet");
            }
            MachineCapability itemCapability = snapshot.capabilities().stream()
                    .filter(capability -> capability.type().id().equals(PortFamilyIds.ITEM))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Stocking item capability is missing"));
            MachineCapability fluidCapability = snapshot.capabilities().stream()
                    .filter(capability -> capability.type().id().equals(PortFamilyIds.FLUID))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Stocking fluid capability is missing"));
            helper.assertTrue(itemCapability.type().id().equals(PortFamilyIds.ITEM),
                    "Stocking item capability is explicitly bound to the item family");
            helper.assertTrue(fluidCapability.type().id().equals(PortFamilyIds.FLUID),
                    "Stocking fluid capability is explicitly bound to the fluid family");
            helper.assertTrue(port.getInterfaceLogic().getConfig().getAmount(0) == 1L,
                    "Stocking item marker is normalized to one resource");
            helper.assertTrue(port.getInterfaceLogic().getConfig().getAmount(1) == 1_000L,
                    "Stocking fluid marker is normalized to one bucket");
            helper.assertTrue(Objects.equals(port.getInterfaceLogic().getConfig().getKey(0), AEItemKey.of(Items.IRON_INGOT)),
                    "Stocking item marker key is iron ingot");
            helper.assertTrue(Objects.equals(port.getInterfaceLogic().getConfig().getKey(1), AEFluidKey.of(Fluids.WATER)),
                    "Stocking fluid marker key is water");
            helper.assertTrue(Objects.requireNonNull(port.getInterfaceLogic().getStorage().getStack(0)).amount() == ITEM_AMOUNT,
                    "Item display mirror follows the watcher amount");
            helper.assertTrue(Objects.requireNonNull(port.getInterfaceLogic().getStorage().getStack(1)).amount() == FLUID_AMOUNT,
                    "Fluid display mirror follows the watcher amount");

            CapabilityRequests.ResourceRequest<ItemResource> itemRequest = new CapabilityRequests.ResourceRequest<>(
                    itemCapability.type(), IOType.INPUT, 1L,
                    List.of(new CapabilityRequests.ResourceAction<>(
                            0, ItemResource.of(Items.IRON_INGOT), 3L, false)));
            CapabilityRequests.ResourceRequest<FluidResource> fluidRequest = new CapabilityRequests.ResourceRequest<>(
                    fluidCapability.type(), IOType.INPUT, 1L,
                    List.of(new CapabilityRequests.ResourceAction<>(
                            1, FluidResource.of(Fluids.WATER), 1_000L, false)));
            CapabilityOperation itemOperation = itemCapability.prepare(itemRequest);
            CapabilityOperation fluidOperation = fluidCapability.prepare(fluidRequest);
            MEStorage itemNetwork = itemChest.getInventory();
            MEStorage fluidNetwork = fluidChest.getInventory();
            try (Transaction transaction = Transaction.openRoot()) {
                CapabilityResult itemResult = itemOperation.commit(transaction);
                CapabilityResult fluidResult = fluidOperation.commit(transaction);
                helper.assertTrue(itemResult.success(),
                        "Stocking item capability operation commits successfully");
                helper.assertTrue(fluidResult.success(),
                        "Stocking fluid capability operation commits successfully");
                helper.assertTrue(itemNetwork.extract(AEItemKey.of(Items.IRON_INGOT), ITEM_AMOUNT,
                                Actionable.SIMULATE, IActionSource.empty()) == ITEM_AMOUNT,
                        "Item MEStorage is unchanged before the root transaction commits");
                helper.assertTrue(fluidNetwork.extract(AEFluidKey.of(Fluids.WATER), FLUID_AMOUNT,
                                Actionable.SIMULATE, IActionSource.empty()) == FLUID_AMOUNT,
                        "Fluid MEStorage is unchanged before the root transaction commits");
                transaction.commit();
                helper.assertTrue(itemNetwork.extract(AEItemKey.of(Items.IRON_INGOT), ITEM_AMOUNT,
                                Actionable.SIMULATE, IActionSource.empty()) == ITEM_AMOUNT - 3L,
                        "Item MEStorage quantity is deducted after transaction commit");
                helper.assertTrue(fluidNetwork.extract(AEFluidKey.of(Fluids.WATER), FLUID_AMOUNT,
                                Actionable.SIMULATE, IActionSource.empty()) == FLUID_AMOUNT - 1_000L,
                        "Fluid MEStorage quantity is deducted after transaction commit");
            }
        });

        helper.runAtTickTime(12, () -> {
            helper.assertTrue(Objects.requireNonNull(port.getInterfaceLogic().getStorage().getStack(0)).amount() == ITEM_AMOUNT - 3L,
                    "Item watcher display updates on the next tick without a full scan");
            helper.assertTrue(Objects.requireNonNull(port.getInterfaceLogic().getStorage().getStack(1)).amount() == FLUID_AMOUNT - 1_000L,
                    "Fluid watcher display updates on the next tick without a full scan");
            var items = port.itemStorage();
            var fluids = port.fluidStorage();
            Object originalNetwork = items.reservationIdentity();
            helper.startSequence()
                    .thenExecute(() -> {
                        port.getInterfaceLogic().getConfig().setStack(0,
                                new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L));
                        port.getInterfaceLogic().getConfig().setStack(1,
                                new GenericStack(AEFluidKey.of(Fluids.LAVA), 1_000L));
                        helper.assertTrue(items == port.itemStorage() && fluids == port.fluidStorage(),
                                "Config changes rebind the existing live storage views");
                        helper.assertTrue(items.size() == 2 && fluids.size() == 2
                                        && ItemResource.of(Items.GOLD_INGOT).equals(items.resource(0))
                                        && items.resource(1) == null && fluids.resource(0) == null
                                        && FluidResource.of(Fluids.LAVA).equals(fluids.resource(1)),
                                "Config changes immediately replace cached resources and retain mixed slot indices");
                        helper.assertTrue(items.reservationIdentity() == originalNetwork
                                        && fluids.reservationIdentity() == originalNetwork,
                                "Config rebind retains the live network reservation identity");
                        helper.assertTrue(itemChest.getInventory().insert(AEItemKey.of(Items.GOLD_INGOT),
                                        6L, Actionable.MODULATE, IActionSource.empty()) == 6L,
                                "Item storage accepts the newly configured key");
                        helper.assertTrue(fluidChest.getInventory().insert(AEFluidKey.of(Fluids.LAVA),
                                        2_000L, Actionable.MODULATE, IActionSource.empty()) == 2_000L,
                                "Fluid storage accepts the newly configured key");
                    })
                    .thenWaitUntil(() -> helper.assertTrue(items.amount(0) == 6L && fluids.amount(1) == 2_000L,
                            "Watchers update quantities for the new delegate configuration"))
                    .thenExecute(() -> {
                        try (Transaction transaction = Transaction.openRoot()) {
                            helper.assertTrue(items.extract(0, items.resource(0), 1L, transaction) == 1L
                                            && fluids.extract(1, fluids.resource(1), 500L, transaction) == 500L,
                                    "Reconfigured delegates extract the new keys from live storage");
                            transaction.commit();
                        }
                        connections.forEach(IGridConnection::destroy);
                        connections.clear();
                    })
                    .thenWaitUntil(() -> helper.assertTrue(items.amount(0) == 0L && fluids.amount(1) == 0L,
                            "Disconnecting the network clears watcher quantities"))
                    .thenExecute(() -> {
                        MEStorage isolatedNetwork = port.getMainNode().getGrid().getStorageService().getInventory();
                        helper.assertTrue(items.reservationIdentity() == isolatedNetwork
                                        && fluids.reservationIdentity() == isolatedNetwork,
                                "Network split rebinds both delegates to the isolated network");
                        helper.assertTrue(ItemResource.of(Items.GOLD_INGOT).equals(items.resource(0))
                                        && FluidResource.of(Fluids.LAVA).equals(fluids.resource(1)),
                                "Network rebind retains the current resource configuration");
                        try (Transaction transaction = Transaction.openRoot()) {
                            helper.assertTrue(items.extract(0, items.resource(0), 1L, transaction) == 0L
                                            && fluids.extract(1, fluids.resource(1), 500L, transaction) == 0L,
                                    "Isolated delegates cannot extract from the previous network");
                        }
                        connections.add(GridHelper.createConnection(port.getMainNode().getNode(), itemChest.getMainNode().getNode()));
                        connections.add(GridHelper.createConnection(port.getMainNode().getNode(), fluidChest.getMainNode().getNode()));
                        connections.add(GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode()));
                    })
                    .thenWaitUntil(() -> helper.assertTrue(items.amount(0) == 5L && fluids.amount(1) == 1_500L,
                            "Reconnected delegates receive the live network quantities"))
                    .thenExecute(() -> {
                        MEStorage reconnectedNetwork = port.getMainNode().getGrid().getStorageService().getInventory();
                        helper.assertTrue(items.reservationIdentity() == reconnectedNetwork
                                        && fluids.reservationIdentity() == reconnectedNetwork,
                                "Reconnected delegates share the current network reservation identity");
                    })
                    .thenSucceed();
        });
    }

    public void smallStockingMirrorCannotMaterializeNetworkItems(GameTestHelper helper) {
        smallStockingMirrorCannotMaterializeNetworkItems(helper, "ae2_me_stocking_input_interface", 0);
    }

    public void extendedSmallStockingMirrorCannotMaterializeNetworkItems(GameTestHelper helper) {
        smallStockingMirrorCannotMaterializeNetworkItems(helper, "eae_me_extended_stocking_input_interface", 35);
    }

    private static void smallStockingMirrorCannotMaterializeNetworkItems(GameTestHelper helper,
                                                                        String blockId, int configSlot) {
        BlockPos chestPos = new BlockPos(3, 0, 0);
        BlockPos energyPos = new BlockPos(3, 0, 2);
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get(blockId).get().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var port = helper.getBlockEntity(BlockPos.ZERO, StockingInterfaceBlockEntity.class);
        var chest = helper.getBlockEntity(chestPos, MEChestBlockEntity.class);
        var energy = helper.getBlockEntity(energyPos, CreativeEnergyCellBlockEntity.class);
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        port.getConfig().setStack(configSlot, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
        List<IGridConnection> connections = new ArrayList<>();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(port.getMainNode().getNode() != null
                                && chest.getMainNode().getNode() != null && energy.getMainNode().getNode() != null,
                        "Small-mirror fixture waits for its real grid nodes"))
                .thenExecute(() -> {
                    connections.add(GridHelper.createConnection(port.getMainNode().getNode(), chest.getMainNode().getNode()));
                    connections.add(GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode()));
                })
                .thenWaitUntil(() -> helper.assertTrue(port.getMainNode().isActive() && chest.getMainNode().isActive(),
                        "Small-mirror network is powered and has channels before inserting resources"))
                .thenExecute(() -> {
                    helper.assertTrue(chest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT), 4L,
                                    Actionable.MODULATE, IActionSource.empty()) == 4L,
                            "Real network owns the four watched iron ingots");
                    helper.assertTrue(chest.getInventory().insert(AEItemKey.of(Items.GOLD_INGOT), 9L,
                                    Actionable.MODULATE, IActionSource.empty()) == 9L,
                            "Real network also owns an unrelated key for the complete resource snapshot");
                })
                .thenWaitUntil(() -> helper.assertTrue(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L)
                                .equals(port.getStorage().getStack(configSlot)),
                        "Watcher exposes the small real network amount as a mirror"))
                .thenExecute(() -> {
                    var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                            new GameProfile(UUID.randomUUID(), "stocking-small-mirror"), ClientInformation.createDefault());
                    player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
                    player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
                    AEBaseMenu menu;
                    if (configSlot == 35) {
                        var extended = new ExtendedInterfaceMenu(ExtendedAEMenuTypes.INTERFACE, 0,
                                player.getInventory(), port);
                        extended.setPage(1);
                        menu = extended;
                    } else {
                        menu = new AE2InterfaceMenu(AE2MenuTypes.INTERFACE, 0, player.getInventory(), port);
                    }
                    var display = configSlot == 35 ? menu.getSlots(ExSemantics.EX_8).getLast()
                            : menu.getSlots(SlotSemantics.STORAGE).getFirst();
                    helper.assertTrue(display.isActive() && display.getItem().is(Items.IRON_INGOT)
                                    && display.getItem().getCount() == 4
                                    && GenericStack.unwrapItemStack(display.getItem()) == null,
                            "The actual attached stocking slot displays a normal small ItemStack, not a protected wrapper");
                    assertSmallMirrorUnchanged(helper, port, chest, menu, player,
                            () -> menu.clicked(display.index, 0, ContainerInput.PICKUP, player));
                    assertSmallMirrorUnchanged(helper, port, chest, menu, player,
                            () -> menu.clicked(display.index, 0, ContainerInput.QUICK_MOVE, player));
                    assertSmallMirrorUnchanged(helper, port, chest, menu, player,
                            () -> helper.assertTrue(display.remove(1).isEmpty(),
                                    "Direct remove cannot materialize even a single mirrored ingot"));
                    connections.forEach(IGridConnection::destroy);
                })
                .thenSucceed();
    }

    private static void assertSmallMirrorUnchanged(GameTestHelper helper, StockingInterfaceBlockEntity port,
                                                  MEChestBlockEntity chest, AEBaseMenu menu,
                                                  ServerPlayer player, Runnable operation) {
        var network = port.getMainNode().getGrid().getStorageService().getInventory();
        var networkBefore = networkSnapshot(network);
        var cellBefore = networkSnapshot(chest.getInventory());
        var mirrored = inventorySnapshot(port.getStorage());
        var configured = inventorySnapshot(port.getConfig());
        var inventory = player.getInventory().getNonEquipmentItems().stream().map(ItemStack::copy).toList();
        var carried = menu.getCarried().copy();
        operation.run();
        helper.assertTrue(networkBefore.equals(networkSnapshot(network))
                        && cellBefore.equals(networkSnapshot(chest.getInventory()))
                        && mirrored.equals(inventorySnapshot(port.getStorage()))
                        && configured.equals(inventorySnapshot(port.getConfig())),
                "Every small-mirror operation preserves grid/cell ownership, all mirror amounts and configuration");
        for (int i = 0; i < inventory.size(); i++) {
            helper.assertTrue(ItemStack.matches(inventory.get(i), player.getInventory().getItem(i)),
                    "Small mirror creates no items and preserves pre-owned player resources at slot " + i);
        }
        helper.assertTrue(ItemStack.matches(carried, menu.getCarried()),
                "Small mirror pickup, Shift and remove leave cursor resources unchanged");
    }

    private static Map<AEKey, Long> networkSnapshot(MEStorage storage) {
        Map<AEKey, Long> amounts = new HashMap<>();
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getLongValue() != 0L) amounts.put(entry.getKey(), entry.getLongValue());
        }
        return amounts;
    }

    private static List<GenericStack> inventorySnapshot(GenericStackInv inventory) {
        List<GenericStack> stacks = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) stacks.add(inventory.getStack(i));
        return stacks;
    }

    // This is a unstable gametest wich will be pushed by the game tick
    // public void oversizeStockingWatcherTracksLargeConfiguredKey(GameTestHelper helper) {
    //     helper.assertTrue(AE2Bridge.get().available(), "AE2 must be loaded for this integration test");
    // 
    //     BlockPos portPos = new BlockPos(0, 0, 0);
    //     BlockPos chestPos = new BlockPos(3, 0, 0);
    //     BlockPos energyPos = new BlockPos(3, 0, 2);
    //     helper.setBlock(portPos, ModBlocks.BLOCKS.get("eae_me_oversize_stocking_input_interface").get().defaultBlockState());
    //     helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
    //     helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
    // 
    //     StockingInterfaceBlockEntity port = helper.getBlockEntity(portPos, StockingInterfaceBlockEntity.class);
    //     MEChestBlockEntity chest = helper.getBlockEntity(chestPos, MEChestBlockEntity.class);
    //     CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos, CreativeEnergyCellBlockEntity.class);
    //     chest.setCell(AEItems.ITEM_CELL_64K.stack());
    //     port.getInterfaceLogic().getConfig().setStack(35,
    //             new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
    //     AtomicLong refreshesAfterInitialWatch = new AtomicLong();
    // 
    //     helper.runAtTickTime(2, () -> {
    //         helper.assertTrue(port.getMainNode().getNode() != null && chest.getMainNode().getNode() != null
    //                         && energy.getMainNode().getNode() != null,
    //                 "Oversize stocking interface and its ME network initialize");
    //         GridHelper.createConnection(port.getMainNode().getNode(), chest.getMainNode().getNode());
    //         GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode());
    //     });
    // 
    //     helper.runAtTickTime(10, () -> {
    //         helper.assertTrue(chest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT), OVERSIZE_TRANSFER_AMOUNT,
    //                         Actionable.MODULATE, IActionSource.empty()) == OVERSIZE_TRANSFER_AMOUNT,
    //                 "Oversize ME inventory transfers an amount beyond an ordinary AE2 interface slot");
    //     });
    // 
    //     helper.startSequence()
    //             .thenWaitUntil(() -> {
    //                 GenericStack mirroredStack = port.getInterfaceLogic().getStorage().getStack(35);
    //                 helper.assertTrue(mirroredStack != null && mirroredStack.amount() == OVERSIZE_TRANSFER_AMOUNT,
    //                         "Oversize stocking watcher mirrors the configured key after AE2 processing");
    //             })
    //             .thenExecute(() -> {
    //                 refreshesAfterInitialWatch.set(port.storageMirrorRefreshes());
    //                 helper.assertTrue(chest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT), 1L,
    //                                 Actionable.MODULATE, IActionSource.empty()) == 1L,
    //                         "Oversize ME inventory allows extracting the watched key");
    //             })
    //             .thenWaitUntil(() -> {
    //                 GenericStack mirroredStack = port.getInterfaceLogic().getStorage().getStack(35);
    //                 helper.assertTrue(mirroredStack != null && mirroredStack.amount() == OVERSIZE_TRANSFER_AMOUNT - 1L,
    //                         "Watcher updates the changed configured key after AE2 processing");
    //             })
    //             .thenExecute(() -> helper.assertTrue(port.storageMirrorRefreshes() == refreshesAfterInitialWatch.get(),
    //                     "Watcher callback updates the changed key without a full network mirror refresh"))
    //             .thenSucceed();
    // }
}
