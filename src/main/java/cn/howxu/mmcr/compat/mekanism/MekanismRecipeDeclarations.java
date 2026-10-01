package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Mekanism-free declarations used while the optional integration is unavailable.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MekanismRecipeDeclarations {
    private static final RequirementHandler<UnavailableChemicalRequirement> CHEMICAL_UNAVAILABLE =
            (requirement, capabilities, context) -> unavailable(requirement, context);
    private static final RequirementHandler<UnavailableHeatRequirement> HEAT_UNAVAILABLE =
            (requirement, capabilities, context) -> unavailable(requirement, context);

    static final MapCodec<UnavailableChemicalRequirement> CHEMICAL_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> MekanismRecipeTypes.CHEMICAL.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(UnavailableChemicalRequirement::io),
            Codec.STRING.optionalFieldOf("kind", "chemical")
                    .forGetter(value -> value.ingredient().kind().name().toLowerCase(Locale.ROOT)),
            ResourceLocation.CODEC.fieldOf("id").forGetter(value -> value.ingredient().id()),
            Codec.LONG.fieldOf("amount").forGetter(value -> value.ingredient().amount()),
            Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(UnavailableChemicalRequirement::chance),
             Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(UnavailableChemicalRequirement::tags),
             Codec.FLOAT.optionalFieldOf("consume_chance", 1F).forGetter(UnavailableChemicalRequirement::consumeChance)
     ).apply(instance, (ignored, io, kind, id, amount, chance, tags, consumeChance) -> new UnavailableChemicalRequirement(
             io, new ChemicalIngredient(parseKind(kind), id, amount), chance, tags, consumeChance)));

    static final RequirementType<UnavailableChemicalRequirement> CHEMICAL_TYPE = new RequirementType.Definition<>(
            MekanismRecipeTypes.CHEMICAL, CHEMICAL_CODEC, CHEMICAL_UNAVAILABLE,
            RecipeSyncCodec.json(CHEMICAL_CODEC.codec()));

    static final MapCodec<UnavailableHeatRequirement> TEMPERATURE_CODEC = heatCodec(
            MekanismRecipeTypes.HEAT_TEMPERATURE, HeatRequirement.Kind.MINIMUM_TEMPERATURE);
    static final MapCodec<UnavailableHeatRequirement> HEAT_CODEC = heatCodec(
            MekanismRecipeTypes.HEAT, HeatRequirement.Kind.OUTPUT_HEAT);
    static final RequirementType<UnavailableHeatRequirement> TEMPERATURE_TYPE = new RequirementType.Definition<>(
            MekanismRecipeTypes.HEAT_TEMPERATURE, TEMPERATURE_CODEC, HEAT_UNAVAILABLE,
            RecipeSyncCodec.json(TEMPERATURE_CODEC.codec()));
    static final RequirementType<UnavailableHeatRequirement> HEAT_TYPE = new RequirementType.Definition<>(
            MekanismRecipeTypes.HEAT, HEAT_CODEC, HEAT_UNAVAILABLE,
            RecipeSyncCodec.json(HEAT_CODEC.codec()));

    static final OutputType<UnavailableChemicalOutput> CHEMICAL_OUTPUT_TYPE = new OutputType.Definition<>(
            MekanismRecipeTypes.CHEMICAL, chemicalOutputCodec(), UnavailableChemicalOutput::withChance,
            (output, modifiers) -> output, output -> output,
            OutputType.Presentation.defaults(MekanismRecipeTypes.CHEMICAL), MekanismRecipeTypes.CHEMICAL.toString(),
            (output, tags) -> UnavailableChemicalRequirement.output(output.id(), output.amount(), output.chance()),
            requirement -> requirement instanceof UnavailableChemicalRequirement chemical
                    && chemical.io() == RecipeModifier.IOType.OUTPUT,
            requirement -> requirement instanceof UnavailableChemicalRequirement chemical
                    && chemical.io() == RecipeModifier.IOType.OUTPUT
                    ? new UnavailableChemicalOutput(chemical.ingredient().id(), chemical.ingredient().amount(), chemical.chance())
                    : null,
            RecipeSyncCodec.json(chemicalOutputCodec().codec()));

    static final OutputType<UnavailableHeatOutput> HEAT_OUTPUT_TYPE = new OutputType.Definition<>(
            MekanismRecipeTypes.HEAT, heatOutputCodec(), (output, ignored) -> output,
            (output, modifiers) -> output, output -> output,
            OutputType.Presentation.defaults(MekanismRecipeTypes.HEAT), MekanismRecipeTypes.HEAT.toString(),
            (output, tags) -> UnavailableHeatRequirement.outputHeat(output.heat()),
            requirement -> requirement instanceof UnavailableHeatRequirement heat
                    && heat.type() == HEAT_TYPE,
            requirement -> requirement instanceof UnavailableHeatRequirement heat
                    && heat.type() == HEAT_TYPE ? new UnavailableHeatOutput(heat.heat().value()) : null,
            RecipeSyncCodec.json(heatOutputCodec().codec()));

    private MekanismRecipeDeclarations() {
    }

    public static void registerUnavailable() {
        registerRequirement(CHEMICAL_TYPE);
        registerRequirement(TEMPERATURE_TYPE);
        registerRequirement(HEAT_TYPE);
        registerOutput(CHEMICAL_OUTPUT_TYPE);
        registerOutput(HEAT_OUTPUT_TYPE);
    }

    private static void registerRequirement(RequirementType<?> type) {
        if (RequirementHandlerRegistry.typeFor(type.id()) == null) {
            registerRequirementUnchecked(type);
        }
    }

    private static void registerOutput(OutputType<?> type) {
        if (OutputRegistry.typeFor(type.id()) == null) {
            registerOutputUnchecked(type);
        }
    }

    private static <R extends MachineRequirement> void registerRequirementUnchecked(RequirementType<R> type) {
        RequirementHandlerRegistry.register(type);
    }

    private static <O extends MachineOutput> void registerOutputUnchecked(OutputType<O> type) {
        OutputRegistry.register(type);
    }

    private static MapCodec<UnavailableHeatRequirement> heatCodec(ResourceLocation id, HeatRequirement.Kind kind) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(ignored -> id.toString()),
                RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", kind == HeatRequirement.Kind.OUTPUT_HEAT
                                ? RecipeModifier.IOType.OUTPUT : RecipeModifier.IOType.INPUT)
                        .forGetter(UnavailableHeatRequirement::io),
                Codec.DOUBLE.fieldOf("value").forGetter(value -> value.heat().value()),
                Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(UnavailableHeatRequirement::tags)
        ).apply(instance, (ignored, io, value, tags) -> new UnavailableHeatRequirement(io,
                kind == HeatRequirement.Kind.OUTPUT_HEAT ? HeatRequirement.outputHeat(value)
                        : HeatRequirement.minimumTemperature(value), tags)));
    }

    private static MapCodec<UnavailableChemicalOutput> chemicalOutputCodec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(ignored -> MekanismRecipeTypes.CHEMICAL.toString()),
                ResourceLocation.CODEC.fieldOf("id").forGetter(UnavailableChemicalOutput::id),
                Codec.LONG.fieldOf("amount").forGetter(UnavailableChemicalOutput::amount),
                Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(UnavailableChemicalOutput::chance)
        ).apply(instance, (ignored, id, amount, chance) -> new UnavailableChemicalOutput(id, amount, chance)));
    }

    private static MapCodec<UnavailableHeatOutput> heatOutputCodec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(ignored -> MekanismRecipeTypes.HEAT.toString()),
                Codec.STRING.optionalFieldOf("io", RecipeModifier.IOType.OUTPUT.getKey()).forGetter(ignored -> "output"),
                Codec.DOUBLE.fieldOf("value").forGetter(UnavailableHeatOutput::heat)
        ).apply(instance, (ignored, io, heat) -> new UnavailableHeatOutput(heat)));
    }

    private static ChemicalIngredient.Kind parseKind(String kind) {
        try {
            return ChemicalIngredient.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown chemical ingredient kind: " + kind, exception);
        }
    }

    private static RequirementPlan unavailable(MachineRequirement requirement, PlanningContext context) {
        return RequirementHandlerSupport.blockedPlan(requirement, context,
                MekanismFailureReasons.MEKANISM_UNAVAILABLE);
    }

    record UnavailableChemicalRequirement(RecipeModifier.IOType io, ChemicalIngredient ingredient,
                                          float chance, List<String> tags, float consumeChance) implements MachineRequirement {
        UnavailableChemicalRequirement {
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

        static UnavailableChemicalRequirement output(ResourceLocation id, long amount, float chance) {
            return new UnavailableChemicalRequirement(RecipeModifier.IOType.OUTPUT,
                     ChemicalIngredient.chemical(id, amount), chance, List.of(), 1F);
        }

        @Override
        public RequirementType<UnavailableChemicalRequirement> type() {
            return CHEMICAL_TYPE;
        }
    }

    record UnavailableHeatRequirement(RecipeModifier.IOType io, HeatRequirement heat,
                                      List<String> tags) implements MachineRequirement {
        UnavailableHeatRequirement {
            Objects.requireNonNull(io, "io");
            Objects.requireNonNull(heat, "heat");
            tags = tags == null ? List.of() : List.copyOf(tags);
            if (io == RecipeModifier.IOType.INPUT && heat.kind() != HeatRequirement.Kind.MINIMUM_TEMPERATURE) {
                throw new IllegalArgumentException("heat inputs must specify a minimum temperature");
            }
            if (io == RecipeModifier.IOType.OUTPUT && heat.kind() != HeatRequirement.Kind.OUTPUT_HEAT) {
                throw new IllegalArgumentException("heat outputs must specify output heat");
            }
        }

        static UnavailableHeatRequirement outputHeat(double heat) {
            return new UnavailableHeatRequirement(RecipeModifier.IOType.OUTPUT,
                    HeatRequirement.outputHeat(heat), List.of());
        }

        @Override
        public RequirementType<UnavailableHeatRequirement> type() {
            return heat.kind() == HeatRequirement.Kind.OUTPUT_HEAT ? HEAT_TYPE : TEMPERATURE_TYPE;
        }
    }

    record UnavailableChemicalOutput(ResourceLocation id, long amount, float chance) implements MachineOutput {
        UnavailableChemicalOutput {
            Objects.requireNonNull(id, "id");
            if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
            if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
                throw new IllegalArgumentException("chance must be between 0 and 1");
            }
        }

        @Override
        public OutputType<UnavailableChemicalOutput> outputType() {
            return CHEMICAL_OUTPUT_TYPE;
        }

        @Override
        public float chance() {
            return chance;
        }

        @Override
        public UnavailableChemicalOutput withChance(float chance) {
            return new UnavailableChemicalOutput(id, amount, chance);
        }
    }

    record UnavailableHeatOutput(double heat) implements MachineOutput {
        UnavailableHeatOutput {
            if (!Double.isFinite(heat) || heat < 0D) throw new IllegalArgumentException("heat must be non-negative");
        }

        @Override
        public OutputType<UnavailableHeatOutput> outputType() {
            return HEAT_OUTPUT_TYPE;
        }

        @Override
        public float chance() {
            return 1F;
        }

        @Override
        public UnavailableHeatOutput withChance(float chance) {
            if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
                throw new IllegalArgumentException("chance must be between 0 and 1");
            }
            return this;
        }
    }
}
