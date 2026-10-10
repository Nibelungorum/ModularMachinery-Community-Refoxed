package cn.howxu.mmcr;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.MachineBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
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
import cn.howxu.mmcr.internal.event.ControllerSyncEvents;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiSnapshotPayload;
import cn.howxu.mmcr.internal.event.ControllerUiEvents;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            helper.assertTrue(controller.behaviorContext().dataStorage() != null,
                    "Pure-tick behavior context exposes the bound storage");
            long ticks = storage.storage().get("ticks").flatMap(DataValue::asLong).orElse(-1L);
            helper.assertTrue(ticks >= 1L && ticks <= 5L,
                    "Tick behavior writes at a 20-tick period, actual=" + ticks);
            PktControllerScreenTextPayload textPayload = lastScreenTextPacket(observer);
            helper.assertTrue(textPayload != null && hasDynamicText(textPayload.lines()),
                    "A legacy test-only menu without a new session still receives callback text");
            observer.setPos(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);
            MachineControllerMenu reopenedMenu = new MachineControllerMenu(1, observer.getInventory(), controller);
            observer.containerMenu = reopenedMenu;
            ControllerUiEvents.opened(new PlayerContainerEvent.Open(observer, reopenedMenu));
            var reopenedSnapshot = uiPackets(observer).getLast().snapshotData();
            helper.assertTrue(hasUiDynamicText(reopenedSnapshot) && reopenedSnapshot.hasDataStorage()
                            && reopenedSnapshot.dataStorageValues().get("ticks").longValue() == ticks,
                    "Reopened actual session receives current text and bound storage in its full snapshot");
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
            // Neighboring tests may share this chunk; exercise that case in isolated runs too.
            BlockPos neighborBlockPos = controllerBlockPos.above();
            helper.setBlock(neighborBlockPos, ModBlocks.controllerFor(MACHINE_ID).get().defaultBlockState());
            MachineControllerBlockEntity neighbor = helper.getBlockEntity(neighborBlockPos, MachineControllerBlockEntity.class);
            neighbor.setMachine(MachineRegistry.getMachine(MACHINE_ID));
            BlockPos neighborPos = neighbor.getBlockPos();
            long baselineCount = machineStatePackets(observer, controllerPos).size();
            long neighborBaselineCount = machineStatePackets(observer, neighborPos).size();
            PktMachineStatePayload expectedBaseline = PktMachineStatePayload.from(controllerPos,
                    controller.runtimeSnapshot(), controller.currentRecipePoolId());
            PktMachineStatePayload expectedNeighborBaseline = PktMachineStatePayload.from(neighborPos,
                    neighbor.runtimeSnapshot(), neighbor.currentRecipePoolId());
            observer.closeContainer();
            int uiBaselines = uiPackets(observer).size();
            MachineControllerMenu serverMenu = new MachineControllerMenu(2, observer.getInventory(), controller);
            observer.containerMenu = serverMenu;
            ControllerUiEvents.opened(new PlayerContainerEvent.Open(observer, serverMenu));
            serverMenu.broadcastChanges();
            helper.assertTrue(uiPackets(observer).size() == uiBaselines + 1
                            && hasUiDynamicText(uiPackets(observer).getLast().snapshotData())
                            && machineStatePackets(observer, controllerPos).size() == baselineCount,
                    "Open sends one new UI baseline; an active session does not duplicate the old menu baseline");
            serverMenu.broadcastChanges();
            helper.assertTrue(uiPackets(observer).size() == uiBaselines + 1,
                    "Unchanged menu state does not resend the full presentation");
            var chunkPos = ChunkPos.containing(controllerPos);
            var chunk = helper.getLevel().getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
            helper.assertTrue(chunk != null, "Controller chunk is already loaded");
            helper.assertTrue(chunkPos.equals(ChunkPos.containing(neighborPos)),
                    "Both fixture controllers share the sent chunk");
            ControllerSyncEvents.onChunkSent(new ChunkWatchEvent.Sent(observer, chunk, helper.getLevel()));
            helper.assertTrue(machineStatePackets(observer, controllerPos).size() == baselineCount + 1,
                    "Chunk Sent sends a complete baseline after the chunk payload");
            helper.assertTrue(machineStatePackets(observer, neighborPos).size() == neighborBaselineCount + 1,
                    "Chunk Sent also sends exactly one baseline for the neighboring controller");
            helper.assertTrue(machineStatePackets(observer, controllerPos).getLast().equals(expectedBaseline)
                            && machineStatePackets(observer, neighborPos).getLast().equals(expectedNeighborBaseline),
                    "Chunk Sent preserves both controllers' complete baseline contents");
            ControllerSyncEvents.onChunkUnWatch(new ChunkWatchEvent.UnWatch(observer, chunkPos, helper.getLevel()));
            ControllerSyncEvents.onChunkSent(new ChunkWatchEvent.Sent(observer, chunk, helper.getLevel()));
            helper.assertTrue(machineStatePackets(observer, controllerPos).size() == baselineCount + 2,
                    "Chunk reentry sends a fresh full baseline without requiring state changes");
            helper.assertTrue(machineStatePackets(observer, neighborPos).size() == neighborBaselineCount + 2,
                    "Chunk reentry also sends exactly one fresh baseline for the neighboring controller");
            helper.assertTrue(machineStatePackets(observer, controllerPos).getLast().equals(expectedBaseline)
                            && machineStatePackets(observer, neighborPos).getLast().equals(expectedNeighborBaseline),
                    "Chunk reentry preserves both unchanged complete baselines");
            helper.getLevel().players().remove(observer);
            observer.closeContainer();
            ControllerScreenTextCache.clear(controllerPos);
            helper.succeed();
        });
    }

    public void recipeSnapshotLoadsWithoutStartCallbackRerun(GameTestHelper helper) {
        ServerConfig.MACHINE_WORK_MODE.clearCache();
        ServerConfig.MACHINE_WORK_MODE.set(MachineWorkMode.SYNC);
        Identifier machineId = MMCR.id("task7_recipe_snapshot");
        BlockPos controllerPos = new BlockPos(3, 1, 3);
        BlockPos inputPos = controllerPos.west();
        BlockPos outputPos = controllerPos.east();
        helper.setBlock(controllerPos, ModBlocks.controllerFor(machineId).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());

        ItemInputBusBlockEntity input = helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class);
        ItemOutputBusBlockEntity output = helper.getBlockEntity(outputPos, ItemOutputBusBlockEntity.class);
        try (Transaction transaction = Transaction.openRoot()) {
            input.itemStorage().insert(0, ItemResource.of(Items.DIAMOND), 1L, transaction);
            transaction.commit();
        }
        try (Transaction transaction = Transaction.openRoot()) {
            input.itemStorage().insert(1, ItemResource.of(Items.IRON_INGOT), 2L, transaction);
            transaction.commit();
        }
        // input.getItemHandler(null).insertItem(0, new ItemStack(Items.DIAMOND), false);
        // input.getItemHandler(null).insertItem(1, new ItemStack(Items.IRON_INGOT, 2), false);

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
                                    new cn.howxu.mmcr.api.recipe.requirement.ItemRequirement(
                                            IOType.INPUT,
                                            Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY, 1F,
                                            DataComponentPredicateSet.EMPTY, 1F),
                                    new cn.howxu.mmcr.api.recipe.requirement.ItemRequirement(
                                            IOType.OUTPUT, null, 0,
                                            new ItemStack(Items.GOLD_NUGGET, 2), 1F,
                                            DataComponentPredicateSet.EMPTY, 1F)));
                        })
                        .recipeTick(context -> {
                            ticks.incrementAndGet();
                            if (context.totalTick() != 2
                                    || ((cn.howxu.mmcr.api.recipe.requirement.ItemRequirement)
                                    context.requirements().getFirst()).count() != 2
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

        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("controller_tick_effective_snapshot"), machineId, 20,
                List.of(MachineRequirement.fromInput(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND), 1))), List.of(), List.of(),
                0, 1, false, false, false, Set.of());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(recipeMachine);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Recipe snapshot machine forms with real I/O buses");
            RecipeRegistry.registerStatic(recipe);
            controller.serverTick();
            helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(recipe.id().equals(controller.runtimeSnapshot().crafting().recipeId())
                                    && starts.get() == 1,
                            "Recipe Start runs once before serialization"))
                    .thenExecute(() -> {

            CompoundTag saved = saveController(controller, helper.getLevel().registryAccess());
            loadController(controller, helper.getLevel().registryAccess(), saved);
            helper.assertTrue(recipe.id().equals(controller.runtimeSnapshot().crafting().recipeId())
                            && controller.runtimeSnapshot().crafting().totalTick() == 2
                            && starts.get() == 1,
                    "Controller load restores the effective recipe without rerunning Start");
            try (Transaction transaction = Transaction.openRoot()) {
                ItemResource resource = input.itemStorage().resource(0);
                if (resource != null && !resource.isEmpty()) {
                    input.itemStorage().extract(0, resource, 1L, transaction);
                    transaction.commit();
                }
            }

            controller.serverTick();
            controller.serverTick();
            String callbackError = callbackFailure.get();
            helper.assertTrue(callbackError == null,
                    callbackError == null ? "Recipe callbacks use the loaded effective snapshot" : callbackError);
            helper.assertTrue(ticks.get() == 2 && finishes.get() == 1,
                    "Loaded recipe continues through Tick and Finish callbacks");
            helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() == null
                            && output.itemStorage().resource(0).toStack(1).is(Items.GOLD_NUGGET)
                            && output.itemStorage().amount(0) == 2L,
                    "Loaded effective output finishes through the real output bus");
            helper.succeed();
                    });
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
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket(
                        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload
                )
                        && payload instanceof PktControllerScreenTextPayload)
                .map(packet -> (PktControllerScreenTextPayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private static boolean hasUiDynamicText(ControllerUiSnapshotData snapshot) {
        return snapshot.lines().stream().anyMatch(line -> line.id().equals(MMCR.id("data_storage_tick_status"))
                && line.text().getString().startsWith("ticks="));
    }

    private static List<PktControllerUiSnapshotPayload> uiPackets(ServerPlayer player) {
        return ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktControllerUiSnapshotPayload)
                .map(packet -> (PktControllerUiSnapshotPayload) ((ClientboundCustomPayloadPacket) packet).payload()).toList();
    }

    private static List<PktMachineStatePayload> machineStatePackets(ServerPlayer player, BlockPos controllerPos) {
        return ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktMachineStatePayload)
                .map(packet -> (PktMachineStatePayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(payload -> payload.pos().equals(controllerPos))
                .toList();
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
