package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.recipe.ItemRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.publicapi.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.internal.api.PublicRecipeAdapter;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.CustomOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.CustomRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies public startup recipe builder values and conversion-ready immutability.
 *
 * @author howxu <dev@howxu.cn>
 */
class PublicRecipeBuilderTest {
    private static final Identifier TEST_LEVEL_TYPE = id("test_recipe_level");
    private static final Identifier TEST_LEVEL = id("test_recipe_level_normal");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void restoreDefaultMachineLevels() {
        cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent.resetCollector();
        var event = cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent.prepare(java.util.Set.of());
        event.registerLevelType(new LevelType(TEST_LEVEL_TYPE, Component.literal("Test Recipe Level")));
        event.registerLevel(new MachineLevel(TEST_LEVEL, TEST_LEVEL_TYPE, 0,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY,
                LevelModifier.IDENTITY));
        MachineLevelRegistry.installSnapshot(event.levelTypes().values(), event.levels().values());
    }

    @Test
    void builds_item_fluid_energy_recipe_with_scalar_options_and_immutable_values() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("recipe"), id("machine"))
                .duration(20).priority(3).maxThreads(4).cancelIfPerTickFails(true)
                .parallelized(true).allowPartialOutputs(true)
                .inputItem(Items.IRON_INGOT, 2)
                .inputFluid(net.minecraft.world.level.material.Fluids.WATER, 1000)
                .inputEnergy(40)
                .outputItem(new ItemStack(Items.GOLD_INGOT, 2))
                .outputFluid(net.minecraft.world.level.material.Fluids.WATER, 250)
                .outputEnergy(10)
                .build();

        assertThat(recipe.tickTime()).isEqualTo(20);
        assertThat(recipe.priority()).isEqualTo(3);
        assertThat(recipe.maxThreads()).isEqualTo(4);
        assertThat(recipe.requirements()).hasSize(6);
        assertThat(recipe.requirements()).allSatisfy(requirement -> assertThat(requirement).isNotNull());
        assertThat(recipe.requirements()).isUnmodifiable();
        assertThat(recipe.modifierIds()).isUnmodifiable();
    }

    @Test
    void preserves_item_tag_component_and_consume_chance_and_output_chance() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("predicates"), id("machine"))
                .inputItem(Ingredient.of(Items.IRON_INGOT), 2)
                .inputItemTag(net.minecraft.tags.ItemTags.create(Identifier.parse("c:ingots/iron")), 3)
                .inputItem(Ingredient.of(Items.GOLD_INGOT), 1, DataComponentPredicateSet.EMPTY, 0.25F)
                .outputChance(new ItemStack(Items.DIAMOND), 0.4F)
                .build();

        assertThat(recipe.requirements()).filteredOn(ItemRequirement.class::isInstance)
                .extracting(ItemRequirement.class::cast)
                .extracting(ItemRequirement::consumeChance).contains(0.25F);
        assertThat(recipe.requirements()).filteredOn(ItemRequirement.class::isInstance)
                .extracting(ItemRequirement.class::cast)
                .extracting(ItemRequirement::chance).contains(0.4F);
    }

    @Test
    void retains_explicit_requirements_smart_interface_level_host_and_modifier_without_deriving_duplicates() {
        var explicit = new cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement(RecipeIo.INPUT, 12);
        SmartInterfaceRequirement smart = SmartInterfaceRequirement.input("Mode", 1F);
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("explicit"), id("machine"))
                .inputItem(Items.IRON_INGOT, 1)
                .requirement(explicit).requirement(smart)
                .modifier(id("snapshot_modifier"))
                .levelRequirement(TEST_LEVEL_TYPE, TEST_LEVEL)
                .requiredHost(id("host"))
                .build();

        assertThat(recipe.requirements()).containsExactly(explicit, smart);
        assertThat(recipe.levelRequirements()).hasSize(1);
        assertThat(recipe.requiredHostIds()).containsExactly(id("host"));
        assertThat(recipe.modifierIds()).hasSize(1);
    }

    @Test
    void smart_interface_requirement_is_added_to_derived_io_requirements() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("smart_interface"), id("machine"))
                .inputItem(Items.IRON_INGOT, 1)
                .inputEnergy(20)
                .outputItem(Items.GOLD_NUGGET, 1)
                .smartInterface(SmartInterfaceRequirement.input("Mode", 1F))
                .build();

        assertThat(recipe.requirements()).hasSize(4);
        assertThat(recipe.requirements()).anyMatch(SmartInterfaceRequirement.class::isInstance);
        assertThat(recipe.requirements()).filteredOn(ItemRequirement.class::isInstance).hasSize(2);
    }

    @Test
    void adapts_public_recipe_values_to_internal_recipe_semantics() {
        ItemStack itemOutput = new ItemStack(Items.GOLD_INGOT, 2);
        FluidStack fluidOutput = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 250);
        var definition = MachineRecipeBuilder.recipe(id("adapter"), id("machine"))
                .inputItem(Ingredient.of(Items.IRON_INGOT), 2, components(), 0.25F)
                .inputFluid(net.minecraft.world.level.material.Fluids.WATER, 1000)
                .inputEnergy(40)
                .outputChance(itemOutput, 0.4F)
                .outputFluid(net.minecraft.world.level.material.Fluids.WATER, 250)
                .outputEnergy(10)
                .levelRequirement(TEST_LEVEL_TYPE, TEST_LEVEL)
                .requiredHost(id("host"))
                .modifier(id("snapshot_modifier"))
                .build();
        var recipe = PublicRecipeAdapter.toRecipe(definition, new MMCRMachineStructuresEvent.Snapshot(
                Map.of(),
                Map.of(),
                 Map.of(TEST_LEVEL, MachineLevelRegistry.getLevel(TEST_LEVEL)),
                Map.of(id("snapshot_modifier"), new ModifierDefinition(List.of(new cn.howxu.mmcr.api.recipe.modifier.RecipeModifier(
                        "item", cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT, 2F,
                        cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation.MULTIPLY, true))))));

        assertThat(recipe.requirements()).hasSize(6);
        assertThat(recipe.requirements()).anySatisfy(requirement -> {
            assertThat(requirement).isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.ItemRequirement.class);
            var item = (cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT);
            assertThat(item.count()).isEqualTo(2);
            assertThat(item.consumeChance()).isEqualTo(0.25F);
            assertThat(item.components().values()).containsKey(DataComponents.REPAIR_COST);
        });
        var smartRecipe = PublicRecipeAdapter.toRecipe(MachineRecipeBuilder.recipe(id("adapter_smart"), id("machine"))
                .requirement(SmartInterfaceRequirement.input("Mode", 1F, 2F)).build(),
                new MMCRMachineStructuresEvent.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
        assertThat(smartRecipe.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.class);
            var smart = (cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement) requirement;
            assertThat(smart.interfaceType()).isEqualTo("Mode");
            assertThat(smart.minValue()).isEqualTo(1F);
            assertThat(smart.maxValue()).isEqualTo(2F);
        });
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement.type())
                .isEqualTo(cn.howxu.mmcr.api.recipe.requirement.FluidRequirement.TYPE));
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement.type())
                .isEqualTo(cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement.TYPE));
        assertThat(recipe.modifiers()).singleElement().satisfies(modifier -> {
            assertThat(modifier.getTarget()).isEqualTo("item");
            assertThat(modifier.getIOTarget()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT);
            assertThat(modifier.getModifier()).isEqualTo(2F);
            assertThat(modifier.getOperation()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation.MULTIPLY);
            assertThat(modifier.affectsChance()).isTrue();
        });
        assertThat(recipe.levelRequirements()).singleElement().satisfies(level -> {
            assertThat(level.typeId()).isEqualTo(TEST_LEVEL_TYPE);
            assertThat(level.levelId()).isEqualTo(TEST_LEVEL);
        });
        assertThat(recipe.requiredHostIds()).containsExactly(id("host"));
    }

    @Test
    void preserves_output_component_predicates_during_internal_adaptation() {
        var definition = MachineRecipeBuilder.recipe(id("component_output"), id("machine"))
                .outputItem(new ItemStack(Items.IRON_SWORD), components())
                .build();

        var recipe = PublicRecipeAdapter.toRecipe(definition,
                new MMCRMachineStructuresEvent.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

        assertThat(recipe.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.ItemRequirement.class);
            var item = (cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT);
            assertThat(item.components().values()).containsKey(DataComponents.REPAIR_COST);
            assertThat(item.resolvedStack().get(DataComponents.REPAIR_COST)).isEqualTo(1);
        });
    }

    @Test
    void rejects_non_exact_output_component_predicates() {
        DataComponentPredicateSet nonExactComponents = new DataComponentPredicateSet(Map.of(
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(DataComponents.REPAIR_COST),
                new ComponentPredicate.Range(1, 2)));

        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("non_exact_output"), id("machine"))
                .outputItem(new ItemStack(Items.IRON_SWORD), nonExactComponents))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensively_copies_item_and_fluid_stacks_at_input_and_accessor_boundaries() {
        ItemStack item = new ItemStack(Items.IRON_INGOT, 2);
        FluidStack fluid = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("copies"), id("machine"))
                .outputItem(item).outputFluid(net.minecraft.world.level.material.Fluids.WATER, 1000).build();
        var fluidOutput = new cn.howxu.mmcr.api.publicapi.recipe.FluidOutput(fluid);
        item.setCount(1);
        fluid.setAmount(1);

        ItemStack itemAccessor = recipe.itemOutputs().getFirst().stack();
        FluidStack fluidAccessor = recipe.fluidOutputs().getFirst().stack();
        assertThat(itemAccessor.getCount()).isEqualTo(2);
        assertThat(fluidAccessor.getAmount()).isEqualTo(1000);
        itemAccessor.setCount(1);
        fluidAccessor.setAmount(1);
        assertThat(recipe.itemOutputs().getFirst().stack().getCount()).isEqualTo(2);
        assertThat(recipe.fluidOutputs().getFirst().stack().getAmount()).isEqualTo(1000);
        FluidStack explicitFluidAccessor = fluidOutput.stack();
        assertThat(explicitFluidAccessor.getAmount()).isEqualTo(1000);
        explicitFluidAccessor.setAmount(1);
        assertThat(fluidOutput.stack().getAmount()).isEqualTo(1000);
    }

    @Test
    void rejects_invalid_ranges() {
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad"), id("machine")).duration(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input(" ", 1F))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input("Mode", Float.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input("Mode", 2F, 1F))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad"), id("machine")).inputItem(Items.STICK, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad"), id("machine"))
                .outputChance(new ItemStack(Items.STICK), 2F))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void custom_recipe_io_decodes_registered_requirement_and_output_without_exposing_runtime_types() {
        var input = new cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement(
                cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT, 12);
        var output = new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F);
        var inputPayload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, input).getOrThrow();
        var outputPayload = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow();

        var definition = MachineRecipeBuilder.recipe(id("custom"), id("machine"))
                .custom(new CustomRecipeIo(input.type().id(), RecipeIo.INPUT, inputPayload))
                .custom(new CustomRecipeIo(output.outputType().id(), RecipeIo.OUTPUT, outputPayload))
                .build();
        var recipe = PublicRecipeAdapter.toRecipe(definition,
                new MMCRMachineStructuresEvent.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

        assertThat(recipe.requirements()).contains(input);
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement)
                .isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.ItemRequirement.class));
    }

    @Test
    void custom_recipe_io_copies_payload_and_rejects_invalid_declarations() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "energy");
        payload.addProperty("io", "input");
        payload.addProperty("fe_per_tick", 12);
        var custom = new CustomRecipeIo(cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement.TYPE.id(),
                RecipeIo.INPUT, payload);
        payload.addProperty("fe_per_tick", 1);
        custom.payload().getAsJsonObject().addProperty("fe_per_tick", 2);

        assertThat(custom.payload().getAsJsonObject().get("fe_per_tick").getAsInt()).isEqualTo(12);
        assertThatThrownBy(() -> new CustomRecipeIo(id("energy"), RecipeIo.INPUT,
                JsonOps.INSTANCE.createInt(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("unknown_custom"), id("machine"))
                .custom(new CustomRecipeIo(id("unknown"), RecipeIo.INPUT, custom.payload())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void custom_recipe_io_preserves_registered_output_without_requirement_factory() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(TestRequirement.TYPE);
            OutputRegistry.register(TestOutput.TYPE);
            var requirement = new TestRequirement(3);
            var output = new TestOutput(7, 1F);
            var requirementPayload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow();
            var outputPayload = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow();
            var customOutput = new CustomRecipeIo(TestOutput.TYPE.id(), RecipeIo.OUTPUT, outputPayload);
            customOutput.payload().getAsJsonObject().addProperty("value", 1);

            var recipe = PublicRecipeAdapter.toRecipe(MachineRecipeBuilder.recipe(id("custom_extension"), id("machine"))
                    .custom(new CustomRecipeIo(TestRequirement.TYPE.id(), RecipeIo.INPUT, requirementPayload))
                    .custom(customOutput).build(),
                    new MMCRMachineStructuresEvent.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

            assertThat(recipe.requirements()).containsExactly(requirement);
            assertThat(recipe.machineOutputs()).containsExactly(output);
            assertThat(customOutput.payload().getAsJsonObject().get("value").getAsInt()).isEqualTo(7);
        }
    }

    private record TestRequirement(int value) implements CustomRequirement {
        private static final RequirementType<TestRequirement> TYPE = new RequirementType.Definition<>(
                id("public_test_requirement"), RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> "mmcr:public_test_requirement"),
                        Codec.INT.fieldOf("value").forGetter(TestRequirement::value)
                ).apply(instance, (ignored, value) -> new TestRequirement(value))),
                (requirement, capabilities, context) -> null);

        @Override
        public RecipeModifier.IOType io() {
            return RecipeModifier.IOType.INPUT;
        }

        @Override
        public RequirementType<TestRequirement> type() {
            return TYPE;
        }
    }

    private record TestOutput(int value, float chance) implements CustomOutput {
        private static final OutputType<TestOutput> TYPE = new OutputType.Definition<>(
                id("public_test_output"), RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> "mmcr:public_test_output"),
                        Codec.INT.fieldOf("value").forGetter(TestOutput::value),
                        Codec.FLOAT.fieldOf("chance").forGetter(TestOutput::chance)
                ).apply(instance, (ignored, value, chance) -> new TestOutput(value, chance))),
                (output, chance) -> new TestOutput(output.value(), chance), (output, modifiers) -> output, output -> output);

        @Override
        public OutputType<TestOutput> outputType() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return MMCR.id(path);
    }

    private static DataComponentPredicateSet components() {
        return new DataComponentPredicateSet(Map.of(
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(DataComponents.REPAIR_COST),
                new ComponentPredicate.Exact(JsonOps.INSTANCE.createInt(1))));
    }
}
