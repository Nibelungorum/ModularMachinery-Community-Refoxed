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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies generic requirement planning without concrete port knowledge.
 *
 * @author howxu <dev@howxu.cn>
 */
class RequirementPlannerTest {
    private static final RequirementType<TestRequirement> TYPE = new RequirementType<>(
            Identifier.fromNamespaceAndPath("mmcr_test", "planner_requirement"));

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
                return new RequirementPlan(context.requirementIndex(), limit,
                        capabilities.stream().map(capability -> capability.prepare(new TestRequest(context.requestedParallelism())))
                                .toList(), null);
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
        assertThat(matching.requestedParallelisms()).containsExactly(8);
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
    void built_in_item_and_fluid_handlers_commit_real_resource_storage_operations_in_order() {
        BulkItemStorage itemStorage = new BulkItemStorage(64, null);
        itemStorage.insert(ItemResource.of(Items.IRON_INGOT), 2, false);
        LongFluidStorage fluidStorage = new LongFluidStorage(2_000, null);
        fluidStorage.setFluid(new FluidStack(Fluids.WATER, 1_000));

        var result = new RequirementPlanner().plan(
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY),
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
                        Items.IRON_INGOT.getDefaultInstance(), 1F, List.of())),
                List.of(capability), new PlanningContext(1, 0));

        assertThat(result.successful()).isTrue();
        assertThat(capability.prepareCalls).isEqualTo(1);
        assertThat(result.plan().commit()).isTrue();
    }

    @Test
    void zero_consume_chance_still_requires_the_full_input_inventory() {
        BulkItemStorage storage = new BulkItemStorage(64, null);
        var result = new RequirementPlanner().plan(
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                        ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)),
                List.of(new StorageCapability(ItemRequirement.TYPE.id(), IOType.INPUT, storage)),
                new PlanningContext(1, 0));

        assertThat(result.successful()).isFalse();
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
    void filtered_context_plans_keep_the_original_recipe_requirement_index() {
        MachineRequirement output = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                Items.IRON_INGOT.getDefaultInstance());
        MachineRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT,
                Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY);
        BulkItemStorage storage = new BulkItemStorage(64, null);
        storage.insert(ItemResource.of(Items.IRON_INGOT), 1, false);
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

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
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
        private final java.util.ArrayList<Integer> requestedParallelisms = new java.util.ArrayList<>();

        private TestCapability(Identifier type, IOType ioType, int limit) {
            this.type = new CapabilityType(type);
            this.ioType = ioType;
            this.limit = limit;
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

    private static final class StorageCapability implements MachineCapability {
        private final CapabilityType type;
        private final IOType ioType;
        private final CapabilityStorage storage;
        private int prepareCalls;

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
            CapabilityRequests.ResourceRequest<?> resourceRequest = (CapabilityRequests.ResourceRequest<?>) request;
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
}
