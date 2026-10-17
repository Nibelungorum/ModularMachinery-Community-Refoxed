package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import java.util.List;

/**
 * Ars-free source quantity transformations and recipe IO payload factories.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceRecipeDeclarations {
    private SourceRecipeDeclarations() {
    }

    public static long applyAmount(long amount, RecipeModifier.IOType io, List<RecipeModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return amount;
        double adjusted = RecipeModifier.applyModifiers(modifiers, "source", io, (double) amount, false);
        if (Double.isNaN(adjusted) || adjusted <= 1D) return 1L;
        if (adjusted >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, (long) Math.floor(adjusted));
    }

    public static long applyOutputMultiplier(long amount, double multiplier) {
        if (multiplier == 1D) return amount;
        double adjusted = amount * multiplier;
        if (Double.isNaN(adjusted) || adjusted <= 1D) return 1L;
        if (adjusted >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, (long) Math.floor(adjusted));
    }

    public static JsonObject inputPayload(long amount) {
        return inputPayload(amount, 1F);
    }

    public static float applyChance(float chance, RecipeModifier.IOType io, List<RecipeModifier> modifiers) {
        float adjusted = RecipeModifier.applyModifiers(modifiers, "source", io, chance, true);
        return Float.isNaN(adjusted) ? 0F : Math.clamp(adjusted, 0F, 1F);
    }

    public static JsonObject inputPayload(long amount, float consumeChance) {
        return SourceRequirement.CODEC.codec().encodeStart(JsonOps.INSTANCE, SourceRequirement.input(amount, consumeChance))
                .getOrThrow().getAsJsonObject();
    }

    public static JsonObject outputPayload(long amount) {
        return outputPayload(amount, 1F);
    }

    public static JsonObject outputPayload(long amount, float chance) {
        return SourceOutput.CODEC.codec().encodeStart(JsonOps.INSTANCE, new SourceOutput(amount, chance))
                .getOrThrow().getAsJsonObject();
    }
}
