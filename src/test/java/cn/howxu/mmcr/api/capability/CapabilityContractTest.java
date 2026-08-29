package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class CapabilityContractTest {

    @Test
    void capability_operation_commits_only_when_the_root_transaction_commits() {
        TestCapability capability = new TestCapability();
        CapabilityOperation operation = capability.prepare(new TestRequest(1));

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
            capability.prepare(new TestRequest(1)).commit(transaction);
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

    private record TestRequest(long parallelism) implements CapabilityRequest {
        @Override
        public CapabilityType type() {
            return new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", "test"));
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }
    }

    private static final class TestCapability implements MachineCapability {
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

        @Override
        public CapabilityType type() {
            return new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", "test"));
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
                    return TestCapability.this.type();
                }

                @Override
                public IOType ioType() {
                    return TestCapability.this.ioType();
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
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
