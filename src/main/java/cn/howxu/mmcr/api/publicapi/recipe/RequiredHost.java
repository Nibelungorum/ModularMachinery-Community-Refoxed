package cn.howxu.mmcr.api.publicapi.recipe;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Immutable public required recipe host value.
 * @author howxu <dev@howxu.cn>
 */
public record RequiredHost(Identifier id) {
    public RequiredHost {
        Objects.requireNonNull(id, "id");
    }
}
