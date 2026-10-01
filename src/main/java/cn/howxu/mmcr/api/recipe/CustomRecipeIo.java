package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Codec-backed recipe IO declaration for a registered requirement or output type.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CustomRecipeIo(ResourceLocation typeId, IOType ioType, JsonElement payload) implements RecipeIoDeclaration {
    public CustomRecipeIo {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(ioType, "ioType");
        Objects.requireNonNull(payload, "payload");
        if (!payload.isJsonObject()) throw new IllegalArgumentException("Recipe IO payload must be an object");
        payload = payload.deepCopy();
    }

    @Override
    public IOType io() {
        return ioType;
    }

    @Override
    public JsonElement payload() {
        return payload.deepCopy();
    }
}
