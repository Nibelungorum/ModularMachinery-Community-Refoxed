package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the controller screen text payload wire boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktControllerScreenTextPayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void payload_round_trips_translatable_components_and_preserves_scope_id_and_order() {
        PktControllerScreenTextPayload payload = new PktControllerScreenTextPayload(
                new BlockPos(3, 4, 5), 7L, List.of(
                line(ControllerScreenTextScope.CONTROLLER, "addon:status",
                        Component.translatable("example.progress", Component.literal("75%"))),
                line(ControllerScreenTextScope.OPERATION, "addon:operation", Component.literal("running"))));

        RegistryFriendlyByteBuf buffer = buffer();
        PktControllerScreenTextPayload.STREAM_CODEC.encode(buffer, payload);

        PktControllerScreenTextPayload decoded = PktControllerScreenTextPayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded.controllerPos()).isEqualTo(payload.controllerPos());
        assertThat(decoded.revision()).isEqualTo(payload.revision());
        assertThat(decoded.lines()).hasSize(2);
        assertThat(decoded.lines().get(0).scope()).isEqualTo(ControllerScreenTextScope.CONTROLLER);
        assertThat(decoded.lines().get(0).lineId()).isEqualTo(Identifier.parse("addon:status"));
        assertThat(decoded.lines().get(0).text()).isInstanceOf(Component.class);
        assertThat(((TranslatableContents) decoded.lines().get(0).text().getContents()).getKey())
                .isEqualTo("example.progress");
        assertThat(((TranslatableContents) decoded.lines().get(0).text().getContents()).getArgs())
                .containsExactly("75%");
        assertThat(decoded.lines().get(1).scope()).isEqualTo(ControllerScreenTextScope.OPERATION);
        assertThat(decoded.lines().get(1).lineId()).isEqualTo(Identifier.parse("addon:operation"));
        assertThat(decoded.lines().get(1).text()).isEqualTo(Component.literal("running"));
        buffer.release();
    }

    @Test
    void payload_rejects_invalid_constructor_values_and_bounds() {
        assertThatThrownBy(() -> new PktControllerScreenTextPayload(null, 0L, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PktControllerScreenTextPayload(BlockPos.ZERO, -1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktControllerScreenTextPayload(BlockPos.ZERO, 0L,
                List.of(line(ControllerScreenTextScope.CONTROLLER, "test:" + "x".repeat(257), Component.empty()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktControllerScreenTextPayload(BlockPos.ZERO, 0L,
                java.util.stream.IntStream.range(0, PktControllerScreenTextPayload.MAX_LINES + 1)
                        .mapToObj(index -> line(ControllerScreenTextScope.CONTROLLER, "test:line_" + index,
                                Component.empty())).toList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_text_section_larger_than_64_kibibytes() {
        PktControllerScreenTextPayload payload = new PktControllerScreenTextPayload(
                BlockPos.ZERO, 0L, List.of(line(ControllerScreenTextScope.CONTROLLER, "test:large",
                        largeComponent())));

        assertThatThrownBy(() -> PktControllerScreenTextPayload.STREAM_CODEC.encode(buffer(), payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_invalid_count_scope_id_and_duplicate_entries() {
        RegistryFriendlyByteBuf tooManyLines = buffer();
        writeHeader(tooManyLines, PktControllerScreenTextPayload.MAX_LINES + 1);
        assertThatThrownBy(() -> PktControllerScreenTextPayload.STREAM_CODEC.decode(tooManyLines))
                .isInstanceOf(IllegalArgumentException.class);
        tooManyLines.release();

        RegistryFriendlyByteBuf invalidScope = buffer();
        writeHeader(invalidScope, 1);
        invalidScope.writeVarInt(99);
        assertThatThrownBy(() -> PktControllerScreenTextPayload.STREAM_CODEC.decode(invalidScope))
                .isInstanceOf(IllegalArgumentException.class);
        invalidScope.release();

        RegistryFriendlyByteBuf duplicate = buffer();
        writeHeader(duplicate, 2);
        writeLine(duplicate, ControllerScreenTextScope.CONTROLLER, "test:duplicate", Component.literal("one"));
        writeLine(duplicate, ControllerScreenTextScope.CONTROLLER, "test:duplicate", Component.literal("two"));
        assertThatThrownBy(() -> PktControllerScreenTextPayload.STREAM_CODEC.decode(duplicate))
                .isInstanceOf(IllegalArgumentException.class);
        duplicate.release();

        RegistryFriendlyByteBuf overlongId = buffer();
        writeHeader(overlongId, 1);
        writeLine(overlongId, ControllerScreenTextScope.CONTROLLER, "test:" + "x".repeat(257), Component.empty());
        assertThatThrownBy(() -> PktControllerScreenTextPayload.STREAM_CODEC.decode(overlongId))
                .isInstanceOf(IllegalArgumentException.class);
        overlongId.release();
    }

    @Test
    void decoder_rejects_negative_revision() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeLong(-1L);

        assertThatThrownBy(() -> PktControllerScreenTextPayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void decoder_rejects_encoded_text_section_larger_than_64_kibibytes() {
        RegistryFriendlyByteBuf buffer = buffer();
        writeHeader(buffer, 1);
        writeLine(buffer, ControllerScreenTextScope.CONTROLLER, "test:large",
                largeComponent());

        assertThatThrownBy(() -> PktControllerScreenTextPayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    private static ControllerScreenTextSnapshot.Line line(ControllerScreenTextScope scope, String id,
                                                           Component text) {
        return new ControllerScreenTextSnapshot.Line(scope, Identifier.parse(id), text);
    }

    private static void writeHeader(RegistryFriendlyByteBuf buffer, int lineCount) {
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeLong(0L);
        buffer.writeVarInt(lineCount);
    }

    private static void writeLine(RegistryFriendlyByteBuf buffer, ControllerScreenTextScope scope, String id,
                                  Component text) {
        buffer.writeVarInt(scope.ordinal());
        Identifier.STREAM_CODEC.encode(buffer, Identifier.parse(id));
        ComponentSerialization.STREAM_CODEC.encode(buffer, text);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static Component largeComponent() {
        return Component.literal("x".repeat(40_000)).append(Component.literal("y".repeat(30_000)));
    }
}
