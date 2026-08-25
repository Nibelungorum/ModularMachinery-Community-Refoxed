package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transactional behavior tests for the built-in smart-interface capability.
 *
 * @author howxu <dev@howxu.cn>
 */
class SmartInterfaceCapabilityTest {
    @Test
    void smart_value_commit_updates_the_existing_interface_after_root_commit() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("temperature", 20F);
        SmartInterfaceCapability capability = new SmartInterfaceCapability(storage, IOType.OUTPUT);
        CapabilityRequests.SmartValueRequest request = new CapabilityRequests.SmartValueRequest(
                capability.type(), IOType.OUTPUT, 1, "temperature", 80F);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(capability.prepare(request).commit(transaction).success()).isTrue();
            assertThat(storage.value("temperature")).contains(80F);
            transaction.commit();
        }

        assertThat(storage.value("temperature")).contains(80F);
    }

    @Test
    void smart_value_operation_rolls_back_without_root_commit() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("mode", 1F);
        SmartInterfaceCapability capability = new SmartInterfaceCapability(storage, IOType.INPUT);
        CapabilityRequests.SmartValueRequest request = new CapabilityRequests.SmartValueRequest(
                capability.type(), IOType.INPUT, 1, "mode", 2F);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(capability.prepare(request).commit(transaction).success()).isTrue();
        }

        assertThat(storage.value("mode")).contains(1F);
    }

    @Test
    void unsupported_smart_value_is_blocked_and_invalid_values_are_rejected() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("mode", 1F);
        SmartInterfaceCapability capability = new SmartInterfaceCapability(storage, IOType.OUTPUT);
        CapabilityRequests.ValueRequest wrongRequest = new CapabilityRequests.ValueRequest(
                capability.type(), IOType.OUTPUT, 1, 1L, true);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(capability.prepare(wrongRequest).commit(transaction).status().details())
                    .containsEntry("reason", "unsupported_request");
        }
        assertThatThrownBy(() -> storage.set("mode", Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.values()).isEqualTo(Map.of("mode", 1F));
    }
}
