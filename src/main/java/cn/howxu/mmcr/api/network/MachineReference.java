package cn.howxu.mmcr.api.network;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Stable identity of a formed machine controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineReference(ResourceLocation type, long hash) {
    public MachineReference {
        Objects.requireNonNull(type, "type");
    }
}
