package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-bound factory runtime snapshot with a complete immutable payload.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktFactoryControllerStatePayload(BlockPos controllerPos, FactorySnapshot snapshot)
        implements CustomPacketPayload {
    public static final int MAX_THREAD_SNAPSHOTS = 1024;
    public static final int MAX_LANE_SNAPSHOTS = 1024;
    public static final int MAX_LEVEL_SNAPSHOTS = 1024;
    public static final int MAX_FAILURE_DETAIL_ENTRIES = 1024;
    public static final int MAX_STRING_LENGTH = 256;
    public static final Type<PktFactoryControllerStatePayload> TYPE = new Type<>(MMCR.id("factory_controller_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktFactoryControllerStatePayload> STREAM_CODEC =
            StreamCodec.of(PktFactoryControllerStatePayload::write, PktFactoryControllerStatePayload::read);

    public PktFactoryControllerStatePayload {
        controllerPos = controllerPos == null ? BlockPos.ZERO : controllerPos.immutable();
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
    }

    private static void write(RegistryFriendlyByteBuf buf, PktFactoryControllerStatePayload payload) {
        FactorySnapshot state = payload.snapshot;
        validateSnapshot(state);
        buf.writeBlockPos(payload.controllerPos);
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.active());
        buf.writeVarInt(state.activeParallelism());
        buf.writeVarInt(state.laneLimit());
        buf.writeVarInt(state.activeLaneCount());
        buf.writeVarInt(state.maxParallelism());
        buf.writeBoolean(state.paused());
        buf.writeUtf(state.machineName(), MAX_STRING_LENGTH);
        buf.writeVarInt(state.parallelSlots());
        buf.writeVarInt(state.foundLevelIds().size());
        for (String id : state.foundLevelIds()) buf.writeUtf(id, MAX_STRING_LENGTH);
        writeFailure(buf, state.failure());
        buf.writeVarInt(state.lanes().size());
        for (CraftingStateSnapshot lane : state.lanes()) writeCrafting(buf, lane);
        buf.writeVarInt(state.presentationLanes().size());
        for (FactoryRuntime.ThreadSnapshot thread : state.presentationLanes()) writeThread(buf, thread);
    }

    private static PktFactoryControllerStatePayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean formed = buf.readBoolean();
        boolean active = buf.readBoolean();
        int activeParallelism = buf.readVarInt();
        int laneLimit = buf.readVarInt();
        int activeLaneCount = buf.readVarInt();
        int maxParallelism = buf.readVarInt();
        boolean paused = buf.readBoolean();
        String machineName = buf.readUtf(MAX_STRING_LENGTH);
        int parallelSlots = buf.readVarInt();
        int levelSize = readCount(buf, MAX_LEVEL_SNAPSHOTS, "machine level");
        List<String> foundLevelIds = new ArrayList<>(levelSize);
        for (int i = 0; i < levelSize; i++) foundLevelIds.add(buf.readUtf(MAX_STRING_LENGTH));
        ExecutionStatus failure = readFailure(buf);
        int laneSize = readCount(buf, MAX_LANE_SNAPSHOTS, "crafting lane");
        List<CraftingStateSnapshot> lanes = new ArrayList<>(laneSize);
        for (int i = 0; i < laneSize; i++) lanes.add(readCrafting(buf));
        int threadSize = readCount(buf, MAX_THREAD_SNAPSHOTS, "factory thread");
        List<FactoryRuntime.ThreadSnapshot> threads = new ArrayList<>(threadSize);
        Set<Integer> indexes = new HashSet<>(threadSize);
        for (int i = 0; i < threadSize; i++) {
            FactoryRuntime.ThreadSnapshot thread = readThread(buf);
            if (thread.index() < 0 || !indexes.add(thread.index())) {
                throw new IllegalArgumentException("Invalid factory thread snapshot index: " + thread.index());
            }
            threads.add(thread);
        }
        FactorySnapshot snapshot = new FactorySnapshot(formed, active, lanes, activeParallelism, laneLimit,
                activeLaneCount, maxParallelism, paused, threads, machineName, parallelSlots, failure, foundLevelIds);
        validateSnapshot(snapshot);
        return new PktFactoryControllerStatePayload(pos, snapshot);
    }

    private static void writeCrafting(RegistryFriendlyByteBuf buf, CraftingStateSnapshot state) {
        buf.writeBoolean(state.recipeId() != null);
        if (state.recipeId() != null) buf.writeUtf(state.recipeId().toString(), MAX_STRING_LENGTH);
        CraftingStatus status = state.status();
        buf.writeVarInt(status.getStatus().ordinal());
        buf.writeUtf(status.getUnlocMessage(), MAX_STRING_LENGTH);
        writeFailure(buf, state.failure());
        buf.writeLong(state.structureVersion());
        buf.writeLong(state.capabilityVersion());
        buf.writeLong(state.modifierVersion());
        buf.writeVarInt(state.tick());
        buf.writeVarInt(state.totalTick());
        buf.writeVarInt(state.parallelism());
        buf.writeVarInt(state.maxParallelism());
        buf.writeBoolean(state.recipeLocked());
        buf.writeUtf(state.lockedRecipeId(), MAX_STRING_LENGTH);
    }

    private static CraftingStateSnapshot readCrafting(RegistryFriendlyByteBuf buf) {
        Identifier recipeId = buf.readBoolean() ? Identifier.parse(buf.readUtf(MAX_STRING_LENGTH)) : null;
        CraftingStatus.Status status = readEnum(CraftingStatus.Status.values(), buf.readVarInt(), "crafting status");
        CraftingStatus craftingStatus = new CraftingStatus(status, buf.readUtf(MAX_STRING_LENGTH));
        ExecutionStatus failure = readFailure(buf);
        return new CraftingStateSnapshot(recipeId, craftingStatus, failure, buf.readLong(), buf.readLong(), buf.readLong(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readUtf(MAX_STRING_LENGTH));
    }

    private static void writeThread(RegistryFriendlyByteBuf buf, FactoryRuntime.ThreadSnapshot thread) {
        buf.writeVarInt(thread.index());
        buf.writeBoolean(thread.baseThread());
        buf.writeBoolean(thread.coreThread());
        buf.writeBoolean(thread.active());
        buf.writeUtf(thread.recipeId(), MAX_STRING_LENGTH);
        buf.writeVarInt(thread.tick());
        buf.writeVarInt(thread.totalTick());
        buf.writeVarInt(thread.parallelism());
        buf.writeUtf(thread.lastFailureUnloc(), MAX_STRING_LENGTH);
        buf.writeBoolean(thread.locked());
        buf.writeUtf(thread.lockedRecipeId(), MAX_STRING_LENGTH);
    }

    private static FactoryRuntime.ThreadSnapshot readThread(RegistryFriendlyByteBuf buf) {
        int index = buf.readVarInt();
        boolean baseThread = buf.readBoolean();
        boolean coreThread = buf.readBoolean();
        boolean active = buf.readBoolean();
        String recipeId = buf.readUtf(MAX_STRING_LENGTH);
        int tick = buf.readVarInt();
        int totalTick = buf.readVarInt();
        int parallelism = buf.readVarInt();
        validateThread(active, tick, totalTick, parallelism);
        return new FactoryRuntime.ThreadSnapshot(index, baseThread, coreThread, active, recipeId, tick,
                totalTick, parallelism, buf.readUtf(MAX_STRING_LENGTH), buf.readBoolean(),
                buf.readUtf(MAX_STRING_LENGTH));
    }

    private static void writeFailure(RegistryFriendlyByteBuf buf, ExecutionStatus failure) {
        buf.writeBoolean(failure != null);
        if (failure == null) return;
        buf.writeUtf(failure.id().toString(), MAX_STRING_LENGTH);
        buf.writeVarInt(failure.severity().ordinal());
        buf.writeUtf(failure.source().toString(), MAX_STRING_LENGTH);
        buf.writeVarInt(failure.details().size());
        for (Map.Entry<String, String> detail : failure.details().entrySet()) {
            buf.writeUtf(detail.getKey(), MAX_STRING_LENGTH);
            buf.writeUtf(detail.getValue(), MAX_STRING_LENGTH);
        }
    }

    private static ExecutionStatus readFailure(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        Identifier id = Identifier.parse(buf.readUtf(MAX_STRING_LENGTH));
        StatusSeverity severity = readEnum(StatusSeverity.values(), buf.readVarInt(), "failure severity");
        Identifier source = Identifier.parse(buf.readUtf(MAX_STRING_LENGTH));
        int detailCount = readCount(buf, MAX_FAILURE_DETAIL_ENTRIES, "failure detail");
        Map<String, String> details = new LinkedHashMap<>();
        for (int i = 0; i < detailCount; i++) {
            details.put(buf.readUtf(MAX_STRING_LENGTH), buf.readUtf(MAX_STRING_LENGTH));
        }
        return new ExecutionStatus(id, severity, source, details);
    }

    private static void validateSnapshot(FactorySnapshot state) {
        if (state == null) throw new IllegalArgumentException("Factory snapshot is null");
        if (state.laneLimit() < 1 || state.laneLimit() > MAX_THREAD_SNAPSHOTS) {
            throw new IllegalArgumentException("Invalid factory lane limit: " + state.laneLimit());
        }
        if (state.presentationLanes().size() != state.laneLimit()) {
            throw new IllegalArgumentException("Factory presentation lanes are incomplete");
        }
        if (state.activeLaneCount() < 0 || state.activeLaneCount() > state.laneLimit()) {
            throw new IllegalArgumentException("Invalid active factory lane count: " + state.activeLaneCount());
        }
        if (state.activeParallelism() < 0 || state.maxParallelism() < 1
                || state.parallelSlots() < 0 || state.lanes().size() > MAX_LANE_SNAPSHOTS
                || state.foundLevelIds().size() > MAX_LEVEL_SNAPSHOTS) {
            throw new IllegalArgumentException("Invalid factory snapshot values");
        }
        validateFailure(state.failure());
        for (CraftingStateSnapshot lane : state.lanes()) validateFailure(lane.failure());
        Set<Integer> indexes = new HashSet<>();
        for (FactoryRuntime.ThreadSnapshot thread : state.presentationLanes()) {
            if (thread == null || thread.index() < 0 || thread.index() >= state.laneLimit() || !indexes.add(thread.index())) {
                throw new IllegalArgumentException("Invalid factory thread snapshot index");
            }
            validateThread(thread.active(), thread.tick(), thread.totalTick(), thread.parallelism());
        }
    }

    private static void validateFailure(ExecutionStatus failure) {
        if (failure != null && failure.details().size() > MAX_FAILURE_DETAIL_ENTRIES) {
            throw new IllegalArgumentException("Invalid failure detail count: " + failure.details().size());
        }
    }

    private static void validateThread(boolean active, int tick, int totalTick, int parallelism) {
        if (tick < 0 || totalTick < 0 || tick > totalTick || parallelism < 1) {
            throw new IllegalArgumentException("Invalid factory thread state");
        }
        if (!active && (tick != 0 || totalTick != 0 || parallelism != 1)) {
            throw new IllegalArgumentException("Inactive factory thread has runtime progress");
        }
    }

    private static int readCount(RegistryFriendlyByteBuf buf, int max, String name) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) throw new IllegalArgumentException("Invalid " + name + " count: " + count);
        return count;
    }

    private static <T> T readEnum(T[] values, int ordinal, String name) {
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Invalid " + name + ": " + ordinal);
        return values[ordinal];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FactoryControllerMenu menu
                    && menu.controllerPos().equals(controllerPos)) {
                menu.applySnapshot(snapshot);
            }
        });
    }
}
