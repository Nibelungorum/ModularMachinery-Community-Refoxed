package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityOperation;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.transfer.TransferContext;
import cn.howxu.mmcr.api.capability.transfer.TransferPolicy;
import cn.howxu.mmcr.api.capability.transfer.TransferResult;
import cn.howxu.mmcr.api.capability.transfer.TransferStrategyRegistry;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registry and context contract tests using a capability that exists only in this test.
 *
 * @author howxu <dev@howxu.cn>
 */
class TransferStrategyRegistryTest {
    private static final CapabilityType TEST_TYPE = new CapabilityType(MMCR.id("test_transfer_strategy"));
    private static final List<TransferContext> OBSERVED_CONTEXTS = new ArrayList<>();
    private static final TransferPolicy TEST_POLICY = context -> {
        OBSERVED_CONTEXTS.add(context);
        return TransferResult.moved(context.simulate() ? 1L : 2L);
    };

    @BeforeAll
    static void registerTestPolicy() {
        TransferStrategyRegistry.register(TEST_TYPE, TEST_POLICY);
    }

    @Test
    void capability_type_lookup_accepts_a_test_only_policy() {
        assertThat(TransferStrategyRegistry.policyFor(new CapabilityType(TEST_TYPE.id())))
                .containsSame(TEST_POLICY);
    }

    @Test
    void simulate_and_commit_pass_direction_side_parallelism_and_transaction_context() {
        MachineCapability capability = capability();
        TransferPolicy policy = TransferStrategyRegistry.policyFor(TEST_TYPE).orElseThrow();

        TransferResult simulated = policy.transfer(TransferContext.simulate(capability, Direction.NORTH, 3L));

        assertThat(simulated.amount()).isEqualTo(1L);
        TransferContext simulationContext = OBSERVED_CONTEXTS.getLast();
        assertThat(simulationContext.capability()).isSameAs(capability);
        assertThat(simulationContext.ioType()).isEqualTo(IOType.INPUT);
        assertThat(simulationContext.side()).isEqualTo(Direction.NORTH);
        assertThat(simulationContext.parallelism()).isEqualTo(3L);
        assertThat(simulationContext.transaction()).isNull();
        assertThat(simulationContext.simulate()).isTrue();

        try (Transaction transaction = Transaction.openRoot()) {
            TransferResult committed = policy.transfer(TransferContext.commit(capability, Direction.SOUTH, 7L,
                    transaction));

            assertThat(committed.amount()).isEqualTo(2L);
            TransferContext commitContext = OBSERVED_CONTEXTS.getLast();
            assertThat(commitContext.ioType()).isEqualTo(IOType.INPUT);
            assertThat(commitContext.side()).isEqualTo(Direction.SOUTH);
            assertThat(commitContext.parallelism()).isEqualTo(7L);
            assertThat(commitContext.transaction()).isSameAs(transaction);
            assertThat(commitContext.simulate()).isFalse();
        }
    }

    private static MachineCapability capability() {
        return new MachineCapability() {
            @Override
            public CapabilityType type() {
                return TEST_TYPE;
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
                        return TEST_TYPE;
                    }

                    @Override
                    public IOType ioType() {
                        return IOType.INPUT;
                    }
                };
            }

            @Override
            public CapabilityOperation prepare(CapabilityRequest request) {
                return transaction -> null;
            }
        };
    }
}
