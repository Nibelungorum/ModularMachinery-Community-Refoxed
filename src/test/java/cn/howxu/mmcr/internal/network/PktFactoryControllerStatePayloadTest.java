package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureTrace;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.internal.sync.FailureStatusCodec;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the final factory snapshot payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktFactoryControllerStatePayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void host_module_count_and_role_round_trip_with_legacy_constructor_compatibility() {
        FactorySnapshot old = snapshot(1);
        FactorySnapshot host = new FactorySnapshot(old.formed(), old.active(), old.lanes(), old.laneLimit(),
                old.activeLaneCount(), old.maxParallelism(), old.paused(), old.presentationLanes(), old.machineName(),
                old.parallelSlots(), old.failure(), old.foundLevelIds(), old.matchedStage(), old.stageCount(),
                "mmcr:host", "mmcr:pool", 1, "", 7);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer,
                    new PktFactoryControllerStatePayload(BlockPos.ZERO, host));
            assertThat(PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer).snapshot()).isEqualTo(host);
            assertThat(buffer.readableBytes()).isZero();
            assertThat(old.installedModuleCount()).isZero();
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoder_rejects_negative_installed_module_count() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer,
                    new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot(1)));
            buffer.writerIndex(buffer.writerIndex() - 1);
            buffer.writeVarInt(-1);
            assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            buffer.release();
        }
    }

    @Test
    void supported_thread_counts_round_trip_without_truncation() {
        for (int count : List.of(1, 65, 128)) {
            FactorySnapshot snapshot = snapshot(count);
            RegistryFriendlyByteBuf buffer = buffer();

            PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer,
                    new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot));

            assertThat(PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer).snapshot())
                    .isEqualTo(snapshot);
            buffer.release();
        }
    }

    @Test
    void long_parallelism_values_round_trip_without_truncation() {
        long parallelism = Long.MAX_VALUE;
        CraftingStateSnapshot lane = new CraftingStateSnapshot(MMCR.id("long_lane"), CraftingStatus.working(),
                null, 0L, 0L, 0L, 1, 20, parallelism, parallelism);
        FactoryRuntime.ThreadSnapshot thread = new FactoryRuntime.ThreadSnapshot(0, "base", true, false, true,
                "mmcr:long_lane", 1, 20, parallelism, (ExecutionStatus) null);
        FactorySnapshot snapshot = new FactorySnapshot(false, true, List.of(lane), 1, 1, parallelism,
                false, List.of(thread), "", 0, null, List.of(), 0, 1);
        RegistryFriendlyByteBuf buffer = buffer();

        PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer,
                new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot));

        assertThat(PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer).snapshot())
                .isEqualTo(snapshot);
        buffer.release();
    }

    @Test
    void factory_snapshot_round_trip_preserves_typed_failures_in_snapshot_lanes_and_threads() {
        ExecutionStatus failure = failure(2);
        CraftingStateSnapshot lane = new CraftingStateSnapshot(MMCR.id("factory_recipe"), CraftingStatus.working(),
                failure, 0L, 0L, 0L, 0, 0, 0L, 1L);
        FactoryRuntime.ThreadSnapshot thread = new FactoryRuntime.ThreadSnapshot(0, "base", true, false, false,
                "", 0, 0, 1, failure);
        FactorySnapshot snapshot = new FactorySnapshot(false, false, List.of(lane), 1, 0, 1L,
                false, List.of(thread), "", 0, failure, List.of(), 0, 1);
        RegistryFriendlyByteBuf buffer = buffer();

        PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer,
                new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot));

        FactorySnapshot decoded = PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer).snapshot();
        assertThat(decoded.failure().source()).isEqualTo(failure.source());
        assertThat(decoded.failure().reason()).isEqualTo(failure.reason());
        assertThat(decoded.failure().failure().trace().frames())
                .containsExactlyElementsOf(failure.failure().trace().frames());
        assertThat(decoded.failure().details()).containsExactlyInAnyOrderEntriesOf(failure.details());
        assertThat(decoded.lanes().getFirst().failure()).isEqualTo(failure);
        assertThat(decoded.presentationLanes().getFirst().failure()).isEqualTo(failure);
        buffer.release();
    }

    @Test
    void decoder_rejects_oversized_thread_list_before_allocation() {
        RegistryFriendlyByteBuf buffer = header(1, 0, 1L, 0, PktFactoryControllerStatePayload.MAX_THREAD_SNAPSHOTS + 1);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void encoder_rejects_oversized_thread_snapshot() {
        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(BlockPos.ZERO,
                        snapshot(PktFactoryControllerStatePayload.MAX_THREAD_SNAPSHOTS + 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_oversized_machine_level_snapshot() {
         FactorySnapshot snapshot = new FactorySnapshot(false, false, List.of(), 1, 0, 1L,
                false, List.of(FactoryRuntime.ThreadSnapshot.idleBase()), "", 0, null,
                IntStream.range(0, PktFactoryControllerStatePayload.MAX_LEVEL_SNAPSHOTS + 1)
                        .mapToObj(Integer::toString).toList(), 0, 1);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_oversized_failure_details() {
        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(BlockPos.ZERO,
                        snapshot(failure(FailureStatusCodec.MAX_DETAILS + 1)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_oversized_failure_detail_count_before_allocation() {
        RegistryFriendlyByteBuf buffer = buffer();
        writeFactoryHeader(buffer, 1, 0, 1L, 0, "");
        buffer.writeBoolean(true);
        buffer.writeUtf("mmcr:failure");
        buffer.writeVarInt(StatusSeverity.BLOCKED.ordinal());
        buffer.writeUtf("mmcr:source");
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeVarInt(FailureStatusCodec.MAX_DETAILS + 1);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void encoder_rejects_incomplete_presentation_lanes() {
         FactorySnapshot snapshot = new FactorySnapshot(false, false, List.of(), 2, 0, 1L,
                false, List.of(FactoryRuntime.ThreadSnapshot.idleBase()), "", 0, null, List.of(), 0, 1);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_active_lane_count_above_lane_limit() {
        RegistryFriendlyByteBuf buffer = header(1, 2, 1L, 0, 1);
        writeThread(buffer, 0);
        appendStageFooter(buffer);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void decoder_rejects_invalid_thread_progress_and_parallelism() {
        for (int[] values : List.of(new int[]{-1, 0, 1}, new int[]{2, 1, 1}, new int[]{0, 1, 0})) {
            RegistryFriendlyByteBuf buffer = header(1, 0, 1L, 0, 1);
            writeThread(buffer, 0, values[0], values[1], values[2]);
            appendStageFooter(buffer);

            assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                    .isInstanceOf(IllegalArgumentException.class);
            buffer.release();
        }
    }

    @Test
    void decoder_rejects_duplicate_thread_indexes() {
            RegistryFriendlyByteBuf buffer = header(2, 0, 1L, 0, 2);
        writeThread(buffer, 0);
        writeThread(buffer, 0);
        appendStageFooter(buffer);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void decoder_rejects_oversized_strings() {
        RegistryFriendlyByteBuf buffer = buffer();
        writeFactoryHeader(buffer, 1, 0, 1L, 0,
                "x".repeat(PktFactoryControllerStatePayload.MAX_STRING_LENGTH + 1));

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(RuntimeException.class);
        buffer.release();
    }

    private static FactorySnapshot snapshot(int count) {
         return new FactorySnapshot(false, false, List.of(), count, 0, 1L, false,
                 IntStream.range(0, count).mapToObj(index -> new FactoryRuntime.ThreadSnapshot(index,
                         index == 0 ? "base" : "factory-" + index, index == 0, false, false,
                         "", 0, 0, 1, (ExecutionStatus) null)).toList(),
                 "", 0, null, List.of(), 0, 1);
    }

    private static FactorySnapshot snapshot(ExecutionStatus failure) {
         return new FactorySnapshot(false, false, List.of(), 1, 0, 1L, false,
                List.of(FactoryRuntime.ThreadSnapshot.idleBase()), "", 0, failure, List.of(), 0, 1);
    }

    private static ExecutionStatus failure(int detailCount) {
        return failure(BuiltinFailureReasons.MISSING_OUTPUT, detailCount);
    }

    private static ExecutionStatus failure(FailureReason reason, int detailCount) {
        Map<String, String> details = IntStream.range(0, detailCount)
                .boxed().collect(Collectors.toMap(String::valueOf, String::valueOf,
                        (left, right) -> left, LinkedHashMap::new));
        FailureTrace trace = new FailureTrace(List.of(
                new FailureTrace.Frame(MMCR.id("factory_planner"), FailurePhase.REQUIREMENT_PLAN,
                        MMCR.id("factory_recipe"), 1),
                new FailureTrace.Frame(MMCR.id("factory_controller"), FailurePhase.RUNTIME, null, null)));
        return ExecutionStatus.blocked(MMCR.id("payload_failure"), MMCR.id("payload_source"),
                new FailureOccurrence(reason, trace, details));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static RegistryFriendlyByteBuf header(int laneLimit, int activeLaneCount,
                                                   long maxParallelism, int parallelSlots, int threadCount) {
        RegistryFriendlyByteBuf buffer = buffer();
        writeFactoryHeader(buffer, laneLimit, activeLaneCount, maxParallelism, parallelSlots, "");
        FailureStatusCodec.write(buffer, null);
        buffer.writeVarInt(0);
        buffer.writeVarInt(threadCount);
        return buffer;
    }

    private static void appendStageFooter(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeUtf("");
        buffer.writeUtf("");
        buffer.writeVarInt(0);
        buffer.writeUtf("");
        buffer.writeVarInt(0);
    }

    private static void writeFactoryHeader(RegistryFriendlyByteBuf buffer, int laneLimit, int activeLaneCount,
                                           long maxParallelism, int parallelSlots, String machineName) {
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeVarInt(laneLimit);
        buffer.writeVarInt(activeLaneCount);
        buffer.writeLong(maxParallelism);
        buffer.writeBoolean(false);
        buffer.writeUtf(machineName);
        buffer.writeVarInt(parallelSlots);
        buffer.writeVarInt(0);
    }

    private static void writeThread(RegistryFriendlyByteBuf buffer, int index) {
        writeThread(buffer, index, 0, 0, 1);
    }

    private static void writeThread(RegistryFriendlyByteBuf buffer, int index, int tick, int totalTick,
                                    long parallelism) {
        buffer.writeVarInt(index);
        buffer.writeUtf(index == 0 ? "base" : "factory-" + index);
        buffer.writeBoolean(index == 0);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeUtf("");
        buffer.writeVarInt(tick);
        buffer.writeVarInt(totalTick);
        buffer.writeLong(parallelism);
        FailureStatusCodec.write(buffer, null);
        ControllerRecipePresentation.write(buffer, ControllerRecipePresentation.empty());
    }
}
