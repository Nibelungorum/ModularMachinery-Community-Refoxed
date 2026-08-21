package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-bound, menu-scoped factory controller runtime snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktFactoryControllerStatePayload(FactoryControllerSnapshot snapshot) implements CustomPacketPayload {
    public static final int MAX_THREAD_SNAPSHOTS = 1024;
    public static final int MAX_STRING_LENGTH = 256;
    public static final Type<PktFactoryControllerStatePayload> TYPE = new Type<>(MMCR.id("factory_controller_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktFactoryControllerStatePayload> STREAM_CODEC =
            StreamCodec.of(PktFactoryControllerStatePayload::write, PktFactoryControllerStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PktFactoryControllerStatePayload payload) {
        FactoryControllerSnapshot state = payload.snapshot;
        validateSnapshot(state);
        buf.writeBlockPos(state.controllerPos());
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.redstonePaused());
        buf.writeVarInt(state.activeThreadCount());
        buf.writeVarInt(state.threadCount());
        buf.writeVarInt(state.currentParallelism());
        buf.writeVarInt(state.maxParallelism());
        buf.writeUtf(state.machineName(), MAX_STRING_LENGTH);
        buf.writeVarInt(state.parallelSlots());
        buf.writeUtf(state.lastFailureUnloc(), MAX_STRING_LENGTH);
        buf.writeVarInt(state.threads().size());
        for (FactoryRecipeScheduler.ThreadSnapshot thread : state.threads()) {
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
    }

    private static PktFactoryControllerStatePayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean formed = buf.readBoolean();
        boolean paused = buf.readBoolean();
        int active = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 1 || count > MAX_THREAD_SNAPSHOTS) {
            throw new IllegalArgumentException("Invalid factory thread snapshot count: " + count);
        }
        if (active < 0 || active > count) {
            throw new IllegalArgumentException("Invalid active factory thread count: " + active);
        }
        int currentParallelism = buf.readVarInt();
        int maxParallelism = buf.readVarInt();
        validateParallelism(currentParallelism, maxParallelism);
        String machineName = buf.readUtf(MAX_STRING_LENGTH);
        int parallelSlots = buf.readVarInt();
        if (parallelSlots < 0) {
            throw new IllegalArgumentException("Invalid factory parallel slot count: " + parallelSlots);
        }
        String lastFailure = buf.readUtf(MAX_STRING_LENGTH);
        int size = buf.readVarInt();
        if (size != count) {
            throw new IllegalArgumentException("Invalid factory thread snapshot size: " + size);
        }
        List<FactoryRecipeScheduler.ThreadSnapshot> threads = new ArrayList<>(size);
        Set<Integer> indexes = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            int index = buf.readVarInt();
            if (index < 0 || index >= count || !indexes.add(index)) {
                throw new IllegalArgumentException("Invalid factory thread snapshot index: " + index);
            }
            boolean baseThread = buf.readBoolean();
            boolean coreThread = buf.readBoolean();
            boolean activeThread = buf.readBoolean();
            String recipeId = buf.readUtf(MAX_STRING_LENGTH);
            int tick = buf.readVarInt();
            int totalTick = buf.readVarInt();
            int parallelism = buf.readVarInt();
            validateThread(activeThread, tick, totalTick, parallelism);
            threads.add(new FactoryRecipeScheduler.ThreadSnapshot(index, baseThread, coreThread, activeThread,
                    recipeId, tick, totalTick, parallelism, buf.readUtf(MAX_STRING_LENGTH), buf.readBoolean(),
                    buf.readUtf(MAX_STRING_LENGTH)));
        }
        return new PktFactoryControllerStatePayload(new FactoryControllerSnapshot(pos, formed, paused, active, count,
                currentParallelism, maxParallelism, machineName, parallelSlots, lastFailure, threads));
    }

    private static void validateSnapshot(FactoryControllerSnapshot state) {
        if (state == null) throw new IllegalArgumentException("Factory controller snapshot is null");
        int count = state.threadCount();
        if (count < 1 || count > MAX_THREAD_SNAPSHOTS || state.threads().size() != count) {
            throw new IllegalArgumentException("Invalid factory thread snapshot count: " + count);
        }
        if (state.activeThreadCount() < 0 || state.activeThreadCount() > count) {
            throw new IllegalArgumentException("Invalid active factory thread count: " + state.activeThreadCount());
        }
        validateParallelism(state.currentParallelism(), state.maxParallelism());
        if (state.parallelSlots() < 0) {
            throw new IllegalArgumentException("Invalid factory parallel slot count: " + state.parallelSlots());
        }
        Set<Integer> indexes = new HashSet<>(count);
        for (FactoryRecipeScheduler.ThreadSnapshot thread : state.threads()) {
            if (thread == null || thread.index() < 0 || thread.index() >= count || !indexes.add(thread.index())) {
                throw new IllegalArgumentException("Invalid factory thread snapshot index");
            }
            validateThread(thread.active(), thread.tick(), thread.totalTick(), thread.parallelism());
        }
    }

    private static void validateParallelism(int currentParallelism, int maxParallelism) {
        if (currentParallelism < 0) {
            throw new IllegalArgumentException("Invalid current factory parallelism: " + currentParallelism);
        }
        if (maxParallelism < 1) {
            throw new IllegalArgumentException("Invalid maximum factory parallelism: " + maxParallelism);
        }
    }

    private static void validateThread(boolean active, int tick, int totalTick, int parallelism) {
        if (tick < 0 || totalTick < 0 || tick > totalTick) {
            throw new IllegalArgumentException("Invalid factory thread tick range: " + tick + "/" + totalTick);
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("Invalid factory thread parallelism: " + parallelism);
        }
        if (!active && (tick != 0 || totalTick != 0 || parallelism != 1)) {
            throw new IllegalArgumentException("Inactive factory thread has runtime progress");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FactoryControllerMenu menu
                    && menu.controllerPos().equals(snapshot.controllerPos())) {
                menu.applySnapshot(snapshot);
            }
        });
    }
}
