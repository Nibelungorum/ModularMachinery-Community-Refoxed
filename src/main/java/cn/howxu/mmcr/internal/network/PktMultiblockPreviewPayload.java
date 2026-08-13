package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.MultiblockPreviewClientHandler;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-bound multiblock ghost preview payload.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktMultiblockPreviewPayload(ResourceKey<Level> dimension, BlockPos controllerPos,
                                          List<MultiblockPreviewSnapshot.Entry> entries,
                                          int durationTicks) implements CustomPacketPayload {
    public static final int DURATION_TICKS = 200;
    public static final int MAX_ENTRIES = 8192;
    public static final Type<PktMultiblockPreviewPayload> TYPE = new Type<>(MMCR.id("multiblock_preview"));
    private static final StreamCodec<RegistryFriendlyByteBuf, BlockState> BLOCK_STATE_CODEC = StreamCodec.of(
            PktMultiblockPreviewPayload::writeBlockState,
            PktMultiblockPreviewPayload::readBlockState);
    private static final StreamCodec<RegistryFriendlyByteBuf, MultiblockPreviewSnapshot.Entry> ENTRY_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MultiblockPreviewSnapshot.Entry::relativePos,
            BLOCK_STATE_CODEC, MultiblockPreviewSnapshot.Entry::state,
            MultiblockPreviewSnapshot.Entry::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMultiblockPreviewPayload> STREAM_CODEC = StreamCodec.of(
            PktMultiblockPreviewPayload::write,
            PktMultiblockPreviewPayload::read);

    public PktMultiblockPreviewPayload {
        controllerPos = controllerPos.immutable();
        entries = List.copyOf(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries);
    }

    public PktMultiblockPreviewPayload(MultiblockPreviewSnapshot snapshot) {
        this(snapshot.dimension(), snapshot.controllerPos(), snapshot.entries(), DURATION_TICKS);
    }

    public static PktMultiblockPreviewPayload clear(ResourceKey<Level> dimension, BlockPos controllerPos) {
        return new PktMultiblockPreviewPayload(dimension, controllerPos, List.of(), 0);
    }

    private static void write(RegistryFriendlyByteBuf buf, PktMultiblockPreviewPayload payload) {
        Identifier.STREAM_CODEC.encode(buf, payload.dimension.identifier());
        buf.writeBlockPos(payload.controllerPos);
        ByteBufCodecs.collection(ArrayList::new, ENTRY_CODEC, MAX_ENTRIES).encode(buf, new ArrayList<>(payload.entries));
        ByteBufCodecs.VAR_INT.encode(buf, payload.durationTicks);
    }

    private static PktMultiblockPreviewPayload read(RegistryFriendlyByteBuf buf) {
        Identifier dimension = Identifier.STREAM_CODEC.decode(buf);
        BlockPos controllerPos = buf.readBlockPos();
        List<MultiblockPreviewSnapshot.Entry> entries = ByteBufCodecs.collection(ArrayList::new, ENTRY_CODEC, MAX_ENTRIES).decode(buf);
        int durationTicks = ByteBufCodecs.VAR_INT.decode(buf);
        return new PktMultiblockPreviewPayload(ResourceKey.create(Registries.DIMENSION, dimension), controllerPos, entries, durationTicks);
    }

    private static void writeBlockState(RegistryFriendlyByteBuf buf, BlockState state) {
        Identifier.STREAM_CODEC.encode(buf, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        buf.writeVarInt(state.getProperties().size());
        for (Property<?> property : state.getProperties()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, property.getName());
            ByteBufCodecs.STRING_UTF8.encode(buf, propertyValueName(state, property));
        }
    }

    private static BlockState readBlockState(RegistryFriendlyByteBuf buf) {
        BlockState state = BuiltInRegistries.BLOCK.getValue(Identifier.STREAM_CODEC.decode(buf)).defaultBlockState();
        int propertyCount = buf.readVarInt();
        for (int i = 0; i < propertyCount; i++) {
            String propertyName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String valueName = ByteBufCodecs.STRING_UTF8.decode(buf);
            Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
            if (property != null) state = setPropertyValue(state, property, valueName);
        }
        return state;
    }

    private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property, String valueName) {
        return property.getValue(valueName).map(value -> state.setValue(property, value)).orElse(state);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> MultiblockPreviewClientHandler.show(dimension, controllerPos, entries, durationTicks));
    }
}
