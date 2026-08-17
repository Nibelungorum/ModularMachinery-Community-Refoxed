package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
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
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class MachineRecipeSchema {
    public static final RecipeComponent<JsonElement> JSON_ELEMENT = new JsonElementComponent();

    public static final RecipeKey<String> MACHINE =
            new RecipeKey<>(StringComponent.ID, "machine", ComponentRole.OTHER).noFunctions();

    public static final RecipeKey<Integer> TICK_TIME =
            new RecipeKey<>(NumberComponent.NON_NEGATIVE_INT, "tick_time", ComponentRole.OTHER);

    public static final RecipeKey<List<JsonElement>> INPUTS =
            new RecipeKey<>(ListRecipeComponent.create(JSON_ELEMENT, true, false), "inputs", ComponentRole.INPUT)
                    .optional(List.of()).exclude();

    public static final RecipeKey<Integer> ENERGY_PER_TICK =
            new RecipeKey<>(NumberComponent.NON_NEGATIVE_INT, "energy_per_tick", ComponentRole.OTHER).optional(0);

    public static final RecipeKey<List<JsonElement>> OUTPUTS =
            new RecipeKey<>(ListRecipeComponent.create(JSON_ELEMENT, true, false), "outputs", ComponentRole.OUTPUT)
                    .optional(List.of()).exclude();

    public static final RecipeKey<List<JsonElement>> MODIFIERS =
            new RecipeKey<>(ListRecipeComponent.create(JSON_ELEMENT, true, false), "modifiers", ComponentRole.OTHER)
                    .optional(List.of()).exclude();

    public static final RecipeKey<Integer> MAX_THREADS =
            new RecipeKey<>(NumberComponent.NON_NEGATIVE_INT, "max_threads", ComponentRole.OTHER).optional(1);

    public static final RecipeKey<Boolean> PARALLELIZED =
            new RecipeKey<>(BooleanComponent.BOOLEAN, "parallelized", ComponentRole.OTHER).optional(false);

    public static final RecipeKey<Boolean> CANCEL_IF_PER_TICK_FAILS =
            new RecipeKey<>(BooleanComponent.BOOLEAN, "cancelIfPerTickFails", ComponentRole.OTHER).optional(false);

    public static final RecipeKey<Boolean> ALLOW_PARTIAL_OUTPUTS =
            new RecipeKey<>(BooleanComponent.BOOLEAN, "allow_partial_outputs", ComponentRole.OTHER).optional(false);

    public static final RecipeSchema SCHEMA = new RecipeSchema(MACHINE, TICK_TIME, INPUTS, ENERGY_PER_TICK, OUTPUTS, MODIFIERS, MAX_THREADS, PARALLELIZED, CANCEL_IF_PER_TICK_FAILS, ALLOW_PARTIAL_OUTPUTS)
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
            .function(new RecipeFunctionInstance("requiredHost", List.of(StringComponent.ID),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID);
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            var hostId = (String) args.get(0);
                            var hosts = cx.recipe().json.getAsJsonArray("required_host_ids");
                            if (hosts == null) {
                                hosts = new com.google.gson.JsonArray();
                                cx.recipe().json.add("required_host_ids", hosts);
                            }
                            hosts.add(hostId);
                            cx.recipe().save();
                        }
                    }))
            .function(new RecipeFunctionInstance("requiresLevel", List.of(StringComponent.ID, StringComponent.ID),
                    new ResolvedRecipeSchemaFunction() {
                        @Override
                        public List<RecipeComponent<?>> arguments() {
                            return List.of(StringComponent.ID, StringComponent.ID);
                        }

                        @Override
                        public void execute(RecipeScriptContext cx, List<Object> args) {
                            var typeId = (String) args.get(0);
                            var levelId = (String) args.get(1);
                            var level = cn.howxu.mmcr.api.machine.level.MachineLevelRegistry.getLevel(net.minecraft.resources.Identifier.parse(levelId));
                            if (level == null || !level.typeId().equals(net.minecraft.resources.Identifier.parse(typeId))) {
                                throw new IllegalArgumentException("Machine level " + levelId + " does not belong to type " + typeId);
                            }
                            var levels = cx.recipe().json.getAsJsonArray("level_requirements");
                            if (levels == null) {
                                levels = new com.google.gson.JsonArray();
                                cx.recipe().json.add("level_requirements", levels);
                            }
                            var requirement = new com.google.gson.JsonObject();
                            requirement.addProperty("type", typeId);
                            requirement.addProperty("level", levelId);
                            levels.add(requirement);
                            cx.recipe().save();
                        }
                    }));

    private MachineRecipeSchema() {
    }

    public static void register(RecipeSchemaRegistry registry) {
        registry.register(MachineRecipeFactory.TYPE, SCHEMA);
    }

    private record JsonElementComponent() implements RecipeComponent<JsonElement> {
        private static final ResourceKey<RecipeComponentType<?>> TYPE = RecipeComponentType.key(MMCR.id("json"));

        @Override
        public ResourceKey<RecipeComponentType<?>> type() {
            return TYPE;
        }

        @Override
        public Codec<JsonElement> codec() {
            return Codec.PASSTHROUGH.xmap(dynamic -> dynamic.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue(),
                    json -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, json));
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
