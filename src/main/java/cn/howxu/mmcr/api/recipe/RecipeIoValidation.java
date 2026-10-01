package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.registration.ApiRuntime;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Public startup recipe lifecycle status API.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeIoValidation {
    private RecipeIoValidation() {
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
    public static CustomRecipeIo custom(ResourceLocation typeId, IOType ioType, JsonElement payload) {
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

    public static MachineRequirement decodeRequirement(CustomRecipeIo custom) {
        MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
        if (!custom.typeId().equals(requirement.type().id()) || requirement.io() != custom.ioType()) {
            throw new IllegalArgumentException("Custom recipe requirement does not match registered type: " + custom.typeId());
        }
        return requirement;
    }

    /** Resolves canonical requirements or decodes custom IO for direct execution.
     * Recipe declarations keep output payloads separately and do not use this conversion.
     */
    public static MachineRequirement decodeIoRequirement(RecipeIoDeclaration source) {
        if (source instanceof MachineRequirement requirement) return requirement;
        if (!(source instanceof CustomRecipeIo io)) {
            throw new IllegalArgumentException("Unsupported recipe IO declaration");
        }
        CustomRecipeIo validated = custom(io.typeId(), io.ioType(), io.payload());
        if (validated.ioType().isInput() || OutputRegistry.typeFor(validated.typeId()) == null) {
            return decodeRequirement(validated);
        }
        MachineRequirement requirement = OutputRegistry.toRequirement(decodeOutput(validated), List.of());
        if (requirement == null) {
            throw new IllegalStateException("Output type does not support execution requirements: " + validated.typeId());
        }
        if (requirement.io() != IOType.OUTPUT) {
            throw new IllegalArgumentException("Custom recipe output conversion must have direction OUTPUT: " + validated.typeId());
        }
        return MachineRequirement.copyOf(requirement);
    }

    private static MachineOutput decodeOutput(CustomRecipeIo custom) {
        var outputType = OutputRegistry.typeFor(custom.typeId());
        MachineOutput output = MachineOutput.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
        if (output.outputType() != outputType) {
            throw new IllegalArgumentException("Custom recipe output does not match registered type: " + custom.typeId());
        }
        return output;
    }

    private static void validatePayload(CustomRecipeIo custom) {
        if (custom.ioType().isInput()) {
            MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
            if (!custom.typeId().equals(requirement.type().id())
                    || requirement.io() != RecipeModifier.IOType.INPUT) {
                throw new IllegalArgumentException("Custom recipe input does not match registered type: " + custom.typeId());
            }
            return;
        }
        var outputType = OutputRegistry.typeFor(custom.typeId());
        if (outputType != null) {
            decodeOutput(custom);
            return;
        }
        MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
        if (!custom.typeId().equals(requirement.type().id())
                || requirement.io() != RecipeModifier.IOType.OUTPUT) {
            throw new IllegalArgumentException("Custom recipe output does not match registered type: " + custom.typeId());
        }
    }
}
