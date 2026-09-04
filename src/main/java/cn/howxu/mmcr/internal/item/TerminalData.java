package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.registry.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
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
        TerminalInventoryMode inventoryMode,
        @Nullable Identifier selectedLevelType,
        Map<Identifier, Identifier> selectedLevels,
        int stage,
        boolean previewEnabled,
        int previewLayer) {

    private static final int MIN_SIGNED_Y = -(1 << (BlockPos.PACKED_Y_LENGTH - 1));
    private static final int MAX_SIGNED_Y = (1 << (BlockPos.PACKED_Y_LENGTH - 1)) - 1;

    public static final TerminalData DEFAULT = new TerminalData(null, null, TerminalInventoryMode.INVENTORY,
            null, Map.of(), 1, false, Integer.MAX_VALUE);

    public static final Codec<TerminalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.optionalFieldOf("controller").forGetter(data -> Optional.ofNullable(data.controller)),
            GlobalPos.CODEC.optionalFieldOf("container").forGetter(data -> Optional.ofNullable(data.container)),
            TerminalInventoryMode.CODEC.fieldOf("inventory_mode").forGetter(TerminalData::inventoryMode),
            Identifier.CODEC.optionalFieldOf("selected_level_type").forGetter(data -> Optional.ofNullable(data.selectedLevelType)),
            Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC).fieldOf("selected_levels").forGetter(TerminalData::selectedLevels),
            Codec.INT.fieldOf("stage").forGetter(TerminalData::stage),
            Codec.BOOL.fieldOf("preview_enabled").forGetter(TerminalData::previewEnabled),
            Codec.INT.fieldOf("preview_layer").forGetter(TerminalData::previewLayer)
    ).apply(instance, (controller, container, inventoryMode, selectedLevelType, selectedLevels, stage,
                       previewEnabled, previewLayer) -> new TerminalData(controller.orElse(null), container.orElse(null),
            inventoryMode, selectedLevelType.orElse(null), selectedLevels, stage, previewEnabled, previewLayer)));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), data -> Optional.ofNullable(data.controller),
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), data -> Optional.ofNullable(data.container),
            TerminalInventoryMode.STREAM_CODEC, TerminalData::inventoryMode,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), data -> Optional.ofNullable(data.selectedLevelType),
            ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, Identifier.STREAM_CODEC), TerminalData::selectedLevels,
            ByteBufCodecs.VAR_INT, TerminalData::stage,
            ByteBufCodecs.BOOL, TerminalData::previewEnabled,
            ByteBufCodecs.VAR_INT, TerminalData::previewLayer,
            (controller, container, inventoryMode, selectedLevelType, selectedLevels, stage, previewEnabled, previewLayer) ->
                    new TerminalData(controller.orElse(null), container.orElse(null), inventoryMode,
                            selectedLevelType.orElse(null), selectedLevels, stage, previewEnabled, previewLayer));

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
        return new TerminalData(Objects.requireNonNull(controller, "controller"), container, inventoryMode,
                selectedLevelType, selectedLevels, stage, previewEnabled, previewLayer);
    }

    public TerminalData withContainer(GlobalPos container) {
        return new TerminalData(controller, Objects.requireNonNull(container, "container"), inventoryMode,
                selectedLevelType, selectedLevels, stage, previewEnabled, previewLayer);
    }

    public TerminalData withInventoryMode(TerminalInventoryMode inventoryMode) {
        return new TerminalData(controller, container, inventoryMode, selectedLevelType, selectedLevels, stage,
                previewEnabled, previewLayer);
    }

    public TerminalData withSelectedLevel(Identifier type, Identifier level) {
        LinkedHashMap<Identifier, Identifier> selectedLevels = new LinkedHashMap<>(this.selectedLevels);
        selectedLevels.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(level, "level"));
        return new TerminalData(controller, container, inventoryMode, type, selectedLevels, stage, previewEnabled, previewLayer);
    }

    public TerminalData withStage(int stage) {
        return new TerminalData(controller, container, inventoryMode, selectedLevelType, selectedLevels, stage,
                previewEnabled, previewLayer);
    }

    public TerminalData withPreview(boolean previewEnabled, int previewLayer) {
        return new TerminalData(controller, container, inventoryMode, selectedLevelType, selectedLevels, stage,
                previewEnabled, previewLayer);
    }

    public TerminalData clear() {
        return DEFAULT;
    }

    private static Map<Identifier, Identifier> immutableSelectedLevels(Map<Identifier, Identifier> selectedLevels) {
        Objects.requireNonNull(selectedLevels, "selectedLevels");
        LinkedHashMap<Identifier, Identifier> copy = new LinkedHashMap<>();
        selectedLevels.forEach((type, level) -> copy.put(Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(level, "level")));
        return Collections.unmodifiableMap(copy);
    }
}
