package cn.howxu.mmcr.api.publicapi.recipe;

import cn.howxu.mmcr.api.publicapi.recipe.requirement.CustomRequirement;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Codec-backed recipe IO declaration for a registered requirement or output type.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CustomRecipeIo(ResourceLocation typeId, RecipeIo ioType, JsonElement payload)
        implements CustomRequirement {
    public CustomRecipeIo {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(ioType, "ioType");
        Objects.requireNonNull(payload, "payload");
        if (!payload.isJsonObject()) throw new IllegalArgumentException("Recipe IO payload must be an object");
        payload = payload.deepCopy();
    }

    @Override
    public RecipeIo io() {
        return ioType;
    }

    @Override
    public JsonElement payload() {
        return payload.deepCopy();
    }
}
