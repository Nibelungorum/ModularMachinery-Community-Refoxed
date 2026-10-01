package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;

import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalOutput;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeIoValidation;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.component.BooleanComponent;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeFunctionInstance;
import dev.latvian.mods.kubejs.recipe.schema.function.ResolvedRecipeSchemaFunction;
import dev.latvian.mods.kubejs.util.IntBounds;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class MachineRecipeSchema {
    private static final RecipeComponentType<JsonElement> JSON_ELEMENT_TYPE =
            RecipeComponentType.unit(MMCR.id("json"), JsonElementComponent::new);
    public static final RecipeComponent<JsonElement> JSON_ELEMENT = JSON_ELEMENT_TYPE.instance();

    public static final RecipeKey<String> RECIPE_POOL =
            new RecipeKey<>(StringComponent.ID.instance(), "recipe_pool", ComponentRole.OTHER).noFunctions();

    public static final RecipeKey<Integer> TICK_TIME =
            new RecipeKey<>(NumberComponent.NON_NEGATIVE_INT.instance(), "tick_time", ComponentRole.OTHER);

    public static final RecipeKey<List<JsonElement>> OUTPUTS =
            new RecipeKey<>(ListRecipeComponent.create(JSON_ELEMENT, true, false, IntBounds.OPTIONAL, Optional.empty()), "outputs", ComponentRole.OUTPUT)
                    .optional(List.of()).exclude();

    public static final RecipeKey<List<JsonElement>> MODIFIERS =
            new RecipeKey<>(ListRecipeComponent.create(JSON_ELEMENT, true, false, IntBounds.OPTIONAL, Optional.empty()), "modifiers", ComponentRole.OTHER)
                    .optional(List.of()).exclude();

    public static final RecipeKey<List<JsonElement>> REQUIREMENTS =
            new RecipeKey<>(ListRecipeComponent.create(JSON_ELEMENT, true, false, IntBounds.OPTIONAL, Optional.empty()), "requirements", ComponentRole.OTHER);

    public static final RecipeKey<Integer> MAX_THREADS =
            new RecipeKey<>(NumberComponent.NON_NEGATIVE_INT.instance(), "max_threads", ComponentRole.OTHER).optional(1);

    public static final RecipeKey<Boolean> PARALLELIZED =
            new RecipeKey<>(BooleanComponent.BOOLEAN.instance(), "parallelized", ComponentRole.OTHER).optional(false);

    public static final RecipeKey<Boolean> CANCEL_IF_PER_TICK_FAILS =
            new RecipeKey<>(BooleanComponent.BOOLEAN.instance(), "cancelIfPerTickFails", ComponentRole.OTHER).optional(false);

    public static final RecipeKey<Boolean> ALLOW_PARTIAL_OUTPUTS =
            new RecipeKey<>(BooleanComponent.BOOLEAN.instance(), "allow_partial_outputs", ComponentRole.OTHER).optional(false);

    public static final RecipeSchema SCHEMA = new RecipeSchema(RECIPE_POOL, TICK_TIME, REQUIREMENTS, OUTPUTS, MODIFIERS,
            MAX_THREADS, PARALLELIZED,
            CANCEL_IF_PER_TICK_FAILS, ALLOW_PARTIAL_OUTPUTS)
            .factory(MachineRecipeFactory.INSTANCE)
            .function(new RecipeFunctionInstance("allowPartialOutputs", List.of(),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of();
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            cx.recipe().json.addProperty("allow_partial_outputs", true);
                            cx.recipe().save();
                        }
                    }))
            .function(new RecipeFunctionInstance("smartInterfaceInput", List.of(StringComponent.ID.instance(), NumberComponent.FLOAT),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), NumberComponent.FLOAT);
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            appendRequirement(cx.recipe(), MachineRecipeFactory.smartInterfaceInput(
                                    (String) args.get(0), ((Number) args.get(1)).floatValue()));
                        }
                    }))
            .function(new RecipeFunctionInstance("smartInterfaceInputRange", List.of(StringComponent.ID.instance(),
                    NumberComponent.FLOAT, NumberComponent.FLOAT), new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), NumberComponent.FLOAT, NumberComponent.FLOAT);
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            appendRequirement(cx.recipe(), MachineRecipeFactory.smartInterfaceInput(
                                    (String) args.get(0), ((Number) args.get(1)).floatValue(),
                                    ((Number) args.get(2)).floatValue()));
                        }
                    }))
            .function(new RecipeFunctionInstance("smartInterfaceOutput", List.of(StringComponent.ID.instance(), NumberComponent.FLOAT),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), NumberComponent.FLOAT);
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            appendRequirement(cx.recipe(), MachineRecipeFactory.smartInterfaceOutput(
                                    (String) args.get(0), ((Number) args.get(1)).floatValue()));
                        }
                    }))
            .function(new RecipeFunctionInstance("custom", List.of(StringComponent.ID.instance(), StringComponent.ID.instance(), JSON_ELEMENT),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), StringComponent.ID.instance(), JSON_ELEMENT);
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            IOType io = switch ((String) args.get(1)) {
                                case "input" -> IOType.INPUT;
                                case "output" -> IOType.OUTPUT;
                                default -> throw new IllegalArgumentException("Unknown recipe IO: " + args.get(1));
                            };
                            var custom = RecipeIoValidation.custom(ResourceLocation.parse((String) args.get(0)), io,
                                    (JsonElement) args.get(2));
                            if (io.isInput() || OutputRegistry.typeFor(custom.typeId()) == null) {
                                 appendRequirement(cx.recipe(), RecipeIoValidation.decodeRequirement(custom));
                             } else appendOutput(cx.recipe(), MachineRecipeConverter.toOutput(custom));
                        }
                    }))
            .function(new RecipeFunctionInstance("requiredHost", List.of(StringComponent.ID.instance()),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance());
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            var hostId = (String) args.get(0);
                            var hosts = cx.recipe().json.getAsJsonArray("required_host_ids");
                            if (hosts == null) {
                                hosts = new JsonArray();
                                cx.recipe().json.add("required_host_ids", hosts);
                            }
                            hosts.add(hostId);
                            cx.recipe().save();
                        }
                    }))
            .function(new RecipeFunctionInstance("requiresLevel", List.of(StringComponent.ID.instance(), StringComponent.ID.instance()),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), StringComponent.ID.instance());
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            var typeId = (String) args.get(0);
                            var levelId = (String) args.get(1);
                            var level = MachineLevelRegistry.getLevel(ResourceLocation.parse(levelId));
                            if (level == null || !level.typeId().equals(ResourceLocation.parse(typeId))) {
                                throw new IllegalArgumentException("Machine level " + levelId + " does not belong to type " + typeId);
                            }
                            appendRequirement(cx.recipe(), LevelRequirement.input(
                                    ResourceLocation.parse(typeId), ResourceLocation.parse(levelId)));
                        }
                    }))
            .function(new RecipeFunctionInstance("chemicalInput",
                    List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance()),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance());
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            ResourceLocation id = requireChemicalId((String) args.get(0), "chemicalId");
                            long amount = ((Number) args.get(1)).longValue();
                            appendChemicalInput(cx.recipe(),
                                    ChemicalIngredient.chemical(id, amount));
                        }
                    }))
            .function(new RecipeFunctionInstance("chemicalTagInput",
                    List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance()),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance());
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            ResourceLocation id = requireChemicalId((String) args.get(0), "tagId");
                            long amount = ((Number) args.get(1)).longValue();
                            appendChemicalInput(cx.recipe(),
                                    ChemicalIngredient.tag(id, amount));
                        }
                    }))
            .function(new RecipeFunctionInstance("chemicalInputChance",
                    List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance(), NumberComponent.doubleRange(0D, 1D)),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance(),
                                    NumberComponent.doubleRange(0D, 1D));
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            ResourceLocation id = requireChemicalId((String) args.get(0), "chemicalId");
                            long amount = ((Number) args.get(1)).longValue();
                            float consumeChance = ((Number) args.get(2)).floatValue();
                            appendChemicalInput(cx.recipe(),
                                    ChemicalIngredient.chemical(id, amount), consumeChance);
                        }
                    }))
            .function(new RecipeFunctionInstance("chemicalTagInputChance",
                    List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance(), NumberComponent.doubleRange(0D, 1D)),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance(),
                                    NumberComponent.doubleRange(0D, 1D));
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            ResourceLocation id = requireChemicalId((String) args.get(0), "tagId");
                            long amount = ((Number) args.get(1)).longValue();
                            float consumeChance = ((Number) args.get(2)).floatValue();
                            appendChemicalInput(cx.recipe(),
                                    ChemicalIngredient.tag(id, amount), consumeChance);
                        }
                    }))
            .function(new RecipeFunctionInstance("chemicalOutput",
                    List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance(), NumberComponent.doubleRange(0D, 1D)),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID.instance(), NumberComponent.POSITIVE_LONG.instance(),
                                    NumberComponent.doubleRange(0D, 1D));
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            ResourceLocation id = requireChemicalId((String) args.get(0), "chemicalId");
                            long amount = ((Number) args.get(1)).longValue();
                            double chance = ((Number) args.get(2)).doubleValue();
                            if (!Double.isFinite(chance)) {
                                throw new IllegalArgumentException("chance must be finite");
                            }
                            appendChemicalOutput(cx.recipe(), id, amount, chance);
                        }
                    }))
            .function(new RecipeFunctionInstance("heatTemperatureInput",
                    List.of(NumberComponent.NON_NEGATIVE_DOUBLE.instance()),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(NumberComponent.NON_NEGATIVE_DOUBLE.instance());
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            double temperature = ((Number) args.get(0)).doubleValue();
                            appendHeatRequirement(cx.recipe(), MekanismPortFamilies.HEAT_TEMPERATURE,
                                    IOType.INPUT, HeatRequirement.minimumTemperature(temperature));
                        }
                    }))
            .function(new RecipeFunctionInstance("heatOutput",
                    List.of(NumberComponent.NON_NEGATIVE_DOUBLE.instance()),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(NumberComponent.NON_NEGATIVE_DOUBLE.instance());
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            double heat = ((Number) args.get(0)).doubleValue();
                            appendHeatRequirement(cx.recipe(), MekanismPortFamilies.HEAT, IOType.OUTPUT,
                                    HeatRequirement.outputHeat(heat));
                        }
                    }));

    private MachineRecipeSchema() {
    }

    public static void register(RecipeSchemaRegistry registry) {
        registry.register(MachineRecipeFactory.TYPE, SCHEMA);
    }

    private static void appendRequirement(KubeRecipe recipe, MachineRequirement requirement) {
        JsonArray requirements = recipe.json.getAsJsonArray("requirements");
        if (requirements == null) {
            requirements = new JsonArray();
            recipe.json.add("requirements", requirements);
        }
        requirements.add(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow());
        recipe.save();
    }

    private static void appendOutput(KubeRecipe recipe, MachineOutput output) {
        JsonArray outputs = recipe.json.getAsJsonArray("outputs");
        if (outputs == null) {
            outputs = new JsonArray();
            recipe.json.add("outputs", outputs);
        }
        outputs.add(MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow());
        recipe.save();
    }

    private static void appendChemicalInput(KubeRecipe recipe, ChemicalIngredient ingredient) {
        appendChemicalInput(recipe, ingredient, 1F);
    }

    private static void appendChemicalInput(KubeRecipe recipe, ChemicalIngredient ingredient, float consumeChance) {
        var custom = RecipeIoValidation.custom(MekanismPortFamilies.CHEMICAL, IOType.INPUT,
                MachineRecipeBuilder.chemicalInputPayload(ingredient, consumeChance));
        appendRequirement(recipe, RecipeIoValidation.decodeRequirement(custom));
    }

    private static void appendChemicalOutput(KubeRecipe recipe, ResourceLocation id, long amount, double chance) {
        var output = ChemicalOutput.of(id, amount, (float) chance);
        var custom = RecipeIoValidation.custom(MekanismPortFamilies.CHEMICAL, IOType.OUTPUT,
                MachineRecipeBuilder.chemicalOutputPayload(output));
        appendOutput(recipe, MachineRecipeConverter.toOutput(custom));
    }

    private static void appendHeatRequirement(KubeRecipe recipe, ResourceLocation typeId, IOType io,
                                              HeatRequirement requirement) {
        var custom = RecipeIoValidation.custom(typeId, io,
                MachineRecipeBuilder.heatPayload(requirement, typeId, io));
        if (io.isInput() || OutputRegistry.typeFor(custom.typeId()) == null) {
            appendRequirement(recipe, RecipeIoValidation.decodeRequirement(custom));
        } else {
            appendOutput(recipe, MachineRecipeConverter.toOutput(custom));
        }
    }

    private static ResourceLocation requireChemicalId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value, exception);
        }
    }

    private record JsonElementComponent(RecipeComponentType<JsonElement> type) implements RecipeComponent<JsonElement> {

        @Override
        public RecipeComponentType<JsonElement> type() {
            return type;
        }

        @Override
        public Codec<JsonElement> codec() {
            return Codec.PASSTHROUGH.xmap(dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
                    json -> new Dynamic<>(JsonOps.INSTANCE, json));
        }

        @Override
        public TypeInfo typeInfo() {
            return TypeInfo.of(JsonElement.class);
        }

        @Override
        public JsonElement wrap(RecipeScriptContext cx, @Nullable Object from) {
            return JsonUtils.of(cx.cx(), from);
        }

        @Override
        public boolean allowEmpty() {
            return true;
        }
    }
}
