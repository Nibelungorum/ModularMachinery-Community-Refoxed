package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavior tests for the built-in smart-interface capability.
 *
 * @author howxu <dev@howxu.cn>
 */
class SmartInterfaceCapabilityTest {
    @Test
    void smart_value_commit_updates_the_existing_interface() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("temperature", 20F);
        SmartInterfaceCapability capability = new SmartInterfaceCapability(storage, IOType.OUTPUT);
        CapabilityRequests.SmartValueRequest request = new CapabilityRequests.SmartValueRequest(
                capability.type(), IOType.OUTPUT, 1, "temperature", 80F);

        assertThat(capability.prepare(request).commit().success()).isTrue();
        assertThat(storage.value("temperature")).contains(80F);
    }

    @Test
    void unsupported_smart_value_is_blocked_and_invalid_values_are_rejected() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("mode", 1F);
        SmartInterfaceCapability capability = new SmartInterfaceCapability(storage, IOType.OUTPUT);
        CapabilityRequests.ValueRequest wrongRequest = new CapabilityRequests.ValueRequest(
                capability.type(), IOType.OUTPUT, 1, 1L, true);

        assertThat(capability.prepare(wrongRequest).commit().status().reason())
                .isEqualTo(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertThatThrownBy(() -> storage.set("mode", Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.values()).isEqualTo(Map.of("mode", 1F));
    }

    @Test
    void output_capability_rejects_input_request() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("mode", 1F);
        SmartInterfaceCapability capability = new SmartInterfaceCapability(storage, IOType.OUTPUT);
        CapabilityRequests.SmartValueRequest request = new CapabilityRequests.SmartValueRequest(
                capability.type(), IOType.INPUT, 1, "mode", 2F);

        assertThat(capability.prepare(request).commit().success()).isFalse();
        assertThat(storage.value("mode")).contains(1F);
    }

    @Test
    void input_capability_rejects_output_request() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("mode", 1F);
        SmartInterfaceCapability capability = new SmartInterfaceCapability(storage, IOType.INPUT);
        CapabilityRequests.SmartValueRequest request = new CapabilityRequests.SmartValueRequest(
                capability.type(), IOType.OUTPUT, 1, "mode", 2F);

        assertThat(capability.prepare(request).commit().success()).isFalse();
        assertThat(storage.value("mode")).contains(1F);
    }
}
