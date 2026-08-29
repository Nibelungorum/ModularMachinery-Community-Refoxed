package cn.howxu.mmcr;

import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineIoPlan;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ControllerTickGameTest {

    public void structureForms3x3Casing(GameTestHelper helper) {
        recipeMachineHooksPublishTextAndRespectRedstone(helper);
    }

    public void recipeMachineHooksPublishTextAndRespectRedstone(GameTestHelper helper) {
        Identifier machineId = MMCR.id("controller_tick");
        helper.assertTrue(MachineRegistry.getMachine(machineId) != null,
                "GameTest startup installs the machine registry entry");
        helper.assertTrue(cn.howxu.mmcr.api.machine.MachineStructureRegistry.effectiveSnapshot().containsKey(machineId),
                "GameTest startup installs the effective structure");
        helper.assertTrue(!MachineRegistry.getCompiledStages(machineId).isEmpty(),
                "GameTest startup compiles the effective structure");

        DynamicMachine registeredMachine = (DynamicMachine) MachineRegistry.getMachine(machineId);
        AtomicBoolean invokeHooks = new AtomicBoolean();
        AtomicInteger preCalls = new AtomicInteger();
        AtomicInteger postCalls = new AtomicInteger();
        Machine recipeMachine = new DynamicMachine(registeredMachine.registryName(), registeredMachine.displayNameKey(),
                registeredMachine.pattern(), registeredMachine.controller(), registeredMachine.appearance(),
                registeredMachine.portRequirements(), registeredMachine.portTierRequirements(),
                registeredMachine.dynamicPatterns(), registeredMachine.modifierReplacements(),
                registeredMachine.maxParallelism(), registeredMachine.parallelizable(), registeredMachine.hasFactory(),
                registeredMachine.factoryThreadLimit(), registeredMachine.factoryThreads(), registeredMachine.role(),
                registeredMachine.acceptedModuleIds(), registeredMachine.structureStages(),
                registeredMachine.failureAction(), RecipeBehavior.builder()
                        .preServerTick(context -> {
                            if (!invokeHooks.get()) return;
                            preCalls.incrementAndGet();
                            context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                                    MMCR.id("recipe_hook_status"), Component.literal("pre"));
                        })
                        .postServerTick(context -> {
                            if (!invokeHooks.get()) return;
                            postCalls.incrementAndGet();
                            context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                                    MMCR.id("recipe_hook_status"), Component.literal("post"));
                        })
                        .build());

        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());

        BlockPos controllerBlockPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerBlockPos, ModBlocks.controllerFor(machineId).get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerBlockPos,
                MachineControllerBlockEntity.class);
        BlockPos controllerPos = controller.getBlockPos();
        controller.setMachine(recipeMachine);
        ServerPlayer observer = observer(helper);
        observer.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        helper.getLevel().players().add(observer);
        ControllerScreenTextCache.clear(controllerPos);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(controller.structureSnapshot().formed()
                            && controller.structureSnapshot().structureAreaLoaded(),
                    "recipe machine is formed and loaded before hooks run");
            invokeHooks.set(true);
            int packetsBefore = screenTextPackets(observer);
            controller.serverTick();
            helper.assertTrue(preCalls.get() == 1 && postCalls.get() == 1,
                    "formed recipe machine invokes both machine-level hooks");
            ControllerScreenTextSnapshot snapshot = runtime(controller).screenText().snapshot();
            helper.assertTrue(snapshot.lines().size() == 1
                            && snapshot.lines().getFirst().lineId().equals(MMCR.id("recipe_hook_status"))
                            && snapshot.lines().getFirst().text().getString().equals("post"),
                    "post hook replaces the keyed text written by pre hook");
            helper.assertTrue(screenTextPackets(observer) == packetsBefore + 1,
                    "hook text sends one controller payload");
            applyLastScreenTextPacket(observer);
            helper.assertTrue(hasText(ControllerScreenTextCache.linesAt(controllerPos), "post"),
                    "hook text reaches the client cache");

            int pausedPackets = screenTextPackets(observer);
            helper.setBlock(controllerBlockPos.below(), Blocks.REDSTONE_TORCH.defaultBlockState());
            controller.serverTick();
            helper.assertTrue(controller.isRedstonePaused(), "redstone signal pauses the recipe machine");
            helper.assertTrue(preCalls.get() == 1 && postCalls.get() == 1
                            && screenTextPackets(observer) == pausedPackets,
                    "redstone pause skips hooks and does not resend unchanged text");

            helper.setBlock(controllerBlockPos.below(), Blocks.AIR.defaultBlockState());
            controller.serverTick();
            helper.assertTrue(!controller.isRedstonePaused()
                            && preCalls.get() == 2 && postCalls.get() == 2,
                    "removing redstone resumes both recipe machine hooks");
            helper.getLevel().players().remove(observer);
            helper.succeed();
        });
    }

    public void redstonePausesAndResumesControllerRecipeProgress(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        BlockPos inputPos = controllerPos.offset(1, 0, 0);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("controller_tick")).get().defaultBlockState());
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null)
                .insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        Identifier recipeId = MMCR.id("controller_tick_redstone_pause");
        RecipeRegistry.register(new MachineRecipe(recipeId, MMCR.id("controller_tick"), 20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)), List.of()));

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("controller_tick")));
        controller.serverTick();
        controller.serverTick();
        int progressBeforePause = controller.runtimeSnapshot().crafting().tick();
        helper.assertTrue(progressBeforePause > 0, "Recipe started before redstone pause");

        helper.setBlock(controllerPos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        for (int tick = 0; tick < 4; tick++) controller.serverTick();
        helper.assertTrue(controller.isRedstonePaused(), "Direct redstone signal pauses controller");
        helper.assertTrue(controller.runtimeSnapshot().crafting().status().isPaused(),
                "Powered controller publishes paused crafting state");

        helper.setBlock(controllerPos.above(), Blocks.AIR.defaultBlockState());
        controller.serverTick();
        helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() != null,
                "Removing redstone resumes the paused recipe");
        helper.assertTrue(controller.runtimeSnapshot().crafting().tick() > progressBeforePause,
                "Recipe resumes from paused progress");
        helper.succeed();
    }

    public void formedTickCommitsPartialOutputAndDataAtomically(GameTestHelper helper) {
        Identifier machineId = MMCR.id("task7_tick_io");
        BlockPos controllerPos = new BlockPos(3, 1, 3);
        BlockPos firstInputPos = controllerPos.west();
        BlockPos secondInputPos = controllerPos.west(2);
        BlockPos firstOutputPos = controllerPos.east();
        BlockPos secondOutputPos = controllerPos.east(2);
        BlockPos energyPos = controllerPos.south();
        BlockPos storagePos = controllerPos.south(2);

        helper.setBlock(controllerPos, ModBlocks.controllerFor(machineId).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(firstInputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(secondInputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(firstOutputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        helper.setBlock(secondOutputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        helper.setBlock(energyPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
        helper.setBlock(storagePos, ModBlocks.DATA_STORAGE.get().defaultBlockState());

        ItemInputBusBlockEntity firstInput = helper.getBlockEntity(firstInputPos, ItemInputBusBlockEntity.class);
        ItemInputBusBlockEntity secondInput = helper.getBlockEntity(secondInputPos, ItemInputBusBlockEntity.class);
        ItemOutputBusBlockEntity firstOutput = helper.getBlockEntity(firstOutputPos, ItemOutputBusBlockEntity.class);
        ItemOutputBusBlockEntity secondOutput = helper.getBlockEntity(secondOutputPos, ItemOutputBusBlockEntity.class);
        EnergyInputHatchBlockEntity energy = helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class);
        DataStorageBlockEntity storage = helper.getBlockEntity(storagePos, DataStorageBlockEntity.class);
        firstInput.getItemHandler(null).insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        secondInput.getItemHandler(null).insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        firstOutput.getItemHandler(null).insertItem(0, new ItemStack(Items.GOLD_NUGGET, 63), false);
        fillOutput(firstOutput, 0);
        fillOutput(secondOutput, -1);
        energy.energyStorage().setAmount(5L);
        storage.storage().set("ticks", DataValue.of(0L));

        DynamicMachine registeredMachine = (DynamicMachine) MachineRegistry.getMachine(machineId);
        AtomicBoolean executed = new AtomicBoolean();
        Machine tickMachine = new DynamicMachine(machineId, registeredMachine.displayNameKey(), new BlockArray(Map.of(
                firstInputPos.subtract(controllerPos), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_input_bus").get()),
                secondInputPos.subtract(controllerPos), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_input_bus").get()),
                firstOutputPos.subtract(controllerPos), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_output_bus").get()),
                secondOutputPos.subtract(controllerPos), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_output_bus").get()),
                energyPos.subtract(controllerPos), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("energy_input_hatch").get()),
                storagePos.subtract(controllerPos), new BlockPredicate.OfBlock(ModBlocks.DATA_STORAGE.get()))),
                registeredMachine.controller(), registeredMachine.appearance(), registeredMachine.portRequirements(),
                registeredMachine.portTierRequirements(), registeredMachine.dynamicPatterns(),
                registeredMachine.modifierReplacements(), registeredMachine.maxParallelism(),
                registeredMachine.parallelizable(), registeredMachine.hasFactory(), registeredMachine.factoryThreadLimit(),
                registeredMachine.factoryThreads(), registeredMachine.role(), registeredMachine.acceptedModuleIds(),
                List.of(), registeredMachine.failureAction(),
                TickBehavior.builder().serverTick(context -> {
                    if (!executed.compareAndSet(false, true)) return;
                    helper.assertTrue(context.dataStorage(context.controllerPos().south(2)).orElse(null) == storage.storage(),
                            "Tick callback resolves the formed DataStorage");
                    MachineIoPlan plan = context.ioPlan()
                            .addInput(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(
                                    Ingredient.of(Items.IRON_INGOT), 2)))
                            .addInput(MachineRequirement.fromInput(new MachineIngredient.EnergyIngredient(5)))
                            .addOutput(MachineRequirement.itemOutput(new ItemStack(Items.GOLD_NUGGET, 3)),
                                    OutputPolicy.ALLOW_PARTIAL);
                    List<ItemStack> firstInputBeforeSimulation = snapshot(firstInput.getItemStackHandler(null));
                    List<ItemStack> secondInputBeforeSimulation = snapshot(secondInput.getItemStackHandler(null));
                    List<ItemStack> firstOutputBeforeSimulation = snapshot(firstOutput.getItemStackHandler(null));
                    List<ItemStack> secondOutputBeforeSimulation = snapshot(secondOutput.getItemStackHandler(null));
                    long energyBeforeSimulation = energy.energyStorage().getAmountAsLong();
                    Map<String, DataValue> dataBeforeSimulation = storage.storage().values();
                    MachineIoPlan.Simulation simulation = plan.simulate();
                    helper.assertTrue(simulation.inputsSatisfied() && simulation.energySatisfied(),
                            "Tick simulation accepts the complete input and energy plan");
                    helper.assertTrue(simulation.outputs().size() == 1
                                    && simulation.outputs().getFirst().requested() == 3L
                                    && simulation.outputs().getFirst().accepted() == 1L
                                    && simulation.outputs().getFirst().fit() == OutputFit.PARTIAL,
                            "Tick simulation reports the one-item partial output fit");
                    helper.assertTrue(sameStacks(firstInputBeforeSimulation, snapshot(firstInput.getItemStackHandler(null)))
                                    && sameStacks(secondInputBeforeSimulation, snapshot(secondInput.getItemStackHandler(null)))
                                    && sameStacks(firstOutputBeforeSimulation, snapshot(firstOutput.getItemStackHandler(null)))
                                    && sameStacks(secondOutputBeforeSimulation, snapshot(secondOutput.getItemStackHandler(null)))
                                    && energy.energyStorage().getAmountAsLong() == energyBeforeSimulation
                                    && dataBeforeSimulation.equals(storage.storage().values()),
                            "Tick simulation leaves input, energy, output, and DataStorage state unchanged");
                    MachineIoPlan.CommitResult commit = plan.commit(transaction ->
                            storage.storage().set("ticks", DataValue.of(1L), transaction));
                    helper.assertTrue(commit.successful(), "Tick commit succeeds with the shared transaction");
                }).build());

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(tickMachine);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Real Tick machine forms with all I/O parts");
            helper.assertTrue(executed.get(), "Formed Tick machine invokes its server callback");
            helper.assertTrue(count(firstInput.getItemStackHandler(null), Items.IRON_INGOT)
                            + count(secondInput.getItemStackHandler(null), Items.IRON_INGOT) == 0L,
                    "Tick commit consumes both input buses according to the complete plan");
            helper.assertTrue(energy.energyStorage().getAmountAsLong() == 0L,
                    "Tick commit consumes the complete energy plan");
            helper.assertTrue(firstOutput.getItemHandler(null).getStackInSlot(0).getCount() == 64
                            && count(firstOutput.getItemStackHandler(null), Items.GOLD_NUGGET) - 63L == 1L
                            && count(secondOutput.getItemStackHandler(null), Items.GOLD_NUGGET) == 0L,
                    "Partial output commit writes only the accepted item");
            helper.assertTrue(storage.storage().get("ticks").map(DataValue.of(1L)::equals).orElse(false),
                    "DataStorage writes commit with the Tick I/O transaction");
            helper.succeed();
        });
    }

    private static void fillOutput(ItemOutputBusBlockEntity output, int retainedGoldSlot) {
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            if (slot != retainedGoldSlot) {
                output.getItemStackHandler(null).setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
        }
    }

    private static long count(ItemStackHandler handler, Item item) {
        long amount = 0L;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private static List<ItemStack> snapshot(ItemStackHandler handler) {
        List<ItemStack> stacks = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            stacks.add(handler.getStackInSlot(slot).copy());
        }
        return List.copyOf(stacks);
    }

    private static boolean sameStacks(List<ItemStack> expected, List<ItemStack> actual) {
        if (expected.size() != actual.size()) return false;
        for (int slot = 0; slot < expected.size(); slot++) {
            ItemStack expectedStack = expected.get(slot);
            ItemStack actualStack = actual.get(slot);
            if (expectedStack.isEmpty() || actualStack.isEmpty()) {
                if (expectedStack.isEmpty() != actualStack.isEmpty()) return false;
                continue;
            }
            if (expectedStack.getCount() != actualStack.getCount()
                    || !ItemStack.isSameItemSameComponents(expectedStack, actualStack)) return false;
        }
        return true;
    }

    private static MachineControllerRuntime runtime(MachineControllerBlockEntity controller) {
        try {
            Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (MachineControllerRuntime) field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect controller text snapshot", exception);
        }
    }

    private static ServerPlayer observer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-controller-tick-observer"),
                ClientInformation.createDefault());
        player.connection = new RecordingConnection(server, player);
        return player;
    }

    private static int screenTextPackets(ServerPlayer player) {
        return (int) ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktControllerScreenTextPayload)
                .count();
    }

    private static PktControllerScreenTextPayload lastScreenTextPacket(ServerPlayer player) {
        return ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktControllerScreenTextPayload)
                .map(packet -> (PktControllerScreenTextPayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private static void applyLastScreenTextPacket(ServerPlayer player) {
        PktControllerScreenTextPayload payload = lastScreenTextPacket(player);
        ControllerScreenTextCache.replace(payload.controllerPos(), payload.revision(), payload.lines());
    }

    private static boolean hasText(List<ControllerScreenTextSnapshot.Line> lines, String text) {
        return lines.stream().anyMatch(line -> line.text().getString().equals(text));
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
                    CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mmcr-controller-tick-connection"), false));
        }

        @Override
        public void send(Packet<?> packet) {
            packets.add(packet);
        }
    }
}
