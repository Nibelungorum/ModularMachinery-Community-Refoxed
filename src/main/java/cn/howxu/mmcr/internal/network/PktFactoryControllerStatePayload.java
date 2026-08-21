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
        String machineName = buf.readUtf(MAX_STRING_LENGTH);
        int parallelSlots = buf.readVarInt();
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
            threads.add(new FactoryRecipeScheduler.ThreadSnapshot(index, buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readUtf(MAX_STRING_LENGTH), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readUtf(MAX_STRING_LENGTH), buf.readBoolean(), buf.readUtf(MAX_STRING_LENGTH)));
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
        Set<Integer> indexes = new HashSet<>(count);
        for (FactoryRecipeScheduler.ThreadSnapshot thread : state.threads()) {
            if (thread == null || thread.index() < 0 || thread.index() >= count || !indexes.add(thread.index())) {
                throw new IllegalArgumentException("Invalid factory thread snapshot index");
            }
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
