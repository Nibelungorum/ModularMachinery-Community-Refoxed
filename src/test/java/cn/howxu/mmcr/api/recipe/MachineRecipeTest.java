package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRecipeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void recipe_codec_roundtrip() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "iron_compressor"),
                Identifier.fromNamespaceAndPath("mmcr", "iron_compressor_machine"),
                40,
                List.of(
                        new MachineIngredient.EnergyIngredient(80)
                ),
                List.of()
        );

        var json = MachineRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow();
        var back = MachineRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

        assertThat(back).isEqualTo(recipe);
    }

    @Test
    void recipe_derives_requirements_from_legacy_fields() {
        var nugget = bindItemComponents(Items.IRON_NUGGET);
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "legacy"),
                Identifier.fromNamespaceAndPath("mmcr", "machine"),
                20,
                List.of(
                        new MachineIngredient.ItemIngredient(net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.EnergyIngredient(40)
                ),
                List.of(new ItemStack(nugget, 1))
        );

        assertThat(recipe.requirements()).hasSize(3);
        assertThat(recipe.inputs()).containsExactlyElementsOf(List.of(
                new MachineIngredient.ItemIngredient(net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT), 2),
                new MachineIngredient.EnergyIngredient(40)
        ));
        assertThat(recipe.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(stack.getCount()).isEqualTo(1);
        });
    }

    @Test
    void recipe_codec_prefers_requirements_and_encodes_stable_shape() {
        var root = new JsonObject();
        root.addProperty("id", "mmcr:mixed");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        root.add("inputs", legacyItemInputs(itemId(Items.GOLD_INGOT), 8));
        root.add("outputs", itemOutputs(itemId(Items.GOLD_NUGGET), 1));
        root.add("requirements", requirements(
                itemRequirement("input", itemId(Items.IRON_INGOT), 2),
                itemOutputRequirement(itemId(Items.IRON_NUGGET), 3),
                energyRequirement(60)
        ));

        bindItemComponents(Items.IRON_NUGGET);
        bindItemComponents(Items.GOLD_NUGGET);

        var recipe = MachineRecipe.CODEC.codec().parse(jsonOps(), root).getOrThrow();
        assertThat(recipe.inputs()).hasSize(2);
        assertThat(recipe.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(stack.getCount()).isEqualTo(3);
        });
        assertThat(recipe.requirements()).hasSize(3);

        var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow().getAsJsonObject();
        assertThat(encoded.has("requirements")).isTrue();
        assertThat(encoded.has("inputs")).isFalse();
        assertThat(encoded.has("outputs")).isFalse();
        assertThat(encoded.has("fluid_outputs")).isFalse();
    }

    @Test
    void registry_filters_recipes_by_machine_and_rejects_null_id() {
        var machineId = Identifier.fromNamespaceAndPath("mmcr", "compressor");
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "iron"), machineId, 20, List.of(), List.of());
        var other = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "gold"),
                Identifier.fromNamespaceAndPath("mmcr", "other"), 20, List.of(), List.of());

        var machine = new DynamicMachine(machineId, "Compressor", new BlockArray(java.util.Map.of()));
        RecipeRegistry.register(recipe);
        RecipeRegistry.register(other);

        assertThat(RecipeRegistry.byMachine(machine)).containsExactly(recipe);
        assertThatThrownBy(() -> RecipeRegistry.register(
                new MachineRecipe(null, machineId, 1, List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JsonArray legacyItemInputs(String itemId, int count) {
        var input = new JsonObject();
        input.addProperty("type", "item");
        var item = new JsonArray();
        item.add(itemId);
        input.add("item", item);
        input.addProperty("count", count);
        var inputs = new JsonArray();
        inputs.add(input);
        return inputs;
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Holder<Item> bindItemComponents(Item item) {
        var holder = item.builtInRegistryHolder();
        holder.bindComponents(DataComponentMap.EMPTY);
        return holder;
    }

    private static JsonArray itemOutputs(String itemId, int count) {
        var output = new JsonObject();
        output.addProperty("id", itemId);
        output.addProperty("count", count);
        var outputs = new JsonArray();
        outputs.add(output);
        return outputs;
    }

    private static JsonArray requirements(JsonObject... values) {
        var requirements = new JsonArray();
        for (JsonObject value : values) requirements.add(value);
        return requirements;
    }

    private static JsonObject itemRequirement(String io, String itemId, int count) {
        var requirement = new JsonObject();
        requirement.addProperty("type", "item");
        requirement.addProperty("io", io);
        var item = new JsonArray();
        item.add(itemId);
        requirement.add("item", item);
        requirement.addProperty("count", count);
        return requirement;
    }

    private static JsonObject itemOutputRequirement(String itemId, int count) {
        var requirement = new JsonObject();
        requirement.addProperty("type", "item");
        requirement.addProperty("io", "output");
        var stack = new JsonObject();
        stack.addProperty("id", itemId);
        stack.addProperty("count", count);
        requirement.add("stack", stack);
        return requirement;
    }

    private static JsonObject energyRequirement(int fePerTick) {
        var requirement = new JsonObject();
        requirement.addProperty("type", "energy");
        requirement.addProperty("io", "input");
        requirement.addProperty("fe_per_tick", fePerTick);
        return requirement;
    }

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
