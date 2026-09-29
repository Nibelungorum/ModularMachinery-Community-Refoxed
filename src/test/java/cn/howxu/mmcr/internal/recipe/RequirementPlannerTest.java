package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.minecraft.world.level.material.Fluids;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.internal.tile.ExtendedItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies generic requirement planning without concrete port knowledge.
 *
 * @author howxu <dev@howxu.cn>
 */
class RequirementPlannerTest {
    private static final TestType TYPE = type("planner_requirement");
    private RequirementHandlerRegistry.TestScope registryScope;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        registryScope = RequirementHandlerRegistry.openTestScope();
        TestBootstrap.bootstrapCapabilities();
    }

    @AfterEach
    void closeRegistryScope() {
        registryScope.close();
    }

    @Test
    void looks_up_handler_filters_capabilities_and_limits_parallelism() {
        register(TYPE, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                int limit = capabilities.stream().mapToInt(capability -> ((TestCapability) capability).limit()).min().orElse(0);
                return new RequirementPlan(context.requirementIndex(), limit, List.of(), null,
                        (parallelism, reservations) -> new RequirementPlan.OperationPlan(
                                capabilities.stream().map(capability -> capability.prepare(new TestRequest(parallelism)))
                                        .toList(), null));
            }
        });

        TestCapability matching = new TestCapability(TYPE.id(), IOType.INPUT, 3);
        TestCapability wrongDirection = new TestCapability(TYPE.id(), IOType.OUTPUT, 1);
        TestCapability wrongType = new TestCapability(MMCR.id("other"), IOType.INPUT, 1);

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(TYPE, RecipeModifier.IOType.INPUT)),
                List.of(matching, wrongDirection, wrongType),
                new PlanningContext(8, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(3);
        assertThat(matching.requestedParallelisms()).containsExactly(3L);
        assertThat(wrongDirection.requestedParallelisms()).isEmpty();
        assertThat(wrongType.requestedParallelisms()).isEmpty();
    }

    @Test
    void supports_mixed_requirements_and_custom_handlers_without_planner_changes() {
        register(TYPE, new SimpleHandler(TYPE));
        TestType secondType = type("planner_second_requirement");
        register(secondType, new SimpleHandler(secondType));

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(TYPE, RecipeModifier.IOType.INPUT), new TestRequirement(secondType, RecipeModifier.IOType.OUTPUT)),
                List.of(new TestCapability(TYPE.id(), IOType.INPUT, 5),
                        new TestCapability(secondType.id(), IOType.OUTPUT, 2)),
                new PlanningContext(4, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2);
        assertThat(result.plan().requirements()).hasSize(2);
    }

    @Test
    void calls_each_handler_once_and_normalizes_every_requirement_to_final_parallelism() {
        TestType firstType = type("planner_once_first");
        TestType secondType = type("planner_once_second");
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        register(firstType, new LimitedHandler(firstType, 4, firstCalls));
        register(secondType, new LimitedHandler(secondType, 2, secondCalls));

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(firstType, RecipeModifier.IOType.INPUT),
                        new TestRequirement(secondType, RecipeModifier.IOType.INPUT)),
                List.of(new TestCapability(firstType.id(), IOType.INPUT, 4),
                        new TestCapability(secondType.id(), IOType.INPUT, 2)),
                new PlanningContext(8, 0));

        assertThat(result.successful()).isTrue();
        assertThat(firstCalls).hasValue(1);
        assertThat(secondCalls).hasValue(1);
        assertThat(result.plan().parallelism()).isEqualTo(2);
        assertThat(result.plan().requirements()).allSatisfy(plan -> {
            assertThat(plan.maxParallelism()).isEqualTo(2);
            assertThat(plan.operations()).isNotEmpty();
        });
    }

    @Test
    void rejects_opaque_direct_operations_when_global_parallelism_is_lowered() {
        TestType unsafeType = type("unsafe_direct_operation");
        register(unsafeType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                CapabilityOperation operation = new CapabilityOperation() {
                    @Override
                    public CapabilityResult commit() {
                        return CapabilityResult.successful();
                    }

                    @Override
                    public CapabilityOperation forParallelism(long parallelism) {
                        return null;
                    }
                };
                return new RequirementPlan(context.requirementIndex(), 1,
                        List.of(operation), null);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(unsafeType, RecipeModifier.IOType.INPUT)),
                List.of(new TestCapability(unsafeType.id(), IOType.INPUT, 1)),
                new PlanningContext(4, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.UNSAFE_OPERATION_PARALLELISM);
    }

    @Test
    void materializes_a_custom_operation_factory_once_after_reservation_selection() {
        TestType factoryType = type("single_operation_factory");
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicLong operationParallelism = new AtomicLong();
        TestCapability capability = new TestCapability(factoryType.id(), IOType.INPUT, 2);
        register(factoryType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), 2, List.of(), null,
                        (parallelism, reservations) -> {
                            factoryCalls.incrementAndGet();
                            operationParallelism.set(parallelism);
                            return new RequirementPlan.OperationPlan(
                                     List.of(capability.prepare(new TestRequest(parallelism))), null);
                         },
                         (parallelism, reservations) -> parallelism == 2
                                 ? unknownFailure(factoryType.id(), StatusSeverity.BLOCKED, FailurePhase.REQUIREMENT_PLAN)
                                 : null);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(factoryType, RecipeModifier.IOType.INPUT)),
                List.of(capability),
                new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(factoryCalls).hasValue(1);
        assertThat(operationParallelism).hasValue(1);
        assertThat(capability.requestedParallelisms()).containsExactly(1L);
    }

    @Test
    void does_not_retry_lower_candidates_after_materialization_failure() {
        TestType failureType = type("materialization_failure");
        AtomicInteger factoryCalls = new AtomicInteger();
        ExecutionStatus materializationFailure = unknownFailure(failureType.id(), StatusSeverity.FAILURE,
                FailurePhase.CAPABILITY_COMMIT);
        TestCapability capability = new TestCapability(failureType.id(), IOType.INPUT, 2);
        register(failureType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), 2, List.of(), null,
                        (parallelism, reservations) -> {
                            factoryCalls.incrementAndGet();
                            return new RequirementPlan.OperationPlan(
                                    List.of(capability.prepare(new TestRequest(parallelism))),
                                    parallelism == 2 ? materializationFailure : null);
                        }, (parallelism, reservations) -> null);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(failureType, RecipeModifier.IOType.INPUT)),
                List.of(capability), new PlanningContext(2, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure()).isSameAs(materializationFailure);
        assertThat(factoryCalls).hasValue(1);
        assertThat(capability.requestedParallelisms()).containsExactly(2L);
    }

    @Test
    void shares_and_rolls_back_reservations_between_candidate_and_final_materialization() {
        TestType reservationType = type("shared_reservation_lifecycle");
        BulkItemStorage storage = new BulkItemStorage(2, null);
        storage.forceInsert(ironResource(), 2, false);
        StorageCapability capability = new StorageCapability(reservationType.id(), CapabilityDirections.input(), storage);
        PlanningReservations shared = new PlanningReservations();
        AtomicInteger factories = new AtomicInteger();
        register(reservationType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                assertThat(context.reservations()).isSameAs(shared);
                return new RequirementPlan(context.requirementIndex(), 2, List.of(), null,
                        (parallelism, reservations) -> {
                            factories.incrementAndGet();
                            assertThat(reservations.reserveItemExtract(
                                    storage, 0, ironResource(), parallelism)).isTrue();
                            assertThat(reservations.itemAmount(storage, 0)).isEqualTo(2 - parallelism * factories.get());
                            CapabilityRequests.ItemAction action =
                                    new CapabilityRequests.ItemAction(0, ironResource(),
                                            parallelism, false);
                            return new RequirementPlan.OperationPlan(List.of(capability.prepare(
                                    new CapabilityRequests.ItemRequest(capability.type(), IOType.INPUT,
                                            parallelism, List.of(action)))), null);
                        },
                        (parallelism, reservations) -> reservations.reserveItemExtract(
                                storage, 0, ironResource(), parallelism)
                                ? null
                                : unknownFailure(reservationType.id(), StatusSeverity.BLOCKED,
                                FailurePhase.REQUIREMENT_PLAN));
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(reservationType, RecipeModifier.IOType.INPUT),
                        new TestRequirement(reservationType, RecipeModifier.IOType.INPUT)),
                List.of(capability), new PlanningContext(2, 0, false, shared));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(factories).hasValue(2);
        assertThat(storage.amount(0)).isEqualTo(2);
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void carries_a_structured_handler_failure() {
        TestType failureType = type("planner_failure_requirement");
        ExecutionStatus failure = unknownFailure(failureType.id(), StatusSeverity.FAILURE,
                FailurePhase.REQUIREMENT_PLAN);
        register(failureType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), 0, List.of(), failure);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(failureType, RecipeModifier.IOType.INPUT)), List.of(), new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure()).isSameAs(failure);
    }

    @Test
    void reports_the_actual_zero_parallelism_requirement_and_original_index() {
        TestType firstType = type("positive_parallelism_requirement");
        TestType blockedType = type("zero_parallelism_requirement");
        register(firstType, new SimpleHandler(firstType));
        register(blockedType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), 0, List.of(), null);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(firstType, RecipeModifier.IOType.INPUT),
                        new TestRequirement(blockedType, RecipeModifier.IOType.INPUT)),
                List.of(new TestCapability(firstType.id(), IOType.INPUT, 1)),
                new PlanningContext(1, 0), List.of(4, 11));

        assertThat(result.successful()).isFalse();
        assertThat(result.failureRequirementIndex()).isEqualTo(11);
        assertThat(result.failure().id()).isEqualTo(blockedType.id());
        assertThat(result.failure().source()).isEqualTo(blockedType.id());
    }

    @Test
    void built_in_energy_handler_prepares_a_real_storage_operation() {
        LongValueStorage storage = new LongValueStorage(100, 100, null);
        storage.setAmount(10);
        MachineCapability capability = new TestCapability(EnergyRequirement.TYPE.id(), IOType.INPUT, 1) {
            @Override
            public CapabilityStorage storage() {
                return storage;
            }

            @Override
            public CapabilityOperation prepare(CapabilityRequest request) {
                assertThat(request).isInstanceOf(CapabilityRequests.ValueRequest.class);
                CapabilityRequests.ValueRequest valueRequest = (CapabilityRequests.ValueRequest) request;
                return () -> {
                    long moved = storage.extract(valueRequest.amount(), false);
                    return moved == valueRequest.amount()
                            ? CapabilityResult.successful()
                            : CapabilityResult.failure(unknownFailure(EnergyRequirement.TYPE.id(),
                                    StatusSeverity.BLOCKED, FailurePhase.CAPABILITY_COMMIT));
                };
            }
        };

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 4)),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount()).isEqualTo(6);
    }

    @Test
    void item_shortage_returns_a_real_operation_for_the_available_parallelism() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.forceInsert(ironResource(), 1, false);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1,
                        ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void bidirectional_item_capability_plans_input_and_output_with_requirement_directions() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.forceInsert(ironResource(), 1, false);
        StorageCapability capability = new StorageCapability(ItemRequirement.TYPE.id(),
                CapabilityDirections.bidirectional(), storage);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(1))),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).hasSize(2);
        assertThat(result.plan().commit()).isTrue();
        assertThat(capability.directions()).isEqualTo(CapabilityDirections.bidirectional());
        assertThat(capability.view().directions()).isEqualTo(CapabilityDirections.bidirectional());
        assertThat(capability.requests()).extracting(CapabilityRequest::ioType)
                .containsExactly(IOType.INPUT, IOType.OUTPUT);
        assertThat(storage.amount(0)).isEqualTo(1);
    }

    @Test
    void bidirectional_item_plan_fails_when_output_capacity_is_insufficient() {
        BulkItemStorage storage = new BulkItemStorage(1, null);
        storage.forceInsert(ironResource(), 1, false);
        StorageCapability capability = new StorageCapability(ItemRequirement.TYPE.id(),
                CapabilityDirections.bidirectional(), storage);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(2))),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure()).satisfies(failure -> {
            assertThat(failure.reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
            assertThat(failure.id()).isEqualTo(ItemRequirement.TYPE.id());
            assertThat(failure.source()).isEqualTo(ItemRequirement.TYPE.id());
        });
        assertThat(result.failureRequirementIndex()).isEqualTo(1);
        assertThat(result.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(2L);
                    assertThat(simulation.accepted()).isZero();
                    assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
                });
        assertThat(storage.amount(0)).isEqualTo(1);
    }

    @Test
    void storage_capability_validates_request_direction_through_production_factory() {
        StorageCapability capability = new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.input(),
                new LongValueStorage(10, 10, null));
        CapabilityRequests.ValueRequest request = new CapabilityRequests.ValueRequest(
                capability.type(), IOType.OUTPUT, 1, 1, true);

        assertThatThrownBy(() -> capability.prepare(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Capability request IO type does not match");
    }

    @Test
    void bidirectional_fluid_capability_uses_input_requirement_direction() {
        LongFluidStorage storage = new LongFluidStorage(2_000, null);
        storage.setFluid(new FluidStack(Fluids.WATER, 1_000));
        StorageCapability capability = new StorageCapability(FluidRequirement.TYPE.id(),
                CapabilityDirections.bidirectional(), storage);

        var result = new RequirementPlanner().plan(
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                        FluidStack.EMPTY)), List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(capability.requests()).singleElement()
                .extracting(CapabilityRequest::ioType).isEqualTo(IOType.INPUT);
    }

    @Test
    void bidirectional_energy_capability_uses_input_requirement_direction() {
        LongValueStorage storage = new LongValueStorage(100, 100, null);
        storage.setAmount(4);
        StorageCapability capability = new StorageCapability(EnergyRequirement.TYPE.id(),
                CapabilityDirections.bidirectional(), storage);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 4)),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(((CapabilityRequests.ValueRequest) capability.requests().getFirst()).ioType()).isEqualTo(IOType.INPUT);
    }

    @Test
    void bidirectional_smart_interface_capability_uses_output_requirement_direction() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("temperature", 0F);
        StorageCapability capability = new StorageCapability(SmartInterfaceRequirement.TYPE.id(),
                CapabilityDirections.bidirectional(), storage);

        var result = new RequirementPlanner().plan(
                List.of(SmartInterfaceRequirement.output("temperature", 1F)),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(((CapabilityRequests.SmartValueRequest) capability.requests().getFirst()).ioType())
                .isEqualTo(IOType.OUTPUT);
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.value("temperature")).contains(1F);
    }

    @Test
    void fluid_shortage_returns_a_real_operation_for_the_available_parallelism() {
        LongFluidStorage storage = new LongFluidStorage(2_000, null);
        storage.setFluid(new FluidStack(Fluids.WATER, 1_000));

        var result = new RequirementPlanner().plan(
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                        FluidStack.EMPTY)),
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.getAmountAsLong()).isZero();
    }

    @Test
    void built_in_requirements_match_existing_capability_identifiers() {
        BulkItemStorage itemStorage = new BulkItemStorage(64, null);
        itemStorage.forceInsert(ironResource(), 1, false);
        LongFluidStorage fluidStorage = new LongFluidStorage(2_000, null);
        fluidStorage.setFluid(new FluidStack(Fluids.WATER, 1_000));
        LongValueStorage energyStorage = new LongValueStorage(100, 100, null);
        energyStorage.setAmount(10);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                                FluidStack.EMPTY),
                        new EnergyRequirement(RecipeModifier.IOType.INPUT, 4)),
                List.of(new StorageCapability(BuiltinCapabilityDefinitions.ITEM_TYPE.id(), CapabilityDirections.input(), itemStorage),
                        new StorageCapability(BuiltinCapabilityDefinitions.FLUID_TYPE.id(), CapabilityDirections.input(), fluidStorage),
                        new StorageCapability(BuiltinCapabilityDefinitions.ENERGY_TYPE.id(), CapabilityDirections.input(), energyStorage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(RequirementHandlerRegistry.resourceWakeupsFor(new EnergyRequirement(
                RecipeModifier.IOType.INPUT, 4))).anySatisfy(wakeup ->
                assertThat(wakeup.matcher().test(BuiltinCapabilityDefinitions.ENERGY_TYPE)).isTrue());
    }

    @Test
    void partial_item_output_commits_the_available_resource_amount() {
        BulkItemStorage storage = new BulkItemStorage(2, null);
        StorageCapability capability = new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage);
        ItemStack output = ironStack(4);
        assertThat(output.getCount()).isEqualTo(4);
        assertThat(storage.capacity(0)).isEqualTo(2);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output, 1F, List.of());
        assertThat(requirement.stack(null).getCount()).isEqualTo(4);
        var result = new RequirementPlanner().plan(
                List.of(requirement),
                List.of(capability),
                new PlanningContext(1, 0, true));

        assertThat(result.successful()).isTrue();
        assertThat(capability.lastItemRequest.actions()).singleElement()
                .extracting(CapabilityRequests.ItemAction::amount).isEqualTo(2L);
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(2);
    }

    @Test
    void extended_item_bus_output_accepts_a_data_bearing_stack_above_vanilla_stack_size() {
        ExtendedItemBusBlockEntity bus = (ExtendedItemBusBlockEntity) ModBlockEntities.BES
                .get("extended_item_output_bus_basic").get().create(
                        BlockPos.ZERO, ModBlocks.BLOCKS.get("extended_item_output_bus_basic").get().defaultBlockState());
        ItemStack output = new ItemStack(Items.IRON_INGOT, 96);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("data output"));
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output, 1F, List.of());

        var result = new CraftingContext(bus.capabilitySnapshot())
                .planOutputRequirements(List.of(requirement), 1, false);

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(bus.itemStorage().amount(0)).isEqualTo(96L);
        assertThat(bus.itemStorage().resource(0).get(DataComponents.CUSTOM_NAME))
                .isEqualTo(Component.literal("data output"));
    }

    @Test
    void extended_item_bus_accepts_parallel_output_above_integer_stack_limit() {
        ExtendedItemBusBlockEntity bus = (ExtendedItemBusBlockEntity) ModBlockEntities.BES
                .get("extended_item_output_bus_basic").get().create(
                        BlockPos.ZERO, ModBlocks.BLOCKS.get("extended_item_output_bus_basic").get().defaultBlockState());
        ItemStack output = new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output, 1F, List.of());

        var result = new CraftingContext(bus.capabilitySnapshot())
                .planOutputRequirements(List.of(requirement), 2L, false);

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().outputSimulations()).singleElement()
                .satisfies(simulation -> assertThat(simulation.requested())
                        .isEqualTo((long) Integer.MAX_VALUE * 2L));
        assertThat(result.plan().commit()).isTrue();
        assertThat(bus.itemStorage().amount(0)).isEqualTo((long) Integer.MAX_VALUE * 2L);
    }

    @Test
    void long_output_shortage_requires_full_capacity_or_accepts_partial_capacity() {
        ExtendedItemBusBlockEntity bus = (ExtendedItemBusBlockEntity) ModBlockEntities.BES
                .get("extended_item_output_bus_basic").get().create(
                        BlockPos.ZERO, ModBlocks.BLOCKS.get("extended_item_output_bus_basic").get().defaultBlockState());
        ItemStack output = new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output, 1F, List.of());
        long requested = (long) Integer.MAX_VALUE * 2L;
        long existing = Long.MAX_VALUE - 1L;
        bus.itemStorage().forceInsert(0, output, existing, false);
        for (int slot = 1; slot < bus.itemStorage().size(); slot++) {
            bus.itemStorage().forceInsert(slot, new ItemStack(Items.COBBLESTONE), Long.MAX_VALUE, false);
        }
        assertThat(bus.itemStorage().amount(0)).isEqualTo(existing);

        var full = new CraftingContext(bus.capabilitySnapshot())
                .planOutputRequirements(List.of(requirement), 2L, false);
        assertThat(full.successful()).isFalse();
        assertThat(full.failure()).isNotNull();

        var partial = new CraftingContext(bus.capabilitySnapshot())
                .planOutputRequirements(List.of(requirement), 2L, true);
        assertThat(partial.successful()).isTrue();
        assertThat(partial.plan().outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(requested);
                    assertThat(simulation.accepted()).isEqualTo(1L);
                });
        assertThat(partial.plan().commit()).isTrue();
        assertThat(bus.itemStorage().amount(0)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void output_simulation_reports_full_fit() {
        BulkItemStorage storage = new BulkItemStorage(4, null);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(4), 1F, List.of());

        var result = new RequirementPlanner().plan(
                List.of(requirement),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(4L);
                    assertThat(simulation.accepted()).isEqualTo(4L);
                    assertThat(simulation.fit()).isEqualTo(OutputFit.FULL);
                });
        assertThatThrownBy(() -> result.outputSimulations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void output_simulation_reports_partial_fit_and_partial_policy_commits_only_accepted_amount() {
        BulkItemStorage storage = new BulkItemStorage(1, null);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(4), 1F, List.of());

        var result = new RequirementPlanner().plan(
                List.of(requirement),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0, Map.of(0, OutputPolicy.ALLOW_PARTIAL)));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(4L);
                    assertThat(simulation.accepted()).isEqualTo(1L);
                    assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
                });
        assertThat(storage.amount(0)).isZero();
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(1L);
    }

    @Test
    void require_full_output_with_partial_space_reports_partial_without_committing() {
        BulkItemStorage storage = new BulkItemStorage(1, null);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(4), 1F, List.of());

        var result = new RequirementPlanner().plan(
                List.of(requirement),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(result.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.accepted()).isEqualTo(1L);
                    assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
                });
        assertThat(result.failureRequirementIndex()).isEqualTo(0);
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void require_full_output_with_no_space_reports_none_without_committing() {
        BulkItemStorage storage = new BulkItemStorage(0, null);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(4), 1F, List.of());

        var result = new RequirementPlanner().plan(
                List.of(requirement),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(result.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.accepted()).isZero();
                    assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
                });
        assertThat(result.failureRequirementIndex()).isEqualTo(0);
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void output_policy_is_selected_by_requirement_index() {
        BulkItemStorage inputStorage = new BulkItemStorage(64, null);
        BulkItemStorage outputStorage = new BulkItemStorage(1, null);
        ItemRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1,
                ItemStack.EMPTY);
        ItemRequirement output = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(4), 1F, List.of());
        inputStorage.forceInsert(ironResource(), 1, false);

        var result = new RequirementPlanner().plan(
                List.of(input, output),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(), inputStorage),
                        new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), outputStorage)),
                new PlanningContext(1, 0, Map.of(1, OutputPolicy.ALLOW_PARTIAL)));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().outputSimulations()).singleElement()
                .extracting(simulation -> simulation.fit()).isEqualTo(OutputFit.PARTIAL);
    }

    @Test
    void partial_fluid_output_commits_the_available_resource_amount() {
        LongFluidStorage storage = new LongFluidStorage(250, null);
        var result = new RequirementPlanner().plan(
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new FluidStack(Fluids.WATER, 1_000), 1F, List.of())),
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0, true));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(1_000L);
                    assertThat(simulation.accepted()).isEqualTo(250L);
                    assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
                });
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.getAmountAsLong()).isEqualTo(250);
    }

    @Test
    void item_planning_and_commit_respect_the_resource_stack_limit() {
        BulkItemStorage storage = new BulkItemStorage(128, null);
        ItemStack output = ironStack(64);
        StorageCapability capability = new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output)),
                List.of(capability), new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(capability.lastItemRequest.actions()).singleElement()
                .extracting(CapabilityRequests.ItemAction::amount).isEqualTo(64L);
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(64L);
    }

    @Test
    void partial_outputs_without_any_capacity_are_structured_blocked_failures() {
        var itemResult = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        ironStack(1), 1F, List.of())),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(),
                        new BulkItemStorage(0, null))), new PlanningContext(1, 0, true));
        var fluidResult = new RequirementPlanner().plan(
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new FluidStack(Fluids.WATER, 1_000), 1F, List.of())),
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), CapabilityDirections.output(),
                        new LongFluidStorage(0, null))), new PlanningContext(1, 0, true));

        assertThat(itemResult.successful()).isFalse();
        assertThat(itemResult.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(itemResult.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(1L);
                    assertThat(simulation.accepted()).isZero();
                    assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
                });
        assertThat(fluidResult.successful()).isFalse();
        assertThat(fluidResult.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(fluidResult.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(1_000L);
                    assertThat(simulation.accepted()).isZero();
                    assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
                });
    }

    @Test
    void output_energy_without_capacity_is_reported_as_output_capacity_failure() {
        LongValueStorage storage = new LongValueStorage(100L, 100L, null);
        storage.setAmount(100L);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)),
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(result.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(4L);
                    assertThat(simulation.accepted()).isZero();
                    assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
                });
    }

    @Test
    void output_energy_simulation_reports_full_fit() {
        LongValueStorage storage = new LongValueStorage(100L, 100L, null);
        storage.setAmount(40L);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 60)),
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(60L);
                    assertThat(simulation.accepted()).isEqualTo(60L);
                    assertThat(simulation.fit()).isEqualTo(OutputFit.FULL);
                });
    }

    @Test
    void partial_output_energy_simulation_reports_requested_accepted_and_fit() {
        LongValueStorage storage = new LongValueStorage(100L, 100L, null);
        storage.setAmount(50L);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 60)),
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0, true));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(60L);
                    assertThat(simulation.accepted()).isEqualTo(50L);
                    assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
                });
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount()).isEqualTo(100L);
    }

    @Test
    void energy_output_admission_reserves_capacity_and_materializes_operation() {
        EnergyAdmissionCapability capability = new EnergyAdmissionCapability(8L, 0);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 8L)),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(capability.reservationCalls).isPositive();
        assertThat(capability.materializedOperations).isEqualTo(1);
        assertThat(result.plan().requirements()).singleElement()
                .satisfies(plan -> assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(capability.committedAmount).isEqualTo(8L);
    }

    @Test
    void admission_output_requires_full_remaining_capacity_in_partial_mode() {
        EnergyAdmissionCapability capability = new EnergyAdmissionCapability(3L, 0);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 5L)),
                List.of(capability), new PlanningContext(1, 0, true));

        assertThat(result.successful()).isFalse();
        assertThat(capability.planOutputCalls).isZero();
        assertThat(result.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(5L);
                    assertThat(simulation.accepted()).isZero();
                    assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
                });
    }

    @Test
    void output_priority_keeps_higher_priority_storage_before_admission() {
        LongValueStorage storage = new LongValueStorage(5L, 5L, null);
        StorageCapability ordinary = new StorageCapability(EnergyRequirement.TYPE.id(),
                CapabilityDirections.output(), storage);
        EnergyAdmissionCapability admission = new EnergyAdmissionCapability(5L, -1);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 5L)),
                List.of(admission, ordinary), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(admission.planOutputCalls).isZero();
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount()).isEqualTo(5L);
    }

    @Test
    void ordinary_partial_energy_output_is_not_disabled_by_another_admission_capability() {
        LongValueStorage storage = new LongValueStorage(3L, 3L, null);
        StorageCapability ordinary = new StorageCapability(EnergyRequirement.TYPE.id(),
                CapabilityDirections.output(), storage);
        EnergyAdmissionCapability admission = new EnergyAdmissionCapability(1L, -1);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 5L)),
                List.of(admission, ordinary), new PlanningContext(1, 0, true));

        assertThat(result.successful()).isTrue();
        assertThat(result.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(5L);
                    assertThat(simulation.accepted()).isEqualTo(3L);
                    assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
                });
        assertThat(admission.planOutputCalls).isZero();
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount()).isEqualTo(3L);
    }

    @Test
    void chance_zero_outputs_are_explicit_no_ops_even_in_partial_mode() {
        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                                ironStack(1), 0F, List.of()),
                        new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                                new FluidStack(Fluids.WATER, 1_000), 0F, List.of())),
                List.of(), new PlanningContext(1, 0, true));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).allSatisfy(plan -> assertThat(plan.operations()).isEmpty());
    }

    @Test
    void generic_matching_filters_tags_and_keeps_untagged_capabilities_matchable() {
        TestType taggedType = type("tagged_requirement");
        register(taggedType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), capabilities.size(), List.of(), null);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(taggedType, RecipeModifier.IOType.INPUT, List.of("blue"))),
                List.of(new TestCapability(taggedType.id(), IOType.INPUT, 1, List.of("red")),
                        new TestCapability(taggedType.id(), IOType.INPUT, 1, List.of("blue")),
                        new TestCapability(taggedType.id(), IOType.INPUT, 1)),
                new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2);
    }

    @Test
    void default_requirement_indexes_are_assigned_in_ascending_order() {
        TestType indexedType = type("ascending_requirement_indexes");
        List<Integer> indexes = new ArrayList<>();
        register(indexedType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                indexes.add(context.requirementIndex());
                return new RequirementPlan(context.requirementIndex(), 1, List.of(), null);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(new TestRequirement(indexedType, RecipeModifier.IOType.INPUT),
                        new TestRequirement(indexedType, RecipeModifier.IOType.INPUT)),
                List.of(), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(indexes).containsExactly(0, 1);
    }

    @Test
    void energy_shortage_returns_a_real_operation_for_the_available_parallelism() {
        LongValueStorage storage = new LongValueStorage(100, 100, null);
        storage.setAmount(4);
        MachineCapability capability = new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.input(), storage);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 4)),
                List.of(capability), new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount()).isZero();
    }

    @Test
    void shared_item_slot_is_reserved_during_planning() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.forceInsert(ironResource(), 1, false);

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure()).isNotNull();
        assertThat(storage.amount(0)).isEqualTo(1);
    }

    @Test
    void shared_item_slot_lowers_parallelism_before_materializing_operations() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.forceInsert(ironResource(), 2, false);

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().requirements()).allSatisfy(plan -> {
            assertThat(plan.maxParallelism()).isEqualTo(1);
            assertThat(plan.operations()).isNotEmpty();
        });
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void shared_output_capacity_lowers_full_output_parallelism_before_materializing_operations() {
        BulkItemStorage storage = new BulkItemStorage(8, null);
        List<MachineRequirement> outputs = List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(4)),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(4)));

        var result = new RequirementPlanner().plan(
                outputs,
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().requirements()).allSatisfy(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(8L);
    }

    @Test
    void shared_output_capacity_lowers_partial_output_parallelism_when_a_candidate_accepts_zero() {
        BulkItemStorage storage = new BulkItemStorage(8, null);
        List<MachineRequirement> outputs = List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(4)),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(4)));

        var result = new RequirementPlanner().plan(
                outputs,
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(2, 0, true));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().outputSimulations()).allSatisfy(simulation -> {
            assertThat(simulation.requested()).isEqualTo(4L);
            assertThat(simulation.accepted()).isEqualTo(4L);
            assertThat(simulation.fit()).isEqualTo(OutputFit.FULL);
        });
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(8L);
    }

    @Test
    void preserves_output_simulation_when_all_partial_output_candidates_fail() {
        BulkItemStorage storage = new BulkItemStorage(4, null);
        List<MachineRequirement> outputs = List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(4)),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(4)));

        var result = new RequirementPlanner().plan(
                outputs,
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage)),
                new PlanningContext(2, 0, true));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(result.failureRequirementIndex()).isEqualTo(1);
        assertThat(result.outputSimulations()).satisfiesExactly(
                first -> {
                    assertThat(first.requested()).isEqualTo(4L);
                    assertThat(first.accepted()).isEqualTo(4L);
                    assertThat(first.fit()).isEqualTo(OutputFit.FULL);
                },
                second -> {
                    assertThat(second.requested()).isEqualTo(4L);
                    assertThat(second.accepted()).isZero();
                    assertThat(second.fit()).isEqualTo(OutputFit.NONE);
                });
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void shared_fluid_slot_is_reserved_during_planning() {
        LongFluidStorage storage = new LongFluidStorage(2_000, null);
        storage.setFluid(new FluidStack(Fluids.WATER, 1_000));

        var result = new RequirementPlanner().plan(
                List.of(
                        new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                                FluidStack.EMPTY),
                        new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                                FluidStack.EMPTY)),
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure()).isNotNull();
        assertThat(storage.getAmountAsLong()).isEqualTo(1_000);
    }

    @Test
    void shared_energy_storage_is_reserved_during_planning() {
        LongValueStorage storage = new LongValueStorage(100, 100, null);
        storage.setAmount(4);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 4),
                        new EnergyRequirement(RecipeModifier.IOType.INPUT, 4)),
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure()).isNotNull();
        assertThat(storage.amount()).isEqualTo(4);
    }

    @Test
    void planning_reservations_use_virtual_energy_state_for_both_transfer_orders() {
        LongValueStorage outputThenInput = new LongValueStorage(10, 10, null);
        outputThenInput.setAmount(5);
        PlanningReservations first = new PlanningReservations();
        assertThat(first.reserveValue(outputThenInput, 3, true)).isTrue();
        assertThat(first.reserveValue(outputThenInput, 7, false)).isTrue();
        assertThat(first.valueAvailable(outputThenInput, false)).isEqualTo(1L);

        LongValueStorage inputThenOutput = new LongValueStorage(10, 10, null);
        inputThenOutput.setAmount(5);
        PlanningReservations second = new PlanningReservations();
        assertThat(second.reserveValue(inputThenOutput, 3, false)).isTrue();
        assertThat(second.reserveValue(inputThenOutput, 7, true)).isTrue();
        assertThat(second.valueAvailable(inputThenOutput, false)).isEqualTo(9L);

        LongValueStorage limited = new LongValueStorage(10, 5, null);
        assertThat(new PlanningReservations().reserveValue(limited, 6, true)).isFalse();
    }

    @Test
    void planning_reservations_reject_resource_virtual_amount_overflow() {
        LongFluidStorage storage = new LongFluidStorage(Long.MAX_VALUE, null);
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        storage.setContents(0, water, Long.MAX_VALUE);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveFluidExtract(storage, 0, water, Long.MAX_VALUE)).isTrue();
        assertThat(reservations.reserveFluidInsert(storage, 0, water, Long.MAX_VALUE, Long.MAX_VALUE)).isTrue();
        assertThat(reservations.reserveFluidInsert(storage, 0, water, 1L, Long.MAX_VALUE)).isFalse();
        assertThat(reservations.fluidAmount(storage, 0)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void planning_reservations_reject_value_virtual_amount_overflow() {
        LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveValue(storage, Long.MAX_VALUE, true)).isTrue();
        assertThat(reservations.valueAvailable(storage, true)).isZero();
        assertThat(reservations.reserveValue(storage, 1L, true)).isFalse();

        storage.setAmount(Long.MAX_VALUE);
        assertThat(reservations.valueAvailable(storage, false)).isZero();
        assertThat(reservations.reserveValue(storage, 1L, false)).isFalse();
    }

    @Test
    void planning_reservations_reject_minimum_amounts_without_changing_state() {
        LongValueStorage valueStorage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
        LongFluidStorage resourceStorage = new LongFluidStorage(Long.MAX_VALUE, null);
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveValue(valueStorage, Long.MIN_VALUE, true)).isFalse();
        assertThat(reservations.reserveFluidInsert(resourceStorage, 0, water, Long.MIN_VALUE, Long.MAX_VALUE)).isFalse();
        assertThat(reservations.reserveFluidExtract(resourceStorage, 0, water, Long.MIN_VALUE)).isFalse();
        assertThat(reservations.valueAvailable(valueStorage, true)).isEqualTo(Long.MAX_VALUE);
        assertThat(reservations.fluidAmount(resourceStorage, 0)).isZero();
    }

    @Test
    void built_in_item_and_fluid_handlers_commit_real_resource_storage_operations_in_order() {
        BulkItemStorage itemStorage = new BulkItemStorage(64, null);
        itemStorage.forceInsert(ironResource(), 2, false);
        LongFluidStorage fluidStorage = new LongFluidStorage(2_000, null);
        fluidStorage.setFluid(new FluidStack(Fluids.WATER, 1_000));

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 2, ItemStack.EMPTY),
                        new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000, FluidStack.EMPTY)
                ),
                List.of(
                        new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(), itemStorage),
                        new StorageCapability(FluidRequirement.TYPE.id(), CapabilityDirections.input(), fluidStorage)
                ),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(itemStorage.amount(0)).isZero();
        assertThat(fluidStorage.getAmountAsLong()).isZero();
    }

    @Test
    void oneCombinedInputPlansBothItemAndFluidRequirements() {
        IOPortBlockEntity port = port("combined_input_reinforced");
        insertCombinedContents(port, 2, 1_000L);
        ComponentRuntime runtime = runtimeFor(port);

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 2, ItemStack.EMPTY),
                        new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                                FluidStack.EMPTY)),
                runtime.capabilities(), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).allSatisfy(plan -> assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(port.itemStorage().amount(0)).isZero();
        assertThat(port.fluidStorage().amount(0)).isZero();
    }

    @Test
    void oneCombinedOutputPlansBothItemAndFluidOutputs() {
        IOPortBlockEntity port = port("combined_output_reinforced");
        ComponentRuntime runtime = runtimeFor(port);

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(2)),
                        new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                                new FluidStack(Fluids.WATER, 1_000))),
                runtime.capabilities(), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).allSatisfy(plan -> assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().outputSimulations()).hasSize(2);
        assertThat(result.plan().outputSimulations().get(0).requested()).isEqualTo(2L);
        assertThat(result.plan().outputSimulations().get(0).accepted()).isEqualTo(2L);
        assertThat(result.plan().outputSimulations().get(0).fit()).isEqualTo(OutputFit.FULL);
        assertThat(result.plan().outputSimulations().get(1).requested()).isEqualTo(1_000L);
        assertThat(result.plan().outputSimulations().get(1).accepted()).isEqualTo(1_000L);
        assertThat(result.plan().outputSimulations().get(1).fit()).isEqualTo(OutputFit.FULL);
        assertThat(result.plan().commit()).isTrue();
        assertThat(port.itemStorage().amount(0)).isEqualTo(2L);
        assertThat(port.fluidStorage().amount(0)).isEqualTo(1_000L);
    }

    @Test
    void itemAndFluidReservationsDoNotCollide() {
        IOPortBlockEntity port = port("combined_input_reinforced");
        insertCombinedContents(port, 2, 2_000L);
        ComponentRuntime runtime = runtimeFor(port);

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                                FluidStack.EMPTY)),
                runtime.capabilities(), new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(2);
        assertThat(result.plan().requirements()).allSatisfy(plan -> assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(port.itemStorage().amount(0)).isZero();
        assertThat(port.fluidStorage().amount(0)).isZero();
    }

    @Test
    void repeatedItemRequirementsShareTheSameItemStorageReservation() {
        IOPortBlockEntity port = port("combined_input_reinforced");
        insertCombinedContents(port, 2, 0L);
        ComponentRuntime runtime = runtimeFor(port);

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY)),
                runtime.capabilities(), new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().requirements()).allSatisfy(plan -> assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(port.itemStorage().amount(0)).isZero();
    }

    @Test
    void built_in_smart_interface_handler_checks_and_commits_a_transactional_value() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("mode", 1F);
        StorageCapability capability = new StorageCapability(
                SmartInterfaceRequirement.TYPE.id(),
                CapabilityDirections.output(), storage);

        var result = new RequirementPlanner().plan(
                List.of(SmartInterfaceRequirement.output("mode", 9F)),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.value("mode")).contains(9F);
    }

    @Test
    void built_in_requirement_failures_expose_typed_reasons() {
        var itemInputFailure = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(),
                        new BulkItemStorage(1, null))), new PlanningContext(1, 0));
        var energyInputFailure = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 1)),
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.input(),
                        new LongValueStorage(1, 1, null))), new PlanningContext(1, 0));
        var itemOutputFailure = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ironStack(1))),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(),
                        new BulkItemStorage(0, null))), new PlanningContext(1, 0));
        var smartInputFailure = new RequirementPlanner().plan(
                List.of(SmartInterfaceRequirement.input("missing", 1F)),
                List.of(new StorageCapability(SmartInterfaceRequirement.TYPE.id(), CapabilityDirections.input(),
                        new FloatValueStorage())), new PlanningContext(1, 0));

        assertThat(itemInputFailure.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
        assertThat(energyInputFailure.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
        assertThat(itemOutputFailure.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(smartInputFailure.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
    }

    @Test
    void built_in_chance_decision_prepares_the_operation_once() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        StorageCapability capability = new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage);
        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        ironStack(1), 1F, List.of())),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(capability.prepareCalls).isEqualTo(1);
        assertThat(result.plan().commit()).isTrue();
    }

    @Test
    void zero_consume_chance_still_requires_the_full_input_inventory() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 2,
                        ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
    }

    @Test
    void fractional_consume_chance_never_plans_an_empty_input_inventory() {
        for (int attempt = 0; attempt < 64; attempt++) {
            var result = new RequirementPlanner().plan(
                    List.of(new ItemRequirement(RecipeModifier.IOType.INPUT,
                            ironIngredient(), 1, ItemStack.EMPTY,
                            1F, List.of(), DataComponentPredicateSet.EMPTY, 0.5F)),
                    List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(),
                            new BulkItemStorage(64, null))), new PlanningContext(1, 0));

            assertThat(result.successful()).isFalse();
        }
    }

    @Test
    void item_planning_supports_parallelism_above_integer_maximum() {
        long parallelism = (long) Integer.MAX_VALUE + 1L;
        LongItemStorage storage = new LongItemStorage(1, Long.MAX_VALUE, null);
        storage.setContents(0, ironResource(), Long.MAX_VALUE);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1,
                        ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(parallelism, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(parallelism);
    }

    @Test
    void energy_planning_supports_long_parallelism_without_batch_iteration() {
        LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, 1L, null);
        storage.setAmount(Long.MAX_VALUE);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 1)),
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(Long.MAX_VALUE, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shared_long_energy_requirements_find_the_highest_feasible_parallelism() {
        LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
        storage.setAmount(Long.MAX_VALUE);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 1),
                        new EnergyRequirement(RecipeModifier.IOType.INPUT, 1)),
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), CapabilityDirections.input(), storage)),
                new PlanningContext(Long.MAX_VALUE, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(Long.MAX_VALUE / 2L);
    }

    @Test
    void full_context_plan_start_honors_partial_outputs() {
        BulkItemStorage storage = new BulkItemStorage(2, null);
        MachineRecipe recipe = RecipeTestSupport.create(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "partial_context_start"),
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        ironStack(4))),
                false, List.of(), true);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.output(), storage))));

        assertThat(context.planOutputs(recipe, 1).successful()).isTrue();
        assertThat(context.planStart(recipe, 1)).isNotNull();
    }

    @Test
    void smart_output_with_missing_interface_is_blocked_during_planning() {
        FloatValueStorage storage = new FloatValueStorage();
        var result = new RequirementPlanner().plan(
                List.of(SmartInterfaceRequirement.output("missing", 9F)),
                List.of(new StorageCapability(SmartInterfaceRequirement.TYPE.id(),
                        CapabilityDirections.output(), storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
    }

    @Test
    void smart_output_checks_later_capabilities_after_an_interface_miss() {
        FloatValueStorage first = new FloatValueStorage();
        FloatValueStorage second = new FloatValueStorage();
        second.set("mode", 1F);

        var result = new RequirementPlanner().plan(
                List.of(SmartInterfaceRequirement.output("mode", 9F)),
                List.of(
                        new StorageCapability(SmartInterfaceRequirement.TYPE.id(),
                                CapabilityDirections.output(), first),
                        new StorageCapability(SmartInterfaceRequirement.TYPE.id(),
                                CapabilityDirections.output(), second)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(second.value("mode")).contains(9F);
    }

    @Test
    void filtered_context_plans_keep_the_original_recipe_requirement_index() {
        MachineRequirement output = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(1));
        MachineRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT,
                ironIngredient(), 1, ItemStack.EMPTY);
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.forceInsert(ironResource(), 1, false);
        MachineRecipe recipe = RecipeTestSupport.create(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "indexed_requirements"),
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(output, input), true);

        var result = new CraftingContext(new CapabilitySnapshot(List.of(
                new StorageCapability(ItemRequirement.TYPE.id(), CapabilityDirections.input(), storage))))
                .planInputs(recipe, 1);

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).singleElement()
                .extracting(RequirementPlan::requirementIndex).isEqualTo(1);
    }

    @Test
    void output_replacement_preserves_the_original_requirement_tags_and_index() {
        BulkItemStorage untaggedStorage = new BulkItemStorage(1, null);
        BulkItemStorage taggedStorage = new BulkItemStorage(1, null);
        TestType trailingType = type("trailing_output_requirement");
        AtomicInteger trailingIndex = new AtomicInteger(-1);
        register(trailingType, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                trailingIndex.set(context.requirementIndex());
                return new RequirementPlan(context.requirementIndex(), 1, List.of(), null);
            }
        });

        MachineRequirement taggedOutput = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(1), 1F, List.of("primary"));
        MachineRequirement trailingOutput = new TestRequirement(trailingType, RecipeModifier.IOType.OUTPUT);
        StorageCapability untaggedCapability = new StorageCapability(
                ItemRequirement.TYPE.id(), CapabilityDirections.output(), untaggedStorage, List.of("other"));
        StorageCapability taggedCapability = new StorageCapability(
                ItemRequirement.TYPE.id(), CapabilityDirections.output(), taggedStorage, List.of("primary"));
        var result = new CraftingContext(new CapabilitySnapshot(List.of(
                untaggedCapability,
                taggedCapability,
                new TestCapability(trailingType.id(), IOType.OUTPUT, 1))))
                .planOutputRequirements(List.of(taggedOutput, trailingOutput),
                        List.of(new MachineOutput.ItemOutput(Items.GOLD_NUGGET.getDefaultInstance(), 1F),
                                new MachineOutput.ItemOutput(Items.DIAMOND.getDefaultInstance(), 1F)), 1, false);

        assertThat(result.successful()).isTrue();
        assertThat(trailingIndex).hasValue(1);
        assertThat(untaggedCapability.prepareCalls).isEqualTo(1);
        assertThat(taggedCapability.prepareCalls).isEqualTo(1);
        assertThat(result.plan().commit()).isTrue();
        assertThat(untaggedStorage.amount(0)).isEqualTo(1L);
        assertThat(untaggedStorage.resource(0).is(Items.DIAMOND)).isTrue();
        assertThat(taggedStorage.amount(0)).isEqualTo(1L);
        assertThat(taggedStorage.resource(0).is(Items.GOLD_NUGGET)).isTrue();
    }

    private static ItemStack ironStack(int count) {
        ItemStack stack = Items.IRON_INGOT.getDefaultInstance().copyWithCount(count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static ItemStack ironResource() {
        return ironStack(1);
    }

    private static void insertCombinedContents(IOPortBlockEntity port, long itemAmount, long fluidAmount) {
        assertThat(port.itemStorage().forceInsert(0, ironResource(), itemAmount, false)).isEqualTo(itemAmount);
        if (fluidAmount > 0L) {
            assertThat(port.fluidStorage().forceInsert(0, new FluidStack(Fluids.WATER, 1), fluidAmount, false))
                    .isEqualTo(fluidAmount);
        }
    }

    private static ComponentRuntime runtimeFor(IOPortBlockEntity port) {
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(new ProcessingComponent(null, port, BlockPos.ZERO, BlockPos.ZERO,
                List.of(), null)));
        return runtime;
    }

    private static Ingredient ironIngredient() {
        return Ingredient.of(Items.IRON_INGOT);
    }

    private static TestType type(String path) {
        return new TestType(ResourceLocation.fromNamespaceAndPath("mmcr_test", path));
    }

    private static void register(TestType type, RequirementHandler<TestRequirement> handler) {
        type.handler = handler;
        RequirementHandlerRegistry.register(type);
    }

    private static final class TestType implements RequirementType<TestRequirement> {
        private final ResourceLocation id;
        private final MapCodec<TestRequirement> codec;
        private RequirementHandler<TestRequirement> handler;

        private TestType(ResourceLocation id) {
            this.id = id;
            this.codec = MapCodec.unit(() -> new TestRequirement(this, RecipeModifier.IOType.INPUT));
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public MapCodec<TestRequirement> codec() {
            return codec;
        }

        @Override
        public RequirementHandler<TestRequirement> handler() {
            return handler;
        }

        @Override
        public RequirementType.Presentation presentation() {
            return RequirementType.Presentation.defaults(id);
        }
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io, List<String> tags)
            implements MachineRequirement {
        private TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io) {
            this(type, io, List.of());
        }
    }

    private record TestRequest(long parallelism) implements CapabilityRequest {
        @Override
        public CapabilityType type() {
            return new CapabilityType(TYPE.id());
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }
    }

    private static class TestCapability implements MachineCapability, ValueFacet<CapabilityStorage> {
        private final CapabilityType type;
        private final IOType ioType;
        private final int limit;
        private final List<String> tags;
        private final ArrayList<Long> requestedParallelisms = new ArrayList<>();

        private TestCapability(ResourceLocation type, IOType ioType, int limit) {
            this(type, ioType, limit, List.of());
        }

        private TestCapability(ResourceLocation type, IOType ioType, int limit, List<String> tags) {
            this.type = new CapabilityType(type);
            this.ioType = ioType;
            this.limit = limit;
            this.tags = List.copyOf(tags);
        }

        private int limit() {
            return limit;
        }

        private List<Long> requestedParallelisms() {
            return requestedParallelisms;
        }

        @Override
        public CapabilityStorage storage() {
            return null;
        }

        @Override
        public CapabilityType type() {
            return type;
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.of(ioType);
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TestCapability.this.type;
                }

                @Override
                public CapabilityDirections directions() {
                    return TestCapability.this.directions();
                }

                @Override
                public List<String> tags() {
                    return TestCapability.this.tags;
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return Set.of(ValueFacet.class);
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            requestedParallelisms.add(request.parallelism());
            return CapabilityResult::successful;
        }
    }

    private record SimpleHandler(RequirementType<TestRequirement> type) implements RequirementHandler<TestRequirement> {
        @Override
        public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return new RequirementPlan(context.requirementIndex(), capabilities.isEmpty() ? 0 : 2, List.of(), null);
        }
    }

    private record LimitedHandler(RequirementType<TestRequirement> type, int limit, AtomicInteger calls)
            implements RequirementHandler<TestRequirement> {
        @Override
        public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            calls.incrementAndGet();
            return new RequirementPlan(context.requirementIndex(), limit, List.of(), null,
                    (parallelism, reservations) -> new RequirementPlan.OperationPlan(
                            List.of(CapabilityResult::successful), null));
        }
    }

    /**
     * Energy output admission fixture that records reservation and operation behavior.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class EnergyAdmissionCapability implements MachineCapability, EnergyOutputAdmissionFacet {
        private final CapabilityType type = new CapabilityType(EnergyRequirement.TYPE.id());
        private final long capacity;
        private final int outputPriority;
        private final Object reservationIdentity = new Object();
        private final Object reservationKey = new Object();
        private final CapabilityView view = new CapabilityView() {
            @Override
            public CapabilityType type() {
                return EnergyAdmissionCapability.this.type;
            }

            @Override
            public CapabilityDirections directions() {
                return EnergyAdmissionCapability.this.directions();
            }

            @Override
            public Set<Class<? extends CapabilityFacet>> facets() {
                return Set.of(EnergyOutputAdmissionFacet.class);
            }
        };
        private int planOutputCalls;
        private int reservationCalls;
        private int materializedOperations;
        private long committedAmount;

        private EnergyAdmissionCapability(long capacity, int outputPriority) {
            this.capacity = capacity;
            this.outputPriority = outputPriority;
        }

        @Override
        public CapabilityType type() {
            return type;
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.output();
        }

        @Override
        public CapabilityView view() {
            return view;
        }

        @Override
        public int outputPriority() {
            return outputPriority;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            throw new AssertionError("admission capability must not use generic energy operations");
        }

        @Override
        public long outputCapacity(PlanningReservations reservations) {
            return reservations.outputAvailable(reservationIdentity, reservationKey, capacity);
        }

        @Override
        public OutputPlan planOutput(long requestedAmount, PlanningReservations reservations, boolean materialize) {
            planOutputCalls++;
            long accepted = Math.min(requestedAmount, outputCapacity(reservations));
            if (accepted <= 0L) {
                return new OutputPlan(0L, null);
            }
            if (!reservations.reserveOutput(reservationIdentity, reservationKey, accepted)) {
                return new OutputPlan(0L, null);
            }
            reservationCalls++;
            if (!materialize || accepted != requestedAmount) return new OutputPlan(accepted, null);
            materializedOperations++;
            return new OutputPlan(accepted, () -> {
                committedAmount += accepted;
                return CapabilityResult.successful();
            });
        }
    }

    private static class StorageCapability implements MachineCapability, ValueFacet<CapabilityStorage>,
            ItemHandlerFacet, FluidHandlerFacet, OperationFacet {
        private final CapabilityType type;
        private final CapabilityDirections directions;
        private final Object storage;
        private final List<String> tags;
        private int prepareCalls;
        private CapabilityRequests.ItemRequest lastItemRequest;
        private final List<CapabilityRequest> requests = new ArrayList<>();
        private final List<IOType> committedRequestDirections = new ArrayList<>();

        private StorageCapability(ResourceLocation type, CapabilityDirections directions, Object storage) {
            this(type, directions, storage, List.of());
        }

        private StorageCapability(ResourceLocation type, CapabilityDirections directions, Object storage,
                                  List<String> tags) {
            this.type = new CapabilityType(type);
            this.directions = directions;
            this.storage = storage;
            this.tags = List.copyOf(tags);
        }

        @Override
        public CapabilityType type() {
            return type;
        }

        @Override
        public CapabilityDirections directions() {
            return directions;
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return StorageCapability.this.type;
                }

                @Override
                public CapabilityDirections directions() {
                    return StorageCapability.this.directions;
                }

                @Override
                public List<String> tags() {
                    return StorageCapability.this.tags;
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    if (storage instanceof IItemHandler) return Set.of(ItemHandlerFacet.class, OperationFacet.class);
                    if (storage instanceof IFluidHandler) return Set.of(FluidHandlerFacet.class, OperationFacet.class);
                    return Set.of(ValueFacet.class, OperationFacet.class);
                }
            };
        }

        @Override
        public CapabilityStorage storage() {
            return storage instanceof CapabilityStorage valueStorage ? valueStorage : null;
        }

        @Override
        public IItemHandler itemHandler() {
            return (IItemHandler) storage;
        }

        @Override
        public IFluidHandler fluidHandler() {
            return (IFluidHandler) storage;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            prepareCalls++;
            requests.add(request);
            return CapabilityFactories.operation(this, request);
        }

        @Override
        public CapabilityOperation prepareOperation(CapabilityRequest request) {
            if (request instanceof CapabilityRequests.SmartValueRequest smartRequest
                    && storage instanceof FloatValueStorage floatStorage) {
                return () -> {
                    committedRequestDirections.add(smartRequest.ioType());
                    return floatStorage.setExisting(smartRequest.interfaceType(), smartRequest.value())
                            ? CapabilityResult.successful()
                            : CapabilityResult.failure(unknownFailure(type.id(), StatusSeverity.BLOCKED,
                                    FailurePhase.CAPABILITY_COMMIT));
                };
            }
            if (request instanceof CapabilityRequests.ValueRequest valueRequest
                    && storage instanceof LongValueStorage longStorage) {
                return () -> {
                    committedRequestDirections.add(valueRequest.ioType());
                    long moved = valueRequest.insert()
                            ? longStorage.insert(valueRequest.amount(), false)
                            : longStorage.extract(valueRequest.amount(), false);
                    return moved == valueRequest.amount()
                            ? CapabilityResult.successful()
                            : CapabilityResult.failure(unknownFailure(type.id(), StatusSeverity.BLOCKED,
                                    FailurePhase.CAPABILITY_COMMIT));
                };
            }
            if (request instanceof CapabilityRequests.ItemRequest itemRequest
                    && storage instanceof LongItemStorage itemStorage) {
                lastItemRequest = itemRequest;
                return () -> {
                    committedRequestDirections.add(itemRequest.ioType());
                    for (CapabilityRequests.ItemAction action : itemRequest.actions()) {
                        ItemStack current = itemStorage.resource(action.slot());
                        long moved = action.insert()
                                ? itemStorage.forceInsert(action.slot(), action.stack(), action.amount(), false)
                                : !current.isEmpty() && ItemStack.isSameItemSameComponents(current, action.stack())
                                ? itemStorage.forceExtract(action.slot(), action.amount(), false) : 0L;
                        if (moved != action.amount()) return operationFailure();
                    }
                    return CapabilityResult.successful();
                };
            }
            if (request instanceof CapabilityRequests.FluidRequest fluidRequest
                    && storage instanceof LongFluidStorage fluidStorage) {
                return () -> {
                    committedRequestDirections.add(fluidRequest.ioType());
                    for (CapabilityRequests.FluidAction action : fluidRequest.actions()) {
                        FluidStack current = fluidStorage.resource(action.tank());
                        long moved = action.insert()
                                ? fluidStorage.forceInsert(action.tank(), action.stack(), action.amount(), false)
                                : !current.isEmpty() && FluidStack.isSameFluidSameComponents(current, action.stack())
                                ? fluidStorage.forceExtract(action.tank(), action.amount(), false) : 0L;
                        if (moved != action.amount()) return operationFailure();
                    }
                    return CapabilityResult.successful();
                };
            }
            return this::operationFailure;
        }

        private CapabilityResult operationFailure() {
            return CapabilityResult.failure(unknownFailure(type.id(), StatusSeverity.BLOCKED,
                    FailurePhase.CAPABILITY_COMMIT));
        }

        private List<CapabilityRequest> requests() {
            return requests;
        }

        private List<IOType> committedRequestDirections() {
            return committedRequestDirections;
        }
    }

    private static ExecutionStatus unknownFailure(ResourceLocation source, StatusSeverity severity, FailurePhase phase) {
        return unknownFailure(source, severity, phase, Map.of());
    }

    private static ExecutionStatus unknownFailure(ResourceLocation source, StatusSeverity severity, FailurePhase phase,
                                                  Map<String, String> details) {
        return new ExecutionStatus(source, severity, source,
                FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, source, phase, null, null, details));
    }

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        BlockState state = ModBlocks.BLOCKS.get(id).get().defaultBlockState();
        return kind.entityFactory().create(BlockPos.ZERO, state);
    }

}
