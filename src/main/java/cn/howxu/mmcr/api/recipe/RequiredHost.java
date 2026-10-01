package cn.howxu.mmcr.api.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable public required recipe host value.
 * @author howxu <dev@howxu.cn>
 */
public record RequiredHost(ResourceLocation id) {
    public RequiredHost {
        Objects.requireNonNull(id, "id");
    }
}
