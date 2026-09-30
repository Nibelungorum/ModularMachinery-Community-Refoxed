package cn.howxu.mmcr;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.slot.AppEngSlot;
import com.glodblock.github.extendedae.container.ContainerExInterface;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBaseBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * End-to-end GameTest coverage for the AE2 normal output interface MMCR integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public class AE2OutputInterfaceGameTest {
    private static final long ITEM_AMOUNT = 16L;
    private static final long FLUID_AMOUNT = 1_000L;
    private static final long ITEM_CELL_TOTAL_BYTES = 1_024L;
    private static final long ITEM_CELL_BYTES_PER_TYPE = 8L;
    private static final long ITEM_CELL_ITEMS_PER_BYTE = 8L;
    private static final long ITEM_CELL_SINGLE_TYPE_CAPACITY =
            (ITEM_CELL_TOTAL_BYTES - ITEM_CELL_BYTES_PER_TYPE) * ITEM_CELL_ITEMS_PER_BYTE;
    private static final long LOCAL_CACHE_ITEM_CAPACITY = 9L * 64L;
    private static final long OVER_CAPACITY_AMOUNT = 1_024L;

    public void outputInterfaceDrainsToNetworkAndLocksConfig(GameTestHelper helper) {
        helper.assertTrue(AE2Bridge.get().available(),
                "AE2 must be loaded for this integration test");

        BlockPos portPos = new BlockPos(0, 0, 0);
        BlockPos itemChestPos = new BlockPos(3, 0, 0);
        BlockPos fluidChestPos = new BlockPos(3, 0, 2);
        BlockPos energyPos = new BlockPos(3, 0, 4);
        helper.setBlock(portPos,
                ModBlocks.BLOCKS.get("ae2_me_output_interface").get().defaultBlockState());
        helper.setBlock(itemChestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(fluidChestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        OutputInterfaceBlockEntity port = helper.getBlockEntity(portPos);
        MEChestBlockEntity itemChest = helper.getBlockEntity(itemChestPos);
        MEChestBlockEntity fluidChest = helper.getBlockEntity(fluidChestPos);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
        itemChest.setCell(AEItems.ITEM_CELL_1K.stack());
        fluidChest.setCell(AEItems.FLUID_CELL_1K.stack());

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(port.getMainNode().getNode() != null,
                    "Output interface has initialized its AE2 grid node");
            helper.assertTrue(itemChest.getMainNode().getNode() != null,
                    "Item ME Chest has initialized its AE2 grid node");
            helper.assertTrue(fluidChest.getMainNode().getNode() != null,
                    "Fluid ME Chest has initialized its AE2 grid node");
            helper.assertTrue(energy.getMainNode().getNode() != null,
                    "Creative energy cell has initialized its AE2 grid node");
            GridHelper.createConnection(port.getMainNode().getNode(), itemChest.getMainNode().getNode());
            GridHelper.createConnection(port.getMainNode().getNode(), fluidChest.getMainNode().getNode());
            GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode());
        });

        helper.runAtTickTime(3, () -> {
            helper.assertTrue(port.ioType() == IOType.OUTPUT,
                    "AE2 output interface reports IOType.OUTPUT");
            BlockState portState = helper.getLevel().getBlockState(helper.absolutePos(portPos));
            IInWorldGridNodeHost gridHost = helper.getLevel().getCapability(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, helper.absolutePos(portPos), null);
            helper.assertTrue(gridHost == port,
                    "AE2 IN_WORLD_GRID_NODE_HOST capability is exposed for output");
            helper.assertTrue(helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Output interface does not expose GENERIC_INTERNAL_INV externally");
            helper.assertTrue(helper.getLevel().getCapability(AECapabilities.ME_STORAGE,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Output interface does not expose ME_STORAGE externally");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) != null,
                    "Output interface exposes an external item handler");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) != null,
                    "Output interface exposes an external fluid handler");

            CapabilitySnapshot snapshot = port.capabilitySnapshot();
            helper.assertTrue(snapshot.capabilities().size() == 2,
                    "Output interface exposes exactly item and fluid MMCR capabilities");
            for (MachineCapability capability : snapshot.capabilities()) {
                helper.assertTrue(capability.type().id().equals(PortFamilyIds.ITEM)
                                || capability.type().id().equals(PortFamilyIds.FLUID),
                        "Output capability is bound to the item or fluid family");
                helper.assertTrue(capability.facet(TransferFacet.class).isPresent(),
                        "Output capability exposes TransferFacet for external extraction");
            }
            helper.assertTrue(port.getInterfaceLogic().getConfig().getKey(0) == null
                            && port.getInterfaceLogic().getConfig().getKey(1) == null,
                    "Output interface starts with an empty interface config");
        });

        helper.runAtTickTime(4, () -> {
            MachineCapability itemCapability = capabilityOf(port, PortFamilyIds.ITEM);
            MachineCapability fluidCapability = capabilityOf(port, PortFamilyIds.FLUID);
            CapabilityOperation itemOperation = itemCapability.prepare(new CapabilityRequests.ItemRequest(
                    itemCapability.type(), IOType.OUTPUT, 1L,
                    java.util.List.of(new CapabilityRequests.ItemAction(
                            0, new ItemStack(Items.IRON_INGOT), ITEM_AMOUNT, true))));
            CapabilityResult itemResult = itemOperation.commit();
            helper.assertTrue(itemResult.success(),
                    "Output item capability operation commits successfully");
            CapabilityOperation fluidOperation = fluidCapability.prepare(new CapabilityRequests.FluidRequest(
                    fluidCapability.type(), IOType.OUTPUT, 1L,
                    java.util.List.of(new CapabilityRequests.FluidAction(
                            0, new FluidStack(Fluids.WATER, 1), FLUID_AMOUNT, true))));
            CapabilityResult fluidResult = fluidOperation.commit();
            helper.assertTrue(fluidResult.success(),
                    "Output fluid capability operation commits successfully");
            helper.assertTrue(itemChest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT),
                            ITEM_AMOUNT, Actionable.SIMULATE, IActionSource.empty()) == ITEM_AMOUNT,
                    "Item ME Chest receives the full 16 iron output");
            helper.assertTrue(fluidChest.getInventory().extract(AEFluidKey.of(Fluids.WATER),
                            FLUID_AMOUNT, Actionable.SIMULATE, IActionSource.empty()) == FLUID_AMOUNT,
                    "Fluid ME Chest receives the full 1000 mB water output");
            helper.assertTrue(port.getInterfaceLogic().getStorage().isEmpty(),
                    "Output cache stays empty when the network absorbs the full amount");
        });

        helper.runAtTickTime(5, () -> {
            long fillAmount = ITEM_CELL_SINGLE_TYPE_CAPACITY - ITEM_AMOUNT;
            long previousInserted = itemChest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT),
                    fillAmount, Actionable.MODULATE, IActionSource.empty());
            helper.assertTrue(previousInserted == fillAmount,
                    "Filling the 1K ME Chest cell consumes the remaining 8112 iron capacity");
            helper.assertTrue(itemChest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT),
                            OVER_CAPACITY_AMOUNT, Actionable.SIMULATE, IActionSource.empty()) == 0L,
                    "Filled 1K ME Chest reports zero spare capacity for iron");
        });

        helper.runAtTickTime(6, () -> {
            IItemHandler itemHandler = port.nativeItemHandler();
            long accepted = 0L;
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                ItemStack requested = new ItemStack(Items.IRON_INGOT, 64);
                accepted += requested.getCount() - itemHandler.insertItem(slot, requested, false).getCount();
            }
            helper.assertTrue(accepted == LOCAL_CACHE_ITEM_CAPACITY,
                    "Native handler accepts the 576 iron units that fit in nine 64-item cache slots");
            long cacheAmount = 0L;
            int occupiedSlots = 0;
            for (int slot = 0; slot < port.getInterfaceLogic().getStorage().size(); slot++) {
                if (port.getInterfaceLogic().getStorage().getStack(slot) != null) {
                    occupiedSlots++;
                    cacheAmount += port.getInterfaceLogic().getStorage().getAmount(slot);
                }
            }
            helper.assertTrue(port.getInterfaceLogic().getStorage().size() == 9,
                    "Output interface cache exposes nine slots");
            helper.assertTrue(occupiedSlots == 9 && cacheAmount == LOCAL_CACHE_ITEM_CAPACITY,
                    "Local cache absorbs exactly 576 iron units across nine slots");

        });

        helper.runAtTickTime(7, () -> {
            reloadBlockEntity(port, helper);
            long cacheAmount = 0L;
            int occupiedSlots = 0;
            for (int slot = 0; slot < port.getInterfaceLogic().getStorage().size(); slot++) {
                if (port.getInterfaceLogic().getStorage().getStack(slot) != null) {
                    occupiedSlots++;
                    cacheAmount += port.getInterfaceLogic().getStorage().getAmount(slot);
                }
            }
            helper.assertTrue(occupiedSlots == 9 && cacheAmount == LOCAL_CACHE_ITEM_CAPACITY,
                    "Output cache preserves all 576 iron units across a save/load cycle while over-capacity");

            IItemHandler externalItems = helper.getLevel().getCapability(
                    Capabilities.ItemHandler.BLOCK, helper.absolutePos(portPos),
                    helper.getLevel().getBlockState(helper.absolutePos(portPos)), port, Direction.NORTH);
            helper.assertTrue(externalItems != null,
                    "External item handler is available while the output cache is occupied");
            helper.assertTrue(externalItems.extractItem(0, 1, false).getCount() == 1,
                    "External item extraction removes one item from the output cache");
            helper.assertTrue(port.getInterfaceLogic().getStorage().getAmount(0) == 63L,
                    "External item extraction changes the local output cache");

            ServerPlayer menuPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.nameUUIDFromBytes(
                            "mmcr-ae2-output-cache-menu".getBytes(StandardCharsets.UTF_8)),
                            "mmcr-ae2-output-cache-menu"),
                    ClientInformation.createDefault());
            InterfaceMenu menu = new InterfaceMenu(InterfaceMenu.TYPE, 0,
                    menuPlayer.getInventory(), port);
            AppEngSlot storageSlot = (AppEngSlot) menu.getSlots(SlotSemantics.STORAGE).get(0);
            helper.assertTrue(storageSlot.mayPickup(menuPlayer),
                    "Output cache storage slot can be picked up from the AE2 menu");
            menu.quickMoveStack(menuPlayer, storageSlot.index);
            helper.assertTrue(port.getInterfaceLogic().getStorage().getAmount(0) == 0L,
                    "AE2 menu shift-click extracts the occupied output cache slot");
        });

        helper.runAtTickTime(8, () -> {
            long extracted = itemChest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT),
                    OVER_CAPACITY_AMOUNT, Actionable.MODULATE, IActionSource.empty());
            helper.assertTrue(extracted > 0L,
                    "Clearing the ME Chest cell extracts the previously filled iron");
            long simulated = itemChest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT),
                    OVER_CAPACITY_AMOUNT, Actionable.SIMULATE, IActionSource.empty());
            helper.assertTrue(simulated > 0L,
                    "Cleared ME Chest restores spare network capacity for iron");
        });

        helper.runAtTickTime(150, () -> {
            int slowerRate = TickRates.Interface.getMax();
            helper.assertTrue(slowerRate > 0,
                    "AE2 interface SLOWER tick rate is positive; covers the drain boundary");
            helper.assertTrue(port.getInterfaceLogic().getStorage().isEmpty(),
                    "Output ticker drains the local cache once the network has spare capacity");
        });

        helper.runAtTickTime(151, () -> {
            ServerPlayer menuPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.nameUUIDFromBytes(
                            "mmcr-ae2-output-menu-test".getBytes(StandardCharsets.UTF_8)),
                            "mmcr-ae2-output-menu"),
                    ClientInformation.createDefault());
            InterfaceMenu menu = new InterfaceMenu(InterfaceMenu.TYPE, 0,
                    menuPlayer.getInventory(), port);
            menu.setFilter(0, new ItemStack(Items.DIAMOND));
            helper.assertTrue(port.getInterfaceLogic().getConfig().getKey(0) == null,
                    "InterfaceMenu.setFilter cannot write into the locked output config");
            helper.assertTrue(port.getInterfaceLogic().getStorage().isEmpty(),
                    "Locked output config leaves the storage empty after the menu write attempt");
            helper.succeed();
        });
    }

    public void extendedOutputMenuAllowsExtractionButBlocksInsertionAndFilters(GameTestHelper helper) {
        helper.assertTrue(AE2Bridge.get().available(), "AE2 must be loaded for this integration test");

        BlockPos portPos = new BlockPos(0, 0, 0);
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("eae_me_extended_output_interface").get().defaultBlockState());

        helper.runAtTickTime(2, () -> {
            OutputInterfaceBlockEntity port = helper.getBlockEntity(portPos);
            ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.nameUUIDFromBytes("mmcr-eae-output-menu".getBytes(StandardCharsets.UTF_8)),
                            "mmcr-eae-output-menu"), ClientInformation.createDefault());
            port.getInterfaceLogic().getStorage().insert(35, AEItemKey.of(Items.IRON_INGOT), 1L, Actionable.MODULATE);
            ContainerExInterface menu = new ContainerExInterface(ContainerExInterface.TYPE, 0, player.getInventory(), port);
            AppEngSlot storageSlot = (AppEngSlot) menu.getSlots(com.glodblock.github.extendedae.client.ExSemantics.EX_8).getLast();
            storageSlot.set(Items.DIAMOND.getDefaultInstance());
            helper.assertTrue(port.getInterfaceLogic().getStorage().getStack(35).what().equals(AEItemKey.of(Items.IRON_INGOT)),
                    "ExtendedAE output storage menu rejects item insertion");
            helper.assertTrue(storageSlot.mayPickup(player), "ExtendedAE output storage can be extracted by a player");
            menu.quickMoveStack(player, storageSlot.index);
            helper.assertTrue(port.getInterfaceLogic().getStorage().getStack(35) == null,
                    "Player menu extraction removes the extended output stack");
            menu.setFilter(35, new ItemStack(Items.DIAMOND));
            helper.assertTrue(port.getInterfaceLogic().getConfig().getKey(35) == null,
                    "ExtendedAE output menu blocks filter edits");
            helper.succeed();
        });
    }

    private static MachineCapability capabilityOf(OutputInterfaceBlockEntity port, ResourceLocation familyId) {
        return port.capabilitySnapshot().capabilities().stream()
                .filter(capability -> capability.type().id().equals(familyId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing capability for family " + familyId));
    }

    private static void reloadBlockEntity(BlockEntity entity, GameTestHelper helper) {
        try {
            Method save = BlockEntity.class.getDeclaredMethod("saveAdditional", CompoundTag.class,
                    HolderLookup.Provider.class);
            save.setAccessible(true);
            Method load = BlockEntity.class.getDeclaredMethod("loadAdditional", CompoundTag.class,
                    HolderLookup.Provider.class);
            load.setAccessible(true);
            CompoundTag tag = new CompoundTag();
            save.invoke(entity, tag, helper.getLevel().registryAccess());
            OutputInterfaceBaseBlockEntity output = (OutputInterfaceBaseBlockEntity) entity;
            output.getInterfaceLogic().getStorage().clear();
            output.getInterfaceLogic().getConfig().clear();
            helper.assertTrue(output.getInterfaceLogic().getStorage().isEmpty()
                            && output.getInterfaceLogic().getConfig().isEmpty(),
                    "Output interface state is cleared before loading persisted data");
            load.invoke(entity, tag, helper.getLevel().registryAccess());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to reload block entity " + entity, exception);
        }
    }
}
