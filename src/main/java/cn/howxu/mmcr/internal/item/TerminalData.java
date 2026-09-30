package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.registry.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent terminal configuration stored on its item stack.
 *
 * @author howxu <dev@howxu.cn>
 */
public record TerminalData(
        @Nullable GlobalPos controller,
        @Nullable GlobalPos container,
        @Nullable GlobalPos ae2AccessPoint,
        TerminalInventoryMode inventoryMode,
        @Nullable ResourceLocation selectedLevelType,
        Map<ResourceLocation, ResourceLocation> selectedLevels,
        int stage,
        boolean previewEnabled,
        int previewLayer) {

    private static final int MIN_SIGNED_Y = -(1 << (BlockPos.PACKED_Y_LENGTH - 1));
    private static final int MAX_SIGNED_Y = (1 << (BlockPos.PACKED_Y_LENGTH - 1)) - 1;

    public static final TerminalData DEFAULT = new TerminalData(null, null, null, TerminalInventoryMode.INVENTORY,
            null, Map.of(), 1, false, Integer.MAX_VALUE);

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, ResourceLocation>> SELECTED_LEVELS_CODEC =
            ByteBufCodecs.map(LinkedHashMap::new, ResourceLocation.STREAM_CODEC, ResourceLocation.STREAM_CODEC);

    public static final Codec<TerminalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.optionalFieldOf("controller").forGetter(data -> Optional.ofNullable(data.controller)),
            GlobalPos.CODEC.optionalFieldOf("container").forGetter(data -> Optional.ofNullable(data.container)),
            GlobalPos.CODEC.optionalFieldOf("ae2_access_point").forGetter(data -> Optional.ofNullable(data.ae2AccessPoint)),
            TerminalInventoryMode.CODEC.fieldOf("inventory_mode").forGetter(TerminalData::inventoryMode),
            ResourceLocation.CODEC.optionalFieldOf("selected_level_type").forGetter(data -> Optional.ofNullable(data.selectedLevelType)),
            Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC).fieldOf("selected_levels").forGetter(TerminalData::selectedLevels),
            Codec.INT.fieldOf("stage").forGetter(TerminalData::stage),
            Codec.BOOL.fieldOf("preview_enabled").forGetter(TerminalData::previewEnabled),
            Codec.INT.fieldOf("preview_layer").forGetter(TerminalData::previewLayer)
    ).apply(instance, (controller, container, ae2AccessPoint, inventoryMode, selectedLevelType, selectedLevels, stage,
                       previewEnabled, previewLayer) -> new TerminalData(controller.orElse(null), container.orElse(null),
            ae2AccessPoint.orElse(null), inventoryMode, selectedLevelType.orElse(null), selectedLevels, stage,
            previewEnabled, previewLayer)));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalData> STREAM_CODEC = StreamCodec.of(
            TerminalData::write, TerminalData::read);

    private static void write(RegistryFriendlyByteBuf buffer, TerminalData data) {
        ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).encode(buffer, Optional.ofNullable(data.controller));
        ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).encode(buffer, Optional.ofNullable(data.container));
        ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).encode(buffer, Optional.ofNullable(data.ae2AccessPoint));
        TerminalInventoryMode.STREAM_CODEC.encode(buffer, data.inventoryMode);
        ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buffer, Optional.ofNullable(data.selectedLevelType));
        SELECTED_LEVELS_CODEC.encode(buffer, data.selectedLevels);
        ByteBufCodecs.VAR_INT.encode(buffer, data.stage);
        ByteBufCodecs.BOOL.encode(buffer, data.previewEnabled);
        ByteBufCodecs.VAR_INT.encode(buffer, data.previewLayer);
    }

    private static TerminalData read(RegistryFriendlyByteBuf buffer) {
        Optional<GlobalPos> controller = ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).decode(buffer);
        Optional<GlobalPos> container = ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).decode(buffer);
        Optional<GlobalPos> ae2AccessPoint = ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).decode(buffer);
        TerminalInventoryMode inventoryMode = TerminalInventoryMode.STREAM_CODEC.decode(buffer);
        Optional<ResourceLocation> selectedLevelType = ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buffer);
        Map<ResourceLocation, ResourceLocation> selectedLevels = SELECTED_LEVELS_CODEC.decode(buffer);
        int stage = ByteBufCodecs.VAR_INT.decode(buffer);
        boolean previewEnabled = ByteBufCodecs.BOOL.decode(buffer);
        int previewLayer = ByteBufCodecs.VAR_INT.decode(buffer);
        return new TerminalData(controller.orElse(null), container.orElse(null), ae2AccessPoint.orElse(null), inventoryMode,
                selectedLevelType.orElse(null), selectedLevels, stage, previewEnabled, previewLayer);
    }

    public TerminalData {
        inventoryMode = Objects.requireNonNull(inventoryMode, "inventoryMode");
        selectedLevels = immutableSelectedLevels(selectedLevels);
        if (stage < 1) throw new IllegalArgumentException("stage must be at least 1");
        if (previewLayer != Integer.MAX_VALUE && (previewLayer < MIN_SIGNED_Y || previewLayer > MAX_SIGNED_Y)) {
            throw new IllegalArgumentException("previewLayer must be a signed Y value or Integer.MAX_VALUE");
        }
    }

    public static TerminalData from(ItemStack stack) {
        return Objects.requireNonNull(stack, "stack").getOrDefault(ModDataComponents.TERMINAL_DATA.get(), DEFAULT);
    }

    public TerminalData withController(GlobalPos controller) {
        return new TerminalData(Objects.requireNonNull(controller, "controller"), container, ae2AccessPoint, inventoryMode,
                selectedLevelType, selectedLevels, stage, previewEnabled, previewLayer);
    }

    public TerminalData withContainer(GlobalPos container) {
        return new TerminalData(controller, Objects.requireNonNull(container, "container"), ae2AccessPoint, inventoryMode,
                selectedLevelType, selectedLevels, stage, previewEnabled, previewLayer);
    }

    public TerminalData withAe2AccessPoint(GlobalPos ae2AccessPoint) {
        return new TerminalData(controller, container, Objects.requireNonNull(ae2AccessPoint, "ae2AccessPoint"), inventoryMode,
                selectedLevelType, selectedLevels, stage, previewEnabled, previewLayer);
    }

    public TerminalData withInventoryMode(TerminalInventoryMode inventoryMode) {
        return switch (Objects.requireNonNull(inventoryMode, "inventoryMode")) {
            case INVENTORY -> new TerminalData(controller, null, null, inventoryMode, selectedLevelType, selectedLevels, stage,
                    previewEnabled, previewLayer);
            case CONTAINER -> new TerminalData(controller, container, null, inventoryMode, selectedLevelType, selectedLevels, stage,
                    previewEnabled, previewLayer);
            case AE2 -> new TerminalData(controller, null, ae2AccessPoint, inventoryMode, selectedLevelType, selectedLevels, stage,
                    previewEnabled, previewLayer);
        };
    }

    public TerminalData withSelectedLevel(ResourceLocation type, ResourceLocation level) {
        LinkedHashMap<ResourceLocation, ResourceLocation> selectedLevels = new LinkedHashMap<>(this.selectedLevels);
        selectedLevels.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(level, "level"));
        return new TerminalData(controller, container, ae2AccessPoint, inventoryMode, type, selectedLevels, stage,
                previewEnabled, previewLayer);
    }

    public TerminalData withStage(int stage) {
        return new TerminalData(controller, container, ae2AccessPoint, inventoryMode, selectedLevelType, selectedLevels, stage,
                previewEnabled, previewLayer);
    }

    public TerminalData withPreview(boolean previewEnabled, int previewLayer) {
        return new TerminalData(controller, container, ae2AccessPoint, inventoryMode, selectedLevelType, selectedLevels, stage,
                previewEnabled, previewLayer);
    }

    public TerminalData clear() {
        return new TerminalData(null, container, ae2AccessPoint, inventoryMode, null, Map.of(), DEFAULT.stage(),
                DEFAULT.previewEnabled(), DEFAULT.previewLayer());
    }

    private static Map<ResourceLocation, ResourceLocation> immutableSelectedLevels(Map<ResourceLocation, ResourceLocation> selectedLevels) {
        Objects.requireNonNull(selectedLevels, "selectedLevels");
        LinkedHashMap<ResourceLocation, ResourceLocation> copy = new LinkedHashMap<>();
        selectedLevels.forEach((type, level) -> copy.put(Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(level, "level")));
        return Collections.unmodifiableMap(copy);
    }
}
