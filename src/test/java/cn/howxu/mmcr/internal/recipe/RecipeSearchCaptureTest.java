package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
        Slot[] slots = {new Slot(new ItemStack(Items.IRON_INGOT), 8L, 64L, null),
                new Slot(new ItemStack(Items.GOLD_INGOT), 4L, 64L, null)};
        GuardedStorage first = new GuardedStorage(slots, 0, 1);
        GuardedStorage reversed = new GuardedStorage(slots, 1, 0);
        var capabilities = capabilities(new ItemBusCapability(first, IOType.INPUT), new ItemBusCapability(reversed, IOType.INPUT));
        assertEquivalent(capabilities, recipe(List.of(input(Items.IRON_INGOT, 1), input(Items.IRON_INGOT, 1)), false));
        assertEquivalent(capabilities, recipe(List.of(input(Items.GOLD_INGOT, 1)), false));
        assertThat(slots[0].amount()).isEqualTo(8L);
    }

    @Test
    void first_isolated_scope_wins_and_two_small_isolated_sources_cannot_pool() {
        var first = new GuardedStorage(new Slot[]{new Slot(new ItemStack(Items.IRON_INGOT), 2L, 64L, null)}, 0);
        var second = new GuardedStorage(new Slot[]{new Slot(new ItemStack(Items.IRON_INGOT), 8L, 64L, null)}, 0);
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
        var output = new GuardedStorage(new Slot[]{new Slot(null, 0L, 3L, new ItemStack(Items.GOLD_INGOT))}, 0);
        CapabilitySnapshot capabilities = capabilities(new ItemBusCapability(output, IOType.OUTPUT));
        assertEquivalent(capabilities, recipe(List.of(output(Items.IRON_INGOT, 1)), false));
        assertEquivalent(capabilities, recipe(List.of(output(Items.GOLD_INGOT, 2), output(Items.GOLD_INGOT, 2)), false));
        assertEquivalent(capabilities, recipe(List.of(output(Items.GOLD_INGOT, 4)), true));
        assertEquivalent(capabilities, recipe(List.of(output(Items.GOLD_INGOT, 4)), false));
    }

    @Test
    void direction_and_tags_are_not_replaced_by_routing_permissions() {
        var storage = new GuardedStorage(new Slot[]{new Slot(new ItemStack(Items.IRON_INGOT), 8L, 64L, null)}, 0);
        assertEquivalent(capabilities(new ItemBusCapability(storage, IOType.OUTPUT)), recipe(List.of(input(Items.IRON_INGOT, 1)), false));
        var tagged = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY, 1F, List.of("missing-tag"));
        assertEquivalent(capabilities(new ItemBusCapability(storage, IOType.INPUT)), recipe(List.of(tagged), false));
    }

    @Test
    void fluid_and_energy_shared_pools_obey_the_same_parallelism_limits() {
        LongFluidStorage fluid = new LongFluidStorage(10_000L, null);
        fluid.forceInsert(0, new FluidStack(Fluids.WATER, 1), 2_500L, false);
        LongEnergyStorage energy = new LongEnergyStorage(100L, 10L, null);
        energy.setAmount(30L);
        var fluidInput = new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER),
                500, FluidStack.EMPTY, 1F, List.of());
        assertEquivalent(capabilities(new FluidHatchCapability(fluid, IOType.INPUT), new EnergyHatchCapability(energy, IOType.INPUT)),
                recipe(List.of(fluidInput, new EnergyRequirement(5L), new EnergyRequirement(5L)), false));
        assertEquivalent(capabilities(new EnergyHatchCapability(energy, IOType.INPUT)),
                recipe(List.of(new EnergyRequirement(11L)), false));
        // Recipe FE output deliberately ignores the external per-operation transfer limit.
        assertEquivalent(capabilities(new EnergyHatchCapability(new LongEnergyStorage(100L, 1L, null), IOType.OUTPUT)),
                recipe(List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 10L, List.of())), false));
    }

    @Test
    void repeated_candidates_capture_output_policies_only_once_per_storage_slot_and_resource() {
        var storage = new GuardedStorage(new Slot[]{new Slot(null, 0L, 64L, null)}, 0);
        var capabilities = capabilities(new ItemBusCapability(storage, IOType.OUTPUT));
        var recipes = new ArrayList<MachineRecipe>();
        for (int i = 0; i < 32; i++) recipes.add(recipe(List.of(output(Items.GOLD_INGOT, 1)), false).withId(MMCR.id("capture_" + i)));
        RecipeSearchSnapshot snapshot = capture(capabilities, recipes);
        assertThat(storage.validityCalls).hasValue(1);
        assertThat(CompletableFuture.supplyAsync(() -> new RecipeSearchPlanner(snapshot).advance()).join())
                .isInstanceOf(RecipeSearchPlanner.Match.class);
        assertThat(storage.validityCalls).hasValue(1);
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
    private record Slot(ItemStack resource, long amount, long capacity, ItemStack allowed) { }

    /** Immutable, reordered physical views whose reads must stay on the capturing test thread.
     * @author howxu <dev@howxu.cn>
     */
    private static final class GuardedStorage implements IItemHandler, NativeReservationAccess, NativeStackSync.Item {
        private final Thread owner = Thread.currentThread();
        private final Slot[] slots;
        private final int[] mapping;
        private final AtomicInteger validityCalls = new AtomicInteger();

        GuardedStorage(Slot[] slots, int... mapping) { this.slots = slots; this.mapping = mapping; }
        private Slot slot(int index) { assertThat(Thread.currentThread()).isSameAs(owner); return slots[mapping[index]]; }
        public int getSlots() { assertThat(Thread.currentThread()).isSameAs(owner); return mapping.length; }
        public Object reservationIdentity() { return slots; }
        public Object reservationSlot(int index) { return mapping[index]; }
        public ItemStack getStackInSlot(int index) {
            Slot slot = slot(index);
            return slot.resource() == null ? ItemStack.EMPTY : slot.resource().copyWithCount((int) slot.amount());
        }
        public Object resourceKey(Object resource) { return resource; }
        public Object resource(Object key) { return key == null ? ItemStack.EMPTY : key; }
        public Object storedKey(int index) { return slot(index).resource(); }
        public long storedAmount(int index) { return amount(index); }
        public long amount(int index) { return slot(index).amount(); }
        public long capacity(int index) { return slot(index).capacity(); }
        public boolean isItemValid(int index, ItemStack resource) {
            validityCalls.incrementAndGet();
            return slot(index).allowed() == null || ItemStack.isSameItemSameComponents(slot(index).allowed(), resource);
        }
        public int getSlotLimit(int index) { return (int) capacity(index); }
        public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
            throw new AssertionError("Search must not pay or produce resources");
        }
        public ItemStack extractItem(int index, int amount, boolean simulate) {
            throw new AssertionError("Search must not pay or produce resources");
        }
        public void setContents(int index, ItemStack stack, long amount) {
            throw new AssertionError("Search must not mutate resources");
        }
    }
}
