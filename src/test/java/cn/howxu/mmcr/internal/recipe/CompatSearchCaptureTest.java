package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirementHandler;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortCapability;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortStorage;
import cn.howxu.mmcr.compat.botania.BotaniaRecipeTypes;
import cn.howxu.mmcr.compat.botania.ManaPortCapability;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirementHandler;
import cn.howxu.mmcr.compat.botania.ManaStorage;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Searches use captured values, preserving native compatibility semantics.
 * @author howxu <dev@howxu.cn>
 */
class CompatSearchCaptureTest {
    private ServerThreadTestScope server;
    private RequirementHandlerRegistry.TestScope requirements;
    private OutputRegistry.TestScope outputs;
    private boolean sourceNative;
    private boolean manaNative;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void setup() throws Exception {
        server = ServerThreadTestScope.open();
        requirements = RequirementHandlerRegistry.openTestScope();
        outputs = OutputRegistry.openTestScope();
        sourceNative = SourceRequirement.supportsSearchSnapshot();
        manaNative = ManaRequirement.supportsSearchSnapshot();
        ArsNouveauRecipeTypes.register();
        BotaniaRecipeTypes.register();
        SourceRequirement.installHandler(new SourceRequirementHandler());
        ManaRequirement.installHandler(new ManaRequirementHandler());
    }

    @AfterEach
    void cleanup() throws Exception {
        if (sourceNative) SourceRequirement.installHandler(new SourceRequirementHandler());
        else SourceRequirement.installUnavailableHandler();
        if (manaNative) ManaRequirement.installHandler(new ManaRequirementHandler());
        else ManaRequirement.installUnavailableHandler();
        outputs.close();
        requirements.close();
        server.close();
    }

    @Test
    void mixedScalarsAndSharedStorageMatchNativeParallelismWithoutMutation() {
        var source = new SourcePortStorage(() -> { });
        source.setAmount(100L);
        var mana = new ManaStorage(() -> { });
        mana.setAmount(90L);
        var capabilities = capabilities(new SourcePortCapability(null, source, IOType.INPUT),
                new SourcePortCapability(null, source, IOType.INPUT),
                new ManaPortCapability(null, mana, IOType.INPUT));
        assertEquivalent(capabilities, recipe(List.of(SourceRequirement.input(20L), SourceRequirement.input(10L),
                ManaRequirement.input(25L)), false));
        assertThat(source.amount()).isEqualTo(100);
        assertThat(mana.amount()).isEqualTo(90);
    }

    @Test
    void retainedInputsCheckFullPresenceButDoNotConsumeSharedBudgets() {
        var source = new SourcePortStorage(() -> { });
        source.setAmount(100L);
        var mana = new ManaStorage(() -> { });
        mana.setAmount(100L);
        var capabilities = capabilities(new SourcePortCapability(null, source, IOType.INPUT),
                new ManaPortCapability(null, mana, IOType.INPUT));
        assertEquivalent(capabilities, recipe(List.of(SourceRequirement.input(30L, 0F), SourceRequirement.input(20L),
                ManaRequirement.input(30L, 0F), ManaRequirement.input(20L)), false));
        assertEquivalent(capabilities, recipe(List.of(SourceRequirement.input(101L, 0F)), false));
        assertEquivalent(capabilities, recipe(List.of(ManaRequirement.input(101L, 0F)), false));
    }

    @Test
    void outputCapacityMissingPortsAndPartialDiscardMatchNativeSemantics() {
        var source = new SourcePortStorage(() -> { });
        source.setAmount(source.capacity());
        var mana = new ManaStorage(() -> { });
        mana.setAmount(mana.capacity());
        var capabilities = capabilities(new SourcePortCapability(null, source, IOType.OUTPUT),
                new ManaPortCapability(null, mana, IOType.OUTPUT));
        for (boolean partial : List.of(false, true)) {
            assertEquivalent(capabilities, recipe(List.of(SourceRequirement.output(10L), ManaRequirement.output(10L)), partial));
            assertEquivalent(capabilities(), recipe(List.of(SourceRequirement.output(10L), ManaRequirement.output(10L)), partial));
        }
        assertEquivalent(capabilities(), recipe(List.of(SourceRequirement.output(10L, 0F), ManaRequirement.output(10L, 0F)), false));
    }

    @Test
    void workerDoesNotReadLiveStorageAfterCaptureAndCustomDelegatesRequireProof() {
        var source = new SourcePortStorage(() -> { });
        source.setAmount(100L);
        var capabilities = capabilities(new SourcePortCapability(null, source, IOType.INPUT));
        var recipe = recipe(List.of(SourceRequirement.input(20L)), false);
        var snapshot = capture(capabilities, List.of(recipe));
        source.setAmount(0L);
        assertThat(advance(snapshot)).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                match -> assertThat(match.parallelism()).isEqualTo(5L));
        assertEquivalent(capabilities, recipe);
        SourceRequirement.installHandler((value, ports, context) -> { throw new AssertionError("not a native planner"); });
        assertThat(capture(capabilities, List.of(recipe)).candidates().getFirst().proofReason())
                .isEqualTo(RecipeSearchProof.Reason.OPAQUE_REQUIREMENT);
    }

    @Test
    void retainedInputsDoNotConsumeRandomSamplesAndPartialConsumptionSharesTheBudget() {
        var source = new SourcePortStorage(() -> { });
        source.setAmount(100L);
        var capabilities = capabilities(new SourcePortCapability(null, source, IOType.INPUT));
        var retained = recipe(List.of(SourceRequirement.input(20L, 0F), SourceRequirement.output(10L, 0.5F)), false);
        var snapshot = capture(capabilities, List.of(retained));
        AtomicInteger samples = new AtomicInteger();
        var result = new RecipeSearchPlanner(snapshot, () -> { samples.incrementAndGet(); return 0.75D; }).advance();
        assertThat(result).isInstanceOf(RecipeSearchPlanner.Match.class);
        assertThat(samples).hasValue(1);
        var consumed = recipe(List.of(SourceRequirement.input(20L, 0.5F), SourceRequirement.input(20L)), false);
        var consumedSnapshot = capture(capabilities, List.of(consumed));
        assertThat(new RecipeSearchPlanner(consumedSnapshot, () -> 0.25D).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> assertThat(match.parallelism()).isEqualTo(2L));
        assertThat(new RecipeSearchPlanner(consumedSnapshot, () -> 0.75D).advance())
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> assertThat(match.parallelism()).isEqualTo(5L));
    }

    @Test
    void scalarFailureReasonsSurviveSearchResultConversion() {
        for (MachineRequirement requirement : List.of(SourceRequirement.input(1L), ManaRequirement.input(1L),
                SourceRequirement.output(1L), ManaRequirement.output(1L))) {
            var capabilities = capabilities();
            var recipe = recipe(List.of(requirement), false);
            var expected = new CraftingContext(capabilities).planStartResult(recipe, 16L);
            var request = AsyncRequirementPlanner.captureRecipeSearch(runtime(), List.of(recipe), 16L,
                    capabilities, List.of(), 0L, new EffectiveRecipeSet.Cache());
            var result = request.result(CompletableFuture.supplyAsync(() -> request.newPlanner().advance()).join());
            assertThat(result.failure()).isNull();
            assertThat(result.result().failureReport().primary().reason()).isSameAs(expected.failure().reason());
        }
    }

    @Test
    void scalarOverlapRetainsSpecificRecipeConflict() {
        var mana = new ManaStorage(() -> { });
        mana.setAmount(40L);
        var source = new SourcePortStorage(() -> { });
        var capabilities = capabilities(new ManaPortCapability(null, mana, IOType.INPUT),
                new SourcePortCapability(null, source, IOType.INPUT));
        var specific = recipe(List.of(ManaRequirement.input(1L), SourceRequirement.input(1L)), false);
        var fallback = recipe(List.of(ManaRequirement.input(1L)), false);
        assertThat(specific.hasOverlappingInputs(fallback)).isTrue();
        assertThat(advance(capture(capabilities, List.of(specific, fallback))))
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> assertThat(match.conflictProne()).isTrue());
    }

    static void assertEquivalent(CapabilitySnapshot capabilities, MachineRecipe recipe) {
        var expected = new CraftingContext(capabilities).planStartResult(recipe, 16L);
        var snapshot = capture(capabilities, List.of(recipe));
        assertThat(snapshot.candidates().getFirst().proofReason()).isNull();
        var actual = advance(snapshot);
        if (expected.successful()) {
            assertThat(actual).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> {
                assertThat(match.parallelism()).isEqualTo(expected.plan().parallelism());
                assertThat(match.scope()).isEqualTo(expected.plan().inputScope());
            });
        } else assertThat(actual).isInstanceOfSatisfying(RecipeSearchPlanner.NoMatch.class, result ->
                assertThat(result.failures()).singleElement().satisfies(failure ->
                        assertThat(failure.reasonId()).isEqualTo(expected.failure().reason().id())));
    }

    static RecipeSearchPlanner.Outcome advance(RecipeSearchSnapshot snapshot) {
        return CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(snapshot).advance()).join();
    }

    static RecipeSearchSnapshot capture(CapabilitySnapshot capabilities, List<MachineRecipe> recipes) {
        var runtime = runtime();
        var resolver = new EffectiveRecipeResolver();
        return RecipeSearchCapture.capture(runtime, 0L, 16L, recipes.stream()
                .map(recipe -> resolver.resolve(recipe, runtime, List.of())).toList(), capabilities, false);
    }

    static ControllerRuntimeSnapshot runtime() {
        Machine machine = new Machine() {
            public ResourceLocation registryName() { return MMCR.id("compat_search_machine"); }
            public BlockArray pattern() { return null; }
            public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };
        var structure = new StructureSnapshot(machine, machine, null, null, Direction.NORTH, Direction.SOUTH,
                1, true, 1, null, null, null, false, true, Set.of());
        return new ControllerRuntimeSnapshot(structure, 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.notRequired(), 0, null, null, List.of(), List.of(), List.of(),
                machine.registryName().toString(), "", 0, false, false, 0, 0, 16L);
    }

    static CapabilitySnapshot capabilities(MachineCapability... capabilities) {
        return new CapabilitySnapshot(List.of(capabilities));
    }

    static MachineRecipe recipe(List<MachineRequirement> requirements, boolean partial) {
        return MachineRecipe.fromCanonical(MMCR.id("compat_" + requirements.size()), MMCR.id("capture_pool"), 20,
                requirements, List.of(), List.of(), 0, 1, false, true, partial, Set.of());
    }
}
