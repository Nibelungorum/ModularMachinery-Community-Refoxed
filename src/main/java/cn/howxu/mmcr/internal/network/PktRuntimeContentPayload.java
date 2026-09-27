package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.config.CommonConfig;
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
import java.util.Optional;
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
    private static final int FORMAT_VERSION = 4;

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
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), spec -> Optional.ofNullable(spec.controllerBaseTexture()),
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), spec -> Optional.ofNullable(spec.formedPortBaseTexture()),
            Identifier.STREAM_CODEC, MachineAppearanceSpec::controllerIdleOverlayTexture,
            Identifier.STREAM_CODEC, MachineAppearanceSpec::controllerActiveOverlayTexture,
            (blockId, controllerTexture, portTexture, idleOverlay, activeOverlay) -> new MachineAppearanceSpec(blockId,
                    controllerTexture.orElse(null), portTexture.orElse(null), idleOverlay, activeOverlay));

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
        buf.writeVarInt(FORMAT_VERSION);
        int maxStructureBlocks = MachineStructureSyncCodec.maximumBlockPatternCount();
        buf.writeVarInt(maxStructureBlocks);
        writeMap(buf, snapshot.structures(), maxStructures(),
                (structureBuf, structure) -> MachineStructureSyncCodec.encode(structureBuf, structure, maxStructureBlocks));
        writeMap(buf, snapshot.recipes(), maxRecipes(), MachineRecipeSyncCodec::encode);
        writeMap(buf, snapshot.controllerSpecs(), maxSpecs(), CONTROLLER_SPEC_CODEC::encode);
        writeMap(buf, snapshot.appearances(), maxSpecs(), APPEARANCE_SPEC_CODEC::encode);
        writeMap(buf, snapshot.machineRecipePools(), maxStructures(), PktRuntimeContentPayload::writeRecipePools);
        buf.writeVarLong(snapshot.contentVersion());
    }

    private static PktRuntimeContentPayload decode(RegistryFriendlyByteBuf buf) {
        int formatVersion = buf.readVarInt();
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported runtime content payload version: " + formatVersion);
        }
        int maxStructureBlocks = buf.readVarInt();
        if (maxStructureBlocks <= 0) throw new IllegalArgumentException("Invalid maximum block pattern count: " + maxStructureBlocks);
        Map<Identifier, MachineStructureDefinition> structures = readMap(buf, maxStructures(),
                structureBuf -> MachineStructureSyncCodec.decode(structureBuf, maxStructureBlocks));
        Map<Identifier, MachineRecipe> recipes = readMap(buf, maxRecipes(), MachineRecipeSyncCodec::decode);
        Map<Identifier, MachineControllerSpec> controllerSpecs = readMap(buf, maxSpecs(), CONTROLLER_SPEC_CODEC::decode);
        Map<Identifier, MachineAppearanceSpec> appearances = readMap(buf, maxSpecs(), APPEARANCE_SPEC_CODEC::decode);
        Map<Identifier, List<Identifier>> machineRecipePools = readMap(buf, maxStructures(),
                PktRuntimeContentPayload::readRecipePools);
        validateMap(structures, (id, value) -> {
            if (!id.equals(value.machineId())) throw new IllegalArgumentException("Structure key does not match machine id: " + id);
        });
        validateMap(recipes, (id, value) -> {
            if (!id.equals(value.id())) throw new IllegalArgumentException("Recipe key does not match recipe id: " + id);
        });
        validateMap(controllerSpecs, (id, value) -> {
            if (value.id() == null || !MachineControllerSpec.defaultsFor(id).id().equals(value.id())) {
                throw new IllegalArgumentException("Controller spec key does not match spec id: " + id);
            }
        });
        if (!machineRecipePools.keySet().containsAll(structures.keySet())) {
            throw new IllegalArgumentException("Missing machine recipe pool mapping for synced structure");
        }
        if (recipes.values().stream().anyMatch(recipe -> machineRecipePools.values().stream()
                .noneMatch(pools -> pools.contains(recipe.recipePoolId())))) {
            throw new IllegalArgumentException("Synced recipe pool is not mapped to a machine");
        }
        long contentVersion = buf.readVarLong();
        if (contentVersion < 0) throw new IllegalArgumentException("Invalid runtime content version: " + contentVersion);
        return new PktRuntimeContentPayload(new RuntimeContentSnapshot(
                structures, recipes, controllerSpecs, appearances, machineRecipePools, contentVersion));
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
        checkSize(values.size(), maxTooltipLines(), "tooltip line");
        buf.writeVarInt(values.size());
        for (String value : values) ByteBufCodecs.STRING_UTF8.encode(buf, value);
    }

    private static List<String> readTooltip(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, maxTooltipLines(), "tooltip line");
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        return List.copyOf(values);
    }

    private static void writeRecipePools(RegistryFriendlyByteBuf buf, List<Identifier> recipePools) {
        if (recipePools == null || recipePools.isEmpty()) {
            throw new IllegalArgumentException("Invalid recipe pool count: 0");
        }
        checkSize(recipePools.size(), maxStructures(), "recipe pool");
        if (recipePools.stream().distinct().count() != recipePools.size()) {
            throw new IllegalArgumentException("Duplicate recipe pool id");
        }
        buf.writeVarInt(recipePools.size());
        recipePools.forEach(pool -> Identifier.STREAM_CODEC.encode(buf, pool));
    }

    private static List<Identifier> readRecipePools(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, maxStructures(), "recipe pool");
        if (count == 0) throw new IllegalArgumentException("Invalid recipe pool count: 0");
        List<Identifier> recipePools = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Identifier pool = Identifier.STREAM_CODEC.decode(buf);
            if (recipePools.contains(pool)) throw new IllegalArgumentException("Duplicate recipe pool id: " + pool);
            recipePools.add(pool);
        }
        return List.copyOf(recipePools);
    }

    private static <T> void validateMap(Map<Identifier, T> values, BiConsumer<Identifier, T> validator) {
        values.forEach(validator);
    }

    private static void checkSize(int size, int max, String label) {
        if (size < 0 || size > max) throw new IllegalArgumentException("Invalid " + label + " count: " + size);
    }

    private static int maxStructures() {
        return CommonConfig.valueOrDefault(CommonConfig.RUNTIME_CONTENT_MAX_STRUCTURES, MAX_STRUCTURES);
    }

    private static int maxRecipes() {
        return CommonConfig.valueOrDefault(CommonConfig.RUNTIME_CONTENT_MAX_RECIPES, MAX_RECIPES);
    }

    private static int maxSpecs() {
        return CommonConfig.valueOrDefault(CommonConfig.RUNTIME_CONTENT_MAX_SPECS, MAX_SPECS);
    }

    private static int maxTooltipLines() {
        return CommonConfig.valueOrDefault(CommonConfig.RUNTIME_CONTENT_MAX_TOOLTIP_LINES, MAX_TOOLTIP_LINES);
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
