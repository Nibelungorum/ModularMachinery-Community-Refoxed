package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.internal.network.DataValuePayloadCodec;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.HeaderData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneProgress;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.OutputData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.RecipeData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.TextLineData;
import cn.howxu.mmcr.internal.sync.FailureStatusCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Independent, owned and capacity-bounded controller UI wire values.
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerUiPayloadCodec {
    public static final int REQUEST_LIMIT = 16 * 1024;
    public static final int RESPONSE_LIMIT = 64 * 1024;
    public static final int STATE_LIMIT = 64 * 1024;
    public static final int SNAPSHOT_LIMIT = 512 * 1024;
    public static final int MAX_LANES = 1024;
    public static final int MAX_LINES = 1024;
    public static final int MAX_OUTPUTS = 4096;
    public static final int ID_LIMIT = 256;
    private static final int COMPONENT_LIMIT = 64 * 1024;

    private ControllerUiPayloadCodec() { }

    public static <T> byte[] encodeBounded(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value,
                                         RegistryAccess registries, int limit) {
        checkLimit(limit);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(Math.min(256, limit), limit), registries, ConnectionType.NEOFORGE);
        try {
            codec.encode(buffer, value);
            byte[] bytes = new byte[buffer.writerIndex()];
            buffer.getBytes(0, bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    public static <T> T decodeExact(StreamCodec<RegistryFriendlyByteBuf, T> codec, byte[] bytes,
                                    RegistryAccess registries, int limit) {
        checkLimit(limit);
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > limit) throw new IllegalArgumentException("Controller UI body exceeds limit");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.copiedBuffer(bytes), registries, ConnectionType.NEOFORGE);
        try {
            T value = Objects.requireNonNull(codec.decode(buffer), "decoded value");
            if (buffer.isReadable()) throw new IllegalArgumentException("Unread controller UI body bytes");
            return value;
        } finally {
            buffer.release();
        }
    }

    /** Bounds the entire packet, including headers, before any nested decoder or allocation. */
    static <T> StreamCodec<RegistryFriendlyByteBuf, T> packetCodec(
            BiConsumer<RegistryFriendlyByteBuf, T> writer, Function<RegistryFriendlyByteBuf, T> reader, int limit) {
        StreamCodec<RegistryFriendlyByteBuf, T> raw = StreamCodec.of(writer::accept, reader::apply);
        return StreamCodec.of((buffer, value) -> buffer.writeBytes(encodeBounded(raw, value, buffer.registryAccess(), limit)),
                buffer -> {
                    int length = buffer.readableBytes();
                    if (length > limit) throw new IllegalArgumentException("Controller UI packet exceeds limit");
                    byte[] bytes = new byte[length];
                    buffer.readBytes(bytes);
                    return decodeExact(raw, bytes, buffer.registryAccess(), limit);
                });
    }

    public static byte[] readBody(RegistryFriendlyByteBuf buffer, int limit) {
        int length = buffer.readVarInt();
        if (length < 0 || length > limit || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid controller UI body length");
        }
        byte[] body = new byte[length];
        buffer.readBytes(body);
        return body;
    }

    static void writeBody(RegistryFriendlyByteBuf buffer, byte[] body, int limit) {
        if (body.length > limit) throw new IllegalArgumentException("Controller UI body exceeds limit");
        buffer.writeVarInt(body.length);
        buffer.writeBytes(body);
    }

    static byte[] ownBody(byte[] body, int limit) {
        Objects.requireNonNull(body, "body");
        if (body.length > limit) throw new IllegalArgumentException("Controller UI body exceeds limit");
        return body.clone();
    }

    static void identity(int containerId, UUID sessionId) {
        if (containerId < 0) throw new IllegalArgumentException("Negative controller UI container ID");
        Objects.requireNonNull(sessionId, "sessionId");
    }

    static void sequence(long value) {
        if (value <= 0) throw new IllegalArgumentException("Controller UI sequence must be positive");
    }

    static void version(int version) {
        if (version <= 0) throw new IllegalArgumentException("Controller UI version must be positive");
    }

    static void laneId(String value) {
        if (value == null || value.isBlank() || value.length() > ID_LIMIT) {
            throw new IllegalArgumentException("Invalid controller UI lane ID");
        }
    }

    static void id(ResourceLocation value) {
        if (value == null || value.getNamespace().isBlank() || value.getPath().isBlank()
                || value.toString().length() > ID_LIMIT) {
            throw new IllegalArgumentException("Invalid controller UI identifier");
        }
    }

    static void writeId(RegistryFriendlyByteBuf buffer, ResourceLocation value) {
        id(value);
        buffer.writeUtf(value.toString(), ID_LIMIT);
    }

    static ResourceLocation readId(RegistryFriendlyByteBuf buffer) {
        ResourceLocation value = ResourceLocation.parse(buffer.readUtf(ID_LIMIT));
        id(value);
        return value;
    }

    static void writeNullableId(RegistryFriendlyByteBuf buffer, ResourceLocation value) {
        buffer.writeBoolean(value != null);
        if (value != null) writeId(buffer, value);
    }

    static ResourceLocation readNullableId(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? readId(buffer) : null;
    }

    static <T> T readEnum(RegistryFriendlyByteBuf buffer, T[] values) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Invalid controller UI enum");
        return values[ordinal];
    }

    static int count(RegistryFriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        checkCount(count, maximum);
        return count;
    }

    static void checkCount(int count, int maximum) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid controller UI list count");
    }

    static void writeComponent(RegistryFriendlyByteBuf buffer, Component value) {
        writeBody(buffer, encodeBounded(ComponentSerialization.STREAM_CODEC, value,
                buffer.registryAccess(), COMPONENT_LIMIT), COMPONENT_LIMIT);
    }

    static Component readComponent(RegistryFriendlyByteBuf buffer) {
        return decodeExact(ComponentSerialization.STREAM_CODEC, readBody(buffer, COMPONENT_LIMIT),
                buffer.registryAccess(), COMPONENT_LIMIT);
    }

    static void writeProgress(RegistryFriendlyByteBuf buffer, LaneProgress value) {
        laneId(value.laneId());
        buffer.writeUtf(value.laneId(), ID_LIMIT);
        writeNullableId(buffer, value.recipeId());
        buffer.writeVarInt(value.tick());
        buffer.writeVarInt(value.totalTick());
        buffer.writeLong(value.parallelism());
    }

    static LaneProgress readProgress(RegistryFriendlyByteBuf buffer) {
        return new LaneProgress(buffer.readUtf(ID_LIMIT), readNullableId(buffer),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readLong());
    }

    static void writeSnapshot(RegistryFriendlyByteBuf b, ControllerUiSnapshotData value) {
        b.writeUUID(value.sessionId());
        b.writeLong(value.revision());
        b.writeBoolean(value.ready());
        writeId(b, value.dimension().location());
        b.writeBlockPos(value.controllerPos());
        HeaderData h = value.header();
        writeId(b, h.machineId());
        b.writeVarInt(h.kind().ordinal());
        b.writeVarInt(h.role().ordinal());
        writeComponent(b, h.machineName());
        b.writeBoolean(h.formed());
        b.writeBoolean(h.active());
        b.writeBoolean(h.redstonePaused());
        b.writeVarInt(h.installedModuleCount());
        writeNullableId(b, h.connectedHost());
        b.writeVarInt(h.matchedStage());
        b.writeVarInt(h.stageCount());
        writeIds(b, h.foundLevelIds());
        b.writeVarInt(h.parallelSlots());
        b.writeLong(h.maxParallelism());
        b.writeVarInt(h.threadLimit());
        b.writeVarInt(h.activeThreadCount());
        writeIds(b, h.recipePoolIds());
        writeNullableId(b, h.currentRecipePool());
        FailureStatusCodec.write(b, h.runtimeFailure());
        b.writeBoolean(h.hasDataStorage());
        Map<String, cn.howxu.mmcr.api.data.DataValue> storage = new LinkedHashMap<>();
        h.dataStorageValues().forEach((key, entry) -> storage.put(key, DataValue.toInternal(entry)));
        DataValuePayloadCodec.writeMap(b, storage);
        writeLines(b, h.textLines());
        checkCount(value.laneData().size(), MAX_LANES);
        b.writeVarInt(value.laneData().size());
        int outputs = 0;
        int lines = h.textLines().size();
        for (LaneData lane : value.laneData()) {
            outputs += lane.recipe().outputData().size();
            lines += lane.textLines().size();
            checkCount(outputs, MAX_OUTPUTS);
            checkCount(lines, MAX_LINES);
            laneId(lane.id());
            b.writeUtf(lane.id(), ID_LIMIT);
            b.writeVarInt(lane.index());
            b.writeBoolean(lane.base());
            b.writeBoolean(lane.core());
            b.writeBoolean(lane.active());
            writeNullableId(b, lane.currentRecipe());
            b.writeVarInt(lane.tick());
            b.writeVarInt(lane.totalTick());
            b.writeLong(lane.parallelism());
            FailureStatusCodec.write(b, lane.runtimeFailure());
            writeLines(b, lane.textLines());
            writeRecipe(b, lane.recipe());
        }
    }

    static ControllerUiSnapshotData readSnapshot(RegistryFriendlyByteBuf b) {
        UUID sessionId = b.readUUID();
        long revision = b.readLong();
        boolean ready = b.readBoolean();
        var dimension = ResourceKey.create(Registries.DIMENSION, readId(b));
        var pos = b.readBlockPos();
        ResourceLocation machineId = readId(b);
        var kind = readEnum(b, ControllerUiSnapshot.Kind.values());
        var role = readEnum(b, ControllerUiSnapshot.Role.values());
        Component name = readComponent(b);
        boolean formed = b.readBoolean(), active = b.readBoolean(), paused = b.readBoolean();
        int modules = b.readVarInt();
        ResourceLocation host = readNullableId(b);
        int stage = b.readVarInt(), stages = b.readVarInt();
        List<ResourceLocation> levels = readIds(b);
        int slots = b.readVarInt();
        long parallelism = b.readLong();
        int threads = b.readVarInt(), activeThreads = b.readVarInt();
        List<ResourceLocation> pools = readIds(b);
        ResourceLocation pool = readNullableId(b);
        var failure = FailureStatusCodec.read(b);
        boolean hasStorage = b.readBoolean();
        Map<String, DataValue> storage = new LinkedHashMap<>();
        DataValuePayloadCodec.readMap(b).forEach((key, entry) -> storage.put(key, DataValue.fromInternal(entry)));
        List<TextLineData> text = readLines(b, MAX_LINES);
        HeaderData header = new HeaderData(machineId, kind, role, name, formed, active, paused, modules, host,
                stage, stages, levels, slots, parallelism, threads, activeThreads, pools, pool, failure,
                hasStorage, storage, text);
        int size = count(b, MAX_LANES);
        List<LaneData> lanes = new ArrayList<>(size);
        int lines = text.size(), outputs = 0;
        for (int i = 0; i < size; i++) {
            String id = b.readUtf(ID_LIMIT);
            int index = b.readVarInt();
            boolean base = b.readBoolean(), core = b.readBoolean(), laneActive = b.readBoolean();
            ResourceLocation recipe = readNullableId(b);
            int tick = b.readVarInt(), total = b.readVarInt();
            long laneParallelism = b.readLong();
            var laneFailure = FailureStatusCodec.read(b);
            List<TextLineData> laneText = readLines(b, MAX_LINES - lines);
            lines += laneText.size();
            RecipeData presentation = readRecipe(b, MAX_OUTPUTS - outputs);
            outputs += presentation.outputData().size();
            lanes.add(new LaneData(id, index, base, core, laneActive, recipe, tick, total, laneParallelism,
                    laneFailure, laneText, presentation));
        }
        return new ControllerUiSnapshotData(sessionId, revision, ready, dimension, pos, header, lanes);
    }

    private static void writeIds(RegistryFriendlyByteBuf b, List<ResourceLocation> ids) {
        checkCount(ids.size(), 1024);
        b.writeVarInt(ids.size());
        ids.forEach(id -> writeId(b, id));
    }

    private static List<ResourceLocation> readIds(RegistryFriendlyByteBuf b) {
        int size = count(b, 1024);
        List<ResourceLocation> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(readId(b));
        return List.copyOf(ids);
    }

    private static void writeLines(RegistryFriendlyByteBuf b, List<TextLineData> lines) {
        checkCount(lines.size(), MAX_LINES);
        b.writeVarInt(lines.size());
        var seen = new HashSet<String>();
        for (TextLineData line : lines) {
            if (!seen.add(line.scope() + ":" + line.id())) throw new IllegalArgumentException("Duplicate UI text line");
            writeId(b, line.id());
            b.writeVarInt(line.scope().ordinal());
            writeComponent(b, line.text());
        }
    }

    private static List<TextLineData> readLines(RegistryFriendlyByteBuf b, int remaining) {
        int size = count(b, remaining);
        List<TextLineData> lines = new ArrayList<>(size);
        var seen = new HashSet<String>();
        for (int i = 0; i < size; i++) {
            ResourceLocation id = readId(b);
            var scope = readEnum(b, ControllerUiSnapshot.TextLine.Scope.values());
            if (!seen.add(scope + ":" + id)) throw new IllegalArgumentException("Duplicate UI text line");
            lines.add(new TextLineData(id, scope, readComponent(b)));
        }
        return List.copyOf(lines);
    }

    private static void writeRecipe(RegistryFriendlyByteBuf b, RecipeData recipe) {
        checkCount(recipe.outputData().size(), MAX_OUTPUTS);
        b.writeVarInt(recipe.outputData().size());
        for (OutputData output : recipe.outputData()) {
            b.writeLong(output.amount());
            writeOutput(b, output.resource());
        }
        b.writeLong(recipe.energyInputPerTick());
        b.writeLong(recipe.energyOutputPerTick());
        b.writeDouble(recipe.heatOutputPerTick());
        b.writeVarInt(recipe.durationTicks());
        b.writeLong(recipe.parallelism());
    }

    private static RecipeData readRecipe(RegistryFriendlyByteBuf b, int remaining) {
        int size = count(b, remaining);
        List<OutputData> outputs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long amount = b.readLong();
            if (amount < 0) throw new IllegalArgumentException("Negative expected output amount");
            outputs.add(new OutputData(readOutput(b), amount, b.registryAccess()));
        }
        return new RecipeData(outputs, b.readLong(), b.readLong(), b.readDouble(), b.readVarInt(), b.readLong());
    }

    // Same canonical registered RecipeSyncCodec and framing as MachineRecipeSyncCodec, with a bounded owned buffer.
    @SuppressWarnings("unchecked")
    private static void writeOutput(RegistryFriendlyByteBuf b, MachineOutput output) {
        var type = OutputRegistry.canonicalType(output.outputType());
        if (type == null) throw new IllegalArgumentException("Unregistered UI output type");
        RecipeSyncCodec<MachineOutput> codec = (RecipeSyncCodec<MachineOutput>) type.syncCodec();
        codec.validate(output);
        int limit = Math.min(SNAPSHOT_LIMIT, codec.maxPayloadSize());
        writeId(b, type.id());
        writeBody(b, encodeBounded(StreamCodec.of(codec::encode, codec::decode), output, b.registryAccess(), limit), limit);
    }

    private static MachineOutput readOutput(RegistryFriendlyByteBuf b) {
        var type = OutputRegistry.typeFor(readId(b));
        if (type == null) throw new IllegalArgumentException("Unknown UI output type");
        MachineOutput output = readOutputValue(b, type.syncCodec());
        if (output.outputType() != type) throw new IllegalArgumentException("Noncanonical UI output type");
        return output;
    }

    private static <T extends MachineOutput> T readOutputValue(RegistryFriendlyByteBuf b, RecipeSyncCodec<T> codec) {
        int limit = Math.min(SNAPSHOT_LIMIT, codec.maxPayloadSize());
        T output = decodeExact(StreamCodec.of(codec::encode, codec::decode), readBody(b, limit),
                b.registryAccess(), limit);
        codec.validate(output);
        return output;
    }

    private static void checkLimit(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("Controller UI buffer limit must be positive");
    }
}
