package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies direction propagation at the shared built-in requirement boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class RequirementHandlerSupportTest {
    @Test
    void deferred_plan_materializes_a_current_value_operation_with_explicit_direction() {
        RecordingCapability capability = new RecordingCapability();
        RequirementPlan plan = RequirementHandlerSupport.deferredPlan(new PlanningContext(1, 0), 1,
                (parallelism, reservations) -> new RequirementPlan.OperationPlan(List.of(capability.prepare(
                        new CapabilityRequests.ValueRequest(capability.type(), IOType.OUTPUT, parallelism, 1, true))), null));
        RequirementPlan materialized = plan.materialize(1, new PlanningReservations(), null);

        assertThat(materialized.operations()).hasSize(1);
        CapabilityRequests.ValueRequest request = (CapabilityRequests.ValueRequest) capability.request();
        assertThat(request.ioType()).isEqualTo(IOType.OUTPUT);
        assertThat(request.insert()).isTrue();
    }

    @Test
    void prioritized_output_capabilities_sort_stably_without_mutating_the_source() {
        RecordingCapability low = new RecordingCapability(1);
        RecordingCapability firstEqual = new RecordingCapability(2);
        RecordingCapability high = new RecordingCapability(3);
        RecordingCapability secondEqual = new RecordingCapability(2);
        List<MachineCapability> capabilities = List.of(low, firstEqual, high, secondEqual);

        assertThat(RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities))
                .containsExactly(high, firstEqual, secondEqual, low);
        assertThat(capabilities).containsExactly(low, firstEqual, high, secondEqual);
    }

    private static final class RecordingCapability implements MachineCapability, OperationFacet {
        private static final CapabilityType TYPE = new CapabilityType(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "resource_direction"));
        private CapabilityRequest request;
        private final int outputPriority;

        private RecordingCapability() {
            this(0);
        }

        private RecordingCapability(int outputPriority) {
            this.outputPriority = outputPriority;
        }

        @Override
        public CapabilityType type() {
            return TYPE;
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.bidirectional();
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TYPE;
                }

                @Override
                public CapabilityDirections directions() {
                    return CapabilityDirections.bidirectional();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return Set.of(OperationFacet.class);
                }
            };
        }

        @Override
        public int outputPriority() {
            return outputPriority;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityFactories.operation(this, request);
        }

        @Override
        public CapabilityOperation prepareOperation(CapabilityRequest request) {
            this.request = request;
            return CapabilityResult::successful;
        }

        private CapabilityRequest request() {
            return request;
        }
    }
}
