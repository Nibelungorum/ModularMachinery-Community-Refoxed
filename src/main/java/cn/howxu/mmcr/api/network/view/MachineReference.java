package cn.howxu.mmcr.api.network.view;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Stable public identity of a formed machine controller.
 * @author howxu <dev@howxu.cn>
 */
public record MachineReference(ResourceLocation type, long hash) {
    public MachineReference {
        Objects.requireNonNull(type, "type");
    }

    /** Internal bridge value for MMCR adapters. */
    public Object bridgeValue() {
        return toInternal();
    }

    public cn.howxu.mmcr.api.network.MachineReference toInternal() {
        return new cn.howxu.mmcr.api.network.MachineReference(type, hash);
    }

    public static MachineReference fromInternal(cn.howxu.mmcr.api.network.MachineReference reference) {
        return new MachineReference(reference.type(), reference.hash());
    }
}
