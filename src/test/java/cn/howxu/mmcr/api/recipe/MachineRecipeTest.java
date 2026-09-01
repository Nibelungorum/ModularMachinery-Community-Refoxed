package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.Map;

import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
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
        var recipe = RecipeTestSupport.create(
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
    void codec_decodes_and_roundtrips_partial_output_flag() {
        JsonObject enabled = baseRecipeJson();
        enabled.addProperty("allow_partial_outputs", true);
        assertThat(MachineRecipe.CODEC.codec().parse(jsonOps(), enabled).getOrThrow().allowPartialOutputs()).isTrue();

        JsonObject legacy = baseRecipeJson();
        assertThat(MachineRecipe.CODEC.codec().parse(jsonOps(), legacy).getOrThrow().allowPartialOutputs()).isFalse();

        MachineRecipe roundTrip = MachineRecipe.CODEC.codec().parse(jsonOps(),
                MachineRecipe.CODEC.codec().encodeStart(jsonOps(), partialOutputRecipe()).getOrThrow()).getOrThrow();
        assertThat(roundTrip.allowPartialOutputs()).isTrue();
    }

    @Test
    void prepared_recipe_preserves_partial_output_flag() {
        var prepared = new PreparedRecipe(
                "mmcr:partial_outputs",
                "mmcr:machine",
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                false,
                true);

        assertThat(prepared.allowPartialOutputs()).isTrue();
        assertThat(prepared.toMachineRecipe().allowPartialOutputs()).isTrue();
    }

    @Test
    void outputs_roundtrip_native_component_stack() {
        bindItemComponents(Items.DIAMOND_SWORD);
        var root = new JsonObject();
        root.addProperty("id", "mmcr:better_diamond_sword");
        root.addProperty("machine", "mmcr:test_machine_name");
        root.addProperty("tick_time", 40);
        var output = new JsonObject();
        output.addProperty("id", "minecraft:diamond_sword");
        output.addProperty("count", 1);
        var components = new JsonObject();
        var customName = new JsonObject();
        customName.addProperty("text", "Better钻石剑");
        components.add("minecraft:custom_name", customName);
        var enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 4);
        components.add("minecraft:enchantments", enchantments);
        output.add("components", components);
        root.add("outputs", itemOutputs(output));

        MachineRecipe recipe = MachineRecipe.CODEC.codec().parse(componentJsonOps(), root).getOrThrow();

        ItemStack outputStack = ((MachineOutput.ItemOutput) recipe.machineOutputs().getFirst()).stack();
        assertThat(outputStack.get(DataComponents.ENCHANTMENTS).getLevel(enchantment("minecraft:sharpness"))).isEqualTo(4);
    }

    @Test
    void enchanted_output_components_use_bound_enchantment_holders_for_tooltips() {
        bindItemComponents(Items.DIAMOND_SWORD);
        var root = new JsonObject();
        root.addProperty("id", "mmcr:tooltip_safe_enchanted_output");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        var output = new JsonObject();
        output.addProperty("id", "minecraft:diamond_sword");
        var components = new JsonObject();
        var enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 2);
        components.add("minecraft:enchantments", enchantments);
        output.add("components", components);
        root.add("outputs", itemOutputs(output));

        MachineRecipe recipe = MachineRecipe.CODEC.codec().parse(componentJsonOps(), root).getOrThrow();

        ItemRequirement outputRequirement = (ItemRequirement) recipe.requirements().stream()
                .filter(ItemRequirement.class::isInstance)
                .map(ItemRequirement.class::cast)
                .filter(requirement -> requirement.io() == RecipeModifier.IOType.OUTPUT)
                .findFirst().orElseThrow();
        ItemStack stack = outputRequirement.stack(componentJsonOps());
        var enchantment = stack.get(DataComponents.ENCHANTMENTS).keySet().iterator().next();
        assertThat(enchantment.unwrapKey().orElseThrow().identifier()).isEqualTo(Identifier.parse("minecraft:sharpness"));
    }

    @Test
    void input_requirement_components_match_runtime_stack_from_standard_json() {
        bindItemComponents(Items.DIAMOND_SWORD);
        var root = new JsonObject();
        root.addProperty("id", "mmcr:component_input");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        var input = itemRequirement("input", itemId(Items.DIAMOND_SWORD), 1);
        var components = new JsonObject();
        var enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 2);
        components.add("minecraft:enchantments", enchantments);
        components.addProperty("minecraft:repair_cost", 1);
        input.add("components", components);
        root.add("requirements", requirements(input));

        MachineRecipe recipe = MachineRecipe.CODEC.codec().parse(componentJsonOps(), root).getOrThrow();
        ItemRequirement requirement = (ItemRequirement) recipe.requirements().getFirst();
        ItemStack actual = new ItemStack(Items.DIAMOND_SWORD);
        requirement.components().applyTo(actual, componentJsonOps());

        assertThat(requirement.components().matches(actual)).isTrue();
        assertThat(actual.get(DataComponents.ENCHANTMENTS).getLevel(enchantment("minecraft:sharpness"))).isEqualTo(2);
        assertThat(actual.get(DataComponents.ENCHANTMENTS).keySet().iterator().next().unwrapKey().orElseThrow().identifier())
                .isEqualTo(Identifier.parse("minecraft:sharpness"));
        assertThat(actual.get(DataComponents.REPAIR_COST)).isEqualTo(1);
    }

    @Test
    void output_requirement_components_roundtrip_from_standard_json() {
        bindItemComponents(Items.DIAMOND_SWORD);
        var root = new JsonObject();
        root.addProperty("id", "mmcr:component_output");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        var output = itemOutputRequirement(itemId(Items.DIAMOND_SWORD), 1);
        var stack = output.getAsJsonObject("stack");
        var components = new JsonObject();
        var enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 2);
        components.add("minecraft:enchantments", enchantments);
        components.addProperty("minecraft:repair_cost", 1);
        stack.add("components", components);
        root.add("requirements", requirements(output));

        MachineRecipe recipe = MachineRecipe.CODEC.codec().parse(componentJsonOps(), root).getOrThrow();
        ItemStack actual = ((MachineOutput.ItemOutput) recipe.runtimeMachineOutputs().getFirst()).stack();

        assertThat(actual.get(DataComponents.ENCHANTMENTS).getLevel(enchantment("minecraft:sharpness"))).isEqualTo(2);
        assertThat(actual.get(DataComponents.REPAIR_COST)).isEqualTo(1);
    }

    @Test
    void codec_roundtrips_level_requirements() {
        var coilType = Identifier.parse("test:coil");
        var kanthal = Identifier.parse("test:kanthal");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        TestBootstrap.registerLevel(new MachineLevel(kanthal, coilType, 1,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));
        var recipe = RecipeTestSupport.create(
                Identifier.parse("test:levelled"),
                Identifier.parse("test:machine"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(),
                false,
                List.of(new LevelRequirement(coilType, kanthal)));

        var decoded = MachineRecipe.CODEC.codec().parse(jsonOps(),
                MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow()).getOrThrow();

        assertThat(decoded.levelRequirements()).containsExactlyElementsOf(recipe.levelRequirements());
    }

    @Test
    void rejects_duplicate_level_requirement_types() {
        var coilType = Identifier.parse("test:coil");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        TestBootstrap.registerLevel(new MachineLevel(Identifier.parse("test:kanthal"), coilType, 1,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));
        TestBootstrap.registerLevel(new MachineLevel(Identifier.parse("test:nichrome"), coilType, 2,
                new BlockPredicate.OfBlockState(Blocks.GOLD_BLOCK.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));

        assertThatThrownBy(() -> RecipeTestSupport.create(
                Identifier.parse("test:duplicate_levels"), Identifier.parse("test:machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(), false,
                List.of(new LevelRequirement(coilType, Identifier.parse("test:kanthal")),
                        new LevelRequirement(coilType, Identifier.parse("test:nichrome")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recipe_preserves_canonical_requirements_and_outputs() {
        var nugget = bindItemComponents(Items.IRON_NUGGET);
        var recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "legacy"),
                Identifier.fromNamespaceAndPath("mmcr", "machine"),
                20,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.EnergyIngredient(40)
                ),
                List.of(new ItemStack(nugget, 1))
        );

        assertThat(recipe.requirements()).hasSize(3);
        assertThat(recipe.requirements()).filteredOn(requirement -> requirement.io() == RecipeModifier.IOType.INPUT)
                .hasSize(2);
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement)
                .isInstanceOfSatisfying(ItemRequirement.class, input -> {
                    assertThat(input.count()).isEqualTo(2);
                    assertThat(input.io()).isEqualTo(RecipeModifier.IOType.INPUT);
                }));
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement)
                .isInstanceOfSatisfying(EnergyRequirement.class, energy -> {
                    assertThat(energy.fePerTick()).isEqualTo(40);
                    assertThat(energy.io()).isEqualTo(RecipeModifier.IOType.INPUT);
                }));
        assertThat(recipe.machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack().getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(output.stack().getCount()).isEqualTo(1);
        });
    }

    @Test
    void recipe_codec_prefers_requirements_and_encodes_stable_shape() {
        var root = new JsonObject();
        root.addProperty("id", "mmcr:mixed");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        root.add("outputs", itemOutputs(itemId(Items.IRON_NUGGET), 3));
        root.add("requirements", requirements(
                itemRequirement("input", itemId(Items.IRON_INGOT), 2),
                itemOutputRequirement(itemId(Items.IRON_NUGGET), 3),
                energyRequirement(60)
        ));

        bindItemComponents(Items.IRON_NUGGET);
        bindItemComponents(Items.GOLD_NUGGET);

        var recipe = MachineRecipe.CODEC.codec().parse(jsonOps(), root).getOrThrow();
        assertThat(recipe.requirements()).hasSize(3);
        assertThat(recipe.requirements()).filteredOn(requirement -> requirement.io() == RecipeModifier.IOType.INPUT)
                .hasSize(2);
        assertThat(recipe.machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack().getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(output.stack().getCount()).isEqualTo(3);
        });

        var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow().getAsJsonObject();
        assertThat(encoded.has("requirements")).isTrue();
        assertThat(encoded.has("inputs")).isFalse();
        assertThat(encoded.has("outputs")).isFalse();
        assertThat(encoded.has("fluid_outputs")).isFalse();
    }

    @Test
    void registry_filters_recipes_by_machine_and_rejects_null_id() {
        var machineId = Identifier.fromNamespaceAndPath("mmcr", "compressor");
        var recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "iron"), machineId, 20, List.of(), List.of());
        var other = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "gold"),
                Identifier.fromNamespaceAndPath("mmcr", "other"), 20, List.of(), List.of());

        var machine = new DynamicMachine(machineId, "Compressor", new BlockArray(Map.of()));
        RecipeRegistry.register(recipe);
        RecipeRegistry.register(other);

        assertThat(RecipeRegistry.byMachine(machine)).containsExactly(recipe);
        assertThatThrownBy(() -> RecipeRegistry.register(
                RecipeTestSupport.create(null, machineId, 1, List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registryReloadVersionOnlyChangesWhenRegistryClears() {
        long before = RecipeRegistry.reloadVersion();
        var machineId = Identifier.fromNamespaceAndPath("mmcr", "versioned_machine");

        RecipeRegistry.register(RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "versioned_recipe"),
                machineId,
                20,
                List.of(),
                List.of()));
        assertThat(RecipeRegistry.reloadVersion()).isEqualTo(before);

        RecipeRegistry.clearAll();

        assertThat(RecipeRegistry.reloadVersion()).isEqualTo(before + 1);
    }

    @Test
    void fluidOnlyRequirementRecipeAssemblesEmptyItemStack() {
        bindFluidComponents(Fluids.WATER);
        var recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "fluid_only"),
                Identifier.fromNamespaceAndPath("mmcr", "machine"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000)))
        );

        assertThat(recipe.assemble(null)).isEqualTo(ItemStack.EMPTY);
    }

    @Test
    void recipeRequirementTagsRoundTripAndDefaultEmpty() {
        var root = new JsonObject();
        root.addProperty("id", "mmcr:tagged");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        var input = itemRequirement("input", itemId(Items.IRON_INGOT), 1);
        var tags = new JsonArray();
        tags.add("input_a");
        input.add("tags", tags);
        var requirements = new JsonArray();
        requirements.add(input);
        root.add("requirements", requirements);

        var recipe = MachineRecipe.CODEC.codec().parse(jsonOps(), root).getOrThrow();

        assertThat(recipe.requirements().getFirst().tags()).containsExactly("input_a");
        var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow().getAsJsonObject();
        assertThat(encoded.getAsJsonArray("requirements").get(0).getAsJsonObject().getAsJsonArray("tags"))
                .extracting(JsonElement::getAsString)
                .containsExactly("input_a");
    }

    @Test
    void requirementsWithoutTagsDecodeToEmptyList() {
        var root = new JsonObject();
        root.addProperty("id", "mmcr:untagged");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        var input = itemRequirement("input", itemId(Items.IRON_INGOT), 1);
        var requirements = new JsonArray();
        requirements.add(input);
        root.add("requirements", requirements);

        var recipe = MachineRecipe.CODEC.codec().parse(jsonOps(), root).getOrThrow();

        assertThat(recipe.requirements().getFirst().tags()).isEmpty();
    }

    @Test
    void output_requirement_chance_roundtrips() {
        bindItemComponents(Items.IRON_NUGGET);
        bindFluidComponents(Fluids.WATER);
        var root = new JsonObject();
        root.addProperty("id", "mmcr:chance_outputs");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        root.add("requirements", requirements(
                itemOutputRequirement(itemId(Items.IRON_NUGGET), 3, 0.25F),
                fluidOutputRequirement("minecraft:water", 500, 0.75F)
        ));

        var recipe = MachineRecipe.CODEC.codec().parse(jsonOps(), root).getOrThrow();

        assertThat(recipe.machineOutputs()).hasSize(2);
        assertThat(recipe.machineOutputs().get(0)).isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack().getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(output.stack().getCount()).isEqualTo(3);
            assertThat(output.chance()).isEqualTo(0.25F);
        });
        assertThat(recipe.machineOutputs().get(1)).isInstanceOfSatisfying(MachineOutput.FluidOutput.class, output -> {
            assertThat(output.stack().getFluid()).isEqualTo(Fluids.WATER);
            assertThat(output.stack().getAmount()).isEqualTo(500);
            assertThat(output.chance()).isEqualTo(0.75F);
        });

        var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow().getAsJsonObject();
        assertThat(encoded.getAsJsonArray("requirements").get(0).getAsJsonObject().get("chance").getAsFloat()).isEqualTo(0.25F);
        assertThat(encoded.getAsJsonArray("requirements").get(1).getAsJsonObject().get("chance").getAsFloat()).isEqualTo(0.75F);
    }

    @Test
    void machine_output_codec_roundtrips_item_output_and_defaults_missing_chance() {
        bindItemComponents(Items.IRON_NUGGET);
        var output = new MachineOutput.ItemOutput(Items.IRON_NUGGET.getDefaultInstance().copyWithCount(4), 0.25F);

        var encoded = MachineOutput.CODEC.encodeStart(jsonOps(), output).getOrThrow().getAsJsonObject();
        var back = MachineOutput.CODEC.parse(jsonOps(), encoded).getOrThrow();

        assertThat(back).isInstanceOfSatisfying(MachineOutput.ItemOutput.class, decoded -> {
            assertThat(decoded.stack().getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(decoded.stack().getCount()).isEqualTo(4);
            assertThat(decoded.chance()).isEqualTo(0.25F);
        });
        encoded.remove("chance");
        assertThat(MachineOutput.CODEC.parse(jsonOps(), encoded).getOrThrow().chance()).isEqualTo(1F);
    }

    @Test
    void machine_output_codec_roundtrips_fluid_output_and_clamps_chance() {
        bindFluidComponents(Fluids.WATER);
        var overChance = new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 500), 2F);

        var encoded = MachineOutput.CODEC.encodeStart(jsonOps(), overChance).getOrThrow().getAsJsonObject();
        var back = MachineOutput.CODEC.parse(jsonOps(), encoded).getOrThrow();

        assertThat(back).isInstanceOfSatisfying(MachineOutput.FluidOutput.class, decoded -> {
            assertThat(decoded.stack().getFluid()).isEqualTo(Fluids.WATER);
            assertThat(decoded.stack().getAmount()).isEqualTo(500);
            assertThat(decoded.chance()).isEqualTo(1F);
        });

        encoded.addProperty("chance", -1F);
        assertThat(MachineOutput.CODEC.parse(jsonOps(), encoded).getOrThrow().chance()).isEqualTo(0F);
    }

    @Test
    void codec_preserves_raw_values_when_runtime_modifiers_change_derived_values() {
        bindItemComponents(Items.IRON_NUGGET);
        var recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "raw_preserved"),
                Identifier.fromNamespaceAndPath("mmcr", "machine"),
                100,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(Items.IRON_NUGGET.getDefaultInstance().copyWithCount(1)),
                List.of(
                        new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT, 3F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.OUTPUT, 4F, RecipeModifier.Operation.MULTIPLY, false)
                ),
                0,
                1
        );

        var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow().getAsJsonObject();
        var back = MachineRecipe.CODEC.codec().parse(jsonOps(), encoded).getOrThrow();

        assertThat(encoded.get("tick_time").getAsInt()).isEqualTo(100);
        assertThat(back.requirements()).containsExactlyElementsOf(recipe.requirements());
        assertThat(back.runtimeMachineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class,
                output -> assertThat(output.stack().getCount()).isEqualTo(1));
        assertThat(((ItemRequirement) back.runtimeRequirements().get(0)).count()).isEqualTo(6);
        assertThat(((ItemRequirement) back.runtimeRequirements().get(1)).stack().getCount()).isEqualTo(4);
    }

    @Test
    void runtime_requirements_accept_structure_modifiers_without_mutating_raw_recipe() {
        MachineRecipe recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "effective_modifiers"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(),
                List.of(new RecipeModifier("item", RecipeModifier.IOType.INPUT, 1F,
                        RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1);
        List<RecipeModifier> effective = List.of(
                new RecipeModifier("item", RecipeModifier.IOType.INPUT, 2F,
                        RecipeModifier.Operation.ADD, false));

        assertThat(recipe.runtimeRequirements(effective).getFirst()).isInstanceOf(ItemRequirement.class);
        assertThat(((ItemRequirement) recipe.runtimeRequirements(effective).getFirst()).count()).isEqualTo(4);
        assertThat(((ItemRequirement) recipe.requirements().getFirst()).count()).isEqualTo(2);
    }

    @Test
    void runtime_requirements_apply_item_input_chance_modifiers() {
        MachineRecipe recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "input_chance_modifier"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                        ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0.5F)),
                false,
                List.of());

        List<RecipeModifier> effective = List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM,
                RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, true));

        assertThat(recipe.runtimeRequirements(effective).getFirst()).isInstanceOf(ItemRequirement.class);
        assertThat(((ItemRequirement) recipe.runtimeRequirements(effective).getFirst()).consumeChance()).isEqualTo(0.25F);
        assertThat(((ItemRequirement) recipe.requirements().getFirst()).consumeChance()).isEqualTo(0.5F);
    }

    @Test
    void runtime_requirements_preserve_energy_output_direction() {
        MachineRecipe recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr", "energy_output_direction"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 200)),
                false,
                List.of());

        assertThat(recipe.runtimeRequirements()).singleElement()
                .isInstanceOfSatisfying(EnergyRequirement.class,
                        energy -> assertThat(energy.io()).isEqualTo(RecipeModifier.IOType.OUTPUT));
    }

    private static Holder<Fluid> bindFluidComponents(Fluid fluid) {
        var holder = fluid.builtInRegistryHolder();
        holder.bindComponents(DataComponentMap.EMPTY);
        return holder;
    }

    private static JsonObject baseRecipeJson() {
        var root = new JsonObject();
        root.addProperty("id", "mmcr:partial_outputs");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        root.add("requirements", new JsonArray());
        return root;
    }

    private static MachineRecipe partialOutputRecipe() {
        return RecipeTestSupport.create(
                Identifier.parse("mmcr:partial_outputs"),
                Identifier.parse("mmcr:machine"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(),
                false,
                List.of(),
                true);
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
        return itemOutputs(output);
    }

    private static JsonArray itemOutputs(JsonObject output) {
        var canonical = new JsonObject();
        canonical.addProperty("type", "mmcr:item");
        canonical.add("stack", output);
        canonical.addProperty("chance", 1F);
        var outputs = new JsonArray();
        outputs.add(canonical);
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

    private static JsonObject itemOutputRequirement(String itemId, int count, float chance) {
        var output = itemOutputRequirement(itemId, count);
        output.addProperty("chance", chance);
        return output;
    }

    private static JsonObject fluidOutputRequirement(String fluidId, int amount, float chance) {
        var output = new JsonObject();
        output.addProperty("type", "fluid");
        output.addProperty("io", "output");
        var stack = new JsonObject();
        stack.addProperty("id", fluidId);
        stack.addProperty("amount", amount);
        output.add("stack", stack);
        output.addProperty("chance", chance);
        return output;
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

    private static Holder<Enchantment> enchantment(String id) {
        return VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(
                        Registries.ENCHANTMENT, Identifier.parse(id)));
    }

    private static DynamicOps<JsonElement> componentJsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
    }
}
