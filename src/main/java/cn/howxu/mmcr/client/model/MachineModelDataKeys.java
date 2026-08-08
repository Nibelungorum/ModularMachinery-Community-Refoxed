package cn.howxu.mmcr.client.model;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.model.data.ModelProperty;

/**
 * Shared model data keys for dynamic machine block models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineModelDataKeys {
    public static final ModelProperty<Identifier> MACHINE_ID = new ModelProperty<>();
    public static final ModelProperty<Identifier> PORT_BASE_TEXTURE = new ModelProperty<>();

    private MachineModelDataKeys() {
    }
}
