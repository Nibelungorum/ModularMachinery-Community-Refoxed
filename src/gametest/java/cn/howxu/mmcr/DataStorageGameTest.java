package cn.howxu.mmcr;

import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public final class DataStorageGameTest {
    private static final Identifier MACHINE_ID = MMCR.id("data_storage_tick");

    public void pureTickWritesBoundStorage(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        BlockPos storagePos = controllerPos.west();
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MACHINE_ID).get().defaultBlockState());
        helper.setBlock(storagePos, ModBlocks.DATA_STORAGE.get().defaultBlockState());

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        DataStorageBlockEntity storage = helper.getBlockEntity(storagePos, DataStorageBlockEntity.class);
        storage.storage().set("ticks", DataValue.of(0L));
        controller.setMachine(MachineRegistry.getMachine(MACHINE_ID));

        helper.runAtTickTime(80, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Pure-tick structure formed");
            helper.assertTrue(controller.structureSnapshot().machine().behavior().kind() == MachineBehavior.Kind.TICK,
                    "Pure-tick machine keeps its TickBehavior");
            helper.assertTrue(controller.behaviorContext().dataStorages().containsKey(storage.getBlockPos()),
                    "Pure-tick behavior context exposes the bound storage");
            long ticks = storage.storage().get("ticks").map(DataValue::longValue).orElse(-1L);
            helper.assertTrue(ticks >= 1L && ticks <= 5L,
                    "Tick behavior writes at a 20-tick period, actual=" + ticks);
            helper.assertTrue(controller.runtimeSnapshot().crafting().status().getStatus()
                            == CraftingStatus.Status.CRAFTING,
                    "Pure-tick controller publishes working status");
            helper.assertTrue(new ControllerSyncRuntime().machineState(controller.runtimeSnapshot()).active(),
                    "Pure-tick controller projects active state");
            helper.assertTrue(controller.getBlockState().getValue(MachineControllerBlock.ACTIVE),
                    "Pure-tick controller block remains active");
            PktMachineStatePayload payload = PktMachineStatePayload.from(controllerPos, controller.runtimeSnapshot());
            helper.assertTrue(payload.active(), "Pure-tick state packet remains active");
            MachineControllerMenu reopened = new MachineControllerMenu(1, new Inventory(null, null), controller);
            helper.assertTrue(reopened.hasActiveRecipe(), "Reopened controller menu reads active state");
            MachineControllerMenu clientMenu = new MachineControllerMenu(1, new Inventory(null, null));
            clientMenu.applyClientSnapshot(payload);
            helper.assertTrue(clientMenu.hasActiveRecipe(), "Client controller menu keeps active state");
            helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() == null,
                    "Pure-tick behavior does not start recipe runtime");
            helper.succeed();
        });
    }
}
