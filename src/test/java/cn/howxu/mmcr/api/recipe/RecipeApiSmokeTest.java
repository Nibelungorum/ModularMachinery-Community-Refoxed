package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.helper.CraftCheck;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import static org.assertj.core.api.Assertions.assertThat;

class RecipeApiSmokeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void recipe_codec_roundtrip_preserves_modifiers_and_priority() {
        var id = Identifier.fromNamespaceAndPath("mmcr", "smoke_with_mods");
        var machineId = Identifier.fromNamespaceAndPath("mmcr", "smoke_machine");
        var mods = List.of(
                new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 2.0F, RecipeModifier.Operation.ADD, false)
        );
        var recipe = new MachineRecipe(
                id, machineId, 100,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(),
                mods, 5, 4, true
        );

        var ops = jsonOps();
        var json = MachineRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
        var back = MachineRecipe.CODEC.codec().parse(ops, json).getOrThrow();

        assertThat(back).isEqualTo(recipe);
        assertThat(back.modifiers()).hasSize(2);
        assertThat(back.priority()).isEqualTo(5);
        assertThat(back.maxThreads()).isEqualTo(4);
        assertThat(back.getRecipeTotalTickTime()).isEqualTo(100);
        assertThat(back.getRegistryName()).isEqualTo(id);
        assertThat(back.getOwningMachineIdentifier()).isEqualTo(machineId);
        assertThat(back.doesCancelRecipeOnPerTickFailure()).isTrue();
    }

    @Test
    void recipe_codec_roundtrip_preserves_fluid_outputs_empty_and_one() {
        var waterHolder = bindFluidComponents(Fluids.WATER);

        var machineId = Identifier.fromNamespaceAndPath("mmcr", "fluid_outputs_machine");

        var emptyRecipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "fluid_outputs_empty"),
                machineId, 20,
                List.of(),
                List.of(),
                List.of(), 0, 1, false, List.of()
        );

        var oneRecipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "fluid_outputs_one"),
                machineId, 20,
                List.of(),
                List.of(),
                List.of(), 0, 1, false,
                List.of(new FluidStack(waterHolder, 250))
        );

        var ops = jsonOps();

        var emptyJson = MachineRecipe.CODEC.codec().encodeStart(ops, emptyRecipe).getOrThrow();
        assertThat(emptyJson.getAsJsonObject().has("fluid_outputs")).isFalse();
        var emptyBack = MachineRecipe.CODEC.codec().parse(ops, emptyJson).getOrThrow();
        assertThat(emptyBack.fluidOutputs()).isEmpty();
        assertThat(emptyBack).isEqualTo(emptyRecipe);

        var oneJson = MachineRecipe.CODEC.codec().encodeStart(ops, oneRecipe).getOrThrow();
        var oneBack = MachineRecipe.CODEC.codec().parse(ops, oneJson).getOrThrow();
        assertThat(oneBack.fluidOutputs()).hasSize(1);
        assertThat(oneBack.fluidOutputs().getFirst().getFluid()).isEqualTo(Fluids.WATER);
        assertThat(oneBack.fluidOutputs().getFirst().getAmount()).isEqualTo(250);
        assertThat(oneBack.id()).isEqualTo(oneRecipe.id());
        assertThat(oneBack.machineId()).isEqualTo(oneRecipe.machineId());
        assertThat(oneBack.tickTime()).isEqualTo(oneRecipe.tickTime());
    }

    private static Holder<Fluid> bindFluidComponents(Fluid fluid) {
        var holder = fluid.builtInRegistryHolder();
        holder.bindComponents(DataComponentMap.EMPTY);
        return holder;
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }

    @Test
    void recipe_codec_optional_fields_have_defaults() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "minimal"),
                Identifier.fromNamespaceAndPath("mmcr", "minimal_machine"),
                20, List.of(), List.of()
        );
        var json = MachineRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow();
        var back = MachineRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

        assertThat(back.modifiers()).isEmpty();
        assertThat(back.priority()).isZero();
        assertThat(back.maxThreads()).isEqualTo(1);
        assertThat(back.doesCancelRecipeOnPerTickFailure()).isFalse();
    }

    @Test
    void recipe_modifier_apply_modifiers_combines_add_and_multiply() {
        var mods = List.of(
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 2.0F, RecipeModifier.Operation.ADD, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 1.5F, RecipeModifier.Operation.MULTIPLY, false)
        );
        float result = RecipeModifier.applyModifiers(mods, "item", RecipeModifier.IOType.OUTPUT, 10F, false);
        assertThat(result).isEqualTo((10F + 2F) * 1.5F);
    }

    @Test
    void recipe_modifier_applies_add_subtract_before_multiply_divide() {
        var mods = List.of(
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 4F, RecipeModifier.Operation.ADD, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 3F, RecipeModifier.Operation.SUBTRACT, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 6F, RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 2F, RecipeModifier.Operation.DIVIDE, false)
        );

        float result = RecipeModifier.applyModifiers(mods, "item", RecipeModifier.IOType.OUTPUT, 10F, false);

        assertThat(result).isEqualTo(((10F + 4F - 3F) * 6F) / 2F);
    }

    @Test
    void recipe_modifier_ignores_zero_divide_modifier() {
        var mods = List.of(
                new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 0F, RecipeModifier.Operation.DIVIDE, false)
        );

        float result = RecipeModifier.applyModifiers(mods, "duration", RecipeModifier.IOType.INPUT, 80F, false);

        assertThat(result).isEqualTo(80F);
    }

    @Test
    void recipe_modifier_filters_by_target_and_io() {
        var mods = List.of(
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 5F, RecipeModifier.Operation.ADD, false),
                new RecipeModifier("fluid", RecipeModifier.IOType.INPUT, 5F, RecipeModifier.Operation.ADD, false),
                new RecipeModifier("item", RecipeModifier.IOType.INPUT, 99F, RecipeModifier.Operation.ADD, false)
        );
        float itemOut = RecipeModifier.applyModifiers(mods, "item", RecipeModifier.IOType.OUTPUT, 1F, false);
        float fluidIn = RecipeModifier.applyModifiers(mods, "fluid", RecipeModifier.IOType.INPUT, 1F, false);
        assertThat(itemOut).isEqualTo(6F);
        assertThat(fluidIn).isEqualTo(6F);
    }

    @Test
    void recipe_modifier_nbt_roundtrip() {
        var mod = new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 0.25F, RecipeModifier.Operation.MULTIPLY, true);
        var tag = mod.serializeNbt();
        var back = RecipeModifier.deserializeNbt(tag);
        assertThat(back).isEqualTo(mod);
    }

    @Test
    void integration_helper_applies_duration_modifier() {
        var mods = List.of(
                new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false)
        );
        assertThat(IntegrationTypeHelper.applyDuration(mods, 200)).isEqualTo(100F);
    }

    @Test
    void active_recipe_uses_derived_duration_but_recipe_tick_time_stays_raw() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "duration_runtime"),
                Identifier.fromNamespaceAndPath("mmcr", "duration_machine"),
                100,
                List.of(),
                List.of(),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
        );

        var active = new ActiveMachineRecipe(recipe);

        assertThat(active.getTotalTick()).isEqualTo(50);
        assertThat(recipe.getRecipeTotalTickTime()).isEqualTo(100);
    }

    @Test
    void runtime_requirements_apply_all_supported_modifier_targets() {
        bindFluidComponents(Fluids.WATER);
        bindItemComponents(Items.IRON_NUGGET);
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "runtime_modifiers"),
                Identifier.fromNamespaceAndPath("mmcr", "runtime_machine"),
                100,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250),
                        new MachineIngredient.EnergyIngredient(40)
                ),
                List.of(Items.IRON_NUGGET.getDefaultInstance().copyWithCount(1)),
                List.of(
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_FLUID, RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ENERGY, RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.OUTPUT, 3F, RecipeModifier.Operation.MULTIPLY, false)
                ),
                0,
                1
        );

        assertThat(recipe.runtimeRequirements()).satisfies(requirements -> {
            assertThat(((ItemRequirement) requirements.get(0)).count()).isEqualTo(4);
            assertThat(((FluidRequirement) requirements.get(1)).amount()).isEqualTo(500);
            assertThat(((EnergyRequirement) requirements.get(2)).fePerTick()).isEqualTo(80);
            assertThat(((ItemRequirement) requirements.get(3)).stack().getCount()).isEqualTo(3);
        });
        assertThat(recipe.inputs()).contains(new MachineIngredient.EnergyIngredient(40));
        assertThat(recipe.outputs().getFirst().getCount()).isEqualTo(1);
    }

    @Test
    void registry_groups_recipes_by_machine_and_priority() {
        var machineA = Identifier.fromNamespaceAndPath("mmcr", "machine_a");
        var machineB = Identifier.fromNamespaceAndPath("mmcr", "machine_b");

        var recipe1 = new MachineRecipe(Identifier.fromNamespaceAndPath("mmcr", "r1"), machineA, 10, List.of(), List.of(), List.of(), 0, 1);
        var recipe2 = new MachineRecipe(Identifier.fromNamespaceAndPath("mmcr", "r2"), machineA, 20, List.of(), List.of(), List.of(), 5, 1);
        var recipe3 = new MachineRecipe(Identifier.fromNamespaceAndPath("mmcr", "r3"), machineB, 30, List.of(), List.of(), List.of(), 0, 1);

        RecipeRegistry.register(recipe1);
        RecipeRegistry.register(recipe2);
        RecipeRegistry.register(recipe3);

        assertThat(RecipeRegistry.getRecipe(recipe1.id())).isEqualTo(recipe1);
        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(3);
        assertThat(RecipeRegistry.byMachineId(machineA)).containsExactly(recipe1, recipe2);
        assertThat(RecipeRegistry.byMachineId(machineB)).containsExactly(recipe3);
        assertThat(RecipeRegistry.byMachineId(Identifier.fromNamespaceAndPath("mmcr", "unknown"))).isEmpty();
    }

    @Test
    void active_recipe_nbt_roundtrip() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "active_test"),
                Identifier.fromNamespaceAndPath("mmcr", "active_test_machine"),
                100, List.of(), List.of()
        );
        RecipeRegistry.register(recipe);

        var active = new ActiveMachineRecipe(recipe, 4);
        active.setTick(50);
        active.setTotalTick(200);
        active.setParallelism(2);
        active.getDataCompound().putInt("custom", 42);

        var tag = active.serialize();
        var back = new ActiveMachineRecipe(tag);

        assertThat(back.getRecipe()).isEqualTo(recipe);
        assertThat(back.getTick()).isEqualTo(50);
        assertThat(back.getTotalTick()).isEqualTo(200);
        assertThat(back.getMaxParallelism()).isEqualTo(4);
        assertThat(back.getParallelism()).isEqualTo(2);
        assertThat(back.getDataCompound().getIntOr("custom", 0)).isEqualTo(42);
        assertThat(back.isCompleted()).isFalse();
    }

    @Test
    void active_recipe_marks_completed_when_tick_reaches_total() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "done_test"),
                Identifier.fromNamespaceAndPath("mmcr", "done_test_machine"),
                10, List.of(), List.of()
        );
        RecipeRegistry.register(recipe);
        var active = new ActiveMachineRecipe(recipe);
        active.setTick(10);
        assertThat(active.isCompleted()).isTrue();
    }

    @Test
    void active_recipe_does_not_recheck_started_inputs_mid_process() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "vanishing_input"),
                Identifier.fromNamespaceAndPath("mmcr", "vanishing_input_machine"),
                10,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of()
        );
        var active = new ActiveMachineRecipe(recipe);
        active.setTick(2);

        assertThat(active.applyTickGrant(true, false, 0))
                .isEqualTo(ActiveMachineRecipe.TickStatus.CONTINUE);
        assertThat(active.getTick()).isEqualTo(3);
    }

    @Test
    void prepared_recipe_converts_to_machine_recipe() {
        var prepared = new PreparedRecipe(
                "mmcr:from_prepared",
                "mmcr:prep_machine",
                50,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND), 1)),
                List.of(),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false)),
                3, 2
        );
        var recipe = prepared.toMachineRecipe();
        assertThat(recipe.id().toString()).isEqualTo("mmcr:from_prepared");
        assertThat(recipe.machineId().toString()).isEqualTo("mmcr:prep_machine");
        assertThat(recipe.tickTime()).isEqualTo(50);
        assertThat(recipe.priority()).isEqualTo(3);
        assertThat(recipe.maxThreads()).isEqualTo(2);
        assertThat(recipe.doesCancelRecipeOnPerTickFailure()).isFalse();
        assertThat(recipe.inputs()).hasSize(1);
        assertThat(recipe.outputs()).isEmpty();
        assertThat(recipe.modifiers()).isEqualTo(prepared.getModifiers());
        assertThat(recipe.getRecipeTotalTickTime()).isEqualTo(50);
    }

    @Test
    void prepared_recipe_preserves_parallel_opt_in_when_converting_to_machine_recipe() {
        var prepared = new PreparedRecipe(
                "mmcr:parallel_prepared",
                "mmcr:prep_machine",
                50,
                List.of(),
                List.of(),
                List.of(),
                0,
                3,
                false,
                List.of(),
                true
        );

        var recipe = prepared.toMachineRecipe();

        assertThat(prepared.isParallelized()).isTrue();
        assertThat(recipe.maxThreads()).isEqualTo(3);
        assertThat(recipe.isParallelized()).isTrue();
    }

    @Test
    void craft_check_reports_success_and_failure() {
        assertThat(CraftCheck.success().isSuccess()).isTrue();
        assertThat(CraftCheck.partialSuccess().isSuccess()).isFalse();
        assertThat(CraftCheck.failure("nope").isSuccess()).isFalse();
        assertThat(CraftCheck.skipComponent().isInvalid()).isTrue();
    }

    @Test
    void craft_check_preserves_structured_requirement_failure() {
        var failure = new RequirementFailure(2, RequirementFailure.Kind.MISSING_OUTPUT, 8, 5);

        var check = CraftCheck.failure("no room", failure);

        assertThat(check.getUnlocalizedMessage()).isEqualTo("no room");
        assertThat(check.getRequirementFailure()).isEqualTo(failure);
    }

    @Test
    void crafting_status_reflects_working_and_failure() {
        assertThat(CraftingStatus.working().isCrafting()).isTrue();
        assertThat(CraftingStatus.failure("err").isFailure()).isTrue();
        assertThat(CraftingStatus.IDLE.isCrafting()).isFalse();
        assertThat(CraftingStatus.MISSING_STRUCTURE.isCrafting()).isFalse();
    }

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static MachineControllerBlockEntity controllerWithoutComponents() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
            Field components = MachineControllerBlockEntity.class.getDeclaredField("components");
            components.setAccessible(true);
            components.set(controller, new ArrayList<>());
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate MachineControllerBlockEntity for crafting context test", e);
        }
    }
}
