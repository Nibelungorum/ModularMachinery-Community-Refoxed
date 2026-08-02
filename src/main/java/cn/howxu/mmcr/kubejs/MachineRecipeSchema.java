package cn.howxu.mmcr.kubejs;

import cn.howxu.mmcr.MMCR;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class MachineRecipeSchema {
    private static final RecipeComponent<JsonElement> JSON_ELEMENT = new JsonElementComponent();

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

    public static final RecipeSchema SCHEMA = new RecipeSchema(MACHINE, TICK_TIME, INPUTS, ENERGY_PER_TICK, OUTPUTS)
            .factory(MachineRecipeFactory.INSTANCE);

    private MachineRecipeSchema() {
    }

    public static void register(RecipeSchemaRegistry registry) {
        registry.register(MMCR.id("machine_recipe"), SCHEMA);
    }

    private record JsonElementComponent() implements RecipeComponent<JsonElement> {
        private static final ResourceKey<RecipeComponentType<?>> TYPE = RecipeComponentType.builtin("json");

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
