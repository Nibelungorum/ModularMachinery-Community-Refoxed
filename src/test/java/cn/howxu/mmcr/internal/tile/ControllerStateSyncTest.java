package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailureTrace;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.network.PktMachineProgressPayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies per-player baseline ownership and client runtime progress merges.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerStateSyncTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void unchanged_idle_state_initializes_later_entrants_and_range_reentry() throws Exception {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        var idle = payload("", 0, 0);
        ServerPlayer first = player();
        ServerPlayer later = player();
        controller.broadcastState(List.of(first), idle);
        controller.broadcastState(List.of(first, later), idle);
        assertThat(packets(first)).containsExactly(idle);
        assertThat(packets(later)).containsExactly(idle);
        controller.broadcastState(List.of(later), idle);
        controller.broadcastState(List.of(first, later), idle);
        assertThat(packets(first)).containsExactly(idle, idle);
    }

    @Test
    void progress_goes_only_to_initialized_players_and_reconnect_does_not_reuse_uuid_baseline() throws Exception {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        var initial = payload("mmcr:recipe", 1, 20);
        var progressed = initial.withProgress(2, 20);
        ServerPlayer first = player();
        ServerPlayer later = player();
        controller.broadcastState(List.of(first), initial);
        controller.broadcastState(List.of(first, later), progressed);
        assertThat(packets(first)).containsExactly(initial,
                new PktMachineProgressPayload(BlockPos.ZERO, "mmcr:recipe", 2, 20));
        assertThat(packets(later)).containsExactly(progressed);
        ServerPlayer reconnected = player();
        UUID uuid = UUID.randomUUID();
        Field uuidField = net.minecraft.world.entity.Entity.class.getDeclaredField("uuid");
        uuidField.setAccessible(true);
        uuidField.set(first, uuid);
        uuidField.set(reconnected, uuid);
        controller.broadcastState(List.of(reconnected, later), progressed);
        assertThat(packets(reconnected)).containsExactly(progressed);
        controller.forgetClientStateBaseline(later);
        controller.broadcastState(List.of(reconnected, later), progressed);
        assertThat(packets(later)).containsExactly(progressed, progressed);
    }

    @Test
    void chunk_unload_removal_and_replacement_clear_server_receiver_baselines() throws Exception {
        var idle = payload("", 0, 0);
        ServerPlayer player = player();
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        controller.broadcastState(List.of(player), idle);
        controller.onChunkUnloaded();
        controller.broadcastState(List.of(player), idle);
        controller.setRemoved();
        controller.clearRemoved();
        controller.broadcastState(List.of(player), idle);
        TestBootstrap.newController().broadcastState(List.of(player), idle);
        assertThat(packets(player)).containsExactly(idle, idle, idle, idle);
    }

    @Test
    void client_progress_merges_only_after_full_baseline_and_keeps_runtime_state_and_versions() throws Exception {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        Level level = LevelStub.createWithBlockEntities(List.of(controller));
        Field clientSide = Level.class.getDeclaredField("isClientSide");
        clientSide.setAccessible(true);
        clientSide.set(level, true);
        controller.setLevel(level);
        controller.applyClientProgress("mmcr:recipe", 7, 20);
        assertThat(controller.runtimeSnapshot().crafting().tick()).isZero();
        ExecutionStatus failure = ExecutionStatus.blocked(MMCR.id("failure"), MMCR.id("source"),
                new FailureOccurrence(BuiltinFailureReasons.MISSING_INPUT, new FailureTrace(List.of()), Map.of()));
        controller.applyClientState("mmcr:recipe", false, false, List.of(), null, 2, 3, true,
                MMCR.id("host"), CraftingStatus.failure("test.failure"), failure, true, 1, 20,
                Long.MAX_VALUE, Long.MAX_VALUE, Map.of("mode", DataValue.of("running")));
        controller.componentRuntime().replaceModifiers(Map.of("progress", List.of(MachineModifier.parallelized(true))));
        var levelId = MMCR.id("progress_level");
        controller.componentRuntime().replaceLevels(Map.of(levelId, new MachineLevel(levelId,
                MMCR.id("progress_level_type"), 1, new BlockPredicate.Any(), ItemStack.EMPTY,
                ModifierDefinition.EMPTY)));
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, controller,
                BlockPos.ZERO, BlockPos.ZERO, List.of("progress"))));
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        ((MachineControllerRuntime) runtimeField.get(controller)).publishSnapshot();
        var before = controller.runtimeSnapshot();
        assertThat(before.foundModifiers()).isNotEmpty();
        assertThat(before.foundLevels()).isNotEmpty();
        assertThat(before.componentPresentations()).isNotEmpty();
        controller.applyClientProgress("mmcr:other_recipe", 7, 20);
        controller.applyClientProgress("mmcr:recipe", 7, 21);
        assertThat(controller.runtimeSnapshot()).isSameAs(before);
        controller.applyClientProgress("mmcr:recipe", 7, 20);
        var after = controller.runtimeSnapshot();
        assertThat(after.foundModifiers()).isSameAs(before.foundModifiers());
        assertThat(after.foundLevels()).isSameAs(before.foundLevels());
        assertThat(after.dataStorageValues()).isSameAs(before.dataStorageValues());
        assertThat(after.componentPresentations()).isSameAs(before.componentPresentations());
        assertThat(after.capabilityPresentations()).isSameAs(before.capabilityPresentations());
        assertThat(after.structure()).isSameAs(before.structure());
        assertThat(before.crafting().tick()).isEqualTo(1);
        assertThat(after.crafting().tick()).isEqualTo(7);
        assertThat(after.crafting().totalTick()).isEqualTo(20);
        assertThat(after.crafting().recipeId()).isEqualTo(before.crafting().recipeId());
        assertThat(after.crafting().status()).isEqualTo(before.crafting().status());
        assertThat(after.crafting().failure()).isSameAs(failure);
        assertThat(after.crafting().parallelism()).isEqualTo(Long.MAX_VALUE);
        assertThat(after.crafting().structureVersion()).isEqualTo(before.crafting().structureVersion());
        assertThat(after.crafting().capabilityVersion()).isEqualTo(before.crafting().capabilityVersion());
        assertThat(after.crafting().modifierVersion()).isEqualTo(before.crafting().modifierVersion());
        assertThat(after.structure()).isEqualTo(before.structure());
        assertThat(after.moduleConnectionStatus()).isEqualTo(before.moduleConnectionStatus());
        assertThat(after.installedModuleCount()).isEqualTo(3);
        assertThat(after.dataStorageValues()).isEqualTo(before.dataStorageValues());
        controller.onChunkUnloaded();
        controller.onLoad();
        controller.applyClientProgress("mmcr:recipe", 8, 20);
        assertThat(controller.runtimeSnapshot()).isSameAs(after);
        controller.applyClientState("mmcr:recipe", false, false, List.of(), null, 0, 0, false,
                null, CraftingStatus.working(), null, true, 2, 20, 1L, 1L, Map.of());
        controller.applyClientProgress("mmcr:recipe", 8, 20);
        assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(8);
        controller.setRemoved();
        controller.applyClientProgress("mmcr:recipe", 9, 20);
        assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(8);
    }

    private static PktMachineStatePayload payload(String recipe, int tick, int totalTick) {
        return new PktMachineStatePayload(BlockPos.ZERO, recipe, false, !recipe.isEmpty(), List.of(),
                "mmcr:machine", 0, 0, false, "", CraftingStatus.Status.IDLE, "", null, true, false,
                tick, totalTick, 1L, 1L, false, 0, 0, 0, 0L, Map.of(), 0, 1);
    }

    private static ServerPlayer player() throws Exception {
        Unsafe unsafe = unsafe();
        ServerPlayer player = (ServerPlayer) unsafe.allocateInstance(ServerPlayer.class);
        RecordingConnection connection = (RecordingConnection) unsafe.allocateInstance(RecordingConnection.class);
        connection.packets = new ArrayList<>();
        player.connection = connection;
        return player;
    }

    private static List<CustomPacketPayload> packets(ServerPlayer player) {
        return ((RecordingConnection) player.connection).packets;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private List<CustomPacketPayload> packets;

        private RecordingConnection() { super(null, null, null, null); }

        @Override
        public void send(Packet<?> packet) {
            packets.add(((ClientboundCustomPayloadPacket) packet).payload());
        }
    }
}
