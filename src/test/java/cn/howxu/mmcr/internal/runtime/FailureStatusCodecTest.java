package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.api.capability.status.FailureTrace;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.internal.sync.FailureStatusCodec;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.MMCR;
import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the typed runtime failure persistence and synchronization codec.
 *
 * @author howxu <dev@howxu.cn>
 */
class FailureStatusCodecTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());
    private static final FailureReason CODEC_REASON = new FailureReason(
            ResourceLocation.fromNamespaceAndPath("mmcr_test", "codec_reason"),
            "gui.mmcr.failure.codec_reason", 10);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void registerReasons() {
        FailureReasonRegistry.clearForTesting();
        BuiltinFailureReasons.register();
        FailureReasonRegistry.register(CODEC_REASON);
    }

    @AfterEach
    void clearReasons() {
        FailureReasonRegistry.clearForTesting();
    }

    @Test
    void value_round_trip_preserves_status_trace_and_details() {
        ExecutionStatus status = fixture();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);

        FailureStatusCodec.write(output, status);

        ExecutionStatus decoded = FailureStatusCodec.read(TagValueInput.create(
                ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()));

        assertThat(decoded).isEqualTo(status);
        assertThat(decoded.failure().trace().frames()).hasSize(2).isUnmodifiable();
        assertThat(decoded.failure().details()).containsExactlyInAnyOrderEntriesOf(status.failure().details());
    }

    @Test
    void network_round_trip_preserves_status_trace_and_details() {
        ExecutionStatus status = fixture();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        FailureStatusCodec.write(buffer, status);

        ExecutionStatus decoded = FailureStatusCodec.read(buffer);

        assertThat(decoded).isEqualTo(status);
    }

    @Test
    void unknown_reason_resolves_to_unknown_and_retains_raw_reason_id() {
        ResourceLocation unknownId = ResourceLocation.fromNamespaceAndPath("legacy", "removed_reason");
        ExecutionStatus status = new ExecutionStatus(MMCR.id("status"),
                StatusSeverity.BLOCKED, MMCR.id("source"),
                new FailureOccurrence(new FailureReason(unknownId, "gui.mmcr.failure.removed"),
                        FailureTrace.single(new FailureTrace.Frame(
                                MMCR.id("source"), FailurePhase.RUNTIME,
                                null, null)),
                        Map.of("required", "1")));
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);

        FailureStatusCodec.write(output, status);

        ExecutionStatus decoded = FailureStatusCodec.read(TagValueInput.create(
                ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()));

        assertThat(decoded.reason()).isSameAs(BuiltinFailureReasons.UNKNOWN);
        assertThat(decoded.details()).containsEntry("raw_reason_id", unknownId.toString());
    }

    @Test
    void invalid_trace_count_is_rejected_before_decoding_frames() {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        output.putBoolean("present", true);
        output.putString("id", "mmcr:status");
        output.putInt("severity", StatusSeverity.BLOCKED.ordinal());
        output.putString("source", "mmcr:source");
        ValueOutput occurrence = output.child("occurrence");
        occurrence.putBoolean("present", true);
        ValueOutput.ValueOutputList trace = occurrence.childrenList("trace");
        for (int index = 0; index < 17; index++) {
            ValueOutput frame = trace.addChild();
            frame.putString("source", "mmcr:source");
            frame.putInt("phase", FailurePhase.RUNTIME.ordinal());
        }

        assertThatThrownBy(() -> FailureStatusCodec.read(TagValueInput.create(
                ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformed_identifier_is_rejected_at_the_codec_boundary() {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        output.putBoolean("present", true);
        output.putString("id", "not an identifier");
        output.putInt("severity", StatusSeverity.BLOCKED.ordinal());
        output.putString("source", "mmcr:source");

        assertThatThrownBy(() -> FailureStatusCodec.read(TagValueInput.create(
                ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExecutionStatus fixture() {
        FailureTrace trace = new FailureTrace(List.of(
                new FailureTrace.Frame(MMCR.id("planner"),
                        FailurePhase.REQUIREMENT_PLAN, MMCR.id("recipe"), 2),
                new FailureTrace.Frame(MMCR.id("controller"),
                        FailurePhase.RUNTIME, null, null)));
        FailureOccurrence occurrence = new FailureOccurrence(CODEC_REASON, trace,
                Map.of("required", "4", "available", "1", "raw_reason_id", "legacy:old_reason"));
        return new ExecutionStatus(MMCR.id("codec_status"),
                StatusSeverity.BLOCKED, MMCR.id("crafting"), occurrence);
    }
}
