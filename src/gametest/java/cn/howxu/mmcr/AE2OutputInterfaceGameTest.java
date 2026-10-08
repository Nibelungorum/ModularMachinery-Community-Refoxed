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
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2InterfaceMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import appeng.menu.slot.AppEngSlot;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedInterfaceMenu;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedAEMenuTypes;
import com.glodblock.github.extendedae.client.ExSemantics;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.recipe.OutputResourceStorage;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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

        OutputInterfaceBlockEntity port = helper.getBlockEntity(portPos,
                OutputInterfaceBlockEntity.class);
        MEChestBlockEntity itemChest = helper.getBlockEntity(itemChestPos, MEChestBlockEntity.class);
        MEChestBlockEntity fluidChest = helper.getBlockEntity(fluidChestPos, MEChestBlockEntity.class);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos,
                CreativeEnergyCellBlockEntity.class);
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
            helper.assertTrue(helper.getLevel().getCapability(ModCapabilities.ITEM_BLOCK,
                            helper.absolutePos(portPos), portState, port, Direction.NORTH) != null,
                    "Output interface exposes an external item handler");
            helper.assertTrue(helper.getLevel().getCapability(ModCapabilities.FLUID_BLOCK,
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
            try (Transaction transaction = Transaction.openRoot()) {
                CapabilityOperation itemOperation = itemCapability.prepare(requestOf(itemCapability,
                        ItemResource.of(Items.IRON_INGOT), ITEM_AMOUNT, true));
                CapabilityResult itemResult = itemOperation.commit(transaction);
                helper.assertTrue(itemResult.success(),
                        "Output item capability operation commits successfully");
                CapabilityOperation fluidOperation = fluidCapability.prepare(requestOf(fluidCapability,
                        FluidResource.of(Fluids.WATER), FLUID_AMOUNT, true));
                CapabilityResult fluidResult = fluidOperation.commit(transaction);
                helper.assertTrue(fluidResult.success(),
                        "Output fluid capability operation commits successfully");
                transaction.commit();
            }
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
            PlanningReservations reservations = new PlanningReservations();
            @SuppressWarnings({"unchecked", "rawtypes"})
            OutputResourceStorage.OutputPlan plan = ((OutputResourceStorage) port.itemStorage())
                    .planOutput(ItemResource.of(Items.IRON_INGOT), OVER_CAPACITY_AMOUNT,
                            reservations, true);
            helper.assertTrue(plan.accepted() == LOCAL_CACHE_ITEM_CAPACITY,
                    "Over-capacity plan accepts the 576 iron units that fit in nine 64-item cache slots");
            try (Transaction transaction = Transaction.openRoot()) {
                if (plan.operation() != null) {
                    plan.operation().commit(transaction);
                }
                transaction.commit();
            }
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

            ResourceHandler<ItemResource> externalItems = helper.getLevel().getCapability(
                    ModCapabilities.ITEM_BLOCK, helper.absolutePos(portPos),
                    helper.getLevel().getBlockState(helper.absolutePos(portPos)), port, Direction.NORTH);
            helper.assertTrue(externalItems != null,
                    "External item handler is available while the output cache is occupied");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(externalItems.extract(0, ItemResource.of(Items.IRON_INGOT), 1,
                                transaction) == 1,
                        "External item extraction removes one item from the output cache");
                transaction.commit();
            }
            helper.assertTrue(port.getInterfaceLogic().getStorage().getAmount(0) == 63L,
                    "External item extraction changes the local output cache");

            ServerPlayer menuPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.nameUUIDFromBytes(
                            "mmcr-ae2-output-cache-menu".getBytes(StandardCharsets.UTF_8)),
                            "mmcr-ae2-output-cache-menu"),
                    ClientInformation.createDefault());
            AE2InterfaceMenu menu = new AE2InterfaceMenu(AE2MenuTypes.INTERFACE, 0,
                    menuPlayer.getInventory(), port);
            AppEngSlot storageSlot = (AppEngSlot) menu.getSlots(SlotSemantics.STORAGE).get(0);
            helper.assertTrue(storageSlot.mayPickup(menuPlayer),
                    "Output cache storage slot can be picked up from the AE2 menu");
            menu.quickMoveStack(menuPlayer, storageSlot.index);
            helper.assertTrue(port.getInterfaceLogic().getStorage().getAmount(0) == 0L,
                    "AE2 menu shift-click extracts the occupied output cache slot");
            helper.assertTrue(menuPlayer.getInventory().getNonEquipmentItems().stream()
                            .filter(stack -> stack.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum() == 63,
                    "Shift extraction transfers all cached resources into the player inventory");
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
            AE2InterfaceMenu menu = new AE2InterfaceMenu(AE2MenuTypes.INTERFACE, 0,
                    menuPlayer.getInventory(), port);
            menu.setFilter(menu.getSlots(SlotSemantics.CONFIG).getFirst().index, new ItemStack(Items.DIAMOND));
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
            OutputInterfaceBlockEntity port = helper.getBlockEntity(portPos, OutputInterfaceBlockEntity.class);
            ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.nameUUIDFromBytes("mmcr-eae-output-menu".getBytes(StandardCharsets.UTF_8)),
                            "mmcr-eae-output-menu"), ClientInformation.createDefault());
            port.getInterfaceLogic().getStorage().insert(35, AEItemKey.of(Items.IRON_INGOT), 1L, Actionable.MODULATE);
            ExtendedInterfaceMenu menu = new ExtendedInterfaceMenu(ExtendedAEMenuTypes.INTERFACE, 0, player.getInventory(), port);
            menu.setPage(1);
            helper.assertTrue(menu.getType() == ExtendedAEMenuTypes.INTERFACE, "Extended output uses its own registered menu");
            AppEngSlot storageSlot = (AppEngSlot) menu.getSlots(ExSemantics.EX_8).getLast();
            storageSlot.set(Items.DIAMOND.getDefaultInstance());
            helper.assertTrue(port.getInterfaceLogic().getStorage().getStack(35).what().equals(AEItemKey.of(Items.IRON_INGOT)),
                    "ExtendedAE output storage menu rejects item insertion");
            helper.assertTrue(storageSlot.mayPickup(player), "ExtendedAE output storage can be extracted by a player");
            menu.quickMoveStack(player, storageSlot.index);
            helper.assertTrue(port.getInterfaceLogic().getStorage().getStack(35) == null,
                    "Player menu extraction removes the extended output stack");
            helper.assertTrue(player.getInventory().getNonEquipmentItems().stream()
                            .filter(stack -> stack.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum() == 1,
                    "Extracted slot 35 resource reaches the player without duplication or loss");
            menu.setFilter(menu.getConfigSlots().get(35).index, new ItemStack(Items.DIAMOND));
            helper.assertTrue(port.getInterfaceLogic().getConfig().getKey(35) == null,
                    "ExtendedAE output menu blocks filter edits");
            helper.succeed();
        });
    }

    private static MachineCapability capabilityOf(OutputInterfaceBlockEntity port, Identifier familyId) {
        return port.capabilitySnapshot().capabilities().stream()
                .filter(capability -> capability.type().id().equals(familyId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing capability for family " + familyId));
    }

    private static cn.howxu.mmcr.api.capability.plan.CapabilityRequests.ResourceRequest<?> requestOf(
            MachineCapability capability, Object resource, long amount, boolean insert) {
        return new cn.howxu.mmcr.api.capability.plan.CapabilityRequests.ResourceRequest<>(
                capability.type(), IOType.OUTPUT, 1L,
                java.util.List.of(new cn.howxu.mmcr.api.capability.plan.CapabilityRequests.ResourceAction<>(
                        0, resource, amount, insert)));
    }

    private static void reloadBlockEntity(BlockEntity entity, GameTestHelper helper) {
        try {
            Method save = BlockEntity.class.getDeclaredMethod("saveAdditional", ValueOutput.class);
            save.setAccessible(true);
            Method load = BlockEntity.class.getDeclaredMethod("loadAdditional", ValueInput.class);
            load.setAccessible(true);
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess());
            save.invoke(entity, output);
            CompoundTag tag = output.buildResult();
            load.invoke(entity, TagValueInput.create(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess(), tag));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to reload block entity " + entity, exception);
        }
    }
}
