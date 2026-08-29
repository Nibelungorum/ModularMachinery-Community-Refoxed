package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
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
    @Test
    void supported_machine_state_round_trips_without_field_shift() {
        PktMachineStatePayload payload = payload(List.of("mmcr:steel"), failure(1));
        RegistryFriendlyByteBuf buffer = buffer();

        PktMachineStatePayload.STREAM_CODEC.encode(buffer, payload);

        assertThat(PktMachineStatePayload.STREAM_CODEC.decode(buffer)).isEqualTo(payload);
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
    void encoder_rejects_oversized_machine_level_snapshot() {
        assertThatThrownBy(() -> PktMachineStatePayload.STREAM_CODEC.encode(buffer(),
                payload(IntStream.range(0, PktMachineStatePayload.MAX_LEVEL_SNAPSHOTS + 1)
                        .mapToObj(Integer::toString).toList(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_oversized_machine_failure_details() {
        assertThatThrownBy(() -> PktMachineStatePayload.STREAM_CODEC.encode(buffer(),
                payload(List.of(), failure(PktMachineStatePayload.MAX_FAILURE_DETAIL_ENTRIES + 1))))
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
                0, 0, 0, 1, false, 0, 0, 0, 0, 0L, 0L, FluidStack.EMPTY, FluidStack.EMPTY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktMachineStatePayload(BlockPos.ZERO, "", false, false, List.of(), false, "",
                "", 0, PktMachineStatePayload.MAX_INSTALLED_MODULES + 1, false, "", CraftingStatus.Status.IDLE,
                "", null, true, false, 0, 0, 0, 1, false, 0, 0, 0, 0, 0L, 0L,
                FluidStack.EMPTY, FluidStack.EMPTY)).isInstanceOf(IllegalArgumentException.class);
    }

    private static PktMachineStatePayload payload(List<String> levels, ExecutionStatus failure) {
        return payload(1L, 1L, 0L, levels, failure);
    }

    private static PktMachineStatePayload payload(long parallelism, long maxParallelism,
                                                  long maxParallelControllerCount) {
        return payload(parallelism, maxParallelism, maxParallelControllerCount, List.of(), null);
    }

    private static PktMachineStatePayload payload(long parallelism, long maxParallelism,
                                                  long maxParallelControllerCount, List<String> levels,
                                                  ExecutionStatus failure) {
        return new PktMachineStatePayload(BlockPos.ZERO, "mmcr:recipe", true, true, levels, false, "",
                "mmcr:machine", 0, 0, false, "", CraftingStatus.Status.IDLE,
                "", failure, true, false, 0, 10, parallelism, maxParallelism, false, 0, 0, 0,
                maxParallelControllerCount, 0L, 0L,
                FluidStack.EMPTY, FluidStack.EMPTY);
    }

    private static ExecutionStatus failure(int detailCount) {
        return new ExecutionStatus(MMCR.id("payload_failure"), StatusSeverity.BLOCKED,
                MMCR.id("payload_source"), IntStream.range(0, detailCount)
                        .boxed().collect(Collectors.toMap(String::valueOf, String::valueOf,
                                (left, right) -> left, LinkedHashMap::new)));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }
}
