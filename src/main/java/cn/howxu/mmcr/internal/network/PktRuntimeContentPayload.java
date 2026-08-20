package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.sync.JeiRuntimeReloadBridge;
import cn.howxu.mmcr.internal.sync.MachineRecipeSyncCodec;
import cn.howxu.mmcr.internal.sync.MachineStructureSyncCodec;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Clientbound runtime content snapshot payload.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktRuntimeContentPayload(RuntimeContentSnapshot snapshot) implements CustomPacketPayload {
    private static final int MAX_STRUCTURES = 4096;
    private static final int MAX_RECIPES = 16384;
    private static final int MAX_SPECS = 4096;
    private static final int MAX_TOOLTIP_LINES = 1024;

    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> TOOLTIP_CODEC = StreamCodec.of(
            PktRuntimeContentPayload::writeTooltip,
            PktRuntimeContentPayload::readTooltip);

    private static final StreamCodec<RegistryFriendlyByteBuf, MachineControllerSpec> CONTROLLER_SPEC_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, MachineControllerSpec::id,
            Identifier.STREAM_CODEC, MachineControllerSpec::frontTexture,
            Identifier.STREAM_CODEC, MachineControllerSpec::sideTexture,
            Identifier.STREAM_CODEC, MachineControllerSpec::topTexture,
            Identifier.STREAM_CODEC, MachineControllerSpec::bottomTexture,
            ByteBufCodecs.BOOL, MachineControllerSpec::allowVerticalFacing,
            ByteBufCodecs.BOOL, MachineControllerSpec::fullyRotationallySymmetric,
            ByteBufCodecs.BOOL, MachineControllerSpec::requireVerticalFacing,
             TOOLTIP_CODEC, MachineControllerSpec::tooltip,
            MachineControllerSpec::new);
    private static final StreamCodec<RegistryFriendlyByteBuf, MachineAppearanceSpec> APPEARANCE_SPEC_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, MachineAppearanceSpec::machineBasicBlock,
            Identifier.STREAM_CODEC, MachineAppearanceSpec::controllerBaseTexture,
            Identifier.STREAM_CODEC, MachineAppearanceSpec::formedPortBaseTexture,
            MachineAppearanceSpec::new);

    public static final Type<PktRuntimeContentPayload> TYPE = new Type<>(MMCR.id("runtime_content"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktRuntimeContentPayload> STREAM_CODEC = StreamCodec.of(
            PktRuntimeContentPayload::encode,
            PktRuntimeContentPayload::decode);

    public PktRuntimeContentPayload {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot null");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (snapshot.applyClient()) {
                JeiRuntimeReloadBridge.reloadIfAvailable(snapshot);
            }
        });
    }

    private static void encode(RegistryFriendlyByteBuf buf, PktRuntimeContentPayload payload) {
        RuntimeContentSnapshot snapshot = payload.snapshot();
        writeMap(buf, snapshot.structures(), MAX_STRUCTURES, MachineStructureSyncCodec::encode);
        writeMap(buf, snapshot.recipes(), MAX_RECIPES, MachineRecipeSyncCodec::encode);
        writeMap(buf, snapshot.controllerSpecs(), MAX_SPECS, CONTROLLER_SPEC_CODEC::encode);
        writeMap(buf, snapshot.appearances(), MAX_SPECS, APPEARANCE_SPEC_CODEC::encode);
        buf.writeVarLong(snapshot.contentVersion());
    }

    private static PktRuntimeContentPayload decode(RegistryFriendlyByteBuf buf) {
        Map<Identifier, MachineStructureDefinition> structures = readMap(buf, MAX_STRUCTURES, MachineStructureSyncCodec::decode);
        Map<Identifier, MachineRecipe> recipes = readMap(buf, MAX_RECIPES, MachineRecipeSyncCodec::decode);
        Map<Identifier, MachineControllerSpec> controllerSpecs = readMap(buf, MAX_SPECS, CONTROLLER_SPEC_CODEC::decode);
        Map<Identifier, MachineAppearanceSpec> appearances = readMap(buf, MAX_SPECS, APPEARANCE_SPEC_CODEC::decode);
        validateMap(structures, (id, value) -> {
            if (!id.equals(value.machineId())) throw new IllegalArgumentException("Structure key does not match machine id: " + id);
        });
        validateMap(recipes, (id, value) -> {
            if (!id.equals(value.id())) throw new IllegalArgumentException("Recipe key does not match recipe id: " + id);
        });
        validateMap(controllerSpecs, (id, value) -> {
            if (value.id() == null) throw new IllegalArgumentException("Invalid controller spec id: " + id);
        });
        long contentVersion = buf.readVarLong();
        if (contentVersion < 0) throw new IllegalArgumentException("Invalid runtime content version: " + contentVersion);
        return new PktRuntimeContentPayload(new RuntimeContentSnapshot(
                structures, recipes, controllerSpecs, appearances, contentVersion));
    }

    private static <T> void writeMap(RegistryFriendlyByteBuf buf, Map<Identifier, T> values, int max,
            EntryWriter<T> writer) {
        checkSize(values.size(), max, "runtime content");
        buf.writeVarInt(values.size());
        for (Map.Entry<Identifier, T> entry : values.entrySet()) {
            Identifier.STREAM_CODEC.encode(buf, entry.getKey());
            writer.write(buf, entry.getValue());
        }
    }

    private static <T> Map<Identifier, T> readMap(RegistryFriendlyByteBuf buf, int max, EntryReader<T> reader) {
        int count = buf.readVarInt();
        checkSize(count, max, "runtime content");
        Map<Identifier, T> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Identifier id = Identifier.STREAM_CODEC.decode(buf);
            if (values.containsKey(id)) throw new IllegalArgumentException("Duplicate runtime content key: " + id);
            values.put(id, reader.read(buf));
        }
        return Map.copyOf(values);
    }

    private static void writeTooltip(RegistryFriendlyByteBuf buf, List<String> values) {
        checkSize(values.size(), MAX_TOOLTIP_LINES, "tooltip line");
        buf.writeVarInt(values.size());
        for (String value : values) ByteBufCodecs.STRING_UTF8.encode(buf, value);
    }

    private static List<String> readTooltip(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_TOOLTIP_LINES, "tooltip line");
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        return List.copyOf(values);
    }

    private static <T> void validateMap(Map<Identifier, T> values, BiConsumer<Identifier, T> validator) {
        values.forEach(validator);
    }

    private static void checkSize(int size, int max, String label) {
        if (size < 0 || size > max) throw new IllegalArgumentException("Invalid " + label + " count: " + size);
    }

    @FunctionalInterface
    private interface EntryWriter<T> {
        void write(RegistryFriendlyByteBuf buf, T value);
    }

    @FunctionalInterface
    private interface EntryReader<T> {
        T read(RegistryFriendlyByteBuf buf);
    }
}
