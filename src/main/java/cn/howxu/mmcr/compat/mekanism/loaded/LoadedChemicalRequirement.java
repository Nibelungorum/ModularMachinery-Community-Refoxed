package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler.ResourceWakeup;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Internal chemical requirement payload shared by the optional bridge handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LoadedChemicalRequirement(RecipeModifier.IOType io, ChemicalIngredient ingredient,
                                         float chance, List<String> tags, float consumeChance) implements MachineRequirement {
    private static final RequirementHandler<LoadedChemicalRequirement> UNAVAILABLE =
            (requirement, capabilities, context) -> unavailable(requirement, context);
    private static volatile RequirementHandler<LoadedChemicalRequirement> delegate = UNAVAILABLE;
    private static final RequirementHandler<LoadedChemicalRequirement> DELEGATING_HANDLER = new RequirementHandler<>() {
        @Override
        public RequirementPlan plan(LoadedChemicalRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return delegate.plan(requirement, capabilities, context);
        }

        @Override
        public List<ResourceWakeup> resourceWakeups(LoadedChemicalRequirement requirement) {
            return delegate.resourceWakeups(requirement);
        }
    };

    public static final MapCodec<LoadedChemicalRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> MekanismRecipeTypes.CHEMICAL.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(LoadedChemicalRequirement::io),
            Codec.STRING.optionalFieldOf("kind", "chemical")
                    .forGetter(value -> value.ingredient().kind().name().toLowerCase(Locale.ROOT)),
            ResourceLocation.CODEC.fieldOf("id").forGetter(value -> value.ingredient().id()),
            Codec.LONG.fieldOf("amount").forGetter(value -> value.ingredient().amount()),
            Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(LoadedChemicalRequirement::chance),
             Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(LoadedChemicalRequirement::tags),
             Codec.FLOAT.optionalFieldOf("consume_chance", 1F).forGetter(LoadedChemicalRequirement::consumeChance)
     ).apply(instance, (ignored, io, kind, id, amount, chance, tags, consumeChance) ->
             new LoadedChemicalRequirement(io, new ChemicalIngredient(parseKind(kind), id, amount), chance, tags, consumeChance)));

    public static final RequirementType<LoadedChemicalRequirement> TYPE = new RequirementType.Definition<>(
            MekanismRecipeTypes.CHEMICAL, CODEC, DELEGATING_HANDLER,
            LoadedChemicalRequirement::copy, RecipeSyncCodec.json(CODEC.codec(), LoadedChemicalRequirement::validateSync));

    public LoadedChemicalRequirement {
        Objects.requireNonNull(io, "io");
        Objects.requireNonNull(ingredient, "ingredient");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException("chance must be between 0 and 1");
        }
        if (!Float.isFinite(consumeChance) || consumeChance < 0F || consumeChance > 1F) {
            throw new IllegalArgumentException("consumeChance must be between 0 and 1");
        }
        if (io == RecipeModifier.IOType.OUTPUT && ingredient.kind() != ChemicalIngredient.Kind.CHEMICAL) {
            throw new IllegalArgumentException("chemical outputs must name a chemical");
        }
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static LoadedChemicalRequirement input(ChemicalIngredient ingredient) {
        return new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT, ingredient, 1F, List.of(), 1F);
    }

    public static LoadedChemicalRequirement output(ResourceLocation id, long amount, float chance) {
        return new LoadedChemicalRequirement(RecipeModifier.IOType.OUTPUT,
                ChemicalIngredient.chemical(id, amount), chance, List.of(), 1F);
    }

    @Override
    public RequirementType<LoadedChemicalRequirement> type() {
        return TYPE;
    }

    public static void installHandler(RequirementHandler<LoadedChemicalRequirement> handler) {
        delegate = Objects.requireNonNull(handler, "handler");
    }

    public static void installUnavailableHandler() {
        delegate = UNAVAILABLE;
    }

    private static LoadedChemicalRequirement copy(LoadedChemicalRequirement requirement) {
        return new LoadedChemicalRequirement(requirement.io(), requirement.ingredient(), requirement.chance(),
                requirement.tags(), requirement.consumeChance());
    }

    public static LoadedChemicalRequirement applyModifiers(LoadedChemicalRequirement requirement,
                                                           List<RecipeModifier> modifiers) {
        if (requirement.io() != RecipeModifier.IOType.INPUT) return requirement;
        float consumeChance = IntegrationTypeHelper.applyChemicalInputChance(modifiers, requirement.consumeChance());
        return new LoadedChemicalRequirement(requirement.io(), requirement.ingredient(), requirement.chance(),
                requirement.tags(), consumeChance);
    }
    private static void validateSync(LoadedChemicalRequirement requirement) {
        if (requirement.tags().size() > 1024) throw new IllegalArgumentException("Invalid tag count");
    }

    private static ChemicalIngredient.Kind parseKind(String kind) {
        try {
            return ChemicalIngredient.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown chemical ingredient kind: " + kind, exception);
        }
    }

    private static RequirementPlan unavailable(LoadedChemicalRequirement requirement, PlanningContext context) {
        return RequirementHandlerSupport.blockedPlan(requirement, context,
                MekanismFailureReasons.MEKANISM_UNAVAILABLE);
    }
}
