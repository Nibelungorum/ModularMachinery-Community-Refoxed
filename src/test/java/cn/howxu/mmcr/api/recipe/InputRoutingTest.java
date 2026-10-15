package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises complete recipe planning and commits through frozen material source groups.
 * @author howxu <dev@howxu.cn>
 */
class InputRoutingTest {
    private static final BlockPos FIRST = new BlockPos(1, 0, 0);
    private static final BlockPos SECOND = new BlockPos(2, 0, 0);

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void ordinary_ports_combine_but_isolated_ports_never_supplement_each_other() {
        ItemBusCapability first = input(1);
        ItemBusCapability second = input(1);
        var ordinary = context(List.of(first, second), false, false).planRequirements(List.of(inputRequirement(2)), 1, Map.of());
        assertThat(ordinary.successful()).isTrue();
        assertThat(ordinary.plan().inputScope()).isEqualTo(new InputScope(InputScope.Kind.ORDINARY, List.of(FIRST, SECOND)));
        assertThat(context(List.of(first, second), true, false)
                .planRequirements(List.of(inputRequirement(2)), 1, Map.of()).successful()).isFalse();
        assertThat(context(List.of(first, second), true, true)
                .planRequirements(List.of(inputRequirement(2)), 1, Map.of()).successful()).isFalse();
        assertThat(ordinary.plan().commit()).isTrue();
        assertThat(amount(first)).isZero();
        assertThat(amount(second)).isZero();
    }

    @Test
    void failed_group_reservations_do_not_pollute_a_later_complete_group() {
        ItemBusCapability first = input(1);
        ItemBusCapability second = input(2);
        var result = context(List.of(first, second), true, true).planRequirements(
                List.of(inputRequirement(1), inputRequirement(1)), 1, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().inputScope()).isEqualTo(new InputScope(InputScope.Kind.ISOLATED, List.of(SECOND)));
        assertThat(result.plan().commit()).isTrue();
        assertThat(amount(first)).isEqualTo(1);
        assertThat(amount(second)).isZero();
    }

    @Test
    void fluid_inputs_can_pool_only_within_ordinary_scope() {
        LongFluidStorage first = new LongFluidStorage(1_000, null);
        LongFluidStorage second = new LongFluidStorage(1_000, null);
        first.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
        second.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
        List<MachineCapability> capabilities = List.of(new FluidHatchCapability(first, IOType.INPUT),
                new FluidHatchCapability(second, IOType.INPUT));
        FluidRequirement requirement = new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER),
                1_000, FluidStack.EMPTY);
        assertThat(context(capabilities, true, false).planRequirements(List.of(requirement), 1, Map.of()).successful()).isFalse();
        var ordinary = context(capabilities, false, false).planRequirements(List.of(requirement), 1, Map.of());
        assertThat(ordinary.successful()).isTrue();
        assertThat(ordinary.plan().commit()).isTrue();
        assertThat(first.amount(0)).isZero();
        assertThat(second.amount(0)).isZero();
    }

    @Test
    void isolated_coordinate_order_wins_without_accumulating_parallelism() {
        ItemBusCapability first = input(2);
        ItemBusCapability second = input(8);
        var result = context(List.of(first, second), true, true).planRequirements(List.of(inputRequirement(1)), 10, Map.of());
        assertThat(result.plan().parallelism()).isEqualTo(2);
        assertThat(result.plan().inputScope().sources()).containsExactly(FIRST);
        assertThat(result.plan().inputRoutingRevision()).isEqualTo(7);
    }

    @Test
    void pinned_ordinary_scope_never_includes_newly_added_ordinary_sources() {
        CraftingContext context = context(List.of(input(1), input(4)), false, false);
        CraftingContext pinned = new CraftingContext(context.capabilitySnapshot(), List.of(),
                new InputScope(InputScope.Kind.ORDINARY, List.of(FIRST)));
        assertThat(pinned.planRequirements(List.of(inputRequirement(2)), 1, Map.of()).successful()).isFalse();
        CraftingContext missingRoute = new CraftingContext(new CapabilitySnapshot(context.capabilitySnapshot().capabilities()),
                List.of(), new InputScope(InputScope.Kind.ISOLATED, List.of(FIRST)));
        assertThat(missingRoute.planRequirements(List.of(inputRequirement(1)), 1, Map.of()).successful()).isFalse();
    }

    @Test
    void scope_filters_still_intersect_requirement_tags_without_changing_untagged_wildcards() {
        MachineCapability north = new ViewedCapability(input(2), CapabilityDirections.of(IOType.INPUT), List.of("north"));
        MachineCapability south = new ViewedCapability(input(2), CapabilityDirections.of(IOType.INPUT), List.of("south"));
        CraftingContext context = context(List.of(north, south), true, true);
        ItemRequirement tagged = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT),
                1, ItemStack.EMPTY, List.of("missing"));
        assertThat(context.planRequirements(List.of(tagged), 1, Map.of()).successful()).isFalse();
        ItemRequirement matching = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT),
                1, ItemStack.EMPTY, List.of("south"));
        var result = context.planRequirements(List.of(matching), 1, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().inputScope().sources()).containsExactly(SECOND);
        assertThat(RequirementPlanner.matchingCapabilities(matching, context.capabilitySnapshot().capabilities(),
                context.inputRouting(), new InputScope(InputScope.Kind.ISOLATED, List.of(FIRST)))).isEmpty();
        assertThat(context(List.of(input(2), input(2)), true, false)
                .planRequirements(List.of(tagged), 1, Map.of()).successful()).isTrue();
    }

    @Test
    void direction_filter_rejects_a_stocked_output_view_even_when_declared_bidirectional() {
        ItemBusCapability stored = input(2);
        MachineCapability output = new ViewedCapability(new ItemBusCapability(stored.itemHandler(), IOType.OUTPUT),
                CapabilityDirections.bidirectional(), List.of());
        CraftingContext context = context(List.of(output, output), false, false);
        assertThat(amount(stored)).isEqualTo(2);
        assertThat(output.directions().supports(IOType.INPUT)).isTrue();
        assertThat(RequirementPlanner.matchingCapabilities(inputRequirement(1), context.capabilitySnapshot().capabilities(),
                context.inputRouting(), context.inputRouting().ordinaryScope())).isEmpty();
        assertThat(context.planRequirements(List.of(inputRequirement(1)), 1, Map.of()).successful()).isFalse();
    }

    @Test
    void energy_and_material_outputs_remain_shared_with_an_isolated_input() {
        ItemBusCapability first = input(2);
        ItemBusCapability output = new ItemBusCapability(new BulkItemStorage(64, null), IOType.OUTPUT);
        LongEnergyStorage energy = new LongEnergyStorage(100, 100, null);
        energy.setAmount(20);
        CraftingContext context = context(List.of(first, output, new EnergyHatchCapability(energy, IOType.INPUT)), true, false);
        var result = context.planRequirements(List.of(inputRequirement(1), new EnergyRequirement(5),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_INGOT))), 1, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(energy.getAmountAsLong()).isEqualTo(15);
        assertThat(output.itemHandler().getStackInSlot(0).is(Items.GOLD_INGOT)).isTrue();
    }

    @Test
    void no_material_input_uses_ordinary_even_when_every_port_is_isolated() {
        CraftingContext context = context(List.of(input(1), input(1)), true, true);
        var result = context.planRequirements(List.of(), 1, Map.of());
        assertThat(result.plan().inputScope()).isEqualTo(new InputScope(InputScope.Kind.ORDINARY, List.of()));
    }

    @Test
    void fixed_output_plans_keep_pinned_scope_and_revision_while_using_shared_outputs() {
        ItemBusCapability output = new ItemBusCapability(new BulkItemStorage(64, null), IOType.OUTPUT);
        CraftingContext routed = context(List.of(input(1), output), true, false);
        InputScope scope = new InputScope(InputScope.Kind.ISOLATED, List.of(FIRST));
        CraftingContext pinned = new CraftingContext(routed.capabilitySnapshot(), List.of(), scope);
        var result = pinned.planOutputRequirements(List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT,
                null, 0, new ItemStack(Items.GOLD_INGOT))), 2, false);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2);
        assertThat(result.plan().inputScope()).isEqualTo(scope);
        assertThat(result.plan().inputRoutingRevision()).isEqualTo(7);
        assertThat(result.plan().commit()).isTrue();
        assertThat(amount(output)).isEqualTo(2);
    }

    @Test
    void pooled_reset_releases_scope_and_transient_views_but_preserves_new_snapshot_metadata() {
        CraftingContext original = context(List.of(input(1), input(1)), true, false);
        CraftingContext request = original.forPatternStart(List.of(input(1)), FIRST);
        assertThat(request.capabilitySnapshot().capabilities()).hasSize(3);
        request.resetFor(original.capabilitySnapshot(), List.of());
        assertThat(request.inputScope()).isNull();
        assertThat(request.capabilitySnapshot()).isSameAs(original.capabilitySnapshot());
        assertThat(request.inputRouting()).isSameAs(original.inputRouting());
        assertThat(request.capabilitySnapshot().capabilities()).hasSize(2);
    }

    @Test
    void unrouted_existing_contexts_keep_legacy_material_pooling() {
        CraftingContext legacy = new CraftingContext(new CapabilitySnapshot(List.of(input(1), input(1))));
        var result = legacy.planRequirements(List.of(inputRequirement(2)), 1, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().inputScope()).isEqualTo(InputScope.legacy());
    }

    @Test
    void failure_retains_the_original_index_of_the_furthest_group() {
        CraftingContext context = context(List.of(input(1), input(2)), true, true);
        var result = context.planFixedRequirements(List.of(inputRequirement(1), inputRequirement(1), inputRequirement(1)),
                1, Map.of(), List.of(4, 9, 12));
        assertThat(result.successful()).isFalse();
        assertThat(result.failureRequirementIndex()).isEqualTo(12);
    }

    @Test
    void pattern_request_combines_with_other_ordinary_physical_inputs_and_commits_once() {
        ItemBusCapability physical = input(1);
        ItemBusCapability supplied = input(1);
        CraftingContext context = context(List.of(physical, input(0)), false, false);
        CraftingContext request = context.forPatternStart(List.of(supplied), SECOND);
        assertThat(request.inputScope().kind()).isEqualTo(InputScope.Kind.ORDINARY);
        var result = request.planRequirements(List.of(inputRequirement(2)), 1, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(amount(physical)).isEqualTo(1);
        assertThat(amount(supplied)).isEqualTo(1);
        assertThat(result.plan().commitInputs()).isTrue();
        assertThat(amount(physical)).isZero();
        assertThat(amount(supplied)).isZero();
    }

    @Test
    void pattern_request_uses_its_selected_group_and_never_supplements_from_another_group() {
        CraftingContext context = context(List.of(input(1), input(20)), true, false);
        CraftingContext isolated = context.forPatternStart(List.of(input(1)), FIRST);
        assertThat(isolated.inputScope().sources()).containsExactly(FIRST);
        assertThat(isolated.planRequirements(List.of(inputRequirement(2)), 1, Map.of()).successful()).isTrue();
        assertThat(isolated.planRequirements(List.of(inputRequirement(3)), 1, Map.of()).successful()).isFalse();
        CraftingContext ordinary = context(List.of(input(20), input(0)), true, false)
                .forPatternStart(List.of(input(1)), SECOND);
        assertThat(ordinary.planRequirements(List.of(inputRequirement(2)), 1, Map.of()).successful()).isFalse();
        CraftingContext otherIsolated = context(List.of(input(0), input(20)), true, true)
                .forPatternStart(List.of(input(1)), FIRST);
        assertThat(otherIsolated.planRequirements(List.of(inputRequirement(2)), 1, Map.of()).successful()).isFalse();
        assertThat(context.forPatternStart(List.of(input(1)), new BlockPos(9, 9, 9))
                .planRequirements(List.of(inputRequirement(1)), 1, Map.of()).successful()).isFalse();
    }

    @Test
    void pattern_return_output_views_never_become_material_sources_despite_declared_bidirectionality() {
        ItemBusCapability stocked = input(20);
        MachineCapability returned = new ViewedCapability(new ItemBusCapability(stocked.itemHandler(), IOType.OUTPUT),
                CapabilityDirections.bidirectional(), List.of());
        CraftingContext context = context(List.of(returned, input(0)), false, false);
        CraftingContext request = context.forPatternStart(List.of(input(1), returned), SECOND);
        assertThat(returned.directions().supports(IOType.INPUT)).isTrue();
        assertThat(returned.view().directions().supports(IOType.INPUT)).isFalse();
        assertThat(request.inputRouting().sources()).extracting(InputRoutingFacet.Source::capabilityIndex)
                .containsExactly(1, 2);
        assertThat(request.planRequirements(List.of(inputRequirement(2)), 1, Map.of()).successful()).isFalse();
        assertThat(request.planRequirements(List.of(inputRequirement(1)), 1, Map.of()).successful()).isTrue();
        assertThat(RequirementPlanner.matchingCapabilities(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(Items.IRON_INGOT)), request.capabilitySnapshot().capabilities())).contains(returned);
        assertThat(amount(stocked)).isEqualTo(20);
    }

    @Test
    void probability_decisions_are_reused_by_original_index_across_scopes() {
        RequirementHandlerSupport.Sampling sampling = new RequirementHandlerSupport.Sampling();
        InputRoutingFacet routing = context(List.of(input(2), input(2)), true, true).inputRouting();
        PlanningContext base = new PlanningContext(16, 7, false, new PlanningReservations(),
                Map.of(7, OutputPolicy.REQUIRE_FULL), routing, null, sampling);
        PlanningContext first = base.forScope(new InputScope(InputScope.Kind.ISOLATED, List.of(FIRST)), sampling);
        PlanningContext second = base.forScope(new InputScope(InputScope.Kind.ISOLATED, List.of(SECOND)), sampling);
        var profile = RequirementHandlerSupport.consumeProfile(0.5F, 16, first);
        assertThat(RequirementHandlerSupport.consumeProfile(0.5F, 16, second)).isSameAs(profile);
        boolean output = RequirementHandlerSupport.shouldProduce(0.5F, first);
        assertThat(RequirementHandlerSupport.shouldProduce(output ? 0F : 1F, second)).isEqualTo(output);
        var later = second.forRequirement(8, second.reservations());
        assertThat(RequirementHandlerSupport.consumeProfile(0.5F, 16, later)).isNotSameAs(profile);
    }

    private static ItemRequirement inputRequirement(int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), count, ItemStack.EMPTY);
    }

    private static ItemBusCapability input(int amount) {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.insertItem(0, new ItemStack(Items.IRON_INGOT, amount), false);
        return new ItemBusCapability(storage, IOType.INPUT);
    }

    private static long amount(ItemBusCapability capability) {
        return ((LongItemStorage) capability.itemHandler()).amount(0);
    }

    private static CraftingContext context(List<MachineCapability> capabilities, boolean firstIsolated, boolean secondIsolated) {
        InputRoutingFacet routing = new InputRoutingFacet(7,
                List.of(new InputRoutingFacet.Source(0, FIRST), new InputRoutingFacet.Source(1, SECOND)),
                List.of(new InputRoutingFacet.Port(SECOND, secondIsolated), new InputRoutingFacet.Port(FIRST, firstIsolated)));
        return new CraftingContext(new CapabilitySnapshot(capabilities, List.of(routing)));
    }

    /**
     * Keeps real storage/operations while exposing independent structural tags and declared directions.
     * @author howxu <dev@howxu.cn>
     */
    private record ViewedCapability(ItemBusCapability delegate, CapabilityDirections directions, List<String> tags)
            implements MachineCapability {
        @Override
        public CapabilityType type() { return delegate.type(); }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() { return delegate.type(); }
                @Override
                public CapabilityDirections directions() { return delegate.view().directions(); }
                @Override
                public List<String> tags() { return tags; }
            };
        }

        @Override
        public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) { return delegate.facet(facetType); }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
    }
}
