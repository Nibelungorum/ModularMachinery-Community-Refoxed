package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
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
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.minecraft.world.level.material.Fluids;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
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
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
    private static final TestType ROLLBACK_FAILURE_TYPE = type("rollback_failure");
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
                    public CapabilityResult commit(TransactionContext transaction) {
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
        assertThat(result.failure().details()).containsEntry("reason", "unsafe_operation_parallelism");
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
                                ? new ExecutionStatus(factoryType.id(), StatusSeverity.BLOCKED, factoryType.id(),
                                java.util.Map.of("reason", "shared_reservation"))
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
        ExecutionStatus materializationFailure = new ExecutionStatus(
                failureType.id(), StatusSeverity.FAILURE, failureType.id(), java.util.Map.of("reason", "factory"));
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
        storage.insert(ironResource(), 2, false);
        StorageCapability capability = new StorageCapability(reservationType.id(), IOType.INPUT, storage);
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
                            assertThat(reservations.reserveExtract(
                                    storage, 0, ironResource(), parallelism)).isTrue();
                            assertThat(reservations.amount(storage, 0)).isEqualTo(2 - parallelism * factories.get());
                            CapabilityRequests.ResourceAction<ItemResource> action =
                                    new CapabilityRequests.ResourceAction<>(0, ironResource(),
                                            parallelism, false);
                            return new RequirementPlan.OperationPlan(List.of(capability.prepare(
                                    new CapabilityRequests.ResourceRequest<>(capability.type(), capability.ioType(),
                                            parallelism, List.of(action)))), null);
                        },
                        (parallelism, reservations) -> reservations.reserveExtract(
                                storage, 0, ironResource(), parallelism)
                                ? null
                                : new ExecutionStatus(reservationType.id(), StatusSeverity.BLOCKED, reservationType.id(),
                                java.util.Map.of("reason", "shared_reservation")));
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
        ExecutionStatus failure = new ExecutionStatus(failureType.id(), StatusSeverity.FAILURE, failureType.id(), java.util.Map.of());
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
    void built_in_energy_handler_prepares_a_real_transactional_storage_operation() {
        LongValueStorage storage = new LongValueStorage(100, 100, null);
        storage.setAmount(10);
        MachineCapability capability = new TestCapability(EnergyRequirement.TYPE.id(), IOType.INPUT, 1) {
            @Override
            public CapabilityStorage storage() {
                return storage;
            }

            @Override
            public CapabilityOperation prepare(cn.howxu.mmcr.api.capability.CapabilityRequest request) {
                assertThat(request).isInstanceOf(CapabilityRequests.ValueRequest.class);
                CapabilityRequests.ValueRequest valueRequest = (CapabilityRequests.ValueRequest) request;
                return transaction -> {
                    storage.updateSnapshots(transaction);
                    long moved = storage.extract(valueRequest.amount(), false);
                    return moved == valueRequest.amount()
                            ? CapabilityResult.successful()
                            : CapabilityResult.failure(new ExecutionStatus(
                                    EnergyRequirement.TYPE.id(), StatusSeverity.BLOCKED,
                                    EnergyRequirement.TYPE.id(), java.util.Map.of()));
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
        storage.insert(ironResource(), 1, false);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1,
                        ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, storage)),
                new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void fluid_shortage_returns_a_real_operation_for_the_available_parallelism() {
        LongFluidStorage storage = new LongFluidStorage(2_000, null);
        storage.setFluid(new FluidStack(Fluids.WATER, 1_000));

        var result = new RequirementPlanner().plan(
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                        FluidStack.EMPTY)),
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), IOType.INPUT, storage)),
                new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.getAmountAsLong()).isZero();
    }

    @Test
    void partial_item_output_commits_the_available_resource_amount() {
        BulkItemStorage storage = new BulkItemStorage(2, null);
        StorageCapability capability = new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage);
        ItemStack output = ironStack(4);
        assertThat(output.getCount()).isEqualTo(4);
        assertThat(storage.capacityResource(0, ItemResource.of(output))).isEqualTo(2);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output, 1F, List.of());
        assertThat(requirement.stack(null).getCount()).isEqualTo(4);
        var result = new RequirementPlanner().plan(
                List.of(requirement),
                List.of(capability),
                new PlanningContext(1, 0, true));

        assertThat(result.successful()).isTrue();
        assertThat(capability.lastResourceRequest.actions()).singleElement()
                .extracting(CapabilityRequests.ResourceAction::amount).isEqualTo(2L);
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
        assertThat(bus.itemStorage().resource(0).toStack(96).get(DataComponents.CUSTOM_NAME))
                .isEqualTo(Component.literal("data output"));
    }

    @Test
    void output_simulation_reports_full_fit() {
        BulkItemStorage storage = new BulkItemStorage(4, null);
        ItemRequirement requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(4), 1F, List.of());

        var result = new RequirementPlanner().plan(
                List.of(requirement),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage)),
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
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage)),
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
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().details()).containsEntry("reason", "insufficient_resource");
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
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().details()).containsEntry("reason", "no_output_capacity");
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
        inputStorage.insert(ironResource(), 1, false);

        var result = new RequirementPlanner().plan(
                List.of(input, output),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, inputStorage),
                        new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, outputStorage)),
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
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), IOType.OUTPUT, storage)),
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
        StorageCapability capability = new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output)),
                List.of(capability), new PlanningContext(2, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1);
        assertThat(capability.lastResourceRequest.actions()).singleElement()
                .extracting(CapabilityRequests.ResourceAction::amount).isEqualTo(64L);
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(64L);
    }

    @Test
    void partial_outputs_without_any_capacity_are_structured_blocked_failures() {
        var itemResult = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        ironStack(1), 1F, List.of())),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT,
                        new BulkItemStorage(0, null))), new PlanningContext(1, 0, true));
        var fluidResult = new RequirementPlanner().plan(
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new FluidStack(Fluids.WATER, 1_000), 1F, List.of())),
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), IOType.OUTPUT,
                        new LongFluidStorage(0, null))), new PlanningContext(1, 0, true));

        assertThat(itemResult.successful()).isFalse();
        assertThat(itemResult.failure().details()).containsEntry("reason", "no_output_capacity");
        assertThat(itemResult.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(1L);
                    assertThat(simulation.accepted()).isZero();
                    assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
                });
        assertThat(fluidResult.successful()).isFalse();
        assertThat(fluidResult.failure().details()).containsEntry("reason", "no_output_capacity");
        assertThat(fluidResult.outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(1_000L);
                    assertThat(simulation.accepted()).isZero();
                    assertThat(simulation.fit()).isEqualTo(OutputFit.NONE);
                });
    }

    @Test
    void planner_rejects_different_resources_in_non_empty_zero_quantity_slots() {
        ZeroQuantityItemStorage itemStorage = new ZeroQuantityItemStorage(ItemResource.of(Items.IRON_INGOT));
        var itemResult = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new ItemStack(Items.GOLD_NUGGET, 1), 1F, List.of())),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, itemStorage)),
                new PlanningContext(1, 0));

        assertThat(itemResult.successful()).isFalse();
        assertThat(itemResult.failure().details()).containsEntry("reason", "no_output_capacity");
        assertThat(itemResult.outputSimulations()).singleElement()
                .extracting(simulation -> simulation.fit()).isEqualTo(OutputFit.NONE);
        assertThat(itemStorage.amount(0)).isZero();

        ZeroQuantityFluidStorage fluidStorage = new ZeroQuantityFluidStorage(FluidResource.of(Fluids.LAVA));
        var fluidResult = new RequirementPlanner().plan(
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new FluidStack(Fluids.WATER, 1), 1F, List.of())),
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), IOType.OUTPUT, fluidStorage)),
                new PlanningContext(1, 0));

        assertThat(fluidResult.successful()).isFalse();
        assertThat(fluidResult.failure().details()).containsEntry("reason", "no_output_capacity");
        assertThat(fluidResult.outputSimulations()).singleElement()
                .extracting(simulation -> simulation.fit()).isEqualTo(OutputFit.NONE);
        assertThat(fluidStorage.amount(0)).isZero();
    }

    @Test
    void output_energy_without_capacity_is_reported_as_output_capacity_failure() {
        LongValueStorage storage = new LongValueStorage(100L, 100L, null);
        storage.setAmount(100L);

        var result = new RequirementPlanner().plan(
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)),
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), IOType.OUTPUT, storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().details()).containsEntry("reason", "no_output_capacity");
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
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), IOType.OUTPUT, storage)),
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
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), IOType.OUTPUT, storage)),
                new PlanningContext(1, 0, true));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().outputSimulations()).singleElement()
                .satisfies(simulation -> {
                    assertThat(simulation.requested()).isEqualTo(60L);
                    assertThat(simulation.accepted()).isEqualTo(50L);
                    assertThat(simulation.fit()).isEqualTo(OutputFit.PARTIAL);
                });
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
        MachineCapability capability = new StorageCapability(EnergyRequirement.TYPE.id(), IOType.INPUT, storage);

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
        storage.insert(ironResource(), 1, false);

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure()).isNotNull();
        assertThat(storage.amount(0)).isEqualTo(1);
    }

    @Test
    void shared_item_slot_lowers_parallelism_before_materializing_operations() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.insert(ironResource(), 2, false);

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, storage)),
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
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage)),
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
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage)),
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
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage)),
                new PlanningContext(2, 0, true));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().details()).containsEntry("reason", "no_output_capacity");
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
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), IOType.INPUT, storage)),
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
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), IOType.INPUT, storage)),
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
        FluidResource water = FluidResource.of(Fluids.WATER);
        storage.setContents(water, Long.MAX_VALUE);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveExtract(storage, 0, water, Long.MAX_VALUE)).isTrue();
        assertThat(reservations.reserveInsert(storage, 0, water, Long.MAX_VALUE)).isTrue();
        assertThat(reservations.reserveInsert(storage, 0, water, 1L)).isFalse();
        assertThat(reservations.amount(storage, 0)).isEqualTo(Long.MAX_VALUE);
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
        FluidResource water = FluidResource.of(Fluids.WATER);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveValue(valueStorage, Long.MIN_VALUE, true)).isFalse();
        assertThat(reservations.reserveInsert(resourceStorage, 0, water, Long.MIN_VALUE)).isFalse();
        assertThat(reservations.reserveExtract(resourceStorage, 0, water, Long.MIN_VALUE)).isFalse();
        assertThat(reservations.valueAvailable(valueStorage, true)).isEqualTo(Long.MAX_VALUE);
        assertThat(reservations.amount(resourceStorage, 0)).isZero();
    }

    @Test
    void built_in_item_and_fluid_handlers_commit_real_resource_storage_operations_in_order() {
        BulkItemStorage itemStorage = new BulkItemStorage(64, null);
        itemStorage.insert(ironResource(), 2, false);
        LongFluidStorage fluidStorage = new LongFluidStorage(2_000, null);
        fluidStorage.setFluid(new FluidStack(Fluids.WATER, 1_000));

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 2, ItemStack.EMPTY),
                        new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000, FluidStack.EMPTY)
                ),
                List.of(
                        new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, itemStorage),
                        new StorageCapability(FluidRequirement.TYPE.id(), IOType.INPUT, fluidStorage)
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
                cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.TYPE.id(), IOType.OUTPUT, storage);

        var result = new RequirementPlanner().plan(
                List.of(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.output("mode", 9F)),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.value("mode")).contains(9F);
    }

    @Test
    void built_in_chance_decision_prepares_the_operation_once() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        StorageCapability capability = new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage);
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
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, storage)),
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
                    List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT,
                            new BulkItemStorage(64, null))), new PlanningContext(1, 0));

            assertThat(result.successful()).isFalse();
        }
    }

    @Test
    void item_planning_supports_parallelism_above_integer_maximum() {
        long parallelism = (long) Integer.MAX_VALUE + 1L;
        LongResourceStorage<ItemResource> storage = new LongResourceStorage<>(
                ItemResource.class, 1, Long.MAX_VALUE, ItemResource::isEmpty, null);
        storage.setContents(0, ironResource(), Long.MAX_VALUE);

        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1,
                        ItemStack.EMPTY)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, storage)),
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
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), IOType.INPUT, storage)),
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
                List.of(new StorageCapability(EnergyRequirement.TYPE.id(), IOType.INPUT, storage)),
                new PlanningContext(Long.MAX_VALUE, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(Long.MAX_VALUE / 2L);
    }

    @Test
    void full_context_plan_start_honors_partial_outputs() {
        BulkItemStorage storage = new BulkItemStorage(2, null);
        MachineRecipe recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr_test", "partial_context_start"),
                Identifier.fromNamespaceAndPath("mmcr_test", "machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        ironStack(4))),
                false, List.of(), true);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new StorageCapability(ItemRequirement.TYPE.id(), IOType.OUTPUT, storage))));

        assertThat(context.planOutputs(recipe, 1).successful()).isTrue();
        assertThat(context.planStart(recipe, 1)).isNotNull();
    }

    @Test
    void smart_output_with_missing_interface_is_blocked_during_planning() {
        FloatValueStorage storage = new FloatValueStorage();
        var result = new RequirementPlanner().plan(
                List.of(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.output("missing", 9F)),
                List.of(new StorageCapability(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.TYPE.id(),
                        IOType.OUTPUT, storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().details()).containsEntry("reason", "missing_smart_interface");
    }

    @Test
    void smart_output_checks_later_capabilities_after_an_interface_miss() {
        FloatValueStorage first = new FloatValueStorage();
        FloatValueStorage second = new FloatValueStorage();
        second.set("mode", 1F);

        var result = new RequirementPlanner().plan(
                List.of(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.output("mode", 9F)),
                List.of(
                        new StorageCapability(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.TYPE.id(),
                                IOType.OUTPUT, first),
                        new StorageCapability(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.TYPE.id(),
                                IOType.OUTPUT, second)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).singleElement().satisfies(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isTrue();
        assertThat(second.value("mode")).contains(9F);
    }

    @Test
    void rolls_back_real_port_item_fluid_and_energy_operations_when_a_later_operation_fails() {
        ItemBusCapability item = (ItemBusCapability) port("item_input_bus").capabilitySnapshot().capabilities().getFirst();
        FluidHatchCapability fluid = (FluidHatchCapability) port("fluid_input_hatch").capabilitySnapshot().capabilities().getFirst();
        EnergyHatchCapability energy = (EnergyHatchCapability) port("energy_input_hatch_tiny").capabilitySnapshot().capabilities().getFirst();
        try (Transaction transaction = Transaction.openRoot()) {
            item.storage().insert(0, ironResource(), 1, transaction);
            transaction.commit();
        }
        ((LongFluidStorage) fluid.storage()).setFluid(new FluidStack(Fluids.WATER, 1_000));
        energy.storage().setAmount(4);
        register(ROLLBACK_FAILURE_TYPE, new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), 1,
                        List.of(transaction -> CapabilityResult.failure(new ExecutionStatus(
                                ROLLBACK_FAILURE_TYPE.id(), StatusSeverity.FAILURE, ROLLBACK_FAILURE_TYPE.id(),
                                java.util.Map.of("reason", "forced_failure")))), null);
            }
        });

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, ironIngredient(), 1, ItemStack.EMPTY),
                        new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1_000,
                                 FluidStack.EMPTY),
                        new EnergyRequirement(RecipeModifier.IOType.INPUT, 4),
                        new TestRequirement(ROLLBACK_FAILURE_TYPE, RecipeModifier.IOType.INPUT)),
                List.of(
                        item, fluid, energy, new TestCapability(ROLLBACK_FAILURE_TYPE.id(), IOType.INPUT, 1)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().requirements()).allSatisfy(plan ->
                assertThat(plan.operations()).isNotEmpty());
        assertThat(result.plan().commit()).isFalse();
        assertThat(item.storage().amount(0)).isEqualTo(1);
        assertThat(fluid.storage().amount(0)).isEqualTo(1_000);
        assertThat(energy.storage().amount()).isEqualTo(4);
    }

    @Test
    void filtered_context_plans_keep_the_original_recipe_requirement_index() {
        MachineRequirement output = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                ironStack(1));
        MachineRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT,
                ironIngredient(), 1, ItemStack.EMPTY);
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.insert(ironResource(), 1, false);
        MachineRecipe recipe = RecipeTestSupport.create(
                Identifier.fromNamespaceAndPath("mmcr_test", "indexed_requirements"),
                Identifier.fromNamespaceAndPath("mmcr_test", "machine"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(output, input), true);

        var result = new CraftingContext(new CapabilitySnapshot(List.of(
                new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, storage))))
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
                ItemRequirement.TYPE.id(), IOType.OUTPUT, untaggedStorage, List.of("other"));
        StorageCapability taggedCapability = new StorageCapability(
                ItemRequirement.TYPE.id(), IOType.OUTPUT, taggedStorage, List.of("primary"));
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
        assertThat(untaggedStorage.resource(0).toStack(1).is(Items.DIAMOND)).isTrue();
        assertThat(taggedStorage.amount(0)).isEqualTo(1L);
        assertThat(taggedStorage.resource(0).toStack(1).is(Items.GOLD_NUGGET)).isTrue();
    }

    private static ItemStack ironStack(int count) {
        ItemStack stack = Items.IRON_INGOT.getDefaultInstance().copyWithCount(count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static ItemResource ironResource() {
        return ItemResource.of(ironStack(1));
    }

    private static void insertCombinedContents(IOPortBlockEntity port, long itemAmount, long fluidAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(port.itemStorage().insert(0, ironResource(), itemAmount, transaction)).isEqualTo(itemAmount);
            if (fluidAmount > 0L) {
                assertThat(port.fluidStorage().insert(0, FluidResource.of(Fluids.WATER), fluidAmount, transaction))
                        .isEqualTo(fluidAmount);
            }
            transaction.commit();
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
        return new TestType(Identifier.fromNamespaceAndPath("mmcr_test", path));
    }

    private static void register(TestType type, RequirementHandler<TestRequirement> handler) {
        type.handler = handler;
        RequirementHandlerRegistry.register(type);
    }

    private static final class ZeroQuantityItemStorage extends LongResourceStorage<ItemResource> {
        private final ItemResource slotResource;

        private ZeroQuantityItemStorage(ItemResource slotResource) {
            super(ItemResource.class, 1, 64L, ItemResource::isEmpty, () -> {});
            this.slotResource = slotResource;
        }

        @Override
        public ItemResource resource(int slot) {
            return slot == 0 ? slotResource : super.resource(slot);
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return true;
        }
    }

    private static final class ZeroQuantityFluidStorage extends LongResourceStorage<FluidResource> {
        private final FluidResource slotResource;

        private ZeroQuantityFluidStorage(FluidResource slotResource) {
            super(FluidResource.class, 1, 64L, FluidResource::isEmpty, () -> {});
            this.slotResource = slotResource;
        }

        @Override
        public FluidResource resource(int slot) {
            return slot == 0 ? slotResource : super.resource(slot);
        }

        @Override
        public boolean isValid(int slot, FluidResource resource) {
            return true;
        }
    }

    private static final class TestType implements RequirementType<TestRequirement> {
        private final Identifier id;
        private final MapCodec<TestRequirement> codec;
        private RequirementHandler<TestRequirement> handler;

        private TestType(Identifier id) {
            this.id = id;
            this.codec = MapCodec.unit(() -> new TestRequirement(this, RecipeModifier.IOType.INPUT));
        }

        @Override
        public Identifier id() {
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
        private final java.util.ArrayList<Long> requestedParallelisms = new java.util.ArrayList<>();

        private TestCapability(Identifier type, IOType ioType, int limit) {
            this(type, ioType, limit, List.of());
        }

        private TestCapability(Identifier type, IOType ioType, int limit, List<String> tags) {
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
        public IOType ioType() {
            return ioType;
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TestCapability.this.type;
                }

                @Override
                public IOType ioType() {
                    return TestCapability.this.ioType;
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
            return transaction -> CapabilityResult.successful();
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
                            List.of(transaction -> CapabilityResult.successful()), null));
        }
    }

    private static class StorageCapability implements MachineCapability, ValueFacet<CapabilityStorage> {
        private final CapabilityType type;
        private final IOType ioType;
        private final CapabilityStorage storage;
        private final List<String> tags;
        private int prepareCalls;
        private CapabilityRequests.ResourceRequest<?> lastResourceRequest;

        private StorageCapability(Identifier type, IOType ioType, CapabilityStorage storage) {
            this(type, ioType, storage, List.of());
        }

        private StorageCapability(Identifier type, IOType ioType, CapabilityStorage storage, List<String> tags) {
            this.type = new CapabilityType(type);
            this.ioType = ioType;
            this.storage = storage;
            this.tags = List.copyOf(tags);
        }

        @Override
        public CapabilityType type() {
            return type;
        }

        @Override
        public IOType ioType() {
            return ioType;
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return StorageCapability.this.type;
                }

                @Override
                public IOType ioType() {
                    return StorageCapability.this.ioType;
                }

                @Override
                public List<String> tags() {
                    return StorageCapability.this.tags;
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return Set.of(ValueFacet.class);
                }
            };
        }

        @Override
        public CapabilityStorage storage() {
            return storage;
        }

        @Override
        public CapabilityOperation prepare(cn.howxu.mmcr.api.capability.CapabilityRequest request) {
            prepareCalls++;
            if (request instanceof CapabilityRequests.SmartValueRequest smartRequest
                    && storage instanceof FloatValueStorage floatStorage) {
                return transaction -> floatStorage.set(smartRequest.interfaceType(), smartRequest.value(), transaction)
                        ? CapabilityResult.successful()
                        : CapabilityResult.failure(new ExecutionStatus(type.id(), StatusSeverity.BLOCKED,
                                type.id(), java.util.Map.of()));
            }
            if (request instanceof CapabilityRequests.ValueRequest valueRequest
                    && storage instanceof LongValueStorage longStorage) {
                return transaction -> {
                    longStorage.updateSnapshots(transaction);
                    long moved = valueRequest.insert()
                            ? longStorage.insert(valueRequest.amount(), false)
                            : longStorage.extract(valueRequest.amount(), false);
                    return moved == valueRequest.amount()
                            ? CapabilityResult.successful()
                            : CapabilityResult.failure(new ExecutionStatus(type.id(), StatusSeverity.BLOCKED,
                                    type.id(), java.util.Map.of()));
                };
            }
            CapabilityRequests.ResourceRequest<?> resourceRequest = (CapabilityRequests.ResourceRequest<?>) request;
            lastResourceRequest = resourceRequest;
            if (!(storage instanceof ResourceStorage<?> resourceStorage)) {
                return transaction -> CapabilityResult.failure(new ExecutionStatus(
                        type.id(), StatusSeverity.BLOCKED, type.id(), java.util.Map.of()));
            }
            return transaction -> {
                for (CapabilityRequests.ResourceAction<?> action : resourceRequest.actions()) {
                    long moved = action.insert()
                            ? resourceStorage.insertResource(action.slot(), action.resource(), action.amount(), transaction)
                            : resourceStorage.extractResource(action.slot(), action.resource(), action.amount(), transaction);
                    if (moved != action.amount()) return CapabilityResult.failure(new ExecutionStatus(
                            type.id(), StatusSeverity.BLOCKED, type.id(), java.util.Map.of()));
                }
                return CapabilityResult.successful();
            };
        }
    }

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        BlockState state = ModBlocks.BLOCKS.get(id).get().defaultBlockState();
        return kind.entityFactory().create(BlockPos.ZERO, state);
    }

}
