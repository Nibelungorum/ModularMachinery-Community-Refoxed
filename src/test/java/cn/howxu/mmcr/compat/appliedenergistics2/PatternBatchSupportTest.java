package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningBridgeBootstrap;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.CustomRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import com.mojang.serialization.MapCodec;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport.Safety;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchBinding;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Binding proofs use exact resources and effective single-lane capacity.
 * @author howxu <dev@howxu.cn>
 */
class PatternBatchSupportTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
        TestBootstrap.registerType(new LevelType(MMCR.id("batch_profile_coil"), Component.empty()));
        TestBootstrap.registerLevel(new MachineLevel(MMCR.id("batch_profile_iron"), MMCR.id("batch_profile_coil"), 1,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY, ModifierDefinition.EMPTY));
    }

    @Test
    void patternUnitAndRecipeCraftsAreNotInterchangeable() {
        var recipe = recipe("two_crafts", List.of(input()), List.of(output(1)));
        var effective = effective(recipe, 256);
        var original = pattern(AEItemKey.of(Items.IRON_INGOT), 20, List.of(stack(Items.GOLD_INGOT, 2)));
        var profile = PatternBatchSupport.inspectRecipe(recipe, effective, original);
        assertThat(profile.binding().recipeCraftsPerPattern()).isEqualTo(2L);
        assertThat(profile.maxMultiplier()).isEqualTo(128L);
        assertThat(PatternBatchSupport.matches(profile.binding(), recipe, effective, original)).isTrue();
        assertThat(PatternBatchSupport.matches(new PatternBatchBinding(MMCR.id("other_pool"), recipe.id(), 2),
                recipe, effective, original)).isFalse();
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective,
                pattern(AEItemKey.of(Items.DIAMOND), 20, original.getOutputs()))).isNull();
    }

    @Test
    void componentIdentityAndConflictingByproductRatiosCannotBind() {
        var recipe = recipe("components", List.of(input()), List.of(output(1)));
        var named = new ItemStack(Items.GOLD_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("different component"));
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective(recipe, 256),
                pattern(AEItemKey.of(Items.IRON_INGOT), 1, List.of(new GenericStack(AEItemKey.of(named), 1))))).isNull();
        var byproducts = recipe("byproducts", List.of(input()), List.of(output(1),
                new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND), 1F)));
        assertThat(PatternBatchSupport.inspectRecipe(byproducts, effective(byproducts, 256),
                pattern(AEItemKey.of(Items.IRON_INGOT), 2,
                        List.of(stack(Items.GOLD_INGOT, 2), stack(Items.DIAMOND, 3))))).isNull();
    }

    @Test
    void conflictingByproductRatiosCannotBind() {
        var recipe = recipe("byproducts", List.of(input()), List.of(output(1),
                new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND), 1F)));
        var effective = effective(recipe, 256L);
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective,
                pattern(AEItemKey.of(Items.IRON_INGOT), 10, List.of(stack(Items.GOLD_INGOT, 2), stack(Items.DIAMOND, 3))))).isNull();
    }

    @ParameterizedTest
    @MethodSource("runtimeRequirements")
    void runtimeRequirementsDoNotDisableBulkInputDelivery(MachineRequirement requirement) {
        var guarded = recipe("guarded", List.of(input(), requirement), List.of(output(1)));
        var pattern = pattern(AEItemKey.of(Items.IRON_INGOT), 1, List.of(stack(Items.GOLD_INGOT, 1)));
        var profile = PatternBatchSupport.inspectRecipe(guarded, effective(guarded, 4), pattern);
        assertThat(profile.safety()).isEqualTo(Safety.PROVEN_LINEAR);
        assertThat(profile.maxMultiplier()).isEqualTo(4L);
    }

    static Stream<MachineRequirement> runtimeRequirements() {
        return Stream.of(new LevelRequirement(MMCR.id("batch_profile_coil"), MMCR.id("batch_profile_iron")), new StageRequirement(2),
                SmartInterfaceRequirement.input("temperature", 10F, 20F),
                SmartInterfaceRequirement.output("temperature", 42F));
    }

    @ParameterizedTest
    @CsvSource({"item,0,PROVEN_LINEAR", "item,0.5,UNPROVEN", "item,1,PROVEN_LINEAR",
            "fluid,0,PROVEN_LINEAR", "fluid,0.5,UNPROVEN", "fluid,1,PROVEN_LINEAR",
            "chemical,0,PROVEN_LINEAR", "chemical,0.5,UNPROVEN", "chemical,1,PROVEN_LINEAR",
            "source,0,PROVEN_LINEAR", "source,0.5,UNPROVEN", "source,1,PROVEN_LINEAR",
            "mana,0,PROVEN_LINEAR", "mana,0.5,UNPROVEN", "mana,1,PROVEN_LINEAR",
            "air,0,PROVEN_LINEAR", "air,0.5,UNPROVEN", "air,1,PROVEN_LINEAR"})
    void onlyIntermediateInputConsumptionProbabilitiesDisableBatching(String family, float consumeChance, Safety safety) {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(LoadedChemicalRequirement.TYPE);
            RequirementHandlerRegistry.register(SourceRequirement.TYPE);
            RequirementHandlerRegistry.register(ManaRequirement.TYPE);
            RequirementHandlerRegistry.register(AirRequirement.TYPE);
            MachineRequirement requirement = switch (family) {
                case "item" -> new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY,
                        1F, List.of(), DataComponentPredicateSet.EMPTY, consumeChance);
                case "fluid" -> new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1, FluidStack.EMPTY,
                        1F, List.of(), consumeChance);
                case "source" -> SourceRequirement.input(1L, consumeChance);
                case "mana" -> ManaRequirement.input(1L, consumeChance);
                case "air" -> AirRequirement.input(1L, 0F, consumeChance);
                default -> new LoadedChemicalRequirement(IOType.INPUT, ChemicalIngredient.chemical(MMCR.id("batch_chemical"), 1),
                        1F, List.of(), consumeChance);
            };
            assertThat(PatternBatchSupport.supportsBatchInputs(List.of(requirement))).isEqualTo(safety == Safety.PROVEN_LINEAR);
            // Exact chemical keys require Mekanism's runtime registry; the native GameTest covers that binding.
            if (family.equals("chemical")) return;
            var requirements = family.equals("item") || family.equals("fluid") ? List.of(requirement) : List.of(input(), requirement);
            var recipe = recipe("input_probability", requirements, List.of(output(1)));
            var effective = effective(recipe, 4);
            var key = family.equals("fluid") ? AEFluidKey.of(Fluids.WATER) : AEItemKey.of(Items.IRON_INGOT);
            var profile = PatternBatchSupport.inspectRecipe(recipe, effective,
                    pattern(key, 1, List.of(stack(Items.GOLD_INGOT, 1))));
            assertThat(profile.safety()).isEqualTo(safety);
            assertThat(profile.maxMultiplier()).isEqualTo(4L);
        }
    }

    @ParameterizedTest
    @CsvSource({"item,0", "item,0.5", "item,1", "fluid,0", "fluid,0.5", "fluid,1"})
    void outputProbabilityDoesNotDisableBatchingOrChangeNominalCraftRatio(String family, float chance) {
        MachineOutput output = family.equals("item") ? new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), chance)
                : new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 1), chance);
        var recipe = recipe("output_probability", List.of(input()), List.of(output));
        var effective = effective(recipe, 8);
        var key = family.equals("item") ? AEItemKey.of(Items.GOLD_INGOT) : AEFluidKey.of(Fluids.LAVA);
        var profile = PatternBatchSupport.inspectRecipe(recipe, effective, pattern(AEItemKey.of(Items.IRON_INGOT), 2, List.of(new GenericStack(key, 2L))));
        assertThat(profile).isNotNull();
        assertThat(profile.safety()).isEqualTo(Safety.PROVEN_LINEAR);
        assertThat(profile.binding().recipeCraftsPerPattern()).isEqualTo(2L);
        assertThat(profile.maxMultiplier()).isEqualTo(4L);
    }

    @ParameterizedTest
    @CsvSource({"HIGH_VOLTAGE,0", "HIGH_VOLTAGE,0.5", "HIGH_VOLTAGE,1",
            "EXTREME_HIGH_VOLTAGE,0", "EXTREME_HIGH_VOLTAGE,0.5", "EXTREME_HIGH_VOLTAGE,1"})
    void lightningInputsAndProbabilisticOutputsKeepTheirTierAndBatchBinding(LightningTier tier, float chance) {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            LightningRecipeTypes.register();
            LightningBridgeBootstrap.installForTesting(new LoadedLightningBridge());
            var outputTier = tier == LightningTier.HIGH_VOLTAGE ? LightningTier.EXTREME_HIGH_VOLTAGE : LightningTier.HIGH_VOLTAGE;
            var recipe = recipe("lightning_probability", List.of(LightningRequirement.input(tier, 1L)),
                    List.of(new LightningOutput(outputTier, 1L, chance)));
            var effective = effective(recipe, 8);
            var profile = PatternBatchSupport.inspectRecipe(recipe, effective,
                    pattern(LoadedLightningBridge.key(tier), 2L, List.of(new GenericStack(LoadedLightningBridge.key(outputTier), 2L))));
            assertThat(profile).isNotNull();
            assertThat(profile.safety()).isEqualTo(Safety.PROVEN_LINEAR);
            assertThat(profile.binding().recipeCraftsPerPattern()).isEqualTo(2L);
            assertThat(profile.maxMultiplier()).isEqualTo(4L);
            assertThat(CraftingRuntime.samePromisedOutputs(effective.outputs(),
                    List.of(new LightningOutput(outputTier, 1L, chance)), 4L)).isTrue();
            assertThat(CraftingRuntime.samePromisedOutputs(effective.outputs(),
                    List.of(new LightningOutput(outputTier, 1L, chance == 1F ? 0.5F : 1F)), 4L)).isFalse();
        } finally {
            LightningBridgeBootstrap.resetForTesting();
        }
    }

    @Test
    void customRequirementIsValidatedByItsRuntimeHandlerRatherThanAProviderWhitelist() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(ExternalCondition.TYPE);
            var recipe = recipe("custom_condition", List.of(input(), new ExternalCondition()), List.of(output(1)));
            var effective = effective(recipe, 4L);
            var profile = PatternBatchSupport.inspectRecipe(recipe, effective,
                    pattern(AEItemKey.of(Items.IRON_INGOT), 1, List.of(stack(Items.GOLD_INGOT, 1))));
            assertThat(profile.safety()).isEqualTo(Safety.PROVEN_LINEAR);
            assertThat(profile.maxMultiplier()).isEqualTo(4L);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record ExternalCondition() implements CustomRequirement {
        private static final RequirementType<ExternalCondition> TYPE = new RequirementType.Definition<>(
                MMCR.id("batch_external_condition"), MapCodec.unit(ExternalCondition::new),
                (requirement, capabilities, context) -> new RequirementPlan(context.requirementIndex(),
                        context.requestedParallelism(), List.of(), null), ignored -> new ExternalCondition());
        @Override public IOType io() { return IOType.INPUT; }
        @Override public RequirementType<ExternalCondition> type() { return TYPE; }
    }

    @Test
    void sameKeyTotalsBoundMultipliersWithoutFloatQuantization() {
        var recipe = recipe("long_inputs", List.of(input()), List.of(output(1)));
        var effective = effective(recipe, Integer.MAX_VALUE);
        var huge = pattern(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE / 2 + 1,
                List.of(stack(Items.GOLD_INGOT, 1)));
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective, huge).maxMultiplier()).isOne();
        var small = pattern(AEItemKey.of(Items.IRON_INGOT), 1, List.of(stack(Items.GOLD_INGOT, 1)));
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective, small).maxMultiplier()).isEqualTo((long) Integer.MAX_VALUE);
    }

    private static MachineRecipe recipe(String name, List<MachineRequirement> requirements, List<MachineOutput> outputs) {
        return MachineRecipe.fromCanonical(MMCR.id("batch_support_" + name), MMCR.id("test_cube"), 20,
                requirements, outputs, List.of(), 0, 1, false, true, false, Set.of());
    }

    private static EffectiveRecipe effective(MachineRecipe recipe, long maximum) {
        return new EffectiveRecipe(recipe, recipe.requirements(), recipe.machineOutputs(), 20, maximum,
                4, 4, true, 0, 0, 0, 0);
    }

    private static ItemRequirement input() {
        return new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY);
    }

    private static MachineOutput output(int amount) {
        return new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, amount), 1F);
    }

    private static GenericStack stack(Item item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }

    private static IPatternDetails pattern(AEKey key, long amount, List<GenericStack> outputs) {
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            @Override public List<GenericStack> getOutputs() { return outputs; }
            @Override public IInput[] getInputs() {
                return new IInput[]{new IInput() {
                    @Override public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(key, amount)}; }
                    @Override public long getMultiplier() { return 1; }
                    @Override public boolean isValid(AEKey input, Level level) { return key.equals(input); }
                    @Override public AEKey getRemainingKey(AEKey template) { return null; }
                }};
            }
        };
    }
}
