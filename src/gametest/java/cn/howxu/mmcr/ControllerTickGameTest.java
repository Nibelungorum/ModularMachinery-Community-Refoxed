package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

public class ControllerTickGameTest {

    public void structureForms3x3Casing(GameTestHelper helper) {
        Identifier machineId = MMCR.id("controller_tick");
        helper.assertTrue(MachineRegistry.getMachine(machineId) != null,
                "GameTest startup installs the machine registry entry");
        helper.assertTrue(cn.howxu.mmcr.api.machine.MachineStructureRegistry.effectiveSnapshot().containsKey(machineId),
                "GameTest startup installs the effective structure");
        helper.assertTrue(!MachineRegistry.getCompiledStages(machineId).isEmpty(),
                "GameTest startup compiles the effective structure");

        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());

        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(machineId).get().defaultBlockState());

        var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.serverTick();
        helper.assertTrue(controller.boundMachine().isPresent(), "Controller binds the startup machine");
        controller.setMachine(MachineRegistry.getMachine(machineId));
        ServerPlayer observer = observer(helper);
        observer.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        helper.getLevel().players().add(observer);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Structure formed after bounded scan");
            int payloadsBeforeFirst = screenTextPackets(observer);
            runtime(controller).runtimeContext().screenText().append(ControllerScreenTextScope.CONTROLLER,
                    MMCR.id("controller_tick_status"), Component.literal("forming complete"));
            controller.serverTick();
            ControllerScreenTextSnapshot first = screenTextSnapshot(controller);
            helper.assertTrue(first.lines().size() == 1
                            && first.lines().getFirst().text().getString().equals("forming complete"),
                    "formed controller publishes controller-scoped text");
            helper.assertTrue(screenTextPackets(observer) == payloadsBeforeFirst + 1,
                    "formed controller sends the first text snapshot to the active menu observer");

            int payloadsBeforeUpdate = screenTextPackets(observer);
            runtime(controller).runtimeContext().screenText().append(ControllerScreenTextScope.CONTROLLER,
                    MMCR.id("controller_tick_status"), Component.literal("updated"));
            controller.serverTick();
            ControllerScreenTextSnapshot updated = screenTextSnapshot(controller);
            helper.assertTrue(updated.lines().size() == 1
                            && updated.lines().getFirst().text().getString().equals("updated"),
                    "controller-scoped keyed text replaces the previous line");
            helper.assertTrue(screenTextPackets(observer) == payloadsBeforeUpdate + 1,
                    "updated controller text sends one replacement snapshot");
            long unchangedRevision = updated.revision();
            int payloadCount = screenTextPackets(observer);
            controller.serverTick();
            helper.assertTrue(screenTextSnapshot(controller).revision() == unchangedRevision
                            && screenTextPackets(observer) == payloadCount,
                    "unchanged controller text does not mutate or resend a payload");

            int payloadsBeforeReset = screenTextPackets(observer);
            controller.invalidateFormedStructure();
            helper.assertTrue(screenTextSnapshot(controller).lines().isEmpty()
                            && screenTextPackets(observer) == payloadsBeforeReset + 1
                            && lastScreenTextPacket(observer).lines().isEmpty(),
                    "reset clears controller-scoped text and synchronizes the empty snapshot");
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

    private static ControllerScreenTextSnapshot screenTextSnapshot(MachineControllerBlockEntity controller) {
        return runtime(controller).screenText().snapshot();
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
