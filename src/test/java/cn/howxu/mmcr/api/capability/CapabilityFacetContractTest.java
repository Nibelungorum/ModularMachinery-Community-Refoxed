package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.test.capability.CapabilityContractAssertions;
import cn.howxu.mmcr.test.capability.TestExchangeFacet;
import cn.howxu.mmcr.test.capability.TestNetworkParticipantFacet;
import cn.howxu.mmcr.test.capability.TestResource;
import cn.howxu.mmcr.test.capability.TestResourceFacet;
import cn.howxu.mmcr.test.capability.TestScalarFacet;
import cn.howxu.mmcr.api.capability.facet.ExchangeFacet;
import cn.howxu.mmcr.api.capability.facet.NetworkParticipantFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reusable contracts for the four public capability facet families.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityFacetContractTest {
    @Test
    void resource_facet_matches_identity_and_commits_only_at_transaction_root() {
        TestResourceFacet facet = new TestResourceFacet();
        TestResource iron = new TestResource("iron");
        TestResource gold = new TestResource("gold");

        assertThat(facet.facet(ResourceFacet.class)).contains(facet);
        assertThat(new CapabilitySnapshot(List.of(facet)).facets(ResourceFacet.class)).containsExactly(facet);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(facet.storage().insert(0, iron, 3L, transaction)).isEqualTo(3L);
            assertThat(facet.storage().insert(0, gold, 1L, transaction)).isZero();
            assertThat(facet.storage().amount(0)).isEqualTo(3L);
        }
        assertThat(facet.storage().amount(0)).isZero();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(facet.storage().insert(0, iron, 3L, transaction)).isEqualTo(3L);
            transaction.commit();
        }
        assertThat(facet.storage().resource(0)).isEqualTo(iron);
        assertThat(facet.storage().amount(0)).isEqualTo(3L);
    }

    @Test
    void scalar_facet_supports_simulation_commit_and_rollback() {
        TestScalarFacet facet = new TestScalarFacet(IOType.OUTPUT);

        assertThat(facet.facet(ScalarFacet.class)).contains(facet);
        assertThat(new CapabilitySnapshot(List.of(facet)).facets(ScalarFacet.class)).containsExactly(facet);

        assertThat(facet.insert(2L, true)).isEqualTo(2L);
        assertThat(facet.amount()).isZero();
        CapabilityContractAssertions.assertRollsBack(facet);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(facet.prepareScalar(CapabilityContractAssertions.request(
                    IOType.INPUT, 1L)).commit(transaction).success())
                    .isFalse();
            transaction.commit();
        }
        assertThat(facet.amount()).isZero();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(facet.prepareScalar(CapabilityContractAssertions.request(
                    IOType.OUTPUT, -1L)).commit(transaction).success())
                    .isFalse();
            transaction.commit();
        }
        assertThat(facet.amount()).isZero();
        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityContractAssertions.assertCommitted(facet.prepareScalar(
                    CapabilityContractAssertions.request(IOType.OUTPUT, 2L)).commit(transaction));
            transaction.commit();
        }
        assertThat(facet.amount()).isEqualTo(2L);

        TestScalarFacet inputFacet = new TestScalarFacet(IOType.INPUT);
        inputFacet.setAmount(2L);
        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityContractAssertions.assertCommitted(inputFacet.prepareScalar(
                    CapabilityContractAssertions.request(IOType.INPUT, 1L)).commit(transaction));
            transaction.commit();
        }
        assertThat(inputFacet.amount()).isEqualTo(1L);
    }

    @Test
    void exchange_facet_applies_signed_deltas_within_capacity() {
        TestExchangeFacet facet = new TestExchangeFacet();

        assertThat(facet.facet(ExchangeFacet.class)).contains(facet);
        assertThat(new CapabilitySnapshot(List.of(facet)).facets(ExchangeFacet.class)).containsExactly(facet);

        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityContractAssertions.assertCommitted(facet.prepareExchange(3D).commit(transaction));
            CapabilityContractAssertions.assertCommitted(facet.prepareExchange(-1D).commit(transaction));
            transaction.commit();
        }
        assertThat(facet.potential()).isEqualTo(2D);
        assertThat(facet.capacity()).isGreaterThanOrEqualTo(facet.potential());
        assertThat(facet.conductance()).isPositive();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(facet.prepareExchange(9D).commit(transaction).success()).isFalse();
            transaction.commit();
        }
        assertThat(facet.potential()).isEqualTo(2D);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(facet.prepareExchange(-9D).commit(transaction).success()).isFalse();
            transaction.commit();
        }
        assertThat(facet.potential()).isEqualTo(2D);
        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityContractAssertions.assertCommitted(facet.prepareExchange(1D).commit(transaction));
            assertThat(facet.prepareExchange(9D).commit(transaction).success()).isFalse();
        }
        assertThat(facet.potential()).isEqualTo(2D);
    }

    @Test
    void network_participant_invalidates_topology_and_returns_read_only_snapshots() {
        TestNetworkParticipantFacet facet = new TestNetworkParticipantFacet();

        assertThat(facet.facet(NetworkParticipantFacet.class)).contains(facet);
        assertThat(facet.networkSnapshot().capabilities()).isEmpty();

        facet.attach();
        long attachedVersion = facet.topologyVersion();
        CapabilitySnapshot attachedSnapshot = facet.networkSnapshot();

        assertThat(attachedSnapshot.capabilities()).containsExactly(facet);
        assertThat(attachedSnapshot.facets(NetworkParticipantFacet.class)).containsExactly(facet);
        facet.detach();

        assertThat(facet.topologyVersion()).isGreaterThan(attachedVersion);
        CapabilitySnapshot detachedSnapshot = facet.networkSnapshot();
        assertThat(detachedSnapshot).isNotSameAs(attachedSnapshot);
        assertThat(detachedSnapshot.capabilities()).isEmpty();
        assertThat(attachedSnapshot.capabilities()).containsExactly(facet);
        assertThat(attachedSnapshot.facets(NetworkParticipantFacet.class)).containsExactly(facet);
        assertThatThrownBy(() -> attachedSnapshot.capabilities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
