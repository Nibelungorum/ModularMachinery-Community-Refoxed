package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.api.capability.status.FailureTrace;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.sync.FailureStatusCodec;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.connection.ConnectionType;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the machine state payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktMachineStatePayloadTest {
    private static final FailureReason CUSTOM_REASON = new FailureReason(
            Identifier.fromNamespaceAndPath("mmcr_test", "custom_packet_reason"),
            "gui.mmcr.failure.custom_packet_reason", 10);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        if (FailureReasonRegistry.isFrozen()) FailureReasonRegistry.clearForTesting();
        if (FailureReasonRegistry.find(BuiltinFailureReasons.UNKNOWN.id()) == null) {
            BuiltinFailureReasons.register();
        }
        if (FailureReasonRegistry.find(CUSTOM_REASON.id()) == null) {
            FailureReasonRegistry.register(CUSTOM_REASON);
        }
    }

    @Test
    void supported_machine_state_round_trips_without_field_shift() {
        PktMachineStatePayload payload = payload(List.of("mmcr:steel"), failure(1));
        RegistryFriendlyByteBuf buffer = buffer();

        PktMachineStatePayload.STREAM_CODEC.encode(buffer, payload);

        PktMachineStatePayload decoded = PktMachineStatePayload.STREAM_CODEC.decode(buffer);
        assertThat(decoded).isEqualTo(payload);
        assertThat(decoded.failure().source()).isEqualTo(payload.failure().source());
        assertThat(decoded.failure().reason()).isEqualTo(payload.failure().reason());
        assertThat(decoded.failure().failure().trace().frames())
                .containsExactlyElementsOf(payload.failure().failure().trace().frames());
        assertThat(decoded.failure().details()).containsExactlyInAnyOrderEntriesOf(payload.failure().details());
        buffer.release();
    }

    @Test
    void registered_custom_reason_keeps_its_translation_key_in_the_client_snapshot() {
        PktMachineStatePayload payload = payload(List.of(), failure(CUSTOM_REASON, 1));
        RegistryFriendlyByteBuf buffer = buffer();

        PktMachineStatePayload.STREAM_CODEC.encode(buffer, payload);

        ExecutionStatus decoded = PktMachineStatePayload.STREAM_CODEC.decode(buffer).failure();
        assertThat(decoded.reason()).isEqualTo(CUSTOM_REASON);
        assertThat(decoded.reason().translationKey()).isEqualTo(CUSTOM_REASON.translationKey());
        buffer.release();
    }

    @Test
    void unknown_reason_uses_the_unknown_translation_key_in_the_client_snapshot() {
        FailureReason unknown = new FailureReason(
                Identifier.fromNamespaceAndPath("legacy", "removed_machine_reason"),
                "gui.mmcr.failure.removed_machine_reason");
        PktMachineStatePayload payload = payload(List.of(), failure(unknown, 1));
        RegistryFriendlyByteBuf buffer = buffer();

        PktMachineStatePayload.STREAM_CODEC.encode(buffer, payload);

        ExecutionStatus decoded = PktMachineStatePayload.STREAM_CODEC.decode(buffer).failure();
        assertThat(decoded.reason()).isSameAs(BuiltinFailureReasons.UNKNOWN);
        assertThat(decoded.reason().translationKey()).isEqualTo("gui.mmcr.failure.unknown");
        buffer.release();
    }

    @Test
    void long_parallelism_values_round_trip_without_truncation() {
        PktMachineStatePayload payload = payload(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        RegistryFriendlyByteBuf buffer = buffer();

        PktMachineStatePayload.STREAM_CODEC.encode(buffer, payload);

        assertThat(PktMachineStatePayload.STREAM_CODEC.decode(buffer)).isEqualTo(payload);
        buffer.release();
    }

    @Test
    void effective_recipe_presentation_round_trips_duration_and_parallelism() {
        PktMachineStatePayload base = payload(List.of(), null);
        ControllerRecipePresentation presentation = new ControllerRecipePresentation(List.of(), 2L, 3L, 4D,
                37, 9L);
        PktMachineStatePayload payload = new PktMachineStatePayload(base.pos(), base.recipeName(), base.formed(),
                base.active(), base.foundLevelIds(), base.recipeLocked(), base.lockedRecipeId(), base.machineId(),
                base.controllerRole(), base.installedModuleCount(), base.moduleConnected(), base.connectedHostId(),
                base.craftingStatus(), base.craftingMessage(), base.failure(), base.structureAreaLoaded(),
                base.redstonePaused(), base.tick(), base.totalTick(), base.parallelism(), base.maxParallelism(),
                base.factoryControllerPresent(), base.factoryThreadCount(), base.activeFactoryThreadCount(),
                base.parallelControllerCount(), base.maxParallelControllerCount(), base.dataStorageValues(),
                 base.matchedStage(), base.stageCount(), presentation, base.recipePoolId());
        RegistryFriendlyByteBuf buffer = buffer();

        PktMachineStatePayload.STREAM_CODEC.encode(buffer, payload);

        assertThat(PktMachineStatePayload.STREAM_CODEC.decode(buffer).recipePresentation()).isEqualTo(presentation);
        buffer.release();
    }

    @Test
    void encoder_rejects_oversized_machine_level_snapshot() {
        assertThatThrownBy(() -> PktMachineStatePayload.STREAM_CODEC.encode(buffer(),
                payload(IntStream.range(0, PktMachineStatePayload.MAX_LEVEL_SNAPSHOTS + 1)
                        .mapToObj(Integer::toString).toList(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_oversized_machine_failure_details() {
        assertThatThrownBy(() -> PktMachineStatePayload.STREAM_CODEC.encode(buffer(),
                payload(List.of(), failure(FailureStatusCodec.MAX_DETAILS + 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_oversized_machine_level_count_before_allocation() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeUtf("");
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeVarInt(PktMachineStatePayload.MAX_LEVEL_SNAPSHOTS + 1);

        assertThatThrownBy(() -> PktMachineStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void machine_state_rejects_negative_or_oversized_installed_module_count() {
        assertThatThrownBy(() -> new PktMachineStatePayload(BlockPos.ZERO, "", false, false, List.of(), false, "",
                "", 0, -1, false, "", CraftingStatus.Status.IDLE, "", null, true, false,
                0, 0, 0, 1, false, 0, 0, 0, 0, Map.of(),
                0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktMachineStatePayload(BlockPos.ZERO, "", false, false, List.of(), false, "",
                "", 0, PktMachineStatePayload.MAX_INSTALLED_MODULES + 1, false, "", CraftingStatus.Status.IDLE,
                "", null, true, false, 0, 0, 0, 1, false, 0, 0, 0, 0,
                Map.of(), 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dataStorageChangeMarksMachineStateChanged() {
        PktMachineStatePayload before = payload(List.of(), null,
                Map.of("mode", DataValue.of("idle")));
        PktMachineStatePayload after = payload(List.of(), null,
                Map.of("mode", DataValue.of("running")));

        assertThat(PktMachineStatePayload.stateChanged(after, before)).isTrue();
    }

    private static PktMachineStatePayload payload(List<String> levels, ExecutionStatus failure) {
        return payload(levels, failure, Map.of());
    }

    private static PktMachineStatePayload payload(List<String> levels, ExecutionStatus failure,
                                                   Map<String, DataValue> dataStorageValues) {
        return payload(1L, 1L, 0L, levels, failure, dataStorageValues);
    }

    private static PktMachineStatePayload payload(long parallelism, long maxParallelism,
                                                  long maxParallelControllerCount) {
        return payload(parallelism, maxParallelism, maxParallelControllerCount, List.of(), null, Map.of());
    }

    private static PktMachineStatePayload payload(long parallelism, long maxParallelism,
                                                  long maxParallelControllerCount, List<String> levels,
                                                  ExecutionStatus failure) {
        return payload(parallelism, maxParallelism, maxParallelControllerCount, levels, failure, Map.of());
    }

    private static PktMachineStatePayload payload(long parallelism, long maxParallelism,
                                                  long maxParallelControllerCount, List<String> levels,
                                                  ExecutionStatus failure, Map<String, DataValue> dataStorageValues) {
        return new PktMachineStatePayload(BlockPos.ZERO, "mmcr:recipe", true, true, levels, false, "",
                "mmcr:machine", 0, 0, false, "", CraftingStatus.Status.IDLE,
                "", failure, true, false, 0, 10, parallelism, maxParallelism, false, 0, 0, 0,
                maxParallelControllerCount, dataStorageValues, 0, 1, "mmcr:pool");
    }

    private static ExecutionStatus failure(int detailCount) {
        return failure(BuiltinFailureReasons.MISSING_INPUT, detailCount);
    }

    private static ExecutionStatus failure(FailureReason reason, int detailCount) {
        Map<String, String> details = IntStream.range(0, detailCount)
                .boxed().collect(Collectors.toMap(String::valueOf, String::valueOf,
                        (left, right) -> left, LinkedHashMap::new));
        FailureTrace trace = new FailureTrace(List.of(
                new FailureTrace.Frame(MMCR.id("payload_planner"), FailurePhase.REQUIREMENT_PLAN,
                        MMCR.id("payload_recipe"), 2),
                new FailureTrace.Frame(MMCR.id("payload_controller"), FailurePhase.RUNTIME, null, null)));
        return ExecutionStatus.blocked(MMCR.id("payload_failure"), MMCR.id("payload_source"),
                new FailureOccurrence(reason, trace, details));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }
}
