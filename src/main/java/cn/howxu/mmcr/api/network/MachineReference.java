package cn.howxu.mmcr.api.network;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Stable identity of a formed machine controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineReference(Identifier type, long hash) {
    public MachineReference {
        Objects.requireNonNull(type, "type");
    }
}
