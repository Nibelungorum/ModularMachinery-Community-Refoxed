package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.test.capability.CapabilityContractAssertions;
import cn.howxu.mmcr.test.capability.TestExchangeFacet;
import cn.howxu.mmcr.test.capability.TestNetworkParticipantFacet;
import cn.howxu.mmcr.test.capability.TestResource;
import cn.howxu.mmcr.test.capability.TestResourceFacet;
import cn.howxu.mmcr.test.capability.TestScalarFacet;
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
        TestScalarFacet facet = new TestScalarFacet();

        assertThat(facet.insert(2L, true)).isEqualTo(2L);
        assertThat(facet.amount()).isZero();
        CapabilityContractAssertions.assertRollsBack(facet);
        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityContractAssertions.assertCommitted(facet.prepareScalar(
                    CapabilityContractAssertions.request(2L)).commit(transaction));
            transaction.commit();
        }
        assertThat(facet.amount()).isEqualTo(2L);
    }

    @Test
    void exchange_facet_applies_signed_deltas_within_capacity() {
        TestExchangeFacet facet = new TestExchangeFacet();

        try (Transaction transaction = Transaction.openRoot()) {
            CapabilityContractAssertions.assertCommitted(facet.prepareExchange(3D).commit(transaction));
            CapabilityContractAssertions.assertCommitted(facet.prepareExchange(-1D).commit(transaction));
            transaction.commit();
        }
        assertThat(facet.potential()).isEqualTo(2D);
        assertThat(facet.capacity()).isGreaterThanOrEqualTo(facet.potential());
        assertThat(facet.conductance()).isPositive();
    }

    @Test
    void network_participant_invalidates_topology_and_returns_read_only_snapshots() {
        TestNetworkParticipantFacet facet = new TestNetworkParticipantFacet();

        facet.attach();
        long attachedVersion = facet.topologyVersion();
        facet.detach();

        assertThat(facet.topologyVersion()).isGreaterThan(attachedVersion);
        assertThatThrownBy(() -> facet.networkSnapshot().capabilities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
