package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import mezz.jei.api.recipe.RecipeIngredientRole;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.machine.SmartInterfaceModifier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import net.minecraft.network.chat.FormattedText;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.stream.IntStream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRecipeDisplayTest {

    @Test
    void inputOverlayTextUsesKeepAndChanceLabels() {
        assertThat(MachineRecipeCategory.inputOverlayText(0F, "zh_cn")).isEqualTo("不消耗");
        assertThat(MachineRecipeCategory.inputOverlayText(0F, "en_us")).isEqualTo("Keep");
        assertThat(MachineRecipeCategory.inputOverlayText(0.5F, "en_us")).isEqualTo("50%");
        assertThat(MachineRecipeCategory.inputOverlayText(1F, "en_us")).isEmpty();
    }

    @Test
    void outputOverlayTextShowsChanceBelowFull() {
        assertThat(MachineRecipeCategory.outputOverlayText(1F)).isEmpty();
        assertThat(MachineRecipeCategory.outputOverlayText(0.5F)).isEqualTo("50%");
        assertThat(MachineRecipeCategory.outputOverlayText(0.25F)).isEqualTo("25%");
    }

    @Test
    void itemQuantityTextIsOnlyShownAboveOne() {
        assertThat(MachineRecipeCategory.itemQuantityText(1)).isEmpty();
        assertThat(MachineRecipeCategory.itemQuantityText(1_234)).isEqualTo("1.23K");
        assertThat(MachineRecipeCategory.itemQuantityText(114_514)).isEqualTo("114K");
    }

    @Test
    void fluidQuantityTextIsOnlyShownAboveOneBucket() {
        assertThat(MachineRecipeCategory.fluidQuantityText(1_000)).isEqualTo("1.00B");
        assertThat(MachineRecipeCategory.fluidQuantityText(1_001)).isEqualTo("1.00B");
        assertThat(MachineRecipeCategory.fluidQuantityText(140)).isEqualTo("0.14B");
        assertThat(MachineRecipeCategory.fluidQuantityText(1_234)).isEqualTo("1.23B");
    }

    @Test
    void tooltipQuantityTextUsesExactGrouping() {
        assertThat(MachineRecipeCategory.itemTooltipQuantity(1)).isEmpty();
        assertThat(MachineRecipeCategory.itemTooltipQuantity(1_234)).isEqualTo("1,234");
        assertThat(MachineRecipeCategory.fluidTooltipQuantity(1_234)).isEqualTo("1.234B");
    }

    @Test
    void jeiItemStackUsesOneAsDisplayCountAndPreservesSourceStack() {
        ItemStack source = new ItemStack(Items.IRON_INGOT, 1_234);

        ItemStack jeiStack = MachineRecipeCategory.jeiItemStack(source);

        assertThat(source.getCount()).isEqualTo(1_234);
        assertThat(jeiStack.getItem()).isSameAs(source.getItem());
        assertThat(jeiStack.getCount()).isEqualTo(1);
    }

    @Test
    void display_uses_i18n_tooltip_for_interface_input() {
        var machineId = MMCR.id("interface_jei_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).localizedName("Interface JEI")
                .smartInterfaceType(new SmartInterfaceType("mode", 0F, 0)).build());
        MachineRecipeDisplay display = displayFor(SmartInterfaceRequirement.input("mode", 1F, 2F), machineId);

        assertThat(display.tooltips()).singleElement().satisfies(tooltip -> {
            var contents = (TranslatableContents) tooltip.getContents();
            assertThat(contents.getKey()).isEqualTo("jei.mmcr.smart_interface.requirement.input");
            assertThat(((TranslatableContents)
                    ((Component) contents.getArgs()[0]).getContents()).getKey())
                    .isEqualTo("mmcr.smart_interface.type.mode");
            assertThat(contents.getArgs()[1]).isEqualTo("[1.0, 2.0]");
        });
        assertThat(display.smartInterfaceInputs()).singleElement()
                .extracting(MachineRecipeDisplay.SmartInterfaceDisplay::value)
                .isEqualTo("[1.0, 2.0]");
    }

    @Test
    void display_uses_i18n_tooltip_for_interface_output() {
        var machineId = MMCR.id("interface_jei_fallback");
        MachineDefinitions.register(MachineRegistration.builder(machineId).localizedName("Interface JEI Fallback")
                .smartInterfaceType(new SmartInterfaceType("mode", 0F, 0)).build());
        MachineRecipeDisplay display = displayFor(SmartInterfaceRequirement.output("mode", 4F), machineId);

        assertThat(display.smartInterfaceOutputs()).singleElement().satisfies(output -> {
            assertThat(((TranslatableContents) output.label().getContents()).getKey())
                    .isEqualTo("mmcr.smart_interface.value");
            assertThat(((TranslatableContents) output.tooltip().getContents()).getKey())
                    .isEqualTo("jei.mmcr.smart_interface.requirement.output");
        });
    }

    @Test
    void smart_interface_jei_text_uses_localized_type_compact_ranges_and_integer_values() {
        var machineId = MMCR.id("interface_jei_compact");
        MachineDefinitions.register(MachineRegistration.builder(machineId).localizedName("Interface JEI Compact")
                .smartInterfaceType(new SmartInterfaceType("Mode", 0F, 0, SmartInterfaceType.ValueType.INTEGER))
                .smartInterfaceType(new SmartInterfaceType("Temperature", 400F, 1, SmartInterfaceType.ValueType.INTEGER))
                .build());

        MachineRecipeDisplay mode = displayFor(SmartInterfaceRequirement.input("Mode", 1F), machineId);
        MachineRecipeDisplay temperature = displayFor(SmartInterfaceRequirement.input("Temperature", 5200F), machineId);

        assertThat(mode.smartInterfaceInputs()).singleElement().satisfies(input -> {
            assertThat(((TranslatableContents) input.label().getContents()).getKey())
                    .isEqualTo("mmcr.smart_interface.value");
            assertThat(((TranslatableContents) input.tooltip().getContents()).getArgs()[1])
                    .isEqualTo("1");
        });
        assertThat(temperature.smartInterfaceInputs()).singleElement().satisfies(input -> {
            assertThat(((TranslatableContents) input.label().getContents()).getKey())
                    .isEqualTo("mmcr.smart_interface.value");
            assertThat(((TranslatableContents) input.tooltip().getContents()).getArgs()[1])
                    .isEqualTo("5200");
        });
    }

    @Test
    void display_includes_smart_interface_modifiers_from_machine_registration() {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.beginRegistryPhase();
        MachineDefinitions.register(MachineRegistration.builder(MMCR.id("jei_interface_modifier"))
                .smartInterfaceType(new SmartInterfaceType("temperature", 20F, 0))
                .smartInterfaceModifier(SmartInterfaceModifier.energy("temperature", 0F, 100F, 1F, 2F,
                        RecipeModifier.Operation.MULTIPLY))
                .build());
        MachineDefinitions.freezeRegistryPhase();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("jei_smart_modifier_recipe"),
                MMCR.id("jei_interface_modifier"), 40, List.of(), List.of());

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.smartInterfaceModifiers()).containsExactly(new MachineRecipeDisplay.SmartInterfaceModifierDisplay(
                "temperature", IntegrationTypeHelper.TARGET_ENERGY, RecipeModifier.IOType.INPUT, false,
                0F, 100F, 1F, 2F, RecipeModifier.Operation.MULTIPLY));
        assertThat(display.tooltips()).anyMatch(text -> text.getString().contains("temperature") && text.getString().contains("energy"));
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);
        assertThat(layout.lastMetadataTextY(display)).isEqualTo(layout.smartInterfaceTextY(display) - 10);
    }

    @Test
    void describesAddedJeiItemStackFromConcreteStackData() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.REPAIR_COST, 3);

        String description = MachineRecipeCategory.describeAddedItemStack(stack);

        assertThat(description).contains("minecraft:diamond_sword x1");
        assertThat(description).contains("minecraft:repair_cost");
        assertThat(description).contains("patch=");
    }

    @Test
    void itemOverlayUsesReducedScaleAtSlotTopLeft() {
        assertThat(MachineRecipeCategory.ITEM_OVERLAY_SCALE).isEqualTo(0.6F);
        assertThat(MachineRecipeCategory.ITEM_OVERLAY_X).isEqualTo(0);
        assertThat(MachineRecipeCategory.ITEM_OVERLAY_Y).isEqualTo(0);
    }

    @Test
    void displaySortsRequiredHostIdsForStableJeiCycling() {
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("jei_required_hosts"), MMCR.id("hosted_module"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(), false, List.of(), Set.of(
                MMCR.id("zeta_host"), MMCR.id("alpha_host"), MMCR.id("middle_host")
        ));

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.requiredHostIds()).containsExactly(MMCR.id("alpha_host"), MMCR.id("middle_host"), MMCR.id("zeta_host"));
    }

    @Test
    void publicConstructorNormalizesMutableUnorderedRequiredHostIds() {
        MachineRecipeDisplay template = MachineRecipeDisplay.from(RecipeTestSupport.create(
                MMCR.id("direct_constructor_host_recipe"), MMCR.id("hosted_module"), 20, List.of(), List.of()));
        Set<Identifier> requiredHostIds = new LinkedHashSet<>(List.of(
                MMCR.id("zeta_host"), MMCR.id("alpha_host"), MMCR.id("middle_host")));

        MachineRecipeDisplay display = new MachineRecipeDisplay(
                template.recipe(), template.recipeId(), template.machineId(), template.durationTicks(),
                template.itemInputs(), template.itemOutputs(), template.fluidInputs(), template.fluidInputAmounts(),
                template.fluidOutputs(), template.energyInputs(), template.energyOutputs(), template.outputs(),
                template.smartInterfaceInputs(), template.smartInterfaceOutputs(), template.smartInterfaceModifiers(),
                requiredHostIds);
        requiredHostIds.add(MMCR.id("mutated_source_host"));

        assertThat(display.requiredHostIds())
                .containsExactly(MMCR.id("alpha_host"), MMCR.id("middle_host"), MMCR.id("zeta_host"));
        assertThatThrownBy(() -> display.requiredHostIds().add(MMCR.id("mutated_display_host")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void displayExposesImmutableRequiredHostIds() {
        MachineRecipeDisplay display = hostDisplay(Set.of(MMCR.id("immutable_host")));

        assertThatThrownBy(() -> display.requiredHostIds().add(MMCR.id("mutated_host")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hostRequirementIsEmptyWhenRecipeHasNoRequiredHost() {
        MachineRecipeDisplay display = MachineRecipeDisplay.from(RecipeTestSupport.create(
                MMCR.id("jei_no_required_host"), MMCR.id("module_without_host"), 20, List.of(), List.of()));

        assertThat(MachineRecipeCategory.hostRequirementComponent(display, 0)).satisfies(component -> {
            assertThat(component.getString()).isEmpty();
            assertThat(component.getContents()).isSameAs(Component.empty().getContents());
        });
    }

    @Test
    void hostRequirementShowsSingleHostDisplayName() {
        MachineDefinitions.register(MachineRegistration.builder(MMCR.id("single_host"))
                .displayNameKey("machine.mmcr.single_host").host(MMCR.id("hosted_module")).build());
        MachineRecipeDisplay display = hostDisplay(Set.of(MMCR.id("single_host")));

        Component component = MachineRecipeCategory.hostRequirementComponent(display, 0);

        assertThat(((TranslatableContents) component.getContents()).getKey())
                .isEqualTo("jei.mmcr.machine_recipe.required_host");
        assertThat(component.getString()).isEqualTo("jei.mmcr.machine_recipe.required_host");
        assertThat(((Component) ((TranslatableContents) component.getContents()).getArgs()[0]).getString())
                .isEqualTo("machine.mmcr.single_host");
    }

    @Test
    void hostRequirementCyclesMultipleHostsByStableSortedGameTime() {
        registerHost("beta_host");
        registerHost("alpha_host");
        registerHost("gamma_host");
        MachineRecipeDisplay display = hostDisplay(Set.of(MMCR.id("gamma_host"), MMCR.id("alpha_host"), MMCR.id("beta_host")));

        assertThat(hostRequirementArg(display, 0).getString()).isEqualTo("machine.mmcr.alpha_host");
        assertThat(hostRequirementArg(display, 20).getString()).isEqualTo("machine.mmcr.beta_host");
        assertThat(hostRequirementArg(display, 40).getString()).isEqualTo("machine.mmcr.gamma_host");
        assertThat(hostRequirementArg(display, 60).getString()).isEqualTo("machine.mmcr.alpha_host");
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(
                Items.COAL, Items.IRON_INGOT, Items.GOLD_INGOT, Items.IRON_NUGGET, Items.GOLD_NUGGET,
                Items.DIAMOND_SWORD
        );
    }

    @BeforeEach
    void resetMachineDefinitions() {
        MachineDefinitions.clearForTesting();
    }

    @Test
    void displayIncludesItemFluidEnergyAndDuration() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_display"),
                MMCR.id("blast_furnace"),
                120,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 8),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250),
                        new MachineIngredient.EnergyIngredient(40)
                ),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 4)),
                List.of(),
                3,
                1,
                true,
                List.of(new FluidStack(Fluids.LAVA.builtInRegistryHolder(), 125))
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.recipeId()).isEqualTo(MMCR.id("jei_display"));
        assertThat(display.machineId()).isEqualTo(MMCR.id("blast_furnace"));
        assertThat(display.durationTicks()).isEqualTo(120);
        assertThat(display.itemInputs()).hasSize(1);
        assertThat(display.itemInputs()).extracting(MachineRecipeDisplay.ItemInputDisplay::count).containsExactly(8);
        assertThat(display.itemInputs().getFirst().stacks()).extracting(ItemStack::getCount).containsExactly(8);
        assertThat(display.itemOutputs()).singleElement().satisfies(output -> {
            assertThat(output.stack().is(Items.IRON_NUGGET)).isTrue();
            assertThat(output.stack().getCount()).isEqualTo(4);
        });
        assertThat(display.fluidInputs()).hasSize(1);
        assertThat(display.fluidInputAmounts()).containsExactly(250);
        assertThat(display.fluidOutputs()).hasSize(1);
        assertThat(display.energyInputs()).containsExactly(new EnergyIngredient(40, true));
        assertThat(display.energyOutputs()).isEmpty();
    }

    @Test
    void displayUsesRuntimeOutputsAfterModifiers() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_modifier_display"),
                MMCR.id("blast_furnace"),
                80,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 2)),
                List.of(new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 3.0F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.outputs()).hasOnlyElementsOfType(MachineOutput.ItemOutput.class);
        assertThat(display.itemOutputs()).extracting(MachineRecipeDisplay.ItemOutputDisplay::stack)
                .extracting(ItemStack::getCount).containsExactly(6);
    }

    @Test
    void displayPreservesItemOutputChanceForOverlay() {
        ItemStack stack = new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1);
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_chanced_output"),
                MMCR.id("blast_furnace"),
                80,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack, 0.5F, List.of()))
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.outputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack().getItem()).isSameAs(Items.IRON_NUGGET);
            assertThat(output.chance()).isEqualTo(0.5F);
        });
    }

    @Test
    void displayPreservesOutputComponents() {
        ItemStack namedSharpnessFourSword = namedSharpnessFourSword();
        namedSharpnessFourSword.setCount(2);
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("component_output_display"),
                MMCR.id("test_machine_name"),
                40,
                List.of(),
                List.of(namedSharpnessFourSword)
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        ItemStack output = display.itemOutputs().getFirst().stack();
        assertThat(output.getHoverName().getString()).isEqualTo("Better钻石剑");
        assertThat(output.getCount()).isEqualTo(2);
        assertThat(output.get(DataComponents.ENCHANTMENTS))
                .isEqualTo(namedSharpnessFourSword.get(DataComponents.ENCHANTMENTS));
    }

    @Test
    void displayResolvesComponentsFromExplicitItemOutputRequirements() {
        DataComponentPredicateSet components = new DataComponentPredicateSet(Map.of(
                DataComponents.REPAIR_COST, ComponentPredicate.exact(
                        new Dynamic<>(JsonOps.INSTANCE,
                                new JsonPrimitive(1)))));
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("explicit_component_output_display"), MMCR.id("test_machine_name"), 40,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_SWORD),
                        1F, List.of(), components, 1F)
        ));

        ItemStack output = MachineRecipeDisplay.from(recipe).itemOutputs().getFirst().stack();

        assertThat(output.get(DataComponents.REPAIR_COST)).isEqualTo(1);
    }

    @Test
    void displayAppliesTextComponentPredicatesToInputStacks() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("component_input_display"),
                MMCR.id("test_machine_name"),
                40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND_SWORD), 1,
                        new DataComponentPredicateSet(Map.of(DataComponents.CUSTOM_NAME,
                                ComponentPredicate.text("Required剑", ComponentPredicate.TextMode.PLAIN))), 1F)),
                List.of()
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        ItemStack input = display.itemInputs().getFirst().stacks().getFirst();
        assertThat(input.get(DataComponents.CUSTOM_NAME)).isEqualTo(Component.literal("Required剑"));
        assertThat(display.itemInputs().getFirst().hasUnexportedComponentConstraints()).isTrue();
    }

    @Test
    void itemSlotsUseComponentAwareStacksForTagInputs() {
        DataComponentPredicateSet components = new DataComponentPredicateSet(Map.of(
                DataComponents.REPAIR_COST, ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(3)))));
        Ingredient tag = Ingredient.of(HolderSet.direct(
                Items.DIAMOND_SWORD.builtInRegistryHolder(), Items.IRON_SWORD.builtInRegistryHolder()));
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("component_tag_jei_slot"), MMCR.id("test_machine_name"), 40,
                List.of(new MachineIngredient.ItemIngredient(tag, 1, components, 1F)), List.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        List<ItemStack> stacks = display.itemInputs().getFirst().stacks();
        assertThat(stacks).hasSize(2);
        assertThat(stacks).allSatisfy(stack ->
                assertThat(stack.get(DataComponents.REPAIR_COST)).isEqualTo(3));
    }

    @Test
    void itemSlotsApplyEnchantmentPredicatesToTagCandidates() throws Exception {
        var lookup = VanillaRegistries.createLookup();
        JsonObject enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 2);
        DataComponentPredicateSet components = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS, ComponentPredicate.exact(new Dynamic<>(
                        RegistryOps.create(JsonOps.INSTANCE, lookup), enchantments))));
        Ingredient tag = Ingredient.of(HolderSet.direct(
                Items.DIAMOND_SWORD.builtInRegistryHolder(), Items.GOLDEN_SWORD.builtInRegistryHolder()));
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("enchantment_tag_jei_slot"), MMCR.id("test_machine_name"), 40,
                List.of(new MachineIngredient.ItemIngredient(tag, 1, components, 1F)), List.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        SlotCapture capture = new SlotCapture();
        JeiDisplayEntry inputEntry = display.entries().stream()
                .filter(entry -> entry.role() == RecipeIngredientRole.INPUT)
                .findFirst().orElseThrow();

        invokeAddEntry(recipeLayoutBuilder(capture), display,
                new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 0, inputEntry), true);

        assertThat(capture.itemStacks).singleElement().satisfies(stacks -> {
            assertThat(stacks).hasSize(2);
            assertThat(stacks).allSatisfy(stack ->
                    assertThat(stack.get(DataComponents.ENCHANTMENTS).getLevel(
                            lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(sharpnessKey()))).isEqualTo(2));
        });
    }

    @Test
    void itemSlotsRegisterQuantityAndChanceTooltips() throws Exception {
        MachineRequirement output = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(Items.GOLD_INGOT, 3), 0.25F, List.of());
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("item_tooltip_jei_slot"), MMCR.id("test_machine_name"), 40,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(output));
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        SlotCapture capture = new SlotCapture();
        JeiDisplayEntry outputEntry = display.entries().stream()
                .filter(entry -> entry.role() == RecipeIngredientRole.OUTPUT)
                .findFirst().orElseThrow();

        invokeAddEntry(recipeLayoutBuilder(capture), display,
                new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 0, outputEntry), false);

        assertThat(capture.tooltips).hasSize(1);
        List<FormattedText> outputTooltip = tooltipLines(capture.tooltips.getFirst());
        assertTranslatableTooltip(outputTooltip, "jei.mmcr.machine_recipe.item_count", "3");
        assertTranslatableTooltip(outputTooltip, "jei.mmcr.machine_recipe.output_chance", "25%");
    }

    @Test
    void itemInputSlotTooltipDescribesCountAndConsumeChance() throws Exception {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("item_input_tooltip_jei_slot"), MMCR.id("test_machine_name"), 40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.GOLD_INGOT), 3,
                        new DataComponentPredicateSet(Map.of()), 0.25F)), List.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        SlotCapture capture = new SlotCapture();
        JeiDisplayEntry inputEntry = display.entries().stream()
                .filter(entry -> entry.role() == RecipeIngredientRole.INPUT)
                .findFirst().orElseThrow();

        invokeAddEntry(recipeLayoutBuilder(capture), display,
                new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 0, inputEntry), true);

        assertThat(capture.tooltips).hasSize(1);
        List<FormattedText> inputTooltip = tooltipLines(capture.tooltips.getFirst());
        assertTranslatableTooltip(inputTooltip, "jei.mmcr.machine_recipe.item_count", "3");
        assertTranslatableTooltip(inputTooltip, "jei.mmcr.machine_recipe.consume_chance", "25%");
    }

    @Test
    void customItemStackAdapterDoesNotUseBuiltinItemSlotPath() throws Exception {
        MachineRecipeDisplay display = MachineRecipeDisplay.from(RecipeTestSupport.create(
                MMCR.id("custom_item_stack_slot"), MMCR.id("test_machine_name"), 40,
                List.of(), List.of()));
        ItemStack ingredient = new ItemStack(Items.GOLD_INGOT);
        JeiDisplayEntry customEntry = new JeiDisplayEntry(
                RecipeIngredientRole.OUTPUT, MMCR.id("custom_item_stack"),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, ingredient, 1, 1F, null, false);
        SlotCapture capture = new SlotCapture();

        invokeAddEntry(recipeLayoutBuilder(capture), display,
                new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 0, customEntry), false);

        assertThat(capture.itemStacks).isEmpty();
        assertThat(capture.added).singleElement().satisfies(arguments -> {
            assertThat(arguments[0]).isSameAs(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
            assertThat(arguments[1]).isSameAs(ingredient);
        });
    }

    @Test
    void emptyFluidInputDoesNotShiftFollowingFluidSlot() throws Exception {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("empty_fluid_before_valid_fluid"), MMCR.id("test_machine_name"), 40,
                List.of(
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(
                                HolderSet.emptyNamed(BuiltInRegistries.FLUID, FluidTags.WATER)), 100),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 200)),
                List.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        List<JeiDisplayEntry> fluidEntries = display.entries().stream()
                .filter(entry -> entry.ingredientType() == mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK)
                .toList();

        assertThat(fluidEntries).hasSize(2);
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);
        assertThat(layout.inputs().slots()).extracting(slot -> slot.entry().index())
                .containsExactly(0, 1);
        SlotCapture emptyCapture = new SlotCapture();
        invokeAddEntry(recipeLayoutBuilder(emptyCapture), display, layout.inputs().slots().get(0).entry(), true);
        assertThat(emptyCapture.added).isEmpty();

        SlotCapture capture = new SlotCapture();

        invokeAddEntry(recipeLayoutBuilder(capture), display, layout.inputs().slots().get(1).entry(), true);

        assertThat(capture.added).anySatisfy(arguments ->
                assertThat(arguments[0]).isSameAs(Fluids.WATER));
    }

    @Test
    void smartInterfaceRequirementsDoNotCreateJeiSlots() {
        var machineId = MMCR.id("jei_smart_interface_text_only");
        MachineDefinitions.register(MachineRegistration.builder(machineId).localizedName("Smart text only")
                .smartInterfaceType(new SmartInterfaceType("mode", 0F, 0)).build());
        MachineRequirement item = new ItemRequirement(RecipeModifier.IOType.INPUT,
                Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY);
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("smart_interface_text_only_recipe"), machineId, 40,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(item, SmartInterfaceRequirement.input("mode", 1F)));

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);

        assertThat(display.entries()).noneMatch(entry ->
                entry.typeId().equals(SmartInterfaceRequirement.TYPE.id()));
        assertThat(layout.inputs().slots()).singleElement()
                .extracting(slot -> slot.entry().kind())
                .isEqualTo(MachineRecipeLayout.Kind.ITEM);
    }

    @Test
    void displayFallsBackToBaseStackForRangeComponentPredicates() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("range_component_input_display"),
                MMCR.id("blast_furnace"),
                40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND_SWORD), 1,
                        new DataComponentPredicateSet(Map.of(DataComponents.MAX_STACK_SIZE,
                                ComponentPredicate.range(1, 4))), 1F)),
                List.of()
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        ItemStack input = display.itemInputs().getFirst().stacks().getFirst();
        assertThat(input.isComponentsPatchEmpty()).isTrue();
        assertThat(display.itemInputs().getFirst().hasUnexportedComponentConstraints()).isTrue();
    }

    @Test
    void displayDoesNotDereferenceUnboundTagInputs() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("tag_input_display"),
                MMCR.id("blast_furnace"),
                40,
                List.of(new MachineIngredient.ItemIngredient(
                        Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, ItemTags.LOGS)), 1)),
                List.of()
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.itemInputs()).singleElement().satisfies(input -> {
            assertThat(input.ingredient()).isNotNull();
            assertThat(input.stacks()).isEmpty();
        });
    }

    @Test
    void jeiEntriesKeepUnboundTagInputsSafe() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("tag_input_jei_display"),
                MMCR.id("blast_furnace"),
                40,
                List.of(new MachineIngredient.ItemIngredient(
                        Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, ItemTags.LOGS)), 1)),
                List.of()
        );

        assertThat(MachineRecipeDisplay.from(recipe).entries()).singleElement().satisfies(entry -> {
            assertThat(entry.ingredientType()).isSameAs(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
            assertThat(entry.ingredient()).isInstanceOf(List.class);
            assertThat((List<?>) entry.ingredient()).isEmpty();
        });
    }

    @Test
    void displayKeepsEveryResolvedTagItemForJeiCarousel() {
        Ingredient tag = Ingredient.of(HolderSet.direct(
                Items.OAK_LOG.builtInRegistryHolder(), Items.BIRCH_LOG.builtInRegistryHolder()));
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("resolved_tag_input_display"), MMCR.id("blast_furnace"), 40,
                List.of(new MachineIngredient.ItemIngredient(tag, 1)), List.of());

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.itemInputs()).singleElement().satisfies(input ->
                assertThat(input.stacks()).extracting(ItemStack::getItem)
                        .containsExactly(Items.OAK_LOG, Items.BIRCH_LOG));
    }

    @Test
    void displaysAllRecipesInDeterministicOrder() {
        RecipeRegistry.clearForTesting();
        MachineRecipe low = recipe("low", "blast_furnace", 0);
        MachineRecipe high = recipe("high", "blast_furnace", 10);
        MachineRecipe other = recipe("other", "other_machine", 5);
        RecipeRegistry.register(low);
        RecipeRegistry.register(other);
        RecipeRegistry.register(high);

        assertThat(MachineRecipeDisplays.all())
                .extracting(MachineRecipeDisplay::recipeId)
                .containsExactly(MMCR.id("high"), MMCR.id("low"), MMCR.id("other"));

        assertThat(MachineRecipeDisplays.byMachine())
                .containsOnlyKeys(MMCR.id("blast_furnace"), MMCR.id("other_machine"));
        assertThat(MachineRecipeDisplays.byMachine().get(MMCR.id("blast_furnace")))
                .extracting(MachineRecipeDisplay::recipeId)
                .containsExactly(MMCR.id("high"), MMCR.id("low"));
    }

    @Test
    void layoutPlacesFluidsBeforeItemsAndCapsOverflow() {
        MachineRecipeDisplay display = MachineRecipeDisplay.from(RecipeTestSupport.create(
                MMCR.id("jei_layout"),
                MMCR.id("blast_furnace"),
                20,
                List.of(
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 100),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.LAVA), 200),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.GOLD_INGOT), 3)
                ),
                List.of(
                        new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1),
                        new ItemStack(Holder.direct(Items.GOLD_NUGGET, DataComponentMap.EMPTY), 2)
                ),
                List.of(),
                0,
                1,
                true,
                List.of(new FluidStack(Fluids.WATER.builtInRegistryHolder(), 100))
        ));
        MachineRecipeDisplay overflow = MachineRecipeDisplay.from(RecipeTestSupport.create(
                MMCR.id("jei_overflow"),
                MMCR.id("blast_furnace"),
                20,
                IntStream.range(0, 33)
                        .<MachineIngredient>mapToObj(index -> new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1))
                        .toList(),
                List.of(),
                List.of(),
                0,
                1
        ));

        assertThat(MachineRecipeLayout.forDisplay(display, 4).inputs().slots()).hasSize(5);
        assertThat(MachineRecipeLayout.forDisplay(display, 4).inputs().slots())
                .extracting(slot -> slot.entry().kind())
                .startsWith(MachineRecipeLayout.Kind.FLUID, MachineRecipeLayout.Kind.FLUID);
        MachineRecipeLayout overflowLayout = MachineRecipeLayout.forDisplay(overflow, 4);
        assertThat(overflowLayout.inputs().slots()).hasSize(14);
        assertThat(overflowLayout.inputs().overflowSlot()).isNotNull();
        assertThat(overflowLayout.inputs().hiddenEntries()).hasSize(19);
    }

    @Test
    void outputOverflowNameFallsBackToItemDescriptionWhenHoverNameIsEmpty() {
        ItemStack stack = new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 3);

        assertThat(MachineRecipeCategory.outputStackName(stack).getString()).isNotEmpty();
    }

    @Test
    void displayExposesAdapterEntriesWithRecipeRoles() {
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe("jei_generic_entries", "blast_furnace", 0));

        assertThat(display.entries()).extracting(JeiDisplayEntry::role)
                .containsExactly(RecipeIngredientRole.INPUT, RecipeIngredientRole.OUTPUT);
    }

    @Test
    void levelRequirementCyclesEligibleLevelsEverySecond() {
        var typeId = MMCR.id("coil");
        var copperId = MMCR.id("copper");
        var ironId = MMCR.id("iron");
        var goldId = MMCR.id("gold");
        var diamondId = MMCR.id("diamond");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(typeId, Component.literal("Coils")));
        registerLevel(copperId, typeId, 0, Blocks.COPPER_BLOCK);
        registerLevel(ironId, typeId, 1, Blocks.IRON_BLOCK);
        registerLevel(goldId, typeId, 2, Blocks.GOLD_BLOCK);
        registerLevel(diamondId, typeId, 3, Blocks.DIAMOND_BLOCK);
        TestBootstrap.freezeRegistration();
        LevelRequirement requirement = new LevelRequirement(typeId, ironId);

        assertThat(MachineRecipeCategory.levelRequirement(requirement, 0).getString())
                .isEqualTo("Coils: Block of Ironjei.mmcr.machine_recipe.minimum_level");
        assertThat(MachineRecipeCategory.levelRequirement(requirement, 20).getString()).isEqualTo("Coils: Block of Gold");
        assertThat(MachineRecipeCategory.levelRequirement(requirement, 40).getString()).isEqualTo("Coils: Block of Diamond");
        assertThat(MachineRecipeCategory.levelRequirement(requirement, 60).getString()).isEqualTo("Coils: Block of Gold");
        assertThat(MachineRecipeCategory.levelRequirement(requirement, 80).getString())
                .isEqualTo("Coils: Block of Ironjei.mmcr.machine_recipe.minimum_level");
    }

    private static MachineRecipe recipe(String id, String machine, int priority) {
        return RecipeTestSupport.create(
                MMCR.id(id),
                MMCR.id(machine),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                List.of(),
                priority,
                1
        );
    }

    private static MachineRecipeDisplay hostDisplay(Set<Identifier> requiredHostIds) {
        return MachineRecipeDisplay.from(RecipeTestSupport.create(MMCR.id("jei_required_host_recipe"), MMCR.id("hosted_module"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(), false, List.of(), requiredHostIds));
    }

    private static Component hostRequirementArg(MachineRecipeDisplay display, long gameTime) {
        var contents = (TranslatableContents)
                MachineRecipeCategory.hostRequirementComponent(display, gameTime).getContents();
        return (Component) contents.getArgs()[0];
    }

    private static void registerHost(String path) {
        MachineDefinitions.register(MachineRegistration.builder(MMCR.id(path)).host(MMCR.id("hosted_module")).build());
    }

    private static MachineRecipeDisplay displayFor(SmartInterfaceRequirement requirement, Identifier machineId) {
        return MachineRecipeDisplay.from(RecipeTestSupport.create(MMCR.id("interface_jei_recipe"), machineId, 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(requirement)));
    }

    private static void registerLevel(Identifier id, Identifier typeId,
                                      int priority, Block block) {
        TestBootstrap.registerLevel(new MachineLevel(id, typeId, priority,
                new BlockPredicate.OfBlockState(block.defaultBlockState()),
                new ItemStack(Holder.direct(block.asItem(), DataComponentMap.EMPTY)), LevelModifier.IDENTITY));
    }

    private static ItemStack namedSharpnessFourSword() {
        return namedSharpnessSword(4, "Better钻石剑", VanillaRegistries.createLookup());
    }

    private static ItemStack namedSharpnessSword(int level, String name, HolderLookup.Provider lookup) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        if (name != null) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(sharpnessKey()), level);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }

    private static ResourceKey<Enchantment> sharpnessKey() {
        return ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.parse("minecraft:sharpness"));
    }

    private static void bindItemComponents(Item... items) {
        for (var item : items) item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    private static IGuiHelper guiHelper() {
        return (IGuiHelper) Proxy.newProxyInstance(
                MachineRecipeDisplayTest.class.getClassLoader(), new Class<?>[]{IGuiHelper.class},
                (proxy, method, arguments) -> method.getName().equals("getSlotDrawable")
                        ? staticDrawable() : method.getName().equals("createDrawableItemLike") ? drawable() : null);
    }

    private static IDrawable drawable() {
        return (IDrawable) Proxy.newProxyInstance(
                MachineRecipeDisplayTest.class.getClassLoader(), new Class<?>[]{IDrawable.class},
                (proxy, method, arguments) -> null);
    }

    private static IDrawableStatic staticDrawable() {
        return (IDrawableStatic) Proxy.newProxyInstance(
                MachineRecipeDisplayTest.class.getClassLoader(), new Class<?>[]{IDrawableStatic.class},
                (proxy, method, arguments) -> null);
    }

    private static IRecipeLayoutBuilder recipeLayoutBuilder(SlotCapture capture) {
        return (IRecipeLayoutBuilder) Proxy.newProxyInstance(
                MachineRecipeDisplayTest.class.getClassLoader(), new Class<?>[]{IRecipeLayoutBuilder.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addSlot")
                            || method.getName().equals("addInputSlot")
                            || method.getName().equals("addOutputSlot")) {
                        return recipeSlotBuilder(capture);
                    }
                    return null;
                });
    }

    private static void invokeAddEntry(IRecipeLayoutBuilder builder, MachineRecipeDisplay display,
                                       MachineRecipeLayout.EntryPlan entry, boolean input) throws Exception {
        Method method = MachineRecipeCategory.class.getDeclaredMethod("addEntry", IRecipeLayoutBuilder.class,
                MachineRecipeDisplay.class, MachineRecipeLayout.SlotPlan.class, boolean.class);
        method.setAccessible(true);
        method.invoke(null, builder, display, new MachineRecipeLayout.SlotPlan(entry, 0, 0), input);
    }

    private static Object recipeSlotBuilder(SlotCapture capture) {
        return Proxy.newProxyInstance(
                MachineRecipeDisplayTest.class.getClassLoader(),
                new Class<?>[]{mezz.jei.api.gui.builder.IRecipeSlotBuilder.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addItemStacks")) {
                        capture.itemStacks.add((List<ItemStack>) arguments[0]);
                    } else if (method.getName().equals("add")) {
                        capture.added.add(arguments.clone());
                    } else if (method.getName().equals("addRichTooltipCallback")) {
                        capture.tooltips.add((IRecipeSlotRichTooltipCallback) arguments[0]);
                    }
                    return method.getReturnType().isAssignableFrom(
                            mezz.jei.api.gui.builder.IRecipeSlotBuilder.class) ? proxy : null;
                });
    }

    private static List<FormattedText> tooltipLines(IRecipeSlotRichTooltipCallback callback) {
        List<FormattedText> lines = new ArrayList<>();
        ITooltipBuilder tooltip = (ITooltipBuilder) Proxy.newProxyInstance(
                MachineRecipeDisplayTest.class.getClassLoader(), new Class<?>[]{ITooltipBuilder.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("add") && arguments.length == 1
                            && arguments[0] instanceof FormattedText text) {
                        lines.add(text);
                    }
                    return null;
                });
        callback.onRichTooltip(null, tooltip);
        return lines;
    }

    private static void assertTranslatableTooltip(List<FormattedText> lines, String key, String argument) {
        assertThat(lines).anySatisfy(line -> {
            assertThat(line).isInstanceOf(Component.class);
            Component component = (Component) line;
            assertThat(component.getContents()).isInstanceOf(TranslatableContents.class);
            TranslatableContents contents = (TranslatableContents) component.getContents();
            assertThat(contents.getKey()).isEqualTo(key);
            assertThat(contents.getArgs()[0]).isEqualTo(argument);
        });
    }

    private static final class SlotCapture {
        private final List<List<ItemStack>> itemStacks = new ArrayList<>();
        private final List<Object[]> added = new ArrayList<>();
        private final List<IRecipeSlotRichTooltipCallback> tooltips = new ArrayList<>();
    }
}
