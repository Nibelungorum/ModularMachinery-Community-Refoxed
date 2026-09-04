package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.network.KeyCardBinding;
import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.item.TerminalMode;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mod data component registrations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ModDataComponents {

    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MMCR.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MultiblockDetectorSelection>>
            MULTIBLOCK_DETECTOR_SELECTION = REGISTER.registerComponentType("multiblock_detector_selection", builder ->
                    builder.persistent(MultiblockDetectorSelection.CODEC)
                            .networkSynchronized(MultiblockDetectorSelection.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>>
            MULTIBLOCK_DETECTOR_MASK = REGISTER.registerComponentType("multiblock_detector_mask", builder ->
                    builder.persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TerminalMode>>
            TERMINAL_MODE = REGISTER.registerComponentType("terminal_mode", builder ->
                    builder.persistent(TerminalMode.CODEC)
                            .networkSynchronized(TerminalMode.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TerminalData>>
            TERMINAL_DATA = REGISTER.registerComponentType("terminal_data", builder ->
                    builder.persistent(TerminalData.CODEC)
                            .networkSynchronized(TerminalData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KeyCardBinding>>
            KEY_CARD_BINDING = REGISTER.registerComponentType("key_card_binding", builder ->
                    builder.persistent(KeyCardBinding.CODEC)
                            .networkSynchronized(KeyCardBinding.STREAM_CODEC));

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModDataComponents() {}
}
