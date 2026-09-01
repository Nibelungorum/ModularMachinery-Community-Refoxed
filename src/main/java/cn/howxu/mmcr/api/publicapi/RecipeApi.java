package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

/** Public startup recipe lifecycle status API.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeApi {
    private RecipeApi() {
    }

    /**
     * Returns whether startup registration is currently accepting recipe definitions.
     *
     * @return {@code true} while the startup registration window is open
     */
    public static boolean isRegistrationOpen() {
        return ApiRuntime.isRegistrationOpen();
    }

    /**
     * Creates a custom recipe IO after checking that its type is registered for its direction.
     *
     * @param typeId registered requirement or output type identifier
     * @param ioType recipe IO direction
     * @param payload codec payload
     * @return validated immutable custom IO declaration
     */
    public static CustomRecipeIo custom(Identifier typeId, RecipeIo ioType, JsonElement payload) {
        CustomRecipeIo custom = new CustomRecipeIo(typeId, ioType, payload);
        if (ioType.isInput() && RequirementHandlerRegistry.typeFor(typeId) == null) {
            throw new IllegalArgumentException("Unknown requirement type: " + typeId);
        }
        if (!ioType.isInput() && OutputRegistry.typeFor(typeId) == null
                && RequirementHandlerRegistry.typeFor(typeId) == null) {
            throw new IllegalArgumentException("Unknown recipe output type: " + typeId);
        }
        validatePayload(custom);
        return custom;
    }

    private static void validatePayload(CustomRecipeIo custom) {
        if (custom.ioType().isInput()) {
            MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
            if (!custom.typeId().equals(requirement.type().id())
                    || requirement.io() != cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT) {
                throw new IllegalArgumentException("Custom recipe input does not match registered type: " + custom.typeId());
            }
            return;
        }
        var outputType = OutputRegistry.typeFor(custom.typeId());
        if (outputType != null) {
            MachineOutput output = MachineOutput.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
            if (output.outputType() != outputType) {
                throw new IllegalArgumentException("Custom recipe output does not match registered type: " + custom.typeId());
            }
            return;
        }
        MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
        if (!custom.typeId().equals(requirement.type().id())
                || requirement.io() != cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT) {
            throw new IllegalArgumentException("Custom recipe output does not match registered type: " + custom.typeId());
        }
    }
}
