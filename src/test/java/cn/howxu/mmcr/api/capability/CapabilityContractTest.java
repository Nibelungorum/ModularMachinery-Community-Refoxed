package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.transfer.TransferContext;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class CapabilityContractTest {

    @Test
    void capability_operation_commits_only_when_the_root_transaction_commits() {
        TestCapability capability = new TestCapability();
        CapabilityOperation operation = capability.prepare(new TestRequest(IOType.INPUT, 1));

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(operation.commit(transaction).success()).isTrue();
            assertThat(capability.amount()).isZero();
            transaction.commit();
        }

        assertThat(capability.amount()).isEqualTo(1L);
    }

    @Test
    void capability_operation_is_rolled_back_when_the_root_transaction_does_not_commit() {
        TestCapability capability = new TestCapability();

        try (Transaction transaction = Transaction.openRoot()) {
            capability.prepare(new TestRequest(IOType.INPUT, 1)).commit(transaction);
        }

        assertThat(capability.amount()).isZero();
    }

    @Test
    void host_exposes_an_immutable_capability_snapshot() {
        TestHost host = new TestHost(List.of(new TestCapability()));
        List<MachineCapability> snapshot = host.capabilities();

        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void capability_type_rejects_a_null_identifier() {
        assertThatThrownBy(() -> new CapabilityType(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failed_capability_result_requires_a_status() {
        assertThatThrownBy(() -> CapabilityResult.failure(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void bidirectional_capability_accepts_both_request_directions() {
        TestCapability capability = new TestCapability(CapabilityDirections.bidirectional());

        assertThat(capability.directions().supports(IOType.INPUT)).isTrue();
        assertThat(capability.directions().supports(IOType.OUTPUT)).isTrue();
    }

    @Test
    void directions_reject_an_empty_set() {
        assertThatThrownBy(() -> CapabilityDirections.of())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transfer_context_accepts_each_supported_bidirectional_request_direction() {
        TestCapability bidirectional = new TestCapability(CapabilityDirections.bidirectional());
        TestCapability inputOnly = new TestCapability(CapabilityDirections.input());

        assertThatCode(() -> TransferContext.simulate(bidirectional, IOType.INPUT, Direction.NORTH, 1L))
                .doesNotThrowAnyException();
        assertThatCode(() -> TransferContext.simulate(bidirectional, IOType.OUTPUT, Direction.NORTH, 1L))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TransferContext.simulate(inputOnly, IOType.OUTPUT, Direction.NORTH, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capability_prepare_uses_the_exact_request_direction() {
        TestCapability bidirectional = new TestCapability(CapabilityDirections.bidirectional());
        TestCapability inputOnly = new TestCapability(CapabilityDirections.input());

        assertThatCode(() -> bidirectional.prepare(new TestRequest(IOType.INPUT, 1L)))
                .doesNotThrowAnyException();
        assertThatCode(() -> bidirectional.prepare(new TestRequest(IOType.OUTPUT, 1L)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> inputOnly.prepare(new TestRequest(IOType.OUTPUT, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bidirectional_capabilities_expose_both_directions() {
        TestCapability capability = new TestCapability(CapabilityDirections.bidirectional());

        assertThat(capability.directions().supports(IOType.INPUT)).isTrue();
        assertThat(capability.directions().supports(IOType.OUTPUT)).isTrue();
        assertThatThrownBy(() -> TransferContext.simulate(capability, Direction.NORTH, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    private record TestRequest(IOType ioType, long parallelism) implements CapabilityRequest {
        @Override
        public CapabilityType type() {
            return new CapabilityType(ResourceLocation.fromNamespaceAndPath("mmcr_test", "test"));
        }

    }

    private static final class TestCapability implements MachineCapability, OperationFacet {
        private final CapabilityDirections directions;
        private final SnapshotJournal<Long> journal = new SnapshotJournal<>() {
            @Override
            protected Long createSnapshot() {
                return pending;
            }

            @Override
            protected void revertToSnapshot(Long snapshot) {
                pending = snapshot;
            }

            @Override
            protected void onRootCommit(Long originalState) {
                amount += pending;
                pending = 0;
            }
        };

        private long amount;
        private long pending;

        private TestCapability() {
            this(CapabilityDirections.input());
        }

        private TestCapability(CapabilityDirections directions) {
            this.directions = directions;
        }

        @Override
        public CapabilityType type() {
            return new CapabilityType(ResourceLocation.fromNamespaceAndPath("mmcr_test", "test"));
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
                    return TestCapability.this.type();
                }

                @Override
                public CapabilityDirections directions() {
                    return TestCapability.this.directions();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return Set.of(OperationFacet.class);
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityFactories.operation(this, request);
        }

        @Override
        public CapabilityOperation prepareOperation(CapabilityRequest request) {
            return new CapabilityOperation() {
                @Override
                public CapabilityResult commit(TransactionContext transaction) {
                    journal.updateSnapshots(transaction);
                    pending += request.parallelism();
                    return CapabilityResult.successful();
                }
            };
        }

        private long amount() {
            return amount;
        }
    }

    private record TestHost(List<MachineCapability> values) implements CapabilityHost {
        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(values);
        }
    }
}
