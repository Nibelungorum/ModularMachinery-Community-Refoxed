package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import com.mojang.serialization.MapCodec;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import net.minecraft.core.BlockPos;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import net.minecraft.SystemReport;
import net.minecraft.util.debugchart.SampleLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.IOException;
import java.net.Proxy;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies worker-safe requirement planning from native capability descriptors.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncRequirementPlannerTest {
    private static final AsyncResourceValue IRON = new AsyncResourceValue(MMCR.id("iron_ingot"), "{}");
    private static final AsyncResourceValue WATER = new AsyncResourceValue(MMCR.id("water"), "{}");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void supported_resource_requirements_plan_to_logical_operations_from_value_descriptors() {
        AsyncRequirementPlanner.PlanResult result = new AsyncRequirementPlanner().plan(List.of(
                resourceRequirement(4, MMCR.id("item"), IRON, 2L, false),
                resourceRequirement(7, MMCR.id("fluid"), WATER, 1_000L, false)), List.of(
                resourceCapability(MMCR.id("item"), IRON, 4L, 64L),
                resourceCapability(MMCR.id("fluid"), WATER, 1_000L, 4_000L)));

        assertThat(result.operations()).hasSize(2);
        assertThat(result.operations()).allSatisfy(operation ->
                assertThat(operation.operation()).isInstanceOf(AsyncCapabilityOperation.Group.class));
        assertThat(result.mainThreadRequirements()).isEmpty();
    }

    @Test
    void routed_ordinary_material_can_combine_sources_but_cannot_borrow_an_isolated_source() {
        var id = BuiltinCapabilityDefinitions.ITEM_TYPE.id();
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);
        InputRoutingFacet routing = new InputRoutingFacet(4,
                List.of(new InputRoutingFacet.Source(3, first), new InputRoutingFacet.Source(8, second)),
                List.of(new InputRoutingFacet.Port(first, false), new InputRoutingFacet.Port(second, false)));
        var a = resourceCapability(id, IRON, 1, 64);
        var b = resourceCapability(id, IRON, 1, 64);
        List<AsyncRequirementPlanner.Capability> capabilities = List.of(
                new AsyncRequirementPlanner.Capability(a.planner(), a.snapshot(), Set.of(IOType.INPUT), 3),
                new AsyncRequirementPlanner.Capability(b.planner(), b.snapshot(), Set.of(IOType.INPUT), 8));
        var requirement = new AsyncRequirementPlanner.Requirement(9, 2, IOType.INPUT,
                resourceRequirement(9, id, IRON, 2, false).requests());
        var ordinary = new AsyncRequirementPlanner.PreparedPlan(List.of(requirement), capabilities, List.of(),
                routing, routing.ordinaryScope()).plan();
        assertThat(ordinary.mainThreadRequirements()).isEmpty();
        assertThat(ordinary.operations()).extracting(AsyncRequirementPlanner.PlannedOperation::capabilityIndex)
                .containsExactly(0, 1);
        assertThat(ordinary.operations()).extracting(AsyncRequirementPlanner.PlannedOperation::requirementIndex)
                .containsOnly(9);
        var isolated = new AsyncRequirementPlanner.PreparedPlan(List.of(requirement), capabilities, List.of(),
                routing, new InputScope(InputScope.Kind.ISOLATED, List.of(first))).plan();
        assertThat(isolated.operations()).isEmpty();
        assertThat(isolated.mainThreadRequirements()).containsExactly(9);
    }

    @Test
    void material_with_unknown_direction_or_source_falls_back_while_outputs_remain_shared() {
        var id = BuiltinCapabilityDefinitions.ITEM_TYPE.id();
        BlockPos host = new BlockPos(1, 0, 0);
        InputRoutingFacet routing = new InputRoutingFacet(4, List.of(new InputRoutingFacet.Source(6, host)),
                List.of(new InputRoutingFacet.Port(host, true)));
        InputScope scope = new InputScope(InputScope.Kind.ISOLATED, List.of(host));
        var base = resourceCapability(id, IRON, 4, 64);
        var known = new AsyncRequirementPlanner.Capability(base.planner(), base.snapshot(), Set.of(IOType.INPUT), 6);
        var directionless = resourceRequirement(7, id, IRON, 1, false);
        assertThat(new AsyncRequirementPlanner.PreparedPlan(List.of(directionless), List.of(known), List.of(), routing, scope)
                .plan().mainThreadRequirements()).containsExactly(7);
        var input = new AsyncRequirementPlanner.Requirement(7, 1, IOType.INPUT, directionless.requests());
        assertThat(new AsyncRequirementPlanner.PreparedPlan(List.of(input), List.of(base), List.of(), routing, scope)
                .plan().mainThreadRequirements()).containsExactly(7);
        var output = new AsyncRequirementPlanner.Requirement(8, 1, IOType.OUTPUT,
                resourceRequirement(8, id, IRON, 1, true).requests());
        assertThat(new AsyncRequirementPlanner.PreparedPlan(List.of(output), List.of(base), List.of(), routing, scope)
                .plan().operations()).hasSize(1);
    }

    @Test
    void filtered_async_capture_keeps_full_capability_occurrence_indices() throws Exception {
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new EnergyHatchCapability(new LongEnergyStorage(20, 20, null), IOType.INPUT),
                new ItemBusCapability(new BulkItemStorage(64, null), IOType.INPUT),
                new EnergyHatchCapability(new LongEnergyStorage(20, 20, null), IOType.INPUT),
                new ItemBusCapability(new BulkItemStorage(64, null), IOType.INPUT))));
        installCurrentServerForTesting();
        List<AsyncRequirementPlanner.Capability> captured;
        try {
            captured = context.captureAsyncCapabilities(Set.of(BuiltinCapabilityDefinitions.ITEM_TYPE.id()));
        } finally {
            clearCurrentServerForTesting();
        }
        assertThat(captured).extracting(AsyncRequirementPlanner.Capability::sourceCapabilityIndex).containsExactly(1, 3);
    }

    @Test
    void indexed_context_capture_preserves_scope_guards_tags_and_original_fallback_indexes() throws Exception {
        installCurrentServerForTesting();
        try {
            for (String mode : List.of("isolated", "unmapped", "tagged")) {
                CapabilitySnapshot snapshot = searchCapabilities(mode.equals("isolated"));
                if (mode.equals("unmapped")) {
                    var routing = snapshot.facets(InputRoutingFacet.class).getFirst();
                    snapshot = new CapabilitySnapshot(snapshot.capabilities(),
                            List.of(new InputRoutingFacet(3, List.of(), routing.inputPorts())));
                }
                ItemRequirement requirement = mode.equals("tagged")
                        ? new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT),
                        1, ItemStack.EMPTY, List.of("north"))
                        : searchInput(Ingredient.of(Items.IRON_INGOT), 2, 1F);
                CraftingContext context = new CraftingContext(snapshot);
                var ordinary = context.planAsync(List.of(requirement, requirement), 1);
                var indexed = context.planAsync(List.of(requirement, requirement), 1, List.of(3, 7));
                assertThat(ordinary.initialMainThreadRequirements()).containsExactly(0, 1);
                assertThat(indexed.initialMainThreadRequirements()).containsExactly(3, 7);
                assertThat(indexed.routing()).isSameAs(ordinary.routing());
                assertThat(indexed.inputScope()).isEqualTo(ordinary.inputScope());
                assertThat(indexed.plan().operations()).isEmpty();
            }
        } finally {
            clearCurrentServerForTesting();
        }
    }

    @Test
    void indexed_pinned_plan_keeps_capability_sources_and_nonconsecutive_operation_indexes() throws Exception {
        CapabilitySnapshot snapshot = searchCapabilities(true);
        BlockPos source = new BlockPos(2, 0, 0);
        CraftingContext context = new CraftingContext(snapshot, List.of(),
                new InputScope(InputScope.Kind.ISOLATED, List.of(source)));
        installCurrentServerForTesting();
        try {
            var prepared = context.planAsync(List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 1, 1F)), 1, List.of(7));
            var planned = prepared.plan();
            assertThat(planned.mainThreadRequirements()).isEmpty();
            assertThat(planned.operations()).singleElement().satisfies(operation -> {
                assertThat(operation.requirementIndex()).isEqualTo(7);
                assertThat(prepared.capabilities().get(operation.capabilityIndex()).sourceCapabilityIndex()).isEqualTo(1);
            });
        } finally {
            clearCurrentServerForTesting();
        }
    }

    @Test
    void snapshot_search_rejects_insufficient_isolated_inputs_without_main_thread_proof() throws Exception {
        MachineRecipe recipe = searchRecipe("isolated", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 2, 1F)));
        CapabilitySnapshot capabilities = searchCapabilities(true);
        var request = captureSearch(recipe, capabilities);
        var result = CompletableFuture.supplyAsync(request::search).join();

        assertThat(request.inputRouting()).isSameAs(capabilities.facets(InputRoutingFacet.class).getFirst());
        assertThat(result.failure()).isNull();
        assertThat(result.result().success()).isFalse();
        assertThat(result.result().failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
        assertThat(result.requiresMainThreadReplan()).isFalse();
        assertThat(new CraftingContext(capabilities).planStartResult(recipe, 1).successful()).isFalse();
    }

    @Test
    void snapshot_search_retains_the_ordinary_single_group_worker_fast_path() throws Exception {
        MachineRecipe recipe = searchRecipe("ordinary", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 2, 1F)));
        var request = captureSearch(recipe, searchCapabilities(false));
        var result = CompletableFuture.supplyAsync(request::search).join();

        assertThat(result.failure()).isNull();
        assertThat(result.result().recipe()).isSameAs(recipe);
        assertThat(result.requiresMainThreadReplan()).isFalse();
    }

    @Test
    void missing_material_source_mapping_requires_main_thread_recheck() throws Exception {
        MachineRecipe recipe = searchRecipe("unmapped", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 1, 1F)));
        CapabilitySnapshot source = searchCapabilities(false);
        InputRoutingFacet routing = source.facets(InputRoutingFacet.class).getFirst();
        CapabilitySnapshot unmapped = new CapabilitySnapshot(source.capabilities(),
                List.of(new InputRoutingFacet(3, List.of(), routing.inputPorts())));
        var result = CompletableFuture.supplyAsync(captureSearch(recipe, unmapped)::search).join();
        assertThat(result.failure()).isNull();
        assertThat(result.requiresMainThreadReplan()).isTrue();
    }

    @Test
    void energy_only_search_keeps_worker_fast_path_when_material_ports_are_isolated() throws Exception {
        MachineRecipe recipe = searchRecipe("energy", List.of(new EnergyRequirement(5)));
        CapabilitySnapshot materials = searchCapabilities(true);
        LongEnergyStorage storage = new LongEnergyStorage(20, 20, null);
        storage.setAmount(10);
        CapabilitySnapshot capabilities = new CapabilitySnapshot(List.of(materials.capabilities().get(0),
                materials.capabilities().get(1), new EnergyHatchCapability(storage, IOType.INPUT)), materials.additionalFacets());
        var result = CompletableFuture.supplyAsync(captureSearch(recipe, capabilities)::search).join();
        assertThat(result.failure()).isNull();
        assertThat(result.result().recipe()).isSameAs(recipe);
        assertThat(result.requiresMainThreadReplan()).isFalse();
    }

    @Test
    void legacy_list_search_cannot_claim_material_success_after_dropping_routing_metadata() throws Exception {
        MachineRecipe recipe = searchRecipe("legacy_material", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 2, 1F)));
        var capabilities = searchCapabilities(true);
        installCurrentServerForTesting();
        AsyncRequirementPlanner.RecipeSearchRequest request;
        try {
            request = AsyncRequirementPlanner.captureRecipeSearch(searchSnapshot(), List.of(recipe), 1,
                    capabilities.capabilities(), List.of(), 0, new EffectiveRecipeSet.Cache());
        } finally {
            clearCurrentServerForTesting();
        }
        var result = CompletableFuture.supplyAsync(request::search).join();
        assertThat(result.failure()).isNull();
        assertThat(result.requiresMainThreadReplan()).isTrue();
        var planner = request.newPlanner();
        var proof = ((RecipeSearchPlanner.NeedsProof) CompletableFuture.supplyAsync(planner::advance).join()).request();
        installCurrentServerForTesting();
        try {
            var evidence = request.prove(proof);
            assertThat(evidence).isInstanceOf(RecipeSearchProof.Failure.class);
            planner.acceptProof(evidence);
        } finally { clearCurrentServerForTesting(); }
        assertThat(CompletableFuture.supplyAsync(planner::advance).join()).isInstanceOf(RecipeSearchPlanner.Failed.class);
    }

    @Test
    void probabilistic_inputs_are_planned_without_main_thread_proof() throws Exception {
        MachineRecipe recipe = searchRecipe("probability", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 1, 0.5F)));
        var result = CompletableFuture.supplyAsync(captureSearch(recipe, searchCapabilities(false))::search).join();
        assertThat(result.failure()).isNull();
        assertThat(result.result().success()).isTrue();
        assertThat(result.requiresMainThreadReplan()).isFalse();
    }

    @Test
    void probabilistic_outputs_are_worker_success_when_output_capacity_is_available() throws Exception {
        MachineRecipe recipe = searchRecipe("output_probability", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 1, 1F),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_INGOT), 0.5F, List.of())));
        CapabilitySnapshot materials = searchCapabilities(false);
        CapabilitySnapshot capabilities = new CapabilitySnapshot(List.of(materials.capabilities().get(0),
                materials.capabilities().get(1), new ItemBusCapability(new BulkItemStorage(64, null), IOType.OUTPUT)),
                materials.additionalFacets());
        var result = CompletableFuture.supplyAsync(captureSearch(recipe, capabilities)::search).join();
        assertThat(result.failure()).isNull();
        assertThat(result.result().success()).isTrue();
        assertThat(result.requiresMainThreadReplan()).isFalse();
    }

    @Test
    void prefetch_on_a_non_energy_capability_still_requires_one_live_candidate_proof() throws Exception {
        AtomicInteger prefetchCalls = new AtomicInteger();
        List<String> events = new ArrayList<>();
        Object owner = new Object();
        RecipeEnergyPrefetchFacet prefetch = new RecipeEnergyPrefetchFacet() {
            @Override public String reservationKey() { return "unrelated-prefetch"; }
            @Override public void onRecipeSearchStarted(Object runtime) { assertThat(runtime).isSameAs(owner); events.add("started"); }
            @Override public void onRecipeSearchCandidate(Object runtime, boolean eligible) { assertThat(eligible).isTrue(); events.add("candidate"); }
            @Override public void onRecipeSearchFinished(Object runtime, boolean found) { events.add("finished:" + found); }
            @Override public Optional<PrefetchPlan> planPrefetch(long amount) { prefetchCalls.incrementAndGet(); return Optional.empty(); }
            @Override public void restoreReservation(long amount) { }
            @Override public long releaseReservation(long amount) { return 0L; }
        };
        ItemBusCapability delegate = new ItemBusCapability(new BulkItemStorage(64L, null), IOType.INPUT);
        MachineCapability unrelated = new MachineCapability() {
            @Override public CapabilityType type() { return delegate.type(); }
            @Override public CapabilityDirections directions() { return delegate.directions(); }
            @Override public CapabilityView view() { return delegate.view(); }
            @Override public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
            @Override public <F extends CapabilityFacet> Optional<F> facet(Class<F> type) {
                return type == RecipeEnergyPrefetchFacet.class ? Optional.of(type.cast(prefetch)) : delegate.facet(type);
            }
        };
        var recipe = searchRecipe("unrelated_prefetch", List.of(new EnergyRequirement(5L), new EnergyRequirement(5L)));
        var missingMaterial = searchRecipe("z_missing_material_prefetch", List.of(new EnergyRequirement(5L),
                searchInput(Ingredient.of(Items.GOLD_INGOT), 1, 1F)));
        var request = captureSearch(List.of(recipe, missingMaterial), new CapabilitySnapshot(List.of(unrelated,
                new EnergyHatchCapability(new LongEnergyStorage(20L, 20L, null), IOType.INPUT))));
        var planner = request.newPlanner();
        var outcome = CompletableFuture.supplyAsync(planner::advance).join();
        assertThat(outcome).isInstanceOfSatisfying(RecipeSearchPlanner.NeedsProof.class, proof ->
                assertThat(proof.request().reason()).isEqualTo(RecipeSearchProof.Reason.LIVE_CAPABILITY));
        assertThat(prefetchCalls).hasValue(0);
        assertThat(events).isEmpty();
        try (var server = ServerThreadTestScope.open()) {
            planner.acceptProof(request.prove(((RecipeSearchPlanner.NeedsProof) outcome).request(), owner));
        }
        assertThat(events).containsExactly("started", "candidate", "finished:false");
        assertThat(request.livePrefetchSearch()).isTrue();
        assertThat(prefetchCalls).hasValue(1);
        var next = (RecipeSearchPlanner.NeedsProof) CompletableFuture.supplyAsync(planner::advance).join();
        try (var server = ServerThreadTestScope.open()) {
            planner.acceptProof(request.prove(next.request(), owner));
        }
        assertThat(events).containsExactly("started", "candidate", "finished:false", "started", "candidate", "finished:true");
        assertThat(request.livePrefetchSearch()).isTrue();
        assertThat(prefetchCalls).hasValue(1);
        assertThat(request.result(CompletableFuture.supplyAsync(planner::advance).join()).result().success()).isFalse();
    }

    @Test
    void custom_ingredient_and_conflict_checks_never_run_on_the_worker() throws Exception {
        Thread captureThread = Thread.currentThread();
        AtomicInteger matches = new AtomicInteger();
        AtomicInteger itemQueries = new AtomicInteger();
        Ingredient ingredient = new Ingredient(new ICustomIngredient() {
            @Override
            public boolean test(ItemStack stack) {
                assertThat(Thread.currentThread()).isSameAs(captureThread);
                matches.incrementAndGet();
                return stack.is(Items.IRON_INGOT);
            }

            @Override
            public Stream<ItemStack> getItems() {
                assertThat(Thread.currentThread()).isSameAs(captureThread);
                itemQueries.incrementAndGet();
                return Stream.of(new ItemStack(Items.IRON_INGOT));
            }

            @Override
            public boolean isSimple() { return false; }

            @Override
            public IngredientType<?> getType() { return null; }
        });
        MachineRecipe earlier = searchRecipe("captured_predicate_earlier", List.of(searchInput(ingredient, 1, 1F),
                searchInput(Ingredient.of(Items.GOLD_INGOT), 1, 1F), StageRequirement.input(2)));
        MachineRecipe recipe = searchRecipe("captured_predicate", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 1, 1F)));
        var request = captureSearch(List.of(earlier, recipe), searchCapabilities(false));
        int capturedMatches = matches.get();
        int capturedItemQueries = itemQueries.get();
        assertThat(capturedItemQueries).isPositive();
        var result = CompletableFuture.supplyAsync(request::search).join();
        assertThat(result.failure()).isNull();
        assertThat(result.result().recipe()).isSameAs(recipe);
        assertThat(result.requiresMainThreadReplan()).isFalse();
        assertThat(matches).hasValue(capturedMatches);
        assertThat(itemQueries).hasValue(capturedItemQueries);
    }

    @Test
    void compat_requirements_suspend_for_scoped_proof_and_keep_native_failure_diagnostics() throws Exception {
        List<MachineRequirement> requirements = List.of(
                SourceRequirement.input(100L), SourceRequirement.output(100L, 0F),
                ManaRequirement.input(100L, 0F), ManaRequirement.output(100L, 0F),
                AirRequirement.input(10L, 4F, 0F), AirRequirement.output(10L, 0F),
                StressRequirement.input(8D, 32D), StressRequirement.output(8D, -32D),
                LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 10L),
                LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 10L, 0F));
        try (var registry = RequirementHandlerRegistry.openTestScope()) {
            for (MachineRequirement requirement : requirements) {
                if (RequirementHandlerRegistry.typeFor(requirement.type().id()) == null) {
                    RequirementHandlerRegistry.register(requirement.type());
                }
                var recipe = searchRecipe("compat_proof_" + requirements.indexOf(requirement), List.of(requirement));
                CapabilitySnapshot capabilities = new CapabilitySnapshot(List.of());
                var expected = new CraftingContext(capabilities).planStartResult(recipe, 1L);
                var request = captureSearch(recipe, capabilities);
                var planner = request.newPlanner();
                var suspended = CompletableFuture.supplyAsync(planner::advance).join();
                assertThat(suspended).as(requirement.type().id().toString())
                        .isInstanceOf(RecipeSearchPlanner.NeedsProof.class);
                var proof = ((RecipeSearchPlanner.NeedsProof) suspended).request();
                assertThat(proof.reason()).isEqualTo(RecipeSearchProof.Reason.OPAQUE_REQUIREMENT);
                assertThat(request.result(suspended).result()).isNull();
                try (var server = ServerThreadTestScope.open()) {
                    planner.acceptProof(request.prove(proof));
                }
                var actual = request.result(CompletableFuture.supplyAsync(planner::advance).join());
                assertThat(actual.failure()).isNull();
                assertThat(actual.requiresMainThreadReplan()).isFalse();
                assertThat(actual.result().success()).isEqualTo(expected.successful());
                if (expected.successful()) {
                    assertThat(actual.result().planningResult().plan().parallelism()).isEqualTo(expected.plan().parallelism());
                } else {
                    assertThat(actual.result().failure().reason()).isEqualTo(expected.failure().reason());
                    assertThat(actual.result().failure().details()).containsAllEntriesOf(expected.failure().details());
                }
            }
        }
    }

    @Test
    void level_gate_keeps_synchronous_failure_precedence() throws Exception {
        TestBootstrap.registerType(new LevelType(MMCR.id("unavailable_type"), Component.literal("Search Test Level")));
        TestBootstrap.registerLevel(new MachineLevel(MMCR.id("unavailable_level"), MMCR.id("unavailable_type"), 1,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY, ModifierDefinition.EMPTY));
        var recipe = searchRecipe("level_and_missing_input", List.of(searchInput(Ingredient.of(Items.GOLD_INGOT), 1, 1F),
                LevelRequirement.input(MMCR.id("unavailable_type"), MMCR.id("unavailable_level"))));
        CapabilitySnapshot capabilities = searchCapabilities(false);
        var expected = new RecipeSearchTask(searchSnapshot(), MMCR.id("async_search_machine"), recipe.recipePoolId(),
                1L, 1L, List.of(recipe), capabilities).compute();
        var actual = CompletableFuture.supplyAsync(captureSearch(recipe, capabilities)::search).join().result();
        assertThat(actual.success()).isFalse();
        assertThat(actual.failure().reason()).isEqualTo(expected.failure().reason()).isEqualTo(BuiltinFailureReasons.LEVEL_INSUFFICIENT);
        assertThat(actual.validity()).isEqualTo(expected.validity());
    }

    @Test
    void failed_candidate_progress_retains_precedence_over_a_shallower_higher_priority_reason() throws Exception {
        var shallow = searchRecipe("shallow_failure", List.of(searchInput(Ingredient.of(Items.GOLD_INGOT), 1, 1F),
                searchInput(Ingredient.of(Items.IRON_INGOT), 1, 1F)));
        var deep = searchRecipe("deep_failure", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 1, 1F),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_INGOT))));
        CapabilitySnapshot capabilities = searchCapabilities(false);
        var recipes = List.of(shallow, deep);
        var expected = new RecipeSearchTask(searchSnapshot(), MMCR.id("async_search_machine"), deep.recipePoolId(),
                1L, 1L, recipes, capabilities).compute();
        var actual = CompletableFuture.supplyAsync(captureSearch(recipes, capabilities)::search).join().result();
        assertThat(actual.failure().reason()).isEqualTo(expected.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(actual.validity()).isEqualTo(expected.validity());
    }

    @Test
    void opaque_candidate_and_overlap_are_individual_main_thread_proofs_not_a_pool_search() throws Exception {
        registerProbe();
        AtomicInteger plans = new AtomicInteger(), overlaps = new AtomicInteger();
        ProbeRequirement probe = new ProbeRequirement(plans, overlaps, Thread.currentThread());
        var opaque = searchRecipe("a_opaque", List.of(probe, probe));
        var nativeRecipe = searchRecipe("b_native", List.of(searchInput(Ingredient.of(Items.IRON_INGOT), 1, 1F)));
        var request = captureSearch(List.of(opaque, nativeRecipe), searchCapabilities(false));
        assertThat(plans).hasValue(0);
        assertThat(overlaps).hasValue(0);
        var planner = request.newPlanner();
        var candidateProof = ((RecipeSearchPlanner.NeedsProof) CompletableFuture.supplyAsync(planner::advance).join()).request();
        assertThatThrownBy(() -> CompletableFuture.supplyAsync(() -> request.prove(candidateProof)).join())
                .hasRootCauseInstanceOf(IllegalStateException.class);
        installCurrentServerForTesting();
        try { planner.acceptProof(request.prove(candidateProof)); }
        finally { clearCurrentServerForTesting(); }
        int planned = plans.get();
        assertThat(planned).isPositive();
        var overlapProof = ((RecipeSearchPlanner.NeedsProof) CompletableFuture.supplyAsync(planner::advance).join()).request();
        assertThat(overlapProof.kind()).isEqualTo(RecipeSearchProof.Kind.OVERLAP);
        assertThat(plans).hasValue(planned);
        assertThat(overlaps).hasValue(0);
        installCurrentServerForTesting();
        try { planner.acceptProof(request.prove(overlapProof)); }
        finally { clearCurrentServerForTesting(); }
        assertThat(overlaps).hasValue(1);
        var outcome = CompletableFuture.supplyAsync(planner::advance).join();
        assertThat(outcome).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> {
            assertThat(match.candidateIndex()).isEqualTo(1);
            assertThat(match.conflictProne()).isTrue();
        });
        assertThat(plans).hasValue(planned);
    }

    @Test
    void opaque_failure_preserves_unregistered_custom_reason_and_diagnostics() throws Exception {
        registerProbe();
        var probe = new ProbeRequirement(new AtomicInteger(), new AtomicInteger(), Thread.currentThread());
        var request = captureSearch(searchRecipe("opaque_diagnostic", List.of(probe)), searchCapabilities(false));
        var planner = request.newPlanner();
        var proof = ((RecipeSearchPlanner.NeedsProof) CompletableFuture.supplyAsync(planner::advance).join()).request();
        installCurrentServerForTesting();
        try { planner.acceptProof(request.prove(proof)); }
        finally { clearCurrentServerForTesting(); }
        var result = request.result(CompletableFuture.supplyAsync(planner::advance).join()).result();
        assertThat(result.failure().reason()).isEqualTo(ProbeRequirement.REASON);
        assertThat(result.failure().details()).containsEntry("proof", "candidate");
    }

    @Test
    void opaque_error_preserves_the_synchronous_severity_and_validity() throws Exception {
        registerProbe();
        var probe = new ProbeRequirement(new AtomicInteger(), new AtomicInteger(), Thread.currentThread(), true);
        var recipe = searchRecipe("opaque_error", List.of(probe));
        var capabilities = searchCapabilities(false);
        var expected = new RecipeSearchTask(searchSnapshot(), MMCR.id("async_search_machine"), recipe.recipePoolId(),
                1L, 1L, List.of(recipe), capabilities).compute();
        var request = captureSearch(recipe, capabilities);
        var planner = request.newPlanner();
        var proof = ((RecipeSearchPlanner.NeedsProof) CompletableFuture.supplyAsync(planner::advance).join()).request();
        installCurrentServerForTesting();
        try { planner.acceptProof(request.prove(proof)); }
        finally { clearCurrentServerForTesting(); }
        var result = request.result(CompletableFuture.supplyAsync(planner::advance).join()).result();
        assertThat(result.failure().severity()).isEqualTo(expected.failure().severity()).isEqualTo(StatusSeverity.FAILURE);
        assertThat(result.validity()).isEqualTo(expected.validity());
    }

    private static void registerProbe() {
        if (RequirementHandlerRegistry.typeFor(ProbeRequirement.TYPE.id()) == null) RequirementHandlerRegistry.register(ProbeRequirement.TYPE);
    }

    /** @author howxu <dev@howxu.cn> */
    private record ProbeRequirement(AtomicInteger plans, AtomicInteger overlaps, Thread owner, boolean error) implements MachineRequirement {
        ProbeRequirement(AtomicInteger plans, AtomicInteger overlaps, Thread owner) { this(plans, overlaps, owner, false); }
        static final FailureReason REASON = new FailureReason(MMCR.id("opaque_search_failure"), "gui.mmcr.failure.unknown", 0);
        static final RequirementType<ProbeRequirement> TYPE = new RequirementType.Definition<>(MMCR.id("opaque_search"),
                MapCodec.unit(() -> new ProbeRequirement(new AtomicInteger(), new AtomicInteger(), Thread.currentThread())),
                new RequirementHandler<>() {
                    @Override
                    public RequirementPlan plan(ProbeRequirement requirement, List<MachineCapability> capabilities,
                                                PlanningContext context) {
                        assertThat(Thread.currentThread()).isSameAs(requirement.owner());
                        requirement.plans().incrementAndGet();
                        RequirementPlan plan = RequirementHandlerSupport.blockedPlan(requirement, context, REASON, Map.of("proof", "candidate"));
                        if (!requirement.error()) return plan;
                        ExecutionStatus failure = plan.failure();
                        return new RequirementPlan(context.requirementIndex(), 0L, List.of(),
                                new ExecutionStatus(failure.id(), StatusSeverity.FAILURE, failure.source(), failure.failure()));
                    }
                    @Override
                    public boolean overlaps(ProbeRequirement requirement, MachineRequirement other) {
                        assertThat(Thread.currentThread()).isSameAs(requirement.owner());
                        requirement.overlaps().incrementAndGet();
                        return true;
                    }
                }, requirement -> requirement);
        @Override public RequirementType<ProbeRequirement> type() { return TYPE; }
        @Override public RecipeModifier.IOType io() { return RecipeModifier.IOType.INPUT; }
    }

    private static AsyncRequirementPlanner.RecipeSearchRequest captureSearch(MachineRecipe recipe,
                                                                              CapabilitySnapshot capabilities) throws Exception {
        return captureSearch(List.of(recipe), capabilities);
    }

    private static AsyncRequirementPlanner.RecipeSearchRequest captureSearch(List<MachineRecipe> recipes,
                                                                              CapabilitySnapshot capabilities) throws Exception {
        installCurrentServerForTesting();
        try {
            return AsyncRequirementPlanner.captureRecipeSearch(searchSnapshot(), recipes, 1,
                    capabilities, List.of(), 0, new EffectiveRecipeSet.Cache());
        } finally {
            clearCurrentServerForTesting();
        }
    }

    private static ItemRequirement searchInput(Ingredient ingredient, int count, float consumeChance) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, ingredient, count, ItemStack.EMPTY, 1F, List.of(),
                DataComponentPredicateSet.EMPTY, consumeChance);
    }

    private static MachineRecipe searchRecipe(String path, List<MachineRequirement> requirements) {
        return RecipeTestSupport.create(MMCR.id("async_search_" + path), MMCR.id("async_search_machine"), 20,
                requirements, List.of(), List.of(), 0, 1, false, false, List.of(), false, Set.of());
    }

    private static CapabilitySnapshot searchCapabilities(boolean isolated) {
        BulkItemStorage first = new BulkItemStorage(64, null);
        BulkItemStorage second = new BulkItemStorage(64, null);
        first.insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        second.insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        BlockPos a = new BlockPos(1, 0, 0);
        BlockPos b = new BlockPos(2, 0, 0);
        return new CapabilitySnapshot(List.of(new ItemBusCapability(first, IOType.INPUT),
                new ItemBusCapability(second, IOType.INPUT)), List.of(new InputRoutingFacet(3,
                List.of(new InputRoutingFacet.Source(0, a), new InputRoutingFacet.Source(1, b)),
                List.of(new InputRoutingFacet.Port(a, isolated), new InputRoutingFacet.Port(b, isolated)))));
    }

    private static ControllerRuntimeSnapshot searchSnapshot() {
        Machine machine = new Machine() {
            @Override
            public ResourceLocation registryName() { return MMCR.id("async_search_machine"); }
            @Override
            public BlockArray pattern() { return null; }
            @Override
            public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };
        StructureSnapshot structure = new StructureSnapshot(machine, machine, null, null, Direction.NORTH, Direction.SOUTH,
                1, true, 1, null, null, null, false, true, Set.of());
        return new ControllerRuntimeSnapshot(structure, 0, 0, 0, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.notRequired(), 0, null, null, List.of(), List.of(), List.of(),
                machine.registryName().toString(), "", 0, false, false, 0, 0, 1);
    }

    @Test
    void unsupported_requirement_falls_back_by_original_index_without_discarding_other_operations() {
        AsyncRequirementPlanner.PlanResult result = new AsyncRequirementPlanner().plan(List.of(
                resourceRequirement(3, MMCR.id("item"), IRON, 2L, false),
                new AsyncRequirementPlanner.Requirement(11, 10L, List.of(
                        new AsyncCapabilityRequest.Scalar(MMCR.id("heat"), 1L, 10L, false)))), List.of(
                resourceCapability(MMCR.id("item"), IRON, 4L, 64L)));

        assertThat(result.operations()).singleElement().satisfies(operation ->
                assertThat(operation.requirementIndex()).isEqualTo(3));
        assertThat(result.mainThreadRequirements()).containsExactly(11);
    }

    @Test
    void native_capabilities_expose_live_async_planning_facets() {
        assertThat(new ItemBusCapability(new BulkItemStorage(64L, null), IOType.INPUT)
                .facet(AsyncPlanningFacet.class)).isPresent();
        assertThat(new FluidHatchCapability(new LongFluidStorage(1_000L, null), IOType.INPUT)
                .facet(AsyncPlanningFacet.class)).isPresent();
        assertThat(new EnergyHatchCapability(new LongEnergyStorage(1_000L, 1_000L, null), IOType.INPUT)
                .facet(AsyncPlanningFacet.class)).isPresent();
    }

    @Test
    void capture_scope_reuses_one_snapshot_for_the_same_physical_storage() throws Exception {
        BulkItemStorage storage = new BulkItemStorage(64L, null);
        storage.forceInsert(new ItemStack(Items.IRON_INGOT, 1), 4L, false);
        ItemRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new ItemBusCapability(storage, IOType.INPUT), new ItemBusCapability(storage, IOType.INPUT))));

        installCurrentServerForTesting();
        AsyncRequirementPlanner.PreparedPlan prepared;
        try (AsyncPlanningFacet.CaptureScope ignored = AsyncPlanningFacet.beginCaptureScope()) {
            prepared = context.planAsync(List.of(input), 1L);
        } finally {
            clearCurrentServerForTesting();
        }

        assertThat(prepared.capabilities()).hasSize(2);
        assertThat(prepared.capabilities().get(0).snapshot())
                .isSameAs(prepared.capabilities().get(1).snapshot());
    }

    @Test
    void shared_snapshot_cannot_be_reserved_twice_through_capability_aliases() {
        var capabilityId = MMCR.id("energy");
        AsyncCapabilitySnapshot snapshot = new AsyncCapabilitySnapshot.Scalar(capabilityId, 6L, 6L, 6L);
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Scalar(capabilityId), snapshot, Set.of(IOType.INPUT));
        AsyncRequirementPlanner.Requirement requirement = new AsyncRequirementPlanner.Requirement(0, 10L,
                IOType.INPUT, List.of(new AsyncCapabilityRequest.Scalar(capabilityId, 1L, 10L, false)));

        AsyncRequirementPlanner.PlanResult result = new AsyncRequirementPlanner().plan(
                List.of(requirement), List.of(capability, capability));

        assertThat(result.operations()).isEmpty();
        assertThat(result.mainThreadRequirements()).containsExactly(0);
    }

    @Test
    void crafting_context_returns_a_worker_safe_descriptor_with_ordered_fallback_indexes() {
        AsyncRequirementPlanner.PreparedPlan prepared = new CraftingContext(new CapabilitySnapshot(List.of()))
                .planAsync(List.of(new EnergyRequirement(4L)), 1L);

        assertThat(prepared.plan().operations()).isEmpty();
        assertThat(prepared.plan().mainThreadRequirements()).containsExactly(0);
    }

    @Test
    void zero_consume_chance_item_input_does_not_create_an_async_extraction_request() throws Exception {
        BulkItemStorage storage = new BulkItemStorage(64L, null);
        storage.forceInsert(new ItemStack(Items.IRON_INGOT, 1), 2L, false);
        ItemRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new ItemBusCapability(storage, IOType.INPUT))));

        installCurrentServerForTesting();
        AsyncRequirementPlanner.PreparedPlan prepared;
        try {
            prepared = context.planAsync(List.of(input), 1L);
        } finally {
            clearCurrentServerForTesting();
        }
        assertThat(prepared.initialMainThreadRequirements()).containsExactly(0);
        var fallback = context.planInputRequirements(List.of(input), 1L, Set.of(), Set.of());
        assertThat(fallback.successful()).isTrue();
        assertThat(fallback.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(2L);
    }

    @Test
    void partial_consume_chance_item_input_does_not_create_an_async_extraction_request() throws Exception {
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 1);
        ItemRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0.5F);

        assertThat(prepareAsyncInput(input, NativeAsyncResourceValues.item(iron), 2L)).isNull();
    }

    @Test
    void zero_consume_chance_fluid_input_does_not_create_an_async_extraction_request() throws Exception {
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        FluidRequirement input = new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 100,
                FluidStack.EMPTY, 1F, List.of(), 0F);

        assertThat(prepareAsyncInput(input, NativeAsyncResourceValues.fluid(water), 100L)).isNull();
    }

    @Test
    void partial_consume_chance_fluid_input_does_not_create_an_async_extraction_request() throws Exception {
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        FluidRequirement input = new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 100,
                FluidStack.EMPTY, 1F, List.of(), 0.5F);

        assertThat(prepareAsyncInput(input, NativeAsyncResourceValues.fluid(water), 100L)).isNull();
    }

    @Test
    void scalar_output_plans_and_commits_by_capacity_even_with_zero_transfer_limit() throws Exception {
        for (long limit : new long[] { 512_000L, 0L }) {
            LongEnergyStorage storage = new LongEnergyStorage(2_000_000L, limit, null);
            EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.OUTPUT);
            var id = capability.type().id();
            var operation = new AsyncCapabilityPlanner.Scalar(id).plan(
                    new AsyncCapabilitySnapshot.Scalar(id, 0L, 2_000_000L, limit),
                    new AsyncCapabilityRequest.Scalar(id, 2L, 1_600_000L, true)).orElseThrow();
            assertThat(operation).isEqualTo(new AsyncCapabilityOperation.Scalar(id, 1_600_000L, true));
            assertThat(commit(capability, operation).success()).isTrue();
            assertThat(storage.getAmountAsLong()).isEqualTo(1_600_000L);
        }
    }

    @Test
    void scalar_output_still_bounds_planning_and_commit_by_available_capacity() throws Exception {
        LongEnergyStorage storage = new LongEnergyStorage(1_000_000L, 0L, null);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.OUTPUT);
        var id = capability.type().id();
        var operation = new AsyncCapabilityPlanner.Scalar(id).plan(
                new AsyncCapabilitySnapshot.Scalar(id, 100_000L, 1_000_000L, 0L),
                new AsyncCapabilityRequest.Scalar(id, 1L, 1_600_000L, true)).orElseThrow();
        assertThat(operation).isEqualTo(new AsyncCapabilityOperation.Scalar(id, 900_000L, true));
        storage.setAmount(200_000L);
        assertThat(commit(capability, operation).success()).isFalse();
        assertThat(storage.getAmountAsLong()).isEqualTo(200_000L);
    }

    @Test
    void batch_limited_energy_groups_revalidate_and_roll_back_when_storage_changes() throws Exception {
        LongEnergyStorage storage = new LongEnergyStorage(100L, 20L, null);
        storage.setAmount(60L);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        AsyncCapabilityOperation operation = new AsyncCapabilityPlanner.Scalar(capability.type().id()).plan(
                new AsyncCapabilitySnapshot.Scalar(capability.type().id(), 60L, 100L, 20L),
                new AsyncCapabilityRequest.Scalar(capability.type().id(), 3L, 60L, false)).orElseThrow();
        storage.setAmount(40L);

        assertThat(commit(capability, operation).success()).isFalse();

        assertThat(storage.getAmountAsLong()).isEqualTo(40L);
    }

    private static AsyncRequirementPlanner.Requirement resourceRequirement(int index,
                                                                            net.minecraft.resources.ResourceLocation capabilityId,
                                                                            AsyncResourceValue resource,
                                                                            long amount, boolean insert) {
        return new AsyncRequirementPlanner.Requirement(index, amount, List.of(
                new AsyncCapabilityRequest.Resource(capabilityId, 1L,
                        List.of(new AsyncResourceAction(resource, amount, insert)))));
    }

    private static AsyncRequirementPlanner.Capability resourceCapability(net.minecraft.resources.ResourceLocation capabilityId,
                                                                          AsyncResourceValue resource,
                                                                          long amount, long capacity) {
        return new AsyncRequirementPlanner.Capability(new AsyncCapabilityPlanner.Resource(capabilityId),
                new AsyncCapabilitySnapshot.Resource(capabilityId, List.of(
                        new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(resource), amount, capacity))));
    }

    private static Object prepareAsyncInput(MachineRequirement input, AsyncResourceValue resource, long amount)
            throws Exception {
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Resource(input.type().id()),
                new AsyncCapabilitySnapshot.Resource(input.type().id(), List.of(
                        new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(resource), amount, amount))), Set.of(IOType.INPUT));
        Method method = CraftingContext.class.getDeclaredMethod("prepareAsyncRequirement", int.class,
                MachineRequirement.class, long.class, List.class);
        method.setAccessible(true);
        return method.invoke(null, 0, input, 1L, List.of(capability));
    }

    private static void installCurrentServerForTesting() throws Exception {
        TestServer server = allocate(TestServer.class);
        server.serverThread = Thread.currentThread();
        Field currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        currentServer.set(null, server);
    }

    private static void clearCurrentServerForTesting() throws Exception {
        Field currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        currentServer.set(null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    /**
     * Minimal server identity used to exercise AsyncPlanningFacet's main-thread guard.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestServer extends MinecraftServer {
        private Thread serverThread;

        private TestServer() {
            super(null, null, null, null, Proxy.NO_PROXY, null, null, null);
        }

        @Override
        public Thread getRunningThread() {
            return serverThread;
        }

        @Override
        protected boolean initServer() throws IOException {
            return false;
        }

        @Override
        public int getOperatorUserPermissionLevel() {
            return 4;
        }

        @Override
        public int getFunctionCompilationLevel() {
            return 4;
        }

        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return false;
        }

        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }

        @Override
        public boolean isEpollEnabled() {
            return false;
        }

        @Override
        public boolean isCommandBlockEnabled() {
            return false;
        }

        @Override
        public boolean isPublished() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean isSingleplayerOwner(GameProfile profile) {
            return false;
        }

        @Override
        protected SampleLogger getTickTimeLogger() {
            return null;
        }

        @Override
        public boolean isTickTimeLoggingEnabled() {
            return false;
        }

        @Override
        public int getMaxPlayers() {
            return 1;
        }

        @Override
        public SystemReport fillServerSystemReport(SystemReport report) {
            return report;
        }
    }

    private static CapabilityResult commit(EnergyHatchCapability capability, AsyncCapabilityOperation operation)
            throws Exception {
        Method method = EnergyHatchCapability.class.getDeclaredMethod("commitAsync", AsyncCapabilityOperation.class);
        method.setAccessible(true);
        return (CapabilityResult) method.invoke(capability, operation);
    }
}
