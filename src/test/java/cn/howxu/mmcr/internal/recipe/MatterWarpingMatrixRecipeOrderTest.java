package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ae2lt.LightningBridgeBootstrap;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterizes the three declarations in the supplied MatterWarpingMatrixRecipes.java.
 * Vanilla stand-ins replace AE2LT items only: copper = collector, lapis = rain condensate,
 * amethyst = thunderstorm condensate. The commented-out pigmee_fumo is not a requirement.
 * No weather requirement is declared. Lower numeric priority wins, then more inputs, then ID.
 *
 * @author howxu <dev@howxu.cn>
 */
class MatterWarpingMatrixRecipeOrderTest {
    private static final Identifier POOL = MMCR.id("test_cube");
    private static final LightningTier HIGH = LightningTier.HIGH_VOLTAGE;
    private static final LightningTier EXTREME = LightningTier.EXTREME_HIGH_VOLTAGE;
    private static Item ROD;
    private static Item COLLECTOR;
    private static Item RAIN;
    private static Item THUNDER;
    private static List<Item> ALL_ITEMS;
    private ServerThreadTestScope server;
    private RequirementHandlerRegistry.TestScope requirements;
    private OutputRegistry.TestScope outputs;
    private List<MachineRecipe> recipes;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrapCapabilities();
        ROD = Items.LIGHTNING_ROD;
        COLLECTOR = Items.COPPER_INGOT;
        RAIN = Items.LAPIS_LAZULI;
        THUNDER = Items.AMETHYST_SHARD;
        ALL_ITEMS = List.of(ROD, COLLECTOR, RAIN, THUNDER);
    }

    @BeforeEach
    void setup() throws Exception {
        server = ServerThreadTestScope.open();
        requirements = RequirementHandlerRegistry.openTestScope();
        outputs = OutputRegistry.openTestScope();
        LightningBridgeBootstrap.installForTesting(LightningBridgeBootstrap.selectForTesting(true));
        LightningRecipeTypes.register();
        recipes = List.of(recipe("clear", 1, null, 10L, 1L, 0.2F),
                recipe("rain", 5, RAIN, 20L, 1L, 0.5F),
                recipe("thunder", 5, THUNDER, 50L, 5L, 1F));
    }

    @AfterEach
    void cleanup() throws Exception {
        RecipeRegistry.clearForTesting();
        outputs.close();
        requirements.close();
        LightningBridgeBootstrap.resetForTesting();
        server.close();
    }

    @ParameterizedTest(name = "{index}: inventory={0}, layout={1}")
    @MethodSource("inventoriesAndLayouts")
    void all_inventory_subsets_and_slot_orders_follow_priority_not_item_order(List<Item> inventory, Layout layout) {
        var capabilities = capabilities(inventory, layout, Environment.READY);
        boolean basePresent = inventory.contains(ROD) && inventory.contains(COLLECTOR)
                && layout != Layout.SEPARATE_ISOLATED_BUSES;
        assertSearches(capabilities, recipes, basePresent ? recipes.getFirst() : null);

        // Excluding clear exposes the equal-priority tie and missing-condensate fallback.
        MachineRecipe expected = !basePresent ? null : inventory.contains(RAIN) ? recipes.get(1)
                : inventory.contains(THUNDER) ? recipes.get(2) : null;
        assertSearches(capabilities, recipes.subList(1, 3), expected);
    }

    @ParameterizedTest
    @EnumSource(value = Environment.class, names = "EXTREME_FULL", mode = EnumSource.Mode.EXCLUDE)
    void energy_and_output_constraints_do_not_make_larger_priority_values_win(Environment environment) {
        for (List<Item> order : permutations(ALL_ITEMS)) {
            var capabilities = capabilities(order, Layout.SINGLE_BUS, environment);
            assertSearches(capabilities, recipes, environment == Environment.READY ? recipes.getFirst() : null);
        }
    }

    @Test
    void registration_order_and_candidate_index_preserve_clear_rain_thunder_order() {
        for (List<MachineRecipe> registrationOrder : permutations(recipes)) {
            RecipeRegistry.replaceDynamic(Map.of());
            for (MachineRecipe recipe : registrationOrder) {
                var registered = new LinkedHashMap<>(RecipeRegistry.dynamicSnapshot());
                registered.put(recipe.id(), recipe);
                RecipeRegistry.replaceDynamic(registered);
            }
            var catalog = RecipeRegistry.catalogForPool(POOL);
            assertThat(catalog.orderedRecipes()).containsExactlyElementsOf(recipes);
            for (List<Item> order : permutations(ALL_ITEMS)) {
                var candidates = catalog.inputIndex().candidates(order);
                assertThat(candidates).containsExactlyElementsOf(recipes);
                assertSearches(capabilities(order, Layout.SINGLE_BUS, Environment.READY), candidates, recipes.getFirst());
            }
        }
    }

    @Test
    void inserting_items_one_at_a_time_never_upgrades_clear_to_rain_or_thunder() {
        for (List<Item> arrivalOrder : permutations(ALL_ITEMS)) {
            for (int size = 0; size <= arrivalOrder.size(); size++) {
                List<Item> present = arrivalOrder.subList(0, size);
                MachineRecipe expected = present.contains(ROD) && present.contains(COLLECTOR) ? recipes.getFirst() : null;
                assertSearches(capabilities(present, Layout.SINGLE_BUS, Environment.READY), recipes, expected);
            }
        }
    }

    @Test
    void retained_items_survive_start_and_allow_the_same_recipe_to_match_again() {
        for (MachineRecipe recipe : recipes) {
            var items = itemStorage(ALL_ITEMS);
            var capabilities = new CapabilitySnapshot(List.of(new ItemBusCapability(items, IOType.INPUT),
                    energy(Environment.READY), new LightningCapability(lightningStorage(Environment.READY), IOType.OUTPUT)));
            for (int start = 0; start < 2; start++) {
                var plan = new CraftingContext(capabilities).planStartResult(recipe, 1L);
                assertThat(plan.successful()).isTrue();
                assertThat(plan.plan().commit()).isTrue();
                for (int slot = 0; slot < ALL_ITEMS.size(); slot++) {
                    assertThat(items.resource(slot)).isEqualTo(ItemResource.of(ALL_ITEMS.get(slot)));
                    assertThat(items.amount(slot)).isEqualTo(1L);
                }
            }
        }
    }

    @Test
    void full_extreme_output_can_select_clear_rain_or_nothing_depending_on_output_samples() {
        var capabilities = capabilities(ALL_ITEMS, Layout.SINGLE_BUS, Environment.EXTREME_FULL);
        var request = capture(capabilities, recipes);
        // Each output samples perturbation, then its fractional unit. Thunder always needs five EXTREME units.
        assertSampledSelection(request, List.of(0.5, 0.9), recipes.getFirst());
        assertSampledSelection(request, List.of(0.5, 0.1, 0.5, 0.9), recipes.get(1));
        assertSampledSelection(request, List.of(0.5, 0.1, 0.5, 0.1), null);
    }

    @Test
    void last_recipe_restart_does_not_reorder_candidates_until_a_full_search_is_requested() {
        var controller = RuntimeTestFixtures.controller(POOL);
        var host = new TestCapabilityHost(capabilities(ALL_ITEMS, Layout.SINGLE_BUS, Environment.READY));
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, host,
                BlockPos.ZERO, BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        var thread = new MachineRecipeThread(controller);
        RecipeRegistry.replaceDynamic(Map.of(recipes.get(0).id(), recipes.get(0),
                recipes.get(1).id(), recipes.get(1), recipes.get(2).id(), recipes.get(2)));
        for (MachineRecipe previous : recipes) {
            // Deliberately seed a previous recipe; rain/thunder are not reachable by a READY full search.
            assertThat(thread.searchAndStartRecipe(List.of(previous), 1L, controller.structureSnapshot().version()))
                    .isEqualTo(RecipeThread.StartResult.STARTED);
            thread.markRecipeFinished();
            thread.runtime().invalidate();
            assertThat(thread.peekRestartRecipe(recipes, 1L, controller.structureSnapshot().version())).isSameAs(previous);
            assertThat(thread.tryRestartLastRecipe(recipes, 1L, controller.structureSnapshot().version()))
                    .isEqualTo(RecipeThread.StartResult.STARTED);
            assertThat(thread.runtime().recipe()).isSameAs(previous);
            thread.runtime().invalidate();
            thread.requestFullSearch();
            assertThat(thread.searchAndStartRecipe(recipes, 1L, controller.structureSnapshot().version()))
                    .isEqualTo(RecipeThread.StartResult.STARTED);
            assertThat(thread.runtime().recipe()).isSameAs(recipes.getFirst());
            thread.runtime().invalidate();
        }
    }

    private static Stream<Arguments> inventoriesAndLayouts() {
        return IntStream.range(0, 1 << ALL_ITEMS.size()).boxed().flatMap(mask -> {
            List<Item> subset = IntStream.range(0, ALL_ITEMS.size()).filter(i -> (mask & (1 << i)) != 0)
                    .mapToObj(ALL_ITEMS::get).toList();
            return permutations(subset).stream().flatMap(order -> Stream.of(Layout.values())
                    .map(layout -> Arguments.of(order, layout)));
        });
    }

    private void assertSearches(CapabilitySnapshot capabilities, List<MachineRecipe> candidates, MachineRecipe expected) {
        var runtime = runtime();
        RecipeSearchResult sync = new RecipeSearchTask(runtime, POOL, POOL, 1L, 1L, candidates, capabilities).compute();
        assertSelection(sync, expected);
        var request = capture(capabilities, candidates);
        assertThat(request.snapshotValues().candidates()).allSatisfy(candidate -> assertThat(candidate.proofReason()).isNull());
        var outcome = CompletableFuture.supplyAsync(() -> request.newPlanner().advance()).join();
        var async = request.result(outcome);
        assertThat(async.failure()).isNull();
        assertThat(async.requiresMainThreadReplan()).isFalse();
        assertSelection(async.result(), expected);
        if (expected != null) {
            assertThat(async.result().planningResult().plan().inputScope()).isEqualTo(sync.planningResult().plan().inputScope());
        }
    }

    private static void assertSelection(RecipeSearchResult result, MachineRecipe expected) {
        assertThat(result.success()).isEqualTo(expected != null);
        assertThat(result.recipe()).isSameAs(expected);
        assertThat(result.hasMoreSpecificPendingInputCandidate()).isFalse();
    }

    private static void assertSampledSelection(AsyncRequirementPlanner.RecipeSearchRequest request,
                                               List<Double> samples, MachineRecipe expected) {
        var iterator = samples.iterator();
        var planner = new RecipeSearchPlanner(request.snapshotValues(), iterator::next);
        var result = request.result(CompletableFuture.supplyAsync(planner::advance).join());
        assertThat(result.failure()).isNull();
        assertSelection(result.result(), expected);
        assertThat(iterator.hasNext()).isFalse();
    }

    private static AsyncRequirementPlanner.RecipeSearchRequest capture(CapabilitySnapshot capabilities,
                                                                       List<MachineRecipe> candidates) {
        return AsyncRequirementPlanner.captureRecipeSearch(runtime(), candidates, 1L, capabilities,
                List.of(), 0L, new EffectiveRecipeSet.Cache());
    }

    private static MachineRecipe recipe(String suffix, int priority, Item condensate,
                                         long high, long extreme, float chance) {
        var builder = MachineRecipeBuilder.recipe(MMCR.id("matter_warping_matrix_" + suffix))
                .recipePool(POOL).priority(priority)
                .inputItem(Ingredient.of(ROD), 1, DataComponentPredicateSet.EMPTY, 0F)
                .inputItem(Ingredient.of(COLLECTOR), 1, DataComponentPredicateSet.EMPTY, 0F);
        if (condensate != null) builder.inputItem(Ingredient.of(condensate), 1, DataComponentPredicateSet.EMPTY, 0F);
        var definition = builder.duration(20).inputEnergy(20_000L).lightningOutput(HIGH, high)
                .lightningOutput(EXTREME, extreme, chance).build();
        return MachineRecipeConverter.toRecipe(definition,
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
    }

    private static CapabilitySnapshot capabilities(List<Item> inventory, Layout layout, Environment environment) {
        List<MachineCapability> capabilities = new ArrayList<>();
        List<InputRoutingFacet.Source> sources = new ArrayList<>();
        List<InputRoutingFacet.Port> ports = new ArrayList<>();
        boolean separate = layout == Layout.SHARED_BUSES || layout == Layout.SEPARATE_ISOLATED_BUSES;
        List<List<Item>> buses = separate ? inventory.stream().map(List::of).toList() : List.of(inventory);
        for (int i = 0; i < buses.size(); i++) {
            BlockPos position = new BlockPos(i + 1, 0, 0);
            sources.add(new InputRoutingFacet.Source(capabilities.size(), position));
            ports.add(new InputRoutingFacet.Port(position,
                    layout == Layout.SINGLE_ISOLATED_BUS || layout == Layout.SEPARATE_ISOLATED_BUSES));
            capabilities.add(new ItemBusCapability(itemStorage(buses.get(i)), IOType.INPUT));
        }
        capabilities.add(energy(environment));
        if (environment != Environment.NO_LIGHTNING_OUTPUT) {
            capabilities.add(new LightningCapability(lightningStorage(environment), IOType.OUTPUT));
        }
        return new CapabilitySnapshot(capabilities, List.of(new InputRoutingFacet(1L, sources, ports)));
    }

    private static LongResourceStorage<ItemResource> itemStorage(List<Item> items) {
        var storage = new LongResourceStorage<>(ItemResource.class, Math.max(1, items.size()), 64L,
                ItemResource::isEmpty, null);
        for (int slot = 0; slot < items.size(); slot++) storage.setContents(slot, ItemResource.of(items.get(slot)), 1L);
        return storage;
    }

    private static EnergyHatchCapability energy(Environment environment) {
        var storage = new LongValueStorage(1_000_000L,
                environment == Environment.LOW_ENERGY_TRANSFER ? 19_999L : 1_000_000L, null);
        storage.setAmount(environment == Environment.NO_ENERGY ? 0L : 1_000_000L);
        return new EnergyHatchCapability(storage, IOType.INPUT);
    }

    private static LongResourceStorage<LightningTier> lightningStorage(Environment environment) {
        var storage = new LongResourceStorage<>(LightningTier.class, 2, 100L, ignored -> false, null);
        storage.setContents(0, HIGH, environment == Environment.HIGH_FULL ? 100L : 0L);
        // Empty slots accept either tier, so block both slots for the HIGH_FULL scenario.
        storage.setContents(1, environment == Environment.HIGH_FULL ? HIGH : EXTREME,
                environment == Environment.HIGH_FULL || environment == Environment.EXTREME_FULL ? 100L : 0L);
        return storage;
    }

    private static ControllerRuntimeSnapshot runtime() {
        Machine machine = new Machine() {
            public Identifier registryName() { return POOL; }
            public BlockArray pattern() { return null; }
            public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(POOL); }
        };
        var structure = new StructureSnapshot(machine, machine, null, null, Direction.NORTH, Direction.SOUTH,
                1, true, 1, null, null, null, false, true, Set.of());
        return new ControllerRuntimeSnapshot(structure, 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.notRequired(), 0, null, null, List.of(), List.of(), List.of(),
                POOL.toString(), "", 0, false, false, 0, 0, 1L);
    }

    private static <T> List<List<T>> permutations(List<T> values) {
        if (values.isEmpty()) return List.of(List.of());
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            List<T> rest = new ArrayList<>(values);
            T first = rest.remove(i);
            for (List<T> suffix : permutations(rest)) {
                List<T> permutation = new ArrayList<>();
                permutation.add(first);
                permutation.addAll(suffix);
                result.add(List.copyOf(permutation));
            }
        }
        return result;
    }

    private enum Layout { SINGLE_BUS, SHARED_BUSES, SINGLE_ISOLATED_BUS, SEPARATE_ISOLATED_BUSES }
    private enum Environment { READY, NO_ENERGY, LOW_ENERGY_TRANSFER, HIGH_FULL, NO_LIGHTNING_OUTPUT, EXTREME_FULL }

    /** @author howxu <dev@howxu.cn> */
    private static final class TestCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        private TestCapabilityHost(CapabilitySnapshot snapshot) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            this.snapshot = snapshot;
        }

        public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
    }
}
