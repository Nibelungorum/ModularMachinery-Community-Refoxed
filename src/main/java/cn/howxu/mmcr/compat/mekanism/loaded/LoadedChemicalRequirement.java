package cn.howxu.mmcr.compat.mekanism.loaded;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;

import cn.howxu.mmcr.api.recipe.RecipeSemantics;

import cn.howxu.mmcr.api.recipe.requirement.RequirementExecutionPolicy;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.internal.recipe.ResourceSearch;
import java.util.Set;
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
                                         float chance, List<String> tags, float consumeChance) implements ResourceSearch {
    @Override public boolean searchAvailable() { return supportsSearchSnapshot(); }
    @Override public long amount() { return ingredient.amount(); }
    @Override public Object predicateKey() { return List.of(ingredient.kind(), ingredient.id()); }
    @Override public boolean validResource() { return LoadedMekanismBridge.validChemicalSearch(this); }
    @Override public boolean matchesPort(MachineCapability port) { return LoadedMekanismBridge.acceptsChemicalSearchPort(this, port); }
    @Override public Set<AsyncResourceValue> accepted(Set<AsyncResourceValue> universe) {
        return LoadedMekanismBridge.matchingChemicalSearchResources(this, universe);
    }
    @Override public AsyncResourceValue outputValue() {
        return LoadedMekanismBridge.chemicalSearchValue(LoadedMekanismBridge.chemicalSearchOutput(this));
    }
    @Override public ResourceLocation exactCommitInput() {
        return ingredient.kind() == ChemicalIngredient.Kind.CHEMICAL ? ingredient.id() : null;
    }
    @Override public ResourceLocation failure(boolean valid, boolean missingPort) {
        if (!valid) return MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH.id();
        if (missingPort) return (io.isInput() ? BuiltinFailureReasons.MISSING_INPUT : BuiltinFailureReasons.MISSING_OUTPUT).id();
        return (io.isInput() ? MekanismFailureReasons.CHEMICAL_INPUT_MISSING : MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED).id();
    }
    @Override public ResourceLocation missingScopeReason() { return io.isInput() ? BuiltinFailureReasons.MISSING_INPUT.id() : null; }
    @Override public Limits limits(MachineCapability port, int slot) {
        var policy = LoadedMekanismBridge.chemicalSearchPolicy(this, port);
        return new Limits(policy.inputLimit(), policy.outputCapacity(),
                policy.radioactiveRejected() ? MekanismFailureReasons.CHEMICAL_RADIOACTIVITY_REJECTED.id() : null);
    }

    private static final RequirementHandler<LoadedChemicalRequirement> UNAVAILABLE =
            (requirement, capabilities, context) -> unavailable(requirement, context);
    private static volatile Object version = new Object();
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

        @Override
        public LoadedChemicalRequirement applyModifiers(LoadedChemicalRequirement requirement,
                                                         List<RecipeModifier> modifiers) {
            return delegate.applyModifiers(requirement, modifiers);
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
    public RequirementExecutionPolicy execution() { return io.isInput() ? RequirementExecutionPolicy.START_MATERIAL : RequirementExecutionPolicy.LEGACY; }

    @Override
    public long batchAmount(int duration) { return ingredient.amount(); }

    @Override
    public Object batchIdentity(DynamicOps<JsonElement> ops) {
        return List.of(ingredient.kind(), ingredient.id());
    }

    @Override
    public float consumptionProbability() { return consumeChance; }

    @Override
    public LoadedChemicalRequirement asRetainedInput() {
        return consumeChance == 0F ? this : new LoadedChemicalRequirement(io, ingredient, chance, tags, 0F);
    }

    @Override
    public Object derivationVersion() { return supportsSearchSnapshot() ? version : null; }

    @Override
    public RequirementType<LoadedChemicalRequirement> type() {
        return TYPE;
    }

    public static void installHandler(RequirementHandler<LoadedChemicalRequirement> handler) {
        delegate = Objects.requireNonNull(handler, "handler");
        version = new Object();
        RecipeSemantics.changed();
    }

    /** True only for the built-in handler whose resource rules can be captured by value. */
    public static boolean supportsSearchSnapshot() {
        return delegate != UNAVAILABLE && LoadedMekanismBridge.isNativeChemicalHandler(delegate);
    }

    public static void installUnavailableHandler() {
        delegate = UNAVAILABLE;
        version = new Object();
        RecipeSemantics.changed();
    }

    private static LoadedChemicalRequirement copy(LoadedChemicalRequirement requirement) {
        return new LoadedChemicalRequirement(requirement.io(), requirement.ingredient(), requirement.chance(),
                requirement.tags(), requirement.consumeChance());
    }

    public static LoadedChemicalRequirement applyModifiers(LoadedChemicalRequirement requirement,
                                                           List<RecipeModifier> modifiers) {
        long amount = IntegrationTypeHelper.applyChemical(modifiers, requirement.ingredient().amount(), requirement.io());
        ChemicalIngredient ingredient = new ChemicalIngredient(requirement.ingredient().kind(),
                requirement.ingredient().id(), amount);
        float consumeChance = requirement.io() == RecipeModifier.IOType.INPUT
                ? IntegrationTypeHelper.applyChemicalInputChance(modifiers, requirement.consumeChance())
                : requirement.consumeChance();
        float chance = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? IntegrationTypeHelper.applyChemicalOutputChance(modifiers, requirement.chance())
                : requirement.chance();
        return new LoadedChemicalRequirement(requirement.io(), ingredient, chance, requirement.tags(), consumeChance);
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
