package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
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
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
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
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies generic requirement planning without concrete port knowledge.
 *
 * @author howxu <dev@howxu.cn>
 */
class RequirementPlannerTest {
    private static final RequirementType<TestRequirement> TYPE = new RequirementType<>(
            Identifier.fromNamespaceAndPath("mmcr_test", "planner_requirement"));
    private static final RequirementType<TestRequirement> ROLLBACK_FAILURE_TYPE = new RequirementType<>(
            Identifier.fromNamespaceAndPath("mmcr_test", "rollback_failure"));

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void looks_up_handler_filters_capabilities_and_limits_parallelism() {
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return TYPE;
            }

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
        assertThat(matching.requestedParallelisms()).containsExactly(3);
        assertThat(wrongDirection.requestedParallelisms()).isEmpty();
        assertThat(wrongType.requestedParallelisms()).isEmpty();
    }

    @Test
    void supports_mixed_requirements_and_custom_handlers_without_planner_changes() {
        RequirementType<TestRequirement> secondType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "planner_second_requirement"));
        RequirementHandlerRegistry.register(new SimpleHandler(secondType));

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
        RequirementType<TestRequirement> firstType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "planner_once_first"));
        RequirementType<TestRequirement> secondType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "planner_once_second"));
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        RequirementHandlerRegistry.register(new LimitedHandler(firstType, 4, firstCalls));
        RequirementHandlerRegistry.register(new LimitedHandler(secondType, 2, secondCalls));

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
        RequirementType<TestRequirement> unsafeType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "unsafe_direct_operation"));
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return unsafeType;
            }

            @Override
            public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), 1,
                        List.of(transaction -> CapabilityResult.successful()), null);
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
        RequirementType<TestRequirement> factoryType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "single_operation_factory"));
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicInteger operationParallelism = new AtomicInteger();
        TestCapability capability = new TestCapability(factoryType.id(), IOType.INPUT, 2);
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return factoryType;
            }

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
        assertThat(capability.requestedParallelisms()).containsExactly(1);
    }

    @Test
    void does_not_retry_lower_candidates_after_materialization_failure() {
        RequirementType<TestRequirement> failureType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "materialization_failure"));
        AtomicInteger factoryCalls = new AtomicInteger();
        ExecutionStatus materializationFailure = new ExecutionStatus(
                failureType.id(), StatusSeverity.FAILURE, failureType.id(), java.util.Map.of("reason", "factory"));
        TestCapability capability = new TestCapability(failureType.id(), IOType.INPUT, 2);
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return failureType;
            }

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
        assertThat(capability.requestedParallelisms()).containsExactly(2);
    }

    @Test
    void shares_and_rolls_back_reservations_between_candidate_and_final_materialization() {
        RequirementType<TestRequirement> reservationType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "shared_reservation_lifecycle"));
        BulkItemStorage storage = new BulkItemStorage(2, null);
        storage.insert(ironResource(), 2, false);
        StorageCapability capability = new StorageCapability(reservationType.id(), IOType.INPUT, storage);
        PlanningReservations shared = new PlanningReservations();
        AtomicInteger factories = new AtomicInteger();
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return reservationType;
            }

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
        RequirementType<TestRequirement> failureType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "planner_failure_requirement"));
        ExecutionStatus failure = new ExecutionStatus(failureType.id(), StatusSeverity.FAILURE, failureType.id(), java.util.Map.of());
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return failureType;
            }

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
    void partial_fluid_output_commits_the_available_resource_amount() {
        LongFluidStorage storage = new LongFluidStorage(250, null);
        var result = new RequirementPlanner().plan(
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new FluidStack(Fluids.WATER, 1_000), 1F, List.of())),
                List.of(new StorageCapability(FluidRequirement.TYPE.id(), IOType.OUTPUT, storage)),
                new PlanningContext(1, 0, true));

        assertThat(result.successful()).isTrue();
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
        assertThat(fluidResult.successful()).isFalse();
        assertThat(fluidResult.failure().details()).containsEntry("reason", "no_output_capacity");
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
        RequirementType<TestRequirement> taggedType = new RequirementType<>(
                Identifier.fromNamespaceAndPath("mmcr_test", "tagged_requirement"));
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return taggedType;
            }

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
    void full_context_plan_start_honors_partial_outputs() {
        BulkItemStorage storage = new BulkItemStorage(2, null);
        MachineRecipe recipe = new MachineRecipe(
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
        RequirementHandlerRegistry.register(new RequirementHandler<TestRequirement>() {
            @Override
            public RequirementType<TestRequirement> type() {
                return ROLLBACK_FAILURE_TYPE;
            }

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
        MachineRecipe recipe = new MachineRecipe(
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

    private static ItemStack ironStack(int count) {
        ItemStack stack = Items.IRON_INGOT.getDefaultInstance().copyWithCount(count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static ItemResource ironResource() {
        return ItemResource.of(ironStack(1));
    }

    private static Ingredient ironIngredient() {
        return Ingredient.of(Items.IRON_INGOT);
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io, List<String> tags)
            implements MachineRequirement {
        private TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io) {
            this(type, io, List.of());
        }
    }

    private record TestRequest(int parallelism) implements CapabilityRequest {
        @Override
        public CapabilityType type() {
            return new CapabilityType(TYPE.id());
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }
    }

    private static class TestCapability implements MachineCapability {
        private final CapabilityType type;
        private final IOType ioType;
        private final int limit;
        private final List<String> tags;
        private final java.util.ArrayList<Integer> requestedParallelisms = new java.util.ArrayList<>();

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

        private List<Integer> requestedParallelisms() {
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

    private static class StorageCapability implements MachineCapability {
        private final CapabilityType type;
        private final IOType ioType;
        private final CapabilityStorage storage;
        private int prepareCalls;
        private CapabilityRequests.ResourceRequest<?> lastResourceRequest;

        private StorageCapability(Identifier type, IOType ioType, CapabilityStorage storage) {
            this.type = new CapabilityType(type);
            this.ioType = ioType;
            this.storage = storage;
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
