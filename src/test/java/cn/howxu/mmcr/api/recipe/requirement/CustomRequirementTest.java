package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.util.IOType;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a custom requirement can use the public type and handler contracts end to end.
 *
 * @author howxu <dev@howxu.cn>
 */
class CustomRequirementTest {
    private static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath(
            "mmcr_test", "virtual_scalar_requirement");
    private static final MapCodec<TestRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.fieldOf("io").forGetter(TestRequirement::io),
            Codec.LONG.fieldOf("amount").forGetter(TestRequirement::amount),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(TestRequirement::tags)
    ).apply(instance, (ignored, io, amount, tags) -> new TestRequirement(io, amount, tags)));
    private static final RequirementHandler<TestRequirement> HANDLER = new RequirementHandler<>() {
        @Override
        public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            VirtualScalarCapability capability = (VirtualScalarCapability) capabilities.getFirst();
            long maximum = Math.min(context.requestedParallelism(), capability.storage.amount());
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null,
                    (parallelism, reservations) -> {
                        if (!reservations.reserveValue(capability.storage, parallelism, false)) {
                            return new RequirementPlan.OperationPlan(List.of(), failure());
                        }
                        return new RequirementPlan.OperationPlan(List.of(capability.prepare(
                                 new VirtualRequest(parallelism))), null);
                    },
                    (parallelism, reservations) -> reservations.reserveValue(capability.storage, parallelism, false)
                            ? null : failure());
        }

        private ExecutionStatus failure() {
            return new ExecutionStatus(TYPE_ID, StatusSeverity.BLOCKED, TYPE_ID,
                    java.util.Map.of("reason", "virtual_scalar_reserved"));
        }
    };
    private static final RequirementType<TestRequirement> TYPE = new RequirementType.Definition<>(
            TYPE_ID, CODEC, HANDLER, new RequirementType.Presentation("test.virtual_scalar", "test.virtual_scalar.description"));

    @Test
    void custom_requirement_registers_plans_commits_copies_and_round_trips() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(TYPE);
            TestRequirement requirement = new TestRequirement(RecipeModifier.IOType.INPUT, 3,
                    List.of("virtual"));
            VirtualScalarCapability capability = new VirtualScalarCapability(2);

            JsonElement encoded = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow();
            assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow())
                    .isEqualTo(requirement);
            assertThat(TYPE.presentation().translationKey()).isEqualTo("test.virtual_scalar");

            MachineRequirement copy = MachineRequirement.copyOf(requirement);
            assertThat(copy).isEqualTo(requirement).isNotSameAs(requirement);

            var result = new RequirementPlanner().plan(List.of(requirement), List.of(capability),
                    new PlanningContext(3, 0));

            assertThat(result.successful()).isTrue();
            assertThat(result.plan().parallelism()).isEqualTo(2);
            assertThat(result.plan().commit()).isTrue();
            assertThat(capability.storage.amount()).isZero();
        }
    }

    private record TestRequirement(RecipeModifier.IOType io, long amount, List<String> tags)
            implements CustomRequirement {
        private TestRequirement {
            tags = List.copyOf(tags);
        }

        @Override
        public RequirementType<TestRequirement> type() {
            return TYPE;
        }
    }

    private record VirtualRequest(long parallelism) implements CapabilityRequest {
        @Override
        public CapabilityType type() {
            return new CapabilityType(TYPE_ID);
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }
    }

    private static final class VirtualScalarCapability implements MachineCapability {
        private final LongValueStorage storage;

        private VirtualScalarCapability(long amount) {
            storage = new LongValueStorage(amount, amount, null);
            storage.setAmount(amount);
        }

        @Override
        public CapabilityType type() {
            return new CapabilityType(TYPE_ID);
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
                    return VirtualScalarCapability.this.type();
                }

                @Override
                public IOType ioType() {
                    return VirtualScalarCapability.this.ioType();
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            VirtualRequest virtual = (VirtualRequest) request;
            return transaction -> {
                storage.updateSnapshots(transaction);
                return storage.extract(virtual.parallelism(), false) == virtual.parallelism()
                        ? CapabilityResult.successful()
                        : CapabilityResult.failure(new ExecutionStatus(TYPE_ID, StatusSeverity.BLOCKED,
                        TYPE_ID, java.util.Map.of("reason", "virtual_scalar_commit")));
            };
        }
    }
}
