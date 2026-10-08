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
import net.minecraft.world.inventory.ClickType;
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
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedAEMenuTypes;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedInterfaceMenu;
import com.glodblock.github.extendedae.client.ExSemantics;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * End-to-end GameTest coverage for the AE2 stocking input interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public class AE2StockingInterfaceGameTest {
    private static final long ITEM_AMOUNT = 128L;
    private static final long FLUID_AMOUNT = 5_000L;

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

        StockingInterfaceBlockEntity port = helper.getBlockEntity(portPos);
        MEChestBlockEntity itemChest = helper.getBlockEntity(itemChestPos);
        MEChestBlockEntity fluidChest = helper.getBlockEntity(fluidChestPos);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);

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
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Stocking interface does not expose an external item handler");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
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
            menu.clicked(displaySlot.index, 0, ClickType.PICKUP, menuPlayer);
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
                            && menuPlayer.getInventory().items.stream().allMatch(ItemStack::isEmpty),
                    "GUI operations preserve the mirror and create no player resources");
            helper.assertTrue(itemChest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE,
                            Actionable.SIMULATE, IActionSource.empty()) == ITEM_AMOUNT
                            && fluidChest.getInventory().extract(AEFluidKey.of(Fluids.WATER), Long.MAX_VALUE,
                            Actionable.SIMULATE, IActionSource.empty()) == FLUID_AMOUNT,
                    "GUI operations leave real item and fluid network inventories unchanged");
            menu.setCarried(ItemStack.EMPTY);

            CapabilitySnapshot snapshot = port.capabilitySnapshot();
            helper.assertTrue(snapshot.capabilities().size() == (AppMekBridge.get().available() ? 3 : 2),
                    "Stocking interface exposes its loaded resource families");
            for (MachineCapability capability : snapshot.capabilities()) {
                helper.assertTrue(capability.type().id().equals(PortFamilyIds.ITEM)
                                || capability.type().id().equals(PortFamilyIds.FLUID)
                                || AppMekBridge.get().available() && capability.type().id().equals(MekanismPortFamilies.CHEMICAL),
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

            CapabilityRequests.ItemRequest itemRequest = new CapabilityRequests.ItemRequest(
                    itemCapability.type(), IOType.INPUT, 1L,
                    List.of(new CapabilityRequests.ItemAction(
                            0, new ItemStack(Items.IRON_INGOT), 3L, false)));
            CapabilityRequests.FluidRequest fluidRequest = new CapabilityRequests.FluidRequest(
                    fluidCapability.type(), IOType.INPUT, 1L,
                    List.of(new CapabilityRequests.FluidAction(
                            0, new FluidStack(Fluids.WATER, 1), 1_000L, false)));
            CapabilityOperation itemOperation = itemCapability.prepare(itemRequest);
            CapabilityOperation fluidOperation = fluidCapability.prepare(fluidRequest);
            MEStorage itemNetwork = itemChest.getInventory();
            MEStorage fluidNetwork = fluidChest.getInventory();
            helper.assertTrue(itemNetwork.extract(AEItemKey.of(Items.IRON_INGOT), ITEM_AMOUNT,
                            Actionable.SIMULATE, IActionSource.empty()) == ITEM_AMOUNT,
                    "Preparing the item operation does not mutate MEStorage");
            helper.assertTrue(fluidNetwork.extract(AEFluidKey.of(Fluids.WATER), FLUID_AMOUNT,
                            Actionable.SIMULATE, IActionSource.empty()) == FLUID_AMOUNT,
                    "Preparing the fluid operation does not mutate MEStorage");
            CapabilityResult itemResult = itemOperation.commit();
            CapabilityResult fluidResult = fluidOperation.commit();
            helper.assertTrue(itemResult.success(),
                    "Stocking item capability operation commits successfully");
            helper.assertTrue(fluidResult.success(),
                    "Stocking fluid capability operation commits successfully");
            helper.assertTrue(itemNetwork.extract(AEItemKey.of(Items.IRON_INGOT), ITEM_AMOUNT,
                            Actionable.SIMULATE, IActionSource.empty()) == ITEM_AMOUNT - 3L,
                    "Item MEStorage quantity is deducted after operation commit");
            helper.assertTrue(fluidNetwork.extract(AEFluidKey.of(Fluids.WATER), FLUID_AMOUNT,
                            Actionable.SIMULATE, IActionSource.empty()) == FLUID_AMOUNT - 1_000L,
                    "Fluid MEStorage quantity is deducted after operation commit");
        });

        helper.runAtTickTime(12, () -> {
            helper.assertTrue(Objects.requireNonNull(port.getInterfaceLogic().getStorage().getStack(0)).amount() == ITEM_AMOUNT - 3L,
                    "Item watcher display updates on the next tick without a full scan");
            helper.assertTrue(Objects.requireNonNull(port.getInterfaceLogic().getStorage().getStack(1)).amount() == FLUID_AMOUNT - 1_000L,
                    "Fluid watcher display updates on the next tick without a full scan");
            var items = port.nativeItemHandler();
            var fluids = port.nativeFluidHandler();
            var itemAccess = (NativeReservationAccess) items;
            var fluidAccess = (NativeReservationAccess) fluids;
            Object originalNetwork = itemAccess.reservationIdentity();
            helper.startSequence()
                    .thenExecute(() -> {
                        port.getInterfaceLogic().getConfig().setStack(0,
                                new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L));
                        port.getInterfaceLogic().getConfig().setStack(1,
                                new GenericStack(AEFluidKey.of(Fluids.LAVA), 1_000L));
                        helper.assertTrue(items == port.nativeItemHandler() && fluids == port.nativeFluidHandler(),
                                "Config changes rebind the existing live storage views");
                        helper.assertTrue(items.getSlots() == 1 && fluids.getTanks() == 1
                                        && AEItemKey.of(Items.GOLD_INGOT).equals(itemAccess.storedKey(0))
                                        && AEFluidKey.of(Fluids.LAVA).equals(fluidAccess.storedKey(0)),
                                "Config changes immediately replace the typed native resource keys");
                        helper.assertTrue(itemAccess.reservationIdentity() == originalNetwork
                                        && fluidAccess.reservationIdentity() == originalNetwork,
                                "Config rebind retains the live network reservation identity");
                        helper.assertTrue(itemChest.getInventory().insert(AEItemKey.of(Items.GOLD_INGOT),
                                        6L, Actionable.MODULATE, IActionSource.empty()) == 6L,
                                "Item storage accepts the newly configured key");
                        helper.assertTrue(fluidChest.getInventory().insert(AEFluidKey.of(Fluids.LAVA),
                                        2_000L, Actionable.MODULATE, IActionSource.empty()) == 2_000L,
                                "Fluid storage accepts the newly configured key");
                    })
                    .thenWaitUntil(() -> helper.assertTrue(itemAccess.storedAmount(0) == 6L && fluidAccess.storedAmount(0) == 2_000L,
                            "Watchers update quantities for the new delegate configuration"))
                    .thenExecute(() -> {
                        helper.assertTrue(items.extractItem(0, 1, false).getCount() == 1
                                        && fluids.drain(new FluidStack(Fluids.LAVA, 500), IFluidHandler.FluidAction.EXECUTE)
                                        .getAmount() == 500,
                                "Reconfigured delegates extract the new keys from live storage");
                        connections.forEach(IGridConnection::destroy);
                        connections.clear();
                    })
                    .thenWaitUntil(() -> helper.assertTrue(itemAccess.storedAmount(0) == 0L && fluidAccess.storedAmount(0) == 0L,
                            "Disconnecting the network clears watcher quantities"))
                    .thenExecute(() -> {
                        MEStorage isolatedNetwork = port.getMainNode().getGrid().getStorageService().getInventory();
                        helper.assertTrue(itemAccess.reservationIdentity() == isolatedNetwork
                                        && fluidAccess.reservationIdentity() == isolatedNetwork,
                                "Network split rebinds both delegates to the isolated network");
                        helper.assertTrue(AEItemKey.of(Items.GOLD_INGOT).equals(itemAccess.storedKey(0))
                                        && AEFluidKey.of(Fluids.LAVA).equals(fluidAccess.storedKey(0)),
                                "Network rebind retains the current resource configuration");
                        helper.assertTrue(items.extractItem(0, 1, true).isEmpty()
                                        && fluids.drain(new FluidStack(Fluids.LAVA, 500), IFluidHandler.FluidAction.SIMULATE).isEmpty(),
                                "Isolated delegates cannot extract from the previous network");
                        connections.add(GridHelper.createConnection(port.getMainNode().getNode(), itemChest.getMainNode().getNode()));
                        connections.add(GridHelper.createConnection(port.getMainNode().getNode(), fluidChest.getMainNode().getNode()));
                        connections.add(GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode()));
                    })
                    .thenWaitUntil(() -> helper.assertTrue(itemAccess.storedAmount(0) == 5L && fluidAccess.storedAmount(0) == 1_500L,
                            "Reconnected delegates receive the live network quantities"))
                    .thenExecute(() -> {
                        MEStorage reconnectedNetwork = port.getMainNode().getGrid().getStorageService().getInventory();
                        helper.assertTrue(itemAccess.reservationIdentity() == reconnectedNetwork
                                        && fluidAccess.reservationIdentity() == reconnectedNetwork,
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
        StockingInterfaceBlockEntity port = helper.getBlockEntity(BlockPos.ZERO);
        MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
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
                            () -> menu.clicked(display.index, 0, ClickType.PICKUP, player));
                    assertSmallMirrorUnchanged(helper, port, chest, menu, player,
                            () -> menu.clicked(display.index, 0, ClickType.QUICK_MOVE, player));
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
        var inventory = player.getInventory().items.stream().map(ItemStack::copy).toList();
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

}
