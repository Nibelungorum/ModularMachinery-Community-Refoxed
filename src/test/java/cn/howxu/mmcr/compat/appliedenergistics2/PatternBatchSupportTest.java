package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
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
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
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
import java.util.Optional;
import java.util.function.Predicate;
import appeng.api.stacks.AEKeyType;
import cn.howxu.mmcr.internal.runtime.CrystalShard;
import cn.howxu.mmcr.compat.jei.*;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.jei.PatternTransfer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamily;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2KeyAdapter;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Binding and capacity proofs use effective requirements, not output names or thread multiplication.
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
    void profileUsesEffectiveRecipeAndOneLaneOnly() {
        var recipe = recipe("profile", List.of(input()), List.of(output(1)), true);
        var pattern = pattern(1, List.of(stack(Items.GOLD_INGOT, 1)));
        var snapshot = snapshot(256L);
        var resolver = new EffectiveRecipeResolver();
        var effective = resolver.resolve(recipe, snapshot, List.of());
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective, pattern).maxMultiplier()).isEqualTo(256L);
        var disabled = resolver.resolve(recipe, snapshot, List.of(new MachineModifier.Parallelized(false)));
        assertThat(PatternBatchSupport.inspectRecipe(recipe, disabled, pattern).maxMultiplier()).isOne();
        var serial = recipe("serial", List.of(input()), List.of(output(1)), false);
        assertThat(PatternBatchSupport.inspectRecipe(serial, resolver.resolve(serial, snapshot, List.of()), pattern)
                .maxMultiplier()).isOne();
        var enabled = resolver.resolve(serial, snapshot, List.of(new MachineModifier.Parallelized(true)));
        assertThat(PatternBatchSupport.inspectRecipe(serial, enabled, pattern).maxMultiplier()).isEqualTo(256L);
    }

    @Test
    void patternUnitAndRecipeCraftsAreNotInterchangeable() {
        var recipe = recipe("two_crafts", List.of(input()), List.of(output(1)), true);
        var effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(256L), List.of());
        var original = pattern(20, List.of(stack(Items.GOLD_INGOT, 2)));
        var profile = PatternBatchSupport.inspectRecipe(recipe, effective, original);
        assertThat(profile.binding().recipeCraftsPerPattern()).isEqualTo(2L);
        assertThat(profile.maxMultiplier()).isEqualTo(128L);
        assertThat(PatternBatchSupport.matches(profile.binding(), recipe, effective, original)).isTrue();
        assertThat(PatternBatchSupport.matches(new PatternBatchBinding(MMCR.id("other_pool"), recipe.id(), 2L),
                recipe, effective, original)).isFalse();
        var wrongInput = patternFor(AEItemKey.of(Items.DIAMOND), 20L, original.getOutputs());
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective, wrongInput)).isNull();
        var named = new ItemStack(Items.GOLD_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("different component"));
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective,
                pattern(20, List.of(new GenericStack(AEItemKey.of(named), 2L))))).isNull();
    }

    @Test
    void conflictingByproductRatiosCannotBind() {
        var recipe = recipe("byproducts", List.of(input()), List.of(output(1),
                new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND), 1F)), true);
        var effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(256L), List.of());
        assertThat(PatternBatchSupport.inspectRecipe(recipe, effective,
                pattern(10, List.of(stack(Items.GOLD_INGOT, 2), stack(Items.DIAMOND, 3))))).isNull();
    }

    @ParameterizedTest
    @MethodSource("runtimeRequirements")
    void runtimeRequirementsDoNotDisableBulkInputDelivery(MachineRequirement requirement) {
        var guarded = recipe("guarded", List.of(input(), requirement), List.of(output(1)), true);
        var resolver = new EffectiveRecipeResolver();
        var pattern = pattern(1, List.of(stack(Items.GOLD_INGOT, 1)));
        var profile = PatternBatchSupport.inspectRecipe(guarded, resolver.resolve(guarded, snapshot(4), List.of()), pattern);
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
            "chemical,0,PROVEN_LINEAR", "chemical,0.5,UNPROVEN", "chemical,1,PROVEN_LINEAR"})
    void onlyIntermediateInputConsumptionProbabilitiesDisableBatching(String family, float consumeChance, Safety safety) {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(LoadedChemicalRequirement.TYPE);
            MachineRequirement requirement = switch (family) {
                case "item" -> new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY,
                        1F, List.of(), DataComponentPredicateSet.EMPTY, consumeChance);
                case "fluid" -> new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1, FluidStack.EMPTY,
                        1F, List.of(), consumeChance);
                default -> new LoadedChemicalRequirement(IOType.INPUT, ChemicalIngredient.chemical(MMCR.id("batch_chemical"), 1),
                        1F, List.of(), consumeChance);
            };
            var requirements = family.equals("chemical") ? List.of(input(), requirement) : List.of(requirement);
            var recipe = recipe("input_probability", requirements, List.of(output(1)), true);
            var effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(4), List.of());
            var key = family.equals("fluid") ? AEFluidKey.of(Fluids.WATER) : AEItemKey.of(Items.IRON_INGOT);
            var profile = PatternBatchSupport.inspectRecipe(recipe, effective,
                    patternFor(key, 1, List.of(stack(Items.GOLD_INGOT, 1))));
            assertThat(profile.safety()).isEqualTo(safety);
            assertThat(profile.maxMultiplier()).isEqualTo(4L);
        }
    }

    @ParameterizedTest
    @CsvSource({"item,0", "item,0.5", "item,1", "fluid,0", "fluid,0.5", "fluid,1"})
    void outputProbabilityDoesNotDisableBatchingOrChangeNominalCraftRatio(String family, float chance) {
        MachineOutput output = family.equals("item") ? new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), chance)
                : new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 1), chance);
        var recipe = recipe("output_probability", List.of(input()), List.of(output), true);
        var effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(8), List.of());
        var key = family.equals("item") ? AEItemKey.of(Items.GOLD_INGOT) : AEFluidKey.of(Fluids.LAVA);
        var profile = PatternBatchSupport.inspectRecipe(recipe, effective, pattern(2, List.of(new GenericStack(key, 2L))));
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
                    List.of(new LightningOutput(outputTier, 1L, chance)), true);
            var effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(8), List.of());
            var adapter = LoadedLightningBridge.FAMILY.adapter();
            var profile = PatternBatchSupport.inspectRecipe(recipe, effective,
                    patternFor(adapter.toKey(tier), 2L, List.of(new GenericStack(adapter.toKey(outputTier), 2L))));
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
    void shard_pattern_requires_an_installed_adapter_and_matches_the_exact_color_in_both_directions() {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            LightningBridgeBootstrap.installForTesting(LightningBridgeBootstrap.selectForTesting(false));
            RequirementHandlerRegistry.register(CrystalShard.TYPE);
            OutputRegistry.register(CrystalShard.Output.TYPE);
            var amber = new CrystalShard.Output("amber", 3, 0.25F);
            var shard = new CrystalShard(IOType.INPUT, "blue", 1, 1F);
            var recipe = recipe("shard_pattern", List.of(shard), List.of(amber), true);
            var effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(8), List.of());
            // Borrow two real non-item keys in this fixture; the production lightning adapter is not installed.
            var nativeKeys = LoadedLightningBridge.FAMILY.adapter();
            var blueKey = nativeKeys.toKey(LightningTier.HIGH_VOLTAGE);
            var amberKey = nativeKeys.toKey(LightningTier.EXTREME_HIGH_VOLTAGE);
            var promised = List.of(new GenericStack(amberKey, 6L));
            JeiIngredientAdapterRegistry.register(new JeiIngredientAdapter() {
                private final IIngredientType<String> type = () -> String.class;
                @Override public net.minecraft.resources.Identifier typeId() { return shard.type().id(); }
                @Override public IIngredientType<?> ingredientType() { return type; }
                @Override public String quantityText(long count) { return count + " shards"; }
                @Override public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
                    return Optional.of(new JeiDisplayEntry(entry.role(), typeId(), type, ((CrystalShard) entry.value()).color(),
                            entry.amount(), entry.chance(), null, false));
                }
                @Override public Optional<IRecipeTransferHandler<?, ?>> transferHandler() { return Optional.empty(); }
            });
            var display = MachineRecipeDisplay.from(recipe);
            var entry = MachineRecipeLayout.forDisplay(display, 4).outputs().slots().getFirst().entry().displayEntry();
            assertThat(entry.role()).isEqualTo(RecipeIngredientRole.OUTPUT);
            assertThat(entry.chance()).isEqualTo(0.25F);
            assertThat(entry.count()).isEqualTo(3L);
            assertThat(JeiIngredientAdapterRegistry.get(entry.typeId()).orElseThrow().quantityText(entry.count())).isEqualTo("3 shards");
            assertThat(PatternTransfer.prepare(display, List.of(), List.of()).unsupported()).isEqualTo(shard.type().id());
            assertThat(PatternBatchSupport.inspectRecipe(recipe, effective, patternFor(blueKey, 2, promised))).isNull();
            AE2ResourceFamilies.resetForTesting();
            var adapter = new AE2KeyAdapter<String>() {
                @Override public AEKeyType keyType() { return nativeKeys.keyType(); }
                @Override public Class<String> resourceType() { return String.class; }
                @Override public AEKey toKey(String color) { return color.equals("blue") ? blueKey : amberKey; }
                @Override public Optional<String> toResource(AEKey key) {
                    return key.equals(blueKey) ? Optional.of("blue") : key.equals(amberKey) ? Optional.of("amber") : Optional.empty();
                }
                @Override public MachineOutput patternOutput(String color, long amount) {
                    return amount > Integer.MAX_VALUE ? null : new CrystalShard.Output(color, (int) amount, 1F);
                }
                @Override public String patternResource(MachineOutput value) {
                    return value instanceof CrystalShard.Output out ? out.color() : null;
                }
                @Override public GenericStack exactJeiStack(MachineRequirement value) {
                    return value instanceof CrystalShard r ? new GenericStack(toKey(r.color()), r.amount()) : null;
                }
                @Override public Predicate<String> patternInput(MachineRequirement value) {
                    return value instanceof CrystalShard r && r.io().isInput() ? r.color()::equals : null;
                }
            };
            var family = CrystalShard.TYPE.id();
            AE2ResourceFamilies.register(new AE2ResourceFamily<>(new CapabilityType(family), adapter,
                    new PortFamilyDescriptor(family, cn.howxu.mmcr.util.IOType.INPUT, 0, List.of()),
                    new PortFamilyDescriptor(family, cn.howxu.mmcr.util.IOType.OUTPUT, 0, List.of()),
                    inv -> { throw new AssertionError(); }, (net, keys) -> { throw new AssertionError(); },
                    (host, storage, io, expose) -> { throw new AssertionError(); }));
            var transfer = PatternTransfer.prepare(display, List.of(), List.of());
            assertThat(transfer.fits(1, 1)).isTrue();
            assertThat(transfer.fits(1, 0)).isFalse();
            assertThat(transfer.outputs()).containsExactly(new GenericStack(amberKey, 3L));
            var profile = PatternBatchSupport.inspectRecipe(recipe, effective, patternFor(blueKey, 2, promised));
            assertThat(profile.binding().recipeCraftsPerPattern()).isEqualTo(2);
            assertThat(PatternBatchSupport.inspectRecipe(recipe, effective, patternFor(amberKey, 2, promised))).isNull();
            assertThat(AE2ResourceFamilies.patternOutput(promised.getFirst())).contains(new CrystalShard.Output("amber", 6, 1F));
        } finally {
            LightningBridgeBootstrap.resetForTesting();
        }
    }

    @Test
    void customRequirementIsValidatedByItsRuntimeHandlerRatherThanAProviderWhitelist() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(ExternalCondition.TYPE);
            var recipe = recipe("custom_condition", List.of(input(), new ExternalCondition()), List.of(output(1)), true);
            var effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(4L), List.of());
            var profile = PatternBatchSupport.inspectRecipe(recipe, effective,
                    pattern(1, List.of(stack(Items.GOLD_INGOT, 1))));
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
        var recipe = recipe("long_inputs", List.of(input()), List.of(output(1)), true);
        var effective = new EffectiveRecipeResolver().resolve(recipe, snapshot(Integer.MAX_VALUE), List.of());
        var original = patternFor(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE / 2L + 1L,
                List.of(stack(Items.GOLD_INGOT, 1)));
        var profile = PatternBatchSupport.inspectRecipe(recipe, effective, original);
        assertThat(profile.maxMultiplier()).isOne();
        var small = PatternBatchSupport.inspectRecipe(recipe, effective, pattern(1, List.of(stack(Items.GOLD_INGOT, 1))));
        assertThat(small.maxMultiplier()).isEqualTo((long) Integer.MAX_VALUE);
    }

    private static MachineRecipe recipe(String name, List<MachineRequirement> requirements,
                                         List<MachineOutput> outputs, boolean parallelized) {
        List<MachineRequirement> canonical = new ArrayList<>(requirements);
        for (MachineOutput output : outputs) canonical.add(OutputRegistry.tryToRequirement(output, List.of()));
        return MachineRecipe.fromCanonical(MMCR.id(name), MMCR.id("test_cube"), 20, canonical,
                outputs, List.of(), 0, 1, false, parallelized, false, Set.of());
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

    private static IPatternDetails pattern(long inputAmount, List<GenericStack> outputs) {
        return patternFor(AEItemKey.of(Items.IRON_INGOT), inputAmount, outputs);
    }

    private static IPatternDetails patternFor(AEKey inputKey, long amount, List<GenericStack> outputs) {
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            @Override public List<GenericStack> getOutputs() { return outputs; }
            @Override public IInput[] getInputs() {
                return new IInput[]{new IInput() {
                    @Override public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(inputKey, amount)}; }
                    @Override public long getMultiplier() { return 1L; }
                    @Override public boolean isValid(AEKey input, Level level) { return inputKey.equals(input); }
                    @Override public AEKey getRemainingKey(AEKey template) { return null; }
                }};
            }
        };
    }

    private static ControllerRuntimeSnapshot snapshot(long parallelism) {
        var source = RuntimeTestFixtures.controller(MMCR.id("test_cube")).currentRuntimeSnapshot();
        return new ControllerRuntimeSnapshot(source.structure(), source.capabilityVersion(), source.modifierVersion(),
                source.stateVersion(), source.foundModifiers(), source.foundLevels(), source.linkedPortPositions(),
                source.moduleConnectionStatus(), source.installedModuleCount(), source.crafting(),
                new FactorySnapshot(true, false, List.of(), 4, 0, parallelism, false, List.of(), "", 0, null, List.of(), 0, 1),
                source.componentPresentations(), source.capabilityPresentations(), source.foundLevelIds(), source.machineId(),
                source.machineName(), source.controllerRole(), true, true, 0, parallelism, parallelism);
    }
}
