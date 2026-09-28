package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Shared model data keys for dynamic machine block models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineModelDataKeys {
    public static final ModelProperty<ResourceLocation> MACHINE_ID = new ModelProperty<>();
    public static final ModelProperty<ResourceLocation> PORT_BASE_TEXTURE = new ModelProperty<>();
    public static final ModelProperty<MachineAppearanceSpec.TextureSource> PORT_TEXTURE_SOURCE = new ModelProperty<>();
    public static final ModelProperty<Boolean> PORT_LINKED = new ModelProperty<>();

    private MachineModelDataKeys() {
    }
}
