package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.SmartInterfaceCapability;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.extension.CapabilityAccess;
import cn.howxu.mmcr.publicapi.recipe.extension.OperationBatch;
import cn.howxu.mmcr.publicapi.recipe.extension.OperationResult;
import cn.howxu.mmcr.publicapi.recipe.extension.RecipeOperation;
import cn.howxu.mmcr.publicapi.recipe.extension.ResourceAction;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementExecution;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementExtension;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementKind;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementKinds;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Public registration through canonical recipes and the production planner, never direct handler calls.
 * @author howxu <dev@howxu.cn>
 */
class PublicExtensionProductionTest {
    private static final ResourceLocation ENERGY = ResourceLocation.parse("neoforge:energy");
    private static final ResourceLocation ITEM = ResourceLocation.parse("minecraft:item");
    private final RequirementPlanner productionRequirementPlanner = new RequirementPlanner();
    private RequirementHandlerRegistry.TestScope scope;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void openRegistry() {
        scope = RequirementHandlerRegistry.openTestScope();
        RequirementHandlerRegistry.registerBuiltIns();
    }

    @AfterEach
    void closeRegistry() { scope.close(); }

    @Test
    void external_compiled_energy_extension_consumes_total_at_final_parallelism() throws Exception {
        try (var loader = fixtureLoader()) {
            var requirement = fixtureRequirement(loader, 4);
            var storage = energy(12);
            var result = simulate(List.of(requirement), List.of(energyCapability(storage, IOType.INPUT, "power")), 3);
            assertThat(result.successful()).isTrue();
            assertThat(result.plan().parallelism()).isEqualTo(3);
            assertThat(storage.getAmountAsLong()).isEqualTo(12);
            assertThat(result.plan().commit()).isTrue();
            assertThat(storage.getAmountAsLong()).isZero();
        }
    }

    @Test
    void external_energy_extension_wakeup_uses_existing_family_id_and_failure_reason() throws Exception {
        try (var loader = fixtureLoader()) {
            var requirement = fixtureRequirement(loader, 4);
            var blocked = simulate(List.of(requirement), List.of(energyCapability(energy(0), IOType.INPUT, "power")), 1);
            assertThat(blocked.successful()).isFalse();
            assertThat(blocked.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
            var wakeups = RequirementHandlerRegistry.resourceWakeupsFor(RequirementAdapters.unwrap(requirement));
            assertThat(wakeups).hasSize(1);
            var wakeup = wakeups.getFirst();
            assertThat(wakeup.failureReasonIds()).contains(blocked.failure().reason().id());
            assertThat(wakeup.matcher().test(new CapabilityType(ENERGY))).isTrue();
            assertThat(wakeup.matcher().test(new CapabilityType(ITEM))).isFalse();
            assertThat(wakeup.matcher().test(new ItemStack(Items.IRON_INGOT))).isFalse();
        }
    }

    @Test
    void external_energy_extension_shares_builtin_reservations_and_retains_native_commits_after_failure() throws Exception {
        try (var loader = fixtureLoader()) {
            var requirement = fixtureRequirement(loader, 4);
            var storage = energy(12);
            var capabilities = List.of(energyCapability(storage, IOType.INPUT, "power"));
            var shared = simulate(List.of(requirement, Requirements.energy(4, List.of("power"))), capabilities, 3);
            assertThat(shared.successful()).isTrue();
            assertThat(shared.plan().parallelism()).isEqualTo(1);
            assertThat(storage.getAmountAsLong()).isEqualTo(12);
            assertThat(shared.plan().commit()).isTrue();
            assertThat(storage.getAmountAsLong()).isEqualTo(4);

            storage.setAmount(12);
            var rollback = simulate(List.of(requirement, failingRequirement()), capabilities, 3);
            assertThat(rollback.successful()).isTrue();
            assertThat(rollback.plan().parallelism()).isEqualTo(3);
            assertThat(storage.getAmountAsLong()).isEqualTo(12);
            assertThat(rollback.plan().commit()).isFalse();
            assertThat(rollback.plan().failure()).isNotNull();
            assertThat(storage.getAmountAsLong()).isZero();
        }
    }

    @Test
    void selector_snapshot_routes_energy_and_item_but_core_still_filters_direction_and_tags() {
        Set<ResourceLocation> selected = new HashSet<>(Set.of(ENERGY, ITEM));
        var seen = new ArrayList<List<ResourceLocation>>();
        var kind = register("mixed", selected, (value, planning) -> {
            seen.add(planning.capabilities().stream().map(CapabilityAccess::kindId).toList());
            return planning.plan(value.amount() == 4
                    ? Requirements.energy(value.io(), value.amount(), value.tags())
                    : itemRequirement(value.io(), value.amount(), value.tags()));
        });
        selected.clear(); // Mutating the declaration after registration must not change the canonical selector.
        var power = energy(8);
        var wrongEnergyDirection = energy(100);
        var wrongEnergyTags = energy(100);
        var items = items(6);
        var wrongItemDirection = items(40);
        var wrongItemTags = items(40);
        List<MachineCapability> capabilities = List.of(
                energyCapability(wrongEnergyDirection, IOType.OUTPUT, "shared"),
                energyCapability(wrongEnergyTags, IOType.INPUT, "other"),
                itemCapability(wrongItemDirection, IOType.OUTPUT, "shared"),
                itemCapability(wrongItemTags, IOType.INPUT, "other"),
                energyCapability(power, IOType.INPUT, "shared"),
                itemCapability(items, IOType.INPUT, "shared"));
        var energyRequirement = Requirements.extension(kind, new Payload(4, IoDirection.INPUT, List.of("shared")));
        var itemRequirement = Requirements.extension(kind, new Payload(3, IoDirection.INPUT, List.of("shared")));
        var result = simulate(List.of(energyRequirement, itemRequirement), capabilities, 3);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2);
        assertThat(seen).containsExactly(List.of(ENERGY, ITEM), List.of(ENERGY, ITEM));
        assertThat(power.getAmountAsLong()).isEqualTo(8);
        assertThat(items.amount(0)).isEqualTo(6);
        assertThat(result.plan().commit()).isTrue();
        assertThat(power.getAmountAsLong()).isZero();
        assertThat(items.amount(0)).isZero();
        assertThat(wrongEnergyDirection.getAmountAsLong()).isEqualTo(100);
        assertThat(wrongEnergyTags.getAmountAsLong()).isEqualTo(100);
        assertThat(wrongItemDirection.amount(0)).isEqualTo(40);
        assertThat(wrongItemTags.amount(0)).isEqualTo(40);

        // Nested plan reuses the core matcher: the item family cannot fulfill a nested energy requirement.
        assertThat(simulate(List.of(energyRequirement),
                List.of(itemCapability(items(20), IOType.INPUT, "shared")), 1).successful()).isFalse();
        assertThat(simulate(List.of(itemRequirement),
                List.of(energyCapability(energy(20), IOType.INPUT, "shared")), 1).successful()).isFalse();
        assertThat(simulate(List.of(energyRequirement), capabilities.subList(0, 4), 1).successful()).isFalse();
    }

    @Test
    void item_extension_and_builtin_input_use_the_same_reservation_budget() {
        var kind = register("items", Set.of(ITEM), (value, planning) ->
                planning.plan(itemRequirement(value.io(), value.amount(), value.tags())));
        var storage = items(8);
        var extension = Requirements.extension(kind, new Payload(3, IoDirection.INPUT, List.of("parts")));
        var result = simulate(List.of(extension, itemRequirement(IoDirection.INPUT, 3, List.of("parts"))),
                List.of(itemCapability(storage, IOType.INPUT, "parts")), 3);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(storage.amount(0)).isEqualTo(8);
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(2);
    }

    @Test
    void nested_output_planning_obeys_core_policy_direction_and_tags() {
        var kind = register("output", Set.of(ITEM), (value, planning) ->
                planning.plan(itemRequirement(value.io(), value.amount(), value.tags())));
        var output = Requirements.extension(kind, new Payload(4, IoDirection.OUTPUT, List.of("sink")));
        var storage = new BulkItemStorage(2, null);
        var wrongTags = new BulkItemStorage(64, null);
        var wrongDirection = new BulkItemStorage(64, null);
        List<MachineCapability> capabilities = List.of(itemCapability(wrongTags, IOType.OUTPUT, "other"),
                itemCapability(wrongDirection, IOType.INPUT, "sink"), itemCapability(storage, IOType.OUTPUT, "sink"));
        var recipe = canonicalRecipe(List.of(output));
        var full = productionRequirementPlanner.plan(recipe.runtimeRequirements(), capabilities,
                new PlanningContext(1, 0, Map.of(0, OutputPolicy.REQUIRE_FULL)));
        assertThat(full.successful()).isFalse();
        assertThat(storage.amount(0)).isZero();
        var partial = productionRequirementPlanner.plan(recipe.runtimeRequirements(), capabilities,
                new PlanningContext(1, 0, Map.of(0, OutputPolicy.ALLOW_PARTIAL)));
        assertThat(partial.successful()).isTrue();
        assertThat(storage.amount(0)).isZero();
        assertThat(partial.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(2);
        assertThat(wrongTags.amount(0)).isZero();
        assertThat(wrongDirection.amount(0)).isZero();
    }

    @Test
    void native_smart_assignment_is_parallelism_independent_and_reusable_through_public_bridge() {
        var smartId = ResourceLocation.parse("mmcr:smart_interface");
        var kind = register("native_smart", Set.of(smartId), (value, planning) -> planning.prepared(
                planning.requestedParallelism(), List.of(planning.capabilities().getFirst().prepareSmartValue(
                        value.io(), planning.requestedParallelism(), "mode", value.amount()))));
        var smart = new FloatValueStorage();
        smart.set("mode", 0);
        var limiter = energy(10);
        var result = simulate(List.of(Requirements.extension(kind, new Payload(4, IoDirection.INPUT, List.of())),
                Requirements.energy(10, List.of("limit"))),
                List.of(new SmartInterfaceCapability(smart, IOType.INPUT),
                        energyCapability(limiter, IOType.INPUT, "limit")), 3);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(smart.value("mode")).contains(0F);
        assertThat(result.plan().commit()).isTrue();
        assertThat(smart.value("mode")).contains(4F);
        assertThat(limiter.getAmountAsLong()).isZero();
    }

    @Test
    void native_prepared_energy_rejects_changed_parallelism_but_commits_and_rolls_back_at_same_parallelism() {
        var kind = register("native_prepared", Set.of(ENERGY), (value, planning) -> planning.prepared(
                planning.requestedParallelism(), List.of(planning.capabilities().getFirst().prepareValue(value.io(),
                        planning.requestedParallelism(), Math.multiplyExact(value.amount(), planning.requestedParallelism()), false))));
        var power = energy(12);
        var limiter = energy(10);
        var rejected = limited(kind, power, limiter);
        assertThat(rejected.successful()).isFalse();
        assertThat(rejected.plan()).isNull();
        assertThat(rejected.failureRequirementIndex()).isZero();
        assertThat(rejected.failure().reason()).isEqualTo(BuiltinFailureReasons.UNSAFE_OPERATION_PARALLELISM);
        assertThat(power.getAmountAsLong()).isEqualTo(12);
        assertThat(limiter.getAmountAsLong()).isEqualTo(10);

        var requirement = Requirements.extension(kind, new Payload(4, IoDirection.INPUT, List.of("power")));
        var capabilities = List.of(energyCapability(power, IOType.INPUT, "power"),
                energyCapability(limiter, IOType.INPUT, "limit"));
        var same = simulate(List.of(requirement), capabilities, 3);
        assertThat(same.successful()).isTrue();
        assertThat(same.plan().parallelism()).isEqualTo(3);
        assertThat(same.plan().commit()).isTrue();
        assertThat(power.getAmountAsLong()).isZero();

        power.setAmount(12);
        var rollback = simulate(List.of(requirement, Requirements.energy(3, List.of("limit")), failingRequirement()),
                capabilities, 3);
        assertThat(rollback.successful()).isTrue();
        assertThat(rollback.plan().parallelism()).isEqualTo(3);
        assertThat(rollback.plan().commit()).isFalse();
        assertThat(power.getAmountAsLong()).isZero();
        assertThat(limiter.getAmountAsLong()).isEqualTo(1);
    }

    @Test
    void native_prepared_resources_reject_absolute_quantity_rescale_through_public_bridge() {
        var kind = register("native_items", Set.of(ITEM), (value, planning) -> planning.prepared(
                planning.requestedParallelism(), List.of(planning.capabilities().getFirst().prepareResources(value.io(),
                        planning.requestedParallelism(), List.of(new ResourceAction<>(0,
                                new ItemStack(Items.IRON_INGOT),
                                Math.multiplyExact(value.amount(), planning.requestedParallelism()), false))))));
        var parts = items(12);
        var limiter = energy(10);
        var requirement = Requirements.extension(kind, new Payload(4, IoDirection.INPUT, List.of("parts")));
        var capabilities = List.of(itemCapability(parts, IOType.INPUT, "parts"),
                energyCapability(limiter, IOType.INPUT, "limit"));
        var rejected = simulate(List.of(requirement, Requirements.energy(10, List.of("limit"))), capabilities, 3);
        assertThat(rejected.successful()).isFalse();
        assertThat(rejected.failure().reason()).isEqualTo(BuiltinFailureReasons.UNSAFE_OPERATION_PARALLELISM);
        assertThat(parts.amount(0)).isEqualTo(12);
        assertThat(limiter.getAmountAsLong()).isEqualTo(10);
        var same = simulate(List.of(requirement), capabilities, 3);
        assertThat(same.successful()).isTrue();
        assertThat(same.plan().commit()).isTrue();
        assertThat(parts.amount(0)).isZero();
    }

    @Test
    void prepared_lambda_rejects_three_to_one_rescale_without_mutating_either_store() {
        var calls = new AtomicInteger();
        var kind = register("unsafe", Set.of(ENERGY), (value, planning) -> {
            var access = planning.capabilities().getFirst();
            var captured = access.prepareValue(value.io(), planning.requestedParallelism(),
                    Math.multiplyExact(value.amount(), planning.requestedParallelism()), false);
            return planning.prepared(planning.requestedParallelism(), List.of(() -> {
                calls.incrementAndGet();
                return captured.commit();
            }));
        });
        var power = energy(12);
        var limiter = energy(10);
        var result = limited(kind, power, limiter);
        assertThat(result.successful()).isFalse();
        assertThat(result.plan()).isNull();
        assertThat(result.failureRequirementIndex()).isEqualTo(0);
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.UNSAFE_OPERATION_PARALLELISM);
        assertThat(calls).hasValue(0);
        assertThat(power.getAmountAsLong()).isEqualTo(12);
        assertThat(limiter.getAmountAsLong()).isEqualTo(10);
    }

    @Test
    void explicit_public_rescaler_executes_exactly_one_batch() {
        var kind = register("safe", Set.of(ENERGY), (value, planning) -> planning.prepared(
                planning.requestedParallelism(), List.of(rescalable(planning.capabilities().getFirst(),
                        value.amount(), planning.requestedParallelism()))));
        var power = energy(12);
        var limiter = energy(10);
        var result = limited(kind, power, limiter);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(power.getAmountAsLong()).isEqualTo(12);
        assertThat(result.plan().commit()).isTrue();
        assertThat(power.getAmountAsLong()).isEqualTo(8);
        assertThat(limiter.getAmountAsLong()).isZero();
    }

    @Test
    void deferred_factory_builds_once_at_final_parallelism_and_later_failure_retains_native_commits() {
        var builds = new AtomicInteger();
        var actualParallelism = new AtomicLong();
        var kind = register("deferred", Set.of(ENERGY), (value, planning) -> planning.deferred(
                planning.requestedParallelism(), (parallelism, reservations) -> {
                    builds.incrementAndGet();
                    actualParallelism.set(parallelism);
                    return new OperationBatch(List.of(planning.capabilities().getFirst().prepareValue(value.io(),
                            parallelism, Math.multiplyExact(parallelism, value.amount()), false)), null);
                }, null));
        var power = energy(12);
        var limiter = energy(10);
        var result = limited(kind, power, limiter);
        assertThat(result.successful()).isTrue();
        assertThat(builds).hasValue(1);
        assertThat(actualParallelism).hasValue(1);
        assertThat(power.getAmountAsLong()).isEqualTo(12);
        assertThat(result.plan().commit()).isTrue();
        assertThat(power.getAmountAsLong()).isEqualTo(8);
        assertThat(limiter.getAmountAsLong()).isZero();

        power.setAmount(12);
        limiter.setAmount(10);
        builds.set(0);
        var rollback = simulate(List.of(Requirements.extension(kind, new Payload(4, IoDirection.INPUT, List.of("power"))),
                Requirements.energy(10, List.of("limit")), failingRequirement()),
                List.of(energyCapability(power, IOType.INPUT, "power"), energyCapability(limiter, IOType.INPUT, "limit")), 3);
        assertThat(rollback.successful()).isTrue();
        assertThat(builds).hasValue(1);
        assertThat(actualParallelism).hasValue(1);
        assertThat(rollback.plan().commit()).isFalse();
        assertThat(power.getAmountAsLong()).isEqualTo(8);
        assertThat(limiter.getAmountAsLong()).isZero();
    }

    @Test
    void core_operation_rescaler_survives_both_spi_bridges() {
        var kind = register("core_rescale", Set.of(ENERGY), (value, planning) -> planning.prepared(
                planning.requestedParallelism(), List.of(planning.capabilities().getFirst().prepareValue(value.io(),
                        planning.requestedParallelism(), Math.multiplyExact(value.amount(), planning.requestedParallelism()), false))));
        var power = energy(12);
        var limiter = energy(10);
        MachineCapability scalable = new TaggedCapability(new EnergyHatchCapability(power, IOType.INPUT), List.of("power")) {
            @Override public CapabilityOperation prepare(CapabilityRequest request) {
                var value = (CapabilityRequests.ValueRequest) request;
                return coreRescalable(delegate, value.amount() / value.parallelism(), value.parallelism());
            }
        };
        var result = simulate(List.of(Requirements.extension(kind, new Payload(4, IoDirection.INPUT, List.of("power"))),
                Requirements.energy(10, List.of("limit"))),
                List.of(scalable, energyCapability(limiter, IOType.INPUT, "limit")), 3);
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().commit()).isTrue();
        assertThat(power.getAmountAsLong()).isEqualTo(8);
        assertThat(limiter.getAmountAsLong()).isZero();
    }

    private PlanningResult limited(RequirementKind<Payload> kind, LongEnergyStorage power, LongEnergyStorage limiter) {
        return simulate(List.of(Requirements.extension(kind, new Payload(4, IoDirection.INPUT, List.of("power"))),
                Requirements.energy(10, List.of("limit"))),
                List.of(energyCapability(power, IOType.INPUT, "power"), energyCapability(limiter, IOType.INPUT, "limit")), 3);
    }

    private PlanningResult simulate(List<RequirementSpec> requirements, List<MachineCapability> capabilities, long parallelism) {
        var recipe = canonicalRecipe(requirements);
        return productionRequirementPlanner.plan(recipe.runtimeRequirements(), capabilities, new PlanningContext(parallelism, 0));
    }

    private static MachineRecipe canonicalRecipe(List<RequirementSpec> requirements) {
        var draft = Recipes.recipe(id("recipe")).recipePool(id("pool"));
        requirements.forEach(draft::requirement);
        return MachineRecipeConverter.toRecipe(RecipeAdapters.unwrap(draft.build()),
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
    }

    private static RequirementKind<Payload> register(String path, Set<ResourceLocation> selector, RequirementExecution<Payload> execution) {
        return RequirementKinds.register(new RequirementExtension<>() {
            public ResourceLocation id() { return PublicExtensionProductionTest.id(path); }
            public Set<ResourceLocation> capabilityIds() { return selector; }
            public MapCodec<Payload> codec() { return Payload.CODEC; }
            public IoDirection io(Payload value) { return value.io(); }
            public List<String> tags(Payload value) { return value.tags(); }
            public Payload copy(Payload value) { return new Payload(value.amount(), value.io(), value.tags()); }
            public RequirementExecution<Payload> execution() { return execution; }
        });
    }

    private static RequirementSpec failingRequirement() {
        var kind = register("failure", Set.of(), (value, planning) -> planning.deferred(planning.requestedParallelism(),
                (parallelism, reservations) -> new OperationBatch(List.of(() -> OperationResult.failure(
                        planning.failure(id("later_failure"), id("failure"), Map.of()))), null), null));
        return Requirements.extension(kind, new Payload(1, IoDirection.INPUT, List.of()));
    }

    private static RecipeOperation rescalable(CapabilityAccess capability, long amount, long parallelism) {
        return new RecipeOperation() {
            public OperationResult commit() {
                return capability.prepareValue(IoDirection.INPUT, parallelism,
                        Math.multiplyExact(amount, parallelism), false).commit();
            }
            public RecipeOperation forParallelism(long actual) { return rescalable(capability, amount, actual); }
        };
    }

    private static CapabilityOperation coreRescalable(MachineCapability capability, long amount, long parallelism) {
        return new CapabilityOperation() {
            public CapabilityResult commit() {
                return capability.prepare(new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT,
                        parallelism, Math.multiplyExact(amount, parallelism), false)).commit();
            }
            public CapabilityOperation forParallelism(long actual) { return coreRescalable(capability, amount, actual); }
        };
    }

    private static RequirementSpec itemRequirement(IoDirection io, long amount, List<String> tags) {
        return Requirements.item(io, Ingredient.of(Items.IRON_INGOT), Math.toIntExact(amount),
                new ItemStack(Items.IRON_INGOT, Math.toIntExact(amount)), tags);
    }

    private static LongEnergyStorage energy(long amount) {
        var storage = new LongEnergyStorage(100, 100, null);
        storage.setAmount(amount);
        return storage;
    }

    private static BulkItemStorage items(long amount) {
        var storage = new BulkItemStorage(64, null);
        storage.setContents(0, new ItemStack(Items.IRON_INGOT), amount);
        return storage;
    }

    private static MachineCapability energyCapability(LongEnergyStorage storage, IOType io, String tag) {
        return new TaggedCapability(new EnergyHatchCapability(storage, io), List.of(tag));
    }

    private static MachineCapability itemCapability(BulkItemStorage storage, IOType io, String tag) {
        return new TaggedCapability(new ItemBusCapability(storage, io), List.of(tag));
    }

    private static URLClassLoader fixtureLoader() throws Exception {
        URL output = Path.of(PublicApiArtifactTest.requiredProperty("mmcr.apiUsageClasses")).toUri().toURL();
        return new URLClassLoader(new URL[] {output}, PublicExtensionProductionTest.class.getClassLoader());
    }

    private static RequirementSpec fixtureRequirement(ClassLoader loader, long amount) throws Exception {
        Class<?> fixture = Class.forName("example.addon.TypedFixture", true, loader);
        Object kind = fixture.getMethod("registerExtension").invoke(null);
        return (RequirementSpec) fixture.getMethod("customRequirement", RequirementKind.class, long.class).invoke(null, kind, amount);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("public_extension_production_test", path); }

    /** Test metadata wrapper; storage, facets and transaction execution remain the real built-in capability.
     * @author howxu <dev@howxu.cn>
     */
    private static class TaggedCapability implements MachineCapability {
        protected final MachineCapability delegate;
        private final List<String> tags;
        TaggedCapability(MachineCapability delegate, List<String> tags) { this.delegate = delegate; this.tags = tags; }
        public CapabilityType type() { return delegate.type(); }
        public CapabilityDirections directions() { return delegate.directions(); }
        public CapabilityView view() {
            return new CapabilityView() {
                public CapabilityType type() { return delegate.type(); }
                public CapabilityDirections directions() { return delegate.directions(); }
                public List<String> tags() { return tags; }
                public Set<Class<? extends CapabilityFacet>> facets() { return delegate.view().facets(); }
            };
        }
        public <F extends CapabilityFacet> Optional<F> facet(Class<F> type) { return delegate.facet(type); }
        public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
    }

    /** Real codec payload used by the public extension registrations.
     * @author howxu <dev@howxu.cn>
     */
    private record Payload(long amount, IoDirection io, List<String> tags) {
        private static final MapCodec<Payload> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("amount").forGetter(Payload::amount),
                Codec.STRING.xmap(IoDirection::valueOf, IoDirection::name).fieldOf("io").forGetter(Payload::io),
                Codec.STRING.listOf().fieldOf("tags").forGetter(Payload::tags)).apply(instance, Payload::new));
        Payload { tags = List.copyOf(tags); }
    }
}
