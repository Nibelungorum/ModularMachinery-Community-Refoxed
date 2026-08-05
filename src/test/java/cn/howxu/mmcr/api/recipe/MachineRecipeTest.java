package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

        var json = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow();
        var back = MachineRecipe.CODEC.codec().parse(jsonOps(), json).getOrThrow();

        assertThat(back).isEqualTo(recipe);
    }

    @Test
    void codec_decodes_new_requirements_and_prefers_them_over_legacy_fields() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "iron_compressor"),
                Identifier.fromNamespaceAndPath("mmcr", "iron_compressor_machine"),
                40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COPPER_INGOT), 1)),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(new ItemRequirement(Ingredient.of(Items.IRON_INGOT), 2, "north", IOType.INPUT))
        );

        var json = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow();
        var back = MachineRecipe.CODEC.codec().parse(jsonOps(), json).getOrThrow();

        assertThat(back.requirements()).containsExactly(new ItemRequirement(Ingredient.of(Items.IRON_INGOT), 2, "north", IOType.INPUT));
        assertThat(back.inputs()).containsExactly(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2));
    }

    @Test
    void codec_derives_fluid_output_requirements_from_legacy_field() {
        String json = """
                {
                  "id": "mmcr:water_output",
                  "machine": "mmcr:fluid_machine",
                  "tick_time": 40,
                  "fluid_outputs": [
                    {"type": "fluid", "fluid": "minecraft:water", "amount": 1000}
                  ]
                }
                """;

        var recipe = MachineRecipe.CODEC.codec().parse(jsonOps(), com.google.gson.JsonParser.parseString(json)).getOrThrow();

        assertThat(recipe.requirements()).containsExactly(new FluidRequirement(FluidIngredient.of(Fluids.WATER), 1000, null, IOType.OUTPUT));
        assertThat(recipe.fluidOutputs()).containsExactly(new FluidRequirement(FluidIngredient.of(Fluids.WATER), 1000, null, IOType.OUTPUT));
    }

    @Test
    void constructor_preserves_legacy_output_stack_components_when_requirements_are_provided() {
        ItemStack output = new ItemStack(Holder.direct(Items.IRON_INGOT, DataComponentMap.EMPTY), 2);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Kept Output"));
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "component_output"),
                Identifier.fromNamespaceAndPath("mmcr", "component_machine"),
                40,
                List.of(),
                List.of(output),
                List.of(),
                0,
                1,
                false,
                List.of(new ItemRequirement(Ingredient.of(Items.IRON_INGOT), 2, null, IOType.OUTPUT))
        );

        assertThat(recipe.outputs()).containsExactly(output);
        assertThat(recipe.requirements()).containsExactly(new ItemRequirement(Ingredient.of(Items.IRON_INGOT), 2, null, IOType.OUTPUT));
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

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
