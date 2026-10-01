package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.recipe.FluidOutput;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.google.gson.JsonObject;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.CustomOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.sync.MachineRecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.CustomRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import io.netty.buffer.Unpooled;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies public startup recipe builder values and conversion-ready immutability.
 *
 * @author howxu <dev@howxu.cn>
 */
class PublicRecipeBuilderTest {
    private static final ResourceLocation TEST_LEVEL_TYPE = id("test_recipe_level");
    private static final ResourceLocation TEST_LEVEL = id("test_recipe_level_normal");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
        MekanismRecipeTypes.register();
    }

    @BeforeEach
    void restoreDefaultMachineLevels() {
        StructureRegistration.resetCollector();
        var event = StructureRegistration.prepare(Set.of());
        event.registerLevelType(new LevelType(TEST_LEVEL_TYPE, Component.literal("Test Recipe Level")));
        event.registerLevel(new MachineLevel(TEST_LEVEL, TEST_LEVEL_TYPE, 0,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
        MachineLevelRegistry.installSnapshot(event.levelTypes().values(), event.levels().values());
    }

    @Test
    void builds_item_fluid_energy_recipe_with_scalar_options_and_immutable_values() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("recipe")).recipePool(id("machine"))
                .duration(20).priority(3).maxThreads(4).cancelIfPerTickFails(true)
                .parallelized(true).allowPartialOutputs(true)
                .inputItem(Items.IRON_INGOT, 2)
                .inputFluid(Fluids.WATER, 1000)
                .inputEnergy(40)
                .outputItem(new ItemStack(Items.GOLD_INGOT, 2))
                .outputFluid(Fluids.WATER, 250)
                .outputEnergy(10)
                .build();

        assertThat(recipe.tickTime()).isEqualTo(20);
        assertThat(recipe.priority()).isEqualTo(3);
        assertThat(recipe.maxThreads()).isEqualTo(4);
        assertThat(recipe.requirements()).hasSize(6);
        assertThat(recipe.requirements()).allSatisfy(requirement -> assertThat(requirement).isNotNull());
        assertThat(recipe.requirements()).isUnmodifiable();
        assertThat(recipe.modifierIds()).isUnmodifiable();
        assertThat(recipe.recipePoolId()).isEqualTo(id("machine"));
    }

    @Test
    void recipe_builder_requires_a_pool_before_building() {
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("missing_pool")).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing_pool")
                .hasMessageContaining("recipe pool");
    }

    @Test
    void preservesMaximumLongEnergyRatesDuringInternalConversion() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("maximum_energy")).recipePool(id("machine"))
                .inputEnergy(Long.MAX_VALUE)
                .outputEnergy(Long.MAX_VALUE)
                .build();

        assertThat(recipe.energyInputs()).extracting(EnergyRequirement::fePerTick)
                .containsExactly(Long.MAX_VALUE);
        assertThat(recipe.energyOutputs()).extracting(EnergyRequirement::fePerTick)
                .containsExactly(Long.MAX_VALUE);
        assertThat(MachineRecipeConverter.toRecipe(recipe,
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of())).requirements())
                .filteredOn(cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement.class::isInstance)
                .extracting(cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement.class::cast)
                .extracting(cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement::fePerTick)
                .containsOnly(Long.MAX_VALUE);
    }

    @Test
    void preserves_item_tag_component_and_consume_chance_and_output_chance() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("predicates")).recipePool(id("machine"))
                .inputItem(Ingredient.of(Items.IRON_INGOT), 2)
                .inputItemTag(ItemTags.create(ResourceLocation.parse("c:ingots/iron")), 3)
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
        var explicit = new cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement(IOType.INPUT, 12);
        SmartInterfaceRequirement smart = SmartInterfaceRequirement.input("Mode", 1F);
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("explicit")).recipePool(id("machine"))
                .inputItem(Items.IRON_INGOT, 1)
                .requirement(explicit).requirement(smart)
                .modifier(id("snapshot_modifier"))
                .levelRequirement(TEST_LEVEL_TYPE, TEST_LEVEL)
                .requiredHost(id("host"))
                .build();

        assertThat(recipe.requirements()).contains(explicit, smart);
        assertThat(recipe.requirements()).anySatisfy(requirement -> {
            assertThat(requirement).isInstanceOf(LevelRequirement.class);
            LevelRequirement level = (LevelRequirement) requirement;
            assertThat(level.io()).isEqualTo(IOType.INPUT);
            assertThat(level.typeId()).isEqualTo(TEST_LEVEL_TYPE);
            assertThat(level.levelId()).isEqualTo(TEST_LEVEL);
        });
        assertThat(recipe.requiredHostIds()).containsExactly(id("host"));
        assertThat(recipe.modifierIds()).hasSize(1);
    }

    @Test
    void smart_interface_requirement_is_added_to_derived_io_requirements() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("smart_interface")).recipePool(id("machine"))
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
    void converts_internal_level_requirement_to_public_requirement() {
        var requirement = MachineRequirement.copyOf(
                cn.howxu.mmcr.api.recipe.requirement.LevelRequirement.input(TEST_LEVEL_TYPE, TEST_LEVEL));

        assertThat(requirement).isInstanceOfSatisfying(LevelRequirement.class, level -> {
            assertThat(level.typeId()).isEqualTo(TEST_LEVEL_TYPE);
            assertThat(level.levelId()).isEqualTo(TEST_LEVEL);
        });
    }

    @Test
    void stage_requirement_is_preserved_by_the_public_builder_and_converters() {
        MachineRecipeDefinition definition = MachineRecipeBuilder.recipe(id("stage_requirement"))
                .recipePool(id("machine"))
                .stageRequirement(2)
                .build();
        StageRequirement publicRequirement = new StageRequirement(2);
        var internalRequirement = cn.howxu.mmcr.api.recipe.requirement.StageRequirement.input(2);

        assertThat(definition.requirements()).containsExactly(publicRequirement);
        assertThat(MachineRequirement.copyOf(publicRequirement)).isEqualTo(internalRequirement);
        assertThat(MachineRequirement.copyOf(internalRequirement)).isEqualTo(publicRequirement);
    }

    @Test
    void stage_requirement_rejects_values_above_the_supported_stage_limit() {
        assertThatThrownBy(() -> new StageRequirement(65))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stage minimum must be in [1, 64]");
    }

    @Test
    void adapts_public_recipe_values_to_internal_recipe_semantics() {
        ItemStack itemOutput = new ItemStack(Items.GOLD_INGOT, 2);
        FluidStack fluidOutput = new FluidStack(Fluids.WATER, 250);
        var definition = MachineRecipeBuilder.recipe(id("adapter")).recipePool(id("machine"))
                .inputItem(Ingredient.of(Items.IRON_INGOT), 2, components(), 0.25F)
                .inputFluid(Fluids.WATER, 1000)
                .inputEnergy(40)
                .outputChance(itemOutput, 0.4F)
                .outputFluid(Fluids.WATER, 250)
                .outputEnergy(10)
                .levelRequirement(TEST_LEVEL_TYPE, TEST_LEVEL)
                .requiredHost(id("host"))
                .modifier(id("snapshot_modifier"))
                .build();
        var recipe = MachineRecipeConverter.toRecipe(definition, new StructureRegistration.Snapshot(
                Map.of(),
                Map.of(),
                 Map.of(TEST_LEVEL, MachineLevelRegistry.getLevel(TEST_LEVEL)),
                Map.of(id("snapshot_modifier"), new ModifierDefinition(List.of(
                        MachineModifier.numeric("output", "output", 2D, "multiply", true))))));

        assertThat(recipe.requirements()).hasSize(7);
        assertThat(recipe.requirements()).anySatisfy(requirement -> {
            assertThat(requirement).isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.ItemRequirement.class);
            var item = (cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(RecipeModifier.IOType.INPUT);
            assertThat(item.count()).isEqualTo(2);
            assertThat(item.consumeChance()).isEqualTo(0.25F);
            assertThat(item.components().values()).containsKey(DataComponents.REPAIR_COST);
        });
        var smartRecipe = MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(id("adapter_smart")).recipePool(id("machine"))
                .requirement(SmartInterfaceRequirement.input("Mode", 1F, 2F)).build(),
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
        assertThat(smartRecipe.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.class);
            var smart = (cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement) requirement;
            assertThat(smart.interfaceType()).isEqualTo("Mode");
            assertThat(smart.minValue()).isEqualTo(1F);
            assertThat(smart.maxValue()).isEqualTo(2F);
        });
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement.type())
                .isEqualTo(FluidRequirement.TYPE));
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement.type())
                .isEqualTo(cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement.TYPE));
        assertThat(recipe.modifiers()).singleElement().satisfies(modifier -> {
            assertThat(modifier.getTarget()).isEmpty();
            assertThat(modifier.getIOTarget()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(modifier.getModifier()).isEqualTo(2F);
            assertThat(modifier.getOperation()).isEqualTo(RecipeModifier.Operation.MULTIPLY);
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
        var definition = MachineRecipeBuilder.recipe(id("component_output")).recipePool(id("machine"))
                .outputItem(new ItemStack(Items.IRON_SWORD), components())
                .build();

        var recipe = MachineRecipeConverter.toRecipe(definition,
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

        assertThat(recipe.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.ItemRequirement.class);
            var item = (cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(item.components().values()).containsKey(DataComponents.REPAIR_COST);
            assertThat(item.resolvedStack().get(DataComponents.REPAIR_COST)).isEqualTo(1);
        });
    }

    @Test
    void converted_output_with_enchantment_components_passes_canonical_validation() {
        ItemStack output = new ItemStack(Items.DIAMOND, 9);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("What a magic recipe"));
        JsonObject enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 4);
        DataComponentPredicateSet components = DataComponentPredicateSet.ofIds(Map.of(
                ResourceLocation.parse("minecraft:enchantments"), ComponentPredicate.exact(enchantments)));

        var recipe = MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(id("enchantment_output")).recipePool(id("machine"))
                        .outputItem(output, components).build(),
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

        assertThatCode(() -> RecipeRegistry.validateClientSnapshot(Map.of(recipe.id(), recipe))).doesNotThrowAnyException();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        MachineRecipeSyncCodec.encode(buffer, recipe);
        assertThatCode(() -> MachineRecipeSyncCodec.decode(buffer)).doesNotThrowAnyException();
    }

    @Test
    void converted_output_with_enchantment_components_round_trips_with_registry_access() {
        ItemStack output = new ItemStack(Items.DIAMOND, 9);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("What a magic recipe"));
        JsonObject enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 4);
        DataComponentPredicateSet components = DataComponentPredicateSet.ofIds(Map.of(
                ResourceLocation.parse("minecraft:enchantments"), ComponentPredicate.exact(enchantments)));

        var recipe = MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(id("enchantment_network_output")).recipePool(id("machine"))
                        .outputItem(output, components).build(),
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                networkRegistryAccess());

        MachineRecipeSyncCodec.encode(buffer, recipe);

        MachineRecipe decoded = MachineRecipeSyncCodec.decode(buffer);
        assertThat(decoded.machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class,
                decodedOutput -> assertThat(decodedOutput.stack().get(DataComponents.ENCHANTMENTS)).isNotNull()
                        .isNotEqualTo(ItemEnchantments.EMPTY));
    }

    private static RegistryAccess networkRegistryAccess() {
        MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(holder -> Registry.register(enchantments, holder.key().location(), holder.value()));
        enchantments.freeze();
        List<Registry<?>> registries = new ArrayList<>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries()
                .forEach(entry -> registries.add(entry.value()));
        registries.add(enchantments);
        return new RegistryAccess.ImmutableRegistryAccess(registries);
    }

    @Test
    void rejects_non_exact_output_component_predicates() {
        DataComponentPredicateSet nonExactComponents = DataComponentPredicateSet.ofIds(Map.of(
                BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(DataComponents.REPAIR_COST),
                new ComponentPredicate.Range(1, 2)));

        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("non_exact_output")).recipePool(id("machine"))
                .outputItem(new ItemStack(Items.IRON_SWORD), nonExactComponents))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensively_copies_item_and_fluid_stacks_at_input_and_accessor_boundaries() {
        ItemStack item = new ItemStack(Items.IRON_INGOT, 2);
        FluidStack fluid = new FluidStack(Fluids.WATER, 1000);
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("copies")).recipePool(id("machine"))
                .outputItem(item).outputFluid(Fluids.WATER, 1000).build();
        var fluidOutput = new FluidOutput(fluid);
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
    void input_fluid_supports_consume_chance() {
        MachineRecipeDefinition def = MachineRecipeBuilder.recipe(id("fluid_consume_chance")).recipePool(id("machine"))
                .inputFluid(Fluids.WATER, 1000, 0.25F)
                .build();

        var fluid = (cn.howxu.mmcr.api.recipe.requirement.FluidRequirement) def.requirements().get(0);
        assertThat(fluid.consumeChance()).isEqualTo(0.25F);
    }

    @Test
    void core_requirements_keep_tags_components_and_probabilities_through_definition_and_runtime() {
        var source = new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 3,
                ItemStack.EMPTY, 1F, List.of("catalyst"), components(), 0.25F);
        var definition = MachineRecipeBuilder.recipe(id("canonical_requirement")).recipePool(id("machine"))
                .requirement(source).build();
        var recipe = MachineRecipeConverter.toRecipe(definition,
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

        assertThat(definition.requirements()).containsExactly(source);
        assertThat(recipe.runtimeRequirements()).singleElement().isInstanceOfSatisfying(ItemRequirement.class, item -> {
            assertThat(item.tags()).containsExactly("catalyst");
            assertThat(item.consumeChance()).isEqualTo(0.25F);
            assertThat(item.components().values()).containsKey(DataComponents.REPAIR_COST);
        });
    }

    @Test
    void internal_fluid_requirement_preserves_consume_chance_after_conversion() {
        MachineRecipeDefinition def = MachineRecipeBuilder.recipe(id("fluid_consume_chance_conversion")).recipePool(id("machine"))
                .inputFluid(Fluids.WATER, 1000, 0.25F)
                .build();

        var recipe = MachineRecipeConverter.toRecipe(def,
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

        assertThat(recipe.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(FluidRequirement.class);
            var fluid = (FluidRequirement) requirement;
            assertThat(fluid.consumeChance()).isEqualTo(0.25F);
        });
    }

    @Test
    void input_fluid_consume_chance_zero_emits_not_consumed_payload() {
        MachineRecipeDefinition def = MachineRecipeBuilder.recipe(id("fluid_consume_zero")).recipePool(id("machine"))
                .inputFluid(Fluids.WATER, 1000, 0F)
                .build();

        var fluid = (cn.howxu.mmcr.api.recipe.requirement.FluidRequirement) def.requirements().get(0);
        assertThat(fluid.consumeChance()).isEqualTo(0F);
    }

    @Test
    void input_chemical_supports_consume_chance() {
        MachineRecipeDefinition def = MachineRecipeBuilder.recipe(id("chemical_consume_chance")).recipePool(id("machine"))
                .inputChemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L, 0.25F)
                .build();

        var requirement = def.requirements().getFirst();
        var payload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow();
        assertThat(payload.getAsJsonObject().get("consume_chance").getAsFloat()).isEqualTo(0.25F);
    }

    @Test
    void rejects_invalid_ranges() {
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad")).recipePool(id("machine")).duration(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input(" ", 1F))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input("Mode", Float.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input("Mode", 2F, 1F))
                .isInstanceOf(IllegalArgumentException.class);
         assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad")).recipePool(id("machine")).inputItem(Items.STICK, 0))
                .isInstanceOf(IllegalArgumentException.class);
         assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad")).recipePool(id("machine"))
                .outputChance(new ItemStack(Items.STICK), 2F))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void custom_recipe_io_decodes_registered_requirement_and_output_without_exposing_runtime_types() {
        var input = new cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement(
                RecipeModifier.IOType.INPUT, 12);
        var output = new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F);
        var inputPayload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, input).getOrThrow();
        var outputPayload = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow();

        var definition = MachineRecipeBuilder.recipe(id("custom")).recipePool(id("machine"))
                .custom(new CustomRecipeIo(input.type().id(), IOType.INPUT, inputPayload))
                .custom(new CustomRecipeIo(output.outputType().id(), IOType.OUTPUT, outputPayload))
                .build();
        var recipe = MachineRecipeConverter.toRecipe(definition,
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

        assertThat(recipe.requirements()).contains(input);
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement)
                .isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.ItemRequirement.class));
    }

    @Test
    void custom_recipe_io_copies_payload_and_rejects_invalid_declarations() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "neoforge:energy");
        payload.addProperty("io", "input");
        payload.addProperty("fe_per_tick", 12);
        var custom = new CustomRecipeIo(cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement.TYPE.id(),
                IOType.INPUT, payload);
        payload.addProperty("fe_per_tick", 1);
        custom.payload().getAsJsonObject().addProperty("fe_per_tick", 2);

        assertThat(custom.payload().getAsJsonObject().get("fe_per_tick").getAsInt()).isEqualTo(12);
        assertThatThrownBy(() -> new CustomRecipeIo(id("energy"), IOType.INPUT,
                JsonOps.INSTANCE.createInt(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("unknown_custom")).recipePool(id("machine"))
                .custom(new CustomRecipeIo(id("unknown"), IOType.INPUT, custom.payload())))
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
            var customOutput = new CustomRecipeIo(TestOutput.TYPE.id(), IOType.OUTPUT, outputPayload);
            customOutput.payload().getAsJsonObject().addProperty("value", 1);

        var recipe = MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(id("custom_extension")).recipePool(id("machine"))
                    .custom(new CustomRecipeIo(TestRequirement.TYPE.id(), IOType.INPUT, requirementPayload))
                    .custom(customOutput).build(),
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

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

    private static ResourceLocation id(String path) {
        return MMCR.id(path);
    }

    private static DataComponentPredicateSet components() {
        return DataComponentPredicateSet.ofIds(Map.of(
                BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(DataComponents.REPAIR_COST),
                ComponentPredicate.exact(JsonOps.INSTANCE.createInt(1))));
    }
}
