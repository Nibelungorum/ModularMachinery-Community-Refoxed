package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ae2lt.LightningBridgeBootstrap;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningFailureReasons;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Native lightning searches must match synchronous planning without worker access to live storage.
 * @author howxu <dev@howxu.cn>
 */
class LightningSearchCaptureTest {
    private static final LightningTier HIGH = LightningTier.HIGH_VOLTAGE;
    private static final LightningTier EXTREME = LightningTier.EXTREME_HIGH_VOLTAGE;
    private ServerThreadTestScope server;
    private RequirementHandlerRegistry.TestScope requirements;
    private OutputRegistry.TestScope outputs;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void setup() throws Exception {
        server = ServerThreadTestScope.open();
        requirements = RequirementHandlerRegistry.openTestScope();
        outputs = OutputRegistry.openTestScope();
        LightningBridgeBootstrap.installForTesting(LightningBridgeBootstrap.selectForTesting(true));
        LightningRecipeTypes.register();
    }

    @AfterEach
    void cleanup() throws Exception {
        outputs.close();
        requirements.close();
        LightningBridgeBootstrap.resetForTesting();
        server.close();
    }

    @Test
    void mixed_item_fluid_and_both_lightning_tiers_search_without_candidate_proofs() {
        var items = new LongResourceStorage<>(ItemResource.class, 1, 64L, ItemResource::isEmpty, null);
        items.setContents(0, ItemResource.of(Items.IRON_INGOT), 8L);
        var fluids = new LongFluidStorage(10_000L, null);
        try (var transaction = Transaction.openRoot()) {
            fluids.insert(0, FluidResource.of(Fluids.WATER), 2_000L, transaction);
            transaction.commit();
        }
        var lightning = storage(1_000L);
        lightning.setContents(0, HIGH, 120L);
        lightning.setContents(1, EXTREME, 60L);
        var capabilities = capabilities(new ItemBusCapability(items, IOType.INPUT),
                new FluidHatchCapability(fluids, IOType.INPUT), lightning(lightning, IOType.INPUT));
        var recipe = recipe("mixed", List.of(new ItemRequirement(RecipeModifier.IOType.INPUT,
                Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY),
                new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER),
                        500, FluidStack.EMPTY, 1F, List.of()),
                LightningRequirement.input(HIGH, 30L), LightningRequirement.input(EXTREME, 20L)), false);
        assertEquivalent(capabilities, recipe);
        assertThat(lightning.amount(0)).isEqualTo(120L);
        assertThat(lightning.amount(1)).isEqualTo(60L);
    }

    @Test
    void tiers_and_shared_physical_reservations_cannot_be_substituted_or_double_spent() {
        var storage = storage(1_000L);
        storage.setContents(0, HIGH, 80L);
        storage.setContents(1, EXTREME, 10L);
        var input = lightning(storage, IOType.INPUT);
        var capabilities = capabilities(input, lightning(storage, IOType.INPUT));
        assertEquivalent(capabilities, recipe("shared", List.of(
                LightningRequirement.input(HIGH, 50L), LightningRequirement.input(HIGH, 50L)), false));
        assertEquivalent(capabilities, recipe("wrong_tier", List.of(LightningRequirement.input(EXTREME, 11L)), false));
    }

    @Test
    void isolated_routing_applies_to_tier_requirements_using_the_shared_capability_id() {
        var first = storage(100L);
        var second = storage(100L);
        first.setContents(0, HIGH, 20L);
        second.setContents(0, HIGH, 30L);
        BlockPos a = new BlockPos(1, 0, 0), b = new BlockPos(2, 0, 0);
        var capabilities = new CapabilitySnapshot(List.of(lightning(first, IOType.INPUT), lightning(second, IOType.INPUT)),
                List.of(new InputRoutingFacet(1L,
                        List.of(new InputRoutingFacet.Source(0, a), new InputRoutingFacet.Source(1, b)),
                        List.of(new InputRoutingFacet.Port(a, true), new InputRoutingFacet.Port(b, true)))));
        assertEquivalent(capabilities, recipe("isolated_blocked", List.of(LightningRequirement.input(HIGH, 40L)), false));
        assertEquivalent(capabilities, recipe("isolated_first", List.of(LightningRequirement.input(HIGH, 10L)), false));
    }

    @Test
    void output_capacity_tiers_partial_policy_and_zero_chance_match_native_handlers() {
        var storage = storage(30L);
        storage.setContents(0, HIGH, 25L);
        storage.setContents(1, EXTREME, 25L);
        var capabilities = capabilities(lightning(storage, IOType.OUTPUT));
        assertEquivalent(capabilities, recipe("strict", List.of(LightningRequirement.output(HIGH, 10L, 1F)), false));
        assertEquivalent(capabilities, recipe("partial", List.of(LightningRequirement.output(HIGH, 10L, 1F)), true));
        assertEquivalent(capabilities, recipe("zero", List.of(LightningRequirement.output(HIGH, 10L, 0F)), false));
        assertEquivalent(capabilities, recipe("two_outputs", List.of(LightningRequirement.output(HIGH, 3L, 1F),
                LightningRequirement.output(HIGH, 3L, 1F)), false));
        assertThat(storage.amount(0)).isEqualTo(25L);
    }

    @Test
    void missing_ports_direction_and_tags_keep_native_failure_reasons() {
        var storage = storage(100L);
        storage.setContents(0, HIGH, 50L);
        assertEquivalent(capabilities(), recipe("absent", List.of(LightningRequirement.input(HIGH, 1L)), false));
        assertEquivalent(capabilities(lightning(storage, IOType.OUTPUT)),
                recipe("direction", List.of(LightningRequirement.input(HIGH, 1L)), false));
        assertEquivalent(capabilities(lightning(storage, IOType.INPUT)), recipe("tag", List.of(new LightningRequirement(
                HIGH, RecipeModifier.IOType.INPUT, 1L, 1F, List.of("missing"))), false));
    }

    @Test
    void native_overlap_preserves_specific_recipe_conflicts_without_proofs() {
        var storage = storage(100L);
        storage.setContents(0, HIGH, 40L);
        var capabilities = capabilities(lightning(storage, IOType.INPUT));
        var specific = recipe("specific", List.of(LightningRequirement.input(HIGH, 1L),
                LightningRequirement.input(EXTREME, 1L)), false);
        var fallback = recipe("fallback", List.of(LightningRequirement.input(HIGH, 1L)), false);
        assertThat(specific.hasOverlappingInputs(fallback)).isTrue();
        assertThat(advance(capture(capabilities, List.of(specific, fallback))))
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> assertThat(match.conflictProne()).isTrue());
        var unrelated = recipe("unrelated", List.of(LightningRequirement.input(EXTREME, 1L),
                LightningRequirement.input(EXTREME, 1L)), false);
        assertThat(advance(capture(capabilities, List.of(unrelated, fallback))))
                .isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> assertThat(match.conflictProne()).isFalse());
    }

    @Test
    void dynamic_output_and_unavailable_bridge_keep_the_proof_boundary() {
        var dynamic = new DynamicStorage(storage(100L));
        var output = recipe("dynamic", List.of(LightningRequirement.output(HIGH, 1L, 1F)), false);
        var snapshot = capture(capabilities(new LightningCapability(dynamic, IOType.OUTPUT)), List.of(output));
        assertThat(snapshot.candidates().getFirst().proofReason()).isEqualTo(RecipeSearchProof.Reason.LIVE_CAPABILITY);
        LightningBridgeBootstrap.installForTesting(LightningBridgeBootstrap.selectForTesting(false));
        var unavailable = capture(capabilities(lightning(storage(100L), IOType.INPUT)),
                List.of(recipe("unavailable", List.of(LightningRequirement.input(HIGH, 1L)), false)));
        assertThat(unavailable.candidates().getFirst().proofReason()).isEqualTo(RecipeSearchProof.Reason.OPAQUE_REQUIREMENT);
    }

    @Test
    void result_conversion_retains_lightning_failure_reasons_and_requirement_types() {
        for (var requirement : List.of(LightningRequirement.input(HIGH, 1L), LightningRequirement.output(EXTREME, 1L, 1F))) {
            boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
            var storage = storage(100L);
            if (!input) {
                storage.setContents(0, HIGH, 100L);
                storage.setContents(1, HIGH, 100L);
            }
            var request = AsyncRequirementPlanner.captureRecipeSearch(runtime(),
                    List.of(recipe("failure_conversion", List.of(requirement), false)), 16L,
                    capabilities(lightning(storage, input ? IOType.INPUT : IOType.OUTPUT)), List.of(), 0L,
                    new EffectiveRecipeSet.Cache());
            var result = request.result(CompletableFuture.supplyAsync(() -> request.newPlanner().advance()).join());
            assertThat(result.failure()).isNull();
            assertThat(result.result().success()).isFalse();
            var failure = result.result().failureReport().primary();
            assertThat(failure.reason()).isSameAs(input ? LightningFailureReasons.INPUT_MISSING : LightningFailureReasons.OUTPUT_BLOCKED);
            assertThat(failure.source()).isEqualTo(requirement.type().id());
        }
    }

    private static void assertEquivalent(CapabilitySnapshot capabilities, MachineRecipe recipe) {
        var expected = new CraftingContext(capabilities).planStartResult(recipe, 16L);
        var snapshot = capture(capabilities, List.of(recipe));
        assertThat(snapshot.candidates().getFirst().proofReason()).isNull();
        var actual = advance(snapshot);
        if (expected.successful()) {
            assertThat(actual).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> {
                assertThat(match.parallelism()).isEqualTo(expected.plan().parallelism());
                assertThat(match.scope()).isEqualTo(expected.plan().inputScope());
            });
        } else {
            assertThat(actual).isInstanceOfSatisfying(RecipeSearchPlanner.NoMatch.class, result ->
                    assertThat(result.failures()).singleElement().satisfies(failure ->
                            assertThat(failure.reasonId()).isEqualTo(expected.failure().reason().id())));
        }
    }

    private static RecipeSearchPlanner.Outcome advance(RecipeSearchSnapshot snapshot) {
        return CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(snapshot).advance()).join();
    }

    private static RecipeSearchSnapshot capture(CapabilitySnapshot capabilities, List<MachineRecipe> recipes) {
        var runtime = runtime();
        var resolver = new EffectiveRecipeResolver();
        return RecipeSearchCapture.capture(runtime, 0L, 16L, recipes.stream()
                .map(recipe -> resolver.resolve(recipe, runtime, List.of())).toList(), capabilities, false);
    }

    private static ControllerRuntimeSnapshot runtime() {
        Machine machine = new Machine() {
            public Identifier registryName() { return MMCR.id("lightning_search_machine"); }
            public BlockArray pattern() { return null; }
            public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };
        var structure = new StructureSnapshot(machine, machine, null, null, Direction.NORTH, Direction.SOUTH,
                1, true, 1, null, null, null, false, true, Set.of());
        return new ControllerRuntimeSnapshot(structure, 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.notRequired(), 0, null, null, List.of(), List.of(), List.of(),
                machine.registryName().toString(), "", 0, false, false, 0, 0, 16L);
    }

    private static CapabilitySnapshot capabilities(MachineCapability... capabilities) {
        return new CapabilitySnapshot(List.of(capabilities));
    }

    private static MachineRecipe recipe(String id, List<MachineRequirement> requirements, boolean partial) {
        return MachineRecipe.fromCanonical(MMCR.id(id), MMCR.id("capture_pool"), 20,
                requirements, List.of(), List.of(), 0, 1, false, true, partial, Set.of());
    }

    private static LongResourceStorage<LightningTier> storage(long capacity) {
        return new LongResourceStorage<>(LightningTier.class, 2, capacity, ignored -> false, null);
    }

    private static LightningCapability lightning(ResourceStorage<LightningTier> storage, IOType direction) {
        return new LightningCapability(new GuardedStorage(storage), direction);
    }

    /** @author howxu <dev@howxu.cn> */
    private record GuardedStorage(ResourceStorage<LightningTier> delegate, Thread owner) implements ResourceStorage<LightningTier> {
        GuardedStorage(ResourceStorage<LightningTier> delegate) { this(delegate, Thread.currentThread()); }
        private void checkThread() { assertThat(Thread.currentThread()).isSameAs(owner); }
        public Class<LightningTier> resourceType() { return LightningTier.class; }
        public Object reservationIdentity() { checkThread(); return delegate.reservationIdentity(); }
        public Object reservationSlot(int slot) { checkThread(); return delegate.reservationSlot(slot); }
        public int size() { checkThread(); return delegate.size(); }
        public LightningTier resource(int slot) { checkThread(); return delegate.resource(slot); }
        public long amount(int slot) { checkThread(); return delegate.amount(slot); }
        public long capacity(int slot, LightningTier tier) { checkThread(); return delegate.capacity(slot, tier); }
        public boolean isValid(int slot, LightningTier tier) { checkThread(); return delegate.isValid(slot, tier); }
        public long insert(int slot, LightningTier tier, long amount, TransactionContext transaction) {
            throw new AssertionError("Search must not produce resources");
        }
        public long extract(int slot, LightningTier tier, long amount, TransactionContext transaction) {
            throw new AssertionError("Search must not consume resources");
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record DynamicStorage(ResourceStorage<LightningTier> delegate) implements OutputResourceStorage<LightningTier> {
        public Class<LightningTier> resourceType() { return LightningTier.class; }
        public int size() { return delegate.size(); }
        public LightningTier resource(int slot) { return delegate.resource(slot); }
        public long amount(int slot) { return delegate.amount(slot); }
        public long capacity(int slot, LightningTier tier) { return delegate.capacity(slot, tier); }
        public boolean isValid(int slot, LightningTier tier) { return true; }
        public long outputCapacity(LightningTier tier) { return 100L; }
        public OutputPlan planOutput(LightningTier tier, long amount, PlanningReservations reservations, boolean materialize) {
            throw new AssertionError("Native capture must defer dynamic outputs");
        }
        public long insert(int slot, LightningTier tier, long amount, TransactionContext transaction) { throw new AssertionError(); }
        public long extract(int slot, LightningTier tier, long amount, TransactionContext transaction) { throw new AssertionError(); }
    }
}
