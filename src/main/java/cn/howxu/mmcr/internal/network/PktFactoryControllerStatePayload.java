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
import java.util.List;

/**
 * Client-bound, menu-scoped factory controller runtime snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktFactoryControllerStatePayload(FactoryControllerSnapshot snapshot) implements CustomPacketPayload {
    private static final int MAX_THREADS = 65;
    public static final Type<PktFactoryControllerStatePayload> TYPE = new Type<>(MMCR.id("factory_controller_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktFactoryControllerStatePayload> STREAM_CODEC =
            StreamCodec.of(PktFactoryControllerStatePayload::write, PktFactoryControllerStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PktFactoryControllerStatePayload payload) {
        FactoryControllerSnapshot state = payload.snapshot;
        buf.writeBlockPos(state.controllerPos());
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.redstonePaused());
        buf.writeVarInt(state.activeThreadCount());
        buf.writeVarInt(state.threadCount());
        buf.writeVarInt(state.currentParallelism());
        buf.writeVarInt(state.maxParallelism());
        buf.writeUtf(state.machineName());
        buf.writeVarInt(state.parallelSlots());
        buf.writeUtf(state.lastFailureUnloc());
        buf.writeVarInt(state.threads().size());
        for (FactoryRecipeScheduler.ThreadSnapshot thread : state.threads()) {
            buf.writeVarInt(thread.index());
            buf.writeBoolean(thread.baseThread());
            buf.writeBoolean(thread.coreThread());
            buf.writeBoolean(thread.active());
            buf.writeUtf(thread.recipeId());
            buf.writeVarInt(thread.tick());
            buf.writeVarInt(thread.totalTick());
            buf.writeVarInt(thread.parallelism());
            buf.writeUtf(thread.lastFailureUnloc());
        }
    }

    private static PktFactoryControllerStatePayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean formed = buf.readBoolean();
        boolean paused = buf.readBoolean();
        int active = buf.readVarInt();
        int count = buf.readVarInt();
        int currentParallelism = buf.readVarInt();
        int maxParallelism = buf.readVarInt();
        String machineName = buf.readUtf();
        int parallelSlots = buf.readVarInt();
        String lastFailure = buf.readUtf();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_THREADS || size > Math.max(1, count)) {
            throw new IllegalArgumentException("Invalid factory thread snapshot size: " + size);
        }
        List<FactoryRecipeScheduler.ThreadSnapshot> threads = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            threads.add(new FactoryRecipeScheduler.ThreadSnapshot(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));
        }
        return new PktFactoryControllerStatePayload(new FactoryControllerSnapshot(pos, formed, paused, active, count,
                currentParallelism, maxParallelism, machineName, parallelSlots, lastFailure, threads));
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
