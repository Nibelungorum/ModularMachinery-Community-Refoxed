package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
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

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModDataComponents() {}
}
