package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.MapCodec;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies opaque capability operation behavior across planning and execution.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityOperationTest {
    private static final ExecutionStatus TYPED_FAILURE = new ExecutionStatus(
            Identifier.fromNamespaceAndPath("mmcr_test", "opaque_operation_failure"),
            StatusSeverity.BLOCKED,
            Identifier.fromNamespaceAndPath("mmcr_test", "opaque_operation"),
            Map.of("reason", "opaque_failure"));
    private static final OpaqueType TYPE = new OpaqueType(
            Identifier.fromNamespaceAndPath("mmcr_test", "opaque_operation_requirement"));

    @Test
    void default_parallelism_adaptation_keeps_an_opaque_operation_reusable() {
        CapabilityOperation operation = transaction -> CapabilityResult.successful();

        assertThat(operation.forParallelism(3L)).isSameAs(operation);
        assertThatThrownBy(() -> operation.forParallelism(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void opaque_operation_is_reserved_then_materialized_once_and_commits_transactionally() {
        LongValueStorage storage = new LongValueStorage(4L, 4L, null);
        storage.setAmount(1L);
        AtomicInteger materializationCalls = new AtomicInteger();
        OpaqueCapability capability = new OpaqueCapability(storage);

        TYPE.handler = new RequirementHandler<OpaqueRequirement>() {
            @Override
            public RequirementPlan plan(OpaqueRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext context) {
                return new RequirementPlan(context.requirementIndex(), 2L, List.of(), null,
                        (parallelism, reservations) -> {
                            materializationCalls.incrementAndGet();
                            if (!reservations.reserveValue(storage, parallelism, false)) {
                                return new RequirementPlan.OperationPlan(List.of(), TYPED_FAILURE);
                            }
                            return new RequirementPlan.OperationPlan(List.of(
                                    capability.prepare(new OpaqueRequest(parallelism, "custom-value"))), null);
                        },
                        (parallelism, reservations) -> reservations.reserveValue(storage, parallelism, false)
                                ? null : TYPED_FAILURE);
            }
        };
        RequirementHandlerRegistry.register(TYPE);

        PlanningResult result = new RequirementPlanner().plan(
                List.of(new OpaqueRequirement(TYPE, RecipeModifier.IOType.INPUT)),
                List.of(capability), new PlanningContext(2L, 0));

        assertThat(result.successful()).isTrue();
        assertThat(result.plan().parallelism()).isEqualTo(1L);
        assertThat(materializationCalls).hasValue(1);
        assertThat(capability.requests).containsExactly(new OpaqueRequest(1L, "custom-value"));
        assertThat(storage.amount()).isEqualTo(1L);
        assertThat(result.plan().commit()).isTrue();
        assertThat(storage.amount()).isZero();
    }

    @Test
    void opaque_operation_reports_its_typed_failure_without_planner_dispatch() {
        LongValueStorage priorStorage = new LongValueStorage(4L, 4L, null);
        LongValueStorage failedStorage = new LongValueStorage(4L, 4L, null);
        priorStorage.setAmount(1L);
        failedStorage.setAmount(1L);
        CapabilityOperation priorOperation = transaction -> {
            priorStorage.insert(1L, transaction);
            return CapabilityResult.successful();
        };
        CapabilityOperation operation = transaction -> {
            failedStorage.insert(1L, transaction);
            return CapabilityResult.failure(TYPED_FAILURE);
        };
        CraftingPlan plan = new CraftingPlan(
                List.of(new RequirementPlan(0, 1L, List.of(priorOperation, operation), null)), 1L);

        assertThat(plan.commit()).isFalse();
        assertThat(priorStorage.amount()).isEqualTo(1L);
        assertThat(failedStorage.amount()).isEqualTo(1L);
        assertThat(plan.failure()).isSameAs(TYPED_FAILURE);
    }

    @Test
    void built_in_capability_exposes_a_typed_operation_facet() {
        EnergyHatchCapability capability = new EnergyHatchCapability(
                new LongValueStorage(4L, 4L, null), IOType.INPUT);

        assertThat(capability.facet(OperationFacet.class)).contains(capability);
    }

    private record OpaqueRequirement(RequirementType<OpaqueRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
    }

    private static final class OpaqueType implements RequirementType<OpaqueRequirement> {
        private final Identifier id;
        private final MapCodec<OpaqueRequirement> codec;
        private RequirementHandler<OpaqueRequirement> handler;

        private OpaqueType(Identifier id) {
            this.id = id;
            this.codec = MapCodec.unit(() -> new OpaqueRequirement(this, RecipeModifier.IOType.INPUT));
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public MapCodec<OpaqueRequirement> codec() {
            return codec;
        }

        @Override
        public RequirementHandler<OpaqueRequirement> handler() {
            return handler;
        }

        @Override
        public RequirementType.Presentation presentation() {
            return RequirementType.Presentation.defaults(id);
        }
    }

    private record OpaqueRequest(long parallelism, String value) implements CapabilityRequest {
        @Override
        public CapabilityType type() {
            return new CapabilityType(TYPE.id());
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }
    }

    private static final class OpaqueCapability implements MachineCapability {
        private final LongValueStorage storage;
        private final List<OpaqueRequest> requests = new java.util.ArrayList<>();

        private OpaqueCapability(LongValueStorage storage) {
            this.storage = storage;
        }

        @Override
        public CapabilityType type() {
            return new CapabilityType(TYPE.id());
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return OpaqueCapability.this.type();
                }

                @Override
                public IOType ioType() {
                    return OpaqueCapability.this.ioType();
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            OpaqueRequest opaque = (OpaqueRequest) request;
            requests.add(opaque);
            return transaction -> storage.extract(opaque.parallelism(), transaction) == opaque.parallelism()
                    ? CapabilityResult.successful() : CapabilityResult.failure(TYPED_FAILURE);
        }
    }
}
