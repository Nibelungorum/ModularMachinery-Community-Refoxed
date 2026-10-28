package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
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
import cn.howxu.mmcr.compat.ae2lt.LightningFailureReasons;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningStorage;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
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
        LightningBridgeBootstrap.installForTesting(() -> true);
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
    void mixedItemFluidAndBothLightningTiersSearchWithoutCandidateProofs() {
        var items = new LongItemStorage(1, 64L, null);
        items.setContents(0, new ItemStack(Items.IRON_INGOT), 8L);
        var fluids = new LongFluidStorage(10_000L, null);
        fluids.forceInsert(0, new FluidStack(Fluids.WATER, 1), 2_000L, false);
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
    void tiersAndSharedPhysicalReservationsCannotBeSubstitutedOrDoubleSpent() {
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
    void reorderedViewsSharePhysicalCellsWithoutReusingTheWrongSnapshot() {
        var storage = storage(1_000L);
        storage.setContents(0, HIGH, 80L);
        storage.setContents(1, EXTREME, 10L);
        var reversed = new MappedStorage(storage, 1, 0);
        var capabilities = capabilities(lightning(storage, IOType.INPUT), lightning(reversed, IOType.INPUT));
        assertEquivalent(capabilities, recipe("reordered_shared", List.of(
                LightningRequirement.input(HIGH, 50L), LightningRequirement.input(HIGH, 50L)), false));
        assertEquivalent(capabilities, recipe("reordered_extreme", List.of(LightningRequirement.input(EXTREME, 7L)), false));
    }

    @Test
    void isolatedRoutingAppliesToTierRequirementsUsingTheSharedCapabilityId() {
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
    void outputCapacityTiersPartialPolicyAndZeroChanceMatchNativeHandlers() {
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
    void missingPortsDirectionAndTagsKeepNativeFailureReasons() {
        var storage = storage(100L);
        storage.setContents(0, HIGH, 50L);
        assertEquivalent(capabilities(), recipe("absent", List.of(LightningRequirement.input(HIGH, 1L)), false));
        assertEquivalent(capabilities(lightning(storage, IOType.OUTPUT)),
                recipe("direction", List.of(LightningRequirement.input(HIGH, 1L)), false));
        assertEquivalent(capabilities(lightning(storage, IOType.INPUT)), recipe("tag", List.of(new LightningRequirement(
                HIGH, RecipeModifier.IOType.INPUT, 1L, 1F, List.of("missing"))), false));
    }

    @Test
    void nativeOverlapPreservesSpecificRecipeConflictsWithoutProofs() {
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
    void dynamicOutputAndUnavailableBridgeKeepTheProofBoundary() {
        var dynamic = new DynamicStorage(storage(100L));
        var output = recipe("dynamic", List.of(LightningRequirement.output(HIGH, 1L, 1F)), false);
        var snapshot = capture(capabilities(new LightningCapability(dynamic, IOType.OUTPUT)), List.of(output));
        assertThat(snapshot.candidates().getFirst().proofReason()).isEqualTo(RecipeSearchProof.Reason.LIVE_CAPABILITY);
        LightningBridgeBootstrap.installForTesting(() -> false);
        var unavailable = capture(capabilities(lightning(storage(100L), IOType.INPUT)),
                List.of(recipe("unavailable", List.of(LightningRequirement.input(HIGH, 1L)), false)));
        assertThat(unavailable.candidates().getFirst().proofReason()).isEqualTo(RecipeSearchProof.Reason.OPAQUE_REQUIREMENT);
    }

    @Test
    void foreignKeysKeepTheLiveCapabilityProofBoundary() {
        var output = recipe("foreign", List.of(LightningRequirement.output(HIGH, 1L, 1F)), false);
        var snapshot = capture(capabilities(new LightningCapability(new ForeignStorage(), IOType.OUTPUT)), List.of(output));
        assertThat(snapshot.candidates().getFirst().proofReason()).isEqualTo(RecipeSearchProof.Reason.LIVE_CAPABILITY);
    }

    @Test
    void resultConversionRetainsLightningFailureReasonsAndRequirementTypes() {
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
            public ResourceLocation registryName() { return MMCR.id("lightning_search_machine"); }
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

    private static TestStorage storage(long capacity) {
        return new TestStorage(2, capacity);
    }

    private static LightningCapability lightning(LightningStorage storage, IOType direction) {
        return new LightningCapability(storage, direction);
    }

    /** Live reads are confined to the capturing thread; searches must use immutable values.
     * @author howxu <dev@howxu.cn>
     */
    private static class TestStorage implements LightningStorage {
        protected final Thread owner = Thread.currentThread();
        private final LightningTier[] tiers;
        private final long[] amounts;
        private final long capacity;

        TestStorage(int size, long capacity) {
            tiers = new LightningTier[size];
            amounts = new long[size];
            this.capacity = capacity;
        }

        void setContents(int slot, LightningTier tier, long amount) {
            checkThread();
            tiers[slot] = tier;
            amounts[slot] = amount;
        }

        protected final void checkThread() { assertThat(Thread.currentThread()).isSameAs(owner); }
        @Override public int size() { checkThread(); return tiers.length; }
        @Override public Object identity() { checkThread(); return this; }
        @Override public Object address(int slot) { checkThread(); return slot; }
        @Override public Object key(LightningTier tier) { checkThread(); return tier; }
        @Override public Object storedKey(int slot) { checkThread(); return tiers[slot]; }
        @Override public long amount(int slot) { checkThread(); return amounts[slot]; }
        @Override public long capacity(int slot, LightningTier tier) { checkThread(); return capacity; }

        @Override
        public long reserveOutput(LightningTier tier, long amount, PlanningReservations reservations) {
            checkThread();
            long remaining = amount;
            for (int slot = 0; slot < size() && remaining > 0L; slot++) {
                Object current = reservations.nativeKey(this, slot, tiers[slot]);
                long stored = reservations.nativeAmount(this, slot, amounts[slot]);
                if (stored > 0L && current != tier) continue;
                long moved = Math.min(remaining, Math.max(0L, capacity - stored));
                if (moved > 0L && reservations.reserveNativeInsert(this, slot, tier, tiers[slot], amounts[slot], capacity, moved)) {
                    remaining -= moved;
                }
            }
            return amount - remaining;
        }

        @Override public long extract(int slot, LightningTier tier, long amount, boolean simulate) {
            throw new AssertionError("Search must not consume resources");
        }
        @Override public long insert(int slot, LightningTier tier, long amount, boolean simulate) {
            throw new AssertionError("Search must not produce resources");
        }
        @Override public long insert(LightningTier tier, long amount, boolean simulate) {
            throw new AssertionError("Search must not produce resources");
        }
    }

    /** A reordered projection of the same physical slots.
     * @author howxu <dev@howxu.cn>
     */
    private static final class MappedStorage extends TestStorage {
        private final TestStorage delegate;
        private final int[] mapping;
        MappedStorage(TestStorage delegate, int... mapping) {
            super(mapping.length, delegate.capacity);
            this.delegate = delegate;
            this.mapping = mapping;
        }
        @Override public Object identity() { return delegate.identity(); }
        @Override public Object address(int slot) { return delegate.address(mapping[slot]); }
        @Override public Object storedKey(int slot) { return delegate.storedKey(mapping[slot]); }
        @Override public long amount(int slot) { return delegate.amount(mapping[slot]); }
        @Override public long capacity(int slot, LightningTier tier) { return delegate.capacity(mapping[slot], tier); }
    }

    /** A non-lightning AE key cannot be represented by AsyncResourceValue.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ForeignStorage extends TestStorage {
        private final Object foreign = new Object();
        ForeignStorage() { super(1, 100L); }
        @Override public Object storedKey(int slot) { checkThread(); return foreign; }
        @Override public long amount(int slot) { checkThread(); return 1L; }
    }

    /** Output storage whose live admission policy cannot be represented by slot snapshots.
     * @author howxu <dev@howxu.cn>
     */
    private static final class DynamicStorage extends TestStorage implements NativeReservationAccess {
        DynamicStorage(TestStorage delegate) {
            super(delegate.size(), delegate.capacity);
            for (int slot = 0; slot < delegate.size(); slot++) setContents(slot, delegate.tiers[slot], delegate.amounts[slot]);
        }
        @Override public Object reservationIdentity() { return this; }
        @Override public Object reservationSlot(int slot) { return slot; }
        @Override public Object resourceKey(Object resource) { return resource; }
        @Override public Object resource(Object key) { return key; }
        @Override public long storedAmount(int slot) { return amount(slot); }
        @Override public boolean requiresServerOutputPlanning() { return true; }
    }
}
