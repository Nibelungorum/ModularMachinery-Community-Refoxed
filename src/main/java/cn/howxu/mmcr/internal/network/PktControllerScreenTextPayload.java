package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Client-bound complete controller screen text snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktControllerScreenTextPayload(BlockPos controllerPos, long revision,
                                              List<ControllerScreenTextSnapshot.Line> lines)
        implements CustomPacketPayload {
    public static final int MAX_LINES = ControllerScreenTextState.MAX_LINES;
    public static final int MAX_LINE_ID_LENGTH = ControllerScreenTextState.MAX_LINE_ID_LENGTH;
    public static final int MAX_ENCODED_TEXT_BYTES = ControllerScreenTextState.MAX_ENCODED_TEXT_BYTES;

    public static final Type<PktControllerScreenTextPayload> TYPE = new Type<>(MMCR.id("controller_screen_text"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktControllerScreenTextPayload> STREAM_CODEC =
            StreamCodec.of(PktControllerScreenTextPayload::write, PktControllerScreenTextPayload::read);

    public PktControllerScreenTextPayload {
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        if (revision < 0L) throw new IllegalArgumentException("revision must not be negative");
        lines = List.copyOf(lines == null ? List.of() : lines);
        validateLines(lines);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ControllerScreenTextCache.replace(controllerPos, revision, lines));
    }

    private static void write(RegistryFriendlyByteBuf buffer, PktControllerScreenTextPayload payload) {
        buffer.writeBlockPos(payload.controllerPos);
        buffer.writeLong(payload.revision);
        buffer.writeVarInt(payload.lines.size());
        int textStart = buffer.writerIndex();
        for (ControllerScreenTextSnapshot.Line line : payload.lines) {
            buffer.writeVarInt(line.scope().ordinal());
            Identifier.STREAM_CODEC.encode(buffer, line.lineId());
            ComponentSerialization.STREAM_CODEC.encode(buffer, line.text());
            if (buffer.writerIndex() - textStart > MAX_ENCODED_TEXT_BYTES) {
                throw new IllegalArgumentException("Encoded controller screen text is too large");
            }
        }
    }

    private static PktControllerScreenTextPayload read(RegistryFriendlyByteBuf buffer) {
        BlockPos controllerPos = buffer.readBlockPos();
        long revision = buffer.readLong();
        if (revision < 0L) throw new IllegalArgumentException("revision must not be negative");

        int lineCount = buffer.readVarInt();
        if (lineCount < 0 || lineCount > MAX_LINES) {
            throw new IllegalArgumentException("Invalid controller screen text line count: " + lineCount);
        }
        if (buffer.readableBytes() > MAX_ENCODED_TEXT_BYTES) {
            throw new IllegalArgumentException("Encoded controller screen text is too large");
        }

        int textStart = buffer.readerIndex();
        List<ControllerScreenTextSnapshot.Line> lines = new ArrayList<>(lineCount);
        Set<LineKey> seen = new HashSet<>();
        for (int index = 0; index < lineCount; index++) {
            ControllerScreenTextScope scope = readScope(buffer.readVarInt());
            Identifier lineId = Identifier.STREAM_CODEC.decode(buffer);
            validateLineId(lineId);
            if (!seen.add(new LineKey(scope, lineId))) {
                throw new IllegalArgumentException("Duplicate controller screen text line");
            }
            Component text = ComponentSerialization.STREAM_CODEC.decode(buffer);
            if (buffer.readerIndex() - textStart > MAX_ENCODED_TEXT_BYTES) {
                throw new IllegalArgumentException("Encoded controller screen text is too large");
            }
            lines.add(new ControllerScreenTextSnapshot.Line(scope, lineId, text));
        }
        return new PktControllerScreenTextPayload(controllerPos, revision, lines);
    }

    private static void validateLines(List<ControllerScreenTextSnapshot.Line> lines) {
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("Too many controller screen text lines");
        }
        Set<LineKey> seen = new HashSet<>();
        for (ControllerScreenTextSnapshot.Line line : lines) {
            Objects.requireNonNull(line, "line");
            validateLineId(line.lineId());
            if (!seen.add(new LineKey(line.scope(), line.lineId()))) {
                throw new IllegalArgumentException("Duplicate controller screen text line");
            }
        }
    }

    private static void validateLineId(Identifier lineId) {
        Objects.requireNonNull(lineId, "lineId");
        if (lineId.getNamespace().isBlank()) {
            throw new IllegalArgumentException("lineId must have a namespace");
        }
        if (lineId.toString().length() > MAX_LINE_ID_LENGTH) {
            throw new IllegalArgumentException("Controller screen text line ID is too long");
        }
    }

    private static ControllerScreenTextScope readScope(int ordinal) {
        ControllerScreenTextScope[] values = ControllerScreenTextScope.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid controller screen text scope: " + ordinal);
        }
        return values[ordinal];
    }

    private record LineKey(ControllerScreenTextScope scope, Identifier lineId) {
    }
}
