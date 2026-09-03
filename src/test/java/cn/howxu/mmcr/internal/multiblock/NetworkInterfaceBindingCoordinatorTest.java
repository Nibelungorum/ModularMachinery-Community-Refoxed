package cn.howxu.mmcr.internal.multiblock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies network-interface connection coordination decisions.
 * @author howxu <dev@howxu.cn>
 */
class NetworkInterfaceBindingCoordinatorTest {

    @Test
    void connect_exposes_deterministic_outcomes_for_key_card_feedback() {
        assertThat(NetworkInterfaceBindingCoordinator.ConnectionResult.values()).containsExactly(
                NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED,
                NetworkInterfaceBindingCoordinator.ConnectionResult.DUPLICATE,
                NetworkInterfaceBindingCoordinator.ConnectionResult.SOURCE_UNLOADED,
                NetworkInterfaceBindingCoordinator.ConnectionResult.TARGET_UNLOADED,
                NetworkInterfaceBindingCoordinator.ConnectionResult.INVALID_SOURCE,
                NetworkInterfaceBindingCoordinator.ConnectionResult.INVALID_TARGET,
                NetworkInterfaceBindingCoordinator.ConnectionResult.SOURCE_NOT_FORMED,
                NetworkInterfaceBindingCoordinator.ConnectionResult.TARGET_NOT_FORMED,
                NetworkInterfaceBindingCoordinator.ConnectionResult.SOURCE_IDENTITY_MISMATCH,
                NetworkInterfaceBindingCoordinator.ConnectionResult.TARGET_IDENTITY_MISMATCH,
                NetworkInterfaceBindingCoordinator.ConnectionResult.ALLOWLIST_REJECTED,
                NetworkInterfaceBindingCoordinator.ConnectionResult.SOURCE_CAPACITY,
                NetworkInterfaceBindingCoordinator.ConnectionResult.TARGET_CAPACITY);
    }
}
