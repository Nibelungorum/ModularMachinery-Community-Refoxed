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
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBaseBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.lang.reflect.Method;

/**
 * End-to-end GameTest coverage for the AE2 async output interface MMCR integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public class AE2AsyncOutputInterfaceGameTest {
    private static final long ITEM_AMOUNT = 32L;
    private static final long FLUID_AMOUNT = 2_000L;

    public void asyncOutputInterfaceDrainsServiceAndSurvivesDisconnect(GameTestHelper helper) {
        helper.assertTrue(AE2Bridge.get().available(),
                "AE2 must be loaded for this integration test");

        BlockPos portPos = new BlockPos(0, 0, 0);
        BlockPos itemChestPos = new BlockPos(3, 0, 0);
        BlockPos fluidChestPos = new BlockPos(3, 0, 2);
        BlockPos energyPos = new BlockPos(3, 0, 4);
        helper.setBlock(portPos,
                ModBlocks.BLOCKS.get("ae2_me_async_output_interface").get().defaultBlockState());
        helper.setBlock(itemChestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(fluidChestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        AsyncOutputInterfaceBlockEntity port = helper.getBlockEntity(portPos,
                AsyncOutputInterfaceBlockEntity.class);
        MEChestBlockEntity itemChest = helper.getBlockEntity(itemChestPos, MEChestBlockEntity.class);
        MEChestBlockEntity fluidChest = helper.getBlockEntity(fluidChestPos, MEChestBlockEntity.class);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos,
                CreativeEnergyCellBlockEntity.class);
        itemChest.setCell(AEItems.ITEM_CELL_1K.stack());
        fluidChest.setCell(AEItems.FLUID_CELL_1K.stack());

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(port.getMainNode().getNode() != null,
                    "Async output interface has initialized its AE2 grid node");
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
                    "AE2 async output interface reports IOType.OUTPUT");
            BlockState portState = helper.getLevel().getBlockState(helper.absolutePos(portPos));
            IInWorldGridNodeHost gridHost = helper.getLevel().getCapability(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, helper.absolutePos(portPos), null);
            helper.assertTrue(gridHost == port,
                    "AE2 IN_WORLD_GRID_NODE_HOST capability is exposed for async output");
            helper.assertTrue(helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Async output interface does not expose GENERIC_INTERNAL_INV externally");
            helper.assertTrue(helper.getLevel().getCapability(AECapabilities.ME_STORAGE,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Async output interface does not expose ME_STORAGE externally");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Async output interface does not expose an external item handler");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) == null,
                    "Async output interface does not expose an external fluid handler");

            CapabilitySnapshot snapshot = port.capabilitySnapshot();
            helper.assertTrue(snapshot.capabilities().size() == 2,
                    "Async output interface exposes exactly item and fluid MMCR capabilities");
            for (MachineCapability capability : snapshot.capabilities()) {
                ResourceLocation id = capability.type().id();
                helper.assertTrue(id.equals(PortFamilyIds.ITEM) || id.equals(PortFamilyIds.FLUID),
                        "Async output capability is bound to the item or fluid family");
                helper.assertTrue(capability.facet(TransferFacet.class).isEmpty(),
                        "Async output capability does not expose TransferFacet");
            }
            helper.assertTrue(snapshot.capabilities().stream()
                            .map(MachineCapability::type)
                            .noneMatch(type -> type.id().equals(MekanismPortFamilies.CHEMICAL)
                                    || type.id().equals(MekanismPortFamilies.RADIOACTIVE_CHEMICAL)),
                    "Async output interface does not expose a chemical capability");
            helper.assertTrue(port.nativeItemHandler().getSlots() == 9,
                    "Async output exposes the native nine-slot item cache");
            helper.assertTrue(port.nativeFluidHandler().getTanks() == 9,
                    "Async output exposes the native nine-tank fluid cache");
            helper.assertTrue(port.getInterfaceLogic().getStorage().isEmpty(),
                    "Async output interface storage starts empty");
            helper.assertTrue(port.getInterfaceLogic().getConfig().getKey(0) == null,
                    "Async output interface config slot 0 starts null");
        });

        helper.runAtTickTime(4, () -> {
            ItemStack itemRemainder = port.nativeItemHandler().insertItem(0,
                    new ItemStack(Items.IRON_INGOT, (int) ITEM_AMOUNT), false);
            int fluidInserted = port.nativeFluidHandler().fill(
                    new FluidStack(Fluids.WATER, (int) FLUID_AMOUNT), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(itemRemainder.isEmpty(),
                    "Async output item handler accepts the requested amount");
            helper.assertTrue(fluidInserted == FLUID_AMOUNT,
                    "Async output fluid handler accepts the requested amount");
            helper.assertTrue(port.getInterfaceLogic().getStorage().isEmpty(),
                    "Async output interface storage stays empty immediately after planning");
        });

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(itemChest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT),
                            ITEM_AMOUNT, Actionable.SIMULATE, IActionSource.empty()) == ITEM_AMOUNT,
                    "Async ticker drains the queued iron into the ME Chest");
            helper.assertTrue(fluidChest.getInventory().extract(AEFluidKey.of(Fluids.WATER),
                            FLUID_AMOUNT, Actionable.SIMULATE, IActionSource.empty()) == FLUID_AMOUNT,
                    "Async ticker drains the queued water into the ME Chest");
            helper.assertTrue(port.getInterfaceLogic().getStorage().isEmpty(),
                    "Async output interface storage remains empty after the service drain");
        });

        helper.runAtTickTime(21, () -> {
            helper.getLevel().destroyBlock(helper.absolutePos(itemChestPos), true);
            helper.getLevel().destroyBlock(helper.absolutePos(fluidChestPos), true);
            helper.getLevel().destroyBlock(helper.absolutePos(energyPos), true);
        });

        helper.runAtTickTime(30, () -> {
            ItemStack itemRemainder = port.nativeItemHandler().insertItem(0,
                    new ItemStack(Items.IRON_INGOT, (int) ITEM_AMOUNT), false);
            int fluidInserted = port.nativeFluidHandler().fill(
                    new FluidStack(Fluids.WATER, (int) FLUID_AMOUNT), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(itemRemainder.isEmpty(),
                    "Disconnected output caches the accepted item amount locally");
            helper.assertTrue(fluidInserted == FLUID_AMOUNT,
                    "Disconnected output caches the accepted fluid amount locally");
        });

        helper.runAtTickTime(31, () -> {
            reloadBlockEntity(port, helper);
            helper.assertTrue(port.getInterfaceLogic().getConfig().getStack(0) == null,
                    "Save/load preserves an empty async output interface config slot 0");
            helper.assertTrue(port.getInterfaceLogic().getStorage().getAmount(0) == ITEM_AMOUNT
                            && port.getInterfaceLogic().getStorage().getAmount(1) == FLUID_AMOUNT,
                    "Save/load preserves the disconnected native output cache");
            for (int slot = 0; slot < port.getInterfaceLogic().getConfig().size(); slot++) {
                helper.assertTrue(port.getInterfaceLogic().getConfig().getStack(slot) == null,
                        "Save/load keeps every async output config slot null at slot=" + slot);
            }
            helper.succeed();
        });
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
                    "Async output state is cleared before loading persisted data");
            load.invoke(entity, tag, helper.getLevel().registryAccess());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to reload block entity " + entity, exception);
        }
    }
}
