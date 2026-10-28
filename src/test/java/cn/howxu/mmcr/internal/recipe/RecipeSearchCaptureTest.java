package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Native capture versus the unchanged synchronous planner; live storage is guarded by thread identity.
 * @author howxu <dev@howxu.cn>
 */
class RecipeSearchCaptureTest {
    private ServerThreadTestScope server;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void installServer() throws Exception { server = ServerThreadTestScope.open(); }

    @AfterEach
    void restoreServer() throws Exception { server.close(); }

    @Test
    void reordered_alias_views_share_physical_cells_and_do_not_lose_resources() {
        Slot[] slots = {new Slot(ItemResource.of(Items.IRON_INGOT), 8L, 64L, null),
                new Slot(ItemResource.of(Items.GOLD_INGOT), 4L, 64L, null)};
        GuardedStorage first = new GuardedStorage(slots, 0, 1);
        GuardedStorage reversed = new GuardedStorage(slots, 1, 0);
        var capabilities = capabilities(new ItemBusCapability(first, IOType.INPUT), new ItemBusCapability(reversed, IOType.INPUT));
        assertEquivalent(capabilities, recipe(List.of(input(Items.IRON_INGOT, 1), input(Items.IRON_INGOT, 1)), false));
        assertEquivalent(capabilities, recipe(List.of(input(Items.GOLD_INGOT, 1)), false));
        assertThat(slots[0].amount()).isEqualTo(8L);
    }

    @Test
    void first_isolated_scope_wins_and_two_small_isolated_sources_cannot_pool() {
        var first = new GuardedStorage(new Slot[]{new Slot(ItemResource.of(Items.IRON_INGOT), 2L, 64L, null)}, 0);
        var second = new GuardedStorage(new Slot[]{new Slot(ItemResource.of(Items.IRON_INGOT), 8L, 64L, null)}, 0);
        BlockPos a = new BlockPos(1, 0, 0), b = new BlockPos(2, 0, 0);
        CapabilitySnapshot capabilities = new CapabilitySnapshot(List.of(new ItemBusCapability(first, IOType.INPUT),
                new ItemBusCapability(second, IOType.INPUT)), List.of(new InputRoutingFacet(2L,
                List.of(new InputRoutingFacet.Source(0, a), new InputRoutingFacet.Source(1, b)),
                List.of(new InputRoutingFacet.Port(a, true), new InputRoutingFacet.Port(b, true)))));
        assertEquivalent(capabilities, recipe(List.of(input(Items.IRON_INGOT, 1)), false));
        assertEquivalent(capabilities, recipe(List.of(input(Items.IRON_INGOT, 9)), false));
    }

    @Test
    void output_filters_and_shared_output_reservations_match_sync() {
        var output = new GuardedStorage(new Slot[]{new Slot(null, 0L, 3L, ItemResource.of(Items.GOLD_INGOT))}, 0);
        CapabilitySnapshot capabilities = capabilities(new ItemBusCapability(output, IOType.OUTPUT));
        assertEquivalent(capabilities, recipe(List.of(output(Items.IRON_INGOT, 1)), false));
        assertEquivalent(capabilities, recipe(List.of(output(Items.GOLD_INGOT, 2), output(Items.GOLD_INGOT, 2)), false));
        assertEquivalent(capabilities, recipe(List.of(output(Items.GOLD_INGOT, 4)), true));
        assertEquivalent(capabilities, recipe(List.of(output(Items.GOLD_INGOT, 4)), false));
    }

    @Test
    void direction_and_tags_are_not_replaced_by_routing_permissions() {
        var storage = new GuardedStorage(new Slot[]{new Slot(ItemResource.of(Items.IRON_INGOT), 8L, 64L, null)}, 0);
        assertEquivalent(capabilities(new ItemBusCapability(storage, IOType.OUTPUT)), recipe(List.of(input(Items.IRON_INGOT, 1)), false));
        var tagged = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY, 1F, List.of("missing-tag"));
        assertEquivalent(capabilities(new ItemBusCapability(storage, IOType.INPUT)), recipe(List.of(tagged), false));
    }

    @Test
    void fluid_and_energy_shared_pools_obey_the_same_parallelism_limits() {
        LongFluidStorage fluid = new LongFluidStorage(10_000L, null);
        try (var transaction = Transaction.openRoot()) {
            fluid.insert(0, FluidResource.of(Fluids.WATER), 2_500L, transaction);
            transaction.commit();
        }
        LongValueStorage energy = new LongValueStorage(100L, 10L, null);
        energy.setAmount(30L);
        var fluidInput = new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER),
                500, FluidStack.EMPTY, 1F, List.of());
        assertEquivalent(capabilities(new FluidHatchCapability(fluid, IOType.INPUT), new EnergyHatchCapability(energy, IOType.INPUT)),
                recipe(List.of(fluidInput, new EnergyRequirement(5L), new EnergyRequirement(5L)), false));
        assertEquivalent(capabilities(new EnergyHatchCapability(energy, IOType.INPUT)),
                recipe(List.of(new EnergyRequirement(11L)), false));
        // Recipe FE output deliberately ignores the external per-operation transfer limit.
        assertEquivalent(capabilities(new EnergyHatchCapability(new LongValueStorage(100L, 1L, null), IOType.OUTPUT)),
                recipe(List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 10L, List.of())), false));
    }

    @Test
    void repeated_candidates_capture_output_policies_only_once_per_storage_slot_and_resource() {
        var storage = new GuardedStorage(new Slot[]{new Slot(null, 0L, 64L, null)}, 0);
        var capabilities = capabilities(new ItemBusCapability(storage, IOType.OUTPUT));
        var recipes = new ArrayList<MachineRecipe>();
        for (int i = 0; i < 32; i++) recipes.add(recipe(List.of(output(Items.GOLD_INGOT, 1)), false).withId(MMCR.id("capture_" + i)));
        RecipeSearchSnapshot snapshot = capture(capabilities, recipes);
        var ports = snapshot.candidates().getFirst().rules().getFirst().ports();
        assertThat(snapshot.candidates()).allSatisfy(candidate ->
                assertThat(candidate.rules().getFirst().ports()).isSameAs(ports));
        assertThat(storage.validityCalls).hasValue(1);
        assertThat(CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(snapshot).advance()).join())
                .isInstanceOf(RecipeSearchPlanner.Match.class);
        assertThat(storage.validityCalls).hasValue(1);
    }

    @Test
    void distinct_item_predicates_reuse_decoded_stacks_only_within_one_capture() {
        var tested = new ArrayList<ItemStack>();
        var recipes = new ArrayList<MachineRecipe>();
        Thread owner = Thread.currentThread();
        for (int i = 0; i < 32; i++) {
            Ingredient ingredient = new Ingredient(new ICustomIngredient() {
                public boolean test(ItemStack stack) {
                    assertThat(Thread.currentThread()).isSameAs(owner);
                    tested.add(stack);
                    return stack.is(Items.IRON_INGOT);
                }
                public Stream<Holder<Item>> items() { return Stream.of(Items.IRON_INGOT.builtInRegistryHolder()); }
                public boolean isSimple() { return false; }
                public IngredientType<?> getType() { return null; }
            });
            recipes.add(recipe(List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ingredient, 1, ItemStack.EMPTY)),
                    false).withId(MMCR.id("predicate_" + i)));
        }
        Slot[] slots = {new Slot(ItemResource.of(Items.IRON_INGOT), 8L, 64L, null),
                new Slot(ItemResource.of(Items.GOLD_INGOT), 4L, 64L, null)};
        var capabilities = capabilities(new ItemBusCapability(new GuardedStorage(slots, 0, 1), IOType.INPUT));
        RecipeSearchSnapshot snapshot = capture(capabilities, recipes);
        Set<ItemStack> decoded = Collections.newSetFromMap(new IdentityHashMap<>());
        decoded.addAll(tested);
        assertThat(tested).hasSize(recipes.size() * slots.length);
        assertThat(decoded).hasSize(slots.length);
        assertThat(snapshot.candidates()).allSatisfy(candidate -> assertThat(candidate.rules().getFirst().resources()).hasSize(1));
        assertThat(CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(snapshot).advance()).join())
                .isInstanceOf(RecipeSearchPlanner.Match.class);
        assertThat(tested).hasSize(recipes.size() * slots.length);

        tested.clear();
        slots[0] = new Slot(ItemResource.of(Items.GOLD_INGOT), 8L, 64L, null);
        RecipeSearchSnapshot changed = capture(capabilities, recipes);
        assertThat(tested).noneMatch(decoded::contains);
        assertThat(CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(changed).advance()).join())
                .isInstanceOf(RecipeSearchPlanner.NoMatch.class);
    }

    @Test
    void distinct_fluid_predicates_reuse_decoded_stacks_only_within_one_capture() {
        var tested = new ArrayList<FluidStack>();
        var recipes = new ArrayList<MachineRecipe>();
        Thread owner = Thread.currentThread();
        for (int i = 0; i < 32; i++) {
            FluidIngredient ingredient = new FluidIngredient() {
                public boolean test(FluidStack stack) {
                    assertThat(Thread.currentThread()).isSameAs(owner);
                    tested.add(stack);
                    return stack.getFluid() == Fluids.WATER;
                }
                protected Stream<Holder<Fluid>> generateFluids() { return Stream.of(Fluids.WATER.builtInRegistryHolder()); }
                public boolean isSimple() { return false; }
                public FluidIngredientType<?> getType() { return null; }
                public int hashCode() { return System.identityHashCode(this); }
                public boolean equals(Object other) { return this == other; }
            };
            recipes.add(recipe(List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, ingredient,
                    100, FluidStack.EMPTY, 1F, List.of())), false).withId(MMCR.id("fluid_predicate_" + i)));
        }
        LongFluidStorage storage = new LongFluidStorage(10_000L, null);
        try (var transaction = Transaction.openRoot()) {
            storage.insert(0, FluidResource.of(Fluids.WATER), 1_000L, transaction);
            transaction.commit();
        }
        var capabilities = capabilities(new FluidHatchCapability(storage, IOType.INPUT));
        RecipeSearchSnapshot snapshot = capture(capabilities, recipes);
        assertThat(tested).hasSize(recipes.size());
        FluidStack decoded = tested.getFirst();
        assertThat(tested).allMatch(stack -> stack == decoded);
        assertThat(CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(snapshot).advance()).join())
                .isInstanceOf(RecipeSearchPlanner.Match.class);
        assertThat(tested).hasSize(recipes.size());
        tested.clear();
        capture(capabilities, recipes);
        assertThat(tested).hasSize(recipes.size()).noneMatch(stack -> stack == decoded);
    }

    @Test
    void repeated_candidates_reuse_slot_mapping_and_refresh_output_policies_on_the_next_capture() {
        Slot[] slots = {new Slot(null, 0L, 64L, ItemResource.of(Items.GOLD_INGOT))};
        var storage = new GuardedStorage(slots, 0);
        var capabilities = capabilities(new ItemBusCapability(storage, IOType.OUTPUT));
        var recipes = new ArrayList<MachineRecipe>();
        for (int i = 0; i < 32; i++) recipes.add(recipe(List.of(output(Items.GOLD_INGOT, 1)), false)
                .withId(MMCR.id("mapping_" + i)));
        RecipeSearchSnapshot snapshot = capture(capabilities, recipes);
        assertThat(storage.reservationSlotCalls).hasValue(slots.length);
        assertThat(storage.validityCalls).hasValue(slots.length);
        assertThat(CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(snapshot).advance()).join())
                .isInstanceOf(RecipeSearchPlanner.Match.class);

        slots[0] = new Slot(null, 0L, 64L, ItemResource.of(Items.IRON_INGOT));
        RecipeSearchSnapshot changed = capture(capabilities, recipes);
        assertThat(storage.reservationSlotCalls).hasValue(2 * slots.length);
        assertThat(storage.validityCalls).hasValue(2 * slots.length);
        assertThat(CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(changed).advance()).join())
                .isInstanceOf(RecipeSearchPlanner.NoMatch.class);
    }

    @Test
    void regular_input_layouts_are_shared_but_output_resources_and_occurrences_stay_distinct() {
        Slot[] slots = {new Slot(ItemResource.of(Items.IRON_INGOT), 8L, 64L, null),
                new Slot(ItemResource.of(Items.GOLD_INGOT), 8L, 64L, null), new Slot(null, 0L, 64L, null)};
        var storage = new GuardedStorage(slots, 0, 1, 2);
        var inputs = new ItemBusCapability(storage, IOType.INPUT);
        var capabilities = capabilities(inputs, inputs, new ItemBusCapability(storage, IOType.OUTPUT));
        var recipe = recipe(List.of(input(Items.IRON_INGOT, 1), input(Items.GOLD_INGOT, 2),
                output(Items.IRON_INGOT, 1), output(Items.GOLD_INGOT, 1)), false);
        var rules = capture(capabilities, List.of(recipe)).candidates().getFirst().rules();
        assertThat(rules.get(0).ports()).isSameAs(rules.get(1).ports());
        assertThat(rules.get(0).ports()).hasSize(6); // Both occurrences, including their empty slots.
        assertThat(rules.get(2).ports()).isNotSameAs(rules.get(3).ports());
        assertThat(rules.get(0).ports().get(0).pool()).isEqualTo(rules.get(0).ports().get(3).pool());
        assertThat(rules.get(0).ports().get(0).sourceIndex()).isNotEqualTo(rules.get(0).ports().get(3).sourceIndex());
        assertEquivalent(capabilities, recipe);
    }

    @Test
    void output_values_are_in_the_universe_even_when_all_physical_slots_are_empty() {
        var storage = new GuardedStorage(new Slot[]{new Slot(null, 0L, 64L, null)}, 0);
        var capabilities = capabilities(new ItemBusCapability(storage, IOType.OUTPUT), new ItemBusCapability(storage, IOType.INPUT));
        var recipe = recipe(List.of(output(Items.IRON_INGOT, 1), input(Items.IRON_INGOT, 1)), false);
        var rules = capture(capabilities, List.of(recipe)).candidates().getFirst().rules();
        assertThat(rules.get(1).resources()).containsExactly(rules.get(0).output());
        assertEquivalent(capabilities, recipe);
    }

    private static void assertEquivalent(CapabilitySnapshot capabilities, MachineRecipe recipe) {
        PlanningResult expected = new CraftingContext(capabilities).planStartResult(recipe, 16L);
        RecipeSearchSnapshot snapshot = capture(capabilities, List.of(recipe));
        var actual = CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(snapshot).advance()).join();
        if (expected.successful()) {
            assertThat(actual).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class, match -> {
                assertThat(match.parallelism()).isEqualTo(expected.plan().parallelism());
                assertThat(match.scope()).isEqualTo(expected.plan().inputScope());
            });
        } else {
            assertThat(actual).isInstanceOfSatisfying(RecipeSearchPlanner.NoMatch.class, failure ->
                    assertThat(failure.failures()).singleElement().satisfies(value ->
                            assertThat(value.reasonId()).isEqualTo(expected.failure().reason().id())));
        }
    }

    private static RecipeSearchSnapshot capture(CapabilitySnapshot capabilities, List<MachineRecipe> recipes) {
        ControllerRuntimeSnapshot runtime = new ControllerRuntimeSnapshot(StructureSnapshot.empty(), 0L, 0L, 0L,
                Map.of(), Map.of(), Set.of(), ModuleConnectionStatus.notRequired(), 0, null, null,
                List.of(), List.of(), List.of(), "", "", 0, false, false, 0, 0, 16L);
        var resolver = new EffectiveRecipeResolver();
        return RecipeSearchCapture.capture(runtime, 0L, 16L, recipes.stream()
                .map(recipe -> resolver.resolve(recipe, runtime, List.of())).toList(), capabilities, false);
    }

    private static CapabilitySnapshot capabilities(MachineCapability... capabilities) {
        return new CapabilitySnapshot(List.of(capabilities));
    }

    private static MachineRecipe recipe(List<MachineRequirement> requirements, boolean partial) {
        return MachineRecipe.fromCanonical(MMCR.id("capture_oracle"), MMCR.id("capture_pool"), 20,
                requirements, List.of(), List.of(), 0, 1, false, true, partial, Set.of());
    }

    private static ItemRequirement input(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY);
    }

    private static ItemRequirement output(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(item, count));
    }

    /** @author howxu <dev@howxu.cn> */
    private record Slot(ItemResource resource, long amount, long capacity, ItemResource allowed) { }

    /** Immutable, reordered physical views whose reads must stay on the capturing test thread.
     * @author howxu <dev@howxu.cn>
     */
    private static final class GuardedStorage implements ResourceStorage<ItemResource> {
        private final Thread owner = Thread.currentThread();
        private final Slot[] slots;
        private final int[] mapping;
        private final AtomicInteger validityCalls = new AtomicInteger();
        private final AtomicInteger reservationSlotCalls = new AtomicInteger();

        GuardedStorage(Slot[] slots, int... mapping) { this.slots = slots; this.mapping = mapping; }
        private Slot slot(int index) { assertThat(Thread.currentThread()).isSameAs(owner); return slots[mapping[index]]; }
        public Class<ItemResource> resourceType() { return ItemResource.class; }
        public int size() { assertThat(Thread.currentThread()).isSameAs(owner); return mapping.length; }
        public Object reservationIdentity() { return slots; }
        public Object reservationSlot(int index) { reservationSlotCalls.incrementAndGet(); return mapping[index]; }
        public ItemResource resource(int index) { return slot(index).resource(); }
        public long amount(int index) { return slot(index).amount(); }
        public long capacity(int index, ItemResource resource) { return slot(index).capacity(); }
        public boolean isValid(int index, ItemResource resource) {
            validityCalls.incrementAndGet();
            return slot(index).allowed() == null || slot(index).allowed().equals(resource);
        }
        public long insert(int index, ItemResource resource, long amount, TransactionContext transaction) {
            throw new AssertionError("Search must not pay or produce resources");
        }
        public long extract(int index, ItemResource resource, long amount, TransactionContext transaction) {
            throw new AssertionError("Search must not pay or produce resources");
        }
    }
}
