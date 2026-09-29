package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalResource;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import mekanism.api.resource.LargeResourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the worker-safe planning boundary for Mekanism chemicals.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncChemicalPlanningTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void chemical_requirement_plans_from_a_worker_safe_snapshot() throws Exception {
        ChemicalResource oxygen = ChemicalResource.of(registerChemical("async_oxygen"));
        ChemicalPortCapability capability = new ChemicalPortCapability(tank(oxygen, 1_000L), IOType.INPUT);
        AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();

        AsyncCapabilitySnapshot.Resource snapshot = (AsyncCapabilitySnapshot.Resource) capture(facet);
        AsyncCapabilityPlanner planner = workerPlanner(facet);
        AsyncCapabilityOperation operation = CompletableFuture.supplyAsync(() -> planner.plan(snapshot,
                new AsyncCapabilityRequest.Resource(capability.type().id(), 1L,
                        List.of(new AsyncResourceAction(snapshot.slots().getFirst().resource().orElseThrow(),
                                100L, false))))).get().orElseThrow();

        assertThat(operation).isInstanceOf(AsyncCapabilityOperation.Group.class);
        assertThat(((AsyncCapabilityOperation.Group) operation).operations())
                .containsExactly(new AsyncCapabilityOperation.Resource(capability.type().id(), 0,
                        snapshot.slots().getFirst().resource().orElseThrow(), 100L, false));
    }

    @Test
    void crafting_context_prepares_full_chance_chemical_input_for_async_extraction() throws Exception {
        ChemicalResource oxygen = ChemicalResource.of(registerChemical("crafting_context_async_oxygen"));

        Object prepared = prepareChemicalInput(oxygen, 1F, 3L);

        assertThat(prepared).isInstanceOf(AsyncRequirementPlanner.Requirement.class);
        AsyncRequirementPlanner.Requirement requirement = (AsyncRequirementPlanner.Requirement) prepared;
        assertThat(requirement.amount()).isEqualTo(300L);
        assertThat(requirement.direction()).isEqualTo(IOType.INPUT);
        assertThat(requirement.requests()).singleElement().isInstanceOf(AsyncCapabilityRequest.Resource.class);
        AsyncCapabilityRequest.Resource request = (AsyncCapabilityRequest.Resource) requirement.requests().getFirst();
        assertThat(request.capabilityId()).isEqualTo(MekanismRecipeTypes.CHEMICAL);
        assertThat(request.parallelism()).isEqualTo(3L);
        assertThat(request.actions()).containsExactly(new AsyncResourceAction(NativeAsyncResourceValues.chemical(oxygen),
                300L, false));
    }

    @Test
    void crafting_context_keeps_zero_chance_chemical_input_on_the_main_thread() throws Exception {
        ChemicalResource oxygen = ChemicalResource.of(registerChemical("crafting_context_zero_oxygen"));

        assertThat(prepareChemicalInput(oxygen, 0F)).isNull();
    }

    @Test
    void crafting_context_keeps_partial_chance_chemical_input_on_the_main_thread() throws Exception {
        ChemicalResource oxygen = ChemicalResource.of(registerChemical("crafting_context_partial_oxygen"));

        assertThat(prepareChemicalInput(oxygen, 0.5F)).isNull();
    }

    @Test
    void crafting_context_keeps_tag_chemical_input_on_the_main_thread() throws Exception {
        ChemicalResource oxygen = ChemicalResource.of(registerChemical("crafting_context_tag_oxygen"));
        LoadedChemicalRequirement input = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.tag(ResourceLocation.parse("mmcr_test:crafting_context_tag"), 100L), 1F, List.of(), 1F);

        assertThat(prepareChemicalInput(input, oxygen, 1L)).isNull();
    }

    @Test
    void changed_chemical_tank_rejects_the_stale_async_intent_without_partial_consumption() throws Exception {
        ChemicalResource oxygen = ChemicalResource.of(registerChemical("stale_oxygen"));
        ChemicalResource hydrogen = ChemicalResource.of(registerChemical("stale_hydrogen"));
        FakeChemicalTank tank = tank(oxygen, 1_000L);
        ChemicalPortCapability capability = new ChemicalPortCapability(tank, IOType.INPUT);
        AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElseThrow();
        AsyncCapabilitySnapshot.Resource snapshot = (AsyncCapabilitySnapshot.Resource) capture(facet);
        AsyncCapabilityOperation operation = workerPlanner(facet).plan(snapshot, new AsyncCapabilityRequest.Resource(
                capability.type().id(), 1L, List.of(new AsyncResourceAction(snapshot.slots().getFirst().resource().orElseThrow(),
                100L, false)))).orElseThrow();
        tank.setContents(hydrogen, 1_000L, null);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(commit(facet, operation, transaction).success()).isFalse();
        }
        assertThat(tank.resource()).isEqualTo(hydrogen);
        assertThat(tank.amountAsLong()).isEqualTo(1_000L);
    }

    @Test
    void heat_port_has_no_async_planning_facet() {
        LoadedMekanismBridge.HeatPort capability = new LoadedMekanismBridge.HeatPort() {
            private final CapabilityView view = new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return new CapabilityType(MekanismRecipeTypes.HEAT);
                }

                @Override
                public CapabilityDirections directions() {
                    return CapabilityDirections.of(IOType.INPUT);
                }
            };

            @Override
            public mekanism.api.heat.IHeatHandler heatHandler() {
                return null;
            }

            @Override
            public CapabilityType type() {
                return view.type();
            }

            @Override
            public CapabilityDirections directions() {
                return view.directions();
            }

            @Override
            public CapabilityView view() {
                return view;
            }

            @Override
            public CapabilityOperation prepare(CapabilityRequest request) {
                throw new UnsupportedOperationException();
            }
        };

        assertThat(capability.facet(AsyncPlanningFacet.class)).isEmpty();
    }

    private static Object capture(AsyncPlanningFacet facet) throws Exception {
        return invoke(facet, "captureSnapshotOnServerThread");
    }

    private static AsyncCapabilityPlanner workerPlanner(AsyncPlanningFacet facet) throws Exception {
        return (AsyncCapabilityPlanner) invoke(facet, "workerPlannerOnServerThread");
    }

    private static CapabilityResult commit(AsyncPlanningFacet facet, AsyncCapabilityOperation operation,
                                            TransactionContext transaction) throws Exception {
        return (CapabilityResult) invoke(facet, "commitOnServerThread", AsyncCapabilityOperation.class,
                TransactionContext.class, operation, transaction);
    }

    private static Object prepareChemicalInput(ChemicalResource chemical, float consumeChance) throws Exception {
        return prepareChemicalInput(chemical, consumeChance, 1L);
    }

    private static Object prepareChemicalInput(ChemicalResource chemical, float consumeChance, long parallelism)
            throws Exception {
        LoadedChemicalRequirement input = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(ResourceLocation.parse(chemical.typeHolder().getRegisteredName()), 100L), 1F,
                List.of(), consumeChance);
        return prepareChemicalInput(input, chemical, parallelism);
    }

    private static Object prepareChemicalInput(LoadedChemicalRequirement input, ChemicalResource chemical,
                                               long parallelism) throws Exception {
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Resource(input.type().id()),
                new AsyncCapabilitySnapshot.Resource(input.type().id(), List.of(
                        new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(NativeAsyncResourceValues.chemical(chemical)),
                                1_000L, 1_000L))), Set.of(IOType.INPUT));
        Method method = CraftingContext.class.getDeclaredMethod("prepareAsyncRequirement", int.class,
                MachineRequirement.class, long.class, List.class);
        method.setAccessible(true);
        return method.invoke(null, 0, input, parallelism, List.of(capability));
    }

    private static Object invoke(AsyncPlanningFacet facet, String name, Class<?>... parameterTypes) throws Exception {
        Method method = AsyncPlanningFacet.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(facet);
    }

    private static Object invoke(AsyncPlanningFacet facet, String name, Class<?> first, Class<?> second,
                                 Object firstValue, Object secondValue) throws Exception {
        Method method = AsyncPlanningFacet.class.getDeclaredMethod(name, first, second);
        method.setAccessible(true);
        return method.invoke(facet, firstValue, secondValue);
    }

    private static Holder.Reference<Chemical> registerChemical(String path) {
        ResourceKey<Chemical> key = ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME,
                ResourceLocation.fromNamespaceAndPath("mmcr_test", path));
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        return registry.get(key).orElseGet(() -> {
            registry.unfreeze(true);
            Registry.register(registry, key.identifier(), new Chemical(ChemicalBuilder.builder()));
            registry.freeze();
            return registry.get(key).orElseThrow();
        });
    }

    private static FakeChemicalTank tank(ChemicalResource resource, long amount) {
        FakeChemicalTank tank = new FakeChemicalTank(1_000L);
        tank.setContents(resource, amount, null);
        return tank;
    }

    private static final class FakeChemicalTank implements IChemicalTank {
        private final long capacity;
        private ChemicalResource resource = ChemicalResource.EMPTY;
        private long amount;

        private FakeChemicalTank(long capacity) {
            this.capacity = capacity;
        }

        @Override
        public LargeResourceStack<ChemicalResource> asStack() {
            return new LargeResourceStack<>(resource, amount);
        }

        @Override
        public int insert(ChemicalResource resource, int amount, TransactionContext transaction,
                          AutomationType automationType) {
            if (!isValid(resource) || (!this.resource.isEmpty() && !this.resource.equals(resource))) return 0;
            int moved = (int) Math.min(amount, capacity - this.amount);
            setContents(resource, this.amount + moved, transaction);
            return moved;
        }

        @Override
        public int extract(ChemicalResource resource, int amount, TransactionContext transaction,
                           AutomationType automationType) {
            if (!this.resource.equals(resource)) return 0;
            int moved = (int) Math.min(amount, this.amount);
            setContents(this.resource, this.amount - moved, transaction);
            return moved;
        }

        @Override
        public long capacityAsLong(ChemicalResource resource) {
            return capacity;
        }

        @Override
        public boolean isValid(ChemicalResource resource) {
            return !resource.isEmpty();
        }

        @Override
        public void setContents(LargeResourceStack<ChemicalResource> contents, TransactionContext transaction) {
            resource = contents.resource();
            amount = contents.amount();
        }

        @Override
        public LargeResourceStack.StackHelper<ChemicalResource> stackHelper() {
            return LargeResourceStack.CHEMICAL_HELPER;
        }

        @Override
        public ChemicalAttributeValidator getAttributeValidator() {
            return ChemicalAttributeValidator.ALWAYS_ALLOW;
        }
    }
}
