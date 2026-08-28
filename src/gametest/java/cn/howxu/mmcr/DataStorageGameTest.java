package cn.howxu.mmcr;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class DataStorageGameTest {
    private static final Identifier MACHINE_ID = MMCR.id("data_storage_tick");

    public void pureTickWritesBoundStorage(GameTestHelper helper) {
        BlockPos controllerBlockPos = new BlockPos(1, 1, 1);
        BlockPos storageBlockPos = controllerBlockPos.west();
        helper.setBlock(controllerBlockPos, ModBlocks.controllerFor(MACHINE_ID).get().defaultBlockState());
        helper.setBlock(storageBlockPos, ModBlocks.DATA_STORAGE.get().defaultBlockState());

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerBlockPos, MachineControllerBlockEntity.class);
        DataStorageBlockEntity storage = helper.getBlockEntity(storageBlockPos, DataStorageBlockEntity.class);
        BlockPos controllerPos = controller.getBlockPos();
        storage.storage().set("ticks", DataValue.of(0L));
        controller.setMachine(MachineRegistry.getMachine(MACHINE_ID));
        ControllerScreenTextCache.clear(controllerPos);
        ServerPlayer observer = observer(helper);
        observer.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        helper.getLevel().players().add(observer);

        helper.runAtTickTime(80, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Pure-tick structure formed");
            helper.assertTrue(controller.structureSnapshot().machine().behavior().kind() == MachineBehavior.Kind.TICK,
                    "Pure-tick machine keeps its TickBehavior");
            helper.assertTrue(controller.behaviorContext().dataStorages().containsKey(storage.getBlockPos()),
                    "Pure-tick behavior context exposes the bound storage");
            long ticks = storage.storage().get("ticks").flatMap(DataValue::asLong).orElse(-1L);
            helper.assertTrue(ticks >= 1L && ticks <= 5L,
                    "Tick behavior writes at a 20-tick period, actual=" + ticks);
            PktControllerScreenTextPayload textPayload = lastScreenTextPacket(observer);
            helper.assertTrue(textPayload != null && hasDynamicText(textPayload.lines()),
                    "Pure-tick callback text is sent in the server payload");
            ControllerScreenTextCache.replace(textPayload.controllerPos(), textPayload.revision(), textPayload.lines());
            ControllerScreenTextCache.clear(controllerPos);
            MachineControllerMenu reopenedMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
            observer.containerMenu = reopenedMenu;
            controller.sendControllerScreenText(observer);
            PktControllerScreenTextPayload reopenedTextPayload = lastScreenTextPacket(observer);
            helper.assertTrue(reopenedTextPayload != null && hasDynamicText(reopenedTextPayload.lines()),
                    "Reopened controller receives the current dynamic text payload");
            helper.assertTrue(ControllerScreenTextCache.replace(reopenedTextPayload.controllerPos(),
                            reopenedTextPayload.revision(), reopenedTextPayload.lines()),
                    "Reopened controller payload replaces the cache snapshot");
            helper.assertTrue(hasDynamicText(ControllerScreenTextCache.linesAt(reopenedMenu.controllerPos())),
                    "Reopened controller cache exposes the dynamic text");
            helper.assertTrue(controller.runtimeSnapshot().crafting().status().getStatus()
                            == CraftingStatus.Status.IDLE,
                    "Pure-tick controller does not start recipe crafting");
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
            helper.getLevel().players().remove(observer);
            ControllerScreenTextCache.clear(controllerPos);
            helper.succeed();
        });
    }

    public void recipeSnapshotLoadsWithoutStartCallbackRerun(GameTestHelper helper) {
        Identifier machineId = MMCR.id("task7_recipe_snapshot");
        BlockPos controllerPos = new BlockPos(3, 1, 3);
        BlockPos inputPos = controllerPos.west();
        BlockPos outputPos = controllerPos.east();
        helper.setBlock(controllerPos, ModBlocks.controllerFor(machineId).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, net.minecraft.core.Direction.SOUTH));
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());

        ItemInputBusBlockEntity input = helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class);
        ItemOutputBusBlockEntity output = helper.getBlockEntity(outputPos, ItemOutputBusBlockEntity.class);
        input.getItemHandler(null).insertItem(0, new ItemStack(Items.DIAMOND), false);
        input.getItemHandler(null).insertItem(1, new ItemStack(Items.IRON_INGOT, 2), false);

        DynamicMachine registeredMachine = (DynamicMachine) MachineRegistry.getMachine(machineId);
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger finishes = new AtomicInteger();
        AtomicReference<String> callbackFailure = new AtomicReference<>();
        BlockArray pattern = new BlockArray(Map.of(
                inputPos.subtract(controllerPos), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_input_bus").get()),
                outputPos.subtract(controllerPos), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_output_bus").get())));
        Machine recipeMachine = new DynamicMachine(machineId, registeredMachine.displayNameKey(), pattern,
                registeredMachine.controller(), registeredMachine.appearance(), registeredMachine.portRequirements(),
                registeredMachine.portTierRequirements(), registeredMachine.dynamicPatterns(),
                registeredMachine.modifierReplacements(), registeredMachine.maxParallelism(),
                registeredMachine.parallelizable(), registeredMachine.hasFactory(), registeredMachine.factoryThreadLimit(),
                registeredMachine.factoryThreads(), registeredMachine.role(), registeredMachine.acceptedModuleIds(),
                List.of(), registeredMachine.failureAction(), RecipeBehavior.builder()
                        .beforeStart(context -> {
                            starts.incrementAndGet();
                            context.setDuration(2);
                            context.setRequirements(List.of(
                                    new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                                            ItemStack.EMPTY),
                                    MachineRequirement.itemOutput(new ItemStack(Items.GOLD_NUGGET, 2))));
                        })
                        .recipeTick(context -> {
                            ticks.incrementAndGet();
                            if (context.totalTick() != 2
                                    || ((ItemRequirement) context.requirements().getFirst()).count() != 2
                                    || ((MachineOutput.ItemOutput) context.outputs().getFirst()).stack().getCount() != 2) {
                                callbackFailure.compareAndSet(null,
                                        "Recipe Tick uses the loaded effective snapshot");
                            }
                        })
                        .beforeFinish(context -> {
                            finishes.incrementAndGet();
                            if (((MachineOutput.ItemOutput) context.outputs().getFirst()).stack().getCount() != 2) {
                                callbackFailure.compareAndSet(null,
                                        "Recipe Finish uses the loaded effective output snapshot");
                            }
                        })
                        .build());

        MachineRecipe recipe = new MachineRecipe(MMCR.id("controller_tick_effective_snapshot"), machineId, 20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND), 1)), List.of());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(recipeMachine);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Recipe snapshot machine forms with real I/O buses");
            RecipeRegistry.register(recipe);
            controller.serverTick();
            helper.assertTrue(recipe.id().equals(controller.runtimeSnapshot().crafting().recipeId()) && starts.get() == 1,
                    "Recipe Start runs once before serialization");

            CompoundTag saved = saveController(controller, helper.getLevel().registryAccess());
            loadController(controller, helper.getLevel().registryAccess(), saved);
            helper.assertTrue(recipe.id().equals(controller.runtimeSnapshot().crafting().recipeId())
                            && controller.runtimeSnapshot().crafting().totalTick() == 2
                            && starts.get() == 1,
                    "Controller load restores the effective recipe without rerunning Start");
            input.getItemHandler(null).extractItem(0, 1, false);

            controller.serverTick();
            controller.serverTick();
            String callbackError = callbackFailure.get();
            helper.assertTrue(callbackError == null,
                    callbackError == null ? "Recipe callbacks use the loaded effective snapshot" : callbackError);
            helper.assertTrue(ticks.get() == 2 && finishes.get() == 1 && starts.get() == 1,
                    "Loaded recipe continues through Tick and Finish callbacks");
            helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() == null
                            && output.getItemHandler(null).getStackInSlot(0).is(Items.GOLD_NUGGET)
                            && output.getItemHandler(null).getStackInSlot(0).getCount() == 2,
                    "Loaded effective output finishes through the real output bus");
            helper.succeed();
        });
    }

    private static CompoundTag saveController(MachineControllerBlockEntity controller,
                                               HolderLookup.Provider registries) {
        try {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
            var save = MachineControllerBlockEntity.class.getDeclaredMethod("saveAdditional", ValueOutput.class);
            save.setAccessible(true);
            save.invoke(controller, output);
            return output.buildResult();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to save controller runtime", exception);
        }
    }

    private static void loadController(MachineControllerBlockEntity controller,
                                       HolderLookup.Provider registries, CompoundTag tag) {
        try {
            var load = MachineControllerBlockEntity.class.getDeclaredMethod("loadAdditional", ValueInput.class);
            load.setAccessible(true);
            load.invoke(controller, TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to load controller runtime", exception);
        }
    }

    private static boolean hasDynamicText(List<ControllerScreenTextSnapshot.Line> lines) {
        return lines.stream().anyMatch(line -> line.lineId().equals(MMCR.id("data_storage_tick_status"))
                && line.text().getString().startsWith("ticks="));
    }

    private static ServerPlayer observer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-data-storage-observer"),
                ClientInformation.createDefault());
        player.connection = new RecordingConnection(server, player);
        return player;
    }

    private static PktControllerScreenTextPayload lastScreenTextPacket(ServerPlayer player) {
        return ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktControllerScreenTextPayload)
                .map(packet -> (PktControllerScreenTextPayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    /**
     * Captures controller screen text payloads without requiring a network client.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.CLIENTBOUND), player,
                    CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),
                            "mmcr-data-storage-connection"), false));
        }

        @Override
        public void send(Packet<?> packet) {
            packets.add(packet);
        }
    }
}
